package br.com.controlegastos.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.FormatadorMoeda;
import br.com.controlegastos.model.Usuario;

/**
 * Analisa os lançamentos de um mês e gera sugestões para gastar melhor:
 * onde a pessoa mais gasta, o que aumentou, limites, quanto conseguiu guardar,
 * projeção até o fim do mês e pequenos gastos que se acumulam.
 */
@Service
public class AnaliseGastos {

	/** Até este valor uma despesa é considerada "pequena". */
	static final double GASTO_PEQUENO = 30;

	private static final Locale BRASIL = Locale.of("pt", "BR");

	private final ControleGastos controle;

	public AnaliseGastos(ControleGastos controle) {
		this.controle = controle;
	}

	/** Resultado da análise: sugestões, ranking das categorias e alguns números do mês. */
	public record Analise(List<Sugestao> sugestoes, List<Ranking> ranking, double despesas, double receitas,
			double mediaDiaria, Double taxaEconomia, String maiorCategoria) {
	}

	/** Uma categoria no ranking de gastos, com a variação em relação ao mês anterior. */
	public record Ranking(ResumoCategoria resumo, double mesAnterior) {

		/** Variação percentual sobre o mês anterior; {@code null} se não houve gasto antes. */
		public Double variacao() {
			return mesAnterior > 0 ? (resumo.total() - mesAnterior) / mesAnterior * 100 : null;
		}
	}

	public Analise analisar(Usuario usuario, YearMonth mes, LocalDate hoje) {
		int m = mes.getMonthValue(), a = mes.getYear();
		List<Despesa> despesasMes = controle.listarDespesas(usuario, mes);
		double despesas = controle.totalDespesasMes(usuario, m, a);
		double receitas = controle.totalReceitasMes(usuario, m, a);
		double saldoAnterior = controle.saldoAnterior(usuario, m, a);
		List<ResumoCategoria> categorias = controle.resumoPorCategoria(usuario, mes);

		YearMonth anterior = mes.minusMonths(1);
		Map<Integer, Double> totaisAnteriores = controle.resumoPorCategoria(usuario, anterior).stream()
				.collect(Collectors.toMap(r -> r.categoria().getId(), ResumoCategoria::total));
		List<Ranking> ranking = categorias.stream().filter(r -> r.total() > 0)
				.map(r -> new Ranking(r, totaisAnteriores.getOrDefault(r.categoria().getId(), 0.0))).toList();

		// Dias considerados para a média: o mês todo, ou até hoje se for o mês atual.
		boolean mesAtual = mes.equals(YearMonth.from(hoje));
		int diasDecorridos = mesAtual ? hoje.getDayOfMonth() : mes.lengthOfMonth();
		double mediaDiaria = despesas / diasDecorridos;
		Double taxaEconomia = receitas > 0 ? (receitas - despesas) / receitas * 100 : null;

		List<Sugestao> lista = new ArrayList<>();
		if (despesasMes.isEmpty()) {
			lista.add(new Sugestao("DICA", "despesa", "Nenhuma despesa em " + nomeMes(mes),
					"Registre seus gastos assim que eles acontecerem — mesmo os pequenos. Com os lançamentos em dia, "
							+ "esta aba mostra onde você gasta mais e como melhorar."));
		} else {
			limites(categorias, mesAtual, hoje, mes, lista);
			balanco(receitas, despesas, saldoAnterior, taxaEconomia, mes, lista);
			projecao(despesas, receitas, saldoAnterior, mediaDiaria, mesAtual, hoje, mes, lista);
			maiorCategoria(ranking, despesas, lista);
			comparacao(ranking, anterior, lista);
			pequenosGastos(despesasMes, despesas, lista);
			maiorDespesa(despesasMes, lista);
		}
		lista.sort(Comparator.comparingInt(s -> List.of("ALERTA", "ATENCAO", "DICA", "POSITIVO").indexOf(s.tipo())));

		String maior = ranking.isEmpty() ? null : ranking.get(0).resumo().categoria().getNome();
		return new Analise(lista, ranking, despesas, receitas, mediaDiaria, taxaEconomia, maior);
	}

	private static void limites(List<ResumoCategoria> categorias, boolean mesAtual, LocalDate hoje, YearMonth mes,
			List<Sugestao> lista) {
		int diasRestantes = mesAtual ? mes.lengthOfMonth() - hoje.getDayOfMonth() + 1 : 0;
		for (ResumoCategoria r : categorias) {
			String nome = r.categoria().getNome();
			double limite = r.categoria().getLimiteMensal();
			if ("ESTOURADO".equals(r.nivel())) {
				lista.add(new Sugestao("ALERTA", "alerta", nome + " passou do limite",
						"Você gastou " + moeda(r.total()) + " de um limite de " + moeda(limite) + " ("
								+ moeda(r.total() - limite) + " a mais). " + dicaDa(nome)
								+ (mesAtual ? " Evite novos gastos nessa categoria até o fim do mês." : "")));
			} else if ("ATENCAO".equals(r.nivel())) {
				double resta = limite - r.total();
				String porDia = mesAtual && diasRestantes > 0
						? " Para não estourar, gaste no máximo " + moeda(resta / diasRestantes) + " por dia nos próximos "
								+ diasRestantes + (diasRestantes == 1 ? " dia." : " dias.")
						: "";
				lista.add(new Sugestao("ATENCAO", "alerta", nome + " está perto do limite",
						"Já foram " + (int) Math.floor(r.percentualLimite()) + "% do limite (restam " + moeda(resta) + ")."
								+ porDia));
			}
		}
	}

	private static void balanco(double receitas, double despesas, double saldoAnterior, Double taxa, YearMonth mes,
			List<Sugestao> lista) {
		if (receitas == 0) {
			lista.add(new Sugestao("ATENCAO", "receita", "Nenhuma receita registrada em " + nomeMes(mes),
					"As despesas estão saindo do que sobrou dos meses anteriores (" + moeda(saldoAnterior)
							+ "). Se você recebeu mesada, bolsa ou salário, registre em Receitas para o saldo ficar correto."));
		} else if (despesas > receitas) {
			lista.add(new Sugestao("ALERTA", "tendencia", "Você gastou mais do que recebeu",
					"As despesas (" + moeda(despesas) + ") passaram as receitas (" + moeda(receitas) + ") em "
							+ moeda(despesas - receitas) + ". A diferença está saindo da sobra dos meses anteriores. "
							+ "Escolha uma ou duas categorias para cortar no próximo mês."));
		} else if (taxa < 10) {
			lista.add(new Sugestao("ATENCAO", "fontes", "Sobrou pouco este mês",
					"Você guardou " + (int) Math.floor(taxa) + "% do que recebeu. Uma boa meta para estudantes é guardar "
							+ "de 10% a 20%: separe esse valor assim que receber, antes de começar a gastar."));
		} else {
			lista.add(new Sugestao("POSITIVO", "ok", "Você guardou " + (int) Math.floor(taxa) + "% do que recebeu",
					"Sobraram " + moeda(receitas - despesas) + " em " + nomeMes(mes) + ". Que tal reservar parte disso "
							+ "para uma reserva de emergência ou para algo que você quer comprar?"));
		}
	}

	private static void projecao(double despesas, double receitas, double saldoAnterior, double mediaDiaria,
			boolean mesAtual, LocalDate hoje, YearMonth mes, List<Sugestao> lista) {
		if (!mesAtual || hoje.getDayOfMonth() >= mes.lengthOfMonth()) {
			return;
		}
		double previsto = mediaDiaria * mes.lengthOfMonth();
		double disponivel = saldoAnterior + receitas;
		if (previsto > disponivel) {
			lista.add(new Sugestao("ALERTA", "tendencia", "No ritmo atual, o dinheiro não fecha o mês",
					"Você está gastando em média " + moeda(mediaDiaria) + " por dia. Nesse ritmo as despesas chegam a "
							+ moeda(previsto) + " até o dia " + mes.lengthOfMonth() + ", mais do que você tem disponível ("
							+ moeda(disponivel) + ")."));
		} else {
			lista.add(new Sugestao("DICA", "tendencia", "Previsão para o fim do mês",
					"Gastando em média " + moeda(mediaDiaria) + " por dia, você deve fechar " + nomeMes(mes)
							+ " com cerca de " + moeda(previsto) + " em despesas."));
		}
	}

	private static void maiorCategoria(List<Ranking> ranking, double despesas, List<Sugestao> lista) {
		if (ranking.isEmpty()) {
			return;
		}
		ResumoCategoria maior = ranking.get(0).resumo();
		lista.add(new Sugestao("DICA", maior.categoria().getNome(), "Seu maior gasto é com " + maior.categoria().getNome(),
				moeda(maior.total()) + ", ou " + (int) Math.round(maior.participacao()) + "% de tudo o que você gastou. "
						+ dicaDa(maior.categoria().getNome())));
	}

	private static void comparacao(List<Ranking> ranking, YearMonth anterior, List<Sugestao> lista) {
		for (Ranking r : ranking) {
			Double variacao = r.variacao();
			double diferenca = r.resumo().total() - r.mesAnterior();
			String nome = r.resumo().categoria().getNome();
			if (variacao != null && variacao >= 20 && diferenca >= 20) {
				lista.add(new Sugestao("ATENCAO", "tendencia", nome + " subiu " + (int) Math.round(variacao) + "%",
						"Em " + nomeMes(anterior) + " foram " + moeda(r.mesAnterior()) + "; agora já são "
								+ moeda(r.resumo().total()) + " (" + moeda(diferenca) + " a mais). Veja o que mudou nesses gastos."));
			} else if (variacao != null && variacao <= -20 && -diferenca >= 20) {
				lista.add(new Sugestao("POSITIVO", "ok", "Você gastou menos com " + nome,
						"Caiu de " + moeda(r.mesAnterior()) + " em " + nomeMes(anterior) + " para "
								+ moeda(r.resumo().total()) + ". Continue assim!"));
			}
		}
	}

	private static void pequenosGastos(List<Despesa> despesasMes, double despesas, List<Sugestao> lista) {
		List<Despesa> pequenos = despesasMes.stream().filter(d -> d.getValor() <= GASTO_PEQUENO).toList();
		double soma = pequenos.stream().mapToDouble(Despesa::getValor).sum();
		if (pequenos.size() >= 3 && soma >= despesas * 0.10) {
			lista.add(new Sugestao("DICA", "despesa", "Pequenos gastos somam " + moeda(soma),
					pequenos.size() + " despesas de até " + moeda(GASTO_PEQUENO) + " representam "
							+ (int) Math.round(soma / despesas * 100) + "% do que você gastou. Lanches, cafés e aplicativos "
							+ "passam despercebidos: defina um valor semanal para eles."));
		}
	}

	private static void maiorDespesa(List<Despesa> despesasMes, List<Sugestao> lista) {
		Despesa maior = despesasMes.stream().max(Comparator.comparingDouble(Despesa::getValor)).orElseThrow();
		Map<String, Long> repetidas = despesasMes.stream()
				.collect(Collectors.groupingBy(d -> d.getDescricao().trim().toLowerCase(BRASIL), Collectors.counting()));
		repetidas.entrySet().stream().filter(e -> e.getValue() >= 3).max(Map.Entry.comparingByValue())
				.ifPresent(e -> {
					String descricao = despesasMes.stream().map(Despesa::getDescricao)
							.filter(d -> d.trim().toLowerCase(BRASIL).equals(e.getKey())).findFirst().orElse(e.getKey());
					double total = despesasMes.stream().filter(d -> d.getDescricao().trim().toLowerCase(BRASIL).equals(e.getKey()))
							.mapToDouble(Despesa::getValor).sum();
					lista.add(new Sugestao("DICA", "despesa", "\"" + descricao + "\" aparece " + e.getValue() + " vezes",
							"Somando, foram " + moeda(total) + " no mês. Gastos que se repetem são os mais fáceis de reduzir."));
				});
		lista.add(new Sugestao("DICA", maior.getCategoria().getNome(), "Maior despesa do mês: " + maior.getDescricao(),
				moeda(maior.getValor()) + " em " + maior.getData().format(DateTimeFormatter.ofPattern("dd/MM"))
						+ ". Compras grandes ficam mais leves quando planejadas: reserve um pouco por mês antes de fazê-las."));
	}

	/** Dica prática conforme o nome da categoria. */
	static String dicaDa(String categoria) {
		String c = categoria.toLowerCase(BRASIL);
		Map<String, String> dicas = Map.of(
				"aliment", "Dá para economizar priorizando o RU, levando marmita e fazendo lista antes de ir ao mercado.",
				"mercado", "Faça lista antes de ir ao mercado e compare preços de marcas próprias.",
				"transporte", "Use a meia-tarifa ou o passe estudantil, combine caronas e junte compromissos no mesmo dia.",
				"lazer", "Procure eventos gratuitos da universidade, dias de promoção no cinema e defina um valor fixo para o lazer.",
				"material", "Use a biblioteca, livros digitais e grupos de troca de material entre colegas.",
				"moradia", "Divida contas com colegas e acompanhe o consumo de luz e água.",
				"saúde", "Verifique os atendimentos gratuitos ou com desconto oferecidos pela universidade.");
		return dicas.entrySet().stream().filter(e -> c.contains(e.getKey())).map(Map.Entry::getValue).findFirst()
				.orElse("Revise os lançamentos dessa categoria e experimente um limite um pouco menor no próximo mês.");
	}

	private static String moeda(double valor) {
		return FormatadorMoeda.formatar(valor);
	}

	private static String nomeMes(YearMonth mes) {
		return mes.format(DateTimeFormatter.ofPattern("MMMM", BRASIL));
	}

}

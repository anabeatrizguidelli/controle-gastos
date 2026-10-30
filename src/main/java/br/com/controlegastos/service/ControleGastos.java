package br.com.controlegastos.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import br.com.controlegastos.model.Alerta;
import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.ReceitaRepository;

/**
 * Regras de negócio do diagrama de classes (classe ControleGastos): listagens,
 * totais do mês, saldo, resumo por categoria e alertas de limite. Tudo é
 * calculado a partir dos registros gravados no banco, sempre de um único usuário.
 */
@Service
public class ControleGastos {

	/** Mesma paleta do protótipo visual; a cor de cada categoria é fixa pelo id. */
	private static final String[] CORES = { "#2563eb", "#16a34a", "#9333ea", "#0891b2", "#db2777", "#64748b" };

	private final DespesaRepository despesas;
	private final ReceitaRepository receitas;
	private final CategoriaRepository categorias;

	public ControleGastos(DespesaRepository despesas, ReceitaRepository receitas, CategoriaRepository categorias) {
		this.despesas = despesas;
		this.receitas = receitas;
		this.categorias = categorias;
	}

	public static String corDa(Categoria categoria) {
		return categoria == null || categoria.getId() == null ? CORES[5] : CORES[categoria.getId() % CORES.length];
	}

	public List<Despesa> listarDespesas(Usuario usuario, YearMonth mes) {
		return despesas.findByUsuarioAndDataBetweenOrderByDataDescIdDesc(usuario, mes.atDay(1), mes.atEndOfMonth());
	}

	public List<Receita> listarReceitas(Usuario usuario, YearMonth mes) {
		return receitas.findByUsuarioAndDataBetweenOrderByDataDescIdDesc(usuario, mes.atDay(1), mes.atEndOfMonth());
	}

	public double totalPorCategoria(Usuario usuario, Categoria categoria, int mes, int ano) {
		return somarDespesas(listarDespesas(usuario, YearMonth.of(ano, mes)).stream()
				.filter(d -> d.getCategoria().getId().equals(categoria.getId())).toList());
	}

	public double totalDespesasMes(Usuario usuario, int mes, int ano) {
		return somarDespesas(listarDespesas(usuario, YearMonth.of(ano, mes)));
	}

	public double totalReceitasMes(Usuario usuario, int mes, int ano) {
		return somarReceitas(listarReceitas(usuario, YearMonth.of(ano, mes)));
	}

	/** Resultado só do mês: receitas − despesas do mês. */
	public double calcularSaldo(Usuario usuario, int mes, int ano) {
		return totalReceitasMes(usuario, mes, ano) - totalDespesasMes(usuario, mes, ano);
	}

	/**
	 * Saldo que sobrou de todos os meses anteriores (receitas − despesas lançadas
	 * antes do dia 1º do mês). É o valor com que o mês começa.
	 */
	public double saldoAnterior(Usuario usuario, int mes, int ano) {
		LocalDate inicio = LocalDate.of(ano, mes, 1);
		return receitas.somarAntesDe(usuario, inicio) - despesas.somarAntesDe(usuario, inicio);
	}

	/** Saldo disponível: o que sobrou dos meses anteriores + receitas − despesas do mês. */
	public double saldoDisponivel(Usuario usuario, int mes, int ano) {
		return saldoAnterior(usuario, mes, ano) + calcularSaldo(usuario, mes, ano);
	}

	/**
	 * Gasto de cada categoria no mês, do maior para o menor. Categorias sem gasto
	 * também aparecem, para mostrar o limite disponível.
	 */
	public List<ResumoCategoria> resumoPorCategoria(Usuario usuario, YearMonth mes) {
		List<Despesa> doMes = listarDespesas(usuario, mes);
		double totalMes = somarDespesas(doMes);
		List<ResumoCategoria> resumo = new ArrayList<>();
		for (Categoria c : categorias.findByUsuarioOrderByNomeAsc(usuario)) {
			double total = somarDespesas(doMes.stream().filter(d -> d.getCategoria().getId().equals(c.getId())).toList());
			double participacao = totalMes > 0 ? total / totalMes * 100 : 0;
			double percentualLimite = c.getLimiteMensal() > 0 ? total / c.getLimiteMensal() * 100 : 0;
			resumo.add(new ResumoCategoria(c, total, participacao, percentualLimite, c.calcularNivel(total), corDa(c)));
		}
		resumo.sort(Comparator.comparingDouble(ResumoCategoria::total).reversed());
		return resumo;
	}

	public List<Alerta> verificarAlertas(Usuario usuario, int mes, int ano) {
		return resumoPorCategoria(usuario, YearMonth.of(ano, mes)).stream()
				.filter(r -> !"OK".equals(r.nivel()))
				.map(r -> new Alerta(r.categoria().getNome(), r.nivel(), r.percentualLimite(), r.total(),
						r.categoria().getLimiteMensal()))
				.toList();
	}

	/** Receitas e despesas dos últimos {@code meses} meses, terminando em {@code ate}. */
	public List<ResumoMes> historico(Usuario usuario, YearMonth ate, int meses) {
		List<ResumoMes> lista = new ArrayList<>();
		for (int i = meses - 1; i >= 0; i--) {
			YearMonth m = ate.minusMonths(i);
			lista.add(new ResumoMes(m, totalReceitasMes(usuario, m.getMonthValue(), m.getYear()),
					totalDespesasMes(usuario, m.getMonthValue(), m.getYear())));
		}
		return lista;
	}

	/**
	 * Monta o CSS do gráfico de pizza (conic-gradient) com a participação de cada
	 * categoria nas despesas do mês.
	 */
	public static String graficoPizza(List<ResumoCategoria> resumo) {
		StringBuilder css = new StringBuilder("conic-gradient(");
		double inicio = 0;
		boolean algum = false;
		for (ResumoCategoria r : resumo) {
			if (r.total() <= 0) {
				continue;
			}
			double fim = inicio + r.participacao();
			css.append(algum ? ", " : "").append(r.cor())
					.append(String.format(Locale.ROOT, " %.2f%% %.2f%%", inicio, fim));
			inicio = fim;
			algum = true;
		}
		return algum ? css.append(")").toString() : "conic-gradient(var(--borda) 0 100%)";
	}

	private static double somarDespesas(List<Despesa> lista) {
		return lista.stream().mapToDouble(Despesa::getValor).sum();
	}

	private static double somarReceitas(List<Receita> lista) {
		return lista.stream().mapToDouble(Receita::getValor).sum();
	}

}

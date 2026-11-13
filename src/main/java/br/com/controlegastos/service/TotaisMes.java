package br.com.controlegastos.service;

import java.util.List;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Receita;

/**
 * Totais de um mês. O mês de uma despesa é o do vencimento (registros antigos,
 * sem vencimento, usam a data); o de uma receita é o da data.
 *
 * @param receitas todas as receitas do mês
 * @param despesas todas as despesas do mês (pagas + pendentes, inclusive parcelas e despesas fixas)
 * @param pagas    as despesas do mês já pagas
 */
public record TotaisMes(double receitas, double despesas, double pagas) {

	/** Soma as listas do mês que a página já carregou (sem consultar o banco de novo). */
	public static TotaisMes de(List<Receita> receitasDoMes, List<Despesa> despesasDoMes) {
		double receitas = receitasDoMes.stream().mapToDouble(Receita::getValor).sum();
		double despesas = 0;
		double pagas = 0;
		for (Despesa d : despesasDoMes) {
			despesas += d.getValor();
			if (d.isPago()) {
				pagas += d.getValor();
			}
		}
		return new TotaisMes(centavos(receitas), centavos(despesas), centavos(pagas));
	}

	/** Despesas do mês ainda a pagar. */
	public double pendentes() {
		return centavos(despesas - pagas);
	}

	/** Resultado do mês até agora: receitas − despesas pagas (as pendentes ainda não saíram). */
	public double resultado() {
		return centavos(receitas - pagas);
	}

	/**
	 * Saldo projetado ao final do mês, se todas as despesas do mês forem pagas:
	 * receitas − todas as despesas (pagas e pendentes). Negativo = quanto vai faltar.
	 */
	public double saldoProjetado() {
		return centavos(receitas - despesas);
	}

	private static double centavos(double valor) {
		return Math.round(valor * 100) / 100.0;
	}

}
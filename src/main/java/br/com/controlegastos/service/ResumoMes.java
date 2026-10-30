package br.com.controlegastos.service;

import java.time.YearMonth;

/**
 * Totais de um mês: receitas, despesas e saldo (receitas − despesas).
 */
public record ResumoMes(YearMonth mes, double receitas, double despesas) {

	public double saldo() {
		return receitas - despesas;
	}

}

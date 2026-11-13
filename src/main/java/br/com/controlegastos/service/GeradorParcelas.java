package br.com.controlegastos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Divide o valor total de uma compra em parcelas mensais.
 * <ul>
 * <li>Os centavos que sobram da divisão vão, um a um, para as primeiras
 * parcelas, então a soma é sempre igual ao valor total (ex.: R$ 100,00 em 3 =
 * 33,34 + 33,33 + 33,33).</li>
 * <li>Cada vencimento é calculado a partir do dia da primeira parcela. Se o mês
 * não tem esse dia, usa o último dia do mês, sem alterar os meses seguintes
 * (ex.: 31/01 → 28/02 → 31/03).</li>
 * </ul>
 */
public final class GeradorParcelas {

	public static final int MAXIMO_PARCELAS = 120;

	/** Uma parcela gerada: número (1, 2, 3...), valor e vencimento. */
	public record Parcela(int numero, BigDecimal valor, LocalDate vencimento) {
	}

	private GeradorParcelas() {
	}

	public static List<Parcela> gerar(BigDecimal valorTotal, int quantidade, LocalDate primeiroVencimento) {
		if (quantidade < 1 || quantidade > MAXIMO_PARCELAS) {
			throw new IllegalArgumentException("A quantidade de parcelas deve ser de 1 a " + MAXIMO_PARCELAS);
		}
		if (valorTotal == null || primeiroVencimento == null) {
			throw new IllegalArgumentException("Informe o valor e o vencimento");
		}
		long centavos = valorTotal.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
		long base = centavos / quantidade;
		long resto = centavos % quantidade;
		if (base <= 0) {
			throw new IllegalArgumentException("Cada parcela precisa ter pelo menos R$ 0,01");
		}
		List<Parcela> parcelas = new ArrayList<>(quantidade);
		for (int i = 0; i < quantidade; i++) {
			long valor = base + (i < resto ? 1 : 0);
			parcelas.add(new Parcela(i + 1, BigDecimal.valueOf(valor, 2), vencimento(primeiroVencimento, i)));
		}
		return parcelas;
	}

	/** Vencimento da parcela {@code indice} (0 = primeira), mantendo o dia da primeira parcela. */
	public static LocalDate vencimento(LocalDate primeiroVencimento, int indice) {
		YearMonth mes = YearMonth.from(primeiroVencimento).plusMonths(indice);
		return mes.atDay(Math.min(primeiroVencimento.getDayOfMonth(), mes.lengthOfMonth()));
	}

}

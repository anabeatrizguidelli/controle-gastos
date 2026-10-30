package br.com.controlegastos.model;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Exibe valores no formato R$ 0,00 (RNF2). Ex.: 1250.9 → R$ 1.250,90.
 */
public final class FormatadorMoeda {

	private static final Locale BRASIL = Locale.of("pt", "BR");

	private FormatadorMoeda() {
	}

	public static String formatar(double valor) {
		// O NumberFormat usa espaço não separável depois de "R$"; trocamos por espaço comum.
		return NumberFormat.getCurrencyInstance(BRASIL).format(valor).replace(' ', ' ');
	}

}

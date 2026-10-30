package br.com.controlegastos.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.FormatadorMoeda;
import br.com.controlegastos.service.ControleGastos;

/**
 * Formatações usadas nas páginas, acessíveis no Thymeleaf como {@code @fmt}.
 */
@Component("fmt")
public class Formatos {

	private static final Locale BRASIL = Locale.of("pt", "BR");
	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", BRASIL);
	private static final DateTimeFormatter MES_CURTO = DateTimeFormatter.ofPattern("MMM/yy", BRASIL);

	public String moeda(double valor) {
		return FormatadorMoeda.formatar(valor);
	}

	public String data(LocalDate data) {
		return data == null ? "" : data.format(DATA);
	}

	/** Ex.: "Outubro de 2026". */
	public String mes(YearMonth mes) {
		String texto = mes.format(MES);
		return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}

	/** Ex.: "out/26". */
	public String mesCurto(YearMonth mes) {
		return mes.format(MES_CURTO).replace(".", "");
	}

	/** Percentual inteiro, sem casas decimais (ex.: 87%). */
	public String pct(double valor) {
		return (int) Math.floor(valor) + "%";
	}

	/** Cor fixa da categoria (a mesma no painel e nas listas). */
	public String cor(Categoria categoria) {
		return ControleGastos.corDa(categoria);
	}

	/** Valor para preencher um campo numérico; vazio quando ainda não foi informado. */
	public String campo(double valor) {
		return valor > 0 ? String.format(Locale.ROOT, "%.2f", valor) : "";
	}

}

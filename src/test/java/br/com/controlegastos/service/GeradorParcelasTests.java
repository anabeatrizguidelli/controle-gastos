package br.com.controlegastos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.controlegastos.service.GeradorParcelas.Parcela;

/**
 * Divisão do valor e cálculo dos vencimentos das parcelas.
 */
class GeradorParcelasTests {

	@Test
	void energiaSolarEm14Parcelas() {
		List<Parcela> p = GeradorParcelas.gerar(new BigDecimal("14000.00"), 14, LocalDate.of(2027, 1, 15));
		assertThat(p).hasSize(14);
		assertThat(p).allMatch(x -> x.valor().compareTo(new BigDecimal("1000.00")) == 0);
		assertThat(p).allMatch(x -> x.vencimento().getDayOfMonth() == 15);
		assertThat(p.get(0).vencimento()).isEqualTo(LocalDate.of(2027, 1, 15));
		assertThat(p.get(11).vencimento()).isEqualTo(LocalDate.of(2027, 12, 15));
		assertThat(p.get(13).vencimento()).isEqualTo(LocalDate.of(2028, 2, 15));
		// Uma parcela por mês, em meses consecutivos e sem repetir mês.
		for (int i = 0; i < p.size(); i++) {
			assertThat(YearMonth.from(p.get(i).vencimento())).isEqualTo(YearMonth.of(2027, 1).plusMonths(i));
			assertThat(p.get(i).numero()).isEqualTo(i + 1);
		}
		assertThat(soma(p)).isEqualByComparingTo("14000.00");
	}

	@Test
	void centavosQueSobramVaoParaAsPrimeirasParcelas() {
		List<Parcela> p = GeradorParcelas.gerar(new BigDecimal("100.00"), 3, LocalDate.of(2027, 1, 10));
		assertThat(p).extracting(Parcela::valor).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"),
				new BigDecimal("33.33"));
		assertThat(soma(p)).isEqualByComparingTo("100.00");

		List<Parcela> q = GeradorParcelas.gerar(new BigDecimal("1000.00"), 7, LocalDate.of(2027, 1, 10));
		assertThat(soma(q)).isEqualByComparingTo("1000.00");
		assertThat(q.get(0).valor()).isEqualByComparingTo("142.86");
		assertThat(q.get(6).valor()).isEqualByComparingTo("142.85");

		List<Parcela> r = GeradorParcelas.gerar(BigDecimal.valueOf(0.10), 3, LocalDate.of(2027, 1, 10));
		assertThat(r).extracting(Parcela::valor).containsExactly(new BigDecimal("0.04"), new BigDecimal("0.03"),
				new BigDecimal("0.03"));
	}

	@Test
	void dia31UsaOUltimoDiaDosMesesCurtosSemAlterarOsSeguintes() {
		List<Parcela> p = GeradorParcelas.gerar(new BigDecimal("600.00"), 6, LocalDate.of(2027, 1, 31));
		assertThat(p).extracting(Parcela::vencimento).containsExactly(LocalDate.of(2027, 1, 31),
				LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 31),
				LocalDate.of(2027, 6, 30));
	}

	@Test
	void anoBissextoUsa29DeFevereiro() {
		List<Parcela> p = GeradorParcelas.gerar(new BigDecimal("300.00"), 3, LocalDate.of(2028, 1, 31));
		assertThat(p).extracting(Parcela::vencimento).containsExactly(LocalDate.of(2028, 1, 31),
				LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31));
		List<Parcela> q = GeradorParcelas.gerar(new BigDecimal("300.00"), 3, LocalDate.of(2027, 1, 30));
		assertThat(q).extracting(Parcela::vencimento).containsExactly(LocalDate.of(2027, 1, 30),
				LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 30));
	}

	@Test
	void parcelasPassamDeUmAnoParaOOutro() {
		List<Parcela> p = GeradorParcelas.gerar(new BigDecimal("400.00"), 4, LocalDate.of(2026, 11, 5));
		assertThat(p).extracting(Parcela::vencimento).containsExactly(LocalDate.of(2026, 11, 5),
				LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 5));
	}

	@Test
	void aVistaEhUmaParcelaComOValorTodo() {
		List<Parcela> p = GeradorParcelas.gerar(new BigDecimal("59.90"), 1, LocalDate.of(2027, 3, 31));
		assertThat(p).hasSize(1);
		assertThat(p.get(0).valor()).isEqualByComparingTo("59.90");
		assertThat(p.get(0).vencimento()).isEqualTo(LocalDate.of(2027, 3, 31));
	}

	@Test
	void recusaQuantidadeOuValorInvalidos() {
		LocalDate d = LocalDate.of(2027, 1, 1);
		assertThatThrownBy(() -> GeradorParcelas.gerar(new BigDecimal("100"), 0, d)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GeradorParcelas.gerar(new BigDecimal("100"), 121, d)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> GeradorParcelas.gerar(new BigDecimal("0.02"), 3, d)).isInstanceOf(IllegalArgumentException.class);
	}

	private static BigDecimal soma(List<Parcela> p) {
		return p.stream().map(Parcela::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

}

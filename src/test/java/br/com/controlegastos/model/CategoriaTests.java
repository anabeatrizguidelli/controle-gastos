package br.com.controlegastos.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class CategoriaTests {

	@Test
	void calcularNivelSegueOsLimitesDoUml() {
		Categoria lazer = new Categoria("Lazer", 100);
		assertThat(lazer.calcularNivel(79.99)).isEqualTo("OK");
		assertThat(lazer.calcularNivel(80)).isEqualTo("ATENCAO");
		assertThat(lazer.calcularNivel(100)).isEqualTo("ESTOURADO");
	}

	@Test
	void pertenceAoMesComparaMesEAno() {
		Despesa despesa = new Despesa("Pizza", 40, LocalDate.of(2026, 10, 15), new Categoria("Lazer", 100));
		assertThat(despesa.pertenceAoMes(10, 2026)).isTrue();
		assertThat(despesa.pertenceAoMes(10, 2025)).isFalse();
		assertThat(despesa.pertenceAoMes(11, 2026)).isFalse();
	}

}

package br.com.controlegastos.config;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;

/**
 * Cadastra categorias e despesas de exemplo na primeira execução (banco vazio).
 */
@Component
public class DadosIniciais implements CommandLineRunner {

	private final CategoriaRepository categorias;
	private final DespesaRepository despesas;

	public DadosIniciais(CategoriaRepository categorias, DespesaRepository despesas) {
		this.categorias = categorias;
		this.despesas = despesas;
	}

	@Override
	public void run(String... args) {
		if (categorias.count() > 0) {
			return;
		}
		Categoria alimentacao = categorias.save(new Categoria("Alimentação", 400));
		Categoria transporte = categorias.save(new Categoria("Transporte", 150));
		categorias.save(new Categoria("Lazer", 100));

		despesas.save(new Despesa("Almoço RU", 12.50, LocalDate.of(2026, 10, 28), alimentacao));
		despesas.save(new Despesa("Recarga do cartão de ônibus", 50.00, LocalDate.of(2026, 10, 27), transporte));
	}

}

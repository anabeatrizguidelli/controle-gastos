package br.com.controlegastos.config;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.FonteRepository;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.repository.UsuarioRepository;
import br.com.controlegastos.service.CadastroUsuario;

/**
 * Na primeira execução cria a conta de demonstração com alguns lançamentos.
 * Registros criados antes do login existir (sem dono) passam a ser dessa conta.
 */
@Component
public class DadosIniciais implements CommandLineRunner {

	public static final String EMAIL_DEMO = "estudante@exemplo.com";
	public static final String SENHA_DEMO = "senha123";

	private final UsuarioRepository usuarios;
	private final CategoriaRepository categorias;
	private final DespesaRepository despesas;
	private final FonteRepository fontes;
	private final ReceitaRepository receitas;
	private final CadastroUsuario cadastro;

	public DadosIniciais(UsuarioRepository usuarios, CategoriaRepository categorias, DespesaRepository despesas,
			FonteRepository fontes, ReceitaRepository receitas, CadastroUsuario cadastro) {
		this.usuarios = usuarios;
		this.categorias = categorias;
		this.despesas = despesas;
		this.fontes = fontes;
		this.receitas = receitas;
		this.cadastro = cadastro;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (usuarios.findByEmailIgnoreCase(EMAIL_DEMO).isPresent()) {
			return;
		}
		Usuario demo = cadastro.cadastrar("Estudante", EMAIL_DEMO, SENHA_DEMO);

		boolean tinhaDadosAntigos = !categorias.findByUsuarioIsNull().isEmpty();
		if (tinhaDadosAntigos) {
			// Remove as categorias padrão recém-criadas para não duplicar as antigas.
			categorias.findByUsuarioOrderByNomeAsc(demo).forEach(categorias::delete);
			categorias.findByUsuarioIsNull().forEach(c -> c.setUsuario(demo));
			despesas.findByUsuarioIsNull().forEach(d -> d.setUsuario(demo));
		} else {
			Categoria alimentacao = categoria(demo, "Alimentação");
			Categoria transporte = categoria(demo, "Transporte");
			Categoria lazer = categoria(demo, "Lazer");
			despesas.save(new Despesa("Almoço RU", 12.50, LocalDate.of(2026, 10, 28), alimentacao, demo));
			despesas.save(new Despesa("Recarga do cartão de ônibus", 50.00, LocalDate.of(2026, 10, 27), transporte, demo));
			despesas.save(new Despesa("Xerox da apostila", 8.40, LocalDate.of(2026, 10, 29), alimentacao, demo));
			despesas.save(new Despesa("Cinema com a turma", 85.00, LocalDate.of(2026, 10, 18), lazer, demo));
			despesas.save(new Despesa("Mercado do mês", 310.00, LocalDate.of(2026, 9, 6), alimentacao, demo));
			despesas.save(new Despesa("Passagens", 120.00, LocalDate.of(2026, 9, 2), transporte, demo));
		}

		Fonte mesada = fontes.findByUsuarioOrderByNomeAsc(demo).stream()
				.filter(f -> f.getNome().equals("Mesada")).findFirst().orElseThrow();
		Fonte estagio = fontes.findByUsuarioOrderByNomeAsc(demo).stream()
				.filter(f -> f.getNome().equals("Estágio")).findFirst().orElseThrow();
		receitas.save(new Receita("Mesada de outubro", 800.00, LocalDate.of(2026, 10, 1), mesada, demo));
		receitas.save(new Receita("Bolsa-auxílio do estágio", 650.00, LocalDate.of(2026, 10, 5), estagio, demo));
		receitas.save(new Receita("Mesada de setembro", 800.00, LocalDate.of(2026, 9, 1), mesada, demo));
	}

	private Categoria categoria(Usuario usuario, String nome) {
		return categorias.findByUsuarioOrderByNomeAsc(usuario).stream()
				.filter(c -> c.getNome().equals(nome)).findFirst().orElseThrow();
	}

}

package br.com.controlegastos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.config.DadosIniciais;
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

/**
 * Saldo acumulado entre meses, aba de sugestões e botão de tema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaldoESugestoesTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CadastroUsuario cadastro;

	@Autowired
	private ControleGastos controle;

	@Autowired
	private AnaliseGastos analise;

	@Autowired
	private CategoriaRepository categorias;

	@Autowired
	private FonteRepository fontes;

	@Autowired
	private DespesaRepository despesas;

	@Autowired
	private ReceitaRepository receitas;

	@Autowired
	private UsuarioRepository usuarios;

	// ---------- saldo que passa de um mês para o outro ----------

	@Test
	void sobraDeSetembroEntraNoSaldoDeOutubro() {
		Usuario u = cadastro.cadastrar("Teste", "saldo@exemplo.com", "123456");
		receitas.save(new Receita("Salário", 1000, LocalDate.of(2026, 9, 5), fonte(u), u));
		despesas.save(new Despesa("Contas", 500, LocalDate.of(2026, 9, 10), categoria(u), u));
		receitas.save(new Receita("Salário", 1000, LocalDate.of(2026, 10, 5), fonte(u), u));
		despesas.save(new Despesa("Contas", 300, LocalDate.of(2026, 10, 10), categoria(u), u));

		assertThat(controle.saldoAnterior(u, 9, 2026)).isEqualTo(0);
		assertThat(controle.saldoDisponivel(u, 9, 2026)).isEqualTo(500);
		// Outubro começa com os R$ 500 que sobraram de setembro.
		assertThat(controle.saldoAnterior(u, 10, 2026)).isEqualTo(500);
		assertThat(controle.calcularSaldo(u, 10, 2026)).isEqualTo(700);
		assertThat(controle.saldoDisponivel(u, 10, 2026)).isEqualTo(1200);
		// O saldo continua acumulando nos meses seguintes, mesmo sem lançamentos.
		assertThat(controle.saldoAnterior(u, 12, 2026)).isEqualTo(1200);
	}

	@Test
	void mesNegativoReduzOSaldoDoProximo() {
		Usuario u = cadastro.cadastrar("Teste", "negativo@exemplo.com", "123456");
		receitas.save(new Receita("Mesada", 300, LocalDate.of(2026, 8, 1), fonte(u), u));
		despesas.save(new Despesa("Viagem", 450, LocalDate.of(2026, 8, 20), categoria(u), u));
		assertThat(controle.saldoAnterior(u, 9, 2026)).isEqualTo(-150);
	}

	@Test
	void painelMostraSaldoAnteriorEDisponivel() throws Exception {
		// Demo: setembro 800 − 430 = 370; outubro 1.450 − 155,90 = 1.294,10; disponível 1.664,10.
		mvc.perform(get("/painel").param("mes", "2026-10").with(user(DadosIniciais.EMAIL_DEMO)))
				.andExpect(content().string(containsString("Saldo do mês anterior")))
				.andExpect(content().string(containsString("R$ 370,00")))
				.andExpect(content().string(containsString("R$ 1.664,10")))
				.andExpect(content().string(containsString("R$ 370,00 + R$ 1.294,10 = R$ 1.664,10")));
	}

	// ---------- sugestões ----------

	@Test
	void sugestoesApontamMaiorGastoLimitesEComparacao() {
		Usuario demo = usuarios.findByEmailIgnoreCase(DadosIniciais.EMAIL_DEMO).orElseThrow();
		AnaliseGastos.Analise a = analise.analisar(demo, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 20));
		List<String> titulos = a.sugestoes().stream().map(Sugestao::titulo).toList();

		assertThat(a.maiorCategoria()).isEqualTo("Lazer");
		assertThat(titulos).contains("Seu maior gasto é com Lazer", "Lazer está perto do limite",
				"Você guardou 89% do que recebeu", "Você gastou menos com Alimentação", "Previsão para o fim do mês");
		// Lazer: R$ 15 restantes em 12 dias = R$ 1,25 por dia.
		assertThat(a.sugestoes()).anyMatch(s -> s.texto().contains("R$ 1,25 por dia nos próximos 12 dias"));
		// Alertas aparecem antes das dicas.
		assertThat(a.sugestoes().get(0).tipo()).isIn("ALERTA", "ATENCAO");
		assertThat(a.ranking().get(0).variacao()).isNull(); // Lazer não teve gasto em setembro
	}

	@Test
	void sugestoesAvisamQuandoGastaMaisDoQueRecebe() {
		Usuario u = cadastro.cadastrar("Teste", "gastao@exemplo.com", "123456");
		receitas.save(new Receita("Mesada", 200, LocalDate.of(2026, 7, 1), fonte(u), u));
		for (int dia = 1; dia <= 4; dia++) {
			despesas.save(new Despesa("Lanche", 15, LocalDate.of(2026, 7, dia), categoria(u), u));
		}
		despesas.save(new Despesa("Show", 400, LocalDate.of(2026, 7, 10), categoria(u), u));
		List<String> titulos = analise.analisar(u, YearMonth.of(2026, 7), LocalDate.of(2026, 10, 1))
				.sugestoes().stream().map(Sugestao::titulo).toList();
		assertThat(titulos).contains("Você gastou mais do que recebeu", "\"Lanche\" aparece 4 vezes",
				"Maior despesa do mês: Show");
	}

	@Test
	void abaDeSugestoesAbre() throws Exception {
		mvc.perform(get("/sugestoes").param("mes", "2026-10").with(user(DadosIniciais.EMAIL_DEMO)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Seu maior gasto é com Lazer")))
				.andExpect(content().string(containsString("Onde vai o seu dinheiro")));
		mvc.perform(get("/sugestoes").param("mes", "2026-01").with(user(DadosIniciais.EMAIL_DEMO)))
				.andExpect(content().string(containsString("Nenhuma despesa em janeiro")));
	}

	// ---------- modo escuro / claro ----------

	@Test
	void todasAsTelasTemOBotaoDeTema() throws Exception {
		mvc.perform(get("/login")).andExpect(content().string(containsString("data-tema")))
				.andExpect(content().string(containsString("localStorage.getItem(\"tema\")")));
		mvc.perform(get("/painel").with(user(DadosIniciais.EMAIL_DEMO)))
				.andExpect(content().string(containsString("Modo claro")))
				.andExpect(content().string(containsString("Modo escuro")));
	}

	private Categoria categoria(Usuario u) {
		return categorias.findByUsuarioOrderByNomeAsc(u).get(0);
	}

	private Fonte fonte(Usuario u) {
		return fontes.findByUsuarioOrderByNomeAsc(u).get(0);
	}

}

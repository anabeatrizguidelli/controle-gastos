package br.com.controlegastos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.RecorrenciaReceita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.FonteRepository;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.repository.RecorrenciaReceitaRepository;
import br.com.controlegastos.service.CadastroUsuario;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.LancamentosFixos;

/**
 * Receitas fixas (mensais), com as mesmas regras das despesas fixas. As datas
 * ficam em 2030 e o "mês atual" é informado, para não depender do dia de hoje.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReceitasFixasTests {

	private static final String EMAIL = "receitas-fixas@exemplo.com";
	private static final RequestPostProcessor LOGADO = user(EMAIL);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CadastroUsuario cadastroUsuario;

	@Autowired
	private FonteRepository fontes;

	@Autowired
	private CategoriaRepository categorias;

	@Autowired
	private ReceitaRepository receitas;

	@Autowired
	private DespesaRepository despesas;

	@Autowired
	private RecorrenciaReceitaRepository recorrencias;

	@Autowired
	private LancamentosFixos fixos;

	@Autowired
	private ControleGastos controle;

	private Usuario usuario;
	private Fonte fonte;

	@BeforeEach
	void criarUsuario() {
		usuario = cadastroUsuario.cadastrar("Receitas", EMAIL, "123456");
		fonte = fontes.findByUsuarioOrderByNomeAsc(usuario).get(0);
	}

	@Test
	void cadastroDeReceitaFixaGravaOPrimeiroMes() throws Exception {
		criarEstagio("2030-01-05");
		assertThat(estagio()).hasSize(1);
		assertThat(estagio().get(0).isFixa()).isTrue();
		RecorrenciaReceita r = recorrencias.findAll().get(0);
		assertThat(r.getValor()).isEqualTo(300.0);
		assertThat(r.isAtiva()).isTrue();
	}

	@Test
	void geraUmLancamentoPorMesSemDuplicar() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		fixos.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		fixos.lancarAte(usuario, YearMonth.of(2030, 2), YearMonth.of(2030, 4));
		assertThat(estagio()).extracting(Receita::getData).containsExactly(LocalDate.of(2030, 1, 5),
				LocalDate.of(2030, 2, 5), LocalDate.of(2030, 3, 5), LocalDate.of(2030, 4, 5));
		assertThat(estagio()).allMatch(r -> r.isFixa() && r.getValor() == 300.0);
	}

	@Test
	void naoGeraMaisDeDozeMesesAFrente() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2040, 1), YearMonth.of(2030, 1));
		assertThat(estagio()).hasSize(1 + LancamentosFixos.MESES_A_FRENTE);
	}

	@Test
	void diaQueNaoExisteNoMesUsaOUltimoDia() throws Exception {
		criarEstagio("2030-01-31");
		fixos.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		assertThat(estagio()).extracting(Receita::getData).containsExactly(LocalDate.of(2030, 1, 31),
				LocalDate.of(2030, 2, 28), LocalDate.of(2030, 3, 31), LocalDate.of(2030, 4, 30));
	}

	@Test
	void editarSoEsteMesNaoMudaOsOutros() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2030, 3), YearMonth.of(2030, 3));
		editar(mes(2), "350", false);
		fixos.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		assertThat(estagio()).extracting(Receita::getValor).containsExactly(300.0, 350.0, 300.0, 300.0);
	}

	@Test
	void editarEsteEOsSeguintesNaoMudaOsAnteriores() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2030, 3), YearMonth.of(2030, 3));
		editar(mes(2), "350", true);
		fixos.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		assertThat(estagio()).extracting(Receita::getValor).containsExactly(300.0, 350.0, 350.0, 350.0);
		assertThat(recorrencias.findAll().get(0).getValor()).isEqualTo(350.0);
	}

	@Test
	void excluirSoUmMesNaoORecria() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2030, 3), YearMonth.of(2030, 3));
		mvc.perform(post("/receitas/{id}/excluir", mes(2).getId()).with(LOGADO).with(csrf()).param("modo", "este"))
				.andExpect(redirectedUrl("/receitas?mes=2030-02"));
		fixos.lancarAte(usuario, YearMonth.of(2030, 5), YearMonth.of(2030, 5));
		assertThat(estagio()).extracting(r -> r.getData().getMonthValue()).containsExactly(1, 3, 4, 5);
		assertThat(recorrencias.findAll().get(0).isAtiva()).isTrue();
	}

	@Test
	void excluirEsteEOsSeguintesEncerraEMantemOHistorico() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2030, 5), YearMonth.of(2030, 5));
		mvc.perform(post("/receitas/{id}/excluir", mes(3).getId()).with(LOGADO).with(csrf()).param("modo", "seguintes"))
				.andExpect(redirectedUrl("/receitas?mes=2030-03"));
		fixos.lancarAte(usuario, YearMonth.of(2030, 12), YearMonth.of(2030, 12));
		assertThat(estagio()).extracting(r -> r.getData().getMonthValue()).containsExactly(1, 2);
		RecorrenciaReceita r = recorrencias.findAll().get(0);
		assertThat(r.isAtiva()).isFalse();
		assertThat(r.getFim()).isEqualTo(LocalDate.of(2030, 2, 1));
	}

	@Test
	void excluirTudoExigeConfirmacaoEApagaTodosOsMeses() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		receitas.save(new Receita("Bico avulso", 80, LocalDate.of(2030, 2, 10), fonte, usuario));
		// Sem a confirmação extra, nada é apagado.
		mvc.perform(post("/receitas/{id}/excluir", mes(3).getId()).with(LOGADO).with(csrf()).param("modo", "tudo"))
				.andExpect(flash().attributeExists("erro"));
		assertThat(estagio()).hasSize(4);
		mvc.perform(post("/receitas/{id}/excluir", mes(3).getId()).with(LOGADO).with(csrf()).param("modo", "tudo")
				.param("confirmarTudo", "true"))
				.andExpect(flash().attributeExists("sucesso"));
		fixos.lancarAte(usuario, YearMonth.of(2030, 12), YearMonth.of(2030, 12));
		assertThat(estagio()).isEmpty();
		assertThat(recorrencias.findAll()).isEmpty();
		// A receita avulsa continua.
		assertThat(receitas.findByUsuarioOrderByDataDescIdDesc(usuario)).extracting(Receita::getDescricao)
				.containsExactly("Bico avulso");
	}

	@Test
	void entraNosTotaisENoSaldoProjetado() throws Exception {
		criarEstagio("2030-01-05");
		fixos.lancarAte(usuario, YearMonth.of(2030, 3), YearMonth.of(2030, 3));
		Categoria categoria = categorias.findByUsuarioOrderByNomeAsc(usuario).get(0);
		Despesa transporte = new Despesa("Transporte", 100, LocalDate.of(2030, 3, 10), categoria, usuario);
		transporte.setVencimento(LocalDate.of(2030, 3, 10));
		transporte.setPago(false);
		despesas.save(transporte);
		assertThat(controle.totaisDoMes(usuario, YearMonth.of(2030, 3)).receitas()).isEqualTo(300.0);
		assertThat(controle.totaisDoMes(usuario, YearMonth.of(2030, 3)).saldoProjetado()).isEqualTo(200.0);
		// Saldo anterior de março: janeiro e fevereiro (sem despesas pagas).
		assertThat(controle.saldoAnterior(usuario, 3, 2030)).isEqualTo(600.0);
	}

	@Test
	void listaEdicaoEPainelMostramOSelo() throws Exception {
		criarEstagio("2030-01-05");
		mvc.perform(get("/receitas").with(LOGADO).param("mes", "2030-01"))
				.andExpect(content().string(containsString("class=\"selo-fixa\"")))
				.andExpect(content().string(containsString("data-excluir-fixa=")))
				.andExpect(content().string(containsString("Excluir tudo")));
		mvc.perform(get("/receitas/{id}/editar", mes(1).getId()).with(LOGADO))
				.andExpect(content().string(containsString("Alterar os meses seguintes?")))
				.andExpect(content().string(containsString("Entendo que todos os meses")));
		mvc.perform(get("/painel").with(LOGADO).param("mes", "2030-01"))
				.andExpect(content().string(containsString("class=\"selo-fixa\"")));
		mvc.perform(get("/receitas/nova").with(LOGADO))
				.andExpect(content().string(containsString("Receita fixa (repete todo mês)")));
	}

	@Test
	void fonteComReceitaFixaNaoPodeSerExcluida() throws Exception {
		criarEstagio("2030-01-05");
		mvc.perform(post("/receitas/{id}/excluir", mes(1).getId()).with(LOGADO).with(csrf()));
		mvc.perform(post("/fontes/{id}/excluir", fonte.getId()).with(LOGADO).with(csrf()));
		assertThat(fontes.findById(fonte.getId())).isPresent();
	}

	private void criarEstagio(String data) throws Exception {
		mvc.perform(post("/receitas").with(LOGADO).with(csrf()).param("descricao", "Estágio (demonstração)")
				.param("valor", "300").param("fixa", "true").param("data", data).param("fonte", fonte.getId().toString()))
				.andExpect(redirectedUrl("/receitas?mes=" + data.substring(0, 7)));
	}

	private void editar(Receita r, String valor, boolean seguintes) throws Exception {
		mvc.perform(post("/receitas/{id}", r.getId()).with(LOGADO).with(csrf()).param("descricao", r.getDescricao())
				.param("valor", valor).param("data", r.getData().toString()).param("fonte", fonte.getId().toString())
				.param("aplicarSeguintes", String.valueOf(seguintes)))
				.andExpect(redirectedUrl("/receitas?mes=" + YearMonth.from(r.getData())));
	}

	/** Lançamentos da receita fixa, do mais antigo para o mais recente. */
	private List<Receita> estagio() {
		return receitas.findByUsuarioOrderByDataDescIdDesc(usuario).stream().filter(Receita::isFixa)
				.sorted(Comparator.comparing(Receita::getData)).toList();
	}

	private Receita mes(int mes) {
		return estagio().stream().filter(r -> r.getData().getMonthValue() == mes && r.getData().getYear() == 2030)
				.findFirst().orElseThrow();
	}

}
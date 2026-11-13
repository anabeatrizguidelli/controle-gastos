package br.com.controlegastos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
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
import br.com.controlegastos.model.Recorrencia;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.RecorrenciaRepository;
import br.com.controlegastos.service.CadastroUsuario;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.LancamentosFixos;

/**
 * Despesas fixas (mensais): cada mês vira um lançamento próprio, sem duplicar,
 * sem recriar o que foi excluído e mantendo o histórico ao encerrar. As datas
 * ficam em 2030 e o "mês atual" é informado, para os testes não dependerem do dia de hoje.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DespesasFixasTests {

	private static final String EMAIL = "fixas@exemplo.com";
	private static final RequestPostProcessor LOGADO = user(EMAIL);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CadastroUsuario cadastroUsuario;

	@Autowired
	private CategoriaRepository categorias;

	@Autowired
	private DespesaRepository despesas;

	@Autowired
	private RecorrenciaRepository recorrencias;

	@Autowired
	private LancamentosFixos fixas;

	@Autowired
	private ControleGastos controle;

	private Usuario usuario;
	private Categoria categoria;

	@BeforeEach
	void criarUsuario() {
		usuario = cadastroUsuario.cadastrar("Fixas", EMAIL, "123456");
		categoria = categorias.findByUsuarioOrderByNomeAsc(usuario).get(0);
	}

	@Test
	void cadastroDeDespesaFixaGravaOPrimeiroMesPendente() throws Exception {
		criarInternet("2030-01-10");
		List<Despesa> lista = internet();
		assertThat(lista).hasSize(1);
		Despesa jan = lista.get(0);
		assertThat(jan.isFixa()).isTrue();
		assertThat(jan.isParcelada()).isFalse();
		assertThat(jan.isPago()).isFalse();
		assertThat(jan.getVencimento()).isEqualTo(LocalDate.of(2030, 1, 10));
		Recorrencia r = recorrencias.findAll().get(0);
		assertThat(r.getValor()).isEqualTo(99.90);
		assertThat(r.isAtiva()).isTrue();
	}

	@Test
	void geraUmLancamentoPorMesAteOMesAtualSemDuplicar() throws Exception {
		criarInternet("2030-01-10");
		fixas.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		fixas.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		fixas.lancarAte(usuario, YearMonth.of(2030, 2), YearMonth.of(2030, 4));
		List<Despesa> lista = internet();
		assertThat(lista).extracting(Despesa::getVencimento).containsExactly(LocalDate.of(2030, 1, 10),
				LocalDate.of(2030, 2, 10), LocalDate.of(2030, 3, 10), LocalDate.of(2030, 4, 10));
		assertThat(lista).allMatch(d -> d.isFixa() && !d.isPago() && d.getValor() == 99.90);
		// Novos lançamentos nascem pendentes e contam no total (e no limite) da categoria.
		assertThat(controle.totalPorCategoria(usuario, categoria, 3, 2030)).isEqualTo(99.90);
	}

	@Test
	void naoAlteraLancamentosJaRegistrados() throws Exception {
		criarInternet("2030-01-10");
		fixas.lancarAte(usuario, YearMonth.of(2030, 2), YearMonth.of(2030, 2));
		Despesa fev = mes(2);
		mvc.perform(post("/despesas/{id}/pagamento", fev.getId()).with(LOGADO).with(csrf()).param("pago", "true"));
		mvc.perform(post("/despesas/{id}", fev.getId()).with(LOGADO).with(csrf()).param("descricao", "Internet fevereiro")
				.param("valor", "80").param("data", "2030-02-10").param("vencimento", "2030-02-12")
				.param("categoria", categoria.getId().toString()));
		fixas.lancarAte(usuario, YearMonth.of(2030, 3), YearMonth.of(2030, 3));
		fev = mes(2);
		assertThat(fev.getDescricao()).isEqualTo("Internet fevereiro");
		assertThat(fev.getValor()).isEqualTo(80);
		assertThat(fev.getVencimento()).isEqualTo(LocalDate.of(2030, 2, 12));
		assertThat(fev.isPago()).isTrue();
		// Sem "aplicar aos próximos", o mês seguinte continua com o valor da recorrência.
		assertThat(mes(3).getValor()).isEqualTo(99.90);
		assertThat(mes(3).isPago()).isFalse();
		assertThat(despesas.listarTodas(usuario)).hasSize(3);
	}

	@Test
	void excluirSoUmMesNaoORecria() throws Exception {
		criarInternet("2030-01-10");
		fixas.lancarAte(usuario, YearMonth.of(2030, 3), YearMonth.of(2030, 3));
		mvc.perform(post("/despesas/{id}/excluir", mes(2).getId()).with(LOGADO).with(csrf()))
				.andExpect(redirectedUrl("/despesas?mes=2030-02"));
		fixas.lancarAte(usuario, YearMonth.of(2030, 3), YearMonth.of(2030, 3));
		fixas.lancarAte(usuario, YearMonth.of(2030, 5), YearMonth.of(2030, 5));
		assertThat(internet()).extracting(d -> d.getVencimento().getMonthValue()).containsExactly(1, 3, 4, 5);
		assertThat(recorrencias.findAll().get(0).isAtiva()).isTrue();
	}

	@Test
	void encerrarMantemOHistoricoEParaDeGerar() throws Exception {
		criarInternet("2030-01-10");
		fixas.lancarAte(usuario, YearMonth.of(2030, 5), YearMonth.of(2030, 5));
		Despesa fev = mes(2);
		mvc.perform(post("/despesas/{id}/pagamento", fev.getId()).with(LOGADO).with(csrf()).param("pago", "true"));
		Despesa mai = mes(5);
		mvc.perform(post("/despesas/{id}/pagamento", mai.getId()).with(LOGADO).with(csrf()).param("pago", "true"));

		// Encerrar a partir de março: jan e fev ficam; mar e abr (pendentes) saem; mai, já pago, fica.
		mvc.perform(post("/despesas/{id}/excluir", mes(3).getId()).with(LOGADO).with(csrf()).param("encerrar", "true"))
				.andExpect(redirectedUrl("/despesas?mes=2030-03"));
		fixas.lancarAte(usuario, YearMonth.of(2030, 12), YearMonth.of(2030, 12));
		assertThat(internet()).extracting(d -> d.getVencimento().getMonthValue()).containsExactly(1, 2, 5);
		assertThat(mes(2).isPago()).isTrue();
		Recorrencia r = recorrencias.findAll().get(0);
		assertThat(r.isAtiva()).isFalse();
		assertThat(r.getFim()).isEqualTo(LocalDate.of(2030, 2, 1));
	}

	@Test
	void encerrarNoPrimeiroMesRemoveARecorrencia() throws Exception {
		criarInternet("2030-01-10");
		mvc.perform(post("/despesas/{id}/excluir", mes(1).getId()).with(LOGADO).with(csrf()).param("encerrar", "true"));
		fixas.lancarAte(usuario, YearMonth.of(2030, 6), YearMonth.of(2030, 6));
		assertThat(internet()).isEmpty();
		assertThat(recorrencias.findAll()).isEmpty();
	}

	@Test
	void diaQueNaoExisteNoMesUsaOUltimoDia() throws Exception {
		criarInternet("2030-01-31");
		fixas.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		assertThat(internet()).extracting(Despesa::getVencimento).containsExactly(LocalDate.of(2030, 1, 31),
				LocalDate.of(2030, 2, 28), LocalDate.of(2030, 3, 31), LocalDate.of(2030, 4, 30));
	}

	@Test
	void naoGeraMesesFuturosSemLimite() throws Exception {
		criarInternet("2030-01-10");
		fixas.lancarAte(usuario, YearMonth.of(2040, 1), YearMonth.of(2030, 1));
		assertThat(internet()).hasSize(1 + LancamentosFixos.MESES_A_FRENTE);
		assertThat(internet().get(internet().size() - 1).getVencimento()).isEqualTo(LocalDate.of(2031, 1, 10));
	}

	@Test
	void aplicarNovoValorAosProximosMesesNaoMudaOsAnteriores() throws Exception {
		criarInternet("2030-01-10");
		fixas.lancarAte(usuario, YearMonth.of(2030, 4), YearMonth.of(2030, 4));
		Despesa abr = mes(4);
		mvc.perform(post("/despesas/{id}/pagamento", abr.getId()).with(LOGADO).with(csrf()).param("pago", "true"));
		mvc.perform(post("/despesas/{id}", mes(2).getId()).with(LOGADO).with(csrf()).param("descricao", "Internet (demonstração)")
				.param("valor", "120").param("data", "2030-02-10").param("vencimento", "2030-02-10")
				.param("categoria", categoria.getId().toString()).param("aplicarDemaisPendentes", "true"))
				.andExpect(redirectedUrl("/despesas?mes=2030-02"));
		fixas.lancarAte(usuario, YearMonth.of(2030, 5), YearMonth.of(2030, 5));
		assertThat(internet()).extracting(Despesa::getValor).containsExactly(99.90, 120.0, 120.0, 99.90, 120.0);
	}

	@Test
	void despesaFixaNaoPodeSerParcelada() throws Exception {
		mvc.perform(post("/despesas").with(LOGADO).with(csrf()).param("descricao", "Academia").param("valor", "300")
				.param("quantidadeParcelas", "3").param("fixa", "true").param("data", "2030-01-05")
				.param("vencimento", "2030-01-05").param("categoria", categoria.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Uma despesa fixa não pode ser parcelada")));
		assertThat(despesas.listarTodas(usuario)).isEmpty();
		assertThat(recorrencias.findAll()).isEmpty();
	}

	@Test
	void listaEEdicaoMostramQueADespesaEFixa() throws Exception {
		criarInternet("2030-01-10");
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2030-01"))
				.andExpect(content().string(containsString("class=\"selo-fixa\"")))
				.andExpect(content().string(containsString("A despesa fixa continua nos próximos meses")));
		mvc.perform(get("/despesas/{id}/editar", mes(1).getId()).with(LOGADO))
				.andExpect(content().string(containsString("Encerrar despesa fixa")))
				.andExpect(content().string(containsString("Alterar os próximos meses?")));
		mvc.perform(get("/despesas/nova").with(LOGADO))
				.andExpect(content().string(containsString("Despesa fixa (repete todo mês)")));
	}

	@Test
	void categoriaComDespesaFixaNaoPodeSerExcluida() throws Exception {
		criarInternet("2030-01-10");
		mvc.perform(post("/despesas/{id}/excluir", mes(1).getId()).with(LOGADO).with(csrf()));
		mvc.perform(post("/categorias/{id}/excluir", categoria.getId()).with(LOGADO).with(csrf()));
		assertThat(categorias.findById(categoria.getId())).isPresent();
	}

	private void criarInternet(String vencimento) throws Exception {
		mvc.perform(post("/despesas").with(LOGADO).with(csrf()).param("descricao", "Internet (demonstração)")
				.param("valor", "99.90").param("fixa", "true").param("data", vencimento).param("vencimento", vencimento)
				.param("categoria", categoria.getId().toString()))
				.andExpect(redirectedUrl("/despesas?mes=" + vencimento.substring(0, 7)));
	}

	/** Lançamentos da despesa fixa, do mais antigo para o mais recente. */
	private List<Despesa> internet() {
		return despesas.listarTodas(usuario).stream().filter(Despesa::isFixa).sorted(
				(a, b) -> a.getVencimento().compareTo(b.getVencimento())).toList();
	}

	private Despesa mes(int mes) {
		return internet().stream().filter(d -> d.getVencimento().getMonthValue() == mes
				&& d.getVencimento().getYear() == 2030).findFirst().orElseThrow();
	}

}
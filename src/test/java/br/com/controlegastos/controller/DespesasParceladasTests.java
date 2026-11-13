package br.com.controlegastos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.FonteRepository;
import br.com.controlegastos.repository.ParcelamentoRepository;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.service.CadastroDespesas;
import br.com.controlegastos.service.CadastroUsuario;
import br.com.controlegastos.service.ControleGastos;
import jakarta.persistence.EntityManager;

/**
 * Despesas parceladas, vencimentos, checkbox de pagamento e saldo, testados
 * pelas páginas (como o navegador usa) e pelo banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DespesasParceladasTests {

	private static final String EMAIL = "parcelas@exemplo.com";
	private static final RequestPostProcessor LOGADO = user(EMAIL);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CadastroUsuario cadastroUsuario;

	@Autowired
	private CategoriaRepository categorias;

	@Autowired
	private FonteRepository fontes;

	@Autowired
	private DespesaRepository despesas;

	@Autowired
	private ParcelamentoRepository parcelamentos;

	@Autowired
	private ReceitaRepository receitas;

	@Autowired
	private ControleGastos controle;

	@Autowired
	private CadastroDespesas cadastro;

	@Autowired
	private EntityManager em;

	private Usuario usuario;
	private String categoria;

	@BeforeEach
	void criarUsuario() {
		usuario = cadastroUsuario.cadastrar("Parcelas", EMAIL, "123456");
		categoria = categorias.findByUsuarioOrderByNomeAsc(usuario).get(0).getId().toString();
	}

	// 1. despesa à vista
	@Test
	void despesaAVistaGravaUmLancamentoPendente() throws Exception {
		mvc.perform(post("/despesas").with(LOGADO).with(csrf()).param("descricao", "Mercado").param("valor", "250.40")
				.param("quantidadeParcelas", "1").param("data", "2027-03-02").param("vencimento", "2027-03-10")
				.param("categoria", categoria))
				.andExpect(redirectedUrl("/despesas?mes=2027-03"));
		List<Despesa> lista = despesas.listarTodas(usuario);
		assertThat(lista).hasSize(1);
		Despesa d = lista.get(0);
		assertThat(d.getValor()).isEqualTo(250.40);
		assertThat(d.getVencimento()).isEqualTo(LocalDate.of(2027, 3, 10));
		assertThat(d.isPago()).isFalse();
		assertThat(d.getParcelamento()).isNull();
		assertThat(d.getRotuloParcela()).isEqualTo("À vista");
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2027-03"))
				.andExpect(content().string(containsString("Mercado")))
				.andExpect(content().string(containsString("À vista")))
				.andExpect(content().string(containsString("10/03/2027")))
				.andExpect(content().string(containsString("R$ 250,40")))
				.andExpect(content().string(containsString("Pendente")));
	}

	// 2, 3 e 4. parcelada em 14 vezes, uma parcela por mês, cada uma no seu mês
	@Test
	void energiaSolarEm14ParcelasUmaPorMes() throws Exception {
		criarEnergiaSolar();
		List<Despesa> parcelas = parcelasDaEnergiaSolar();
		assertThat(parcelas).hasSize(14);
		assertThat(parcelamentos.findAll()).hasSize(1);
		assertThat(parcelamentos.findAll().get(0).getValorTotal()).isEqualTo(14000.0);
		assertThat(parcelas).allMatch(p -> p.getParcelamento() != null
				&& p.getParcelamento().getId().equals(parcelas.get(0).getParcelamento().getId()));
		BigDecimal soma = BigDecimal.ZERO;
		for (int i = 0; i < 14; i++) {
			Despesa p = parcelas.get(i);
			assertThat(p.getNumeroParcela()).isEqualTo(i + 1);
			assertThat(p.getTotalParcelas()).isEqualTo(14);
			assertThat(p.getValor()).isEqualTo(1000.0);
			assertThat(p.getVencimento()).isEqualTo(LocalDate.of(2027, 1, 15).plusMonths(i));
			assertThat(p.isPago()).isFalse();
			soma = soma.add(BigDecimal.valueOf(p.getValor()));
		}
		assertThat(soma).isEqualByComparingTo("14000");

		// Cada mês mostra só a sua parcela.
		for (int i = 0; i < 14; i++) {
			YearMonth mes = YearMonth.of(2027, 1).plusMonths(i);
			assertThat(controle.listarDespesas(usuario, mes)).hasSize(1);
			assertThat(controle.totalDespesasMes(usuario, mes.getMonthValue(), mes.getYear())).isEqualTo(1000.0);
		}
		assertThat(controle.listarDespesas(usuario, YearMonth.of(2026, 12))).isEmpty();
		assertThat(controle.listarDespesas(usuario, YearMonth.of(2028, 3))).isEmpty();

		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2027-01"))
				.andExpect(content().string(containsString("Energia solar")))
				.andExpect(content().string(containsString("Parcela 1/14")))
				.andExpect(content().string(containsString("15/01/2027")))
				.andExpect(content().string(containsString("R$ 1.000,00")));
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2027-12"))
				.andExpect(content().string(containsString("Parcela 12/14")))
				.andExpect(content().string(containsString("15/12/2027")));
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2028-02"))
				.andExpect(content().string(containsString("Parcela 14/14")))
				.andExpect(content().string(not(containsString("Parcela 13/14"))));
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2028-03"))
				.andExpect(content().string(not(containsString("Energia solar"))));
	}

	// 5. meses com menos dias
	@Test
	void vencimentoDia31EmMesesCurtos() throws Exception {
		mvc.perform(post("/despesas").with(LOGADO).with(csrf()).param("descricao", "Curso").param("valor", "100")
				.param("quantidadeParcelas", "3").param("data", "2028-01-20").param("vencimento", "2028-01-31")
				.param("categoria", categoria));
		List<LocalDate> vencimentos = despesas.listarTodas(usuario).stream().map(Despesa::getVencimento).sorted().toList();
		assertThat(vencimentos).containsExactly(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29),
				LocalDate.of(2028, 3, 31));
		List<Double> valores = despesas.listarTodas(usuario).stream()
				.sorted((a, b) -> a.getNumeroParcela() - b.getNumeroParcela()).map(Despesa::getValor).toList();
		assertThat(valores).containsExactly(33.34, 33.33, 33.33);
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2028-02"))
				.andExpect(content().string(containsString("29/02/2028")));
	}

	// 6, 8 e 9. marcar / desmarcar, só a parcela escolhida, salvo no banco
	@Test
	void marcarEDesmarcarPagamentoDeUmaParcela() throws Exception {
		criarEnergiaSolar();
		Despesa terceira = parcelasDaEnergiaSolar().get(2);

		mvc.perform(post("/despesas/{id}/pagamento", terceira.getId()).with(LOGADO).with(csrf())
				.param("pago", "true").param("mes", "2027-03"))
				.andExpect(redirectedUrl("/despesas?mes=2027-03#despesa-" + terceira.getId()));
		em.flush();
		em.clear();
		List<Despesa> depois = parcelasDaEnergiaSolar();
		assertThat(depois.get(2).isPago()).isTrue();
		assertThat(depois.stream().filter(Despesa::isPago)).hasSize(1);
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2027-03"))
				.andExpect(content().string(containsString("checked")))
				.andExpect(content().string(containsString(">Pago<")));
		assertThat(controle.totalDespesasPagasMes(usuario, 3, 2027)).isEqualTo(1000.0);
		assertThat(controle.totalDespesasPendentesMes(usuario, 3, 2027)).isEqualTo(0.0);
		assertThat(controle.totalDespesasPendentesMes(usuario, 4, 2027)).isEqualTo(1000.0);

		// Checkbox desmarcado não envia "pago": volta a Pendente.
		mvc.perform(post("/despesas/{id}/pagamento", terceira.getId()).with(LOGADO).with(csrf()).param("mes", "2027-03"));
		em.flush();
		em.clear();
		assertThat(parcelasDaEnergiaSolar()).noneMatch(Despesa::isPago);
	}

	// 7 e 8. saldo disponível = receitas − despesas pagas (exemplo do enunciado)
	@Test
	void saldoDisponivelDescontaSoAsDespesasPagas() throws Exception {
		Fonte fonte = fontes.findByUsuarioOrderByNomeAsc(usuario).get(0);
		receitas.save(new Receita("Salário", 3000, LocalDate.of(2030, 5, 5), fonte, usuario));
		Despesa aluguel = criarAVista("Aluguel", "900", "2030-05-10");
		criarAVista("Internet", "600", "2030-05-20");

		// Antes de pagar: nada sai do saldo.
		assertThat(controle.saldoDisponivel(usuario, 5, 2030)).isEqualTo(3000.0);
		marcar(aluguel, true);
		marcar(aluguel, true); // marcar de novo não desconta duas vezes

		assertThat(controle.totalReceitasMes(usuario, 5, 2030)).isEqualTo(3000.0);
		assertThat(controle.totalDespesasMes(usuario, 5, 2030)).isEqualTo(1500.0);
		assertThat(controle.totalDespesasPagasMes(usuario, 5, 2030)).isEqualTo(900.0);
		assertThat(controle.totalDespesasPendentesMes(usuario, 5, 2030)).isEqualTo(600.0);
		assertThat(controle.saldoDisponivel(usuario, 5, 2030)).isEqualTo(2100.0);

		mvc.perform(get("/painel").with(LOGADO).param("mes", "2030-05"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("R$ 3.000,00")))
				.andExpect(content().string(containsString("R$ 1.500,00")))
				.andExpect(content().string(containsString("R$ 900,00")))
				.andExpect(content().string(containsString("R$ 600,00")))
				.andExpect(content().string(containsString("R$ 2.100,00")))
				.andExpect(content().string(containsString("Despesas pendentes")));

		// Desmarcar devolve o valor ao saldo.
		marcar(aluguel, false);
		assertThat(controle.saldoDisponivel(usuario, 5, 2030)).isEqualTo(3000.0);
		// Meses diferentes não se misturam: junho não tem despesas e herda só o que foi pago.
		marcar(aluguel, true);
		assertThat(controle.totalDespesasMes(usuario, 6, 2030)).isEqualTo(0.0);
		assertThat(controle.saldoAnterior(usuario, 6, 2030)).isEqualTo(2100.0);
	}

	// 10. sem duplicar ao reenviar o formulário ou recarregar
	@Test
	void reenviarOMesmoFormularioNaoDuplicaParcelas() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		String chave = (String) mvc.perform(get("/despesas/nova").with(LOGADO).session(sessao))
				.andReturn().getModelAndView().getModel().get("chaveFormulario");
		assertThat(chave).isNotBlank();
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/despesas").with(LOGADO).with(csrf()).session(sessao).param("chaveFormulario", chave)
					.param("descricao", "Notebook").param("valor", "3000").param("quantidadeParcelas", "10")
					.param("data", "2027-01-05").param("vencimento", "2027-02-05").param("categoria", categoria));
		}
		assertThat(despesas.listarTodas(usuario)).hasSize(10);
		for (int i = 0; i < 3; i++) {
			mvc.perform(get("/despesas").with(LOGADO).param("mes", "2027-02")).andExpect(status().isOk());
			mvc.perform(get("/painel").with(LOGADO).param("mes", "2027-02")).andExpect(status().isOk());
		}
		assertThat(despesas.listarTodas(usuario)).hasSize(10);
		assertThat(parcelamentos.findAll()).hasSize(1);
	}

	// 11. registros antigos (sem vencimento e sem status) continuam valendo
	@Test
	void registrosAntigosContinuamNoMesDaDataEComoPagos() throws Exception {
		Despesa antiga = despesas.save(new Despesa("Antiga", 80, LocalDate.of(2026, 8, 12),
				categorias.findByUsuarioOrderByNomeAsc(usuario).get(0), usuario));
		em.flush();
		em.clear();
		Number pagoNoBanco = (Number) em.createNativeQuery("select count(*) from despesa where id = ?1 and pago is null and vencimento is null")
				.setParameter(1, antiga.getId()).getSingleResult();
		assertThat(pagoNoBanco.intValue()).isEqualTo(1);
		assertThat(controle.listarDespesas(usuario, YearMonth.of(2026, 8))).extracting(Despesa::getDescricao).containsExactly("Antiga");
		assertThat(controle.totalDespesasPagasMes(usuario, 8, 2026)).isEqualTo(80.0);
		assertThat(controle.saldoAnterior(usuario, 9, 2026)).isEqualTo(-80.0);
		mvc.perform(get("/despesas").with(LOGADO).param("mes", "2026-08"))
				.andExpect(content().string(containsString("12/08/2026")))
				.andExpect(content().string(containsString(">Pago<")));
		mvc.perform(get("/api/despesas").with(httpBasic(EMAIL, "123456")))
				.andExpect(jsonPath("$[0].vencimento").value("2026-08-12"))
				.andExpect(jsonPath("$[0].pago").value(true))
				.andExpect(jsonPath("$[0].status").value("Pago"));
		// Editar o registro antigo mantém o id e preenche o vencimento.
		mvc.perform(get("/despesas/{id}/editar", antiga.getId()).with(LOGADO))
				.andExpect(content().string(containsString("value=\"2026-08-12\"")));
	}

	// Edição e exclusão de parcelas
	@Test
	void editarUmaParcelaEExcluirUmaOuTodas() throws Exception {
		criarEnergiaSolar();
		List<Despesa> parcelas = parcelasDaEnergiaSolar();
		Despesa segunda = parcelas.get(1);
		mvc.perform(post("/despesas/{id}", segunda.getId()).with(LOGADO).with(csrf()).param("descricao", "Energia solar")
				.param("valor", "1000").param("data", "2027-01-02").param("vencimento", "2027-02-20").param("categoria", categoria))
				.andExpect(redirectedUrl("/despesas?mes=2027-02"));
		em.flush();
		em.clear();
		assertThat(parcelasDaEnergiaSolar().get(1).getVencimento()).isEqualTo(LocalDate.of(2027, 2, 20));
		assertThat(parcelasDaEnergiaSolar().get(2).getVencimento()).isEqualTo(LocalDate.of(2027, 3, 15));

		mvc.perform(post("/despesas/{id}/excluir", segunda.getId()).with(LOGADO).with(csrf()));
		assertThat(parcelasDaEnergiaSolar()).hasSize(13);
		assertThat(parcelasDaEnergiaSolar().get(1).getRotuloParcela()).isEqualTo("3/14");

		Despesa primeira = parcelasDaEnergiaSolar().get(0);
		mvc.perform(post("/despesas/{id}/excluir", primeira.getId()).with(LOGADO).with(csrf()).param("todas", "true"));
		assertThat(despesas.listarTodas(usuario)).isEmpty();
		assertThat(parcelamentos.findAll()).isEmpty();
	}

	// Edição do valor de uma parcela: pergunta se vale para as demais pendentes
	@Test
	void editarValorDeUmaParcelaMostraAPerguntaDasDemaisPendentes() throws Exception {
		criarEnergiaSolar();
		List<Despesa> parcelas = parcelasDaEnergiaSolar();
		mvc.perform(get("/despesas/{id}/editar", parcelas.get(2).getId()).with(LOGADO))
				.andExpect(content().string(containsString("Deseja aplicar este novo valor às demais parcelas pendentes?")))
				.andExpect(content().string(containsString("as outras 13 parcelas pendentes passam")))
				.andExpect(content().string(containsString("name=\"aplicarDemaisPendentes\"")))
				.andExpect(content().string(containsString("data-valor-original=\"1000.00\"")));

		// Despesa à vista: sem pergunta.
		Despesa mercado = criarAVista("Mercado", "80", "2027-01-10");
		mvc.perform(get("/despesas/{id}/editar", mercado.getId()).with(LOGADO))
				.andExpect(content().string(not(containsString("Deseja aplicar este novo valor"))))
				.andExpect(content().string(not(containsString("aplicarDemaisPendentes"))));

		// Todas as outras parcelas pagas: também não pergunta.
		for (Despesa p : parcelas.subList(0, 13)) {
			marcar(p, true);
		}
		mvc.perform(get("/despesas/{id}/editar", parcelas.get(13).getId()).with(LOGADO))
				.andExpect(content().string(not(containsString("Deseja aplicar este novo valor"))));
	}

	@Test
	void editarValorRespondendoNaoMudaSoEstaParcelaEAtualizaOTotal() throws Exception {
		criarEnergiaSolar();
		List<Despesa> parcelas = parcelasDaEnergiaSolar();
		Despesa terceira = parcelas.get(2);
		mvc.perform(post("/despesas/{id}", terceira.getId()).with(LOGADO).with(csrf()).param("descricao", "Energia solar")
				.param("valor", "1200").param("data", "2026-12-01").param("vencimento", "2027-03-15")
				.param("categoria", categoria).param("aplicarDemaisPendentes", "false"))
				.andExpect(redirectedUrl("/despesas?mes=2027-03"))
				.andExpect(flash().attribute("sucesso", "Parcela 3/14 atualizada."));
		em.flush();
		em.clear();
		List<Despesa> depois = parcelasDaEnergiaSolar();
		for (int i = 0; i < 14; i++) {
			assertThat(depois.get(i).getValor()).isEqualTo(i == 2 ? 1200.0 : 1000.0);
		}
		assertThat(parcelamentos.findAll().get(0).getValorTotal()).isEqualTo(14200.0);
		// Sem o parâmetro (navegador sem JavaScript) vale "Não".
		mvc.perform(post("/despesas/{id}", terceira.getId()).with(LOGADO).with(csrf()).param("descricao", "Energia solar")
				.param("valor", "1100.50").param("data", "2026-12-01").param("vencimento", "2027-03-15")
				.param("categoria", categoria));
		em.flush();
		em.clear();
		assertThat(parcelasDaEnergiaSolar().stream().filter(p -> p.getValor() == 1000.0)).hasSize(13);
		assertThat(parcelamentos.findAll().get(0).getValorTotal()).isEqualTo(14100.50);
		mvc.perform(get("/despesas/{id}/editar", terceira.getId()).with(LOGADO))
				.andExpect(content().string(containsString("R$ 14.100,50")));
	}

	@Test
	void editarValorRespondendoSimMudaAsDemaisPendentesMasNaoAsPagas() throws Exception {
		criarEnergiaSolar();
		List<Despesa> parcelas = parcelasDaEnergiaSolar();
		marcar(parcelas.get(0), true);
		marcar(parcelas.get(1), true);
		marcar(parcelas.get(6), true);
		Despesa quinta = parcelas.get(4);
		mvc.perform(post("/despesas/{id}", quinta.getId()).with(LOGADO).with(csrf()).param("descricao", "Energia solar")
				.param("valor", "1100").param("data", "2026-12-01").param("vencimento", "2027-05-15")
				.param("categoria", categoria).param("aplicarDemaisPendentes", "true"))
				.andExpect(redirectedUrl("/despesas?mes=2027-05"))
				.andExpect(flash().attribute("sucesso", "Parcela 5/14 atualizada. Novo valor aplicado também a 10 parcelas pendentes."));
		em.flush();
		em.clear();
		List<Despesa> depois = parcelasDaEnergiaSolar();
		for (int i = 0; i < 14; i++) {
			Despesa p = depois.get(i);
			boolean paga = i == 0 || i == 1 || i == 6;
			assertThat(p.isPago()).isEqualTo(paga);
			assertThat(p.getValor()).as("parcela %d", i + 1).isEqualTo(paga ? 1000.0 : 1100.0);
			// Só o valor muda: vencimentos e descrição continuam os mesmos.
			assertThat(p.getVencimento()).isEqualTo(LocalDate.of(2027, 1, 15).plusMonths(i));
		}
		// 3 pagas x 1.000 + 11 pendentes x 1.100
		assertThat(parcelamentos.findAll().get(0).getValorTotal()).isEqualTo(15100.0);
		assertThat(controle.totalDespesasPendentesMes(usuario, 8, 2027)).isEqualTo(1100.0);
		assertThat(controle.totalDespesasPagasMes(usuario, 7, 2027)).isEqualTo(1000.0);
	}

	@Test
	void servicoMantemOTotalIgualASomaDasParcelas() throws Exception {
		mvc.perform(post("/despesas").with(LOGADO).with(csrf()).param("descricao", "Curso").param("valor", "100")
				.param("quantidadeParcelas", "3").param("data", "2027-01-05").param("vencimento", "2027-01-10")
				.param("categoria", categoria));
		List<Despesa> curso = despesas.listarTodas(usuario).stream()
				.sorted((a, b) -> a.getNumeroParcela() - b.getNumeroParcela()).toList();
		// 33,34 + 33,33 + 33,33: a 2ª passa a 40,005 (arredonda para 40,01) e vale para a 3ª, pendente.
		Despesa dados = new Despesa("Curso", 40.005, LocalDate.of(2027, 1, 5), curso.get(1).getCategoria());
		dados.setVencimento(LocalDate.of(2027, 2, 10));
		assertThat(cadastro.outrasParcelasPendentes(curso.get(1))).isEqualTo(2);
		assertThat(cadastro.atualizar(curso.get(1), dados, true)).isEqualTo(2);
		em.flush();
		em.clear();
		List<Double> valores = despesas.listarTodas(usuario).stream()
				.sorted((a, b) -> a.getNumeroParcela() - b.getNumeroParcela()).map(Despesa::getValor).toList();
		assertThat(valores).containsExactly(40.01, 40.01, 40.01);
		assertThat(parcelamentos.findAll().get(0).getValorTotal()).isEqualTo(120.03);
	}

	// API: começa pendente e o pagamento é por parcela
	@Test
	void apiRegistraPendenteEMarcaPagamento() throws Exception {
		String json = """
				{"descricao": "Livro", "valor": 45.0, "data": "2027-04-01", "categoria": {"id": %s}}
				""".formatted(categoria);
		mvc.perform(post("/api/despesas").with(httpBasic(EMAIL, "123456")).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pago").value(false))
				.andExpect(jsonPath("$.vencimento").value("2027-04-01"));
		Integer id = despesas.listarTodas(usuario).get(0).getId();
		mvc.perform(put("/api/despesas/{id}/pagamento", id).with(httpBasic(EMAIL, "123456"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"pago\": true}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Pago"));
	}

	// 12. celular: viewport e regras responsivas da lista e do painel
	@Test
	void telasPreparadasParaCelular() throws Exception {
		mvc.perform(get("/despesas").with(LOGADO))
				.andExpect(content().string(containsString("name=\"viewport\" content=\"width=device-width, initial-scale=1\"")));
		mvc.perform(get("/despesas/nova").with(LOGADO))
				.andExpect(content().string(containsString("Quantidade de parcelas")))
				.andExpect(content().string(containsString("Vencimento da 1ª parcela")))
				.andExpect(content().string(containsString("inputmode=\"numeric\"")));
		mvc.perform(get("/estilo.css"))
				.andExpect(content().string(containsString(".lan.desp { grid-template-columns: 36px minmax(0, 1fr) auto; }")))
				.andExpect(content().string(containsString(".resumo.seis { grid-template-columns: 1fr; }")));
	}

	private void criarEnergiaSolar() throws Exception {
		mvc.perform(post("/despesas").with(LOGADO).with(csrf()).param("descricao", "Energia solar").param("valor", "14000")
				.param("quantidadeParcelas", "14").param("data", "2026-12-01").param("vencimento", "2027-01-15")
				.param("categoria", categoria))
				.andExpect(redirectedUrl("/despesas?mes=2027-01"));
	}

	private List<Despesa> parcelasDaEnergiaSolar() {
		return despesas.listarTodas(usuario).stream().filter(d -> d.getDescricao().equals("Energia solar"))
				.sorted((a, b) -> a.getNumeroParcela() - b.getNumeroParcela()).toList();
	}

	private Despesa criarAVista(String descricao, String valor, String vencimento) throws Exception {
		mvc.perform(post("/despesas").with(LOGADO).with(csrf()).param("descricao", descricao).param("valor", valor)
				.param("quantidadeParcelas", "1").param("data", vencimento).param("vencimento", vencimento)
				.param("categoria", categoria));
		return despesas.listarTodas(usuario).stream().filter(d -> d.getDescricao().equals(descricao)).findFirst().orElseThrow();
	}

	private void marcar(Despesa d, boolean pago) throws Exception {
		var req = post("/despesas/{id}/pagamento", d.getId()).with(LOGADO).with(csrf());
		if (pago) {
			req = req.param("pago", "true");
		}
		mvc.perform(req);
		em.flush();
		em.clear();
	}

}

package br.com.controlegastos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
 * Testa as funcionalidades pelas páginas, do jeito que o navegador usa:
 * login, categorias, fontes, receitas, edição/exclusão e painel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FuncionalidadesTests {

	private static final RequestPostProcessor DEMO = user(DadosIniciais.EMAIL_DEMO);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private CategoriaRepository categorias;

	@Autowired
	private FonteRepository fontes;

	@Autowired
	private DespesaRepository despesas;

	@Autowired
	private ReceitaRepository receitas;

	// ---------- 1. login ----------

	@Test
	void semLoginQualquerPaginaLevaAoLogin() throws Exception {
		mvc.perform(get("/painel")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
		mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Entrar")));
	}

	@Test
	void loginCorretoVaiParaOPainel() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("email", DadosIniciais.EMAIL_DEMO).param("senha", DadosIniciais.SENHA_DEMO))
				.andExpect(redirectedUrl("/painel"));
	}

	@Test
	void loginErradoMostraMensagem() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("email", DadosIniciais.EMAIL_DEMO).param("senha", "errada"))
				.andExpect(redirectedUrl("/login?erro"));
		mvc.perform(get("/login").param("erro", ""))
				.andExpect(content().string(containsString("E-mail ou senha inválidos.")));
	}

	@Test
	void cadastroDeContaValidaOsCampos() throws Exception {
		mvc.perform(post("/cadastro").with(csrf()).param("nome", "Ana").param("email", "ana@exemplo.com")
				.param("senha", "123456").param("confirmacao", "654321"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("As senhas não conferem.")));
		mvc.perform(post("/cadastro").with(csrf()).param("nome", "Ana").param("email", DadosIniciais.EMAIL_DEMO)
				.param("senha", "123456").param("confirmacao", "123456"))
				.andExpect(content().string(containsString("Já existe uma conta com esse e-mail.")));
	}

	@Test
	void cadaUsuarioVeSomenteOsPropriosRegistros() throws Exception {
		mvc.perform(post("/cadastro").with(csrf()).param("nome", "Bia").param("email", "bia@exemplo.com")
				.param("senha", "123456").param("confirmacao", "123456"))
				.andExpect(redirectedUrl("/login"));
		Integer despesaDaDemo = despesas.findByUsuarioOrderByDataDescIdDesc(demo()).get(0).getId();

		mvc.perform(get("/api/despesas").with(httpBasic("bia@exemplo.com", "123456")))
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/despesas/{id}/editar", despesaDaDemo).with(user("bia@exemplo.com")))
				.andExpect(status().isNotFound());
		mvc.perform(post("/despesas/{id}/excluir", despesaDaDemo).with(user("bia@exemplo.com")).with(csrf()))
				.andExpect(status().isNotFound());
		assertThat(despesas.findById(despesaDaDemo)).isPresent();
		// A conta nova já começa com categorias e fontes próprias.
		Usuario bia = usuarios.findByEmailIgnoreCase("bia@exemplo.com").orElseThrow();
		assertThat(categorias.findByUsuarioOrderByNomeAsc(bia)).hasSize(3);
		assertThat(fontes.findByUsuarioOrderByNomeAsc(bia)).hasSize(3);
	}

	// ---------- 2. categorias ----------

	@Test
	void criarEditarEExcluirCategoria() throws Exception {
		mvc.perform(post("/categorias").with(DEMO).with(csrf()).param("nome", "Material escolar").param("limiteMensal", "80"))
				.andExpect(redirectedUrl("/categorias")).andExpect(flash().attribute("sucesso", "Categoria \"Material escolar\" criada."));
		Categoria nova = categoria("Material escolar");

		mvc.perform(post("/categorias/{id}", nova.getId()).with(DEMO).with(csrf()).param("nome", "Livros").param("limiteMensal", "120"))
				.andExpect(redirectedUrl("/categorias"));
		assertThat(nova.getNome()).isEqualTo("Livros");
		assertThat(nova.getLimiteMensal()).isEqualTo(120);

		mvc.perform(post("/categorias/{id}/excluir", nova.getId()).with(DEMO).with(csrf()))
				.andExpect(flash().attribute("sucesso", "Categoria \"Livros\" excluída."));
		assertThat(categorias.findById(nova.getId())).isEmpty();
	}

	@Test
	void categoriaRepetidaOuInvalidaMostraErro() throws Exception {
		mvc.perform(post("/categorias").with(DEMO).with(csrf()).param("nome", "lazer").param("limiteMensal", "50"))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Já existe uma categoria com esse nome")));
		mvc.perform(post("/categorias").with(DEMO).with(csrf()).param("nome", "").param("limiteMensal", ""))
				.andExpect(content().string(containsString("Informe o nome da categoria")))
				.andExpect(content().string(containsString("Informe um limite válido")));
	}

	@Test
	void naoExcluiCategoriaComDespesas() throws Exception {
		Categoria alimentacao = categoria("Alimentação");
		mvc.perform(post("/categorias/{id}/excluir", alimentacao.getId()).with(DEMO).with(csrf()))
				.andExpect(flash().attributeExists("erro"));
		assertThat(categorias.findById(alimentacao.getId())).isPresent();
	}

	@Test
	void cadastroRapidoDeCategoriaFicaDisponivelNoFormulario() throws Exception {
		mvc.perform(post("/api/categorias").with(DEMO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": \"Saúde\", \"limiteMensal\": 60}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Saúde"));
		mvc.perform(post("/api/categorias").with(DEMO).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": \"saúde\", \"limiteMensal\": 60}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Já existe uma categoria com esse nome"));
		mvc.perform(get("/despesas/nova").with(DEMO))
				.andExpect(content().string(containsString(">Saúde</option>")));
	}

	// ---------- 3. receitas e fontes ----------

	@Test
	void criarEditarEExcluirFonte() throws Exception {
		mvc.perform(post("/fontes").with(DEMO).with(csrf()).param("nome", "Vendas"))
				.andExpect(redirectedUrl("/fontes"));
		Fonte vendas = fonte("Vendas");
		mvc.perform(post("/fontes/{id}", vendas.getId()).with(DEMO).with(csrf()).param("nome", "Vendas online"))
				.andExpect(redirectedUrl("/fontes"));
		assertThat(vendas.getNome()).isEqualTo("Vendas online");
		mvc.perform(post("/fontes/{id}/excluir", vendas.getId()).with(DEMO).with(csrf()))
				.andExpect(flash().attributeExists("sucesso"));
		assertThat(fontes.findById(vendas.getId())).isEmpty();

		Fonte mesada = fonte("Mesada");
		mvc.perform(post("/fontes/{id}/excluir", mesada.getId()).with(DEMO).with(csrf()))
				.andExpect(flash().attributeExists("erro"));
	}

	@Test
	void registrarEditarEExcluirReceita() throws Exception {
		Fonte bolsa = fonte("Bolsa");
		mvc.perform(post("/receitas").with(DEMO).with(csrf()).param("descricao", "Bolsa de iniciação")
				.param("valor", "400").param("data", "2026-11-03").param("fonte", bolsa.getId().toString()))
				.andExpect(redirectedUrl("/receitas?mes=2026-11"));
		Receita receita = receitas.findByUsuarioOrderByDataDescIdDesc(demo()).get(0);
		assertThat(receita.getDescricao()).isEqualTo("Bolsa de iniciação");
		assertThat(receita.getFonte().getNome()).isEqualTo("Bolsa");

		mvc.perform(get("/receitas").with(DEMO).param("mes", "2026-11"))
				.andExpect(content().string(containsString("Bolsa de iniciação")));

		mvc.perform(post("/receitas/{id}", receita.getId()).with(DEMO).with(csrf()).param("descricao", "Bolsa IC")
				.param("valor", "450").param("data", "2026-11-03").param("fonte", bolsa.getId().toString()))
				.andExpect(flash().attribute("sucesso", "Receita atualizada."));
		assertThat(receita.getValor()).isEqualTo(450);

		mvc.perform(post("/receitas/{id}/excluir", receita.getId()).with(DEMO).with(csrf()))
				.andExpect(redirectedUrl("/receitas?mes=2026-11"));
		assertThat(receitas.findById(receita.getId())).isEmpty();
	}

	@Test
	void receitaSemCamposMostraErros() throws Exception {
		mvc.perform(post("/receitas").with(DEMO).with(csrf()).param("descricao", "").param("valor", "").param("data", "").param("fonte", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Informe a descrição")))
				.andExpect(content().string(containsString("Informe um valor válido")))
				.andExpect(content().string(containsString("Informe a data")))
				.andExpect(content().string(containsString("Escolha uma fonte")));
	}

	// ---------- 4. edição e exclusão de despesas ----------

	@Test
	void editarEExcluirDespesa() throws Exception {
		Despesa despesa = despesas.save(new Despesa("Lanche", 15, LocalDate.of(2026, 11, 10), categoria("Lazer"), demo()));
		mvc.perform(get("/despesas/{id}/editar", despesa.getId()).with(DEMO))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Editar despesa")))
				.andExpect(content().string(containsString("value=\"15.00\"")));

		mvc.perform(post("/despesas/{id}", despesa.getId()).with(DEMO).with(csrf()).param("descricao", "Lanche da tarde")
				.param("valor", "18.5").param("data", "2026-11-10").param("categoria", categoria("Alimentação").getId().toString()))
				.andExpect(redirectedUrl("/despesas?mes=2026-11"));
		assertThat(despesa.getDescricao()).isEqualTo("Lanche da tarde");
		assertThat(despesa.getValor()).isEqualTo(18.5);
		assertThat(despesa.getCategoria().getNome()).isEqualTo("Alimentação");

		mvc.perform(post("/despesas/{id}/excluir", despesa.getId()).with(DEMO).with(csrf()))
				.andExpect(flash().attribute("sucesso", "Despesa \"Lanche da tarde\" excluída."));
		assertThat(despesas.findById(despesa.getId())).isEmpty();
	}

	@Test
	void naoAceitaCategoriaDeOutroUsuario() throws Exception {
		mvc.perform(post("/cadastro").with(csrf()).param("nome", "Caio").param("email", "caio@exemplo.com")
				.param("senha", "123456").param("confirmacao", "123456"));
		Categoria doCaio = categorias.findByUsuarioOrderByNomeAsc(usuarios.findByEmailIgnoreCase("caio@exemplo.com").orElseThrow()).get(0);
		mvc.perform(post("/despesas").with(DEMO).with(csrf()).param("descricao", "Teste").param("valor", "10")
				.param("data", "2026-10-10").param("categoria", doCaio.getId().toString()))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Escolha uma categoria")));
	}

	// ---------- 5. painel ----------

	@Test
	void painelMostraTotaisDoMesEscolhido() throws Exception {
		// Outubro/2026: receitas 800 + 650; despesas 12,50 + 50 + 8,40 + 85.
		mvc.perform(get("/painel").with(DEMO).param("mes", "2026-10"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("R$ 1.450,00")))
				.andExpect(content().string(containsString("R$ 155,90")))
				.andExpect(content().string(containsString("R$ 1.294,10")))
				.andExpect(content().string(containsString("Lazer chegou a 85% do limite do mês.")));
		// Setembro/2026: receitas 800; despesas 310 + 120.
		mvc.perform(get("/painel").with(DEMO).param("mes", "2026-09"))
				.andExpect(content().string(containsString("R$ 800,00")))
				.andExpect(content().string(containsString("R$ 430,00")))
				.andExpect(content().string(containsString("R$ 370,00")))
				.andExpect(content().string(not(containsString("Lazer chegou"))));
	}

	@Test
	void painelAtualizaDepoisDeUmaExclusao() throws Exception {
		Despesa cinema = despesas.findByUsuarioOrderByDataDescIdDesc(demo()).stream()
				.filter(d -> d.getDescricao().equals("Cinema com a turma")).findFirst().orElseThrow();
		mvc.perform(post("/despesas/{id}/excluir", cinema.getId()).with(DEMO).with(csrf()));
		mvc.perform(get("/painel").with(DEMO).param("mes", "2026-10"))
				.andExpect(content().string(containsString("R$ 70,90")))
				.andExpect(content().string(not(containsString("Lazer chegou"))));
	}

	@Test
	void todasAsPaginasAbrem() throws Exception {
		for (String url : new String[] { "/painel", "/despesas", "/despesas/nova", "/receitas", "/receitas/nova",
				"/categorias", "/fontes" }) {
			mvc.perform(get(url).with(DEMO)).andExpect(status().isOk());
		}
	}

	private Usuario demo() {
		return usuarios.findByEmailIgnoreCase(DadosIniciais.EMAIL_DEMO).orElseThrow();
	}

	private Categoria categoria(String nome) {
		return categorias.findByUsuarioOrderByNomeAsc(demo()).stream()
				.filter(c -> c.getNome().equals(nome)).findFirst().orElseThrow();
	}

	private Fonte fonte(String nome) {
		return fontes.findByUsuarioOrderByNomeAsc(demo()).stream()
				.filter(f -> f.getNome().equals(nome)).findFirst().orElseThrow();
	}

}

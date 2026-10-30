package br.com.controlegastos.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.config.DadosIniciais;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DespesaApiControllerTests {

	private static final RequestPostProcessor DEMO = httpBasic(DadosIniciais.EMAIL_DEMO, DadosIniciais.SENHA_DEMO);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CategoriaRepository categorias;

	@Autowired
	private UsuarioRepository usuarios;

	@Test
	void semLoginARecusaOAcesso() throws Exception {
		mvc.perform(get("/api/despesas"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listarDevolveDespesasDoUsuarioEmJson() throws Exception {
		mvc.perform(get("/api/despesas").with(DEMO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].descricao").value("Xerox da apostila"))
				.andExpect(jsonPath("$[0].valor").value(8.4))
				.andExpect(jsonPath("$[0].data").value("2026-10-29"))
				.andExpect(jsonPath("$[0].categoria.nome").value("Alimentação"))
				.andExpect(jsonPath("$[0].usuario").doesNotExist());
	}

	@Test
	void buscarIdInexistenteDevolve404() throws Exception {
		mvc.perform(get("/api/despesas/9999").with(DEMO))
				.andExpect(status().isNotFound());
	}

	@Test
	void registrarDespesaDevolve201() throws Exception {
		Integer lazer = idDaCategoria("Lazer");
		String json = """
				{"descricao": "Cinema", "valor": 30.0, "data": "2026-10-29", "categoria": {"id": %d}}
				""".formatted(lazer);
		mvc.perform(post("/api/despesas").with(DEMO).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.descricao").value("Cinema"))
				.andExpect(jsonPath("$.categoria.nome").value("Lazer"));
	}

	@Test
	void registrarDespesaInvalidaDevolve400() throws Exception {
		String json = """
				{"descricao": "", "valor": -5, "data": "2026-10-29", "categoria": {"id": 1}}
				""";
		mvc.perform(post("/api/despesas").with(DEMO).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest());
	}

	private Integer idDaCategoria(String nome) {
		return categorias.findByUsuarioOrderByNomeAsc(usuarios.findByEmailIgnoreCase(DadosIniciais.EMAIL_DEMO).orElseThrow())
				.stream().filter(c -> c.getNome().equals(nome)).findFirst().orElseThrow().getId();
	}

}

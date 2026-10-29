package br.com.controlegastos.controller;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class DespesaApiControllerTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void listarDevolveDespesasEmJson() throws Exception {
		mvc.perform(get("/api/despesas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].descricao").value("Almoço RU"))
				.andExpect(jsonPath("$[0].valor").value(12.5))
				.andExpect(jsonPath("$[0].data").value("2026-10-28"))
				.andExpect(jsonPath("$[0].categoria.nome").value("Alimentação"));
	}

	@Test
	void buscarIdInexistenteDevolve404() throws Exception {
		mvc.perform(get("/api/despesas/9999"))
				.andExpect(status().isNotFound());
	}

	@Test
	void registrarDespesaDevolve201() throws Exception {
		String json = """
				{"descricao": "Cinema", "valor": 30.0, "data": "2026-10-29", "categoria": {"id": 3}}
				""";
		mvc.perform(post("/api/despesas").contentType(MediaType.APPLICATION_JSON).content(json))
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
		mvc.perform(post("/api/despesas").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest());
	}

}

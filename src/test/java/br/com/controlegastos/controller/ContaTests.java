package br.com.controlegastos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.config.DadosIniciais;
import br.com.controlegastos.model.CodigoVerificacao;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CodigoVerificacaoRepository;
import br.com.controlegastos.repository.UsuarioRepository;
import br.com.controlegastos.service.CadastroUsuario;
import br.com.controlegastos.service.EnvioEmail;
import br.com.controlegastos.service.Verificacoes;

/**
 * Parte 5: nome de usuário, confirmação do e-mail por código, login por e-mail
 * ou nome de usuário e "esqueci minha senha". Os e-mails são capturados em
 * memória (só neste teste) para ler o código enviado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContaTests {

	@TestConfiguration
	static class EmailsDeTeste {

		@Bean
		@Primary
		CaixaDeEntrada caixaDeEntrada() {
			return new CaixaDeEntrada();
		}

	}

	static class CaixaDeEntrada implements EnvioEmail {

		final List<String> recebidos = new ArrayList<>();

		@Override
		public void enviar(String para, String assunto, String texto) {
			recebidos.add(para + "|" + texto);
		}

		@Override
		public boolean real() {
			return false;
		}

		String ultimoCodigo(String para) {
			for (int i = recebidos.size() - 1; i >= 0; i--) {
				if (recebidos.get(i).startsWith(para + "|")) {
					Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(recebidos.get(i));
					if (m.find()) {
						return m.group(1);
					}
				}
			}
			throw new AssertionError("Nenhum código enviado para " + para);
		}

	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CaixaDeEntrada caixa;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private CodigoVerificacaoRepository codigos;

	@Autowired
	private CadastroUsuario cadastro;

	@Autowired
	private Verificacoes verificacoes;

	@Autowired
	private PasswordEncoder codificador;

	@BeforeEach
	void limparCaixa() {
		caixa.recebidos.clear();
	}

	// ---------- cadastro e confirmação ----------

	@Test
	void cadastroGeraCodigoEBloqueiaLoginAteConfirmar() throws Exception {
		cadastrar("Joana.Silva", "joana@exemplo.com")
				.andExpect(redirectedUrl("/confirmar?login=joana%40exemplo.com"));
		Usuario joana = usuarios.findByEmailIgnoreCase("joana@exemplo.com").orElseThrow();
		assertThat(joana.getUsername()).isEqualTo("joana.silva");
		assertThat(joana.isEmailConfirmado()).isFalse();
		String codigo = caixa.ultimoCodigo("joana@exemplo.com");

		// Senha certa, e-mail pendente: vai para a confirmação (e a API também recusa).
		entrar("joana.silva", "segredo1").andExpect(header().string("Location", startsWith("/confirmar?pendente&login=joana.silva")));
		mvc.perform(get("/api/despesas").with(httpBasic("joana@exemplo.com", "segredo1"))).andExpect(status().isUnauthorized());
		// Senha errada não revela nada sobre a conta.
		entrar("joana.silva", "errada").andExpect(redirectedUrl("/login?erro"));

		mvc.perform(post("/confirmar").with(csrf()).param("login", "joana@exemplo.com").param("codigo", outro(codigo)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Código inválido ou expirado")));
		mvc.perform(post("/confirmar").with(csrf()).param("login", "joana@exemplo.com").param("codigo", codigo))
				.andExpect(redirectedUrl("/login")).andExpect(flash().attribute("sucesso", "E-mail confirmado! Agora é só entrar."));

		assertThat(usuarios.findByEmailIgnoreCase("joana@exemplo.com").orElseThrow().isEmailConfirmado()).isTrue();
		entrar("joana.silva", "segredo1").andExpect(redirectedUrl("/painel"));
	}

	@Test
	void loginPorNomeDeUsuarioOuEmail() throws Exception {
		entrar(DadosIniciais.EMAIL_DEMO, DadosIniciais.SENHA_DEMO).andExpect(redirectedUrl("/painel"));
		entrar("estudante", DadosIniciais.SENHA_DEMO).andExpect(redirectedUrl("/painel"));
		entrar("  ESTUDANTE ", DadosIniciais.SENHA_DEMO).andExpect(redirectedUrl("/painel"));
		entrar("estudante", "errada").andExpect(redirectedUrl("/login?erro"));
		entrar("ninguem", DadosIniciais.SENHA_DEMO).andExpect(redirectedUrl("/login?erro"));
	}

	@Test
	void codigoErradoVencidoOuReusadoERecusado() {
		Usuario u = pendente("rui", "rui@exemplo.com");
		assertThat(verificacoes.enviarCodigo(u, CodigoVerificacao.CONFIRMAR_EMAIL)).isTrue();
		String codigo = caixa.ultimoCodigo("rui@exemplo.com");
		assertThat(codigos.findFirstByUsuarioAndTipo(u, CodigoVerificacao.CONFIRMAR_EMAIL).orElseThrow().getHash())
				.as("só o hash fica no banco").isNotEqualTo(codigo).hasSize(64);

		assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, outro(codigo))).isFalse();
		assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, codigo)).isTrue();
		assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, codigo)).as("uso único").isFalse();
		// Código de um tipo não serve para o outro.
		verificacoes.enviarCodigo(u, CodigoVerificacao.REDEFINIR_SENHA);
		assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, caixa.ultimoCodigo("rui@exemplo.com"))).isFalse();

		verificacoes.enviarCodigo(u, CodigoVerificacao.CONFIRMAR_EMAIL);
		String vencido = caixa.ultimoCodigo("rui@exemplo.com");
		codigos.findFirstByUsuarioAndTipo(u, CodigoVerificacao.CONFIRMAR_EMAIL).orElseThrow()
				.setExpiraEm(Instant.now().minusSeconds(1));
		assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, vencido)).isFalse();
		assertThat(codigos.findFirstByUsuarioAndTipo(u, CodigoVerificacao.CONFIRMAR_EMAIL)).as("vencido é apagado").isEmpty();
	}

	@Test
	void limiteDeTentativas() {
		Usuario u = pendente("lara", "lara@exemplo.com");
		verificacoes.enviarCodigo(u, CodigoVerificacao.CONFIRMAR_EMAIL);
		String codigo = caixa.ultimoCodigo("lara@exemplo.com");
		for (int i = 0; i < Verificacoes.MAX_TENTATIVAS; i++) {
			assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, outro(codigo))).isFalse();
		}
		assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, codigo))
				.as("depois de 5 erros o código é descartado").isFalse();
	}

	@Test
	void reenvioRespeitaOIntervaloETrocaOCodigo() throws Exception {
		Usuario u = pendente("teo", "teo@exemplo.com");
		verificacoes.enviarCodigo(u, CodigoVerificacao.CONFIRMAR_EMAIL);
		String primeiro = caixa.ultimoCodigo("teo@exemplo.com");

		reenviar("teo").andExpect(redirectedUrl("/confirmar?login=teo"))
				.andExpect(flash().attribute("sucesso", containsString("Se houver uma confirmação pendente")));
		assertThat(caixa.recebidos).as("antes de 1 minuto não reenvia").hasSize(1);

		CodigoVerificacao atual = codigos.findFirstByUsuarioAndTipo(u, CodigoVerificacao.CONFIRMAR_EMAIL).orElseThrow();
		ReflectionTestUtils.setField(atual, "criadoEm", Instant.now().minusSeconds(61));
		reenviar("teo@exemplo.com");
		assertThat(caixa.recebidos).hasSize(2);
		String segundo = caixa.ultimoCodigo("teo@exemplo.com");
		if (!segundo.equals(primeiro)) {
			assertThat(verificacoes.conferir(u, CodigoVerificacao.CONFIRMAR_EMAIL, primeiro)).as("o antigo deixa de valer").isFalse();
		}
		mvc.perform(post("/confirmar").with(csrf()).param("login", "teo").param("codigo", segundo))
				.andExpect(redirectedUrl("/login"));
		// Conta já confirmada (ou inexistente): mesma mensagem, nenhum e-mail.
		reenviar("teo").andExpect(flash().attribute("sucesso", containsString("Se houver uma confirmação pendente")));
		reenviar("nao.existe").andExpect(flash().attribute("sucesso", containsString("Se houver uma confirmação pendente")));
		assertThat(caixa.recebidos).hasSize(2);
	}

	// ---------- nome de usuário ----------

	@Test
	void nomeDeUsuarioRepetidoERecusadoMesmoComOutrasMaiusculas() throws Exception {
		cadastrar("EsTuDaNtE", "outra@exemplo.com").andExpect(status().isOk())
				.andExpect(content().string(containsString("Esse nome de usuário já está em uso.")));
		assertThat(usuarios.existsByEmailIgnoreCase("outra@exemplo.com")).isFalse();
	}

	@Test
	void nomeDeUsuarioInvalidoERecusado() throws Exception {
		for (String invalido : List.of("", "ab", "com espaco", "ana@casa", "acentuação", "a".repeat(31), "ana!")) {
			cadastrar(invalido, "invalido@exemplo.com").andExpect(status().isOk())
					.andExpect(content().string(containsString("O nome de usuário deve ter de 3 a 30 caracteres")));
		}
		assertThat(usuarios.existsByEmailIgnoreCase("invalido@exemplo.com")).isFalse();
		cadastrar("a_b-c.9", "valido@exemplo.com").andExpect(status().is3xxRedirection());
	}

	// ---------- esqueci minha senha ----------

	@Test
	void esqueciMinhaSenhaDePontaAPonta() throws Exception {
		cadastro.cadastrar("Lia", "lia@exemplo.com", "antiga1");
		esqueci(" LIA ", " Lia@Exemplo.com ")
				.andExpect(redirectedUrl("/senha/redefinir?login=lia"))
				.andExpect(flash().attribute("sucesso", AutenticacaoController.PEDIDO_NEUTRO));
		String codigo = caixa.ultimoCodigo("lia@exemplo.com");

		redefinir("lia", outro(codigo), "novinha1").andExpect(status().isOk())
				.andExpect(content().string(containsString("Código inválido ou expirado")));
		redefinir("lia", codigo, "123").andExpect(content().string(containsString("pelo menos 6 caracteres")));
		redefinir("lia", codigo, "novinha1").andExpect(redirectedUrl("/login"));
		redefinir("lia", codigo, "outra123").andExpect(content().string(containsString("Código inválido ou expirado")));

		entrar("lia", "antiga1").andExpect(redirectedUrl("/login?erro"));
		entrar("lia@exemplo.com", "novinha1").andExpect(redirectedUrl("/painel"));
		assertThat(codificador.matches("novinha1", usuarios.findByEmailIgnoreCase("lia@exemplo.com").orElseThrow().getSenha())).isTrue();
	}

	@Test
	void mensagemNeutraParaContaDesconhecida() throws Exception {
		esqueci("ninguem", "ninguem@exemplo.com")
				.andExpect(redirectedUrl("/senha/redefinir?login=ninguem"))
				.andExpect(flash().attribute("sucesso", AutenticacaoController.PEDIDO_NEUTRO));
		assertThat(caixa.recebidos).isEmpty();
		redefinir("ninguem", "123456", "novinha1")
				.andExpect(content().string(containsString("Código inválido ou expirado")));
	}

	@Test
	void usuarioEEmailDeContasDiferentesNaoGeramCodigo() throws Exception {
		cadastro.cadastrar("Lia", "lia@exemplo.com", "antiga1");
		// Usuário da Lia com o e-mail da demo, e o contrário: mesma resposta, nenhum e-mail.
		esqueci("lia", DadosIniciais.EMAIL_DEMO).andExpect(redirectedUrl("/senha/redefinir?login=lia"))
				.andExpect(flash().attribute("sucesso", AutenticacaoController.PEDIDO_NEUTRO));
		esqueci("estudante", "lia@exemplo.com").andExpect(flash().attribute("sucesso", AutenticacaoController.PEDIDO_NEUTRO));
		// Só um dos dois campos não serve (o e-mail não vale como nome de usuário).
		esqueci("lia@exemplo.com", "lia@exemplo.com").andExpect(flash().attribute("sucesso", AutenticacaoController.PEDIDO_NEUTRO));
		esqueci("lia", "").andExpect(status().isOk())
				.andExpect(content().string(containsString("Informe o nome de usuário e o e-mail cadastrado.")));
		assertThat(caixa.recebidos).isEmpty();
		assertThat(codigos.count()).isZero();
	}

	@Test
	void menuMostraONomeDeUsuario() throws Exception {
		mvc.perform(get("/painel").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
				.user(DadosIniciais.EMAIL_DEMO))).andExpect(content().string(containsString("@estudante")));
	}

	@Test
	void redefinirSenhaTambemConfirmaOEmail() throws Exception {
		pendente("davi", "davi@exemplo.com");
		esqueci("Davi", "davi@exemplo.com");
		redefinir("davi", caixa.ultimoCodigo("davi@exemplo.com"), "novinha1").andExpect(redirectedUrl("/login"));
		entrar("davi", "novinha1").andExpect(redirectedUrl("/painel"));
	}

	// ---------- contas antigas ----------

	@Test
	void contasAntigasGanhamNomeDeUsuarioEContinuamEntrando() throws Exception {
		usuarios.save(new Usuario("Antiga", "x.y@a.com", codificador.encode("velha12")));
		usuarios.save(new Usuario("Outra antiga", "X.Y@b.com", codificador.encode("velha12")));
		usuarios.save(new Usuario("Curta", "a+b@c.com", codificador.encode("velha12")));
		assertThat(cadastro.preencherUsernames()).isEqualTo(3);
		assertThat(cadastro.preencherUsernames()).as("idempotente").isZero();

		assertThat(usuarios.findByEmailIgnoreCase("x.y@a.com").orElseThrow().getUsername()).isEqualTo("x.y");
		assertThat(usuarios.findByEmailIgnoreCase("x.y@b.com").orElseThrow().getUsername()).isEqualTo("x.y2");
		assertThat(usuarios.findByEmailIgnoreCase("a+b@c.com").orElseThrow().getUsername()).isEqualTo("usuarioab");
		assertThat(usuarios.findByEmailIgnoreCase("x.y@a.com").orElseThrow().isEmailConfirmado()).isTrue();

		entrar("x.y@a.com", "velha12").andExpect(redirectedUrl("/painel"));
		entrar("x.y2", "velha12").andExpect(redirectedUrl("/painel"));
		assertThat(usuarios.findByEmailIgnoreCase(DadosIniciais.EMAIL_DEMO).orElseThrow().getUsername()).isEqualTo("estudante");
	}

	@Test
	void telasMostramOModoDesenvolvimento() throws Exception {
		mvc.perform(get("/login")).andExpect(content().string(containsString("Esqueci minha senha")));
		mvc.perform(get("/senha/esqueci")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Modo desenvolvimento")));
		mvc.perform(get("/confirmar").param("login", "x")).andExpect(status().isOk())
				.andExpect(content().string(containsString("Confirme seu e-mail")));
	}

	// ---------- apoio ----------

	private ResultActions cadastrar(String usuario, String email) throws Exception {
		return mvc.perform(post("/cadastro").with(csrf()).param("nome", "Pessoa").param("usuario", usuario)
				.param("email", email).param("senha", "segredo1").param("confirmacao", "segredo1"));
	}

	private ResultActions esqueci(String usuario, String email) throws Exception {
		return mvc.perform(post("/senha/esqueci").with(csrf()).param("usuario", usuario).param("email", email));
	}

	private ResultActions entrar(String login, String senha) throws Exception {
		return mvc.perform(post("/login").with(csrf()).param("email", login).param("senha", senha));
	}

	private ResultActions reenviar(String login) throws Exception {
		return mvc.perform(post("/confirmar/reenviar").with(csrf()).param("login", login));
	}

	private ResultActions redefinir(String login, String codigo, String senha) throws Exception {
		return mvc.perform(post("/senha/redefinir").with(csrf()).param("login", login).param("codigo", codigo)
				.param("senha", senha).param("confirmacao", senha));
	}

	private Usuario pendente(String username, String email) {
		return cadastro.cadastrar("Pessoa", username, email, "segredo1", false);
	}

	/** Um código de 6 dígitos diferente do certo. */
	private static String outro(String codigo) {
		return "%06d".formatted((Integer.parseInt(codigo) + 1) % 1_000_000);
	}

}
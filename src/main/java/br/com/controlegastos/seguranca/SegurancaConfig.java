package br.com.controlegastos.seguranca;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import br.com.controlegastos.repository.UsuarioRepository;
import br.com.controlegastos.service.EnvioEmail;
import br.com.controlegastos.service.Verificacoes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login por formulário (páginas) e HTTP Basic (API). Tudo exige login, exceto
 * as telas de entrada, cadastro, confirmação de e-mail e recuperação de senha.
 */
@Configuration
public class SegurancaConfig {

	private static final AuthenticationEntryPoint API_SEM_LOGIN = new BasicAuthenticationEntryPoint() {
		{
			setRealmName("controle-gastos");
		}
	};
	private static final AuthenticationEntryPoint PAGINA_SEM_LOGIN = new LoginUrlAuthenticationEntryPoint("/login");

	private final Verificacoes verificacoes;

	SegurancaConfig(@Lazy Verificacoes verificacoes) {
		this.verificacoes = verificacoes;
	}

	@Bean
	SecurityFilterChain filtros(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(a -> a
						.requestMatchers("/login", "/cadastro", "/confirmar", "/confirmar/reenviar", "/senha/esqueci",
								"/senha/redefinir", "/estilo.css", "/app.js", "/favicon.ico", "/error", "/h2-console/**")
						.permitAll()
						.anyRequest().authenticated())
				.formLogin(f -> f
						.loginPage("/login")
						.usernameParameter("email")
						.passwordParameter("senha")
						.defaultSuccessUrl("/painel", true)
						.failureHandler(this::falhaNoLogin))
				.logout(l -> l.logoutSuccessUrl("/login?saiu"))
				.httpBasic(Customizer.withDefaults())
				// Sem login: páginas vão para a tela de login; a API responde 401.
				.exceptionHandling(e -> e.authenticationEntryPoint((req, resp, falha) -> {
					if (req.getRequestURI().startsWith(req.getContextPath() + "/api/")) {
						API_SEM_LOGIN.commence(req, resp, falha);
					} else {
						PAGINA_SEM_LOGIN.commence(req, resp, falha);
					}
				}))
				// A API recebe JSON (o navegador exige pré-verificação CORS para isso) e o console do H2 tem o próprio formulário.
				.csrf(c -> c.ignoringRequestMatchers("/api/**", "/h2-console/**"))
				.headers(h -> h.frameOptions(o -> o.sameOrigin()));
		return http.build();
	}

	/**
	 * Senha certa de conta que ainda não confirmou o e-mail: manda (ou reenvia,
	 * respeitando o intervalo) o código e leva para a tela de confirmação.
	 * Qualquer outra falha mostra a mesma mensagem genérica.
	 */
	private void falhaNoLogin(HttpServletRequest pedido, HttpServletResponse resposta, AuthenticationException falha)
			throws IOException {
		String login = Objects.toString(pedido.getParameter("email"), "").trim();
		if (falha instanceof DisabledException) {
			verificacoes.reenviarConfirmacao(login);
			resposta.sendRedirect(pedido.getContextPath() + "/confirmar?pendente&login="
					+ URLEncoder.encode(login, StandardCharsets.UTF_8));
		} else {
			resposta.sendRedirect(pedido.getContextPath() + "/login?erro");
		}
	}

	@Bean
	PasswordEncoder codificadorDeSenha() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	EnvioEmail envioEmail(Environment ambiente) {
		return EnvioEmail.doAmbiente(ambiente);
	}

	/** Entra com e-mail ou nome de usuário; a identidade da sessão continua sendo o e-mail. */
	private UserDetailsService usuarios(UsuarioRepository repositorio) {
		return login -> repositorio.buscarPorLogin(login)
				.map(u -> User.withUsername(u.getEmail()).password(u.getSenha()).roles("USUARIO")
						.disabled(!u.isEmailConfirmado() && verificacoes.ativa()).build())
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
	}

	/**
	 * Só olha a confirmação do e-mail depois de conferir a senha: com a senha
	 * errada a resposta é sempre a mesma, sem revelar o estado da conta.
	 */
	@Bean
	DaoAuthenticationProvider autenticacao(UsuarioRepository repositorio, PasswordEncoder codificador) {
		DaoAuthenticationProvider provedor = new DaoAuthenticationProvider(usuarios(repositorio));
		provedor.setPasswordEncoder(codificador);
		provedor.setPreAuthenticationChecks(u -> {
		});
		provedor.setPostAuthenticationChecks(u -> {
			if (!u.isEnabled()) {
				throw new DisabledException("E-mail ainda não confirmado");
			}
		});
		return provedor;
	}

}

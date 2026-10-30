package br.com.controlegastos.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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

/**
 * Login por formulário (páginas) e HTTP Basic (API). Tudo exige login, exceto
 * as telas de entrada e cadastro.
 */
@Configuration
public class SegurancaConfig {

	private static final AuthenticationEntryPoint API_SEM_LOGIN = new BasicAuthenticationEntryPoint() {
		{
			setRealmName("controle-gastos");
		}
	};
	private static final AuthenticationEntryPoint PAGINA_SEM_LOGIN = new LoginUrlAuthenticationEntryPoint("/login");

	@Bean
	SecurityFilterChain filtros(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(a -> a
						.requestMatchers("/login", "/cadastro", "/estilo.css", "/app.js", "/favicon.ico", "/error",
								"/h2-console/**")
						.permitAll()
						.anyRequest().authenticated())
				.formLogin(f -> f
						.loginPage("/login")
						.usernameParameter("email")
						.passwordParameter("senha")
						.defaultSuccessUrl("/painel", true)
						.failureUrl("/login?erro"))
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

	@Bean
	PasswordEncoder codificadorDeSenha() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService usuarios(UsuarioRepository repositorio) {
		return email -> repositorio.findByEmailIgnoreCase(email.trim())
				.map(u -> User.withUsername(u.getEmail()).password(u.getSenha()).roles("USUARIO").build())
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
	}

}

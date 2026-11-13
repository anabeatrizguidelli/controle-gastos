package br.com.controlegastos.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.seguranca.UsuarioAtual;

/**
 * Coloca o nome de quem está logado em todas as páginas (aparece no menu lateral).
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class DadosComuns {

	private final UsuarioAtual usuarioAtual;

	public DadosComuns(UsuarioAtual usuarioAtual) {
		this.usuarioAtual = usuarioAtual;
	}

	@ModelAttribute("usuarioNome")
	public String usuarioNome() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			return null;
		}
		Usuario usuario = usuarioAtual.get();
		// O nome de usuário aparece junto para quem teve um gerado automaticamente saber qual é.
		return usuario.getUsername() == null ? usuario.getNome() : usuario.getNome() + " · @" + usuario.getUsername();
	}

}

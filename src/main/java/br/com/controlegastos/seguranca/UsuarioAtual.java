package br.com.controlegastos.seguranca;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.UsuarioRepository;

/**
 * Devolve o usuário que está logado na requisição atual.
 */
@Component
public class UsuarioAtual {

	private final UsuarioRepository usuarios;

	public UsuarioAtual(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	public Usuario get() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return usuarios.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new IllegalStateException("Usuário logado não encontrado: " + email));
	}

}

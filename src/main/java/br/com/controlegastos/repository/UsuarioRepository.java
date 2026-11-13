package br.com.controlegastos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.controlegastos.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

	Optional<Usuario> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByUsername(String username);

	Optional<Usuario> findByUsername(String username);

	Optional<Usuario> findFirstByEmailIgnoreCaseOrUsername(String email, String username);

	/** Contas antigas, de antes do nome de usuário existir. */
	List<Usuario> findByUsernameIsNull();

	/** Busca pelo e-mail ou pelo nome de usuário (o que a pessoa digitou no login). */
	default Optional<Usuario> buscarPorLogin(String login) {
		String texto = login == null ? "" : login.trim();
		return texto.isEmpty() ? Optional.empty() : findFirstByEmailIgnoreCaseOrUsername(texto, texto.toLowerCase());
	}

}
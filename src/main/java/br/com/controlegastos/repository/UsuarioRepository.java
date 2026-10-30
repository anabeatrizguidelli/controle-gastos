package br.com.controlegastos.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.controlegastos.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

	Optional<Usuario> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

}

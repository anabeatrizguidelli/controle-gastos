package br.com.controlegastos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Usuario;

public interface FonteRepository extends JpaRepository<Fonte, Integer> {

	List<Fonte> findByUsuarioOrderByNomeAsc(Usuario usuario);

	Optional<Fonte> findByIdAndUsuario(Integer id, Usuario usuario);

	boolean existsByUsuarioAndNomeIgnoreCase(Usuario usuario, String nome);

	boolean existsByUsuarioAndNomeIgnoreCaseAndIdNot(Usuario usuario, String nome, Integer id);

	long countByUsuario(Usuario usuario);

}

package br.com.controlegastos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Usuario;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

	List<Categoria> findByUsuarioOrderByNomeAsc(Usuario usuario);

	Optional<Categoria> findByIdAndUsuario(Integer id, Usuario usuario);

	boolean existsByUsuarioAndNomeIgnoreCase(Usuario usuario, String nome);

	boolean existsByUsuarioAndNomeIgnoreCaseAndIdNot(Usuario usuario, String nome, Integer id);

	long countByUsuario(Usuario usuario);

	List<Categoria> findByUsuarioIsNull();

}

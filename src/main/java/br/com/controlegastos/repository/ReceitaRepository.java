package br.com.controlegastos.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Usuario;

public interface ReceitaRepository extends JpaRepository<Receita, Integer> {

	List<Receita> findByUsuarioOrderByDataDescIdDesc(Usuario usuario);

	List<Receita> findByUsuarioAndDataBetweenOrderByDataDescIdDesc(Usuario usuario, LocalDate inicio, LocalDate fim);

	Optional<Receita> findByIdAndUsuario(Integer id, Usuario usuario);

	boolean existsByFonte(Fonte fonte);

	/** Soma das receitas do usuário com data anterior a {@code data}. */
	@Query("select coalesce(sum(r.valor), 0) from Receita r where r.usuario = ?1 and r.data < ?2")
	double somarAntesDe(Usuario usuario, LocalDate data);

}

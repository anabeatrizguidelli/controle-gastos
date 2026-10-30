package br.com.controlegastos.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Usuario;

public interface DespesaRepository extends JpaRepository<Despesa, Integer> {

	List<Despesa> findByUsuarioOrderByDataDescIdDesc(Usuario usuario);

	List<Despesa> findByUsuarioAndDataBetweenOrderByDataDescIdDesc(Usuario usuario, LocalDate inicio, LocalDate fim);

	Optional<Despesa> findByIdAndUsuario(Integer id, Usuario usuario);

	boolean existsByCategoria(Categoria categoria);

	/** Soma das despesas do usuário com data anterior a {@code data}. */
	@Query("select coalesce(sum(d.valor), 0) from Despesa d where d.usuario = ?1 and d.data < ?2")
	double somarAntesDe(Usuario usuario, LocalDate data);

	List<Despesa> findByUsuarioIsNull();

}

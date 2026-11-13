package br.com.controlegastos.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.RecorrenciaReceita;
import br.com.controlegastos.model.Usuario;

public interface RecorrenciaReceitaRepository extends JpaRepository<RecorrenciaReceita, Integer> {

	/** Só as receitas fixas do usuário que ainda têm mês a lançar até {@code mes}. */
	@Query("select r from RecorrenciaReceita r where r.usuario = ?1 and r.geradaAte < ?2 "
			+ "and (r.fim is null or r.geradaAte < r.fim)")
	List<RecorrenciaReceita> comMesesALancar(Usuario usuario, LocalDate mes);

	boolean existsByFonte(Fonte fonte);

}
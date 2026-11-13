package br.com.controlegastos.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Recorrencia;
import br.com.controlegastos.model.Usuario;

public interface RecorrenciaRepository extends JpaRepository<Recorrencia, Integer> {

	/**
	 * Só as recorrências do usuário que ainda têm mês a lançar até {@code mes}
	 * (normalmente nenhuma, depois da primeira visita do mês).
	 */
	@Query("select r from Recorrencia r where r.usuario = ?1 and r.geradaAte < ?2 "
			+ "and (r.fim is null or r.geradaAte < r.fim)")
	List<Recorrencia> comMesesALancar(Usuario usuario, LocalDate mes);

	boolean existsByCategoria(Categoria categoria);

}
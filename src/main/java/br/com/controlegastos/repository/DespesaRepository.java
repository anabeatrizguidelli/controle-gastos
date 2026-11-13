package br.com.controlegastos.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Parcelamento;
import br.com.controlegastos.model.Recorrencia;
import br.com.controlegastos.model.Usuario;

/**
 * O mês de cada despesa é o do vencimento. Registros antigos não têm vencimento,
 * por isso as consultas usam {@code coalesce(vencimento, data)}. Despesas antigas
 * sem status ({@code pago} nulo) contam como pagas.
 */
public interface DespesaRepository extends JpaRepository<Despesa, Integer> {

	List<Despesa> findByUsuarioOrderByDataDescIdDesc(Usuario usuario);

	List<Despesa> findByUsuarioAndDataBetweenOrderByDataDescIdDesc(Usuario usuario, LocalDate inicio, LocalDate fim);

	/** Todas as despesas do usuário, do vencimento mais recente para o mais antigo. */
	@Query("select d from Despesa d where d.usuario = ?1 order by coalesce(d.vencimento, d.data) desc, d.id desc")
	List<Despesa> listarTodas(Usuario usuario);

	/** Despesas com vencimento entre {@code inicio} e {@code fim} (inclusive). */
	@Query("select d from Despesa d where d.usuario = ?1 and coalesce(d.vencimento, d.data) between ?2 and ?3 "
			+ "order by coalesce(d.vencimento, d.data) desc, d.id desc")
	List<Despesa> listarPorVencimento(Usuario usuario, LocalDate inicio, LocalDate fim);

	Optional<Despesa> findByIdAndUsuario(Integer id, Usuario usuario);

	boolean existsByCategoria(Categoria categoria);

	/** Soma das despesas do usuário com data anterior a {@code data}. */
	@Query("select coalesce(sum(d.valor), 0) from Despesa d where d.usuario = ?1 and d.data < ?2")
	double somarAntesDe(Usuario usuario, LocalDate data);

	/** Soma das despesas pagas com vencimento anterior a {@code data}. */
	@Query("select coalesce(sum(d.valor), 0) from Despesa d where d.usuario = ?1 "
			+ "and coalesce(d.vencimento, d.data) < ?2 and (d.pago is null or d.pago = true)")
	double somarPagasAntesDe(Usuario usuario, LocalDate data);

	List<Despesa> findByParcelamentoOrderByNumeroParcelaAsc(Parcelamento parcelamento);

	long countByParcelamento(Parcelamento parcelamento);

	List<Despesa> findByUsuarioIsNull();

	/** Lançamentos pendentes de uma despesa fixa que vencem depois de {@code data}. */
	List<Despesa> findByRecorrenciaAndVencimentoAfterAndPagoFalse(Recorrencia recorrencia, LocalDate data);

	long countByRecorrencia(Recorrencia recorrencia);

}

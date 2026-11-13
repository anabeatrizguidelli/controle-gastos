package br.com.controlegastos.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.controlegastos.model.CodigoVerificacao;
import br.com.controlegastos.model.Usuario;

public interface CodigoVerificacaoRepository extends JpaRepository<CodigoVerificacao, Integer> {

	Optional<CodigoVerificacao> findFirstByUsuarioAndTipo(Usuario usuario, String tipo);

	@Modifying
	@Query("delete from CodigoVerificacao c where c.usuario = ?1 and c.tipo = ?2")
	void apagar(Usuario usuario, String tipo);

	/** Limpa os códigos vencidos de todo mundo (chamado a cada código novo). */
	@Modifying
	@Query("delete from CodigoVerificacao c where c.expiraEm < ?1")
	void apagarVencidos(Instant agora);

}
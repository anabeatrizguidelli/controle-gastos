package br.com.controlegastos.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Código de 6 dígitos enviado por e-mail (confirmar a conta ou redefinir a
 * senha). Só o hash é gravado; o registro é apagado ao ser usado, ao esgotar
 * as tentativas ou quando expira.
 */
@Entity
public class CodigoVerificacao {

	public static final String CONFIRMAR_EMAIL = "CONFIRMAR_EMAIL";
	public static final String REDEFINIR_SENHA = "REDEFINIR_SENHA";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(optional = false)
	private Usuario usuario;

	@Column(length = 20, nullable = false)
	private String tipo;

	/** SHA-256 do código (hexadecimal). */
	@Column(length = 64, nullable = false)
	private String hash;

	private Instant criadoEm;

	private Instant expiraEm;

	private int tentativas;

	protected CodigoVerificacao() {
	}

	public CodigoVerificacao(Usuario usuario, String tipo, String hash, Instant criadoEm, Instant expiraEm) {
		this.usuario = usuario;
		this.tipo = tipo;
		this.hash = hash;
		this.criadoEm = criadoEm;
		this.expiraEm = expiraEm;
	}

	public String getHash() {
		return hash;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getExpiraEm() {
		return expiraEm;
	}

	public void setExpiraEm(Instant expiraEm) {
		this.expiraEm = expiraEm;
	}

	/** Conta uma tentativa errada e devolve o total. */
	public int errou() {
		return ++tentativas;
	}

}
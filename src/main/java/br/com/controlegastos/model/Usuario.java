package br.com.controlegastos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Pessoa que usa o sistema. Cada usuário enxerga apenas os próprios registros.
 * <p>
 * {@code username} e {@code emailConfirmado} foram adicionados depois e aceitam
 * nulo no banco: o nome de usuário dos registros antigos é preenchido na
 * inicialização, e {@code emailConfirmado} nulo significa conta antiga,
 * tratada como confirmada (ninguém fica trancado do lado de fora).
 */
@Entity
public class Usuario {

	/** Nome de usuário: letras minúsculas, números, ponto, hífen e sublinhado, de 3 a 30 caracteres. */
	public static final String FORMATO_USERNAME = "[a-z0-9._-]{3,30}";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String nome;

	@Column(unique = true, nullable = false)
	private String email;

	/** Senha guardada com hash BCrypt, nunca em texto puro. */
	@Column(nullable = false)
	private String senha;

	/** Guardado em minúsculas, então o índice único já não diferencia maiúsculas. */
	@Column(unique = true, length = 30)
	private String username;

	/** {@code false} = aguardando o código do e-mail; {@code true} ou nulo (conta antiga) = confirmado. */
	private Boolean emailConfirmado;

	protected Usuario() {
	}

	public Usuario(String nome, String email, String senha) {
		this.nome = nome;
		this.email = email;
		this.senha = senha;
	}

	public boolean isEmailConfirmado() {
		return !Boolean.FALSE.equals(emailConfirmado);
	}

	public void setEmailConfirmado(Boolean emailConfirmado) {
		this.emailConfirmado = emailConfirmado;
	}

	public Integer getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getSenha() {
		return senha;
	}

	public void setSenha(String senha) {
		this.senha = senha;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

}
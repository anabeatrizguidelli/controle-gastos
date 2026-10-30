package br.com.controlegastos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Pessoa que usa o sistema. Cada usuário enxerga apenas os próprios registros.
 */
@Entity
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String nome;

	@Column(unique = true, nullable = false)
	private String email;

	/** Senha guardada com hash BCrypt, nunca em texto puro. */
	@Column(nullable = false)
	private String senha;

	protected Usuario() {
	}

	public Usuario(String nome, String email, String senha) {
		this.nome = nome;
		this.email = email;
		this.senha = senha;
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

}

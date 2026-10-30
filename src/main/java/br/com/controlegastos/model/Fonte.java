package br.com.controlegastos.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Origem de uma receita (ex.: Mesada, Bolsa, Estágio), cadastrada pelo próprio usuário.
 */
@Entity
public class Fonte {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@NotBlank(message = "Informe o nome da fonte")
	@Size(max = 40, message = "Use no máximo 40 caracteres")
	private String nome;

	@JsonIgnore
	@ManyToOne
	private Usuario usuario;

	@JsonCreator
	public Fonte() {
	}

	public Fonte(String nome, Usuario usuario) {
		this.nome = nome;
		this.usuario = usuario;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

}

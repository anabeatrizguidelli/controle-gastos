package br.com.controlegastos.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Categoria de despesa (ex.: Alimentação, Transporte) com um limite de gasto mensal.
 */
@Entity
public class Categoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@NotBlank(message = "Informe o nome da categoria")
	@Size(max = 40, message = "Use no máximo 40 caracteres")
	private String nome;

	@Positive(message = "O limite deve ser maior que zero")
	private double limiteMensal;

	@JsonIgnore
	@ManyToOne
	private Usuario usuario;

	@JsonCreator
	public Categoria() {
	}

	public Categoria(String nome, double limiteMensal) {
		this.nome = nome;
		this.limiteMensal = limiteMensal;
	}

	public Categoria(String nome, double limiteMensal, Usuario usuario) {
		this(nome, limiteMensal);
		this.usuario = usuario;
	}

	/**
	 * Retorna "OK" abaixo de 80% do limite, "ATENCAO" a partir de 80% e
	 * "ESTOURADO" a partir de 100%.
	 */
	public String calcularNivel(double totalGasto) {
		double percentual = totalGasto / limiteMensal;
		if (percentual >= 1.0) {
			return "ESTOURADO";
		}
		if (percentual >= 0.8) {
			return "ATENCAO";
		}
		return "OK";
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

	public double getLimiteMensal() {
		return limiteMensal;
	}

	public void setLimiteMensal(double limiteMensal) {
		this.limiteMensal = limiteMensal;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

}

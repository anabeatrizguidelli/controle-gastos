package br.com.controlegastos.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Categoria de despesa (ex.: Alimentação, Transporte) com um limite de gasto
 * mensal opcional. Limite 0 significa "sem limite": assim a coluna existente
 * continua igual e nenhuma migração é necessária.
 */
@Entity
public class Categoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@NotBlank(message = "Informe o nome da categoria")
	@Size(max = 40, message = "Use no máximo 40 caracteres")
	private String nome;

	@PositiveOrZero(message = "O limite não pode ser negativo")
	private double limiteMensal;

	/** Escolha feita no formulário ou na API. Não é gravada: no banco vale {@code limiteMensal > 0}. */
	@Transient
	private Boolean temLimite;

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
	 * "ESTOURADO" a partir de 100%. Sem limite, é sempre "OK" (nenhum alerta).
	 */
	public String calcularNivel(double totalGasto) {
		if (limiteMensal <= 0) {
			return "OK";
		}
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

	/** Se a categoria tem limite máximo de gastos. Sem escolha explícita, vale o limite gravado. */
	public Boolean getTemLimite() {
		return temLimite != null ? temLimite : limiteMensal > 0;
	}

	public void setTemLimite(Boolean temLimite) {
		this.temLimite = temLimite;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

}
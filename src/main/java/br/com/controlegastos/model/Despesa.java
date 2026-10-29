package br.com.controlegastos.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonCreator;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Despesa registrada pelo estudante (RF1).
 */
@Entity
public class Despesa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@NotBlank(message = "Informe a descrição")
	private String descricao;

	@Positive(message = "O valor deve ser maior que zero")
	private double valor;

	@NotNull(message = "Informe a data")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate data;

	@NotNull(message = "Escolha uma categoria")
	@ManyToOne(optional = false)
	private Categoria categoria;

	@JsonCreator
	public Despesa() {
	}

	public Despesa(String descricao, double valor, LocalDate data, Categoria categoria) {
		this.descricao = descricao;
		this.valor = valor;
		this.data = data;
		this.categoria = categoria;
	}

	public boolean pertenceAoMes(int mes, int ano) {
		return data != null && data.getMonthValue() == mes && data.getYear() == ano;
	}

	public Integer getId() {
		return id;
	}

	public String getDescricao() {
		return descricao;
	}

	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}

	public double getValor() {
		return valor;
	}

	public void setValor(double valor) {
		this.valor = valor;
	}

	public LocalDate getData() {
		return data;
	}

	public void setData(LocalDate data) {
		this.data = data;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

}

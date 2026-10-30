package br.com.controlegastos.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Receita registrada pelo estudante (RF2).
 */
@Entity
public class Receita {

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

	@NotNull(message = "Escolha uma fonte")
	@ManyToOne
	private Fonte fonte;

	@JsonIgnore
	@ManyToOne
	private Usuario usuario;

	@JsonCreator
	public Receita() {
	}

	public Receita(String descricao, double valor, LocalDate data, Fonte fonte, Usuario usuario) {
		this.descricao = descricao;
		this.valor = valor;
		this.data = data;
		this.fonte = fonte;
		this.usuario = usuario;
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

	public Fonte getFonte() {
		return fonte;
	}

	public void setFonte(Fonte fonte) {
		this.fonte = fonte;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

}

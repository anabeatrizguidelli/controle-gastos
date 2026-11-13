package br.com.controlegastos.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Receita registrada pelo estudante (RF2). Pode ser avulsa ou o lançamento de
 * um mês de uma receita fixa ({@link RecorrenciaReceita}).
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

	/** Receita fixa da qual este lançamento faz parte. Nulo nas avulsas (coluna nova, aceita nulo). */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	private RecorrenciaReceita recorrencia;

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

	public void vincularRecorrencia(RecorrenciaReceita recorrencia) {
		this.recorrencia = recorrencia;
	}

	/** Lançamento de uma receita fixa (mensal). Não carrega a recorrência do banco. */
	public boolean isFixa() {
		return recorrencia != null;
	}

	@JsonIgnore
	public RecorrenciaReceita getRecorrencia() {
		return recorrencia;
	}

	/** Id da receita fixa (aparece na API); nulo nas avulsas. */
	public Integer getRecorrenciaId() {
		return recorrencia == null ? null : recorrencia.getId();
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

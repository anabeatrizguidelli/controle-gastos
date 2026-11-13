package br.com.controlegastos.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Compra parcelada (a "despesa original"). Cada parcela é gravada como uma
 * {@link Despesa} própria, com valor, vencimento e status de pagamento, ligada
 * a este registro.
 */
@Entity
public class Parcelamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String descricao;

	/** Valor total da compra (soma exata de todas as parcelas). */
	private double valorTotal;

	private int quantidadeParcelas;

	private LocalDate primeiroVencimento;

	/** Data da compra. */
	private LocalDate data;

	@ManyToOne
	private Usuario usuario;

	protected Parcelamento() {
	}

	public Parcelamento(String descricao, double valorTotal, int quantidadeParcelas, LocalDate primeiroVencimento,
			LocalDate data, Usuario usuario) {
		this.descricao = descricao;
		this.valorTotal = valorTotal;
		this.quantidadeParcelas = quantidadeParcelas;
		this.primeiroVencimento = primeiroVencimento;
		this.data = data;
		this.usuario = usuario;
	}

	public Integer getId() {
		return id;
	}

	public String getDescricao() {
		return descricao;
	}

	public double getValorTotal() {
		return valorTotal;
	}

	public void setValorTotal(double valorTotal) {
		this.valorTotal = valorTotal;
	}

	public int getQuantidadeParcelas() {
		return quantidadeParcelas;
	}

	public LocalDate getPrimeiroVencimento() {
		return primeiroVencimento;
	}

	public LocalDate getData() {
		return data;
	}

	public Usuario getUsuario() {
		return usuario;
	}

}

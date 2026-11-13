package br.com.controlegastos.model;

import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

/**
 * Despesa fixa (recorrente mensal), ex.: aluguel, internet. Cada mês vira uma
 * {@link Despesa} própria (lançamento), com valor, vencimento e status de
 * pagamento, ligada a este registro. As regras de meses ficam em {@link RecorrenciaBase}.
 */
@Entity
public class Recorrencia extends RecorrenciaBase {

	@ManyToOne(fetch = FetchType.LAZY)
	private Categoria categoria;

	protected Recorrencia() {
	}

	/** Recorrência que começa com o lançamento {@code primeira} (já gravado). */
	public Recorrencia(Despesa primeira, Usuario usuario) {
		super(primeira.getDescricao(), primeira.getValor(), primeira.getVencimento(), usuario);
		this.categoria = primeira.getCategoria();
	}

	/** Novo lançamento (pendente) para o mês {@code mes}. */
	public Despesa ocorrencia(YearMonth mes) {
		LocalDate vence = vencimentoEm(mes);
		Despesa d = new Despesa(getDescricao(), getValor(), vence, categoria, getUsuario());
		d.setVencimento(vence);
		d.setPago(false);
		d.vincularRecorrencia(this);
		return d;
	}

	public Categoria getCategoria() {
		return categoria;
	}

}
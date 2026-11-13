package br.com.controlegastos.model;

import java.time.YearMonth;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

/**
 * Receita fixa (recorrente mensal), ex.: estágio, mesada. Cada mês vira uma
 * {@link Receita} própria, ligada a este registro. As regras de meses ficam
 * em {@link RecorrenciaBase} (as mesmas das despesas fixas).
 */
@Entity
public class RecorrenciaReceita extends RecorrenciaBase {

	@ManyToOne(fetch = FetchType.LAZY)
	private Fonte fonte;

	protected RecorrenciaReceita() {
	}

	/** Recorrência que começa com a receita {@code primeira} (já gravada). */
	public RecorrenciaReceita(Receita primeira, Usuario usuario) {
		super(primeira.getDescricao(), primeira.getValor(), primeira.getData(), usuario);
		this.fonte = primeira.getFonte();
	}

	/** Nova receita para o mês {@code mes}. */
	public Receita ocorrencia(YearMonth mes) {
		Receita r = new Receita(getDescricao(), getValor(), vencimentoEm(mes), fonte, getUsuario());
		r.vincularRecorrencia(this);
		return r;
	}

	public Fonte getFonte() {
		return fonte;
	}

}
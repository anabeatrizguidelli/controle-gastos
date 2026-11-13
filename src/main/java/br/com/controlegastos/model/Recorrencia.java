package br.com.controlegastos.model;

import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

/**
 * Despesa fixa (recorrente mensal), ex.: aluguel, internet. Cada mês vira uma
 * {@link Despesa} própria (ocorrência), com valor, vencimento e status de
 * pagamento, ligada a este registro.
 * <p>
 * {@code geradaAte} guarda o último mês já lançado: a geração só cria os meses
 * depois dele, então nunca duplica um mês nem recria uma ocorrência excluída.
 * {@code fim} é o último mês da recorrência (nulo enquanto ela estiver ativa).
 * Meses são gravados como o dia 1º.
 */
@Entity
public class Recorrencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String descricao;

	/** Valor usado nas próximas ocorrências. */
	private double valor;

	/** Dia do vencimento (1 a 31); em meses mais curtos vale o último dia. */
	private int diaVencimento;

	/** Mês da primeira ocorrência. */
	private LocalDate inicio;

	/** Último mês já lançado. */
	private LocalDate geradaAte;

	/** Último mês da recorrência; nulo = ativa, sem data para acabar. */
	private LocalDate fim;

	@ManyToOne(fetch = FetchType.LAZY)
	private Categoria categoria;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario usuario;

	/** Evita que duas requisições ao mesmo tempo lancem o mesmo mês. */
	@Version
	private Long versao;

	protected Recorrencia() {
	}

	/** Recorrência que começa com a ocorrência {@code primeira} (já lançada). */
	public Recorrencia(Despesa primeira, Usuario usuario) {
		this.descricao = primeira.getDescricao();
		this.valor = primeira.getValor();
		this.diaVencimento = primeira.getVencimento().getDayOfMonth();
		this.inicio = YearMonth.from(primeira.getVencimento()).atDay(1);
		this.geradaAte = this.inicio;
		this.categoria = primeira.getCategoria();
		this.usuario = usuario;
	}

	/** Vencimento no mês {@code mes}, no último dia quando o mês não tem o dia escolhido (ex.: 31 → 28/02). */
	public LocalDate vencimentoEm(YearMonth mes) {
		return mes.atDay(Math.min(diaVencimento, mes.lengthOfMonth()));
	}

	/** Nova ocorrência (pendente) para o mês {@code mes}. */
	public Despesa ocorrencia(YearMonth mes) {
		LocalDate vence = vencimentoEm(mes);
		Despesa d = new Despesa(descricao, valor, vence, categoria, usuario);
		d.setVencimento(vence);
		d.setPago(false);
		d.vincularRecorrencia(this);
		return d;
	}

	/** Encerra a recorrência: {@code ultimoMes} é o último mês que continua valendo. */
	public void encerrar(YearMonth ultimoMes) {
		this.fim = ultimoMes.atDay(1);
	}

	public boolean isAtiva() {
		return fim == null;
	}

	public Integer getId() {
		return id;
	}

	public String getDescricao() {
		return descricao;
	}

	public double getValor() {
		return valor;
	}

	public void setValor(double valor) {
		this.valor = valor;
	}

	public int getDiaVencimento() {
		return diaVencimento;
	}

	public LocalDate getInicio() {
		return inicio;
	}

	public LocalDate getGeradaAte() {
		return geradaAte;
	}

	public void setGeradaAte(LocalDate geradaAte) {
		this.geradaAte = geradaAte;
	}

	public LocalDate getFim() {
		return fim;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public Usuario getUsuario() {
		return usuario;
	}

}
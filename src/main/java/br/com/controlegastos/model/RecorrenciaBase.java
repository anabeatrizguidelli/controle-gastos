package br.com.controlegastos.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * O que despesas fixas ({@link Recorrencia}) e receitas fixas
 * ({@link RecorrenciaReceita}) têm em comum: cada mês vira um lançamento
 * próprio, criado uma vez só.
 * <p>
 * {@code geradaAte} guarda o último mês já lançado: a geração só cria os meses
 * depois dele, então nunca duplica um mês nem recria um lançamento excluído.
 * {@code fim} é o último mês da recorrência (nulo enquanto ela estiver ativa).
 * Meses são gravados como o dia 1º.
 */
@MappedSuperclass
public abstract class RecorrenciaBase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String descricao;

	/** Valor usado nos próximos lançamentos. */
	private double valor;

	/** Dia do mês (1 a 31); em meses mais curtos vale o último dia. */
	private int diaVencimento;

	/** Mês do primeiro lançamento. */
	private LocalDate inicio;

	/** Último mês já lançado. */
	private LocalDate geradaAte;

	/** Último mês da recorrência; nulo = ativa, sem data para acabar. */
	private LocalDate fim;

	@ManyToOne(fetch = FetchType.LAZY)
	private Usuario usuario;

	/** Evita que duas requisições ao mesmo tempo lancem o mesmo mês. */
	@Version
	private Long versao;

	protected RecorrenciaBase() {
	}

	/** Recorrência que começa com um lançamento já gravado em {@code primeiraData}. */
	protected RecorrenciaBase(String descricao, double valor, LocalDate primeiraData, Usuario usuario) {
		this.descricao = descricao;
		this.valor = valor;
		this.diaVencimento = primeiraData.getDayOfMonth();
		this.inicio = YearMonth.from(primeiraData).atDay(1);
		this.geradaAte = this.inicio;
		this.usuario = usuario;
	}

	/** Dia do lançamento no mês {@code mes}, no último dia quando o mês não tem o dia escolhido (ex.: 31 → 28/02). */
	public LocalDate vencimentoEm(YearMonth mes) {
		return mes.atDay(Math.min(diaVencimento, mes.lengthOfMonth()));
	}

	/**
	 * Meses que ainda faltam lançar até {@code alvo} (respeitando o fim) e
	 * marca-os como lançados. Chamar de novo não devolve os mesmos meses.
	 */
	public List<YearMonth> mesesALancarAte(YearMonth alvo) {
		YearMonth ate = fim == null || YearMonth.from(fim).isAfter(alvo) ? alvo : YearMonth.from(fim);
		List<YearMonth> meses = new ArrayList<>();
		for (YearMonth m = YearMonth.from(geradaAte).plusMonths(1); !m.isAfter(ate); m = m.plusMonths(1)) {
			meses.add(m);
		}
		if (!meses.isEmpty()) {
			geradaAte = ate.atDay(1);
		}
		return meses;
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

	public LocalDate getFim() {
		return fim;
	}

	public Usuario getUsuario() {
		return usuario;
	}

}
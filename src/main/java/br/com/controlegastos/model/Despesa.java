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
 * Despesa registrada pelo estudante (RF1). Cada registro é um lançamento com
 * vencimento próprio: uma despesa à vista, uma parcela de uma compra
 * parcelada ({@link Parcelamento}) ou o lançamento de um mês de uma despesa
 * fixa ({@link Recorrencia}). Uma despesa não é fixa e parcelada ao mesmo tempo.
 * <p>
 * Os campos {@code vencimento}, {@code pago}, {@code numeroParcela},
 * {@code totalParcelas} e {@code parcelamento} foram adicionados depois e
 * aceitam nulo no banco, para que os registros antigos continuem valendo:
 * sem vencimento, vale a data da despesa; sem status, a despesa antiga conta
 * como paga (era assim que o saldo já a considerava).
 */
@Entity
public class Despesa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@NotBlank(message = "Informe a descrição")
	private String descricao;

	/** Valor deste lançamento (numa compra parcelada, o valor da parcela). */
	@Positive(message = "O valor deve ser maior que zero")
	private double valor;

	/** Data da compra / do lançamento. */
	@NotNull(message = "Informe a data")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate data;

	/** Data de vencimento; define em qual mês o lançamento aparece. Nulo nos registros antigos. */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate vencimento;

	/** {@code true} = Pago, {@code false} = Pendente, nulo = registro antigo (conta como pago). */
	private Boolean pago;

	/** Número desta parcela (1, 2, 3...). Nulo nas despesas à vista. */
	private Integer numeroParcela;

	/** Quantidade total de parcelas da compra. Nulo nas despesas à vista. */
	private Integer totalParcelas;

	/** Compra parcelada à qual esta parcela pertence. Nulo nas despesas à vista. */
	@JsonIgnore
	@ManyToOne
	private Parcelamento parcelamento;

	/** Despesa fixa da qual este lançamento faz parte. Nulo nas demais (coluna nova, aceita nulo). */
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	private Recorrencia recorrencia;

	@NotNull(message = "Escolha uma categoria")
	@ManyToOne
	private Categoria categoria;

	@JsonIgnore
	@ManyToOne
	private Usuario usuario;

	@JsonCreator
	public Despesa() {
	}

	public Despesa(String descricao, double valor, LocalDate data, Categoria categoria) {
		this.descricao = descricao;
		this.valor = valor;
		this.data = data;
		this.categoria = categoria;
	}

	public Despesa(String descricao, double valor, LocalDate data, Categoria categoria, Usuario usuario) {
		this(descricao, valor, data, categoria);
		this.usuario = usuario;
	}

	/** O mês do lançamento é o mês do vencimento. */
	public boolean pertenceAoMes(int mes, int ano) {
		LocalDate v = getVencimento();
		return v != null && v.getMonthValue() == mes && v.getYear() == ano;
	}

	/** Liga esta despesa a uma compra parcelada como a parcela {@code numero} de {@code total}. */
	public void vincularParcela(Parcelamento parcelamento, int numero, int total) {
		this.parcelamento = parcelamento;
		this.numeroParcela = numero;
		this.totalParcelas = total;
	}

	/** Remove qualquer informação de parcela (usado em despesas à vista). */
	public void desvincularParcela() {
		this.parcelamento = null;
		this.numeroParcela = null;
		this.totalParcelas = null;
	}

	/** Liga este lançamento a uma despesa fixa (e tira qualquer informação de parcela). */
	public void vincularRecorrencia(Recorrencia recorrencia) {
		desvincularParcela();
		this.recorrencia = recorrencia;
	}

	/** Lançamento de uma despesa fixa (mensal). Não carrega a recorrência do banco. */
	public boolean isFixa() {
		return recorrencia != null;
	}

	public boolean isParcelada() {
		return totalParcelas != null && totalParcelas > 1;
	}

	/** Ex.: "3/14", "Fixa" ou "À vista". */
	@JsonIgnore
	public String getRotuloParcela() {
		return isParcelada() ? numeroParcela + "/" + totalParcelas : isFixa() ? "Fixa" : "À vista";
	}

	/** "Pago" ou "Pendente". */
	public String getStatus() {
		return isPago() ? "Pago" : "Pendente";
	}

	/** Verdadeiro quando o pagamento ainda não foi informado (nem pago, nem pendente). */
	public boolean pagamentoNaoInformado() {
		return pago == null;
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

	/** Vencimento do lançamento; nos registros antigos (sem vencimento) é a própria data. */
	public LocalDate getVencimento() {
		return vencimento != null ? vencimento : data;
	}

	public void setVencimento(LocalDate vencimento) {
		this.vencimento = vencimento;
	}

	/** Registros antigos, sem status, contam como pagos. */
	public boolean isPago() {
		return pago == null || pago;
	}

	public void setPago(Boolean pago) {
		this.pago = pago;
	}

	public Integer getNumeroParcela() {
		return numeroParcela;
	}

	public Integer getTotalParcelas() {
		return totalParcelas;
	}

	@JsonIgnore
	public Parcelamento getParcelamento() {
		return parcelamento;
	}

	/** Id da compra parcelada (aparece na API); nulo nas despesas à vista. */
	public Integer getParcelamentoId() {
		return parcelamento == null ? null : parcelamento.getId();
	}

	@JsonIgnore
	public Recorrencia getRecorrencia() {
		return recorrencia;
	}

	/** Id da despesa fixa (aparece na API); nulo nas demais. */
	public Integer getRecorrenciaId() {
		return recorrencia == null ? null : recorrencia.getId();
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

}

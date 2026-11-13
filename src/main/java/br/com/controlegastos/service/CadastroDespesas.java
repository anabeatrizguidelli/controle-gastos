package br.com.controlegastos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Parcelamento;
import br.com.controlegastos.model.Recorrencia;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.ParcelamentoRepository;
import br.com.controlegastos.repository.RecorrenciaRepository;

/**
 * Cadastro de despesas à vista, parceladas e fixas, pagamento de parcelas e exclusão.
 */
@Service
public class CadastroDespesas {

	private final DespesaRepository despesas;
	private final ParcelamentoRepository parcelamentos;
	private final RecorrenciaRepository recorrencias;

	public CadastroDespesas(DespesaRepository despesas, ParcelamentoRepository parcelamentos,
			RecorrenciaRepository recorrencias) {
		this.despesas = despesas;
		this.parcelamentos = parcelamentos;
		this.recorrencias = recorrencias;
	}

	/**
	 * Registra uma despesa fixa (mensal): grava o primeiro mês, no vencimento
	 * informado, e a recorrência que lança os meses seguintes (ver {@link DespesasFixas}).
	 */
	@Transactional
	public Despesa registrarFixa(Despesa dados, Usuario usuario) {
		dados.setUsuario(usuario);
		dados.setVencimento(dados.getVencimento());
		dados.setValor(emCentavos(dados.getValor()).doubleValue());
		dados.setPago(false);
		Recorrencia fixa = recorrencias.save(new Recorrencia(dados, usuario));
		dados.vincularRecorrencia(fixa);
		return despesas.save(dados);
	}

	/**
	 * Encerra a despesa fixa a partir do mês deste lançamento: ele e os meses
	 * seguintes ainda pendentes são excluídos e nenhum mês novo é lançado. Os
	 * meses anteriores (e qualquer mês já pago) continuam no histórico.
	 */
	@Transactional
	public void encerrarFixa(Despesa despesa) {
		Recorrencia fixa = despesa.getRecorrencia();
		fixa.encerrar(YearMonth.from(despesa.getVencimento()).minusMonths(1));
		recorrencias.save(fixa);
		List<Despesa> seguintes = despesas.findByRecorrenciaAndVencimentoAfterAndPagoFalse(fixa, despesa.getVencimento());
		despesas.deleteAll(seguintes);
		despesas.delete(despesa);
		despesas.flush();
		if (despesas.countByRecorrencia(fixa) == 0) {
			recorrencias.delete(fixa);
		}
	}

	/**
	 * Registra a despesa. Com 1 parcela grava um único lançamento; com mais,
	 * grava a compra ({@link Parcelamento}) e uma despesa por parcela, cada uma
	 * no mês do seu vencimento. Tudo começa como "Pendente".
	 *
	 * @param dados descrição, valor total, data, vencimento da 1ª parcela e categoria
	 */
	@Transactional
	public List<Despesa> registrar(Despesa dados, int quantidadeParcelas, Usuario usuario) {
		LocalDate primeiro = dados.getVencimento();
		List<GeradorParcelas.Parcela> geradas = GeradorParcelas.gerar(BigDecimal.valueOf(dados.getValor()),
				quantidadeParcelas, primeiro);
		if (quantidadeParcelas == 1) {
			dados.setUsuario(usuario);
			dados.setVencimento(primeiro);
			dados.setValor(geradas.get(0).valor().doubleValue());
			dados.setPago(false);
			dados.desvincularParcela();
			return List.of(despesas.save(dados));
		}
		BigDecimal total = geradas.stream().map(GeradorParcelas.Parcela::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
		Parcelamento compra = parcelamentos.save(new Parcelamento(dados.getDescricao(), total.doubleValue(),
				quantidadeParcelas, primeiro, dados.getData(), usuario));
		List<Despesa> lista = new ArrayList<>();
		for (GeradorParcelas.Parcela p : geradas) {
			Despesa parcela = new Despesa(dados.getDescricao(), p.valor().doubleValue(), dados.getData(),
					dados.getCategoria(), usuario);
			parcela.setVencimento(p.vencimento());
			parcela.setPago(false);
			parcela.vincularParcela(compra, p.numero(), quantidadeParcelas);
			lista.add(parcela);
		}
		return despesas.saveAll(lista);
	}

	/**
	 * Salva a edição de uma despesa ou parcela (descrição, valor, data,
	 * vencimento e categoria valem só para ela).
	 * <p>
	 * Numa compra parcelada, com {@code aplicarNasDemaisPendentes} o novo valor
	 * também passa para as outras parcelas ainda <strong>pendentes</strong> da
	 * mesma compra; sem ele, as outras mantêm o valor que já tinham. Parcelas
	 * pagas nunca são alteradas. Em todos os casos o valor total da compra é
	 * recalculado como a soma das parcelas.
	 *
	 * @return quantas outras parcelas tiveram o valor alterado
	 */
	@Transactional
	public int atualizar(Despesa existente, Despesa dados, boolean aplicarNasDemaisPendentes) {
		double novoValor = emCentavos(dados.getValor()).doubleValue();
		existente.setDescricao(dados.getDescricao());
		existente.setValor(novoValor);
		existente.setData(dados.getData());
		if (dados.getVencimento() != null) {
			existente.setVencimento(dados.getVencimento());
		}
		existente.setCategoria(dados.getCategoria());
		despesas.save(existente);
		if (existente.isFixa()) {
			return aplicarNaFixa(existente, novoValor, aplicarNasDemaisPendentes);
		}
		Parcelamento compra = existente.getParcelamento();
		if (compra == null) {
			return 0;
		}
		List<Despesa> parcelas = despesas.findByParcelamentoOrderByNumeroParcelaAsc(compra);
		int alteradas = 0;
		if (aplicarNasDemaisPendentes) {
			for (Despesa outra : parcelas) {
				if (!outra.getId().equals(existente.getId()) && !outra.isPago()
						&& emCentavos(outra.getValor()).compareTo(emCentavos(novoValor)) != 0) {
					outra.setValor(novoValor);
					alteradas++;
				}
			}
			despesas.saveAll(parcelas);
		}
		recalcularTotal(compra, parcelas);
		return alteradas;
	}

	/**
	 * Despesa fixa: com {@code aplicar}, o novo valor passa a valer para os
	 * próximos meses (os já lançados e ainda pendentes, e os que serão lançados).
	 * Meses anteriores nunca mudam.
	 */
	private int aplicarNaFixa(Despesa existente, double novoValor, boolean aplicar) {
		if (!aplicar) {
			return 0;
		}
		Recorrencia fixa = existente.getRecorrencia();
		fixa.setValor(novoValor);
		recorrencias.save(fixa);
		int alteradas = 0;
		for (Despesa outra : despesas.findByRecorrenciaAndVencimentoAfterAndPagoFalse(fixa, existente.getVencimento())) {
			if (emCentavos(outra.getValor()).compareTo(emCentavos(novoValor)) != 0) {
				outra.setValor(novoValor);
				alteradas++;
			}
		}
		return alteradas;
	}

	/** Quantas outras parcelas da mesma compra ainda estão pendentes (0 para despesas à vista). */
	public long outrasParcelasPendentes(Despesa despesa) {
		if (despesa.getParcelamento() == null) {
			return 0;
		}
		return despesas.findByParcelamentoOrderByNumeroParcelaAsc(despesa.getParcelamento()).stream()
				.filter(p -> !p.getId().equals(despesa.getId()) && !p.isPago()).count();
	}

	/** O valor total da compra é sempre a soma exata das parcelas que ela tem. */
	private void recalcularTotal(Parcelamento compra, List<Despesa> parcelas) {
		BigDecimal soma = parcelas.stream().map(p -> emCentavos(p.getValor())).reduce(BigDecimal.ZERO, BigDecimal::add);
		compra.setValorTotal(soma.doubleValue());
		parcelamentos.save(compra);
	}

	private static BigDecimal emCentavos(double valor) {
		return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
	}

	/** Marca (ou desmarca) só esta parcela como paga. Repetir o mesmo valor não muda nada. */
	@Transactional
	public Despesa marcarPagamento(Despesa despesa, boolean pago) {
		despesa.setPago(pago);
		return despesas.save(despesa);
	}

	/**
	 * Exclui a despesa. Numa compra parcelada, {@code todasAsParcelas} exclui a
	 * compra inteira; caso contrário só esta parcela (as outras mantêm a
	 * numeração, ex.: 3/14). Quando não sobra nenhuma parcela, a compra também é
	 * removida.
	 */
	@Transactional
	public void excluir(Despesa despesa, boolean todasAsParcelas) {
		Parcelamento compra = despesa.getParcelamento();
		if (compra != null && todasAsParcelas) {
			despesas.deleteAll(despesas.findByParcelamentoOrderByNumeroParcelaAsc(compra));
			despesas.flush();
			parcelamentos.delete(compra);
			return;
		}
		despesas.delete(despesa);
		if (compra != null) {
			despesas.flush();
			if (despesas.countByParcelamento(compra) == 0) {
				parcelamentos.delete(compra);
			}
		}
	}

}

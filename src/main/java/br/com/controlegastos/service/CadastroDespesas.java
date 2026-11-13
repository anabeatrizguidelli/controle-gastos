package br.com.controlegastos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Parcelamento;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.ParcelamentoRepository;

/**
 * Cadastro de despesas à vista e parceladas, pagamento de parcelas e exclusão.
 */
@Service
public class CadastroDespesas {

	private final DespesaRepository despesas;
	private final ParcelamentoRepository parcelamentos;

	public CadastroDespesas(DespesaRepository despesas, ParcelamentoRepository parcelamentos) {
		this.despesas = despesas;
		this.parcelamentos = parcelamentos;
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

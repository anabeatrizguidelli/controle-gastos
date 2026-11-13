package br.com.controlegastos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.RecorrenciaReceita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.repository.RecorrenciaReceitaRepository;

/**
 * Cadastro, edição e exclusão de receitas avulsas e fixas (mensais). Os meses
 * seguintes de uma receita fixa são lançados por {@link LancamentosFixos}.
 */
@Service
public class CadastroReceitas {

	/** O que excluir de uma receita fixa. */
	public enum Exclusao {
		/** Só este mês; a receita fixa continua nos próximos. */
		ESTE,
		/** Este mês e os seguintes; encerra a receita fixa e mantém os meses anteriores. */
		ESTE_E_SEGUINTES,
		/** Todos os meses, inclusive os anteriores, e a própria receita fixa. */
		TUDO
	}

	private final ReceitaRepository receitas;
	private final RecorrenciaReceitaRepository recorrencias;

	public CadastroReceitas(ReceitaRepository receitas, RecorrenciaReceitaRepository recorrencias) {
		this.receitas = receitas;
		this.recorrencias = recorrencias;
	}

	/** Grava a receita; se {@code fixa}, ela é o primeiro mês de uma receita fixa. */
	@Transactional
	public Receita registrar(Receita dados, boolean fixa, Usuario usuario) {
		dados.setUsuario(usuario);
		dados.setValor(emCentavos(dados.getValor()));
		if (fixa) {
			dados.vincularRecorrencia(recorrencias.save(new RecorrenciaReceita(dados, usuario)));
		}
		return receitas.save(dados);
	}

	/**
	 * Salva a edição deste mês (descrição, valor, data e fonte). Numa receita
	 * fixa, com {@code seguintes} o novo valor passa a valer também para os
	 * meses seguintes (já lançados e futuros). Meses anteriores nunca mudam.
	 *
	 * @return quantos meses seguintes tiveram o valor alterado
	 */
	@Transactional
	public int atualizar(Receita existente, Receita dados, boolean seguintes) {
		double novoValor = emCentavos(dados.getValor());
		existente.setDescricao(dados.getDescricao());
		existente.setValor(novoValor);
		existente.setData(dados.getData());
		existente.setFonte(dados.getFonte());
		receitas.save(existente);
		RecorrenciaReceita fixa = existente.getRecorrencia();
		if (fixa == null || !seguintes) {
			return 0;
		}
		fixa.setValor(novoValor);
		recorrencias.save(fixa);
		int alteradas = 0;
		for (Receita outra : receitas.findByRecorrenciaAndDataAfter(fixa, existente.getData())) {
			if (emCentavos(outra.getValor()) != novoValor) {
				outra.setValor(novoValor);
				alteradas++;
			}
		}
		return alteradas;
	}

	/** Exclui a receita. Nas avulsas, {@code modo} é ignorado. */
	@Transactional
	public void excluir(Receita receita, Exclusao modo) {
		RecorrenciaReceita fixa = receita.getRecorrencia();
		if (fixa == null || modo == Exclusao.ESTE) {
			receitas.delete(receita);
			return;
		}
		if (modo == Exclusao.TUDO) {
			receitas.deleteAll(receitas.findByRecorrencia(fixa));
		} else {
			fixa.encerrar(YearMonth.from(receita.getData()).minusMonths(1));
			recorrencias.save(fixa);
			List<Receita> seguintes = receitas.findByRecorrenciaAndDataAfter(fixa, receita.getData());
			receitas.deleteAll(seguintes);
			receitas.delete(receita);
		}
		receitas.flush();
		if (modo == Exclusao.TUDO || receitas.countByRecorrencia(fixa) == 0) {
			recorrencias.delete(fixa);
		}
	}

	private static double emCentavos(double valor) {
		return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

}
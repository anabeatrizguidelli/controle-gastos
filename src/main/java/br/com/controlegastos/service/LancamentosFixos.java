package br.com.controlegastos.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Recorrencia;
import br.com.controlegastos.model.RecorrenciaReceita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.repository.RecorrenciaReceitaRepository;
import br.com.controlegastos.repository.RecorrenciaRepository;

/**
 * Lança os meses das despesas e receitas fixas. Não depende de agendamento (o
 * Render Free dorme): as páginas chamam {@link #lancarAte} e só os meses que
 * faltam são criados, até o mês consultado (no máximo {@value #MESES_A_FRENTE}
 * meses depois do atual). Depois da primeira visita do mês, o custo são duas
 * consultas que não encontram nada.
 */
@Service
public class LancamentosFixos {

	/** Até quantos meses à frente do atual um lançamento fixo pode ser criado ao navegar. */
	public static final int MESES_A_FRENTE = 12;

	private final RecorrenciaRepository recorrencias;
	private final RecorrenciaReceitaRepository recorrenciasReceita;
	private final DespesaRepository despesas;
	private final ReceitaRepository receitas;
	private final TransactionTemplate transacao;

	public LancamentosFixos(RecorrenciaRepository recorrencias, RecorrenciaReceitaRepository recorrenciasReceita,
			DespesaRepository despesas, ReceitaRepository receitas, PlatformTransactionManager transacoes) {
		this.recorrencias = recorrencias;
		this.recorrenciasReceita = recorrenciasReceita;
		this.despesas = despesas;
		this.receitas = receitas;
		this.transacao = new TransactionTemplate(transacoes);
	}

	/** Lança os meses que faltam até {@code consultado} (ou até o mês atual, se for depois). */
	public void lancarAte(Usuario usuario, YearMonth consultado) {
		lancarAte(usuario, consultado, YearMonth.now());
	}

	/** Igual a {@link #lancarAte(Usuario, YearMonth)}, com o mês atual informado (usado nos testes). */
	public void lancarAte(Usuario usuario, YearMonth consultado, YearMonth atual) {
		YearMonth limite = atual.plusMonths(MESES_A_FRENTE);
		YearMonth alvo = consultado.isBefore(atual) ? atual : consultado.isAfter(limite) ? limite : consultado;
		try {
			transacao.executeWithoutResult(s -> lancar(usuario, alvo));
		} catch (OptimisticLockingFailureException outraRequisicaoJaLancou) {
			// Outra requisição lançou os mesmos meses ao mesmo tempo; esta foi desfeita e nada duplicou.
		}
	}

	private void lancar(Usuario usuario, YearMonth alvo) {
		for (Recorrencia r : recorrencias.comMesesALancar(usuario, alvo.atDay(1))) {
			List<Despesa> novas = new ArrayList<>();
			r.mesesALancarAte(alvo).forEach(m -> novas.add(r.ocorrencia(m)));
			despesas.saveAll(novas);
			recorrencias.save(r);
		}
		for (RecorrenciaReceita r : recorrenciasReceita.comMesesALancar(usuario, alvo.atDay(1))) {
			List<Receita> novas = new ArrayList<>();
			r.mesesALancarAte(alvo).forEach(m -> novas.add(r.ocorrencia(m)));
			receitas.saveAll(novas);
			recorrenciasReceita.save(r);
		}
	}

}
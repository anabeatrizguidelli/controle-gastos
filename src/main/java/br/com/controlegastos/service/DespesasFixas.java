package br.com.controlegastos.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Recorrencia;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.RecorrenciaRepository;

/**
 * Lança os meses das despesas fixas. Não depende de agendamento (o Render
 * Free dorme): as páginas chamam {@link #lancarAte} e só os meses que faltam
 * são criados, até o mês consultado (no máximo {@value #MESES_A_FRENTE} meses
 * depois do atual). Depois da primeira visita do mês, o custo é uma consulta
 * que não encontra nada.
 */
@Service
public class DespesasFixas {

	/** Até quantos meses à frente do atual uma despesa fixa pode ser lançada ao navegar. */
	public static final int MESES_A_FRENTE = 12;

	private final RecorrenciaRepository recorrencias;
	private final DespesaRepository despesas;
	private final TransactionTemplate transacao;

	public DespesasFixas(RecorrenciaRepository recorrencias, DespesaRepository despesas,
			PlatformTransactionManager transacoes) {
		this.recorrencias = recorrencias;
		this.despesas = despesas;
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
			YearMonth ate = r.getFim() == null || YearMonth.from(r.getFim()).isAfter(alvo) ? alvo : YearMonth.from(r.getFim());
			List<Despesa> novas = new ArrayList<>();
			for (YearMonth m = YearMonth.from(r.getGeradaAte()).plusMonths(1); !m.isAfter(ate); m = m.plusMonths(1)) {
				novas.add(r.ocorrencia(m));
			}
			despesas.saveAll(novas);
			r.setGeradaAte(ate.atDay(1));
			recorrencias.save(r);
		}
	}

}
package br.com.controlegastos.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.DespesasFixas;
import br.com.controlegastos.service.ResumoCategoria;
import br.com.controlegastos.service.ResumoMes;
import br.com.controlegastos.service.TotaisMes;

/**
 * Painel de resumo financeiro do mês selecionado (RF4 e RF5).
 */
@Controller
public class PainelController {

	private final ControleGastos controle;
	private final DespesasFixas fixas;
	private final UsuarioAtual usuarioAtual;

	public PainelController(ControleGastos controle, DespesasFixas fixas, UsuarioAtual usuarioAtual) {
		this.controle = controle;
		this.fixas = fixas;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping("/painel")
	public String painel(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		YearMonth m = mes != null ? mes : YearMonth.now();
		Usuario usuario = usuarioAtual.get();
		fixas.lancarAte(usuario, m);
		// As listas do mês são carregadas uma vez só; totais, categorias e "últimas" saem delas.
		List<Despesa> despesasDoMes = controle.listarDespesas(usuario, m);
		List<Receita> receitasDoMes = controle.listarReceitas(usuario, m);
		TotaisMes totais = TotaisMes.de(receitasDoMes, despesasDoMes);
		double saldoAnterior = controle.saldoAnterior(usuario, m.getMonthValue(), m.getYear());
		List<ResumoCategoria> categorias = controle.resumoPorCategoria(usuario, despesasDoMes);
		List<ResumoMes> historico = controle.historico(usuario, m, 6);
		double maiorDoHistorico = historico.stream()
				.mapToDouble(h -> Math.max(h.receitas(), h.despesas())).max().orElse(0);

		model.addAttribute("mes", m);
		model.addAttribute("receitas", totais.receitas());
		model.addAttribute("despesas", totais.despesas());
		model.addAttribute("despesasPagas", totais.pagas());
		model.addAttribute("despesasPendentes", totais.pendentes());
		model.addAttribute("saldo", totais.resultado());
		model.addAttribute("saldoAnterior", saldoAnterior);
		model.addAttribute("saldoDisponivel", saldoAnterior + totais.resultado());
		model.addAttribute("saldoProjetado", totais.saldoProjetado());
		model.addAttribute("alertas", ControleGastos.alertas(categorias));
		model.addAttribute("categorias", categorias);
		model.addAttribute("pizza", ControleGastos.graficoPizza(categorias));
		model.addAttribute("historico", historico);
		model.addAttribute("maiorDoHistorico", maiorDoHistorico > 0 ? maiorDoHistorico : 1);
		model.addAttribute("ultimasDespesas", despesasDoMes.stream().limit(5).toList());
		model.addAttribute("ultimasReceitas", receitasDoMes.stream().limit(5).toList());
		return "painel";
	}

}

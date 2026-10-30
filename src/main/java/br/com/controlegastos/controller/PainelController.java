package br.com.controlegastos.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.ResumoCategoria;
import br.com.controlegastos.service.ResumoMes;

/**
 * Painel de resumo financeiro do mês selecionado (RF4 e RF5).
 */
@Controller
public class PainelController {

	private final ControleGastos controle;
	private final UsuarioAtual usuarioAtual;

	public PainelController(ControleGastos controle, UsuarioAtual usuarioAtual) {
		this.controle = controle;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping("/painel")
	public String painel(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		YearMonth m = mes != null ? mes : YearMonth.now();
		Usuario usuario = usuarioAtual.get();
		List<ResumoCategoria> categorias = controle.resumoPorCategoria(usuario, m);
		List<ResumoMes> historico = controle.historico(usuario, m, 6);
		double maiorDoHistorico = historico.stream()
				.mapToDouble(h -> Math.max(h.receitas(), h.despesas())).max().orElse(0);

		model.addAttribute("mes", m);
		model.addAttribute("receitas", controle.totalReceitasMes(usuario, m.getMonthValue(), m.getYear()));
		model.addAttribute("despesas", controle.totalDespesasMes(usuario, m.getMonthValue(), m.getYear()));
		model.addAttribute("saldo", controle.calcularSaldo(usuario, m.getMonthValue(), m.getYear()));
		model.addAttribute("saldoAnterior", controle.saldoAnterior(usuario, m.getMonthValue(), m.getYear()));
		model.addAttribute("saldoDisponivel", controle.saldoDisponivel(usuario, m.getMonthValue(), m.getYear()));
		model.addAttribute("alertas", controle.verificarAlertas(usuario, m.getMonthValue(), m.getYear()));
		model.addAttribute("categorias", categorias);
		model.addAttribute("pizza", ControleGastos.graficoPizza(categorias));
		model.addAttribute("historico", historico);
		model.addAttribute("maiorDoHistorico", maiorDoHistorico > 0 ? maiorDoHistorico : 1);
		model.addAttribute("ultimasDespesas", controle.listarDespesas(usuario, m).stream().limit(5).toList());
		model.addAttribute("ultimasReceitas", controle.listarReceitas(usuario, m).stream().limit(5).toList());
		return "painel";
	}

}

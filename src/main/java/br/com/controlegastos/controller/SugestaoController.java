package br.com.controlegastos.controller;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.AnaliseGastos;

/**
 * Aba "Sugestões": onde a pessoa mais gasta e como pode melhorar.
 */
@Controller
public class SugestaoController {

	private final AnaliseGastos analise;
	private final UsuarioAtual usuarioAtual;

	public SugestaoController(AnaliseGastos analise, UsuarioAtual usuarioAtual) {
		this.analise = analise;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping("/sugestoes")
	public String sugestoes(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		YearMonth m = mes != null ? mes : YearMonth.now();
		model.addAttribute("mes", m);
		model.addAttribute("analise", analise.analisar(usuarioAtual.get(), m, LocalDate.now()));
		return "sugestoes";
	}

}

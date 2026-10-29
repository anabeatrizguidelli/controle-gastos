package br.com.controlegastos.controller;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import jakarta.validation.Valid;

/**
 * Páginas web (Thymeleaf) para listar e cadastrar despesas.
 */
@Controller
@RequestMapping("/despesas")
public class DespesaController {

	private final DespesaRepository despesas;
	private final CategoriaRepository categorias;

	public DespesaController(DespesaRepository despesas, CategoriaRepository categorias) {
		this.despesas = despesas;
		this.categorias = categorias;
	}

	@GetMapping
	public String listar(Model model) {
		model.addAttribute("despesas", despesas.findAll());
		return "despesas/lista";
	}

	@GetMapping("/nova")
	public String formulario(Model model) {
		Despesa despesa = new Despesa();
		despesa.setData(LocalDate.now());
		model.addAttribute("despesa", despesa);
		model.addAttribute("categorias", categorias.findAll());
		return "despesas/form";
	}

	@PostMapping
	public String registrar(@Valid @ModelAttribute("despesa") Despesa despesa, BindingResult erros, Model model) {
		if (erros.hasErrors()) {
			model.addAttribute("categorias", categorias.findAll());
			return "despesas/form";
		}
		despesas.save(despesa);
		return "redirect:/despesas";
	}

}

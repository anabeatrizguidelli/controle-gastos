package br.com.controlegastos.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.GerenciaFontes;
import jakarta.validation.Valid;

/**
 * Páginas para listar, cadastrar, editar e excluir receitas (RF2).
 */
@Controller
@RequestMapping("/receitas")
public class ReceitaController {

	private final ReceitaRepository receitas;
	private final GerenciaFontes fontes;
	private final ControleGastos controle;
	private final UsuarioAtual usuarioAtual;

	public ReceitaController(ReceitaRepository receitas, GerenciaFontes fontes, ControleGastos controle,
			UsuarioAtual usuarioAtual) {
		this.receitas = receitas;
		this.fontes = fontes;
		this.controle = controle;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public String listar(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		YearMonth m = mes != null ? mes : YearMonth.now();
		List<Receita> lista = controle.listarReceitas(usuarioAtual.get(), m);
		model.addAttribute("mes", m);
		model.addAttribute("receitas", lista);
		model.addAttribute("total", lista.stream().mapToDouble(Receita::getValor).sum());
		return "receitas/lista";
	}

	@GetMapping("/nova")
	public String nova(Model model) {
		Receita receita = new Receita();
		receita.setData(LocalDate.now());
		return formulario(receita, null, model);
	}

	@PostMapping
	public String criar(@Valid @ModelAttribute("receita") Receita receita, BindingResult erros, Model model,
			RedirectAttributes redirect) {
		Usuario usuario = usuarioAtual.get();
		validarFonte(receita, usuario, erros);
		if (erros.hasErrors()) {
			return formulario(receita, null, model);
		}
		receita.setUsuario(usuario);
		receitas.save(receita);
		redirect.addFlashAttribute("sucesso", "Receita registrada.");
		return "redirect:/receitas?mes=" + YearMonth.from(receita.getData());
	}

	@GetMapping("/{id}/editar")
	public String editar(@PathVariable Integer id, Model model) {
		return formulario(buscar(id), id, model);
	}

	@PostMapping("/{id}")
	public String atualizar(@PathVariable Integer id, @Valid @ModelAttribute("receita") Receita dados,
			BindingResult erros, Model model, RedirectAttributes redirect) {
		Receita existente = buscar(id);
		validarFonte(dados, existente.getUsuario(), erros);
		if (erros.hasErrors()) {
			return formulario(dados, id, model);
		}
		existente.setDescricao(dados.getDescricao());
		existente.setValor(dados.getValor());
		existente.setData(dados.getData());
		existente.setFonte(dados.getFonte());
		receitas.save(existente);
		redirect.addFlashAttribute("sucesso", "Receita atualizada.");
		return "redirect:/receitas?mes=" + YearMonth.from(existente.getData());
	}

	@PostMapping("/{id}/excluir")
	public String excluir(@PathVariable Integer id, RedirectAttributes redirect) {
		Receita receita = buscar(id);
		receitas.delete(receita);
		redirect.addFlashAttribute("sucesso", "Receita \"" + receita.getDescricao() + "\" excluída.");
		return "redirect:/receitas?mes=" + YearMonth.from(receita.getData());
	}

	@InitBinder
	void camposPermitidos(WebDataBinder binder) {
		binder.setAllowedFields("descricao", "valor", "data", "fonte");
	}

	private String formulario(Receita receita, Integer id, Model model) {
		model.addAttribute("receita", receita);
		model.addAttribute("idEdicao", id);
		model.addAttribute("fontes", fontes.listar(usuarioAtual.get()));
		return "receitas/form";
	}

	private Receita buscar(Integer id) {
		return receitas.findByIdAndUsuario(id, usuarioAtual.get())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receita não encontrada"));
	}

	/** A fonte escolhida precisa ser do próprio usuário. */
	private static void validarFonte(Receita receita, Usuario usuario, BindingResult erros) {
		if (receita.getFonte() != null && (receita.getFonte().getUsuario() == null
				|| !receita.getFonte().getUsuario().getId().equals(usuario.getId()))) {
			erros.rejectValue("fonte", "invalida", "Escolha uma fonte");
		}
	}

}

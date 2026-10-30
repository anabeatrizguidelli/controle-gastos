package br.com.controlegastos.controller;

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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.GerenciaFontes;
import jakarta.validation.Valid;

/**
 * Aba "Fontes de receita": criar, editar, excluir e listar as fontes.
 */
@Controller
@RequestMapping("/fontes")
public class FonteController {

	private final GerenciaFontes fontes;
	private final UsuarioAtual usuarioAtual;

	public FonteController(GerenciaFontes fontes, UsuarioAtual usuarioAtual) {
		this.fontes = fontes;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public String listar(Model model) {
		return pagina(new Fonte(), null, model);
	}

	@PostMapping
	public String criar(@Valid @ModelAttribute("fonte") Fonte fonte, BindingResult erros, Model model,
			RedirectAttributes redirect) {
		Usuario usuario = usuarioAtual.get();
		fontes.validarNome(fonte, usuario, erros);
		if (erros.hasErrors()) {
			return pagina(fonte, null, model);
		}
		Fonte criada = fontes.criar(fonte, usuario);
		redirect.addFlashAttribute("sucesso", "Fonte \"" + criada.getNome() + "\" criada.");
		return "redirect:/fontes";
	}

	@GetMapping("/{id}/editar")
	public String editar(@PathVariable Integer id, Model model) {
		return pagina(buscar(id), id, model);
	}

	@PostMapping("/{id}")
	public String atualizar(@PathVariable Integer id, @Valid @ModelAttribute("fonte") Fonte dados,
			BindingResult erros, Model model, RedirectAttributes redirect) {
		Fonte existente = buscar(id);
		dados.setId(id);
		fontes.validarNome(dados, existente.getUsuario(), erros);
		if (erros.hasErrors()) {
			return pagina(dados, id, model);
		}
		fontes.atualizar(existente, dados);
		redirect.addFlashAttribute("sucesso", "Fonte atualizada.");
		return "redirect:/fontes";
	}

	@PostMapping("/{id}/excluir")
	public String excluir(@PathVariable Integer id, RedirectAttributes redirect) {
		Fonte fonte = buscar(id);
		String erro = fontes.excluir(fonte);
		if (erro != null) {
			redirect.addFlashAttribute("erro", erro);
		} else {
			redirect.addFlashAttribute("sucesso", "Fonte \"" + fonte.getNome() + "\" excluída.");
		}
		return "redirect:/fontes";
	}

	@InitBinder
	void camposPermitidos(WebDataBinder binder) {
		binder.setAllowedFields("nome");
	}

	private String pagina(Fonte fonte, Integer id, Model model) {
		model.addAttribute("fonte", fonte);
		model.addAttribute("idEdicao", id);
		model.addAttribute("fontes", fontes.listar(usuarioAtual.get()));
		return "fontes";
	}

	private Fonte buscar(Integer id) {
		return fontes.buscar(id, usuarioAtual.get())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte não encontrada"));
	}

}

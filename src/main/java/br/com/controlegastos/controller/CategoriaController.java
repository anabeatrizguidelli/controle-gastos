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

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.GerenciaCategorias;
import jakarta.validation.Valid;

/**
 * Aba "Categorias": criar, editar, excluir e listar as categorias de despesa.
 */
@Controller
@RequestMapping("/categorias")
public class CategoriaController {

	private final GerenciaCategorias categorias;
	private final UsuarioAtual usuarioAtual;

	public CategoriaController(GerenciaCategorias categorias, UsuarioAtual usuarioAtual) {
		this.categorias = categorias;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public String listar(Model model) {
		return pagina(new Categoria(), null, model);
	}

	@PostMapping
	public String criar(@Valid @ModelAttribute("categoria") Categoria categoria, BindingResult erros, Model model,
			RedirectAttributes redirect) {
		Usuario usuario = usuarioAtual.get();
		categorias.validarNome(categoria, usuario, erros);
		if (erros.hasErrors()) {
			return pagina(categoria, null, model);
		}
		Categoria criada = categorias.criar(categoria, usuario);
		redirect.addFlashAttribute("sucesso", "Categoria \"" + criada.getNome() + "\" criada.");
		return "redirect:/categorias";
	}

	@GetMapping("/{id}/editar")
	public String editar(@PathVariable Integer id, Model model) {
		return pagina(buscar(id), id, model);
	}

	@PostMapping("/{id}")
	public String atualizar(@PathVariable Integer id, @Valid @ModelAttribute("categoria") Categoria dados,
			BindingResult erros, Model model, RedirectAttributes redirect) {
		Categoria existente = buscar(id);
		dados.setId(id);
		categorias.validarNome(dados, existente.getUsuario(), erros);
		if (erros.hasErrors()) {
			return pagina(dados, id, model);
		}
		categorias.atualizar(existente, dados);
		redirect.addFlashAttribute("sucesso", "Categoria atualizada.");
		return "redirect:/categorias";
	}

	@PostMapping("/{id}/excluir")
	public String excluir(@PathVariable Integer id, RedirectAttributes redirect) {
		Categoria categoria = buscar(id);
		String erro = categorias.excluir(categoria);
		if (erro != null) {
			redirect.addFlashAttribute("erro", erro);
		} else {
			redirect.addFlashAttribute("sucesso", "Categoria \"" + categoria.getNome() + "\" excluída.");
		}
		return "redirect:/categorias";
	}

	@InitBinder
	void camposPermitidos(WebDataBinder binder) {
		binder.setAllowedFields("nome", "limiteMensal");
	}

	private String pagina(Categoria categoria, Integer id, Model model) {
		model.addAttribute("categoria", categoria);
		model.addAttribute("idEdicao", id);
		model.addAttribute("categorias", categorias.listar(usuarioAtual.get()));
		return "categorias";
	}

	private Categoria buscar(Integer id) {
		return categorias.buscar(id, usuarioAtual.get())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
	}

}

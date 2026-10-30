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

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.GerenciaCategorias;
import jakarta.validation.Valid;

/**
 * Páginas para listar, cadastrar, editar e excluir despesas.
 */
@Controller
@RequestMapping("/despesas")
public class DespesaController {

	private final DespesaRepository despesas;
	private final GerenciaCategorias categorias;
	private final ControleGastos controle;
	private final UsuarioAtual usuarioAtual;

	public DespesaController(DespesaRepository despesas, GerenciaCategorias categorias, ControleGastos controle,
			UsuarioAtual usuarioAtual) {
		this.despesas = despesas;
		this.categorias = categorias;
		this.controle = controle;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public String listar(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		YearMonth m = mes != null ? mes : YearMonth.now();
		List<Despesa> lista = controle.listarDespesas(usuarioAtual.get(), m);
		model.addAttribute("mes", m);
		model.addAttribute("despesas", lista);
		model.addAttribute("total", lista.stream().mapToDouble(Despesa::getValor).sum());
		return "despesas/lista";
	}

	@GetMapping("/nova")
	public String nova(Model model) {
		Despesa despesa = new Despesa();
		despesa.setData(LocalDate.now());
		return formulario(despesa, null, model);
	}

	@PostMapping
	public String criar(@Valid @ModelAttribute("despesa") Despesa despesa, BindingResult erros, Model model,
			RedirectAttributes redirect) {
		Usuario usuario = usuarioAtual.get();
		validarCategoria(despesa, usuario, erros);
		if (erros.hasErrors()) {
			return formulario(despesa, null, model);
		}
		despesa.setUsuario(usuario);
		despesas.save(despesa);
		redirect.addFlashAttribute("sucesso", "Despesa registrada.");
		return "redirect:/despesas?mes=" + YearMonth.from(despesa.getData());
	}

	@GetMapping("/{id}/editar")
	public String editar(@PathVariable Integer id, Model model) {
		return formulario(buscar(id), id, model);
	}

	@PostMapping("/{id}")
	public String atualizar(@PathVariable Integer id, @Valid @ModelAttribute("despesa") Despesa dados,
			BindingResult erros, Model model, RedirectAttributes redirect) {
		Despesa existente = buscar(id);
		validarCategoria(dados, existente.getUsuario(), erros);
		if (erros.hasErrors()) {
			return formulario(dados, id, model);
		}
		existente.setDescricao(dados.getDescricao());
		existente.setValor(dados.getValor());
		existente.setData(dados.getData());
		existente.setCategoria(dados.getCategoria());
		despesas.save(existente);
		redirect.addFlashAttribute("sucesso", "Despesa atualizada.");
		return "redirect:/despesas?mes=" + YearMonth.from(existente.getData());
	}

	@PostMapping("/{id}/excluir")
	public String excluir(@PathVariable Integer id, RedirectAttributes redirect) {
		Despesa despesa = buscar(id);
		despesas.delete(despesa);
		redirect.addFlashAttribute("sucesso", "Despesa \"" + despesa.getDescricao() + "\" excluída.");
		return "redirect:/despesas?mes=" + YearMonth.from(despesa.getData());
	}

	@InitBinder
	void camposPermitidos(WebDataBinder binder) {
		binder.setAllowedFields("descricao", "valor", "data", "categoria");
	}

	private String formulario(Despesa despesa, Integer id, Model model) {
		model.addAttribute("despesa", despesa);
		model.addAttribute("idEdicao", id);
		model.addAttribute("categorias", categorias.listar(usuarioAtual.get()));
		return "despesas/form";
	}

	private Despesa buscar(Integer id) {
		return despesas.findByIdAndUsuario(id, usuarioAtual.get())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada"));
	}

	/** A categoria escolhida precisa ser do próprio usuário. */
	private static void validarCategoria(Despesa despesa, Usuario usuario, BindingResult erros) {
		if (despesa.getCategoria() != null && (despesa.getCategoria().getUsuario() == null
				|| !despesa.getCategoria().getUsuario().getId().equals(usuario.getId()))) {
			erros.rejectValue("categoria", "invalida", "Escolha uma categoria");
		}
	}

}

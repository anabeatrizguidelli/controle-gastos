package br.com.controlegastos.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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
import br.com.controlegastos.service.CadastroReceitas;
import br.com.controlegastos.service.CadastroReceitas.Exclusao;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.GerenciaFontes;
import br.com.controlegastos.service.LancamentosFixos;
import jakarta.validation.Valid;

/**
 * Páginas para listar, cadastrar (avulsa ou fixa), editar e excluir receitas (RF2).
 */
@Controller
@RequestMapping("/receitas")
public class ReceitaController {

	private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

	private final ReceitaRepository receitas;
	private final GerenciaFontes fontes;
	private final ControleGastos controle;
	private final CadastroReceitas cadastro;
	private final LancamentosFixos fixos;
	private final UsuarioAtual usuarioAtual;

	public ReceitaController(ReceitaRepository receitas, GerenciaFontes fontes, ControleGastos controle,
			CadastroReceitas cadastro, LancamentosFixos fixos, UsuarioAtual usuarioAtual) {
		this.receitas = receitas;
		this.fontes = fontes;
		this.controle = controle;
		this.cadastro = cadastro;
		this.fixos = fixos;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public String listar(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		YearMonth m = mes != null ? mes : YearMonth.now();
		fixos.lancarAte(usuarioAtual.get(), m);
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
	public String criar(@Valid @ModelAttribute("receita") Receita receita, BindingResult erros,
			@RequestParam(defaultValue = "false") boolean fixa, Model model, RedirectAttributes redirect) {
		Usuario usuario = usuarioAtual.get();
		validarFonte(receita, usuario, erros);
		if (erros.hasErrors()) {
			model.addAttribute("fixa", fixa);
			return formulario(receita, null, model);
		}
		cadastro.registrar(receita, fixa, usuario);
		redirect.addFlashAttribute("sucesso", fixa ? "Receita fixa registrada. Ela será lançada todo mês até você encerrá-la."
				: "Receita registrada.");
		return "redirect:/receitas?mes=" + YearMonth.from(receita.getData());
	}

	@GetMapping("/{id}/editar")
	public String editar(@PathVariable Integer id, Model model) {
		return formulario(buscar(id), id, model);
	}

	@PostMapping("/{id}")
	public String atualizar(@PathVariable Integer id, @Valid @ModelAttribute("receita") Receita dados,
			BindingResult erros, @RequestParam(defaultValue = "false") boolean aplicarSeguintes, Model model,
			RedirectAttributes redirect) {
		Receita existente = buscar(id);
		validarFonte(dados, existente.getUsuario(), erros);
		if (erros.hasErrors()) {
			model.addAttribute("original", existente);
			return formulario(dados, id, model);
		}
		cadastro.atualizar(existente, dados, aplicarSeguintes);
		redirect.addFlashAttribute("sucesso", !existente.isFixa() ? "Receita atualizada."
				: aplicarSeguintes ? "Receita fixa atualizada. O novo valor vale também para os meses seguintes."
						: "Receita fixa atualizada só neste mês.");
		return "redirect:/receitas?mes=" + YearMonth.from(existente.getData());
	}

	/**
	 * Exclui a receita. Numa receita fixa, {@code modo} escolhe: "este" (só este mês),
	 * "seguintes" (este e os seguintes, encerrando a receita fixa) ou "tudo" (todos os
	 * meses, que exige também {@code confirmarTudo}).
	 */
	@PostMapping("/{id}/excluir")
	public String excluir(@PathVariable Integer id, @RequestParam(defaultValue = "este") String modo,
			@RequestParam(defaultValue = "false") boolean confirmarTudo, RedirectAttributes redirect) {
		Receita receita = buscar(id);
		YearMonth mes = YearMonth.from(receita.getData());
		String nome = "\"" + receita.getDescricao() + "\"";
		Exclusao exclusao = switch (modo) {
			case "seguintes" -> Exclusao.ESTE_E_SEGUINTES;
			case "tudo" -> Exclusao.TUDO;
			default -> Exclusao.ESTE;
		};
		if (exclusao == Exclusao.TUDO && !confirmarTudo) {
			redirect.addFlashAttribute("erro", "Para excluir todos os meses de " + nome + ", confirme que entende que os meses anteriores também serão apagados.");
			return "redirect:/receitas?mes=" + mes;
		}
		cadastro.excluir(receita, exclusao);
		String mensagem;
		if (!receita.isFixa()) {
			mensagem = "Receita " + nome + " excluída.";
		} else if (exclusao == Exclusao.TUDO) {
			mensagem = "Receita fixa " + nome + " excluída, com todos os meses.";
		} else if (exclusao == Exclusao.ESTE_E_SEGUINTES) {
			mensagem = "Receita fixa " + nome + " encerrada a partir de " + mes.format(MES)
					+ ". Os meses anteriores continuam no histórico.";
		} else {
			mensagem = "Receita fixa " + nome + " excluída só em " + mes.format(MES) + ". Ela continua nos próximos meses.";
		}
		redirect.addFlashAttribute("sucesso", mensagem);
		return "redirect:/receitas?mes=" + mes;
	}

	@InitBinder("receita")
	void camposPermitidos(WebDataBinder binder) {
		binder.setAllowedFields("descricao", "valor", "data", "fonte");
	}

	private String formulario(Receita receita, Integer id, Model model) {
		model.addAttribute("receita", receita);
		model.addAttribute("idEdicao", id);
		if (!model.containsAttribute("original")) {
			model.addAttribute("original", id == null ? null : receita);
		}
		Receita original = (Receita) model.getAttribute("original");
		// Valor salvo da receita fixa, para a tela perguntar "só este mês ou os seguintes" quando ele mudar.
		model.addAttribute("valorOriginal", original != null && original.isFixa()
				? String.format(Locale.ROOT, "%.2f", original.getValor()) : null);
		if (!model.containsAttribute("fixa")) {
			model.addAttribute("fixa", false);
		}
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
package br.com.controlegastos.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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
import br.com.controlegastos.service.CadastroDespesas;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.GerenciaCategorias;
import br.com.controlegastos.service.GeradorParcelas;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Páginas para listar, cadastrar (à vista ou parcelado), editar, excluir e
 * marcar o pagamento de despesas.
 */
@Controller
@RequestMapping("/despesas")
public class DespesaController {

	/** Formulários já enviados nesta sessão; evita gravar as parcelas duas vezes. */
	private static final String ENVIADOS = "formulariosDespesaEnviados";

	private final DespesaRepository despesas;
	private final GerenciaCategorias categorias;
	private final ControleGastos controle;
	private final CadastroDespesas cadastro;
	private final UsuarioAtual usuarioAtual;

	public DespesaController(DespesaRepository despesas, GerenciaCategorias categorias, ControleGastos controle,
			CadastroDespesas cadastro, UsuarioAtual usuarioAtual) {
		this.despesas = despesas;
		this.categorias = categorias;
		this.controle = controle;
		this.cadastro = cadastro;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public String listar(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			Model model) {
		YearMonth m = mes != null ? mes : YearMonth.now();
		List<Despesa> lista = controle.listarDespesas(usuarioAtual.get(), m);
		double total = lista.stream().mapToDouble(Despesa::getValor).sum();
		double pagas = lista.stream().filter(Despesa::isPago).mapToDouble(Despesa::getValor).sum();
		model.addAttribute("mes", m);
		model.addAttribute("despesas", lista);
		model.addAttribute("total", total);
		model.addAttribute("totalPagas", pagas);
		model.addAttribute("totalPendentes", total - pagas);
		return "despesas/lista";
	}

	@GetMapping("/nova")
	public String nova(Model model) {
		Despesa despesa = new Despesa();
		despesa.setData(LocalDate.now());
		despesa.setVencimento(LocalDate.now());
		return formulario(despesa, null, 1, model);
	}

	@PostMapping
	public String criar(@Valid @ModelAttribute("despesa") Despesa despesa, BindingResult erros,
			@RequestParam(defaultValue = "1") Integer quantidadeParcelas,
			@RequestParam(required = false) String chaveFormulario, Model model, HttpSession sessao,
			RedirectAttributes redirect) {
		Usuario usuario = usuarioAtual.get();
		Set<String> enviados = formulariosEnviados(sessao);
		if (chaveFormulario != null && enviados.contains(chaveFormulario)) {
			// Mesmo formulário enviado de novo (duplo clique, voltar + reenviar): não duplica.
			redirect.addFlashAttribute("sucesso", "Essa despesa já tinha sido registrada.");
			return "redirect:/despesas";
		}
		validarCategoria(despesa, usuario, erros);
		// Sem vencimento informado, vale a data da despesa (getVencimento() já faz isso).
		int quantidade = quantidadeParcelas == null ? 1 : quantidadeParcelas;
		if (quantidade < 1 || quantidade > GeradorParcelas.MAXIMO_PARCELAS) {
			model.addAttribute("erroParcelas", "Informe de 1 a " + GeradorParcelas.MAXIMO_PARCELAS + " parcelas");
		} else if (despesa.getValor() > 0 && Math.round(despesa.getValor() * 100) < quantidade) {
			model.addAttribute("erroParcelas", "Cada parcela precisa ter pelo menos R$ 0,01");
		}
		if (erros.hasErrors() || model.containsAttribute("erroParcelas")) {
			model.addAttribute("chaveFormulario", chaveFormulario);
			return formulario(despesa, null, quantidade, model);
		}
		List<Despesa> gravadas = cadastro.registrar(despesa, quantidade, usuario);
		if (chaveFormulario != null) {
			enviados.add(chaveFormulario);
		}
		Despesa primeira = gravadas.get(0);
		redirect.addFlashAttribute("sucesso", quantidade == 1 ? "Despesa registrada."
				: "Despesa parcelada em " + quantidade + "x registrada. Primeira parcela vence em "
						+ primeira.getVencimento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
		return "redirect:/despesas?mes=" + YearMonth.from(primeira.getVencimento());
	}

	@GetMapping("/{id}/editar")
	public String editar(@PathVariable Integer id, Model model) {
		return formulario(buscar(id), id, 1, model);
	}

	@PostMapping("/{id}")
	public String atualizar(@PathVariable Integer id, @Valid @ModelAttribute("despesa") Despesa dados,
			BindingResult erros, @RequestParam(defaultValue = "false") boolean aplicarDemaisPendentes, Model model,
			RedirectAttributes redirect) {
		Despesa existente = buscar(id);
		validarCategoria(dados, existente.getUsuario(), erros);
		if (erros.hasErrors()) {
			model.addAttribute("original", existente);
			return formulario(dados, id, 1, model);
		}
		// "Sim" na pergunta da tela: o novo valor vale também para as outras parcelas pendentes.
		int outras = cadastro.atualizar(existente, dados, aplicarDemaisPendentes);
		String mensagem = existente.isParcelada() ? "Parcela " + existente.getRotuloParcela() + " atualizada."
				: "Despesa atualizada.";
		if (outras > 0) {
			mensagem += " Novo valor aplicado também a " + (outras == 1 ? "1 parcela pendente."
					: outras + " parcelas pendentes.");
		}
		redirect.addFlashAttribute("sucesso", mensagem);
		return "redirect:/despesas?mes=" + YearMonth.from(existente.getVencimento());
	}

	/** Checkbox de pagamento: marcado = Pago, desmarcado = Pendente. Vale só para esta parcela. */
	@PostMapping("/{id}/pagamento")
	public String pagamento(@PathVariable Integer id, @RequestParam(defaultValue = "false") boolean pago,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
			RedirectAttributes redirect) {
		Despesa despesa = cadastro.marcarPagamento(buscar(id), pago);
		String nome = despesa.getDescricao() + (despesa.isParcelada() ? " (" + despesa.getRotuloParcela() + ")" : "");
		redirect.addFlashAttribute("sucesso", "\"" + nome + "\" marcada como " + (pago ? "paga." : "pendente."));
		YearMonth volta = mes != null ? mes : YearMonth.from(despesa.getVencimento());
		return "redirect:/despesas?mes=" + volta + "#despesa-" + despesa.getId();
	}

	@PostMapping("/{id}/excluir")
	public String excluir(@PathVariable Integer id, @RequestParam(defaultValue = "false") boolean todas,
			RedirectAttributes redirect) {
		Despesa despesa = buscar(id);
		boolean todasAsParcelas = todas && despesa.getParcelamento() != null;
		YearMonth mes = YearMonth.from(despesa.getVencimento());
		cadastro.excluir(despesa, todasAsParcelas);
		String mensagem;
		if (todasAsParcelas) {
			mensagem = "Todas as " + despesa.getTotalParcelas() + " parcelas de \"" + despesa.getDescricao() + "\" foram excluídas.";
		} else if (despesa.isParcelada()) {
			mensagem = "Parcela " + despesa.getRotuloParcela() + " de \"" + despesa.getDescricao() + "\" excluída.";
		} else {
			mensagem = "Despesa \"" + despesa.getDescricao() + "\" excluída.";
		}
		redirect.addFlashAttribute("sucesso", mensagem);
		return "redirect:/despesas?mes=" + mes;
	}

	@InitBinder("despesa")
	void camposPermitidos(WebDataBinder binder) {
		binder.setAllowedFields("descricao", "valor", "data", "vencimento", "categoria");
	}

	private String formulario(Despesa despesa, Integer id, int quantidadeParcelas, Model model) {
		model.addAttribute("despesa", despesa);
		model.addAttribute("idEdicao", id);
		model.addAttribute("quantidadeParcelas", quantidadeParcelas);
		model.addAttribute("maximoParcelas", GeradorParcelas.MAXIMO_PARCELAS);
		if (!model.containsAttribute("original")) {
			model.addAttribute("original", id == null ? null : despesa);
		}
		Despesa original = (Despesa) model.getAttribute("original");
		long outrasPendentes = original == null ? 0L : cadastro.outrasParcelasPendentes(original);
		model.addAttribute("outrasPendentes", outrasPendentes);
		// Valor salvo da parcela, para a tela saber se o valor mudou antes de perguntar.
		model.addAttribute("valorOriginal", outrasPendentes > 0 ? String.format(Locale.ROOT, "%.2f", original.getValor()) : null);
		if (id == null && model.getAttribute("chaveFormulario") == null) {
			model.addAttribute("chaveFormulario", UUID.randomUUID().toString());
		}
		model.addAttribute("categorias", categorias.listar(usuarioAtual.get()));
		return "despesas/form";
	}

	private Despesa buscar(Integer id) {
		return despesas.findByIdAndUsuario(id, usuarioAtual.get())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Despesa não encontrada"));
	}

	@SuppressWarnings("unchecked")
	private static Set<String> formulariosEnviados(HttpSession sessao) {
		Object atual = sessao.getAttribute(ENVIADOS);
		if (atual instanceof Set<?> conjunto) {
			return (Set<String>) conjunto;
		}
		Set<String> novo = Collections.synchronizedSet(new HashSet<>());
		sessao.setAttribute(ENVIADOS, novo);
		return novo;
	}

	/** A categoria escolhida precisa ser do próprio usuário. */
	private static void validarCategoria(Despesa despesa, Usuario usuario, BindingResult erros) {
		if (despesa.getCategoria() != null && (despesa.getCategoria().getUsuario() == null
				|| !despesa.getCategoria().getUsuario().getId().equals(usuario.getId()))) {
			erros.rejectValue("categoria", "invalida", "Escolha uma categoria");
		}
	}

}

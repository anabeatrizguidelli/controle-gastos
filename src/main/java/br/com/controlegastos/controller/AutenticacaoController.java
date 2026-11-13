package br.com.controlegastos.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.controlegastos.model.CodigoVerificacao;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.service.CadastroUsuario;
import br.com.controlegastos.service.Verificacoes;

/**
 * Tela de login (primeira tela do sistema), criação de conta, confirmação do
 * e-mail e "esqueci minha senha". As três últimas usam a mesma página
 * ({@code codigo.html}), que muda conforme a {@code etapa}.
 */
@Controller
public class AutenticacaoController {

	static final String CODIGO_INVALIDO = "Código inválido ou expirado. Confira o e-mail ou peça um novo código.";
	static final String PEDIDO_NEUTRO = "Se existir uma conta com esses dados, enviamos um código de 6 dígitos para o e-mail dela. "
			+ "Você pode pedir outro a cada minuto.";

	private final CadastroUsuario cadastro;
	private final Verificacoes verificacoes;

	public AutenticacaoController(CadastroUsuario cadastro, Verificacoes verificacoes) {
		this.cadastro = cadastro;
		this.verificacoes = verificacoes;
	}

	@ModelAttribute
	void avisosDasTelas(Model model) {
		model.addAttribute("modoDev", verificacoes.modoDesenvolvimento());
		model.addAttribute("recuperacao", verificacoes.ativa());
	}

	@GetMapping("/")
	public String inicio() {
		return "redirect:/painel";
	}

	@GetMapping("/login")
	public String login(Authentication auth) {
		return estaLogado(auth) ? "redirect:/painel" : "login";
	}

	@GetMapping("/cadastro")
	public String formularioCadastro(Authentication auth) {
		return estaLogado(auth) ? "redirect:/painel" : "cadastro";
	}

	@PostMapping("/cadastro")
	public String cadastrar(@RequestParam(defaultValue = "") String nome, @RequestParam(defaultValue = "") String usuario,
			@RequestParam(defaultValue = "") String email, @RequestParam(defaultValue = "") String senha,
			@RequestParam(defaultValue = "") String confirmacao, Model model, RedirectAttributes redirect) {
		String erro = null;
		if (nome.isBlank()) {
			erro = "Informe seu nome.";
		} else if (!CadastroUsuario.usernameValido(usuario)) {
			erro = "O nome de usuário deve ter de 3 a 30 caracteres: letras sem acento, números, ponto, hífen ou sublinhado (sem @ e sem espaços).";
		} else if (!email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			erro = "Informe um e-mail válido.";
		} else if (senha.length() < 6) {
			erro = "A senha deve ter pelo menos 6 caracteres.";
		} else if (!senha.equals(confirmacao)) {
			erro = "As senhas não conferem.";
		} else if (cadastro.emailEmUso(email)) {
			erro = "Já existe uma conta com esse e-mail.";
		} else if (cadastro.usernameEmUso(usuario)) {
			erro = "Esse nome de usuário já está em uso.";
		}
		Usuario novo = null;
		if (erro == null) {
			try {
				novo = cadastro.cadastrar(nome, usuario, email, senha, verificacoes.ativa() ? false : null);
			} catch (DataIntegrityViolationException e) {
				erro = "Esse nome de usuário ou e-mail acabou de ser usado. Tente outro.";
			}
		}
		if (erro != null) {
			model.addAttribute("erro", erro);
			model.addAttribute("nome", nome);
			model.addAttribute("usuario", usuario);
			model.addAttribute("email", email);
			return "cadastro";
		}
		if (!verificacoes.ativa()) {
			redirect.addFlashAttribute("sucesso", "Conta criada! Entre com seu e-mail ou nome de usuário.");
			return "redirect:/login";
		}
		verificacoes.enviarCodigo(novo, CodigoVerificacao.CONFIRMAR_EMAIL);
		redirect.addFlashAttribute("sucesso", "Conta criada! Enviamos um código de 6 dígitos para " + novo.getEmail()
				+ ". Digite-o abaixo para liberar o acesso.");
		redirect.addAttribute("login", novo.getEmail());
		return "redirect:/confirmar";
	}

	// ---------- confirmação do e-mail ----------

	@GetMapping("/confirmar")
	public String telaConfirmar(@RequestParam(defaultValue = "") String login, Model model) {
		return etapa(model, "confirmar", login);
	}

	@PostMapping("/confirmar")
	public String confirmar(@RequestParam(defaultValue = "") String login, @RequestParam(defaultValue = "") String codigo,
			Model model, RedirectAttributes redirect) {
		if (verificacoes.confirmarEmail(login, codigo)) {
			redirect.addFlashAttribute("sucesso", "E-mail confirmado! Agora é só entrar.");
			return "redirect:/login";
		}
		model.addAttribute("erro", CODIGO_INVALIDO);
		return etapa(model, "confirmar", login);
	}

	@PostMapping("/confirmar/reenviar")
	public String reenviar(@RequestParam(defaultValue = "") String login, RedirectAttributes redirect) {
		verificacoes.reenviarConfirmacao(login);
		redirect.addFlashAttribute("sucesso", "Se houver uma confirmação pendente para essa conta, enviamos um novo código. "
				+ "Você pode pedir outro a cada minuto.");
		redirect.addAttribute("login", login.trim());
		return "redirect:/confirmar";
	}

	// ---------- esqueci minha senha ----------

	@GetMapping("/senha/esqueci")
	public String telaEsqueci(@RequestParam(defaultValue = "") String login, Model model, RedirectAttributes redirect) {
		if (!verificacoes.ativa()) {
			return indisponivel(redirect);
		}
		return etapa(model, "esqueci", login);
	}

	@PostMapping("/senha/esqueci")
	public String esqueci(@RequestParam(defaultValue = "") String login, Model model, RedirectAttributes redirect) {
		if (!verificacoes.ativa()) {
			return indisponivel(redirect);
		}
		if (login.isBlank()) {
			model.addAttribute("erro", "Informe seu e-mail ou nome de usuário.");
			return etapa(model, "esqueci", login);
		}
		verificacoes.pedirNovaSenha(login);
		redirect.addFlashAttribute("sucesso", PEDIDO_NEUTRO);
		redirect.addAttribute("login", login.trim());
		return "redirect:/senha/redefinir";
	}

	@GetMapping("/senha/redefinir")
	public String telaRedefinir(@RequestParam(defaultValue = "") String login, Model model, RedirectAttributes redirect) {
		if (!verificacoes.ativa()) {
			return indisponivel(redirect);
		}
		return etapa(model, "redefinir", login);
	}

	@PostMapping("/senha/redefinir")
	public String redefinir(@RequestParam(defaultValue = "") String login, @RequestParam(defaultValue = "") String codigo,
			@RequestParam(defaultValue = "") String senha, @RequestParam(defaultValue = "") String confirmacao,
			Model model, RedirectAttributes redirect) {
		if (!verificacoes.ativa()) {
			return indisponivel(redirect);
		}
		String erro = null;
		if (senha.length() < 6) {
			erro = "A senha deve ter pelo menos 6 caracteres.";
		} else if (!senha.equals(confirmacao)) {
			erro = "As senhas não conferem.";
		} else if (!verificacoes.redefinirSenha(login, codigo, senha)) {
			erro = CODIGO_INVALIDO;
		}
		if (erro != null) {
			model.addAttribute("erro", erro);
			return etapa(model, "redefinir", login);
		}
		redirect.addFlashAttribute("sucesso", "Senha alterada! Entre com a nova senha.");
		return "redirect:/login";
	}

	private static String etapa(Model model, String etapa, String login) {
		model.addAttribute("etapa", etapa);
		model.addAttribute("login", login.trim());
		return "codigo";
	}

	private static String indisponivel(RedirectAttributes redirect) {
		redirect.addFlashAttribute("falha", "A recuperação de senha ainda não está disponível.");
		return "redirect:/login";
	}

	private static boolean estaLogado(Authentication auth) {
		return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
	}

}
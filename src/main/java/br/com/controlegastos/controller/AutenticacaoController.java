package br.com.controlegastos.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.controlegastos.service.CadastroUsuario;

/**
 * Tela de login (primeira tela do sistema) e criação de conta.
 */
@Controller
public class AutenticacaoController {

	private final CadastroUsuario cadastro;

	public AutenticacaoController(CadastroUsuario cadastro) {
		this.cadastro = cadastro;
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
	public String cadastrar(@RequestParam(defaultValue = "") String nome, @RequestParam(defaultValue = "") String email,
			@RequestParam(defaultValue = "") String senha, @RequestParam(defaultValue = "") String confirmacao,
			Model model, RedirectAttributes redirect) {
		String erro = null;
		if (nome.isBlank()) {
			erro = "Informe seu nome.";
		} else if (!email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			erro = "Informe um e-mail válido.";
		} else if (senha.length() < 6) {
			erro = "A senha deve ter pelo menos 6 caracteres.";
		} else if (!senha.equals(confirmacao)) {
			erro = "As senhas não conferem.";
		} else if (cadastro.emailEmUso(email)) {
			erro = "Já existe uma conta com esse e-mail.";
		}
		if (erro != null) {
			model.addAttribute("erro", erro);
			model.addAttribute("nome", nome);
			model.addAttribute("email", email);
			return "cadastro";
		}
		cadastro.cadastrar(nome, email, senha);
		redirect.addFlashAttribute("sucesso", "Conta criada! Entre com seu e-mail e senha.");
		return "redirect:/login";
	}

	private static boolean estaLogado(Authentication auth) {
		return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
	}

}

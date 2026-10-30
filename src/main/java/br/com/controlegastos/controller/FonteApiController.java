package br.com.controlegastos.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.GerenciaFontes;
import jakarta.validation.Valid;

/**
 * API de fontes de receita. O POST é usado pelo "cadastro rápido" no formulário de receita.
 */
@RestController
@RequestMapping("/api/fontes")
public class FonteApiController {

	private final GerenciaFontes fontes;
	private final UsuarioAtual usuarioAtual;

	public FonteApiController(GerenciaFontes fontes, UsuarioAtual usuarioAtual) {
		this.fontes = fontes;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public List<Fonte> listar() {
		return fontes.listar(usuarioAtual.get());
	}

	@PostMapping
	public ResponseEntity<?> criar(@Valid @RequestBody Fonte fonte, BindingResult erros) {
		Usuario usuario = usuarioAtual.get();
		fonte.setId(null);
		fontes.validarNome(fonte, usuario, erros);
		if (erros.hasErrors()) {
			return ResponseEntity.badRequest().body(Map.of("erro", erros.getFieldErrors().get(0).getDefaultMessage()));
		}
		Fonte criada = fontes.criar(fonte, usuario);
		return ResponseEntity.created(URI.create("/api/fontes/" + criada.getId())).body(criada);
	}

}

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

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.GerenciaCategorias;
import jakarta.validation.Valid;

/**
 * API de categorias. O POST é usado pelo "cadastro rápido" no formulário de despesa.
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaApiController {

	private final GerenciaCategorias categorias;
	private final UsuarioAtual usuarioAtual;

	public CategoriaApiController(GerenciaCategorias categorias, UsuarioAtual usuarioAtual) {
		this.categorias = categorias;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public List<Categoria> listar() {
		return categorias.listar(usuarioAtual.get());
	}

	@PostMapping
	public ResponseEntity<?> criar(@Valid @RequestBody Categoria categoria, BindingResult erros) {
		Usuario usuario = usuarioAtual.get();
		categoria.setId(null);
		categorias.validarNome(categoria, usuario, erros);
		if (erros.hasErrors()) {
			return ResponseEntity.badRequest().body(Map.of("erro", erros.getFieldErrors().get(0).getDefaultMessage()));
		}
		Categoria criada = categorias.criar(categoria, usuario);
		return ResponseEntity.created(URI.create("/api/categorias/" + criada.getId())).body(criada);
	}

}

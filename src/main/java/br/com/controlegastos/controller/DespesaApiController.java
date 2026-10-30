package br.com.controlegastos.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.seguranca.UsuarioAtual;
import jakarta.validation.Valid;

/**
 * API REST de despesas do usuário logado: devolve e recebe dados em JSON.
 */
@RestController
@RequestMapping("/api/despesas")
public class DespesaApiController {

	private final DespesaRepository despesas;
	private final CategoriaRepository categorias;
	private final UsuarioAtual usuarioAtual;

	public DespesaApiController(DespesaRepository despesas, CategoriaRepository categorias, UsuarioAtual usuarioAtual) {
		this.despesas = despesas;
		this.categorias = categorias;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public List<Despesa> listar() {
		return despesas.findByUsuarioOrderByDataDescIdDesc(usuarioAtual.get());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Despesa> buscar(@PathVariable Integer id) {
		return ResponseEntity.of(despesas.findByIdAndUsuario(id, usuarioAtual.get()));
	}

	@PostMapping
	public ResponseEntity<Despesa> registrar(@Valid @RequestBody Despesa despesa) {
		Usuario usuario = usuarioAtual.get();
		return categorias.findByIdAndUsuario(despesa.getCategoria().getId(), usuario)
				.map(categoria -> {
					despesa.setCategoria(categoria);
					despesa.setUsuario(usuario);
					Despesa salva = despesas.save(despesa);
					return ResponseEntity.created(URI.create("/api/despesas/" + salva.getId())).body(salva);
				})
				.orElse(ResponseEntity.badRequest().build());
	}

}

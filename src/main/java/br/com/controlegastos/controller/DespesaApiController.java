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
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import jakarta.validation.Valid;

/**
 * API REST de despesas: devolve e recebe dados em JSON.
 */
@RestController
@RequestMapping("/api/despesas")
public class DespesaApiController {

	private final DespesaRepository despesas;
	private final CategoriaRepository categorias;

	public DespesaApiController(DespesaRepository despesas, CategoriaRepository categorias) {
		this.despesas = despesas;
		this.categorias = categorias;
	}

	@GetMapping
	public List<Despesa> listar() {
		return despesas.findAll();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Despesa> buscar(@PathVariable Integer id) {
		return ResponseEntity.of(despesas.findById(id));
	}

	@PostMapping
	public ResponseEntity<Despesa> registrar(@Valid @RequestBody Despesa despesa) {
		return categorias.findById(despesa.getCategoria().getId())
				.map(categoria -> {
					despesa.setCategoria(categoria);
					Despesa salva = despesas.save(despesa);
					return ResponseEntity.created(URI.create("/api/despesas/" + salva.getId())).body(salva);
				})
				.orElse(ResponseEntity.badRequest().build());
	}

}

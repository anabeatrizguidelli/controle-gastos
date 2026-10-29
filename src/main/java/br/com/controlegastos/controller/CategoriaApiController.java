package br.com.controlegastos.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.repository.CategoriaRepository;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaApiController {

	private final CategoriaRepository categorias;

	public CategoriaApiController(CategoriaRepository categorias) {
		this.categorias = categorias;
	}

	@GetMapping
	public List<Categoria> listar() {
		return categorias.findAll();
	}

}

package br.com.controlegastos.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.controlegastos.model.Receita;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.seguranca.UsuarioAtual;

/**
 * API REST de receitas do usuário logado (somente leitura).
 */
@RestController
@RequestMapping("/api/receitas")
public class ReceitaApiController {

	private final ReceitaRepository receitas;
	private final UsuarioAtual usuarioAtual;

	public ReceitaApiController(ReceitaRepository receitas, UsuarioAtual usuarioAtual) {
		this.receitas = receitas;
		this.usuarioAtual = usuarioAtual;
	}

	@GetMapping
	public List<Receita> listar() {
		return receitas.findByUsuarioOrderByDataDescIdDesc(usuarioAtual.get());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Receita> buscar(@PathVariable Integer id) {
		return ResponseEntity.of(receitas.findByIdAndUsuario(id, usuarioAtual.get()));
	}

}

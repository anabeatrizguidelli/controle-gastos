package br.com.controlegastos.controller;

import java.net.URI;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.seguranca.UsuarioAtual;
import br.com.controlegastos.service.CadastroDespesas;
import br.com.controlegastos.service.LancamentosFixos;
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
	private final CadastroDespesas cadastro;
	private final LancamentosFixos fixas;

	public DespesaApiController(DespesaRepository despesas, CategoriaRepository categorias, UsuarioAtual usuarioAtual,
			CadastroDespesas cadastro, LancamentosFixos fixas) {
		this.despesas = despesas;
		this.categorias = categorias;
		this.usuarioAtual = usuarioAtual;
		this.cadastro = cadastro;
		this.fixas = fixas;
	}

	@GetMapping
	public List<Despesa> listar() {
		Usuario usuario = usuarioAtual.get();
		fixas.lancarAte(usuario, YearMonth.now());
		return despesas.listarTodas(usuario);
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
					// Pela API também é um lançamento único; sem "pago": true, começa como Pendente.
					despesa.desvincularParcela();
					if (despesa.pagamentoNaoInformado()) {
						despesa.setPago(false);
					}
					despesa.setVencimento(despesa.getVencimento());
					Despesa salva = despesas.save(despesa);
					return ResponseEntity.created(URI.create("/api/despesas/" + salva.getId())).body(salva);
				})
				.orElse(ResponseEntity.badRequest().build());
	}

	/** Marca ({@code {"pago": true}}) ou desmarca ({@code {"pago": false}}) o pagamento só desta despesa/parcela. */
	@PutMapping("/{id}/pagamento")
	public ResponseEntity<Despesa> pagamento(@PathVariable Integer id, @RequestBody Map<String, Boolean> corpo) {
		Boolean pago = corpo.get("pago");
		if (pago == null) {
			return ResponseEntity.badRequest().build();
		}
		return despesas.findByIdAndUsuario(id, usuarioAtual.get())
				.map(d -> ResponseEntity.ok(cadastro.marcarPagamento(d, pago)))
				.orElse(ResponseEntity.notFound().build());
	}

}

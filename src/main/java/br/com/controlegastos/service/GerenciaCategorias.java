package br.com.controlegastos.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;

/**
 * Regras de cadastro de categorias, usadas pelas páginas e pela API.
 */
@Service
public class GerenciaCategorias {

	private final CategoriaRepository categorias;
	private final DespesaRepository despesas;

	public GerenciaCategorias(CategoriaRepository categorias, DespesaRepository despesas) {
		this.categorias = categorias;
		this.despesas = despesas;
	}

	public List<Categoria> listar(Usuario usuario) {
		return categorias.findByUsuarioOrderByNomeAsc(usuario);
	}

	public Optional<Categoria> buscar(Integer id, Usuario usuario) {
		return categorias.findByIdAndUsuario(id, usuario);
	}

	/**
	 * Recusa nome repetido (ignorando maiúsculas) entre as categorias do usuário
	 * e exige um valor maior que zero quando o limite máximo estiver ativado.
	 */
	public void validar(Categoria categoria, Usuario usuario, Errors erros) {
		if (categoria.getTemLimite() && categoria.getLimiteMensal() <= 0 && !erros.hasFieldErrors("limiteMensal")) {
			erros.rejectValue("limiteMensal", "limiteObrigatorio", "Informe um limite maior que zero");
		}
		String nome = categoria.getNome() == null ? "" : categoria.getNome().trim();
		boolean repetido = categoria.getId() == null
				? categorias.existsByUsuarioAndNomeIgnoreCase(usuario, nome)
				: categorias.existsByUsuarioAndNomeIgnoreCaseAndIdNot(usuario, nome, categoria.getId());
		if (!nome.isEmpty() && repetido) {
			erros.rejectValue("nome", "repetido", "Já existe uma categoria com esse nome");
		}
	}

	public Categoria criar(Categoria categoria, Usuario usuario) {
		return categorias.save(new Categoria(categoria.getNome().trim(), limiteDe(categoria), usuario));
	}

	public Categoria atualizar(Categoria existente, Categoria dados) {
		existente.setNome(dados.getNome().trim());
		existente.setLimiteMensal(limiteDe(dados));
		existente.setTemLimite(null);
		return categorias.save(existente);
	}

	/** Sem limite ativado, grava 0 (sem limite), mesmo que algum valor tenha sido enviado. */
	private static double limiteDe(Categoria dados) {
		return dados.getTemLimite() ? dados.getLimiteMensal() : 0;
	}

	/** @return mensagem de erro, ou {@code null} se excluiu. */
	public String excluir(Categoria categoria) {
		if (despesas.existsByCategoria(categoria)) {
			return "A categoria \"" + categoria.getNome()
					+ "\" tem despesas lançadas. Mova ou exclua essas despesas antes de excluí-la.";
		}
		categorias.delete(categoria);
		return null;
	}

}

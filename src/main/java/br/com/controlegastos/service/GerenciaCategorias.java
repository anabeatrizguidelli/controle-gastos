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

	/** Recusa nome repetido (ignorando maiúsculas) entre as categorias do usuário. */
	public void validarNome(Categoria categoria, Usuario usuario, Errors erros) {
		String nome = categoria.getNome() == null ? "" : categoria.getNome().trim();
		boolean repetido = categoria.getId() == null
				? categorias.existsByUsuarioAndNomeIgnoreCase(usuario, nome)
				: categorias.existsByUsuarioAndNomeIgnoreCaseAndIdNot(usuario, nome, categoria.getId());
		if (!nome.isEmpty() && repetido) {
			erros.rejectValue("nome", "repetido", "Já existe uma categoria com esse nome");
		}
	}

	public Categoria criar(Categoria categoria, Usuario usuario) {
		return categorias.save(new Categoria(categoria.getNome().trim(), categoria.getLimiteMensal(), usuario));
	}

	public Categoria atualizar(Categoria existente, Categoria dados) {
		existente.setNome(dados.getNome().trim());
		existente.setLimiteMensal(dados.getLimiteMensal());
		return categorias.save(existente);
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

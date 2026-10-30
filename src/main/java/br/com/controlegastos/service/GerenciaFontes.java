package br.com.controlegastos.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;

import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.FonteRepository;
import br.com.controlegastos.repository.ReceitaRepository;

/**
 * Regras de cadastro das fontes de receita, usadas pelas páginas e pela API.
 */
@Service
public class GerenciaFontes {

	private final FonteRepository fontes;
	private final ReceitaRepository receitas;

	public GerenciaFontes(FonteRepository fontes, ReceitaRepository receitas) {
		this.fontes = fontes;
		this.receitas = receitas;
	}

	public List<Fonte> listar(Usuario usuario) {
		return fontes.findByUsuarioOrderByNomeAsc(usuario);
	}

	public Optional<Fonte> buscar(Integer id, Usuario usuario) {
		return fontes.findByIdAndUsuario(id, usuario);
	}

	/** Recusa nome repetido (ignorando maiúsculas) entre as fontes do usuário. */
	public void validarNome(Fonte fonte, Usuario usuario, Errors erros) {
		String nome = fonte.getNome() == null ? "" : fonte.getNome().trim();
		boolean repetido = fonte.getId() == null
				? fontes.existsByUsuarioAndNomeIgnoreCase(usuario, nome)
				: fontes.existsByUsuarioAndNomeIgnoreCaseAndIdNot(usuario, nome, fonte.getId());
		if (!nome.isEmpty() && repetido) {
			erros.rejectValue("nome", "repetido", "Já existe uma fonte com esse nome");
		}
	}

	public Fonte criar(Fonte fonte, Usuario usuario) {
		return fontes.save(new Fonte(fonte.getNome().trim(), usuario));
	}

	public Fonte atualizar(Fonte existente, Fonte dados) {
		existente.setNome(dados.getNome().trim());
		return fontes.save(existente);
	}

	/** @return mensagem de erro, ou {@code null} se excluiu. */
	public String excluir(Fonte fonte) {
		if (receitas.existsByFonte(fonte)) {
			return "A fonte \"" + fonte.getNome()
					+ "\" tem receitas lançadas. Mova ou exclua essas receitas antes de excluí-la.";
		}
		fontes.delete(fonte);
		return null;
	}

}

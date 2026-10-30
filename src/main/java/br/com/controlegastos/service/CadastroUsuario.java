package br.com.controlegastos.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Fonte;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.FonteRepository;
import br.com.controlegastos.repository.UsuarioRepository;

/**
 * Cria contas novas, já com algumas categorias e fontes de receita para começar
 * (o usuário pode editar ou excluir todas depois).
 */
@Service
public class CadastroUsuario {

	private final UsuarioRepository usuarios;
	private final CategoriaRepository categorias;
	private final FonteRepository fontes;
	private final PasswordEncoder codificador;

	public CadastroUsuario(UsuarioRepository usuarios, CategoriaRepository categorias, FonteRepository fontes,
			PasswordEncoder codificador) {
		this.usuarios = usuarios;
		this.categorias = categorias;
		this.fontes = fontes;
		this.codificador = codificador;
	}

	public boolean emailEmUso(String email) {
		return usuarios.existsByEmailIgnoreCase(email.trim());
	}

	@Transactional
	public Usuario cadastrar(String nome, String email, String senha) {
		Usuario usuario = usuarios.save(new Usuario(nome.trim(), email.trim().toLowerCase(), codificador.encode(senha)));
		criarPadroes(usuario);
		return usuario;
	}

	public void criarPadroes(Usuario usuario) {
		if (categorias.countByUsuario(usuario) == 0) {
			categorias.save(new Categoria("Alimentação", 400, usuario));
			categorias.save(new Categoria("Transporte", 150, usuario));
			categorias.save(new Categoria("Lazer", 100, usuario));
		}
		if (fontes.countByUsuario(usuario) == 0) {
			fontes.save(new Fonte("Mesada", usuario));
			fontes.save(new Fonte("Bolsa", usuario));
			fontes.save(new Fonte("Estágio", usuario));
		}
	}

}

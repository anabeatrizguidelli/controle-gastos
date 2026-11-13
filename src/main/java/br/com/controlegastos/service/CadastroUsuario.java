package br.com.controlegastos.service;

import java.util.List;
import java.util.Locale;

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

	/** Nome de usuário como fica gravado: sem espaços nas pontas e em minúsculas. */
	public static String normalizarUsername(String username) {
		return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
	}

	public static boolean usernameValido(String username) {
		return normalizarUsername(username).matches(Usuario.FORMATO_USERNAME);
	}

	public boolean usernameEmUso(String username) {
		return usuarios.existsByUsername(normalizarUsername(username));
	}

	/** Conta já confirmada, com nome de usuário gerado a partir do e-mail (conta de demonstração e testes). */
	@Transactional
	public Usuario cadastrar(String nome, String email, String senha) {
		return cadastrar(nome, null, email, senha, null);
	}

	/**
	 * @param emailConfirmado {@code false} quando a conta precisa confirmar o e-mail
	 *                        antes de entrar; {@code null} quando não precisa
	 */
	@Transactional
	public Usuario cadastrar(String nome, String username, String email, String senha, Boolean emailConfirmado) {
		String emailNormalizado = email.trim().toLowerCase();
		Usuario usuario = new Usuario(nome.trim(), emailNormalizado, codificador.encode(senha));
		usuario.setUsername(username == null ? usernameLivre(emailNormalizado) : normalizarUsername(username));
		usuario.setEmailConfirmado(emailConfirmado);
		usuarios.save(usuario);
		criarPadroes(usuario);
		return usuario;
	}

	/**
	 * Contas criadas antes do nome de usuário existir ganham um a partir do
	 * e-mail (ex.: ana.souza@... vira "ana.souza", ou "ana.souza2" se já existir).
	 * Roda na inicialização e não faz nada quando todas já têm.
	 */
	@Transactional
	public int preencherUsernames() {
		List<Usuario> semUsername = usuarios.findByUsernameIsNull();
		semUsername.forEach(u -> u.setUsername(usernameLivre(u.getEmail())));
		return semUsername.size();
	}

	private String usernameLivre(String email) {
		int arroba = email.indexOf('@');
		String base = (arroba > 0 ? email.substring(0, arroba) : email).toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9._-]", "");
		if (base.length() < 3) {
			base = "usuario" + base;
		}
		base = base.substring(0, Math.min(base.length(), 25));
		String candidato = base;
		for (int i = 2; usuarios.existsByUsername(candidato); i++) {
			candidato = base + i;
		}
		return candidato;
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

package br.com.controlegastos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.CodigoVerificacao;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CodigoVerificacaoRepository;
import br.com.controlegastos.repository.UsuarioRepository;

/**
 * Códigos de 6 dígitos por e-mail: confirmação da conta nova e "esqueci minha
 * senha". Cada código vale 15 minutos, serve uma vez só, aceita 5 tentativas
 * e fica no banco apenas como hash. Nada fica guardado em memória.
 * <p>
 * Em produção sem provedor de e-mail configurado, os dois recursos ficam
 * desligados (contas novas entram direto e a recuperação some da tela), para
 * que nenhum código vá parar no log do servidor.
 */
@Service
public class Verificacoes {

	public static final Duration VALIDADE = Duration.ofMinutes(15);
	public static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);
	public static final int MAX_TENTATIVAS = 5;

	private static final Logger LOG = LoggerFactory.getLogger(Verificacoes.class);
	private static final SecureRandom SORTEIO = new SecureRandom();

	private final CodigoVerificacaoRepository codigos;
	private final UsuarioRepository usuarios;
	private final PasswordEncoder codificador;
	private final EnvioEmail envio;
	private final boolean ativa;

	public Verificacoes(CodigoVerificacaoRepository codigos, UsuarioRepository usuarios, PasswordEncoder codificador,
			EnvioEmail envio, Environment ambiente) {
		this.codigos = codigos;
		this.usuarios = usuarios;
		this.codificador = codificador;
		this.envio = envio;
		this.ativa = envio.real() || !ambiente.matchesProfiles("prod");
		if (!ativa) {
			LOG.warn("Nenhum provedor de e-mail configurado (MAIL_API_KEY e MAIL_FROM): contas novas entram sem "
					+ "confirmar o e-mail e a recuperação de senha fica desativada.");
		}
	}

	/** Confirmação de e-mail e recuperação de senha estão ligadas? */
	public boolean ativa() {
		return ativa;
	}

	/** Ligadas, mas o "e-mail" só aparece no log (aviso discreto nas telas). */
	public boolean modoDesenvolvimento() {
		return ativa && !envio.real();
	}

	/** Gera e envia um código novo. Devolve {@code false} se o anterior foi pedido há menos de 1 minuto. */
	@Transactional
	public boolean enviarCodigo(Usuario usuario, String tipo) {
		Instant agora = Instant.now();
		codigos.apagarVencidos(agora);
		Optional<CodigoVerificacao> anterior = codigos.findFirstByUsuarioAndTipo(usuario, tipo);
		if (anterior.isPresent() && anterior.get().getCriadoEm().plus(INTERVALO_REENVIO).isAfter(agora)) {
			return false;
		}
		codigos.apagar(usuario, tipo);
		String codigo = "%06d".formatted(SORTEIO.nextInt(1_000_000));
		codigos.save(new CodigoVerificacao(usuario, tipo, hash(codigo), agora, agora.plus(VALIDADE)));
		boolean confirmar = CodigoVerificacao.CONFIRMAR_EMAIL.equals(tipo);
		envio.enviar(usuario.getEmail(),
				confirmar ? "Seu código de confirmação - Finora" : "Código para redefinir sua senha - Finora",
				"Olá, " + usuario.getNome() + "!\n\nSeu código para "
						+ (confirmar ? "confirmar o e-mail" : "redefinir a senha") + " no Finora é: " + codigo
						+ "\n\nEle vale por 15 minutos e só pode ser usado uma vez. "
						+ "Se não foi você quem pediu, é só ignorar este e-mail.");
		return true;
	}

	/** Confere o código digitado. Acertou: o código é apagado (uso único). Errou 5 vezes ou venceu: também. */
	@Transactional
	public boolean conferir(Usuario usuario, String tipo, String digitado) {
		CodigoVerificacao codigo = codigos.findFirstByUsuarioAndTipo(usuario, tipo).orElse(null);
		if (codigo == null) {
			return false;
		}
		if (codigo.getExpiraEm().isBefore(Instant.now())) {
			codigos.delete(codigo);
			return false;
		}
		String limpo = digitado == null ? "" : digitado.replaceAll("\\s", "");
		if (MessageDigest.isEqual(hash(limpo).getBytes(StandardCharsets.US_ASCII),
				codigo.getHash().getBytes(StandardCharsets.US_ASCII))) {
			codigos.delete(codigo);
			return true;
		}
		if (codigo.errou() >= MAX_TENTATIVAS) {
			codigos.delete(codigo);
		}
		return false;
	}

	/** Reenvia o código de confirmação, se a conta existir e ainda estiver pendente. */
	@Transactional
	public void reenviarConfirmacao(String login) {
		usuarios.buscarPorLogin(login).filter(u -> !u.isEmailConfirmado())
				.ifPresent(u -> enviarCodigo(u, CodigoVerificacao.CONFIRMAR_EMAIL));
	}

	@Transactional
	public boolean confirmarEmail(String login, String codigo) {
		Usuario usuario = usuarios.buscarPorLogin(login).filter(u -> !u.isEmailConfirmado()).orElse(null);
		if (usuario == null || !conferir(usuario, CodigoVerificacao.CONFIRMAR_EMAIL, codigo)) {
			return false;
		}
		usuario.setEmailConfirmado(true);
		return true;
	}

	/**
	 * "Esqueci minha senha": só envia o código se o nome de usuário e o e-mail
	 * forem da mesma conta. A tela mostra a mesma mensagem em qualquer caso.
	 */
	@Transactional
	public void pedirNovaSenha(String username, String email) {
		String emailNormalizado = email == null ? "" : email.trim();
		if (ativa && !emailNormalizado.isEmpty()) {
			usuarios.findByUsername(CadastroUsuario.normalizarUsername(username))
					.filter(u -> u.getEmail().equalsIgnoreCase(emailNormalizado))
					.ifPresent(u -> enviarCodigo(u, CodigoVerificacao.REDEFINIR_SENHA));
		}
	}

	@Transactional
	public boolean redefinirSenha(String login, String codigo, String senhaNova) {
		Usuario usuario = usuarios.buscarPorLogin(login).orElse(null);
		if (!ativa || usuario == null || !conferir(usuario, CodigoVerificacao.REDEFINIR_SENHA, codigo)) {
			return false;
		}
		usuario.setSenha(codificador.encode(senhaNova));
		// Quem recebeu o código no e-mail também provou que o e-mail é dele.
		if (!usuario.isEmailConfirmado()) {
			usuario.setEmailConfirmado(true);
		}
		return true;
	}

	static String hash(String codigo) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
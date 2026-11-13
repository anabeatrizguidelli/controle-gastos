package br.com.controlegastos.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Envio dos e-mails com código. Sem provedor configurado, o e-mail só vai para o
 * log (desenvolvimento). Com {@code MAIL_API_KEY} e {@code MAIL_FROM} definidos,
 * envia de verdade pela API HTTP da Brevo (o Render Free bloqueia portas SMTP).
 */
public interface EnvioEmail {

	void enviar(String para, String assunto, String texto);

	/** {@code true} quando o e-mail realmente sai para a caixa da pessoa. */
	boolean real();

	static EnvioEmail doAmbiente(Environment env) {
		String chave = env.getProperty("MAIL_API_KEY", "").trim();
		String remetente = env.getProperty("MAIL_FROM", "").trim();
		if (chave.isEmpty() || remetente.isEmpty()) {
			return new SoLog();
		}
		return new Brevo(chave, remetente, env.getProperty("MAIL_FROM_NAME", "Finora"));
	}

	/** Desenvolvimento: escreve o e-mail (com o código) no log do app. */
	final class SoLog implements EnvioEmail {

		private static final Logger LOG = LoggerFactory.getLogger(EnvioEmail.class);

		@Override
		public void enviar(String para, String assunto, String texto) {
			LOG.info("[modo desenvolvimento] E-mail para {} | {} | {}", para, assunto, texto.replace('\n', ' '));
		}

		@Override
		public boolean real() {
			return false;
		}

	}

	/** Envio real pela API transacional da Brevo, só com o cliente HTTP do Java. */
	final class Brevo implements EnvioEmail {

		private static final Logger LOG = LoggerFactory.getLogger(EnvioEmail.class);
		private static final URI ENDERECO = URI.create("https://api.brevo.com/v3/smtp/email");

		private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		private final String chave;
		private final String remetente;
		private final String nomeRemetente;

		Brevo(String chave, String remetente, String nomeRemetente) {
			this.chave = chave;
			this.remetente = remetente;
			this.nomeRemetente = nomeRemetente;
		}

		@Override
		public void enviar(String para, String assunto, String texto) {
			String json = "{\"sender\":{\"name\":" + json(nomeRemetente) + ",\"email\":" + json(remetente)
					+ "},\"to\":[{\"email\":" + json(para) + "}],\"subject\":" + json(assunto)
					+ ",\"textContent\":" + json(texto) + "}";
			HttpRequest pedido = HttpRequest.newBuilder(ENDERECO).timeout(Duration.ofSeconds(10))
					.header("api-key", chave).header("content-type", "application/json")
					.header("accept", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json)).build();
			try {
				int status = http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
				if (status / 100 != 2) {
					LOG.warn("O provedor de e-mail recusou o envio (HTTP {}).", status);
				}
			} catch (IOException e) {
				LOG.warn("Falha ao enviar e-mail: {}", e.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public boolean real() {
			return true;
		}

		static String json(String texto) {
			StringBuilder s = new StringBuilder("\"");
			for (char c : texto.toCharArray()) {
				switch (c) {
					case '"' -> s.append("\\\"");
					case '\\' -> s.append("\\\\");
					case '\n' -> s.append("\\n");
					case '\r' -> s.append("\\r");
					case '\t' -> s.append("\\t");
					default -> {
						if (c < 0x20) {
							s.append(String.format("\\u%04x", (int) c));
						} else {
							s.append(c);
						}
					}
				}
			}
			return s.append('"').toString();
		}

	}

}
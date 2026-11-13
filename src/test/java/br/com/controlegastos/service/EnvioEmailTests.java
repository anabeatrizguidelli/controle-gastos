package br.com.controlegastos.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Escolha do envio de e-mail pelo ambiente e política em produção sem provedor. */
class EnvioEmailTests {

	@Test
	void semVariaveisSoEscreveNoLog() {
		assertThat(EnvioEmail.doAmbiente(new MockEnvironment())).isInstanceOf(EnvioEmail.SoLog.class);
		assertThat(EnvioEmail.doAmbiente(new MockEnvironment().withProperty("MAIL_API_KEY", "x")))
				.as("falta MAIL_FROM").isInstanceOf(EnvioEmail.SoLog.class);
	}

	@Test
	void comVariaveisEnviaPelaBrevo() {
		EnvioEmail envio = EnvioEmail.doAmbiente(
				new MockEnvironment().withProperty("MAIL_API_KEY", "chave").withProperty("MAIL_FROM", "app@exemplo.com"));
		assertThat(envio).isInstanceOf(EnvioEmail.Brevo.class);
		assertThat(envio.real()).isTrue();
	}

	@Test
	void producaoSemProvedorNaoExigeConfirmacao() {
		MockEnvironment prod = new MockEnvironment();
		prod.setActiveProfiles("prod");
		Verificacoes semProvedor = new Verificacoes(null, null, null, new EnvioEmail.SoLog(), prod);
		assertThat(semProvedor.ativa()).isFalse();
		assertThat(semProvedor.modoDesenvolvimento()).isFalse();

		EnvioEmail brevo = EnvioEmail.doAmbiente(
				new MockEnvironment().withProperty("MAIL_API_KEY", "chave").withProperty("MAIL_FROM", "app@exemplo.com"));
		assertThat(new Verificacoes(null, null, null, brevo, prod).ativa()).isTrue();
		Verificacoes local = new Verificacoes(null, null, null, new EnvioEmail.SoLog(), new MockEnvironment());
		assertThat(local.ativa()).isTrue();
		assertThat(local.modoDesenvolvimento()).isTrue();
	}

	@Test
	void jsonEscapaOTexto() {
		assertThat(EnvioEmail.Brevo.json("Olá \"Ana\"\n\\fim")).isEqualTo("\"Olá \\\"Ana\\\"\\n\\\\fim\"");
	}

}
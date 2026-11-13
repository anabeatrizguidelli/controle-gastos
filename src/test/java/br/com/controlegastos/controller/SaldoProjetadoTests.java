package br.com.controlegastos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.controlegastos.model.Categoria;
import br.com.controlegastos.model.Despesa;
import br.com.controlegastos.model.Receita;
import br.com.controlegastos.model.Usuario;
import br.com.controlegastos.repository.CategoriaRepository;
import br.com.controlegastos.repository.DespesaRepository;
import br.com.controlegastos.repository.FonteRepository;
import br.com.controlegastos.repository.ReceitaRepository;
import br.com.controlegastos.service.CadastroDespesas;
import br.com.controlegastos.service.CadastroUsuario;
import br.com.controlegastos.service.ControleGastos;
import br.com.controlegastos.service.DespesasFixas;

/**
 * Painel: saldo projetado ao final do mês = receitas do mês − todas as despesas
 * do mês (pagas e pendentes, inclusive parcelas e despesas fixas). O mês de uma
 * despesa é o do vencimento. As datas ficam em 2031 para não depender do dia de hoje.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SaldoProjetadoTests {

	private static final YearMonth MARCO = YearMonth.of(2031, 3);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CadastroUsuario cadastroUsuario;

	@Autowired
	private CategoriaRepository categorias;

	@Autowired
	private FonteRepository fontes;

	@Autowired
	private DespesaRepository despesas;

	@Autowired
	private ReceitaRepository receitas;

	@Autowired
	private CadastroDespesas cadastro;

	@Autowired
	private DespesasFixas fixas;

	@Autowired
	private ControleGastos controle;

	private Usuario ana;

	@BeforeEach
	void criarUsuario() {
		ana = cadastroUsuario.cadastrar("Ana", "projecao@exemplo.com", "123456");
	}

	@Test
	void exemploDaTayna() throws Exception {
		despesa(ana, "Transporte", 100, "2031-03-10", false);
		despesa(ana, "Internet", 100, "2031-03-15", true);
		receita(ana, "Estágio", 300, "2031-03-05");

		assertThat(controle.totaisDoMes(ana, MARCO).saldoProjetado()).isEqualTo(100.0);
		painel(ana, "2031-03")
				.andExpect(content().string(containsString("Saldo projetado ao final de março de 2031")))
				.andExpect(content().string(containsString("+ R$ 300,00")))
				.andExpect(content().string(containsString("− R$ 100,00")))
				.andExpect(content().string(containsString("disponíveis ao final do mês")))
				.andExpect(content().string(containsString("projecao-final sobra")))
				.andExpect(content().string(not(containsString("Faltarão"))));
	}

	@Test
	void saldoNegativoMostraQuantoVaiFaltar() throws Exception {
		receita(ana, "Mesada", 100, "2031-03-01");
		despesa(ana, "Aluguel", 250, "2031-03-10", false);
		despesa(ana, "Mercado", 30.5, "2031-03-12", true);

		assertThat(controle.totaisDoMes(ana, MARCO).saldoProjetado()).isEqualTo(-180.5);
		painel(ana, "2031-03")
				.andExpect(content().string(containsString("Faltarão")))
				.andExpect(content().string(containsString("R$ 180,50")))
				.andExpect(content().string(containsString("projecao-final falta")))
				.andExpect(content().string(not(containsString("disponíveis ao final do mês"))));
	}

	@Test
	void mesSemLancamentosDaZero() throws Exception {
		assertThat(controle.totaisDoMes(ana, MARCO).saldoProjetado()).isZero();
		painel(ana, "2031-03")
				.andExpect(content().string(containsString("disponíveis ao final do mês")))
				.andExpect(content().string(containsString("projecao-final sobra")));
	}

	@Test
	void naoMisturaUsuariosNemMeses() throws Exception {
		Usuario bia = cadastroUsuario.cadastrar("Bia", "outra-projecao@exemplo.com", "123456");
		receita(ana, "Estágio", 300, "2031-03-05");
		despesa(ana, "Transporte", 100, "2031-03-10", false);
		// Outro usuário, mesmo mês: não entra.
		receita(bia, "Salário", 5000, "2031-03-05");
		despesa(bia, "Viagem", 900, "2031-03-10", false);
		// Mesmo usuário, outros meses: não entram. Comprada em março, mas vence em abril: é de abril.
		despesa(ana, "Curso", 40, "2031-02-28", false);
		receita(ana, "Bolsa", 70, "2031-04-01");
		Despesa abril = despesa(ana, "Livro", 60, "2031-04-05", false);
		abril.setData(LocalDate.of(2031, 3, 28));

		assertThat(controle.totaisDoMes(ana, MARCO).saldoProjetado()).isEqualTo(200.0);
		assertThat(controle.totaisDoMes(bia, MARCO).saldoProjetado()).isEqualTo(4100.0);
		assertThat(controle.totaisDoMes(ana, YearMonth.of(2031, 4)).saldoProjetado()).isEqualTo(10.0);
		painel(ana, "2031-03").andExpect(content().string(containsString("R$ 200,00")))
				.andExpect(content().string(not(containsString("R$ 4.100,00"))));
	}

	@Test
	void incluiParcelasEDespesasFixasDoMes() throws Exception {
		Categoria categoria = categorias.findByUsuarioOrderByNomeAsc(ana).get(0);
		// Compra de R$ 300 em 3x a partir de março: só a parcela de março (R$ 100) entra.
		Despesa compra = new Despesa("Notebook", 300, LocalDate.of(2031, 3, 1), categoria);
		compra.setVencimento(LocalDate.of(2031, 3, 20));
		cadastro.registrar(compra, 3, ana);
		// Despesa fixa de R$ 50 desde janeiro: o lançamento de março entra.
		Despesa internet = new Despesa("Internet", 50, LocalDate.of(2031, 1, 10), categoria);
		internet.setVencimento(LocalDate.of(2031, 1, 10));
		cadastro.registrarFixa(internet, ana);
		fixas.lancarAte(ana, MARCO, MARCO);
		receita(ana, "Estágio", 1000, "2031-03-05");

		assertThat(controle.totaisDoMes(ana, MARCO).despesas()).isEqualTo(150.0);
		assertThat(controle.totaisDoMes(ana, MARCO).saldoProjetado()).isEqualTo(850.0);
		painel(ana, "2031-03").andExpect(content().string(containsString("R$ 850,00")))
				.andExpect(content().string(containsString("disponíveis ao final do mês")));
	}

	private org.springframework.test.web.servlet.ResultActions painel(Usuario usuario, String mes) throws Exception {
		return mvc.perform(get("/painel").with(user(usuario.getEmail())).param("mes", mes)).andExpect(status().isOk());
	}

	private Despesa despesa(Usuario usuario, String descricao, double valor, String vencimento, boolean pago) {
		Categoria categoria = categorias.findByUsuarioOrderByNomeAsc(usuario).get(0);
		Despesa d = new Despesa(descricao, valor, LocalDate.parse(vencimento), categoria, usuario);
		d.setVencimento(LocalDate.parse(vencimento));
		d.setPago(pago);
		return despesas.save(d);
	}

	private void receita(Usuario usuario, String descricao, double valor, String data) {
		receitas.save(new Receita(descricao, valor, LocalDate.parse(data), fontes.findByUsuarioOrderByNomeAsc(usuario).get(0),
				usuario));
	}

}
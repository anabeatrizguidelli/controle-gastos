package br.com.controlegastos.model;

/**
 * Aviso de que os gastos de uma categoria chegaram a 80% ("ATENCAO") ou 100% ("ESTOURADO") do limite (RF5).
 */
public class Alerta {

	private final String categoria;
	private final String nivel;
	private final double percentualUsado;
	private final String mensagem;

	public Alerta(String categoria, String nivel, double percentualUsado, double totalGasto, double limite) {
		this.categoria = categoria;
		this.nivel = nivel;
		this.percentualUsado = percentualUsado;
		this.mensagem = "ESTOURADO".equals(nivel)
				? categoria + " passou do limite do mês: " + FormatadorMoeda.formatar(totalGasto)
						+ " de " + FormatadorMoeda.formatar(limite) + "."
				: categoria + " chegou a " + (int) percentualUsado + "% do limite do mês. Restam "
						+ FormatadorMoeda.formatar(limite - totalGasto) + ".";
	}

	public String getCategoria() {
		return categoria;
	}

	public String getNivel() {
		return nivel;
	}

	public double getPercentualUsado() {
		return percentualUsado;
	}

	public String getMensagem() {
		return mensagem;
	}

}

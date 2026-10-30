package br.com.controlegastos.service;

import br.com.controlegastos.model.Categoria;

/**
 * Quanto foi gasto numa categoria no mês, quanto isso representa do total de
 * despesas (participação) e do limite da categoria.
 */
public record ResumoCategoria(Categoria categoria, double total, double participacao, double percentualLimite,
		String nivel, String cor) {
}

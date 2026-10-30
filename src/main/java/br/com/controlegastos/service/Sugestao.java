package br.com.controlegastos.service;

/**
 * Uma recomendação mostrada na aba "Sugestões".
 *
 * @param tipo   ALERTA (problema), ATENCAO (cuidado), DICA (ideia) ou POSITIVO (está indo bem)
 * @param icone  nome do ícone usado na página
 */
public record Sugestao(String tipo, String icone, String titulo, String texto) {
}

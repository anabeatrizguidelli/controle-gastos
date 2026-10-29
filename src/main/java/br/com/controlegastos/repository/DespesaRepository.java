package br.com.controlegastos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.controlegastos.model.Despesa;

public interface DespesaRepository extends JpaRepository<Despesa, Integer> {
}

package br.com.controlegastos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.controlegastos.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
}

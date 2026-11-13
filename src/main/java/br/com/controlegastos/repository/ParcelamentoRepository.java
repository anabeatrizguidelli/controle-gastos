package br.com.controlegastos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.controlegastos.model.Parcelamento;

public interface ParcelamentoRepository extends JpaRepository<Parcelamento, Integer> {

}

package com.example.demo.repository;

import com.example.demo.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    // Buscar productos por nombre, ignorando mayúsculas/minúsculas y paginando
    Page<Producto> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);
    // Buscar productos por estado y stock > 0, y opcionalmente por nombre
    Page<Producto> findByEstadoAndStockGreaterThan(int estado, int stock, Pageable pageable);
    Page<Producto> findByEstadoAndStockGreaterThanAndNombreContainingIgnoreCase(int estado, int stock, String nombre, Pageable pageable);
}
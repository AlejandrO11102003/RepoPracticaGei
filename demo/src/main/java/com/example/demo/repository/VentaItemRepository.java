package com.example.demo.repository;

import com.example.demo.model.VentaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VentaItemRepository extends JpaRepository<VentaItem, Long> {

    // Para obtener todos los ítems de venta, ordenados por fecha de venta de la Venta a la que pertenecen (más reciente primero)
    Page<VentaItem> findAllByOrderByVentaFechaVentaDesc(Pageable pageable);

    // Para buscar ítems de venta por nombre de producto (ignorando mayúsculas/minúsculas)
    Page<VentaItem> findByProductoNombreContainingIgnoreCaseOrderByVentaFechaVentaDesc(String productoNombre, Pageable pageable);

    // Para obtener el historial de ventas de un producto específico, ordenado por fecha de venta
    List<VentaItem> findByProductoIdOrderByVentaFechaVentaDesc(Long productoId);
}
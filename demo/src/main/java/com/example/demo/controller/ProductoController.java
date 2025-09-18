package com.example.demo.controller;

import com.example.demo.model.Producto;
import com.example.demo.repository.ProductoRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoRepository productoRepository;

    // Crear un nuevo producto
    @PostMapping
    public ResponseEntity<Producto> createProducto(@Valid @RequestBody Producto producto) {
        producto.setEstado(1); // Asegurarse de que el producto se crea como activo
        Producto savedProducto = productoRepository.save(producto);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProducto);
    }

    // Obtener todos los productos con paginación y búsqueda
    @GetMapping
    public ResponseEntity<Page<Producto>> getAllProductos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) Integer estado) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Producto> productosPage;

        if (estado != null) {
            // Si se pasa el parámetro estado, filtrar por estado y stock > 0
            if (nombre != null && !nombre.isEmpty()) {
                productosPage = productoRepository.findByEstadoAndStockGreaterThanAndNombreContainingIgnoreCase(estado, 0, nombre, pageable);
            } else {
                productosPage = productoRepository.findByEstadoAndStockGreaterThan(estado, 0, pageable);
            }
        } else if (nombre != null && !nombre.isEmpty()) {
            productosPage = productoRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else {
            productosPage = productoRepository.findAll(pageable);
        }
        return ResponseEntity.ok(productosPage);
    }

    // Obtener un producto por ID
    @GetMapping("/{id}")
    public ResponseEntity<Producto> getProductoById(@PathVariable Long id) {
        Optional<Producto> producto = productoRepository.findById(id);
        return producto.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Actualizar un producto existente
    @PutMapping("/{id}")
    public ResponseEntity<Producto> updateProducto(@PathVariable Long id, @Valid @RequestBody Producto productoDetails) {
        Optional<Producto> optionalProducto = productoRepository.findById(id);
        if (optionalProducto.isPresent()) {
            Producto producto = optionalProducto.get();
            producto.setNombre(productoDetails.getNombre());
            producto.setPrecio(productoDetails.getPrecio());
            producto.setStock(productoDetails.getStock());
            producto.setEstado(productoDetails.getEstado()); // Permitir actualizar el estado
            Producto updatedProducto = productoRepository.save(producto);
            return ResponseEntity.ok(updatedProducto);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Eliminar lógicamente un producto (cambiar estado a 2 = Eliminado)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProducto(@PathVariable Long id) {
        Optional<Producto> optionalProducto = productoRepository.findById(id);
        if (optionalProducto.isPresent()) {
            Producto producto = optionalProducto.get();
            if (producto.getEstado() == 2) {
                // Si ya está eliminado lógicamente, devolver un conflicto o simplemente 204
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            producto.setEstado(2); // Establecer estado como "Eliminado"
            productoRepository.save(producto);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Cambiar estado entre activo (1) e inactivo (0)
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<Producto> toggleProductoStatus(@PathVariable Long id) {
        Optional<Producto> optionalProducto = productoRepository.findById(id);
        if (optionalProducto.isPresent()) {
            Producto producto = optionalProducto.get();
            if (producto.getEstado() == 2) {
                // No permitir cambiar estado si está eliminado lógicamente
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            producto.setEstado(producto.getEstado() == 1 ? 0 : 1); // Cambiar entre 1 (activo) y 0 (inactivo)
            Producto updatedProducto = productoRepository.save(producto);
            return ResponseEntity.ok(updatedProducto);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
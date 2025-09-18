package com.example.demo.controller;

import com.example.demo.model.Venta;
import com.example.demo.model.VentaItem;
import com.example.demo.model.Producto;
import com.example.demo.repository.ProductoRepository;
import com.example.demo.repository.VentaRepository;
import com.example.demo.repository.VentaItemRepository;
import com.example.demo.repository.ClienteRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Importar Sort
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private VentaItemRepository ventaItemRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    // DTO para la creación de la venta
    public static class VentaRequest {
        public Long clienteId;
        public List<VentaItemRequest> items;
        // El campo total se puede omitir o usar para una validación en el frontend,
        // pero el backend siempre debe calcularlo para evitar fraudes.
        public Double total;
    }

    public static class VentaItemRequest {
        public Long productoId;
        public Integer cantidad;
    }

    // Endpoint para realizar una venta
    @PostMapping
    @Transactional // Asegura que toda la operación sea atómica
    public ResponseEntity<Venta> createVenta(@Valid @RequestBody VentaRequest ventaRequest) {
        if (ventaRequest.items == null || ventaRequest.items.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }
        // Buscar el cliente
        Optional<com.example.demo.model.Cliente> optionalCliente = Optional.empty();
        if (ventaRequest.clienteId != null) {
            optionalCliente = clienteRepository.findById(ventaRequest.clienteId);
            if (optionalCliente.isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }
        }
        Venta venta = new Venta();
        venta.setFechaVenta(LocalDateTime.now());
        venta.setTotal(0.0); // Inicializar y recalcular en el backend
        optionalCliente.ifPresent(venta::setCliente);

        Venta savedVenta = ventaRepository.save(venta);
        double calculatedTotal = 0.0;

        for (VentaItemRequest itemRequest : ventaRequest.items) {
            Optional<Producto> optionalProducto = productoRepository.findById(itemRequest.productoId);
            if (optionalProducto.isEmpty()) {
                // Lanza una excepción que será manejada por GlobalExceptionHandler
                throw new RuntimeException("Producto con ID " + itemRequest.productoId + " no encontrado.");
            }
            Producto producto = optionalProducto.get();

            // Validar stock antes de decrementar
            if (producto.getStock() < itemRequest.cantidad) {
                // Lanza una excepción que será manejada por GlobalExceptionHandler
                throw new RuntimeException("Stock insuficiente para el producto: " + producto.getNombre() + ". Stock disponible: " + producto.getStock());
            }
            // Validar que el producto esté activo
            if (producto.getEstado() != 1) {
                // Lanza una excepción que será manejada por GlobalExceptionHandler
                throw new RuntimeException("El producto " + producto.getNombre() + " no está activo para la venta.");
            }

            VentaItem ventaItem = new VentaItem();
            ventaItem.setVenta(savedVenta);
            ventaItem.setProducto(producto); // Asignar el objeto Producto completo
            ventaItem.setCantidad(itemRequest.cantidad);
            ventaItem.setPrecioUnitario(producto.getPrecio()); // Registrar el precio al momento de la venta

            ventaItemRepository.save(ventaItem);

            // Decrementar stock del producto
            producto.setStock(producto.getStock() - itemRequest.cantidad);
            productoRepository.save(producto);

            calculatedTotal += (itemRequest.cantidad * producto.getPrecio());
        }

        savedVenta.setTotal(calculatedTotal);
        ventaRepository.save(savedVenta); // Actualizar la venta con el total calculado

        return ResponseEntity.status(HttpStatus.CREATED).body(savedVenta);
    }

    // DTO para devolver los ítems de venta con detalles del producto y stock actual
    public static class VentaItemResponse {
        public Long id;
        public Long ventaId;
        public String productoNombre;
        public Double precioUnitarioVenta;
        public Integer cantidadVendida;
        public Integer stockActualProducto; // Stock actual del producto en la base de datos
        public Long productoId;
        public LocalDateTime fechaVenta; // Fecha de la venta
    }

    // NUEVO ENDPOINT: Obtener todos los ítems de ventas paginados, con detalles del producto y stock actual
    @GetMapping("/items")
    public ResponseEntity<Page<VentaItemResponse>> getAllVentaItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String productName) {

        Pageable pageable = PageRequest.of(page, size);
        Page<VentaItem> ventaItemsPage;

        if (productName != null && !productName.isEmpty()) {
            ventaItemsPage = ventaItemRepository.findByProductoNombreContainingIgnoreCaseOrderByVentaFechaVentaDesc(productName, pageable);
        } else {
            ventaItemsPage = ventaItemRepository.findAllByOrderByVentaFechaVentaDesc(pageable);
        }

        Page<VentaItemResponse> responsePage = ventaItemsPage.map(item -> {
            VentaItemResponse dto = new VentaItemResponse();
            dto.id = item.getId();
            dto.ventaId = item.getVenta().getId();
            dto.productoId = item.getProducto().getId();
            dto.productoNombre = item.getProducto().getNombre();
            dto.precioUnitarioVenta = item.getPrecioUnitario();
            dto.cantidadVendida = item.getCantidad();

            // Obtener el stock actual del producto (re-consulta para asegurar el valor más reciente)
            Optional<Producto> currentProduct = productoRepository.findById(item.getProducto().getId());
            dto.stockActualProducto = currentProduct.map(Producto::getStock).orElse(0); // 0 si no se encuentra
            dto.fechaVenta = item.getVenta().getFechaVenta();
            return dto;
        });

        return ResponseEntity.ok(responsePage);
    }

    // NUEVO ENDPOINT: Obtener el historial de ventas para un producto específico
    @GetMapping("/by-product/{productId}")
    public ResponseEntity<List<VentaItemResponse>> getVentaHistoryByProductId(@PathVariable Long productId) {
        // Asegúrate de que el producto exista y no esté lógicamente eliminado si es relevante para el histórico
        Optional<Producto> productOpt = productoRepository.findById(productId);
        if (productOpt.isEmpty() || productOpt.get().getEstado() == 2) {
            return ResponseEntity.notFound().build();
        }

        // Buscar todos los VentaItem para ese producto, ordenados por fecha de venta
        List<VentaItem> ventaItems = ventaItemRepository.findByProductoIdOrderByVentaFechaVentaDesc(productId);

        List<VentaItemResponse> responseList = ventaItems.stream().map(item -> {
            VentaItemResponse dto = new VentaItemResponse();
            dto.id = item.getId();
            dto.ventaId = item.getVenta().getId();
            dto.productoId = item.getProducto().getId();
            dto.productoNombre = item.getProducto().getNombre();
            dto.precioUnitarioVenta = item.getPrecioUnitario();
            dto.cantidadVendida = item.getCantidad();

            // Para el histórico, el stock actual de ese item no es tan relevante,
            // pero podríamos cargarlo si se desea ver el stock "después de la venta" o "al momento de consultar"
            // Por simplicidad, aquí cargamos el stock actual global del producto
            Optional<Producto> currentProduct = productoRepository.findById(item.getProducto().getId());
            dto.stockActualProducto = currentProduct.map(Producto::getStock).orElse(0);
            dto.fechaVenta = item.getVenta().getFechaVenta();
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responseList);
    }

    // Endpoint para filtrar ventas por producto o fecha (sin asociación a cliente)
    @GetMapping("/historial")
    public ResponseEntity<List<Map<String, Object>>> getHistorialVentas(
            @RequestParam(required = false) String producto,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String cliente) {
        List<Venta> ventas = ventaRepository.findAll();
        System.out.println("Ventas obtenidas: " + ventas.size());
        ventas.forEach(v -> System.out.println("Venta: " + v.getId() + ", Fecha: " + v.getFechaVenta() + ", Total: " + v.getTotal()));
        List<Map<String, Object>> resultado = ventas.stream()
            .filter(v -> {
                if (fecha != null && !fecha.isEmpty()) {
                    String fechaVenta = v.getFechaVenta().toLocalDate().toString();
                    if (!fechaVenta.equals(fecha)) return false;
                }
                return true;
            })
            .filter(v -> cliente == null || cliente.isEmpty() || (v.getCliente() != null && (v.getCliente().getNombre() + (v.getCliente().getApellido() != null ? (" " + v.getCliente().getApellido()) : "")).toLowerCase().contains(cliente.toLowerCase())))
            .filter(v -> producto == null || producto.isEmpty() || v.getItems().stream().anyMatch(i -> i.getProducto().getNombre().toLowerCase().contains(producto.toLowerCase())))
            .map(v -> {
                Map<String, Object> map = new HashMap<>();
                String clienteNombre = v.getCliente() != null ? v.getCliente().getNombre() + (v.getCliente().getApellido() != null ? (" " + v.getCliente().getApellido()) : "") : "Sin cliente";
                map.put("id", v.getId());
                map.put("clienteNombre", clienteNombre);
                map.put("fecha", v.getFechaVenta());
                map.put("total", v.getTotal());
                map.put("items", v.getItems().stream().map(i -> Map.of(
                    "productoNombre", i.getProducto().getNombre(),
                    "cantidad", i.getCantidad()
                )).toList());
                return map;
            })
            .toList();
        return ResponseEntity.ok(resultado);
    }

}
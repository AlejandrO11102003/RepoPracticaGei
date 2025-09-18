package com.example.demo.controller;

import com.example.demo.model.Cliente;
import com.example.demo.repository.ClienteRepository;
import com.example.demo.service.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private FileStorageService fileStorageService;

    // Crear un nuevo cliente
    @PostMapping
    public ResponseEntity<Cliente> createCliente(@Valid @RequestBody Cliente cliente) {
        cliente.setEstado(1); // Asegurarse de que el cliente se crea como activo
        Cliente savedCliente = clienteRepository.save(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCliente);
    }

    // Obtener todos los clientes con paginación y búsqueda
    @GetMapping
    public ResponseEntity<Page<Cliente>> getAllClientes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String email) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Cliente> clientes;

        if (nombre != null && !nombre.isEmpty()) {
            clientes = clienteRepository.findByNombreContainingIgnoreCase(nombre, pageable);
        } else if (email != null && !email.isEmpty()) {
            clientes = clienteRepository.findByEmailContainingIgnoreCase(email, pageable);
        } else {
            clientes = clienteRepository.findAll(pageable);
        }
        return ResponseEntity.ok(clientes);
    }

    // Obtener un cliente por ID
    @GetMapping("/{id}")
    public ResponseEntity<Cliente> getClienteById(@PathVariable Long id) {
        return clienteRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Actualizar un cliente existente
    @PutMapping("/{id}")
    public ResponseEntity<Cliente> updateCliente(@PathVariable Long id, @Valid @RequestBody Cliente clienteDetails) {
        return clienteRepository.findById(id)
                .map(cliente -> {
                    cliente.setNombre(clienteDetails.getNombre());
                    cliente.setApellido(clienteDetails.getApellido());
                    cliente.setEmail(clienteDetails.getEmail());
                    // No actualizar rutaFoto aquí, debe hacerse con el endpoint de carga de foto
                    // Si clienteDetails.getEstado() es null, mantener el estado actual.
                    // Si se envía un estado, actualizarlo.
                    if (clienteDetails.getEstado() != null) {
                        cliente.setEstado(clienteDetails.getEstado());
                    }
                    Cliente updatedCliente = clienteRepository.save(cliente);
                    return ResponseEntity.ok(updatedCliente);
                }).orElse(ResponseEntity.notFound().build());
    }

    // Eliminar lógicamente un cliente (cambiar estado a 2)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCliente(@PathVariable Long id) {
        Optional<Cliente> optionalCliente = clienteRepository.findById(id);
        if (optionalCliente.isPresent()) {
            Cliente cliente = optionalCliente.get();
            if (cliente.getEstado() == 2) {
                // Si ya está eliminado lógicamente, devolver un conflicto o simplemente 204
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            // Eliminar la foto asociada si existe
            if (cliente.getRutaFoto() != null && !cliente.getRutaFoto().isEmpty()) {
                try {
                    fileStorageService.deleteFile(cliente.getRutaFoto());
                    cliente.setRutaFoto(null); // Limpiar la ruta de la foto en la DB
                } catch (IOException ex) {
                    // Log the error but don't fail the client deletion if photo deletion fails
                    System.err.println("Error al eliminar la foto del cliente " + cliente.getId() + ": " + ex.getMessage());
                }
            }

            cliente.setEstado(2); // Establecer estado como "Eliminado"
            clienteRepository.save(cliente);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Cambiar estado entre activo (1) e inactivo (0)
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<Cliente> toggleClienteStatus(@PathVariable Long id) {
        Optional<Cliente> optionalCliente = clienteRepository.findById(id);
        if (optionalCliente.isPresent()) {
            Cliente cliente = optionalCliente.get();
            if (cliente.getEstado() == 2) {
                // No permitir cambiar estado si está eliminado lógicamente
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            cliente.setEstado(cliente.getEstado() == 1 ? 0 : 1); // Cambiar entre 1 (activo) y 0 (inactivo)
            Cliente updatedCliente = clienteRepository.save(cliente);
            return ResponseEntity.ok(updatedCliente);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Subir y asociar una foto a un cliente
    @PostMapping("/{id}/uploadFoto")
    public ResponseEntity<Map<String, String>> uploadClienteFoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Map<String, String> response = new HashMap<>();
        try {
            Optional<Cliente> optionalCliente = clienteRepository.findById(id);
            if (optionalCliente.isEmpty()) {
                response.put("message", "Cliente no encontrado.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Cliente cliente = optionalCliente.get();
            String oldFileName = cliente.getRutaFoto(); // Obtener la ruta de la foto antigua

            // Almacenar el nuevo archivo
            String newFileName = fileStorageService.storeFile(file);
            cliente.setRutaFoto(newFileName);
            clienteRepository.save(cliente);

            // Si existe una foto antigua, eliminarla después de guardar la nueva
            if (oldFileName != null && !oldFileName.isEmpty()) {
                try {
                    fileStorageService.deleteFile(oldFileName);
                } catch (IOException ex) {
                    System.err.println("Advertencia: No se pudo eliminar la foto antigua '" + oldFileName + "': " + ex.getMessage());
                    // No lanzamos error para no interrumpir el flujo principal de carga
                }
            }

            String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/clientes/fotos/")
                    .path(newFileName)
                    .toUriString();

            response.put("fileName", newFileName);
            response.put("fileDownloadUri", fileDownloadUri);
            response.put("message", "Foto subida exitosamente.");
            return ResponseEntity.ok(response);

        } catch (RuntimeException ex) { // Captura las RuntimeException del FileStorageService
            response.put("message", "Error al subir la foto: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Descargar una foto
    @GetMapping("/fotos/{fileName:.+}")
    public ResponseEntity<Resource> downloadClienteFoto(@PathVariable String fileName) {
        try {
            Resource resource = fileStorageService.loadFileAsResource(fileName);
            String contentType = fileStorageService.getFileContentType(fileName); // Este método puede lanzar IOException

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (RuntimeException ex) { // Captura RuntimeException de loadFileAsResource
            System.err.println("Error al cargar la foto: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IOException ex) { // Captura IOException de getFileContentType
            System.err.println("Error al determinar el tipo de contenido de la foto: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
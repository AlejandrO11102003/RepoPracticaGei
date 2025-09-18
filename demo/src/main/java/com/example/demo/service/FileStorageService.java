package com.example.demo.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        // Define el directorio donde se guardarán las fotos.
        // Aquí se crea una carpeta 'uploads' en el directorio del proyecto.
        this.fileStorageLocation = Paths.get("./uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("No se pudo crear el directorio donde se almacenarán los archivos.", ex);
        }
    }

    /**
     * Almacena un archivo en el sistema de archivos.
     * Genera un nombre de archivo único para evitar colisiones.
     * @param file El archivo a almacenar.
     * @return El nombre del archivo almacenado (incluyendo el UUID).
     */
    public String storeFile(MultipartFile file) {
        // Normalizar el nombre del archivo original
        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        // Generar un nombre de archivo único prefijando con un UUID
        String fileName = UUID.randomUUID().toString() + "_" + originalFileName;

        try {
            // Verificar si el nombre del archivo contiene caracteres inválidos
            if (fileName.contains("..")) {
                throw new RuntimeException("¡Nombre de archivo inválido! La ruta contiene secuencia de ruta inválida " + fileName);
            }

            // Copiar el archivo al directorio destino (reemplazando si ya existe con el mismo nombre único)
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo almacenar el archivo " + fileName + ". Por favor, inténtelo de nuevo!", ex);
        }
    }

    /**
     * Carga un archivo como un recurso.
     * @param fileName El nombre del archivo a cargar.
     * @return El recurso del archivo.
     */
    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("Archivo no encontrado " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Archivo no encontrado " + fileName, ex);
        }
    }

    /**
     * Elimina un archivo del sistema de archivos.
     * @param fileName El nombre del archivo a eliminar.
     * @throws IOException Si ocurre un error al eliminar el archivo.
     */
    public void deleteFile(String fileName) throws IOException {
        Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        } else {
            System.err.println("Advertencia: El archivo a eliminar no existe: " + fileName);
        }
    }

    /**
     * Obtiene el tipo de contenido (MIME type) de un archivo.
     * @param fileName El nombre del archivo.
     * @return El tipo de contenido del archivo.
     * @throws IOException Si ocurre un error al determinar el tipo de contenido.
     */
    public String getFileContentType(String fileName) throws IOException {
        Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
        return Files.probeContentType(filePath);
    }
}
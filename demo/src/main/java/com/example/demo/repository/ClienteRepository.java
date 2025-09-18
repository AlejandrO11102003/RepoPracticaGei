package com.example.demo.repository;

import com.example.demo.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Page<Cliente> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);
    Page<Cliente> findByEmailContainingIgnoreCase(String email, Pageable pageable);
    
}
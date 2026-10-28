package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;

public interface DocumentoIdentidadRepository extends JpaRepository<DocumentoIdentidad, Long> {

    List<DocumentoIdentidad> findByArrendadorIdOrderByTipoAsc(Long arrendadorId);
}

package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;

public interface PerfilArrendadorRepository extends JpaRepository<PerfilArrendador, Long> {

    Optional<PerfilArrendador> findByHashDocumento(String hashDocumento);
}

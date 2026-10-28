package pe.edu.ulima.espacioya.cuentas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.cuentas.domain.Sesion;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

    Optional<Sesion> findByHashToken(String hashToken);
}

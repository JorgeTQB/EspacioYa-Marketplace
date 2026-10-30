package pe.edu.ulima.espacioya.administracion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ulima.espacioya.administracion.domain.AccionSuspension;
import pe.edu.ulima.espacioya.administracion.domain.SuspensionCuenta;

public interface SuspensionCuentaRepository extends JpaRepository<SuspensionCuenta, Long> {

    // A igual fecha gana el id mayor: el último registro guardado (HU-09, R10).
    Optional<SuspensionCuenta> findFirstByUsuarioIdAndAccionOrderByFechaDescIdDesc(Long usuarioId,
                                                                                    AccionSuspension accion);
}

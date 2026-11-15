package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

/** Contrato de {@link AlmacenamientoArchivos} sobre {@link AlmacenamientoLocal} (HU-29 R25). */
class AlmacenamientoLocalContratoTest extends ContratoAlmacenamientoArchivos {

    @TempDir
    Path directorio;

    @Override
    protected AlmacenamientoArchivos crearAlmacenamiento() {
        return new AlmacenamientoLocal(directorio);
    }
}

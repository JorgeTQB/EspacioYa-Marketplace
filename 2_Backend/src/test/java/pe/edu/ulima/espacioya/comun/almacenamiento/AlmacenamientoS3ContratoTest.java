package pe.edu.ulima.espacioya.comun.almacenamiento;

/** Contrato de {@link AlmacenamientoArchivos} sobre {@link AlmacenamientoS3} con un S3 en memoria (HU-29 R25). */
class AlmacenamientoS3ContratoTest extends ContratoAlmacenamientoArchivos {

    @Override
    protected AlmacenamientoArchivos crearAlmacenamiento() {
        return new AlmacenamientoS3(new S3EnMemoria(), "espacioya-uat-archivos");
    }
}

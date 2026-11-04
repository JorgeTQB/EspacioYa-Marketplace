package pe.edu.ulima.espacioya.cuentas.dto;

/** Lo que responde {@code POST /api/sesiones}: la sesión (201) o un desafío MFA (202) (HU-17 DT-1). */
public sealed interface RespuestaInicioSesion permits SesionIniciadaDTO, DesafioMfaDTO {
}

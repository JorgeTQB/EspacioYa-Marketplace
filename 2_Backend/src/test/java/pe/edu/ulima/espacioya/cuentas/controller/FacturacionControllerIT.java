package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.ValidadorDocumento;
import pe.edu.ulima.espacioya.cuentas.dto.ActualizacionFacturacionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.FacturacionService;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil;

/** HU-20 CA3 por la API contra PostgreSQL real: facturación, vuelta a revisión, cifrado y concurrencia. */
@SpringBootTest
// Sin volcar las solicitudes de MockMvc: R46 revisa que la salida no tenga contraseñas ni datos personales.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, PerfilSoporte.Dobles.class})
@ExtendWith(OutputCaptureExtension.class)
class FacturacionControllerIT extends PerfilSoporte {

    private static final String RUTA = "/api/arrendadores/actual/facturacion";
    private static final String RUC_NUEVO = "20131312955";
    private static final String RUC_OTRO = "20601234565";
    private static final String INCORRECTA = "Incorrecta99";
    private static final String NUEVA_RAZON = "Canchas Renombradas SAC";
    private static final String NUEVA_DIRECCION = "Av. Secreta Nueva 456";

    @Autowired
    private CuentaService cuentas;
    @Autowired
    private FacturacionService servicio;

    private long arrendadorId;

    private static String cuerpo(String tipo, String numero, String razon, String direccion, String contrasena) {
        return "{\"tipoDocumento\":" + (tipo == null ? "null" : "\"" + tipo + "\"")
                + (numero == null ? "" : ",\"numeroDocumento\":\"" + numero + "\"")
                + ",\"razonSocial\":\"" + razon + "\",\"direccionFiscal\":\"" + direccion + "\""
                + (contrasena == null ? "" : ",\"contrasena\":\"" + contrasena + "\"") + "}";
    }

    private ResultActions guardar(String sesion, String cuerpo) throws Exception {
        return mvc.perform(con(sesion, put(RUTA)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions guardar(String sesion, String numero, String razon, String direccion) throws Exception {
        return guardar(sesion, cuerpo("RUC", numero, razon, direccion, CONTRASENA));
    }

    private String sesionArrendador() throws Exception {
        arrendadorId = crearArrendador();
        return token(CORREO_ARRENDADOR);
    }

    private String estadoIdentidad() {
        return (String) filaPerfil(arrendadorId).get("estado_identidad");
    }

    private String descifrado(String columna) {
        return cifrador.descifrar(filaPerfil(arrendadorId).get(columna).toString());
    }

    private void documentoVigente() {
        jdbc.update("INSERT INTO documento_identidad (arrendador_id, tipo, ruta_archivo, tipo_mime, tamano, "
                        + "subido_en) VALUES (?, 'IDENTIDAD', ?, 'image/png', 100, ?)", arrendadorId,
                "identidad/0f8fad5b-d9cb-469f-a165-70867728950e", Timestamp.from(reloj.instant()));
    }

    // ── CA3 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R36, R37, R44, R45, R51, R7: CA3 un arrendador VERIFICADA que cambia el RUC vuelve a EN_REVISION, "
            + "con el número cifrado, la huella nueva, el aviso en la sesión y auditoría")
    void ca3CambiaRucYVuelveARevision() throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "VERIFICADA");
        mvc.perform(con(sesion, get("/api/sesiones/actual"))).andExpect(jsonPath("$.aviso").doesNotExist());

        var respuesta = guardar(sesion, RUC_NUEVO, RAZON, DIRECCION).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.facturacion.numeroDocumentoEnmascarado").value("****2955"))
                .andExpect(jsonPath("$.facturacion.estadoIdentidad").value("EN_REVISION"));
        assertThat(texto(respuesta)).doesNotContain(RUC_NUEVO).doesNotContain(CONTRASENA);

        var fila = filaPerfil(arrendadorId);
        assertThat(fila.get("estado_identidad")).isEqualTo("EN_REVISION");
        assertThat(fila.get("numero_documento").toString()).startsWith("v1:").doesNotContain(RUC_NUEVO);
        assertThat(descifrado("numero_documento")).isEqualTo(RUC_NUEVO);
        assertThat(fila.get("hash_documento")).isEqualTo(huella.calcular(RUC_NUEVO));
        assertThat(fila.get("razon_social").toString()).startsWith("v1:").doesNotContain(RAZON);
        assertThat(fila.get("direccion_fiscal").toString()).startsWith("v1:").doesNotContain(DIRECCION);

        // R44: desde la solicitud siguiente el aviso y la habilitación ya ven la identidad en revisión.
        mvc.perform(con(sesion, get("/api/sesiones/actual"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value(PerfilArrendador.AVISO_IDENTIDAD_EN_REVISION));
        var habilitacion = cuentas.habilitacionArrendador(new UsuarioAutenticado(arrendadorId, 1L, "ARRENDADOR"));
        assertThat(habilitacion.identidadVerificada()).isFalse();

        var registros = auditorias("FACTURACION_ACTUALIZADA");
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().get("usuario_id")).isEqualTo(arrendadorId);
        assertThat(registros.getFirst().get("ip")).isEqualTo(ip);
        assertThat(registros.getFirst().get("entidad")).isEqualTo("perfil_arrendador");
        assertThat(registros.getFirst().get("entidad_id")).isEqualTo(arrendadorId);
        assertThat(registros.getFirst().get("detalle"))
                .isEqualTo("campos=numeroDocumento;identidad=VERIFICADA->EN_REVISION");
    }

    @Test
    @DisplayName("HU-20 R38, R45: un arrendador VERIFICADA que cambia la razón social vuelve a EN_REVISION")
    void cambiaRazonSocial() throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "VERIFICADA");

        guardar(sesion, null, NUEVA_RAZON, DIRECCION).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturacion.razonSocial").value(NUEVA_RAZON))
                .andExpect(jsonPath("$.facturacion.estadoIdentidad").value("EN_REVISION"));

        assertThat(estadoIdentidad()).isEqualTo("EN_REVISION");
        assertThat(filaPerfil(arrendadorId).get("razon_social").toString()).doesNotContain(NUEVA_RAZON);
        assertThat(descifrado("razon_social")).isEqualTo(NUEVA_RAZON);
        assertThat(descifrado("numero_documento")).isEqualTo(RUC);
        assertThat(auditorias("FACTURACION_ACTUALIZADA").getFirst().get("detalle"))
                .isEqualTo("campos=razonSocial;identidad=VERIFICADA->EN_REVISION");
    }

    @Test
    @DisplayName("HU-20 R39, R36: cambiar solo la dirección mantiene VERIFICADA; un número vacío conserva el actual")
    void soloDireccion() throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "VERIFICADA");
        var hashAntes = filaPerfil(arrendadorId).get("hash_documento");

        guardar(sesion, "", RAZON, NUEVA_DIRECCION).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturacion.direccionFiscal").value(NUEVA_DIRECCION))
                .andExpect(jsonPath("$.facturacion.numeroDocumentoEnmascarado").value("****0970"))
                .andExpect(jsonPath("$.facturacion.estadoIdentidad").value("VERIFICADA"));

        assertThat(estadoIdentidad()).isEqualTo("VERIFICADA");
        assertThat(filaPerfil(arrendadorId).get("hash_documento")).isEqualTo(hashAntes);
        assertThat(descifrado("direccion_fiscal")).isEqualTo(NUEVA_DIRECCION);
        mvc.perform(con(sesion, get("/api/sesiones/actual"))).andExpect(jsonPath("$.aviso").doesNotExist());
    }

    @Test
    @DisplayName("HU-20 R40: NO_INICIADA y RECHAZADA cambian el documento sin cambiar el estado ni el motivo")
    void noIniciadaYRechazada() throws Exception {
        var sesion = sesionArrendador();

        guardar(sesion, RUC_NUEVO, RAZON, DIRECCION).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturacion.estadoIdentidad").value("NO_INICIADA"));
        assertThat(estadoIdentidad()).isEqualTo("NO_INICIADA");

        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = 'RECHAZADA', motivo_rechazo = 'Ilegible' "
                + "WHERE usuario_id = ?", arrendadorId);
        guardar(sesion, RUC_OTRO, NUEVA_RAZON, DIRECCION).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturacion.estadoIdentidad").value("RECHAZADA"));

        assertThat(estadoIdentidad()).isEqualTo("RECHAZADA");
        assertThat(filaPerfil(arrendadorId).get("motivo_rechazo")).isEqualTo("Ilegible");
        assertThat(descifrado("numero_documento")).isEqualTo(RUC_OTRO);
    }

    @Test
    @DisplayName("HU-20 R41: con la identidad EN_REVISION, otro documento o razón social da 400 sin cambiar nada; la "
            + "dirección sí")
    void enRevision() throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "EN_REVISION");
        var antes = filaPerfil(arrendadorId);

        guardar(sesion, RUC_NUEVO, RAZON, DIRECCION).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(FacturacionService.MENSAJE_IDENTIDAD_EN_REVISION));
        guardar(sesion, null, NUEVA_RAZON, DIRECCION).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(FacturacionService.MENSAJE_IDENTIDAD_EN_REVISION));
        assertThat(filaPerfil(arrendadorId)).isEqualTo(antes);
        assertThat(auditorias("FACTURACION_ACTUALIZADA")).isEmpty();

        guardar(sesion, null, RAZON, NUEVA_DIRECCION).andExpect(status().isOk());
        assertThat(descifrado("direccion_fiscal")).isEqualTo(NUEVA_DIRECCION);
        assertThat(estadoIdentidad()).isEqualTo("EN_REVISION");
    }

    @Test
    @DisplayName("HU-20 R42, R51: el documento de otro arrendador da 400 genérico, no revela la cuenta, cuenta un "
            + "fallo y se audita sin el número")
    void documentoDeOtroArrendador() throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "VERIFICADA");
        var otro = crearArrendador(CORREO_OTRO, RUC_OTRO);
        var antes = filaPerfil(arrendadorId);

        var respuesta = guardar(sesion, RUC_OTRO, RAZON, DIRECCION).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(FacturacionService.MENSAJE_DOCUMENTO_NO_DISPONIBLE))
                .andExpect(jsonPath("$.errores.length()").value(0));

        assertThat(texto(respuesta)).doesNotContain(CORREO_OTRO).doesNotContain(RUC_OTRO)
                .doesNotContain(String.valueOf(otro));
        assertThat(filaPerfil(arrendadorId)).isEqualTo(antes);
        var rechazos = auditorias("FACTURACION_DOCUMENTO_RECHAZADO");
        assertThat(rechazos).hasSize(1);
        assertThat(rechazos.getFirst().get("usuario_id")).isEqualTo(arrendadorId);
        assertThat(rechazos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(rechazos.getFirst().get("detalle")).isEqualTo("motivo=DOCUMENTO_EN_USO");

        // El rechazo gastó un intento del cupo de perfil: con 4 fallos más de contraseña se llega a 5.
        for (int i = 0; i < 4; i++) {
            guardar(sesion, cuerpo("RUC", null, RAZON, NUEVA_DIRECCION, INCORRECTA))
                    .andExpect(status().isBadRequest());
        }
        guardar(sesion, null, RAZON, NUEVA_DIRECCION).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));
    }

    @Test
    @DisplayName("HU-20 R43: sin cambios responde 200 con el perfil sin escribir, sin cambiar la identidad ni auditar")
    void sinCambios() throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "VERIFICADA");
        var antes = filaPerfil(arrendadorId);

        guardar(sesion, RUC, " " + RAZON + " ", DIRECCION).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturacion.estadoIdentidad").value("VERIFICADA"));

        assertThat(filaPerfil(arrendadorId)).isEqualTo(antes);
        assertThat(auditorias("FACTURACION_ACTUALIZADA")).isEmpty();
    }

    // ── Validación y reautenticación ─────────────────────────────────────

    @Test
    @DisplayName("HU-20 R35: un RUC o DNI inválido o un tipo nuevo sin número dan 400 en numeroDocumento sin "
            + "comprobar la contraseña")
    void documentoInvalido() throws Exception {
        var sesion = sesionArrendador();
        var antes = filaPerfil(arrendadorId);

        guardar(sesion, cuerpo("RUC", "20100070971", RAZON, DIRECCION, INCORRECTA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("numeroDocumento"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(ValidadorDocumento.MENSAJE_RUC_VERIFICADOR));
        guardar(sesion, cuerpo("DNI", "1234567", RAZON, DIRECCION, INCORRECTA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(ValidadorDocumento.MENSAJE_DNI));
        guardar(sesion, cuerpo("DNI", null, RAZON, DIRECCION, INCORRECTA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("numeroDocumento"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(ValidadorDocumento.MENSAJE_NUMERO_FALTA));

        assertThat(filaPerfil(arrendadorId)).isEqualTo(antes);
        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R35: tipo desconocido, razón social o dirección en blanco o largas dan un error por campo")
    void camposInvalidos() throws Exception {
        var sesion = sesionArrendador();

        guardar(sesion, cuerpo("CE", null, " ", "a".repeat(201), CONTRASENA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(3))
                .andExpect(jsonPath("$.errores[?(@.campo=='tipoDocumento')].mensaje")
                        .value(RegistroArrendadorDTO.MENSAJE_TIPO_DOCUMENTO))
                .andExpect(jsonPath("$.errores[?(@.campo=='razonSocial')].mensaje")
                        .value(RegistroArrendadorDTO.MENSAJE_RAZON_SOCIAL))
                .andExpect(jsonPath("$.errores[?(@.campo=='direccionFiscal')].mensaje")
                        .value(RegistroArrendadorDTO.MENSAJE_DIRECCION));
        guardar(sesion, cuerpo(null, null, "a".repeat(151), DIRECCION, CONTRASENA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2));
    }

    @Test
    @DisplayName("HU-20 R18: sin contraseña responde 400 en contrasena sin contar un fallo")
    void sinContrasena() throws Exception {
        var sesion = sesionArrendador();

        guardar(sesion, cuerpo("RUC", RUC_NUEVO, RAZON, DIRECCION, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(ReautenticacionPerfil.MENSAJE_CONTRASENA));

        assertThat(descifrado("numero_documento")).isEqualTo(RUC);
        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R19: una contraseña incorrecta da 400 en contrasena, no modifica nada y audita FACTURACION")
    void contrasenaIncorrecta() throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "VERIFICADA");
        var antes = filaPerfil(arrendadorId);

        guardar(sesion, cuerpo("RUC", RUC_NUEVO, RAZON, DIRECCION, INCORRECTA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(ReautenticacionPerfil.MENSAJE_CONTRASENA_INCORRECTA));

        assertThat(filaPerfil(arrendadorId)).isEqualTo(antes);
        var fallos = auditorias("PERFIL_CONTRASENA_FALLIDA");
        assertThat(fallos).hasSize(1);
        assertThat(fallos.getFirst().get("usuario_id")).isEqualTo(arrendadorId);
        assertThat(fallos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(fallos.getFirst().get("detalle")).isEqualTo("operacion=FACTURACION");
    }

    @Test
    @DisplayName("HU-20 R3: un USUARIO que envía facturación válida recibe 403")
    void usuario403() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        guardar(sesion, RUC_NUEVO, RAZON, DIRECCION).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));

        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
    }

    // ── Concurrencia (R52) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R52: dos PUT simultáneos de la misma cuenta terminan sin error de integridad, como en serie")
    void dosPutSimultaneos() throws Exception {
        arrendadorId = crearArrendador();
        identidad(arrendadorId, "VERIFICADA");
        var usuario = new UsuarioAutenticado(arrendadorId, 1L, "ARRENDADOR");
        var listos = new CountDownLatch(1);
        var resultados = new ArrayList<Callable<String>>();
        for (var ruc : List.of(RUC_NUEVO, RUC_OTRO)) {
            resultados.add(() -> {
                listos.await();
                try {
                    servicio.actualizar(usuario, new ActualizacionFacturacionDTO("RUC", ruc, RAZON, DIRECCION,
                            CONTRASENA), ip);
                    return ruc;
                } catch (ReglaNegocioException e) {
                    return e.getMessage();
                }
            });
        }

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(resultados.get(0));
            var b = hilos.submit(resultados.get(1));
            listos.countDown();
            var salida = List.of(a.get(), b.get());
            // El primero vuelve a revisión; el segundo, ya en revisión, no puede cambiar el documento (R41).
            assertThat(salida).contains(FacturacionService.MENSAJE_IDENTIDAD_EN_REVISION);
            var ganador = salida.stream().filter(r -> r.equals(RUC_NUEVO) || r.equals(RUC_OTRO)).toList();
            assertThat(ganador).hasSize(1);
            assertThat(descifrado("numero_documento")).isEqualTo(ganador.getFirst());
        }
        assertThat(estadoIdentidad()).isEqualTo("EN_REVISION");
        assertThat(auditorias("FACTURACION_ACTUALIZADA")).hasSize(1);
    }

    @Test
    @DisplayName("HU-20 R52: un PUT de facturación y la aprobación del admin a la vez terminan sin error, como en serie")
    void facturacionYDecisionDelAdmin() throws Exception {
        arrendadorId = crearArrendador();
        identidad(arrendadorId, "EN_REVISION");
        documentoVigente();
        var adminId = crearAdmin();
        var admin = new UsuarioAutenticado(adminId, 1L, "ADMIN");
        var usuario = new UsuarioAutenticado(arrendadorId, 1L, "ARRENDADOR");
        var listos = new CountDownLatch(1);
        Callable<String> editar = () -> {
            listos.await();
            servicio.actualizar(usuario, new ActualizacionFacturacionDTO("RUC", null, RAZON, NUEVA_DIRECCION,
                    CONTRASENA), ip);
            return "editado";
        };
        Callable<String> aprobar = () -> {
            listos.await();
            return cuentas.aprobarIdentidad(admin, arrendadorId);
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(editar);
            var b = hilos.submit(aprobar);
            listos.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactly("editado", "VERIFICADA");
        }

        assertThat(estadoIdentidad()).isEqualTo("VERIFICADA");
        assertThat(descifrado("direccion_fiscal")).isEqualTo(NUEVA_DIRECCION);
    }

    // ── Datos sensibles (R46) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R46: ni los logs ni la auditoría contienen la contraseña, el documento, la razón social ni la "
            + "dirección")
    void sinDatosSensibles(CapturedOutput salida) throws Exception {
        var sesion = sesionArrendador();
        identidad(arrendadorId, "VERIFICADA");
        crearArrendador(CORREO_OTRO, RUC_OTRO);

        guardar(sesion, cuerpo("RUC", RUC_NUEVO, NUEVA_RAZON, NUEVA_DIRECCION, INCORRECTA))
                .andExpect(status().isBadRequest());
        guardar(sesion, RUC_OTRO, RAZON, DIRECCION).andExpect(status().isBadRequest());
        guardar(sesion, RUC_NUEVO, NUEVA_RAZON, NUEVA_DIRECCION).andExpect(status().isOk());
        mvc.perform(con(sesion, put("/api/usuarios/actual/perfil")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nombre Secreto\",\"telefono\":\"934567812\"}")).andExpect(status().isOk());

        var registros = String.join("\n", detallesDeAuditoria());
        for (var secreto : List.of(CONTRASENA, INCORRECTA, RUC_NUEVO, RUC_OTRO, NUEVA_RAZON, NUEVA_DIRECCION,
                "Nombre Secreto", "934567812")) {
            assertThat(registros).doesNotContain(secreto);
            assertThat(salida.getAll()).doesNotContain(secreto);
        }
    }
}

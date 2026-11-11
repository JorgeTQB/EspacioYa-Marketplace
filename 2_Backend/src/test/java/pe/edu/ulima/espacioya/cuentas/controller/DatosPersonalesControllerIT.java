package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.pagos.service.impl.DatosPersonalesPagosImpl;
import pe.edu.ulima.espacioya.reservas.service.impl.DatosPersonalesReservasImpl;

/** HU-22 por la API: exportación (CA1), eliminación (CA2), impedimentos (CA3) y reautenticación (STRIDE S, I, R). */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, DatosPersonalesSoporte.Dobles.class})
class DatosPersonalesControllerIT extends DatosPersonalesSoporte {

    private static final String CONTRASENA_INCORRECTA = "Otra1234!";

    /** Usuario con reservas y pagos, y otro usuario con los suyos en el mismo espacio. */
    private record EscenarioUsuario(long usuario, long otro, long reservaPasada, long reservaCancelada,
                                    long reservaAjena, long pago, long pagoAjeno) {
    }

    private EscenarioUsuario escenarioUsuario() {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        var otro = crearUsuario(CORREO_OTRO_USUARIO);
        var pasada = reserva(usuario, espacio, "CONFIRMADA", hoy().minusDays(5), 8);
        var cancelada = reserva(usuario, espacio, "CANCELADA", hoy().plusDays(10), 8);
        var ajena = reserva(otro, espacio, "CONFIRMADA", hoy().minusDays(4), 8);
        var pago = pago(pasada, "PAGADO");
        var pagoCancelada = pago(cancelada, "REEMBOLSADO");
        reembolsoPendiente(pagoCancelada);
        var pagoAjeno = pago(ajena, "PAGADO");
        return new EscenarioUsuario(usuario, otro, pasada, cancelada, ajena, pago, pagoAjeno);
    }

    /** Arrendador con perfil, documentos, cuenta bancaria, un espacio, una reserva pasada liquidada y pagada. */
    private record EscenarioArrendador(long arrendador, long otroArrendador, long usuario, long espacio,
                                       long liquidacion, long liquidacionAjena, String documento, long reserva,
                                       long pago, long admin) {
    }

    private EscenarioArrendador escenarioArrendador() {
        var admin = crearAdmin();
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var otroArrendador = crearArrendador(CORREO_OTRO_ARRENDADOR, RUC_OTRO);
        var usuario = crearUsuario(CORREO_USUARIO);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var espacioAjeno = espacioDe(otroArrendador, "Sala Ajena");
        var documento = documentoDe(arrendador, "IDENTIDAD");
        cuentaBancaria(arrendador);
        var reserva = reserva(usuario, espacio, "CONFIRMADA", hoy().minusDays(6), 10);
        var pago = pago(reserva, "PAGADO");
        var liquidacion = liquidacion(arrendador, "PAGADA", admin);
        lineaDe(liquidacion, reserva, pago);
        var reservaAjena = reserva(usuario, espacioAjeno, "CONFIRMADA", hoy().minusDays(6), 10);
        var pagoAjeno = pago(reservaAjena, "PAGADO");
        var liquidacionAjena = liquidacion(otroArrendador, "PAGADA", admin);
        lineaDe(liquidacionAjena, reservaAjena, pagoAjeno);
        return new EscenarioArrendador(arrendador, otroArrendador, usuario, espacio, liquidacion, liquidacionAjena,
                documento, reserva, pago, admin);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> secciones(Map<String, Object> exportacion) {
        return (Map<String, Object>) exportacion.get("secciones");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Map<String, Object> exportacion, String seccion) {
        return (List<Map<String, Object>>) ((Map<String, Object>) exportacion.get("secciones")).get(seccion);
    }

    private static List<Long> ids(List<Map<String, Object>> filas, String campo) {
        return filas.stream().map(f -> ((Number) f.get(campo)).longValue()).toList();
    }

    // ── Exportación ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-22 R1, R10, R11: el usuario descarga un JSON adjunto con perfil, reservas, pagos y reseñas, sin "
            + "caché")
    void usuarioExporta() throws Exception {
        var escenario = escenarioUsuario();
        var sesion = token(CORREO_USUARIO);

        var respuesta = exportar(sesion, CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"espacioya-mis-datos-" + hoy() + ".json\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.generadaEn").exists())
                .andExpect(jsonPath("$.perfil.rol").value("USUARIO"))
                .andExpect(jsonPath("$.perfil.correo").value(CORREO_USUARIO));
        var exportacion = mapa(texto(respuesta));

        assertThat(secciones(exportacion).keySet())
                .containsExactly("reservas", "pagos", "resenas", "reclamos", "reportes");
        assertThat(ids(lista(exportacion, "reservas"), "id"))
                .containsExactly(escenario.reservaPasada(), escenario.reservaCancelada());
        assertThat(ids(lista(exportacion, "pagos"), "reservaId"))
                .containsExactlyInAnyOrder(escenario.reservaPasada(), escenario.reservaCancelada());
    }

    @Test
    @DisplayName("HU-22 R14: la exportación audita DATOS_EXPORTADOS con las secciones y sin valores personales")
    void exportacionAuditada() throws Exception {
        var escenario = escenarioUsuario();
        var sesion = token(CORREO_USUARIO);

        exportar(sesion, CONTRASENA).andExpect(status().isOk());

        var filas = auditorias("DATOS_EXPORTADOS");
        assertThat(filas).hasSize(1);
        var fila = filas.getFirst();
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(escenario.usuario());
        assertThat(fila.get("ip")).isEqualTo(ip);
        assertThat(fila.get("entidad")).isEqualTo("usuario");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(escenario.usuario());
        assertThat(fila.get("detalle")).isEqualTo("secciones=reservas,pagos,resenas,reclamos,reportes");
    }

    // ── Reseñas (HU-24) ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-24 R39: la exportación trae, después de pagos, las reseñas visibles y ocultas del titular")
    void exportaLasResenasDelUsuario() throws Exception {
        var escenario = escenarioUsuario();
        var espacio = jdbc.queryForObject("SELECT id FROM espacio", Long.class);
        var segunda = reserva(escenario.usuario(), espacio, "CONFIRMADA", hoy().minusDays(3), 8);
        resena(escenario.reservaPasada(), escenario.usuario(), espacio, 5, "Excelente", "VISIBLE");
        resena(segunda, escenario.usuario(), espacio, 2, "Regular", "OCULTA");
        resena(escenario.reservaAjena(), escenario.otro(), espacio, 1, "Texto ajeno", "VISIBLE");
        var sesion = token(CORREO_USUARIO);

        var exportacion = mapa(texto(exportar(sesion, CONTRASENA).andExpect(status().isOk())));

        assertThat(secciones(exportacion).keySet())
                .containsExactly("reservas", "pagos", "resenas", "reclamos", "reportes");
        var resenas = lista(exportacion, "resenas");
        assertThat(ids(resenas, "reservaId")).containsExactly(escenario.reservaPasada(), segunda);
        assertThat(resenas).extracting(r -> r.get("comentario")).containsExactly("Excelente", "Regular");
        assertThat(resenas).extracting(r -> r.get("estado")).containsExactly("VISIBLE", "OCULTA");
        assertThat(resenas.getFirst()).containsOnlyKeys("reservaId", "espacioNombre", "calificacion", "comentario",
                "estado", "fecha");
        assertThat(resenas.getFirst()).containsEntry("espacioNombre", "Cancha Norte")
                .containsEntry("calificacion", 5)
                .containsEntry("fecha", hoy().toString());
    }

    @Test
    @DisplayName("HU-24 R39: sin reseñas, la sección resenas aparece vacía")
    void seccionDeResenasVacia() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        var exportacion = mapa(texto(exportar(sesion, CONTRASENA).andExpect(status().isOk())));

        assertThat(secciones(exportacion)).containsKey("resenas");
        assertThat(lista(exportacion, "resenas")).isEmpty();
    }

    @Test
    @DisplayName("HU-24 R40: el arrendador no exporta la sección resenas aunque sus espacios tengan reseñas")
    void arrendadorSinSeccionDeResenas() throws Exception {
        var escenario = escenarioArrendador();
        resena(escenario.reserva(), escenario.usuario(), escenario.espacio(), 4, "Buena", "VISIBLE");
        var sesion = token(CORREO_ARRENDADOR);

        var exportacion = mapa(texto(exportar(sesion, CONTRASENA).andExpect(status().isOk())));

        assertThat(secciones(exportacion).keySet()).doesNotContain("resenas");
    }

    @Test
    @DisplayName("HU-24 R41: una reseña no impide eliminar la cuenta")
    void resenaNoImpideEliminar() throws Exception {
        var escenario = escenarioUsuario();
        var espacio = jdbc.queryForObject("SELECT id FROM espacio", Long.class);
        resena(escenario.reservaPasada(), escenario.usuario(), espacio, 5, "Excelente", "VISIBLE");
        var sesion = token(CORREO_USUARIO);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        assertThat(filaUsuario(escenario.usuario()).get("estado")).isEqualTo("ELIMINADA");
    }

    @Test
    @DisplayName("HU-24 R42, R43: eliminar la cuenta reemplaza sus comentarios y conserva calificación, estado, autor "
            + "y resumen")
    void eliminarAnonimizaLasResenas() throws Exception {
        var escenario = escenarioUsuario();
        var espacio = jdbc.queryForObject("SELECT id FROM espacio", Long.class);
        var segunda = reserva(escenario.usuario(), espacio, "CONFIRMADA", hoy().minusDays(3), 8);
        var visible = resena(escenario.reservaPasada(), escenario.usuario(), espacio, 5, "Mi teléfono es 912",
                "VISIBLE");
        var oculta = resena(segunda, escenario.usuario(), espacio, 2, "Texto oculto", "OCULTA");
        var ajena = resena(escenario.reservaAjena(), escenario.otro(), espacio, 4, "Texto ajeno", "VISIBLE");
        var antes = texto(mvc.perform(get("/api/espacios/" + espacio)).andExpect(status().isOk()));
        var sesion = token(CORREO_USUARIO);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        var filaVisible = filaResena(visible);
        assertThat(filaVisible.get("comentario")).isEqualTo("Comentario eliminado.");
        assertThat(((Number) filaVisible.get("calificacion")).intValue()).isEqualTo(5);
        assertThat(filaVisible.get("estado")).isEqualTo("VISIBLE");
        assertThat(((Number) filaVisible.get("usuario_id")).longValue()).isEqualTo(escenario.usuario());
        var filaOculta = filaResena(oculta);
        assertThat(filaOculta.get("comentario")).isEqualTo("Comentario eliminado.");
        assertThat(filaOculta.get("estado")).isEqualTo("OCULTA");
        assertThat(filaResena(ajena).get("comentario")).isEqualTo("Texto ajeno");

        var despues = mapa(texto(mvc.perform(get("/api/espacios/" + espacio)).andExpect(status().isOk())));
        assertThat(despues.get("calificacionPromedio")).isEqualTo(mapa(antes).get("calificacionPromedio"));
        assertThat(despues.get("totalResenas")).isEqualTo(mapa(antes).get("totalResenas")).isEqualTo(2);
    }

    private Map<String, Object> filaResena(long id) {
        return jdbc.queryForMap("SELECT * FROM resena WHERE id = ?", id);
    }

    @Test
    @DisplayName("HU-22 R2, R4, R9: el arrendador exporta facturación enmascarada, reservas, liquidaciones y cuenta")
    void arrendadorExporta() throws Exception {
        var escenario = escenarioArrendador();
        var sesion = token(CORREO_ARRENDADOR);

        var exportacion = mapa(texto(exportar(sesion, CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.perfil.rol").value("ARRENDADOR"))
                .andExpect(jsonPath("$.perfil.facturacion.numeroDocumento").value("****0970"))
                .andExpect(jsonPath("$.perfil.facturacion.razonSocial").value(RAZON))
                .andExpect(jsonPath("$.secciones.cuentaBancaria.numero").value("****9876"))
                .andExpect(jsonPath("$.secciones.cuentaBancaria.banco").value("BCP"))
                .andExpect(jsonPath("$.secciones.cuentaBancaria.estado").value("DISPONIBLE"))));

        assertThat(secciones(exportacion).keySet())
                .containsExactly("reservas", "liquidaciones", "cuentaBancaria", "reclamos", "reportes");
        assertThat(ids(lista(exportacion, "reservas"), "id")).containsExactly(escenario.reserva());
        assertThat(ids(lista(exportacion, "liquidaciones"), "id")).containsExactly(escenario.liquidacion());
        assertThat(auditorias("DATOS_EXPORTADOS").getFirst().get("detalle"))
                .isEqualTo("secciones=reservas,liquidaciones,cuentaBancaria,reclamos,reportes");
    }

    @Test
    @DisplayName("HU-22 R9: sin cuenta bancaria, cuentaBancaria es null")
    void arrendadorSinCuenta() throws Exception {
        crearArrendador(CORREO_ARRENDADOR, RUC);
        var sesion = token(CORREO_ARRENDADOR);

        var exportacion = mapa(texto(exportar(sesion, CONTRASENA).andExpect(status().isOk())));

        var secciones = secciones(exportacion);
        assertThat(secciones).containsKey("cuentaBancaria");
        assertThat(secciones.get("cuentaBancaria")).isNull();
        assertThat(lista(exportacion, "liquidaciones")).isEmpty();
    }

    @Test
    @DisplayName("HU-22 R12: la exportación no incluye reservas, pagos ni liquidaciones de otras cuentas")
    void soloDatosDelTitular() throws Exception {
        var deUsuario = escenarioUsuario();
        var sesion = token(CORREO_USUARIO);

        var exportacion = mapa(texto(exportar(sesion, CONTRASENA).andExpect(status().isOk())));

        assertThat(ids(lista(exportacion, "reservas"), "id")).doesNotContain(deUsuario.reservaAjena());
        assertThat(ids(lista(exportacion, "pagos"), "reservaId")).doesNotContain(deUsuario.reservaAjena());
        limpiar();

        var deArrendador = escenarioArrendador();
        var otraSesion = token(CORREO_ARRENDADOR);
        var delArrendador = mapa(texto(exportar(otraSesion, CONTRASENA).andExpect(status().isOk())));
        assertThat(ids(lista(delArrendador, "liquidaciones"), "id")).doesNotContain(deArrendador.liquidacionAjena());
        assertThat(lista(delArrendador, "reservas")).hasSize(1);
    }

    @Test
    @DisplayName("HU-22 R13: el archivo no tiene hash, documento ni cuenta completos, secreto MFA, tarjeta, id de "
            + "pasarela ni datos de quien reservó")
    void sinDatosProhibidos() throws Exception {
        escenarioArrendador();
        var arrendador = usuarios.findByCorreo(CORREO_ARRENDADOR).orElseThrow();
        mfaCompleto(arrendador.getId());
        var sesion = token(CORREO_USUARIO);
        var cuerpoUsuario = texto(exportar(sesion, CONTRASENA).andExpect(status().isOk()));
        var tokenArrendador = MfaDePrueba.iniciarSesion(mvc, jdbc, CORREO_ARRENDADOR,
                CONTRASENA, ip, reloj.instant());

        var cuerpo = texto(exportar(tokenArrendador, CONTRASENA).andExpect(status().isOk()));

        var hashUsuario = (String) filaUsuario(usuarios.findByCorreo(CORREO_USUARIO).orElseThrow().getId())
                .get("hash_contrasena");
        var transacciones = jdbc.queryForList("SELECT id_transaccion_pasarela FROM pago", String.class);
        assertThat(cuerpoUsuario).doesNotContain(hashUsuario, ULTIMOS4, "ultimos4", "idTransaccion")
                .doesNotContain(transacciones);
        assertThat(cuerpo).doesNotContain(arrendador.getHashContrasena(), RUC, NUMERO_CUENTA, "v1:",
                        (String) filaPerfil(arrendador.getId()).get("hash_documento"),
                        MfaDePrueba.SECRETO, NOMBRE_USUARIO, CORREO_USUARIO, TELEFONO_USUARIO,
                        "usuarioId", "usuarioNombre", ULTIMOS4)
                .doesNotContain(transacciones);
        assertThat(cuerpo).contains("\"mfaActivo\":true");
    }

    @Test
    @DisplayName("HU-25 R37: tras desactivar los recordatorios, la exportación trae perfil.recordatoriosActivos false")
    void exportaLaPreferenciaDeRecordatorios() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        exportar(sesion, CONTRASENA).andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.recordatoriosActivos").value(true));

        mvc.perform(con(sesion, put("/api/usuarios/actual/preferencias-correo"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recordatoriosActivos\":false}"))
                .andExpect(status().isOk());

        exportar(sesion, CONTRASENA).andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.recordatoriosActivos").value(false))
                .andExpect(jsonPath("$.perfil.mfaActivo").value(false));
    }

    // ── Reautenticación ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-22 R15: sin contraseña, las dos rutas dan 400 en contrasena sin exportar ni eliminar")
    void sinContrasena() throws Exception {
        var usuario = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        exportar(sesion, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa tu contraseña para confirmar."));
        eliminar(sesion, " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));

        assertThat(auditorias("DATOS_EXPORTADOS")).isEmpty();
        assertThat(filaUsuario(usuario).get("estado")).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("HU-22 R16, R17: una contraseña incorrecta da 400 en contrasena y audita la operación")
    void contrasenaIncorrecta() throws Exception {
        var usuario = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        exportar(sesion, CONTRASENA_INCORRECTA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La contraseña no es correcta."));
        eliminar(sesion, CONTRASENA_INCORRECTA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));

        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).extracting(f -> f.get("detalle"))
                .containsExactly("operacion=EXPORTACION", "operacion=ELIMINACION");
        assertThat(auditorias("DATOS_EXPORTADOS")).isEmpty();
        assertThat(filaUsuario(usuario).get("estado")).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("HU-22 R18: con 5 fallos en 15 minutos, exportar y eliminar dan 429 aunque la contraseña sea "
            + "correcta")
    void cupoAgotado() throws Exception {
        var usuario = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        for (int i = 0; i < 5; i++) {
            exportar(sesion, CONTRASENA_INCORRECTA).andExpect(status().isBadRequest());
        }

        exportar(sesion, CONTRASENA).andExpect(status().isTooManyRequests());
        eliminar(sesion, CONTRASENA).andExpect(status().isTooManyRequests());

        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).hasSize(5);
        assertThat(filaUsuario(usuario).get("estado")).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("HU-22 R19: el ADMIN recibe 403 en las dos rutas")
    void adminRechazado() throws Exception {
        crearAdmin();
        var sesion = token(CORREO_ADMIN);

        exportar(sesion, CONTRASENA).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        eliminar(sesion, CONTRASENA).andExpect(status().isForbidden());

        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
        assertThat(auditorias("DATOS_EXPORTADOS")).isEmpty();
    }

    @Test
    @DisplayName("HU-22 R20: con contraseña incorrecta e impedimentos, solo responde el error de contrasena")
    void contrasenaIncorrectaNoRevelaImpedimentos() throws Exception {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        reserva(usuario, espacio, "CONFIRMADA", hoy().plusDays(3), 8);
        var sesion = token(CORREO_USUARIO);

        eliminar(sesion, CONTRASENA_INCORRECTA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));
    }

    @Test
    @DisplayName("HU-22 (S): sin sesión, las dos rutas responden 401")
    void sinSesion() throws Exception {
        mvc.perform(desde(post(EXPORTACIONES), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contrasena\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(desde(post(ELIMINACION), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contrasena\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    // ── Eliminación ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-22 R21, R22, R23, R24: el usuario elimina su cuenta; queda ELIMINADA, anonimizada y sin foto")
    void usuarioEliminaSuCuenta() throws Exception {
        var escenario = escenarioUsuario();
        var foto = fotoDe(escenario.usuario());
        correoPendiente(escenario.usuario(), "carla.nueva@correo.pe");
        var sesion = token(CORREO_USUARIO);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mensaje").value("Tu cuenta fue eliminada."));

        var fila = filaUsuario(escenario.usuario());
        assertThat(fila.get("estado")).isEqualTo("ELIMINADA");
        assertThat(fila.get("correo")).isEqualTo("eliminada-" + escenario.usuario() + "@espacioya.invalid");
        assertThat(fila.get("nombre")).isNull();
        assertThat(fila.get("telefono")).isNull();
        assertThat(fila.get("correo_pendiente")).isNull();
        assertThat(fila.get("foto_ruta")).isNull();
        assertThat(fila.get("foto_tipo_mime")).isNull();
        assertThat(fila.get("rol")).isEqualTo("USUARIO");
        var hash = (String) fila.get("hash_contrasena");
        assertThat(hash).startsWith("$2").isNotEqualTo(filaUsuario(escenario.otro()).get("hash_contrasena"));
        assertThat(encoder.matches(CONTRASENA, hash)).isFalse();
        assertThat(archivos.leer(foto)).isEmpty();
    }

    @Test
    @DisplayName("HU-22 R28, R29, R30: se revocan las sesiones, se anulan los tokens y se borra el MFA")
    void revocaSesionesTokensYMfa() throws Exception {
        var usuario = crearUsuario(CORREO_USUARIO);
        var otraSesion = token(CORREO_USUARIO);
        tokenPendiente(usuario, "RECUPERACION");
        tokenPendiente(usuario, "CAMBIO_CORREO");
        var sesion = token(CORREO_USUARIO);
        mfaCompleto(usuario);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        assertThat(contar("SELECT count(*) FROM sesion WHERE usuario_id = ? AND NOT revocada", usuario)).isZero();
        assertThat(contar("SELECT count(*) FROM sesion WHERE usuario_id = ?", usuario)).isEqualTo(2);
        mvc.perform(con(sesion, get(PERFIL))).andExpect(status().isUnauthorized());
        mvc.perform(con(otraSesion, get(PERFIL))).andExpect(status().isUnauthorized());
        assertThat(contar("SELECT count(*) FROM token WHERE usuario_id = ? AND usado_en IS NULL", usuario)).isZero();
        assertThat(contar("SELECT count(*) FROM token WHERE usuario_id = ?", usuario)).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM mfa WHERE usuario_id = ?", usuario)).isZero();
        assertThat(contar("SELECT count(*) FROM codigo_respaldo WHERE usuario_id = ?", usuario)).isZero();
        assertThat(contar("SELECT count(*) FROM desafio_mfa WHERE usuario_id = ?", usuario)).isZero();
    }

    @Test
    @DisplayName("HU-22 R25, R26, R27: el arrendador elimina su cuenta; perfil anonimizado, documentos y cuenta "
            + "bancaria borrados")
    void arrendadorEliminaSuCuenta() throws Exception {
        var escenario = escenarioArrendador();
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = 'RECHAZADA', motivo_rechazo = 'Ilegible' "
                + "WHERE usuario_id = ?", escenario.arrendador());
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        var perfil = perfiles.findById(escenario.arrendador()).orElseThrow();
        assertThat(perfil.getNumeroDocumento()).isEqualTo(Usuario.TEXTO_ANONIMIZADO);
        assertThat(perfil.getRazonSocial()).isEqualTo(Usuario.TEXTO_ANONIMIZADO);
        assertThat(perfil.getDireccionFiscal()).isEqualTo(Usuario.TEXTO_ANONIMIZADO);
        var fila = filaPerfil(escenario.arrendador());
        assertThat(fila.get("hash_documento")).isEqualTo("eliminada-" + escenario.arrendador());
        assertThat(fila.get("motivo_rechazo")).isNull();
        assertThat((String) fila.get("numero_documento")).startsWith("v1:");
        assertThat(contar("SELECT count(*) FROM documento_identidad WHERE arrendador_id = ?", escenario.arrendador()))
                .isZero();
        assertThat(archivos.leer(escenario.documento())).isEmpty();
        assertThat(contar("SELECT count(*) FROM cuenta_bancaria WHERE arrendador_id = ?", escenario.arrendador()))
                .isZero();
        assertThat(filaUsuario(escenario.arrendador()).get("nombre")).isNull();
    }

    @Test
    @DisplayName("HU-22 R31: los registros contables y la auditoría se conservan sin cambios")
    void conservaRegistrosContables() throws Exception {
        var escenario = escenarioArrendador();
        var sesion = token(CORREO_ARRENDADOR);
        var antes = registrosContables();
        exportar(sesion, CONTRASENA_INCORRECTA).andExpect(status().isBadRequest());
        var auditoriaAntes = jdbc.queryForList("SELECT * FROM auditoria ORDER BY id");

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        assertThat(registrosContables()).isEqualTo(antes);
        var auditoriaDespues = jdbc.queryForList("SELECT * FROM auditoria ORDER BY id");
        assertThat(auditoriaDespues.subList(0, auditoriaAntes.size())).isEqualTo(auditoriaAntes);
        assertThat(contar("SELECT count(*) FROM espacio WHERE id = ?", escenario.espacio())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM usuario WHERE id = ?", escenario.arrendador())).isEqualTo(1);
    }

    private List<List<Map<String, Object>>> registrosContables() {
        return List.of(
                jdbc.queryForList("SELECT * FROM reserva ORDER BY id"),
                jdbc.queryForList("SELECT * FROM reserva_bloque ORDER BY id"),
                jdbc.queryForList("SELECT * FROM pago ORDER BY id"),
                jdbc.queryForList("SELECT * FROM reembolso ORDER BY id"),
                jdbc.queryForList("SELECT * FROM checkout_pago ORDER BY id"),
                jdbc.queryForList("SELECT * FROM liquidacion ORDER BY id"),
                jdbc.queryForList("SELECT * FROM liquidacion_detalle ORDER BY id"));
    }

    @Test
    @DisplayName("HU-22 R31: los pagos y reembolsos del usuario eliminado se conservan")
    void conservaPagosDelUsuario() throws Exception {
        var escenario = escenarioUsuario();
        var sesion = token(CORREO_USUARIO);
        var antes = registrosContables();

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        assertThat(registrosContables()).isEqualTo(antes);
        assertThat(contar("SELECT count(*) FROM reserva WHERE usuario_id = ?", escenario.usuario())).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-22 R32: tras eliminar, iniciar sesión con el correo y la contraseña anteriores da el 401 "
            + "genérico")
    void noIniciaSesion() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        var generico = texto(iniciarSesion("nadie.datos@correo.pe", CONTRASENA).andExpect(status().isUnauthorized()));

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        var respuesta = texto(iniciarSesion(CORREO_USUARIO, CONTRASENA).andExpect(status().isUnauthorized()));
        assertThat(respuesta).isEqualTo(generico);
    }

    @Test
    @DisplayName("HU-22 R33: el correo y el documento anteriores quedan libres para un registro nuevo")
    void correoYDocumentoLibres() throws Exception {
        var anterior = crearArrendador(CORREO_ARRENDADOR, RUC);
        var sesion = token(CORREO_ARRENDADOR);
        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        mvc.perform(desde(post("/api/arrendadores"), ip).contentType(MediaType.APPLICATION_JSON).content("""
                {"correo":"%s","contrasena":"%s","aceptaPrivacidad":true,"versionPolitica":"2026-10",
                 "nombre":"Ana Nueva","telefono":"999555666","tipoDocumento":"RUC","numeroDocumento":"%s",
                 "razonSocial":"Ana Nueva SAC","direccionFiscal":"Av. Nueva 1"}
                """.formatted(CORREO_ARRENDADOR, CONTRASENA, RUC))).andExpect(status().isAccepted());

        var nueva = usuarios.findByCorreo(CORREO_ARRENDADOR).orElseThrow();
        assertThat(nueva.getId()).isNotEqualTo(anterior);
        assertThat(filaPerfil(nueva.getId()).get("hash_documento")).isEqualTo(huella.calcular(RUC));
        assertThat(filaUsuario(anterior).get("estado")).isEqualTo("ELIMINADA");
    }

    @Test
    @DisplayName("HU-22 R33: un usuario eliminado deja su correo libre para POST /api/usuarios")
    void correoDeUsuarioLibre() throws Exception {
        var anterior = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        mvc.perform(desde(post("/api/usuarios"), ip).contentType(MediaType.APPLICATION_JSON).content("""
                {"correo":"%s","contrasena":"%s","aceptaPrivacidad":true,"versionPolitica":"2026-10"}
                """.formatted(CORREO_USUARIO, CONTRASENA))).andExpect(status().isAccepted());

        assertThat(usuarios.findByCorreo(CORREO_USUARIO).orElseThrow().getId()).isNotEqualTo(anterior);
    }

    @Test
    @DisplayName("HU-22 R34: la eliminación audita CUENTA_ELIMINADA con el rol y la IP")
    void eliminacionAuditada() throws Exception {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        var filas = auditorias("CUENTA_ELIMINADA");
        assertThat(filas).hasSize(1);
        var fila = filas.getFirst();
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(arrendador);
        assertThat(fila.get("ip")).isEqualTo(ip);
        assertThat(fila.get("entidad")).isEqualTo("usuario");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(arrendador);
        assertThat(fila.get("detalle")).isEqualTo("rol=ARRENDADOR");
    }

    @Test
    @DisplayName("HU-22 R35: si un participante falla, nada cambia: la cuenta sigue ACTIVA con sus datos y sesiones")
    void rollbackCompleto() throws Exception {
        var escenario = escenarioArrendador();
        var usuarioAntes = filaUsuario(escenario.arrendador());
        var perfilAntes = filaPerfil(escenario.arrendador());
        var sesion = token(CORREO_ARRENDADOR);
        participanteQueFalla.activar();

        eliminar(sesion, CONTRASENA).andExpect(status().isInternalServerError());

        assertThat(filaUsuario(escenario.arrendador())).isEqualTo(usuarioAntes);
        assertThat(filaPerfil(escenario.arrendador())).isEqualTo(perfilAntes);
        assertThat(contar("SELECT count(*) FROM documento_identidad WHERE arrendador_id = ?", escenario.arrendador()))
                .isEqualTo(1);
        assertThat(archivos.leer(escenario.documento())).isPresent();
        assertThat(contar("SELECT count(*) FROM cuenta_bancaria WHERE arrendador_id = ?", escenario.arrendador()))
                .isEqualTo(1);
        assertThat(auditorias("CUENTA_ELIMINADA")).isEmpty();
        mvc.perform(con(sesion, get(PERFIL))).andExpect(status().isOk());
    }

    // ── Impedimentos ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-22 R36: un usuario con una reserva futura recibe 400 en reservas y la cuenta no cambia")
    void usuarioConReservaFutura() throws Exception {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        reserva(usuario, espacio, "CONFIRMADA", hoy().plusDays(3), 8);
        var antes = filaUsuario(usuario);
        var sesion = token(CORREO_USUARIO);

        eliminar(sesion, CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("reservas"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value(DatosPersonalesReservasImpl.MENSAJE_RESERVAS_USUARIO));

        assertThat(filaUsuario(usuario)).isEqualTo(antes);
        assertThat(auditorias("CUENTA_ELIMINADA")).isEmpty();
        mvc.perform(con(sesion, get(PERFIL))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-22 R36: una reserva PENDIENTE_PAGO vigente también impide eliminar")
    void usuarioConPendienteVigente() throws Exception {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        reserva(usuario, espacio, "PENDIENTE_PAGO", hoy().plusDays(2), 8);
        var sesion = token(CORREO_USUARIO);

        eliminar(sesion, CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("reservas"));

        assertThat(filaUsuario(usuario).get("estado")).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("HU-22 R37: un arrendador con una reserva por terminar en su espacio recibe 400 en reservas")
    void arrendadorConReservaPorTerminar() throws Exception {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        reserva(usuario, espacio, "CONFIRMADA", hoy().plusDays(4), 8);
        cuentaBancaria(arrendador);
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("reservas"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value(DatosPersonalesReservasImpl.MENSAJE_RESERVAS_ARRENDADOR));

        assertThat(filaUsuario(arrendador).get("estado")).isEqualTo("ACTIVA");
        assertThat(contar("SELECT count(*) FROM cuenta_bancaria WHERE arrendador_id = ?", arrendador)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-22 R38: un arrendador con una liquidación GENERADA recibe 400 en liquidaciones")
    void arrendadorConLiquidacionPendiente() throws Exception {
        var admin = crearAdmin();
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        liquidacion(arrendador, "GENERADA", admin);
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("liquidaciones"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(DatosPersonalesPagosImpl.MENSAJE_LIQUIDACIONES));

        assertThat(filaPerfil(arrendador).get("hash_documento")).isEqualTo(huella.calcular(RUC));
    }

    @Test
    @DisplayName("HU-22 R38: una liquidación PENDIENTE_CUENTA también impide eliminar; una PAGADA no")
    void pendienteCuentaImpidePagadaNo() throws Exception {
        var admin = crearAdmin();
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var pendiente = liquidacion(arrendador, "PENDIENTE_CUENTA", admin);
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("liquidaciones"));
        jdbc.update("DELETE FROM liquidacion WHERE id = ?", pendiente);
        liquidacion(arrendador, "PAGADA", admin);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-22 R39: un arrendador con un cobro pagado aún no liquidado recibe 400 en pagos")
    void arrendadorConCobroPorLiquidar() throws Exception {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        var pasada = reserva(usuario, espacio, "CONFIRMADA", hoy().minusDays(3), 8);
        pago(pasada, "PAGADO");
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("pagos"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value(DatosPersonalesPagosImpl.MENSAJE_COBROS_POR_LIQUIDAR));
    }

    @Test
    @DisplayName("HU-22 R39: el cobro por liquidar de otro arrendador no impide eliminar")
    void cobroDeOtroNoImpide() throws Exception {
        crearArrendador(CORREO_ARRENDADOR, RUC);
        var otro = crearArrendador(CORREO_OTRO_ARRENDADOR, RUC_OTRO);
        var espacioAjeno = espacioDe(otro, "Sala Ajena");
        var usuario = crearUsuario(CORREO_USUARIO);
        pago(reserva(usuario, espacioAjeno, "CONFIRMADA", hoy().minusDays(3), 8), "PAGADO");
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-22 R40: varios impedimentos vuelven juntos en el orden reservas, liquidaciones, pagos")
    void variosImpedimentos() throws Exception {
        var admin = crearAdmin();
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        reserva(usuario, espacio, "CONFIRMADA", hoy().plusDays(4), 8);
        pago(reserva(usuario, espacio, "CONFIRMADA", hoy().minusDays(3), 8), "PAGADO");
        liquidacion(arrendador, "GENERADA", admin);
        var sesion = token(CORREO_ARRENDADOR);

        eliminar(sesion, CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(3))
                .andExpect(jsonPath("$.errores[0].campo").value("reservas"))
                .andExpect(jsonPath("$.errores[1].campo").value("liquidaciones"))
                .andExpect(jsonPath("$.errores[2].campo").value("pagos"));

        assertThat(filaUsuario(arrendador).get("estado")).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("HU-22 R41: reservas pasadas, canceladas o expiradas no impiden eliminar")
    void reservasQueNoImpiden() throws Exception {
        var arrendador = crearArrendador(CORREO_ARRENDADOR, RUC);
        var espacio = espacioDe(arrendador, "Cancha Norte");
        var usuario = crearUsuario(CORREO_USUARIO);
        reserva(usuario, espacio, "CONFIRMADA", hoy().minusDays(2), 8);
        reserva(usuario, espacio, "CANCELADA", hoy().plusDays(6), 8);
        reserva(usuario, espacio, "EXPIRADA", hoy().plusDays(7), 8);
        var sesion = token(CORREO_USUARIO);

        eliminar(sesion, CONTRASENA).andExpect(status().isOk());

        assertThat(filaUsuario(usuario).get("estado")).isEqualTo("ELIMINADA");
    }
}

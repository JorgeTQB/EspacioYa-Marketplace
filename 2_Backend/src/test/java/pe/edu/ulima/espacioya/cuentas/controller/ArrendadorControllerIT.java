package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ArrendadorControllerIT.Dobles.class})
class ArrendadorControllerIT {

    private static final String MENSAJE_RECIBIDO =
            "Te enviamos un correo de verificación. Revisa tu bandeja de entrada.";
    private static final String AVISO = "Para publicar tus espacios, primero debes verificar tu identidad.";
    private static final String RESPUESTA_202 =
            "{\"mensaje\":\"" + MENSAJE_RECIBIDO + "\",\"aviso\":\"" + AVISO + "\"}";
    private static final String REQUISITOS = "La contraseña debe tener entre 8 y 64 caracteres, "
            + "al menos una letra y un número, y no puede ser igual a tu correo.";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String MENSAJE_LIMITE = "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    private static final String MENSAJE_INESPERADO = "Ocurrió un problema. Intenta de nuevo más tarde.";

    private static final String CONTRASENA = "Segura123!";
    private static final String NOMBRE = "Ana Pérez Quispe";
    private static final String TELEFONO = "987654321";
    private static final String RUC = "20100070970";
    private static final String OTRO_RUC = "20131312955";
    private static final String DNI = "45678912";
    private static final String RAZON = "Canchas del Sur SAC";
    private static final String DIRECCION = "Av. Javier Prado Este 4600, Surco";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NotificadorFalso notificador;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private CifradorCampos cifrador;
    @Autowired
    private HuellaCampos huella;

    private String ip;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        NotificadorFalso notificadorFalso() {
            return new NotificadorFalso();
        }

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-27T15:00:00Z"));
        }
    }

    static class NotificadorFalso implements NotificadorCorreo {

        private final List<String> correos = new CopyOnWriteArrayList<>();
        private final AtomicBoolean fallar = new AtomicBoolean();

        @Override
        public void notificarVerificacion(String correoNormalizado) {
            if (fallar.get()) {
                throw new IllegalStateException("fallo simulado del servidor de correo");
            }
            correos.add(correoNormalizado);
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
            throw new UnsupportedOperationException("HU-04 no se prueba en esta clase");
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-06 no se prueba en esta clase");
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
            throw new UnsupportedOperationException("HU-06 no se prueba en esta clase");
        }

        @Override
        public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
            throw new UnsupportedOperationException("HU-09 no se prueba en esta clase");
        }

        @Override
        public void notificarCuentaReactivada(String correoNormalizado) {
            throw new UnsupportedOperationException("HU-09 no se prueba en esta clase");
        }

        @Override
        public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
            throw new UnsupportedOperationException("HU-13 no se prueba en esta clase");
        }

        @Override
        public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
            throw new UnsupportedOperationException("HU-13 no se prueba en esta clase");
        }

        @Override
        public void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso) {
        }

        @Override
        public void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso) {
        }

        @Override
        public void notificarCambioCuentaBancaria(String correoArrendador,
                pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria aviso) {
        }

        void reiniciar() {
            correos.clear();
            fallar.set(false);
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        notificador.reiniciar();
        ip = siguienteIp();
    }

    // HU-01 usa 10.1.x.x; el cupo de registro ahora es compartido entre los dos endpoints.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.2." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Cuerpos de solicitud ─────────────────────────────────────────────

    private static Map<String, Object> campos(String correo) {
        var campos = new LinkedHashMap<String, Object>();
        campos.put("correo", correo);
        campos.put("contrasena", CONTRASENA);
        campos.put("aceptaPrivacidad", true);
        campos.put("versionPolitica", "2026-10");
        campos.put("nombre", NOMBRE);
        campos.put("telefono", TELEFONO);
        campos.put("tipoDocumento", "RUC");
        campos.put("numeroDocumento", RUC);
        campos.put("razonSocial", RAZON);
        campos.put("direccionFiscal", DIRECCION);
        return campos;
    }

    private static String json(Map<String, Object> campos) {
        return campos.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + valor(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String valor(Object valor) {
        if (valor == null || valor instanceof Boolean) {
            return String.valueOf(valor);
        }
        return "\"" + valor + "\"";
    }

    private static String valido(String correo) {
        return json(campos(correo));
    }

    private static String con(String correo, String campo, Object valor) {
        var campos = campos(correo);
        campos.put(campo, valor);
        return json(campos);
    }

    private static String sin(String correo, String campo) {
        var campos = campos(correo);
        campos.remove(campo);
        return json(campos);
    }

    private static String registroUsuario(String correo) {
        var campos = new LinkedHashMap<String, Object>();
        campos.put("correo", correo);
        campos.put("contrasena", CONTRASENA);
        campos.put("aceptaPrivacidad", true);
        campos.put("versionPolitica", "2026-10");
        return json(campos);
    }

    private static String conDocumento(String correo, String tipo, String numero) {
        var campos = campos(correo);
        campos.put("tipoDocumento", tipo);
        campos.put("numeroDocumento", numero);
        return json(campos);
    }

    // ── Llamadas y consultas ─────────────────────────────────────────────

    private ResultActions registrar(String json) throws Exception {
        return registrarDesde(ip, json);
    }

    private ResultActions registrarDesde(String origen, String json) throws Exception {
        return enviar("/api/arrendadores", origen, json);
    }

    private ResultActions enviar(String ruta, String origen, String json) throws Exception {
        return mvc.perform(post(ruta)
                .with(solicitud -> {
                    solicitud.setRemoteAddr(origen);
                    return solicitud;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void esperaErrorDeCampo(String json, String campo, String mensaje) throws Exception {
        registrarDesde(siguienteIp(), json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
        sinFilasNiCorreos();
    }

    private void sinFilasNiCorreos() {
        assertThat(usuarios()).isZero();
        assertThat(perfiles()).isZero();
        assertThat(notificador.correos).isEmpty();
    }

    private int usuarios() {
        return jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class);
    }

    private int perfiles() {
        return jdbc.queryForObject("SELECT count(*) FROM perfil_arrendador", Integer.class);
    }

    private Map<String, Object> usuario(String correo) {
        return jdbc.queryForMap("SELECT * FROM usuario WHERE correo = ?", correo);
    }

    private Map<String, Object> perfilDe(String correo) {
        return jdbc.queryForMap("SELECT p.* FROM perfil_arrendador p JOIN usuario u ON u.id = p.usuario_id "
                + "WHERE u.correo = ?", correo);
    }

    private List<Map<String, Object>> auditoria(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    // ── Registro exitoso (CA1) ───────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R1: crea un usuario ARRENDADOR ACTIVA con el correo verificado")
    void creaArrendadorActivoVerificado() throws Exception {
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        var fila = usuario("ana@correo.pe");
        assertThat(fila.get("rol")).isEqualTo("ARRENDADOR");
        assertThat(fila.get("estado")).isEqualTo("ACTIVA");
        assertThat(fila.get("correo_verificado")).isEqualTo(true);
    }

    @Test
    @DisplayName("HU-02 R2: crea el perfil del arrendador con el tipo enviado, NO_INICIADA y sin motivo de rechazo")
    void creaPerfilNoIniciado() throws Exception {
        registrar(conDocumento("ana@correo.pe", "DNI", DNI)).andExpect(status().isAccepted());

        var perfil = perfilDe("ana@correo.pe");
        assertThat(perfil.get("usuario_id")).isEqualTo(usuario("ana@correo.pe").get("id"));
        assertThat(perfil.get("tipo_documento")).isEqualTo("DNI");
        assertThat(perfil.get("estado_identidad")).isEqualTo("NO_INICIADA");
        assertThat(perfil.get("motivo_rechazo")).isNull();
    }

    @Test
    @DisplayName("HU-02 R3: guarda el nombre sin espacios a los extremos y el teléfono")
    void guardaNombreYTelefono() throws Exception {
        registrar(con("ana@correo.pe", "nombre", "  " + NOMBRE + "  ")).andExpect(status().isAccepted());

        var fila = usuario("ana@correo.pe");
        assertThat(fila.get("nombre")).isEqualTo(NOMBRE);
        assertThat(fila.get("telefono")).isEqualTo(TELEFONO);
    }

    @Test
    @DisplayName("HU-02 R4: guarda la versión aceptada y la fecha del consentimiento tomada del reloj")
    void guardaConsentimiento() throws Exception {
        var ahora = reloj.instant();

        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        var fila = usuario("ana@correo.pe");
        assertThat(fila.get("version_politica")).isEqualTo("2026-10");
        assertThat(((Timestamp) fila.get("fecha_consentimiento")).toInstant()).isEqualTo(ahora);
    }

    @Test
    @DisplayName("HU-02 R5: responde 202 solo con el mensaje de registro recibido y el aviso de identidad")
    void responde202ConMensajeYAviso() throws Exception {
        registrar(valido("ana@correo.pe"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(RESPUESTA_202, true))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.rol").doesNotExist());
    }

    @Test
    @DisplayName("HU-02 R6: invoca una vez al notificador con el correo normalizado")
    void invocaAlNotificador() throws Exception {
        registrar(valido("Ana@Correo.PE")).andExpect(status().isAccepted());

        assertThat(notificador.correos).containsExactly("ana@correo.pe");
    }

    @Test
    @DisplayName("HU-02 R7: guarda el correo del arrendador normalizado")
    void guardaCorreoNormalizado() throws Exception {
        registrar(valido("Ana.Perez@Correo.PE")).andExpect(status().isAccepted());

        assertThat(jdbc.queryForList("SELECT correo FROM usuario", String.class))
                .containsExactly("ana.perez@correo.pe");
    }

    // ── Consentimiento ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R8: rechaza con 400 en aceptaPrivacidad si es falso o falta, sin crear nada ni notificar")
    void rechazaSinAceptarPrivacidad() throws Exception {
        var mensaje = "Debes aceptar los términos y la política de privacidad.";
        esperaErrorDeCampo(con("ana@correo.pe", "aceptaPrivacidad", false), "aceptaPrivacidad", mensaje);
        esperaErrorDeCampo(sin("ana@correo.pe", "aceptaPrivacidad"), "aceptaPrivacidad", mensaje);
    }

    @Test
    @DisplayName("HU-02 R9: rechaza con 400 en versionPolitica si falta o no es la vigente, sin crear ni notificar")
    void rechazaVersionInvalida() throws Exception {
        var mensaje = "La política de privacidad se actualizó. Recarga la página y vuelve a aceptarla.";
        esperaErrorDeCampo(con("ana@correo.pe", "versionPolitica", "2025-01"), "versionPolitica", mensaje);
        esperaErrorDeCampo(sin("ana@correo.pe", "versionPolitica"), "versionPolitica", mensaje);
    }

    // ── Datos de acceso, contacto y facturación ──────────────────────────

    @Test
    @DisplayName("HU-02 R10: rechaza una contraseña que no cumple la política con los requisitos en contrasena")
    void rechazaContrasenaDebil() throws Exception {
        esperaErrorDeCampo(con("ana@correo.pe", "contrasena", "corta1"), "contrasena", REQUISITOS);
        esperaErrorDeCampo(con("ana1@correo.pe", "contrasena", "ANA1@correo.pe"), "contrasena", REQUISITOS);
        esperaErrorDeCampo(con("ana@correo.pe", "contrasena", "solamenteletras"), "contrasena", REQUISITOS);
    }

    @Test
    @DisplayName("HU-02 R11: rechaza un correo ausente, sin formato o de más de 254 caracteres")
    void rechazaCorreoInvalido() throws Exception {
        var mensaje = "Ingresa un correo válido.";
        esperaErrorDeCampo(sin("ana@correo.pe", "correo"), "correo", mensaje);
        esperaErrorDeCampo(valido("no-es-correo"), "correo", mensaje);
        esperaErrorDeCampo(valido("a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "."
                + "d".repeat(60) + ".pe"), "correo", mensaje);
    }

    @Test
    @DisplayName("HU-02 R12: rechaza un nombre ausente, vacío o de más de 100 caracteres")
    void rechazaNombreInvalido() throws Exception {
        var mensaje = "Ingresa tu nombre completo (máximo 100 caracteres).";
        esperaErrorDeCampo(sin("ana@correo.pe", "nombre"), "nombre", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "nombre", "   "), "nombre", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "nombre", "a".repeat(101)), "nombre", mensaje);
    }

    @Test
    @DisplayName("HU-02 R13: rechaza un teléfono ausente o que no tiene 9 dígitos empezando con 9")
    void rechazaTelefonoInvalido() throws Exception {
        var mensaje = "Ingresa un celular de 9 dígitos que empiece con 9.";
        esperaErrorDeCampo(sin("ana@correo.pe", "telefono"), "telefono", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "telefono", "812345678"), "telefono", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "telefono", "98765432"), "telefono", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "telefono", "9876543210"), "telefono", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "telefono", "98765432a"), "telefono", mensaje);
    }

    @Test
    @DisplayName("HU-02 R14: rechaza una razón social ausente, vacía o de más de 150 caracteres")
    void rechazaRazonSocialInvalida() throws Exception {
        var mensaje = "Ingresa la razón social o tu nombre (máximo 150 caracteres).";
        esperaErrorDeCampo(sin("ana@correo.pe", "razonSocial"), "razonSocial", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "razonSocial", " "), "razonSocial", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "razonSocial", "r".repeat(151)), "razonSocial", mensaje);
    }

    @Test
    @DisplayName("HU-02 R15: rechaza una dirección fiscal ausente, vacía o de más de 200 caracteres")
    void rechazaDireccionInvalida() throws Exception {
        var mensaje = "Ingresa la dirección fiscal (máximo 200 caracteres).";
        esperaErrorDeCampo(sin("ana@correo.pe", "direccionFiscal"), "direccionFiscal", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "direccionFiscal", ""), "direccionFiscal", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "direccionFiscal", "d".repeat(201)), "direccionFiscal", mensaje);
    }

    @Test
    @DisplayName("HU-02 R16: rechaza un tipo de documento ausente o distinto de DNI y RUC")
    void rechazaTipoDocumentoInvalido() throws Exception {
        var mensaje = "Elige el tipo de documento: DNI o RUC.";
        esperaErrorDeCampo(sin("ana@correo.pe", "tipoDocumento"), "tipoDocumento", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "tipoDocumento", "PASAPORTE"), "tipoDocumento", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "tipoDocumento", "ruc"), "tipoDocumento", mensaje);
    }

    // ── RUC / DNI (CA2) ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R17: rechaza un número de documento ausente o vacío")
    void rechazaNumeroAusente() throws Exception {
        var mensaje = "Ingresa tu número de documento.";
        esperaErrorDeCampo(sin("ana@correo.pe", "numeroDocumento"), "numeroDocumento", mensaje);
        esperaErrorDeCampo(con("ana@correo.pe", "numeroDocumento", "   "), "numeroDocumento", mensaje);
    }

    @Test
    @DisplayName("HU-02 R18: rechaza un DNI que no tiene 8 dígitos, también si trae 11")
    void rechazaDniInvalido() throws Exception {
        var mensaje = "El DNI debe tener 8 dígitos.";
        esperaErrorDeCampo(conDocumento("ana@correo.pe", "DNI", "1234567"), "numeroDocumento", mensaje);
        esperaErrorDeCampo(conDocumento("ana@correo.pe", "DNI", "1234567A"), "numeroDocumento", mensaje);
        esperaErrorDeCampo(conDocumento("ana@correo.pe", "DNI", RUC), "numeroDocumento", mensaje);
    }

    @Test
    @DisplayName("HU-02 R19: rechaza un RUC que no tiene 11 dígitos")
    void rechazaRucDeLongitudInvalida() throws Exception {
        var mensaje = "El RUC debe tener 11 dígitos.";
        esperaErrorDeCampo(conDocumento("ana@correo.pe", "RUC", "2010007097"), "numeroDocumento", mensaje);
        esperaErrorDeCampo(conDocumento("ana@correo.pe", "RUC", DNI), "numeroDocumento", mensaje);
    }

    @Test
    @DisplayName("HU-02 R20: rechaza un RUC que no empieza con 10, 15, 17 o 20")
    void rechazaRucConPrefijoInvalido() throws Exception {
        esperaErrorDeCampo(conDocumento("ana@correo.pe", "RUC", "30000000007"), "numeroDocumento",
                "El RUC debe empezar con 10, 15, 17 o 20.");
    }

    @Test
    @DisplayName("HU-02 R21: rechaza un RUC con el dígito verificador incorrecto")
    void rechazaRucConVerificadorInvalido() throws Exception {
        esperaErrorDeCampo(conDocumento("ana@correo.pe", "RUC", "20100070971"), "numeroDocumento",
                "El RUC no es válido. Revisa que lo hayas escrito bien.");
    }

    // ── Correo o documento existentes ────────────────────────────────────

    @Test
    @DisplayName("HU-02 R22, R23, R24: con el correo de un USUARIO responde igual, no lo cambia y audita")
    void correoDeUsuarioExistente() throws Exception {
        enviar("/api/usuarios", siguienteIp(), registroUsuario("ana@correo.pe")).andExpect(status().isAccepted());
        var antes = usuario("ana@correo.pe");
        notificador.reiniciar();

        registrar(valido("Ana@correo.pe"))
                .andExpect(status().isAccepted())
                .andExpect(content().json(RESPUESTA_202, true));

        assertThat(usuario("ana@correo.pe")).isEqualTo(antes);
        assertThat(usuario("ana@correo.pe").get("rol")).isEqualTo("USUARIO");
        assertThat(usuarios()).isEqualTo(1);
        assertThat(perfiles()).isZero();
        assertThat(notificador.correos).isEmpty();
        var eventos = auditoria("REGISTRO_CORREO_EXISTENTE");
        assertThat(eventos).hasSize(1);
        assertThat(eventos.getFirst().get("usuario_id")).isEqualTo(antes.get("id"));
        assertThat(eventos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(((Timestamp) eventos.getFirst().get("fecha")).toInstant()).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-02 R25, R26, R27: con un documento ya registrado responde igual, no crea la cuenta y audita")
    void documentoExistente() throws Exception {
        registrarDesde(siguienteIp(), valido("titular@correo.pe")).andExpect(status().isAccepted());
        var titular = usuario("titular@correo.pe");
        notificador.reiniciar();

        registrar(con("otra@correo.pe", "numeroDocumento", " " + RUC + " "))
                .andExpect(status().isAccepted())
                .andExpect(content().json(RESPUESTA_202, true));

        assertThat(usuarios()).isEqualTo(1);
        assertThat(perfiles()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo = 'otra@correo.pe'", Integer.class))
                .isZero();
        assertThat(notificador.correos).isEmpty();
        var eventos = auditoria("REGISTRO_DOCUMENTO_EXISTENTE");
        assertThat(eventos).hasSize(1);
        assertThat(eventos.getFirst().get("usuario_id")).isEqualTo(titular.get("id"));
        assertThat(eventos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(((Timestamp) eventos.getFirst().get("fecha")).toInstant()).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-02 R28: un RUC inválido con correo existente responde igual que con datos nuevos")
    void validaAntesDeConsultarLaBd() throws Exception {
        registrarDesde(siguienteIp(), valido("ana@correo.pe")).andExpect(status().isAccepted());

        var conExistentes = registrarDesde(siguienteIp(), conDocumento("ana@correo.pe", "RUC", "20100070971"))
                .andReturn().getResponse();
        var conNuevos = registrarDesde(siguienteIp(), conDocumento("nuevo@correo.pe", "RUC", "20100070971"))
                .andReturn().getResponse();

        assertThat(conExistentes.getStatus()).isEqualTo(400).isEqualTo(conNuevos.getStatus());
        assertThat(conExistentes.getContentAsString()).isEqualTo(conNuevos.getContentAsString());
        assertThat(auditoria("REGISTRO_CORREO_EXISTENTE")).isEmpty();
    }

    @Test
    @DisplayName("HU-02 R29: dos registros simultáneos con el mismo correo crean una sola cuenta y ambos reciben 202")
    void carreraPorCorreo() throws Exception {
        var listos = new CountDownLatch(1);
        var numeros = List.of(RUC, OTRO_RUC);
        var indice = new AtomicInteger();
        Callable<MvcResult> registro = () -> {
            var numero = numeros.get(indice.getAndIncrement());
            listos.await();
            return registrarDesde(siguienteIp(), con("ana@correo.pe", "numeroDocumento", numero)).andReturn();
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(registro);
            var b = hilos.submit(registro);
            listos.countDown();
            assertThat(List.of(a.get().getResponse().getStatus(), b.get().getResponse().getStatus()))
                    .containsExactly(202, 202);
            assertThat(a.get().getResponse().getContentAsString())
                    .isEqualTo(b.get().getResponse().getContentAsString());
        }

        assertThat(usuarios()).isEqualTo(1);
        assertThat(perfiles()).isEqualTo(1);
        assertThat(notificador.correos).containsExactly("ana@correo.pe");
    }

    @Test
    @DisplayName("HU-02 R30: dos registros simultáneos con el mismo documento crean una cuenta y ambos reciben 202")
    void carreraPorDocumento() throws Exception {
        var listos = new CountDownLatch(1);
        var correos = List.of("ana@correo.pe", "beto@correo.pe");
        var indice = new AtomicInteger();
        Callable<MvcResult> registro = () -> {
            var correo = correos.get(indice.getAndIncrement());
            listos.await();
            return registrarDesde(siguienteIp(), valido(correo)).andReturn();
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(registro);
            var b = hilos.submit(registro);
            listos.countDown();
            assertThat(List.of(a.get().getResponse().getStatus(), b.get().getResponse().getStatus()))
                    .containsExactly(202, 202);
            assertThat(a.get().getResponse().getContentAsString())
                    .isEqualTo(b.get().getResponse().getContentAsString());
        }

        assertThat(usuarios()).isEqualTo(1);
        assertThat(perfiles()).isEqualTo(1);
        assertThat(notificador.correos).hasSize(1);
        assertThat(auditoria("REGISTRO_DOCUMENTO_EXISTENTE")).hasSize(1);
    }

    // ── Rol asignado en el servidor (STRIDE E) ───────────────────────────

    @Test
    @DisplayName("HU-02 R31: ignora rol, estado, correoVerificado y estadoIdentidad enviados por el cliente")
    void ignoraCamposDeRolYEstado() throws Exception {
        var campos = campos("ana@correo.pe");
        campos.put("rol", "ADMIN");
        campos.put("estado", "SUSPENDIDA");
        campos.put("correoVerificado", false);
        campos.put("estadoIdentidad", "VERIFICADA");

        registrar(json(campos)).andExpect(status().isAccepted());

        var fila = usuario("ana@correo.pe");
        assertThat(fila.get("rol")).isEqualTo("ARRENDADOR");
        assertThat(fila.get("estado")).isEqualTo("ACTIVA");
        assertThat(fila.get("correo_verificado")).isEqualTo(true);
        assertThat(perfilDe("ana@correo.pe").get("estado_identidad")).isEqualTo("NO_INICIADA");
    }

    // ── Auditoría ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R32: audita REGISTRO_ARRENDADOR con el id nuevo, la IP, la fecha y la versión")
    void auditaRegistro() throws Exception {
        var ahora = reloj.instant();

        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        var eventos = auditoria("REGISTRO_ARRENDADOR");
        assertThat(eventos).hasSize(1);
        assertThat(eventos.getFirst().get("usuario_id")).isEqualTo(usuario("ana@correo.pe").get("id"));
        assertThat(eventos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(((Timestamp) eventos.getFirst().get("fecha")).toInstant()).isEqualTo(ahora);
        assertThat((String) eventos.getFirst().get("detalle")).contains("2026-10");
    }

    @Test
    @DisplayName("HU-02 R33: ninguna auditoría guarda el documento, la razón social, la dirección ni el teléfono")
    void auditoriaSinDatosDeFacturacion() throws Exception {
        registrarDesde(siguienteIp(), valido("ana@correo.pe")).andExpect(status().isAccepted());
        registrarDesde(siguienteIp(), valido("ana@correo.pe")).andExpect(status().isAccepted());
        registrarDesde(siguienteIp(), valido("otra@correo.pe")).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Integer.class)).isEqualTo(3);
        var detalles = jdbc.queryForList("SELECT coalesce(detalle, '') FROM auditoria", String.class);
        assertThat(detalles).allSatisfy(detalle -> assertThat(detalle)
                .doesNotContain(RUC).doesNotContain(RAZON).doesNotContain(DIRECCION).doesNotContain(TELEFONO));
    }

    // ── Cifrado en reposo (STRIDE I) ─────────────────────────────────────

    @Test
    @DisplayName("HU-02 R34: guarda número, razón social y dirección cifrados (v1:) y se descifran al original")
    void guardaFacturacionCifrada() throws Exception {
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());

        var perfil = perfilDe("ana@correo.pe");
        var numero = (String) perfil.get("numero_documento");
        var razon = (String) perfil.get("razon_social");
        var direccion = (String) perfil.get("direccion_fiscal");
        assertThat(numero).startsWith("v1:").doesNotContain(RUC);
        assertThat(razon).startsWith("v1:").doesNotContain(RAZON).doesNotContain("Canchas");
        assertThat(direccion).startsWith("v1:").doesNotContain(DIRECCION).doesNotContain("Javier Prado");
        assertThat(cifrador.descifrar(numero)).isEqualTo(RUC);
        assertThat(cifrador.descifrar(razon)).isEqualTo(RAZON);
        assertThat(cifrador.descifrar(direccion)).isEqualTo(DIRECCION);
    }

    @Test
    @DisplayName("HU-02 R37: guarda en hash_documento el HMAC-SHA256 del número normalizado")
    void guardaHuellaDelDocumento() throws Exception {
        registrar(con("ana@correo.pe", "numeroDocumento", "  " + RUC + " ")).andExpect(status().isAccepted());

        var hash = (String) perfilDe("ana@correo.pe").get("hash_documento");
        assertThat(hash).isEqualTo(huella.calcular(RUC)).matches("[0-9a-f]{64}");
    }

    // ── Minimización en tránsito ─────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R40: ni el 202 ni el 400 devuelven documento, facturación, teléfono, nombre ni contraseña")
    void respuestasSinDatosSensibles() throws Exception {
        var exito = registrar(valido("ana@correo.pe")).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        var error = registrarDesde(siguienteIp(), conDocumento("beto@correo.pe", "RUC", "20100070971"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        for (var cuerpo : List.of(exito, error)) {
            assertThat(cuerpo).doesNotContain("2010007097").doesNotContain(RAZON).doesNotContain(DIRECCION)
                    .doesNotContain(TELEFONO).doesNotContain(NOMBRE).doesNotContain(CONTRASENA);
        }
    }

    // ── Límite de registros por IP (STRIDE D) ────────────────────────────

    @Test
    @DisplayName("HU-02 R42: el cupo de 5 registros por IP y hora se comparte con /api/usuarios")
    void cupoCompartidoConUsuarios() throws Exception {
        for (int i = 1; i <= 3; i++) {
            enviar("/api/usuarios", ip, registroUsuario("persona" + i + "@correo.pe"))
                    .andExpect(status().isAccepted());
        }
        registrar(con("ana@correo.pe", "telefono", "1")).andExpect(status().isBadRequest());
        registrar(conDocumento("ana@correo.pe", "RUC", "1")).andExpect(status().isBadRequest());

        registrar(valido("ana@correo.pe"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"mensaje\":\"" + MENSAJE_LIMITE + "\",\"errores\":[]}", true));

        assertThat(usuarios()).isEqualTo(3);
        assertThat(perfiles()).isZero();
        enviar("/api/usuarios", ip, registroUsuario("persona9@correo.pe")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-02 R43: cuentan también los intentos rechazados con 400 y los de correo o documento existente")
    void cuentanLosIntentosInvalidosYExistentes() throws Exception {
        registrarDesde(siguienteIp(), valido("titular@correo.pe")).andExpect(status().isAccepted());

        registrar("{ esto no es json").andExpect(status().isBadRequest());
        registrar(conDocumento("ana@correo.pe", "RUC", "20100070971")).andExpect(status().isBadRequest());
        registrar(con("titular@correo.pe", "numeroDocumento", OTRO_RUC)).andExpect(status().isAccepted());
        registrar(valido("ana@correo.pe")).andExpect(status().isAccepted());
        registrar(con("ana@correo.pe", "aceptaPrivacidad", false)).andExpect(status().isBadRequest());

        registrar(con("nuevo@correo.pe", "numeroDocumento", OTRO_RUC)).andExpect(status().isTooManyRequests());

        assertThat(usuarios()).isEqualTo(1);
        assertThat(perfiles()).isEqualTo(1);
    }

    // ── Errores genéricos ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R44: si el notificador falla responde 500 genérico sin dejar usuario ni perfil")
    void falloDelNotificador() throws Exception {
        notificador.fallar.set(true);

        registrar(valido("ana@correo.pe"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensaje\":\"" + MENSAJE_INESPERADO + "\",\"errores\":[]}", true))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("servidor de correo"))));

        assertThat(usuarios()).isZero();
        assertThat(perfiles()).isZero();
        assertThat(auditoria("REGISTRO_ARRENDADOR")).isEmpty();
    }
}

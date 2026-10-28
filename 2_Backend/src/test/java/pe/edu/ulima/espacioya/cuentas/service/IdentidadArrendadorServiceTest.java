package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoValidado;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.almacenamiento.MotivoRechazoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.TipoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.UrlFirmada;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo.ResultadoValidacion;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class IdentidadArrendadorServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final long ID = 7L;
    private static final String IP = "10.5.0.1";
    private static final String CLAVE_IDENTIDAD = "identidad/0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String CLAVE_PROPIEDAD = "identidad/1f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String CLAVE_ANTERIOR = "identidad/2f8fad5b-d9cb-469f-a165-70867728950e";
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");

    private static final ArchivoRecibido PDF = recibido("%PDF-1.4 identidad");
    private static final ArchivoRecibido PNG = recibido("png de propiedad");
    private static final ArchivoValidado PDF_VALIDADO =
            new ArchivoValidado(PDF.contenido(), TipoArchivo.PDF, PDF.tamano());
    private static final ArchivoValidado PNG_VALIDADO =
            new ArchivoValidado("png limpio".getBytes(StandardCharsets.UTF_8), TipoArchivo.PNG, PNG.tamano());

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private DocumentoIdentidadRepository documentos;
    @Mock
    private ValidadorArchivo validador;
    @Mock
    private ArchivosPrivados archivos;
    @Mock
    private AuditoriaService auditoria;

    private IdentidadArrendadorService servicio;
    private final AtomicLong siguienteId = new AtomicLong(100);

    @BeforeEach
    void preparar() {
        var transacciones = new TransactionTemplate(mock(PlatformTransactionManager.class));
        servicio = new IdentidadArrendadorService(usuarios, perfiles, documentos, validador, archivos, auditoria,
                transacciones, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ArchivoRecibido recibido(String texto) {
        var bytes = texto.getBytes(StandardCharsets.UTF_8);
        return new ArchivoRecibido(bytes, bytes.length);
    }

    private static Usuario cuenta(boolean correoVerificado) {
        var usuario = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$hash", "Ana", "987654321", "2026-10",
                AHORA.minusSeconds(7200));
        ReflectionTestUtils.setField(usuario, "id", ID);
        ReflectionTestUtils.setField(usuario, "correoVerificado", correoVerificado);
        return usuario;
    }

    private static PerfilArrendador perfil(EstadoIdentidad estado, String motivo) {
        var perfil = PerfilArrendador.crear(ID, TipoDocumento.RUC, "20100070970", "c".repeat(64), "Canchas SAC",
                "Lima");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", estado);
        ReflectionTestUtils.setField(perfil, "motivoRechazo", motivo);
        return perfil;
    }

    private static DocumentoIdentidad documento(long id, TipoDocumentoIdentidad tipo, String clave, String mime) {
        var documento = DocumentoIdentidad.registrar(ID, tipo, clave, mime, 1234, AHORA.minusSeconds(60));
        ReflectionTestUtils.setField(documento, "id", id);
        return documento;
    }

    private void dado(Usuario usuario, PerfilArrendador perfil) {
        when(usuarios.findById(ID)).thenReturn(Optional.of(usuario));
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil));
    }

    private void validacionAceptada() {
        when(validador.validar(PDF)).thenReturn(new ResultadoValidacion.Aceptado(PDF_VALIDADO));
        when(validador.validar(PNG)).thenReturn(new ResultadoValidacion.Aceptado(PNG_VALIDADO));
        when(archivos.guardar("identidad", PDF_VALIDADO.contenido())).thenReturn(CLAVE_IDENTIDAD);
        when(archivos.guardar("identidad", PNG_VALIDADO.contenido())).thenReturn(CLAVE_PROPIEDAD);
    }

    private void guardadoConId() {
        when(documentos.save(any())).thenAnswer(invocacion -> {
            DocumentoIdentidad documento = invocacion.getArgument(0);
            ReflectionTestUtils.setField(documento, "id", siguienteId.getAndIncrement());
            return documento;
        });
    }

    private List<EventoAuditoria> auditorias(int veces) {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(veces)).registrar(eventos.capture());
        return eventos.getAllValues();
    }

    @Test
    @DisplayName("HU-05 R17, R20: una cuenta USUARIO recibe acceso denegado sin validar ni almacenar archivos")
    void rolUsuarioDenegado() {
        var usuario = new UsuarioAutenticado(ID, 31L, "USUARIO");

        assertThatThrownBy(() -> servicio.enviar(usuario, PDF, PNG, IP)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.consultar(usuario)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(validador, archivos, auditoria, documentos);
    }

    @Test
    @DisplayName("HU-05 R18, R20: un arrendador con correo no verificado recibe acceso denegado sin tocar archivos")
    void correoNoVerificadoDenegado() {
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta(false)));

        assertThatThrownBy(() -> servicio.enviar(ARRENDADOR, PDF, PNG, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(validador, archivos, auditoria, documentos);
    }

    @Test
    @DisplayName("HU-05 R16, R20, R41: en EN_REVISION rechaza con el mensaje de estado, audita y no toca archivos")
    void estadoNoPermitido() {
        dado(cuenta(true), perfil(EstadoIdentidad.EN_REVISION, null));

        assertThatThrownBy(() -> servicio.enviar(ARRENDADOR, PDF, PNG, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya enviaste tus documentos o tu identidad ya está verificada.");
        var evento = auditorias(1).getFirst();
        assertThat(evento).isEqualTo(new EventoAuditoria(ID, "CARGA_DOCUMENTO_RECHAZADA", "perfil_arrendador", ID,
                IP, "estado=EN_REVISION"));
        verifyNoInteractions(validador, archivos, documentos);
    }

    @Test
    @DisplayName("HU-05 R14, R15, R41: dos partes inválidas lanzan un error por parte, auditan y no almacenan nada")
    void dosPartesInvalidas() {
        dado(cuenta(true), perfil(EstadoIdentidad.NO_INICIADA, null));
        when(validador.validar(PDF)).thenReturn(new ResultadoValidacion.Rechazado(
                MotivoRechazoArchivo.TIPO_NO_PERMITIDO));
        when(validador.validar(PNG)).thenReturn(new ResultadoValidacion.Rechazado(
                MotivoRechazoArchivo.TAMANO_EXCEDIDO));

        assertThatThrownBy(() -> servicio.enviar(ARRENDADOR, PDF, PNG, IP))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores()).containsExactly(
                        new ErrorCampoDTO("documentoIdentidad", "El archivo debe ser PDF, JPG o PNG."),
                        new ErrorCampoDTO("documentoPropiedad", "El archivo debe pesar como máximo 5 MB.")));
        var evento = auditorias(1).getFirst();
        assertThat(evento.accion()).isEqualTo("CARGA_DOCUMENTO_RECHAZADA");
        assertThat(evento.detalle())
                .isEqualTo("documentoIdentidad=TIPO_NO_PERMITIDO;documentoPropiedad=TAMANO_EXCEDIDO");
        verifyNoInteractions(archivos, documentos);
    }

    @Test
    @DisplayName("HU-05 R8: una parte VACIO usa el mensaje propio de cada documento")
    void vacioUsaMensajePropio() {
        dado(cuenta(true), perfil(EstadoIdentidad.NO_INICIADA, null));
        var vacio = new ArchivoRecibido(null, 0);
        when(validador.validar(vacio)).thenReturn(new ResultadoValidacion.Rechazado(MotivoRechazoArchivo.VACIO));

        assertThatThrownBy(() -> servicio.enviar(ARRENDADOR, vacio, vacio, IP))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores()).containsExactly(
                        new ErrorCampoDTO("documentoIdentidad", "Sube tu documento de identidad (DNI o RUC)."),
                        new ErrorCampoDTO("documentoPropiedad",
                                "Sube el documento que acredita la propiedad o autorización del espacio.")));
        assertThat(auditorias(1).getFirst().detalle()).isEqualTo("documentoIdentidad=VACIO;documentoPropiedad=VACIO");
    }

    @Test
    @DisplayName("HU-05 R14: si la transacción falla se eliminan los dos archivos nuevos y el estado no cambia")
    void falloEnTransaccionEliminaArchivosNuevos() {
        var perfil = perfil(EstadoIdentidad.NO_INICIADA, null);
        dado(cuenta(true), perfil);
        validacionAceptada();
        when(perfiles.findParaActualizar(ID)).thenReturn(Optional.of(perfil));
        when(documentos.findByArrendadorIdOrderByTipoAsc(ID)).thenReturn(List.of());
        when(documentos.save(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> servicio.enviar(ARRENDADOR, PDF, PNG, IP))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(archivos).eliminar(CLAVE_IDENTIDAD);
        verify(archivos).eliminar(CLAVE_PROPIEDAD);
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.NO_INICIADA);
    }

    @Test
    @DisplayName("HU-05 R14: si falla el segundo guardado se elimina el primer archivo")
    void falloEnSegundoGuardadoEliminaElPrimero() {
        dado(cuenta(true), perfil(EstadoIdentidad.NO_INICIADA, null));
        when(validador.validar(PDF)).thenReturn(new ResultadoValidacion.Aceptado(PDF_VALIDADO));
        when(validador.validar(PNG)).thenReturn(new ResultadoValidacion.Aceptado(PNG_VALIDADO));
        when(archivos.guardar("identidad", PDF_VALIDADO.contenido())).thenReturn(CLAVE_IDENTIDAD);
        when(archivos.guardar("identidad", PNG_VALIDADO.contenido())).thenThrow(new IllegalStateException("disco"));

        assertThatThrownBy(() -> servicio.enviar(ARRENDADOR, PDF, PNG, IP)).isInstanceOf(IllegalStateException.class);
        verify(archivos).eliminar(CLAVE_IDENTIDAD);
        verifyNoInteractions(documentos);
    }

    @Test
    @DisplayName("HU-05 R16: si otro envío ganó la carrera, la transacción rechaza por estado y borra los nuevos")
    void carreraPorEstado() {
        dado(cuenta(true), perfil(EstadoIdentidad.NO_INICIADA, null));
        validacionAceptada();
        when(perfiles.findParaActualizar(ID)).thenReturn(Optional.of(perfil(EstadoIdentidad.EN_REVISION, null)));

        assertThatThrownBy(() -> servicio.enviar(ARRENDADOR, PDF, PNG, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya enviaste tus documentos o tu identidad ya está verificada.");
        verify(archivos).eliminar(CLAVE_IDENTIDAD);
        verify(archivos).eliminar(CLAVE_PROPIEDAD);
        assertThat(auditorias(1).getFirst().detalle()).isEqualTo("estado=EN_REVISION");
        verify(documentos, never()).save(any());
    }

    @Test
    @DisplayName("HU-05 R1, R2, R3, R4, R5: un envío aceptado desde RECHAZADA registra dos documentos y reemplaza")
    void envioAceptado() {
        var perfil = perfil(EstadoIdentidad.RECHAZADA, "Documento borroso.");
        var anterior = documento(50L, TipoDocumentoIdentidad.IDENTIDAD, CLAVE_ANTERIOR, "application/pdf");
        dado(cuenta(true), perfil);
        validacionAceptada();
        guardadoConId();
        when(perfiles.findParaActualizar(ID)).thenReturn(Optional.of(perfil));
        when(documentos.findByArrendadorIdOrderByTipoAsc(ID)).thenReturn(List.of(anterior), List.of());

        var respuesta = servicio.enviar(ARRENDADOR, PDF, PNG, IP);

        assertThat(respuesta.estadoIdentidad()).isEqualTo("EN_REVISION");
        assertThat(respuesta.motivoRechazo()).isNull();
        assertThat(respuesta.puedeEnviar()).isFalse();
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getMotivoRechazo()).isNull();
        var guardados = ArgumentCaptor.forClass(DocumentoIdentidad.class);
        verify(documentos, times(2)).save(guardados.capture());
        assertThat(guardados.getAllValues()).extracting(DocumentoIdentidad::getArrendadorId,
                        DocumentoIdentidad::getTipo, DocumentoIdentidad::getRutaArchivo,
                        DocumentoIdentidad::getTipoMime, DocumentoIdentidad::getTamano, DocumentoIdentidad::getSubidoEn)
                .containsExactly(
                        tuple(ID, TipoDocumentoIdentidad.IDENTIDAD, CLAVE_IDENTIDAD,
                                "application/pdf", (int) PDF.tamano(), AHORA),
                        tuple(ID, TipoDocumentoIdentidad.PROPIEDAD, CLAVE_PROPIEDAD,
                                "image/png", (int) PNG.tamano(), AHORA));
        var orden = inOrder(documentos, archivos);
        orden.verify(documentos).deleteAll(List.of(anterior));
        orden.verify(documentos, times(2)).save(any());
        orden.verify(archivos).eliminar(CLAVE_ANTERIOR);
        verify(archivos, never()).eliminar(CLAVE_IDENTIDAD);
    }

    @Test
    @DisplayName("HU-05 R40, R42: un envío aceptado audita una carga por documento con tipo, mime y tamaño")
    void envioAceptadoAudita() {
        var perfil = perfil(EstadoIdentidad.NO_INICIADA, null);
        dado(cuenta(true), perfil);
        validacionAceptada();
        guardadoConId();
        when(perfiles.findParaActualizar(ID)).thenReturn(Optional.of(perfil));
        when(documentos.findByArrendadorIdOrderByTipoAsc(ID)).thenReturn(List.of());

        servicio.enviar(ARRENDADOR, PDF, PNG, IP);

        var eventos = auditorias(2);
        assertThat(eventos).containsExactly(
                new EventoAuditoria(ID, "CARGA_DOCUMENTO_IDENTIDAD", "documento_identidad", 100L, IP,
                        "tipo=IDENTIDAD;mime=application/pdf;tamano=" + PDF.tamano()),
                new EventoAuditoria(ID, "CARGA_DOCUMENTO_IDENTIDAD", "documento_identidad", 101L, IP,
                        "tipo=PROPIEDAD;mime=image/png;tamano=" + PNG.tamano()));
        for (var evento : eventos) {
            assertThat(evento.detalle()).doesNotContain("identidad/").doesNotContain("20100070970");
        }
    }

    @Test
    @DisplayName("HU-05 R21, R22, R24, R25, R38: consultar en RECHAZADA devuelve el motivo y los documentos firmados")
    void consultarRechazada() {
        dado(cuenta(true), perfil(EstadoIdentidad.RECHAZADA, "Documento borroso."));
        var doc = documento(9L, TipoDocumentoIdentidad.IDENTIDAD, CLAVE_IDENTIDAD, "application/pdf");
        when(documentos.findByArrendadorIdOrderByTipoAsc(ID)).thenReturn(List.of(doc));
        when(archivos.urlFirmada(CLAVE_IDENTIDAD, "application/pdf"))
                .thenReturn(new UrlFirmada("/api/archivos?firmada", AHORA.plusSeconds(300)));

        var respuesta = servicio.consultar(ARRENDADOR);

        assertThat(respuesta.estadoIdentidad()).isEqualTo("RECHAZADA");
        assertThat(respuesta.motivoRechazo()).isEqualTo("Documento borroso.");
        assertThat(respuesta.puedeEnviar()).isTrue();
        assertThat(respuesta.documentos()).hasSize(1);
        var dto = respuesta.documentos().getFirst();
        assertThat(dto.id()).isEqualTo(9L);
        assertThat(dto.tipo()).isEqualTo("IDENTIDAD");
        assertThat(dto.tipoMime()).isEqualTo("application/pdf");
        assertThat(dto.tamano()).isEqualTo(1234);
        assertThat(dto.url()).isEqualTo("/api/archivos?firmada");
        assertThat(dto.urlExpiraEn()).isEqualTo(AHORA.plusSeconds(300));
    }

    @Test
    @DisplayName("HU-05 R23, R24: fuera de RECHAZADA el motivo es nulo y puedeEnviar depende del estado y del correo")
    void consultarOtrosEstados() {
        var casos = List.of(
                new Object[] {EstadoIdentidad.NO_INICIADA, true, true},
                new Object[] {EstadoIdentidad.EN_REVISION, true, false},
                new Object[] {EstadoIdentidad.VERIFICADA, true, false},
                new Object[] {EstadoIdentidad.NO_INICIADA, false, false});
        for (var caso : casos) {
            var perfil = perfil((EstadoIdentidad) caso[0], "motivo viejo");
            when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta((boolean) caso[1])));
            when(perfiles.findById(eq(ID))).thenReturn(Optional.of(perfil));
            when(documentos.findByArrendadorIdOrderByTipoAsc(ID)).thenReturn(List.of());

            var respuesta = servicio.consultar(ARRENDADOR);

            assertThat(respuesta.motivoRechazo()).isNull();
            assertThat(respuesta.puedeEnviar()).isEqualTo(caso[2]);
            assertThat(respuesta.documentos()).isEmpty();
        }
    }
}

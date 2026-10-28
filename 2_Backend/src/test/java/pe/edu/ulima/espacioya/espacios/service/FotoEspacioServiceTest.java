package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPublicos;
import pe.edu.ulima.espacioya.comun.almacenamiento.MotivoRechazoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.TipoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo.ResultadoValidacion;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FotoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.dto.EspacioDTO;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;

@ExtendWith(MockitoExtension.class)
class FotoEspacioServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final long ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final String IP = "10.7.0.1";
    private static final String UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String CLAVE = "espacios/" + UUID;
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");
    private static final ArchivoRecibido RECIBIDA = recibido("jpeg con exif");
    private static final ArchivoValidado LIMPIA =
            new ArchivoValidado("jpeg limpio".getBytes(StandardCharsets.UTF_8), TipoArchivo.JPEG, RECIBIDA.tamano());
    private static final EspacioDTO RESPUESTA = new EspacioDTO(ESPACIO_ID, "Cancha", "CANCHA", "Av. Uno 123",
            "MIRAFLORES", "Miraflores", 20, new BigDecimal("80.00"), "Descripción larga.", "BORRADOR", AHORA, AHORA,
            List.of());

    @Mock
    private EspacioService espacioService;
    @Mock
    private EspacioRepository espacios;
    @Mock
    private FotoEspacioRepository fotos;
    @Mock
    private ValidadorArchivo validador;
    @Mock
    private ArchivosPublicos archivos;
    @Mock
    private AuditoriaService auditoria;

    private FotoEspacioService servicio;

    @BeforeEach
    void preparar() {
        var transacciones = new TransactionTemplate(mock(PlatformTransactionManager.class));
        servicio = new FotoEspacioService(espacioService, espacios, fotos, validador, archivos, auditoria,
                transacciones, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ArchivoRecibido recibido(String texto) {
        var bytes = texto.getBytes(StandardCharsets.UTF_8);
        return new ArchivoRecibido(bytes, bytes.length);
    }

    private static Espacio espacio(EstadoEspacio estado) {
        var datos = new DatosEspacio("Cancha", TipoEspacio.CANCHA, "Av. Uno 123", "MIRAFLORES", 20,
                new BigDecimal("80"), "Descripción larga.");
        var espacio = Espacio.crear(ID, datos, AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(espacio, "id", ESPACIO_ID);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    private static FotoEspacio foto(long id, String clave, int orden) {
        var foto = FotoEspacio.registrar(ESPACIO_ID, clave, "image/jpeg", orden);
        ReflectionTestUtils.setField(foto, "id", id);
        return foto;
    }

    private void habilitadoConEspacio(Espacio espacio) {
        when(espacioService.arrendadorHabilitado(ARRENDADOR)).thenReturn(ID);
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio));
    }

    private List<EventoAuditoria> auditados() {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, atLeastOnce()).registrar(eventos.capture());
        return eventos.getAllValues();
    }

    private static EventoAuditoria rechazo(String detalle) {
        return new EventoAuditoria(ID, FotoEspacioService.ACCION_FOTO_RECHAZADA, "espacio", ESPACIO_ID, IP, detalle);
    }

    // ── Rechazos antes de mirar la foto (R44) ────────────────────────────

    @Test
    @DisplayName("HU-07 R44, R41, R42: sin rol o correo no se valida ni se guarda la foto")
    void rolOCorreoNoTocaLaFoto() {
        when(espacioService.arrendadorHabilitado(ARRENDADOR)).thenThrow(new AccesoDenegadoException());

        assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(validador, archivos, espacios, fotos, auditoria);
    }

    @Test
    @DisplayName("HU-07 R44, R40: un espacio ajeno responde no encontrado sin mirar la foto")
    void ajenoNoTocaLaFoto() {
        when(espacioService.arrendadorHabilitado(ARRENDADOR)).thenReturn(ID);
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(validador, archivos, fotos, auditoria);
    }

    @Test
    @DisplayName("HU-07 R44, R23: un espacio en BAJA u OCULTO responde estado no editable sin mirar la foto")
    void noEditableNoTocaLaFoto() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BAJA, EstadoEspacio.OCULTO}) {
            when(espacioService.arrendadorHabilitado(ARRENDADOR)).thenReturn(ID);
            when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio(estado)));

            assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(EspacioService.MENSAJE_ESTADO_NO_EDITABLE);
            assertThatThrownBy(() -> servicio.eliminar(ARRENDADOR, ESPACIO_ID, 3L, IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(EspacioService.MENSAJE_ESTADO_NO_EDITABLE);
        }
        verifyNoInteractions(validador, archivos, fotos, auditoria);
    }

    @Test
    @DisplayName("HU-07 R27, R44, R50: con 5 fotos responde máximo de fotos, audita fotos=MAXIMO y no mira la foto")
    void cupoAgotado() {
        habilitadoConEspacio(espacio(EstadoEspacio.BORRADOR));
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(5L);

        assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya tienes 5 fotos. Quita una para subir otra.");
        verifyNoInteractions(validador, archivos);
        assertThat(auditados()).containsExactly(rechazo("fotos=MAXIMO"));
    }

    // ── Validación de la foto (R28–R31, R50) ─────────────────────────────

    @Test
    @DisplayName("HU-07 R30, R50: un PDF aceptado por el validador se rechaza como tipo de foto y se audita")
    void pdfRechazado() {
        habilitadoConEspacio(espacio(EstadoEspacio.BORRADOR));
        when(validador.validar(RECIBIDA)).thenReturn(new ResultadoValidacion.Aceptado(
                new ArchivoValidado(RECIBIDA.contenido(), TipoArchivo.PDF, RECIBIDA.tamano())));

        assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("foto");
                    assertThat(e.getMessage()).isEqualTo("La foto debe ser JPG o PNG.");
                });
        verifyNoInteractions(archivos);
        assertThat(auditados()).containsExactly(rechazo("foto=TIPO_NO_PERMITIDO"));
    }

    @Test
    @DisplayName("HU-07 R28, R29, R30, R31: cada motivo de rechazo da su mensaje en el campo foto")
    void mensajesPorMotivo() {
        var esperados = new Object[][] {
                {MotivoRechazoArchivo.VACIO, "Elige una foto."},
                {MotivoRechazoArchivo.TIPO_NO_PERMITIDO, "La foto debe ser JPG o PNG."},
                {MotivoRechazoArchivo.TAMANO_EXCEDIDO, ErrorDTO.MENSAJE_ARCHIVO_DEMASIADO_GRANDE},
                {MotivoRechazoArchivo.IMAGEN_ILEGIBLE, MotivoRechazoArchivo.IMAGEN_ILEGIBLE.mensaje()}};
        for (var caso : esperados) {
            habilitadoConEspacio(espacio(EstadoEspacio.BORRADOR));
            when(validador.validar(RECIBIDA))
                    .thenReturn(new ResultadoValidacion.Rechazado((MotivoRechazoArchivo) caso[0]));

            assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                        assertThat(e.getCampo()).isEqualTo("foto");
                        assertThat(e.getMessage()).isEqualTo(caso[1]);
                    });
        }
        verifyNoInteractions(archivos);
        assertThat(auditados()).extracting(EventoAuditoria::detalle).containsExactly("foto=VACIO",
                "foto=TIPO_NO_PERMITIDO", "foto=TAMANO_EXCEDIDO", "foto=IMAGEN_ILEGIBLE");
    }

    // ── Foto aceptada (R25, R26, R37, R49) ───────────────────────────────

    @Test
    @DisplayName("HU-07 R25, R26, R37, R49: guarda los bytes re-codificados en espacios y audita el detalle exacto")
    void fotoAceptada() {
        var espacio = espacio(EstadoEspacio.BORRADOR);
        habilitadoConEspacio(espacio);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(1L);
        when(validador.validar(RECIBIDA)).thenReturn(new ResultadoValidacion.Aceptado(LIMPIA));
        when(archivos.guardar("espacios", LIMPIA.contenido())).thenReturn(CLAVE);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio));
        when(fotos.save(any(FotoEspacio.class))).thenAnswer(invocacion -> {
            FotoEspacio guardada = invocacion.getArgument(0);
            ReflectionTestUtils.setField(guardada, "id", 9L);
            return guardada;
        });
        when(espacioService.consultar(ARRENDADOR, ESPACIO_ID)).thenReturn(RESPUESTA);

        assertThat(servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP)).isEqualTo(RESPUESTA);

        var guardada = ArgumentCaptor.forClass(FotoEspacio.class);
        verify(fotos).save(guardada.capture());
        assertThat(guardada.getValue().getOrden()).isEqualTo(2);
        assertThat(guardada.getValue().getRutaArchivo()).isEqualTo(CLAVE);
        assertThat(guardada.getValue().getTipoMime()).isEqualTo("image/jpeg");
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        assertThat(auditados()).containsExactly(new EventoAuditoria(ID, FotoEspacioService.ACCION_FOTO_AGREGADA,
                "espacio", ESPACIO_ID, IP, "foto=9;orden=2;mime=image/jpeg;tamano=" + RECIBIDA.tamano()));
        verify(archivos, never()).eliminar(anyString());
    }

    @Test
    @DisplayName("HU-07 R32: si la transacción falla se borra el archivo recién guardado")
    void falloEnTransaccionBorraArchivo() {
        var espacio = espacio(EstadoEspacio.BORRADOR);
        habilitadoConEspacio(espacio);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(0L);
        when(validador.validar(RECIBIDA)).thenReturn(new ResultadoValidacion.Aceptado(LIMPIA));
        when(archivos.guardar("espacios", LIMPIA.contenido())).thenReturn(CLAVE);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio));
        when(fotos.save(any(FotoEspacio.class))).thenThrow(new DataIntegrityViolationException("orden"));

        assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                .isInstanceOf(DataIntegrityViolationException.class);
        var orden = inOrder(archivos);
        orden.verify(archivos).guardar("espacios", LIMPIA.contenido());
        orden.verify(archivos).eliminar(CLAVE);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-07 R27, R32, R50: si otra subida llenó el cupo, se borra el archivo y se audita fotos=MAXIMO")
    void cupoAgotadoDentroDeLaTransaccion() {
        var espacio = espacio(EstadoEspacio.BORRADOR);
        habilitadoConEspacio(espacio);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(4L, 5L);
        when(validador.validar(RECIBIDA)).thenReturn(new ResultadoValidacion.Aceptado(LIMPIA));
        when(archivos.guardar("espacios", LIMPIA.contenido())).thenReturn(CLAVE);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio));

        assertThatThrownBy(() -> servicio.agregar(ARRENDADOR, ESPACIO_ID, RECIBIDA, IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(FotoEspacioService.MENSAJE_MAXIMO_FOTOS);
        verify(archivos).eliminar(CLAVE);
        verify(fotos, never()).save(any());
        assertThat(auditados()).containsExactly(rechazo("fotos=MAXIMO"));
    }

    // ── Eliminar (R34–R36, R49) ──────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R34, R35, R49: quitar la foto 2 de 3 renumera la 3, audita y borra el archivo tras el commit")
    void eliminarRenumera() {
        var espacio = espacio(EstadoEspacio.PUBLICADO);
        habilitadoConEspacio(espacio);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio));
        var primera = foto(1, "espacios/1f8fad5b-d9cb-469f-a165-70867728950e", 1);
        var segunda = foto(2, CLAVE, 2);
        var tercera = foto(3, "espacios/3f8fad5b-d9cb-469f-a165-70867728950e", 3);
        when(fotos.findByIdAndEspacioId(2L, ESPACIO_ID)).thenReturn(Optional.of(segunda));
        when(fotos.findByEspacioIdOrderByOrdenAsc(ESPACIO_ID)).thenReturn(new ArrayList<>(List.of(primera, tercera)));
        when(espacioService.consultar(ARRENDADOR, ESPACIO_ID)).thenReturn(RESPUESTA);

        assertThat(servicio.eliminar(ARRENDADOR, ESPACIO_ID, 2L, IP)).isEqualTo(RESPUESTA);

        verify(fotos).delete(segunda);
        assertThat(primera.getOrden()).isEqualTo(1);
        assertThat(tercera.getOrden()).isEqualTo(2);
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.PUBLICADO);
        verify(archivos).eliminar(CLAVE);
        assertThat(auditados()).containsExactly(new EventoAuditoria(ID, FotoEspacioService.ACCION_FOTO_ELIMINADA,
                "espacio", ESPACIO_ID, IP, "foto=2;orden=2"));
    }

    @Test
    @DisplayName("HU-07 R36: una foto que no es del espacio responde no encontrado sin borrar nada")
    void eliminarFotoDeOtroEspacio() {
        var espacio = espacio(EstadoEspacio.BORRADOR);
        habilitadoConEspacio(espacio);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio));
        when(fotos.findByIdAndEspacioId(99L, ESPACIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(ARRENDADOR, ESPACIO_ID, 99L, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(fotos, never()).delete(any());
        verifyNoInteractions(archivos, auditoria);
    }

    // ── Ver (R39, R54, R55) ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R55: un uuid mal formado responde no encontrado sin consultar repositorios")
    void uuidMalFormado() {
        for (var uuid : new String[] {null, "", "no-es-uuid", UUID.toUpperCase(), "../" + UUID, UUID + "/..",
                "0f8fad5b..d9cb-469f-a165-70867728950e"}) {
            assertThatThrownBy(() -> servicio.ver(null, uuid)).isInstanceOf(RecursoNoEncontradoException.class);
        }
        verifyNoInteractions(fotos, espacios, archivos);
    }

    private void fotoDe(EstadoEspacio estado) {
        when(fotos.findByRutaArchivo(CLAVE)).thenReturn(Optional.of(foto(5, CLAVE, 1)));
        when(espacios.findById(ESPACIO_ID)).thenReturn(Optional.of(espacio(estado)));
    }

    @Test
    @DisplayName("HU-07 R39: la foto de un espacio PUBLICADO se sirve sin sesión y como pública")
    void verPublicada() {
        fotoDe(EstadoEspacio.PUBLICADO);
        when(archivos.leer(CLAVE)).thenReturn(Optional.of(LIMPIA.contenido()));

        var servida = servicio.ver(null, UUID);

        assertThat(servida.publica()).isTrue();
        assertThat(servida.tipoMime()).isEqualTo("image/jpeg");
        assertThat(servida.contenido()).isEqualTo(LIMPIA.contenido());
    }

    @Test
    @DisplayName("HU-07 R54: la foto de un BORRADOR se sirve a su dueño y no como pública")
    void verBorradorDelDueno() {
        fotoDe(EstadoEspacio.BORRADOR);
        when(archivos.leer(CLAVE)).thenReturn(Optional.of(LIMPIA.contenido()));

        var servida = servicio.ver(ARRENDADOR, UUID);

        assertThat(servida.publica()).isFalse();
        assertThat(servida.contenido()).isEqualTo(LIMPIA.contenido());
    }

    @Test
    @DisplayName("HU-07 R55: la foto de un BORRADOR sin sesión o de otra cuenta responde 404 sin leer el archivo")
    void verBorradorAjeno() {
        for (var usuario : new UsuarioAutenticado[] {null, new UsuarioAutenticado(99L, 1L, "ARRENDADOR")}) {
            fotoDe(EstadoEspacio.BORRADOR);
            assertThatThrownBy(() -> servicio.ver(usuario, UUID)).isInstanceOf(RecursoNoEncontradoException.class);
        }
        verifyNoInteractions(archivos);
    }

    @Test
    @DisplayName("HU-07 R55: una foto inexistente o sin archivo almacenado responde 404")
    void verSinFotoOSinArchivo() {
        when(fotos.findByRutaArchivo(CLAVE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.ver(null, UUID)).isInstanceOf(RecursoNoEncontradoException.class);

        fotoDe(EstadoEspacio.PUBLICADO);
        when(archivos.leer(CLAVE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.ver(null, UUID)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}

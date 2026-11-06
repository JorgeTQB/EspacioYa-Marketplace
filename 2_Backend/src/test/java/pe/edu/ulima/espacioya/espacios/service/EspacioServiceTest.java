package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.Caracteristica;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FotoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Zona;
import pe.edu.ulima.espacioya.espacios.dto.FichaEspacioDTO;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.ZonaRepository;

@ExtendWith(MockitoExtension.class)
class EspacioServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final long ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final String IP = "10.7.0.1";
    private static final String UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");
    private static final FichaEspacioDTO FICHA = new FichaEspacioDTO("Cancha Los Olivos", "CANCHA",
            "Av. Universitaria 1234", "MIRAFLORES", 20, new BigDecimal("80"), "Cancha de grass sintético techada.");

    @Mock
    private EspacioRepository espacios;
    @Mock
    private FotoEspacioRepository fotos;
    @Mock
    private ZonaRepository zonas;
    @Mock
    private CuentaService cuentas;
    @Mock
    private AuditoriaService auditoria;

    private EspacioService servicio;

    @BeforeEach
    void preparar() {
        servicio = new EspacioService(espacios, fotos, zonas, cuentas, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(zonas.findById("MIRAFLORES")).thenReturn(Optional.of(zona("MIRAFLORES", "Miraflores")));
    }

    private static Zona zona(String codigo, String nombre) {
        var zona = instanciar(Zona.class);
        ReflectionTestUtils.setField(zona, "codigo", codigo);
        ReflectionTestUtils.setField(zona, "nombre", nombre);
        return zona;
    }

    private static <T> T instanciar(Class<T> clase) {
        try {
            var constructor = clase.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Espacio espacio(EstadoEspacio estado) {
        var espacio = Espacio.crear(ID, FICHA.aDatos(), AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(espacio, "id", ESPACIO_ID);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    private static FotoEspacio foto(long id, String uuid, int orden) {
        var foto = FotoEspacio.registrar(ESPACIO_ID, "espacios/" + uuid, "image/jpeg", orden);
        ReflectionTestUtils.setField(foto, "id", id);
        return foto;
    }

    private void habilitado(boolean correo, boolean identidad) {
        when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(correo, identidad));
    }

    private void propio(Espacio espacio) {
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio));
    }

    private EventoAuditoria auditado() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    // ── Consulta y catálogo ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R10, R11: consultar devuelve las fotos por orden con url /api/fotos/<UUID>, sin la clave")
    void consultarArmaUrlEstable() {
        habilitado(true, false);
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.of(espacio(EstadoEspacio.BORRADOR)));
        var otroUuid = "1f8fad5b-d9cb-469f-a165-70867728950e";
        when(fotos.findByEspacioIdOrderByOrdenAsc(ESPACIO_ID)).thenReturn(List.of(foto(1, UUID, 1),
                foto(2, otroUuid, 2)));

        var dto = servicio.consultar(ARRENDADOR, ESPACIO_ID);

        assertThat(dto.fotos()).extracting(f -> f.url())
                .containsExactly("/api/fotos/" + UUID, "/api/fotos/" + otroUuid);
        assertThat(dto.fotos()).extracting(f -> f.orden()).containsExactly(1, 2);
        assertThat(dto.zonaNombre()).isEqualTo("Miraflores");
        assertThat(dto.toString()).doesNotContain("espacios/");
    }

    @Test
    @DisplayName("HU-07 R40: consultar un espacio ajeno o inexistente responde no encontrado")
    void consultarAjeno() {
        habilitado(true, false);
        when(espacios.findByIdAndArrendadorId(ESPACIO_ID, ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ARRENDADOR, ESPACIO_ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("HU-07 R42: consultar y listar no exigen el correo verificado")
    void consultaSinCorreoVerificado() {
        habilitado(false, false);
        when(espacios.findByArrendadorIdOrderByCreadoEnDesc(ID)).thenReturn(List.of(espacio(EstadoEspacio.BORRADOR)));
        when(zonas.findAll()).thenReturn(List.of(zona("MIRAFLORES", "Miraflores")));

        var lista = servicio.listar(ARRENDADOR);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).zonaNombre()).isEqualTo("Miraflores");
        assertThat(lista.get(0).precioHora()).hasToString("80.00");
    }

    @Test
    @DisplayName("HU-07 R8: listarZonas ordena por nombre en español")
    void zonasOrdenadas() {
        when(zonas.findAll()).thenReturn(List.of(zona("LURIN", "Lurín"), zona("ATE", "Ate"),
                zona("LURIGANCHO", "Lurigancho-Chosica"), zona("JESUS_MARIA", "Jesús María")));

        assertThat(servicio.listarZonas()).extracting(z -> z.nombre())
                .containsExactly("Ate", "Jesús María", "Lurigancho-Chosica", "Lurín");
    }

    // ── Creación ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R1, R2, R45: crear guarda un BORRADOR del arrendador y audita el detalle exacto")
    void crearAudita() {
        habilitado(true, false);
        when(zonas.existsById("MIRAFLORES")).thenReturn(true);
        when(espacios.save(any(Espacio.class))).thenAnswer(invocacion -> {
            Espacio guardado = invocacion.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", ESPACIO_ID);
            return guardado;
        });

        var dto = servicio.crear(ARRENDADOR, FICHA, IP);

        assertThat(dto.estado()).isEqualTo("BORRADOR");
        assertThat(dto.fotos()).isEmpty();
        assertThat(dto.creadoEn()).isEqualTo(AHORA);
        var guardado = ArgumentCaptor.forClass(Espacio.class);
        verify(espacios).save(guardado.capture());
        assertThat(guardado.getValue().getArrendadorId()).isEqualTo(ID);
        assertThat(auditado()).isEqualTo(new EventoAuditoria(ID, EspacioService.ACCION_CREACION, "espacio",
                ESPACIO_ID, IP, "tipo=CANCHA;zona=MIRAFLORES;capacidad=20;precioHora=80.00"));
    }

    @Test
    @DisplayName("HU-07 R5, R6: una zona que no está en el catálogo se rechaza en el campo zona sin guardar")
    void zonaInexistente() {
        habilitado(true, false);
        when(zonas.existsById("MIRAFLORES")).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(ARRENDADOR, FICHA, IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("zona");
                    assertThat(e.getMessage()).isEqualTo(FichaEspacioDTO.M_ZONA);
                });
        verify(espacios, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    // ── Orden de las comprobaciones (R53) ─────────────────────────────────

    @Test
    @DisplayName("HU-07 R53, R41: el rol se comprueba antes que todo lo demás")
    void rolPrimero() {
        when(cuentas.habilitacionArrendador(any())).thenThrow(new AccesoDenegadoException());
        var usuario = new UsuarioAutenticado(ID, 31L, "USUARIO");

        assertThatThrownBy(() -> servicio.editar(usuario, ESPACIO_ID, FICHA, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.publicar(usuario, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(espacios, fotos, zonas, auditoria);
    }

    @Test
    @DisplayName("HU-07 R53, R42: el correo verificado se comprueba antes que la propiedad")
    void correoAntesQuePropiedad() {
        habilitado(false, true);

        assertThatThrownBy(() -> servicio.crear(ARRENDADOR, FICHA, IP)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.editar(ARRENDADOR, ESPACIO_ID, FICHA, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.publicar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(espacios, fotos, zonas, auditoria);
    }

    @Test
    @DisplayName("HU-07 R53, R40: la propiedad se comprueba antes que el estado")
    void propiedadAntesQueEstado() {
        habilitado(true, true);
        when(espacios.findParaActualizar(ESPACIO_ID, ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.editar(ARRENDADOR, ESPACIO_ID, FICHA, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.publicar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(zonas, fotos, auditoria);
    }

    @Test
    @DisplayName("HU-07 R53, R23: el estado no editable se informa antes que una zona inexistente")
    void estadoAntesQueZona() {
        habilitado(true, true);
        propio(espacio(EstadoEspacio.BAJA));

        assertThatThrownBy(() -> servicio.editar(ARRENDADOR, ESPACIO_ID, FICHA, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(EspacioService.MENSAJE_ESTADO_NO_EDITABLE);
        verify(zonas, never()).existsById(any());
        verifyNoInteractions(auditoria);
    }

    // ── Publicación (R14–R17) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-07 R16, R14: un PUBLICADO sin identidad verificada responde solo borrador")
    void soloBorradorAntesQueIdentidad() {
        habilitado(true, false);
        propio(espacio(EstadoEspacio.PUBLICADO));

        assertThatThrownBy(() -> servicio.publicar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(EspacioService.MENSAJE_SOLO_BORRADOR);
        verifyNoInteractions(fotos, auditoria);
    }

    @Test
    @DisplayName("HU-07 R16, R15: un BORRADOR sin identidad y sin fotos responde identidad no verificada")
    void identidadAntesQueFotos() {
        habilitado(true, false);
        var espacio = espacio(EstadoEspacio.BORRADOR);
        propio(espacio);

        assertThatThrownBy(() -> servicio.publicar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(EspacioService.MENSAJE_IDENTIDAD_NO_VERIFICADA)
                .hasMessage("Para publicar tus espacios, primero debes verificar tu identidad.");
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BORRADOR);
        verifyNoInteractions(fotos, auditoria);
    }

    @Test
    @DisplayName("HU-07 R17, R18: con identidad verificada y sin fotos responde sin fotos y no cambia el estado")
    void sinFotos() {
        habilitado(true, true);
        var espacio = espacio(EstadoEspacio.BORRADOR);
        propio(espacio);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(0L);

        assertThatThrownBy(() -> servicio.publicar(ARRENDADOR, ESPACIO_ID, IP))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(EspacioService.MENSAJE_SIN_FOTOS);
        assertThat(espacio.getEstado()).isEqualTo(EstadoEspacio.BORRADOR);
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA.minusSeconds(3600));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-07 R12, R13, R48: con identidad y una foto publica y audita BORRADOR->PUBLICADO")
    void publica() {
        habilitado(true, true);
        var espacio = espacio(EstadoEspacio.BORRADOR);
        propio(espacio);
        when(fotos.countByEspacioId(ESPACIO_ID)).thenReturn(1L);

        var dto = servicio.publicar(ARRENDADOR, ESPACIO_ID, IP);

        assertThat(dto.estado()).isEqualTo("PUBLICADO");
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA);
        assertThat(auditado()).isEqualTo(new EventoAuditoria(ID, EspacioService.ACCION_PUBLICACION, "espacio",
                ESPACIO_ID, IP, "estado=BORRADOR->PUBLICADO"));
    }

    // ── Edición (R20–R23, R46, R47) ──────────────────────────────────────

    @Test
    @DisplayName("HU-07 R47: editar sin cambios no audita ni toca actualizadoEn")
    void editarSinCambiosNoAudita() {
        habilitado(true, false);
        var espacio = espacio(EstadoEspacio.PUBLICADO);
        propio(espacio);
        when(zonas.existsById("MIRAFLORES")).thenReturn(true);

        var dto = servicio.editar(ARRENDADOR, ESPACIO_ID, FICHA, IP);

        assertThat(dto.estado()).isEqualTo("PUBLICADO");
        assertThat(espacio.getActualizadoEn()).isEqualTo(AHORA.minusSeconds(3600));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-07 R20, R21, R46: editar con cambios devuelve los nuevos valores y audita cada cambio")
    void editarConCambiosAudita() {
        habilitado(true, false);
        var espacio = espacio(EstadoEspacio.PUBLICADO);
        propio(espacio);
        when(zonas.existsById("MIRAFLORES")).thenReturn(true);
        var ficha = new FichaEspacioDTO(FICHA.nombre(), FICHA.tipo(), FICHA.direccion(), FICHA.zona(),
                FICHA.capacidad(), new BigDecimal("90.00"), "Otra descripción completa.");

        var dto = servicio.editar(ARRENDADOR, ESPACIO_ID, ficha, IP);

        assertThat(dto.precioHora()).hasToString("90.00");
        assertThat(dto.estado()).isEqualTo("PUBLICADO");
        assertThat(dto.actualizadoEn()).isEqualTo(AHORA);
        assertThat(auditado()).isEqualTo(new EventoAuditoria(ID, EspacioService.ACCION_EDICION, "espacio",
                ESPACIO_ID, IP, "precioHora=80.00->90.00;descripcion"));
    }

    @Test
    @DisplayName("HU-07 R41: arrendadorHabilitado devuelve el id de la sesión si el rol y el correo son válidos")
    void arrendadorHabilitado() {
        habilitado(true, false);

        assertThat(servicio.arrendadorHabilitado(ARRENDADOR)).isEqualTo(ID);
        verify(espacios, never()).findById(anyLong());
    }

    // ── Características (HU-23) ──────────────────────────────────────────

    private static FichaEspacioDTO fichaCon(List<String> caracteristicas) {
        return new FichaEspacioDTO(FICHA.nombre(), FICHA.tipo(), FICHA.direccion(), FICHA.zona(), FICHA.capacidad(),
                FICHA.precioHora(), FICHA.descripcion(), caracteristicas);
    }

    private static Espacio espacioCon(EstadoEspacio estado, List<String> caracteristicas) {
        var espacio = Espacio.crear(ID, fichaCon(caracteristicas).aDatos(), AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(espacio, "id", ESPACIO_ID);
        ReflectionTestUtils.setField(espacio, "estado", estado);
        return espacio;
    }

    @Test
    @DisplayName("HU-23 R6: el EspacioDTO trae las características en el orden de la lista fija")
    void dtoConCaracteristicasEnOrdenFijo() {
        var espacio = espacioCon(EstadoEspacio.BORRADOR, List.of("ESTACIONAMIENTO", "VESTUARIOS", "TECHADO"));

        assertThat(servicio.aDto(espacio).caracteristicas())
                .containsExactly("TECHADO", "VESTUARIOS", "ESTACIONAMIENTO");
        assertThat(servicio.aDto(espacio(EstadoEspacio.BORRADOR)).caracteristicas()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("HU-23 R9: editar un espacio en BAJA u OCULTO responde no editable sin cambiar sus características")
    void editarNoEditableNoCambiaCaracteristicas() {
        for (var estado : new EstadoEspacio[] {EstadoEspacio.BAJA, EstadoEspacio.OCULTO}) {
            var espacio = espacioCon(estado, List.of("TECHADO"));
            when(cuentas.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(true, true));
            propio(espacio);

            assertThatThrownBy(() -> servicio.editar(ARRENDADOR, ESPACIO_ID,
                    fichaCon(List.of("VESTUARIOS", "ILUMINACION")), IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(EspacioService.MENSAJE_ESTADO_NO_EDITABLE);
            assertThat(espacio.getCaracteristicas()).containsExactly(Caracteristica.TECHADO);
        }
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-23 R2, R7: editar las características audita EDICION_ESPACIO con caracteristicas=[…]->[…]")
    void editarCaracteristicasAudita() {
        habilitado(true, true);
        var espacio = espacioCon(EstadoEspacio.PUBLICADO, List.of());
        propio(espacio);
        when(zonas.existsById("MIRAFLORES")).thenReturn(true);

        var dto = servicio.editar(ARRENDADOR, ESPACIO_ID, fichaCon(List.of("VESTUARIOS", "TECHADO")), IP);

        assertThat(dto.caracteristicas()).containsExactly("TECHADO", "VESTUARIOS");
        var evento = auditado();
        assertThat(evento.accion()).isEqualTo(EspacioService.ACCION_EDICION);
        assertThat(evento.detalle()).isEqualTo("caracteristicas=[]->[TECHADO, VESTUARIOS]");
    }

    @Test
    @DisplayName("HU-23 R8: editar con las mismas características en otro orden no audita")
    void editarMismasCaracteristicasNoAudita() {
        habilitado(true, true);
        propio(espacioCon(EstadoEspacio.PUBLICADO, List.of("TECHADO", "VESTUARIOS")));
        when(zonas.existsById("MIRAFLORES")).thenReturn(true);

        servicio.editar(ARRENDADOR, ESPACIO_ID, fichaCon(List.of("VESTUARIOS", "TECHADO")), IP);

        verifyNoInteractions(auditoria);
    }
}

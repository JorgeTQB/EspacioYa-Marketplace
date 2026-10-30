package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FotoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Zona;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.ZonaRepository;

@ExtendWith(MockitoExtension.class)
class CatalogoEspaciosServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");

    @Mock
    private EspacioRepository espacios;
    @Mock
    private ZonaRepository zonas;
    @Mock
    private FotoEspacioRepository fotos;
    @Mock
    private CuentaService cuentas;

    private CatalogoEspaciosServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new CatalogoEspaciosServiceImpl(espacios, zonas, fotos, cuentas);
    }

    private static Espacio espacio(long id, String precio) {
        var datos = new DatosEspacio("Cancha " + id, TipoEspacio.CANCHA, "Av. Universitaria 1234", "LOS_OLIVOS",
                20, new BigDecimal(precio), "Cancha de grass sintético techada.");
        var espacio = Espacio.crear(7L, datos, AHORA);
        ReflectionTestUtils.setField(espacio, "id", id);
        ReflectionTestUtils.setField(espacio, "estado", EstadoEspacio.PUBLICADO);
        return espacio;
    }

    private void zonaLosOlivos() {
        var zona = mock(Zona.class);
        when(zona.getNombre()).thenReturn("Los Olivos");
        when(zonas.findById("LOS_OLIVOS")).thenReturn(Optional.of(zona));
    }

    @Test
    @DisplayName("HU-10 R4: sin arrendadores no ACTIVA consulta sin la condición de exclusión")
    void sinExcluidosUsaLaConsultaSimple() {
        when(cuentas.arrendadoresNoActivos()).thenReturn(Set.of());
        when(espacios.buscarPublicados(EstadoEspacio.PUBLICADO, TipoEspacio.CANCHA, "LOS_OLIVOS"))
                .thenReturn(List.of(espacio(2L, "50.00"), espacio(1L, "80.00")));
        zonaLosOlivos();

        var publicados = servicio.publicados("CANCHA", "LOS_OLIVOS");

        assertThat(publicados).containsExactly(
                new EspacioPublicado(2L, "Cancha 2", "CANCHA", "LOS_OLIVOS", "Los Olivos", new BigDecimal("50.00")),
                new EspacioPublicado(1L, "Cancha 1", "CANCHA", "LOS_OLIVOS", "Los Olivos", new BigDecimal("80.00")));
        verify(espacios, never()).buscarPublicadosExcluyendo(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("HU-10 R4: con arrendadores no ACTIVA consulta excluyendo sus espacios")
    void conExcluidosUsaLaConsultaConExclusion() {
        when(cuentas.arrendadoresNoActivos()).thenReturn(Set.of(9L, 10L));
        when(espacios.buscarPublicadosExcluyendo(EstadoEspacio.PUBLICADO, TipoEspacio.CANCHA, "LOS_OLIVOS",
                Set.of(9L, 10L))).thenReturn(List.of(espacio(1L, "80")));
        zonaLosOlivos();

        assertThat(servicio.publicados("CANCHA", "LOS_OLIVOS")).extracting(EspacioPublicado::id).containsExactly(1L);
        verify(espacios, never()).buscarPublicados(any(), any(), anyString());
    }

    @Test
    @DisplayName("HU-10 R2: un tipo no válido en publicados lanza IllegalArgumentException sin consultar")
    void publicadosConTipoInvalido() {
        assertThatThrownBy(() -> servicio.publicados("PISCINA", "LOS_OLIVOS"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(espacios, cuentas);
    }

    @Test
    @DisplayName("HU-10 R23: solo CANCHA, ESTADIO y AUDITORIO exactos son tipos válidos")
    void tiposValidos() {
        assertThat(servicio.esTipoValido("CANCHA")).isTrue();
        assertThat(servicio.esTipoValido("ESTADIO")).isTrue();
        assertThat(servicio.esTipoValido("AUDITORIO")).isTrue();
        assertThat(servicio.esTipoValido(null)).isFalse();
        assertThat(servicio.esTipoValido("")).isFalse();
        assertThat(servicio.esTipoValido(" ")).isFalse();
        assertThat(servicio.esTipoValido("cancha")).isFalse();
        assertThat(servicio.esTipoValido("PISCINA")).isFalse();
    }

    @Test
    @DisplayName("HU-10 R24: null o en blanco no son zonas válidas y no consultan la base")
    void zonasValidas() {
        when(zonas.existsById("LOS_OLIVOS")).thenReturn(true);
        when(zonas.existsById("los_olivos")).thenReturn(false);

        assertThat(servicio.existeZona("LOS_OLIVOS")).isTrue();
        assertThat(servicio.existeZona("los_olivos")).isFalse();
        assertThat(servicio.existeZona(null)).isFalse();
        assertThat(servicio.existeZona("")).isFalse();
        assertThat(servicio.existeZona("  ")).isFalse();
        verify(zonas, never()).existsById(" ");
    }

    @Test
    @DisplayName("HU-10 R15, R16: fotosPrincipales da la URL de la foto con orden 1 y omite los espacios sin ella")
    void fotosPrincipales() {
        when(fotos.findByEspacioIdInAndOrden(List.of(1L, 2L), (short) 1)).thenReturn(List.of(
                FotoEspacio.registrar(1L, "espacios/3f0c1e2a-0000-4000-8000-000000000001", "image/jpeg", 1)));

        assertThat(servicio.fotosPrincipales(List.of(1L, 2L)))
                .isEqualTo(Map.of(1L, "/api/fotos/3f0c1e2a-0000-4000-8000-000000000001"));
        assertThat(servicio.fotosPrincipales(List.of())).isEmpty();
        verify(fotos, never()).findByEspacioIdInAndOrden(List.of(), (short) 1);
    }
}

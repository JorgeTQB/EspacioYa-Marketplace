package pe.edu.ulima.espacioya.administracion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.administracion.domain.MensajeReclamo;
import pe.edu.ulima.espacioya.administracion.domain.MotivoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.administracion.domain.Reclamo;
import pe.edu.ulima.espacioya.administracion.dto.ReclamoExportadoDTO;
import pe.edu.ulima.espacioya.administracion.repository.MensajeReclamoRepository;
import pe.edu.ulima.espacioya.administracion.repository.ReclamoRepository;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaReclamo;

@ExtendWith(MockitoExtension.class)
class DatosPersonalesReclamosImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final long USUARIO = 7L;
    private static final long ARRENDADOR = 3L;
    private static final long RESERVA = 90L;
    private static final long RECLAMO = 5L;

    @Mock
    private ReclamoRepository reclamos;
    @Mock
    private MensajeReclamoRepository mensajes;
    @Mock
    private ReservasParaReclamo reservas;
    @InjectMocks
    private DatosPersonalesReclamosImpl participante;

    private static Reclamo reclamo() {
        var reclamo = Reclamo.abrir(RESERVA, USUARIO, ARRENDADOR, MotivoReclamo.OTRO, "Mi descripción", AHORA);
        ReflectionTestUtils.setField(reclamo, "id", RECLAMO);
        reclamo.resolverAFavorDelUsuario(1L, "Texto del admin", new BigDecimal("10.00"), AHORA.plusSeconds(60));
        return reclamo;
    }

    private static MensajeReclamo mensaje(long autor, PapelReclamo papel, String texto) {
        return MensajeReclamo.nuevo(RECLAMO, autor, papel, texto, AHORA);
    }

    private void conReclamoDe(long titular, List<MensajeReclamo> suyos) {
        when(reclamos.findByUsuarioIdOrArrendadorIdOrderByAbiertoEnAscIdAsc(titular, titular))
                .thenReturn(List.of(reclamo()));
        when(mensajes.findByAutorIdOrderByFechaAscIdAsc(titular)).thenReturn(suyos);
        when(reservas.describir(List.of(RESERVA))).thenReturn(List.of(new ReservaParaReclamo(RESERVA, USUARIO, 40L,
                "Cancha Olivos", ARRENDADOR, true, LocalDate.of(2026, 11, 3), 8, 9, AHORA, AHORA)));
    }

    @Test
    @DisplayName("HU-26 R67, R68: el usuario exporta su descripción y sus mensajes, nunca la resolución")
    void exportaUsuario() {
        conReclamoDe(USUARIO, List.of(mensaje(USUARIO, PapelReclamo.USUARIO, "Mío")));

        var secciones = participante.exportar(new TitularDatos(USUARIO, "USUARIO"));

        assertThat(secciones).singleElement().satisfies(seccion -> {
            assertThat(seccion.nombre()).isEqualTo("reclamos");
            assertThat(seccion.contenido()).isEqualTo(List.of(new ReclamoExportadoDTO(RESERVA, "Cancha Olivos",
                    "OTRO", "RESUELTO_USUARIO", AHORA, AHORA.plusSeconds(60), "USUARIO",
                    List.of("Mi descripción", "Mío"))));
        });
    }

    @Test
    @DisplayName("HU-26 R67, R68: el arrendador exporta solo sus mensajes, sin la descripción del usuario")
    void exportaArrendador() {
        conReclamoDe(ARRENDADOR, List.of(mensaje(ARRENDADOR, PapelReclamo.ARRENDADOR, "Respuesta")));

        var exportado = (List<?>) participante.exportar(new TitularDatos(ARRENDADOR, "ARRENDADOR")).getFirst()
                .contenido();

        assertThat(((ReclamoExportadoDTO) exportado.getFirst()).misTextos()).containsExactly("Respuesta");
    }

    @Test
    @DisplayName("HU-26 R67: el admin no exporta la sección reclamos")
    void adminSinSeccion() {
        assertThat(participante.exportar(new TitularDatos(1L, "ADMIN"))).isEmpty();
        verifyNoInteractions(reclamos, mensajes, reservas);
    }

    @Test
    @DisplayName("HU-26 R67: sin reclamos la sección queda vacía y no describe reservas")
    void seccionVacia() {
        when(reclamos.findByUsuarioIdOrArrendadorIdOrderByAbiertoEnAscIdAsc(USUARIO, USUARIO)).thenReturn(List.of());

        var secciones = participante.exportar(new TitularDatos(USUARIO, "USUARIO"));

        assertThat(secciones).singleElement().satisfies(s -> assertThat((List<?>) s.contenido()).isEmpty());
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("HU-26 R69: con un reclamo abierto devuelve el impedimento reclamos")
    void impedimento() {
        when(reclamos.existeAbiertoDe(USUARIO)).thenReturn(true);

        assertThat(participante.impedimentos(new TitularDatos(USUARIO, "USUARIO"), AHORA)).containsExactly(
                new Impedimento("reclamos",
                        "Tienes un reclamo abierto. Espera a que se resuelva antes de eliminar tu cuenta."));
    }

    @Test
    @DisplayName("HU-26 R69: sin reclamo abierto no hay impedimento")
    void sinImpedimento() {
        when(reclamos.existeAbiertoDe(any())).thenReturn(false);

        assertThat(participante.impedimentos(new TitularDatos(ARRENDADOR, "ARRENDADOR"), AHORA)).isEmpty();
    }

    @Test
    @DisplayName("HU-26 R70: anonimizar reemplaza descripciones y mensajes del titular por \"Texto eliminado.\"")
    void anonimiza() {
        participante.anonimizar(new TitularDatos(USUARIO, "USUARIO"));

        verify(reclamos).reemplazarDescripciones(USUARIO, "Texto eliminado.");
        verify(mensajes).reemplazarTextos(USUARIO, "Texto eliminado.");
    }

    @Test
    @DisplayName("HU-26 § 11: va después de los participantes de reservas, pagos y búsqueda (@Order 4)")
    void orden() {
        assertThat(DatosPersonalesReclamosImpl.class.getAnnotation(Order.class).value()).isEqualTo(4);
    }
}

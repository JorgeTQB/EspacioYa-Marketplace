package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    private static final long ID = 7L;
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;

    private CuentaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new CuentaServiceImpl(usuarios, perfiles);
    }

    private void dado(boolean correoVerificado, EstadoIdentidad estado) {
        var usuario = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$hash", "Ana", "987654321", "2026-10",
                Instant.parse("2026-10-28T15:00:00Z"));
        ReflectionTestUtils.setField(usuario, "id", ID);
        ReflectionTestUtils.setField(usuario, "correoVerificado", correoVerificado);
        var perfil = PerfilArrendador.crear(ID, TipoDocumento.RUC, "20100070970", "c".repeat(64), "Canchas SAC",
                "Lima");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", estado);
        when(usuarios.findById(ID)).thenReturn(Optional.of(usuario));
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil));
    }

    @Test
    @DisplayName("HU-07 R41: una cuenta USUARIO o ADMIN recibe AccesoDenegado sin consultar repositorios")
    void rolDistintoDeArrendador() {
        for (var rol : new String[] {"USUARIO", "ADMIN"}) {
            var usuario = new UsuarioAutenticado(ID, 31L, rol);
            assertThatThrownBy(() -> servicio.habilitacionArrendador(usuario))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(usuarios, perfiles);
    }

    @Test
    @DisplayName("HU-07 R42: informa si el correo del arrendador está verificado")
    void correoVerificado() {
        dado(false, EstadoIdentidad.VERIFICADA);
        assertThat(servicio.habilitacionArrendador(ARRENDADOR))
                .isEqualTo(new HabilitacionArrendadorDTO(false, true));
    }

    @Test
    @DisplayName("HU-07 R15: solo VERIFICADA cuenta como identidad verificada")
    void identidadVerificadaSoloEnVerificada() {
        for (var estado : EstadoIdentidad.values()) {
            dado(true, estado);
            assertThat(servicio.habilitacionArrendador(ARRENDADOR))
                    .isEqualTo(new HabilitacionArrendadorDTO(true, estado == EstadoIdentidad.VERIFICADA));
        }
    }
}

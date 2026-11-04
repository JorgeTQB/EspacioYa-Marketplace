package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CreadorAdminTest {

    private static final String CORREO = "admin@espacioya.pe";
    private static final String CONTRASENA = "ClaveAdmin2026";

    private final CreacionAdminService servicio = mock(CreacionAdminService.class);

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withBean(CreacionAdminService.class, () -> servicio)
                .withUserConfiguration(CreadorAdmin.class);
    }

    @Test
    @DisplayName("HU-06 R19: sin espacioya.admin.crear=true no se registra el componente que crea admins")
    void sinPropiedadNoHayComando() {
        contexto().run(contexto -> assertThat(contexto).doesNotHaveBean(CreadorAdmin.class));
        contexto().withPropertyValues("espacioya.admin.crear=false")
                .run(contexto -> assertThat(contexto).doesNotHaveBean(CreadorAdmin.class));
        contexto().withPropertyValues("espacioya.admin.crear=si")
                .run(contexto -> assertThat(contexto).doesNotHaveBean(CreadorAdmin.class));
    }

    @Test
    @DisplayName("HU-06 R15, R19: con la propiedad activa crea la cuenta con las variables, no escribe el correo en "
            + "el log y cierra la aplicación")
    void conPropiedadCreaYCierra(CapturedOutput salida) {
        when(servicio.crear(CORREO, CONTRASENA)).thenReturn(42L);

        contexto().withPropertyValues("espacioya.admin.crear=true", "espacioya.admin.correo=" + CORREO,
                        "espacioya.admin.contrasena=" + CONTRASENA)
                .run(contexto -> {
                    assertThat(contexto).hasSingleBean(CreadorAdmin.class);
                    contexto.getBean(CreadorAdmin.class).run(new DefaultApplicationArguments());

                    verify(servicio).crear(CORREO, CONTRASENA);
                    assertThat(contexto.getSourceApplicationContext().isActive()).isFalse();
                });
        assertThat(salida.getAll()).contains("Cuenta admin creada").doesNotContain(CORREO).doesNotContain(CONTRASENA);
    }

    @Test
    @DisplayName("HU-06 R17: si la creación falla, la excepción detiene el arranque y la aplicación no se cierra "
            + "como si hubiera terminado bien")
    void fallaDetieneElArranque() {
        when(servicio.crear("", "")).thenThrow(new IllegalStateException("ADMIN_CORREO está vacía"));

        contexto().withPropertyValues("espacioya.admin.crear=true")
                .run(contexto -> {
                    var comando = contexto.getBean(CreadorAdmin.class);
                    assertThatThrownBy(() -> comando.run(new DefaultApplicationArguments()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("ADMIN_CORREO");
                    assertThat(contexto.getSourceApplicationContext().isActive()).isTrue();
                });
    }
}

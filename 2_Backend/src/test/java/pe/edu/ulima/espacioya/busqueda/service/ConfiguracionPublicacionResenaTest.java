package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Clock;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.texto.SanitizadorTexto;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

/** ★ Ensamblador del Decorator de HU-24: la cadena y su orden (R44). */
class ConfiguracionPublicacionResenaTest {

    @Test
    @DisplayName("HU-24 R44: la cadena es Sanitizada → Transaccional → Auditada → Base, en ese orden")
    void ordenDeLaCadena() {
        var cadena = ConfiguracionPublicacionResena.cadena(mock(ResenaDAO.class), mock(ReservasParaResena.class),
                mock(AuditoriaService.class), new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.systemUTC());

        assertThat(cadena).isInstanceOf(PublicacionSanitizada.class);
        var segunda = ((PublicacionResenaDecorador) cadena).envuelta();
        assertThat(segunda).isInstanceOf(PublicacionTransaccional.class);
        var tercera = ((PublicacionResenaDecorador) segunda).envuelta();
        assertThat(tercera).isInstanceOf(PublicacionAuditada.class);
        var cuarta = ((PublicacionResenaDecorador) tercera).envuelta();
        assertThat(cuarta).isExactlyInstanceOf(PublicacionResenaBase.class);
    }

    @Test
    @DisplayName("HU-24 R44: el bean devuelve la misma cadena que cadena(...)")
    void beanUsaLaCadena() {
        var bean = new ConfiguracionPublicacionResena().publicacionResena(mock(ResenaDAO.class),
                mock(ReservasParaResena.class), mock(AuditoriaService.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.systemUTC());

        assertThat(bean).isInstanceOf(PublicacionSanitizada.class);
    }

    @Test
    @DisplayName("HU-24 R44: ResenaService solo conoce PublicacionResena para crear, sin auditoría, transacción ni sanitizador")
    void clientSoloConoceElComponent() {
        var tipos = Arrays.stream(ResenaService.class.getDeclaredFields()).map(Field::getType).toList();

        assertThat(tipos).contains(PublicacionResena.class)
                .doesNotContain(AuditoriaService.class, TransactionTemplate.class, SanitizadorTexto.class,
                        PublicacionSanitizada.class, PublicacionTransaccional.class, PublicacionAuditada.class,
                        PublicacionResenaBase.class);
    }
}

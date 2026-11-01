package pe.edu.ulima.espacioya.pagos.service.reembolso;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.client.RestClient;

import pe.edu.ulima.espacioya.pagos.service.ReembolsoAdminService;
import pe.edu.ulima.espacioya.pagos.service.impl.PagoServiceImpl;
import pe.edu.ulima.espacioya.pagos.service.pasarela.MercadoPagoAdapter;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaSimulada;

/**
 * ★ Command y ★ Adapter de HU-16 por reflexión: qué tipos aparecen en los campos y en los parámetros de constructores y
 * métodos de las clases del reembolso y de sus dos Clients.
 */
class ArquitecturaReembolsoTest {

    private static final String PAQUETE = "pe.edu.ulima.espacioya.pagos.service.reembolso";

    /** Todas las clases compiladas del paquete del Command (incluidas las anidadas), para no olvidar ninguna nueva. */
    private static List<Class<?>> clasesDelPaquete() throws IOException, ClassNotFoundException {
        var recursos = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + PAQUETE.replace('.', '/') + "/*.class");
        var clases = new ArrayList<Class<?>>();
        for (var recurso : recursos) {
            // Solo las de src/main: los tests del paquete también están en el classpath.
            if (!recurso.getURL().toString().contains("/test-classes/")) {
                clases.add(Class.forName(PAQUETE + "." + recurso.getFilename().replace(".class", "")));
            }
        }
        return clases;
    }

    /** Tipos de los campos y de los parámetros de constructores y métodos declarados en la clase. */
    private static Set<Class<?>> tiposUsados(Class<?> clase) {
        var tipos = new HashSet<Class<?>>();
        Arrays.stream(clase.getDeclaredFields()).forEach(campo -> tipos.add(campo.getType()));
        Stream.<Executable>concat(Arrays.stream(clase.getDeclaredConstructors()),
                        Arrays.stream(clase.getDeclaredMethods()))
                .forEach(ejecutable -> tipos.addAll(Arrays.asList(ejecutable.getParameterTypes())));
        return tipos;
    }

    @Test
    @DisplayName("HU-16 R41: ninguna clase del Command ni ReembolsoAdminService usa RestClient, MercadoPagoAdapter ni "
            + "PasarelaSimulada")
    void sinTiposDelProveedor() throws Exception {
        var clases = new ArrayList<>(clasesDelPaquete());
        clases.add(ReembolsoAdminService.class);
        clases.add(PagoServiceImpl.class);

        assertThat(clases).contains(SolicitudReembolso.class, SolicitudesReembolso.class, EjecutorReembolsos.class,
                RegistroReembolsos.class, ComandoReembolso.class);
        for (var clase : clases) {
            assertThat(tiposUsados(clase)).as(clase.getSimpleName())
                    .doesNotContain(RestClient.class, MercadoPagoAdapter.class, PasarelaSimulada.class);
        }
    }

    @Test
    @DisplayName("HU-16 R40: solo SolicitudReembolso y SolicitudesReembolso reciben PasarelaPago; los Clients y el "
            + "Invoker no")
    void soloElComandoConoceLaPasarela() throws Exception {
        var clases = new ArrayList<>(clasesDelPaquete());
        clases.add(ReembolsoAdminService.class);
        clases.add(PagoServiceImpl.class);

        var conPasarela = clases.stream().filter(clase -> tiposUsados(clase).contains(PasarelaPago.class))
                .map(Class::getSimpleName).toList();

        assertThat(conPasarela).containsExactlyInAnyOrder("SolicitudReembolso", "SolicitudesReembolso");
    }
}

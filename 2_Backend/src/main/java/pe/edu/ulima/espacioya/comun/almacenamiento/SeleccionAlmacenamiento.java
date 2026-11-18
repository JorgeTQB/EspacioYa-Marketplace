package pe.edu.ulima.espacioya.comun.almacenamiento;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Valida {@code espacioya.almacenamiento.tipo} antes de crear cualquier bean (HU-29 D5). Sin esta validación, un valor
 * inválido haría fallar el arranque con un "no hay bean de AlmacenamientoArchivos" que no nombra los valores válidos.
 */
@Component
public class SeleccionAlmacenamiento implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String PROPIEDAD = "espacioya.almacenamiento.tipo";
    static final String LOCAL = "local";
    static final String S3 = "s3";

    private Environment entorno;

    @Override
    public void setEnvironment(Environment entorno) {
        this.entorno = entorno;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory fabrica) {
        validar(entorno.getProperty(PROPIEDAD));
    }

    // Misma regla que @ConditionalOnProperty (equalsIgnoreCase, sin recortar espacios): validación y selección
    // nunca discrepan.
    static void validar(String valor) {
        if (valor == null || LOCAL.equalsIgnoreCase(valor) || S3.equalsIgnoreCase(valor)) {
            return;
        }
        throw new IllegalStateException(PROPIEDAD + " (ALMACENAMIENTO_TIPO) vale '" + valor
                + "': los valores válidos son " + LOCAL + " y " + S3);
    }
}

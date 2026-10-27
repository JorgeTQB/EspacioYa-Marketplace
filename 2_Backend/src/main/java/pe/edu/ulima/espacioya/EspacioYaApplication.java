package pe.edu.ulima.espacioya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend. Los módulos (cuentas, espacios, busqueda,
 * reservas, pagos, administracion y comun) viven como subpaquetes de este.
 */
@SpringBootApplication
public class EspacioYaApplication {

	public static void main(String[] args) {

		SpringApplication.run(EspacioYaApplication.class, args);

	}
}

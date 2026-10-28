# Convenciones de código

> Homogeneidad extrema. La IA predice mejor cuando el repositorio se parece
> a sí mismo en todas partes. Seis personas programan este proyecto: el código
> debe verse como si lo hubiera escrito una sola.

## Estilo Java (backend, `2_Backend/`)

- **Versión:** la que fija `2_Backend/pom.xml` (Java 25, Spring Boot 4).
- **Formato:** 4 espacios de sangría. Líneas de máximo 120 caracteres.
- **Imports:** sin comodines (`import java.util.*` no). Sin imports sin usar.
- **Inyección de dependencias:** siempre por constructor con campos `final`.
  Nada de `@Autowired` en campos.
- **DTO:** `record` de Java, con anotaciones de Bean Validation
  (`@NotBlank`, `@Email`, `@Size`…) en los de entrada.
- **Entidades:** clases JPA con constructor protegido para JPA y métodos de
  dominio que cambian el estado (`reserva.confirmar()`), no setters públicos
  para los estados.
- **Sin Lombok** (no está en el `pom.xml`).

## Estilo React (frontend, `3_frontend/`)

- Componentes funcionales con hooks, un componente por archivo `.jsx`.
- El estilo lo marca ESLint (`npm run lint`), sin advertencias.
- Las llamadas a la API van en un solo lugar por módulo (`src/api/`), no
  dentro de los componentes.
- Colores y medidas solo desde `src/styles/tokens.css`; CSS Modules por
  componente; pantallas armadas con el kit de `src/components/ui/`. Todo el
  detalle (carpetas, paleta, kit, mensajes, rutas) está en `docs/frontend.md`.

## Nombres

| Tipo                    | Convención                               | Ejemplo |
|-------------------------|------------------------------------------|---------|
| Clases de dominio       | `PascalCase`, en español                 | `Reserva`, `Espacio`, `Liquidacion` |
| Clases técnicas         | dominio en español + sufijo en inglés    | `ReservaController`, `ReservaService`, `ReservaRepository`, `ReservaDTO` |
| Enums de estado         | `PascalCase` + valores `UPPER_SNAKE`     | `EstadoReserva.PENDIENTE_PAGO` |
| Métodos / variables     | `camelCase`, en español                  | `calcularMonto`, `fechaInicio` |
| Constantes              | `UPPER_SNAKE`                            | `MINUTOS_RETENCION_RESERVA` |
| Paquetes                | minúsculas, sin guiones                  | `pe.edu.ulima.espacioya.reservas.service` |
| Tablas y columnas       | `snake_case`, en español                 | `reserva_bloque`, `id_transaccion_pasarela` |
| Componentes React       | `PascalCase.jsx`                         | `MisReservas.jsx` |
| Ramas                   | `feature/HU-XX-descripcion-corta`        | `feature/HU-01-registro-usuario` |

## Endpoints REST

- En plural y minúsculas, con guiones si hace falta: `/api/reservas`,
  `/api/espacios/{id}/disponibilidad`.
- Verbos HTTP con su significado: `GET` lee, `POST` crea, `PUT`/`PATCH`
  modifica, `DELETE` elimina.
- La identidad del usuario sale de la sesión; nunca de un `usuarioId` en la URL
  o el cuerpo.

## Estructura de archivo

Cada clase vive en el subpaquete de su capa dentro del módulo:

```
pe/edu/ulima/espacioya/<modulo>/
├── controller/   <Recurso>Controller.java
├── dto/          <Recurso>DTO.java, Crear<Recurso>DTO.java
├── service/      <Recurso>Service.java
├── domain/       <Entidad>.java, Estado<Entidad>.java
└── repository/   <Entidad>Repository.java
```

Las clases concretas de cada historia las fija su `design.md`. Los puertos
entre módulos van en `service/puertos/` (ver `docs/architecture.md`), y las
tablas siguen `docs/modelo-datos.md`.

Migraciones Flyway en `src/main/resources/db/migration/`, numeradas por sprint
para no chocar entre personas: `V<sprint>_<n>__<descripcion>.sql`
(p. ej. `V1_1__usuarios_y_tokens.sql`). Una migración ya subida a `develop`
no se edita: se crea otra.

### Migraciones que llegan tarde (`out-of-order`)

Las historias no terminan en orden de sprint, así que una `V1_n` puede llegar a
`develop` después de una `V2_n` ya aplicada (pasó con `V1_5__token.sql` de HU-04
tras `V2_1` de HU-05). Por eso `application.properties` tiene
`spring.flyway.out-of-order=true`: Flyway aplica la migración atrasada en vez de
negarse a arrancar. Reglas:

- **El número sale del sprint de tu historia** (`sprint` en `feature_list.json`)
  y es el siguiente `n` libre de ese sprint **en `develop`**. Antes de crearla,
  actualiza `develop` y mira la carpeta de migraciones.
- **Si al unir tu rama `develop` ya tiene tu número** (otra persona del mismo
  sprint llegó antes), renombra la tuya al siguiente `n` libre antes del merge.
  Todavía no está en `develop`, así que renombrarla está permitido.
- **Una migración no depende de otra de número mayor.** En una base vacía
  (Testcontainers, una base local nueva) Flyway aplica por número, así que una
  `V1_6` que altera una tabla creada en `V2_1` falla. Si la necesitas, numérala
  en el sprint de esa migración y justifícalo en el `design.md`.
- **Si tu base local se queja de Flyway** tras traer `develop`, bórrala y deja
  que las migraciones la recreen: `docker compose -f 1_Database/compose.yaml down -v`.

## Tests

- **Herramientas:** JUnit 5 + Mockito para unitarias; `@SpringBootTest` +
  `MockMvc` contra un PostgreSQL real con Testcontainers para integración.
  No se usa H2: las migraciones son de PostgreSQL.
- **Ubicación:** `2_Backend/src/test/java/`, en el mismo paquete que la clase
  probada.
- **Nombres de clase:** `<Clase>Test` (unitaria, corre con `./mvnw test`, sin
  Docker) y `<Clase>IT` (integración, corre con `./mvnw verify`, necesita
  Docker). Toda `*IT` lleva `@Import(TestcontainersConfiguration.class)`.
- **Una prueba por criterio de aceptación**, incluido el de error, y por cada
  control STRIDE verificable.
- **Nombres de test descriptivos** y `@DisplayName` con la historia y el
  requirement que cubre:

  ```java
  @Test
  @DisplayName("HU-01 R2: rechaza el registro sin aceptar la privacidad")
  void rechazaRegistroSinAceptarPrivacidad() { ... }
  ```

- El reloj se inyecta como bean `Clock` para probar plazos (15 min, 48 h, 72 h)
  sin esperar.

## Manejo de errores

Excepciones de negocio en `comun/error/`:

```java
public class ReglaNegocioException extends RuntimeException { }      // regla violada → 400/409
public class RecursoNoEncontradoException extends RuntimeException { } // no existe o no es tuyo → 404
public class AccesoDenegadoException extends RuntimeException { }      // rol insuficiente → 403
```

Un único `ManejadorGlobalErrores` (`@RestControllerAdvice`) las convierte en un
`ErrorDTO` con mensaje genérico. El detalle técnico va solo a los logs. Nunca
se devuelven stack traces ni mensajes que confirmen si un correo o un recurso
existe.

## Configuración y secretos

- En variables de entorno, leídas en `application.properties` con `${VARIABLE}`.
- Nunca se suben contraseñas, llaves de la pasarela ni tokens. Se mantiene
  `2_Backend/.env.example` con los nombres y sin valores reales.
- Única excepción: los valores por defecto de la base local
  (`1_Database/compose.yaml`), que no protegen nada fuera de tu máquina.

## Ramas y pull requests

- Una rama por historia: `feature/HU-XX-descripcion-corta`, creada desde `develop`.
- Se termina con un pull request hacia `develop`. Nada entra a `develop` sin
  que la pareja revise el PR.

## Comentarios

Por defecto **no** se escriben. Solo se permiten cuando explican un *por qué*
no obvio (p. ej. una regla de negocio sutil o una restricción de la pasarela).
Los nombres deben hacer el resto. Sin `System.out.println` ni `console.log`
de depuración.

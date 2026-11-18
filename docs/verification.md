# Verificación — Cómo demostrar que el trabajo funciona

> Regla de oro: **el agente no dice "funciona", lo demuestra**.
> Toda historia termina con evidencia ejecutable, no con afirmaciones.

## Niveles de verificación

### Nivel 1 — Tests unitarios (obligatorio)

Todo servicio y toda regla de dominio tiene tests JUnit 5 que:

1. Cubren cada criterio de aceptación de la historia, incluido el de error.
2. Cubren cada control STRIDE verificable en código (hash de contraseña,
   propiedad del recurso, rate limiting, mensajes genéricos…).

Las dependencias del servicio (repositorios, otros servicios, adaptadores) se
reemplazan con Mockito. El reloj se inyecta como `Clock` fijo.

Comando:
```bash
cd 2_Backend && ./mvnw test
```

`./init.sh` no corre todas: corre solo las unitarias de las clases creadas o
modificadas en la rama respecto de `develop` (`<Clase>Test` y toda `*Test`
que mencione la clase). Si cambia `pom.xml` o la configuración en
`src/*/resources` (salvo las migraciones), corren todas. `./init.sh --todo`
fuerza la suite unitaria completa.

### Nivel 2 — Tests de integración (obligatorio para historias con endpoints o restricciones de BD)

Las historias que añaden o cambian endpoints, migraciones o restricciones de
la base de datos se verifican levantando el contexto de Spring
(`@SpringBootTest`) contra un **PostgreSQL real en Docker** (Testcontainers),
con las mismas migraciones Flyway que UAT, y llamando al endpoint real con
`MockMvc`:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistroControllerIT {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("HU-01 R2: rechaza el registro sin aceptar la privacidad")
    void rechazaRegistroSinAceptarPrivacidad() throws Exception {
        mvc.perform(post("/api/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"correo":"a@b.pe","contrasena":"Segura123!","aceptaPrivacidad":false}
                    """))
           .andExpect(status().isBadRequest());
    }
}
```

Comprueban también que la respuesta es un DTO sin campos sensibles y que los
errores no revelan datos internos.

Las reglas que protege la base de datos (bloque único, pago único, un
reembolso por pago…) se prueban aquí, no en UAT. Si la regla es de
concurrencia, la prueba lanza las dos operaciones a la vez:

```java
@Test
@DisplayName("HU-12 R4: dos reservas simultáneas del mismo bloque, solo una gana")
void soloUnaReservaGanaElMismoBloque() throws Exception {
    var listos = new CountDownLatch(1);
    Callable<Boolean> reservar = () -> {
        listos.await();
        try { reservaService.crear(solicitudMismoBloque()); return true; }
        catch (ReglaNegocioException e) { return false; }
    };
    try (var hilos = Executors.newFixedThreadPool(2)) {
        var a = hilos.submit(reservar);
        var b = hilos.submit(reservar);
        listos.countDown();
        assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
    }
}
```

Comando (necesita Docker Desktop encendido):
```bash
cd 2_Backend && ./mvnw verify
```

En el modo completo, `./init.sh` corre **siempre todas** las `*IT`: son las
que miran el sistema entero y atrapan lo que la selección de unitarias deja
pasar (una clase no tocada que se rompe por un cambio en otra).

### Nivel 3 — Caja negra en UAT (obligatorio, lo hace la pareja)

Con la historia desplegada en UAT, la pareja ejecuta los casos de caja negra
desde el frontend o la API y guarda las capturas como `HU-XX-E01`,
`HU-XX-E02`… Luego llena la fila de la historia en la matriz de trazabilidad
(`docs/requerimientos.md` §7). El Product Owner la acepta en la review.

### Nivel 4 — Trazabilidad de requirements (obligatorio para historias con `"sdd": true`)

Cada `R<n>` de `specs/HU-XX/requirements.md` debe poder mapearse a al
menos un test concreto. El reviewer rechaza si falta cobertura.

El implementer documenta el mapa en `progress/impl_HU-XX.md`:

```markdown
## Trazabilidad
- R1 → `RegistroServiceTest.registraUsuarioConDatosValidos`
- R2 → `RegistroControllerIT.rechazaRegistroSinAceptarPrivacidad`
- R3 → `RegistroServiceTest.guardaContrasenaConHash`
```

### Frontend

Mientras no haya pruebas de frontend, el mínimo es que compile y pase el lint:

```bash
cd 3_frontend && npm run lint && npm run build
```

Las pantallas no tienen `R<n>` propios: sus requirements se verifican en la
API. Además del lint y el build:

- `./init.sh` rechaza colores fuera de `src/styles/tokens.css` y librerías de
  componentes o de CSS.
- El `reviewer` comprueba que la pantalla respeta `docs/frontend.md` y la
  sección "Pantallas" del `design.md`.
- La pareja comprueba en UAT, con la caja negra (Nivel 3), que cada CA visible
  se ve como dice esa sección.

## Anti-patrones (no hacer)

- ❌ "He añadido el endpoint, debería funcionar." → falta test ejecutable.
- ❌ Test que solo verifica que el método no lanza excepción. → tiene que
  comprobar el resultado concreto (estado, monto, código HTTP, campos del DTO).
- ❌ Test que no prueba el camino de error del criterio de aceptación.
- ❌ Mockear la clase que se está probando o el dominio. → solo se mockean
  sus dependencias.
- ❌ Tests que dependen de la hora real o de servicios externos (pasarela,
  correo). → `Clock` fijo y adaptadores simulados.
- ❌ Marcar la historia como `done` sin pasar `./init.sh`.

## Definition of Done

Tiene dos partes. El estado `done` de `feature_list.json` cubre solo la
primera; la segunda se registra en la columna **Estado** de la matriz §7 de
`docs/requerimientos.md`.

**1. Código terminado** (lo verifica el arnés y permite marcar `done`):

- Código revisado, con pruebas unitarias y de integración verdes.
- Todos los `R<n>` con su test y todas las tasks `[x]`.
- Controles STRIDE de la historia implementados.
- Cumple PCI DSS y la protección de datos cuando aplica.
- Documentación actualizada si cambia un contrato de API o un estado.

**2. Historia aceptada** (la registra la pareja en la matriz §7):

- Desplegada en UAT, con los casos de caja negra y sus capturas (Nivel 3).
- Validada por el Product Owner en la review.

## Verificación final antes de cerrar

```bash
./init.sh           # debe terminar con [OK] Entorno listo
```

Si `./init.sh` está rojo, **no** marques nada como `done`. Anota el bloqueo
en `progress/HU-XX.md` con estado `blocked` en `feature_list.json`.

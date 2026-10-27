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

### Nivel 2 — Tests de integración (obligatorio para historias con endpoints)

Las historias que añaden o cambian endpoints se verifican levantando el
contexto de Spring (`@SpringBootTest`) con H2 en memoria y llamando al
endpoint real con `MockMvc`:

```java
@SpringBootTest
@AutoConfigureMockMvc
class RegistroControllerIntegrationTest {
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

> **Límite de H2:** H2 no soporta todo el SQL de PostgreSQL (por ejemplo, el
> índice único parcial del bloque activo de HU-12). Si una restricción solo
> existe en PostgreSQL, el `design.md` lo indica y se verifica en UAT (Nivel 3).

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
- R2 → `RegistroControllerIntegrationTest.rechazaRegistroSinAceptarPrivacidad`
- R3 → `RegistroServiceTest.guardaContrasenaConHash`
```

### Frontend

Mientras no haya pruebas de frontend, el mínimo es que compile y pase el lint:

```bash
cd 3_frontend && npm run lint && npm run build
```

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

- Código revisado, con pruebas unitarias y de integración verdes.
- Todos los `R<n>` con su test y todas las tasks `[x]`.
- Controles STRIDE de la historia implementados.
- Cumple PCI DSS y la protección de datos cuando aplica.
- Documentación actualizada si cambia un contrato de API o un estado.
- Desplegada en UAT y validada por el Product Owner.

## Verificación final antes de cerrar

```bash
./init.sh           # debe terminar con [OK] Entorno listo
```

Si `./init.sh` está rojo, **no** marques nada como `done`. Anota el bloqueo
en `progress/HU-XX.md` con estado `blocked` en `feature_list.json`.

# Arquitectura de despliegue en AWS (UAT)

> Fuente de verdad del despliegue de EspacioYa en UAT. El diagrama está en
> [`arquitectura-aws.html`](arquitectura-aws.html). Última revisión: 2026-11-15, después de HU-29, HU-30 y HU-31.
>
> **Estado:** la arquitectura está definida, el backend ya está listo para 2 réplicas y ya existen los Dockerfiles
> y el Terraform ([`terraform/`](terraform/README.md)), probado de punta a punta en AWS Emulator. **Todavía no hay
> nada desplegado en el Learner Lab** (ver § 10, "Próximos pasos").

## 1. Entorno: AWS Academy Learner Lab

Las restricciones del entorno determinan varias decisiones:

| Restricción | Consecuencia |
|---|---|
| Solo `us-east-1` o `us-west-2` | Todo en **`us-east-1`** |
| No se crean roles IAM; solo existe `LabRole` | `LabRole` como *task role* y *execution role* de ECS. Sin usuarios IAM ni claves de acceso |
| **CloudFront bloqueado** (`AccessDenied` en `cloudfront:ListDistributions`) | El frontend no puede ir en S3 + CloudFront: va en un contenedor **nginx** detrás del ALB |
| **SES no está disponible** | Correo **simulado** en UAT (`CORREO_PROVEEDOR=simulado`). Opcional: SMTP externo (Brevo, Mailtrap) sin cambiar código |
| Route 53 no registra dominios | Sin dominio propio no hay certificado público de ACM (ver § 6, HTTPS) |
| RDS: micro/small/medium, gp2, sin Enhanced Monitoring | `db.t4g.micro`, gp2 de 20 GB |
| Si se supera el presupuesto, la cuenta se **desactiva y se borra** | Bajar las tareas a 0 y detener RDS cuando no se use (§ 9) |
| RDS y Fargate **siguen corriendo** al cerrar la sesión del lab | Igual que arriba: apagar a mano |

## 2. Vista general

```
Usuario (navegador)
   │ HTTPS 443
   ▼
ALB + AWS WAF ────────────── /api/*  ──►  ECS Fargate · servicio backend (2 tareas, Spring Boot .jar, :8080)
   │                                          │  JDBC 5432         │ SDK S3 (HTTPS)       │ HTTPS
   │  /* (default)                            ▼                    ▼                      ▼
   └──────────────────────────────►  ECS Fargate · nginx      RDS PostgreSQL 17     S3 privado         Pasarela (simulada en UAT)
                                     (1 tarea, dist/, :80)    (subred privada)      (documentos, fotos)
```

- **Mismo origen:** el navegador solo habla con el ALB. El frontend se compila con `VITE_API_URL` **vacía** y llama a
  `/api/...` con ruta relativa (`3_frontend/src/api/cliente.js:3`, `import.meta.env.VITE_API_URL ?? ''`). No hace falta
  CORS.
- **Autenticación:** token Bearer en `sessionStorage`, sin cookies. Las sesiones están en la base de datos, así que
  cualquier réplica atiende a cualquier usuario y el ALB no necesita *sticky sessions*.

## 3. Componentes

| Componente | Servicio | Configuración |
|---|---|---|
| Balanceador | **Application Load Balancer** (internet-facing, 2 AZ, subredes públicas) | Listener 443 (HTTPS, certificado en ACM) y listener 80 que **redirige a 443**. Regla `/api/*` → TG `espacioya-backend`; regla por defecto → TG `espacioya-frontend`. Sin sesiones pegajosas |
| Firewall | **AWS WAF** (Web ACL asociada al ALB) | `AWSManagedRulesCommonRuleSet`, `AWSManagedRulesKnownBadInputsRuleSet` y una regla *rate-based* por IP (p. ej. 1000 peticiones / 5 min). La regla por IP complementa a `LimitadorIntentos` (HU-30) |
| Frontend | **ECS Fargate**, servicio `espacioya-frontend`, **1 tarea** (0.25 vCPU / 0.5 GB) | Imagen nginx con el `dist/`. `try_files $uri /index.html` para las rutas de la SPA. Health check del TG: `GET /` → 200 |
| Backend | **ECS Fargate**, servicio `espacioya-backend`, **2 tareas** (1 vCPU / 2 GB cada una) | Imagen con el `.jar` en Java 25 JRE, puerto 8080, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. Health check del TG: `GET /api/politicas-privacidad/vigente` → 200 (ruta pública; no hay Actuator, así que no comprueba la base). *Deployment*: mínimo 100 %, máximo 200 % (despliegue sin cortes) |
| Base de datos | **RDS PostgreSQL 17**, `db.t4g.micro`, gp2 20 GB, Single-AZ | Cifrado en reposo (KMS), backups automáticos de 1 a 7 días, sin acceso público, subredes privadas |
| Archivos | **S3**, bucket privado | Block Public Access activado, cifrado por defecto SSE-S3. Lo usa el backend con `ALMACENAMIENTO_TIPO=s3` (HU-29). La app no crea el bucket |
| Imágenes | **ECR**: `espacioya-backend`, `espacioya-frontend` | Imágenes etiquetadas con el commit |
| Secretos | **Secrets Manager** (recomendado) o variables de la *task definition* | Llaves de cifrado y firma, contraseña de la base. En la *task definition* van como `secrets` (ARN), no como texto |
| Logs | **CloudWatch Logs** | Driver `awslogs`: grupos `/ecs/espacioya-backend` y `/ecs/espacioya-frontend`, retención de 7 días |
| Correo | Ninguno en UAT | `CORREO_PROVEEDOR=simulado`. Para correos reales: `smtp` + variables `CORREO_SMTP_*` (`docs/architecture.md`, "Correo") |
| Pasarela | Ninguna en UAT | `PAGOS_PASARELA=simulada`, `PAGOS_SECRETO_WEBHOOK` vacía (la pantalla simulada es la única entrada) |

## 4. Red

**Decisión recomendada: sin NAT Gateway.** Un NAT cuesta ~32 USD al mes más el tráfico, y en el Learner Lab no aporta
nada que no se logre con grupos de seguridad.

| Pieza | Subred | Entrada permitida (Security Group) |
|---|---|---|
| ALB | Pública (2 AZ) | 443 y 80 desde `0.0.0.0/0` |
| Tareas Fargate (backend y nginx) | **Pública**, con `assignPublicIp=ENABLED` (para bajar imágenes de ECR y hablar con S3/Secrets Manager sin NAT) | **Solo desde el SG del ALB** (8080 backend, 80 nginx). Nada directo desde Internet |
| RDS | **Privada** (2 AZ, grupo de subredes) | 5432 **solo desde el SG del backend** |

- **VPC endpoint gateway para S3** (gratis): el tráfico del backend al bucket no sale a Internet.
- Alternativa con tareas en subred privada: hacen falta endpoints de interfaz para ECR (`api` y `dkr`), CloudWatch Logs y
  Secrets Manager (~7 USD al mes cada uno) o un NAT. Cuesta lo mismo o más y no mejora la seguridad, porque el SG
  ya bloquea toda entrada que no venga del ALB.
- `PROXIES_CONFIANZA` = la red de la VPC, p. ej. `10\.0\.\d{1,3}\.\d{1,3}`. Sin esto, todos los clientes comparten la
  IP del ALB, y el límite del catálogo (120 por minuto por IP) se aplica a todos juntos.

## 5. Escalabilidad y disponibilidad (2 réplicas del backend)

El backend corre en **2 tareas idénticas** y el ALB las reparte por turnos (*round robin*). No guarda nada en la memoria
de cada contenedor:

| Estado | Dónde vive | Historia |
|---|---|---|
| Sesiones, checkouts, reservas, pagos | PostgreSQL | Desde el diseño original |
| Documentos y fotos | S3 (`ALMACENAMIENTO_TIPO=s3`) | **HU-29** |
| Contadores de límite de intentos (login, MFA, catálogo, etc.) | PostgreSQL, tabla `intento_limite` | **HU-30** |
| Fallo simulado de reembolsos (demo de HU-16) | Número de intento en `reembolso.intentos` | **HU-31** |
| Procesos programados (expiración, recordatorios, limpieza) | Corren en ambas tareas; se coordinan con bloqueo de fila o bloqueo consultivo | HU-12, HU-25, HU-30 |
| Migraciones Flyway | Se aplican al arrancar; Flyway toma un bloqueo en PostgreSQL, así que dos tareas que arrancan a la vez no chocan | — |

Consecuencias:

- **Disponibilidad (RNF-067):** si una tarea cae, la otra sigue atendiendo mientras ECS reemplaza la caída.
- **Rendimiento (RNF-009, login p95 ≤ 2 s con 50 concurrentes):** bcrypt con costo 12 tarda ~0.25 s por núcleo; dos
  tareas duplican la CPU disponible para el login.
- **Opcional:** *Application Auto Scaling* con mínimo 1 y máximo 2–3 tareas, según CPU al 60 %.
- **Pool de conexiones:** 2 tareas × 10 conexiones de Hikari = 20, muy por debajo del límite de `db.t4g.micro`.

## 6. Seguridad

- **HTTPS (RNF-SEG-13) — decisión pendiente del equipo.** ACM solo emite certificados públicos para un dominio que se
  pueda validar. Opciones, de mejor a peor:
  1. **Dominio propio barato** (cualquier registrador): se valida el certificado en ACM con un registro CNAME en el DNS
     del registrador y se apunta el dominio al ALB con otro CNAME. No necesita Route 53. **Recomendada.**
  2. **Certificado autofirmado importado en ACM:** el tráfico va cifrado, pero el navegador muestra una advertencia. Se
     documenta como limitación de UAT.
  3. Solo HTTP con el DNS del ALB: **no cumple RNF-SEG-13.** Solo para una prueba interna rápida.
- **Secretos:** nunca en el repositorio ni en la imagen. Se pasan en la *task definition* como `secrets` desde Secrets
  Manager.
- **Credenciales de AWS:** el backend usa `LabRole` por la cadena por defecto del SDK. No hay claves de acceso.
- **Admin:** `ADMIN_IPS_PERMITIDAS` con las IPs de quienes administran, más MFA obligatorio (HU-06, HU-17).
- **Base de datos:** sin acceso público; solo acepta conexiones del SG del backend. Cifrada en reposo.
- **Bucket:** privado, con SSE-S3. Los documentos además van cifrados por la aplicación (AES-256-GCM).

## 7. Configuración del backend (variables de la task definition)

| Variable | Valor en UAT | Origen |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<endpoint-rds>:5432/espacioya` | Variable |
| `DB_USUARIO` | usuario maestro de RDS | Variable |
| `DB_CONTRASENA` | contraseña de RDS | **Secret** |
| `CIFRADO_LLAVE`, `CIFRADO_LLAVE_HUELLA`, `ALMACENAMIENTO_LLAVE_FIRMA` | 32 bytes en base64, distintas y **propias de UAT** (`openssl rand -base64 32`) | **Secret**. No se rotan las de cifrado: lo cifrado dejaría de leerse. Rotar `CIFRADO_LLAVE_HUELLA` reinicia los contadores de HU-30 |
| `FRONTEND_URL` | `https://<dominio o DNS del ALB>` | Variable |
| `ALMACENAMIENTO_TIPO` | `s3` | Variable |
| `ALMACENAMIENTO_S3_BUCKET` | nombre del bucket | Variable |
| `ALMACENAMIENTO_S3_REGION` | `us-east-1` | Variable |
| `PROXIES_CONFIANZA` | `10\.0\.\d{1,3}\.\d{1,3}` (según la CIDR de la VPC) | Variable |
| `ADMIN_IPS_PERMITIDAS` | IPs o CIDR de quienes administran | Variable |
| `PAGOS_PASARELA` | `simulada` | Variable |
| `CORREO_PROVEEDOR` | `simulado` | Variable |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` | Variable |
| `LIMITE_CATALOGO_MAXIMO` | `120` (subirlo **solo** durante las pruebas de carga) | Variable |

**Crear el admin:** se corre una tarea única (`aws ecs run-task`) con la misma *task definition* y además
`ADMIN_CREAR=true`, `ADMIN_CORREO` y `ADMIN_CONTRASENA` (esta como secret). La tarea crea la cuenta y termina
(README, "Cuenta de administrador").

**Frontend:** `npm --prefix 3_frontend run build` con `VITE_API_URL=` vacía. El `.env` local (con `localhost`) **no**
debe entrar a la imagen (`.dockerignore`).

## 8. Pruebas de carga (JMeter)

- Se ejecutan **contra UAT ya desplegado** (la URL del ALB). JMeter solo genera la carga; lo que se mide es el
  servidor de la URL. Contra `localhost` se mide la laptop.
- Primero se arma y depura el `.jmx` en local, pero esos números no valen como evidencia.
- Escenarios de los RNF: login (RNF-009), búsqueda sobre **2000 espacios publicados** (RNF-023/056), ficha
  (RNF-026, p95 ≤ 1 s) con 50 usuarios concurrentes y meta de p95 ≤ 2 s.
- Antes de la prueba:
  - sembrar 2000 espacios y **50 cuentas de prueba distintas** (CSV en JMeter), para no activar el bloqueo de login;
  - subir `LIMITE_CATALOGO_MAXIMO` y relajar la regla por IP del WAF, porque toda la carga sale de una sola IP.
- Modo sin interfaz: `jmeter -n -t plan.jmx -l resultados.jtl -e -o reporte/` (el reporte HTML trae el p95).
- La latencia de Lima a `us-east-1` suma ~100–150 ms por respuesta. Para quitar ese ruido, JMeter se puede correr
  desde un EC2 en la misma región.

## 9. Costos aproximados (us-east-1, 24/7)

| Recurso | USD/hora aprox. |
|---|---|
| 2 tareas backend (1 vCPU / 2 GB) | 0.099 |
| 1 tarea nginx (0.25 vCPU / 0.5 GB) | 0.012 |
| ALB (sin contar LCU) | 0.023 |
| RDS `db.t4g.micro` | 0.016 |
| IPv4 públicas (3 tareas + ALB) | ~0.025 |
| **Total** | **~0.175 USD/h ≈ 125 USD/mes**, más WAF (~7 USD/mes), Secrets Manager (0.40 USD por secreto al mes) y almacenamiento |

**Para no agotar el presupuesto del lab:** fuera de las pruebas y la demo, poner los dos servicios en *desired count*
0 y detener RDS (se puede detener hasta 7 días). Se puede dejar el backend en 1 tarea y subirlo a 2 solo para la
prueba de carga y la demo.

## 10. Próximos pasos (para la siguiente sesión)

1. **Dockerfiles** (todavía no existen en el repo):
   - `2_Backend/Dockerfile`: *multi-stage*. Compila con el Maven Wrapper y Java 25, y corre en `eclipse-temurin:25-jre`
     como usuario no root, con `EXPOSE 8080`.
   - `3_frontend/Dockerfile`: *multi-stage*. `npm ci` y `npm run build` con `VITE_API_URL=` vacía, y luego
     `nginx:alpine` con un `nginx.conf` que haga *fallback* de la SPA a `index.html` y cachee `assets/`.
   - `.dockerignore` en ambos (sin `.env`, `node_modules`, `target`, `almacenamiento/`).
2. Crear los repositorios ECR y subir las imágenes.
3. Crear la red: VPC, subredes públicas y privadas en 2 AZ, SG, y el endpoint gateway de S3.
4. RDS PostgreSQL 17 y el bucket S3 privado.
5. Secrets Manager, el cluster ECS, las *task definitions* (con `LabRole`), los servicios, el ALB con sus TG y reglas,
   y el WAF.
6. HTTPS según la decisión del equipo (§ 6).
7. Crear el admin con una tarea única y hacer una prueba de humo.
8. Sembrar datos y correr JMeter (§ 8).

Los pasos 2 a 8 son de infraestructura (`sdd: false`); el paso 1 agrega archivos de empaquetado, no lógica.

## 11. Decisiones y alternativas descartadas

| Decisión | Alternativa descartada | Motivo |
|---|---|---|
| Frontend en nginx sobre Fargate detrás del ALB | S3 + CloudFront | CloudFront bloqueado en el Learner Lab. Un sitio estático de S3 sin CloudFront no tiene HTTPS |
| Mismo origen (ALB enruta `/api/*`) | Dos dominios con CORS | Menos configuración y sin *preflight* |
| 2 tareas del backend | 1 tarea | Disponibilidad y CPU para bcrypt. Requirió HU-29/30/31 |
| S3 para archivos (HU-29) | EFS | S3 es más barato, coincide con el diseño y no exige montar volúmenes. EFS no pedía código, pero sí NFS y más costo |
| Contador de intentos en PostgreSQL (HU-30) | Redis/ElastiCache, sesiones pegajosas | Un servicio menos. Las sesiones pegajosas no protegen: el atacante descarta la cookie |
| Tareas en subred pública con SG solo desde el ALB | Subred privada con NAT | ~32 USD/mes sin beneficio real en este entorno |
| Correo simulado en UAT | Amazon SES | SES no está disponible; R14 del informe ya fija correo simulado |

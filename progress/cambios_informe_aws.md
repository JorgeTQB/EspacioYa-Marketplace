# Cambios al informe por la nueva arquitectura AWS

> Informe: `Documentacion del Proyecto Final_2026_2 (1).docx` (carpeta de contexto). Revisado el 2026-11-15.
> Fuente técnica: [`4_Infraestructure/arquitectura-aws.md`](../4_Infraestructure/arquitectura-aws.md).
> El número entre paréntesis (§ p.NNNN) es el índice del párrafo en el XML del documento; sirve para ubicarlo, no es
> la página.

## Qué cambió

| Antes (informe) | Ahora |
|---|---|
| Frontend en S3 + CloudFront | Contenedor **nginx** en ECS Fargate detrás del ALB (CloudFront bloqueado en el Learner Lab) |
| "Un solo contenedor" del backend | **2 tareas** del backend; el ALB reparte la carga |
| Documentos y fotos "en S3 privado" (en realidad, en disco) | S3 privado de verdad con `ALMACENAMIENTO_TIPO=s3` (HU-29) |
| Correos por Amazon SES | Correo **simulado** en UAT (SES no está disponible); SMTP externo opcional |
| Límite de intentos en memoria | Contador compartido en PostgreSQL (HU-30) |
| Pruebas solo en local; sin pruebas de carga | Pruebas de carga con **JMeter contra UAT** |
| CPU/RAM "[Completar]" | Backend 1 vCPU / 2 GB × 2; nginx 0.25 vCPU / 0.5 GB; RDS `db.t4g.micro` |

## Lista de cambios por sección

### Cap. 5 — Gestión del producto

1. **5.1 Product backlog** y **5.6 Trazabilidad**: agregar HU-29, HU-30 y HU-31 como historias técnicas (épica T,
   "Despliegue en AWS"), dueño Jorge Quispe, estado Completada, evidencia en `specs/HU-29..31/`.
   *Decidir con el equipo* en qué sprint se reportan: en `feature_list.json` figuran como sprint 9, pero el informe
   solo tiene 8 sprints. Opción: presentarlas como "endurecimiento para el despliegue" dentro del Sprint 8.
2. **5.4 Riesgos, decisiones y trade-offs**: agregar dos filas.
   - *Entorno sin CloudFront ni SES:* decisión nginx detrás del ALB y correo simulado. Trade-off: no hay CDN, pero
     queda un solo origen y no hace falta CORS.
   - *Escalar a 2 réplicas:* decisión de sacar el estado de la memoria (HU-29, HU-30, HU-31). Trade-off: tres historias
     técnicas y el doble de costo de cómputo, a cambio de disponibilidad y del doble de CPU para el login.
3. **5.5 Evolución de los requisitos**: un párrafo con lo anterior.

### Cap. 6 — Arquitectura

4. **6.1, stakeholder ST06** (§ p.2057). Reemplazar por:
   > Requiere un ALB con WAF, el frontend en un contenedor nginx y el backend en dos tareas de ECS Fargate, RDS
   > PostgreSQL cifrado, un bucket S3 privado para documentos y fotos, y llaves en Secrets Manager. Todo dentro de
   > las restricciones de AWS Academy Learner Lab.
5. **6.3, escenario de disponibilidad** (§ p.2094: "dependen del contenedor del backend"). Cambiar a "dependen de las
   dos réplicas del backend…" y agregar: "si una réplica cae, la otra sigue atendiendo mientras ECS la reemplaza".
6. **6.3, AC008 rendimiento** (§ p.2247–2264): agregar a la medida "verificado con JMeter contra UAT".
7. **6.4 Restricciones**:
   - **R02** (§ p.2284): "…se despliega como archivos estáticos en S3 + CloudFront" → "…se despliega como archivos
     estáticos servidos por un contenedor nginx detrás del balanceador".
   - **R08** (§ p.2312–2314). Reemplazar las tres celdas:
     - Restricción: *El despliegue en UAT es en AWS Academy Learner Lab: ALB con WAF, ECS Fargate (frontend nginx y
       backend con dos réplicas), RDS PostgreSQL cifrado y S3 privado. CloudFront y SES no están disponibles en ese
       entorno.*
     - Origen: *Equipo y entorno: la cuenta asignada es un Learner Lab, que solo permite el rol LabRole, las regiones
       us-east-1 y us-west-2, y bloquea CloudFront.*
     - Consecuencia: *El backend se empaqueta como un .jar en un contenedor sin estado en memoria: sesiones, límites
       de intentos y archivos van a PostgreSQL y S3 (HU-29, HU-30, HU-31). El frontend y la API comparten el origen
       del balanceador, sin CORS.*
   - **Nueva restricción R15 (HTTPS sin dominio)**, según lo que decida el equipo (ver § 6 de `arquitectura-aws.md`).
8. **6.5 Componentes** (§ p.2381): "Correo: NotificadorCorreo (simulado; Amazon SES en UAT)" → "Correo:
   NotificadorCorreo (simulado en UAT; SMTP real configurable). Archivos: AlmacenamientoArchivos (disco en local, S3
   en UAT)".
9. **6.7 Vista lógica** (§ p.2810): "desde el Servidor Frontend (AWS S3 + CloudFront…)" → "desde el Servidor
   Frontend (contenedor nginx en ECS Fargate detrás del balanceador…)".
10. **6.9 Vista de despliegue** (§ p.2874–3023). Es la sección que más cambia:
    - **Texto inicial** (§ p.2883–2889): quitar "La vista de despliegue nos muestra ambos dispositivos…" y describir
      Navegador → ALB/WAF → nginx y backend ×2 → RDS y S3.
    - **Figura 7**: reemplazarla por el diagrama actualizado (`4_Infraestructure/arquitectura-aws.html`) o por un
      diagrama de despliegue UML con esos nodos.
    - **Tabla 1 (Nodos)**: agregar *Balanceador (ALB + WAF)* y *Almacenamiento de archivos (S3)*. El *Servidor de
      Aplicaciones* pasa a "dos réplicas idénticas".
    - **Tabla 2 (Componentes por nodo)**: Servidor Frontend → "nginx + SPA React (dist/)".
    - **Tabla 3 (Tecnologías)**:
      - Fila "AWS S3 + CloudFront" → **"nginx en ECS Fargate"**: sirve los archivos estáticos del frontend detrás del
        ALB.
      - Agregar: **ALB + AWS WAF** (entrada HTTPS, enruta `/api/*` y `/*`, reglas administradas y límite por IP),
        **Amazon S3** (documentos y fotos, privado, SSE-S3), **Amazon ECR** (imágenes), **Secrets Manager** (llaves y
        contraseña de la base), **CloudWatch Logs**.
      - Fila "Variables de entorno": agregar "los secretos vienen de Secrets Manager".
    - **Tabla 4 (Protocolos)**. Reemplazar por:
      | Origen | Destino | Protocolo |
      |---|---|---|
      | Navegador | ALB | HTTPS (443) |
      | ALB | nginx | HTTP (80, dentro de la VPC) |
      | ALB | Backend (2 tareas) | HTTP (8080, dentro de la VPC), API REST JSON |
      | Backend | RDS | JDBC sobre TCP (5432) |
      | Backend | S3 | HTTPS (SDK de AWS, rol LabRole) |
    - **Tabla 5 (Requisitos operativos)**:
      - *Disponibilidad:* agregar "El backend corre en dos réplicas; si una cae, la otra atiende. No guarda estado en
        memoria: sesiones, límites de intentos y archivos están en PostgreSQL y S3".
      - *Rendimiento:* quitar "CDN (CloudFront)". Poner "nginx sirve los estáticos sin pasar por el backend; dos
        réplicas reparten la CPU del login (bcrypt)".
      - *Escalabilidad:* reemplazar "un solo contenedor" por "servicio ECS con 2 tareas, escalable horizontalmente
        porque no tiene estado en memoria".
    - **Tabla 6 (Recursos)**:
      | Recurso | Especificación |
      |---|---|
      | CPU | Backend 1 vCPU por tarea (×2); nginx 0.25 vCPU; RDS `db.t4g.micro` (2 vCPU con ráfagas) |
      | RAM | Backend 2 GB por tarea (JVM al 75 %); nginx 0.5 GB; RDS 1 GB |
      | Almacenamiento | RDS gp2 20 GB cifrado; S3 para documentos (≤ 5 MB) y fotos (≤ 5 por espacio) |
      | Sistema operativo | Contenedores Linux en Fargate (imagen Eclipse Temurin 25 JRE y nginx Alpine) |
    - **Tabla 7 (Riesgos)**: concretar las mitigaciones. Caída del servidor → dos réplicas y health checks del ALB.
      Pérdida de datos → backups automáticos de RDS y S3. Acceso no autorizado → WAF, SG solo desde el ALB, IPs del
      admin y MFA. Saturación → límite por IP en el WAF y en la app. Agregar: *Agotar el presupuesto del lab* (Alto) →
      bajar las tareas a 0 y detener RDS fuera de las pruebas.
    - **Tabla 8 (Atributos)**: Disponibilidad → "2 réplicas detrás del ALB". Escalabilidad → "backend sin estado en
      memoria". Confiabilidad → "backups de RDS y logs en CloudWatch".
11. **6.10 Razonamiento arquitectónico** (§ p.3027–3038, hoy con texto de plantilla). Contar la evolución: R1 con un
    solo contenedor y diseño S3 + CloudFront → descubrimiento de las restricciones del Learner Lab → nginx detrás del
    ALB → 2 réplicas, para lo cual se sacó el estado de la memoria (HU-29, HU-30, HU-31). Las alternativas
    descartadas están en § 11 de `arquitectura-aws.md` (EFS, Redis, sesiones pegajosas, NAT, SES).

### Cap. 7 — Diseño

12. **7.7 Patrones** (§ p.3569): completar "AlmacenamientoArchivos" con "con AlmacenamientoLocal y AlmacenamientoS3
    (HU-29)".

### Cap. 9 y 10 — Implementación y pruebas

13. **9.4 Integración** (§ p.4223): actualizar las cifras. El último `./init.sh` completo (HU-31, 2026-11-15) dio
    **2228 pruebas unitarias y 1275 de integración**, sin fallas.
14. **10.1 Estrategia de pruebas**:
    - *Alcance* (§ p.4377): reemplazar "Las pruebas se ejecutan en el entorno local… Quedan fuera las pruebas de carga
      y rendimiento…" por: "Las pruebas funcionales se ejecutan en local; las de carga y rendimiento, con JMeter
      contra el entorno UAT en AWS. Queda fuera la integración real con la pasarela de pago, que se reemplaza por una
      pasarela simulada."
    - Agregar una fila a la tabla de niveles: **Carga y rendimiento** | p95 de login, búsqueda (2000 espacios) y ficha
      con 50 usuarios concurrentes (AC008, RNF-009/023/026/056) | Apache JMeter en modo sin interfaz contra UAT |
      Responsable de infraestructura.
    - *Datos y entorno* (§ p.4402): agregar "En UAT se siembran 2000 espacios y 50 cuentas de prueba".

### Cap. 11 — Impacto

15. **11.4 Ambiental** (§ p.4471): "monolito modular en un único contenedor, que consume solo la capacidad necesaria"
    → "monolito modular en contenedores pequeños (dos réplicas del backend que se apagan cuando no se usan), que
    consumen solo la capacidad necesaria".

## Fuera del .docx

- `6.9_Vista_despliegue.xlsx`: los mismos campos "[Completar]" de CPU, RAM, almacenamiento y SO (usar la Tabla 6 de
  arriba).
- `EspacioYa Documentacion Excel.xlsx`: los RNF de rendimiento dicen "meta propuesta". Cuando haya resultados de
  JMeter, cambiar a "meta verificada" o anotar el p95 obtenido.

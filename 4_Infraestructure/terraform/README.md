# Terraform de UAT (EspacioYa)

Crea en AWS todo lo que describe [`../arquitectura-aws.md`](../arquitectura-aws.md): VPC, ALB + WAF, ECS Fargate
(backend con 2 tareas y nginx con 1), RDS PostgreSQL 17, bucket S3 privado, ECR, Secrets Manager y CloudWatch Logs.
Las imágenes salen de [`2_Backend/Dockerfile`](../../2_Backend/Dockerfile) y
[`3_frontend/Dockerfile`](../../3_frontend/Dockerfile).

## Archivos

| Archivo | Qué crea |
|---|---|
| `versions.tf` | Proveedores y el seguro que impide aplicar en la cuenta equivocada |
| `variables.tf` | Todo lo configurable (tareas, HTTPS, WAF, IPs del admin…) |
| `red.tf` | VPC, 2 subredes públicas y 2 privadas, IGW, endpoint gateway de S3 y los 4 security groups |
| `alb.tf` | ALB, target groups, listeners (`/api/*` → backend, resto → nginx) y certificado autofirmado opcional |
| `waf.tf` | Web ACL: límite por IP + reglas administradas de AWS |
| `datos.tf` | RDS |
| `s3.tf` | Bucket S3 (por CloudFormation en AWS) |
| `secretos.tf` | Contraseñas y llaves generadas, en **un** secreto JSON |
| `ecs.tf` | ECR, logs, cluster, task definitions, servicios y la tarea única para crear el admin |
| `outputs.tf` | URL, repos ECR, bucket, endpoint de RDS y el comando para crear el admin |
| `entornos/*.tfvars.example` | Valores para el emulador y para el Learner Lab |
| `probar-emulador.ps1`, `emulador/Dockerfile` | Prueba local contra AWS Emulator |

Decisiones que no están en `arquitectura-aws.md`:

- **Los secretos los genera Terraform** (`random_password`, `random_bytes`) y quedan en el state. Si se pierde el
  `terraform.tfstate`, un nuevo apply genera llaves nuevas y lo cifrado con las anteriores ya no se puede leer. Guarda
  una copia del state fuera del repo.
- **Un solo secreto JSON** en Secrets Manager. La task definition lee cada clave con `ARN:CLAVE::`.
- **WAF:** la regla `SizeRestrictions_BODY` de `AWSManagedRulesCommonRuleSet` queda en modo *count*. Si bloqueara,
  rompería la subida de documentos y fotos (HU-05), porque bloquea cuerpos de más de 8 KB.
- **HTTPS:** `modo_https = "autofirmado"` por defecto (opción 2 de § 6). Con dominio propio usa `"acm"`.
- **Workspaces separados:** `emulador` y `learnerlab` tienen states distintos y nunca se pisan.
- **Bucket por CloudFormation** ([`s3.tf`](s3.tf)): la SCP del Learner Lab niega
  `s3:GetBucketObjectLockConfiguration`, y `aws_s3_bucket` lo lee siempre (falla con `AccessDenied` tras crearlo).
  En AWS el bucket lo crea una pila `espacioya-uat-bucket`; en el emulador, `aws_s3_bucket`. En `destroy`, el bucket
  se vacía antes con `aws s3 rm` (hace falta la AWS CLI).

## 1. Desplegar en el Learner Lab

Todos los comandos se corren desde la raíz del repo, en PowerShell. Deja las comillas de `"-var-file=..."`: sin ellas,
Windows PowerShell 5.1 parte el argumento en el punto y Terraform responde `Too many command line arguments`.
Necesitas Docker Desktop, AWS CLI v2 y
Terraform ≥ 1.9.

### 1.1 Credenciales (cada vez que inicias el lab)

El archivo de credenciales está en `C:\Users\<tu-usuario>\.aws\credentials` (en PowerShell,
`$env:USERPROFILE\.aws\credentials`). Es un archivo de texto sin extensión. El emulador **no** usa ese archivo: guarda
lo suyo en `.aws\emulator\9297\` y solo lo usa dentro de `probar-emulador.ps1`.

1. En AWS Academy: *Start Lab* y espera a que el círculo junto a *AWS* esté en verde.
2. *AWS Details* → junto a *AWS CLI*, botón *Show*. Aparece un bloque como este (los valores son tuyos):

   ```ini
   [default]
   aws_access_key_id=ASIA...
   aws_secret_access_key=...
   aws_session_token=IQoJb3JpZ2luX2Vj...(muy largo)
   ```

   Cópialo entero, desde `[default]` hasta el final del token.
3. (Solo la primera vez) guarda una copia del archivo actual, por si lo necesitas:

   ```bash
   Copy-Item $env:USERPROFILE\.aws\credentials $env:USERPROFILE\.aws\credentials.bak
   ```

4. Ábrelo en el Bloc de notas:

   ```bash
   notepad $env:USERPROFILE\.aws\credentials
   ```

   - Si ya tiene un bloque `[default]` (por ejemplo de un lab anterior), **borra ese bloque entero**: la línea
     `[default]` y sus tres líneas. No dejes dos `[default]`.
   - Si tiene otros perfiles (`[otro-nombre]`), déjalos como están.
   - Pega el bloque del paso 2, guarda (Ctrl+S) y cierra.
5. Comprueba la región. `$env:USERPROFILE\.aws\config` debe tener `region = us-east-1` bajo `[default]`.
6. Comprueba a qué cuenta apuntas:

   ```bash
   aws sts get-caller-identity
   ```

   Debe salir un `Account` de 12 dígitos (tu cuenta del lab) y un `Arn` con `assumed-role/voclabs/...`.
   - `000000000000`: la consola tiene variables del emulador. Ciérrala y abre una nueva.
   - `ExpiredToken` o `InvalidClientTokenId`: el bloque está viejo o mal pegado. Repite los pasos 2 y 4.

Las credenciales caducan a las ~4 horas, o antes si el lab se detiene. Cada vez que inicies el lab, repite los pasos
2, 4 y 6: el bloque cambia en cada sesión.

### 1.2 Configuración

1. Copia el archivo de variables (no se sube al repo):

   ```bash
   cp 4_Infraestructure/terraform/entornos/learnerlab.tfvars.example 4_Infraestructure/terraform/entornos/learnerlab.tfvars
   ```

2. Averigua tu IP pública:

   ```bash
   curl.exe ifconfig.me
   ```

3. En `learnerlab.tfvars`, pon en `admin_ips_permitidas` tu IP con `/32` (y las de quien más administre, separadas
   por coma), y en `admin_correo` el correo del admin. Deja `backend_tareas = 0` y `frontend_tareas = 0`: todavía no
   hay imágenes.

### 1.3 Crear la infraestructura

```bash
terraform -chdir=4_Infraestructure/terraform init
```

```bash
terraform -chdir=4_Infraestructure/terraform workspace select -or-create learnerlab
```

```bash
terraform -chdir=4_Infraestructure/terraform plan "-var-file=entornos/learnerlab.tfvars"
```

Revisa que el plan diga **63 to add** (61 con `waf_activo = false`) y que no haya errores. El emulador muestra 64
porque ahí también crea un rol `LabRole` falso. Luego:

```bash
terraform -chdir=4_Infraestructure/terraform apply "-var-file=entornos/learnerlab.tfvars"
```

Escribe `yes`. RDS tarda de 5 a 10 minutos; el resto, segundos. Si algo falla por permisos del lab
(`AccessDenied`, `not authorized`), anota el recurso y el mensaje: no reintentes a ciegas.

### 1.4 Subir las imágenes

1. Corre las pruebas antes, desde Git Bash (la imagen del backend no las corre):

   ```bash
   ./init.sh
   ```

2. Guarda las URLs de ECR en variables de la consola:

   ```bash
   $ecrBack = terraform -chdir=4_Infraestructure/terraform output -raw ecr_backend
   ```

   ```bash
   $ecrFront = terraform -chdir=4_Infraestructure/terraform output -raw ecr_frontend
   ```

3. Inicia sesión de Docker en ECR (el registro es la parte de la URL antes de la `/`):

   ```bash
   cmd /c "aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $($ecrBack.Split('/')[0])"
   ```

   Debe responder `Login Succeeded`. Va por `cmd` porque el `|` de Windows PowerShell 5.1 modifica la contraseña y
   ECR responde `400 Bad Request`. La sesión dura 12 horas.

4. Construye y sube:

   ```bash
   docker build -t "${ecrBack}:latest" 2_Backend
   ```

   ```bash
   docker build -t "${ecrFront}:latest" 3_frontend
   ```

   ```bash
   docker push "${ecrBack}:latest"
   ```

   ```bash
   docker push "${ecrFront}:latest"
   ```

### 1.5 Encender los servicios

1. En `learnerlab.tfvars`, cambia a `backend_tareas = 2` y `frontend_tareas = 1`.
2. Aplica:

   ```bash
   terraform -chdir=4_Infraestructure/terraform apply "-var-file=entornos/learnerlab.tfvars"
   ```

3. Espera unos 3 minutos y revisa que las tareas estén corriendo (`runningCount` 2 y 1):

   ```bash
   aws ecs describe-services --cluster espacioya-uat --services espacioya-backend espacioya-frontend --query "services[].[serviceName,runningCount,events[0].message]" --output table
   ```

4. Si el backend no llega a 2, mira su log (Flyway, llaves, conexión a RDS):

   ```bash
   aws logs tail /ecs/espacioya-backend --since 15m
   ```

### 1.6 Crear el admin y entrar

1. Corre la tarea única. Imprime el comando, cópialo y ejecútalo:

   ```bash
   terraform -chdir=4_Infraestructure/terraform output -raw crear_admin
   ```

   Termina en 1 o 2 minutos. En el log del backend debe salir `Cuenta admin creada`.
2. Saca la contraseña del admin del secreto (no la pegues en ningún lado público):

   ```bash
   (aws secretsmanager get-secret-value --secret-id espacioya-uat/backend --query SecretString --output text | ConvertFrom-Json).ADMIN_CONTRASENA
   ```

3. Abre la URL:

   ```bash
   terraform -chdir=4_Infraestructure/terraform output -raw url
   ```

   El certificado es autofirmado: el navegador muestra una advertencia. Acéptala (*Avanzado → Continuar*). Entra con
   el correo del admin, la contraseña del paso 2 y configura el MFA.

### 1.7 Subir una versión nueva

La etiqueta `latest` no cambia, así que ECS no se entera solo. Tras `docker build` + `docker push` de la imagen nueva:

```bash
aws ecs update-service --cluster espacioya-uat --service espacioya-backend --force-new-deployment
```

(igual con `espacioya-frontend`). El despliegue no corta el servicio: arranca las tareas nuevas antes de apagar las
viejas.

### 1.8 Apagar para cuidar el presupuesto

Al terminar cada sesión de pruebas:

1. Tareas en 0: `backend_tareas = 0` y `frontend_tareas = 0` en `learnerlab.tfvars`, y `apply`.
2. Detener RDS (AWS lo vuelve a encender solo a los 7 días):

   ```bash
   aws rds stop-db-instance --db-instance-identifier espacioya-uat
   ```

   Para volver a usarlo: `aws rds start-db-instance --db-instance-identifier espacioya-uat`, espera a que esté
   `available` y sube las tareas.

El ALB, el WAF y las IP públicas siguen cobrando aunque todo esté en 0 (~0.03 USD/h). Si no vas a usar el entorno
en días, bórralo:

```bash
terraform -chdir=4_Infraestructure/terraform destroy "-var-file=entornos/learnerlab.tfvars"
```

Con `eliminar_con_datos = true` (el valor por defecto) se borran también el bucket, las imágenes y la base, sin
snapshot.

## 2. Probar en AWS Emulator (opcional)

Sirve para revisar sintaxis y referencias sin gastar presupuesto. Requiere Docker. Si falta la imagen
`aws-emulator`, el script la construye desde [`emulador/Dockerfile`](emulador/Dockerfile) con el paquete `latest`.

```bash
powershell -File 4_Infraestructure/terraform/probar-emulador.ps1 plan
```

El script levanta el emulador en `127.0.0.1:9297` (telemetría apagada), comprueba que la cuenta sea `000000000000`
y usa el workspace `emulador`. Para actualizar el emulador:

```bash
docker build --pull --no-cache -t aws-emulator 4_Infraestructure/terraform/emulador
```

```bash
powershell -File 4_Infraestructure/terraform/probar-emulador.ps1 detener
```

**Límite conocido (emulador 1.0.385, 2026-11-15):** `plan` pasa con 64 recursos, pero `apply` falla en dos puntos
que son del emulador y no del código:

1. **Security groups:** el proveedor borra la regla de salida por defecto con `IpProtocol=-1` y puertos 0–0. AWS
   ignora los puertos con `-1`; el emulador responde `InvalidPermission.NotFound`.
2. **Bucket S3:** el proveedor lee las etiquetas con S3 Control en `000000000000.127.0.0.1`, un host que no existe.

Con un proxy local que corregía esas dos peticiones, el apply completo (64/64), un segundo `plan` sin cambios y el
`destroy` pasaron en el emulador. Ese proxy se quitó: el despliegue real se prueba en el Learner Lab.

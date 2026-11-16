# ECR, CloudWatch Logs, cluster ECS, task definitions y servicios Fargate.

# --- Rol de las tareas -------------------------------------------------------------------------
# En el Learner Lab no se crean roles: se usa LabRole. En el emulador LabRole no existe, así que
# lo creamos (solo con emulador = true).

resource "aws_iam_role" "lab_emulador" {
  count = var.emulador ? 1 : 0
  name  = var.nombre_rol_tareas

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

data "aws_iam_role" "tareas" {
  count = var.emulador ? 0 : 1
  name  = var.nombre_rol_tareas
}

locals {
  rol_tareas_arn = var.emulador ? aws_iam_role.lab_emulador[0].arn : data.aws_iam_role.tareas[0].arn
}

# --- ECR ---------------------------------------------------------------------------------------

resource "aws_ecr_repository" "app" {
  for_each             = toset(["backend", "frontend"])
  name                 = "${var.proyecto}-${each.key}"
  image_tag_mutability = "MUTABLE"
  force_delete         = var.eliminar_con_datos

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Guardar solo las 10 imágenes más recientes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# --- Logs --------------------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "app" {
  for_each          = toset(["backend", "frontend"])
  name              = "/ecs/${var.proyecto}-${each.key}"
  retention_in_days = 7
}

# --- Cluster -----------------------------------------------------------------------------------

resource "aws_ecs_cluster" "principal" {
  name = local.nombre

  setting {
    name  = "containerInsights"
    value = "disabled" # cuesta aparte; los logs de CloudWatch bastan para UAT
  }
}

# --- Backend -----------------------------------------------------------------------------------

locals {
  secreto_arn = aws_secretsmanager_secret.backend.arn

  backend_entorno = [
    { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.principal.address}:${aws_db_instance.principal.port}/${var.db_nombre}" },
    { name = "DB_USUARIO", value = var.db_usuario },
    { name = "FRONTEND_URL", value = local.url_publica },
    { name = "ALMACENAMIENTO_TIPO", value = "s3" },
    { name = "ALMACENAMIENTO_S3_BUCKET", value = aws_s3_bucket.archivos.bucket },
    { name = "ALMACENAMIENTO_S3_REGION", value = var.region },
    { name = "PROXIES_CONFIANZA", value = var.proxies_confianza },
    { name = "ADMIN_IPS_PERMITIDAS", value = var.admin_ips_permitidas },
    { name = "PAGOS_PASARELA", value = "simulada" },
    { name = "PAGOS_SIMULADA_FALLOS_REEMBOLSO", value = tostring(var.pagos_fallos_reembolso) },
    { name = "CORREO_PROVEEDOR", value = "simulado" },
    { name = "LIMITE_CATALOGO_MAXIMO", value = tostring(var.limite_catalogo_maximo) },
    { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=75" },
  ]

  backend_secretos = [
    for clave in ["DB_CONTRASENA", "CIFRADO_LLAVE", "CIFRADO_LLAVE_HUELLA", "ALMACENAMIENTO_LLAVE_FIRMA"] :
    { name = clave, valueFrom = "${local.secreto_arn}:${clave}::" }
  ]

  backend_logs = {
    logDriver = "awslogs"
    options = {
      awslogs-group         = aws_cloudwatch_log_group.app["backend"].name
      awslogs-region        = var.region
      awslogs-stream-prefix = "backend"
    }
  }
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.proyecto}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memoria
  execution_role_arn       = local.rol_tareas_arn
  task_role_arn            = local.rol_tareas_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name             = "backend"
    image            = "${aws_ecr_repository.app["backend"].repository_url}:${var.imagen_tag}"
    essential        = true
    portMappings     = [{ containerPort = 8080, protocol = "tcp" }]
    environment      = local.backend_entorno
    secrets          = local.backend_secretos
    logConfiguration = local.backend_logs
  }])

  depends_on = [aws_secretsmanager_secret_version.backend]
}

resource "aws_ecs_service" "backend" {
  name            = "${var.proyecto}-backend"
  cluster         = aws_ecs_cluster.principal.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_tareas
  launch_type     = "FARGATE"

  # Despliegue sin cortes: las tareas nuevas arrancan antes de apagar las viejas.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Spring Boot + Flyway tarda en arrancar; sin margen el ALB la mataría antes de estar lista.
  health_check_grace_period_seconds = 180

  network_configuration {
    subnets          = aws_subnet.publica[*].id
    security_groups  = [aws_security_group.backend.id]
    assign_public_ip = true # sin NAT: así baja la imagen de ECR y habla con Secrets Manager
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener_rule.api]
}

# --- Frontend (nginx) --------------------------------------------------------------------------

resource "aws_ecs_task_definition" "frontend" {
  family                   = "${var.proyecto}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.frontend_cpu
  memory                   = var.frontend_memoria
  execution_role_arn       = local.rol_tareas_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "frontend"
    image        = "${aws_ecr_repository.app["frontend"].repository_url}:${var.imagen_tag}"
    essential    = true
    portMappings = [{ containerPort = 80, protocol = "tcp" }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app["frontend"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "frontend"
      }
    }
  }])
}

resource "aws_ecs_service" "frontend" {
  name            = "${var.proyecto}-frontend"
  cluster         = aws_ecs_cluster.principal.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = var.frontend_tareas
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  health_check_grace_period_seconds = 30

  network_configuration {
    subnets          = aws_subnet.publica[*].id
    security_groups  = [aws_security_group.frontend.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.frontend.arn
    container_name   = "frontend"
    container_port   = 80
  }

  depends_on = [aws_lb_listener.principal]
}

# --- Tarea única para crear el admin (README, "Cuenta de administrador") -----------------------
# Misma imagen con ADMIN_CREAR=true: crea la cuenta y la aplicación se cierra (CreadorAdmin).
# Solo existe si admin_correo no está vacío. Se lanza con el comando de la salida "crear_admin".

resource "aws_ecs_task_definition" "crear_admin" {
  count                    = var.admin_correo != "" ? 1 : 0
  family                   = "${var.proyecto}-crear-admin"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memoria
  execution_role_arn       = local.rol_tareas_arn
  task_role_arn            = local.rol_tareas_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "crear-admin"
    image     = "${aws_ecr_repository.app["backend"].repository_url}:${var.imagen_tag}"
    essential = true
    environment = concat(local.backend_entorno, [
      { name = "ADMIN_CREAR", value = "true" },
      { name = "ADMIN_CORREO", value = var.admin_correo },
    ])
    secrets = concat(local.backend_secretos, [
      { name = "ADMIN_CONTRASENA", valueFrom = "${local.secreto_arn}:ADMIN_CONTRASENA::" },
    ])
    logConfiguration = local.backend_logs
  }])

  depends_on = [aws_secretsmanager_secret_version.backend]
}

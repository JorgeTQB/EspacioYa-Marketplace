# --- Entorno -----------------------------------------------------------------------------------

variable "region" {
  description = "Región de AWS. El Learner Lab solo permite us-east-1 y us-west-2."
  type        = string
  default     = "us-east-1"
}

variable "proyecto" {
  description = "Prefijo de los nombres de los recursos."
  type        = string
  default     = "espacioya"
}

variable "entorno" {
  description = "Nombre del entorno (va en etiquetas y nombres)."
  type        = string
  default     = "uat"
}

variable "emulador" {
  description = "true al probar contra AWS Emulator: S3 con estilo de ruta y se crea un rol LabRole falso."
  type        = bool
  default     = false
}

variable "nombre_rol_tareas" {
  description = "Rol IAM que usan las tareas de ECS (task role y execution role). En el Learner Lab solo existe LabRole."
  type        = string
  default     = "LabRole"
}

variable "eliminar_con_datos" {
  description = "Permite que terraform destroy borre el bucket con archivos, los repos ECR con imágenes y la base sin snapshot final. Pensado para UAT."
  type        = bool
  default     = true
}

# --- Red ---------------------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR de la VPC. Si cambia, cambia también proxies_confianza."
  type        = string
  default     = "10.0.0.0/16"
}

variable "proxies_confianza" {
  description = "Expresión regular de las IPs del ALB (PROXIES_CONFIANZA). Debe cubrir vpc_cidr."
  type        = string
  default     = "10\\.0\\.\\d{1,3}\\.\\d{1,3}"
}

# --- HTTPS -------------------------------------------------------------------------------------

variable "modo_https" {
  description = <<-EOT
    ninguno: solo HTTP con el DNS del ALB (no cumple RNF-SEG-13).
    autofirmado: Terraform genera un certificado y lo importa en ACM (el navegador advierte).
    acm: certificado ya emitido en ACM para tu dominio (certificado_arn + dominio).
  EOT
  type        = string
  default     = "autofirmado"

  validation {
    condition     = contains(["ninguno", "autofirmado", "acm"], var.modo_https)
    error_message = "modo_https debe ser ninguno, autofirmado o acm."
  }
}

variable "certificado_arn" {
  description = "ARN del certificado de ACM (solo con modo_https = acm)."
  type        = string
  default     = ""
}

variable "dominio" {
  description = "Dominio propio que apunta al ALB (CNAME). Vacío = se usa el DNS del ALB."
  type        = string
  default     = ""
}

# --- WAF ---------------------------------------------------------------------------------------

variable "waf_activo" {
  description = "Asocia una Web ACL de WAF al ALB."
  type        = bool
  default     = true
}

variable "waf_limite_por_ip" {
  description = "Peticiones por IP en 5 minutos antes de bloquear. Súbelo durante las pruebas de carga (JMeter sale de una sola IP)."
  type        = number
  default     = 1000
}

# --- ECS ---------------------------------------------------------------------------------------

variable "imagen_tag" {
  description = "Etiqueta de las imágenes en ECR (se recomienda el hash del commit)."
  type        = string
  default     = "latest"
}

variable "backend_tareas" {
  description = "Tareas del backend. 2 en pruebas y demo; 0 para apagar y no gastar presupuesto."
  type        = number
  default     = 2
}

variable "frontend_tareas" {
  description = "Tareas de nginx. 1 normalmente; 0 para apagar."
  type        = number
  default     = 1
}

variable "backend_cpu" {
  type    = number
  default = 1024
}

variable "backend_memoria" {
  type    = number
  default = 2048
}

variable "frontend_cpu" {
  type    = number
  default = 256
}

variable "frontend_memoria" {
  type    = number
  default = 512
}

# --- Base de datos -----------------------------------------------------------------------------

variable "db_clase" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_almacenamiento_gb" {
  type    = number
  default = 20
}

variable "db_version" {
  description = "Versión mayor de PostgreSQL."
  type        = string
  default     = "17"
}

variable "db_nombre" {
  type    = string
  default = "espacioya"
}

variable "db_usuario" {
  type    = string
  default = "espacioya"
}

variable "db_dias_backup" {
  description = "Días de backups automáticos (1 a 7)."
  type        = number
  default     = 1
}

# --- Aplicación --------------------------------------------------------------------------------

variable "admin_ips_permitidas" {
  description = "IPs o CIDR (separados por coma) desde los que se entra al panel admin. Vacío = nadie."
  type        = string
  default     = ""
}

variable "admin_correo" {
  description = "Correo del admin inicial. Si no está vacío se crea una task definition de un solo uso para crearlo."
  type        = string
  default     = ""
  # No se marca sensitive: va en texto plano en la task definition y decide un count.
}

variable "limite_catalogo_maximo" {
  description = "LIMITE_CATALOGO_MAXIMO. Súbelo solo durante las pruebas de carga."
  type        = number
  default     = 120
}

variable "pagos_fallos_reembolso" {
  description = "PAGOS_SIMULADA_FALLOS_REEMBOLSO (demo de HU-16/HU-31)."
  type        = number
  default     = 0
}

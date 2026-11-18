# Secretos del backend, generados por Terraform y guardados en UN secreto JSON de Secrets Manager
# (0.40 USD al mes en vez de 2 USD por cinco secretos). La task definition los lee por clave.
#
# OJO: los valores también quedan en el terraform.tfstate. Si pierdes el state y vuelves a aplicar,
# se generan llaves nuevas y lo cifrado con las anteriores deja de leerse.

resource "random_password" "db" {
  length = 32
  # RDS no acepta / @ " ni espacios en la contraseña maestra.
  override_special = "!#$%^&*()-_=+[]{}<>:?"
}

resource "random_bytes" "cifrado_llave" {
  length = 32
}

resource "random_bytes" "cifrado_llave_huella" {
  length = 32
}

resource "random_bytes" "almacenamiento_llave_firma" {
  length = 32
}

resource "random_password" "admin" {
  length      = 24
  min_upper   = 2
  min_lower   = 2
  min_numeric = 2
  min_special = 2
}

resource "aws_secretsmanager_secret" "backend" {
  name        = "${local.nombre}/backend"
  description = "Contraseña de RDS y llaves de cifrado y firma del backend de EspacioYa"

  # 0 = se borra al instante en terraform destroy, para poder recrear el entorno con el mismo nombre.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "backend" {
  secret_id = aws_secretsmanager_secret.backend.id
  secret_string = jsonencode({
    DB_CONTRASENA              = random_password.db.result
    CIFRADO_LLAVE              = random_bytes.cifrado_llave.base64
    CIFRADO_LLAVE_HUELLA       = random_bytes.cifrado_llave_huella.base64
    ALMACENAMIENTO_LLAVE_FIRMA = random_bytes.almacenamiento_llave_firma.base64
    ADMIN_CONTRASENA           = random_password.admin.result
  })
}

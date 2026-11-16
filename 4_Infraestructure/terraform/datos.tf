# RDS PostgreSQL y bucket S3 privado.

resource "aws_db_subnet_group" "principal" {
  name       = local.nombre
  subnet_ids = aws_subnet.privada[*].id
  tags       = { Name = local.nombre }
}

resource "aws_db_instance" "principal" {
  identifier     = local.nombre
  engine         = "postgres"
  engine_version = var.db_version
  instance_class = var.db_clase

  allocated_storage = var.db_almacenamiento_gb
  storage_type      = "gp2" # el Learner Lab no permite gp3/io1
  storage_encrypted = true  # llave administrada aws/rds (KMS)

  db_name  = var.db_nombre
  username = var.db_usuario
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.principal.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = var.db_dias_backup
  skip_final_snapshot       = var.eliminar_con_datos
  final_snapshot_identifier = var.eliminar_con_datos ? null : "${local.nombre}-final"
  deletion_protection       = false
  apply_immediately         = true

  # El Learner Lab no permite Enhanced Monitoring ni Performance Insights de pago.
  monitoring_interval          = 0
  performance_insights_enabled = false
  auto_minor_version_upgrade   = true

  tags = { Name = local.nombre }
}

# --- S3 ----------------------------------------------------------------------------------------

resource "random_id" "bucket" {
  byte_length = 4
}

resource "aws_s3_bucket" "archivos" {
  # Los nombres de bucket son globales: el sufijo evita choques con otros equipos.
  bucket        = "${local.nombre}-archivos-${random_id.bucket.hex}"
  force_destroy = var.eliminar_con_datos
}

resource "aws_s3_bucket_public_access_block" "archivos" {
  bucket                  = aws_s3_bucket.archivos.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "archivos" {
  bucket = aws_s3_bucket.archivos.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "archivos" {
  bucket = aws_s3_bucket.archivos.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256" # SSE-S3; los documentos además van cifrados por la app (AES-256-GCM)
    }
  }
}

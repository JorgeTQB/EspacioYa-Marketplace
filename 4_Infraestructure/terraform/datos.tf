# RDS PostgreSQL. El bucket S3 está en s3.tf.

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


output "url" {
  description = "URL pública de la aplicación."
  value       = local.url_publica
}

output "alb_dns" {
  description = "DNS del ALB (el CNAME de tu dominio apunta aquí)."
  value       = aws_lb.principal.dns_name
}

output "ecr_backend" {
  value = aws_ecr_repository.app["backend"].repository_url
}

output "ecr_frontend" {
  value = aws_ecr_repository.app["frontend"].repository_url
}

output "bucket_archivos" {
  value = aws_s3_bucket.archivos.bucket
}

output "rds_endpoint" {
  value = aws_db_instance.principal.address
}

output "secreto_backend_arn" {
  description = "Secreto JSON con la contraseña de RDS, las llaves y la contraseña del admin."
  value       = aws_secretsmanager_secret.backend.arn
}

output "cluster" {
  value = aws_ecs_cluster.principal.name
}

output "crear_admin" {
  description = "Comando para crear el admin (una sola vez, con las imágenes ya subidas)."
  value = var.admin_correo == "" ? "admin_correo vacío: no se creó la tarea" : join(" ", [
    "aws ecs run-task --cluster ${aws_ecs_cluster.principal.name}",
    "--launch-type FARGATE --task-definition ${aws_ecs_task_definition.crear_admin[0].family}",
    "--network-configuration \"awsvpcConfiguration={subnets=[${aws_subnet.publica[0].id}],securityGroups=[${aws_security_group.backend.id}],assignPublicIp=ENABLED}\"",
  ])
}

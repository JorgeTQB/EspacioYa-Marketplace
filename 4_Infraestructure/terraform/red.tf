# VPC con 2 subredes públicas (ALB y tareas Fargate) y 2 privadas (RDS) en 2 AZ.
# Sin NAT Gateway (arquitectura-aws.md § 4): las tareas salen a Internet con IP pública y
# el SG solo deja entrar tráfico del ALB.

data "aws_availability_zones" "disponibles" {
  state = "available"
}

locals {
  nombre = "${var.proyecto}-${var.entorno}"
  azs    = slice(data.aws_availability_zones.disponibles.names, 0, 2)
}

resource "aws_vpc" "principal" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.nombre }
}

resource "aws_internet_gateway" "principal" {
  vpc_id = aws_vpc.principal.id
  tags   = { Name = local.nombre }
}

resource "aws_subnet" "publica" {
  count                   = 2
  vpc_id                  = aws_vpc.principal.id
  availability_zone       = local.azs[count.index]
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index) # 10.0.0.0/24, 10.0.1.0/24
  map_public_ip_on_launch = false                                    # la IP la pide el servicio ECS

  tags = { Name = "${local.nombre}-publica-${count.index + 1}" }
}

resource "aws_subnet" "privada" {
  count             = 2
  vpc_id            = aws_vpc.principal.id
  availability_zone = local.azs[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10) # 10.0.10.0/24, 10.0.11.0/24

  tags = { Name = "${local.nombre}-privada-${count.index + 1}" }
}

resource "aws_route_table" "publica" {
  vpc_id = aws_vpc.principal.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.principal.id
  }

  tags = { Name = "${local.nombre}-publica" }
}

resource "aws_route_table_association" "publica" {
  count          = 2
  subnet_id      = aws_subnet.publica[count.index].id
  route_table_id = aws_route_table.publica.id
}

# Las subredes privadas no tienen salida a Internet: solo la usa RDS.
resource "aws_route_table" "privada" {
  vpc_id = aws_vpc.principal.id
  tags   = { Name = "${local.nombre}-privada" }
}

resource "aws_route_table_association" "privada" {
  count          = 2
  subnet_id      = aws_subnet.privada[count.index].id
  route_table_id = aws_route_table.privada.id
}

# Endpoint gateway de S3 (gratis): el tráfico del backend al bucket no sale por Internet.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.principal.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.publica.id]

  tags = { Name = "${local.nombre}-s3" }
}

# --- Security groups ---------------------------------------------------------------------------

resource "aws_security_group" "alb" {
  name        = "${local.nombre}-alb"
  description = "ALB: HTTP y HTTPS desde Internet"
  vpc_id      = aws_vpc.principal.id
  tags        = { Name = "${local.nombre}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  count             = local.https ? 1 : 0
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_a_vpc" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = var.vpc_cidr
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 8080
}

resource "aws_security_group" "backend" {
  name        = "${local.nombre}-backend"
  description = "Tareas del backend: 8080 solo desde el ALB"
  vpc_id      = aws_vpc.principal.id
  tags        = { Name = "${local.nombre}-backend" }
}

resource "aws_vpc_security_group_ingress_rule" "backend_desde_alb" {
  security_group_id            = aws_security_group.backend.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# Salida abierta: ECR, S3, Secrets Manager y CloudWatch Logs por HTTPS, y RDS por 5432.
resource "aws_vpc_security_group_egress_rule" "backend_salida" {
  security_group_id = aws_security_group.backend.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "frontend" {
  name        = "${local.nombre}-frontend"
  description = "Tareas de nginx: 80 solo desde el ALB"
  vpc_id      = aws_vpc.principal.id
  tags        = { Name = "${local.nombre}-frontend" }
}

resource "aws_vpc_security_group_ingress_rule" "frontend_desde_alb" {
  security_group_id            = aws_security_group.frontend.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 80
  to_port                      = 80
}

resource "aws_vpc_security_group_egress_rule" "frontend_salida" {
  security_group_id = aws_security_group.frontend.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "rds" {
  name        = "${local.nombre}-rds"
  description = "PostgreSQL: 5432 solo desde el backend"
  vpc_id      = aws_vpc.principal.id
  tags        = { Name = "${local.nombre}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_desde_backend" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.backend.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

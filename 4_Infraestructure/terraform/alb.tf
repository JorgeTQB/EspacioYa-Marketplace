# ALB internet-facing: /api/* va al backend y el resto a nginx (mismo origen, sin CORS).

locals {
  https = var.modo_https != "ninguno"

  certificado_arn = (
    var.modo_https == "acm" ? var.certificado_arn :
    var.modo_https == "autofirmado" ? aws_acm_certificate.autofirmado[0].arn :
    null
  )

  host_publico = var.dominio != "" ? var.dominio : aws_lb.principal.dns_name
  url_publica  = "${local.https ? "https" : "http"}://${local.host_publico}"
}

resource "aws_lb" "principal" {
  name               = local.nombre
  load_balancer_type = "application"
  internal           = false
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.publica[*].id

  drop_invalid_header_fields = true
  idle_timeout               = 60
}

resource "aws_lb_target_group" "backend" {
  name                 = "${local.nombre}-backend"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.principal.id
  deregistration_delay = 30

  # Ruta pública y barata. No hay Actuator, así que no comprueba la base.
  health_check {
    path                = "/api/politicas-privacidad/vigente"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_target_group" "frontend" {
  name                 = "${local.nombre}-frontend"
  port                 = 80
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.principal.id
  deregistration_delay = 15

  health_check {
    path                = "/"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# Listener principal: 443 con HTTPS, o 80 si modo_https = ninguno.
resource "aws_lb_listener" "principal" {
  load_balancer_arn = aws_lb.principal.arn
  port              = local.https ? 443 : 80
  protocol          = local.https ? "HTTPS" : "HTTP"
  certificate_arn   = local.certificado_arn
  ssl_policy        = local.https ? "ELBSecurityPolicy-TLS13-1-2-2021-06" : null

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.principal.arn
  priority     = 10

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

# Con HTTPS, el 80 solo redirige a 443.
resource "aws_lb_listener" "redireccion" {
  count             = local.https ? 1 : 0
  load_balancer_arn = aws_lb.principal.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      protocol    = "HTTPS"
      port        = "443"
      status_code = "HTTP_301"
    }
  }
}

# --- Certificado autofirmado (opción 2 de arquitectura-aws.md § 6) ------------------------------
# Cifra el tráfico, pero el navegador muestra una advertencia. Sirve para UAT sin dominio propio.

resource "tls_private_key" "autofirmado" {
  count     = var.modo_https == "autofirmado" ? 1 : 0
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "autofirmado" {
  count           = var.modo_https == "autofirmado" ? 1 : 0
  private_key_pem = tls_private_key.autofirmado[0].private_key_pem

  subject {
    common_name  = local.host_publico
    organization = "EspacioYa UAT"
  }

  dns_names             = [local.host_publico]
  validity_period_hours = 24 * 365
  allowed_uses          = ["key_encipherment", "digital_signature", "server_auth"]
}

resource "aws_acm_certificate" "autofirmado" {
  count            = var.modo_https == "autofirmado" ? 1 : 0
  private_key      = tls_private_key.autofirmado[0].private_key_pem
  certificate_body = tls_self_signed_cert.autofirmado[0].cert_pem

  lifecycle {
    create_before_destroy = true
  }
}

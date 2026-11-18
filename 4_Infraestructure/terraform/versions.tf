terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # State local a propósito: el Learner Lab no deja crear un backend remoto útil entre sesiones
  # y el state guarda contraseñas y llaves en texto plano. *.tfstate está en .gitignore.
  # Respáldalo fuera del repo (no se puede regenerar: perderlo deja recursos huérfanos).
}

provider "aws" {
  region = var.region

  # Solo para el emulador: el endpoint es 127.0.0.1, así que S3 no puede usar subdominios.
  s3_use_path_style = var.emulador

  default_tags {
    tags = {
      Proyecto    = var.proyecto
      Entorno     = var.entorno
      Gestion     = "terraform"
      Repositorio = "4_Infraestructure/terraform"
    }
  }
}

# Seguro contra confundir entornos: con emulador = true la cuenta tiene que ser la del emulador
# (000000000000), y sin él no puede serlo. Falla en el plan, antes de crear nada.
data "aws_caller_identity" "actual" {}

resource "terraform_data" "cuenta_correcta" {
  lifecycle {
    precondition {
      condition     = var.emulador == (data.aws_caller_identity.actual.account_id == "000000000000")
      error_message = "La cuenta ${data.aws_caller_identity.actual.account_id} no coincide con emulador = ${var.emulador}. Revisa las credenciales y el workspace."
    }
  }
}

# Bucket S3 privado para documentos y fotos (HU-29).
#
# Por qué CloudFormation: la SCP del Learner Lab niega s3:GetBucketObjectLockConfiguration, y el proveedor
# aws de Terraform lee esa configuración cada vez que lee un aws_s3_bucket (falla con AccessDenied justo
# después de crearlo). Con una pila de CloudFormation, Terraform solo lee la pila, nunca el bucket.
# Probado en el Learner Lab el 2026-11-15: la pila crea el bucket con las tres configuraciones de abajo.
#
# En el emulador no hay SCP y CloudFormation no hace falta: ahí se usa aws_s3_bucket normal.

resource "random_id" "bucket" {
  byte_length = 4
}

locals {
  # Los nombres de bucket son globales: el sufijo evita choques con otros equipos.
  bucket_nombre_deseado = "${local.nombre}-archivos-${random_id.bucket.hex}"

  bucket_nombre = (var.emulador
    ? aws_s3_bucket.archivos[0].bucket
    : aws_cloudformation_stack.bucket[0].outputs["Nombre"]
  )
}

# --- Learner Lab / AWS real: pila de CloudFormation --------------------------------------------

resource "aws_cloudformation_stack" "bucket" {
  count = var.emulador ? 0 : 1
  name  = "${local.nombre}-bucket"

  parameters = {
    Nombre = local.bucket_nombre_deseado
  }

  template_body = jsonencode({
    AWSTemplateFormatVersion = "2010-09-09"
    Description              = "Bucket privado de archivos de EspacioYa (gestionado por Terraform)"
    Parameters = {
      Nombre = { Type = "String" }
    }
    Resources = {
      Archivos = {
        Type = "AWS::S3::Bucket"
        # Con eliminar_con_datos = false, el bucket y sus archivos se quedan al borrar la pila.
        DeletionPolicy      = var.eliminar_con_datos ? "Delete" : "Retain"
        UpdateReplacePolicy = "Retain"
        Properties = {
          BucketName = { Ref = "Nombre" }
          PublicAccessBlockConfiguration = {
            BlockPublicAcls       = true
            BlockPublicPolicy     = true
            IgnorePublicAcls      = true
            RestrictPublicBuckets = true
          }
          OwnershipControls = {
            Rules = [{ ObjectOwnership = "BucketOwnerEnforced" }]
          }
          BucketEncryption = {
            ServerSideEncryptionConfiguration = [{
              # SSE-S3; los documentos además van cifrados por la app (AES-256-GCM)
              ServerSideEncryptionByDefault = { SSEAlgorithm = "AES256" }
            }]
          }
        }
      }
    }
    Outputs = {
      Nombre = { Value = { Ref = "Archivos" } }
    }
  })
}

# CloudFormation no puede borrar un bucket con archivos. Con eliminar_con_datos = true, antes de borrar la
# pila se vacía el bucket con la AWS CLI (equivale al force_destroy de aws_s3_bucket). Necesita la CLI
# con las mismas credenciales con las que corres terraform.
resource "terraform_data" "vaciar_bucket" {
  count = !var.emulador && var.eliminar_con_datos ? 1 : 0
  input = aws_cloudformation_stack.bucket[0].outputs["Nombre"]

  provisioner "local-exec" {
    when    = destroy
    command = "aws s3 rm s3://${self.input} --recursive"
  }
}

# --- Emulador: aws_s3_bucket normal ------------------------------------------------------------

resource "aws_s3_bucket" "archivos" {
  count         = var.emulador ? 1 : 0
  bucket        = local.bucket_nombre_deseado
  force_destroy = var.eliminar_con_datos
}

resource "aws_s3_bucket_public_access_block" "archivos" {
  count                   = var.emulador ? 1 : 0
  bucket                  = aws_s3_bucket.archivos[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "archivos" {
  count  = var.emulador ? 1 : 0
  bucket = aws_s3_bucket.archivos[0].id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "archivos" {
  count  = var.emulador ? 1 : 0
  bucket = aws_s3_bucket.archivos[0].id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

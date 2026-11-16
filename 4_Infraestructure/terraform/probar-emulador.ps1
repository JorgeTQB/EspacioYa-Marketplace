<#
.SYNOPSIS
  Corre Terraform contra AWS Emulator (Docker, 127.0.0.1:9297) en el workspace "emulador".

.EXAMPLE
  powershell -File 4_Infraestructure/terraform/probar-emulador.ps1 plan
  powershell -File 4_Infraestructure/terraform/probar-emulador.ps1 apply -auto-approve
  powershell -File 4_Infraestructure/terraform/probar-emulador.ps1 detener

  Las variables de entorno solo viven en este proceso: tu consola sigue apuntando a donde apuntaba.
  Si falta la imagen "aws-emulator", se construye desde emulador/Dockerfile (paquete latest).
  Con el emulador 1.0.385, apply falla en los security groups y en el bucket (README.md, "Probar en AWS Emulator").
#>
param(
  [Parameter(Position = 0)][string]$Accion = "plan",
  [Parameter(ValueFromRemainingArguments = $true)][string[]]$Extra
)

$ErrorActionPreference = "Stop"
$puerto = 9297
$dir    = $PSScriptRoot
$carpetaEmulador = Join-Path $env:USERPROFILE ".aws\emulator\$puerto"
$ca     = Join-Path $carpetaEmulador "ca.pem"

if ($Accion -eq "detener") {
  docker stop aws-emulator | Out-Null
  Write-Host "Emulador detenido (su estado se pierde)."
  exit 0
}

# 1. Imagen del emulador.
if (-not (docker images -q aws-emulator)) {
  Write-Host "Construyendo la imagen aws-emulator ..."
  docker build -t aws-emulator (Join-Path $dir "emulador")
  if ($LASTEXITCODE -ne 0) { throw "No se pudo construir aws-emulator." }
}

# 2. Emulador arriba, en loopback y con telemetría apagada.
if (-not (docker ps -q --filter "name=^aws-emulator$")) {
  Write-Host "Iniciando AWS Emulator en 127.0.0.1:$puerto ..."
  New-Item -ItemType Directory -Force $carpetaEmulador | Out-Null
  if (-not (docker network ls -q --filter "name=^aws-emulator$")) { docker network create aws-emulator | Out-Null }
  docker run -d --rm --name aws-emulator --network aws-emulator `
    --cap-drop ALL --security-opt no-new-privileges --read-only `
    -e EMULATOR_TELEMETRY=off `
    -p "127.0.0.1:${puerto}:${puerto}" `
    -v "${carpetaEmulador}:/home/emulator/.aws/emulator/$puerto" `
    aws-emulator | Out-Null

  # El emulador recién iniciado está vacío: el state viejo del workspace ya no describe nada real.
  $stateViejo = Join-Path $dir "terraform.tfstate.d\emulador\terraform.tfstate"
  if (Test-Path $stateViejo) {
    Write-Host "Emulador nuevo: se descarta el state anterior del workspace emulador."
    Remove-Item $stateViejo, "$stateViejo.backup" -ErrorAction SilentlyContinue
  }
}

$listo = $false
for ($i = 0; $i -lt 30; $i++) {
  if (Test-Path $ca) {
    $salud = curl.exe -sf --cacert $ca "https://127.0.0.1:$puerto/__admin/health" 2> $null
    if ($LASTEXITCODE -eq 0) { $listo = $true; break }
  }
  Start-Sleep -Seconds 1
}
if (-not $listo) { throw "El emulador no respondió en 30 s (docker logs aws-emulator)." }

# 3. Variables solo para este proceso; nunca credenciales reales.
Remove-Item Env:AWS_ACCESS_KEY_ID, Env:AWS_SECRET_ACCESS_KEY, Env:AWS_SESSION_TOKEN, Env:AWS_PROFILE,
  Env:AWS_DEFAULT_PROFILE, Env:AWS_WEB_IDENTITY_TOKEN_FILE, Env:AWS_ROLE_ARN, Env:AWS_ROLE_SESSION_NAME,
  Env:AWS_CONTAINER_CREDENTIALS_RELATIVE_URI, Env:AWS_CONTAINER_CREDENTIALS_FULL_URI,
  Env:AWS_CONTAINER_AUTHORIZATION_TOKEN -ErrorAction SilentlyContinue
$env:AWS_ENDPOINT_URL            = "https://127.0.0.1:$puerto"
$env:AWS_SHARED_CREDENTIALS_FILE = Join-Path $carpetaEmulador "emulator_credentials"
$env:AWS_CONFIG_FILE             = Join-Path $carpetaEmulador "config"
$env:AWS_CA_BUNDLE               = $ca
$env:AWS_EC2_METADATA_DISABLED   = "true"
$env:AWS_REGION                  = "us-east-1"
$env:AWS_DEFAULT_REGION          = "us-east-1"
$env:AWS_PAGER                   = ""

# 4. Confirmar que hablamos con el emulador y no con AWS.
$cuenta = aws sts get-caller-identity --query Account --output text
if ($cuenta -ne "000000000000") { throw "La cuenta es '$cuenta', no la del emulador. No se ejecuta Terraform." }

# 5. Terraform en su propio workspace (el de Learner Lab queda intacto).
terraform -chdir="$dir" init -input=false | Out-Null
if ($LASTEXITCODE -ne 0) { throw "terraform init falló." }
terraform -chdir="$dir" workspace select -or-create emulador
terraform -chdir="$dir" $Accion -var-file="entornos/emulador.tfvars.example" @Extra
exit $LASTEXITCODE

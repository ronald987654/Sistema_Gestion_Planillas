param(
    [ValidateSet('msi', 'exe', 'app-image')]
    [string]$Tipo = 'msi'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$version = '0.1.0'
$uuidActualizacion = '6f3e2a71-5c1b-4d8e-9a4f-2b7c0d9e8a15'

if (-not $env:JAVA_HOME) {
    throw 'Defina JAVA_HOME con la carpeta de un JDK 21 (por ejemplo, el que usa IntelliJ IDEA).'
}
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) {
    throw "No se encontró jpackage en $jpackage. Use un JDK 21 completo (no un JRE)."
}
if ($Tipo -ne 'app-image' -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    throw ('Para crear el instalador .' + $Tipo + ' se necesita WiX Toolset 3.x (candle.exe y light.exe en el PATH). ' +
        'Instálelo desde https://wixtoolset.org/ o use -Tipo app-image.')
}

Write-Host 'Compilando y copiando dependencias (mvnw -Pinstalador package)...'
& .\mvnw.cmd -B -q -Pinstalador clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación con Maven.' }

$destino = 'target\dist'
if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }

$argumentos = @(
    '--type', $Tipo,
    '--name', 'Planilla Perú',
    '--app-version', $version,
    '--description', 'Planilla de remuneraciones para empresas peruanas',
    '--vendor', 'Planilla Perú',
    '--icon', 'src\main\packaging\planilla-peru.ico',
    '--input', 'target\instalador',
    '--main-jar', 'planilla-peru.jar',
    '--main-class', 'com.planillaperu.RonaldmichaLauncher',
    '--add-modules', 'java.se,jdk.unsupported,jdk.localedata,jdk.crypto.ec,jdk.charsets,jdk.zipfs',
    '--java-options', '-Xmx512m',
    '--dest', $destino
)
if ($Tipo -ne 'app-image') {
    $argumentos += @(
        '--win-menu', '--win-menu-group', 'Planilla Perú',
        '--win-shortcut', '--win-shortcut-prompt',
        '--win-dir-chooser',
        '--win-upgrade-uuid', $uuidActualizacion
    )
}

Write-Host "Ejecutando jpackage ($Tipo)..."
& $jpackage @argumentos
if ($LASTEXITCODE -ne 0) { throw 'Falló jpackage.' }
Write-Host "Listo: $((Resolve-Path $destino).Path)"
Get-ChildItem $destino

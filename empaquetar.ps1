param(
    [ValidateSet('exe', 'msi', 'app-image')]
    [string]$Tipo = 'exe'
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$nombre = 'Planilla Peru'
$version = '1.0.0'
$uuidActualizacion = '6f3e2a71-5c1b-4d8e-9a4f-2b7c0d9e8a15'

if (-not $env:JAVA_HOME) {
    throw 'Defina JAVA_HOME con la carpeta de un JDK 21 (por ejemplo, el que usa IntelliJ IDEA).'
}
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) {
    throw "No se encontró jpackage en $jpackage. Use un JDK 21 completo (no un JRE)."
}
if ($Tipo -ne 'app-image' -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    $candidatos = @('D:\wix314-binaries')
    $candidatos += Get-PSDrive -PSProvider FileSystem | ForEach-Object {
        Get-ChildItem $_.Root -Filter 'wix3*' -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
    }
    $candidatos += Join-Path $env:USERPROFILE '.wix314'
    $portable = $candidatos | Where-Object { Test-Path (Join-Path $_ 'candle.exe') } | Select-Object -First 1
    $wix = Get-ChildItem 'C:\Program Files (x86)' -Filter 'WiX Toolset v3*' -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($portable) {
        Write-Host "Usando WiX de $portable"
        $env:PATH = $portable + ';' + $env:PATH
    } elseif ($wix) {
        $env:PATH = (Join-Path $wix.FullName 'bin') + ';' + $env:PATH
    } else {
        throw ('Para crear el instalador .' + $Tipo + ' se necesita WiX Toolset 3.x (candle.exe y light.exe). ' +
            'Instálelo con: winget install WiXToolset.WiXToolset  (o use -Tipo app-image).')
    }
}

Write-Host 'Generando el fat JAR y los módulos de JavaFX (mvnw -Pinstalador package)...'
& .\mvnw.cmd -B -q -Pinstalador clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación con Maven.' }

$destino = 'dist'
if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }

$argumentos = @(
    '--type', $Tipo,
    '--name', $nombre,
    '--app-version', $version,
    '--description', 'Sistema de gestión de planillas para empresas peruanas',
    '--vendor', 'Ronald Micha - UNAJ',
    '--copyright', 'Planilla Perú 2026',
    '--icon', 'src\main\packaging\planilla-peru.ico',
    '--input', 'target\instalador',
    '--main-jar', 'planilla-peru.jar',
    '--main-class', 'com.planillaperu.RonaldmichaLauncher',
    '--module-path', 'target\javafx-mods',
    '--add-modules', 'javafx.controls,javafx.fxml,java.se,jdk.unsupported,jdk.localedata,jdk.crypto.ec,jdk.charsets,jdk.zipfs',
    '--jlink-options', '--strip-debug --no-header-files --no-man-pages',
    '--java-options', '-Xmx512m',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--dest', $destino
)
if ($Tipo -ne 'app-image') {
    $argumentos += @(
        '--win-menu', '--win-menu-group', $nombre,
        '--win-shortcut', '--win-shortcut-prompt',
        '--win-dir-chooser',
        '--win-per-user-install',
        '--win-upgrade-uuid', $uuidActualizacion
    )
}

Write-Host "Ejecutando jpackage ($Tipo)..."
& $jpackage @argumentos
if ($LASTEXITCODE -ne 0) { throw 'Falló jpackage.' }
Write-Host "Listo: $((Resolve-Path $destino).Path)"
Get-ChildItem $destino

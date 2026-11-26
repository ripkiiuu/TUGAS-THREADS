param(
    [ValidateSet('build', 'run', 'test', 'gui-test')]
    [string]$Action = 'run'
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw 'JDK 17 atau lebih baru diperlukan. Pastikan java dan javac tersedia di PATH.'
}

New-Item -ItemType Directory -Force 'lib', 'out' | Out-Null
$dependencies = @(
    @{
        File = 'h2-2.3.232.jar'
        Url = 'https://repo.maven.apache.org/maven2/com/h2database/h2/2.3.232/h2-2.3.232.jar'
        Sha256 = '8dae62d22db8982c3dcb3826edb9c727c5d302063a67eef7d63d82de401f07d3'
    },
    @{
        File = 'mysql-connector-j-9.2.0.jar'
        Url = 'https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/9.2.0/mysql-connector-j-9.2.0.jar'
        Sha256 = '7e9941bbdcca244d878ea95bfff788fd9ba6a65af757f24be6c632930d61c7ed'
    }
)
foreach ($dependency in $dependencies) {
    $destination = Join-Path 'lib' $dependency.File
    if (-not (Test-Path $destination)) {
        Write-Host "Mengunduh $($dependency.File)..."
        $partial = "$destination.download"
        Invoke-WebRequest -UseBasicParsing -Uri $dependency.Url -OutFile $partial
        if ((Get-FileHash $partial -Algorithm SHA256).Hash.ToLowerInvariant() -ne $dependency.Sha256) {
            throw "Checksum unduhan tidak cocok: $partial. Jangan gunakan berkas tersebut."
        }
        Move-Item -Force $partial $destination
    }
    if ((Get-FileHash $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $dependency.Sha256) {
        throw "Checksum $destination tidak cocok. Periksa berkas dependency."
    }
}

$sources = Get-ChildItem 'src', 'test' -Filter '*.java' -Recurse | ForEach-Object {
    '"' + $_.FullName.Replace('\', '/') + '"'
}
# Argfile menghindari masalah path folder yang mengandung spasi.
[System.IO.File]::WriteAllLines((Join-Path $PWD 'out/sources.txt'), [string[]]$sources,
    [System.Text.UTF8Encoding]::new($false))
& javac -encoding UTF-8 --release 17 -Xlint:all -cp 'lib/*' -d out '@out/sources.txt'
if ($LASTEXITCODE -ne 0) { throw 'Kompilasi Java gagal.' }
Write-Host 'BUILD OK'

switch ($Action) {
    'run' { & java -cp 'out;lib/*' kampus.App }
    'test' { & java -ea -cp 'out;lib/*' kampus.DaoIntegrationTest }
    'gui-test' { & java -ea -cp 'out;lib/*' kampus.GuiIntegrationTest }
}
if ($Action -ne 'build' -and $LASTEXITCODE -ne 0) { throw "Perintah $Action gagal." }

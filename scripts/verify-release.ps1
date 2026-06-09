param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $JavaHome) {
    $knownJava = 'C:\Program Files\Java\jdk-21'
    if (Test-Path (Join-Path $knownJava 'bin\java.exe')) {
        $JavaHome = $knownJava
    }
}

$java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { $null }
if (-not $java -or -not (Test-Path $java)) {
    throw 'Java was not found. Set JAVA_HOME to a JDK 17 or newer installation.'
}

Write-Host '[1/2] Running backend tests...'
Push-Location (Join-Path $root 'backend')
try {
    & $java -classpath 'gradle/wrapper/gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain test
    if ($LASTEXITCODE -ne 0) {
        throw "Backend tests failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host '[2/2] Building frontend...'
Push-Location (Join-Path $root 'frontend')
try {
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host 'Release verification passed.'

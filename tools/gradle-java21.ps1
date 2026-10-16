[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('version', 'format', 'check', 'build', 'gametest', 'validate')]
    [string] $Mode
)

$ErrorActionPreference = 'Stop'
if ($args.Count -ne 0) {
    throw 'Extra arguments are not supported. Use one documented validation mode.'
}

$repository = Split-Path -Parent $PSScriptRoot
$gradleCache = [IO.Path]::GetFullPath((Join-Path $repository '../workspace/.cache/gradle'))
$jdkCandidates = @(
    foreach ($jdkRoot in @((Join-Path $gradleCache 'jdks'), (Join-Path $HOME '.gradle/jdks'))) {
        if (Test-Path -LiteralPath $jdkRoot -PathType Container) {
            Get-ChildItem -LiteralPath $jdkRoot -Directory | Sort-Object Name | ForEach-Object FullName
        }
    }
    if ($env:JAVA_HOME) { $env:JAVA_HOME }
)
$java21 = $jdkCandidates | Where-Object {
    $releaseFile = Join-Path $_ 'release'
    (Test-Path -LiteralPath (Join-Path $_ 'bin/java.exe') -PathType Leaf) -and
        (Test-Path -LiteralPath $releaseFile -PathType Leaf) -and
        (Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="21(?:\.|"|-)' -Quiet)
} | Select-Object -First 1
if (-not $java21) {
    throw 'Java 21 JDK not found in workspace/user Gradle jdks or JAVA_HOME. Install Java 21 before retrying.'
}

$gradleTasks = @(switch ($Mode) {
    'version' { @('--version') }
    'format' { @('spotlessApply') }
    'check' { @('spotlessCheck', 'check') }
    'build' { @('spotlessCheck', 'build') }
    'gametest' { @('runGameTestServer') }
    'validate' { @('spotlessCheck', 'build', 'runGameTestServer') }
})

$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $java21
    $env:GRADLE_USER_HOME = $gradleCache
    Write-Host "Java 21: $java21"
    Write-Host "Gradle cache: $gradleCache"
    Write-Host "Mode: $Mode -> $($gradleTasks -join ' ')"
    Push-Location -LiteralPath $repository
    try {
        & (Join-Path $repository 'gradlew.bat') @gradleTasks --console=plain
        $gradleExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
}
exit $gradleExit

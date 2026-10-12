param(
    [ValidateSet('mobvats','xr2')][string]$Implementation='mobvats',
    [string[]]$Machines=@('mixer_min','vat_min'),
    [int[]]$Counts=@(1,4,16),
    [int]$Repetitions=5,
    [int]$Warmup=200,
    [int]$Measure=400,
    [switch]$NoProfile,
    [switch]$NoProbes,
    [switch]$Cold
)
$ErrorActionPreference='Stop'
$benchmarkRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repositoryRoot=[IO.Path]::GetFullPath((Join-Path $benchmarkRoot '..'))
$mutex=[Threading.Mutex]::new($false,'MobVatsLifecycleBenchmark')
if (!$mutex.WaitOne(0)) {throw 'Another benchmark runner is active. Run environments sequentially.'}
try {
    $batchId=Get-Date -Format 'yyyyMMdd-HHmmss'
    foreach($machine in $Machines) {foreach($count in $Counts) {
        if($count -lt 1 -or $count -gt 32) {throw 'Count must be 1..32'}
        if($machine -notmatch '^[a-z0-9_]+$') {throw 'Invalid machine name'}
        $runId="$batchId-$machine-$count"
        $log=Join-Path $benchmarkRoot ".cache/$Implementation/$runId-gradle.log"
        New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
        $arguments=@('-p',$benchmarkRoot,'runBenchmark',"-Pimplementation=$Implementation","-Pmachine=$machine",
            "-Pcount=$count","-Prepetitions=$Repetitions","-Pwarmup=$Warmup","-Pmeasure=$Measure",
            "-Pprofile=$((!$NoProfile).ToString().ToLowerInvariant())","-Pprobes=$((!$NoProbes).ToString().ToLowerInvariant())")
        Push-Location $repositoryRoot
        try {
            & '.\gradlew.bat' @arguments "-PrunId=$runId" *> $log
            if($LASTEXITCODE -ne 0) {throw "Benchmark failed; see $log"}
            $runPath=Join-Path $benchmarkRoot ".cache/$Implementation/$runId"
            $row=Get-Content -LiteralPath (Join-Path $runPath 'results.jsonl') -Tail 1 | ConvertFrom-Json
            $exitRecord=@{schema_version=1;implementation=$Implementation;machine=$machine;count=$count;
                scenario='server_stopped_to_gradle_exit';duration_ms=([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()-$row.recorded_at_epoch_ms);
                limitation='Includes JFR dump, JVM exit and Gradle completion; not pure JVM termination.'}
            $exitRecord | ConvertTo-Json -Compress | Add-Content -LiteralPath (Join-Path $runPath 'process.jsonl')
            Write-Output "Completed $Implementation $machine count=$count : $runPath"
            if($Cold) {
                for($rep=1;$rep -le $Repetitions;$rep++) {
                    $coldId="$runId-cold-$rep"; $coldPath=Join-Path $benchmarkRoot ".cache/$Implementation/$coldId"
                    New-Item -ItemType Directory -Path $coldPath | Out-Null
                    # Always copy the immutable suite snapshot into a new destination; no recursive deletion.
                    Copy-Item -LiteralPath (Join-Path $runPath 'benchmark') -Destination $coldPath -Recurse
                    Copy-Item -LiteralPath (Join-Path $runPath 'config') -Destination $coldPath -Recurse
                    & '.\gradlew.bat' @arguments "-PrunId=$coldId" '-Pphase=cold' *> "$coldPath-gradle.log"
                    if($LASTEXITCODE -ne 0) {throw "Cold restore failed; see $coldPath-gradle.log"}
                    Write-Output "Completed cold restore $rep : $coldPath"
                }
            }
        } finally {Pop-Location}
    }}
} finally {$mutex.ReleaseMutex(); $mutex.Dispose()}

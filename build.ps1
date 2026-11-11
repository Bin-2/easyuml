param(
    [string[]]$Targets = @('single-nbm'),
    [string]$NetBeansHome = 'C:\Program Files\NetBeans-24\netbeans',
    [string]$JdkHome = 'C:\Program Files\Java\jdk-21',
    [string]$ApiBaselineHome = (Join-Path $PSScriptRoot '__working\toolchains\netbeans-8.2\netbeans')
)
$ErrorActionPreference = 'Stop'
$ant = Join-Path $NetBeansHome 'extide\ant\bin\ant.bat'
$harness = Join-Path $NetBeansHome 'harness'
foreach ($required in @($ant, "$harness\suite.xml", "$JdkHome\bin\javac.exe", "$ApiBaselineHome\platform\core\org-openide-filesystems.jar")) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing build tool: $required" }
}
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkHome
    $launch = $Targets -contains 'run'
    $antTargets = @($Targets | Where-Object { $_ -ne 'run' })
    if ($antTargets.Count -eq 0) { $antTargets = @('build') }
    & $ant "-Dnbplatform.active.dir=$ApiBaselineHome" "-Dharness.dir=$harness" `
        "-Dnbjdk.home=$JdkHome" "-Dtest.user.dir=$PSScriptRoot\build\testuserdir" `
        -f "$PSScriptRoot\build.xml" @antTargets
    if ($LASTEXITCODE -ne 0) { throw "Ant failed with exit code $LASTEXITCODE" }
    if ($launch) {
        $profile = Join-Path $PSScriptRoot 'build\testuserdir'
        New-Item -ItemType Directory -Path "$profile\etc" -Force | Out-Null
        ('netbeans_extraclusters="' + "$PSScriptRoot\build\cluster" + '"') |
            Set-Content "$profile\etc\netbeans.conf" -Encoding ascii
        $arguments = '--jdkhome "{0}" --userdir "{1}" --cachedir "{2}\build\testcache" --nosplash' -f $JdkHome,$profile,$PSScriptRoot
        Start-Process -FilePath "$NetBeansHome\bin\netbeans64.exe" -ArgumentList $arguments -WindowStyle Hidden
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}

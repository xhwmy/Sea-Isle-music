$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'setup-remote.ps1'
if (-not (Test-Path -LiteralPath $script)) { throw 'setup-remote.ps1 missing' }
$output = & $script -CheckOnly 2>&1 | Out-String
if ($output -notmatch 'Navidrome' -or $output -notmatch 'Tailscale') {
    throw "Check mode did not report both components: $output"
}
if ($output -notmatch '待完成|就绪|已安装') { throw "Check mode lacked a readiness result: $output" }
Write-Output 'setup-remote check mode passed'

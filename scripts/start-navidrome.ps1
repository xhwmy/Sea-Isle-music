$ErrorActionPreference = 'Stop'
$musicRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$exe = Join-Path $musicRoot 'Navidrome\navidrome.exe'
$config = Join-Path $musicRoot 'Navidrome\navidrome.toml'
if (-not (Test-Path -LiteralPath $exe) -or -not (Test-Path -LiteralPath $config)) {
    throw 'Navidrome 程序或配置文件不存在。'
}
function Test-MusicService {
    try {
        return (Invoke-WebRequest 'http://127.0.0.1:4533/ping' -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200
    } catch { return $false }
}
if (Test-MusicService) { return }
$running = Get-CimInstance Win32_Process -Filter "Name='navidrome.exe'" | Where-Object {
    $_.ExecutablePath -eq $exe -and $_.CommandLine -match [regex]::Escape($config)
}
if (-not $running) {
    $logRoot = Join-Path $musicRoot 'Navidrome\data'
    Start-Process -FilePath $exe -ArgumentList @('-c', ('"' + $config + '"')) -WorkingDirectory (Split-Path -Parent $exe) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'server-stdout.log') -RedirectStandardError (Join-Path $logRoot 'server-stderr.log')
}
for ($attempt = 0; $attempt -lt 8; $attempt++) {
    if (Test-MusicService) { return }
    Start-Sleep -Seconds 1
}
throw '音乐服务未能就绪，请检查 Navidrome/data/server-stderr.log。'

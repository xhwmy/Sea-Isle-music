param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$musicRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$exe = Join-Path $musicRoot 'Navidrome\navidrome.exe'
$config = Join-Path $musicRoot 'Navidrome\navidrome.toml'
$navidromeInstalled = (Test-Path -LiteralPath $exe) -and (Test-Path -LiteralPath $config)
$tailscaleCommand = Get-Command tailscale -ErrorAction SilentlyContinue
$tailscale = if ($tailscaleCommand) { $tailscaleCommand.Source } else {
    @('C:\Program Files\Tailscale\tailscale.exe', 'C:\Program Files (x86)\Tailscale\tailscale.exe') |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
Write-Output ("Navidrome: " + $(if ($navidromeInstalled) { '已安装' } else { '待完成：程序或配置缺失' }))
Write-Output ("Tailscale: " + $(if ($tailscale) { '已安装' } else { '待完成：请在电脑安装 Tailscale 并登录' }))
if ($CheckOnly) { return }
if (-not $navidromeInstalled) { throw 'Navidrome 缺失，无法继续。' }
& (Join-Path $PSScriptRoot 'start-navidrome.ps1')
try {
    $startupScript = Join-Path $PSScriptRoot 'start-navidrome.ps1'
    $taskAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $startupScript + '"')
    $taskTrigger = @(
        (New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME),
        (New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Minutes 2))
    )
    $taskSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew -Hidden
    Register-ScheduledTask -TaskName 'SeaShelf Navidrome' -Action $taskAction -Trigger $taskTrigger -Settings $taskSettings -Description '登录时启动私人音乐库；每两分钟检查，服务退出后自动恢复' -Force | Out-Null
    Write-Output 'Navidrome 登录自启动：已设置'
} catch {
    Write-Output ("Navidrome 登录自启动：设置失败，请手动运行 start-navidrome.ps1。原因：" + $_.Exception.Message)
}
if (-not $tailscale) {
    Write-Output '外网入口待完成：在电脑安装并登录 Tailscale，然后重新运行此脚本。手机无需安装 Tailscale。'
    return
}
$statusJson = & $tailscale status --json 2>$null | Out-String
if ($LASTEXITCODE -ne 0 -or -not $statusJson.Trim()) {
    Write-Output '外网入口待完成：请先在电脑登录 Tailscale，然后重新运行此脚本。'
    return
}
$status = $statusJson | ConvertFrom-Json
if ($status.BackendState -ne 'Running') {
    Write-Output '外网入口待完成：请先在电脑登录并连接 Tailscale，然后重新运行此脚本。'
    return
}
Write-Output '正在配置 HTTPS 外网入口…'
& $tailscale funnel --bg 4533
if ($LASTEXITCODE -ne 0) {
    throw 'Tailscale Funnel 设置失败；请在 Tailscale 管理界面启用 Funnel 权限后重试。'
}
& $tailscale funnel status
Write-Output '请把上方 https:// 地址填入手机 App 的服务器地址。'

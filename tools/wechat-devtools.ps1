param(
  [ValidateSet('open', 'preview', 'auto-preview', 'upload')]
  [string]$Command = 'open',

  [string]$ProjectPath = (Resolve-Path "$PSScriptRoot\..").Path,

  [string]$QrFormat = 'terminal',

  [string]$Version = '',

  [string]$Desc = 'VSCode upload'
)

$ErrorActionPreference = 'Stop'

function Find-WechatDevtoolsCli {
  $candidates = @()

  if ($env:WECHAT_DEVTOOLS_CLI) {
    $candidates += $env:WECHAT_DEVTOOLS_CLI
  }

  $candidates += @(
    'C:\Program Files (x86)\Tencent\WeChat DevTools\cli.bat',
    'C:\Program Files\Tencent\WeChat DevTools\cli.bat',
    'C:\Program Files (x86)\Tencent\wechatdevtools\cli.bat',
    'C:\Program Files\Tencent\wechatdevtools\cli.bat',
    "$env:LOCALAPPDATA\WeChat DevTools\cli.bat",
    "$env:LOCALAPPDATA\Programs\WeChat DevTools\cli.bat"
  )

  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path -LiteralPath $candidate)) {
      return (Resolve-Path -LiteralPath $candidate).Path
    }
  }

  $searchRoots = @(
    'C:\Program Files\Tencent',
    'C:\Program Files (x86)\Tencent',
    "$env:LOCALAPPDATA\Tencent",
    "$env:LOCALAPPDATA\Programs"
  )

  foreach ($root in $searchRoots) {
    if (Test-Path -LiteralPath $root) {
      $match = Get-ChildItem -LiteralPath $root -Recurse -Filter 'cli.bat' -File -ErrorAction SilentlyContinue |
        Select-Object -First 1

      if ($match) {
        return $match.FullName
      }
    }
  }

  $message = @(
    'WeChat DevTools CLI was not found.',
    '',
    'Fix:',
    '1. Open WeChat DevTools and enable Settings -> Security -> Service Port.',
    '2. Find cli.bat in the WeChat DevTools install directory.',
    '3. Set WECHAT_DEVTOOLS_CLI, for example:',
    "   [Environment]::SetEnvironmentVariable('WECHAT_DEVTOOLS_CLI', 'C:\Program Files (x86)\Tencent\WeChat DevTools\cli.bat', 'User')",
    '4. Restart VSCode and run the task again.'
  ) -join [Environment]::NewLine

  throw $message
}

if (-not (Test-Path -LiteralPath $ProjectPath)) {
  throw "Mini program project path does not exist: $ProjectPath"
}

$cli = Find-WechatDevtoolsCli
$cliArgs = @($Command, '--project', $ProjectPath)

if ($Command -eq 'preview' -or $Command -eq 'auto-preview') {
  $cliArgs += @('--qr-format', $QrFormat)
}

if ($Command -eq 'upload') {
  if ([string]::IsNullOrWhiteSpace($Version)) {
    throw 'upload requires -Version, for example: -Version 1.0.0'
  }

  $cliArgs += @('--version', $Version, '--desc', $Desc)
}

Write-Host "Using WeChat DevTools CLI: $cli"
Write-Host "Project: $ProjectPath"
& $cli @cliArgs

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$backendDir = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$jarPath = Join-Path $backendDir 'target\property-management-1.0.0-SNAPSHOT.jar'
$applicationDev = Join-Path $backendDir 'src\main\resources\application-dev.yml'
$maven = 'C:\Users\PC\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd'
$java = 'C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'

$config = Get-Content -Raw -LiteralPath $applicationDev
$userMatch = [regex]::Match($config, 'username:\s*\$\{MYSQL_USERNAME:([^}]+)\}')
$passwordMatch = [regex]::Match($config, 'password:\s*\$\{MYSQL_PASSWORD:([^}]+)\}')
if (-not $userMatch.Success -or -not $passwordMatch.Success) {
    throw 'Cannot resolve local datasource defaults'
}

$databaseUser = $userMatch.Groups[1].Value
$env:MYSQL_PWD = $passwordMatch.Groups[1].Value
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$roleCode = "CODEX_UPGRADE_$suffix"
$username = "codex_upgrade_$suffix"
$testDataCreated = $false
$startedProcess = $null

function Invoke-MySql {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $output = & $mysql `
        --default-character-set=utf8mb4 `
        --batch `
        --raw `
        -u $databaseUser `
        dehui_property_dev `
        -e $Sql |
        Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed with exit code $LASTEXITCODE"
    }
    return $output.Trim()
}

function Start-Backend {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $stdout = Join-Path $backendDir "rbac-preservation-$stamp.out.log"
    $stderr = Join-Path $backendDir "rbac-preservation-$stamp.err.log"
    $process = Start-Process `
        -FilePath $java `
        -ArgumentList '-jar', $jarPath `
        -WorkingDirectory $backendDir `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            Invoke-WebRequest `
                -UseBasicParsing `
                -Uri 'http://127.0.0.1:8080/api/system/roles' `
                -TimeoutSec 2 |
                Out-Null
            $ready = $true
            break
        } catch {
            $response = $_.Exception.Response
            $statusCodeProperty = if ($null -eq $response) {
                $null
            } else {
                $response.PSObject.Properties['StatusCode']
            }
            if ($null -ne $statusCodeProperty -and [int]$statusCodeProperty.Value -eq 401) {
                $ready = $true
                break
            }
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        throw 'Restarted backend did not become ready'
    }

    return [pscustomobject]@{
        Process = $process
        Stdout = $stdout
        Stderr = $stderr
    }
}

try {
    $insertSql = @"
INSERT INTO sys_role
    (code, name, role_type, status, created_by, updated_by, deleted, remark)
VALUES
    ('$roleCode', 'Codex upgrade preservation', 'BUSINESS', 'ENABLED',
     0, 0, 0, 'RBAC upgrade preservation verification');
SET @test_role_id = LAST_INSERT_ID();

INSERT INTO sys_user
    (code, username, password_hash, real_name, status,
     mobile_enabled, web_login_enabled, primary_role_id,
     created_by, updated_by, deleted, remark)
SELECT
    CONCAT('USER_', '$suffix'),
    '$username',
    source.password_hash,
    'Codex upgrade preservation',
    'ENABLED',
    0,
    1,
    @test_role_id,
    0,
    0,
    0,
    'RBAC upgrade preservation verification'
FROM sys_user source
WHERE source.deleted = 0
LIMIT 1;
SET @test_user_id = LAST_INSERT_ID();

INSERT INTO sys_user_role
    (user_id, role_id, status, created_by, updated_by, deleted)
VALUES
    (@test_user_id, @test_role_id, 'ENABLED', 0, 0, 0);

INSERT INTO sys_role_menu
    (role_id, menu_id, status, created_by, updated_by, deleted)
SELECT @test_role_id, id, 'ENABLED', 0, 0, 0
FROM sys_menu
WHERE code = 'dashboard' AND deleted = 0
LIMIT 1;

INSERT INTO sys_role_permission
    (role_id, permission_id, status, created_by, updated_by, deleted)
SELECT @test_role_id, id, 'ENABLED', 0, 0, 0
FROM sys_permission
WHERE code = 'dashboard:view' AND deleted = 0
LIMIT 1;
"@
    Invoke-MySql -Sql $insertSql | Out-Null
    $testDataCreated = $true

    $snapshotSql = @"
SELECT
    'sys_user_role' AS relation_table,
    COUNT(*) AS row_count,
    COALESCE(SUM(CRC32(CONCAT_WS(
        '|', id, user_id, role_id, status,
        COALESCE(created_by, ''), COALESCE(updated_by, ''), deleted
    ))), 0) AS relation_digest
FROM sys_user_role
UNION ALL
SELECT
    'sys_role_menu',
    COUNT(*),
    COALESCE(SUM(CRC32(CONCAT_WS(
        '|', id, role_id, menu_id, status,
        COALESCE(created_by, ''), COALESCE(updated_by, ''), deleted
    ))), 0)
FROM sys_role_menu
UNION ALL
SELECT
    'sys_role_permission',
    COUNT(*),
    COALESCE(SUM(CRC32(CONCAT_WS(
        '|', id, role_id, permission_id, status,
        COALESCE(created_by, ''), COALESCE(updated_by, ''), deleted
    ))), 0)
FROM sys_role_permission;

SELECT
    r.code,
    u.username,
    ur.status AS user_role_status,
    rm.status AS menu_status,
    rp.status AS permission_status
FROM sys_role r
JOIN sys_user u
  ON u.primary_role_id = r.id
 AND u.deleted = 0
JOIN sys_user_role ur
  ON ur.user_id = u.id
 AND ur.role_id = r.id
 AND ur.deleted = 0
JOIN sys_role_menu rm
  ON rm.role_id = r.id
 AND rm.deleted = 0
JOIN sys_role_permission rp
  ON rp.role_id = r.id
 AND rp.deleted = 0
WHERE r.code = '$roleCode'
  AND r.deleted = 0;
"@
    $before = Invoke-MySql -Sql $snapshotSql

    $listener = Get-NetTCPConnection `
        -State Listen `
        -LocalPort 8080 |
        Select-Object -First 1
    $backendPid = $listener.OwningProcess
    $current = Get-CimInstance Win32_Process -Filter "ProcessId=$backendPid"
    if (-not $current -or -not $current.CommandLine.Contains($jarPath)) {
        throw "PID $backendPid is not the expected backend jar"
    }

    Stop-Process -Id $backendPid
    Wait-Process -Id $backendPid -ErrorAction SilentlyContinue

    & $maven -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Backend package failed with exit code $LASTEXITCODE"
    }

    $startedProcess = Start-Backend
    $after = Invoke-MySql -Sql $snapshotSql
    $preserved = $before -eq $after

    Write-Output "UPGRADE_PRESERVED=$preserved"
    Write-Output "NEW_BACKEND_PID=$($startedProcess.Process.Id)"
    Write-Output "BEFORE=$($before -replace "`r?`n", ' | ')"
    Write-Output "AFTER=$($after -replace "`r?`n", ' | ')"
    Select-String `
        -LiteralPath $startedProcess.Stdout `
        -Pattern 'Current version|Schema .* up to date|Started PropertyManagementApplication' |
        ForEach-Object { Write-Output $_.Line }

    if (-not $preserved) {
        throw 'RBAC relationship digest changed during upgrade restart'
    }
} finally {
    if ($testDataCreated) {
        $cleanupSql = @"
DELETE FROM sys_user_role
WHERE user_id = (
    SELECT id
    FROM sys_user
    WHERE username = '$username'
      AND remark = 'RBAC upgrade preservation verification'
    LIMIT 1
);
DELETE FROM sys_role_menu
WHERE role_id = (
    SELECT id
    FROM sys_role
    WHERE code = '$roleCode'
      AND remark = 'RBAC upgrade preservation verification'
    LIMIT 1
);
DELETE FROM sys_role_permission
WHERE role_id = (
    SELECT id
    FROM sys_role
    WHERE code = '$roleCode'
      AND remark = 'RBAC upgrade preservation verification'
    LIMIT 1
);
DELETE FROM sys_user
WHERE username = '$username'
  AND remark = 'RBAC upgrade preservation verification';
DELETE FROM sys_role
WHERE code = '$roleCode'
  AND remark = 'RBAC upgrade preservation verification';
SELECT
    (SELECT COUNT(*) FROM sys_user WHERE username = '$username')
        AS remaining_users,
    (SELECT COUNT(*) FROM sys_role WHERE code = '$roleCode')
        AS remaining_roles;
"@
        $cleanup = Invoke-MySql -Sql $cleanupSql
        Write-Output "CLEANUP=$($cleanup -replace "`r?`n", ' | ')"
    }

    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue

    $activeListener = Get-NetTCPConnection `
        -State Listen `
        -LocalPort 8080 `
        -ErrorAction SilentlyContinue
    if (-not $activeListener -and (Test-Path -LiteralPath $jarPath)) {
        $startedProcess = Start-Backend
        Write-Output "BACKEND_RECOVERED_PID=$($startedProcess.Process.Id)"
    }
}

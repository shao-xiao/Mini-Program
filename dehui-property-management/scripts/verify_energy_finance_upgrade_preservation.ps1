$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$backendDir = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$jarPath = Join-Path $backendDir 'target\property-management-1.0.0-SNAPSHOT.jar'
$applicationDev = Join-Path $backendDir 'src\main\resources\application-dev.yml'
$maven = 'C:\Users\PC\.cache\codex-runtimes\apache-maven-3.9.11\bin\mvn.cmd'
$java = 'C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
$databaseName = 'dehui_property_dev'
$protectedTableCatalog = Join-Path $PSScriptRoot 'protected_asset_business_tables.txt'
$protectedTables = @(
    Get-Content -LiteralPath $protectedTableCatalog |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith('#') }
)
if ($protectedTables.Count -eq 0) {
    throw 'Protected asset business table catalog is empty'
}
if (($protectedTables | Select-Object -Unique).Count -ne $protectedTables.Count) {
    throw 'Protected asset business table catalog contains duplicates'
}
$configuredUploadDir = if ($env:DEHUI_UPLOAD_DIR) {
    $env:DEHUI_UPLOAD_DIR
} else {
    'uploads'
}
$uploadRoot = if ([System.IO.Path]::IsPathRooted($configuredUploadDir)) {
    [System.IO.Path]::GetFullPath($configuredUploadDir)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $backendDir $configuredUploadDir))
}

$config = Get-Content -Raw -LiteralPath $applicationDev
$userMatch = [regex]::Match($config, 'username:\s*\$\{MYSQL_USERNAME:([^}]+)\}')
$passwordMatch = [regex]::Match($config, 'password:\s*\$\{MYSQL_PASSWORD:([^}]+)\}')
if (-not $userMatch.Success -or -not $passwordMatch.Success) {
    throw 'Cannot resolve local datasource defaults'
}

$databaseUser = $userMatch.Groups[1].Value
$originalMysqlPwdEntry = Get-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
$originalMysqlPwdWasSet = $null -ne $originalMysqlPwdEntry
$originalMysqlPwd = if ($originalMysqlPwdWasSet) {
    $originalMysqlPwdEntry.Value
} else {
    $null
}
$env:MYSQL_PWD = $passwordMatch.Groups[1].Value
$startedProcess = $null

function Invoke-MySql {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $output = & $mysql `
        --default-character-set=utf8mb4 `
        --batch `
        --raw `
        -u $databaseUser `
        $databaseName `
        -e $Sql |
        Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed with exit code $LASTEXITCODE"
    }
    return $output.Trim()
}

function Invoke-MySqlCanonical {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $output = & $mysql `
        --default-character-set=utf8mb4 `
        --batch `
        --binary-as-hex `
        -u $databaseUser `
        $databaseName `
        -e $Sql |
        Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL canonical snapshot failed with exit code $LASTEXITCODE"
    }
    return $output.Trim()
}

function Get-Sha256ForText {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha256.ComputeHash($bytes)
    } finally {
        $sha256.Dispose()
    }
    return ([BitConverter]::ToString($hash)).Replace('-', '')
}

function Get-ProtectedTableColumns {
    $columnSql = @"
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = '$databaseName'
  AND table_name IN ('$($protectedTables -join "','")')
ORDER BY table_name, ordinal_position;
"@
    $columnOutput = Invoke-MySql -Sql $columnSql
    $columnsByTable = @{}
    foreach ($line in @($columnOutput -split "`r?`n" | Select-Object -Skip 1)) {
        if (-not $line) {
            continue
        }
        $parts = $line -split "`t", 2
        if ($parts.Count -ne 2) {
            throw "Unexpected protected table column metadata: $line"
        }
        if (-not $columnsByTable.ContainsKey($parts[0])) {
            $columnsByTable[$parts[0]] =
                [System.Collections.Generic.List[string]]::new()
        }
        $columnsByTable[$parts[0]].Add($parts[1])
    }
    return $columnsByTable
}

function Get-BusinessDataSnapshot {
    param([hashtable]$SnapshotColumns = $null)

    $missingSql = @"
SELECT table_name
FROM information_schema.tables
WHERE table_schema = '$databaseName'
  AND table_name IN ('$($protectedTables -join "','")');
"@
    $existingOutput = Invoke-MySql -Sql $missingSql
    $existingTables = @(
        $existingOutput -split "`r?`n" |
        Select-Object -Skip 1 |
        Where-Object { $_ }
    )
    $missingTables = @($protectedTables | Where-Object { $_ -notin $existingTables })
    if ($missingTables.Count -gt 0) {
        throw "Protected business tables are missing: $($missingTables -join ', ')"
    }

    $currentColumns = Get-ProtectedTableColumns
    if ($null -eq $SnapshotColumns) {
        $SnapshotColumns = $currentColumns
    }
    foreach ($table in $protectedTables) {
        if (-not $SnapshotColumns.ContainsKey($table)) {
            throw "Protected business table has no snapshot columns: $table"
        }
        if ('id' -notin $SnapshotColumns[$table]) {
            throw "Protected business table must have a stable id primary key: $table"
        }
        $missingColumns = @(
            $SnapshotColumns[$table] |
            Where-Object { $_ -notin $currentColumns[$table] }
        )
        if ($missingColumns.Count -gt 0) {
            throw "Protected columns were removed from $table`: $($missingColumns -join ', ')"
        }
    }

    $countSelects = $protectedTables |
        ForEach-Object { "SELECT '$_' AS table_name, COUNT(*) AS row_count FROM ``$_``" }
    $counts = Invoke-MySql -Sql (($countSelects -join " UNION ALL ") + ';')

    $snapshotSql = [System.Text.StringBuilder]::new()
    [void]$snapshotSql.AppendLine(
        'SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;'
    )
    [void]$snapshotSql.AppendLine('START TRANSACTION WITH CONSISTENT SNAPSHOT;')
    foreach ($table in $protectedTables) {
        $columns = @($SnapshotColumns[$table])
        $quotedColumns = $columns |
            ForEach-Object { "``$($_.Replace('`', '``'))``" }
        $columnMarker = $columns -join ','
        [void]$snapshotSql.AppendLine(
            "SELECT '__DHIC_PROTECTED_TABLE__' AS marker, " +
            "'$table' AS table_name, '$columnMarker' AS protected_columns;"
        )
        [void]$snapshotSql.AppendLine(
            "SELECT $($quotedColumns -join ', ') FROM ``$table`` ORDER BY ``id``;"
        )
    }
    [void]$snapshotSql.AppendLine('COMMIT;')
    $dataOutput = Invoke-MySqlCanonical -Sql $snapshotSql.ToString()

    $flywayOutput = Invoke-MySql -Sql @"
SELECT version
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1;
"@
    $flywayVersion = @($flywayOutput -split "`r?`n")[-1]

    return [pscustomobject]@{
        Counts = $counts
        Digest = Get-Sha256ForText -Text $dataOutput
        Columns = $SnapshotColumns
        FlywayVersion = $flywayVersion
    }
}

function Get-UploadFileSnapshot {
    $files = if (Test-Path -LiteralPath $uploadRoot -PathType Container) {
        @(
            Get-ChildItem -LiteralPath $uploadRoot -Recurse -File |
            Sort-Object FullName
        )
    } else {
        @()
    }
    $manifest = foreach ($file in $files) {
        $relativePath = $file.FullName.Substring($uploadRoot.Length).
            TrimStart([char[]]@('\', '/')).
            Replace('\', '/')
        $fileHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        "$relativePath`t$($file.Length)`t$fileHash"
    }
    $totalBytes = ($files | Measure-Object -Property Length -Sum).Sum
    if ($null -eq $totalBytes) {
        $totalBytes = 0
    }
    return [pscustomobject]@{
        Count = $files.Count
        Bytes = [long]$totalBytes
        Digest = Get-Sha256ForText -Text ($manifest -join "`n")
    }
}

function Start-Backend {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $stdout = Join-Path $backendDir "business-preservation-$stamp.out.log"
    $stderr = Join-Path $backendDir "business-preservation-$stamp.err.log"
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
                -Uri 'http://127.0.0.1:8080/api/bills' `
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
    & $maven -q -Dtest=BusinessDataUpgradePreservationPolicyTest test
    if ($LASTEXITCODE -ne 0) {
        throw "Asset business migration guard failed with exit code $LASTEXITCODE"
    }

    $before = Get-BusinessDataSnapshot
    $uploadsBefore = Get-UploadFileSnapshot

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
    $after = Get-BusinessDataSnapshot -SnapshotColumns $before.Columns
    $uploadsAfter = Get-UploadFileSnapshot
    $countsPreserved = $before.Counts -eq $after.Counts
    $contentPreserved = $before.Digest -eq $after.Digest
    $uploadsPreserved =
        $uploadsBefore.Count -eq $uploadsAfter.Count -and
        $uploadsBefore.Bytes -eq $uploadsAfter.Bytes -and
        $uploadsBefore.Digest -eq $uploadsAfter.Digest

    Write-Output "UPGRADE_PRESERVED=$($countsPreserved -and $contentPreserved -and $uploadsPreserved)"
    Write-Output "COUNTS_PRESERVED=$countsPreserved"
    Write-Output "CONTENT_PRESERVED=$contentPreserved"
    Write-Output "UPLOAD_FILES_PRESERVED=$uploadsPreserved"
    Write-Output "NEW_BACKEND_PID=$($startedProcess.Process.Id)"
    Write-Output "BUSINESS_DATA_SHA256=$($after.Digest)"
    Write-Output "UPLOAD_FILES_SHA256=$($uploadsAfter.Digest)"
    Write-Output "UPLOAD_FILE_COUNT=$($uploadsAfter.Count)"
    Write-Output "UPLOAD_FILE_BYTES=$($uploadsAfter.Bytes)"
    Write-Output "FLYWAY_BEFORE=$($before.FlywayVersion)"
    Write-Output "FLYWAY_AFTER=$($after.FlywayVersion)"
    Write-Output "TABLE_COUNTS=$($after.Counts -replace "`r?`n", ' | ')"
    Select-String `
        -LiteralPath $startedProcess.Stdout `
        -Pattern 'Current version|Schema .* up to date|Started PropertyManagementApplication' |
        ForEach-Object { Write-Output $_.Line }

    if (-not $countsPreserved -or -not $contentPreserved -or -not $uploadsPreserved) {
        throw 'Asset, finance or uploaded file data changed during upgrade restart'
    }
} finally {
    if ($originalMysqlPwdWasSet) {
        $env:MYSQL_PWD = $originalMysqlPwd
    } else {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }

    $activeListener = Get-NetTCPConnection `
        -State Listen `
        -LocalPort 8080 `
        -ErrorAction SilentlyContinue
    if (-not $activeListener -and (Test-Path -LiteralPath $jarPath)) {
        $startedProcess = Start-Backend
        Write-Output "BACKEND_RECOVERED_PID=$($startedProcess.Process.Id)"
    }
}

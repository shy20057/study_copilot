param(
    [Parameter(Mandatory = $true)][string]$BackupDirectory,
    [ValidateRange(1, 365)][int]$RetentionDays = 7
)

# Use PGHOST/PGPORT/PGUSER and a private PGPASSFILE or process environment.
# This script never prints credentials or reads another project's configuration.
$ErrorActionPreference = 'Stop'
$taskDumpCommand = (Get-Command pg_dump -ErrorAction Stop).Source
$taskBackupRoot = [System.IO.Path]::GetFullPath($BackupDirectory)
if ($taskBackupRoot -eq [System.IO.Path]::GetPathRoot($taskBackupRoot)) { throw 'Choose a dedicated backup directory.' }
New-Item -ItemType Directory -Path $taskBackupRoot -Force | Out-Null
$taskBackupRoot = (Resolve-Path -LiteralPath $taskBackupRoot).Path
$taskStamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$taskDumpPath = Join-Path $taskBackupRoot "study-copilot-$taskStamp.dump"
$taskPartialPath = "$taskDumpPath.partial"
foreach ($taskPath in @($taskDumpPath, $taskPartialPath)) {
    $taskResolved = [System.IO.Path]::GetFullPath($taskPath)
    if (-not $taskResolved.StartsWith($taskBackupRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Backup path must remain inside the chosen directory.'
    }
}

try {
    $ErrorActionPreference = 'Continue'
    & $taskDumpCommand --no-password --format=custom --dbname=study_copilot --file=$taskPartialPath
    $taskDumpExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($taskDumpExit -ne 0) { throw "pg_dump failed with exit code $taskDumpExit. Existing backups were retained." }
    if ((Get-Item -LiteralPath $taskPartialPath).Length -eq 0) { throw 'The database archive is empty.' }
    Move-Item -LiteralPath $taskPartialPath -Destination $taskDumpPath

    # Expire only this script's archive format, only after a successful new dump.
    $taskCutoff = [DateTime]::UtcNow.AddDays(-$RetentionDays)
    foreach ($taskArchive in Get-ChildItem -LiteralPath $taskBackupRoot -Filter 'study-copilot-*.dump' -File) {
        if ($taskArchive.Name -match '^study-copilot-\d{8}T\d{9}Z\.dump$' -and $taskArchive.LastWriteTimeUtc -lt $taskCutoff) {
            if (-not $taskArchive.FullName.StartsWith($taskBackupRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'Refusing to expire an archive outside the backup directory.'
            }
            Remove-Item -LiteralPath $taskArchive.FullName
        }
    }
    Write-Output "Database archive written: $taskDumpPath"
} finally {
    $ErrorActionPreference = 'Stop'
    if (Test-Path -LiteralPath $taskPartialPath) { Remove-Item -LiteralPath $taskPartialPath }
}

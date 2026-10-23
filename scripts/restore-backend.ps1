param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [Parameter(Mandatory = $true)][ValidatePattern('^study_copilot_restore_[a-z0-9_]+$')][string]$TargetDatabase
)

# Only a separately provisioned empty restore database is accepted. Never cleans a database.
$ErrorActionPreference = 'Stop'
$taskRestoreCommand = (Get-Command pg_restore -ErrorAction Stop).Source
$taskPsqlCommand = (Get-Command psql -ErrorAction Stop).Source
$taskArchivePath = (Resolve-Path -LiteralPath $Archive).Path
if (-not [System.IO.File]::Exists($taskArchivePath)) { throw 'Archive must be a regular file.' }

$ErrorActionPreference = 'Continue'
$taskExisting = & $taskPsqlCommand --no-password --dbname=$TargetDatabase --tuples-only --no-align --set=ON_ERROR_STOP=1 --command="select count(*) from information_schema.tables where table_schema='study_copilot'"
$taskCheckExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($taskCheckExit -ne 0 -or ($taskExisting -join '').Trim() -ne '0') {
    throw 'Restore target must already exist and contain no study_copilot tables.'
}
$ErrorActionPreference = 'Continue'
& $taskRestoreCommand --no-password --no-owner --no-acl --exit-on-error --dbname=$TargetDatabase $taskArchivePath
$taskRestoreExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($taskRestoreExit -ne 0) { throw "pg_restore failed with exit code $taskRestoreExit. The isolated restore target may contain partial data." }
Write-Output "Archive restored into isolated database: $TargetDatabase"
Write-Output 'Before serving traffic, reconcile later deletion requests and revoke restored sessions using the operations guide.'

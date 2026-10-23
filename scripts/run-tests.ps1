param(
    [string]$Selection,
    [string]$Log = "backend/target/test-output.log",
    [string]$Container = $env:SC_PG_CONTAINER,
    [string]$DbUrl = $env:SC_TEST_DB_URL,
    [int]$DbPort = 5433
)

# Runs the test profile against the isolated study_copilot_test database.
#
# The test role has no stored password. This script generates a throwaway one
# per run, injects it via the environment, and drops it again afterwards, so no
# credential is ever written to disk or a shell transcript.
#
# Run from the repository root:
#   powershell -NoProfile -File scripts/run-tests.ps1
#   powershell -NoProfile -File scripts/run-tests.ps1 -Selection "AuthApiTest"
#
# Works on Windows PowerShell 5.1 and on PowerShell 7+.
#
# Requires the PostgreSQL container to be running with study_copilot_test
# already provisioned. See docs/backend-local-development.md.

$ErrorActionPreference = 'Stop'

if (-not $Container) { $Container = 'procurement-stack-postgres-1' }
if (-not $DbUrl) {
    $DbUrl = "jdbc:postgresql://127.0.0.1:$DbPort/study_copilot_test?currentSchema=study_copilot"
}

# Hex encoding by hand rather than [Convert]::ToHexString, which only exists
# on .NET 5+ and would break this script on Windows PowerShell 5.1.
$randomBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($randomBytes)
$rng.Dispose()
$temporaryPassword = -join ($randomBytes | ForEach-Object { $_.ToString('x2') })

try {
    "ALTER ROLE study_copilot_test_runner PASSWORD '$temporaryPassword';" |
        docker exec -i $Container sh -c 'exec psql -U "$POSTGRES_USER" -d study_copilot_test -v ON_ERROR_STOP=1' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Test role password setup failed' }

    $env:SC_TEST_DB_URL = $DbUrl
    $env:SC_TEST_DB_USER = 'study_copilot_test_runner'
    $env:SC_TEST_DB_PASSWORD = $temporaryPassword

    $arguments = @('-B', '-ntp', '-f', 'backend/pom.xml', '-Dspring.profiles.active=test')
    if ($Selection) { $arguments += "-Dtest=$Selection" }

    # The log directory does not exist on a fresh clone.
    $logDirectory = Split-Path -Parent $Log
    if ($logDirectory) { New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null }

    # Maven writes progress to stderr. Under Stop, PowerShell turns that into a
    # terminating NativeCommandError, so relax the preference for this call and
    # judge the run by the exit code instead.
    $ErrorActionPreference = 'Continue'
    & mvn @arguments test *> $Log
    $testExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Get-Content $Log -Tail 25
    exit $testExit
} finally {
    Remove-Item Env:SC_TEST_DB_PASSWORD, Env:SC_TEST_DB_URL, Env:SC_TEST_DB_USER -ErrorAction SilentlyContinue
    $temporaryPassword = $null

    # Clear the stored hash so the role keeps no credential between runs.
    # This runs before exit, and exit inside the try still reaches it.
    # Cleanup must never throw, or it would mask the test run's exit code.
    $ErrorActionPreference = 'Continue'
    "ALTER ROLE study_copilot_test_runner PASSWORD NULL;" |
        docker exec -i $Container sh -c 'exec psql -U "$POSTGRES_USER" -d study_copilot_test -v ON_ERROR_STOP=1' 2>&1 | Out-Null
    $ErrorActionPreference = 'Stop'
}

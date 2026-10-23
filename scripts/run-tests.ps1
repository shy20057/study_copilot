param(
    [string]$Selection,
    [string]$Log = "target/test-output.log",
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
#   pwsh scripts/run-tests.ps1
#   pwsh scripts/run-tests.ps1 -Selection "AuthApiTest"
#
# Requires the PostgreSQL container to be running with study_copilot_test
# already provisioned. See docs/backend-local-development.md.

$ErrorActionPreference = 'Stop'

if (-not $Container) { $Container = 'procurement-stack-postgres-1' }
if (-not $DbUrl) {
    $DbUrl = "jdbc:postgresql://127.0.0.1:$DbPort/study_copilot_test?currentSchema=study_copilot"
}

$temporaryPassword = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
try {
    "ALTER ROLE study_copilot_test_runner PASSWORD '$temporaryPassword';" |
        docker exec -i $Container sh -c 'exec psql -U "$POSTGRES_USER" -d study_copilot_test -v ON_ERROR_STOP=1' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Test role password setup failed' }

    $env:SC_TEST_DB_URL = $DbUrl
    $env:SC_TEST_DB_USER = 'study_copilot_test_runner'
    $env:SC_TEST_DB_PASSWORD = $temporaryPassword

    $arguments = @('-B', '-ntp', '-f', 'backend/pom.xml', '-Dspring.profiles.active=test')
    if ($Selection) { $arguments += "-Dtest=$Selection" }
    & mvn @arguments test *> $Log
    $testExit = $LASTEXITCODE
    Get-Content $Log -Tail 25
    exit $testExit
} finally {
    Remove-Item Env:SC_TEST_DB_PASSWORD, Env:SC_TEST_DB_URL, Env:SC_TEST_DB_USER -ErrorAction SilentlyContinue
    $temporaryPassword = $null
}

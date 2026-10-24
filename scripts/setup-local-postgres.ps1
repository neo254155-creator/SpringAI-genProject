[CmdletBinding()]
param(
    [string]$ServerHost = 'localhost',
    [ValidateRange(1, 65535)][int]$Port = 5432,
    [string]$AdminUser = 'postgres',
    [ValidatePattern('^[a-z_][a-z0-9_]{0,62}$')][string]$AppUser = 'genproject',
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\18\bin\psql.exe',
    [string]$ApplicationHost = ''
)

$ErrorActionPreference = 'Stop'
$DatabaseName = 'InterviewData'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath $PsqlPath)) {
    throw "psql.exe was not found. Supply its full path with -PsqlPath."
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 21 or newer must be available on PATH.'
}
if (-not $ApplicationHost) { $ApplicationHost = $ServerHost }

function Read-PlainPassword([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Invoke-Postgres([string]$Database, [string]$User, [string]$Sql) {
    # Credentials are supplied through PGPASSWORD, never command-line arguments.
    $output = $Sql | & $PsqlPath -X -w -h $ServerHost -p $Port -U $User -d $Database `
        -v ON_ERROR_STOP=1 -t -A -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL command failed for database '$Database'. Check the server, login, and permissions."
    }
    return ($output -join "`n").Trim()
}

function ConvertTo-JavaProperty([string]$Value) {
    $encoded = New-Object System.Text.StringBuilder
    foreach ($character in $Value.ToCharArray()) {
        $code = [int]$character
        if ($code -eq 92) { [void]$encoded.Append('\\') }
        elseif ($code -le 32 -or $code -gt 126) { [void]$encoded.Append(('\u{0:x4}' -f $code)) }
        else { [void]$encoded.Append($character) }
    }
    return $encoded.ToString()
}

$PreviousPassword = $env:PGPASSWORD
$PreviousTimeout = $env:PGCONNECT_TIMEOUT
$AdminPassword = $null
$ApplicationPassword = $null
Push-Location $ProjectRoot
try {
    $AdminPassword = Read-PlainPassword "Password for PostgreSQL administrator '$AdminUser'"
    $ApplicationPassword = Read-PlainPassword "Password for app login '$AppUser' (choose one if the login is new)"
    if ([string]::IsNullOrWhiteSpace($ApplicationPassword)) {
        throw 'The app login password must not be empty.'
    }
    $env:PGCONNECT_TIMEOUT = '5'
    $env:PGPASSWORD = $AdminPassword

    $owner = Invoke-Postgres 'postgres' $AdminUser `
        "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = '$DatabaseName';"
    if ($owner -and $owner -ne $AppUser) {
        throw "Database '$DatabaseName' already belongs to '$owner'. No ownership changes were made."
    }
    $roleExists = Invoke-Postgres 'postgres' $AdminUser `
        "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$AppUser');"
    if ($roleExists -eq 'f') {
        $escapedPassword = $ApplicationPassword.Replace("'", "''")
        # AppUser is restricted by ValidatePattern above. Password is SQL-quoted.
        [void](Invoke-Postgres 'postgres' $AdminUser `
            "SET standard_conforming_strings = on; CREATE ROLE `"$AppUser`" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$escapedPassword';")
    }
    # Existing logins are never reset. Check the supplied app password first.
    $env:PGPASSWORD = $ApplicationPassword
    [void](Invoke-Postgres 'postgres' $AppUser 'SELECT 1;')
    $env:PGPASSWORD = $AdminPassword
    if (-not $owner) {
        [void](Invoke-Postgres 'postgres' $AdminUser `
            "CREATE DATABASE `"$DatabaseName`" OWNER `"$AppUser`";")
    }
    $env:PGPASSWORD = $ApplicationPassword
    $connectedDatabase = Invoke-Postgres $DatabaseName $AppUser 'SELECT current_database();'
    if ($connectedDatabase -ne $DatabaseName) { throw 'Unexpected database connection.' }

    $localDirectory = Join-Path $ProjectRoot '.local'
    [void](New-Item -ItemType Directory -Force -Path $localDirectory)
    $localFile = Join-Path $localDirectory 'database.properties'
    $settings = @(
        ('local.database.url=' + (ConvertTo-JavaProperty "jdbc:postgresql://${ApplicationHost}:${Port}/$DatabaseName")),
        ('local.database.user=' + (ConvertTo-JavaProperty $AppUser)),
        ('local.database.password=' + (ConvertTo-JavaProperty $ApplicationPassword))
    )
    [IO.File]::WriteAllLines($localFile, $settings, (New-Object Text.UTF8Encoding($false)))
    Write-Host "Database '$DatabaseName' exists and the app login connected successfully."
    Write-Host 'Connection settings saved to .local/database.properties (ignored by Git).'

    # Run the actual project migrations without starting Spring or calling Groq.
    & .\mvnw.cmd -q dependency:build-classpath '-Dmdep.outputFile=target/setup-classpath.txt' '-DincludeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Could not build the Java dependency classpath. Database creation succeeded; migration did not run.' }
    $classpath = (Get-Content -Raw 'target/setup-classpath.txt').Trim()
    & java --class-path $classpath 'scripts/LocalDatabaseSetup.java' $ProjectRoot
    if ($LASTEXITCODE -ne 0) { throw 'Database verification/migration failed. Check the output above.' }
    Write-Host 'Setup complete. Start the project from this directory after configuring the Groq key.'
}
finally {
    $env:PGPASSWORD = $PreviousPassword
    $env:PGCONNECT_TIMEOUT = $PreviousTimeout
    $AdminPassword = $null
    $ApplicationPassword = $null
    Pop-Location
}

# Injects a p2 category IU into local-repo/p2/pde/content.jar so that
# "Help -> Install New Software" groups the PDE feature under a named category.
# Idempotent: safe to run multiple times.
param(
    [Parameter(Mandatory)][string]$RepoDir   # path to local-repo/p2/pde
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

$jar = Join-Path $RepoDir 'content.jar'
if (-not (Test-Path $jar)) { Write-Error "Not found: $jar"; exit 1 }

$tmp = Join-Path $env:TEMP 'pde_content_patch'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
[IO.Compression.ZipFile]::ExtractToDirectory($jar, $tmp)

$xmlFile = Join-Path $tmp 'content.xml'
$xml = Get-Content $xmlFile -Raw

if ($xml -match 'com\.example\.automation\.pde\.category') {
    Write-Host 'Category IU already present — skipping patch.'
    Remove-Item $tmp -Recurse -Force
    exit 0
}

# Determine new unit count
$m = [regex]::Match($xml, "<units size='(\d+)'>")
$newCount = [int]$m.Groups[1].Value + 1

$categoryUnit = @"
  <unit id='com.example.automation.pde.category' version='1.0.0'>
    <update id='com.example.automation.pde.category' range='[0.0.0,1.0.0]' severity='0'/>
    <properties size='2'>
      <property name='org.eclipse.equinox.p2.name' value='Eclipse Plug-in Development Environment'/>
      <property name='org.eclipse.equinox.p2.type.category' value='true'/>
    </properties>
    <provides size='1'>
      <provided namespace='org.eclipse.equinox.p2.iu' name='com.example.automation.pde.category' version='1.0.0'/>
    </provides>
    <requires size='1'>
      <required namespace='org.eclipse.equinox.p2.iu' name='org.eclipse.pde.feature.group' range='[3.15.0.v20230605-0440,3.15.0.v20230605-0440]'/>
    </requires>
    <touchpoint id='null' version='0.0.0'/>
  </unit>
"@

$xml = $xml -replace "<units size='\d+'>", "<units size='$newCount'>"
$xml = $xml -replace '</units>', "$categoryUnit</units>"

[System.IO.File]::WriteAllText($xmlFile, $xml, [System.Text.Encoding]::UTF8)

$newJar = Join-Path $env:TEMP 'content_patched.jar'
if (Test-Path $newJar) { Remove-Item $newJar -Force }
[IO.Compression.ZipFile]::CreateFromDirectory($tmp, $newJar, [IO.Compression.CompressionLevel]::Optimal, $false)
Copy-Item $newJar $jar -Force
Remove-Item $tmp -Recurse -Force
Remove-Item $newJar -Force

Write-Host "Category IU injected. content.jar: $((Get-Item $jar).Length) bytes"

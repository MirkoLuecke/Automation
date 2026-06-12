# Injects a p2 category IU into a mirrored p2 repo's content.jar so that
# "Help -> Install New Software" groups the feature under a named category.
# Idempotent: safe to run multiple times.
#
# The feature version is read automatically from content.xml — no need to
# hard-code it here.
param(
    [Parameter(Mandatory)][string]$RepoDir,        # path to p2 repo (contains content.jar)
    [Parameter(Mandatory)][string]$CategoryId,     # e.g. com.example.automation.pde.category
    [Parameter(Mandatory)][string]$CategoryLabel,  # e.g. Eclipse Plug-in Development Environment
    [Parameter(Mandatory)][string]$FeatureGroupId  # e.g. org.eclipse.pde.feature.group
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

$jar = Join-Path $RepoDir 'content.jar'
if (-not (Test-Path $jar)) { Write-Error "Not found: $jar"; exit 1 }

$tmp = Join-Path $env:TEMP "p2_category_patch_$([System.IO.Path]::GetRandomFileName())"
[IO.Compression.ZipFile]::ExtractToDirectory($jar, $tmp)

$xmlFile = Join-Path $tmp 'content.xml'
$xml = Get-Content $xmlFile -Raw

if ($xml -match [regex]::Escape($CategoryId)) {
    Write-Host "Category '$CategoryId' already present — skipping patch."
    Remove-Item $tmp -Recurse -Force
    exit 0
}

# Read the feature version from content.xml
$versionMatch = [regex]::Match($xml, "<unit id='$([regex]::Escape($FeatureGroupId))' version='([^']+)'")
if (-not $versionMatch.Success) {
    Write-Error "Feature '$FeatureGroupId' not found in content.xml"
    Remove-Item $tmp -Recurse -Force
    exit 1
}
$featureVersion = $versionMatch.Groups[1].Value
Write-Host "Found $FeatureGroupId at version $featureVersion"

# Determine new unit count
$m = [regex]::Match($xml, "<units size='(\d+)'>")
$newCount = [int]$m.Groups[1].Value + 1

$categoryUnit = @"
  <unit id='$CategoryId' version='1.0.0'>
    <update id='$CategoryId' range='[0.0.0,1.0.0]' severity='0'/>
    <properties size='2'>
      <property name='org.eclipse.equinox.p2.name' value='$CategoryLabel'/>
      <property name='org.eclipse.equinox.p2.type.category' value='true'/>
    </properties>
    <provides size='1'>
      <provided namespace='org.eclipse.equinox.p2.iu' name='$CategoryId' version='1.0.0'/>
    </provides>
    <requires size='1'>
      <required namespace='org.eclipse.equinox.p2.iu' name='$FeatureGroupId' range='[$featureVersion,$featureVersion]'/>
    </requires>
    <touchpoint id='null' version='0.0.0'/>
  </unit>
"@

$xml = $xml -replace "<units size='\d+'>", "<units size='$newCount'>"
$xml = $xml -replace '</units>', "$categoryUnit</units>"

[System.IO.File]::WriteAllText($xmlFile, $xml, [System.Text.Encoding]::UTF8)

$newJar = Join-Path $env:TEMP "content_patched_$([System.IO.Path]::GetRandomFileName()).jar"
[IO.Compression.ZipFile]::CreateFromDirectory($tmp, $newJar, [IO.Compression.CompressionLevel]::Optimal, $false)
Copy-Item $newJar $jar -Force
Remove-Item $tmp -Recurse -Force
Remove-Item $newJar -Force

Write-Host "Category '$CategoryId' injected. content.jar: $((Get-Item $jar).Length) bytes"

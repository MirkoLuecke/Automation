param(
    [Parameter(Mandatory)][string]$RepoDir,
    [Parameter(Mandatory)][string]$CategoryId,
    [Parameter(Mandatory)][string]$CategoryLabel,
    [Parameter(Mandatory)][string]$FeatureGroupId
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

$jar = Join-Path $RepoDir 'content.jar'
if (-not (Test-Path $jar)) {
    Write-Error "Not found: $jar"
    exit 1
}

$tmp = Join-Path $env:TEMP ('p2cat_' + [System.IO.Path]::GetRandomFileName())
[IO.Compression.ZipFile]::ExtractToDirectory($jar, $tmp)

$xmlFile = Join-Path $tmp 'content.xml'
$xml = [System.IO.File]::ReadAllText($xmlFile, [System.Text.Encoding]::UTF8)

if ($xml.IndexOf($CategoryId) -ge 0) {
    Write-Host 'Category already present, skipping.'
    Remove-Item $tmp -Recurse -Force
    exit 0
}

$pattern = "<unit id='" + [regex]::Escape($FeatureGroupId) + "' version='([^']+)'"
$m = [regex]::Match($xml, $pattern)
if (-not $m.Success) {
    Write-Error "Feature '$FeatureGroupId' not found in content.xml"
    Remove-Item $tmp -Recurse -Force
    exit 1
}
$ver = $m.Groups[1].Value
Write-Host "Found $FeatureGroupId at version $ver"

$countMatch = [regex]::Match($xml, "<units size='(\d+)'>")
$newCount = [int]$countMatch.Groups[1].Value + 1

$nl = "`n"
$unit  = "  <unit id='" + $CategoryId + "' version='1.0.0'>" + $nl
$unit += "    <update id='" + $CategoryId + "' range='[0.0.0,1.0.0]' severity='0'/>" + $nl
$unit += "    <properties size='2'>" + $nl
$unit += "      <property name='org.eclipse.equinox.p2.name' value='" + $CategoryLabel + "'/>" + $nl
$unit += "      <property name='org.eclipse.equinox.p2.type.category' value='true'/>" + $nl
$unit += "    </properties>" + $nl
$unit += "    <provides size='1'>" + $nl
$unit += "      <provided namespace='org.eclipse.equinox.p2.iu' name='" + $CategoryId + "' version='1.0.0'/>" + $nl
$unit += "    </provides>" + $nl
$unit += "    <requires size='1'>" + $nl
$unit += "      <required namespace='org.eclipse.equinox.p2.iu' name='" + $FeatureGroupId + "' range='[" + $ver + "," + $ver + "]'/>" + $nl
$unit += "    </requires>" + $nl
$unit += "    <touchpoint id='null' version='0.0.0'/>" + $nl
$unit += "  </unit>" + $nl

$xml = [regex]::Replace($xml, "<units size='\d+'>", ("<units size='" + $newCount + "'>"))
$xml = $xml.Replace('</units>', ($unit + '</units>'))

[System.IO.File]::WriteAllText($xmlFile, $xml, [System.Text.Encoding]::UTF8)

$newJar = Join-Path $env:TEMP ('content_new_' + [System.IO.Path]::GetRandomFileName() + '.jar')
[IO.Compression.ZipFile]::CreateFromDirectory($tmp, $newJar, [IO.Compression.CompressionLevel]::Optimal, $false)
Copy-Item $newJar $jar -Force
Remove-Item $tmp -Recurse -Force
Remove-Item $newJar -Force

Write-Host "Done. content.jar: $((Get-Item $jar).Length) bytes"

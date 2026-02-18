# Upload Background Image to DigiCache Box
# Uploads a background text or building image to a specific box

param(
    [Parameter(Mandatory=$true)]
    [string]$ImagePath,
    
    [Parameter(Mandatory=$true)]
    [string]$BoxId,
    
    [string]$Type = "background",  # Changed default to "background"
    [string]$Url = "https://digicache-backend-production.up.railway.app/api/images/background/upload",  # Changed to background endpoint
    [int]$TcpTimeoutMs = 3000
)

# Load required assemblies
Add-Type -AssemblyName System.Net.Http

function Test-TcpPort {
    param($HostName, $Port, $TimeoutMs)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        $success = $iar.AsyncWaitHandle.WaitOne($TimeoutMs)
        if (-not $success) {
            $client.Close()
            return $false
        }
        $client.EndConnect($iar)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

Write-Host "Uploading Background ($Type) to box: $BoxId"
Write-Host " File: $(Split-Path -Leaf $ImagePath)"
Write-Host " Size: $((Get-Item $ImagePath).Length) bytes"
Write-Host ""
Write-Host "Target URL: $Url"

# Parse host and port
try {
    $uri = [System.Uri] $Url
} catch {
    Write-Error "Invalid URL: $Url"
    exit 1
}
# use non-reserved variable names
$targetHost = $uri.Host
$targetPort = if ($uri.Port -gt 0) { $uri.Port } else { if ($uri.Scheme -eq "https") {443} else {80} }

Write-Host "Resolving host: ${targetHost}"
try {
    $resolvedIps = [System.Net.Dns]::GetHostAddresses($targetHost) | ForEach-Object { $_.IPAddressToString } 
    Write-Host "Resolved IPs: $($resolvedIps -join ', ')"
} catch {
    Write-Warning "DNS resolution failed for ${targetHost}: $($_)"
}

Write-Host "Testing TCP connectivity to ${targetHost}:${targetPort} ..."
if (-not (Test-TcpPort -HostName $targetHost -Port $targetPort -TimeoutMs $TcpTimeoutMs)) {
    Write-Error "TCP connection to ${targetHost}:${targetPort} failed. Backend may not be reachable from this host or port is blocked."
    exit 2
}
Write-Host "TCP connectivity OK"

# Prepare multipart form
if (-not (Test-Path $ImagePath)) {
    Write-Error "Image file not found: $ImagePath"
    exit 3
}

Write-Host "Sending multipart POST using HttpClient..."
try {
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $true
    $client = New-Object System.Net.Http.HttpClient($handler)
    $multipart = New-Object System.Net.Http.MultipartFormDataContent

    $fileStream = [System.IO.File]::OpenRead($ImagePath)
    $fileContent = New-Object System.Net.Http.StreamContent($fileStream)
    # Optional: set a generic content type; server will detect if needed
    try {
        $contentType = [System.Net.Mime.MediaTypeNames]::Image 2>$null
        # don't rely on this; keep default if not available
    } catch { }
    $fileName = [System.IO.Path]::GetFileName($ImagePath)
    $fileContent.Headers.ContentDisposition = New-Object System.Net.Http.Headers.ContentDispositionHeaderValue("form-data")
    $fileContent.Headers.ContentDisposition.Name = '"file"'
    $fileContent.Headers.ContentDisposition.FileName = '"' + $fileName + '"'
    # add file
    $null = $multipart.Add($fileContent, "file", $fileName)
    # add other fields
    $null = $multipart.Add( (New-Object System.Net.Http.StringContent($BoxId)), "boxId")
    # Removed "type" parameter - not needed for background endpoint

    $response = $client.PostAsync($Url, $multipart).Result
    $body = $response.Content.ReadAsStringAsync().Result

    # cleanup
    $fileStream.Close()
    $multipart.Dispose()
    $client.Dispose()

    if ($response.IsSuccessStatusCode) {
        Write-Host "Upload successful. HTTP $($response.StatusCode.value__)"
        Write-Host "Response: $body"
        exit 0
    } else {
        Write-Error "Upload failed. HTTP $($response.StatusCode.value__)"
        Write-Error "Response: $body"
        exit 4
    }
} catch {
    Write-Error "Upload failed: $($_.Exception.Message)"
    if ($_.Exception.InnerException) {
        Write-Error "Inner exception: $($_.Exception.InnerException.Message)"
    }
    exit 5
}

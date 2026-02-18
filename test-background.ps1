# Test if background image exists for a box

param([string]$BoxId = "iovine-and-young")

Write-Host "`nTesting background image for box: $BoxId" -ForegroundColor Cyan
Write-Host "=========================================`n" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/images/background/$BoxId" -Method GET
    
    Write-Host " SUCCESS! Background image found!" -ForegroundColor Green
    Write-Host " Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host " Content Type: $($response.Headers['Content-Type'])" -ForegroundColor Yellow
    Write-Host " Size: $($response.RawContentLength) bytes`n" -ForegroundColor Yellow
    Write-Host " URL: http://localhost:8080/api/images/background/$BoxId`n" -ForegroundColor Cyan
}
catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        Write-Host " No background image found for box: $BoxId" -ForegroundColor Yellow
        Write-Host " Upload one with:" -ForegroundColor Yellow
        Write-Host " .\upload-background.ps1 -ImagePath 'path\to\image.png' -BoxId '$BoxId'`n" -ForegroundColor Cyan
    }
    else {
        Write-Host " Error: $($_.Exception.Message)`n" -ForegroundColor Red
    }
}

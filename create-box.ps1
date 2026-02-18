# Simple Box Creator for DigiCache
# This creates a box by calling the backend API

param([string]$BoxId = "my-new-box")

$body = "{`"boxId`":`"$BoxId`"}"

try {
    Write-Host "`n Creating box: $BoxId" -ForegroundColor Cyan
    Write-Host "================================`n" -ForegroundColor Cyan
    
    $result = Invoke-RestMethod -Uri "http://localhost:8080/api/images/boxes" `
                                -Method POST `
                                -ContentType "application/json" `
                                -Body $body
    
    Write-Host " SUCCESS!" -ForegroundColor Green
    Write-Host " Box '$BoxId' created!`n" -ForegroundColor Green
    Write-Host " Access it at: http://localhost:5173/$BoxId`n" -ForegroundColor Yellow
}
catch {
    Write-Host " FAILED!" -ForegroundColor Red
    Write-Host " Error: $($_.Exception.Message)`n" -ForegroundColor Red
    Write-Host " Make sure the backend is running on port 8080`n" -ForegroundColor Yellow
}

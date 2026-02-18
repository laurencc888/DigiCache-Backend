# Create 20 numbered boxes (001-020) for DigiCache
# Usage: .\create-20-boxes.ps1

$apiUrl = "https://digicache-backend-production.up.railway.app/api/images/boxes"

Write-Host "`n==================================" -ForegroundColor Cyan
Write-Host " Creating 20 DigiCache Boxes" -ForegroundColor Cyan
$successCount = 0
$failCount = 0

for ($i = 1; $i -le 20; $i++) {
    # Format number as 001, 002, 003, etc.
    $boxId = "{0:D3}" -f $i
    
    $body = @{
        boxId = $boxId
    } | ConvertTo-Json
    
    Write-Host "Creating box: $boxId ... " -NoNewline
    
    try {
        $response = Invoke-RestMethod -Uri $apiUrl `
                                      -Method POST `
                                      -ContentType "application/json" `
                                      -Body $body `
                                      -ErrorAction Stop
        
        Write-Host "SUCCESS" -ForegroundColor Green
        $successCount++
        
        # Small delay to avoid overwhelming the server
        Start-Sleep -Milliseconds 100
    }
    catch {
        Write-Host "FAILED" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
        $failCount++
    }
}

Write-Host "`n==================================" -ForegroundColor Cyan
Write-Host " Summary" -ForegroundColor Cyan
Write-Host "==================================`n" -ForegroundColor Cyan
Write-Host " Success: $successCount boxes created" -ForegroundColor Green
Write-Host " Failed:  $failCount boxes" -ForegroundColor Red
Write-Host "`n Access boxes at:" -ForegroundColor Yellow
Write-Host " http://localhost:5173/001" -ForegroundColor Cyan
Write-Host " http://localhost:5173/002" -ForegroundColor Cyan
Write-Host " ..." -ForegroundColor Cyan
Write-Host " http://localhost:5173/020`n" -ForegroundColor Cyan

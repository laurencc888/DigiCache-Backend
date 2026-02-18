# Add a box to DigiCache database
# Usage: .\add-box.ps1 "my-custom-box-id"

param(
    [Parameter(Mandatory=$true)]
    [string]$BoxId
)

$apiUrl = "http://localhost:8080/api/images/boxes"
$body = @{
    boxId = $BoxId
} | ConvertTo-Json

Write-Host "Creating box with ID: $BoxId" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri $apiUrl -Method POST -ContentType "application/json" -Body $body
    Write-Host "Success! Box created successfully!" -ForegroundColor Green
    Write-Host $response.Content -ForegroundColor White
}
catch {
    Write-Host "Failed to create box" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Make sure the Java backend is running!" -ForegroundColor Yellow
    Write-Host "Run this command to start it:" -ForegroundColor Yellow
    Write-Host "  cd C:\Users\Maia\digicache-workspace\DigiCache" -ForegroundColor Cyan
    Write-Host "  C:\Java\jdk-21\bin\java.exe -jar target\digicache-1.0-SNAPSHOT.jar" -ForegroundColor Cyan
}

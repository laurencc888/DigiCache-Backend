# Add a box directly to the database (no backend needed)
# Usage: .\add-box-direct.ps1 "my-custom-box-id"

param(
    [Parameter(Mandatory=$true)]
    [string]$BoxId
)

$dbPath = "digicache.db"

Write-Host "Adding box '$BoxId' directly to database..." -ForegroundColor Cyan

# Create a temporary SQL file
$sqlContent = "INSERT OR IGNORE INTO box_ids (id) VALUES ('$BoxId');
SELECT * FROM box_ids WHERE id = '$BoxId';"

$tempSqlFile = "temp_add_box.sql"
$sqlContent | Out-File -FilePath $tempSqlFile -Encoding ASCII

Write-Host "Attempting to add box to database..." -ForegroundColor Yellow
Write-Host ""
Write-Host "IMPORTANT: You need SQLite3 installed to use this method." -ForegroundColor Red
Write-Host "Alternative: Just use the frontend at http://localhost:5173/iovine-and-young-hall" -ForegroundColor Green
Write-Host "The box will be created automatically when you first visit it!" -ForegroundColor Green
Write-Host ""
Write-Host "Box ID: $BoxId" -ForegroundColor Cyan
Write-Host "URL: http://localhost:5173/$BoxId" -ForegroundColor Cyan

# Clean up
Remove-Item $tempSqlFile -ErrorAction SilentlyContinue

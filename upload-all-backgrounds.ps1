# Upload background images for boxes 001-020
# Usage: .\upload-all-backgrounds.ps1 -ImageDirectory "C:\Users\Maia\Downloads"

param(
    [Parameter(Mandatory=$true)]
    [string]$ImageDirectory
)

Write-Host "`n==================================" -ForegroundColor Cyan
Write-Host " Batch Background Image Uploader" -ForegroundColor Cyan
Write-Host "==================================`n" -ForegroundColor Cyan

$successCount = 0
$failCount = 0
$skippedCount = 0

# Loop through boxes 001 to 020
for ($i = 1; $i -le 20; $i++) {
    $boxId = "{0:D3}" -f $i  # Format as 001, 002, etc.
    $imagePath = Join-Path $ImageDirectory "$boxId.png"
    
    Write-Host "[$i/20] Processing box: $boxId" -ForegroundColor Yellow
    
    # Check if image file exists
    if (-not (Test-Path $imagePath)) {
        Write-Host "  [SKIP] Image not found: $imagePath" -ForegroundColor DarkYellow
        $skippedCount++
        continue
    }
    
    try {
        # Get file size for logging
        $fileSize = (Get-Item $imagePath).Length
        Write-Host "  [FILE] $boxId.png ($fileSize bytes)" -ForegroundColor Gray
        
        # Upload using the existing upload-background.ps1 script
        $result = & "$PSScriptRoot\upload-background.ps1" -ImagePath $imagePath -BoxId $boxId 2>&1
        
        if ($LASTEXITCODE -eq 0 -or $result -match "Upload successful") {
            Write-Host "  [OK] Successfully uploaded to box $boxId" -ForegroundColor Green
            $successCount++
        } else {
            Write-Host "  [FAIL] Failed to upload to box $boxId" -ForegroundColor Red
            $failCount++
        }
    }
    catch {
        Write-Host "  [ERROR] $($_.Exception.Message)" -ForegroundColor Red
        $failCount++
    }
    
    Write-Host ""  # Blank line between uploads
    Start-Sleep -Milliseconds 500  # Small delay to avoid overwhelming the server
}

# Summary
Write-Host "`n==================================" -ForegroundColor Cyan
Write-Host " Upload Summary" -ForegroundColor Cyan
Write-Host "==================================`n" -ForegroundColor Cyan
Write-Host "  [SUCCESS] $successCount uploads" -ForegroundColor Green
Write-Host "  [FAILED]  $failCount uploads" -ForegroundColor Red
Write-Host "  [SKIPPED] $skippedCount images" -ForegroundColor Yellow
Write-Host "  --------------------------" -ForegroundColor Gray
Write-Host "  [TOTAL]   20 images`n" -ForegroundColor White

if ($successCount -eq 20) {
    Write-Host "All backgrounds uploaded successfully!`n" -ForegroundColor Green
} elseif ($failCount -gt 0) {
    Write-Host "Some uploads failed. Check the output above for details.`n" -ForegroundColor Yellow
}

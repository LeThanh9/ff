$Host.UI.RawUI.WindowTitle = "FF Master - Build APK"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   FF MASTER - DONG GOI VA BIEN DICH FILE APK" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# Check Java
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    $possibleJdk = Get-ChildItem -Path 'C:\Program Files\Java', 'C:\Program Files\Microsoft', 'C:\Program Files\Eclipse Adoptium' -Filter 'java.exe' -Recurse -Depth 3 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($possibleJdk) {
        $env:JAVA_HOME = Split-Path (Split-Path $possibleJdk.FullName)
        $env:PATH = "$($possibleJdk.DirectoryName);$env:PATH"
        Write-Host "[INFO] Da tim thay Java tai: $($possibleJdk.FullName)" -ForegroundColor Green
    }
}

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "[CANH BAO] May tinh cua ban CHUA CO JAVA (JDK 17)!" -ForegroundColor Yellow
    Write-Host "De bien dich gradlew tren may tinh, ban can cai dat Java JDK 17:" -ForegroundColor White
    Write-Host "Link tai truc tiep (Microsoft):" -ForegroundColor Cyan
    Write-Host "https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.msi" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Sau khi tai va bam Next cai dat xong, hay chay lai file nay de dong goi APK!" -ForegroundColor Green
    Write-Host ""
    Write-Host "==============================================================" -ForegroundColor DarkGray
    Write-Host "HOAC DUNG GITHUB ACTIONS (Khong can cai bat cu gi tren may):" -ForegroundColor White
    Write-Host "File workflow .github/workflows/build_apk.yml da san sang trong du an." -ForegroundColor DarkGray
    Write-Host "Chi can dua du an len GitHub ca nhan, GitHub se tu dong build" -ForegroundColor DarkGray
    Write-Host "va xuat file APK de ban tai ve truc tiep sau 2 phut!" -ForegroundColor DarkGray
    Write-Host "==============================================================" -ForegroundColor DarkGray
    Read-Host "Nhan Enter de thoat..."
    exit
}

Write-Host "[INFO] Dang khoi chay Gradle Wrapper (./gradlew.bat assembleDebug)..." -ForegroundColor Green
cmd.exe /c "gradlew.bat assembleDebug"
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host "[THANH CONG] File APK da duoc tao tai:" -ForegroundColor Green
    Write-Host "app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Yellow
    Write-Host "========================================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[CHU Y] Gradle gap loi. Neu thieu Android SDK/NDK tren may, hay dung GitHub Actions de build dam may." -ForegroundColor Red
}
Read-Host "Nhan Enter de thoat..."

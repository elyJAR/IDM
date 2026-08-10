$ErrorActionPreference = 'Stop'

$JDK_PATH = "C:\Users\ElyJah\Documents\AntiGravity_Projects\MovieBox\tools\jdk-17.0.10+7"
$ANDROID_SDK = "C:\Users\ElyJah\AppData\Local\Android\Sdk"

Write-Host "Setting up Environment Variables..."
$env:JAVA_HOME = $JDK_PATH
$env:ANDROID_HOME = $ANDROID_SDK

# We found Gradle 8.4 already cached on your system! No need to download anything.
$GRADLE_BIN = "C:\Users\ElyJah\.gradle\wrapper\dists\gradle-8.4-bin\1w5dpkrfk8irigvoxmyhowfim\gradle-8.4\bin\gradle.bat"

Write-Host "Running your cached Gradle to build Debug APK..."
& $GRADLE_BIN assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n==============================================" -ForegroundColor Green
    Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "Your APK is located at: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
    Write-Host "==============================================" -ForegroundColor Green
} else {
    Write-Host "`nBUILD FAILED." -ForegroundColor Red
}

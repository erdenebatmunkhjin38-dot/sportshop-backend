<#
  SportShop өгөгдлийн сан + админы оруулсан зургийн өдөр тутмын backup.

  Нэг удаагийн тохиргоо:
    1. MySQL-ийн нэвтрэх мэдээллийг git-д орохгүй файлд хадгална:
         %USERPROFILE%\.shop-backup.cnf
       Агуулга:
         [client]
         user=root
         password=ТАНЫ_НУУЦ_ҮГ
    2. Гараар нэг удаа ажиллуулж шалгана:
         powershell -ExecutionPolicy Bypass -File C:\Java\shop\scripts\backup-db.ps1
    3. Өдөр бүр автоматаар ажиллуулах (PRODUCTION.md-д заасан Task Scheduler команд).

  Үр дүн: <BackupDir>\onlineshop-YYYY-MM-DD_HHmm.zip (SQL dump + uploads хавтас).
  KeepDays-ээс хуучин backup-ууд автоматаар устна.
#>
param(
  [string]$Database   = "onlineshop",
  [string]$BackupDir  = "C:\ShopBackups",
  [string]$UploadsDir = "C:\Users\Munkhjin\next-app\uploads",
  [string]$ConfigFile = "$env:USERPROFILE\.shop-backup.cnf",
  [string]$MysqlDump  = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe",
  [int]$KeepDays      = 14
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ConfigFile)) { throw "MySQL тохиргооны файл олдсонгүй: $ConfigFile (скриптийн толгой дахь зааврыг үзнэ үү)" }
if (-not (Test-Path $MysqlDump))  { throw "mysqldump олдсонгүй: $MysqlDump" }
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$stamp   = Get-Date -Format "yyyy-MM-dd_HHmm"
$workDir = Join-Path $BackupDir "tmp-$stamp"
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

try {
  # 1. Өгөгдлийн сан. --single-transaction: сайт ажиллаж байх үед ч зөрчилгүй хуулбар авна
  $sqlFile = Join-Path $workDir "$Database.sql"
  & $MysqlDump "--defaults-extra-file=$ConfigFile" --single-transaction --routines --triggers `
      --default-character-set=utf8mb4 "--result-file=$sqlFile" $Database
  if ($LASTEXITCODE -ne 0) { throw "mysqldump амжилтгүй (код $LASTEXITCODE)" }
  if ((Get-Item $sqlFile).Length -lt 1000) { throw "Dump файл хэт жижиг байна — алдаа гарсан байж магадгүй" }

  # 2. Админы оруулсан зургууд
  if (Test-Path $UploadsDir) { Copy-Item $UploadsDir (Join-Path $workDir "uploads") -Recurse }

  # 3. Нэг zip болгоно
  $zip = Join-Path $BackupDir "$Database-$stamp.zip"
  Compress-Archive -Path (Join-Path $workDir "*") -DestinationPath $zip -Force
  Write-Output "Backup бэлэн: $zip ($([math]::Round((Get-Item $zip).Length / 1MB, 2)) MB)"
}
finally {
  Remove-Item $workDir -Recurse -Force -ErrorAction SilentlyContinue
}

# 4. Хуучин backup-уудыг устгана
Get-ChildItem $BackupDir -Filter "$Database-*.zip" |
  Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } |
  ForEach-Object { Remove-Item $_.FullName -Force; Write-Output "Хуучин backup устгалаа: $($_.Name)" }

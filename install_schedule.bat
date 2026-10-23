@echo off
rem 建立每天 09:05 執行的工作排程（錯過時開機後補跑）
schtasks /Create /F /TN "DailyFinanceDigest" /SC DAILY /ST 09:05 /TR "\"%~dp0run.bat\""
powershell -NoProfile -Command "$t=Get-ScheduledTask DailyFinanceDigest; $t.Settings.StartWhenAvailable=$true; $t.Settings.DisallowStartIfOnBatteries=$false; Set-ScheduledTask $t | Out-Null"

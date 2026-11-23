from pathlib import Path
import json,subprocess,sys
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-catalog-255';D=B/'ui-preview-1'
source=(R/'build/close_mrpresso_owned_browser_20261123_181.cjs').read_text(encoding='utf-8');assert source.count('mrpresso-catalog-178')==1
(R/'build/close_smartpca_owned_browser_20261123_259.cjs').write_text(source.replace('mrpresso-catalog-178','smartpca-catalog-255'),encoding='utf-8',newline='\n')
runner=R/'build/run_smartpca_catalog_ui_20261123_258.py';text=runner.read_text(encoding='utf-8');assert text.count('close_mrpresso_owned_browser_20261123_181.cjs')==1
runner.write_text(text.replace('close_mrpresso_owned_browser_20261123_181.cjs','close_smartpca_owned_browser_20261123_259.cjs'),encoding='utf-8',newline='\n')
node=Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node');command=[str(node/'bin/node.exe'),str(R/'build/close_smartpca_owned_browser_20261123_259.cjs'),str(node/'node_modules/playwright'),str(D/'owned-edge-profile')]
p=subprocess.run(command,cwd=T,capture_output=True);(D/'corrected-close.stdout').write_bytes(p.stdout);(D/'corrected-close.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr
shell='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'
code="$p='D:\\projects\\JLinAlg\\build\\fu-1\\build\\smartpca-catalog-255\\ui-preview-1\\owned-edge-profile'; $a=@(Get-CimInstance Win32_Process -Filter \"Name='msedge.exe'\" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($p) } | Select-Object ProcessId,ParentProcessId,CreationDate); [Console]::Out.Write((ConvertTo-Json -InputObject $a -Compress))"
q=subprocess.run([shell,'-NoLogo','-NoProfile','-NonInteractive','-Command',code],capture_output=True);assert q.returncode==0 and json.loads(q.stdout.decode('utf-8-sig'))==[],q.stderr
(D/'GUARD-RECOVERY.json').write_text(json.dumps(dict(status='RETAINED_OLD_TASK_GUARD_FAILURE_OWN_BROWSER_CONFIRMED_CLOSED',initial_node_page_check_exit=0,initial_close_guard_exit=json.loads((D/'CLOSE.json').read_bytes())['exit_code'],corrected_exact_profile_close_exit=0,own_profile_processes_after=0,unrelated_processes_stopped=0,original_failure_retained=True),indent=2)+'\n')
print('Own browser closure confirmed; previous failed guard evidence retained; fresh preview will test corrected helper.')

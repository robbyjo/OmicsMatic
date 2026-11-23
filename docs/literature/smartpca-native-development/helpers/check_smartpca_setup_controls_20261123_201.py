from pathlib import Path
import json,subprocess,sys
from handover_inventory_20261122_111 import pin,save
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/smartpca-1';out=B/'setup-controls-1';out.mkdir()
script=T/'tools/literature/setup_smartpca_reference.py';intake=B/'source-intake-1';source_before=pin(script)
cases=[]
for name,host,flags in [('install-not-authorized','windows',[]),('linux-required','windows',['--allow-install']),('installer-missing','linux',['--allow-install'])]:
 target=out/name
 if host=='windows':command=[sys.executable,'-X','utf8','-B',str(script),'--intake',str(intake),'--micromamba',str(out/'nonexistent-installer'),'--out',str(target),*flags]
 else:
  def linux(path):p=Path(path).as_posix();return '/mnt/'+p[0].lower()+p[2:]
  command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','python3','-B',linux(script),'--intake',linux(intake),'--micromamba',linux(out/'nonexistent-installer'),'--out',linux(target),*flags]
 result=subprocess.run(command,cwd=T,capture_output=True,timeout=120)
 (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
 saved=json.loads((target/'COMPLETED.json').read_bytes());assert result.returncode==3 and saved['status']=='SKIP3_SMARTPCA_REFERENCE_PREREQUISITE_UNAVAILABLE'
 assert not saved['analysis_executed'] and not (target/'dependency-prefix').exists() and not (target/'source-build').exists()
 assert (target/'WARNINGS.md').read_text().startswith('SKIP3:') and saved['required_action']
 assert pin(target/'setup-source.py')==source_before
 cases.append(dict(name=name,host=host,exit_code=result.returncode,analysis_executed=False,required_action=saved['required_action'],error=saved['error']))
assert pin(script)==source_before
save(out/'COMPLETED.json',dict(status='PASS_THREE_ACTUAL_PREREQUISITE_SKIPS_WITH_REMEDIES',cases=cases,installer_source=source_before,
 installed_or_executed_scientific_analysis=False,catalog_admitted=False))
print('PASS: three actual SKIP3 controls, explicit remedies, no installation or analysis.')

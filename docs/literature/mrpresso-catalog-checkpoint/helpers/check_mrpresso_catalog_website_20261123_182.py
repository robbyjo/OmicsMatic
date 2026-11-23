from pathlib import Path
import json,os,subprocess,time
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');D=T/'build/mrpresso-catalog-178/website';D.mkdir()
prior=json.loads((T/'docs/literature/squidpy-catalog-checkpoint/VALIDATION.json').read_bytes());expected=prior['preexisting_missing_citation_panels']
for row in expected:assert pin(T/row['path'])==row['identity'],row['path']
found=[]
for folder,suffix,excluded in [('site/vignettes','.html',{'index.html'}),('docs/vignettes','.md',{'README.md'})]:
 for path in sorted((T/folder).glob('*'+suffix)):
  if path.name in excluded:continue
  source=path.read_text(encoding='utf-8')
  if 'SCIENTIFIC-CITATIONS:START' not in source or 'SCIENTIFIC-CITATIONS:END' not in source:found.append(path.relative_to(T).as_posix())
assert set(found)=={x['path'] for x in expected} and len(found)==33,'New/different citation-panel failures'
gradle='C:/Users/User/.gradle/wrapper/dists/gradle-9.7.1-bin/1w1c7tv4s851m17nbqdsro2tv/gradle-9.7.1/bin/gradle.bat'
command=[gradle,'--offline','--no-daemon','--console=plain','-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m','checkWebsite']
env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot');start=time.monotonic()
with (D/'stdout').open('xb') as out,(D/'stderr').open('xb') as err:
 p=subprocess.Popen(command,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/'PROCESS.json',dict(pid=p.pid,command=command));code=p.wait()
combined=(D/'stdout').read_text(errors='replace')+(D/'stderr').read_text(errors='replace')
assert code!=0 and 'lacks a scientific citation panel' in combined and 'broad-gsea-tools' in combined
save(D/'VALIDATION.json',dict(status='RETAINED_PREEXISTING_WEBSITE_CITATION_FAILURE',exit_code=code,seconds=time.monotonic()-start,
 full_checkWebsite_passed=False,preexisting_missing_citation_panels=expected,new_missing_citation_panels=[],
 remedy='Add actual mapped scientific citations for the33 listed existing panels and rerun full checkWebsite; do not weaken the check or insert empty panels.',
 literature_reports_withheld_by_unchanged_Pages_workflow=True,whole_goal_complete=False))
print(json.dumps(dict(exit_code=code,full_checkWebsite_passed=False,existing_missing_citation_panels=33,new_missing_citation_panels=0)),flush=True)

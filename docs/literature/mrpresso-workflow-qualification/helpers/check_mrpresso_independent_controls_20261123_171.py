from pathlib import Path
import json,subprocess
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'independent-controls-1';D.mkdir()
reader=T/'tools/literature/mrpresso_workflow/compare_original.py';controls=B/'saved-controls-2'
normal=B/'original-reference-windows-1/outputs/outlier-stable-precision/original.json'
tiny=B/'extended-reference-windows-1/outputs/tiny-outcome-univ/original.json'
cases=[('valid',normal,B/'workflow-original-references-2/windows21/outlier-stable-precision/analysis',0),
 ('false-main-beta',normal,controls/'false-main-beta',2),('coherent-tiny-inflation',tiny,controls/'coherent-tiny-inflation',2),
 ('false-source-bound',normal,controls/'false-source-bound',2),('false-count-with-coherent-probability',normal,controls/'false-count-with-coherent-probability',2),
 ('false-rng-state',normal,controls/'false-rng-state',2),('false-retained-row-id',normal,controls/'false-retained-row-id',2),('coherent-false-fit',normal,controls/'coherent-false-fit',2)]
def linux(p):
 x=Path(p).as_posix();return '/mnt/'+x[0].lower()+x[2:]
records=[]
for host in ('windows','linux'):
 for label,original,capture,expected in cases:
  if host=='windows':command=['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-B',str(reader),'--original',str(original),'--native-result',str(capture/'native-result.json')]
  else:command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-B',linux(reader),'--original',linux(original),'--native-result',linux(capture/'native-result.json')]
  p=subprocess.run(command,cwd=T,capture_output=True);(D/(host+'-'+label+'.stdout')).write_bytes(p.stdout);(D/(host+'-'+label+'.stderr')).write_bytes(p.stderr)
  row=dict(host=host,case=label,exit_code=p.returncode,expected=expected);records.append(row);save(D/'PROGRESS.json',records)
  assert p.returncode==expected,(row,p.stderr)
save(D/'RESULT.json',dict(status='PASS',reader=pin(reader),cases=records,positive_comparisons=2,scientific_contradictions_refused_per_host=7,
 general_capture_verifier_claim=False,whole_workflow_qualified=False,new_family_credit=0,new_reviews=0))
print('PASS independent original-reference comparator: one valid case and seven scientific contradictions on Windows/Linux.')

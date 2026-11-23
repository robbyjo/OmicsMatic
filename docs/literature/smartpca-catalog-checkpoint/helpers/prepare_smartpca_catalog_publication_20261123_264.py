from pathlib import Path
import json,shutil,sys,subprocess
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-catalog-255';Q=T/'docs/literature/smartpca-catalog-checkpoint'
intro='''**Current:** [smartpca admitted roles, validation and remaining full-goal work](SMARTPCA_CATALOG_CHECKPOINT_2026-11-23.md).
One scoped native family and six source reviews are admitted: **154 scoped
families (29 native+125 upstream), 1,697 reviewed/1,025 pending uses, 250 papers
and 2,722 positive observations**. Prior reviews and unrelated records remain.
Twenty-one model/transaction tests per host, ten actual CLI refusals, fresh
all250 primary checks, the861-entry manifest and styled preview/installed UI pass.
Per-context editions/program IDs remain distinct. The33 existing website citation
errors remain open; both reports remain withheld from Pages. Continue remaining
high-impact gaps and all pending/missed-provider reviews. Preserve private
evidence/runtimes/JARs, all19 worktrees and restricted historical exports. The
full objective is unfinished; lower entries are historical where superseded.

'''
for name in ('docs/literature/HANDOVER_CURRENT.md','docs/literature/HANDOVER_2026-11-23.md'):
 p=T/name;head,rest=p.read_text(encoding='utf-8').split('\n',1);p.write_text(head+'\n\n'+intro+rest.lstrip('\n'),encoding='utf-8',newline='\n')
reader=B/'checkpoint-reader-263';checks=json.loads((Q/'READER-CONTROLS.json').read_bytes());assert checks['checks']==8
logs=[]
for row in checks['cases']:
 folder=reader/(row['host']+'-'+row['case']);result=json.loads((folder/'RESULT.json').read_bytes());assert result==row
 logs.append(dict(**row,stdout=(folder/'stdout').read_text(encoding='utf-8'),stderr=(folder/'stderr').read_text(encoding='utf-8')))
save(Q/'READER-EVIDENCE.json',dict(status='PASS_SAVED_READER_TWO_HOSTS_VALID_AND_THREE_EARLY_INTEGRITY_REFUSALS',cases=logs,
 check_scope='Saved count, source and archive identity controls; full scientific contradiction controls remain in the qualified native packets.',original_C_reexecuted=False))
state=json.loads((Q/'STATUS.json').read_bytes());state['source_code']['docs/literature/smartpca-catalog-checkpoint/READER-EVIDENCE.json']=pin(Q/'READER-EVIDENCE.json');save(Q/'STATUS.json',state)
for name in ('qualify_smartpca_catalog_reader_20261123_263.py',Path(__file__).name):shutil.copyfile(R/'build'/name,Q/'helpers'/name)
with (Q/'README.md').open('a',encoding='utf-8',newline='\n') as f:f.write('''

Eight fresh saved-reader checks pass on Windows/Linux: a valid current context,
false reported counts, changed source and changed archive on each host. Their
actual stdout/stderr are retained in READER-EVIDENCE.json. These early integrity
checks do not replace the scientific contradiction controls in native qualification.
The first guard-recovery helper's overescaped Windows probe is historical; the
independent native exact-profile check in the evidence archive is authoritative
for confirming that initial owned browser had already closed. Later preview and
installed checks use the corrected task guard and record their own process closure.
''')
result=subprocess.run([sys.executable,'-X','utf8','-B','tools/literature/verify_smartpca_catalog_checkpoint.py','--jar',str(T/'build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar')],cwd=T,capture_output=True,timeout=90)
O=R/'build/git-publish/20261123-smartpca-catalog-255';(O/'PREPUBLICATION-SAVED-READER.stdout').write_bytes(result.stdout);(O/'PREPUBLICATION-SAVED-READER.stderr').write_bytes(result.stderr);assert result.returncode==0,result.stderr
print('Prepared current handover and eight saved-reader receipts; exact producer/current catalog verified.')

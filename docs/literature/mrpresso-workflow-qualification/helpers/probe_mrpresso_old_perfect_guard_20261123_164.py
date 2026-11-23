from pathlib import Path
import json,subprocess
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'near-perfect-guard-probe-1';D.mkdir()
record=json.loads((B/'extended-reference-windows-1/outputs/essentially-perfect-raw/original.json').read_bytes())
def field(record,key):return record['values'][record['names'].index(key)]
frame=field(record,'input');columns={x['name']:x['data']['values'] for x in frame['columns']};names=list(columns)
table=D/'associations.tsv';table.write_text('row_id\t'+'\t'.join(names)+'\n'+'\n'.join(str(label)+'\t'+'\t'.join(str(columns[x][i]) for x in names) for i,label in enumerate(frame['row_names']))+'\n',encoding='utf-8')
request=dict(schema_version=1,mode='RUN',engine='native',profile='MRPRESSO_1.0_3e3c92d',operation='ANALYZE',out=str(D/'analysis'),
 inputs=dict(associations=str(table),id_column='row_id',beta_outcome='Y',se_outcome='SY',beta_exposure=['X1'],se_exposure=['SX1']),
 rng=dict(kind='Mersenne-Twister/Inversion',sample_kind='Rejection',seed=19),options=dict(OUTLIERtest=True,DISTORTIONtest=True,NbDistribution=257))
save(D/'request.json',request);jar=B/'normal-build-2/project-build/cli/omicsmatic-0.4.0.jar'
command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-jar',str(jar),'mrpresso-tools','--request',str(D/'request.json')]
p=subprocess.run(command,cwd=T,capture_output=True,timeout=60);(D/'stdout').write_bytes(p.stdout);(D/'stderr').write_bytes(p.stderr)
native=json.loads((D/'analysis/native-result.json').read_bytes())
save(D/'FINDING.json',dict(status='DEVELOPMENT_WEIGHTED_PERFECT_FIT_GUARD_MISSED' if p.returncode==0 else 'DEVELOPMENT_GUARD_REFUSED',
 command=command,exit_code=p.returncode,producer=pin(jar),source_case='essentially-perfect-raw',native_essentially_perfect=native['raw_mr']['essentially_perfect'],
 native_inference_admitted=native['raw_mr']['inference_admitted'],original_source_and_native_diagnostics_retained=True,
 required_remedy='Use a dimensionally consistent weighted residual/fitted norm admission guard; retain all coefficients/SE/t/P unchanged; rerun actual workflow policy controls on NEW producer.',
 full_workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
assert p.returncode==0 and native['raw_mr']['inference_admitted'] and not native['raw_mr']['essentially_perfect']
print('Confirmed old unqualified producer misses weighted perfect-fit admission guard; original and native diagnostics retained.')

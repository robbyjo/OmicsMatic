from pathlib import Path
import json,subprocess,sys,time
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'packaged-smoke-1';D.mkdir()
jar=B/'normal-build-1/project-build/cli/omicsmatic-0.4.0.jar';java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
def field(value,name):return value['values'][value['names'].index(name)]
source=json.loads((B/'original-reference-windows-1/outputs/outlier-stable-precision/original.json').read_bytes());data=field(source,'input')
columns={row['name']:row['data']['values'] for row in data['columns']}
def token(value):
    if isinstance(value,dict):return 'NA' if value['special'].startswith('NA_') else value['special']
    return str(value)
table=D/'associations.tsv';names=list(columns)
table.write_text('\n'.join(['row_id\t'+'\t'.join(names)]+[str(label)+'\t'+'\t'.join(token(columns[n][i]) for n in names) for i,label in enumerate(data['row_names'])])+'\n',encoding='utf-8')
request=dict(schema_version=1,mode='RUN',engine='native',profile='MRPRESSO_1.0_3e3c92d',operation='ANALYZE',out=str(D/'analysis'),
    inputs=dict(associations=str(table),id_column='row_id',beta_outcome='Y',se_outcome='SY',beta_exposure=['X1'],se_exposure=['SX1']),
    rng=dict(kind='Mersenne-Twister/Inversion',sample_kind='Rejection',seed=19),options=dict(OUTLIERtest=True,DISTORTIONtest=True,NbDistribution=257))
save(D/'request.json',request);save(D/'replay.json',dict(schema_version=1,mode='REPLAY',analysis=str(D/'analysis'),out=str(D/'replayed')))
records=[]
for label,args in [('run',['--request',str(D/'request.json')]),('verify',['--verify',str(D/'analysis')]),('replay',['--request',str(D/'replay.json')]),('verify-replay',['--verify',str(D/'replayed')])]:
    command=[java,'-jar',str(jar),'mrpresso-tools',*args]
    with (D/(label+'.stdout')).open('xb') as out,(D/(label+'.stderr')).open('xb') as err:
        p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/(label+'.PROCESS.json'),dict(pid=p.pid,command=command));code=p.wait()
    row=dict(case=label,exit_code=code);records.append(row);save(D/'PROGRESS.json',records);print(json.dumps(row),flush=True)
    assert code==0,(D/(label+'.stderr')).read_text(errors='replace')[-3000:]
save(D/'RESULT.json',dict(cases=records,producer=pin(jar),scoped_smoke_only=True,new_family_credit=0,new_reviews=0))

"""Cross-host readback and refusal controls for the reviewed handover only."""
from pathlib import Path
import hashlib, json, os, subprocess, sys, time

linux=sys.platform.startswith('linux')
R=Path('/mnt/d/projects/JLinAlg' if linux else 'D:/projects/JLinAlg');T=R/'build/fu-1'
O=R/'build/git-publish/20261123-handover-297';host='linux' if linux else 'windows'
C=O/('checks-'+host);C.mkdir()
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
checks=[]
def run(label,script,extra=(),expected=0):
    args=[sys.executable,'-X','utf8','-B',str(T/'tools/literature'/script),*map(str,extra)]
    started=time.time()
    p=subprocess.run(args,cwd=T,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1'))
    (C/(label+'.stdout')).write_bytes(p.stdout);(C/(label+'.stderr')).write_bytes(p.stderr)
    row=dict(label=label,arguments=args,exit_code=p.returncode,expected_exit_code=expected,seconds=time.time()-started)
    checks.append(row);save(C/'RESULTS.json',checks)
    if p.returncode!=expected:raise RuntimeError((label,p.returncode,p.stdout[-1600:],p.stderr[-1600:]))
run('handover','verify_scenic_network_handover.py')
run('native-aucell','verify_pyscenic_aucell_component.py')
run('current-catalog','verify_smartpca_catalog_checkpoint.py')
source=T/'docs/literature/scenic-network-handover-20261123'
for name in ('changed-archive','false-count'):
    root=C/name;folder=root/'docs/literature/scenic-network-handover-20261123';folder.mkdir(parents=True)
    for file in ('STATUS.json','FILES.json','evidence.zip'):(folder/file).write_bytes((source/file).read_bytes())
    if name=='changed-archive':
        raw=bytearray((folder/'evidence.zip').read_bytes());raw[len(raw)//2]^=1;(folder/'evidence.zip').write_bytes(raw)
    else:
        state=json.loads((folder/'STATUS.json').read_bytes());state['catalog']['scoped_families']=155;save(folder/'STATUS.json',state)
    run(name,'verify_scenic_network_handover.py',('--root',root),1)
    require='FAIL2: SCENIC handover evidence check failed:'
    assert require in (C/(name+'.stderr')).read_text(encoding='utf-8')
save(C/'COMPLETED.json',dict(host=host,checks=checks,valid_readers=3,refusal_controls=2,original_scientific_analysis_reexecuted=False))
print(json.dumps(dict(host=host,valid_readers=3,refusal_controls=2,all_expected=True)))

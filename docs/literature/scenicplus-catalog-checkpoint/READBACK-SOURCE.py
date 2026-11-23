from pathlib import Path
from unittest.mock import patch
import importlib.util,json,subprocess,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenicplus-catalog-522';D=B/'checkpoint-reader-1';F=T/'docs/literature/scenicplus-catalog-checkpoint';D.mkdir();reader=T/'tools/literature/verify_scenicplus_catalog_checkpoint.py'
def linux(p):return '/mnt/d/'+str(p.resolve()).replace('\\','/').split(':/',1)[1]
rows=[]
for host in ('windows','linux'):
    path=lambda p:str(p) if host=='windows' else linux(p)
    cmd=[sys.executable,'-X','utf8','-B'] if host=='windows' else ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','env','GIT_DIR='+linux(R/'.git/worktrees/fu-1'),'GIT_WORK_TREE='+linux(T),'PYTHONDONTWRITEBYTECODE=1','/usr/bin/python3','-B']
    cmd+=[path(reader)]
    for name,value in [('jar',T/'build/scenicplus-dispatch-497/normal-build-1/project-build/cli/omicsmatic-0.4.0.jar'),('private-root',T/'build/scenicplus-dispatch-497'),('cache',R/'build/literature-review/xml'),('author-root',T/'build/scenicplus-370/p153-code-1/original')]:cmd+=['--'+name,path(value)]
    p=subprocess.run(cmd,cwd=T,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(D/(host+'.stdout')).write_bytes(p.stdout);(D/(host+'.stderr')).write_bytes(p.stderr);assert p.returncode==0,(host,p.stderr.decode(errors='replace'));rows.append(dict(host=host,exit_code=0,measured=json.loads(p.stdout)));print(host+' full checkpoint/source readback PASS',flush=True)
sys.path.insert(0,str(reader.parent));spec=importlib.util.spec_from_file_location('scenicplus_checkpoint_control',reader);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
original=Path.read_bytes;controls=[]
for label,target,change in [('false-credit',F/'STATUS.json',lambda raw:json.dumps(dict(json.loads(raw),new_reviews=6)).encode()),('changed-archive',F/'evidence.zip',lambda raw:raw[:-1]+bytes([raw[-1]^1])),('changed-source',T/'tools/literature/scenicplus_catalog/model.py',lambda raw:raw+b'\n# controlled alteration\n'),('changed-current-catalog',T/'docs/literature/omics-2023-2026/summary.json',lambda raw:json.dumps(dict(json.loads(raw),paper_tool_observations=2724)).encode())]:
    def altered(path):
        raw=original(path);return change(raw) if path==target else raw
    with patch.object(Path,'read_bytes',altered):
        try:module.verify(T)
        except ValueError as error:controls.append(dict(case=label,refused=True,error=str(error),physical_files_changed=False))
        else:raise AssertionError('Altered checkpoint accepted: '+label)
result=dict(status='PASS_SCENICPLUS_CATALOG_CHECKPOINT_BOTH_HOSTS_AND_FOUR_CHANGE_REFUSALS',reader=pin(reader),checks=rows,controls=controls,physical_source_or_evidence_changed=False,original_scientific_analysis_reexecuted=False)
save(D/'RESULT.json',result);save(F/'READBACK.json',result);(F/'READBACK-SOURCE.py').write_bytes(Path(__file__).read_bytes());print(json.dumps(dict(hosts=2,reader_change_controls=4,new_family_credit=1,new_reviews=5,existing_reviews_enriched=1)),flush=True)

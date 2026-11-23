from pathlib import Path
import concurrent.futures,importlib.util,json,shutil,subprocess,sys
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';Q=T/'docs/literature/smartpca-catalog-checkpoint';D=T/'build/smartpca-catalog-255/checkpoint-reader-263';D.mkdir()
reader=T/'tools/literature/verify_smartpca_catalog_checkpoint.py';status=json.loads((Q/'STATUS.json').read_bytes())
for case in ('false-count','changed-source','changed-archive'):
 root=D/case;q=root/'docs/literature/smartpca-catalog-checkpoint';q.mkdir(parents=True);save(q/'STATUS.json',status)
 if case=='false-count':save(q/'STATUS.json',dict(status,tests_per_host=999))
 elif case=='changed-source':
  name=next(iter(status['source_code']));dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((T/name).read_bytes()+b'\n# different saved source context\n')
 else:
  for name in status['source_code']:
   dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
  shutil.copyfile(Q/'EVIDENCE.json',q/'EVIDENCE.json');data=bytearray((Q/'evidence.zip').read_bytes());data[len(data)//2]^=1;(q/'evidence.zip').write_bytes(data)
def linux(path):
 value=Path(path).as_posix();return '/mnt/'+value[0].lower()+value[2:]
def run(row):
 host,case=row;out=D/(host+'-'+case);out.mkdir();target=T if case=='valid' else D/case
 if case=='valid':code=None;args=[str(reader)] if host=='windows' else [linux(reader)]
 else:
  source=str(reader) if host=='windows' else linux(reader);root=str(target) if host=='windows' else linux(target)
  code='import importlib.util,json;from pathlib import Path;s=importlib.util.spec_from_file_location("saved",'+repr(source)+');m=importlib.util.module_from_spec(s);s.loader.exec_module(m);print(json.dumps(m.verify(Path('+repr(root)+'))))'
  args=['-c',code]
 if host=='windows':command=[sys.executable,'-X','utf8','-B',*args]
 else:command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','env','GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE='+linux(T),'GIT_OPTIONAL_LOCKS=0','python3','-B',*args]
 p=subprocess.run(command,cwd=T,capture_output=True);(out/'stdout').write_bytes(p.stdout);(out/'stderr').write_bytes(p.stderr);expected=0 if case=='valid' else 1
 assert p.returncode==expected,(host,case,p.returncode,p.stderr[-1600:])
 if case=='false-count':assert b'Reported checkpoint counts differ' in p.stderr
 if case=='changed-source':assert b'Changed checkpoint source' in p.stderr
 if case=='changed-archive':assert b'Changed checkpoint evidence archive/index' in p.stderr and b'FileNotFoundError' not in p.stderr
 result=dict(host=host,case=case,exit_code=p.returncode,expected=expected);save(out/'RESULT.json',result);print(json.dumps(result),flush=True);return result
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(run,[(host,case) for host in ('windows','linux') for case in ('valid','false-count','changed-source','changed-archive')]))
save(Q/'READER-CONTROLS.json',dict(status='PASS_TWO_HOSTS_VALID_AND_THREE_CHECKPOINT_CONTRADICTIONS',checks=8,cases=results,original_analysis_reexecuted=False,byte_integrity_not_host_authentication=True))
status['source_code']['docs/literature/smartpca-catalog-checkpoint/READER-CONTROLS.json']=pin(Q/'READER-CONTROLS.json');save(Q/'STATUS.json',status)
save(D/'RESULT.json',dict(status='PASS',checks=results,scientific_source_unchanged=True,original_archive_and_sources_unchanged=True))

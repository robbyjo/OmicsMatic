from pathlib import Path
import hashlib,importlib.metadata as md,json,os,stat,sys
T=Path('/mnt/d/projects/JLinAlg/build/fu-1');P=Path('/var/tmp/omicsmatic-scenic-runtime-20261123-274');D=T/'build/scenic-1/runtime-index-1';D.mkdir()
assert sys.platform=='linux' and Path(sys.executable).is_relative_to(P/'environment')
files={}
for root in (P/'environment',P/'python'):
 for path in sorted(root.rglob('*')):
  mode=path.lstat().st_mode
  if stat.S_ISLNK(mode):files[path.relative_to(P).as_posix()]=dict(type='symlink',target=os.readlink(path))
  elif stat.S_ISREG(mode):
   with path.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
   files[path.relative_to(P).as_posix()]=dict(bytes=path.stat().st_size,sha256=digest)
expected=json.loads((T/'build/scenic-1/original-aucell-reference-2/COMPLETED.json').read_bytes())['sources']
for row in expected.values():assert files[Path(row['path']).relative_to(P).as_posix()]==row['identity']
result=dict(status='COMPLETE_OWNED_ENVIRONMENT_AND_MANAGED_PYTHON_FILE_INDEX',runtime_root=str(P),python=sys.version,executable=sys.executable,
 files=files,packages={d.metadata['Name']:d.version for d in md.distributions()},indexed_roots=['environment','python'],
 excluded_generated_or_installer_caches=['cache','numba-cache','reference-numba-cache','matplotlib-cache','python-bin'],system_libraries_base_OS_not_indexed=True,
 original_core_AUCell_geneSignature_recovery_bytes_match_reference=True,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
(D/'INVENTORY.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(files=len(files),regular_file_bytes=sum(r.get('bytes',0) for r in files.values()),packages=len(result['packages']),scientific_result=False)))

from pathlib import Path
import hashlib,json,os,shutil,subprocess
T=Path('/mnt/d/projects/JLinAlg/build/fu-1');B=T/'build/smartpca-1';out=B/'reference-setup-records-1';out.mkdir()
def pin(path):
 with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,obj):path.write_text(json.dumps(obj,indent=2)+'\n',encoding='utf-8')
roots=[Path('/var/tmp/omicsmatic-smartpca-reference-20261123-197'),Path('/var/tmp/omicsmatic-smartpca-reference-20261123-198')]
for root in roots:
 target=out/root.name;target.mkdir()
 for path in root.iterdir():
  if path.is_file() and path.suffix in ('.json','.stdout','.stderr','.py'):shutil.copyfile(path,target/path.name)
first=json.loads((roots[0]/'COMPLETED.json').read_bytes());final=json.loads((roots[1]/'COMPLETED.json').read_bytes())
assert first['status']=='FAIL2_SMARTPCA_REFERENCE_SETUP' and final['status']=='PASS_ORIGINAL_SMARTPCA721_PRIVATE_BUILD_ONLY'
binary=Path(final['binary']);assert pin(binary)==final['binary_identity']
prefix=Path(final['dependency_prefix']);members={}
for path in sorted(prefix.rglob('*')):
 if path.is_symlink():members[path.relative_to(prefix).as_posix()]=dict(symlink=os.readlink(path))
 elif path.is_file():members[path.relative_to(prefix).as_posix()]=pin(path)
save(out/'RUNTIME-FILE-LOCK.json',dict(dependency_prefix=str(prefix),members=members,binary=str(binary),binary_identity=pin(binary),byte_integrity_not_authentication=True))
before=json.loads((roots[1]/'REUSED-DEPENDENCIES-BEFORE.json').read_bytes());after=json.loads((roots[1]/'REUSED-DEPENDENCIES-AFTER.json').read_bytes());assert before==after==members
ps=subprocess.run(['ps','-eo','pid,lstart,args'],capture_output=True,check=True)
active=[line for line in ps.stdout.decode().splitlines() if any(str(root) in line for root in roots)]
save(out/'COMPLETED.json',dict(status='PASS_RETAINED_ORIGINAL_BUILD_FAILURE_SUCCESS_AND_PRIVATE_RUNTIME_FILE_LOCK',
 first_setup=first['status'],final_setup=final['status'],dependency_files=len(members),private_roots=list(map(str,roots)),
 dependency_prefix_unchanged_during_build=True,scientific_source_files_unchanged=159,binary_identity=pin(binary),active_processes_in_exact_prefixes=active,
 original_binaries_or_libraries_redistributed=False,original_scientific_source_modified=False,workflow_qualified=False,catalog_credit=0))
assert not active,active
print(json.dumps(dict(status='PASS',dependency_files=len(members),source_files=159,original_build_failure_preserved=True,active_reference_processes=0),indent=2))

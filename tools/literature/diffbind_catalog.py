"""Apply/check the narrowly scoped DiffBind catalog transition; scientific execution stays separate."""
from pathlib import Path
import argparse,hashlib,json,os,sys,zipfile
import diffbind_model as model
from gemma_primary import encoded,require
from diffbind_primary import audit as primary_audit
from verify_diffbind_workflows import source_record,private_evidence

ROOT=Path(__file__).resolve().parents[2]
ROLES='docs/literature/diffbind-source-audit/ROLES.json'
PRIOR_REGISTRY='docs/literature/merqury-catalog-admission/REGISTRY.json'
COVERAGE=model.DATA+'/diffbind-source-role-coverage.json'
PROOF=model.DATA+'/diffbind-catalog-validation.json'
LEGACY_CHECKSUM_MEMBERS=frozenset('../'+name for name in (
 'MALTEXTRACT_INTEGRATION.md','maltextract17-integration/catalog-before.zip',
 'maltextract17-integration/LICENSE-project.txt','maltextract17-integration/maltextract17-workflow-jdk21.jar',
 'maltextract17-integration/maltextract17-workflow-jdk25.jar','maltextract17-integration/NOTICE-source-attribution.txt',
 'maltextract17-integration/SOURCE_AUDIT_RECOVERY.json','maltextract17-integration/OFFLINE_UI_VALIDATION.json',
 'maltextract17-integration/PACKAGED_APPLICATION_IDENTITY.json'))
def pin(path):
 digest=hashlib.sha256();size=0
 with Path(path).open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 return dict(bytes=size,sha256=digest.hexdigest())
def read(path):return json.loads(Path(path).read_bytes())
def checksums(raw):
 result={}
 for line in raw.decode().splitlines():
  fields=line.split('  ',1);require(len(fields)==2,'Invalid catalog checksum row');digest,name=fields
  safe=name in LEGACY_CHECKSUM_MEMBERS or (name and not name.startswith('/')and'\\'not in name and':'not in name and all(part not in ('','.','..')for part in name.split('/')))
  require(len(digest)==64 and all(c in'0123456789abcdef'for c in digest)and safe and name not in result,'Invalid catalog checksum namespace');result[name]=digest
 return result
def verify_sums(root,rows):
 for name,digest in rows.items():
  path=(root/model.DATA/name).resolve();require(path.is_relative_to(root.resolve()),'Catalog artifact escapes source root');require(pin(path)['sha256']==digest,'Changed catalog checksum member: '+name)
def before(root):
 folder=root/model.ADMISSION;baseline=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==baseline['metadata_archive'],'Changed predecessor metadata archive')
 require(pin(folder/'PREVIOUS-SHA256SUMS')==baseline['previous_checksums']and pin(folder/'PREVIOUS-REGISTRY.json')==baseline['previous_registry'],'Changed predecessor checksum/registry bytes')
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(model.META)and len(archive.namelist())==len(model.META),'Different archived metadata population');old={name:archive.read(name)for name in model.META}
 require({name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())for name,raw in old.items()}==baseline['metadata'],'Changed exact predecessor bytes');return old,baseline
def coverage(papers):return dict(schema_version=1,tool_id='diffbind',roles=[dict(paper_id=paper['paper_id'],qualification=use['source_role_qualification'])for paper in papers for use in paper['tools']if use['tool_id']=='diffbind'],new_family_credit=1,new_native_family_credit=0,whole_paper_reproduced=False)
def check(root):
 root=Path(root);source_record(root);old,baseline=before(root);q=read(root/model.Q);roles=read(root/ROLES);actual={name:(root/name).read_bytes()for name in model.META};result=model.check(old,actual,roles,q);folder=root/model.ADMISSION
 require(pin(root/model.Q)==baseline['qualification']and pin(root/ROLES)==baseline['source_roles'],'Changed qualification or source-role record')
 require({name:pin(root/name)for name in baseline['catalog_code']}==baseline['catalog_code'],'Catalog transformer changed; use its archived entrypoint')
 registry=read(folder/'PREVIOUS-REGISTRY.json');require(read(folder/'REGISTRY.json')==model.registry(registry),'Different effective provider registry')
 require(read(root/COVERAGE)==coverage(json.loads(actual[model.META[0]])),'Different source-role coverage')
 require(read(root/PROOF)==dict(schema_version=1,status='PASS_SCOPED_DIFFBIND_CATALOG_ADMISSION',result=result,qualification=pin(root/model.Q),source_role_audit=pin(root/ROLES),original_full_paper_analyses_reexecuted=False,source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False),'Different catalog proof')
 prior=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());current=checksums((root/model.DATA/'SHA256SUMS').read_bytes());require(len(current)==len(prior)+2 and set(current)==set(prior)|{Path(COVERAGE).name,Path(PROOF).name},'Different checksum population');allowed={Path(name).name for name in model.META if name.startswith(model.DATA+'/')};require(all(current[name]==digest or name in allowed for name,digest in prior.items()),'Unrelated historical checksum refreshed');verify_sums(root,current);return result
def apply(root,private,cache,out):
 root,private,cache,out=map(Path,(root,private,cache,out));require(root.resolve()==root and private.resolve()==private and out.is_relative_to(private)and not out.exists()and out.parent.resolve()==out.parent,'Canonical roots and NEW private output required');folder=root/model.ADMISSION;require(not folder.exists(),'DiffBind admission already exists')
 q,evidence=private_evidence(root,private);primary=primary_audit(root,cache);model.qualified(q);old={name:(root/name).read_bytes()for name in model.META};roles=read(root/ROLES);after=model.transition(old,roles,q);result=model.check(old,after,roles,q)
 previous=(root/model.DATA/'SHA256SUMS').read_bytes();prior=checksums(previous);require(len(prior)==891,'Different predecessor checksum population');verify_sums(root,prior);registry=(root/PRIOR_REGISTRY).read_bytes();require(len(json.loads(registry))==490,'Different effective provider population');out.mkdir();folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as archive:
  for name,raw in old.items():archive.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'PREVIOUS-REGISTRY.json').write_bytes(registry)
 baseline=dict(schema_version=1,metadata={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())for name,raw in old.items()},metadata_archive=pin(folder/'metadata-before.zip'),previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),previous_registry=pin(folder/'PREVIOUS-REGISTRY.json'),qualification=pin(root/model.Q),source_roles=pin(root/ROLES),catalog_code={name:pin(root/name)for name in ['tools/literature/diffbind_catalog.py','tools/literature/diffbind_model.py']})
 (folder/'BASELINE.json').write_bytes(encoded(baseline));(out/'PLAN.json').write_bytes(encoded(dict(before=baseline['metadata'],after={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())for name,raw in after.items()},result=result,primary=primary)))
 for index,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==old[name],'Concurrent metadata change: '+name);stage=out/('staged-'+str(index));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(model.registry(json.loads(registry))));(root/COVERAGE).write_bytes(encoded(coverage(json.loads(after[model.META[0]]))));(root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_SCOPED_DIFFBIND_CATALOG_ADMISSION',result=result,qualification=pin(root/model.Q),source_role_audit=pin(root/ROLES),original_full_paper_analyses_reexecuted=False,source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False)))
 current=dict(prior)
 for name in [n for n in model.META if n.startswith(model.DATA+'/')]+[COVERAGE,PROOF]:current[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';stage=out/'new-SHA256SUMS';stage.write_bytes(''.join(digest+'  '+name+newline for name,digest in sorted(current.items())).encode());os.replace(stage,root/model.DATA/'SHA256SUMS')
 require(check(root)==result,'Final catalog readback differs');status=dict(status='PASS_SCOPED_DIFFBIND_CATALOG_ADMISSION',result=result,checksum_entries=len(current),effective_provider_registry=pin(folder/'REGISTRY.json'),raw_process_runtime_cleanup_receipts_published=False,website_reports_published=False,whole_goal_complete=False);(folder/'STATUS.json').write_bytes(encoded(status));(out/'COMPLETED.json').write_bytes(encoded(status));return result
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--apply',action='store_true');p.add_argument('--private-root',type=Path);p.add_argument('--cache',type=Path);p.add_argument('--out',type=Path);a=p.parse_args()
 try:
  if a.apply:require(all(v is not None for v in(a.private_root,a.cache,a.out)),'Explicit private root/cache/NEW output required');result=apply(a.root.absolute(),a.private_root.absolute(),a.cache.absolute(),a.out.absolute())
  else:result=check(a.root.absolute())
  print(json.dumps(result,sort_keys=True));return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT DIFFBIND CATALOG ADMISSION SKIPPED: 'if code==3 else'FAIL2 NO DIFFBIND CATALOG ADMISSION: ')+str(e)+'. Required action: restore exact source, qualifier, producer and retained evidence; preserve old contexts, attempts and seals.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())

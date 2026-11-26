"""Apply/check the scoped ASCAT catalog transition after complete private qualification."""
from pathlib import Path
import argparse,hashlib,json,os,sys,zipfile
import ascat_model as model
from ascat_primary import audit as primary_audit
from verify_ascat_workflows import source_record,private_evidence,pin,read,require

ROOT=Path(__file__).resolve().parents[2]
ROLES='docs/literature/ascat-source-audit-v2/ROLES.json'
PRIOR_REGISTRY='docs/literature/diffbind-catalog-admission/REGISTRY.json'
COVERAGE=model.DATA+'/ascat-source-role-coverage.json'
PROOF=model.DATA+'/ascat-catalog-validation.json'
LEGACY_SUMS=frozenset('../'+name for name in('MALTEXTRACT_INTEGRATION.md','maltextract17-integration/catalog-before.zip','maltextract17-integration/LICENSE-project.txt','maltextract17-integration/maltextract17-workflow-jdk21.jar','maltextract17-integration/maltextract17-workflow-jdk25.jar','maltextract17-integration/NOTICE-source-attribution.txt','maltextract17-integration/SOURCE_AUDIT_RECOVERY.json','maltextract17-integration/OFFLINE_UI_VALIDATION.json','maltextract17-integration/PACKAGED_APPLICATION_IDENTITY.json'))

def checksums(raw):
 result={}
 for line in raw.decode().splitlines():
  fields=line.split('  ',1);require(len(fields)==2,'Invalid checksum row');digest,name=fields;safe=name in LEGACY_SUMS or(name and not name.startswith('/')and'\\'not in name and':'not in name and all(p not in('','.','..')for p in name.split('/')));require(safe and len(digest)==64 and all(c in'0123456789abcdef'for c in digest)and name not in result,'Invalid/duplicate checksum member');result[name]=digest
 return result

def verify_sums(root,rows):
 for name,digest in rows.items():
  path=(root/model.DATA/name).resolve();require(path.is_relative_to(root.resolve())and pin(path)['sha256']==digest,'Changed/escaping catalog checksum member: '+name)

def before(root):
 folder=root/model.ADMISSION;baseline=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==baseline['metadata_archive']and pin(folder/'PREVIOUS-SHA256SUMS')==baseline['previous_checksums']and pin(folder/'PREVIOUS-REGISTRY.json')==baseline['previous_registry'],'Predecessor archive changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as stream:
  require(len(stream.namelist())==len(model.META)and set(stream.namelist())==set(model.META),'Archived metadata population differs');old={name:stream.read(name)for name in model.META}
 require({name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())for name,raw in old.items()}==baseline['metadata'],'Archived predecessor bytes changed');return old,baseline

def coverage(papers):return dict(schema_version=1,tool_id='ascat',roles=[dict(paper_id=p['paper_id'],qualification=u['source_role_qualification'])for p in papers for u in p['tools']if u['tool_id']=='ascat'],new_family_credit=1,new_native_family_credit=0,whole_paper_reproduced=False,additional_supplementary_named_tools=2,primary_registry_expansion_pending=True)

def check(root):
 root=Path(root);source_record(root);old,baseline=before(root);q=read(root/model.Q);roles=read(root/ROLES);actual={name:(root/name).read_bytes()for name in model.META};result=model.check(old,actual,roles,q);folder=root/model.ADMISSION
 require(pin(root/model.Q)==baseline['qualification']and pin(root/ROLES)==baseline['source_roles'],'Qualification/source-role record changed');require({name:pin(root/name)for name in baseline['catalog_code']}==baseline['catalog_code'],'Catalog transformer changed; use archived matching entrypoint')
 require(read(folder/'REGISTRY.json')==model.registry(read(folder/'PREVIOUS-REGISTRY.json')),'Effective provider registry differs');require(read(root/COVERAGE)==coverage(json.loads(actual[model.META[0]])),'Source-role coverage differs')
 expected=dict(schema_version=1,status='PASS_SCOPED_ASCAT_CATALOG_ADMISSION',result=result,qualification=pin(root/model.Q),source_role_audit=pin(root/ROLES),original_full_paper_analyses_reexecuted=False,source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False);require(read(root/PROOF)==expected,'Catalog proof differs')
 prior=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());current=checksums((root/model.DATA/'SHA256SUMS').read_bytes());require(len(current)==len(prior)+2 and set(current)==set(prior)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population differs');allowed={Path(n).name for n in model.META if n.startswith(model.DATA+'/')};require(all(current[n]==digest or n in allowed for n,digest in prior.items()),'Unrelated historical checksum refreshed');verify_sums(root,current);return result

def apply(root,private,cache,supplements,out):
 root,private,cache,supplements,out=map(Path,(root,private,cache,supplements,out));require(root.resolve()==root and private.resolve()==private and out.is_relative_to(private)and not out.exists()and out.parent.resolve()==out.parent,'Canonical roots and NEW private output required');folder=root/model.ADMISSION;require(not folder.exists(),'ASCAT admission already exists')
 q,evidence=private_evidence(root,private);primary=primary_audit(root,cache,supplements);old={n:(root/n).read_bytes()for n in model.META};roles=read(root/ROLES);after=model.transition(old,roles,q);result=model.check(old,after,roles,q);previous=(root/model.DATA/'SHA256SUMS').read_bytes();prior=checksums(previous);require(len(prior)==893,'Predecessor checksum population differs');verify_sums(root,prior);registry=(root/PRIOR_REGISTRY).read_bytes();require(len(json.loads(registry))==490,'Predecessor provider population differs');out.mkdir();folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as stream:
  for name,raw in old.items():stream.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'PREVIOUS-REGISTRY.json').write_bytes(registry);baseline=dict(schema_version=1,metadata={n:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())for n,raw in old.items()},metadata_archive=pin(folder/'metadata-before.zip'),previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),previous_registry=pin(folder/'PREVIOUS-REGISTRY.json'),qualification=pin(root/model.Q),source_roles=pin(root/ROLES),catalog_code={n:pin(root/n)for n in('tools/literature/ascat_catalog.py','tools/literature/ascat_model.py')});(folder/'BASELINE.json').write_bytes(model.encoded(baseline));(out/'PLAN.json').write_bytes(model.encoded(dict(before=baseline['metadata'],after={n:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())for n,raw in after.items()},result=result,primary=primary,private_evidence=evidence)))
 for index,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==old[name],'Concurrent metadata change: '+name);stage=out/('staged-'+str(index));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(model.encoded(model.registry(json.loads(registry))));(root/COVERAGE).write_bytes(model.encoded(coverage(json.loads(after[model.META[0]]))));(root/PROOF).write_bytes(model.encoded(dict(schema_version=1,status='PASS_SCOPED_ASCAT_CATALOG_ADMISSION',result=result,qualification=pin(root/model.Q),source_role_audit=pin(root/ROLES),original_full_paper_analyses_reexecuted=False,source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False)))
 current=dict(prior)
 for name in[n for n in model.META if n.startswith(model.DATA+'/')]+[COVERAGE,PROOF]:current[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';stage=out/'new-SHA256SUMS';stage.write_bytes(''.join(digest+'  '+name+newline for name,digest in sorted(current.items())).encode());os.replace(stage,root/model.DATA/'SHA256SUMS');require(check(root)==result,'Final catalog readback differs');status=dict(status='PASS_SCOPED_ASCAT_CATALOG_ADMISSION',result=result,checksum_entries=len(current),effective_provider_registry=pin(folder/'REGISTRY.json'),raw_process_runtime_cleanup_receipts_published=False,website_reports_published=False,whole_goal_complete=False);(folder/'STATUS.json').write_bytes(model.encoded(status));(out/'COMPLETED.json').write_bytes(model.encoded(status));return result

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--apply',action='store_true');parser.add_argument('--private-root',type=Path);parser.add_argument('--cache',type=Path);parser.add_argument('--supplements',type=Path);parser.add_argument('--out',type=Path);args=parser.parse_args()
 try:
  if args.apply:require(all(v is not None for v in(args.private_root,args.cache,args.supplements,args.out)),'Explicit retained private evidence/cache/supplements/NEW output required');result=apply(args.root.absolute(),args.private_root.absolute(),args.cache.absolute(),args.supplements.absolute(),args.out.absolute())
  else:result=check(args.root.absolute())
  print(json.dumps(result,sort_keys=True));return 0
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT ASCAT CATALOG ADMISSION SKIPPED: 'if code==3 else'FAIL2 NO COMPLETE ASCAT CATALOG ADMISSION: ')+str(error)+'. Required action: restore exact source, qualifier, producing JAR, complete retained evidence and predecessor metadata; preserve historical contexts and failed attempts.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())

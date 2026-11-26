"""Apply/check the source-qualified native ALLCools subset; never run scientific analyses."""
from pathlib import Path
import argparse,json,os,sys,zipfile
import allcools_posterior_model as model
from gemma_primary import encoded,pin,require
from gemma_catalog import checksum_rows,verify_checksums
from contact_primary import audit as primary_audit
ROOT=Path(__file__).resolve().parents[2]
COVERAGE=model.DATA+'/allcools-posterior-source-role-coverage.json'
PROOF=model.DATA+'/allcools-posterior-catalog-validation.json'
PRIOR_REGISTRY='docs/literature/contact-insulation-catalog-admission/REGISTRY.json'
ROLES='docs/literature/contact-insulation-source-audit/ROLES.json'
def filepin(p):return pin(Path(p).read_bytes())
def safe(name):return isinstance(name,str)and name and ':'not in name and'\\'not in name and not name.startswith('/')and all(p not in ('','.','..')for p in name.split('/'))
def namespace(root,manifest):
 require(root.resolve()==root.absolute()and not root.is_symlink(),'Require canonical unlinked capture')
 files=set();directories={''}
 for p in root.rglob('*'):
  require(not p.is_symlink()and p.resolve()==p.absolute(),'Linked capture refused')
  if p.is_dir():directories.add(p.relative_to(root).as_posix())
  else:require(p.is_file(),'Nonregular capture');files.add(p.relative_to(root).as_posix())
 require(directories=={'','inputs','outputs'}and files==set(manifest['files'])|{'manifest.json','COMPLETED.json'},'Missing/extra capture member')
 for name,identity in manifest['files'].items():require(safe(name)and filepin(root/name)==identity,'Changed complete capture member: '+name)
 done=json.loads((root/'COMPLETED.json').read_bytes());require(done['manifest']==filepin(root/'manifest.json')and done['analysis_admitted']and not done['whole_package_admitted'],'Different capture completion')
def before(root):
 folder=root/model.ADMISSION;b=json.loads((folder/'BASELINE.json').read_bytes())
 for name,key in [('metadata-before.zip','metadata_archive'),('PREVIOUS-SHA256SUMS','previous_checksums'),('PREVIOUS-REGISTRY.json','previous_registry')]:require(filepin(folder/name)==b[key],'Changed predecessor archive: '+name)
 with zipfile.ZipFile(folder/'metadata-before.zip')as z:
  require(len(z.namelist())==len(set(z.namelist()))==len(model.META)and set(z.namelist())==set(model.META),'Different predecessor metadata namespace')
  result={name:z.read(name)for name in model.META}
 require({k:pin(v)for k,v in result.items()}==b['metadata'],'Changed predecessor bytes');return result
def private_controls(root,private,q):
 require((root/model.Q).read_bytes()==(private/'qualification-1/QUALIFICATION.json').read_bytes(),'Different retained qualification');model.qualified(q)
 stage=private/q['source_build_stage'];require(safe(q['source_build_stage']),'Unsafe build stage')
 require(filepin(stage/'project-build/cli/omicsmatic-0.4.1.jar')==q['producer']and filepin(stage/'SOURCES.json')==q['source_manifest'],'Changed full producer/source context')
 for name,expected in q['selected_public_sources'].items():require(safe(name)and filepin(root/name)==expected==filepin(stage/'source'/name),'Current scientific source differs from its full build: '+name)
 for host in ('Windows','Linux'):
  state=json.loads((private/('workflow-'+host+'-1/COMPLETED.json')).read_bytes());require(state['producer']==q['producer']and len(state['positive_captures'])==5 and state['originals_and_producer_unchanged'],'Incomplete native capture qualification')
  require((state['corrupted_capture_refusals'],state['resource_refusals'],state['unstable_M_normalization_refusals'],state['missing_prerequisite_skips'])==(8,1,1,1),'Incomplete failure/skip controls')
  require([sum(c['exit_code']==code for c in state['calls'])for code in (0,2,3)]==[11,10,1],'Different actual capture/verify/failure/skip population')
  for row in state['positive_captures']:
   require(safe(row['capture']),'Unsafe capture path');capture=private/row['capture'];manifest=json.loads((capture/'manifest.json').read_bytes());namespace(capture,manifest);require(manifest['producer']==q['producer'],'Different exact producing JAR')
  foreign=json.loads((private/('portable-'+host+'-1/COMPLETED.json')).read_bytes());require(foreign['captures']==5 and foreign['producer']==q['producer']and all(r['exit_code']==0 and r['original_foreign_input_path_unavailable']for r in foreign['replays']),'Missing full foreign saved replay')
 require(json.loads((private/'project-linux-tests-1/COMPLETED.json').read_bytes())['tests']==q['Linux_tests'],'Different Linux test population')
 reference=json.loads((private/'reference-full-Linux-1/source-results/COMPLETED.json').read_bytes());require(reference['cases']==384 and not reference['selected_function_definitions_without_package_import_orchestrator'],'Normal original import comparison missing')
 require(filepin(private/'reference-full-Linux-1/source-results/cases.tsv')==filepin(private/'reference-selected-Linux-1/cases.tsv')==q['original_reference_fixture'],'Different original reference values')
def coverage(papers):
 roles=[dict(paper_id=p['paper_id'],tool_id='allcools',qualification=u['source_role_qualification'])for p in papers for u in p['tools']if u['tool_id']=='allcools']
 require(len(roles)==4 and sum(r['qualification']['native_prepared_posterior_available']for r in roles)==2,'Different native posterior source roles')
 return dict(schema_version=1,roles=roles,new_native_family_credit=1,new_upstream_family_credit=0,new_source_reviews=0,full_author_roles_implemented=False,whole_goal_complete=False)
def check(root,artifacts):
 old=before(root);folder=root/model.ADMISSION;b=json.loads((folder/'BASELINE.json').read_bytes());q=json.loads((root/model.Q).read_bytes());require(filepin(root/model.Q)==b['qualification']and filepin(root/ROLES)==b['source_roles'],'Changed qualification/role audit')
 after={name:(root/name).read_bytes()for name in model.META};result=model.check(old,after,q)
 previous=json.loads((folder/'PREVIOUS-REGISTRY.json').read_bytes());require(json.loads((folder/'REGISTRY.json').read_bytes())==model.registry(previous),'Different effective provider registry')
 require(json.loads((root/COVERAGE).read_bytes())==coverage(json.loads(after[model.META[0]])),'Different source role availability')
 proof=json.loads((root/PROOF).read_bytes());require(proof==dict(schema_version=1,status='PASS_SCOPED_NATIVE_ALLCOOLS_POSTERIOR_CATALOG_ADMISSION',result=result,qualification=filepin(root/model.Q),source_role_audit=filepin(root/ROLES),original_full_paper_analyses_reexecuted=False,source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False),'Different catalog proof')
 prior=checksum_rows((folder/'PREVIOUS-SHA256SUMS').read_bytes());rows=checksum_rows((root/model.DATA/'SHA256SUMS').read_bytes());require(len(prior)==887 and len(rows)==889 and set(rows)==set(prior)|{Path(COVERAGE).name,Path(PROOF).name},'Different checksum namespace')
 allowed={Path(name).name for name in model.META if name.startswith(model.DATA+'/')};require(all(rows[name]==digest or name in allowed for name,digest in prior.items()),'Unrelated historical checksum refreshed')
 verify_checksums(root,artifacts,rows);return result
def apply(root,private,cache,out):
 require(root.resolve()==root and private.resolve()==private and out.is_relative_to(private)and out.parent.resolve()==out.parent and not out.exists(),'Require canonical roots and NEW private receipt path')
 folder=root/model.ADMISSION;require(not folder.exists(),'Admission exists; preserve it')
 old={name:(root/name).read_bytes()for name in model.META};q=json.loads((root/model.Q).read_bytes());model.qualified(q);private_controls(root,private,q)
 prior=(root/model.DATA/'SHA256SUMS').read_bytes();require(len(checksum_rows(prior))==887,'Different predecessor checksum count');verify_checksums(root,root,checksum_rows(prior));primary=primary_audit(root,cache)
 registry=(root/PRIOR_REGISTRY).read_bytes();require(len(json.loads(registry))==490,'Different predecessor provider registry')
 after=model.transition(old,q);result=model.check(old,after,q);new_registry=model.registry(json.loads(registry));require(not(root/COVERAGE).exists()and not(root/PROOF).exists(),'Credit already applied')
 out.mkdir();folder.mkdir();archive=folder/'metadata-before.zip'
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as z:
  for name in model.META:z.writestr(name,old[name])
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(prior);(folder/'PREVIOUS-REGISTRY.json').write_bytes(registry)
 baseline=dict(schema_version=1,metadata={name:pin(raw)for name,raw in old.items()},metadata_archive=filepin(archive),previous_checksums=filepin(folder/'PREVIOUS-SHA256SUMS'),previous_registry=filepin(folder/'PREVIOUS-REGISTRY.json'),qualification=filepin(root/model.Q),source_roles=filepin(root/ROLES))
 (folder/'BASELINE.json').write_bytes(encoded(baseline));(out/'PLAN.json').write_bytes(encoded(dict(before=baseline['metadata'],after={name:pin(raw)for name,raw in after.items()},result=result,primary=primary)))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==old[name],'Concurrent metadata change: '+name);temp=out/('staged-'+str(i));temp.write_bytes(raw);os.replace(temp,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(new_registry));(root/COVERAGE).write_bytes(encoded(coverage(json.loads(after[model.META[0]]))))
 (root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_SCOPED_NATIVE_ALLCOOLS_POSTERIOR_CATALOG_ADMISSION',result=result,qualification=filepin(root/model.Q),source_role_audit=filepin(root/ROLES),original_full_paper_analyses_reexecuted=False,source_build_required=True,released_binary_contains_workflow=False,website_reports_published=False)))
 rows=checksum_rows(prior)
 for name in [n for n in model.META if n.startswith(model.DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=filepin(root/name)['sha256']
 newline='\r\n'if prior.count(b'\n')==prior.count(b'\r\n')else'\n';temp=out/'new-SHA256SUMS';temp.write_bytes(''.join(digest+'  '+name+newline for name,digest in sorted(rows.items())).encode());os.replace(temp,root/model.DATA/'SHA256SUMS')
 require(check(root,root)==result,'Final catalog readback differs');status=dict(schema_version=1,status='PASS_SCOPED_NATIVE_ALLCOOLS_POSTERIOR_CATALOG_ADMISSION',result=result,checksum_entries=889,effective_provider_registry=filepin(folder/'REGISTRY.json'),current_checksum_manifest=filepin(root/model.DATA/'SHA256SUMS'),raw_process_runtime_cleanup_receipts_published=False,website_reports_published=False,whole_goal_complete=False)
 (folder/'STATUS.json').write_bytes(encoded(status));(out/'COMPLETED.json').write_bytes(encoded(status));return result
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--artifact-root',type=Path);p.add_argument('--apply',action='store_true');p.add_argument('--private-root',type=Path);p.add_argument('--cache',type=Path);p.add_argument('--out',type=Path);a=p.parse_args()
 try:
  if a.apply:
   require(all(x is not None for x in (a.private_root,a.cache,a.out)),'Explicit private root/cache/NEW output required');result=apply(a.root.absolute(),a.private_root.absolute(),a.cache.absolute(),a.out.absolute())
  else:result=check(a.root.absolute(),(a.artifact_root or a.root).absolute())
  print(json.dumps(result,sort_keys=True));return 0
 except Exception as e:
  code=3 if isinstance(e,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT ALLCOOLS CATALOG AUDIT SKIPPED: 'if code==3 else'FAIL2 NO ALLCOOLS CATALOG ADMISSION: ')+str(e)+'. Required action: restore the exact permitted source, producer and retained proof; preserve failed attempts and historical contexts. Do not refresh old seals.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())

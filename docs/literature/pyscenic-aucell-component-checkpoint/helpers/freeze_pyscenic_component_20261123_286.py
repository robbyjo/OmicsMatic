from pathlib import Path
import hashlib,json,shutil,sys,zipfile
from handover_inventory_20261122_111 import save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenic-1';Q=T/'docs/literature/pyscenic-aucell-component-checkpoint';Q.mkdir()
sys.path.insert(0,str(T/'tools/literature'));from smartpca_catalog.model import META
sources=['src/main/java/org/jlinalg/enrichment/PyScenicAucell.java','src/main/java/org/jlinalg/enrichment/CisTargetRecovery.java','src/test/java/org/jlinalg/enrichment/PyScenicAucellReferenceTest.java','src/test/resources/org/jlinalg/enrichment/pyscenic-original-aucell-reference.zip','tools/literature/generate_pyscenic_aucell_reference.py','tools/literature/verify_pyscenic_aucell_component.py']
sources+=[p.relative_to(T).as_posix() for p in (T/'src/main/resources/org/jlinalg/omics/pyscenic-ce41b61').rglob('*') if p.is_file()]
sources+=[p.relative_to(T).as_posix() for p in (T/'examples/pyscenic-aucell').iterdir() if p.is_file()]
source_pins={n:pin(T/n) for n in sources}
snapshot=B/'catalog-snapshot';snapshot.mkdir();before={};after={}
for name in (*META,'docs/literature/omics-2023-2026/SHA256SUMS'):
 raw=git('show','db45f34b42d0cff4d6393a9b1e460ce5bb5a67e1:'+name,cwd=T);identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());before[name]=identity;after[name]=pin(T/name);assert identity==after[name]
 target=snapshot/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
save(snapshot/'IDENTITIES.json',dict(before=before,after=after,base='db45f34b42d0cff4d6393a9b1e460ce5bb5a67e1',new_family_credit=0,new_reviews=0))
selected={};groups=['component-check-1','component-check-2','normal-build-1','normal-build-2','packaged-check-1','packaged-check-2','original-aucell-reference-1','original-aucell-reference-2','runtime-install-1','runtime-install-2','runtime-index-1','runtime-index-2','predecessor-checks-1','catalog-snapshot']
for group in groups:
 for path in sorted((B/group).rglob('*')):
  if not path.is_file():continue
  name=path.relative_to(B).as_posix()
  if '/project-build/' in name and not ('/project-build/test-results/test/' in name and path.suffix=='.xml'):continue
  selected[name]=path
for name in ('primary-audit-1/SUMMARY.json','primary-audit-1/IDENTITIES.json','SOURCE-ROLES-DRAFT.json','EARLIER-PRODUCER-LIMITS.json'):selected[name]=B/name
for name in sources:selected['qualified-source/'+name]=T/name
files={};objects=set()
with zipfile.ZipFile(Q/'evidence.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
 for name,path in sorted(selected.items()):
  identity=pin(path);assert identity['bytes']<67_108_864;files[name]=identity
  if identity['sha256'] not in objects:archive.write(path,'objects/'+identity['sha256']);objects.add(identity['sha256'])
save(Q/'EVIDENCE.json',dict(files=files,full_primary_texts_included=False,installed_runtime_binaries_included=False,source_resources_selected_GPL_BSD_only=True))
normal=json.loads((B/'normal-build-2/RESULT.json').read_bytes());assert normal['normal_tests']==dict(tests=2059,failures=0,errors=0,skipped=0)
save(Q/'STATUS.json',dict(schema_version=1,status='PASS_NATIVE_PYSCENIC_AUCELL_COMPONENT_ONLY',source_code=source_pins,evidence_index=pin(Q/'EVIDENCE.json'),evidence_archive=pin(Q/'evidence.zip'),producer=normal['producer'],
 normal_tests=2059,previous_payloads_unchanged=22995,focused_methods_per_JVM=7,original_unique_cases=37,original_permutation_cases=114,
 native_returned_score_cases=29,native_refusals=8,original_refusals=6,native_component_qualified=True,full_SCENIC_workflow_qualified=False,catalog_admitted=False,
 whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_family_credit=0,new_reviews=0,source_role_drafts=5,missed_provider_candidates=['P153:SCENIC+']))
roots=[]
for stage in ('component-check-1','component-check-2','packaged-check-1','packaged-check-2'):
 for host in ('windows21','windows25','linux21'):
  root=B/stage/host/'classes';children=[]
  for path in sorted(root.rglob('*')):
   if path.is_file():
    member=path.relative_to(B).as_posix();identity=pin(path);assert files[member]==identity;children.append(dict(path=path.relative_to(root).as_posix(),identity=identity,logical_evidence=member,member='objects/'+identity['sha256']))
  assert children;roots.append(dict(root=str(root),files=children))
save(Q/'CLEANUP-PLAN.json',dict(status='AFTER_BYTE_EXACT_PUBLIC_RECOVERY_VERIFIED_ONLY',roots=roots,recovery_archive=pin(Q/'evidence.zip'),scientific_inputs_outputs_exact_producers_runtimes_eligible=False))
helpers=Q/'helpers';helpers.mkdir()
names=['start_scenic_20261123_270.py','intake_scenic_sources_20261123_271.py','audit_scenic_roles_20261123_272.py','install_scenic_runtime_20261123_273.py','install_scenic_runtime_20261123_274.py','intake_numpy_scenic_20261123_275.py','check_pyscenic_aucell_core_20261123_276.py','check_pyscenic_aucell_core_20261123_277.py','package_pyscenic_sources_20261123_278.py','build_pyscenic_aucell_20261123_279.py','curate_scenic_roles_20261123_280.py','qualify_pyscenic_aucell_jar_20261123_281.py','build_pyscenic_aucell_20261123_282.py','index_scenic_runtime_20261123_283.py','index_scenic_runtime_20261123_284.py','final_pyscenic_checks_20261123_285.py','qualify_pyscenic_aucell_jar_20261123_285.py',Path(__file__).name,'handover_inventory_20261122_111.py']
for name in names:shutil.copyfile(R/'build'/name,helpers/name)
save(R/'build/git-publish/20261123-scenic-270/FROZEN.json',dict(status='NATIVE_COMPONENT_EVIDENCE_FROZEN',logical_files=len(files),objects=len(objects),archive=pin(Q/'evidence.zip'),new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(logical_files=len(files),objects=len(objects),archive=pin(Q/'evidence.zip')),indent=2))

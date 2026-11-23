from pathlib import Path
import hashlib,json,shutil,sys,zipfile
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-catalog-255';Q=T/'docs/literature/smartpca-catalog-checkpoint';Q.mkdir()
sys.path.insert(0,str(T/'tools/literature'));from smartpca_catalog import catalog as c,model as m
sources=c.sources();context=B/'source-context-final';context.mkdir()
for name in sources:
 dest=context/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
groups=['checks-1','plan-windows-1','inspect-windows-1','inspect-linux-1','apply-windows-1','verify-windows-1','verify-linux-1','controls-1','ui-preview-1','ui-preview-2','ui-installed-1','website-2','transaction-tests','source-context-final']
selected={}
for group in groups:
 for path in (B/group).rglob('*'):
  if not path.is_file():continue
  relative=path.relative_to(B)
  if any(part in ('owned-edge-profile','private-altered-primary','__pycache__') for part in relative.parts):continue
  if path.suffix.lower() in ('.gz','.pyc','.class','.exe','.dll','.so'):continue
  if path.suffix.lower()=='.jar' and path.stat().st_size>128:continue
  assert path.stat().st_size<80_000_000,relative
  selected[relative.as_posix()]=path
for name in ('SOURCE-CONTEXTS.json','EXPANDED-NAME-SCREEN.json'):selected[name]=B/name
index={};objects={}
for name,path in sorted(selected.items()):
 raw=path.read_bytes();identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());index[name]=identity;objects[identity['sha256']]=raw
archive=Q/'evidence.zip'
with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for digest,raw in sorted(objects.items()):
  info=zipfile.ZipInfo('objects/'+digest,(2026,11,23,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
  z.writestr(info,raw,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
save(Q/'EVIDENCE.json',dict(files=index,full_primary_texts_included=False,browser_profiles_included=False,private_altered_primary_retained_only_locally=True))
website=json.loads((B/'website-2/VALIDATION.json').read_bytes())
validation=dict(status='PASS_SMARTPCA_ONE_NATIVE_FAMILY_SIX_CONTEXT_ROLES_WITH_RETAINED_WEBSITE_FAILURE',tests_per_host=21,hosts=['Windows','Linux'],fresh_XML_checked_per_host=250,
 actual_CLI_refusal_controls=10,styled_preview_and_installed_UI_passed=True,prior_reviews_preserved=1691,new_reviews=6,
 observations=2722,reviewed_uses=1697,pending_uses=1025,scoped_families=154,native=29,upstream=125,complete_manifest_entries=861,
 full_checkWebsite_passed=False,preexisting_missing_citation_panels=website['preexisting_missing_citation_panels'],new_missing_citation_panels=[],
 website_reports_withheld=True,old_browser_task_guard_failure_retained=True,corrected_unique_profiles_closed=True,whole_goal_complete=False)
save(Q/'VALIDATION.json',validation)
sources['tools/literature/verify_smartpca_catalog_checkpoint.py']=pin(T/'tools/literature/verify_smartpca_catalog_checkpoint.py')
sources['docs/literature/smartpca-catalog-checkpoint/VALIDATION.json']=pin(Q/'VALIDATION.json')
save(Q/'STATUS.json',dict(status=validation['status'],schema_version=1,source_code=sources,evidence_index=pin(Q/'EVIDENCE.json'),evidence_archive=pin(archive),
 logical_files=len(index),unique_objects=len(objects),tests_per_host=21,actual_CLI_refusals=10,complete_manifest_entries=861,
 scoped_families=154,native=29,upstream=125,including_existing_adapters=157,reviewed_uses=1697,pending_uses=1025,
 new_native_family_credit=1,new_upstream_family_credit=0,new_reviews=6,unchanged_prior_reviews=1691,catalog_admitted=True,native_workflow_qualification_unchanged=True,
 whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,full_checkWebsite_passed=False,preexisting_missing_citation_panels=33,unrelated_worktree_owners_preserved=18))
helpers=Q/'helpers';helpers.mkdir()
names=['start_smartpca_catalog_20261123_255.py','prepare_smartpca_catalog_20261123_256.py','prepare_smartpca_catalog_controller_20261123_257.py',
 'prepare_smartpca_catalog_runners_20261123_258.py','run_smartpca_catalog_ui_20261123_258.py','close_smartpca_owned_browser_20261123_259.cjs','close_mrpresso_owned_browser_20261123_181.cjs',
 'run_smartpca_catalog_phase_20261123_258.py','run_smartpca_catalog_checks_20261123_258.py','run_smartpca_catalog_controls_20261123_258.py','check_smartpca_catalog_website_20261123_258.py',
 'fix_smartpca_browser_guard_20261123_259.py','apply_smartpca_catalog_20261123_260.py','retain_smartpca_catalog_contexts_20261123_261.py',Path(__file__).name,'handover_inventory_20261122_111.py']
for name in names:shutil.copyfile(R/'build'/name,helpers/name)
roots=[]
for root in (B/'plan-windows-1/result/preview',B/'source-context-final'):
 children=[]
 for path in sorted(root.rglob('*')):
  if path.is_file():
   member=path.relative_to(B).as_posix();identity=pin(path);assert index[member]==identity;children.append(dict(path=path.relative_to(root).as_posix(),identity=identity,logical_evidence=member,member='objects/'+identity['sha256']))
 roots.append(dict(root=str(root),files=children))
save(Q/'CLEANUP-PLAN.json',dict(status='AFTER_VERIFIED_PUBLIC_BYTE_EXACT_RECOVERY_ONLY',roots=roots,recovery_archive=pin(archive),source_producer_runtime_inputs_failed_evidence_eligible=False))
save(R/'build/git-publish/20261123-smartpca-catalog-255/CHECKPOINT-FROZEN.json',dict(status=validation['status'],logical_files=len(index),unique_objects=len(objects),archive=pin(archive),new_native_family_credit=1,new_reviews=6,whole_goal_complete=False))
print(json.dumps(dict(status=validation['status'],logical_files=len(index),unique_objects=len(objects),archive=pin(archive)),indent=2))

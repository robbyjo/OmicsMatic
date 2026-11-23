from pathlib import Path
import hashlib,json,shutil,sys,zipfile
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-catalog-178';Q=T/'docs/literature/mrpresso-catalog-checkpoint';Q.mkdir()
sys.path.insert(0,str(T/'tools/literature'));from mrpresso_catalog import catalog as c,model as m
groups=['checks-1','checks-2','plan-windows-1','plan-windows-2','inspect-windows-1','inspect-linux-1','inspect-windows-2','inspect-linux-2',
 'ui-preview-1','ui-preview-2','ui-installed-1','controls-1','controls-2','website','website-2','apply-windows-1','verify-windows-1','verify-linux-1','source-context-final','source-context-plan1','transaction-tests']
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
for name in ('SOURCE-CONTEXTS.json','SUPPLEMENTARY-ALIAS-SCREEN.json'):selected['source-contexts/'+name]=B/name
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
validation=dict(status='PASS_MRPRESSO_ONE_NATIVE_FAMILY_SIX_ROLES_WITH_RETAINED_WEBSITE_FAILURE',tests_per_host=19,hosts=['Windows','Linux'],
 fresh_XML_checked_per_host=250,actual_CLI_refusal_controls=10,styled_preview_and_installed_UI_passed=True,prior_reviews_preserved=1685,new_reviews=6,
 observations=2722,reviewed_uses=1691,pending_uses=1031,scoped_families=153,native=28,upstream=125,complete_manifest_entries=859,
 full_checkWebsite_passed=False,preexisting_missing_citation_panels=website['preexisting_missing_citation_panels'],new_missing_citation_panels=[],
 website_reports_withheld=True,whole_goal_complete=False)
save(Q/'VALIDATION.json',validation)
sources=c.sources();sources['tools/literature/verify_mrpresso_catalog_checkpoint.py']=pin(T/'tools/literature/verify_mrpresso_catalog_checkpoint.py')
sources['docs/literature/mrpresso-catalog-checkpoint/VALIDATION.json']=pin(Q/'VALIDATION.json')
status=dict(status=validation['status'],schema_version=1,source_code=sources,evidence_index=pin(Q/'EVIDENCE.json'),evidence_archive=pin(archive),
 logical_files=len(index),unique_objects=len(objects),tests_per_host=19,actual_CLI_refusals=10,complete_manifest_entries=859,
 scoped_families=153,native=28,upstream=125,including_existing_adapters=156,reviewed_uses=1691,pending_uses=1031,
 new_native_family_credit=1,new_upstream_family_credit=0,new_reviews=6,unchanged_prior_reviews=1685,
 catalog_admitted=True,native_workflow_qualification_unchanged=True,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,
 full_checkWebsite_passed=False,preexisting_missing_citation_panels=33,unrelated_worktree_owners_preserved=18)
save(Q/'STATUS.json',status)
helpers=Q/'helpers';helpers.mkdir()
names=['start_mrpresso_catalog_20261123_178.py','prepare_mrpresso_catalog_controller_20261123_179.py','prepare_mrpresso_catalog_tests_20261123_180.py',
 'prepare_mrpresso_catalog_runners_20261123_181.py','run_mrpresso_catalog_ui_20261123_181.py','close_mrpresso_owned_browser_20261123_181.cjs',
 'run_mrpresso_catalog_phase_20261123_181.py','run_mrpresso_catalog_checks_20261123_181.py','run_mrpresso_catalog_controls_20261123_181.py',
 'check_mrpresso_catalog_website_20261123_182.py','recheck_mrpresso_website_classification_20261123_183.py','check_mrpresso_catalog_website_20261123_183.py',
 'apply_mrpresso_catalog_20261123_184.py','retain_mrpresso_catalog_contexts_20261123_185.py','freeze_mrpresso_catalog_checkpoint_20261123_186.py','handover_inventory_20261122_111.py']
for name in names:shutil.copyfile(R/'build'/name,helpers/name)
save(R/'build/git-publish/20261123-mrpresso-catalog-178/CHECKPOINT-FROZEN.json',dict(status=status['status'],logical_files=len(index),unique_objects=len(objects),archive=pin(archive),
 private_primary_and_browser_profiles_excluded=True,new_native_family_credit=1,new_reviews=6,whole_goal_complete=False))
print(json.dumps(dict(status=status['status'],logical_files=len(index),unique_objects=len(objects),archive=pin(archive)),indent=2))

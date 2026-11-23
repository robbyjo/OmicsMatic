from pathlib import Path
import json,shutil,zipfile
from handover_inventory_20261122_111 import pin,save,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-1';Q=T/'docs/literature/smartpca-native-development'
normal=json.loads((B/'normal-build-1/RESULT.json').read_bytes());final=json.loads((B/'final-producer-check-3/COMPLETED.json').read_bytes())
assert normal['unique_tests_covered']==1871 and final['native_component_qualified'] and not final['workflow_qualified']
roles=(B/'source-roles-3/ROLES.json').read_bytes();assert roles==(B/'source-roles-linux-2/ROLES.json').read_bytes()
(Q/'ROLES.json').write_bytes(roles)
save(B/'SOURCE-ROLE-REFINEMENT.json',dict(status='CURRENT_SIX_ROLE_DRAFT_PRESERVES_UNREPORTED_SEPARATE_FST_EDITION',
 first='source-roles-1/ROLES.json',current='source-roles-3/ROLES.json',cause='P180 PCA names7.2.0; separate FST paragraph does not restate the edition. Current roles preserve that distinction.',
 canonical_reviews_changed=False,scientific_source_changed=False))
selected={}
catalog_paths=['docs/literature/omics-2023-2026/'+name for name in ('papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS')]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
import hashlib
before={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for name in catalog_paths for raw in [git('show','1709bf5be17e1c6b6a5250b1086f8b01c65341fc:'+name,cwd=T)]}
after={name:pin(T/name) for name in catalog_paths};assert before==after
save(B/'CATALOG-PRESERVATION.json',dict(base='1709bf5be17e1c6b6a5250b1086f8b01c65341fc',before=before,after=after,catalog_admitted=False,new_reviews=0,new_family_credit=0))
def add(path,name=None):
 if path.is_file():
  relative=path.relative_to(B).as_posix() if name is None else name
  assert relative not in selected;selected[relative]=path
for folder in ('component-check-1','final-producer-check-1','final-producer-check-2','final-producer-check-3','setup-controls-1','reference-setup-records-1','source-roles-1','source-roles-2','source-roles-linux-1','source-roles-3','source-roles-linux-2','freeze-first-attempt'):
 for path in (B/folder).rglob('*'):
  if path.is_file():add(path)
for name in ('COMPLETED.json',):add(B/'original-program-reference-1'/name)
add(B/'SOURCE-ROLE-REFINEMENT.json')
add(B/'CATALOG-PRESERVATION.json')
for path in (B/'normal-build-1').iterdir():
 if path.is_file():add(path)
for path in (B/'normal-build-1/source-context').rglob('*'):
 if path.is_file():add(path)
for path in (B/'normal-build-1/project-build/test-results/test').glob('TEST-*.xml'):add(path,'normal-build-1/test-results/'+path.name)
for path in (B/'normal-build-1/configured-followup-1').rglob('*'):
 if path.is_file() and 'html' not in path.relative_to(B/'normal-build-1/configured-followup-1').parts:add(path)
for path in (B/'source-intake-1').iterdir():
 if path.name in ('SOURCE.json','tag.json'):add(path)
for name in ('NEXT-PRIORITY-CENSUS.json','NEXT-PRIORITY-PRIMARY-IDENTITIES.json'):add(B/name)
helpers=Q/'helpers';helpers.mkdir()
for path in sorted((R/'build').glob('*smartpca*20261123_*.py')):shutil.copyfile(path,helpers/path.name)
shutil.copyfile(R/'build/handover_inventory_20261122_111.py',helpers/'handover_inventory_20261122_111.py')
index={name:pin(path) for name,path in sorted(selected.items())}
with zipfile.ZipFile(Q/'evidence.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
 for name,path in sorted(selected.items()):archive.write(path,name)
save(Q/'EVIDENCE.json',dict(files=index,contains_full_primary_texts=False,contains_original_runtime_binaries=False,contains_private_cohort_data=False))
paths=['src/main/java/org/jlinalg/genetics/SmartpcaPrepared.java','src/test/java/org/jlinalg/genetics/SmartpcaPreparedTest.java',
 'src/test/resources/org/jlinalg/genetics/smartpca-original-reference.zip','tools/literature/setup_smartpca_reference.py',
 'tools/literature/generate_smartpca_reference.py','tools/literature/audit_smartpca_sources.py','tools/literature/verify_smartpca_component_checkpoint.py',
 'src/main/java/org/jlinalg/compute/BackendContext.java','src/main/java/org/jlinalg/compute/BackendPolicy.java','docs/literature/SMARTPCA_NATIVE_DEVELOPMENT.md',
 'examples/smartpca-prepared/PreparedExample.java','docs/literature/smartpca-native-development/ROLES.json']
paths += [p.relative_to(T).as_posix() for p in (T/'src/main/resources/org/jlinalg/omics/smartpca-4f7e9f0').rglob('*') if p.is_file()]
status=dict(status='PASS_SMARTPCA_PREPARED_COMPONENT_NOT_WORKFLOW',schema_version=1,source_code={name:pin(T/name) for name in paths},
 evidence_index=pin(Q/'EVIDENCE.json'),evidence_archive=pin(Q/'evidence.zip'),evidence_files=len(index),
 component_methods=7,original_program_calls=12,finite_original_cases=9,unique_normal_tests=1871,configured_followups=5,
 producer=normal['producer'],previous_payloads_unchanged=22929,source_commit='4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1',
 papers_source_checked=250,source_role_drafts=6,canonical_catalog_unchanged=True,scoped_families=153,reviewed_uses=1691,pending_uses=1031,
 workflow_qualified=False,catalog_admitted=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_family_credit=0,new_reviews=0)
save(Q/'STATUS.json',status)
attributes=T/'.gitattributes';attributes.write_bytes(attributes.read_bytes()+b'\ndocs/literature/smartpca-native-development/** -text whitespace=cr-at-eol\ndocs/literature/SMARTPCA_NATIVE_DEVELOPMENT.md -text whitespace=cr-at-eol\ntools/literature/audit_smartpca_sources.py -text whitespace=cr-at-eol\ntools/literature/verify_smartpca_component_checkpoint.py -text whitespace=cr-at-eol\nexamples/smartpca-prepared/** -text whitespace=cr-at-eol\n')
print(json.dumps(dict(status=status['status'],evidence_files=len(index),archive=status['evidence_archive'],source_pins=len(paths)),indent=2))

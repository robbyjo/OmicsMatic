from pathlib import Path
import hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1'
O=R/'build/git-publish/20261123-mrpresso-handover-161'
P=T/'docs/literature/mrpresso-development-handover-20261123';P.mkdir()
helpers=P/'helpers';helpers.mkdir()
names=['handover_inventory_20261122_111.py','audit_next_high_impact_20261122_152.py',
 'intake_mrpresso_original_20261122_153.py','install_mrpresso_private_20261122_154.py',
 'reference_mrpresso_random_20261122_154.py','run_mrpresso_original_references_20261122_154.py',
 'check_mrpresso_random_20261122_155.py','freeze_mrpresso_api_fixture_20261122_156.py',
 'check_mrpresso_native_20261122_156.py','prepare_mrpresso_resources_20261122_157.py',
 'prepare_mrpresso_global_20261123_158.py','build_mrpresso_native_20261123_158.py',
 'probe_mrpresso_packaged_20261123_159.py','probe_mrpresso_small_units_verifier_20261123_160.py',
 'handover_mrpresso_inventory_20261123_161.py','probe_mrpresso_handover_20261123_161.py',
 'package_mrpresso_handover_20261123_161.py']
for name in names:shutil.copyfile(R/'build'/name,helpers/name)
groups=['upstream-intake-1','random-reference-windows-1','original-reference-windows-1',
 'random-component-checks-1','native-component-checks-1','native-component-checks-2',
 'normal-build-1','normal-build-2','packaged-smoke-1','small-unit-verifier-probe-1',
 'handover-packaged-1','runtime-install-windows-1','pre-dispatch-baseline-1']
selected={}
for group in groups:
 for p in (B/group).rglob('*'):
    if not p.is_file():continue
    relative=p.relative_to(B);parts=relative.parts
    if p.suffix.lower() in ('.class','.jar','.pyc','.so','.dll','.exe'):continue
    if group=='runtime-install-windows-1' and ('library' in parts or p.name.endswith('.tar.gz')):continue
    if group.startswith('normal-build-') and 'project-build' in parts:
        if not (p.suffix=='.xml' and 'test-results' in parts):continue
    if any(x in ('classes','__pycache__') for x in parts):continue
    assert p.stat().st_size<8_000_000,(p,p.stat().st_size)
    selected[relative.as_posix()]=p
p=B/'INVALIDATED-PRELIMINARY-VERIFIER.json';selected[p.name]=p
assert len(selected)>200
members={name:pin(p) for name,p in sorted(selected.items())};temporary=O/'evidence.zip'
with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for name,p in sorted(selected.items()):
    info=zipfile.ZipInfo(name,(2026,11,23,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
    z.writestr(info,p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
identity=pin(temporary);name='evidence-'+identity['sha256']+'.zip';shutil.copyfile(temporary,P/name)
save(P/'EVIDENCE.json',dict(archive=name,identity=identity,members=members,contains_full_primary_texts=False,
 includes_producer_jars=False,includes_installed_runtime_binaries=False,synthetic_reference_data_only=True))
sources={}
for base,pattern in [('src/main/java/org/jlinalg/cli','MrPresso*.java'),('src/test/java/org/jlinalg/cli','MrPresso*.java'),
 ('src/main/java/org/jlinalg/mrpresso','*.java'),('src/test/java/org/jlinalg/mrpresso','*.java')]:
 for p in (T/base).glob(pattern):sources[p.relative_to(T).as_posix()]=pin(p)
for base in ('src/main/resources/org/jlinalg/omics/mrpresso-3e3c92d','src/test/resources/org/jlinalg/mrpresso','tools/literature/mrpresso_workflow'):
 for p in (T/base).rglob('*'):
    if p.is_file():sources[p.relative_to(T).as_posix()]=pin(p)
p=T/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';sources[p.relative_to(T).as_posix()]=pin(p)
save(P/'SOURCES.json',sources)
counts={};suites=[]
for p in sorted((B/'normal-build-2/project-build/test-results/test').glob('*.xml')):
 x=ET.fromstring(p.read_bytes());row={key:int(x.get(key,'0')) for key in ('tests','failures','errors','skipped')};row['suite']=x.get('name');suites.append(row)
for key in ('tests','failures','errors','skipped'):counts[key]=sum(x[key] for x in suites)
assert counts['tests']>100 and counts['failures']==counts['errors']==counts['skipped']==0,counts
smoke=json.loads((B/'handover-packaged-1/RESULT.json').read_bytes());assert len(smoke['cases'])==30 and all(x['exit_code']==x['expected'] for x in smoke['cases'])
status=dict(schema_version=1,status='UNFINISHED_NATIVE_MRPRESSO_DEVELOPMENT_HANDOVER',
 source_base_commit='e763d9ce33e8f8f2ef7dc6fd7bf0d8fc3c885e51',development_branch='codex/mrpresso-workflows-20261122',
 main_publication_scope='Handover note only; unfinished native command remains on development branch',
 whole_goal_complete=False,full_workflow_qualified=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,
 current_catalog=dict(papers=250,scoped_families=152,native=27,upstream=125,including_existing_adapters=155,reviewed_uses=1685,pending_uses=1037),
 upstream=dict(package='MRPRESSO',version='1.0',commit='3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87',license='GPL-3'),
 original_reference_scope=dict(public_api_calls=25,finite_returns_compared=20,original_errors=4,aliased_return_unreportable=1,rng_profiles=12),
 earlier_component_hosts=['windows21','windows25','linux21'],normal_build=json.loads((B/'normal-build-2/RESULT.json').read_bytes()),
 normal_build_tests=counts,test_suites=suites,packaged_handover_cases=len(smoke['cases']),packaged_hosts=['windows21','windows25','linux21'],
 expected_packaged_refusals=sum(x['expected']==2 for x in smoke['cases']),
 preliminary_verifier_invalidated=json.loads((B/'INVALIDATED-PRELIMINARY-VERIFIER.json').read_bytes()),
 current_producer_scoped_fix_checks_passed=True,private_transfer_performed=False,WSL_export_performed=False,
 cleanup=dict(removed_paths=[],recovered_bytes=0,reason='Unfinished feature; exact producers, failures, references and controls remain required for handover.'),
 publication_exclusions=['Previously rejected possible-sensitive-material historical branches','Full primary texts and private audits','Installed runtimes and producer JAR binaries','Unrelated dirty overlays'],
 preservation=dict(other_owners_unchanged_from_intake=18,registered_worktrees=19),
 checkpoint_source_files=len(sources),evidence_identity=identity,evidence_members=len(members))
save(P/'STATUS.json',status)
save(O/'PACKAGE.json',dict(packet=str(P),archive=identity,members=len(members),source_files=len(sources),tests=counts))
print(json.dumps(dict(packet=str(P),archive=identity,members=len(members),source_files=len(sources),tests=counts),indent=2))

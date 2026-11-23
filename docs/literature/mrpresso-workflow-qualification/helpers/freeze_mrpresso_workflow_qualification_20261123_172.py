from pathlib import Path
import hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1';P=T/'docs/literature/mrpresso-workflow-qualification';P.mkdir()
O=R/'build/git-publish/20261123-mrpresso-qualification-162'
for group in ('workflow-original-references-2','saved-controls-2','protocol-controls-1','independent-controls-1','extended-component-checks-3'):
 assert json.loads((B/group/'RESULT.json').read_bytes())['status']=='PASS',group
assert (B/'source-audit-2/ROLES.json').read_bytes()==(B/'source-audit-linux-2/ROLES.json').read_bytes()
roles=T/'docs/literature/mrpresso-catalog-sources';roles.mkdir();shutil.copyfile(B/'source-audit-2/ROLES.json',roles/'ROLES.json')
sources={}
for base,pattern in [('src/main/java/org/jlinalg/cli','MrPresso*.java'),('src/test/java/org/jlinalg/cli','MrPresso*.java'),
 ('src/main/java/org/jlinalg/mrpresso','*.java'),('src/test/java/org/jlinalg/mrpresso','*.java')]:
 for path in (T/base).glob(pattern):sources[path.relative_to(T).as_posix()]=pin(path)
for base in ('src/main/resources/org/jlinalg/omics/mrpresso-3e3c92d','src/test/resources/org/jlinalg/mrpresso',
 'tools/literature/mrpresso_workflow','tools/literature/mrpresso_catalog','examples/mrpresso-native'):
 for path in (T/base).rglob('*'):
  if path.is_file() and '__pycache__' not in path.parts:sources[path.relative_to(T).as_posix()]=pin(path)
for name in ('src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/audit_mrpresso_sources.py',
 'tools/literature/verify_mrpresso_workflow_checkpoint.py','docs/literature/MRPRESSO_NATIVE_WORKFLOWS.md',
 'docs/literature/mrpresso-catalog-sources/ROLES.json','LICENSES/LAPACK-3.12.1-BSD.txt'):
 sources[name]=pin(T/name)
save(P/'SOURCES.json',sources)
groups=['original-reference-windows-1','extended-reference-windows-1','extended-component-checks-1','extended-component-checks-2','extended-component-checks-3',
 'normal-build-2','normal-build-3','normal-build-4','near-perfect-guard-probe-1','workflow-original-references-1','workflow-original-references-2',
 'saved-controls-1','saved-controls-2','protocol-controls-1','independent-controls-1','final-suite-setup-1',
 'source-audit-1','source-audit-linux-1','source-audit-2','source-audit-linux-2']
selected={}
for group in groups:
 for path in (B/group).rglob('*'):
  if not path.is_file():continue
  name=path.relative_to(B).as_posix();parts=path.relative_to(B).parts
  if path.suffix.lower() in ('.jar','.class','.pyc','.dll','.so','.exe') or '__pycache__' in parts or 'classes' in parts:continue
  if group.startswith('normal-build-') and 'project-build' in parts:
   if not (path.suffix=='.xml' and 'test-results' in parts):continue
  assert path.stat().st_size<8_000_000,(name,path.stat().st_size)
  selected[name]=path
for label,base in [('run',T/'build/NEW-mrpresso-native-example'),('replay',T/'build/NEW-mrpresso-native-example-replay')]:
 for path in base.rglob('*'):
  if path.is_file():selected['documentation-examples/'+label+'/'+path.relative_to(base).as_posix()]=path
helpers=P/'helpers';helpers.mkdir()
names=['start_mrpresso_qualification_20261123_162.py','run_mrpresso_extended_references_20261123_162.py','prepare_mrpresso_extended_tests_20261123_163.py',
 'freeze_mrpresso_extended_fixture_20261123_163.py','check_mrpresso_extended_native_20261123_163.py','probe_mrpresso_old_perfect_guard_20261123_164.py',
 'run_mrpresso_workflow_references_20261123_165.py','check_mrpresso_saved_controls_20261123_166.py','prepare_mrpresso_examples_20261123_167.py',
 'prepare_mrpresso_final_suite_20261123_168.py','run_mrpresso_final_references_20261123_168.py','prepare_mrpresso_final_controls_20261123_169.py',
 'check_mrpresso_final_controls_20261123_169.py','check_mrpresso_protocol_20261123_170.py','check_mrpresso_independent_controls_20261123_171.py',
 'freeze_mrpresso_workflow_qualification_20261123_172.py','handover_inventory_20261122_111.py','build_mrpresso_native_20261123_158.py']
for name in names:shutil.copyfile(R/'build'/name,helpers/name)
for name in ('baseline-0.stdout','baseline-0.stderr','baseline-1.stdout','baseline-1.stderr','START.json','TEST-ASSERTION-REFINEMENT.json','MrPressoOriginalTest-before.java'):
 selected['qualification-baseline/'+name]=O/name
archive=O/'qualification-evidence.zip';members={name:pin(path) for name,path in sorted(selected.items())}
with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for name,path in sorted(selected.items()):
  info=zipfile.ZipInfo(name,(2026,11,23,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
  z.writestr(info,path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
identity=pin(archive);archive_name='evidence-'+identity['sha256']+'.zip';shutil.copyfile(archive,P/archive_name)
save(P/'EVIDENCE.json',dict(archive=archive_name,identity=identity,members=members,
 synthetic_reference_data_only=True,full_primary_texts_redistributed=False,producer_jars_included=False,installed_runtime_binaries_included=False))
counts={key:0 for key in ('tests','failures','errors','skipped')}
for path in (B/'normal-build-4/project-build/test-results/test').glob('*.xml'):
 suite=ET.fromstring(path.read_bytes())
 for key in counts:counts[key]+=int(suite.get(key,'0'))
assert counts==dict(tests=797,failures=0,errors=0,skipped=0)
status=dict(schema_version=1,status='PASS_NATIVE_MRPRESSO_ORIGINAL_PROFILE_SCOPED_WORKFLOW',native_workflow_qualified=True,
 profile='MRPRESSO_1.0_3e3c92d',upstream_commit='3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87',upstream_license='GPL-3',
 original_R_executed_by_native_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,
 catalog_admitted=False,new_family_credit=0,new_reviews=0,producer=pin(B/'normal-build-4/project-build/cli/omicsmatic-0.4.0.jar'),
 private_producer='D:/projects/JLinAlg/build/fu-1/build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar',
 private_linux_producer='/var/tmp/omicsmatic-mrpresso-qualification-20261123-168/omicsmatic-0.4.0.jar',
 normal_build_tests=counts,component_methods_per_host=8,component_hosts=['windows21','windows25','linux21'],
 original_public_api_calls=45,fully_finite_original_fit_comparisons=38,explicit_degenerate_reference_limits=2,original_errors=4,aliased_original_return_not_admitted=1,
 packaged_commands=309,independent_original_comparisons=153,actual_replays=21,
 packaged_hosts=['windows21','windows25','linux21'],saved_control_scenarios=17,saved_control_host_checks=51,
 protocol_commands=30,independent_scientific_contradictions_per_host=7,independent_control_hosts=['windows','linux'],
 fresh_primary_papers_per_host=250,fresh_source_role_packets_byte_identical=True,positive_paper_roles=6,discussion_only_execution_not_established=['P177'],
 sources_identity=pin(P/'SOURCES.json'),source_files=len(sources),evidence_identity=identity,evidence_manifest_identity=pin(P/'EVIDENCE.json'),evidence_members=len(members),
 retained_findings=[dict(producer='7b9bedea257c085da227f49223f032585450ad7101df60fe8ed26d5dab52c52b',issue='Preliminary fixed absolute verifier tolerance admitted million-fold tiny-unit coefficient/SE corruption; original witness retained in predecessor handover.'),
  dict(producer='0e44e4609a0895c2d2f67e5034325e8563531015b8ae6f59775ff7deb513ce85',issue='Earlier weighted perfect-fit guard missed unreliable finite inference; actual old capture retained; calculated diagnostics were not repaired.'),
  dict(producer='86bcb46ca7d5034546b13be5d25e52b804e1cdd6d81e355c01424fed675ab4c9',issue='Missing admission markers returned SKIP3 rather than FAIL2; both controls retained; final audit-only class refinement fixes classification.')],
 limitations=['Prepared-association native interface with literal column labels, finite full-rank design profile and explicit resource limits.',
  'Original reference exposure counts1..3; native capacity1..64 does not claim all possible shapes were separately original-runtime tested.',
  'Perfect-fit SE/t/P and near-zero cancellation-dependent fields retain explicit original/native discrepancies and no relative inference-equivalence claim.',
  'Original multivariable distortion values above1 are retained without clamping and not admitted as probability inference.',
  'No allele harmonization, instrument filtering, study method-selection tree, multiple-testing policy, biological or author-environment reproduction.',
  'Reference comparator checks known synthetic witnesses; it does not verify arbitrary scientific captures or authenticate data.'],
 current_catalog=dict(scoped_families=152,native=27,upstream=125,reviewed_uses=1685,pending_uses=1037),
 next_required_work='Guarded six-role catalog admission, transaction/rollback/concurrent-write tests, current report checks and publication; then continue the full250-paper goal.')
save(P/'STATUS.json',status)
save(O/'QUALIFICATION-FROZEN.json',dict(packet=str(P),producer=status['producer'],archive=identity,members=len(members),source_files=len(sources),new_family_credit=0,new_reviews=0))
print(json.dumps(dict(status=status['status'],archive=identity,members=len(members),source_files=len(sources),normal_build_tests=counts),indent=2))

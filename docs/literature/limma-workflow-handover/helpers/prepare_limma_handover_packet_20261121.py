from pathlib import Path
import hashlib,json,shutil,struct,subprocess,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';Q=T/'docs/literature/limma-workflow-handover';Q.mkdir()

def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
def copy(p,n):
    dst=Q/n;dst.parent.mkdir(parents=True,exist_ok=True);assert not dst.exists();shutil.copyfile(p,dst)

normal=json.loads((B/'normal-gradle-2/COMPLETED.json').read_bytes());assert normal['exit_code']==0
assert normal['reports']['org.jlinalg.cli.LimmaWorkflowTest']==dict(tests=69,failures=0,errors=0,skipped=0,failed_cases=[],stdout='LIMMA_SAVED_ORIGINAL_CASES=45 R_FREE_VERIFY=true\n')
for n,w in normal['source_inputs'].items():assert pin(T/n)==w,n
jar=B/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';old=R/'build/lm-2/build/limma-native-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(jar) as b:
    before=set(a.namelist());after=set(b.namelist());assert before<=after
    for n in before:assert a.read(n)==b.read(n),'Predecessor payload changed: '+n
    additions=sorted(after-before);classes={n:struct.unpack('>H',b.read(n)[6:8])[0] for n in additions if n.endswith('.class')};assert set(classes.values())=={61}
payloads=dict(predecessor_payloads_unchanged=len(before),new_payloads=additions,new_class_major_versions=classes,producer=pin(jar),producer_path=str(jar),live_execution_path_qualified=False)
save(Q/'PAYLOADS.json',payloads)
cases={};comparisons=0
for folder in ['direct-originals-2','direct-originals-variants-1']:
    data=json.loads((B/folder/'RESULTS.json').read_bytes());assert not(set(cases)&set(data));cases.update(data)
    for name,row in data.items():
        assert row['exit_code']==0 and row['result']['status']=='PASS_INDEPENDENT_DIRECT_STOCK_COMPLETE_OBJECTS',name
        assert row['result']['tolerance']==0 and row['result']['adapter_worker_sourced'] is False
        for result in row['result']['comparisons'].values():assert result['identical'] and result['all_equal'];comparisons+=1
    copy(B/folder/'RESULTS.json','receipts/'+folder+'-RESULTS.json')
assert len(cases)==45
for attempt in [1,2]:
    folder=B/('normal-gradle-'+str(attempt))
    for name in ['COMPLETED.json','COMMAND.json','stdout','stderr']:
        copy(folder/name,'receipts/normal-gradle-'+str(attempt)+'/'+name)
    copy(folder/'project-build/test-results/test/TEST-org.jlinalg.cli.LimmaWorkflowTest.xml','receipts/normal-gradle-'+str(attempt)+'/TEST.xml')
copy(B/'saved-fixture-1/COMPLETED.json','receipts/SAVED-FIXTURE.json')
helpers=sorted((R/'build').glob('*limma*20261121*.py'))
for p in helpers:copy(p,'helpers/'+p.name)
readme='''# Saved limma workflow development handover

This packet preserves saved receipts and helper sources for the
[current handover](../HANDOVER_2026-11-21_LIMMA_WORKFLOWS.md).
It adds no catalog credit and does not qualify the complete live normal-JAR
workflow. CHECKS.json pins current source and every other packet file.

Run `python -B tools/literature/verify_limma_workflow_handover.py` from the
repository root. The reader verifies saved bytes only, without R or a new fit.
The ordinary Gradle focused test command is
`gradlew.bat :test --tests org.jlinalg.cli.LimmaWorkflowTest`.
The development class can be inspected using
`java -cp PATH_TO_NORMAL_JAR org.jlinalg.cli.LimmaWorkflowCli --help`.
There is no global limma-tools registration yet.

The first normal build's two failed harness assertions remain preserved;
the corrected build passes all 69 focused tests. The synthetic fixture ZIP
contains 45 saved original-runtime analyses. Independent stock code matched
every compared complete original return exactly; totals are in CHECKS.json.
Full independent reference RDS files and failed/private run directories remain
local and require separate checksummed transfer. A GitHub clone is insufficient
for a complete machine handover.

Helpers are exact historical source snapshots with fixed Windows paths and
exclusive output directories. Inspect dependencies and create new attempt names
before reuse. Do not execute them blindly or refresh older source hashes.
The current packet-builder snapshot records its invocation before these packet
files existed; it is provenance rather than an idempotent installer.
'''
(Q/'README.md').write_text(readme,encoding='utf-8',newline='\n')
current=T/'docs/literature/HANDOVER_CURRENT.md';text=current.read_text(encoding='utf-8');assert text.startswith('# Current toolbox handover\n\n')
top='''**Latest handover: [21 November 2026 limma workflows and exact remaining work](HANDOVER_2026-11-21_LIMMA_WORKFLOWS.md).**
The standalone original-R adapter saves six prepared-data operations and 45
synthetic execution captures with exact independent complete-object comparisons.
The corrected normal build passes all 69 focused tests; the first failed harness
attempt remains retained. Full live normal-JAR/cross-host qualification, observer
error-path review, a tiny positive probability control, global CLI registration,
historical editions and source-role/catalog admission remain open. Totals stay
**141 families, 1,575 reviewed uses and 1,140 pending**; no new catalog credit.
Preserve all **33 worktrees** and transfer ignored exact runtimes, producer JARs,
primary full text and complete private evidence separately with checksums.
Both literature reports remain off Pages. The full goal remains unfinished.
Use this handover first; the lower entries retain historical checkpoints.

'''
current.write_text('# Current toolbox handover\n\n'+top+text[len('# Current toolbox handover\n\n'):],encoding='utf-8',newline='\n')
attrs=T/'.gitattributes'
with attrs.open('a',encoding='utf-8',newline='\n') as f:f.write('\n# Preserve exact limma workflow source profiles, fixtures and handover receipts.\nsrc/main/resources/org/jlinalg/omics/limma-workflow/** -text whitespace=-blank-at-eol,-blank-at-eof,cr-at-eol\nsrc/test/resources/org/jlinalg/cli/limma-workflow-originals.zip -text\ndocs/literature/limma-workflow-handover/** -text whitespace=cr-at-eol\n')
names=[n for n in normal['source_inputs'] if n!='build.gradle']+['docs/literature/HANDOVER_2026-11-21_LIMMA_WORKFLOWS.md','tools/literature/verify_limma_workflow_handover.py']
checks=dict(schema_version=1,status='SAVED_LIMMA_WORKFLOW_DEVELOPMENT_HANDOVER',source_inputs={n:pin(T/n) for n in names},saved_files={p.relative_to(Q).as_posix():pin(p) for p in sorted(Q.rglob('*')) if p.is_file()},original_compositions=45,complete_original_object_comparisons=comparisons,all_original_comparisons_identical=True,normal_focused_tests=69,normal_test_JVM='Windows Java21',normal_packaged_cross_host_tests_complete=False,normal_live_execution_path_qualified=False,predecessor_payloads_unchanged=len(before),helper_snapshots=len(helpers),catalog_credit_added=0,whole_goal_complete=False,scientific_source_bytes_refreshed=False)
save(Q/'CHECKS.json',checks)
print(json.dumps({k:v for k,v in checks.items() if k not in ['source_inputs','saved_files']},indent=2),flush=True)

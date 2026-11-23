from pathlib import Path
import hashlib,importlib.util,json,shutil,zipfile
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';S=T/'build/squidpy-1';Q=T/'docs/literature/squidpy-workflow-qualification';Q.mkdir()
provenance=T/'examples/squidpy-original/DATA-PROVENANCE.json';p=json.loads(provenance.read_bytes())
p['files']={name:value for name,value in p['files'].items() if name not in ('README.md','prepare_requests.py')}
p['files_scope']='Synthetic input bytes only; generator and README are separately pinned by workflow qualification.';save(provenance,p)
files={};objects={}
def capture(path,name):
 raw=path.read_bytes();identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert len(raw)<80_000_000,(name,len(raw))
 files[name]=identity;objects[identity['sha256']]=raw
def directory(origin,prefix):
 for path in sorted(origin.rglob('*')):
  if path.is_file():capture(path,prefix+'/'+path.relative_to(origin).as_posix())
normal=S/'workflow-normal-build-145'
for name in ('RESULT.json','PAYLOADS.json','COMMAND.json','SOURCE-INPUTS.json','stdout','stderr'):capture(normal/name,'normal/'+name)
directory(normal/'project-build/test-results/test','normal/tests')
directory(normal/'source-context','normal/source-context')
directory(S/'packaged-unit-146','packaged')
for version in ('1.1.2','1.2.2','1.8.4'):
 directory(S/('packaged-cases-'+version+'-145'),'cases/'+version)
 directory(S/('direct-oracle-'+version+('-138' if version=='1.1.2' else '-137')),'oracles/'+version)
 directory(S/('matrix-transport-'+version+'-144'),'transport/'+version)
 directory(S/('h5ad-regression-'+version+'-139'),'h5ad-regressions/'+version)
directory(S/'independent-comparison-146','independent')
directory(S/'saved-controls-146','controls')
directory(S/'runtime-controls-146','runtime-controls')
directory(S/'verification-hosts-146','verification-hosts')
directory(S/'examples-cases-145','examples')
directory(S/'worker-estimate-controls-137','worker-estimates')
directory(T/'build/squidpy-source-roles-145','primary')
directory(S/'matrix-fix-intake-143','invalidated')
for label in ('comparison-diagnostic-launch-141','comparison-diagnostic-launch-142'):
 directory(S/label,'failed-comparisons/'+label)
# Retain the malformed output bytes as diagnostics, never as admitted results.
invalid=json.loads((S/'matrix-fix-intake-143/INVALIDATED-MATRIX-EXPORTS.json').read_bytes())
for i,row in enumerate(invalid['files']):
 path=Path(row['file']);assert pin(path)=={k:row[k] for k in ('bytes','sha256')};capture(path,'invalidated/originals/'+str(i)+'.mtx')
# Earlier failed adapter attempts retain their exact source contexts and journals.
for phase in ('workflow-normal-build-137','workflow-normal-build-139','workflow-normal-build-143'):
 base=S/phase
 for name in ('RESULT.json','PAYLOADS.json','stdout','stderr'):capture(base/name,'earlier/'+phase+'/'+name)
 directory(base/'source-context','earlier/'+phase+'/source-context')
for version in ('1.1.2','1.2.2','1.8.4'):
 for phase in ('137','139'):
  base=S/('packaged-cases-'+version+'-'+phase);capture(base/'PROGRESS.json','earlier/'+base.name+'/PROGRESS.json')
  for name in ('h5ad-knn','moran-on-built-graph','matrix-market-ligrec'):
   folder=base/name
   if (folder/'FAILURE.json').exists():directory(folder,'earlier/'+base.name+'/'+name)
source_paths=[]
for base,pattern in [('src/main/java/org/jlinalg/cli','SquidpyWorkflow*.java'),('src/test/java/org/jlinalg/cli','SquidpyWorkflow*Test.java')]:source_paths.extend((T/base).glob(pattern))
source_paths.extend(T/n for n in ('src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/main/java/org/jlinalg/cli/OwnedProcesses.java','src/main/java/org/jlinalg/cli/GprofilerWorkflowContracts.java','src/main/java/org/jlinalg/gprofiler/GprofilerJson.java','src/main/java/org/jlinalg/cli/FusionWorkflowContracts.java','src/test/resources/org/jlinalg/squidpy/primitive-npy-original.zip','docs/literature/SQUIDPY_WORKFLOWS.md','tools/literature/verify_squidpy_workflow_capture.py','tools/literature/verify_squidpy_workflow_checkpoint.py'))
for base in ('tools/literature/squidpy_workflow','src/main/resources/org/jlinalg/omics/squidpy-workflow','examples/squidpy-original'):
 source_paths.extend(p for p in (T/base).rglob('*') if p.is_file())
sources={p.relative_to(T).as_posix():pin(p) for p in source_paths}
for n,identity in json.loads((normal/'SOURCE-INPUTS.json').read_bytes()).items():
 if n in sources:assert sources[n]==identity,n
helpers={}
for path in sorted((R/'build').iterdir()):
 if path.is_file() and path.suffix in ('.py','.java') and ('squidpy' in path.name.lower()) and any(str(n) in path.name for n in range(137,147)):
  capture(path,'helpers/'+path.name);helpers[path.name]=pin(path)
capture(R/'build/handover_inventory_20261122_111.py','helpers/handover_inventory_20261122_111.py')
save(Q/'EVIDENCE.json',dict(files=files,selection='Complete current synthetic workflow/independent/control captures; selected retained earlier failures, malformed exports and frozen source contexts. No private runtime binaries, full primary text or cohort data.'))
with zipfile.ZipFile(Q/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for digest,raw in sorted(objects.items()):
  info=zipfile.ZipInfo('objects/'+digest,(2026,11,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw,compresslevel=9)
assert (Q/'evidence.zip').stat().st_size<90_000_000
status=dict(schema_version=1,status='PASS_SQUIDPY_ORIGINAL_WORKFLOW_SCOPED_CHECKPOINT',source_code=sources,
    evidence_index=pin(Q/'EVIDENCE.json'),evidence_archive=pin(Q/'evidence.zip'),producer=json.loads((normal/'RESULT.json').read_bytes())['producer'],
    normal_tests=763,packaged_tests_per_JVM=763,packaged_JVMs=['windows21','windows25','linux21'],packaged_cases=73,
    complete_cases=60,expected_no_result_cases=6,skipped_prerequisite_cases=7,actual_replays=11,
    independent_original_comparisons=66,independent_original_public_calls=46,independent_original_returned=43,independent_original_raised=3,
    offline_controls=22,resealed_contradictions=5,offline_host_checks=44,runtime_controls=4,transport_controls=27,h5ad_regression_controls=12,
    input_work_estimate_controls=12,actual_example_RUN=4,actual_example_REPLAY=1,previous_payloads_preserved=22870,dispatcher_only_modified=True,
    invalidated_earlier_matrix_exports=16,global_command_enabled=True,original_runtime_RUN_platform='Linux',saved_verification_platforms=['Windows21','Windows25','Linux21','Python Windows/Linux'],
    opaque_H5AD_transport_decoded_by_offline_readers=False,source_wheel_members_verified=True,author_full_environment_reproduced=False,
    new_family_credit=0,new_reviews=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)
save(Q/'STATUS.json',status)
spec=importlib.util.spec_from_file_location('squidpy_checkpoint',T/'tools/literature/verify_squidpy_workflow_checkpoint.py');reader=importlib.util.module_from_spec(spec);spec.loader.exec_module(reader)
result=reader.verify(T,Path(status['producer']['path']));save(Q/'VALIDATION.json',result)
print(json.dumps(dict(status=result['status'],logical_files=len(files),objects=len(objects),archive_bytes=(Q/'evidence.zip').stat().st_size,source_files=len(sources))),flush=True)

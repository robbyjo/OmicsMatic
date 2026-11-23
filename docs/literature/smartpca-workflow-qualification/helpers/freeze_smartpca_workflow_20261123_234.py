from pathlib import Path
import hashlib,json,shutil,sys,zipfile
from handover_inventory_20261122_111 import git,pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-3';Q=T/'docs/literature/smartpca-workflow-qualification'
sys.path.insert(0,str(T/'tools/literature'))
from mrpresso_catalog.model import META
for run in ('protocol-windows21-3','protocol-windows25-2','protocol-linux21-1'):
 result=json.loads((B/run/'COMPLETED.json').read_bytes());assert all(x['exit_code']==x['expected_exit'] for x in result['commands'])
helpers=['start_smartpca_workflow_20261123_228.py','compile_smartpca_workflow_20261123_229.py','prepare_smartpca_workflow_build_20261123_230.py','build_smartpca_workflow_20261123_230.py','build_smartpca_workflow_20261123_231.py','build_smartpca_workflow_20261123_232.py','qualify_smartpca_host_checks_20261123_233.py',Path(__file__).name]
(Q/'helpers').mkdir(exist_ok=True)
for name in helpers:shutil.copyfile(R/'build'/name,Q/'helpers'/name)
source_names=[p.relative_to(T).as_posix() for p in (T/'src/main/java/org/jlinalg/cli').glob('SmartpcaWorkflow*.java')]
source_names+=['src/main/java/org/jlinalg/cli/SmartpcaToolsCli.java']
source_names+=['src/main/java/org/jlinalg/genetics/'+name+'.java' for name in ('SmartpcaPrepared','SmartpcaOutliers','SmartpcaShrinkage','SmartpcaAnalysis')]
source_names+=['src/main/java/'+name for name in ('org/jlinalg/cli/GprofilerWorkflowContracts.java','org/jlinalg/gprofiler/GprofilerJson.java','org/jlinalg/cli/MrPressoWorkflowContracts.java','org/jlinalg/compute/BackendContext.java','org/jlinalg/compute/BackendPolicy.java')]
source_names+=['src/test/java/org/jlinalg/genetics/'+name+'.java' for name in ('SmartpcaPreparedTest','SmartpcaExtensionsTest','SmartpcaAnalysisReferenceTest')]+['src/test/java/org/jlinalg/cli/SmartpcaWorkflowTest.java']
source_names+=['src/test/resources/org/jlinalg/genetics/'+name for name in ('smartpca-original-reference.zip','smartpca-original-outlier-reference.zip','smartpca-original-analysis-reference.zip')]
source_names+=[p.relative_to(T).as_posix() for p in (T/'src/main/resources/org/jlinalg/omics/smartpca-4f7e9f0').iterdir() if p.is_file()]
source_names+=['tools/literature/'+name for name in ('check_smartpca_workflows.py','verify_smartpca_workflow_checkpoint.py','audit_smartpca_sources.py')]
source_names+=[p.relative_to(T).as_posix() for p in (T/'examples/smartpca-native').iterdir() if p.is_file()]
source_names+=['build.gradle']
source_pins={name:pin(T/name) for name in sorted(source_names)}
catalog={};before={};snapshot=B/'catalog-snapshot';snapshot.mkdir(exist_ok=True)
for name in (*META,'docs/literature/omics-2023-2026/SHA256SUMS'):
 raw=git('show','05e2d615f7b1c6c791d2ed29ff3e45fa4517c5ff:'+name,cwd=T);identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());before[name]=identity;catalog[name]=pin(T/name);assert catalog[name]==identity,name
 target=snapshot/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
save(snapshot/'METADATA.json',dict(before=before,after=catalog,changed=[],base='05e2d615f7b1c6c791d2ed29ff3e45fa4517c5ff',new_family_credit=0,new_reviews=0))
files={};objects=set()
with zipfile.ZipFile(Q/'evidence.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
 def put(name,path):
  identity=pin(path);assert identity['bytes']<=33_554_432,name;assert name not in files;files[name]=identity
  if identity['sha256'] not in objects:archive.write(path,'objects/'+identity['sha256']);objects.add(identity['sha256'])
 for path in sorted(B.rglob('*')):
  if not path.is_file():continue
  name=path.relative_to(B).as_posix()
  if '/project-build/' in name and not ('/project-build/test-results/test/' in name and path.suffix=='.xml'):continue
  put(name,path)
 for name in source_pins:put('qualified-source/'+name,T/name)
 for name in helpers:put('orchestration/'+name,R/'build'/name)
save(Q/'EVIDENCE.json',dict(files=files,content_addressed=True,archive='evidence.zip'))
normal=json.loads((B/'normal-build-3/RESULT.json').read_bytes())
state=dict(schema_version=1,status='PASS_NATIVE_PREPARED_SMARTPCA_RUN_VERIFY_REPLAY',source_code=source_pins,evidence_index=pin(Q/'EVIDENCE.json'),evidence_archive=pin(Q/'evidence.zip'),producer=normal['producer'],
 workflow_qualified=True,global_dispatch_integrated=False,FST_implemented=False,catalog_admitted=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,
 new_family_credit=0,new_reviews=0,scoped_families_at_qualification=153,native_at_qualification=28,upstream_at_qualification=125,reviewed_uses_at_qualification=1691,pending_uses_at_qualification=1031,
 normal_tests=1892,packaged_protocol_commands=261,example_commands=18,workflow_methods_per_JVM=15,fresh_all250_audit_profiles=2,new_original_C_calls=0)
save(Q/'STATUS.json',state)
roots=[B/'compile-1/classes']+[B/'host-checks-1'/host/'classes' for host in ('windows21','windows25','linux21')];rows=[]
for root in roots:
 children=[]
 for path in sorted(root.rglob('*')):
  if not path.is_file():continue
  member=path.relative_to(B).as_posix();identity=pin(path);assert files[member]==identity;children.append(dict(path=path.relative_to(root).as_posix(),identity=identity,member=member))
 assert children;rows.append(dict(root=str(root),files=children))
save(Q/'CLEANUP-PLAN.json',dict(status='AFTER_BYTE_EXACT_PUBLICATION_ONLY',roots=rows,recovery_archive=pin(Q/'evidence.zip'),content_addressed_recovery=True,all_failed_scientific_evidence_retained=True,scientific_inputs_outputs_producers_and_runtimes_eligible=False))
print(json.dumps(dict(logical_files=len(files),objects=len(objects),archive=pin(Q/'evidence.zip'),source_files=len(source_pins),cleanup_roots=len(roots)),indent=2))

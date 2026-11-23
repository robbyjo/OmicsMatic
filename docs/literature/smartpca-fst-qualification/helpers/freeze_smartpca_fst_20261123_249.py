from pathlib import Path
import hashlib,json,shutil,sys,zipfile
from handover_inventory_20261122_111 import pin,save,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-4';Q=T/'docs/literature/smartpca-fst-qualification';Q.mkdir()
sys.path.insert(0,str(T/'tools/literature'));from mrpresso_catalog.model import META
for run,count in [('protocol-windows21-1',104),('protocol-windows25-1',129),('protocol-linux21-1',129)]:
 result=json.loads((B/run/'COMPLETED.json').read_bytes());assert len(result['commands'])==count and all(r['exit_code']==r['expected_exit'] for r in result['commands'])
helpers=['start_smartpca_fst_20261123_240.py','check_smartpca_fst_core_20261123_243.py','prepare_smartpca_fst_workflow_20261123_244.py','bridge_smartpca_dispatch_20261123_245.py','build_smartpca_fst_20261123_246.py','build_smartpca_fst_20261123_247.py','qualify_smartpca_fst_hosts_20261123_248.py',Path(__file__).name]
(Q/'helpers').mkdir()
for name in helpers:shutil.copyfile(R/'build'/name,Q/'helpers'/name)
sources=[p.relative_to(T).as_posix() for p in (T/'src/main/java/org/jlinalg/cli').glob('Smartpca*.java')]
sources+=['src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/main/java/org/jlinalg/genetics/SmartpcaFst.java','src/test/java/org/jlinalg/cli/SmartpcaFstWorkflowTest.java','src/test/java/org/jlinalg/genetics/SmartpcaFstReferenceTest.java','src/test/resources/org/jlinalg/genetics/smartpca-original-fst-reference.zip']
sources+=['tools/literature/'+name for name in ('generate_smartpca_fst_reference.py','check_smartpca_fst_workflows.py','verify_smartpca_fst_checkpoint.py','smartpca_dispatch_compatibility.py','smartpca_reference/jackknife_harness.c')]
sources+=[p.relative_to(T).as_posix() for p in (T/'examples/smartpca-fst').iterdir() if p.is_file()]
source_pins={name:pin(T/name) for name in sorted(set(sources))};snapshot=B/'catalog-snapshot';snapshot.mkdir();before={};after={}
for name in (*META,'docs/literature/omics-2023-2026/SHA256SUMS'):
 raw=git('show','eb167472e9e71cc51483e338ee3b8d32ca7bdcd8:'+name,cwd=T);identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());before[name]=identity;after[name]=pin(T/name);assert identity==after[name]
 target=snapshot/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
save(snapshot/'METADATA.json',dict(before=before,after=after,base='eb167472e9e71cc51483e338ee3b8d32ca7bdcd8',new_family_credit=0,new_reviews=0))
files={};objects=set()
with zipfile.ZipFile(Q/'evidence.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
 def put(name,path):
  identity=pin(path);assert identity['bytes']<33_554_432 and name not in files;files[name]=identity
  if identity['sha256'] not in objects:archive.write(path,'objects/'+identity['sha256']);objects.add(identity['sha256'])
 for path in sorted(B.rglob('*')):
  if not path.is_file():continue
  name=path.relative_to(B).as_posix()
  if '/project-build/' in name and not ('/project-build/test-results/test/' in name and path.suffix=='.xml'):continue
  put(name,path)
 for name in source_pins:put('qualified-source/'+name,T/name)
 for name in helpers:put('orchestration/'+name,R/'build'/name)
save(Q/'EVIDENCE.json',dict(files=files,archive='evidence.zip',content_addressed=True))
normal=json.loads((B/'normal-build-2/RESULT.json').read_bytes());assert normal['normal_tests']==dict(tests=1906,failures=0,errors=0,skipped=0)
save(Q/'STATUS.json',dict(schema_version=1,status='PASS_NATIVE_SMARTPCA_PCA_FST_GLOBAL_WORKFLOWS',source_code=source_pins,evidence_index=pin(Q/'EVIDENCE.json'),evidence_archive=pin(Q/'evidence.zip'),producer=normal['producer'],
 native_fst_workflow_qualified=True,global_CLI_dispatch_available=True,catalog_admitted=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,new_family_credit=0,new_reviews=0,
 normal_tests=1906,packaged_protocol_commands=362,source_program_calls=24,direct_source_jackknife_calls=23,source_finite_comparisons_across_profiles=69,methods_per_JVM=14,example_commands=18,source_audit_profiles=2))
roots=[B/'component-check-1'/h/'classes' for h in ('windows21','windows25','linux21')]+[B/'host-checks-1'/h/'classes' for h in ('windows21','windows25','linux21')];rows=[]
for root in roots:
 children=[]
 for path in sorted(root.rglob('*')):
  if path.is_file():
   member=path.relative_to(B).as_posix();identity=pin(path);assert files[member]==identity;children.append(dict(path=path.relative_to(root).as_posix(),identity=identity,member=member))
 assert children;rows.append(dict(root=str(root),files=children))
save(Q/'CLEANUP-PLAN.json',dict(status='AFTER_BYTE_EXACT_PUBLICATION_ONLY',roots=rows,recovery_archive=pin(Q/'evidence.zip'),content_addressed_recovery=True,scientific_inputs_outputs_producers_runtimes_eligible=False))
print(json.dumps(dict(logical_files=len(files),objects=len(objects),archive=pin(Q/'evidence.zip'),source_files=len(source_pins)),indent=2))

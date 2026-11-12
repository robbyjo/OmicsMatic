from pathlib import Path
import hashlib,json,lzma,os,re,shutil,stat,time,zipfile
B=Path(__file__).resolve().parent;ROOT=B.parents[2];DATA=ROOT/'docs/literature/hops035-postprocessing-development';DEV=B/'hops035-postprocessing-development-1'
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def read(p):return json.loads(p.read_bytes())
def payload(p):
 with zipfile.ZipFile(p) as z:return {i.filename:dict(bytes=i.file_size,sha256=hashlib.sha256(z.read(i)).hexdigest()) for i in z.infolist() if not i.is_dir()}
assert not (DATA/'VALIDATION.json').exists() and not (DATA/'controls.zip.xz').exists()
proof=[]
for version,old,new in [('21','hops035-postprocessing-build21-2','hops035-postprocessing-build21-3'),('25','hops035-postprocessing-build25-1','hops035-postprocessing-build25-2')]:
 oldjar=B/old/'hops035-postprocessing-development.jar';newjar=B/new/'hops035-postprocessing-development.jar';assert payload(oldjar)==payload(newjar)
 proof.append(dict(JDK_compiler=version,producing_artifact=pin(oldjar),final_artifact=pin(newjar),identical_program_resource_members=len(payload(newjar)),container_timestamps_may_differ=True))
 shutil.copyfile(newjar,DATA/('hops035-postprocessing-jdk'+version+'.jar'))
shutil.copyfile(ROOT/'LICENSE',DATA/'LICENSE-project.txt')
shutil.copyfile(B/'hops035-postprocessing-source-intake-1/share/hops-0.35-1/LICENSE',DATA/'LICENSE-HOPS.txt')
save(DATA/'PACKAGED_APPLICATION_IDENTITY.json',dict(status='PASS_IDENTICAL_PRODUCER_AND_FINAL_EXECUTABLE_PAYLOADS',comparisons=proof))
logical={};objects={}
def add(name,path):
 assert name and not name.startswith('/') and '\\' not in name and not any(p in ['','.','..'] for p in name.split('/')),name
 assert not path.is_symlink() and path.is_file(),path
 assert path.suffix.lower()!='.sif' and 'gatk' not in path.name.lower(),path
 assert name not in logical,name
 value=pin(path);assert value['bytes']<=268435456,path
 logical[name]=dict(**value,mode=stat.S_IMODE(path.stat().st_mode));objects.setdefault(value['sha256'],path)
def tree(name,path):
 assert path.is_dir(),path
 for p in sorted(path.rglob('*')):
  assert not p.is_symlink(),p
  if p.is_file():add(name+'/'+p.relative_to(path).as_posix(),p)
 print('Captured complete evidence',name,len(logical),'members',flush=True)
case_root=Path('/var/tmp/omicsmatic-hops035-post-adapter-controls-1')
for case in ['complete10001-2','multi-sample-1','hyphen-jdk25-1']:
 report=read(case_root/case/'bundle/audit.json');assert report['status']=='complete-captured-original-hops035-postprocessing-only';assert (case_root/case/'final-verification/VERIFIED.json').exists();tree('controls/'+case,case_root/case)
for label,case in [('initial-provider-link','complete10001-1'),('original-default','original-default-failure-1'),('empty-candidates','empty-candidates-failure-1')]:
 assert (case_root/case/'FAILURE.json').exists() and not (case_root/case/'bundle/COMPONENT_COMPLETED.json').exists();tree('failed/'+label,case_root/case)
refusals=Path('/var/tmp/omicsmatic-hops035-rehashed-refusals-1');assert read(refusals/'COMPLETED.json')['status']=='PASS_NINE_REHASHED_HOPS_POSTPROCESSING_CONTRADICTION_REFUSALS';tree('refusals',refusals)
policy=Path('/var/tmp/omicsmatic-hops035-policy-controls-2');assert read(policy/'COMPLETED.json')['status']=='PASS_INSTALL_TIMEOUT_AND_FOUR_DEPENDENCY_CAPACITY_RETENTION_CONTROLS';tree('policies',policy)
tree('failed/policy-fixture',Path('/var/tmp/omicsmatic-hops035-policy-controls-1'))
for label,path in [('reference','omicsmatic-hops035-original-post-1'),('public','omicsmatic-hops035-public-post-1'),('hops','omicsmatic-hops035-public-mepo-1'),('workspace-exports','omicsmatic-hops035-rdata-reference-export-1')]:tree('original/'+label,Path('/var/tmp')/path)
tree('extra-extractions',Path('/var/tmp/omicsmatic-hops035-extra-extractions-1'))
for n in [1,2]:
 prefix=Path('/var/tmp')/('omicsmatic-hops035-public-runtime-'+str(n))
 assert read(prefix/'INSTALL_PROCESS.json')['exit_code']==0 and read(prefix/'PROBE_PROCESS.json')['exit_code']==0
 for p in sorted(prefix.iterdir()):
  if p.is_file() and (p.suffix in ['.json','.stdout','.stderr','.md'] or p.name=='explicit.local.txt'):add('providers/provider-'+str(n)+'/'+p.name,p)
for version,folder in [('21','hops035-postprocessing-junit21-1'),('25','hops035-postprocessing-junit25-1')]:
 path=B/folder;assert read(path/'COMPLETED.json')['totals']==dict(tests=31,failures=0,errors=0,skipped=0)
 for p in path.iterdir():
  if p.is_file():add('unit/windows'+version+'/'+p.name,p)
 tree('unit/windows'+version+'/reports',path/'reports')
for folder in ['hops035-postprocessing-build21-1','hops035-postprocessing-build21-2','hops035-postprocessing-build21-3','hops035-postprocessing-build25-1','hops035-postprocessing-build25-2']:
 for p in (B/folder).iterdir():
  if p.is_file():add('builds/'+folder+'/'+p.name,p)
for name in ['run_hops035_original_post_1.py','run_hops035_public_original_1.py','export_hops035_reference_workspaces_1.py','run_hops035_post_adapter_1.py','run_hops035_extra_extraction_1.py','refuse_hops035_rehashed_bundles_1.py','test_hops035_policies_1.py','test_hops035_policies_2.py','run_hops035_post_junit_1.py','Hops035OwnedProbe.java']:
 add('producing-helpers/'+name,B/name)
add('reference/GCC_LINK_PROVENANCE.json',DEV/'GCC_LINK_PROVENANCE.json')
for p in (B/'hops035-postprocessing-controls-1').iterdir():
 if p.is_file():add('controller-receipts/'+p.name,p)
manifest=dict(schema='HOPS035_POSTPROCESSING_COMPLETE_EVIDENCE_V1',files=logical,whole_hops_qualified=False,biological_result_admitted=False,new_tool_credit=0)
container=DEV/'controls.zip';assert not container.exists()
with zipfile.ZipFile(container,'x',compression=zipfile.ZIP_STORED) as z:
 z.writestr('MANIFEST.json',json.dumps(manifest,sort_keys=True,indent=2)+'\n')
 for digest,path in sorted(objects.items()):z.write(path,'objects/'+digest)
archive=DATA/'controls.zip.xz'
print('Compressing complete archive',len(logical),'logical members',len(objects),'unique objects',container.stat().st_size,'bytes',flush=True)
with container.open('rb') as source,lzma.open(archive,'xb',preset=6) as dest:shutil.copyfileobj(source,dest,1048576)
for name,value in logical.items():
 # Each content object is rechecked against the immutable original source.
 path=objects[value['sha256']];assert pin(path)=={k:value[k] for k in ['bytes','sha256']},name
sources={}
for prior,field in [('maltextract17-development','sources'),('maltextract17-workflow-development','sources'),('hops-evidence-development','source_pins')]:
 d=read(ROOT/'docs/literature'/prior/'VALIDATION.json')
 for name,value in d[field].items():assert pin(ROOT/name)==value;sources[name]=value
for folder in ['hops035-postprocessing-build21-3','hops035-postprocessing-build25-2']:
 for name,value in read(B/folder/'BUILD.json')['sources'].items():assert pin(ROOT/name)==value;sources[name]=value
for name in ['src/test/java/org/jlinalg/cli/HopsPostprocessingAuditTest.java','docs/literature/HOPS_POSTPROCESSING.md',*['src/test/resources/org/jlinalg/metagenomics/hops-postprocessing/'+p.name for p in (ROOT/'src/test/resources/org/jlinalg/metagenomics/hops-postprocessing').iterdir() if p.is_file()]]:sources[name]=pin(ROOT/name)
validation=dict(status='PASS_ORIGINAL_HOPS035_POSTPROCESSING_COMPLETE_OUTPUT_CONTROLS',archive=pin(archive),sources=sources,logical_evidence_files=len(logical),logical_evidence_bytes=sum(v['bytes'] for v in logical.values()),unique_objects=len(objects),unique_object_bytes=sum(p.stat().st_size for p in objects.values()),complete_workflow_bundles=3,sample_taxon_pairs=22,source_node_numeric_fields=308,independent_original_RData_productions=3,metadata_refusals=9,original_failed_attempts_retained=3,invalidated_policy_fixture_attempt_retained=True,policy_controls=4,installer_timeout_control=True,unit_tests_per_JVM=31,unit_JVMs=['windows21','windows25'],original_processing_JVM='linux21',native_source_pairs=2901,native_classification_boundaries=420,linux25_execution_tested=False,new_tool_credit=0,whole_hops_qualified=False,whole_goal_complete=False,saved_replay_status='PENDING_PUBLIC_REPLAY')
save(DATA/'VALIDATION.json',validation);save(DEV/'ARCHIVE_CREATED.json',dict(status='COMPLETE_ARCHIVE_CREATED_PENDING_PUBLIC_REPLAY',archive=validation['archive'],logical_evidence_files=len(logical),unique_objects=len(objects)))
print('Complete source-bound archive ready',validation['archive'],flush=True)

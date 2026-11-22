"""Plan, inspect, atomically admit and verify one bounded Monocle family."""
from pathlib import Path
import argparse,io,json,sys,traceback,zipfile
from monocle_catalog import primary as p,model as m,evidence as e
from seurat_catalog.catalog import clean_manifest,manifest_bytes,install
from catalog_source_integrity import reverse_delta,verify_source
from wire_signac_catalog import delta

ROOT=Path(__file__).resolve().parents[3]
STATUS='PASS_MONOCLE_ONE_SCOPED_FAMILY_NINE_EXISTING_REVIEW_UPDATES'
require=p.require

def zipped(files):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(files.items()):archive.writestr(name,raw)
    return stream.getvalue()

def read_before(path):
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist())==8 and set(archive.namelist())==set(m.META),'Exactly eight retained metadata files required');require(archive.testzip() is None,'Corrupt retained metadata archive');return {name:archive.read(name) for name in m.META}

def baseline(before):
    require(set(before)==set(m.META),'Different before population')
    for name,raw in before.items():require(raw==e.git(ROOT,'show',e.BASE+':'+name),'Exact predecessor metadata differs: '+name)
    return dict(base_commit=e.BASE,metadata={name:p.pin(raw) for name,raw in before.items()})

def primary(cache):
    require(cache is not None,'Supply --primary-cache for a fresh source audit');fresh=p.audit(cache);require(fresh==p.frozen(),'Fresh primary audit differs from frozen nine-role census');return fresh

def control_evidence():
    folder=ROOT/e.CONTROLS;control=json.loads((folder/'CONTROL-CHECKS.json').read_bytes())
    require(control['status']=='PASS_MONOCLE_MODEL_TRANSACTION_AND_SOURCE_CONTROLS_BOTH_HOSTS' and set(control['hosts'])=={'windows','linux'},'Both metadata/source control hosts required')
    require(control['source_code']==e.sources() and control['scientific_models_run']==0 and control['canonical_metadata_modified'] is False,'Changed control source or scope')
    raw=(folder/'checks.zip').read_bytes();require(p.pin(raw)==control['archive'],'Changed control archive bytes')
    fixtures=['metadata-before.zip','QUALIFICATION-BASE.json','FIXTURE.json']
    receipts=['STARTED.json','PROCESS.json','stdout','stderr','checks/RESULT.json','checks/unittest.txt']
    expected={'source/'+name for name in e.OWN}|{'fixture/'+name for name in fixtures}|{host+'/'+name for host in ['windows','linux'] for name in receipts}
    require(set(control['files'])==expected,'Different control evidence population')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        require(len(archive.namelist())==len(expected) and set(archive.namelist())==expected,'Duplicate, missing or extra control archive member')
        require(all(info.file_size<=8*1024*1024 for info in archive.infolist()) and sum(info.file_size for info in archive.infolist())<=64*1024*1024,'Control archive exceeds capacity')
        files={name:archive.read(name) for name in expected}
    for name,wanted in control['files'].items():require(p.pin(files[name])==wanted,'Changed retained control member: '+name)
    for name in e.OWN:require(files['source/'+name]==(ROOT/name).read_bytes(),'Changed tested control source: '+name)
    for name in fixtures:require(files['fixture/'+name]==(folder/name).read_bytes(),'Changed tested fixture: '+name)
    for host,row in control['hosts'].items():
        result=json.loads(files[host+'/checks/RESULT.json']);process=json.loads(files[host+'/PROCESS.json'])
        require(row==dict(exit_code=0,tests=41,source_refusals=8,result=result) and process['exit_code']==0,'Different actual control process/result')
        require(result['status']=='PASS_MONOCLE_MODEL_TRANSACTION_AND_SOURCE_CONTROLS' and (result['tests'],result['source_refusals'],result['failures'],result['errors'],result['skipped'])==(41,8,0,0,0),'Incomplete model/transaction/source controls')
        require(len(result['source_controls'])==8 and all(item['refused'] is True for item in result['source_controls'].values()),'Missing actual source refusals')
        require(b'Ran 41 tests' in files[host+'/checks/unittest.txt'] and b'\nOK\n' in files[host+'/checks/unittest.txt'],'Missing successful unittest transcript')
    return control

def qualification(checks):
    require(checks is not None,'Supply --checks-root with actual current original/native and source receipts');value=e.component_qualification(checks)
    value['controls']=control_evidence();return value

def coverage(source,result):return p.encoded(dict(schema_version=1,status='PASS_ADMITTED_MONOCLE9_EXISTING_SCOPE_UPDATES',source_crosswalk=source,result=result,new_upstream_family_credit=1,new_native_family_credit=0,native_helpers_additional_family_credit=0,separate_cicero_batchelor_credit_added=0,new_review_records=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))

def plan(args,out):
    before={name:(ROOT/name).read_bytes() for name in m.META};foundation=baseline(before);require(clean_manifest()['entries']==838,'Different preceding manifest')
    for name in [m.COVERAGE,e.PROOF,e.COMPAT,e.FOLDER]:require(not (ROOT/name).exists(),'Preserve existing admission target: '+name)
    source=primary(args.primary_cache);qualified=qualification(args.checks_root);after=m.transition(before,source,qualified['result']);result=m.check(before,after,source,qualified['result']);preview=dict(after);preview[m.COVERAGE]=coverage(source,result)
    for name,value in [('metadata-before.zip',zipped(before)),('PREVIOUS-SHA256SUMS',(ROOT/m.DATA/'SHA256SUMS').read_bytes()),('PRIMARY.json',p.encoded(source)),('QUALIFICATION.json',p.encoded(qualified))]:(out/name).write_bytes(value)
    for name,raw in preview.items():path=out/'preview'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    record=dict(schema_version=1,status='PLANNED_MONOCLE_ONE_FAMILY_NINE_EXISTING_SCOPE_UPDATES',foundation=foundation,source_code=e.sources(),result=result,files={name:p.pin((out/name).read_bytes()) for name in ['metadata-before.zip','PREVIOUS-SHA256SUMS','PRIMARY.json','QUALIFICATION.json']},preview={name:p.pin(raw) for name,raw in preview.items()})
    (out/'PLAN.json').write_bytes(p.encoded(record));(out/'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_MONOCLE_PLAN_WITHOUT_CANONICAL_MUTATION',result=result)));return record

def inspect(folder,cache=None,checks=None):
    record=json.loads((folder/'PLAN.json').read_bytes());require(record['schema_version']==1 and record['status']=='PLANNED_MONOCLE_ONE_FAMILY_NINE_EXISTING_SCOPE_UPDATES' and record['source_code']==e.sources(),'Changed plan/schema/current source')
    for name,wanted in record['files'].items():require(p.pin((folder/name).read_bytes())==wanted,'Changed planned evidence: '+name)
    before=read_before(folder/'metadata-before.zip');require(baseline(before)==record['foundation'] and all((ROOT/name).read_bytes()==raw for name,raw in before.items()),'Concurrent canonical/baseline change')
    require((ROOT/m.DATA/'SHA256SUMS').read_bytes()==(folder/'PREVIOUS-SHA256SUMS').read_bytes() and clean_manifest()['entries']==838,'Changed preceding manifest')
    source=json.loads((folder/'PRIMARY.json').read_bytes());require(source==p.frozen(),'Changed planned source census')
    if cache is not None:require(primary(cache)==source,'Changed fresh source audit')
    qualified=json.loads((folder/'QUALIFICATION.json').read_bytes())
    if checks is not None:require(qualification(checks)==qualified,'Changed actual current qualification')
    after=m.transition(before,source,qualified['result']);result=m.check(before,after,source,qualified['result']);require(result==record['result'],'Changed planned result');preview=dict(after);preview[m.COVERAGE]=coverage(source,result)
    require(record['preview']=={name:p.pin(raw) for name,raw in preview.items()},'Changed preview identity')
    for name,raw in preview.items():require((folder/'preview'/name).read_bytes()==raw,'Changed preview bytes: '+name)
    return record,before,source,qualified,preview

def apply(args,out):
    require(args.plan_dir is not None and args.primary_cache is not None and args.checks_root is not None,'Apply requires --plan-dir, --primary-cache and --checks-root');record,before,source,qualified,preview=inspect(args.plan_dir,args.primary_cache,args.checks_root);folder=ROOT/e.FOLDER;require(not folder.exists(),'Preserve existing admission owner; do not reapply');folder.mkdir(parents=True)
    after={name:preview[name] for name in m.META};result=m.check(before,after,source,qualified['result'])
    evidence={'.gitattributes':b'* -text whitespace=cr-at-eol\n','metadata-before.zip':(args.plan_dir/'metadata-before.zip').read_bytes(),'PREVIOUS-SHA256SUMS':(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes(),'PRIMARY.json':p.encoded(source),'QUALIFICATION.json':p.encoded(qualified),'PLAN.json':p.encoded(record),'owner-source.zip':zipped({name:(ROOT/name).read_bytes() for name in e.OWN}),'README.md':('# Monocle cumulative admission\n\nOne bounded upstream family and nine revised existing scopes; no new observations or reviews. Exact-before bytes, actual predecessor receipts, source/edition/namespace boundaries and current original/native qualification are retained.\n\n'+m.ACTION+'\n').encode(),'WARNINGS.md':('# Warnings and required actions\n\n'+m.ACTION+'\n\n'+m.NOTE+'\n').encode()}
    updates={e.FOLDER+'/'+name:raw for name,raw in evidence.items()};updates.update(after);updates[m.COVERAGE]=preview[m.COVERAGE]
    inverse=delta(m.META[5],before[m.META[5]],after[m.META[5]]);require(reverse_delta(after[m.META[5]],inverse)==before[m.META[5]],'Complete review-source inverse failed')
    updates[e.COMPAT]=p.encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='monocle',scope='Nine existing Monocle review scopes and one related P024 py-monocle policy note only; no review append, dispatcher inverse, scientific-source inverse or hash waiver.',paths=[inverse]))
    proof=dict(schema_version=1,status=STATUS,base_commit=e.BASE,source_code=e.sources(),before={name:p.pin(raw) for name,raw in before.items()},after={name:p.pin(raw) for name,raw in after.items()},result=result,qualification=qualified,evidence={name:p.pin(raw) for name,raw in updates.items() if name.startswith(e.FOLDER+'/')},whole_goal_complete=False)
    proof['evidence'].update({m.GUIDE:p.pin((ROOT/m.GUIDE).read_bytes()),m.COVERAGE:p.pin(updates[m.COVERAGE]),e.COMPAT:p.pin(updates[e.COMPAT])})
    for name in ['.gitattributes','CONTROL-CHECKS.json','checks.zip','metadata-before.zip','QUALIFICATION-BASE.json','FIXTURE.json']:proof['evidence'][e.CONTROLS+'/'+name]=p.pin((ROOT/e.CONTROLS/name).read_bytes())
    for path in (ROOT/p.FOLDER).iterdir():
        if path.is_file():proof['evidence'][path.relative_to(ROOT).as_posix()]=p.pin(path.read_bytes())
    updates[e.PROOF]=p.encoded(proof);updates[m.DATA+'/SHA256SUMS']=manifest_bytes(updates)
    expected={name:None for name in updates};expected.update(before);expected[m.DATA+'/SHA256SUMS']=(args.plan_dir/'PREVIOUS-SHA256SUMS').read_bytes()
    for name in updates:(ROOT/name).parent.mkdir(parents=True,exist_ok=True)
    def post():
        require(m.check(before,{name:(ROOT/name).read_bytes() for name in m.META},source,qualified['result'])==result,'Installed metadata differs');manifest=clean_manifest();require(manifest['entries']==841,'Different after manifest population');return manifest
    manifest=install(ROOT,out,expected,updates,post);(out/'COMPLETED.json').write_bytes(p.encoded(dict(status=STATUS,result=result,current_manifest=manifest)))

def verify(out,cache=None):
    proof=json.loads((ROOT/e.PROOF).read_bytes());require(proof['schema_version']==1 and proof['status']==STATUS and proof['base_commit']==e.BASE and proof['source_code']==e.sources(),'Changed admitted source/proof')
    for name,wanted in proof['evidence'].items():require(p.pin((ROOT/name).read_bytes())==wanted,'Changed admitted evidence: '+name)
    folder=ROOT/e.FOLDER;before=read_before(folder/'metadata-before.zip');require(baseline(before)['metadata']==proof['before'],'Changed retained before metadata')
    require((folder/'PREVIOUS-SHA256SUMS').read_bytes()==e.git(ROOT,'show',e.BASE+':'+m.DATA+'/SHA256SUMS'),'Changed preceding manifest')
    source=json.loads((folder/'PRIMARY.json').read_bytes());require(source==p.frozen(),'Changed admitted source census')
    if cache is not None:require(primary(cache)==source,'Changed fresh admitted source audit')
    qualified=json.loads((folder/'QUALIFICATION.json').read_bytes());require(qualified==proof['qualification'] and qualified['controls']==control_evidence(),'Changed admitted component/control qualification')
    for name,wanted in qualified['source_inputs'].items():require(p.pin((ROOT/name).read_bytes())==wanted,'Changed selected scientific/provider source')
    after={name:(ROOT/name).read_bytes() for name in m.META};require(proof['after']=={name:p.pin(raw) for name,raw in after.items()},'Changed canonical after bytes');result=m.check(before,after,source,qualified['result']);require(result==proof['result'],'Changed admitted counts/scope')
    compat=json.loads((ROOT/e.COMPAT).read_bytes());require(len(compat['paths'])==1 and compat['paths'][0]['path']==m.META[5],'Unexpected compatibility scope');require(reverse_delta(after[m.META[5]],compat['paths'][0])==before[m.META[5]],'Changed exact review inverse');verify_source(ROOT,ROOT/m.DATA,m.META[5],p.pin(before[m.META[5]])['sha256'])
    manifest=clean_manifest();require(manifest['entries']==841,'Different current manifest');record=dict(status=STATUS,result=result,current_manifest=manifest,fresh_primary_audit=cache is not None,scientific_models_executed=False,old_metadata_views_or_hash_waivers_used=False);(out/'COMPLETED.json').write_bytes(p.encoded(record));return record

def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group(required=True)
    for name in ['plan','inspect','apply','verify']:mode.add_argument('--'+name,action='store_true')
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--primary-cache',type=Path);parser.add_argument('--checks-root',type=Path);parser.add_argument('--plan-dir',type=Path);args=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');require(args.out.resolve().is_relative_to(ROOT/'build') and args.out.resolve()!=ROOT/'build','Use NEW workspace build output');args.out.mkdir(parents=True,exist_ok=False);owned=True
        if args.plan:plan(args,args.out)
        elif args.inspect:
            require(args.plan_dir is not None,'Inspect requires --plan-dir');record=inspect(args.plan_dir,args.primary_cache,args.checks_root)[0];(args.out/'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_MONOCLE_INSPECTED_PLAN',result=record['result'])))
        elif args.apply:apply(args,args.out)
        else:verify(args.out,args.primary_cache)
        print('PASS Monocle guarded '+('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;record=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=m.ACTION)
        if owned:(args.out/'FAILURE.json').write_bytes(p.encoded(record))
        print(record['status']+': '+str(error)+'\n'+m.ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())

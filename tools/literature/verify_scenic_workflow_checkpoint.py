"""Check frozen SCENIC evidence and current source; no fresh scientific fit."""
from pathlib import Path
import argparse,hashlib,io,json,zipfile
from scenic_dispatch_compatibility import verify as verify_dispatch

def require(value,message):
    if not value:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(root,jar=None):
    folder=root/'docs/literature/scenic-workflow-checkpoint-20261123'
    state=json.loads((folder/'STATUS.json').read_bytes())
    require(state['status']=='SCENIC_SCOPED_WORKFLOW_HANDOVER_CHECKPOINT','Wrong checkpoint')
    require(state['bounded_workflow_qualification'] and not state['whole_tool_qualified'] and not state['catalog_admitted'],'Wrong qualification scope')
    require(state['new_family_credit']==state['new_reviews']==0 and not state['whole_goal_complete'],'False completion or catalog credit')
    for name,key in [('FILES.json','manifest'),('SOURCE-FILES.json','source_manifest'),('OMISSIONS.json','omissions'),('evidence.zip','archive')]:
        require(pin((folder/name).read_bytes())==state[key],'Changed checkpoint: '+name)
    manifest=json.loads((folder/'FILES.json').read_bytes());sources=json.loads((folder/'SOURCE-FILES.json').read_bytes())
    require(len(manifest)==state['logical_files'] and 1000<len(manifest)<20000,'Wrong logical file count')
    require(state['archive']['bytes']<50_000_000 and state['unique_uncompressed_bytes']<150_000_000,'Oversized archive')
    with zipfile.ZipFile(io.BytesIO((folder/'evidence.zip').read_bytes())) as z:
        names=z.namelist();wanted={'objects/'+v['sha256'] for v in manifest.values()}
        require(len(names)==len(set(names))==state['unique_objects'] and set(names)==wanted,'Object population differs')
        require(sum(i.file_size for i in z.infolist())==state['unique_uncompressed_bytes'],'Object sizes differ')
        objects={name:z.read(name) for name in names}
    for name,identity in manifest.items():
        require(not name.startswith('/') and '..' not in name.split('/') and '\\' not in name,'Unsafe logical path')
        require(pin(objects['objects/'+identity['sha256']])==identity,'Changed evidence: '+name)
    def read(name):return objects['objects/'+manifest[name]['sha256']]
    def obj(name):return json.loads(read(name))
    for name,identity in sources.items():
        require(pin((root/name).read_bytes())==identity and pin(read('source/'+name))==identity,'Current source drift: '+name)
    for name,identity in state['catalog_files_unchanged'].items():
        require(pin((root/name).read_bytes())==identity,'Catalog changed; do not refresh this historical context: '+name)
    dispatch=verify_dispatch(root,read)
    normal=obj('normal-build-4/RESULT.json');require(normal['source_unchanged'] and normal['exit_code']==0,'Normal build failed')
    require(normal['normal_tests']==state['normal_tests']==dict(tests=2069,failures=0,errors=0,skipped=0),'Wrong normal tests')
    require(normal['producer']==state['producer'],'Producer identity differs')
    payload=obj('normal-build-4/PAYLOADS.json');require(payload['previous_payloads']==23020 and payload['modified']==['org/jlinalg/cli/JLinAlgCli.class'],'Previous payload regression')
    units=obj('unit-check-4/RESULT.json');require(units['expected_methods_per_host']==10 and {r['host'] for r in units['hosts']}=={'windows21','windows25','linux21'} and all(r['compiler_exit']==r['test_exit']==0 for r in units['hosts']),'Focused tests failed')
    require(any(r['test_exit']!=0 for r in obj('unit-check-1/RESULT.json')['hosts']),'Original failed unit attempt lost')
    modes=obj('java-probe-4/COMPLETED.json')['modes'];require([r['mode'] for r in modes]==['RUN','VERIFY','REPLAY'] and all(r['exit_code']==0 for r in modes),'Actual modes did not pass')
    cases=obj('workflow-cases-3/COMPLETED.json');require(cases['expected']==cases['matched']==14 and len(cases['cases'])==14,'Wrong workflow cases')
    comparisons=obj('normal-original-comparison-2/COMPLETED.json')['cases'];require(len(comparisons)==4,'Missing original references')
    for row in comparisons:
        c=row['comparisons'];require(all(c[k]['exact'] for k in ('adjacencies','enriched','modules','regulons','ranks')) and c['activity']['maximum_absolute_error']==0.0,'Original comparison differs')
    controls=obj('integrity-controls-2/COMPLETED.json');require(controls['checks']==13 and len(controls['controls'])==11,'Missing integrity controls')
    for row in controls['controls']:
        for key in ('check','closure_check','fresh_refit_check'):
            if key in row:require(row[key]['exit_code']==row[key]['expected'],'Integrity result differs')
    for row in controls['controls'][-2:]:require(row['parent_preserved'] and row['original_GRN_CTX_and_native_activity_rerun'] and row['scientific_corruption_refused'],'Resealed science was admitted')
    deadline=obj('deadline-after-fork-2/RESULT.json');require(deadline['exit_code']==2 and deadline['observed_owned_processes']>=3 and deadline['all_owned_closed'] and deadline['unrelated_sentinel_survived'] and not deadline['completion_exists'],'Deadline ownership proof failed')
    example=obj('packaged-examples-1/RESULT.json');require(len(example['modes'])==3 and all(r['exit_code']==0 and r['working_directory']=='/tmp' for r in example['modes']),'Published examples failed')
    for name,identity in example['source_files_unchanged'].items():require(pin((root/'examples/scenic-workflow'/name).read_bytes())==identity,'Example changed: '+name)
    for name in ('worker.py','operations.py','SOURCE-PROFILE.json'):
        require((root/'tools/literature/scenic_workflow'/name).read_bytes()==(root/'src/main/resources/org/jlinalg/omics/scenic-workflow'/name).read_bytes(),'Resource mirror differs: '+name)
    if jar is not None:
        with jar.open('rb') as stream:
            h=hashlib.sha256()
            for block in iter(lambda:stream.read(1<<20),b''):h.update(block)
        require(dict(bytes=jar.stat().st_size,sha256=h.hexdigest())=={k:v for k,v in state['producer'].items() if k!='path'},'Wrong exact producer JAR')
    require(state['catalog']==dict(papers=250,scoped_families=154,native_families=29,upstream_families=125,including_existing_adapters=157,reviewed_uses=1697,pending_uses=1025,positive_observations=2722,missing_families=258),'Wrong historical catalog totals')
    return dict(status=state['status'],logical_files=len(manifest),normal_tests=2069,original_reference_cases=4,maximum_activity_error=0.0,workflow_scenarios=14,integrity_commands=13,saved_evidence_checked=True,current_source_checked=True,exact_jar_checked=jar is not None,fresh_scientific_fit_executed=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,dispatch=dispatch)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(args.root,args.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: SCENIC checkpoint readback failed: '+repr(error)+'. Restore the correct source/evidence context; retain changed evidence and do not refresh historical hashes to bypass failure.') from error

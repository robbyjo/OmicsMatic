"""Verify saved graph qualification bytes and recorded scope; execute no R, Java or model."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def parts(name):
    path=PurePosixPath(name);require(bool(name) and not path.is_absolute() and '..' not in path.parts and '\\' not in name and all(':' not in part for part in path.parts),'Unsafe retained path');return path.parts

def verify(root,out,restore):
    folder=root/'docs/literature/monocle100-graph-checkpoint';profile=json.loads((folder/'PROFILE.json').read_bytes())
    require(profile['status']=='PASS_ORIGINAL_MONOCLE100_PREPARED_GRAPH_WORKFLOWS' and profile['new_catalog_credit']==0 and profile['whole_goal_complete'] is False,'Different graph checkpoint scope')
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged'],**profile['preceding_public_inputs']}.items():require(pin(root.joinpath(*parts(name)).read_bytes())==wanted,'Changed pinned source/catalog/reference: '+name)
    for name,wanted in profile['packet_files'].items():require(pin(folder.joinpath(*parts(name)).read_bytes())==wanted,'Changed packet file: '+name)
    inventory=json.loads((folder/'INVENTORY.json').read_bytes());expected={}
    for name,wanted in inventory.items():
        parts(name);key='blobs/'+wanted['sha256'];require(key not in expected or expected[key]==wanted,'Conflicting blob pins');expected[key]=wanted
    with zipfile.ZipFile(folder/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(expected) and set(names)==set(expected),'Different complete retained population');total=0
        for name,wanted in expected.items():
            require(re.fullmatch('blobs/[0-9a-f]{64}',name) is not None,'Malformed blob name');size=archive.getinfo(name).file_size;total+=size;require(0<=size<=64*1024*1024 and total<=512*1024*1024,'Retained evidence capacity exceeded');require(pin(archive.read(name))==wanted,'Changed retained evidence blob')
        def raw(name):
            wanted=inventory[name];value=archive.read('blobs/'+wanted['sha256']);require(pin(value)==wanted,'Changed logical evidence file');return value
        def read(name):return json.loads(raw(name))
        normal=read('current/normal-gradle-2/RESULT.json');first=read('current/normal-gradle-1/RESULT.json')
        for result in [normal,first]:require(result['status']=='PASS_NORMAL_JAR_MONOCLE100_GRAPH_CONTROLS' and result['tests']==824 and len(result['suites'])==10 and all(row['failures']==row['errors']==row['skipped']==0 for row in result['suites'].values()),'Incomplete normal build controls')
        require(normal['producer']==first['producer']==profile['producer'],'Normal producer bytes differ')
        packaged=read('current/packaged-1/RESULT.json');require(packaged['status']=='PASS_PACKAGED_GRAPH_AND_NEIGHBOR_CONTROLS_THREE_JVMS' and packaged['JAR']==normal['producer'] and packaged['tests_each_host']==824 and set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(row['exit_code']==0 for row in packaged['hosts'].values()),'Incomplete actual packaged qualification')
        comparison=read('current/packaged-1/JAR-COMPARISON.json');require((comparison['preceding_payloads'],comparison['preceding_payloads_unchanged'])==(22405,22403) and set(comparison['changed_preceding_payloads'])=={'org/jlinalg/cli/Monocle100NeighborsRuntime.class','org/jlinalg/cli/Monocle100NeighborsRuntime$Unavailable.class'} and len(comparison['new_payloads'])==13,'Different preceding payload changes')
        require(all(name.startswith(('org/jlinalg/cli/Monocle100Graph','org/jlinalg/omics/monocle100-graph/')) for name in comparison['new_payloads']),'Unexpected new JAR payload')
        for label,count,completed,status in [('normal-matrix-1',21,16,'PASS_ALL21_ACTUAL_GRAPH_EXPECTATIONS'),('extra-matrix-1',2,2,'PASS_ALL_ACTUAL_GRAPH_EXPECTATIONS')]:
            matrix=read('current/'+label+'/RESULT.json');require(matrix['status']==status and (matrix['total'],matrix['completed_graphs'],matrix['retained_failures'])==(count,completed,count-completed) and matrix['quick_compiled_only'] is False and len(matrix['results'])==count and all(row['all_expected'] for row in matrix['results'].values()),'Incomplete actual normal-JAR matrix: '+label)
            require(read('current/'+label+'/PRODUCER.json')['jar']['sha256']==normal['producer']['sha256'],'Matrix used a different normal JAR')
            for name,row in matrix['results'].items():
                prefix='current/'+label+'/'+name+'/capture/';analysis=read(prefix+'analysis.json');require(analysis['graph_admitted']==row['complete'] and analysis['pseudotime_admitted']==(row['complete'] and row['operation']!='LEARN') and analysis['scientific_test_admitted'] is False,'Different case admission')
                require((prefix+'COMPLETED.json' in inventory)==row['complete'],'Different case completion population')
                if row['complete']:
                    require(row['verify_windows_exit_code']==0,'Missing actual portable verification')
                    required=['inputs/source-cds.rds','worker/output-cds.rds','worker/output-scientific-state.rds','worker/output-invariants.rds','worker/input-invariants.rds','worker/input-scientific-state.rds','worker/original-principal-graph.rds','worker/RNG.rds']
                    if row['operation']!='ORDER':required.append('worker/learned-cds.rds')
                    if row['operation']!='LEARN':required+=['worker/original-ordering.rds','worker/original-projected-graph.rds']
                    require(all(prefix+member in inventory and inventory[prefix+member]['bytes']>0 for member in required),'Missing complete saved model member')
        for label,counts in [('normal-independent-original-1',(15,16,12,21,3,2)),('extra-independent-original-1',(2,2,2,2,0,0)),('examples-1/independent-comparison',(2,3,2,3,0,0))]:
            result=read('current/'+label+'/RESULT.json');fields=['complete_learned_CDS','complete_output_CDS','complete_ordering_returns','complete_RNG_returns','complete_original_errors','convergence_policy_refusals'];require(result['status']=='PASS_COMPLETE_ORIGINAL_GRAPH_MODELS_ORDERING_ERRORS_AND_RNG' and tuple(result[key] for key in fields)==counts and result['source_function_signatures_unchanged'] and result['adapter_worker_sourced'] is False and all(all(row.values()) for row in result['cases'].values()),'Incomplete independent full-object comparison')
        extra=read('current/extra-matrix-1/run-circle-loop-retain-original/capture/results/audit.json');require((extra['cells'],extra['principal_points'],extra['principal_edges'])==(60,50,49) and not extra['objective_convergence_verified'] and extra['pseudotime_computed'],'Source loop-control return was changed or convergence inferred')
        require(read('current/normal-matrix-1/run-two-partitions-one-root/capture/results/audit.json')['infinite_cells']==27,'Disconnected original Inf was not retained')
        examples=read('current/examples-1/RESULT.json');require(examples['status']=='PASS_THREE_PUBLIC_GRAPH_OPERATIONS_REPLAYS_AND_FRESH_ORIGINAL_OBJECTS' and set(examples['operations'])=={'learn','order','run'} and examples['producer']==normal['producer'] and examples['learn_then_order_and_combined_pseudotime_identical'] and examples['complete_LEARN_RUN_principal_projections_identical'],'Incomplete public graph example')
        for name,row in examples['operations'].items():
            require(row['parent_preserved'] and row['complete_original_scientific_state_ordering_RNG_replay_identical'] and set(row['commands'])=={'run','replay','linux21','windows21','windows25'} and all(command['exit_code']==0 for command in row['commands'].values()),'Incomplete actual graph replay/host verification')
            parent='current/examples-1/work/'+name+'/';replay=read('current/examples-1/work/'+name+'-replayed/replay.json');require(replay['parent_preserved'] and replay['complete_original_scientific_state_ordering_RNG_identical'] and replay['parent_manifest']==pin(raw(parent+'manifest.json')),'Changed replay parent identity')
        require(raw('current/examples-1/work/order/results/pseudotime.tsv')==raw('current/examples-1/work/run/results/pseudotime.tsv'),'Two-stage and combined public ordering differ')
        regression=read('current/neighbor-regression-1/RESULT.json');require(regression['status']=='PASS_BOTH_PUBLIC_ENGINES_REPLAYS_AND_THREE_HOST_VERIFICATION' and regression['producer']==normal['producer'] and set(regression['engines'])=={'original','java'} and all(row['parent_preserved'] and row['replay_complete_original_returns_RNG_identical'] and row['arcs']==74 and row['original_spdep_fields_compared']==21 for row in regression['engines'].values()),'Shared runner neighbor regression incomplete')
        refusals=read('current/refusals-1/actual/RESULT.json');require(refusals['status']=='PASS5_ACTUAL_NORMAL_JAR_GRAPH_REAL_R_NO_RESULT_CONTROLS' and refusals['producer_sha256']==normal['producer']['sha256'] and set(refusals['controls'])=={'timeout','cancel','java-parent-death','capture-cap','broken-console'} and all(row['no_complete_graph_admitted'] and row['owned_R_group_not_live'] for row in refusals['controls'].values()),'Actual no-result controls incomplete')
        guards=read('current/source-guards-1/RESULT.json');require(guards['status']=='PASS_BOTH_PACKAGED_GRAPH_CHANGED_NAMESPACE_REFUSALS' and guards['producer']==normal['producer'] and set(guards['controls'])=={'body','formals'} and all(not row['original_graph_executed'] and not row['installed_source_modified'] for row in guards['controls'].values()),'Actual selected namespace refusals incomplete')
        context=read('current/context-1/RESULT.json');require(context['status']=='PASS_FRESH_MONOCLE9_AND_RETAINED_CATALOG_BOTH_HOSTS_WITH_THREE_SETUP_REFUSALS' and (context['source_papers_per_host'],context['source_roles'],context['catalog_families'],context['reviewed'],context['pending'],context['manifest_entries'])==(250,9,144,1630,1092,838) and len(context['setup_controls'])==3 and all(row['process']['exit_code']==row['expected_exit'] for row in context['setup_controls']),'Different fresh source/catalog context')
        require(read('current/quick-independent-original-1/PROCESS.json')['exit_code']!=0 and read('current/quick-independent-original-2/PROCESS.json')['exit_code']==0,'Earlier comparator failure or corrected attempt missing')
        if restore:
            for name in inventory:
                target=out/'restored'/Path(*parts(name));target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(raw(name))
    return dict(status='PASS_SAVED_MONOCLE100_GRAPH_CHECKPOINT_BYTES_ONLY',logical_files=len(inventory),unique_blobs=len(expected),tests_per_JVM=824,JVMs=3,actual_expectations=23,completed_graphs=18,retained_failures=5,complete_original_learned_CDS=19,complete_original_output_CDS=21,complete_original_orderings=16,complete_original_RNG=26,original_errors=3,declared_convergence_refusals=2,actual_graph_replays=3,actual_no_result_controls=5,actual_namespace_refusals=2,worktrees_preserved=53,scoped_catalog_families=144,reviewed=1630,pending=1092,new_catalog_credit=0,whole_goal_complete=False,R_Java_XML_or_scientific_models_executed=False,restored=restore)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');root=Path(__file__).resolve().parents[4];out=args.out.resolve();require(not out.exists(),'Use NEW output');out.mkdir(parents=True,exist_ok=False)
    try:
        result=verify(root,out,args.restore)
        with (out/'RESULT.json').open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
        print(json.dumps(result));return 0
    except Exception as error:
        (out/'FAILURE.txt').write_text(str(error)+'\n',encoding='utf-8');print('FAIL2 saved graph checkpoint verification: '+str(error),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())

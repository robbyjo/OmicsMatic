"""Verify saved neighbor qualification bytes and recorded scope; execute no model, R, Java or Git."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,sys,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def parts(name):
    path=PurePosixPath(name);require(bool(name) and not path.is_absolute() and '..' not in path.parts and '\\' not in name and all(':' not in part for part in path.parts),'Unsafe retained path');return path.parts
def verify(root,out,restore):
    folder=root/'docs/literature/monocle100-neighbors-checkpoint';profile=json.loads((folder/'PROFILE.json').read_bytes())
    require(profile['status']=='PASS_ORIGINAL_MONOCLE100_PREPARED_NEIGHBOR_WORKFLOWS' and profile['new_catalog_credit']==0 and profile['whole_goal_complete'] is False,'Different checkpoint scope')
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged'],**profile['preceding_public_inputs']}.items():require(pin(root.joinpath(*parts(name)).read_bytes())==wanted,'Changed pinned source/catalog/reference: '+name)
    for name,wanted in profile['packet_files'].items():require(pin(folder.joinpath(*parts(name)).read_bytes())==wanted,'Changed packet file: '+name)
    inventory=json.loads((folder/'INVENTORY.json').read_bytes());expected={}
    for name,wanted in inventory.items():
        parts(name);key='blobs/'+wanted['sha256'];require(key not in expected or expected[key]==wanted,'Conflicting blob pins');expected[key]=wanted
    with zipfile.ZipFile(folder/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(expected) and set(names)==set(expected),'Different complete retained evidence population');total=0
        for name,wanted in expected.items():
            require(re.fullmatch('blobs/[0-9a-f]{64}',name) is not None,'Malformed blob name');size=archive.getinfo(name).file_size;total+=size
            require(0<=size<=64*1024*1024 and total<=256*1024*1024,'Retained evidence capacity exceeded');require(pin(archive.read(name))==wanted,'Changed retained evidence blob')
        def raw(name):
            wanted=inventory[name];value=archive.read('blobs/'+wanted['sha256']);require(pin(value)==wanted,'Changed logical evidence file');return value
        def read(name):return json.loads(raw(name))
        normal=read('current/normal-gradle-3/RESULT.json');require(normal['status']=='PASS_NORMAL_JAR_MONOCLE100_NEIGHBOR_CONTROLS' and normal['tests']==716 and all(row['failures']==row['errors']==row['skipped']==0 for row in normal['suites'].values()),'Incomplete normal producer controls')
        packaged=read('current/packaged-1/RESULT.json');require(packaged['status']=='PASS_PACKAGED_NEIGHBOR_CONTROLS_THREE_JVMS' and packaged['JAR']==normal['producer'] and packaged['tests_each_host']==716 and set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(row['exit_code']==0 for row in packaged['hosts'].values()),'Incomplete packaged producer qualification')
        comparison=read('current/packaged-1/JAR-COMPARISON.json');require(comparison['preceding_payloads']==comparison['preceding_payloads_unchanged']==22383 and not comparison['changed_preceding_payloads'] and len(comparison['new_payloads'])==22,'Changed preceding JAR payloads')
        matrix=read('current/normal-matrix-1/RESULT.json');require(matrix['status']=='PASS_ALL66_ACTUAL_NEIGHBOR_EXPECTATIONS' and (matrix['total'],matrix['completed_graphs'],matrix['retained_failures'])==(66,50,16),'Different actual case population')
        require(len(matrix['results'])==66 and all(row['all_expected'] for row in matrix['results'].values()),'A complete case expectation was not met')
        for name,row in matrix['results'].items():
            prefix='current/normal-matrix-1/'+row['engine']+'-'+row['case']+'/capture/'
            analysis=read(prefix+'analysis.json');require(analysis['weights_admitted']==row['complete'] and analysis['scientific_test_admitted'] is False,'Different scientific admission')
            require((prefix+'COMPLETED.json' in inventory)==row['complete'],'Different completion population')
            if row['complete']:
                audit=read(prefix+'results/audit.json');require(audit['engine']==row['engine'] and audit['original_weights_binary64_identical']==(row['engine']=='original') and audit['scientific_test_admitted'] is False,'Engine substitution or statistical overclaim')
        independent=read('current/independent-original-1/RESULT.json');require(independent['status']=='PASS_INDEPENDENT_ORIGINAL66_NEAREST25_LISTW8_ERRORS66_RNG' and (independent['complete_original_nearest_returns'],independent['complete_original_listw_returns'],independent['original_errors_class_message_call_identical'],independent['complete_RNG_returns'])==(66,25,8,66) and independent['source_function_signatures_unchanged'] and not independent['adapter_worker_sourced'],'Incomplete fresh original comparison')
        require(len(independent['cases'])==66 and all(row['complete_nearest_return_identical'] and row['complete_RNG_return_identical'] for row in independent['cases'].values()),'Missing complete original-object comparison')
        a=read('current/fingerprint-C-1/FUNCTION-FINGERPRINTS.json');b=read('current/fingerprint-UTF8-1/FUNCTION-FINGERPRINTS.json');require(a==b and a['slam']['rowapply_simple_triplet_matrix']['serialization_version']==2,'Different locale-safe original fingerprints')
        for name in ['C','UTF8']:
            controls=read('current/fingerprint-'+name+'-1/CONTROLS.json');require(controls['original_loaded_strict_identity'] and controls['original_accepted'] and controls['loaded_accepted'] and controls['changed_body_rejected'] and controls['changed_formals_rejected'],'Original source identity or mutation controls failed')
        for name in ['body','formals']:
            guard=read('current/guard-controls-1/'+name+'/RESULT.json');require(guard['status']=='PASS_ACTUAL_PRODUCTION_WORKER_CHANGED_NAMESPACE_REFUSAL' and not guard['installed_source_modified'] and not guard['original_RANN_executed'],'Changed namespace was not rejected before original analysis')
        lifetimes=read('current/lifetime-1/RESULT.json');require(lifetimes['status']=='PASS8_PROCESS_ONLY_SUPERVISOR_CONTROLS' and lifetimes['scientific_analyses_run']==0 and len(lifetimes['cases'])==8,'Different process-only scope')
        refusals=read('current/refusals-1/RESULT.json');require(refusals['status']=='PASS5_ACTUAL_NORMAL_JAR_REAL_R_NO_RESULT_CONTROLS' and refusals['scientific_results_admitted']==0 and len(refusals['controls'])==5,'Incomplete actual no-result controls')
        for name,row in refusals['controls'].items():require(row['no_complete_graph_admitted'] and row['owned_R_group_not_live'],'A real-R refusal admitted results or left a live group')
        examples=read('current/examples-2/RESULT.json');require(examples['status']=='PASS_BOTH_PUBLIC_ENGINES_REPLAYS_AND_THREE_HOST_VERIFICATION' and examples['producer']==normal['producer'],'Incomplete normal-JAR public examples')
        for name,row in examples['engines'].items():require(row['engine']==name and (row['complete_cells'],row['complete_response_genes'],row['arcs'],row['original_spdep_fields_compared'])==(27,3,74,21) and row['parent_preserved'] and row['replay_complete_original_returns_RNG_identical'] and all(value['exit_code']==0 for value in row['commands'].values()),'Incomplete public example/replay/host coverage')
        context=read('current/context-1/RESULT.json');require(context['status']=='PASS_FRESH_MONOCLE9_AND_RETAINED_CATALOG_BOTH_HOSTS_WITH_THREE_SETUP_REFUSALS' and (context['source_papers_per_host'],context['source_roles'],context['catalog_families'],context['reviewed'],context['pending'],context['manifest_entries'])==(250,9,144,1630,1092,838),'Changed source/catalog scope')
        for name in ['normal-gradle-1','normal-gradle-2']:require(read('current/'+name+'/PROCESS.json')['exit_code']!=0,'Failed build attempt erased or relabeled')
        require(read('current/examples-1/original/replay.PROCESS.json')['exit_code']==2,'Initial invalid replay invocation was not retained')
        if restore:
            for name in inventory:
                target=out/'evidence'/Path(*parts(name));target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(raw(name))
    return dict(status='PASS_SAVED_MONOCLE100_NEIGHBOR_CHECKPOINT_BYTES_ONLY',logical_files=len(inventory),unique_blobs=len(expected),tests_per_JVM=716,JVMs=3,actual_expectations=66,completed_graphs=50,retained_failures=16,independent_complete_nearest_returns=66,independent_complete_listw_returns=25,independent_original_errors=8,independent_complete_RNG_returns=66,actual_no_result_controls=5,process_only_controls=8,preceding_JAR_payloads_exact=22383,scoped_catalog_families=144,reviewed=1630,pending=1092,new_catalog_credit=0,whole_goal_complete=False,Git_Java_R_XML_or_scientific_models_executed=False,restored=restore)
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
    root=Path(__file__).resolve().parents[4];out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    try:
        result=verify(root,out,args.restore)
        with (out/'RESULT.json').open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
        print(json.dumps(result));return 0
    except Exception as error:
        (out/'FAILURE.txt').write_text(str(error)+'\n',encoding='utf-8');print('FAIL2 saved neighbor checkpoint: '+str(error),file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())

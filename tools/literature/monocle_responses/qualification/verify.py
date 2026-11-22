"""Verify saved native response/global-stage qualification bytes and scope; run no models."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,re,sys,xml.etree.ElementTree as ET,zipfile

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def safe(name):
    path=PurePosixPath(name)
    require(bool(name) and not path.is_absolute() and '..' not in path.parts and '\\' not in name and all(':' not in part for part in path.parts),'Unsafe saved path')
    return path.parts

def verify(root,out,restore):
    folder=root/'docs/literature/monocle-response-checkpoint';profile=json.loads((folder/'PROFILE.json').read_bytes())
    require(profile['status']=='PASS_NATIVE_MONOCLE_RESPONSES_AND_EXACT_GLOBAL_STAGES' and profile['new_catalog_credit']==0 and profile['whole_goal_complete'] is False,'Different checkpoint scope')
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged'],**profile['preceding_public_inputs']}.items():
        require(pin(root.joinpath(*safe(name)).read_bytes())==wanted,'Changed source/catalog/predecessor bytes: '+name)
    for name,wanted in profile['packet_files'].items():require(pin(folder.joinpath(*safe(name)).read_bytes())==wanted,'Changed packet file: '+name)
    inventory=json.loads((folder/'INVENTORY.json').read_bytes());expected={}
    for name,wanted in inventory.items():
        safe(name);key='blobs/'+wanted['sha256'];require(key not in expected or expected[key]==wanted,'Conflicting saved identity');expected[key]=wanted
    with zipfile.ZipFile(folder/'checks.zip') as archive:
        names=archive.namelist();require(len(names)==len(set(names))==len(expected) and set(names)==set(expected),'Different complete saved population');total=0
        for name,wanted in expected.items():
            require(re.fullmatch('blobs/[0-9a-f]{64}',name) is not None,'Invalid blob name');size=archive.getinfo(name).file_size;total+=size
            require(size<=64*1024*1024 and total<=256*1024*1024,'Saved capacity exceeded');require(pin(archive.read(name))==wanted,'Changed saved blob')
        def raw(name):
            wanted=inventory[name];value=archive.read('blobs/'+wanted['sha256']);require(pin(value)==wanted,'Changed saved logical member');return value
        def read(name):return json.loads(raw(name))
        normal=read('current/normal-gradle-2/RESULT.json');producer=normal['producer']
        require(normal['status']=='PASS_NORMAL_JAR_MONOCLE_RESPONSE_AND_STAGE_CONTROLS' and normal['tests']==889 and len(normal['suites'])==13 and all(row['failures']==row['errors']==row['skipped']==0 for row in normal['suites'].values()),'Incomplete normal producer tests')
        for attempt,failures in [(1,1),(2,0)]:
            totals=dict(tests=0,failures=0,errors=0,skipped=0);files=[name for name in inventory if name.startswith('current/normal-gradle-'+str(attempt)+'/project-build/test-results/test/TEST-') and name.endswith('.xml')]
            require(len(files)==13,'Different saved test suite population')
            for name in files:
                suite=ET.fromstring(raw(name))
                for key in totals:totals[key]+=int(suite.attrib[key])
            require(totals==dict(tests=889,failures=failures,errors=0,skipped=0),'Changed complete normal test totals')
            require(read('current/normal-gradle-'+str(attempt)+'/PROCESS.json')['exit_code']==(1 if failures else 0),'Changed retained build outcome')
        require(read('handover/BUILD-JAR-COMPARISON.json')['normal_build_JARs_identical'] and read('handover/BUILD-JAR-COMPARISON.json')['producer']==producer,'Different normal JAR bytes')
        packaged=read('current/packaged-1/RESULT.json')
        require(packaged['status']=='PASS_PACKAGED_RESPONSE_AND_STAGE_CONTROLS_THREE_JVMS' and packaged['JAR']==producer and packaged['tests_each_host']==889 and set(packaged['hosts'])=={'windows21','windows25','linux21'} and all(row['exit_code']==0 for row in packaged['hosts'].values()),'Incomplete actual packaged JVM checks')
        payload=read('current/packaged-1/JAR-COMPARISON.json')
        require((payload['preceding_payloads'],payload['preceding_payloads_unchanged'])==(22418,22417) and payload['changed_preceding_payloads']==['org/jlinalg/cli/MonocleToolsCli.class'] and len(payload['new_payloads'])==7,'Unreviewed preceding scientific payload change')
        reference=read('current/response-reference-4/RESULT.json');guards=read('current/response-reference-4/SOURCE-GUARDS.json')
        require(read('current/response-reference-4/PROCESS.json')['exit_code']==0 and reference['status']=='PASS_ORIGINAL_MONOCLE_RESPONSE_BRANCH_AND_SPDEP_CONTROLS' and reference['official_source_body_formals_identical'] and reference['official_namespace_unchanged'] and not reference['author_code_executed'] and not reference['whole_paper_reproduced'],'Different original reference scope')
        require(len(reference['cases'])==6 and sum(len(rows) for case in reference['cases'].values() for rows in case['tests'].values())==66 and sum(len(row) for case in reference['cases'].values() for row in case['expected_responses'])==984,'Incomplete original control population')
        require(guards['status']=='PASS_STRICT_PARSED_OFFICIAL_FUNCTION_GUARDS' and guards['body_formals_environment_match_before_and_after'] and not guards['namespace_modified'] and len(guards['owned_changed_function_controls'])==6 and all(row['refused'] and not row['installed_namespace_modified'] for row in guards['owned_changed_function_controls'].values()),'Incomplete strict original source refusal controls')
        probe=read('current/argument-probe-1/RESULT.json')
        require(probe['status']=='PASS_MINIMAL_R433_SUBSTITUTE_ARGUSED_STATE_REPRODUCTION' and probe['strict_body_formals_identical'] and not probe['namespace_modified'] and probe['before_values']==[0] and probe['after_values']==[16],'Different serialization-state diagnosis')
        degeneracy=read('current/degeneracy-diagnosis-1/RESULT.json')
        require(degeneracy['status']=='PASS_RETAINED_ORIGINAL_NUMERICALLY_DEGENERATE_RESPONSE_DIAGNOSIS' and degeneracy['case']=='asis-plain' and degeneracy['gene']=='gene-4' and degeneracy['original_R_residual_relative_norm']<degeneracy['native_relative_resolution_threshold'] and not degeneracy['original_R_refitted'] and not degeneracy['original_input_modified'],'Lost original/native degeneracy difference')
        live=read('current/live-2/RESULT.json')
        require(live['status']=='PASS6_COMPLETE_RESPONSE_CASES_REPLAYS_THREE_HOST_VERIFY_AND_ORIGINAL_INFERENCE_POLICY' and live['producer']==producer and (live['response_values_compared'],live['admitted_original_gene_tests_compared'],live['original_fields_compared'],live['retained_original_R_finite_native_degenerate_family_refusals'])==(984,54,378,3) and live['original_source_gene_tests_retained']==66 and live['public_documented_example_pass'],'Incomplete actual complete-response comparisons')
        for name,row in live['cases'].items():
            require(row['parent_preserved'] and row['replay_complete_outputs_identical'] and set(row['verify_hosts'])=={'windows21','windows25','linux21'} and all(command['exit_code']==command['expected_exit'] for command in row['commands'].values()),'Incomplete actual response replay/verification')
            if name=='asis-plain':
                require(row['original_R_finite_but_native_degenerate_family_refused'] and row['original_gene_tests_compared']==0,'Degenerate source result promoted')
                for alternative in ['greater','less','two.sided']:
                    prefix='current/live-2/asis-plain/work/moran-'+alternative+'/'
                    require(read(prefix+'analysis.json')['result_admitted'] is False and prefix+'COMPLETED.json' not in inventory and prefix+'tests.json' not in inventory,'Degenerate family admitted')
            else:require(not row['original_R_finite_but_native_degenerate_family_refused'],'Different original/native policy classification')
        graph=read('current/graph-finish-1/RESULT.json')
        require(graph['status']=='PASS_THREE_PUBLIC_GRAPH_OPERATIONS_REPLAYS_AND_FRESH_ORIGINAL_OBJECTS' and graph['producer']==producer and set(graph['operations'])=={'learn','order','run'} and graph['fresh_original_complete_output_CDS']==3 and graph['fresh_original_complete_orderings']==2 and graph['fresh_original_complete_RNG']==3,'Incomplete actual global graph/full-original comparisons')
        for row in graph['operations'].values():require(row['parent_preserved'] and row['complete_original_scientific_state_ordering_RNG_replay_identical'] and all(command['exit_code']==0 for command in row['commands'].values()),'Incomplete actual graph replay')
        require(graph['resumed_final_comparison_only'] and graph['original_capture_preserved'] and read('current/graph-finish-1/comparison/PROCESS.json')['exit_code']==0,'Graph capture was not preserved during comparison recovery')
        neighbor=read('current/global-neighbors-1/RESULT.json')
        require(neighbor['status']=='PASS_BOTH_PUBLIC_ENGINES_REPLAYS_AND_THREE_HOST_VERIFICATION' and neighbor['producer']==producer and set(neighbor['engines'])=={'original','java'} and all(row['arcs']==74 and row['original_spdep_fields_compared']==21 and row['parent_preserved'] and row['replay_complete_original_returns_RNG_identical'] for row in neighbor['engines'].values()),'Incomplete global neighbor comparisons')
        weights=read('current/global-weights-1/RESULT.json')
        require(weights['status']=='PASS_THREE_HOST_PUBLIC_WEIGHT_TO_MORAN_EXAMPLES' and weights['producer']==producer and set(weights['hosts'])=={'windows21','windows25','linux21'} and all(row['arcs']==74 and row['original_spdep_fields_compared']==21 and row['weights_binary64_identical'] for row in weights['hosts'].values()),'Incomplete global weight comparisons')
        require('unsupported operand type' in raw('handover/LIVE1-FAILURE.txt').decode('utf-8'),'Earlier comparator failure not retained')
        catalog=read('current/catalog-current-1/RESULT.json')
        require((catalog['papers'],catalog['integration_families'],catalog['reviewed'],catalog['pending'],catalog['manifest_entries'])==(250,144,1630,1092,838),'Different unchanged current catalog')
        preservation=read('handover/PRESERVATION.json');require(preservation['original_worktrees_preserved']==55 and preservation['canonical_files_unchanged']==30,'Incomplete preservation proof')
        if restore:
            for name in inventory:
                target=out/'restored'/Path(*safe(name));target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(raw(name))
    return dict(status='PASS_SAVED_NATIVE_MONOCLE_RESPONSES_CHECKPOINT_BYTES_ONLY',logical_files=len(inventory),unique_blobs=len(expected),tests_per_JVM=889,packaged_JVMs=3,response_cases=6,response_values=984,admitted_original_gene_tests=54,original_fields=378,retained_degenerate_family_refusals=3,original_source_gene_tests_retained=66,source_guard_controls=6,response_replays=6,actual_global_graph_operations=3,original_worktrees_preserved=55,scoped_families=144,reviewed=1630,pending=1092,new_catalog_credit=0,whole_goal_complete=False,R_Java_or_scientific_models_executed=False,restored=restore)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--restore',action='store_true');args=parser.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
    root=Path(__file__).resolve().parents[4];out=args.out.resolve();require(not out.exists(),'Use NEW output');out.mkdir(parents=True,exist_ok=False)
    try:
        result=verify(root,out,args.restore)
        with (out/'RESULT.json').open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
        print(json.dumps(result));return 0
    except Exception as error:
        (out/'FAILURE.txt').write_text(str(error)+'\n',encoding='utf-8');print('FAIL2 saved response checkpoint: '+str(error),file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())

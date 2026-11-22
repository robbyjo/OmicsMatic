"""Verify retained QTLtools source/control bytes and exact comparison scope;execute no model."""
from pathlib import Path,PurePosixPath
import argparse,gzip,hashlib,json,re,sys,zipfile

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/qtltools-workflow-development'

def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def safe(name):
    path=PurePosixPath(name);require(name and not path.is_absolute() and all(part not in {'.','..'} and ':' not in part and '\\' not in part for part in path.parts),'Unsafe retained member');return path.parts
def scope(profile):
    require(profile['schema_version']==1 and profile['status']=='PASS_QTLTOOLS_PREPARED_WORKFLOWS_GLOBAL' and profile['workflow_qualified'] is True,'Different workflow qualification scope')
    require(profile['whole_tool_qualified'] is False and profile['whole_paper_reproduced'] is False and profile['whole_goal_complete'] is False,'False whole-tool/paper/goal claim')
    require(profile['catalog_admitted'] is False and profile['new_catalog_credit']==0 and profile['tests_each_JVM']==680,'Different admission/test scope')
    require(profile['catalog']==dict(papers=250,scoped_families=145,native=26,upstream=119,reviewed=1630,pending=1092),'Different catalog population')

def verify(root,out,restore,jar):
    folder=root/FOLDER;profile=json.loads((folder/'PROFILE.json').read_bytes());scope(profile)
    for name,wanted in {**profile['source_inputs'],**profile['canonical_unchanged']}.items():require(pin(root.joinpath(*safe(name)).read_bytes())==wanted,'Changed source/canonical: '+name)
    for name,wanted in profile['files'].items():require(pin(folder.joinpath(*safe(name)).read_bytes())==wanted,'Changed packet file: '+name)
    if jar is not None:require(pin(jar.read_bytes())==profile['producer'],'Supply the exact qualified normal JAR;do not refresh old hashes')
    index=json.loads((folder/'INDEX.json').read_bytes());require(len(index)<=60000,'Excessive logical control population')
    wanted={}
    for name,row in index.items():
        safe(name);member='blobs/'+row['sha256'];require(re.fullmatch('blobs/[0-9a-f]{64}',member) is not None,'Invalid content-addressed member')
        if member in wanted:require(wanted[member]==row,'Contradictory deduplicated control pin')
        wanted[member]=row
    with zipfile.ZipFile(folder/'controls.zip') as archive:
        entries=archive.infolist();require(len(entries)==len(wanted)==len({row.filename for row in entries}) and set(row.filename for row in entries)==set(wanted),'Different complete archive population')
        require(sum(row.file_size for row in entries)<=512*1024*1024 and all(row.file_size<=32*1024*1024 for row in entries),'Archive exceeds declared decoded capacity')
        for entry in entries:require(pin(archive.read(entry))==wanted[entry.filename],'Changed retained blob: '+entry.filename)
        def raw(name):
            row=index[name];value=archive.read('blobs/'+row['sha256']);require(pin(value)==row,'Changed logical control: '+name);return value
        def read(name):return json.loads(raw(name))
        matrix=read('current/global-models-2/RESULT.json');replays=read('current/global-replays-2/RESULT.json');require(matrix['all_passed'] and replays['all_passed'],'A current actual operation/replay failed')
        attempts=completed=errors=replayed=cross_host=0
        for host in ['windows21','windows25','linux21']:
            rows=matrix['results'][host];require(len(rows)==81 and all(row['passed'] for row in rows.values()),'Different current global operation population')
            for case,row in rows.items():
                attempts+=1;prefix='current/global-models-2/'+host+'/'+case+'/capture/'
                manifest=read(prefix+'manifest.json');status=read(prefix+'analysis-status.json');require(manifest['analysis_status']==status['status'] and manifest['result_kind']==status['result_kind'],'Contradictory current status')
                if row['expected_exit_code']==0:
                    require(row['exit_code']==0 and manifest['results_admitted'] and status['results_admitted'],'Unadmitted current success');completed+=1
                    if row['result_kind'].startswith('null-'):require(status['status']=='completed-null-with-warnings','Null result promoted to positive model')
                else:
                    require(row['exit_code']==2 and manifest['results_admitted'] is False and status['results_admitted'] is False,'Original failure promoted')
                    require(case in {'1.1/mbv-no-covered-genotypes','1.2/trans-adjust-source-export'},'Unexpected original failure');errors+=1
                request=read(prefix+'effective-request.json')
                if request['engine']=='original':
                    closure=read(prefix+'worker/owned.closed.json');require(closure['complete_cleanup'] and closure['live_writers_after']==[],'Remaining original writers')
                    if row['expected_exit_code']==0:require(closure['original_wait_completed'] and closure['original_exit_code']==0,'Contradictory child exit in admitted original')
                    else:require(closure['original_exit_code']==(-8 if case.startswith('1.1/') else -6),'Different retained source error')
            replay_rows=replays['results'][host];require(len(replay_rows)==46 and all(row['passed'] for row in replay_rows.values()),'Different current replay population')
            for case,row in replay_rows.items():
                replayed+=1;cross_host+=row['parent_host']!=host
                def exports(prefix):
                    output={}
                    for name in index:
                        if not name.startswith(prefix):continue
                        suffix=name[len(prefix):];require('/' not in suffix,'Unexpected nested scientific export');key=re.sub(r'^result\.[A-Za-z0-9]{1,11}(?=\.(?:gene|exon)\.|\.stats$)','result',suffix)
                        require(key not in output,'Repeated normalized export identity');value=raw(name);value=gzip.decompress(value) if suffix.endswith('.gz') else value;output[key]=pin(value)
                    return output
                before=exports('current/global-models-2/'+row['parent_host']+'/'+case+'/capture/outputs/')
                after=exports('current/global-replays-2/'+host+'/'+case+'/capture/outputs/')
                require(before==after==row['scientific_exports'] and before,'Complete replay scientific exports differ')
                if case.endswith('quan-hash-content'):require(row['original_export_names']==row['replayed_export_names'],'Content-hash output names changed')
                if case.endswith('quan-hash-source'):require(row['original_export_names']!=row['replayed_export_names'],'Source-location hash was silently retained/renamed')
        require((attempts,completed,errors,replayed,cross_host)==(243,237,6,138,13),'Different actual execution/replay scope')
        comparison=read('current/final-scientific-comparison-1/RESULT.json');require((comparison['source_operations_compared'],comparison['complete_direct_original_files_compared'],comparison['complete_direct_original_lines_compared'])==(192,324,15456),'Different direct-reference comparison scope')
        for host,operations in comparison['comparisons'].items():
            for case,files in operations.items():
                for name,pinned in files.items():
                    value=raw('current/global-models-2/'+host+'/'+case+'/capture/outputs/'+name);value=gzip.decompress(value) if name.endswith('.gz') else value;require(pin(value)==pinned,'Changed full direct-original comparison file')
        controls=read('current/current-integrity-controls-1/RESULT.json');require(controls['passed'] and len(controls['results'])==15 and all(row['passed'] for row in controls['results'].values()),'Different current integrity/provider refusal scope')
        require(all(controls['results'][name]['exit_code']==2 for name in controls['results'] if name!='wrong-executable') and controls['results']['wrong-executable']['exit_code']==3,'Contradictory refusal/skip code')
        package=read('current/packaged-3/RESULT.json');require(package['tests_each_JVM']==680 and all(row['exit_code']==0 for row in package['hosts'].values()),'Different packaged tests')
        require(package['jar_comparison']['unchanged_preceding_payloads']==22428 and package['jar_comparison']['changed_preceding_payloads']==['org/jlinalg/cli/JLinAlgCli.class'],'Unrelated preceding JAR payload changed')
        provider=read('current/current-provider-controls-1/RESULT.json');require(provider['real_timeout_capture_closed_and_stable'] and provider['scientific_library_original_unchanged'],'Different timeout/dependency scope')
        require(provider['results']['real-original-timeout']['exit_code']==2 and provider['results']['real-original-timeout']['cleanup']['complete_cleanup'],'Timeout admitted or cleanup incomplete')
        require(all(provider['results'][name]['exit_code']==3 for name in ['missing-executable','missing-library-directory','wrong-scientific-library-hash']),'Missing/wrong dependency was not skipped')
        cancelled=read('current/cancel-race-control-2/RESULT.json');require(cancelled['future_original_start_refused'] and cancelled['wrong_token_refused_without_mutation'] and cancelled['real_timeout_capture_seal_exact_and_stable'],'Different cancellation/race control')
        gap=read('current/exit-consistency-before-fix-1/RESULT.json');require(gap['exit_code']==0 and gap['expected_exit_code']==2 and gap['deliberate_invalid_capture_admitted'] is True and gap['qualification_claimed'] is False,'Pre-fix failed integrity control was repaired/promoted')
        if restore:
            for name in index:
                target=out.joinpath('captured',*safe(name));target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(raw(name))
    return dict(status='PASS_SAVED_QTLTOOLS_WORKFLOW_SOURCE_AND_CONTROLS_BYTES_ONLY',logical_files=len(index),unique_blobs=len(wanted),tests_each_JVM=680,actual_operations=attempts,completed_operations=completed,retained_expected_original_errors=errors,actual_replays=replayed,cross_host_replays=cross_host,complete_original_files_compared=324,source_integrity_refusals=14,wrong_binary_skip=1,qualified_JAR_verified=jar is not None,restored=restore,catalog_admitted=False,new_catalog_credit=0,scientific_models_executed=False,whole_goal_complete=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--out',type=Path,required=True);p.add_argument('--restore',action='store_true');p.add_argument('--jar',type=Path);args=p.parse_args();require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');root=args.root.resolve();out=args.out.resolve();require(not out.exists(),'Use NEW output');require(not out.is_relative_to(root/FOLDER) and not (root/FOLDER).is_relative_to(out),'Output overlaps saved packet');out.mkdir(parents=True)
    try:
        result=verify(root,out,args.restore,args.jar)
        with (out/'RESULT.json').open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
        print(json.dumps(result));return 0
    except Exception as error:
        (out/'FAILURE.txt').write_text(str(error)+'\n',encoding='utf-8');print('FAIL2 saved QTLtools packet: '+str(error)+'. Preserve evidence;restore the named exact source/archive/JAR or use the original unchanged checkout. No model was executed.',file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())

"""Read saved normal-JAR global-route/replay-guard qualification; no R/Git/refit."""
from pathlib import Path,PurePosixPath
import argparse
import io
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from susie_dispatch_transition import DISPATCH,dispatcher_before,pin,require
from susie_replay_control import WORKFLOW,ARCHIVE,check as control_change

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/susie-dispatch-checks'
STATUS='PASS_SUSIE_GLOBAL_ROUTE_AND_REPLAY_GUARD_QUALIFICATION'
ACTION=('SKIP3: restore the named exact permitted source/evidence or keep dependent work skipped. '
        'FAIL2: retain changed evidence/diagnostics and investigate; no complete result. Use NEW output; '
        'do not refresh earlier hashes. Saved evidence does not refit models or authenticate the host.')


def safe(name):
    p=PurePosixPath(name)
    require(name and not p.is_absolute() and '..' not in p.parts and '\\' not in name and ':' not in name,'Unsafe evidence path')
    return name


def verify(folder=FOLDER):
    folder=Path(folder);q=json.loads((folder/'QUALIFICATION.json').read_bytes())
    require(q['status']==STATUS and q['schema_version']==1,'Different qualification')
    for name,wanted in q['sources'].items():require(pin((ROOT/safe(name)).read_bytes())==wanted,'Changed qualified source: '+name)
    archive=(folder/'evidence.zip').read_bytes();require(pin(archive)==q['archive'],'Changed evidence archive')
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        require(len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(q['payloads']),'Different evidence inventory')
        require(sum(i.file_size for i in z.infolist())<=128*1024*1024,'Evidence capacity exceeded')
        raw={safe(n):z.read(n) for n in z.namelist()}
    for name,wanted in q['payloads'].items():require(pin(raw[name])==wanted,'Changed evidence payload: '+name)
    before=dispatcher_before((ROOT/DISPATCH).read_bytes());require(before==raw['producer/JLinAlgCli.before.java'],'Different exact dispatcher predecessor')
    control=control_change((ROOT/ARCHIVE).read_bytes(),(ROOT/WORKFLOW).read_bytes())
    classes=json.loads(raw['tests-3/JAR-PAYLOADS.json']);changed=[n for n,v in classes.items() if v['before']!=v['after']]
    require(len(classes)==23460 and set(changed)=={'org/jlinalg/cli/JLinAlgCli.class','org/jlinalg/cli/SusieWorkflowCli.class'},'Unexpected producer payload change')
    for member in changed:
        for phase in ['before','after']:
            require(pin(raw['tests-3/'+Path(member).stem+'.'+phase+'.class'])==classes[member][phase],'Changed recorded class bytes')
    for host in ['windows21','windows25','linux21']:
        suites=[ET.fromstring(v) for n,v in raw.items() if n.startswith('tests-3/reports-'+host+'/') and n.endswith('.xml')]
        total={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','errors','failures','skipped']}
        require(total==dict(tests=173,errors=0,failures=0,skipped=0),'Final JVM disposition differs')
    first=json.loads(raw['tests-1/RESULTS.json'])
    require(all(v['totals']==dict(tests=99,errors=0,failures=0,skipped=7) for v in first.values()),'First omitted-reference skips not retained')
    models=json.loads(raw['original-global-2/COMPLETED.json']);comparison=json.loads(raw['original-global-2/COMPARISONS.json'])
    require(models['models_compared']==comparison['models']==5 and models['producer']==q['producer']
        and models['original_model_maximum_numeric_absolute_error']==0 and not models['original_objects_modified']
        and not models['scientific_source_overlay'] and not models['Linux_R_scientific_runs'],'Different original-model scope')
    require(all(r['passed'] and not r['differences'] and r['maximum_numeric_absolute_error']==0 for r in comparison['comparisons'].values()),'Original model differences')
    require(all(p['exit_code']==p['expected'] for p in models['processes']),'Actual global process disposition differs')
    guards=json.loads(raw['replay-guard-controls-1/COMPLETED.json'])
    require(len(guards['controls'])==3 and all(r['exit_code']==2 and r['parent_after_exit']==0 and not r['output_created'] for r in guards['controls']),
            'Replay-parent protection disposition differs')
    bug=json.loads(raw['nested-replay-before-1/CONFIRMED.json'])
    require(bug['parent_before_exit']==0 and bug['parent_after_exit']==2 and bug['original_capture_exit']==0
            and not bug['original_capture_modified'],'Original copied-parent defect not retained')
    old=json.loads(raw['predecessor-1/COMPLETED.json'])
    require(old['historical_commit']=='05a3b132447c60b93fd0c51401c4e17a79405f30' and old['portable_reader_without_git']
            and all(r['exit_code']==0 for r in old['results'].values()) and not old['source_overlay'],'Unchanged historical owner/reader disposition differs')
    return dict(status='PASS_SAVED_SUSIE_GLOBAL_ROUTE_REPLAY_GUARD_AND_ORIGINAL_COMPARISONS',tests_per_JVM=173,
        JVMs=3,original_model_comparisons=5,maximum_numeric_absolute_error=0,unchanged_producer_payloads=23458,
        replay_parent_controls=3,control_change=control,fresh_JVM_execution=False,fresh_R_models_run=False,
        model_fitting_source_changed=False,new_family_credit=0,whole_goal_complete=False),raw


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--packet',type=Path,default=FOLDER);args=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O')
        require(not args.out.exists(),'Preserve existing output; choose NEW output');args.out.mkdir(parents=True);owned=True
        result,_=verify(args.packet);(args.out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n');print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        value=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),required_action=ACTION,complete_result=False)
        if owned:(args.out/'FAILURE.json').write_text(json.dumps(value,indent=2)+'\n')
        print(value['status']+': '+str(error)+'\n'+ACTION,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())

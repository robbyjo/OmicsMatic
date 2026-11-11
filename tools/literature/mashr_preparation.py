#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original covariance learning plus the verified native fit/posterior workflow.

Parent verification requires the exact permitted Windows R profile. The native
child remains independently portable. No caller-supplied code is replayed.
"""
import argparse
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile
import math
for stream in [sys.stdout,sys.stderr]:
    if hasattr(stream,'reconfigure'):stream.reconfigure(encoding='utf-8')
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import ACTION,LOCK,Unavailable,ProcessFailure,canonical,census,disjoint,environment,load_lock,load_monitor,pin,platform_admission,run_owned,verify_home,verify_rhome,workspace_size,write

ROOT=Path(__file__).resolve().parents[2]
SCRIPT=ROOT/'src/main/resources/org/jlinalg/omics/mashr-covariance-preparation/prepare.R'
NATIVE_PROOF=ROOT/'docs/literature/mashr-native-workflow/VALIDATION.json'
TOOL='mashr-original-covariances-native-fit'
SCOPE='original-canonical-uncentered-PCA-Bovy-ED-and-native-EM-posteriors-v1'
MAXIMUM=536_870_912
DEFAULT_PROCESS=dict(wall_seconds=600,cpu_seconds=600,rss_bytes=4_294_967_296,processes=16,threads=64,log_bytes=16_777_216,workspace_bytes=268_435_456,entries=20000,poll_seconds=.1)


def json_file(path,maximum=16_777_216):
    path=canonical(path)
    if not path.is_file() or path.stat().st_size>maximum:raise ValueError('Missing/oversized preparation record: '+str(path))
    def unique(pairs):
        result={}
        for key,value in pairs:
            if key in result:raise ValueError('Duplicate preparation JSON key')
            result[key]=value
        return result
    def nonfinite(value):raise ValueError('Nonfinite JSON value is forbidden: '+value)
    return json.loads(path.read_text(encoding='utf-8'),object_pairs_hook=unique,parse_constant=nonfinite)


def runtime(path):
    platform_admission();lock=load_lock();report=canonical(path)
    if not report.is_file():raise Unavailable('Completed exact R preparation runtime report is unavailable; run permitted private setup before dependent analysis')
    record=json_file(report)
    if record.get('status')!='COMPLETED_MASHR_PREPARATION_RUNTIME_SETUP_ONLY' or record.get('profile')!=lock['profile'] or record.get('user_analysis_admitted') is not False:raise Unavailable('Require a completed exact preparation runtime setup report; setup is not an analysis result')
    if record['lock']!=pin(LOCK):raise Unavailable('Setup profile differs from the exact supported lock; no substitution selected')
    setup_files=census(report.parent);setup_files.pop(report.name)
    if setup_files!=record['report_files']:raise ValueError('Completed setup report file graph/checksums changed; preserve it and perform a NEW setup')
    home,rhome=canonical(record['home']),canonical(record['rhome']);marker=json_file(home/'RUNTIME.json')
    if marker['profile']!=lock['profile'] or marker['lock']!=pin(LOCK) or canonical(marker['setup_report'])!=report:raise ValueError('Private runtime/setup binding differs')
    base=verify_rhome(rhome,lock);installed=verify_home(home,lock);monitor=load_monitor(home,lock)
    return dict(lock=lock,home=home,rhome=rhome,report=report,report_pin=pin(report),setup_files=setup_files,marker_pin=pin(home/'RUNTIME.json'),base=base,installed=installed,monitor=monitor)


def native(jar,java):
    proof=json_file(NATIVE_PROOF);expected={k:proof['artifact'][k] for k in ['bytes','sha256']}
    jar=canonical(jar or ROOT/proof['artifact']['path'])
    if not jar.is_file():raise Unavailable('Validated native candidate JAR unavailable; provide the exact artifact identified in the native public proof')
    if pin(jar)!=expected:raise Unavailable('Native JAR differs from the already validated public artifact; new editions need independent validation, not a fallback')
    selected=java or shutil.which('java')
    if not selected:raise Unavailable('Permitted Java runtime unavailable; provide --java with the tested Java21/25 executable')
    executable=canonical(Path(selected).resolve(strict=True))
    return dict(jar=jar,java=executable,jar_pin=expected,java_pin=pin(executable))


def snapshot(source,target,maximum=16_777_216):
    before=census(source,maximum,1000);target.mkdir()
    for name in before:
        to=target/name;to.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source/name,to)
    if before!=census(target,maximum,1000) or before!=census(source,maximum,1000):raise ValueError('Original preparation inputs changed during complete capture')
    return before


def source_prepare(inputs,root,rt):
    root.mkdir();shutil.copyfile(SCRIPT,root/'trusted-prepare.R')
    env=environment(rt['rhome'],rt['home'],root/'scratch')
    try:process=run_owned([rt['rhome']/'bin/Rscript.exe','--vanilla',root/'trusted-prepare.R',rt['home']/'library',inputs,root/'original'],root,'original',env,rt['monitor'],rt['process_limits'])
    except ProcessFailure as failed:
        detail=diagnostic(root/'original/source-status.tsv')+' '+diagnostic(root/'original.stderr')
        if failed.result['exit_code']==3:raise Unavailable('Exact original preparation dependency unavailable; '+detail+'; inspect '+str(root/'original.stderr')) from failed
        raise ValueError('Original covariance preparation failed: '+detail+'; preserve full '+str(root/'original.stderr')) from failed
    if (root/'original/source-status.tsv').read_text(encoding='utf-8').splitlines()!=['ORIGINAL_COVARIANCE_PREPARATION_COMPLETE_UNADMITTED']:raise ValueError('Original preparation did not return complete unadmitted source-stage status')
    return process


def native_command(arguments,root,label,rt,native_profile,heap):
    env=environment(rt['rhome'],rt['home'],root/'native-scratch')
    command=[native_profile['java'],'-Xmx'+str(heap)+'m','-XX:ActiveProcessorCount=2','-jar',native_profile['jar'],'mashr-tools','--no-config',*arguments]
    try:return run_owned(command,root,label,env,rt['monitor'],rt['process_limits'])
    except ProcessFailure as failed:
        if failed.result['exit_code']==3:raise Unavailable('Native child requested capability unavailable; inspect complete '+label+'.stderr') from failed
        raise


def checked_state(rt,native_profile):
    setup_files=census(rt['report'].parent);setup_files.pop(rt['report'].name,None)
    if setup_files!=rt['setup_files'] or pin(rt['home']/'RUNTIME.json')!=rt['marker_pin']:raise ValueError('Previously admitted setup file graph or private runtime binding changed')
    try:
        if verify_rhome(rt['rhome'],rt['lock'])!=rt['base'] or verify_home(rt['home'],rt['lock'])!=rt['installed']:raise ValueError('Original R/package/monitor runtime changed during analysis or verification')
    except Unavailable as changed:raise ValueError('Previously admitted runtime became unavailable or changed: '+str(changed)) from changed
    if pin(rt['report'])!=rt['report_pin'] or pin(native_profile['jar'])!=native_profile['jar_pin'] or pin(native_profile['java'])!=native_profile['java_pin']:raise ValueError('Runtime/setup/native executable binding changed')


def scratch_admission(parent,root,rt,input=None):
    if parent is None:return
    protected=[root,rt['home'],rt['rhome'],rt['report'].parent]
    if input is not None:protected.append(input)
    if any(parent.is_relative_to(path) for path in protected):raise ValueError('Verification scratch parent must be outside analysis/input/runtime/setup trees')


def diagnostic(path):
    if not path.is_file():return ''
    with path.open('rb') as stream:raw=stream.read(4096)
    return raw.decode('utf-8',errors='replace').strip()


def process_budget(process):
    if set(process)!=set(DEFAULT_PROCESS) or any(type(v) not in [int,float] or not math.isfinite(v) or v<=0 for v in process.values()):raise ValueError('Process limits require every exact positive finite numeric field')
    if not .05<=process['poll_seconds']<=1 or any(type(process[k]) is not int for k in ['rss_bytes','processes','threads','log_bytes','workspace_bytes','entries']):raise ValueError('Invalid process poll or nonintegral process capacities')
    if process['processes']>1024 or process['threads']>4096 or process['entries']>1000000 or process['rss_bytes']>1_099_511_627_776 or process['workspace_bytes']>68_719_476_736:raise ValueError('Process capacities exceed this supported profile')
    return process


def budgets(args):
    if not 1024<=args.input_bytes<=8_589_934_592 or not 65536<=args.bundle_bytes<=68_719_476_736 or not 100<=args.entries<=100000:raise ValueError('Explicit input/bundle/entry capacities outside supported range')
    process=process_budget(json_file(args.process_limits,262144) if args.process_limits else dict(DEFAULT_PROCESS))
    return dict(input_bytes=args.input_bytes,bundle_bytes=args.bundle_bytes,entries=args.entries,process=process)


def covariance_mode(inputs):
    rows=(inputs/'preparation.tsv').read_text(encoding='utf-8').splitlines()
    if not rows or rows[0]!='key\tvalue':raise ValueError('Invalid captured preparation settings header')
    values={}
    for row in rows[1:]:
        fields=row.split('\t')
        if len(fields)!=2 or fields[0] in values:raise ValueError('Invalid/duplicate captured preparation setting')
        values[fields[0]]=fields[1]
    mode=values.get('covariance_mode')
    if mode not in ['canonical','canonical_pca_ed']:raise ValueError('Unknown captured source covariance mode')
    return mode


def warnings(mode):
    text='''# Original mashr covariance preparation and native fitting

Only top-level COMPLETED.json plus successful parent --verify admits this declared
mixed workflow. Preparation uses original mashr0.2.79 canonical/uncentered PCA/Bovy
ED covariance functions with the explicit strong population. PCA initializes ED;
fitting receives canonical+ED bases. Original identity/sqrt(Nstrong) addition is
retained as a source method. No global ED optimum is certified; inspect ED logs.

The native child fits representative training rows by plain EM, with its own
dual-gap and ad-hoc residual rules. It is not upstream mixSQP/SQUAREM identity.
Read native-analysis/WARNINGS.md and native-analysis/execution/WARNINGS.md in full.
Read preparation/original/WARNINGS.md and METHOD-NOTES.txt for actual source
warnings, transformed-data scope and source residual initialization.
Strong rows recorded in that child were already used by original preparation;
the child does not relearn covariances. True singleton fitting is native; no rows
are padded or silently rerouted through broken original posterior wrappers.

Parent verify requires the exact permitted original Windows R profile and reruns
original covariance preparation plus the child's complete native verification.
The child can separately verify portably with Java. Runtime unavailability is
SKIP3, not evidence that an existing producer bundle is invalid. Producer paths
are recorded history; verification reads captured inputs and current explicit
runtime only. Native child completion alone admits its conditional supplied-basis
scope, not this parent preparation claim. Stage-only status records remain raw.

Numeric source tables compare with fixed absolute1e-9 plus relative1e-8 tolerance;
IDs, methods, RNG states, source code and file graphs are exact. Raw RDS, session
metadata, human logs and stdout/stderr are retained supplements, not interpreted
as executable analysis inputs or authenticated process history. ED numeric
likelihood history, all source covariance matrices, transformed strong data,
prepared native inputs and full native child analysis are checked. Hashes provide
integrity, not authenticity or source-to-binary reproduction. No whole-paper or
biological-accuracy certification. Sampling limits are not an OS sandbox.

'''+ACTION+'\n'
    if mode=='canonical':text=text.replace('Strong rows recorded in that child were already used by original preparation;\nthe child does not relearn covariances.','Canonical-only mode does not learn covariances from strong rows. Any strong\nflags remain recorded; neither stage invents data-driven covariance learning.')
    return text+'\nActual source covariance mode: '+mode+'\n'


def seal(root,policy):
    files=census(root,policy['bundle_bytes'],policy['entries']);write(root/'manifest.json',dict(schema_version=1,tool=TOOL,scope=SCOPE,files=files))
    write(root/'COMPLETION-PROVISIONAL.json',dict(schema_version=1,tool=TOOL,scope=SCOPE,manifest=pin(root/'manifest.json'),scoped_result_admitted=True,original_covariance_functions_executed=True,native_fitter_executed=True,whole_paper_reproduced=False,biological_accuracy_certified=False))
    return files,pin(root/'COMPLETION-PROVISIONAL.json'),pin(root/'manifest.json')


def compare_source(recorded,replay,policy):
    actual,expected=census(recorded,policy['bundle_bytes'],policy['entries']),census(replay,policy['bundle_bytes'],policy['entries'])
    if actual.keys()!=expected.keys():raise ValueError('Original preparation replay complete file graph differs')
    numeric=0;maximum=0
    exact=['condition-ids.tsv','training-ids.tsv','strong-ids.tsv','target-ids.tsv','original-null-selected-ids.tsv','source-progress.txt','source-status.tsv','WARNINGS.md','METHOD-NOTES.txt','rng-kind.txt','rng-initial-state.tsv','rng-final-state.tsv']
    for name in actual:
        if name in ['session-info.txt','covariances/original-ed-candidates.rds','covariances/ed_loglike.log','covariances/ed_c.log']:continue
        if name.startswith('requested-') or name in exact or name.startswith('prepared/') and not name.startswith('prepared/basis/component-') and name!='prepared/residual.tsv' or name.endswith('component-names.tsv') or name.endswith('-names.tsv'):
            if actual[name]!=expected[name]:raise ValueError('Original preparation exact identity/method/RNG/input differs: '+name)
        elif name.endswith('.tsv'):
            import itertools
            with (recorded/name).open(encoding='utf-8') as a,(replay/name).open(encoding='utf-8') as b:
                for x,y in itertools.zip_longest(a,b):
                    if x is None or y is None or len(x)>67_108_864 or len(y)>67_108_864:raise ValueError('Truncated/oversized complete original numeric replay: '+name)
                    left,right=x.rstrip('\n').split('\t'),y.rstrip('\n').split('\t')
                    if len(left)!=len(right):raise ValueError('Original preparation numeric width differs: '+name)
                    for p,q in zip(left,right):
                        p,q=float(p),float(q);error=abs(p-q)
                        if not math.isfinite(p) or not math.isfinite(q) or error>1e-9+1e-8*abs(q):raise ValueError('Original preparation numeric replay differs: '+name)
                        numeric+=1;maximum=max(maximum,error)
        elif actual[name]!=expected[name]:raise ValueError('Unrecognized changed original output: '+name)
    return dict(numeric_values=numeric,maximum_absolute_error=maximum)


def verify(root,rt,native_profile,heap,provisional=False):
    root=canonical(root);scratch_admission(rt.get('scratch_root'),root,rt)
    marker_name='COMPLETION-PROVISIONAL.json' if provisional else 'COMPLETED.json';marker=json_file(root/marker_name);manifest=json_file(root/'manifest.json')
    expected_fields={'schema_version','tool','scope','manifest','scoped_result_admitted','original_covariance_functions_executed','native_fitter_executed','whole_paper_reproduced','biological_accuracy_certified'}
    if set(marker)!=expected_fields or marker['schema_version']!=1 or marker['tool']!=TOOL or marker['scope']!=SCOPE or marker['scoped_result_admitted'] is not True or marker['original_covariance_functions_executed'] is not True or marker['native_fitter_executed'] is not True or marker['whole_paper_reproduced'] is not False or marker['biological_accuracy_certified'] is not False:raise ValueError('No admitted original-preparation/native-fit parent result')
    if set(manifest)!={'schema_version','tool','scope','files'} or manifest['schema_version']!=1 or manifest['tool']!=TOOL or manifest['scope']!=SCOPE or marker['manifest']!=pin(root/'manifest.json'):raise ValueError('Parent manifest/schema integrity differs')
    policy=rt['policy'];before=census(root,policy['bundle_bytes'],policy['entries']);data={n:v for n,v in before.items() if n not in [marker_name,'manifest.json']}
    if data!=manifest['files']:raise ValueError('Missing/extra/changed parent evidence file')
    if pin(root/'source-prepare.R')!=pin(SCRIPT) or pin(root/'preparation/trusted-prepare.R')!=pin(SCRIPT) or pin(root/'runtime-lock.json')!=pin(LOCK):raise ValueError('Captured original preparation code/profile differs from current trusted program; no untrusted captured code is executed')
    session=json_file(root/'session.json')
    if set(session)!={'schema_version','tool','scope','status','scoped_result_admitted','runtime_profile','source_script','runtime_setup','native_artifact','original_input_directory','heap_mib','original_representative_mixture_optimizer_executed','native_limits_explicit','admission_limits','covariance_mode'} or session.get('schema_version')!=1 or session.get('tool')!=TOOL or session.get('status')!='audited-provisional' or session.get('scoped_result_admitted') is not False or session.get('original_representative_mixture_optimizer_executed') is not False or session.get('scope')!=SCOPE or session.get('native_artifact')!=native_profile['jar_pin'] or session.get('source_script')!=pin(SCRIPT) or session.get('runtime_profile')!=rt['lock']['profile']:raise ValueError('Parent method/runtime/native artifact claims differ')
    mode=covariance_mode(root/'inputs')
    if session['covariance_mode']!=mode or (root/'WARNINGS.md').read_text(encoding='utf-8')!=warnings(mode):raise ValueError('Actual source covariance mode or required parent warnings changed')
    declared_policy=json_file(root/'admission-limits.json',262144)
    if set(declared_policy)!={'input_bytes','bundle_bytes','entries','process'} or session['admission_limits']!=pin(root/'admission-limits.json'):raise ValueError('Parent declared admission capacities differ')
    process_budget(declared_policy['process'])
    if type(session['native_limits_explicit']) is not bool:raise ValueError('Native limit mode must be explicit boolean')
    if session['native_limits_explicit']:
        if pin(root/'native-limits-request.json')!=pin(root/'native-analysis/limits-request.json'):raise ValueError('Native child capacities differ from the complete explicit request')
    elif (root/'native-limits-request.json').exists():raise ValueError('Unexpected unconsumed native capacities')
    fixed={'source-prepare.R','runtime-lock.json','producer-runtime-setup.json','session.json','WARNINGS.md','admission-limits.json','native-child.stdout','native-child.stderr','native-child.process.json'}
    if session['native_limits_explicit']:fixed.add('native-limits-request.json')
    if {n for n in data if '/' not in n}!=fixed or any(not (n in fixed or n.startswith(('inputs/','preparation/','native-analysis/','native-scratch/'))) for n in data):raise ValueError('Unexpected parent semantic file graph')
    producer=json_file(root/'producer-runtime-setup.json')
    if pin(root/'producer-runtime-setup.json')!=session['runtime_setup'] or producer.get('status')!='COMPLETED_MASHR_PREPARATION_RUNTIME_SETUP_ONLY' or producer.get('profile')!=rt['lock']['profile'] or producer.get('lock')!=pin(LOCK) or producer.get('runtime_files')!={'library':rt['lock']['package_files'],'monitor':rt['lock']['monitor']['files']} or producer.get('R_runtime_files')!=rt['lock']['rhome_runtime_files']:raise ValueError('Recorded original runtime/setup evidence differs from the locked producer profile')
    for name in ['preparation/original.process.json','native-child.process.json']:
        process=json_file(root/name)
        if process.get('exit_code')!=0 or process.get('error') is not None or process.get('sampling_is_not_OS_sandbox') is not True or process.get('samples',0)<1:raise ValueError('No successfully monitored original/native process record: '+name)
        if name=='native-child.process.json' and '--no-config' not in process.get('argv',[]):raise ValueError('Native child inherited an unreviewed project/user configuration')
        limits=process['limits'];peaks=process['peaks']
        if limits!=declared_policy['process']:raise ValueError('Owned process limits differ from the captured parent request')
        if any(not isinstance(peaks.get(k),int) or peaks[k]<0 or peaks[k]>limits[k] for k in ['rss_bytes','processes','threads','workspace_bytes']) or not math.isfinite(process['sampled_cpu_seconds']) or process['sampled_cpu_seconds']>limits['cpu_seconds'] or not math.isfinite(process['wall_seconds']) or process['wall_seconds']>limits['wall_seconds']+limits['poll_seconds']:raise ValueError('Recorded process exceeds its admitted sampled capacities: '+name)
    prepared=root/'preparation/original/prepared';captured=root/'native-analysis/execution/inputs'
    if census(prepared,policy['input_bytes'],1000)!=census(captured,policy['input_bytes'],1000):raise ValueError('Native analysis inputs differ from every original prepared input')
    scratch=Path(tempfile.mkdtemp(prefix='omicsmatic-mashr-parent-verify-',dir=rt.get('scratch_root'))).resolve()
    if scratch.is_relative_to(root) or root.is_relative_to(scratch):raise ValueError('Parent verification scratch overlaps source')
    try:
        native_command(['--verify',str(root/'native-analysis')],scratch,'native-child-verify',rt,native_profile,heap)
        source_prepare(root/'inputs',scratch/'preparation',rt)
        compared=compare_source(root/'preparation/original',scratch/'preparation/original',policy)
        if pin(root/'source-prepare.R')!=pin(scratch/'preparation/trusted-prepare.R'):raise ValueError('Preparation replay source code differs')
        checked_state(rt,native_profile)
        if before!=census(root,policy['bundle_bytes'],policy['entries']):raise ValueError('Parent bundle changed during complete verification')
        # Retain successful verification evidence too; no implicit historical cleanup.
        write(scratch/'VERIFIED.json',dict(status='passed-complete-parent-replay',source=str(root),source_unchanged=True,comparison=compared))
        return dict(status='passed-complete-parent-replay',comparison=compared,scratch=str(scratch),source_unchanged=True)
    except (Exception,KeyboardInterrupt) as failure:
        try:unchanged=before==census(root,policy['bundle_bytes'],policy['entries'])
        except Exception:unchanged=None
        write(scratch/'FAILURE.json',dict(status='FAIL2_PARENT_REPLAY',source_unchanged=unchanged,error=str(failure),required_action=ACTION))
        raise ValueError('Parent verification failed; preserve scratch '+str(scratch)+'; source_unchanged='+str(unchanged)+'; '+str(failure)) from failure


def produce(root,input,rt,native_profile,heap):
    disjoint(root,input,rt['home'],rt['rhome'],rt['report'].parent);scratch_admission(rt.get('scratch_root'),root,rt,input);root.mkdir(parents=False)
    try:
        policy=rt['policy'];source=snapshot(input,root/'inputs',policy['input_bytes']);shutil.copyfile(SCRIPT,root/'source-prepare.R');shutil.copyfile(LOCK,root/'runtime-lock.json');shutil.copyfile(rt['report'],root/'producer-runtime-setup.json');write(root/'admission-limits.json',policy)
        source_prepare(root/'inputs',root/'preparation',rt)
        arguments=['--input',str(root/'preparation/original/prepared'),'--out',str(root/'native-analysis')]
        if rt.get('native_limits'):
            shutil.copyfile(rt['native_limits'],root/'native-limits-request.json');arguments+=['--limits',str(root/'native-limits-request.json')]
        native_command(arguments,root,'native-child',rt,native_profile,heap)
        mode=covariance_mode(root/'inputs');session=dict(schema_version=1,tool=TOOL,scope=SCOPE,status='audited-provisional',scoped_result_admitted=False,runtime_profile=rt['lock']['profile'],source_script=pin(SCRIPT),runtime_setup=rt['report_pin'],native_artifact=native_profile['jar_pin'],original_input_directory=str(input),heap_mib=heap,original_representative_mixture_optimizer_executed=False,native_limits_explicit=bool(rt.get('native_limits')),admission_limits=pin(root/'admission-limits.json'),covariance_mode=mode)
        write(root/'session.json',session);(root/'WARNINGS.md').write_text(warnings(mode),encoding='utf-8',newline='\n');checked_state(rt,native_profile)
        if source!=census(input,policy['input_bytes'],1000):raise ValueError('Original source inputs changed during mixed workflow')
        data,marker,manifest=seal(root,policy);verified=verify(root,rt,native_profile,heap,True)
        (root/'COMPLETION-PROVISIONAL.json').rename(root/'COMPLETED.json')
        current=census(root,policy['bundle_bytes'],policy['entries'])
        if {n:v for n,v in current.items() if n not in ['COMPLETED.json','manifest.json']}!=data or current['COMPLETED.json']!=marker or current['manifest.json']!=manifest:raise ValueError('Parent evidence changed during finalization')
        return dict(status='COMPLETED_ORIGINAL_COVARIANCES_NATIVE_FIT',verification=verified,output=str(root))
    except (Exception,KeyboardInterrupt) as failure:
        try:checked_state(rt,native_profile);write(root/'post-failure-runtime-check.json',dict(status='runtime-unchanged'))
        except Exception as secondary:write(root/'post-failure-runtime-check.json',dict(status='runtime-integrity-unverified',error=str(secondary)))
        if (root/'COMPLETED.json').exists():(root/'COMPLETED.json').rename(root/'REJECTED-COMPLETION.json')
        code=3 if isinstance(failure,Unavailable) else 2;write(root/'FAILURE.json',dict(status='SKIP3_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_RESULT_ADMITTED',error=str(failure),required_action=ACTION))
        with (root/'WARNINGS.md').open('a',encoding='utf-8',newline='\n') as out:out.write('\n# '+('SKIP3: analysis skipped' if code==3 else 'FAIL2: no result admitted')+'\n\n'+str(failure)+'\n\n'+ACTION+'\n')
        raise


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--jar',type=Path);parser.add_argument('--java',type=Path);parser.add_argument('--heap-mib',type=int,default=128);parser.add_argument('--input',type=Path);parser.add_argument('--out',type=Path);parser.add_argument('--verify',type=Path)
    parser.add_argument('--input-bytes',type=int,default=16_777_216);parser.add_argument('--bundle-bytes',type=int,default=MAXIMUM);parser.add_argument('--entries',type=int,default=20000);parser.add_argument('--process-limits',type=Path);parser.add_argument('--native-limits',type=Path);parser.add_argument('--scratch-root',type=Path);args=parser.parse_args()
    if args.heap_mib<64 or args.heap_mib>16384:raise ValueError('Java heap outside explicit64..16384MiB admission range')
    if bool(args.verify)==bool(args.input or args.out) or not args.verify and (not args.input or not args.out):raise ValueError('Choose either --input/--out or --verify')
    policy=budgets(args);rt=runtime(args.runtime);rt.update(policy=policy,process_limits=policy['process'],native_limits=canonical(args.native_limits) if args.native_limits else None,scratch_root=canonical(args.scratch_root) if args.scratch_root else None);app=native(args.jar,args.java)
    result=verify(args.verify,rt,app,args.heap_mib) if args.verify else produce(canonical(args.out),canonical(args.input),rt,app,args.heap_mib)
    print(json.dumps(result));return 0


if __name__=='__main__':
    try:sys.exit(main())
    except (Exception,KeyboardInterrupt) as failure:
        code=3 if isinstance(failure,Unavailable) else 2;print(('SKIP3 — ANALYSIS/VERIFICATION SKIPPED: ' if code==3 else 'FAIL2 — NO RESULT ADMITTED: ')+str(failure)+'\n'+ACTION,file=sys.stderr);sys.exit(code)

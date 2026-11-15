#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read pinned legacy source through R's parser; never evaluate app code or install packages."""
from pathlib import Path
import argparse,csv,hashlib,json,os,shutil,subprocess,sys,traceback,uuid

def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def run(args,hook=lambda phase,out:None):
    out=args.out.resolve();owned=False;phase='PREREQUISITES'
    try:
        source=args.source.resolve();profile=args.profile.resolve()
        if out.is_relative_to(source) or source.is_relative_to(out):raise ValueError('Output and original source must be separate')
        out.mkdir(exist_ok=False);owned=True
        write(out/'WARNINGS.json',dict(status='STATIC_SOURCE_AUDIT_NOT_IMPLEMENTATION_OR_ANALYSIS',
            required_action='No source expression is evaluated and no package is installed. Original runtime, legacy license and scientific workflow qualification remain separate. Missing prerequisites skip dependent work; preserve all partials and retry NEW paths.'))
        phase='SOURCE_CAPTURE';original=json.loads(profile.read_bytes());profile_pin=pin(profile)
        captured=out/'inputs';captured.mkdir();identities={}
        for name,identity in original['members'].items():
            relative=Path(name)
            if relative.is_absolute() or '..' in relative.parts:raise ValueError('Unsafe source member')
            path=source/relative
            if not path.is_file() or path.is_symlink():raise FileNotFoundError('Supply permitted canonical original source: '+str(path))
            expected={key:identity[key] for key in ['bytes','sha256']}
            if pin(path)!=expected:raise ValueError('Original source identity differs: '+name)
            target=captured/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
            if pin(target)!=expected:raise ValueError('Captured source differs: '+name)
            raw=target.read_bytes();blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
            if blob!=identity['git_blob_sha']:raise ValueError('Recorded Git source blob differs: '+name)
            identities[name]=expected
        shutil.copyfile(profile,out/'SOURCE-PROFILE.json')
        audit=Path(__file__).with_name('Audit.R');audit_pin=pin(audit);wrapper_pin=pin(Path(__file__))
        shutil.copyfile(audit,out/'Audit.R')
        shutil.copyfile(__file__,out/'source_audit.py')
        executable=args.rscript.resolve()
        if not executable.is_file():raise FileNotFoundError('Supply an existing permitted Rscript for static parsing: '+str(executable))
        command=[str(executable),'--vanilla',str(out/'Audit.R'),str(captured),str(out/'ast')]
        env={key:value for key,value in os.environ.items() if key.upper() in ['SYSTEMROOT','WINDIR','PATH']}
        (out/'tmp').mkdir();(out/'empty-library').mkdir()
        env.update(HOME=str(out),USERPROFILE=str(out),TMP=str(out/'tmp'),TEMP=str(out/'tmp'),TMPDIR=str(out/'tmp'),
            R_LIBS_USER=str(out/'empty-library'),R_LIBS_SITE=str(out/'empty-library'),R_DEFAULT_PACKAGES='base,utils,stats')
        write(out/'COMMAND.json',dict(command=command,environment=env,timeout_seconds=60,rscript=pin(executable),
            source_commit=original['repository_commit'],original_analysis_executed=False))
        phase='STATIC_PARSER'
        with (out/'process.stdout').open('xb') as so,(out/'process.stderr').open('xb') as se:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
            write(out/'LIVE.json',dict(pid=child.pid,command=command))
            try:code=child.wait(timeout=60)
            except subprocess.TimeoutExpired:
                child.kill();child.wait();raise RuntimeError('Owned static parser exceeded deadline; no replacement was started')
        write(out/'PROCESS.json',dict(pid=child.pid,exit_code=code))
        if code:raise RuntimeError('Static source parser failed; read complete process.stderr')
        if not (out/'ast/RETURN.txt').read_text().startswith('PASS_PARSE_ONLY_NO_ORIGINAL_EXPRESSION_EVALUATION'):raise ValueError('No complete parse return')
        hook('AFTER_PARSER',out);phase='SOURCE_FACTS'
        tables={}
        for name in ['bindings','calls','inputs']:
            with (out/'ast'/(name+'.tsv')).open(encoding='utf-8',newline='') as handle:tables[name]=list(csv.DictReader(handle,delimiter='\t'))
        def binding(file,name):
            rows=[row for row in tables['bindings'] if row['file']==file and row['lhs']==name and row['scope']=='global']
            if len(rows)!=1:raise ValueError('Expected one global source binding: '+file+'/'+name)
            return rows[0]
        pairs={}
        for name in ['detectGroups','DEG.DESeq2']:
            server,helper=[binding(file,name) for file in ['server.R','iDEP_core_functions.R']]
            a,b=[pin(out/'ast'/row['expression_record']) for row in [server,helper]]
            pairs[name]=dict(server=a,standalone_helper=b,normalized_R_AST_equal=a==b)
        values={name:binding('server.R',name)['literal_value'] for name in ['iDEPversion','maxSamples','maxSamplesDefault','maxComparisons','maxFactors']}
        values['UI_iDEPversion']=binding('ui.R','iDEPversion')['literal_value']
        if values['UI_iDEPversion']!='iDEP.96' or values['iDEPversion']!='iDEP 0.96':raise ValueError('Legacy source version literals differ')
        if pairs['DEG.DESeq2']['normalized_R_AST_equal']:raise ValueError('Known server/standalone divergence disappeared')
        ui_defaults={}
        for name in ['minCounts','NminSamples','CountsTransform','countsLogStart','missingValue','noIDConversion','limmaPval','limmaFC']:
            rows=[row for row in tables['inputs'] if row['file']=='ui.R' and row['id']==name]
            if len(rows)!=1:
                # Synthetic parser controls need not reproduce the full app UI.
                continue
            ui_defaults[name]={key:rows[0][key] for key in ['type','value_expression','selected_expression','choices_expression']}
        server_reactives=[dict(lhs=row['lhs'],scope=row['scope'],kind=row['kind'],expression_record=row['expression_record'])
            for row in tables['bindings'] if row['file']=='server.R' and row['kind'] in ['reactive','eventReactive']]
        facts=dict(status='PASS_PINNED_IDEP96_STATIC_R_AST_AND_SERVER_HELPER_DIVERGENCE_AUDIT',source_commit=original['repository_commit'],
            source_literal_globals=values,server_and_standalone_helpers=pairs,bindings=len(tables['bindings']),
            selected_calls=len(tables['calls']),UI_inputs=len(tables['inputs']),
            repository_UI_default_expressions=ui_defaults,server_reactive_bindings=server_reactives,
            server_DESeq2_function_formals=binding('server.R','DEG.DESeq2')['formals_expression'],
            top_level_installer_calls=[row for row in tables['calls'] if row['scope']=='global' and row['head'] in ['install.packages','BiocManager::install']],
            original_app_or_scientific_expressions_evaluated=False,package_installation_or_network_by_parser=False,
            runtime_or_license_qualification_established=False,author_source_commit_established=False,implementation_credit_added=0)
        write(out/'SOURCE-FACTS.json',facts)
        for name,identity in identities.items():
            if pin(source/name)!=identity or pin(captured/name)!=identity:raise ValueError('Source changed during parsing: '+name)
        if pin(profile)!=profile_pin or pin(out/'Audit.R')!=audit_pin or pin(audit)!=audit_pin or pin(Path(__file__))!=wrapper_pin or pin(out/'source_audit.py')!=wrapper_pin:raise ValueError('Audit recipe or source profile changed')
        phase='FINAL_RECORDING'
        files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}
        write(out/'MANIFEST.json',files)
        write(out/'COMPLETED.json',dict(status=facts['status'],manifest=pin(out/'MANIFEST.json'),source_files=len(identities),
            native_analysis_executed=False,implementation_credit_added=0,whole_goal_complete=False))
        hook('AFTER_COMPLETE',out)
        print(json.dumps(facts,indent=2));return 0
    except BaseException as error:
        code=3 if isinstance(error,FileNotFoundError) else 2
        label='SKIP3_IDEP_STATIC_AUDIT_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_IDEP_STATIC_AUDIT_UNADMITTED'
        if owned:
            try:
                marker=out/'COMPLETED.json'
                if marker.exists():marker.rename(out/('COMPLETED-UNADMITTED-'+str(uuid.uuid4())+'.json'))
            except OSError as recording:print('FAIL2: completion quarantine failed; do not consume this audit directory:',recording,file=sys.stderr)
            try:
                write(out/'FAILURE.json',dict(status=label,phase=phase,cause=repr(error),traceback=traceback.format_exc(),
                    required_action='Supply the named permitted source/runtime prerequisite or resolve the parse/identity cause. Preserve partials and retry NEW output paths. No scientific result is admitted.'))
            except OSError as recording:print('FAIL2_FAILURE_RECORDING:',recording,'; no complete audit is admitted. Preserve partials.',file=sys.stderr)
        print(label,repr(error),file=sys.stderr);return code

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['source','profile','rscript','out']:parser.add_argument('--'+name,type=Path,required=True)
    raise SystemExit(run(parser.parse_args()))

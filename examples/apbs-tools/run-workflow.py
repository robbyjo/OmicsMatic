#!/usr/bin/env python3
"""Fixed 1CRN example: Windows PDB2PQR/APBS, then original Linux PyMOL in WSL.

This runner never installs or downloads. Model/presentation choices are the
adjacent explicit recipes. Use the individual commands for other structures.
"""
from pathlib import Path
import argparse,datetime as dt,hashlib,json,os,shutil,subprocess,sys,uuid

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
SOURCE_SHA='42199a30a0701864a2a5cc76cd7f35cc544cd0e65fbcf63e03c166543249b811'
RECOVERY=('Required action: read this WARNINGS.md and each executed stage/WARNINGS.md, '
          'session.json and original logs. On Windows x86_64 explicitly admit PDB2PQR '
          'with tools/literature/install_pdb2pqr.py --python CPYTHON312_EXECUTABLE '
          '--home NEW_HOME --cache WHEEL_CACHE --allow-install, and APBS with '
          'tools/literature/install_apbs.py --home NEW_HOME --cache ARCHIVE_CACHE '
          '--allow-install. Add --allow-download only where permitted. For PyMOL '
          'use Ubuntu24.04 x86_64 CPython3.12 and install_pymol.py with --home NEW_HOME '
          '--cache CACHE --report NEW_REPORT --reference-wheels VERIFIED_WHEELHOUSE '
          '--allow-install. Supply the exact validated local PyMOL wheel and required '
          'system libraries; see PYMOL_WORKFLOWS.md. Supply these admitted homes to '
          'this runner. If installation or a required dependency is forbidden or '
          'unavailable, skip that stage and all dependent analyses until resolved. '
          'Never re-seal changed homes. Correct failures and rerun to a new output '
          'directory; completed upstream bundles and failed partials are retained.')

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def linux_path(path,distro):
    # Forward slashes survive WSL's Windows argument bridge; backslashes can
    # be consumed before wslpath sees the drive-qualified path.
    result=subprocess.run(['wsl.exe','-d',distro,'--','/usr/bin/wslpath','-a','-u',Path(path).absolute().as_posix()],capture_output=True,timeout=30)
    if result.returncode:raise RuntimeError('WSL path conversion failed: '+result.stderr.decode(errors='replace'))
    value=result.stdout.decode('utf-8').strip()
    if not value.startswith('/') or '\n' in value:raise RuntimeError('Invalid WSL path result')
    return value

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java',default='java');p.add_argument('--jar',required=True,type=Path)
    p.add_argument('--pdb2pqr-home',required=True,type=Path);p.add_argument('--apbs-home',required=True,type=Path)
    p.add_argument('--pymol-home',required=True);p.add_argument('--wsl-distro',required=True)
    p.add_argument('--linux-java',default='/usr/bin/java');p.add_argument('--out',required=True,type=Path)
    p.add_argument('--recipe-root',type=Path,default=ROOT/'build/apbs-workflow-recipes')
    a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
    stages=['preparation','solver','surface'];record=dict(status='running',started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),stages=[],warnings=[])
    code=2;current=None;inputs={};carrier=None
    try:
        if os.name!='nt':code=3;raise RuntimeError('This cross-platform example runner requires Windows x86_64 with WSL. Run the individual typed commands on their admitted hosts.')
        jar=a.jar.absolute();java=shutil.which(a.java)
        if not java:code=3;raise RuntimeError('Windows Java executable unavailable')
        if not jar.is_file():code=3;raise RuntimeError('The validated development executable JAR is unavailable')
        # Windows Java launcher argv handling can corrupt Unicode paths. Data
        # paths belong inside UTF8 JSON; only ASCII recipe/JAR paths use argv.
        if not str(jar).isascii() or not str(a.recipe_root.absolute()).isascii():raise ValueError('Use ASCII JAR and --recipe-root paths; data/output paths may contain Unicode')
        if not a.pymol_home.startswith('/') or not a.linux_java.startswith('/'):raise ValueError('PyMOL home and Linux Java must be absolute Linux paths')
        carrier=a.recipe_root.absolute()/('run-'+uuid.uuid4().hex);carrier.mkdir(parents=True,exist_ok=False);record['recipe_directory']=str(carrier)
        source=HERE/'1CRN.pdb'
        if sha(source)!=SOURCE_SHA:raise ValueError('The unchanged 1CRN source differs from provenance.json')
        inputs={str(path):sha(path) for path in [jar,source,HERE/'prepare-parse.json',HERE/'protein.json',HERE/'surface.json',Path(__file__)]};record['input_sha256']=inputs
        def run(stage,command,options):
            nonlocal current,code
            current=stage;code=2;recipe=carrier/(stage+'.json');write(recipe,options);record.setdefault('recipe_sha256',{})[stage]=sha(recipe)
            row=dict(stage=stage,status='running',output=str(out/stage),command=command+['--options-file',str(recipe)]);record['stages'].append(row);write(out/'workflow-status.json',record)
            if stage=='surface':
                row['command']=command+['--options-file',linux_path(recipe,a.wsl_distro)]
            try:r=subprocess.run(row['command'],cwd=ROOT,capture_output=True,timeout=900)
            except FileNotFoundError as failure:code=3;row.update(status='skipped-unavailable-dependency',error=str(failure));raise RuntimeError('Required stage launcher unavailable: '+str(failure)) from failure
            (out/(stage+'.stdout')).write_bytes(r.stdout);(out/(stage+'.stderr')).write_bytes(r.stderr)
            row['exit_code']=r.returncode;row['stdout_sha256']=sha(out/(stage+'.stdout'));row['stderr_sha256']=sha(out/(stage+'.stderr'))
            if sha(recipe)!=record['recipe_sha256'][stage]:raise RuntimeError('Stage recipe changed during execution')
            status_file=out/stage/'analysis-status.json';row['status']=json.loads(status_file.read_text(encoding='utf-8'))['status'] if status_file.is_file() else 'failed-no-status'
            if r.returncode or row['status']!='completed-with-warnings':
                code=3 if r.returncode==3 else 2
                raise RuntimeError(stage+' did not complete; exit='+str(r.returncode)+',status='+row['status']+'. Read retained stage warnings/logs.')
            row['warnings_path']=str(out/stage/'WARNINGS.md');print(stage+' completed;REVIEW '+row['warnings_path'],file=sys.stderr,flush=True)
        prep=json.loads((HERE/'prepare-parse.json').read_text());prep.update(input=str(source),home=str(a.pdb2pqr_home.absolute()),out=str(out/'preparation'))
        run('preparation',[java,'-Xmx768m','-jar',str(jar),'pdb2pqr-tools'],prep)
        pqr=out/'preparation/prepared.pqr';prep_result=json.loads((out/'preparation/results.json').read_text());assert prep_result['pqr_sha256']==sha(pqr)
        solver=json.loads((HERE/'protein.json').read_text());solver.update(input=str(pqr),home=str(a.apbs_home.absolute()),out=str(out/'solver'))
        run('solver',[java,'-Xmx768m','-jar',str(jar),'apbs-tools'],solver)
        solver_session=json.loads((out/'solver/session.json').read_text());solver_result=json.loads((out/'solver/results.json').read_text());grid=out/'solver/potential.dx'
        if solver_session['source_sha256']!=sha(pqr) or solver_result['potential_sha256']!=sha(grid):raise RuntimeError('Preparation-to-solver provenance mismatch')
        current='surface';code=3
        surface=json.loads((HERE/'surface.json').read_text());surface.update(input=linux_path(pqr,a.wsl_distro),grid=linux_path(grid,a.wsl_distro),home=a.pymol_home,out=linux_path(out/'surface',a.wsl_distro))
        command=['wsl.exe','-d',a.wsl_distro,'--',a.linux_java,'-Xmx768m','-jar',linux_path(jar,a.wsl_distro),'potential-surface']
        run('surface',command,surface)
        final=json.loads((out/'surface/session.json').read_text());loaded=set(final['source_sha256'].values())
        if loaded!={sha(pqr),sha(grid)}:raise RuntimeError('Solver-to-renderer provenance mismatch')
        for name,digest in inputs.items():
            if sha(name)!=digest:raise RuntimeError('Workflow input changed: '+name)
        record['provenance_chain']=dict(prepared_pqr_sha256=sha(pqr),solver_input_sha256=solver_session['source_sha256'],solver_grid_sha256=sha(grid),renderer_source_sha256=final['source_sha256'],standalone_figure_sha256=sha(out/'surface/figure.svg'))
        record['scope']='Fixed1CRN PARSE/no declared-pH titration,NPBE symmetric0.15M salt,298.15K,explicit source-PQR radii,excluded surface and -5/0/+5 kT/e display. Native audits and original calculations;not source-paper reconstruction or biological validation.'
        code=0;record['status']='completed-with-warnings'
    except Exception as failure:
        record.update(status='skipped-unavailable-dependency' if code==3 else 'failed',error=str(failure),stopped_at=current)
        executed={row['stage'] for row in record['stages']}
        for stage in stages:
            if stage not in executed:record['stages'].append(dict(stage=stage,status='skipped-dependent-analysis',cause=str(failure)))
    finally:
        record['completed_utc']=dt.datetime.now(dt.timezone.utc).isoformat();record['exit_code']=code
        text='# WORKFLOW '+('WARNINGS REQUIRE REVIEW' if code==0 else 'ANALYSES SKIPPED' if code==3 else 'FAILED;DEPENDENT ANALYSES SKIPPED')+'\n\n'
        if 'error' in record:text+=record['error']+'\n\n'
        text+='\n'.join('- '+row['stage']+': '+row['status'] for row in record['stages'])+'\n\n'+RECOVERY+'\n\nRequired scientific review: inspect every stage warning,choose a justified protonation/charge/radius model and check grid/domain/convergence. Source-PQR zero-radius atoms remain. Original PyMOL stores float32 fields;raw DX and canonical short-token rendering DX are retained and fully audited. The raw APBS energy includes self-energy and is not solvation or binding free energy. A colored surface is a display of the supplied model,not experimental validation.\n'
        (out/'WARNINGS.md').write_text(text,encoding='utf-8',newline='\n');write(out/'workflow-status.json',record)
        print(('WORKFLOW WARNINGS: ' if code==0 else 'WORKFLOW ANALYSIS SKIPPED: ' if code==3 else 'WORKFLOW FAILED: ')+str(out/'WARNINGS.md'),file=sys.stderr,flush=True)
    return code

if __name__=='__main__':raise SystemExit(main())

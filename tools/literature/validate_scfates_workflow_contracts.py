# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise portable export, actual forbidden setup and connected dependency skips without analysis."""
import argparse,copy,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.resolve().relative_to(ROOT).as_posix()
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_PUBLIC_DEPENDENCY_CONTRACTS',checks=[],implementation_credit=False,whole_goal_complete=False)
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};resources=ROOT/'src/main/resources/org/jlinalg/omics/scfates-workflow';profile=json.loads((resources/'profiles.json').read_bytes());worker=json.loads((resources/'worker-profile.json').read_bytes());kit=out/'kit';template=json.loads((a.fixtures/'requests/quoted-identifiers.json').read_bytes())
        def execute(name,command,expected,timeout=600):
            run=subprocess.run(command,capture_output=True,timeout=timeout);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);record=dict(name=name,command=command,exit_code=run.returncode);state['checks'].append(record);save(out/(name+'.process.json'),record);assert run.returncode==expected,(name,run.stderr[-3000:]);return run
        def windows(clazz,args):return [str(JAVA21/'java.exe'),'-cp',str(jar),'org.jlinalg.cli.'+clazz,*map(str,args)]
        def wsl(executable,args):return ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',executable,*map(str,args)]
        execute('portable-export',windows('ScfatesSetupCli',['--export',kit]),0);kit_files={p.relative_to(kit).as_posix():pin(p) for p in kit.rglob('*') if p.is_file()};expected={**profile['files'],**worker['files'],'profiles.json':pin(resources/'profiles.json'),'worker-profile.json':pin(resources/'worker-profile.json')};assert kit_files==expected;execute('refuse-export-overwrite',windows('ScfatesSetupCli',['--export',kit]),2);assert {p.relative_to(kit).as_posix():pin(p) for p in kit.rglob('*') if p.is_file()}==kit_files
        empty=out/'empty-cache';empty.mkdir();setup_reports={}
        for name,flags in [('install-forbidden',[]),('download-forbidden',['--allow-install'])]:
            home=out/name;prefix=ROOT/'build/cellphonedb-workflow-case-sensitive-1'/('scfates-'+out.name+'-'+name);assert not prefix.exists();args=['-I','-B',linux(kit/'setup/prepare.py'),'--out',linux(home),'--prefix',linux(prefix),'--base-runtime',linux(ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1/COMPLETED.json'),'--archive-cache',linux(empty),*flags];execute(name,wsl('/usr/bin/python3',args),3);report=json.loads((home/'COMPLETED.json').read_bytes());assert report['status']=='SKIP3_SCFATES_RUNTIME_UNAVAILABLE' and not report['processes'] and not report['analysis_admitted'] and not report['analysis_installed_software'] and (home/'WARNINGS.md').is_file();setup_reports[name]=home/'COMPLETED.json'
            if name=='install-forbidden':assert not prefix.exists()
            else:assert report['shared_inputs_unchanged'] and 'download not permitted' in report['error'] and len(list((home/'archives').glob('*.whl')))==2 and not(prefix/'dependencies').exists()
        for name,platform,runtime in [('windows-platform-skip','windows',None),('missing-runtime-skip','linux',None),('connected-runtime-skip','linux',setup_reports['install-forbidden'])]:
            request=copy.deepcopy(template);home=out/name;request['out']=str(home) if platform=='windows' else linux(home);runtime=runtime or out/'absent-runtime.json';request['runtime']=str(runtime) if platform=='windows' else linux(runtime);path=out/(name+'.json');save(path,request);command=windows('ScfatesWorkflowCli',['--request',path]) if platform=='windows' else wsl('/usr/bin/java',['-Xmx1g','-cp',linux(jar),'org.jlinalg.cli.ScfatesWorkflowCli','--request',linux(path)]);run=execute(name,command,3);admission=json.loads((home/'analysis-status.json').read_bytes());assert admission['status']=='SKIP3_SCFATES_DEPENDENCIES_UNAVAILABLE' and not admission['result_admitted'] and not(home/'worker').exists() and b'SKIP3' in run.stderr and b'REQUIRED ACTION' in run.stderr
        assert all(pin(ROOT/n)==want for n,want in build['source_pins'].items());state.update(status='PASS_SEVEN_SCFATES_EXPORT_PERMISSION_AND_CONNECTED_SKIP_CONTRACTS_ONLY',checks_completed=7,kit_files=kit_files,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},original_analysis_executed=False,software_installed=False,artifact=build['artifact'])
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())

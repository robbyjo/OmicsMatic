# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check public-command preflight recovery, repeated tamper rejection, named profiles and late-capacity diagnostics."""
import argparse,concurrent.futures,copy,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_nextflow_user_workflows import DISTRO,VERSIONS,wsl,write
from validate_mashr_native import linux,pin

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    jar=args.artifact.resolve();matrix=args.matrix.resolve();out=args.out.resolve();base=ROOT/'build/literature-review/nextflow-runtime';assert all(p.is_relative_to(base.resolve()) for p in (jar,matrix,out)) and not out.exists();out.mkdir()
    original_remote=json.loads((matrix/'OWNER.json').read_bytes())['owned_linux_root'];assert re.fullmatch(r'/tmp/omicsmatic-nextflow-workflows-[A-Za-z0-9_-]+',original_remote);artifact=pin(jar)
    remote=wsl('/usr/bin/python3','-c','import tempfile;print(tempfile.mkdtemp(prefix="omicsmatic-nextflow-recovery-"))').decode().strip();assert re.fullmatch(r'/tmp/omicsmatic-nextflow-recovery-[A-Za-z0-9_-]+',remote);write(out/'OWNER.json',dict(owned_linux_root=remote,source_matrix=str(matrix.relative_to(ROOT))))
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in [*sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Nextflow*.java')),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java',Path(__file__),ROOT/'tools/literature/validate_nextflow_user_workflows.py']}
    java=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/timeout','--signal=TERM','--kill-after=10s','300s','/usr/bin/java','-Xmx256m','-XX:ActiveProcessorCount=2','-jar',linux(jar),'nextflow-tools']
    setup=base/'managed-setup-development-1'
    def run(folder,label,arguments,expected):
        with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(java+arguments,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=330)
        record=dict(arguments=arguments,expected_exit=expected,actual_exit=result.returncode);write(folder/(label+'.json'),record)
        assert result.returncode==expected,(folder,label,result.returncode);return record
    def read(path):return json.loads(wsl('/usr/bin/cat',path))
    def family(spec):
        version,suffix=spec;folder=out/version;folder.mkdir();original=matrix/version/'module-success';assert json.loads((original/'COMPLETED-CONTROL.json').read_bytes())['passed']
        wsl('/usr/bin/mkdir',remote+'/'+version);session=remote+'/'+version+'/module-success';home=linux(setup/('home-'+suffix));report=linux(setup/('report-'+suffix));calls=[]
        calls.append(run(folder,'base-run',['--recipe',linux(original/'recipe.json'),'--home',home,'--setup-report',report,'--out',session],0));calls.append(run(folder,'base-resume',['--resume',session,'--home',home,'--setup-report',report],0))
        assert read(session+'/latest.json')==dict(attempt='000002',status='COMPLETED')
        calls.append(run(folder,'preflight-skip',['--resume',session,'--home',remote+'/absent-runtime','--setup-report',report],3));assert read(session+'/latest.json')==dict(attempt='000003',status='SKIP3')
        calls.append(run(folder,'verify-skipped-latest',['--verify',session],2));calls.append(run(folder,'verify-prior',['--verify',session+'/attempts/000002'],0))
        calls.append(run(folder,'recover',['--resume',session,'--home',home,'--setup-report',report],0));assert read(session+'/latest.json')==dict(attempt='000004',status='COMPLETED')
        recovered=read(session+'/attempts/000004/completion.json');assert recovered['stats']['cached']==4 and recovered['stats']['succeeded']==0
        assert read(session+'/attempts/000004/resume-basis.json')['attempt']=='000002';calls.append(run(folder,'verify-recovered',['--verify',session],0))
        wsl('/usr/bin/python3','-c','from pathlib import Path;import sys;p=Path(sys.argv[1]);p.write_bytes(p.read_bytes()+b"tampered\\n")',session+'/workspace/published/counts.tsv')
        for id,label in [('000005','tamper-first'),('000006','tamper-repeat')]:
            calls.append(run(folder,label,['--resume',session,'--home',home,'--setup-report',report],2));assert read(session+'/latest.json')==dict(attempt=id,status='FAIL2')
            assert wsl('/usr/bin/python3','-c','from pathlib import Path;import sys;print((Path(sys.argv[1])/"process").exists())',session+'/attempts/'+id).decode().strip()=='False'
        calls.append(run(folder,'verify-tampered-latest',['--verify',session],2));calls.append(run(folder,'verify-preserved-recovery',['--verify',session+'/attempts/000004'],0))
        wsl('/usr/bin/cp','-r',session+'/attempts',linux(folder/'recovery-attempts'))
        wsl('/usr/bin/cp','-r',session+'/objects',linux(folder/'recovery-object-store'))
        base_recipe=json.loads((original/'recipe.json').read_bytes());new_case=folder/'named-profile';new_case.mkdir();new_project=new_case/'project';source_project=next(p for p in original.iterdir() if p.is_dir() and p.name.startswith('workflow-'))
        wsl('/usr/bin/cp','-a',linux(source_project),linux(new_project))
        with (new_project/'executor.config').open('a',encoding='utf-8',newline='\n') as config:config.write("profiles { local { params.profile_marker='named-local-profile' } }\n")
        main=new_project/'main.nf';text=main.read_text(encoding='utf-8');main.write_text(text.replace('workflow {',"workflow {\n    if(params.profile_marker != 'named-local-profile') error('named profile not active')"),encoding='utf-8',newline='\n')
        named=copy.deepcopy(base_recipe);named['workflow']['path']=linux(new_project);named['workflow']['profiles']=['local'];named['resources']['reads']['path']=linux(new_project/'inputs');write(new_case/'recipe.json',named)
        named_session=remote+'/'+version+'/named-profile';calls.append(run(folder,'named-profile',['--recipe',linux(new_case/'recipe.json'),'--home',home,'--setup-report',report,'--out',named_session],0));calls.append(run(folder,'verify-named-profile',['--verify',named_session],0));wsl('/usr/bin/cp','-r',named_session+'/attempts',linux(folder/'profile-attempts'))
        capacity=folder/'finalization-capacity';capacity.mkdir();recipe=copy.deepcopy(base_recipe)
        requested=max((original/'retained'/name).stat().st_size for name in ('request.json','capture.json','session.json','tools.json'))+2048
        assert requested<(original/'retained/attempts/000001/runtime.json').stat().st_size;recipe['limits']['metadata_bytes']=requested;write(capacity/'recipe.json',recipe);capacity_session=remote+'/'+version+'/finalization-capacity'
        calls.append(run(folder,'late-capacity',['--recipe',linux(capacity/'recipe.json'),'--home',home,'--setup-report',report,'--out',capacity_session],2));wsl('/usr/bin/cp','-r',capacity_session+'/attempts',linux(folder/'capacity-attempts'));attempt=folder/'capacity-attempts/000001'
        ledger=json.loads((attempt/'process/process.json').read_bytes());assert ledger['exit_code']==0
        assert (attempt/'WARNINGS-PREFINAL.md').exists() and 'FAIL2' in (attempt/'WARNINGS.md').read_text(encoding='utf-8').splitlines()[0] and not (attempt/'COMPLETED.json').exists();assert 'oversized' in json.loads((attempt/'FAILURE.json').read_bytes())['error'].lower()
        record=dict(version=version,passed=True,cached_after_preflight_skip=4,repeated_tamper_rejections=2,named_profile_proven=True,late_failure_warning_prominent=True,calls=calls);write(folder/'COMPLETED.json',record);print(json.dumps({k:v for k,v in record.items() if k!='calls'}),flush=True);return record
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(family,VERSIONS))
    assert pin(jar)==artifact and all(pin(ROOT/p)==value for p,value in sources.items());write(out/'COMPLETED.json',dict(status='passed-three-engine-preflight-recovery-cache-tamper-profiles-and-late-diagnostics',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),runs=runs,source_pins=sources,owned_linux_root=remote,whole_paper_reproduced=False,catalog_promoted=False))
if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_NEXTFLOW_RECOVERY_MATRIX: '+str(failure)+'\nRequired action: preserve every original and copied attempt, inspect the named lifecycle/baseline/profile/capacity discrepancy, and rerun to NEW paths only after correction.',file=sys.stderr);sys.exit(2)

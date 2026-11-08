# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check portable exact kit export, overwrite refusal and forbidden-install skip without analysis."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_SETUP_CONTRACTS',checks=[],full_adapter_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-GSEApy-runtime-transport-and-export-foundation-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};resource=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-workflow';sources={n:pin(p) for n,p in [(str(p.relative_to(resource).as_posix()),p) for p in resource.rglob('*') if p.is_file()]}
        for label,java,linux in [('windows21',str(JAVA21/'java.exe'),False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False),('linux21','/usr/bin/java',True)]:
            home=out/label;home.mkdir();kit=home/'exported-kit'
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
            prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java] if linux else [java]
            for name,options,expected in [('export',['--export',path(kit)],0),('refuse-existing',['--export',path(kit)],2),('help',['--help'],0)]:
                command=prefix+['-cp',path(jar),'org.jlinalg.cli.GseapySetupCli',*options];run=subprocess.run(command,capture_output=True,cwd=ROOT,timeout=120);(home/(name+'.stdout')).write_bytes(run.stdout);(home/(name+'.stderr')).write_bytes(run.stderr);save(home/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==expected,run.stderr.decode(errors='replace');assert {p.relative_to(kit).as_posix():pin(p) for p in kit.rglob('*') if p.is_file()}==sources;state['checks'].append(dict(jvm=label,contract=name,exit_code=run.returncode))
        home=out/'forbidden-install';home.mkdir();setup=home/'receipts';prefix=home/'must-remain-absent';kit=out/'linux21/exported-kit';base='/mnt/d/projects/JLinAlg/build/cellphonedb-workflow-case-sensitive-1/runtime-1/bin/python';linux=lambda p:'/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix();command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',base,'-I','-B',linux(kit/'setup/prepare.py'),'--out',linux(setup),'--prefix',linux(prefix),'--base-runtime','/mnt/d/projects/JLinAlg/build/cellphonedb-workflow-case-sensitive-1/setup-1/COMPLETED.json'];run=subprocess.run(command,capture_output=True,cwd=ROOT,timeout=60);(home/'setup.stdout').write_bytes(run.stdout);(home/'setup.stderr').write_bytes(run.stderr);save(home/'process.json',dict(command=command,exit_code=run.returncode));report=json.loads((setup/'COMPLETED.json').read_bytes());assert run.returncode==3 and report['status']=='SKIP3_GSEAPY_RUNTIME_UNAVAILABLE' and not prefix.exists() and not report['analysis_admitted'];assert 'skip dependent' in (setup/'WARNINGS.md').read_text();state['checks'].append(dict(contract='forbidden-install-skips-dependent-analysis',exit_code=3,prefix_created=False));assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-three-JVM-exact-export-and-forbidden-install-skip-only',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())

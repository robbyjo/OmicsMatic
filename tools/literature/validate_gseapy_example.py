# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the guide's exact source example against the bound artifact on all three actual JVMs."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_EXAMPLE',runs=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-native-GSEApy-stats-component-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};source=ROOT/'examples/gseapy-statistics/GseapyOraExample.java';guide=ROOT/'docs/literature/GSEAPY_STATISTICS_DEVELOPMENT.md';assert built['artifact']['path'] in guide.read_text() and 'java -cp $gseapyJar examples/gseapy-statistics/GseapyOraExample.java' in guide.read_text();sources=[Path(__file__).resolve(),source,guide,ROOT/'examples/gseapy-statistics/README.md'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
        for label,java,linux in [('windows21',str(JAVA21/'java.exe'),False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False),('linux21','/usr/bin/java',True)]:
            home=out/label;home.mkdir()
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
            command=([java] if not linux else ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java])+['-Xmx512m','-cp',path(jar),path(source)]
            run=subprocess.run(command,capture_output=True,cwd=ROOT,timeout=120);(home/'example.stdout').write_bytes(run.stdout);(home/'example.stderr').write_bytes(run.stderr);save(home/'process.json',dict(command=command,exit_code=run.returncode));text=run.stdout.decode('utf8');assert run.returncode==0 and text.count(' overlap=')==2 and 'Native local one-sided ORA complete.' in text,run.stderr.decode(errors='replace');state['runs'].append(dict(jvm=label,exit_code=run.returncode,complete_pathways=2))
        assert all(pin(ROOT/n)==v for n,v in pins.items());state.update(status='passed-exact-guide-native-GSEApy-example-on-three-JVMs',source_pins=pins,artifact=built['artifact'])
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())

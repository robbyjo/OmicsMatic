# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate all original stats outcomes on actual Windows21/25 and Linux21 JVMs."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['build','reference','out']:parser.add_argument('--'+name,required=True,type=Path)
    a=parser.parse_args();a.build=a.build.resolve();a.reference=a.reference.resolve();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_NATIVE_STATISTICS_VALIDATION',runs=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-native-GSEApy-stats-component-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};reference=json.loads((a.reference/'COMPLETED.json').read_bytes());assert reference['status']=='passed-original-four-edition-GSEApy-stats-reference-only' and reference['complete_runtime_unchanged'];cases=a.reference/'cases.json';assert pin(cases)==reference['cases'];files=[Path(__file__).resolve(),a.reference/'COMPLETED.json',cases];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        for label,java,linux,major in [('windows21',str(JAVA21/'java.exe'),False,'21'),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False,'25'),('linux21','/usr/bin/java',True,'21')]:
            home=out/label;home.mkdir()
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
            def command(args):return ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java,*args] if linux else [java,*args]
            version=subprocess.run(command(['-version']),capture_output=True,timeout=60);(home/'java-version.stdout').write_bytes(version.stdout);(home/'java-version.stderr').write_bytes(version.stderr);assert version.returncode==0 and ('"'+major+'.' in (version.stdout+version.stderr).decode(errors='replace') or '"'+major+'"' in (version.stdout+version.stderr).decode(errors='replace'))
            arguments=command(['-Xmx2g','-cp',path(jar),'org.jlinalg.cli.GseapyStatisticsMatrix',path(cases),path(home/'audit.json')]);run=subprocess.run(arguments,cwd=ROOT,capture_output=True,timeout=300);(home/'audit.stdout').write_bytes(run.stdout);(home/'audit.stderr').write_bytes(run.stderr);save(home/'process.json',dict(command=arguments,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');report=json.loads((home/'audit.json').read_bytes());assert report['status']=='passed-complete-four-edition-native-GSEApy-stats-component-only' and report['ora_cases']==reference['ora_cases'] and report['tail_cases']==reference['tail_cases'] and report['correction_cases']==reference['correction_cases'] and report['invalid_inputs_rejected']==13;state['runs'].append(dict(jvm=label,report=report));print(label,report['numeric_states'],'numeric states passed',flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='passed-three-JVM-four-edition-native-GSEApy-stats-component-only',source_pins=pins,artifact=built['artifact'],canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())

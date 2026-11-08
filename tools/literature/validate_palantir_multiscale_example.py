# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the documented native-only JShell example from the exact additive artifact."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

EXAMPLE='''import java.util.Arrays;
import org.jlinalg.embedding.PalantirMultiscale;
var result = PalantirMultiscale.scale(new String[]{"cell-A","cell-B"}, new double[][]{{1,2,3,3},{1,4,5,6}}, new double[]{1,.75,.5,.25}, 4, PalantirMultiscale.Limits.defaults());
System.out.println(Arrays.deepToString(result.coordinates()));
System.out.println(Arrays.toString(result.selectedComponents()));
/exit
'''

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_PALANTIR_EXAMPLE',implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};(out/'example.jsh').write_text(EXAMPLE,encoding='utf8',newline='\n');command=[str(JAVA21/'jshell.exe'),'--class-path',str(jar)];run=subprocess.run(command,input=EXAMPLE.encode(),capture_output=True,timeout=90);(out/'stdout').write_bytes(run.stdout);(out/'stderr').write_bytes(run.stderr);assert run.returncode==0 and b'[[6.0, 3.0, 1.0], [12.0, 5.0, 2.0]]' in run.stdout and b'[1, 2, 3]' in run.stdout and b'Error:' not in run.stdout;state.update(status='PASS_NATIVE_ONLY_PALANTIR_DOCUMENTED_EXAMPLE',command=command,exit_code=run.returncode,artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())

# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented native-only example from the exact development artifact."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

EXAMPLE='''import java.util.Arrays;
import org.jlinalg.embedding.SimplePptMath;
var limits = SimplePptMath.Limits.defaults();
var r = SimplePptMath.assignments(new double[][]{{0,0},{1000,1000}}, .5, limits);
System.out.println(Arrays.deepToString(r.weights()));
System.out.println("zero rows="+r.zeroMassRows());
System.out.println(r.warnings());
System.out.println("literal error="+SimplePptMath.convergenceError(new double[][]{{1,3}}, new double[][]{{2,2}}, limits));
/exit
'''

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['build','out']:parser.add_argument('--'+name,type=Path,required=True)
    a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SIMPLEPPT_NATIVE_EXAMPLE',implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};(out/'example.jsh').write_text(EXAMPLE,encoding='utf8',newline='\n');command=[str(JAVA21/'jshell.exe'),'--class-path',str(jar)];run=subprocess.run(command,input=EXAMPLE.encode(),capture_output=True,timeout=90);(out/'stdout').write_bytes(run.stdout);(out/'stderr').write_bytes(run.stderr);assert run.returncode==0 and b'[[0.5, 0.5], [0.0, 0.0]]' in run.stdout and b'SOURCE ZERO-MASS ASSIGNMENTS' in run.stdout and b'Error:' not in run.stdout and b'zero rows=1' in run.stdout and b'literal error=0.0' in run.stdout;state.update(status='PASS_SIMPLEPPT_NATIVE_DOCUMENTED_EXAMPLE_ONLY',command=command,exit_code=run.returncode,artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())

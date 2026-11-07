# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual motifmatchr, sequence statistics and original Signac chromVAR source wrapper."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    names=['tools/literature/probe_signac_motif_runtime.py','tools/literature/probe_signac_motif_runtime.R','src/main/resources/org/jlinalg/omics/signac_motif_legacy_access.R','src/main/resources/org/jlinalg/omics/signac-motifs/1.9.0/RunChromVAR.ChromatinAssay.R'];sources={n:pin(ROOT/n) for n in names};(out/'SOURCE-PINS.json').write_text(json.dumps(sources,indent=2)+'\n',encoding='utf8')
    for name in sources:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    env=dict(os.environ)
    for key in list(env):
        if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
    env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/'tools/literature/probe_signac_motif_runtime.R'),str(out/'analysis'),str(BASE/'motif-reference-setup-1/R-lib')]
    process=None
    try:
        with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:
            process=subprocess.Popen(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
            (out/'RUNNING.json').write_text(json.dumps(dict(command=argv,owned_pid=process.pid),indent=2)+'\n',encoding='utf8');code=process.wait(timeout=180)
        (out/'process.json').write_text(json.dumps(dict(command=argv,exit_code=code),indent=2)+'\n',encoding='utf8');assert code==0;result=json.loads((out/'analysis/RESULT.json').read_bytes());assert all('error' not in v['value'] for v in result['results'].values());assert all(pin(ROOT/n)==v for n,v in sources.items());(out/'COMPLETED.json').write_text(json.dumps(dict(status='passed-actual-original-motif-scanning-stats-and-chromVAR-runtime-probe',source_pins=sources,results=result,user_analysis_admitted=False),indent=2)+'\n',encoding='utf8');print(json.dumps(result,ensure_ascii=True))
    except BaseException as error:
        if process is not None and process.poll() is None:
            subprocess.run(['taskkill.exe','/PID',str(process.pid),'/T','/F'],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20,check=False);process.wait(timeout=20)
        (out/'FAILURE.json').write_text(json.dumps(dict(status='FAILED_ORIGINAL_MOTIF_RUNTIME_PROBE_NO_USER_ANALYSIS_ADMITTED',error=str(error),required_action='Preserve logs/partials; correct the named runtime/input/parallel-backend cause and use NEW paths. Skip dependent analysis until the original runtime controls pass.'),indent=2)+'\n',encoding='utf8');raise

if __name__=='__main__':main()

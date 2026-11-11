# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Probe existing R dependencies without installation or scientific inference."""
import argparse,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,BASES,environment,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cellchat-runtime');out.mkdir(parents=True)
    source=ROOT/'tools/literature/probe_cellchat_reference.R'
    command=[str(RSCRIPT),'--vanilla',str(source),str(out),*map(str,BASES)]
    run=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
    (out/'probe.stdout').write_bytes(run.stdout);(out/'probe.stderr').write_bytes(run.stderr)
    save(out/'COMPLETED.json',dict(status='PASS_DEPENDENCY_PROBE_ONLY' if run.returncode==0 else 'SKIP3_R_DEPENDENCY_PROBE',command=command,exit_code=run.returncode,analysis_admitted=False,implementation_credit=False,source=pin(source)))
    print(run.stdout.decode(errors='replace'));print(run.stderr.decode(errors='replace'))
    return run.returncode

if __name__=='__main__':raise SystemExit(main())

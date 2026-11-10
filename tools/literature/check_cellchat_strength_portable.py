# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete saved CellChat strength bundle verification without R or installation."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--java',required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--case',action='append',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True);jar=a.jar.resolve();before=pin(jar);records=[]
    for i,case in enumerate(a.case):
        case=case.resolve();command=[a.java,'-Xmx2g','-jar',str(jar),'--verify',str(case)];r=subprocess.run(command,capture_output=True,stdin=subprocess.DEVNULL,timeout=1200);(out/f'case-{i+1}.stdout').write_bytes(r.stdout);(out/f'case-{i+1}.stderr').write_bytes(r.stderr);records.append(dict(case=case.relative_to(BASE).as_posix(),command=command,exit_code=r.returncode));assert r.returncode==0,(case,r.stderr[-3000:])
    assert pin(jar)==before
    save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_ORIGINAL_STRENGTH_OFFLINE_BUNDLES',bundles=len(records),records=records,jar=before,source_pin=pin(Path(__file__).resolve()),R_executed=False,new_catalog_credit=False,whole_goal_complete=False));print('PASS',len(records),'complete saved bundles; no R execution',flush=True)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable portable verification checks')
    main()

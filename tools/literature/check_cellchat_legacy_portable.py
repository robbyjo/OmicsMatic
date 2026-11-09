# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual standalone-JAR offline verification on every complete legacy family."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/cellchat-runtime'
CASES=['legacy-typed-controls-7/analysis','legacy-typed-controls-8/analysis','legacy-projected-mouse-controls-2/analysis','legacy-prefilter-boundary-1/analysis','legacy-public-example-1']
def pin(path):raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--java',required=True);p.add_argument('--jar',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True);jar=a.jar.resolve();before=pin(jar);records=[]
    for i,case in enumerate(CASES):
        target=BASE/case;command=[a.java,'-Xmx1g','-jar',str(jar),'--verify',str(target)];r=subprocess.run(command,capture_output=True,stdin=subprocess.DEVNULL,timeout=300);(out/f'case-{i+1}.stdout').write_bytes(r.stdout);(out/f'case-{i+1}.stderr').write_bytes(r.stderr);records.append(dict(case=case,command=command,exit_code=r.returncode));assert r.returncode==0,(case,r.stderr[-3000:])
    command=[a.java,'-Xmx1g','-cp',str(jar),'org.jlinalg.cli.CellChatLegacyAuditProbe'];r=subprocess.run(command,capture_output=True,timeout=60);(out/'api.stdout').write_bytes(r.stdout);(out/'api.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr;assert pin(jar)==before
    (out/'COMPLETED.json').write_text(json.dumps(dict(status='PASS_COMPLETE_LEGACY_OFFLINE_BUNDLES_AND_NATIVE_API',bundles=len(CASES),records=records,jar=before,source_pin=pin(Path(__file__).resolve()),R_executed=False,new_catalog_credit=False,whole_goal_complete=False),indent=2)+'\n',encoding='utf8');print('PASS',len(CASES),'complete legacy bundles and native API; no R execution',flush=True)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable portable verification checks')
    main()

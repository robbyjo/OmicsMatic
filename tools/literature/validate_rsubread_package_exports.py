# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise permission-controlled private original-package export without running R or changing shared software."""
import argparse,json,os,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    script=ROOT/'tools/literature/export_rsubread_runtime_package.py';runs={}
    for name,edition,allowed,expected in [('forbidden','2260',False,3),('2260','2260',True,0),('2232-R441','2232-R441',True,0)]:
        command=[sys.executable,str(script),'--edition',edition,'--out',str(out/name)]+(['--allow-install'] if allowed else [])
        run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==expected,(name,run.stderr.decode(errors='replace'))
        receipt=json.loads((out/name/'COMPLETED.json').read_bytes());assert receipt['runtime_exported']==allowed and not receipt['analysis_admitted']
        if allowed:assert not receipt['R_executed'] and not receipt['shared_library_changed'] and (out/name/'corresponding-original-source/src/readSummary.c').is_file()
        else:assert not (out/name/'R-lib').exists()
        runs[name]=dict(receipt=pin(out/name/'COMPLETED.json'),private_software_exported=allowed)
    save(out/'COMPLETED.json',dict(status='passed-two-exact-original-GPL-private-package-and-source-exports-and-one-real-forbidden-skip',runs=runs,R_executed=False,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),script]}));print('passed-two-exact-original-GPL-private-package-and-source-exports-and-one-real-forbidden-skip')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable package-export permission and source checks')
    main()

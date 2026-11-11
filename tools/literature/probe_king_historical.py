# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Privately probe unchanged official KING2.0/2.2.7 programs; preserve source failures and exact profiles."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE, DISTRO, linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[];skips=[]
    source=BASE/'king-historical-intake-1'
    original=BASE/'king-source-intake-1/ex-original/ex'
    own=BASE/'king-own-controls-1/own-inputs/cohort'
    profiles=[('2.2.7-windows','windows',source/'Windows-king227-original/king.exe'),
              ('2.2.7-linux','linux',source/'Linux-king227-original/king'),
              ('2.0-linux','linux',source/'Linux-king20-original/king'),
              ('2.0-ubuntu','linux',source/'Ubuntu-king20-original/king')]
    inputs=[p.with_suffix('.'+e) for p in [original,own] for e in ['bed','bim','fam']]
    before={str(p):pin(p) for p in [*[r[2] for r in profiles],*inputs]}
    state=dict(status='FAIL2_HISTORICAL_KING_RUNTIME_PROBE',new_catalog_credit=False,whole_goal_complete=False)
    try:
        def run(profile,platform,executable,name,args):
            folder=out/profile/name;folder.mkdir(parents=True)
            command=[str(executable),*args] if platform=='windows' else ['wsl.exe','-d',DISTRO,'--cd',linux(folder),'--exec',
                      '/usr/bin/env','-i','PATH=/usr/bin:/bin','LANG=C','LC_ALL=C',linux(executable),*args]
            with (folder/'stdout').open('xb') as so,(folder/'stderr').open('xb') as se:
                r=subprocess.run(command,cwd=folder,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=240)
            record=dict(profile=profile,platform=platform,mode=name,exit_code=r.returncode,command=command,
                        files={p.name:pin(p) for p in sorted(folder.iterdir()) if p.is_file()})
            save(folder/'process.json',record);records.append(record)
            print(json.dumps(dict(profile=profile,mode=name,exit_code=r.returncode,files=list(record['files']))),flush=True)
            return folder,r.returncode
        for profile,platform,executable in profiles:
            if platform=='linux':
                folder=out/profile/'dependencies';folder.mkdir(parents=True)
                command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/ldd',linux(executable)]
                r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=60)
                (folder/'stdout').write_bytes(r.stdout);(folder/'stderr').write_bytes(r.stderr)
                save(folder/'process.json',dict(command=command,exit_code=r.returncode))
                if r.returncode or b'not found' in r.stdout+r.stderr:
                    warning='SKIP3 '+profile+': dependency unavailable; skip exact historical analysis. Supply named Linux dependencies where permitted, otherwise keep skipped; do not substitute another KING version. '+(r.stdout+r.stderr).decode(errors='replace')
                    skips.append(warning);(folder/'WARNINGS.md').write_text(warning,encoding='utf8');print(warning,flush=True);continue
            folder,code=run(profile,platform,executable,'no-input-banner',[])
            text=(folder/'stdout').read_text(errors='replace')+(folder/'stderr').read_text(errors='replace')
            version=profile.rsplit('-',1)[0];assert 'KING '+version in text,(profile,'Exact version banner absent')
            convert=linux if platform=='linux' else lambda p:str(p.resolve())
            plan=[('official-kinship',original,['--kinship']),('own-kinship',own,['--kinship']),
                  ('own-duplicate',own,['--duplicate'])]
            if version=='2.0':
                plan += [('official-kinship-bit64',original,['--kinship','--bit64']),
                         ('own-kinship-bit64',own,['--kinship','--bit64'])]
            for label,prefix,flags in plan:
                folder=out/profile/label
                _,code=run(profile,platform,executable,label,['-b',convert(prefix.with_suffix('.bed')),*flags,'--cpus','1','--prefix',convert(folder/'original')])
                assert code==0,(profile,label,'Original execution failed; retain cause and outputs')
        assert all(pin(Path(p))==v for p,v in before.items())
        state.update(status='PASS_ORIGINAL_HISTORICAL_KING_RUNTIME_PROBE_ONLY',records=records,skipped_profiles=skips,
                     complete_input_binary_pins=before,producer=pin(Path(__file__)),
                     scope='Private original execution only. No typed adapter, numerical population validation or historical-paper reproduction credit.')
    except Exception as error:
        state.update(error=repr(error),records=records,skipped_profiles=skips);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2400],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original input/runtime preservation checks')
    raise SystemExit(main())

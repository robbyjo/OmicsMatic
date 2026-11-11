# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual exported MTAG preparation refusals without downloading, installing or fitting."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux
from validate_mtag_tools_safety import census


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--runtime-profile',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_RUNTIME_REFUSAL_CONTROLS',downloads_permitted=False,installation_executed=False,analysis_executed=False,new_catalog_credit=False)
    (out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        helper=ROOT/'src/main/resources/org/jlinalg/omics/mtag/prepare_mtag_runtime.py';lock=helper.with_name('mtag-workflow-lock.json');protected={str(p):pin(p) for p in [helper,lock,a.runtime_profile]};runtime=json.loads(a.runtime_profile.read_bytes());cases=[]
        def add(name,args,expected,phrase,target=None):cases.append(dict(case=name,args=args,expected=expected,phrase=phrase,target=target))
        for name in ['no-download','no-install','corrupt-offline','existing-output','unsafe-runtime-nesting']:(out/name).mkdir()
        native='/var/tmp/omicsmatic-mtag-forbidden-'+out.name
        add('no-download',['--out',linux(out/'no-download/receipts'),'--environment-home',native+'-download','--allow-install'],3,'downloads were not authorized')
        add('no-install',['--out',linux(out/'no-install/receipts'),'--environment-home',native+'-install','--archive-dir',linux(BASE/'workflow-offline-source-1')],3,'Installation was not authorized')
        bad=out/'corrupt-offline/archives';bad.mkdir();(bad/'mtag-source.zip').write_bytes(b'wrong archive bytes, no replacement download permitted\n')
        add('corrupt-offline',['--out',linux(out/'corrupt-offline/receipts'),'--environment-home',native+'-corrupt','--archive-dir',linux(bad)],2,'archive')
        existing=out/'existing-output/receipts';existing.mkdir();(existing/'KEEP.txt').write_bytes(b'preserve complete existing output\r\n');before=census(existing)
        add('existing-output',['--out',linux(existing),'--environment-home',native+'-existing'],2,'output exists',existing)
        unsafe=runtime['environment']+'/NEVER_CREATE_BY_SETUP_CONTROL'
        add('unsafe-runtime-nesting',['--out',unsafe,'--existing-environment',runtime['environment']],2,'without nesting')
        results=[]
        for c in cases:
            home=out/c['case'];cmd=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/python3',linux(helper),*c['args']]
            r=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(home/'stdout').write_bytes(r.stdout);(home/'stderr').write_bytes(r.stderr);save(home/'process.json',dict(command=cmd,exit_code=r.returncode,expected=c['expected']))
            assert r.returncode==c['expected'],(c['case'],r.stderr.decode(errors='replace'));assert c['phrase'].lower() in r.stderr.decode(errors='replace').lower(),(c['case'],r.stderr.decode(errors='replace'))
            if c['target']:assert census(c['target'])==before
            receipt=home/'receipts/setup.json'
            if receipt.exists():
                d=json.loads(receipt.read_bytes());assert not d['analysis_executed'] and not d['result_admitted'];assert not (home/'receipts/runtime.json').exists()
            results.append(dict(case=c['case'],exit_code=r.returncode,passed=True));print(c['case'],r.returncode,flush=True)
        for name,wanted in protected.items():assert pin(Path(name))==wanted
        # Never create, remove or update the proposed environment paths.
        check=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/python3','-c',"import os,sys; assert all(not os.path.lexists(p) for p in sys.argv[1:]); print('All forbidden installation and unsafe-output targets absent')",*[native+'-'+n for n in ['download','install','corrupt','existing']],unsafe]
        r=subprocess.run(check,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=30);(out/'absent-targets.stdout').write_bytes(r.stdout);save(out/'absent-targets.process.json',dict(command=check,exit_code=r.returncode));assert r.returncode==0
        state.update(status='PASS5_MTAG_RUNTIME_REFUSAL_AND_PRESERVATION_CONTROLS',cases=results,existing_output_unchanged=True,forbidden_environments_not_created=True)
        print(state['status'])
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');print(state['status']+': '+repr(error),flush=True)
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable forbidden-installation and existing-output preservation checks')
    raise SystemExit(main())

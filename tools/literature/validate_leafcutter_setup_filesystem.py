# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the packaged setup's case-collision skip before any installer or prefix creation."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup-kit',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir();state=dict(status='FAIL2_LEAFCUTTER_FILESYSTEM_SKIP_NOT_VALIDATED',implementation_credit=False,whole_goal_complete=False)
    script=a.setup_kit/'setup/prepare.py';sources={str(Path(__file__).resolve().relative_to(ROOT).as_posix()):pin(Path(__file__))};prefix=out/'must-not-create-runtime';setup=out/'preparation';installer=ROOT/'build/literature-review/leafcutter-runtime/r-python-setup-1/micromamba';original_installer=pin(installer)
    try:
        kit=json.loads((a.setup_kit/'setup-kit.json').read_bytes());assert all(pin(a.setup_kit/name)==want for name,want in kit['files'].items());command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(script),'--mode','INSTALL','--out',linux(setup),'--prefix',linux(prefix),'--micromamba',linux(installer),'--allow-install'];result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW);(out/'stdout').write_bytes(result.stdout);(out/'stderr').write_bytes(result.stderr);save(out/'process.json',dict(command=command,exit_code=result.returncode));receipt=json.loads((setup/'COMPLETED.json').read_bytes());assert result.returncode==3 and receipt['status']=='SKIP3_LEAFCUTTER_RUNTIME_UNAVAILABLE' and receipt['analysis_executed'] is False and receipt['processes']==[] and not prefix.exists() and receipt['filesystem_checks'][0]['case_sensitive'] is False and 'case-sensitive' in receipt['error'] and b'case-sensitive' in (setup/'WARNINGS.md').read_bytes();assert pin(installer)==original_installer and all(pin(a.setup_kit/name)==want for name,want in kit['files'].items());state.update(status='PASS_PACKAGED_LEAFCUTTER_CASE_COLLISION_SKIP_BEFORE_INSTALL_ONLY',setup_kit_manifest=pin(a.setup_kit/'setup-kit.json'),setup_source=pin(script),source_pins=sources,installer_unchanged=original_installer,software_prefix_created=False,installation_invoked=False,dependent_analysis_admitted=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete packaged setup skip checks')
    raise SystemExit(main())

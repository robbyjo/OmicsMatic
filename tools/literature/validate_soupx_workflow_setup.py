# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise offline exact-source export, explicit private installs and an unenabled-install skip."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/soupx-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir();names=['export_soupx_workflow_source.py','setup_soupx_workflow_runtime.py','install_soupx_reference.R','prepare_soupx_workflow_example.py','validate_soupx_workflow_setup.py'];pins={('tools/literature/'+n):pin(ROOT/'tools/literature'/n) for n in names};reports=[]
    def run(label,arguments,expected):
        command=[sys.executable,*map(str,arguments)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=660);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==expected,(label,r.stderr.decode('utf8',errors='replace')[-1800:])
    for version in ['1.6.2','1.3.0']:
        folder=out/version;folder.mkdir();source=folder/'export';run('export-'+version,[ROOT/'tools/literature/export_soupx_workflow_source.py','--version',version,'--out',source],0);ref=base/('reference-1.6.2-1' if version=='1.6.2' else 'reference-1.3.0-2');old=json.loads((ref/'STARTED.json').read_bytes());libraries=old['libraries'];args=[ROOT/'tools/literature/setup_soupx_workflow_runtime.py','--source-home',source,'--rscript',old['command'][0]]
        for library in libraries:args+=['--dependency-library',library]
        prepared=folder/'prepared';run('install-'+version,[*args,'--out',prepared,'--allow-install'],0);state=json.loads((prepared/'COMPLETED.json').read_bytes());assert state['status']=='installed-source-bound-original-SoupX-reference-only' and state['preexisting_libraries_unchanged'] and not state['analysis_admitted'];assert (prepared/'LIBRARIES-BEFORE.json').read_bytes()==(prepared/'LIBRARIES-AFTER.json').read_bytes();run('example-'+version,[ROOT/'tools/literature/prepare_soupx_workflow_example.py','--reference-home',prepared,'--out',folder/'example'],0);request=json.loads((folder/'example/request.json').read_bytes());assert request['version']==version and Path(request['soup_source'])==source/'source' and request['r_libraries'][0]==str(prepared/'R-lib');reports.append(dict(version=version,original_source_files=58 if version=='1.6.2' else 45,private_install_and_source_health_passed=True,documented_example_prepared=True))
        if version=='1.6.2':
            skipped=folder/'installation-disabled';run('installation-disabled',[*args,'--out',skipped],3);skip=json.loads((skipped/'COMPLETED.json').read_bytes());assert skip['status']=='SKIP3_SOUPX_REFERENCE_UNAVAILABLE' and not skip['analysis_admitted'] and skip['preexisting_libraries_unchanged'] and not (skipped/'STARTED.json').exists() and not (skipped/'R-lib').exists();assert 'skip dependent SoupX analysis' in (skipped/'WARNINGS.md').read_text();reports.append(dict(installation_not_enabled=True,exit_code=3,install_process_started=False,dependent_analysis_skipped=True))
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='passed-offline-original-SoupX-export-and-opt-in-private-setup',source_pins=pins,protocols=reports,preexisting_libraries_unchanged=True,analysis_admitted=False,whole_goal_complete=False));print('Passed2 exact-source exports/private installs/example preparations and unenabled-install SKIP3')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/installation checks')
    main()

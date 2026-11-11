# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify denied/unsupported preparation writes prominent SKIP3 actions before any download or analysis."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/loftee-runtime') and not out.exists();out.mkdir(parents=True);installer=ROOT/'tools/literature/install_loftee_workflow_runtime.py';exporter=ROOT/'tools/literature/setup_loftee_workflow_runtime.py';sources=[Path(__file__).resolve(),installer,exporter,ROOT/'src/main/resources/org/jlinalg/omics/loftee-workflow/SOURCE-PROFILE.json',ROOT/'docs/literature/loftee-workflow-development/INSTALL-LOCK.json'];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources};rows=[]
    commands=[('installation-not-authorized',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3',linux(installer),'--out',linux(out/'installation-not-authorized/preparation')]),('windows-installation-unsupported',[sys.executable,str(installer),'--out',str(out/'windows-installation-unsupported/preparation'),'--allow-install']),('export-not-authorized',[sys.executable,str(exporter),'--environment',str(out/'not-an-environment'),'--bioperl',str(out/'not-bioperl'),'--out',str(out/'export-not-authorized/preparation')])]
    for name,command in commands:
        home=out/name;home.mkdir();r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(home/'stdout.log').write_bytes(r.stdout);(home/'stderr.log').write_bytes(r.stderr);save(home/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==3,(name,r.returncode,r.stderr);prepared=home/'preparation';status=json.loads((prepared/'COMPLETED.json').read_bytes());warnings=(prepared/'WARNINGS.md').read_text(encoding='utf8');assert status['status']=='SKIP3_DEPENDENCY_UNAVAILABLE' and 'SKIP3' in warnings and 'permitted' in warnings and not (prepared/'archives').exists() and not (prepared/'runtime').exists();assert not status.get('analysis_executed',status.get('variant_analysis_executed'));assert b'SKIP3' in r.stdout;rows.append(dict(name=name,exit_code=3,download_or_analysis_started=False,prominent_warning_and_required_action_present=True));print(name,'SKIP3 before downloads/analysis passed',flush=True)
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='passed-denied-and-unsupported-dependency-preparation-SKIP3-controls',source_pins=pins,protocols=rows,implementation_credit=False))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable denied-installation checks')
    main()

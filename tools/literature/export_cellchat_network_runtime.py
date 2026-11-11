# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the exact private original CellChat1.1.3 network and Python runtimes."""
import argparse,json,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--setup',type=Path,required=True);p.add_argument('--python-inventory',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    setup=a.setup.resolve();ready=json.loads((setup/'COMPLETED.json').read_bytes());assert ready['status']=='PASS_ORIGINAL_NETWORK_REFERENCE_SETUP_ONLY' and ready['preexisting_libraries_unchanged'];assert all(pin(ROOT/n)==v for n,v in ready['source_pins'].items())
    for root,members in ready['private_library_inventory'].items():assert all(pin(Path(root)/n)==v for n,v in members.items())
    original=json.loads((BASE/'legacy-runtime-profile-1/runtime-profile.json').read_bytes());assert original['source_label']=='1.1.3';assert all(pin(Path(n))==v for n,v in original['files'].items())
    python=json.loads(a.python_inventory.read_bytes());assert python['status']=='PYTHON_NETWORK_DEPENDENCY_INVENTORY_ONLY';assert all(pin(Path(n))==v for n,v in python['files'].items())
    libraries=[str(setup/'R-lib'),*original['libraries']];files=dict(original['files']);files.update(python['files']);new=inventory([setup/'R-lib']);files.update({str(Path(root)/name):value for root,members in new.items() for name,value in members.items()})
    profile=dict(schema_version=1,status='validated-original-cellchat-network-runtime-profile-only',source_label='1.1.3',rscript=original['rscript'],r_home=original['r_home'],libraries=libraries,source_root=original['source_root'],python=python['python'],python_home=python['venv'],python_base=python['base'],files=files,
        setup_receipts={str(setup/'COMPLETED.json'):pin(setup/'COMPLETED.json'),str(a.python_inventory.resolve()):pin(a.python_inventory)},scope='Windows R4.6.1 and Python3.13.13; original CellChat1.1.3, future1.23.0/future.apply1.8.1, reticulate1.47.0 and umap-learn0.5.12. Complete selected R libraries and R bin/etc/modules; selected Python venv, base stdlib excluding base site-packages, base DLLs and Python root files. Other host/OS libraries are not authenticated. Analysis installs nothing; managed Python discovery is disabled and exact Python is prescribed.')
    save(out/'runtime-profile.json',profile);save(out/'COMPLETED.json',dict(status='PASS_NETWORK_RUNTIME_PROFILE_EXPORT_ONLY',profile=pin(out/'runtime-profile.json'),files=len(files),bytes=sum(v['bytes'] for v in files.values()),analysis_admitted=False,whole_goal_complete=False));print('Bound original network runtime files='+str(len(files))+'; no analysis admitted')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable runtime integrity checks')
    try:raise SystemExit(main())
    except Exception as error:
        unavailable=isinstance(error,(FileNotFoundError,PermissionError));print(('SKIP3_NETWORK_RUNTIME_UNAVAILABLE' if unavailable else 'FAIL2_NETWORK_RUNTIME_PROFILE')+': '+repr(error)+'\nREQUIRED ACTION: Skip dependent network analysis. Complete the exact permitted setup and Python prerequisites, preserve this attempt, and export a fresh profile in NEW paths. Do not change shared libraries or silently substitute an engine or parallel strategy.',file=sys.stderr);raise SystemExit(3 if unavailable else 2)

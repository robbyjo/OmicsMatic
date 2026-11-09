# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the validated private original CellChat/Presto libraries for typed analysis."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,pin,save
from setup_chipseeker_reference import inventory

BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    setups=[BASE/'presto-reference-setup-2',BASE/'reference-setup-1'];reports=[json.loads((p/'COMPLETED.json').read_bytes()) for p in setups]
    assert [r['status'] for r in reports]==['INSTALLED_ORIGINAL_PRESTO_REFERENCE_ONLY','INSTALLED_ORIGINAL_CELLCHAT_REFERENCE_ONLY']
    for setup,report in zip(setups,reports,strict=True):
        assert report['preexisting_libraries_unchanged']
        for name,wanted in report['source_pins'].items():assert pin(ROOT/name)==wanted
        for root,members in report.get('private_library_inventory',{}).items():
            for name,wanted in members.items():assert pin(Path(root)/name)==wanted
    libraries=[setups[0]/'R-lib',setups[1]/'R-lib',*[Path(p) for p in json.loads((setups[1]/'STARTED.json').read_bytes())['libraries']]]
    libraries=list(dict.fromkeys(p.resolve() for p in libraries));snapshots=inventory(libraries);files={str(Path(root)/name):value for root,members in snapshots.items() for name,value in members.items()}
    rhome=RSCRIPT.parents[1]
    for folder in [rhome/'bin',rhome/'etc',rhome/'modules']:
        for file in folder.rglob('*'):
            if file.is_file():files[str(file.resolve())]=pin(file)
    assert str(RSCRIPT.resolve()) in files
    profile=dict(schema_version=1,status='validated-original-cellchat-runtime-profile-only',source_label='2.2.0-2025-release',rscript=str(RSCRIPT.resolve()),r_home=str(rhome.resolve()),libraries=list(map(str,libraries)),source_root=str((BASE/'source-intake-1/2.2.0-2025-release/source').resolve()),files=files,
        setup_receipts={p.relative_to(ROOT).as_posix():pin(p/'COMPLETED.json') for p in setups},scope='Windows R4.6.1; original CellChat2.2.0 and Presto1.0.0. Declared complete R library file populations plus R bin/etc/modules are bound; no hermetic host, compiler reproduction or historical paper environment is claimed. Analysis installs nothing.')
    save(out/'runtime-profile.json',profile);save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_CELLCHAT_RUNTIME_PROFILE_EXPORT_ONLY',profile=pin(out/'runtime-profile.json'),files=len(files),bytes=sum(p['bytes'] for p in files.values()),analysis_admitted=False,implementation_credit=False,whole_goal_complete=False))
    print('Bound original CellChat runtime files='+str(len(files))+'; setup only; no analysis admitted')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original runtime binding checks')
    raise SystemExit(main())

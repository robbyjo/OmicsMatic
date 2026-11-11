# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal original MTAG4-through8-trait controls, preserving the original default four-trait maxFDR calculation."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from signac_scan_evidence import pack
from verify_mtag_dimensions import HOME,STATUS,SOURCES,inspect
from verify_mtag_workflow_evidence import PREFIX


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists() and not HOME.exists()
    source=BASE/'dimension-controls-1';control=json.loads((source/'COMPLETED.json').read_bytes())
    assert control['status']=='PASS_MTAG4_THROUGH8_ORIGINAL_WORKFLOWS_DEFAULT4_TRAIT_GRID_AND15_SAVED_REPLAYS'
    assert len(control['records'])==5 and all(r['passed'] for r in control['records'])
    out.mkdir(parents=True);HOME.mkdir(parents=True);(HOME/'.gitattributes').write_bytes(b'* -text\n')
    files={PREFIX+'dimension-controls-1/'+p.relative_to(source).as_posix():p.read_bytes() for p in sorted(source.rglob('*')) if p.is_file()}
    for name in SOURCES:files['current-source/'+name]=(ROOT/name).read_bytes()
    record=pack(HOME/'controls.zip',files);assert (HOME/'controls.zip').stat().st_size<95_000_000
    dependency=ROOT/'docs/literature/mtag-workflow-development';protected={p.relative_to(ROOT).as_posix():pin(p) for p in dependency.iterdir() if p.is_file()}
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=False,whole_paper_reproduced=False,whole_goal_complete=False,
        synthetic_only=True,independent_biological_datasets=0,provided_covariance_profile=True,source_parameters_repaired_or_retried_with_smaller_grid=False,
        dimensions=[4,5,6,7,8],admitted_trait_rows=30000,saved_replays=15,unpruned_default_four_trait_grid_points=3268760,
        original_default_four_trait_intervals=10,explicit_five_through_eight_trait_intervals=2,
        source_pins={name:pin(ROOT/name) for name in SOURCES},workflow_dependency_files=protected,archive=pin(HOME/'controls.zip'),**record,
        saved_bundle_paths=['dimension-controls-1/traits-'+str(n)+'/analysis' for n in range(4,9)],
        required_action='Synthetic original source controls only. Read MTAG_INTEGRATION.md. Require successful full saved verification and inspect every warning; SKIP dependent analysis if exact prerequisites are unavailable/forbidden. FAIL2 admits no repaired/partial/scientifically unassessable result. Preserve all attempts; no automatic smaller FDR grid, parameter change or biological-validity claim.')
    save(HOME/'VALIDATION.json',proof)
    (HOME/'SHA256SUMS').write_text(''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in sorted(HOME.iterdir()) if p.is_file() and p.name!='SHA256SUMS'),encoding='utf8',newline='\n')
    actual,contents=inspect();assert actual==proof
    save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_MTAG_DIMENSION_EVIDENCE_SEALED',validation=pin(HOME/'VALIDATION.json'),admitted_trait_rows=30000,saved_replays=15,new_catalog_credit=False))
    print(proof['status'])


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original default-grid/full dimension/source evidence checks')
    main()

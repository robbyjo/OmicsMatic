# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete original MTAG4-through8-trait source controls and their unchanged typed-workflow dependency."""
import json
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from verify_mtag_workflow_evidence import inspect as workflow,bundle_inventory,PREFIX
from catalog_source_integrity import verify_source

HOME=ROOT/'docs/literature/mtag-dimensions-development'
STATUS='PASS_ORIGINAL_MTAG4_THROUGH8_DIMENSIONS_AND_DEFAULT4_TRAIT_FDR_GRID'
SOURCES=['tools/literature/'+n+'.py' for n in ['validate_mtag_dimensions','seal_mtag_dimensions','verify_mtag_dimensions']]


def inspect(home=HOME):
    proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']==STATUS and not proof['new_catalog_credit'] and not proof['whole_paper_reproduced']
    assert pin(home/'controls.zip')==proof['archive'];files=unpack(home/'controls.zip',proof)
    for name,wanted in proof['source_pins'].items():
        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',name,wanted['sha256']);assert byte_pin(files['current-source/'+name])==wanted
    for name,wanted in proof['workflow_dependency_files'].items():assert pin(ROOT/name)==wanted,name
    workflow()
    base=PREFIX+'dimension-controls-1/';control=json.loads(files[base+'COMPLETED.json'])
    assert control['status']=='PASS_MTAG4_THROUGH8_ORIGINAL_WORKFLOWS_DEFAULT4_TRAIT_GRID_AND15_SAVED_REPLAYS'
    records=control['records'];assert [r['traits'] for r in records]==[4,5,6,7,8] and all(r['passed'] for r in records)
    rows=0;grids={}
    for record in records:
        n=record['traits'];case='dimension-controls-1/traits-'+str(n)+'/analysis';audit=bundle_inventory(files,case)
        assert audit==record['audit'] and audit['variants']==1000 and audit['traits']==n and audit['fdr']['calculated']
        assert len(record['replays'])==3 and all(r['passed'] for r in record['replays']) and {r['jvm'] for r in record['replays']}=={'windows21','windows25','linux21'}
        request=json.loads(files[base+'traits-'+str(n)+'/request.json']);assert request['fdr']['intervals']==(10 if n==4 else 2) and not request['force_low_signal']
        assert request['covariance']['omega']==request['covariance']['sigma']=='provided' and request['columns']['freq']=='frequency'
        assert json.loads(files[base+'traits-'+str(n)+'/process.json'])['exit_code']==0
        rows+=audit['trait_rows_checked'];grids[str(n)]=audit['fdr']['grid_rows']
    assert rows==30000 and proof['admitted_trait_rows']==rows and proof['saved_replays']==15
    generation=json.loads(files[base+'GENERATION.json']);assert generation['seed']==20261111 and generation['traits']==8 and generation['shared_population_prefixes'] and not generation['independent_biological_datasets']
    assert proof['unpruned_default_four_trait_grid_points']==3268760 and not proof['source_parameters_repaired_or_retried_with_smaller_grid']
    assert len(proof['saved_bundle_paths'])==5
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete dimension/default-grid/source bindings')
    try:
        p,f=inspect();print(json.dumps(dict(status=p['status'],dimensions=[4,5,6,7,8],admitted_trait_rows=30000,saved_replays=15,new_catalog_credit=False)));sys.exit(0)
    except Exception as error:
        print('FAIL2_MTAG_DIMENSION_EVIDENCE: '+repr(error)+'\nRequired action: preserve the complete source/attempt evidence and resolve its named source/archive/scientific cause. No smaller grid or repaired result is admitted.',file=sys.stderr);sys.exit(2)

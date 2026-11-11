# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check immutable privately retained P100 code facts; do not execute or redistribute code."""
import argparse,hashlib,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    home=BASE/'p100-author-intake-1';receipt=json.loads((home/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_IMMUTABLE_P100_AUTHOR_CODE_INTAKE_ONLY' and receipt['commit']=='85767ada483db0d326477b7b3d5d8d355426664b' and not receipt['executed'] and not receipt['license_inferred']
    for entry in receipt['files']:
        file=home/'source'/entry['path'];raw=file.read_bytes();assert pin(file)=={k:entry[k] for k in ['bytes','sha256']};assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==entry['git_blob']
    text=(home/'source/single_cell_analysis/cellchat.R').read_text(encoding='utf8');active='\n'.join(line for line in text.splitlines() if not line.lstrip().startswith('#'))
    assert 'search = "Secreted Signaling"' in active and 'filterCommunication(' not in active
    for prefix in ['ctrl','masl','mash']:
        assert f'{prefix}.cc <- smoothData({prefix}.cc, adj=PPI.human)' in active
        assert f'{prefix}.cc <- computeCommunProb({prefix}.cc, raw.use = FALSE, population.size = FALSE)' in active
    assert all(f'"{name}"' in active for name in ['CTRL','MASL','MASH']) and 'samples = ctrl.meta$sample' in active
    facts=dict(status='PASS_SOURCE_BOUND_INFERENCE_PAPER_ADDENDUM_ONLY',new_catalog_credit=False,whole_goal_complete=False,
        P090=dict(pmcid='PMC11631752',doi='10.1038/s41588-024-01952-y',primary_anchor=39,declared_package='CellChat1.0',declared_R='4.0.3',tested_R='4.6.1 Windows',historical_R_reproduced=False,exact_historical_commit_known=False,database_and_PPI_choice_known=False,public_main_GWAS_code_does_not_establish_CellChat_recipe=True,controls='Explicit full human database and PPI, original default family; these choices are not inferred paper settings'),
        P100=dict(pmcid='PMC12695644',doi='10.1038/s41588-025-02407-8',primary_anchor=49,declared_package='CellChat2.1.2',author_commit=receipt['commit'],conditions=['CTRL','MASL','MASH'],database='CellChatDB.human: Secreted Signaling',sample_labels_retained=True,smoothing='smoothData with PPI.human',raw_use=False,population_size=False,filterCommunication_called=False,communication_exports_for_FlowSig=3,author_install_version_pinned=False,author_code_executed=False,raw_author_code_redistributed=False,FlowSig_completed_by_this_checkpoint=False,source_files=receipt['files']),
        retained_attempts=dict(early_1_0_missing_helper_guard='Failed before inference; preserved',early_1_0_adjusted_p_column='Original fits completed but native admission failed; no admitted result',early_1_0_multi_category='Original vector-recycling subsetDB executed; superseded by full original DB controls, never treated as defaults',first_reference='Failed and exposed full database versus recycled subset difference; preserved',first_admission_test='Changed an unweighted graph metric not independently audited; corrected regression targets outgoing strength. Offline verification is not producer authentication'))
    save(out/'paper-addendum.json',facts);save(out/'COMPLETED.json',dict(status=facts['status'],addendum=pin(out/'paper-addendum.json'),private_receipt=pin(home/'COMPLETED.json'),raw_code_redistributed=False,source_pin=pin(Path(__file__).resolve())));print(facts['status'])

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source fact checks')
    main()

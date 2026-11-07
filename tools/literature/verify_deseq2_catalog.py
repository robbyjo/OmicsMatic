# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline integrity and scoped-evidence verification; never install or refit data."""
import json,sys
from pathlib import Path
from deseq2_catalog import verify,pin
from catalog_source_integrity import verify_source
from signac_scan_evidence import unpack
ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026'
def main():
    proof=verify(ROOT,DATA);record=json.loads((DATA/'deseq2-catalog-validation.json').read_bytes());assert record['status']=='passed-scoped-DESeq2-catalog-primary-use-browser-and-source-closure' and pin(DATA/'deseq2-catalog-support.zip')==record['archive'];files=unpack(DATA/'deseq2-catalog-support.zip',record)
    for name,wanted in record['source_pins'].items():
        raw=files['current-source/'+name];import hashlib
        assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==wanted;verify_source(ROOT,DATA,name,wanted['sha256'])
    assert record['public_workflow_archive']==proof['archive'] and (record['reviewed_primary_records'],record['preserved_unrelated_tool_objects'],record['focused_browser_checks'],record['broad_browser_checks'],record['source_review_unit_tests'])==(49,386,5,299,51)
    assert record['catalog_counts']==dict(papers=250,tools=387,observations=2444,reviewed_uses=880,supporting_pointers=289,new_scoped_integrations=70,including_existing=73,missing=258)
    for key in ['historical_whole_packages_executed','independent_Java_NB_replay','installed_DLL_source_authentication','quantifier_import_pipeline_available','Linux_R_fits_executed','whole_paper_reproduced','biological_accuracy_certified','released','pushed','deployed','whole_goal_complete']:assert record[key] is False
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_preserved'];print(json.dumps(dict(status='passed-DESeq2-offline-scoped-catalog-verification',catalog_counts=record['catalog_counts'],logical_files=record['logical_files'],scientific_source_hash_waivers=False)))
if __name__=='__main__':
    try:main()
    except Exception as e:
        print('DESEQ2 EVIDENCE FAIL2: '+str(e)+'\nRequired action: preserve evidence, correct the named source/archive mismatch and repeat validation. Do not admit partial results.',file=sys.stderr);sys.exit(2)

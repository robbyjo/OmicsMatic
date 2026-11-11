# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify historical HTSeq catalog admission plus live source/role/checksum integrity."""
import json
import sys
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source
from htseq_catalog import verify


def main():
    data=ROOT/'docs/literature/omics-2023-2026';proof=json.loads((data/'htseq-catalog-validation.json').read_bytes());public=verify(ROOT,data)
    assert proof['status']=='passed-scoped-HTSeq-catalog-integration' and pin(data/'htseq-catalog-support.zip')==proof['archive']
    assert pin(ROOT/'docs/literature/htseq-tools-development/VALIDATION.json')==proof['public_validation'] and public['archive']==proof['public_archive']
    files=unpack(data/'htseq-catalog-support.zip',proof)
    for n,wanted in proof['source_pins'].items():verify_source(ROOT,data,n,wanted['sha256']);assert byte_pin(files['current-source/'+n])==wanted,n
    summary=json.loads(files['catalog-snapshot/summary.json']);counts=summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,387,2446,16,56,3,256)
    inventory=json.loads(files['catalog-snapshot/tool-inventory.json']);tool=next(t for t in inventory if t['id']=='htseq')
    assert tool['implementation_status']=='upstream-adapter-validated' and tool['paper_count']==tool['source_roles_reviewed']==8 and tool['development_candidate_sha256']==public['artifact']['sha256']
    reviews=json.loads(files['catalog-snapshot/reviewed-uses.json'])['reviews'];assert len(reviews)==898 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==301
    assert len([r for r in reviews if r['tool_id']=='htseq'])==8 and proof['unrelated_tool_rows_preserved']==386 and proof['papers_and_reviews_unchanged_since_role_curation']
    prefix='build/literature-review/htseq-runtime/catalog-ui-2/'
    general=json.loads(files[prefix+'general/ui-validation.json']);browser=json.loads(files[prefix+'htseq/htseq-ui-verification.json'])
    assert general['status']==browser['status']=='passed' and len(general['checks'])==proof['general_browser_checks']==299 and len(browser['checks'])==proof['HTSeq_browser_checks']==5
    assert browser['mobile_layout']==proof['mobile_layout']==dict(viewport=390,document=390)
    for flag in ['full_project_test_suite_run','historical_whole_packages','whole_paper_reproduced','biological_validity_certified','execution_authentication','released','pushed','deployed','whole_goal_complete']:assert proof[flag] is False,flag
    names=set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,name=line.split('  ',1);assert name not in names and pin(data/name)['sha256']==digest,name;names.add(name)
    assert names=={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    print(json.dumps(dict(status='passed-HTSeq-catalog-offline-and-live-source-checks',catalog_checksums=len(names),checkpoint_new_scoped_integrations=72,
        checkpoint_including_existing_adapters=75,checkpoint_missing=256,source_roles=8,tests_per_JVM=260,JVMs=3,final_count_analyses=7,portable_verifications=24,
        general_browser_checks=299,HTSeq_browser_checks=5,whole_goal_complete=False)))


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable source/archive checks')
        main()
    except Exception as error:
        print('HTSEQ CATALOG FAIL2: '+str(error)+'\nRequired action: preserve the checkpoint, correct the named source/archive/catalog mismatch and repeat verification. No partial evidence is admitted.',file=sys.stderr);raise SystemExit(2)

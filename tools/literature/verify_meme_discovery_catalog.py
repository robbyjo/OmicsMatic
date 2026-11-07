# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify scoped classic MEME discovery catalog evidence and current source/checksum integrity."""
import json
import sys
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source
from meme_discovery_catalog import verify


def main():
    data=ROOT/'docs/literature/omics-2023-2026';proof=json.loads((data/'meme-discovery-catalog-validation.json').read_bytes());public=verify(ROOT,data)
    assert proof['status']=='passed-scoped-MEME-discovery-catalog-integration' and pin(data/'meme-discovery-catalog-support.zip')==proof['archive']
    assert pin(ROOT/'docs/literature/meme-discovery-workflow-development/VALIDATION.json')==proof['public_validation'] and public['archive']==proof['public_archive']
    files=unpack(data/'meme-discovery-catalog-support.zip',proof)
    for name,wanted in proof['source_pins'].items():
        verify_source(ROOT,data,name,wanted['sha256']);assert byte_pin(files['current-source/'+name])==wanted,name
    summary=json.loads(files['catalog-snapshot/summary.json']);counts=summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,387,2446,16,57,3,255)
    inventory=json.loads(files['catalog-snapshot/tool-inventory.json']);tool=next(t for t in inventory if t['id']=='meme')
    assert tool['implementation_status']=='upstream-adapter-validated' and tool['source_roles_reviewed']==tool['paper_count']==8
    assert tool['development_candidate_sha256']==public['artifact']['sha256']
    reviews=json.loads(files['catalog-snapshot/reviewed-uses.json'])['reviews']
    assert len(reviews)==898 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==301
    assert proof['unrelated_tool_rows_preserved']==386 and proof['papers_and_reviews_unchanged']
    prefix='build/literature-review/meme-runtime/discovery-catalog-ui-2/'
    general=json.loads(files[prefix+'general/ui-validation.json']);scoped=json.loads(files[prefix+'meme/meme-discovery-ui-verification.json'])
    assert general['status']==scoped['status']=='passed' and len(general['checks'])==299 and len(scoped['checks'])==5
    assert scoped['mobile_layout']==dict(viewport=390,document=390)
    for name in ['full_project_test_suite_run','historical_whole_suite','whole_paper_reproduced','biological_validity_certified','execution_authentication','released','pushed','deployed','whole_goal_complete']:
        assert proof[name] is False,name
    names=set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,name=line.split('  ',1);assert name not in names and pin(data/name)['sha256']==digest,name;names.add(name)
    assert names=={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    print(json.dumps(dict(status='passed-MEME-discovery-catalog-offline-and-live-source-checks',catalog_checksums=len(names),checkpoint_new_scoped_integrations=73,
        checkpoint_including_existing_adapters=76,checkpoint_missing=255,source_roles=8,tests_per_JVM=91,JVMs=3,whole_goal_complete=False)))


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable source/archive/catalog checks')
        main()
    except Exception as error:
        print('MEME CATALOG FAIL2: '+str(error)+'\nREQUIRED ACTION: preserve the checkpoint, correct the named source/archive/catalog mismatch and repeat verification. No partial evidence is admitted.',file=sys.stderr)
        raise SystemExit(2)

# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the scoped Signac catalog checkpoint and live source correspondence."""
import json
import sys
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source
from catalog_fixed_cohort import verify_fixed_cohort
from signac_catalog import verify


def main():
    data=ROOT/'docs/literature/omics-2023-2026'
    proof=json.loads((data/'signac-catalog-validation.json').read_bytes())
    assert proof['schema_version']==1 and proof['status']=='passed-scoped-Signac-catalog-integration'
    public=verify(ROOT,data)
    assert proof['public_validation']==pin(ROOT/'docs/literature/signac-tools-development/VALIDATION.json')
    assert proof['public_archive']==public['archive'] and proof['artifact']==public['artifact']
    assert pin(data/'signac-catalog-support.zip')==proof['archive']
    files=unpack(data/'signac-catalog-support.zip',proof)
    for name,want in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==want
        verify_source(ROOT,data,name,want['sha256'])
    summary=json.loads(files['catalog-snapshot/summary.json'])
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,387,2445)
    counts=summary['implementation_status_counts']
    assert counts['native-subset-validated']==16 and counts['upstream-adapter-validated']==55
    assert counts['existing-adapter']==3 and counts['missing']==257 and counts['method-overlap']==47
    assert proof['new_scoped_integrations']==71 and proof['including_existing_adapters']==74
    assert proof['reviewed_uses']==890 and proof['supporting_pointers']==299 and proof['prior_reviews_preserved']==880
    snapshot=json.loads(files['catalog-snapshot/papers.json'])
    verify_fixed_cohort(ROOT,data,snapshot)
    assert sum(len(p['tools']) for p in snapshot)==2445
    inventory=json.loads(files['catalog-snapshot/tool-inventory.json'])
    item=next(t for t in inventory if t['id']=='signac')
    assert item['implementation_status']=='upstream-adapter-validated' and item['paper_count']==item['source_roles_reviewed']==10
    assert item['development_candidate_sha256']==public['artifact']['sha256']
    assert next(t for t in inventory if t['id']=='cellranger')['implementation_status']=='excluded-by-user'
    def obj(name):
        return json.loads(files['build/literature-review/signac-runtime/'+name])
    general=obj('catalog-general-ui-1/ui-validation.json')
    browser=obj('catalog-ui-3/signac-ui-verification.json')
    assert general['status']==browser['status']=='passed'
    assert proof['general_browser_checks']==len(general['checks']) and proof['Signac_browser_checks']==len(browser['checks'])==4
    assert browser['mobile_layout']==proof['mobile_layout']==dict(viewport=390,document=390)
    for key in ['semantic_paper_wide_completeness_review_open','unrelated_paper_observations_preserved','Cell_Ranger_excluded','visual_desktop_and_mobile_review']:
        assert proof[key] is True
    for key in ['full_project_test_suite_run','historical_whole_packages','whole_paper_reproduced',
        'biological_validity_certified','released','pushed','deployed','whole_goal_complete']:
        assert proof[key] is False
    # The snapshot remains historically fixed; future unrelated catalog work may
    # advance counts. Live Signac source/role bindings are checked above.
    manifest=data/'SHA256SUMS'
    names=set()
    for line in manifest.read_text(encoding='utf8').splitlines():
        digest,name=line.split('  ',1)
        assert name not in names and pin(data/name)['sha256']==digest,name
        names.add(name)
    assert names=={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    print(json.dumps(dict(status='passed-Signac-catalog-offline-and-live-source-checks',catalog_checksums=len(names),
        checkpoint_new_scoped_integrations=71,checkpoint_including_existing_adapters=74,checkpoint_missing=257,
        source_roles=10,tests_per_JVM=1679,JVMs=3,public_analyses=21,portable_verifications=51,
        general_browser_checks=len(general['checks']),Signac_browser_checks=4,whole_goal_complete=False)))


if __name__=='__main__':
    try:
        if not __debug__:
            raise RuntimeError('Never disable evidence checks')
        main()
    except Exception as failure:
        print('SIGNAC CATALOG FAIL2: '+str(failure)+'\nRequired action: preserve the checkpoint, correct the named source/archive/catalog mismatch and repeat verification. No partial evidence is admitted.',file=sys.stderr)
        raise SystemExit(2)

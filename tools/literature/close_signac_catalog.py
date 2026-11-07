# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the scoped Signac catalog admission and browser checks; publish nothing remotely."""
import gzip
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack
from signac_catalog import verify
from catalog_source_integrity import reverse_delta

BASE=ROOT/'build/literature-review/signac-runtime'
DATA=ROOT/'docs/literature/omics-2023-2026'


def main():
    target=DATA/'signac-catalog-validation.json'
    archive=DATA/'signac-catalog-support.zip'
    assert not target.exists() and not archive.exists()
    public=verify(ROOT,DATA)
    summary=json.loads((DATA/'summary.json').read_bytes())
    counts=summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],
        counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,387,2445,16,55,3,257)
    general=json.loads((BASE/'catalog-general-ui-1/ui-validation.json').read_bytes())
    browser=json.loads((BASE/'catalog-ui-3/signac-ui-verification.json').read_bytes())
    assert general['status']==browser['status']=='passed'
    assert browser['mobile_layout']==dict(viewport=390,document=390)
    reviews=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    bridge=json.loads((DATA/'signac-use-shared-source-compatibility.json').read_bytes())
    previous=json.loads(reverse_delta(reviews,bridge['paths'][0]))['reviews']
    current=json.loads(reviews)['reviews']
    assert len(previous)==880 and len(current)==890 and current[:880]==previous
    files=unpack(ROOT/'docs/literature/signac-tools-development/controls.zip',public)
    old=json.loads(gzip.decompress(files['build/literature-review/signac-runtime/primary-role-curation-applied-1/before-papers.json.gz']))
    papers=json.loads((DATA/'papers.json').read_bytes())
    assert len(old)==len(papers)==250
    for a,b in zip(old,papers):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        assert [t for t in a['tools'] if t['tool_id']!='signac']==[t for t in b['tools'] if t['tool_id']!='signac']
    sources=[ROOT/'tools/literature'/name for name in ['signac_catalog.py','wire_signac_catalog.py',
        'check_signac_review_ui.cjs','close_signac_catalog.py','verify_signac_catalog.py','render_review.py','check_review_ui.cjs']]
    sources += [DATA/'signac-catalog-shared-source-compatibility.json']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    evidence={'current-source/'+name:(ROOT/name).read_bytes() for name in pins}
    for name in ['summary.json','tool-inventory.json','papers.json','catalog.md','implementation-plan.md','WARNINGS.md']:
        evidence['catalog-snapshot/'+name]=(DATA/name).read_bytes()
    for name in ['literature-review.html','literature-warnings.html']:
        evidence['site-snapshot/'+name]=(ROOT/'site'/name).read_bytes()
    evidence['catalog-snapshot/reviewed-uses.json']=reviews
    for name in ['catalog-ui-1','catalog-ui-2','catalog-ui-3','catalog-source-intake-1']:
        for path in (BASE/name).rglob('*'):
            if path.is_file():
                evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['catalog-render-1.log','catalog-render-2.log','catalog-render-3.log',
        'catalog-signac-ui-1.log','catalog-signac-ui-2.log','catalog-signac-ui-3.log',
        'catalog-general-ui-1/ui-validation.json','catalog-general-ui-1.log']:
        path=BASE/name
        evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    layout=pack(archive,evidence)
    result=dict(schema_version=1,status='passed-scoped-Signac-catalog-integration',
        public_validation=pin(ROOT/'docs/literature/signac-tools-development/VALIDATION.json'),
        public_archive=public['archive'],artifact=public['artifact'],source_pins=pins,
        archive=pin(archive),**layout,papers=250,distinct_named_tools=387,paper_tool_observations=2445,
        new_scoped_integrations=71,including_existing_adapters=74,native_integrations=16,upstream_integrations=55,
        missing_integrations=257,method_overlap_tools=47,reviewed_uses=890,supporting_pointers=299,
        source_roles_reviewed=10,prior_reviews_preserved=880,unrelated_paper_observations_preserved=True,
        general_browser_checks=len(general['checks']),Signac_browser_checks=len(browser['checks']),
        mobile_layout=browser['mobile_layout'],visual_desktop_and_mobile_review=True,
        tests_per_JVM=1679,JVMs=3,public_analyses=21,portable_verifications=51,
        expected_runtime_skips=2,expected_failures=2,Cell_Ranger_excluded=True,
        canonical_executable_preserved=public['canonical_executable_preserved'],
        semantic_paper_wide_completeness_review_open=True,full_project_test_suite_run=False,
        historical_whole_packages=False,whole_paper_reproduced=False,biological_validity_certified=False,
        released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in pins.items())
    save(target,result)
    # Only append the two new evidence files. Every existing checksum must first
    # match the completed renderer output; do not conceal a stale catalog file.
    manifest=DATA/'SHA256SUMS'
    lines=manifest.read_text(encoding='utf8').splitlines()
    prior={line.split('  ',1)[1]:line.split('  ',1)[0] for line in lines}
    assert all(pin(DATA/name)['sha256']==digest for name,digest in prior.items())
    assert target.name not in prior and archive.name not in prior
    manifest.write_text('\n'.join(lines)+'\n'+''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in [archive,target]),encoding='utf8',newline='\n')
    print(json.dumps({k:result[k] for k in ['status','new_scoped_integrations','including_existing_adapters','missing_integrations','general_browser_checks','Signac_browser_checks','archive']}))


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable catalog admission checks')
    main()

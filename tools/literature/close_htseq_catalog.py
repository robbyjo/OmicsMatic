# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal scoped HTSeq catalog admission and browser checks; preserve every unrelated tool row."""
import json
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack
from htseq_catalog import verify

BASE=ROOT/'build/literature-review/htseq-runtime';DATA=ROOT/'docs/literature/omics-2023-2026'


def main():
    target=DATA/'htseq-catalog-validation.json';archive=DATA/'htseq-catalog-support.zip';assert not target.exists() and not archive.exists()
    public=verify(ROOT,DATA);summary=json.loads((DATA/'summary.json').read_bytes());counts=summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,387,2446,16,56,3,256)
    general=json.loads((BASE/'catalog-ui-2/general/ui-validation.json').read_bytes());browser=json.loads((BASE/'catalog-ui-2/htseq/htseq-ui-verification.json').read_bytes())
    ui=json.loads((BASE/'catalog-ui-2/COMPLETED.json').read_bytes())
    assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['general']==pin(BASE/'catalog-ui-2/general/ui-validation.json') and ui['HTSeq']==pin(BASE/'catalog-ui-2/htseq/htseq-ui-verification.json')
    assert general['status']==browser['status']=='passed' and len(general['checks'])==299 and len(browser['checks'])==5
    assert browser['mobile_layout']==dict(viewport=390,document=390)
    parent=ROOT/'docs/literature/htseq-read-count-development';record=json.loads((parent/'VALIDATION.json').read_bytes());old_files=unpack(parent/'controls.zip',record)
    old={t['id']:t for t in json.loads(old_files['catalog-snapshot/tool-inventory.json'])};current={t['id']:t for t in json.loads((DATA/'tool-inventory.json').read_bytes())}
    assert old.keys()==current.keys() and len(current)==387
    assert all(old[n]==current[n] for n in old if n!='htseq'),'Unrelated tool metadata changed'
    assert old['htseq']['implementation_status']=='missing' and current['htseq']['implementation_status']=='upstream-adapter-validated'
    for name in ['papers.json','reviewed-uses.json']:
        path=DATA/name if name=='papers.json' else ROOT/'tools/literature'/name
        assert path.read_bytes()==old_files['catalog-snapshot/'+name],name
    sources=[ROOT/'tools/literature'/n for n in ['htseq_catalog.py','wire_htseq_catalog.py','check_htseq_review_ui.cjs','validate_htseq_catalog_ui.py',
        'close_htseq_catalog.py','verify_htseq_catalog.py','render_review.py','check_review_ui.cjs']]
    sources.append(DATA/'htseq-catalog-shared-source-compatibility.json');pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    evidence={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    for n in ['summary.json','tool-inventory.json','papers.json','catalog.md','implementation-plan.md','WARNINGS.md']:evidence['catalog-snapshot/'+n]=(DATA/n).read_bytes()
    evidence['catalog-snapshot/reviewed-uses.json']=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    for n in ['literature-review.html','literature-warnings.html']:evidence['site-snapshot/'+n]=(ROOT/'site'/n).read_bytes()
    for n in ['catalog-source-intake-1','catalog-ui-1','catalog-ui-2','catalog-text-clarity-1']:
        for p in (BASE/n).rglob('*'):
            if p.is_file():evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    for n in ['catalog-render-1.log','catalog-render-2.log','catalog-website-1.log','catalog-website-2.log']:evidence[n]=(BASE/n).read_bytes()
    assert 'BUILD SUCCESSFUL' in evidence['catalog-website-2.log'].decode('utf8')
    layout=pack(archive,evidence)
    proof=dict(schema_version=1,status='passed-scoped-HTSeq-catalog-integration',public_validation=pin(ROOT/'docs/literature/htseq-tools-development/VALIDATION.json'),
        public_archive=public['archive'],artifact=public['artifact'],source_pins=pins,archive=pin(archive),**layout,
        papers=250,distinct_named_tools=387,paper_tool_observations=2446,new_scoped_integrations=72,including_existing_adapters=75,native_integrations=16,upstream_integrations=56,
        missing_integrations=256,method_overlap_tools=47,reviewed_uses=898,supporting_pointers=301,source_roles_reviewed=8,prior_reviews_preserved=890,
        unrelated_tool_rows_preserved=386,papers_and_reviews_unchanged_since_role_curation=True,general_browser_checks=299,HTSeq_browser_checks=5,mobile_layout=browser['mobile_layout'],
        visual_desktop_and_mobile_review=True,tests_per_JVM=260,JVMs=3,final_count_analyses=7,final_positive_portable_verifications=24,
        expected_missing_runtime_skips=3,consistency_refusals=33,source_or_commit_refusals=4,Cell_Ranger_excluded=True,
        semantic_paper_wide_completeness_review_open=True,full_project_test_suite_run=False,historical_whole_packages=False,whole_paper_reproduced=False,
        biological_validity_certified=False,execution_authentication=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(target,proof)
    manifest=DATA/'SHA256SUMS';lines=manifest.read_text(encoding='utf8').splitlines();prior={line.split('  ',1)[1]:line.split('  ',1)[0] for line in lines}
    assert all(pin(DATA/n)['sha256']==h for n,h in prior.items()) and archive.name not in prior and target.name not in prior
    manifest.write_text('\n'.join(lines)+'\n'+''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in [archive,target]),encoding='utf8',newline='\n')
    print(json.dumps({k:proof[k] for k in ['status','new_scoped_integrations','including_existing_adapters','missing_integrations','general_browser_checks','HTSeq_browser_checks','archive']}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog admission checks')
    main()

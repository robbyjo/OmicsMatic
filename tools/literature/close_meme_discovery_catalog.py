# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal one scoped MEME discovery integration after workflow, primary roles and local UI pass."""
import json
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from meme_discovery_catalog import verify


def main():
    data=ROOT/'docs/literature/omics-2023-2026';base=ROOT/'build/literature-review/meme-runtime'
    target=data/'meme-discovery-catalog-validation.json';archive=data/'meme-discovery-catalog-support.zip'
    assert not target.exists() and not archive.exists();public=verify(ROOT,data)
    summary=json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,387,2446,16,57,3,255)
    intake=base/'discovery-catalog-source-intake-1'
    old={t['id']:t for t in json.loads((intake/'tool-inventory.json').read_bytes())}
    current={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())}
    assert old.keys()==current.keys() and len(current)==387 and all(old[n]==current[n] for n in old if n!='meme')
    assert old['meme']['implementation_status']=='missing' and current['meme']['implementation_status']=='upstream-adapter-validated'
    assert (data/'papers.json').read_bytes()==(intake/'papers.json').read_bytes()
    assert (ROOT/'tools/literature/reviewed-uses.json').read_bytes()==(intake/'reviewed-uses.json').read_bytes()
    ui=base/'discovery-catalog-ui-2';general=json.loads((ui/'general/ui-validation.json').read_bytes());scoped=json.loads((ui/'meme/meme-discovery-ui-verification.json').read_bytes())
    check=json.loads((ui/'COMPLETED.json').read_bytes())
    assert check['page']==pin(ROOT/'site/literature-review.html') and check['general']==pin(ui/'general/ui-validation.json') and check['MEME']==pin(ui/'meme/meme-discovery-ui-verification.json')
    assert general['status']==scoped['status']=='passed' and len(general['checks'])==299 and len(scoped['checks'])==5
    assert scoped['mobile_layout']==dict(viewport=390,document=390)
    visuals=base/'discovery-catalog-visuals-1';visual=json.loads((visuals/'COMPLETED.json').read_bytes());assert visual['page']==check['page'] and visual['source_page_modified'] is False
    source_names=['meme_discovery_catalog.py','wire_meme_discovery_catalog.py','close_meme_discovery_catalog.py','verify_meme_discovery_catalog.py',
        'check_meme_discovery_review_ui.cjs','validate_meme_discovery_catalog_ui.py','capture_meme_discovery_visuals.py','render_review.py','check_review_ui.cjs']
    paths=[ROOT/'tools/literature'/n for n in source_names]+[data/'meme-discovery-catalog-shared-source-compatibility.json']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
    files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    for name in ['summary.json','tool-inventory.json','papers.json','catalog.md','implementation-plan.md','WARNINGS.md']:
        files['catalog-snapshot/'+name]=(data/name).read_bytes()
    files['catalog-snapshot/reviewed-uses.json']=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    for name in ['literature-review.html','literature-warnings.html']:files['site-snapshot/'+name]=(ROOT/'site'/name).read_bytes()
    for folder in [intake,ui,visuals,base/'discovery-catalog-ui-1',base/'meme-ui-locator-fix-intake-1']:
        for path in folder.rglob('*'):
            if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    layout=pack(archive,files)
    proof=dict(schema_version=1,status='passed-scoped-MEME-discovery-catalog-integration',public_validation=pin(ROOT/'docs/literature/meme-discovery-workflow-development/VALIDATION.json'),
        public_archive=public['archive'],artifact=public['artifact'],source_pins=pins,archive=pin(archive),**layout,
        papers=250,distinct_named_tools=387,paper_tool_observations=2446,new_scoped_integrations=73,including_existing_adapters=76,native_integrations=16,upstream_integrations=57,
        missing_integrations=255,method_overlap_tools=47,reviewed_uses=898,supporting_pointers=301,source_roles_reviewed=8,
        unrelated_tool_rows_preserved=386,papers_and_reviews_unchanged=True,general_browser_checks=299,MEME_browser_checks=5,mobile_layout=scoped['mobile_layout'],
        visual_desktop_and_mobile_review=True,tests_per_JVM=91,JVMs=3,positive_original_analyses=5,empty_original_analyses=4,workflow_portable_verifications=27,
        Cell_Ranger_excluded=True,semantic_paper_wide_completeness_review_open=True,full_project_test_suite_run=False,historical_whole_suite=False,
        whole_paper_reproduced=False,biological_validity_certified=False,execution_authentication=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(target,proof)
    manifest=data/'SHA256SUMS';lines=manifest.read_text(encoding='utf8').splitlines();prior={line.split('  ',1)[1]:line.split('  ',1)[0] for line in lines}
    assert all(pin(data/n)['sha256']==h for n,h in prior.items()) and archive.name not in prior and target.name not in prior
    manifest.write_text('\n'.join(lines)+'\n'+''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in [archive,target]),encoding='utf8',newline='\n')
    print(json.dumps({k:proof[k] for k in ['status','new_scoped_integrations','including_existing_adapters','missing_integrations','archive']}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog, source or browser checks')
    main()

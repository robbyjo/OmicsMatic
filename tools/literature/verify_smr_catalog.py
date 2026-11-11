# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify current250-paper catalog, exact932+12 review append and two availability updates."""
import json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from smr_catalog import verify_review_extension,load_catalog_registry,smr_metadata


def verify():
    data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((data/'smr-catalog-validation.json').read_bytes());assert p['status']=='passed-twelve-SMR-HEIDI-primary-roles-and-two-scoped-integrations' and not p['whole_goal_complete'];assert pin(data/'smr-catalog-support.zip')==p['archive'];files=unpack(data/'smr-catalog-support.zip',p)
    for name,value in p['source_pins'].items():assert pin(ROOT/name)==value and byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda n:json.loads(files[n]);papers=get('catalog-snapshot/papers.json');assert papers==json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);assert len(papers)==250 and sum(len(row['tools']) for row in papers)==2450
    reviews=get('catalog-snapshot/reviewed-uses.json');assert (ROOT/'tools/literature/reviewed-uses.json').read_bytes()==files['catalog-snapshot/reviewed-uses.json'];verify_review_extension(ROOT,data,reviews['reviews']);assert len(load_catalog_registry(ROOT))==428 and len(reviews['reviews'])==944
    base='build/literature-review/smr-runtime/';prior={t['id']:t for t in get(base+'catalog-render-bootstrap-1/before-tool-inventory.json')};current={t['id']:t for t in get('catalog-snapshot/tool-inventory.json')};assert len(prior)==len(current)==390 and set(k for k in prior if prior[k]!=current[k])=={'smr','heidi'}
    for tool in ['smr','heidi']:
        assert prior[tool]['implementation_status']=='missing';expected=smr_metadata(ROOT,data,tool);assert all(current[tool][k]==v for k,v in expected.items())
    assert current=={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};summary=get('catalog-snapshot/summary.json');assert summary==json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(250,390,2450,17,63,251);assert (p['new_scoped_integrations'],p['including_existing_adapters'],p['missing_integrations'])==(80,83,251)
    audit=get('catalog-snapshot/smr-source-audit.json');assert audit['papers_pin']==byte_pin(files['catalog-snapshot/papers.json']) and audit['reviews_pin']==byte_pin(files['catalog-snapshot/reviewed-uses.json']) and (audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(2450,944,327)
    for name,value in audit['source_pins'].items():assert pin(ROOT/name)==value
    gates=get(base+'catalog-scientific-gates-1/COMPLETED.json');assert gates['status']=='passed-SMR-and-frozen-scientific-current-gates' and len(gates['checks'])==7 and gates['checks']['verify_smr_review_context']['current_reviews']==944
    for name,value in gates['source_pins'].items():assert pin(ROOT/name)==value
    ui=get(base+'catalog-ui-2/COMPLETED.json');assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html');general=get(base+'catalog-ui-2/general/ui-validation.json');focus=get(base+'catalog-ui-2/smr/smr-ui-verification.json');assert general['status']=='passed' and len(general['checks'])==299 and focus['status']=='passed' and len(focus['checks'])==5 and focus['document_width']==390 and not focus['page_errors'];assert ui['general']==byte_pin(files[base+'catalog-ui-2/general/ui-validation.json']) and ui['smr']==byte_pin(files[base+'catalog-ui-2/smr/smr-ui-verification.json']);assert get(base+'catalog-ui-2/visual-inspection.json')['desktop_card_and_mobile_warning_reviewed']
    for name,value in ui['source_pins'].items():assert pin(ROOT/name)==value
    reused=ui['reused_general'];assert reused['same_page_bytes'];receipt=get(reused['completion_path']);assert byte_pin(files[reused['completion_path']])==reused['completion'] and receipt['status']=='passed-general-only-from-retained-failed-focused-run' and not receipt['focused_run_passed'];assert receipt['general']==ui['general'] and receipt['page']==ui['page'] and receipt['warning_page']==ui['warning_page'];assert not get(base+'catalog-ui-1/FAILURE.json')['focused_run_passed']
    for name,value in receipt['source_pins'].items():assert pin(ROOT/name)==value
    for name,value in receipt['input_pins'].items():assert pin(ROOT/name)==value
    for name in ['papers.json','tool-inventory.json','summary.json','catalog.md','implementation-plan.md','README.md','WARNINGS.md','smr-role-coverage.json','smr-source-audit.json','smr-prior-reviews.json.gz','smr-prior-papers.json.gz','smr-root-readme-evolution.json']:assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    for name in ['smr-reviews-shared-source-compatibility.json','smr-render-shared-source-compatibility.json']:
        for row in get('catalog-snapshot/'+name)['paths']:
            raw=files['current-source/'+row['path']];assert byte_pin(raw)['sha256']==row['after_sha256'];reverse_delta(raw,row)
    workflow=json.loads((ROOT/'docs/literature/smr-workflow-development/VALIDATION.json').read_bytes());assert workflow['adapter_admitted'] and workflow['artifact']==p['artifact'] and workflow['complete_original_case_comparisons']==63;assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text(encoding='utf8')
    names=set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name not in names and pin(data/name)['sha256']==sha;names.add(name)
    assert names=={f.name for f in data.iterdir() if f.is_file() and f.name!='SHA256SUMS'};print('Verified250 papers,390 tools,2450 observations,944 reviews;80 new scoped integrations,251 missing;63 complete original SMR/HEIDI comparisons and current scientific/browser gates');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog/scientific evidence checks')
    verify()

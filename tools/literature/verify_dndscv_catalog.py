# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify current250-paper catalog, exact925+7 review append and one dndscv availability update."""
import gzip,json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from dndscv_catalog import verify_review_extension,load_catalog_registry,dndscv_metadata


def verify():
    data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((data/'dndscv-catalog-validation.json').read_bytes());assert p['status']=='passed-seven-dndscv-primary-roles-and-scoped-integration' and not p['whole_goal_complete'];assert pin(data/'dndscv-catalog-support.zip')==p['archive'];files=unpack(data/'dndscv-catalog-support.zip',p)
    for name,value in p['source_pins'].items():verify_source(ROOT,data,name,value['sha256']);assert byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda n:json.loads(files[n]);papers=get('catalog-snapshot/papers.json');assert papers==json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);oldpapers=json.loads(gzip.decompress(files['catalog-snapshot/dndscv-prior-papers.json.gz']));assert len(papers)==len(oldpapers)==250;changed=[]
    for before,after in zip(oldpapers,papers):
        assert {k:v for k,v in before.items() if k!='tools'}=={k:v for k,v in after.items() if k!='tools'} and [t for t in before['tools'] if t['tool_id']!='dndscv']==[t for t in after['tools'] if t['tool_id']!='dndscv']
        if before!=after:changed.append(after['paper_id'])
    assert changed==['P036','P042','P056','P111','P146','P148','P165'] and sum(len(p['tools']) for p in papers)==sum(len(p['tools']) for p in oldpapers)==2450;reviews=get('catalog-snapshot/reviewed-uses.json');assert (ROOT/'tools/literature/reviewed-uses.json').read_bytes()==files['catalog-snapshot/reviewed-uses.json'];verify_review_extension(ROOT,data,reviews['reviews']);assert len(load_catalog_registry(ROOT))==428;oldreviews=json.loads(gzip.decompress(files['catalog-snapshot/dndscv-prior-reviews.json.gz']));assert reviews['reviews'][:925]==oldreviews['reviews'] and len(reviews['reviews'])==932
    base='build/literature-review/dndscv-runtime/';prior={t['id']:t for t in get(base+'catalog-render-bootstrap-1/before-tool-inventory.json')};current={t['id']:t for t in get('catalog-snapshot/tool-inventory.json')};assert len(prior)==len(current)==390 and [k for k in prior if prior[k]!=current[k]]==['dndscv'];assert prior['dndscv']['implementation_status']=='missing';expected=dndscv_metadata(ROOT,data);assert all(current['dndscv'][k]==v for k,v in expected.items());assert current=={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};summary=get('catalog-snapshot/summary.json');assert summary==json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(250,390,2450,17,61,253);assert p['new_scoped_integrations']==78 and p['including_existing_adapters']==81
    audit=get('catalog-snapshot/dndscv-source-audit.json');assert audit['papers_pin']==byte_pin(files['catalog-snapshot/papers.json']) and audit['reviews_pin']==byte_pin(files['catalog-snapshot/reviewed-uses.json']) and (audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(2450,932,322);gates=get(base+'catalog-scientific-gates-1/COMPLETED.json');assert gates['status']=='passed-dndscv-and-frozen-scientific-current-gates' and len(gates['checks'])==5 and gates['checks']['verify_dndscv_review_context']['current_reviews']==932;linked=get(base+'catalog-linked-role-details-1/COMPLETED.json');assert linked['applied'] and linked['linked_unnamed_paragraphs']==5 and linked['supporting_paragraphs']==11
    ui=get(base+'catalog-ui-1/COMPLETED.json');assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html');general=get(base+'catalog-ui-1/general/ui-validation.json');focus=get(base+'catalog-ui-1/dndscv/dndscv-ui-verification.json');assert general['status']=='passed' and len(general['checks'])==299 and focus['status']=='passed' and len(focus['checks'])==5 and focus['document_width']==390 and not focus['page_errors'];assert ui['general']==byte_pin(files[base+'catalog-ui-1/general/ui-validation.json']) and ui['dndscv']==byte_pin(files[base+'catalog-ui-1/dndscv/dndscv-ui-verification.json']);visual=get(base+'catalog-ui-1/visual-inspection.json');assert visual['desktop_card_and_mobile_warning_reviewed'] and not visual['source_page_modified']
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','dndscv-role-coverage.json','dndscv-source-audit.json']:assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    for name in ['dndscv-reviews-shared-source-compatibility.json','dndscv-render-shared-source-compatibility.json']:
        for row in get('catalog-snapshot/'+name)['paths']:
            raw=files['current-source/'+row['path']];assert byte_pin(raw)['sha256']==row['after_sha256'];reverse_delta(raw,row)
    workflow=json.loads((ROOT/'docs/literature/dndscv-workflow-development/VALIDATION.json').read_bytes());assert workflow['adapter_admitted'] and workflow['artifact']==p['artifact'] and workflow['runtime_protocols']==workflow['complete_original_case_comparisons']==24;assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text(encoding='utf8');names=set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name not in names and pin(data/name)['sha256']==sha;names.add(name)
    assert names=={f.name for f in data.iterdir() if f.is_file() and f.name!='SHA256SUMS'};print('Verified current catalog:250 papers,390 tools,2450 observations,932 reviews;78 new scoped integrations,253 missing;24 complete dndscv original comparisons and strict scientific/browser gates');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/catalog/scientific evidence assertions')
    verify()

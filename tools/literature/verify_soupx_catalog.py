# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the current250-paper catalog, exact918+7 review append and one SoupX availability update."""
import gzip,json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from soupx_catalog import verify_review_extension,load_catalog_registry,soupx_metadata


def verify():
    data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((data/'soupx-catalog-validation.json').read_bytes());assert p['status']=='passed-seven-SoupX-primary-roles-and-scoped-integration' and not p['whole_goal_complete'];assert pin(data/'soupx-catalog-support.zip')==p['archive'];files=unpack(data/'soupx-catalog-support.zip',p)
    for name,value in p['source_pins'].items():verify_source(ROOT,data,name,value['sha256']);assert byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda n:json.loads(files[n]);papers=get('catalog-snapshot/papers.json');assert papers==json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);oldpapers=json.loads(gzip.decompress(files['catalog-snapshot/soupx-prior-papers.json.gz']));assert len(papers)==len(oldpapers)==250;changed=[]
    for before,after in zip(oldpapers,papers):
        assert {k:v for k,v in before.items() if k!='tools'}=={k:v for k,v in after.items() if k!='tools'} and [t for t in before['tools'] if t['tool_id']!='soupx']==[t for t in after['tools'] if t['tool_id']!='soupx']
        if before!=after:changed.append(after['paper_id'])
    assert changed==['P008','P087','P104','P145','P182','P225','P233'] and sum(len(p['tools']) for p in papers)==sum(len(p['tools']) for p in oldpapers)==2450
    reviews=get('catalog-snapshot/reviewed-uses.json');assert (ROOT/'tools/literature/reviewed-uses.json').read_bytes()==files['catalog-snapshot/reviewed-uses.json'];verify_review_extension(ROOT,data,reviews['reviews']);assert len(load_catalog_registry(ROOT))==428;oldreviews=json.loads(gzip.decompress(files['catalog-snapshot/soupx-prior-reviews.json.gz']));assert reviews['reviews'][:918]==oldreviews['reviews'] and len(reviews['reviews'])==925
    base='build/literature-review/soupx-runtime/';prior={t['id']:t for t in get(base+'catalog-render-bootstrap-1/before-tool-inventory.json')};current={t['id']:t for t in get('catalog-snapshot/tool-inventory.json')};assert len(prior)==len(current)==390 and [k for k in prior if prior[k]!=current[k]]==['soupx'];assert prior['soupx']['implementation_status']=='missing';expected=soupx_metadata(ROOT,data);assert all(current['soupx'][k]==v for k,v in expected.items());assert current=={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};summary=get('catalog-snapshot/summary.json');assert summary==json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(250,390,2450,17,60,254);assert p['new_scoped_integrations']==77 and p['including_existing_adapters']==80
    audit=get('catalog-snapshot/soupx-source-audit.json');assert audit['papers_pin']==byte_pin(files['catalog-snapshot/papers.json']) and audit['reviews_pin']==byte_pin(files['catalog-snapshot/reviewed-uses.json']) and (audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(2450,925,311);gates=get(base+'catalog-scientific-gates-3/COMPLETED.json');assert gates['status']=='passed-SoupX-and-frozen-scientific-current-gates' and len(gates['checks'])==4 and gates['checks']['verify_soupx_review_context']['current_reviews']==925
    ui=get(base+'catalog-ui-4/COMPLETED.json');assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html');general=get(base+'catalog-ui-4/general/ui-validation.json');focus=get(base+'catalog-ui-4/soupx/soupx-ui-verification.json');assert general['status']=='passed' and len(general['checks'])==299 and focus['status']=='passed' and len(focus['checks'])==5 and focus['document_width']==390 and not focus['page_errors'];assert ui['general']==byte_pin(files[base+'catalog-ui-4/general/ui-validation.json']) and ui['soupx']==byte_pin(files[base+'catalog-ui-4/soupx/soupx-ui-verification.json']);visual=get(base+'catalog-ui-4/visual-inspection.json');assert visual['desktop_card_and_mobile_warning_reviewed'] and not visual['source_page_modified']
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','soupx-role-coverage.json','soupx-source-audit.json']:assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    for name in ['soupx-reviews-shared-source-compatibility.json','soupx-render-shared-source-compatibility.json','soupx-render-final-shared-source-compatibility.json']:
        delta=get('catalog-snapshot/'+name)
        for row in delta['paths']:
            raw=files['current-source/'+row['path']]
            if byte_pin(raw)['sha256']==row['after_sha256']:reverse_delta(raw,row)
            else:verify_source(ROOT,data,row['path'],row['before_sha256'])
    workflow=json.loads((ROOT/'docs/literature/soupx-workflow-development/VALIDATION.json').read_bytes());assert workflow['adapter_admitted'] and workflow['artifact']==p['artifact'];wf=unpack(ROOT/'docs/literature/soupx-workflow-development/controls.zip',workflow);reports={k:json.loads(wf[base+n+'/COMPLETED.json']) for k,n in workflow['final_attempts'].items()};assert sum(len(r.get('reference_comparisons',[])) for r in reports['runtime']['protocols'])==38 and sum(len(r['corrected_entries_compared']) for r in reports['platform']['protocols'] if r['result_admitted'])==4 and len(reports['literal']['protocols'])==2 and reports['example']['corrected_coordinates_compared']==3422
    assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text(encoding='utf8');names=set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name not in names and pin(data/name)['sha256']==sha;names.add(name)
    assert names=={f.name for f in data.iterdir() if f.is_file() and f.name!='SHA256SUMS'};print('Verified current catalog:250 papers,390 tools,2450 observations,925 reviews;77 new scoped integrations,254 missing;45 exact SoupX corrected matrices and frozen scientific/browser gates');return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/catalog/scientific evidence assertions')
    verify()

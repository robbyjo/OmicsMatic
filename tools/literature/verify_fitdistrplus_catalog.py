# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify exact one-role append, two scoped availability rows and preserved scientific evidence."""
import gzip,json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from fitdistrplus_catalog import verify_review_extension,load_catalog_registry,normal_fit_metadata,cicero_metadata


def verify():
    data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((data/'fitdistrplus-catalog-validation.json').read_bytes());assert p['status']=='passed-fitdistrplus-primary-role-and-two-scoped-integrations'
    assert pin(data/'fitdistrplus-catalog-support.zip')==p['archive'];files=unpack(data/'fitdistrplus-catalog-support.zip',p)
    for name,value in p['source_pins'].items():verify_source(ROOT,data,name,value['sha256']);assert byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda name:json.loads(files[name]);papers=get('catalog-snapshot/papers.json');assert papers==json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers)
    oldpapers=json.loads(gzip.decompress(files['catalog-snapshot/fitdistrplus-prior-papers.json.gz']));assert len(papers)==len(oldpapers)==250
    for before,after in zip(oldpapers,papers):
        assert {k:v for k,v in before.items() if k!='tools'}=={k:v for k,v in after.items() if k!='tools'} and before['tools']==[t for t in after['tools'] if t['tool_id']!='fitdistrplus']
    assert sum(len(p['tools']) for p in papers)==2450 and sum(len(p['tools']) for p in oldpapers)==2449
    reviews=get('catalog-snapshot/reviewed-uses.json');assert (ROOT/'tools/literature/reviewed-uses.json').read_bytes()==files['catalog-snapshot/reviewed-uses.json'];verify_review_extension(ROOT,data,reviews['reviews']);assert len(load_catalog_registry(ROOT))==428
    oldreviews=json.loads(gzip.decompress(files['catalog-snapshot/fitdistrplus-prior-reviews.json.gz']));assert reviews['reviews'][:917]==oldreviews['reviews'] and len(reviews['reviews'])==918
    prior={t['id']:t for t in get('build/literature-review/fitdistrplus-runtime/catalog-render-bootstrap-1/before-tool-inventory.json')};current={t['id']:t for t in get('catalog-snapshot/tool-inventory.json')};assert len(prior)==389 and len(current)==390 and set(current)-set(prior)=={'fitdistrplus'}
    assert [k for k in prior if prior[k]!=current[k]]==['cicero'];assert prior['cicero']['implementation_status']=='missing'
    for name,function in [('fitdistrplus',normal_fit_metadata),('cicero',cicero_metadata)]:
        expected=function(ROOT,data);assert all(current[name][k]==v for k,v in expected.items())
    assert current=={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())}
    summary=get('catalog-snapshot/summary.json');assert summary==json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(250,390,2450,17,59,255)
    assert p['new_scoped_integrations']==76 and p['including_existing_adapters']==79 and p['unrelated_tool_rows_preserved']==388
    audit=get('catalog-snapshot/fitdistrplus-source-audit.json');assert audit['papers_pin']==byte_pin(files['catalog-snapshot/papers.json']) and audit['reviews_pin']==byte_pin(files['catalog-snapshot/reviewed-uses.json']) and audit['observations_verified']==2450 and audit['reviewed_uses_verified']==918
    base='build/literature-review/fitdistrplus-runtime/';gates=get(base+'catalog-scientific-gates-1/COMPLETED.json');assert gates['status']=='passed-normal-Cicero-and-frozen-scientific-current-gates' and len(gates['checks'])==4 and gates['checks']['verify_fitdistrplus_review_context']['current_reviews']==918
    ui=get(base+'catalog-ui-2/COMPLETED.json');assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html')
    assert ui['general']==byte_pin(files[base+'catalog-ui-2/general/ui-validation.json']) and ui['normal']==byte_pin(files[base+'catalog-ui-2/normal/fitdistrplus-ui-verification.json'])
    general=get(base+'catalog-ui-2/general/ui-validation.json');focus=get(base+'catalog-ui-2/normal/fitdistrplus-ui-verification.json');assert general['status']=='passed' and len(general['checks'])==299 and focus['status']=='passed' and len(focus['checks'])==6 and focus['document_width']==390 and not focus['page_errors']
    visual=get(base+'catalog-ui-2/visual-inspection.json');assert visual['desktop_card_and_mobile_warning_reviewed'] and visual['source_page_modified'] is False
    assert base+'catalog-ui-1/DIAGNOSIS.txt' in files and base+'catalog-curation-1/RENDER-DIAGNOSIS.txt' in files
    for name in ['papers.json','tool-inventory.json','summary.json','fitdistrplus-role-coverage.json','fitdistrplus-source-audit.json','README.md','WARNINGS.md']:
        assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    for name in ['fitdistrplus-render-shared-source-compatibility.json','fitdistrplus-reviews-shared-source-compatibility.json','fitdistrplus-browser-shared-source-compatibility.json']:
        delta=get('catalog-snapshot/'+name)
        for row in delta['paths']:reverse_delta(files['current-source/'+row['path']],row)
    old=json.loads((data/'monocle-catalog-validation.json').read_bytes());historical=unpack(data/'monocle-catalog-support.zip',old);assert pin(data/'monocle-catalog-support.zip')==old['archive']
    for name,value in old['source_pins'].items():verify_source(ROOT,data,name,value['sha256']);assert byte_pin(historical['current-source/'+name])==value
    for key in ['native_normal','original_Cicero','native_prediction']:
        assert p['components'][key]['admitted'] and p['components'][key]['canonical_executable_unchanged']==p['canonical_executable_unchanged']
    page=(ROOT/'.github/workflows/pages.yml').read_text(encoding='utf8');assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in page
    names=set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name not in names and pin(data/name)['sha256']==sha;names.add(name)
    assert names=={f.name for f in data.iterdir() if f.is_file() and f.name!='SHA256SUMS'}
    assert not p['whole_goal_complete'];print('Verified current catalog:250 papers,390 tools,2450 observations,918 reviews;76 scoped integrations,255 missing; frozen scientific gates and exact browser evidence preserved')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/catalog/scientific evidence assertions')
    verify()

# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify one hifiasm availability row and seven exact primary roles; historical scientific sources remain strict."""
import gzip,json,re
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import ALLOWED_PATHS,reverse_delta,verify_source

DATA=ROOT/'docs/literature/omics-2023-2026'
BASE='build/literature-review/hifiasm-runtime/'
STATUS='passed-one-hifiasm-workflow-and-seven-primary-roles-catalog-integration'


def verify(emit=True,folder=None,check_manifest=True):
    folder=DATA if folder is None else Path(folder);p=json.loads((folder/'hifiasm-catalog-validation.json').read_bytes());assert p['status']==STATUS and (p['new_scoped_integrations'],p['including_existing_adapters'],p['missing'],p['reviewed_roles'],p['supporting_pointers'])==(89,92,252,1000,347) and p['new_catalog_credit'] and p['availability_rows_changed']==1 and p['paper_roles_changed']==7
    assert not any(p[k] for k in ['scientific_verifiers_changed','scientific_source_exception_added','independent_consensus','independent_phasing','whole_paper_reproduced','whole_goal_complete']);assert pin(folder/'hifiasm-catalog-support.zip')==p['archive'];files=unpack(folder/'hifiasm-catalog-support.zip',p)
    for name,value in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==value,name
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value,name
    for name in p['catalog_snapshot']:
        actual=folder/name if name=='hifiasm-source-audit.json' else DATA/name;assert pin(actual)==byte_pin(files['catalog-snapshot/'+name]),name
    for name in ['site/literature-review.html','site/literature-warnings.html']:assert (ROOT/name).read_bytes()==files['current-output/'+name]
    assert ALLOWED_PATHS==frozenset(['.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/render_review.py','tools/literature/reviewed-uses.json','tools/literature/repeat_catalog.py','tools/literature/check_review_ui.cjs','tools/literature/check_repeat_review_ui.cjs'])
    oldproof=json.loads((DATA/'gseapy-catalog-validation.json').read_bytes());assert pin(DATA/'gseapy-catalog-support.zip')==oldproof['archive'];oldfiles=unpack(DATA/'gseapy-catalog-support.zip',oldproof)
    for name,value in oldproof['source_pins'].items():assert byte_pin(oldfiles['current-source/'+name])==value;verify_source(ROOT,DATA,name,value['sha256'])
    for name,value in oldproof['parent_proofs'].items():assert pin(ROOT/name)==value,name
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']:assert files['catalog-before/'+name]==oldfiles['catalog-snapshot/'+name],name
    assert gzip.decompress((DATA/'hifiasm-prior-reviews.json.gz').read_bytes())==oldfiles['current-source/tools/literature/reviewed-uses.json'];assert gzip.decompress((DATA/'hifiasm-prior-papers.json.gz').read_bytes())==oldfiles['catalog-snapshot/papers.json']
    for descriptor,name in [('hifiasm-render-shared-source-compatibility.json','tools/literature/render_review.py'),('hifiasm-reviews-shared-source-compatibility.json','tools/literature/reviewed-uses.json')]:
        d=json.loads((DATA/descriptor).read_bytes());assert d['status']=='passed-dispatch-catalog-only-source-deltas' and len(d['paths'])==1;row=d['paths'][0];assert row['path']==name;restored=reverse_delta((ROOT/name).read_bytes(),row);assert restored==files[BASE+'catalog-prepare-1/before/'+name]
        if name=='tools/literature/render_review.py':assert byte_pin(restored)==json.loads(oldfiles['build/literature-review/gseapy-runtime/catalog-render-1/COMPLETED.json'])['source_pins'][name]
        else:assert restored==oldfiles['current-source/'+name]
    assert (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==oldfiles['current-source/tools/literature/check_review_ui.cjs'];oldpapers=json.loads(oldfiles['catalog-snapshot/papers.json']);papers=json.loads((DATA/'papers.json').read_bytes());changed=[]
    for old,new in zip(oldpapers,papers,strict=True):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'} and [t['tool_id'] for t in old['tools']]==[t['tool_id'] for t in new['tools']];assert [t for t in old['tools'] if t['tool_id']!='hifiasm']==[t for t in new['tools'] if t['tool_id']!='hifiasm']
        if old!=new:changed.append(new['paper_id'])
    assert changed==['P068','P101','P109','P164','P171','P235','P248'] and len(papers)==250 and sum(len(q['tools']) for q in papers)==2465
    from hifiasm_catalog import current_catalog_review_context,verify_review_extension,load_catalog_registry
    reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    with current_catalog_review_context():verify_review_extension(ROOT,DATA,reviews)
    assert len(load_catalog_registry(ROOT))==438 and len(reviews)==1000 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==347
    old={r['id']:r for r in json.loads(oldfiles['catalog-snapshot/tool-inventory.json'])};new={r['id']:r for r in json.loads((DATA/'tool-inventory.json').read_bytes())};assert len(new)==400 and set(old)==set(new) and all(old[k]==new[k] for k in old if k!='hifiasm') and old['hifiasm']['implementation_status']=='missing';h=new['hifiasm'];assert h['implementation_status']=='upstream-adapter-validated' and h['source_roles_reviewed']==7
    from hifiasm_execution_evidence import verify as workflow
    w=workflow(False);assert w['full_declared_hifiasm_workflow_admitted'] and not w['implementation_credit'];assert h['development_candidate']==w['artifact']['path'] and h['development_candidate_sha256']==w['artifact']['sha256'] and h['development_cli_class']=='org.jlinalg.cli.HifiasmWorkflowCli' and h['runtime_setup_cli_class']=='org.jlinalg.cli.HifiasmSetupCli' and h['runtime_binding_cli_class']=='org.jlinalg.cli.HifiasmRuntimeCli'
    for key in ['canonical_adapter_available','analysis_installs_software','independent_consensus','independent_phasing','whole_package_implemented','whole_paper_reproduced','whole_goal_complete']:assert not h[key]
    summary=json.loads((DATA/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,72,252) and (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,400,2465)
    get=lambda name:json.loads(files[BASE+name]);render=get('catalog-render-1/COMPLETED.json');assert render['status']=='passed-one-hifiasm-availability-catalog-render' and render['unchanged_tool_rows']==399
    audit=json.loads((folder/'hifiasm-source-audit.json').read_bytes());assert audit==get('catalog-source-audit-1/COMPLETED.json') and (audit['source_records'],audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(250,2465,1000,347) and audit['all_non_hifiasm_source_roles_preserved'] and audit['refined_existing_roles']==0;assert audit['papers_pin']==pin(DATA/'papers.json') and audit['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json')
    gates=get('catalog-gates-2/COMPLETED.json');assert gates['status']=='passed-twenty-seven-frozen-and-additive-hifiasm-gates' and len(gates['checks'])==27 and gates['current_reviews']==1000 and gates['prior_metadata_reviews']==980 and not gates['scientific_verifiers_changed'];assert set(gates['checks'])==set(json.loads(oldfiles['build/literature-review/gseapy-runtime/catalog-gates-2/COMPLETED.json'])['checks'])|{'hifiasm_execution_evidence'}
    ui=get('catalog-ui-2/COMPLETED.json');general=get('catalog-ui-2/general/ui-validation.json');focused=get('catalog-ui-2/hifiasm/hifiasm-ui-verification.json');assert ui['status']=='passed-local-general-and-hifiasm-catalog-browser-checks' and general['status']==focused['status']=='passed' and len(general['checks'])==299 and len(focused['checks'])==31 and focused['document_width']<=390 and not focused['page_errors'];assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html')
    for report in [render,audit,gates,ui]:
        for name,value in report['source_pins'].items():assert pin(ROOT/name)==value,name
        for name,value in report.get('input_pins',{}).items():assert pin(ROOT/name)==value,name
    from prepare_hifiasm_catalog_ui import verify as browser_sources
    browser_sources();assert all(p['visual_review'][key] for key in ['desktop_card','mobile_dark_warnings','no_horizontal_overflow']);evolution=json.loads((DATA/'hifiasm-readme-evolution.json').read_bytes());assert evolution['before']==byte_pin(files['catalog-before/README.md']) and evolution['after']==pin(DATA/'README.md') and files['catalog-before/README.md'] in (DATA/'README.md').read_bytes() and b'89 new integrations' in (DATA/'README.md').read_bytes()[:1000]
    embedded=json.loads(re.findall(r'<script[^>]*type="application/json"[^>]*>(.*?)</script>',(ROOT/'site/literature-review.html').read_text(encoding='utf8'),re.S)[0]);display=dict(papers=json.loads(json.dumps(papers)),tools=list(new.values()),summary=summary)
    from shapeit_catalog import PAPER_SCOPE
    for paper in display['papers']:
        for use in paper['tools']:
            if use['tool_id']=='shapeit':previous=use.get('implementation_scope_note','');use['implementation_scope_note']=PAPER_SCOPE+(' '+previous if previous else '')
    assert embedded==json.loads(json.dumps(display,ensure_ascii=False).replace('<','\\u003c'))
    if check_manifest:
        names=set()
        for line in (DATA/'SHA256SUMS').read_text().splitlines():digest,name=line.split('  ',1);assert name not in names and pin(DATA/name)['sha256']==digest,name;names.add(name)
        assert names=={q.name for q in DATA.iterdir() if q.is_file() and q.name!='SHA256SUMS'}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
    if emit:print('Verified250 papers,400 tools,2465 observations,1000 reviews;89 new scoped integrations/92 including existing,252 missing;27 scientific gates and330 browser checks. Goal remains active.')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source and catalog admission gates')
    verify()

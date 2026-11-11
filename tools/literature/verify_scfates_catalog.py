# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify one scFates availability delta against immutable catalog and scientific parents."""
import gzip,json,re,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import ALLOWED_PATHS,reverse_delta,verify_source

DATA=ROOT/'docs/literature/omics-2023-2026'
BASE='build/literature-review/scfates-runtime/'
STATUS='passed-one-scFates-coordinate-workflow-catalog-integration'

def verify(emit=True,folder=None,check_manifest=True):
    folder=DATA if folder is None else Path(folder)
    p=json.loads((folder/'scfates-catalog-validation.json').read_bytes())
    assert p['status']==STATUS and (p['new_scoped_integrations'],p['including_existing_adapters'],p['missing'])==(87,90,254)
    assert not any(p[k] for k in ['scientific_verifiers_changed','scientific_source_exception_added','independent_trajectory_refit','whole_paper_reproduced','whole_goal_complete'])
    assert p['availability_rows_changed']==1 and p['new_catalog_credit'] and p['paper_roles_changed']==0
    assert pin(folder/'scfates-catalog-support.zip')==p['archive']
    files=unpack(folder/'scfates-catalog-support.zip',p)
    for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
    for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
    for name in p['catalog_snapshot']:
        actual=folder/name if name=='scfates-source-audit.json' else DATA/name
        assert pin(actual)==byte_pin(files['catalog-snapshot/'+name]),name
    for name in ['site/literature-review.html','site/literature-warnings.html']:assert (ROOT/name).read_bytes()==files['current-output/'+name]
    assert ALLOWED_PATHS==frozenset(['.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/render_review.py','tools/literature/reviewed-uses.json','tools/literature/repeat_catalog.py','tools/literature/check_review_ui.cjs','tools/literature/check_repeat_review_ui.cjs'])
    oldp=json.loads((DATA/'palantir-catalog-validation.json').read_bytes())
    assert pin(DATA/'palantir-catalog-support.zip')==oldp['archive']
    oldfiles=unpack(DATA/'palantir-catalog-support.zip',oldp)
    for name,want in oldp['source_pins'].items():
        assert byte_pin(oldfiles['current-source/'+name])==want,name
        verify_source(ROOT,DATA,name,want['sha256'])
    for name,want in oldp['parent_proofs'].items():assert pin(ROOT/name)==want,name
    descriptor=json.loads((DATA/'scfates-render-shared-source-compatibility.json').read_bytes())
    assert descriptor['status']=='passed-dispatch-catalog-only-source-deltas' and len(descriptor['paths'])==1
    row=descriptor['paths'][0];assert row['path']=='tools/literature/render_review.py'
    restored=reverse_delta((ROOT/row['path']).read_bytes(),row)
    prior_render=json.loads(oldfiles['build/literature-review/palantir-runtime/catalog-render-2/COMPLETED.json'])
    assert byte_pin(restored)==prior_render['source_pins'][row['path']]
    assert restored==files[BASE+'catalog-prepare-1/before/'+row['path']]
    before=lambda n:files['catalog-before/'+n]
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']:
        assert before(name)==oldfiles['catalog-snapshot/'+name],name
    papers_raw=(DATA/'papers.json').read_bytes();reviews_raw=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    assert papers_raw==before('papers.json') and reviews_raw==oldfiles['current-source/tools/literature/reviewed-uses.json']
    assert (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==oldfiles['current-source/tools/literature/check_review_ui.cjs']
    papers=json.loads(papers_raw);reviews=json.loads(reviews_raw)['reviews'];assert len(papers)==250 and sum(len(q['tools']) for q in papers)==2465
    assert len(reviews)==988 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==340
    from palantir_catalog import load_catalog_registry
    assert len(load_catalog_registry(ROOT))==438
    old={r['id']:r for r in json.loads(before('tool-inventory.json'))};new={r['id']:r for r in json.loads((DATA/'tool-inventory.json').read_bytes())}
    assert set(old)==set(new) and len(new)==400 and old['scfates']['implementation_status']=='missing'
    assert all(old[k]==new[k] for k in old if k!='scfates')
    workflow=json.loads((ROOT/'docs/literature/scfates-workflow-development/VALIDATION.json').read_bytes())
    assert workflow['full_scfates_coordinate_adapter_admitted'] and workflow['implementation_credit'] and not workflow['new_catalog_credit'] and not workflow['whole_package_implemented']
    # Historical workflow admission remains immutable; this distinct proof adds catalog credit.
    for name,want in workflow['source_pins'].items():assert pin(ROOT/name)==want,name
    sf=new['scfates'];assert sf['implementation_status']=='upstream-adapter-validated'
    assert sf['development_candidate']==workflow['artifact']['path'] and sf['development_candidate_sha256']==workflow['artifact']['sha256']
    assert sf['development_cli_class']=='org.jlinalg.cli.ScfatesWorkflowCli' and sf['runtime_setup_cli_class']=='org.jlinalg.cli.ScfatesSetupCli'
    assert not any(sf[k] for k in ['canonical_adapter_available','analysis_installs_software','independent_trajectory_refit','whole_package_implemented','whole_paper_reproduced','whole_goal_complete'])
    assert all(new[k]['implementation_status']=='missing' for k in ['stereo-saw','stereopy','dorothea'])
    summary=json.loads((DATA/'summary.json').read_bytes());counts=summary['implementation_status_counts']
    assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,70,254)
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,400,2465)
    get=lambda name:json.loads(files[BASE+name])
    render=get('catalog-render-1/COMPLETED.json');assert render['status']=='passed-one-scFates-availability-catalog-render' and render['unchanged_tool_rows']==399
    audit=json.loads((folder/'scfates-source-audit.json').read_bytes());assert audit==get('catalog-source-audit-2/COMPLETED.json')
    assert audit['status']=='passed-250-primary-XML-DOI-and-988-unchanged-roles-with-scFates-availability'
    assert (audit['source_records'],audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'],audit['followup_observations'])==(250,2465,988,340,8)
    assert audit['papers_pin']==pin(DATA/'papers.json') and audit['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json')
    assert not audit['registry_or_reviews_changed'] and audit['existing_source_roles_preserved'] and audit['availability_rows_changed']==1
    followraw=files[BASE+'catalog-source-audit-2/named-followup-scan.json'];assert byte_pin(followraw)==audit['named_followup_scan']
    follow=json.loads(followraw)['observations'];assert len(follow)==8 and all(r['registry_applied'] and r['role_review_applied'] and r['implementation_credit']==(r['tool_id'] in {'palantir','scfates'}) for r in follow)
    gates=get('catalog-gates-1/COMPLETED.json');assert gates['status']=='passed-twenty-five-frozen-and-additive-scFates-gates' and len(gates['checks'])==25
    assert gates['current_reviews']==988 and gates['prior_metadata_reviews']==980 and not gates['scientific_verifiers_changed']
    assert set(gates['checks'])==set(json.loads(oldfiles['build/literature-review/palantir-runtime/catalog-gates-2/COMPLETED.json'])['checks'])|{'simpleppt_math_evidence','scfates_workflow_evidence'}
    assert set(gates['metadata_snapshot_overrides'])=={'tools/literature/reviewed-uses.json','docs/literature/omics-2023-2026/papers.json'}
    for name,archive in [('tools/literature/reviewed-uses.json','palantir-prior-reviews.json.gz'),('docs/literature/omics-2023-2026/papers.json','palantir-prior-papers.json.gz')]:assert gates['metadata_snapshot_overrides'][name]==byte_pin(gzip.decompress((DATA/archive).read_bytes()))
    ui=get('catalog-ui-2/COMPLETED.json');general=get('catalog-ui-2/general/ui-validation.json');focused=get('catalog-ui-2/scfates/scfates-ui-verification.json')
    assert ui['status']=='passed-local-general-and-scfates-catalog-browser-checks' and general['status']==focused['status']=='passed'
    assert len(general['checks'])==299 and len(focused['checks'])==21 and focused['document_width']<=390 and not focused['page_errors']
    assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html')
    reuse=ui['reused_general'];assert reuse['source']==BASE+'catalog-ui-1' and reuse['same_page_bytes']
    receipt=get('catalog-ui-1/GENERAL-COMPLETED.json');assert byte_pin(files[BASE+'catalog-ui-1/GENERAL-COMPLETED.json'])==reuse['completion']
    assert receipt['status']=='passed-general-only-from-retained-failed-focused-run' and not receipt['focused_run_passed']
    assert get('catalog-ui-1/general.process.json')['exit_code']==0 and get('catalog-ui-1/scfates.process.json')['exit_code']==1
    assert files[BASE+'catalog-ui-1/general/ui-validation.json']==files[BASE+'catalog-ui-2/general/ui-validation.json']
    for report in [render,audit,gates,ui,receipt]:
        for name,want in report['source_pins'].items():assert pin(ROOT/name)==want,name
        for name,want in report.get('input_pins',{}).items():assert pin(ROOT/name)==want,name
    from prepare_scfates_catalog_ui import verify as verify_ui
    verify_ui()
    assert p['visual_review']['desktop_card'] and p['visual_review']['mobile_dark_warnings'] and p['visual_review']['no_horizontal_overflow']
    embedded=json.loads(re.findall(r'<script[^>]*type="application/json"[^>]*>(.*?)</script>',(ROOT/'site/literature-review.html').read_text(encoding='utf8'),re.S)[0])
    display=dict(papers=json.loads(papers_raw),tools=list(new.values()),summary=summary)
    from shapeit_catalog import PAPER_SCOPE
    for paper in display['papers']:
        for use in paper['tools']:
            if use['tool_id']=='shapeit':previous=use.get('implementation_scope_note','');use['implementation_scope_note']=PAPER_SCOPE+(' '+previous if previous else '')
    assert embedded==json.loads(json.dumps(display,ensure_ascii=False).replace('<','\\u003c'))
    evolution=json.loads((DATA/'scfates-readme-evolution.json').read_bytes());current=(DATA/'README.md').read_bytes()
    assert evolution['before']==byte_pin(before('README.md')) and evolution['after']==pin(DATA/'README.md')
    assert evolution['historical_document_retained_verbatim'] and before('README.md') in current and b'87 new integrations' in current[:1000]
    assert not evolution['scientific_source_exception_added']
    if check_manifest:
        names=set()
        for line in (DATA/'SHA256SUMS').read_text().splitlines():
            digest,name=line.split('  ',1);assert name not in names and pin(DATA/name)['sha256']==digest,name;names.add(name)
        assert names=={q.name for q in DATA.iterdir() if q.is_file() and q.name!='SHA256SUMS'}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
    if emit:print('Verified250 unchanged papers,400 tools,2465 observations,988 reviews;87 scoped integrations/90 including existing,254 missing;25 scientific gates and320 browser checks. Goal remains open.')
    return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog or scientific source gates')
    verify()

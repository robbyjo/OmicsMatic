# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify one scoped Sniffles availability row and six added primary-source reviews."""
import gzip,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import ALLOWED_PATHS,verify_source,reverse_delta
from sniffles_catalog import current_catalog_review_context,verify_review_extension,load_catalog_registry
from sniffles_execution_evidence import verify as execution_verify

DATA=ROOT/'docs/literature/omics-2023-2026';BASE='build/literature-review/sniffles-runtime/'
STATUS='passed-one-Sniffles-original-calling-workflow-and-six-primary-roles-catalog-integration'


def verify(emit=True,folder=None,check_manifest=True):
    folder=DATA if folder is None else Path(folder);proof=json.loads((folder/'sniffles-catalog-validation.json').read_bytes())
    assert proof['status']==STATUS and (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'])==(91,94,250)
    assert (proof['reviewed_roles'],proof['supporting_pointers'],proof['new_reviews'],proof['unchanged_prior_reviews'])==(1009,353,6,1003)
    assert proof['new_catalog_credit'] and proof['availability_rows_changed']==1 and proof['paper_roles_changed']==6
    assert not any(proof[key] for key in ['scientific_verifiers_changed','scientific_source_exception_added','independent_SV_calling','whole_paper_reproduced','whole_goal_complete'])
    assert pin(folder/'sniffles-catalog-support.zip')==proof['archive'];files=unpack(folder/'sniffles-catalog-support.zip',proof)
    for name,value in proof['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==value,name
    for name,value in proof['parent_proofs'].items():assert pin(ROOT/name)==value,name
    for name in proof['catalog_snapshot']:
        path=folder/name if name=='sniffles-source-audit.json' else DATA/name
        assert pin(path)==byte_pin(files['catalog-snapshot/'+name]),name
    for name in ['site/literature-review.html','site/literature-warnings.html']:assert (ROOT/name).read_bytes()==files['current-output/'+name]
    assert ALLOWED_PATHS==frozenset({'.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/render_review.py',
        'tools/literature/reviewed-uses.json','tools/literature/repeat_catalog.py','tools/literature/check_review_ui.cjs','tools/literature/check_repeat_review_ui.cjs'})
    prior=json.loads((DATA/'rsubread-catalog-validation.json').read_bytes());assert pin(DATA/'rsubread-catalog-support.zip')==prior['archive']
    oldfiles=unpack(DATA/'rsubread-catalog-support.zip',prior)
    oldpapers=gzip.decompress((DATA/'sniffles-prior-papers.json.gz').read_bytes());oldreviews=gzip.decompress((DATA/'sniffles-prior-reviews.json.gz').read_bytes())
    assert oldpapers==oldfiles['catalog-snapshot/papers.json'] and oldreviews==oldfiles['current-source/tools/literature/reviewed-uses.json']
    for name,value in prior['source_pins'].items():
        assert byte_pin(oldfiles['current-source/'+name])==value
        if name=='docs/literature/omics-2023-2026/papers.json':assert oldfiles['current-source/'+name]==oldpapers
        else:verify_source(ROOT,DATA,name,value['sha256'])
    for name,value in prior['parent_proofs'].items():assert pin(ROOT/name)==value
    for descriptor,name in [('sniffles-shared-source-compatibility.json','tools/literature/render_review.py'),
        ('sniffles-reviews-shared-source-compatibility.json','tools/literature/reviewed-uses.json')]:
        delta=json.loads((DATA/descriptor).read_bytes());assert delta['status']=='passed-dispatch-catalog-only-source-deltas' and len(delta['paths'])==1
        row=delta['paths'][0];assert row['path']==name
        assert reverse_delta((ROOT/name).read_bytes(),row)==oldfiles['current-source/'+name]==files['catalog-before/'+name]
    assert (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==oldfiles['current-source/tools/literature/check_review_ui.cjs']
    papers=json.loads((DATA/'papers.json').read_bytes());changed=[]
    for old,new in zip(json.loads(oldpapers),papers,strict=True):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [t['tool_id'] for t in old['tools']]==[t['tool_id'] for t in new['tools']]
        assert [t for t in old['tools'] if t['tool_id']!='sniffles']==[t for t in new['tools'] if t['tool_id']!='sniffles']
        if old!=new:changed.append(new['paper_id'])
    assert changed==['P033','P102','P129','P164','P247','P248'] and len(papers)==250 and sum(len(p['tools']) for p in papers)==2465
    reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    with current_catalog_review_context():verify_review_extension(ROOT,DATA,reviews)
    assert len(load_catalog_registry(ROOT))==438 and len(reviews)==1009 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==353
    old={row['id']:row for row in json.loads(oldfiles['catalog-snapshot/tool-inventory.json'])}
    new={row['id']:row for row in json.loads((DATA/'tool-inventory.json').read_bytes())}
    assert len(new)==400 and set(old)==set(new) and all(old[key]==new[key] for key in old if key!='sniffles')
    assert old['sniffles']['implementation_status']=='missing';row=new['sniffles'];assert row['implementation_status']=='upstream-adapter-validated'
    workflow,_=execution_verify(False);assert workflow['declared_calling_workflow_available'] and not workflow['implementation_credit']
    assert row['development_candidate']==workflow['artifact']['path'] and row['development_candidate_sha256']==workflow['artifact']['sha256']
    assert row['development_cli_class']=='org.jlinalg.cli.SnifflesWorkflowCli' and row['source_roles_reviewed']==6
    assert all(not row[key] for key in ['canonical_adapter_available','analysis_installs_software','independent_SV_calling','whole_package_implemented','whole_paper_reproduced','whole_goal_complete'])
    summary=json.loads((DATA/'summary.json').read_bytes());counts=summary['implementation_status_counts']
    assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,74,250)
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,400,2465)
    def document(name):return json.loads(files[BASE+name])
    render=document('catalog-render-1/COMPLETED.json');assert render['status']=='passed-one-Sniffles-availability-and-six-role-catalog-render' and render['unchanged_tool_rows']==399
    audit=json.loads((folder/'sniffles-source-audit.json').read_bytes());assert audit==document('catalog-source-audit-1/COMPLETED.json')
    assert (audit['source_records'],audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(250,2465,1009,353)
    assert audit['all_non_sniffles_source_roles_preserved'] and audit['unchanged_prior_reviews']==1003
    assert audit['papers_pin']==pin(DATA/'papers.json') and audit['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json')
    gates=document('catalog-gates-2/COMPLETED.json');assert gates['status']=='passed-thirty-one-frozen-and-additive-Sniffles-gates'
    assert len(gates['checks'])==31 and gates['current_reviews']==1009 and gates['prior_metadata_reviews']==980 and not gates['scientific_verifiers_changed']
    assert set(gates['checks'])==set(json.loads(oldfiles['build/literature-review/rsubread-runtime/catalog-gates-1/COMPLETED.json'])['checks'])|{'sniffles_execution_evidence'}
    ui=document('catalog-ui-3/COMPLETED.json');general=document('catalog-ui-3/general/ui-validation.json');focused=document('catalog-ui-3/sniffles/sniffles-ui-verification.json')
    assert ui['status']=='passed-local-general-and-sniffles-catalog-browser-checks' and general['status']==focused['status']=='passed'
    assert len(general['checks'])==299 and len(focused['checks'])==43 and focused['document_width']<=390 and not focused['page_errors']
    assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html')
    reuse=ui['reused_general'];assert reuse['same_page_bytes'] and reuse['source']==BASE+'catalog-ui-1'
    retained=json.loads(files[reuse['completion_path']]);assert byte_pin(files[reuse['completion_path']])==reuse['completion']
    assert retained['status']=='passed-general-only-from-retained-failed-focused-run' and not retained['focused_run_passed']
    assert retained['page']==ui['page'] and retained['warning_page']==ui['warning_page']
    assert retained['general']==reuse['general']==byte_pin(files[BASE+'catalog-ui-1/general/ui-validation.json'])
    assert files[BASE+'catalog-ui-1/general/ui-validation.json']==files[BASE+'catalog-ui-3/general/ui-validation.json']
    assert document('catalog-ui-1/general.process.json')['exit_code']==0
    for name,value in retained['source_pins'].items():
        assert byte_pin(files[BASE+'catalog-ui-1/source-snapshot/'+name])==pin(ROOT/name)==value
    for report in [render,audit,gates,ui]:
        for name,value in report['source_pins'].items():assert pin(ROOT/name)==value,name
        for name,value in report.get('input_pins',{}).items():assert pin(ROOT/name)==value,name
    from prepare_sniffles_catalog_checks import verify as check_sources
    check_sources()
    before=files['catalog-before/docs/literature/omics-2023-2026/README.md'];current=(DATA/'README.md').read_bytes()
    assert before==oldfiles['catalog-snapshot/README.md'] and before in current
    assert b'91 new integrations' in current and b'250 named tools still missing' in current and b'1,009 reviewed roles' in current
    warning=(DATA/'WARNINGS.md').read_text(encoding='utf8');assert warning.startswith('## Sniffles: warnings and required actions')
    for marker in ['SKIP3','FAIL2','CANCEL_REQUESTED','--allow-download','--allow-install','legacy_minimum_segment_size','minimum_alignment_length','DUP','INV','Cell Ranger']:
        assert marker in warning
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    if check_manifest:
        names=[]
        for line in (DATA/'SHA256SUMS').read_text().splitlines():
            digest,name=line.split('  ',1);assert pin(DATA/name)['sha256']==digest;names.append(name)
        assert len(names)==len(set(names)) and set(names)=={p.name for p in DATA.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    if emit:print('Verified91 new scoped integrations/94 including existing;250 missing;250 papers/1009 roles;31 gates and342 browser checks; goal active')
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/catalog/evidence checks')
    verify()

# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the complete cisTarget catalog/source/root/browser checkpoint without runtime analysis."""
import argparse,gzip,io,json,zipfile
from collections import Counter
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort

DATA=ROOT/'docs/literature/omics-2023-2026'
HOME=ROOT/'docs/literature/cistarget-catalog-development'
BASE='build/literature-review/cistarget-runtime/'
STATUS='PASS_CISTARGET_CATALOG_ROOT_SOURCE_AND_BROWSER_INTEGRATION_WITH_GOAL_OPEN'


def verify(emit=True,folder=None,check_manifest=True):
    folder=DATA if folder is None else Path(folder);proof=json.loads((folder/'cistarget-catalog-validation.json').read_bytes())
    assert proof['status']==STATUS and proof['new_catalog_credit'] and not proof['whole_goal_complete']
    assert (proof['papers'],proof['tools'],proof['observations'],proof['reviewed_roles'],proof['supporting_pointers'])==(250,400,2466,1018,356)
    assert (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'],proof['unchanged_unrelated_tool_objects'])==(93,96,248,399)
    assert pin(folder/'cistarget-catalog-support.zip')==proof['archive'];files=unpack(folder/'cistarget-catalog-support.zip',proof)
    for name,wanted in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==wanted;verify_source(ROOT,DATA,name,wanted['sha256'])
    for name,wanted in proof['frozen_development_files'].items():assert pin(ROOT/name)==wanted
    for name,wanted in proof['published_root_artifacts'].items():assert pin(ROOT/name)==wanted
    for name,wanted in proof['catalog_snapshot'].items():assert pin(DATA/name)==wanted and byte_pin(files['catalog-snapshot/'+name])==wanted
    for name,wanted in proof['page_pins'].items():assert pin(ROOT/name)==wanted and byte_pin(files['catalog-snapshot/'+name])==wanted
    def document(name):return json.loads(files[BASE+name])
    def current(name):return json.loads(files['catalog-snapshot/'+name])
    papers=current('papers.json');verify_fixed_cohort(ROOT,DATA,papers)
    oldpapers=json.loads(gzip.decompress(files['catalog-snapshot/cistarget-prior-papers.json.gz']));oldreviews=json.loads(gzip.decompress(files['catalog-snapshot/cistarget-prior-reviews.json.gz']));reviews=json.loads(files['current-source/tools/literature/reviewed-uses.json'])
    assert len(oldreviews['reviews'])==1012 and len(reviews['reviews'])==1018
    changed=[];additions=[]
    for old,new in zip(oldpapers,papers,strict=True):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [r for r in old['tools'] if r['tool_id']!='cistarget']==[r for r in new['tools'] if r['tool_id']!='cistarget']
        before={r['tool_id'] for r in old['tools']};after={r['tool_id'] for r in new['tools']};assert before<=after
        additions.extend((new['paper_id'],name) for name in after-before)
        if old!=new:changed.append(new['paper_id'])
    assert changed==['P047','P058','P061','P065','P094','P100','P145'] and additions==[('P145','cistarget')]
    assert sum(len(p['tools']) for p in papers)==2466 and sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews'])==356
    roles=current('cistarget-role-coverage.json');assert roles['applied'] and (roles['unchanged_prior_reviews'],roles['superseded_prior_reviews'],roles['new_reviews'])==(1011,1,6)
    supersession=current('cistarget-review-supersession.json')['supersessions'];assert len(supersession)==1 and supersession[0]['paper_id']=='P061'
    assert oldreviews['reviews'][supersession[0]['review_index']]==supersession[0]['before']
    assert reviews['reviews'][supersession[0]['review_index']]==supersession[0]['after']
    drafts=json.loads(files['current-source/tools/literature/cistarget-use-reviews.json'])['reviews'];assert len(drafts)==7
    by_review={(r['pmcid'],r['tool_id']):r for r in reviews['reviews']};by_paper={p['paper_id']:p for p in papers}
    for draft in drafts:
        review=draft['review'];assert by_review[(review['pmcid'],'cistarget')]==review
        tool=next(t for t in by_paper[draft['paper_id']]['tools'] if t['tool_id']=='cistarget')
        for field in ['evidence_paragraph_sha256','use_summary','implementation_scope_note','version_reported_near_name','supporting_evidence']:
            if field in review:assert tool[field]==review[field]
    assert by_paper['P145']['tools'][-1]['tool_id']=='cistarget' and by_paper['P145']['tools'][-1]['evidence_paragraph_index']==53
    unchanged=lambda rows:[r for r in rows if not(r['pmcid']=='PMC10719113' and r['tool_id']=='cistarget')]
    assert unchanged(reviews['reviews'])[:1011]==unchanged(oldreviews['reviews'])
    review_delta=current('cistarget-reviews-shared-source-compatibility.json')['paths'][0]
    assert reverse_delta(files['current-source/tools/literature/reviewed-uses.json'],review_delta)==gzip.decompress(files['catalog-snapshot/cistarget-prior-reviews.json.gz'])
    old={r['id']:r for r in json.loads(files['catalog-before/tool-inventory.json'])};new={r['id']:r for r in current('tool-inventory.json')};assert set(old)==set(new) and len(new)==400
    assert all(old[k]==new[k] for k in old if k!='cistarget');assert old['cistarget']['implementation_status']=='missing'
    row=new['cistarget'];assert row['implementation_status']=='native-subset-validated' and row['root_command']=='cistarget-tools' and row['paper_count']==row['source_roles_reviewed']==7
    assert new['cellranger']['implementation_status']=='excluded-by-user' and not row['whole_paper_reproduced'] and not row['whole_goal_complete']
    counts=dict(Counter(r['implementation_status'] for r in new.values()));summary=current('summary.json');assert counts==summary['implementation_status_counts']
    assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(19,74,3,248)
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,400,2466)
    assert summary['literature_status'].endswith('semantic-completeness-review-open') and summary['goal_status']=='implementation-in-progress'
    dispatch=current('cistarget-dispatch-shared-source-compatibility.json')['paths'][0];assert len(dispatch['edits'])==2
    assert reverse_delta(files['current-source/src/main/java/org/jlinalg/cli/JLinAlgCli.java'],dispatch)==files[BASE+'catalog-dispatch-bridge-1/JLinAlgCli.before.java']
    routed=document('catalog-root-validation-2/COMPLETED.json');assert routed['status']=='PASS108_COMPLETE_CISTARGET_ROOT_FLOWS_12_PREDECESSOR_FLOWS_AND72_DISPATCH_CONTRACTS'
    assert (routed['complete_root_workflow_comparisons'],routed['predecessor_workflows'],routed['dispatcher_contracts'])==(108,12,72)
    platforms={(c,p) for c in ['javac21','javac25'] for p in ['windows21','windows25','linux21']};assert {(r['compiler'],r['platform']) for r in routed['runs']}==platforms
    native_home=ROOT/'docs/literature/cistarget-development';native_proof=json.loads((native_home/'VALIDATION.json').read_bytes());native_files=unpack(native_home/'controls.zip',native_proof)
    for run in routed['runs']:
        prefix=BASE+'catalog-root-validation-2/'+run['compiler']+'-'+run['platform'];assert len(run['cases'])==18 and len(run['prior_workflows'])==2 and run['dispatcher_contracts']==12
        for case in run['cases']:
            name=case['id'];native=prefix+'/'+name
            previous=BASE+('literal-example-2/'+run['compiler']+'-'+run['platform']+'/native' if name=='literal' else 'feather-validation-3/'+run['compiler']+'-'+run['platform'] if name=='original-feather' else 'workflow-validation-4/'+run['compiler']+'-'+run['platform']+'/'+name)
            assert document('catalog-root-validation-2/'+run['compiler']+'-'+run['platform']+'/'+name+'-process/process.json')['exit_code']==0
            for file,wanted in case['all_output_bytes_identical'].items():assert byte_pin(files[native+'/'+file])==wanted and files[native+'/'+file]==native_files[previous+'/'+file]
        for prior in run['prior_workflows']:
            assert prior['all_nonreceipt_files_byte_identical'] and prior['run_receipt_semantically_equal']
            for file,wanted in prior['output_pins'].items():assert byte_pin(files[prefix+'/prior-'+prior['id']+'/'+file])==wanted
    for compiler in ['javac21','javac25']:
        published=ROOT/next(n for n in proof['published_root_artifacts'] if n.endswith('root-'+compiler+'.jar'))
        parent=ROOT/'docs/literature/mrmega-catalog-development'/('mrmega-root-'+compiler+'.jar');native=native_home/('cistarget-workflow-'+compiler+'.jar')
        with zipfile.ZipFile(published) as added,zipfile.ZipFile(parent) as previous,zipfile.ZipFile(native) as science:
            assert all(added.read(n)==previous.read(n) for n in previous.namelist() if n!='org/jlinalg/cli/JLinAlgCli.class')
            assert all(added.read(n)==science.read(n) for n in science.namelist() if n.endswith('.class'))
            for name in added.namelist():
                assert name.startswith('org/jlinalg/') or name.startswith(('META-INF/mrmega/','META-INF/cistarget/'))
                if name.endswith('.class'):assert int.from_bytes(added.read(name)[6:8],'big')==61
            assert added.read('META-INF/cistarget/NUMPY-LICENSE.txt')==science.read('META-INF/NUMPY-LICENSE.txt')
    render=document('catalog-render-3/COMPLETED.json');assert render['status']=='PASS_CISTARGET_RENDER_WITH_399_UNRELATED_TOOL_OBJECTS_IDENTICAL' and render['unchanged_unrelated_tool_objects']==399
    audit=document('catalog-source-audit-2/COMPLETED.json');assert (audit['source_records'],audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(250,2466,1018,356)
    assert audit['all_non_cistarget_source_roles_preserved'] and audit['unchanged_prior_reviews']==1011 and audit['explicit_prior_review_supersessions']==1
    gates=document('catalog-gate-execution-1/COMPLETED.json');assert gates['status']=='PASS33_CISTARGET_SCIENCE_GATES_WITH_UNCHANGED_EXECUTED_SOURCES_AND_METADATA' and len(gates['checks'])==33
    assert not gates['scientific_verifiers_changed'] and not gates['source_allowlist_changed']
    assert document('catalog-gate-execution-1/SOURCE-BEFORE.json')==gates['source_pins'] and document('catalog-gate-execution-1/METADATA-BEFORE.json')==gates['input_pins']
    index_name='docs/literature/omics-2023-2026/SHA256SUMS';old_index=files[BASE+'catalog-manifest-before-gates-1/SHA256SUMS.after'];assert byte_pin(old_index)==gates['input_pins'][index_name]
    for name,wanted in gates['input_pins'].items():
        if name!=index_name:assert pin(ROOT/name)==wanted
    documentation=document('catalog-readme-1/COMPLETED.json');assert documentation['post_gate_documentation_only'] and not documentation['product_pages_and_scientific_inputs_changed']
    before_readme=files[BASE+'catalog-readme-1/README.before.md'];header=files[BASE+'catalog-readme-1/README.header.md'];after_readme=files['catalog-snapshot/README.md']
    assert after_readme==header+before_readme+b'\n</details>\n' and byte_pin(before_readme)==documentation['before'] and byte_pin(after_readme)==documentation['after']
    assert b'93 new scoped integrations' in header and b'96 including three existing adapters' in header and b'248 named tools still missing' in header
    for line in old_index.decode().splitlines():
        digest,name=line.split('  ',1)
        if name=='README.md':assert byte_pin(before_readme)['sha256']==digest
        else:assert pin(DATA/name)['sha256']==digest
    worker=document('catalog-gate-execution-1/checks/COMPLETED.json');assert worker['current_reviews']==1018 and worker['prior_metadata_reviews']==980 and len(worker['checks'])==33
    for name,wanted in gates['source_pins'].items():assert byte_pin(files[BASE+'catalog-gate-execution-1/source-snapshot/'+name])==wanted;verify_source(ROOT,DATA,name,wanted['sha256'])
    ui=document('catalog-ui-2/COMPLETED.json');general=document('catalog-ui-2/general/ui-validation.json');focused=document('catalog-ui-2/cistarget/cistarget-ui-verification.json')
    assert ui['status']=='passed-local-general-and-cistarget-catalog-browser-checks' and general['status']==focused['status']=='passed'
    assert len(general['checks'])==299 and len(focused['checks'])==60 and focused['document_width']<=390 and not focused['page_errors']
    for report in [render,audit,routed,ui]:
        for name,wanted in report['source_pins'].items():verify_source(ROOT,DATA,name,wanted['sha256'])
    from prepare_cistarget_catalog_checks import verify as verify_derivatives
    verify_derivatives()
    warnings=files['catalog-snapshot/WARNINGS.md'].decode('utf8');assert warnings.startswith('## cisTarget: warnings and required actions')
    for term in ['SKIP3','FAIL2','NaN NES','SOURCE ROUNDING WARNING','NEW output paths','PyArrow17.0.0','Cell Ranger','partial/']:assert term in warnings
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    if check_manifest:
        names=[]
        for line in (DATA/'SHA256SUMS').read_text().splitlines():digest,name=line.split('  ',1);assert pin(DATA/name)['sha256']==digest;names.append(name)
        assert len(names)==len(set(names)) and set(names)=={p.name for p in DATA.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    if emit:print('Verified93 new scoped integrations/96 including existing;248 missing;250 papers/1018 roles;33 science gates and359 browser checks; goal active')
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete catalog/source preservation checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.parse_args();verify()

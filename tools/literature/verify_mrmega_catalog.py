# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify native MR-MEGA command/catalog credit and all preserved predecessor evidence."""
import gzip,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import ALLOWED_PATHS,verify_source,reverse_delta
from mrmega_catalog import current_catalog_review_context,verify_review_extension,load_catalog_registry
from mrmega_workflow_evidence import verify as workflow_verify

DATA=ROOT/'docs/literature/omics-2023-2026';BASE='build/literature-review/mrmega-runtime/'
STATUS='passed-one-native-MRMEGA-command-and-three-corrected-primary-roles-catalog-integration'


def verify(emit=True,folder=None,check_manifest=True):
    folder=DATA if folder is None else Path(folder);proof=json.loads((folder/'mrmega-catalog-validation.json').read_bytes())
    assert proof['status']==STATUS and (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'])==(92,95,249)
    assert (proof['reviewed_roles'],proof['supporting_pointers'],proof['new_reviews'],proof['unchanged_prior_reviews'])==(1012,355,3,1009)
    assert proof['new_catalog_credit'] and proof['availability_rows_changed']==1 and proof['paper_roles_changed']==3
    assert not any(proof[k] for k in ['scientific_verifiers_changed','scientific_source_exception_added','whole_package_implemented','whole_paper_reproduced','whole_goal_complete'])
    assert pin(folder/'mrmega-catalog-support.zip')==proof['archive'];files=unpack(folder/'mrmega-catalog-support.zip',proof)
    for n,v in proof['source_pins'].items():assert pin(ROOT/n)==byte_pin(files['current-source/'+n])==v,n
    for n,v in proof['parent_proofs'].items():assert pin(ROOT/n)==v,n
    for n in proof['catalog_snapshot']:
        path=folder/n if n=='mrmega-source-audit.json' else DATA/n;assert pin(path)==byte_pin(files['catalog-snapshot/'+n]),n
    for n in ['site/literature-review.html','site/literature-warnings.html']:assert (ROOT/n).read_bytes()==files['current-output/'+n]
    assert ALLOWED_PATHS==frozenset({'.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/render_review.py','tools/literature/reviewed-uses.json','tools/literature/repeat_catalog.py','tools/literature/check_review_ui.cjs','tools/literature/check_repeat_review_ui.cjs'})
    prior=json.loads((DATA/'sniffles-catalog-validation.json').read_bytes());assert pin(DATA/'sniffles-catalog-support.zip')==prior['archive'];oldfiles=unpack(DATA/'sniffles-catalog-support.zip',prior)
    oldpapers=gzip.decompress((DATA/'mrmega-prior-papers.json.gz').read_bytes());oldreviews=gzip.decompress((DATA/'mrmega-prior-reviews.json.gz').read_bytes())
    assert oldpapers==oldfiles['catalog-snapshot/papers.json'] and oldreviews==oldfiles['current-source/tools/literature/reviewed-uses.json']
    for n,v in prior['source_pins'].items():
        assert byte_pin(oldfiles['current-source/'+n])==v
        if n=='docs/literature/omics-2023-2026/papers.json':assert oldfiles['current-source/'+n]==oldpapers
        else:verify_source(ROOT,DATA,n,v['sha256'])
    for n,v in prior['parent_proofs'].items():assert pin(ROOT/n)==v
    delta=json.loads((DATA/'mrmega-shared-source-compatibility.json').read_bytes());assert delta['status']=='passed-dispatch-catalog-only-source-deltas' and len(delta['paths'])==2
    for row in delta['paths']:
        n=row['path'];assert n in {'tools/literature/render_review.py','src/main/java/org/jlinalg/cli/JLinAlgCli.java'}
        assert reverse_delta((ROOT/n).read_bytes(),row)==files['catalog-before/'+n]
        if n=='tools/literature/render_review.py':assert files['catalog-before/'+n]==oldfiles['current-source/'+n]
    row=json.loads((DATA/'mrmega-reviews-shared-source-compatibility.json').read_bytes())['paths'][0]
    assert row['path']=='tools/literature/reviewed-uses.json' and reverse_delta((ROOT/row['path']).read_bytes(),row)==oldreviews
    assert (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==oldfiles['current-source/tools/literature/check_review_ui.cjs']
    papers=json.loads((DATA/'papers.json').read_bytes());changed=[]
    for old,new in zip(json.loads(oldpapers),papers,strict=True):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [t['tool_id'] for t in old['tools']]==[t['tool_id'] for t in new['tools']]
        assert [t for t in old['tools'] if t['tool_id']!='mrmega']==[t for t in new['tools'] if t['tool_id']!='mrmega']
        if old!=new:changed.append(new['paper_id'])
    assert changed==['P072','P105','P162'] and len(papers)==250 and sum(len(p['tools']) for p in papers)==2465
    reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    with current_catalog_review_context():verify_review_extension(ROOT,DATA,reviews)
    assert len(load_catalog_registry(ROOT))==438 and len(reviews)==1012 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==355
    old={r['id']:r for r in json.loads(oldfiles['catalog-snapshot/tool-inventory.json'])};new={r['id']:r for r in json.loads((DATA/'tool-inventory.json').read_bytes())}
    assert len(new)==400 and set(old)==set(new) and all(old[k]==new[k] for k in old if k!='mrmega')
    assert old['mrmega']['implementation_status']=='missing';row=new['mrmega'];assert row['implementation_status']=='native-subset-validated' and row['root_command']=='mrmega-tools'
    from mrmega_regression_evidence import verify as regression_verify
    from mrmega_ancestry_evidence import verify as ancestry_verify
    regression_verify(False);ancestry_verify(False)
    workflow,_=workflow_verify(False);assert workflow['standalone_native_file_workflow_available'] and not workflow['catalog_credit']
    assert row['source_roles_reviewed']==3 and all(not row[k] for k in ['canonical_adapter_available','analysis_installs_software','original_runtime_required','whole_package_implemented','whole_paper_reproduced','whole_goal_complete'])
    summary=json.loads((DATA/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(18,74,249)
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,400,2465)
    def doc(n):return json.loads(files[BASE+n])
    render=doc('catalog-render-1/COMPLETED.json');assert render['status']=='passed-one-native-MRMEGA-availability-and-three-role-catalog-render' and render['unchanged_tool_rows']==399
    audit=json.loads((folder/'mrmega-source-audit.json').read_bytes());assert audit==doc('catalog-source-audit-1/COMPLETED.json')
    assert (audit['source_records'],audit['observations_verified'],audit['reviewed_uses_verified'],audit['supporting_pointers_verified'])==(250,2465,1012,355)
    assert audit['all_non_mrmega_source_roles_preserved'] and audit['unchanged_prior_reviews']==1009 and audit['papers_pin']==pin(DATA/'papers.json') and audit['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json')
    gate=doc('catalog-gates-1/COMPLETED.json');assert gate['status']=='passed-thirty-two-frozen-and-additive-MRMEGA-gates' and len(gate['checks'])==32 and gate['current_reviews']==1012 and not gate['scientific_verifiers_changed']
    oldgates=json.loads(oldfiles['build/literature-review/sniffles-runtime/catalog-gates-2/COMPLETED.json']);assert set(gate['checks'])==set(oldgates['checks'])|{'mrmega_workflow_evidence'}
    routed=doc('catalog-root-validation-2/COMPLETED.json');assert routed['status']=='PASS_MRMEGA_ROOT_132_COMPLETE_NATIVE_WORKFLOWS_AND_66_DISPATCH_CONTRACTS' and (routed['complete_root_workflow_comparisons'],routed['dispatcher_contracts'])==(132,66)
    for compiler,build in [('javac21','catalog-root-build21'),('javac25','catalog-root-build25')]:
        built=doc(build+'/COMPLETED.json');artifact=ROOT/built['published_artifact']['path'];assert pin(artifact)=={k:built['published_artifact'][k] for k in ['bytes','sha256']}
        assert built['parent_entries_unchanged_except_root'] and built['unchanged_native_workflow_classes']==22
        with zipfile.ZipFile(artifact) as current,zipfile.ZipFile(ROOT/'docs/literature/mrmega-workflow-development'/('mrmega-workflow-'+compiler+'.jar')) as native:
            assert all(current.read(n)==native.read(n) for n in native.namelist() if n.endswith('.class'))
            assert current.read('org/jlinalg/cli/JLinAlgCli.class')==files[BASE+build+'/classes/org/jlinalg/cli/JLinAlgCli.class']
            for n in current.namelist():assert n.startswith('org/jlinalg/') or n in {'META-INF/mrmega/LICENSE','META-INF/mrmega/DEPENDENCIES.json'}
        if compiler=='javac21':assert row['development_candidate']==built['published_artifact']['path'] and row['development_candidate_sha256']==built['published_artifact']['sha256']
    for run in routed['runs']:
        assert len(run['cases'])==22 and run['dispatcher_contracts']==11
        prefix='catalog-root-validation-2/'+run['compiler']+'-'+run['platform']+'/'
        for case in run['cases']:
            assert case['all_native_tables_settings_and_warnings_byte_identical'] and case['semantic_run_receipt_equal']
            assert doc(prefix+case['id']+'/process.json')['exit_code']==0
            for n,v in case['files'].items():assert byte_pin(files[BASE+prefix+case['id']+'/native/'+n])==v
    ui=doc('catalog-ui-1/COMPLETED.json');general=doc('catalog-ui-1/general/ui-validation.json');focused=doc('catalog-ui-1/mrmega/mrmega-ui-verification.json')
    assert ui['status']=='passed-local-general-and-mrmega-catalog-browser-checks' and general['status']==focused['status']=='passed'
    assert len(general['checks'])==299 and len(focused['checks'])==51 and focused['document_width']<=390 and not focused['page_errors']
    assert ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html')
    for report in [render,audit,gate,routed,ui]:
        for n,v in report['source_pins'].items():assert pin(ROOT/n)==v,n
        for n,v in report.get('input_pins',{}).items():assert pin(ROOT/n)==v,n
    from prepare_mrmega_catalog_checks import verify as verify_derivatives
    verify_derivatives()
    before=files['catalog-before/docs/literature/omics-2023-2026/README.md'];current=(DATA/'README.md').read_bytes();assert before==oldfiles['catalog-snapshot/README.md'] and before in current
    assert b'92 new integrations' in current and b'249 named tools still missing' in current and b'1,012 reviewed' in current
    warning=(DATA/'WARNINGS.md').read_text(encoding='utf8');assert warning.startswith('## MR-MEGA: warnings and required actions')
    for term in ['SKIP3','FAIL2','partial/','NEW output paths','no_alleles=true','floor(n/2)-1','Cell Ranger','lower OR','upper bound','SmallCohortCount']:assert term in warning
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    if check_manifest:
        names=[]
        for line in (DATA/'SHA256SUMS').read_text().splitlines():digest,n=line.split('  ',1);assert pin(DATA/n)['sha256']==digest;names.append(n)
        assert len(names)==len(set(names)) and set(names)=={p.name for p in DATA.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    if emit:print('Verified92 new scoped integrations/95 including existing;249 missing;250 papers/1012 roles;32 gates and350 browser checks; goal active')
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete catalog and source checks')
    verify()

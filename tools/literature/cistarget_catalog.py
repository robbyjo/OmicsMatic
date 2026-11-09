# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""cisTarget catalog extension: exact historical review archive, one supersession and one new observation."""
import contextlib,copy,gzip,html,json
from mrmega_catalog import *
import mrmega_catalog as prior
from cellphonedb_catalog import PriorReviewRoot
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import verify_source,reverse_delta

_prior_1012=prior.verify_review_extension


def load_catalog_registry(root):
    result=copy.deepcopy(prior.load_catalog_registry(root));assert len(result)==438
    extension=json.loads((root/'tools/literature/cistarget-registry-spelling.json').read_bytes())
    assert extension['status']=='explicit-primary-bound-cisTarget-spelling-extension' and extension['new_definitions']==0
    rows=[r for r in result if r['id']=='cistarget'];assert len(rows)==1 and rows[0]['pattern']==extension['before_pattern']
    rows[0]['pattern']=extension['after_pattern'];assert len({r['id'] for r in result})==438;return result


def verify_review_extension(root,data,reviews):
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();roles_path=data/'cistarget-role-coverage.json'
    if not roles_path.exists():
        assert len(reviews)==1012;_prior_1012(root,data,reviews);return
    roles=json.loads(roles_path.read_bytes());oldraw=gzip.decompress((data/'cistarget-prior-reviews.json.gz').read_bytes());old=json.loads(oldraw)
    history=[oldraw,*[gzip.decompress((data/n).read_bytes()) for n in ['mrmega-prior-reviews.json.gz','sniffles-prior-reviews.json.gz','rsubread-prior-reviews.json.gz','hifiasm-prior-reviews.json.gz','gseapy-prior-reviews.json.gz','palantir-prior-reviews.json.gz']]]
    if raw in history:
        assert reviews==json.loads(raw)['reviews'];_prior_1012(root,data,reviews);return
    assert roles['status']=='passed-seven-cisTarget-primary-role-reviews-and-one-spelling-observation' and roles['applied']
    assert (roles['prior_reviews'],roles['unchanged_prior_reviews'],roles['superseded_prior_reviews'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'],roles['observations'])==(1012,1011,1,6,1018,356,2466)
    assert pin(data/'cistarget-prior-reviews.json.gz')==roles['prior_review_archive'] and pin(data/'cistarget-prior-papers.json.gz')==roles['prior_papers_archive']
    assert byte_pin(oldraw)['sha256']==roles['before_reviews_sha256']
    draft_path=root/'tools/literature/cistarget-use-reviews.json';draft=json.loads(draft_path.read_bytes())
    assert pin(draft_path)==roles['draft_pin'] and pin(root/'tools/literature/curate_cistarget_uses.py')['sha256']==roles['curator_sha256']
    assert pin(root/'tools/literature/cistarget-registry-spelling.json')==roles['registry_spelling']
    assert draft['status']=='seven-primary-bound-cisTarget-role-reviews' and {r['paper_id'] for r in draft['reviews']}=={'P047','P058','P061','P065','P094','P100','P145'}
    expected=copy.deepcopy(old)
    for item in draft['reviews']:
        review=item['review'];assert review['tool_id']=='cistarget';indexes=[i for i,r in enumerate(expected['reviews']) if (r['pmcid'],r['tool_id'])==(review['pmcid'],'cistarget')]
        if indexes:
            assert item['paper_id']=='P061' and len(indexes)==1 and item['prior_review']==expected['reviews'][indexes[0]]
            expected['reviews'][indexes[0]]=review
        else:assert item['prior_review'] is None;expected['reviews'].append(review)
    assert json.loads(raw)==expected and reviews==expected['reviews'] and byte_pin(raw)['sha256']==roles['reviews_sha256']
    assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==1018
    assert [r for r in reviews if not(r['pmcid']=='PMC10719113' and r['tool_id']=='cistarget')][:1011]==[r for r in old['reviews'] if not(r['pmcid']=='PMC10719113' and r['tool_id']=='cistarget')]
    _prior_1012(PriorReviewRoot(root,oldraw),data,old['reviews'])
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(oldraw)['sha256']);load_catalog_registry(root)


@contextlib.contextmanager
def current_catalog_review_context():
    saved=prior.verify_review_extension
    try:
        prior.verify_review_extension=verify_review_extension
        with prior.current_catalog_review_context():yield
    finally:prior.verify_review_extension=saved


def _wrapped(name):
    def metadata(*args):
        if name=='mrmega_metadata':
            # The frozen predecessor pins its own dispatcher source directly.
            # Reconstruct only that exact allowed route/help document; every
            # scientific path and every other metadata input stays live.
            from run_mrmega_catalog_checks import ArchivedCatalogView
            root,data=args;relative='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
            descriptor=json.loads((data/'cistarget-dispatch-shared-source-compatibility.json').read_bytes());assert len(descriptor['paths'])==1
            row=descriptor['paths'][0];assert row['path']==relative and len(row['edits'])==2
            before=reverse_delta((root/relative).read_bytes(),row);verify_source(root,data,relative,row['before_sha256'])
            args=(ArchivedCatalogView(root,{relative:before}),data)
        with current_catalog_review_context():return getattr(prior,name)(*args)
    return metadata


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata','soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata','cellphonedb_metadata','palantir_metadata','scfates_metadata','gseapy_metadata','hifiasm_metadata','rsubread_metadata','sniffles_metadata','mrmega_metadata']:globals()[_name]=_wrapped(_name)


def cistarget_metadata(root,data):
    home=root/'docs/literature/cistarget-development';proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['complete_kernel_comparisons']==672 and proof['standalone_native_tsv_workflow_available'] and all(pin(root/n)==v for n,v in proof['source_pins'].items())
    assert pin(home/'controls.zip')==proof['archive'] and all(pin(home/n)==v for n,v in proof['artifacts'].items())
    roles=json.loads((data/'cistarget-role-coverage.json').read_bytes());assert roles['applied'];verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    routed=json.loads((root/'build/literature-review/cistarget-runtime/catalog-root-validation-2/COMPLETED.json').read_bytes())
    assert routed['status']=='PASS108_COMPLETE_CISTARGET_ROOT_FLOWS_12_PREDECESSOR_FLOWS_AND72_DISPATCH_CONTRACTS'
    built=json.loads((root/'build/literature-review/cistarget-runtime/catalog-root-build21-1/COMPLETED.json').read_bytes());artifact=root/'docs/literature/cistarget-catalog-development/cistarget-root-javac21.jar'
    assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']}
    for name,value in routed['source_pins'].items():verify_source(root,data,name,value['sha256'])
    return dict(implementation_status='native-subset-validated',implementation_source='src/main/java/org/jlinalg/cli/CisTargetWorkflowCli.java',
        implementation_scope='Native Java cistarget-tools ranked-feature TSV analysis: complete motif-background recovery curves, AUC, NES, exact leading-target membership, strict/inclusive selection, missing-signature dispositions and exact supplied motif-to-TF joins. Explicit ctxcore0.2.0/NumPy1.26.4 or RcisTarget1.29.0 exact iCisTarget profiles preserve distinct cutoffs, SD and reduction conventions. Optional tested PyArrow17.0.0 converter exports complete Feather V1/V2 rankings without rank shifts. No upstream runtime is required for native TSV analysis. DNA/Cluster-Buster scoring, interval overlap, SCENIC/SCENIC+ network fitting, AUCell, score-database analysis, annotation inference and R approximate leading edges remain separate. Read CISTARGET_INTEGRATION.md and every warning.',
        validation_note='672 complete original numerical comparisons across two compiler builds and three JVMs;96 native TSV flows, six positive original-Feather file flows, six literal example runs,60 CLI refusals and actual converter SKIP3/FAIL2 controls. The main dispatcher matches all108 native output populations and12 predecessor GWAS flows, with72 dispatch contracts. Python references execute original public ctxcore functions; R references execute unchanged original function definitions under R4.6.1, without installing the full R package. Source finite-NES rounding artifacts on identical AUCs are retained with prominent warnings. Frozen verification checks stored integrity/relationships, not refitting, execution authenticity or biological/paper reproduction.',
        project_adapter_available=True,canonical_adapter_available=False,source_roles_reviewed=7,source_role_coverage='cistarget-role-coverage.json',
        development_candidate=artifact.relative_to(ROOT).as_posix(),development_candidate_sha256=pin(artifact)['sha256'],development_cli_class='org.jlinalg.cli.CisTargetWorkflowCli',root_cli_class='org.jlinalg.cli.JLinAlgCli',root_command='cistarget-tools',
        native_workflow_proof='docs/literature/cistarget-development/VALIDATION.json',native_helpers=['src/main/java/org/jlinalg/enrichment/CisTargetRecovery.java'],analysis_installs_software=False,original_runtime_required=False,full_R_package_installed=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


CISTARGET_WARNING='''## cisTarget: warnings and required actions

Use cistarget-tools with the published root supplement, recorded project dependency and explicit source profile, AUC integration parameter, recovery bound, motif-selection thresholds and overlap requirement. Read CISTARGET_INTEGRATION.md and CISTARGET_DEVELOPMENT.md. Native TSV analysis requires Java and installs nothing. Require exit0, run.json PASS_NATIVE_CISTARGET_WITH_WARNINGS and enriched-motifs.tsv; read WARNINGS.md, all-motifs.tsv and every signature/annotation disposition.

FAIL2 admits no result. Preserve failed partial/ files and completed outputs; correct the named schema, identifier, source-profile, numerical or capacity problem and retry NEW output paths. All supplied motif rows define normalization: never prefilter motifs before AUC/NES/background estimation. Raw ranks are used literally; do not infer a zero/one-based conversion. Python and R cutoffs, SD and reductions differ. NaN NES is undefined enrichment, not a valid null test. SOURCE ROUNDING WARNING means finite NES on identical AUCs is a retained source arithmetic artifact, not biological enrichment.

Optional Feather conversion requires PyArrow17.0.0 in a permitted private Python environment. SKIP3 means skip the conversion and every dependent Feather-based analysis; where permitted install that exact dependency in a NEW private environment and retry NEW paths, otherwise supply an already available complete TSV database or leave analysis skipped. Conversion never installs software. Preserve conversion and native receipts together. Use full matching motif/feature/annotation populations; score and transposed databases, missing ranks and identifier repair are refused. Full export memory/disk and explicit --max-cells limits apply.

Seven reviewed paper roles distinguish algorithm execution, SCENIC/SCENIC+ inputs and motif-collection-only use. P145 adds the primary Methods spelling cis-target; it does not establish standalone package execution. DNA scoring/Cluster-Buster, interval overlap, genome conversion, DMR selection, expression/methylation filtering, annotation inference, SCENIC network fitting, AUCell and R approximate leading edges remain separate. Reference editions are not inferred historical paper editions. Full R package and the separately licensed pycisTarget wrapper are not installed or executed. Frozen verification checks stored integrity and numeric relationships without independent refitting or authentication. Canonical executable unchanged; Cell Ranger remains excluded and the full250-paper goal remains active.

'''
CISTARGET_ACTIONS='<section id="cistarget-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>cisTarget: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in CISTARGET_WARNING.strip().split('\n\n')[1:])+'</section>'

# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Credit the validated FUMA subset while keeping data requirements and missing methods explicit."""
import hashlib
import json
from collections import Counter
from pathlib import Path
import zipfile

PROOF='docs/literature/fuma-expression-preparation-2/VALIDATION.json'
EXPECTED=dict(bytes=150907,sha256='8f8ad049c55f4ae83492576628d5e6d352e3d669089722411da0ac5387ed2134')
SOURCE_CLOSURE='docs/literature/omics-2023-2026/fuma-source-closure.json'
SOURCE_CLOSURE_PIN=dict(bytes=82771,sha256='47c4e39450f05bc0ee39d9854c98011c2f8693a1c7e095c90e7f409e09f985d2')
CONTEXT={'docs/literature/omics-2023-2026/tool-inventory.json','docs/literature/omics-2023-2026/summary.json','docs/literature/omics-2023-2026/papers.json','tools/literature/reviewed-uses.json','tools/literature/tool-registry.json'}


def pin(path):
    with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    return dict(bytes=path.stat().st_size,sha256=digest)


def exact(path,expected):
    if pin(path)!=expected:raise ValueError('Changed FUMA evidence/source/artifact: '+str(path))


def verify(root,data):
    if not __debug__:raise RuntimeError('Never disable FUMA evidence checks')
    root,data=Path(root),Path(data);exact(root/PROOF,EXPECTED);proof=json.loads((root/PROOF).read_text(encoding='utf-8'))
    assert proof['status']=='passed-native-FUMA-sample-expression-preparation-and-typed-handoff'
    assert proof['tests_per_JVM']==148 and proof['new_tests_per_JVM']==44 and proof['heap_mib']==128
    assert proof['affected_family_tests']['tests']==663 and all(proof['affected_family_tests'][k]==0 for k in ['failed','skipped','errors'])
    assert proof['platforms']==['windows-java21','windows-java25','linux-java21']
    reference=proof['independent_reference'];assert (reference['cases_per_JVM'],reference['gene_rows_per_JVM'],reference['finite_reference_comparisons_per_JVM'],reference['retained_base_R_raw_mean_overflows_per_JVM'])==(16,192,2094,18)
    assert not proof['claims']['original_FUMA_preprocessing_runtime'] and not proof['claims']['DEG_generation_available'] and not proof['claims']['heatmap_clustering_available']
    assert proof['metadata_correction']['corrected_label_counts']==[1,3] and len(proof['metadata_correction']['scientific_tables_unchanged'])==26
    exact(root/proof['artifact']['path'],{k:proof['artifact'][k] for k in ['bytes','sha256']});exact(root/'build/cli/omicsmatic-0.3.7.jar',proof['canonical_executable_unchanged'])
    # Older LD/reference modules are inherited by this candidate. A fresh full
    # rebuild matched the tested JAR byte for byte; pin their transitive source
    # dependencies as well as the latest expression checkpoint's direct inputs.
    exact(root/SOURCE_CLOSURE,SOURCE_CLOSURE_PIN)
    closure=json.loads((root/SOURCE_CLOSURE).read_text(encoding='utf-8'))
    assert closure['status']=='passed-current-FUMA-source-dependency-closure-with-identical-rebuild'
    assert closure['whole_JAR_bytes_identical'] and closure['artifact']==proof['artifact']
    assert {k:closure['rebuilt_artifact'][k] for k in ['bytes','sha256']}=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert closure['source_file_count']==len(closure['source_files'])==58 and closure['class_count']==len(closure['packaged_classes'])==413
    assert 'src/main/java/org/jlinalg/genetics/HardcallLd.java' in closure['source_files']
    exact(root/'tools/literature/fuma_source_closure.py',closure['collector_pin'])
    for name,value in closure['source_files'].items():exact(root/name,value)
    for name,value in proof['source_pins'].items():
        if name not in CONTEXT:exact(root/name,value)
    archive_path=(root/PROOF).parent/proof['archive']['path'];exact(archive_path,{k:proof['archive'][k] for k in ['bytes','sha256']})
    with zipfile.ZipFile(archive_path) as archive:
        entries=archive.infolist();assert len(entries)==len({e.filename for e in entries})==proof['archive']['entries']==676
        assert {e.filename for e in entries}==set(proof['archive_files'])
        for name,value in proof['archive_files'].items():
            raw=archive.read(name);assert len(raw)==value['bytes'] and hashlib.sha256(raw).hexdigest()==value['sha256'],name
        historical={}
        for name in CONTEXT:
            raw=archive.read('source/'+name);value=proof['source_pins'][name];assert len(raw)==value['bytes'] and hashlib.sha256(raw).hexdigest()==value['sha256'];historical[name]=json.loads(raw)
        # These FIVE exact files contain global catalog context. Their historical
        # bytes stay archive-pinned. Recheck the relevant FUMA reviews and source
        # identities below, allowing future unrelated catalog work. Numerical code,
        # fixtures, validators, the FUMA review draft and artifacts stay live-pinned.
        old=historical['docs/literature/omics-2023-2026/tool-inventory.json'];current=json.loads((data/'tool-inventory.json').read_text(encoding='utf-8'))
        a={t['id']:t for t in old};b={t['id']:t for t in current};assert 'fuma' in a and 'fuma' in b
        for key in ['id','name','pattern','role','current_coverage','existing_source','paper_count','papers']:assert a['fuma'][key]==b['fuma'][key],key
        assert a['fuma']['implementation_status']=='missing' and b['fuma']['implementation_status'] in {'missing','native-subset-validated'}
        prior=historical['docs/literature/omics-2023-2026/summary.json'];summary=json.loads((data/'summary.json').read_text(encoding='utf-8'));assert prior['implementation_status_counts']['native-subset-validated']==14 and prior['implementation_status_counts']['missing']==258
        assert summary['implementation_status_counts']==dict(Counter(t['implementation_status'] for t in current)),'Summary must count the actual inventory'
        matrix=json.loads(archive.read('evidence/matrix-3/COMPLETED.json'));commands=json.loads(archive.read('evidence/commands-3/COMPLETED.json'))
        assert matrix['tests_per_JVM']==148 and all(r['process']['exit_code']==0 for r in matrix['runs'])
        assert len(commands['calls'])==5 and all(r['exit_code']==0 for r in commands['calls'])
        assert commands['original_MAGMA_gene_count']==201 and commands['original_MAGMA_tested_properties']==3
        roles=json.loads(archive.read('evidence/role-audit-3/coverage.json'));assert roles['roles']==14 and roles['tool_availability_does_not_require_historical_or_whole_paper_equivalence']
        assert {r['paper_id'] for r in roles['evidence']}==set(b['fuma']['papers'])
        papers=json.loads((data/'papers.json').read_text(encoding='utf-8'));by_id={p['paper_id']:p for p in papers};assert len(papers)==summary['papers']==250
        assert summary['distinct_named_tools']==len(current) and summary['paper_tool_observations']==sum(len(p['tools']) for p in papers)
        drafts=json.loads((root/'tools/literature/fuma-use-reviews.json').read_text(encoding='utf-8'));draft_by_id={r['paper_id']:r['review'] for r in drafts}
        previous_registry={r['id']:r for r in historical['tools/literature/tool-registry.json']};registry={r['id']:r for r in json.loads((root/'tools/literature/tool-registry.json').read_text(encoding='utf-8'))};assert registry['fuma']==previous_registry['fuma']
        reviews=[r for r in json.loads((root/'tools/literature/reviewed-uses.json').read_text(encoding='utf-8'))['reviews'] if r['tool_id']=='fuma'];assert len(reviews)==14 and all(r['review'] in reviews for r in drafts)
        observed={p['paper_id'] for p in papers if any(t['tool_id']=='fuma' for t in p['tools'])};assert observed==set(draft_by_id)==set(b['fuma']['papers'])
        for source in roles['evidence']:
            paper=by_id[source['paper_id']];draft=draft_by_id[source['paper_id']];assert (paper['doi'],paper['pmcid'],paper['xml_sha256'])==(source['doi'],source['pmcid'],source['source_XML_sha256'])
            use=next(t for t in paper['tools'] if t['tool_id']=='fuma');assert use['evidence_paragraph_index']==source['primary_paragraph'] and use['evidence_paragraph_sha256']==source['primary_paragraph_sha256']
            for key in ['use_summary','implementation_scope_note','version_reported_near_name']:assert use[key]==draft[key],(source['paper_id'],key)
    return proof,roles


def fuma_metadata(root,data):
    proof,roles=verify(root,data)
    return dict(implementation_status='native-subset-validated',implementation_source='src/main/java/org/jlinalg/cli/FumaToolsCli.java',
        coverage_note='Fourteen source-reviewed uses. Validated local native methods are available with explicit supplied inputs; unreported paper settings and historical resources are not inferred.',
        implementation_scope='Validated native FUMA subset through fuma-tools: autosomal diploid hardcall MAF/LD preparation with complete capture/replay and verified locus handoff; two-stage locus selection; supplied positional/eQTL/chromatin mapping and variant/score postprocessing; GENE2FUNC gene-set and supplied-DEG enrichment; sample-level TPM/RPKM expression preparation; gene/property reports, novelty, known-association union and Fisher enrichment. Typed workflows preserve dependencies and independent branches. Native stages require Java and declared data. MAGMA fitting and classic cell-type fitting use the separately managed original MAGMA 1.10 adapter. No automatic annotation/eQTL/interaction acquisition, DEG generation, t-tests, heatmap clustering, unexposed hosted modules or historical/full-paper equivalence. Required data and unspecified author settings are distinct from missing methods. Read FUMA_SCOPED_WORKFLOWS.md, FUMA_REFERENCE_WORKFLOW.md and FUMA_EXPRESSION_PREPARATION.md.',
        validation_note='Current corrected candidate: 148 actual-JAR checks on each of Windows Java21/25 and Linux Java21 at128MiB; 663 affected checks pass with no skips. Sixteen independent published-method base-R cases cover192 expression rows and2094 finite numerical comparisons per JVM, retaining18 extreme raw-mean overflow departures. These are not original FUMA preprocessing controls. Actual original MAGMA1.10 fits three prepared tissue properties across201 synthetic genes. Reference replay retains7568 original PLINK LD values and228 frequency rows per JVM. Metadata correction is directly checked;26 scientific tables are byte-identical. Full biological/paper reproduction is not certified.',
        project_adapter_available=True,canonical_adapter_available=False,native_subset_available=True,full_hosted_tool_available=False,
        development_adapter_cli='fuma-tools',development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../fuma-expression-preparation-2/VALIDATION.json',reference_preparation_validation='../fuma-reference-workflow/VALIDATION.json',source_role_coverage='fuma-role-coverage.json',source_roles_reviewed=14,
        native_reference_preparation_available=True,native_reference_replay_available=True,native_expression_preparation_available=True,native_expression_preparation_cli='fuma-tools --method prepare-expression',
        native_helper_available=True,native_helper_source='src/main/java/org/jlinalg/enrichment/FumaExpressionPreparation.java',
        preparation_source_files=['src/main/java/org/jlinalg/genetics/HardcallLd.java','src/main/java/org/jlinalg/genetics/FumaReferencePreparation.java','src/main/java/org/jlinalg/cli/FumaReferenceCli.java','src/main/java/org/jlinalg/enrichment/FumaExpressionPreparation.java','src/main/java/org/jlinalg/cli/FumaExpressionPrepareCli.java'],
        external_analysis_runtime_required=False,original_MAGMA_fitting_runtime_required=True,original_MAGMA_runtime_version='1.10',
        variant_annotation_resource_generation_available=False,DEG_generation_available=False,heatmap_clustering_available=False,whole_paper_reproduced=False,biological_accuracy_certified=False,paper_exact_unknown_settings_available=False)


FUMA_WARNING='''
## FUMA validated native subset: required actions and remaining methods

**Use the corrected current-source candidate, SHA beginning31cf8700.** It provides native reference preparation/replay, loci, supplied-resource mapping/annotation, gene sets, supplied-DEG enrichment, sample-level expression preparation, reports, novelty, known-association union and Fisher enrichment. Read FUMA_SCOPED_WORKFLOWS.md, FUMA_REFERENCE_WORKFLOW.md and FUMA_EXPRESSION_PREPARATION.md. Native stages need Java17+ and explicitly declared inputs. The canonical/released JAR has not been updated to this candidate.

**Preserve complete evidence and declared selections.** Reference-bundle admission replays selected hardcalls before loci. Outside-window LD is uncomputed; undefined eligible LD fails the complete request. Expression eligibility is strict raw mean>1 before cap50/log2(x+1). Centering uses selected samples; MAGMA Average is an unweighted mean of label means. Required action: inspect every sample/gene/variant exclusion, pair audit, namespace, units, build/population/release and correction family. Raw means near1 or extreme values require the named boundary/units review. Native scaled means retain explicit differences from18 independent base-R overflow fields; the R implementation is not original FUMA preprocessing code.

**Unavailable/forbidden dependencies: SKIP3.** MAGMA and classic cell-type fitting require the exact managed original MAGMA1.10 runtime on its supported host. Where permitted run python tools/literature/install_magma.py --allow-download and supply --home build/omics-magma; otherwise keep dependent fitting skipped until a permitted runtime is supplied. Native stages do not need MAGMA/R/FUMA installation. Never silently substitute1.10 for a paper's reported1.08. Cell Ranger remains excluded.

**Input, integrity, numerical, cancellation or capacity failures: FAIL2.** Read each WARNINGS.md/session and retained partial/ diagnostics; correct the named cause and rerun the complete request to a NEW directory. Do not use partial tables as completed results. Connected stages skip after upstream failure; independent completion preserves the overall failure/skip status. Inspect the top-level completion marker and every selected stage status.

**Remaining functions stay explicit.** Automatic variant/eQTL/interaction acquisition, DEG generation/t-tests, heatmap clustering and unexposed hosted modules are not provided by this subset. Annotation tracks, gene sets, DEG memberships and exact identifiers are normal declared data inputs to the supported methods. Unreported author settings remain unknown; they do not establish a default. The14-paper role map distinguishes implemented commands, required resources, separate upstream work and genuine method gaps. A native-subset badge does not certify a complete hosted service, biological accuracy or whole-paper reproduction.

'''

FUMA_ACTIONS='''<section id="fuma-current-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>FUMA: validated native workflows and required recovery</h2>
<p><strong>Validated native subset available.</strong> Use the corrected current-source candidate (SHA begins <code>31cf8700</code>) and <code>fuma-tools</code>. Read <code>FUMA_SCOPED_WORKFLOWS.md</code>, <code>FUMA_REFERENCE_WORKFLOW.md</code> and <code>FUMA_EXPRESSION_PREPARATION.md</code>. Native Java17+ stages need explicit data; the canonical release has not been updated to this candidate.</p>
<p><strong>Reference preparation and replay.</strong> <code>prepare-reference</code> captures every selected requested hardcall row and audits exclusions. <code>replay-reference</code> recomputes the complete family. <code>loci --reference-bundle</code> replays before admission and carries inclusive bp/rank windows. Outside-window LD is uncomputed, not observed weak LD; undefined eligible LD fails the complete request. Required action: review sample/founder selection, build/population/release, all exclusions and pair coverage.</p>
<p><strong>Expression preparation.</strong> <code>prepare-expression</code> streams TPM/RPKM TSV/GCT1.2 into label means, centered expression and MAGMA covariates. Gene eligibility is strict raw mean&gt;1 before capping/logging. The sample-weighted center and unweighted label <code>Average</code> differ. Required action: inspect units, exact IDs, label counts, exclusions and boundary/overflow warnings. Sixteen independent published-method R cases retain18 extreme overflow differences; they are not original FUMA preprocessing code.</p>
<p><strong>SKIP3 when a required upstream runtime is unavailable or forbidden.</strong> Native stages need no R/MAGMA/FUMA runtime. MAGMA and classic cell-type fitting require managed original1.10 on its supported host. Where permitted run <code>python tools/literature/install_magma.py --allow-download</code> and supply <code>--home build/omics-magma</code>; otherwise keep dependent fitting skipped. No silent historical-version substitution. Cell Ranger remains excluded.</p>
<p><strong>FAIL2 for invalid input, integrity, numerical, cancellation or capacity failures.</strong> Required action: read each <code>WARNINGS.md</code>, session and retained <code>partial/</code>; correct the named cause and rerun completely to a NEW directory. No partial resource/result is admitted. Upstream failure skips dependent stages; independent completion retains the parent failure or partial-skip status.</p>
<p><strong>Scoped availability.</strong> Loci, supplied-resource mapping/annotation, gene-set/supplied-DEG tests, expression preparation, reports, novelty, known associations and Fisher are exposed. Automatic annotation/eQTL/interaction acquisition, DEG generation/t-tests, heatmap clustering and unexposed hosted modules remain unsupported. Required inputs and unspecified paper settings are separate from missing methods. No whole-paper or biological-accuracy certification. Current evidence:148 actual-JAR checks per JVM across three profiles,663 affected checks without skips and an original MAGMA fit across201 synthetic genes.</p></section>'''


def historical_warning(text):
    return '\n\n### Earlier FUMA module checkpoint: historical candidate and counts\n\nCurrent scoped availability, candidate and recovery are stated in the FUMA section above. The following retained module protocol records its earlier candidate, test totals and availability; its broader missing-tool counts are historical.\n\n'+text


def historical_actions(text):
    return '<details class="fuma-history" open><summary>Earlier FUMA module validation: historical candidate and counts</summary><p><strong>Historical scoped checkpoint.</strong> Use the current FUMA candidate and recovery instructions above. This retained protocol records an earlier candidate, test totals and broader availability; its missing-tool counts are historical.</p>'+text+'</details>'

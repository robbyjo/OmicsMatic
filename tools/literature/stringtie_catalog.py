# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Credit only the exact validated StringTie workflow, root entry and eight source-checked paper roles."""
import hashlib
import json
from pathlib import PurePosixPath
import stat
import zipfile
from catalog_source_integrity import verify_source
from catalog_fixed_cohort import verify_fixed_cohort

def sha(raw):return hashlib.sha256(raw).hexdigest()
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def checkpoint(root,data,name,status):
    folder=root/'docs/literature'/name;proof=json.loads((folder/'VALIDATION.json').read_bytes());assert proof['schema_version']==1 and proof['status']==status
    sums=dict(line.split('  ',1)[::-1] for line in (folder/'SHA256SUMS').read_text().splitlines());assert set(sums)=={'VALIDATION.json','controls.zip'} and all(pin(folder/n)['sha256']==h for n,h in sums.items())
    assert pin(folder/'controls.zip')=={k:proof['archive'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(folder/'controls.zip') as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==proof['archive']['entries']==len(proof['archive_files'])
        for entry in z.infolist():
            member=PurePosixPath(entry.filename);assert not member.is_absolute() and '..' not in member.parts and '\\' not in entry.filename and stat.S_IFMT(entry.external_attr>>16)==stat.S_IFREG
            raw=z.read(entry);assert dict(bytes=len(raw),sha256=sha(raw))==proof['archive_files'][entry.filename]
        for n,p in proof['source_pins'].items():
            raw=z.read('source/'+n);assert dict(bytes=len(raw),sha256=sha(raw))==p;verify_source(root,data,n,p['sha256'])
    artifact=root/proof['artifact']['path']
    if artifact.exists():assert pin(artifact)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    return proof

def verify(root,data):
    standalone=checkpoint(root,data,'stringtie-typed-workflow','passed-typed-StringTie-short-read-assembly-estimation-merge-and-portable-verification')
    assert standalone['complete_user_fit_cases']==27 and standalone['complete_main_portable_verifications']==72 and standalone['example_copied_windows_verifications']==3 and standalone['all_failed_attempts_retained']
    assert standalone['independent_retained_original_comparisons']==24 and standalone['independent_fresh_original_merge_comparisons']==1 and len(standalone['new_classes'])==12 and standalone['unchanged_parent_entries']==19607
    assert len(standalone['platform_validation'])==3 and all(r['passed']==1353 and r['skipped']==0 for r in standalone['platform_validation'])
    assert standalone['recovery']['actual_cli_rejections']==24 and standalone['recovery']['actual_user_fit_cancellation'] and standalone['recovery']['cancelled_process_cleanup_complete']
    assert standalone['standalone_public_fitting_workflow_available'] and not standalone['root_dispatch_available'] and not standalone['implementation_credit']
    proof=checkpoint(root,data,'stringtie-main-workflow','passed-public-root-StringTie-dispatch-and-actual-main-workflows')
    assert proof['parent_validation']==pin(root/'docs/literature/stringtie-typed-workflow/VALIDATION.json') and proof['parent_archive']==pin(root/'docs/literature/stringtie-typed-workflow/controls.zip') and proof['parent_artifact']==standalone['artifact']
    assert set(proof['changed_classes'])=={'org/jlinalg/cli/JLinAlgCli.class'} and proof['unchanged_parent_entries']==19618 and proof['scientific_classes_unchanged'] and proof['root_dispatch_available']
    assert len(proof['platform_validation'])==3 and all(r['passed']==1361 and r['skipped']==0 and r['actual_main_controls']==5 for r in proof['platform_validation'])
    assert (proof['actual_main_fits'],proof['actual_main_native_verifies'],proof['actual_main_copied_windows_verifies'])==(3,3,2)
    assert not any(proof[k] for k in ['implementation_credit','released','pushed','deployed','whole_goal_complete']) and not proof['original_StringTie_executable_bundled']
    roles=json.loads((data/'stringtie-role-coverage.json').read_bytes());assert roles['status']=='passed-eight-primary-StringTie-role-reviews' and roles['applied'] and roles['source_papers_scanned']==250 and roles['registry_rows']==425
    assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'],roles['reviewed_roles'],roles['named_paragraphs'])==(819,8,827,253,8,13) and not roles['implementation_promoted']
    verify_source(root,data,'tools/literature/reviewed-uses.json',roles['reviews_sha256']);assert sha((root/'tools/literature/curate_stringtie_uses.py').read_bytes())==roles['curator_sha256'] and sha((root/'tools/literature/stringtie-use-reviews.json').read_bytes())==roles['draft_sha256']
    papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(root,data,papers);selected={p['paper_id']:p for p in papers if any(t['tool_id']=='stringtie' for t in p['tools'])};assert set(selected)=={'P101','P102','P109','P110','P171','P183','P223','P248'}=={r['paper_id'] for r in roles['source_proof']}
    reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];drafts=json.loads((root/'tools/literature/stringtie-use-reviews.json').read_bytes());assert len([r for r in reviews if r['tool_id']=='stringtie'])==8
    for row in roles['source_proof']:
        paper=selected[row['paper_id']];tool=next(t for t in paper['tools'] if t['tool_id']=='stringtie');review=next(r for r in reviews if r['tool_id']=='stringtie' and r['pmcid']==paper['pmcid']);draft=next(d for d in drafts if d['paper_id']==row['paper_id'])
        assert paper['doi']==row['doi'] and paper['xml_sha256']==row['xml_sha256'] and tool['evidence_paragraph_sha256']==review['evidence_paragraph_sha256']==row['paragraph_sha256'] and review==draft['review']
        assert tool['use_summary']==review['use_summary'] and tool['implementation_scope_note']==review['implementation_scope_note'] and tool['supporting_evidence']==review['supporting_evidence']==row['supporting_paragraphs'] and tool['version_reported_near_name']==row['reported_version'] and row['source_roles']==draft['source_roles']
        assert tool['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open'
    use=lambda id:next(t for t in selected[id]['tools'] if t['tool_id']=='stringtie')
    assert use('P102')['version_reported_near_name']=='1.3.6' and use('P110')['version_reported_near_name'] is None and '2.1.2' in use('P110')['use_summary']
    assert 'unclear' in use('P109')['implementation_scope_note'] and 'literal -rf' in use('P171')['implementation_scope_note'] and 'hg19' in use('P183')['implementation_scope_note'] and 'RNA-SeQC' in use('P183')['implementation_scope_note']
    return proof

def stringtie_metadata(root,data):
    proof=verify(root,data)
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/StringTieToolsCli.java',project_adapter_available=True,canonical_adapter_available=False,development_cli='stringtie-tools',development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'],source_roles_reviewed=8,whole_paper_reproduced=False,
        coverage_note='Exact original StringTie short-read assembly, guided assembly, reference estimation and transcript merge through stringtie-tools, with complete native Java audits and copied-bundle verification. Historical command/resource ambiguities and full paper pipelines remain open.',
        implementation_scope='Use stringtie-tools in the exact development JAR; older canonical/released executables lack it. Exact Linux x86_64 editions 1.3.0, 1.3.6, 2.1.2, 2.1.7, 2.2.1, 3.0.0 and 3.0.3. Separate permitted standalone kit installation; live payload/ELF/helper/current-host checks and fresh supervised synthetic health precede user fitting. Native Java checks complete BGZF BAM framing/dictionaries/records/tags/order, GTF geometry/populations, all six gene/Ballgown tables and exact-edition normalization. Explicit strand, NH/XS/sort, omission, source-default/threshold and process capacities; every input and original output retained. Full captured verification works on Windows/Linux without opening producer paths. No aliases, sorting, filtering, conversion, imputed zeros or forced TPM sums. Gene residuals are diagnostic and hidden denominators/biology are not certified. SAM/CRAM/CG long-CIGAR, GFF3/adjacent-exon normalization, long/mixed-read fitting, alignment, sequence extraction, annotation integration and differential expression remain separate. Read STRINGTIE_TOOLS.md, STRINGTIE_WORKFLOW.md and examples/stringtie/README.md.',
        validation_note='Frozen standalone evidence: 27 successful synthetic user-fit cases, 75 copied-bundle verifications, 1,353 tests per Windows Java21/25 and Linux Java21 environment; 24 retained exact-original protocol comparisons and one fresh original default-merge comparison. Eight consistently rehashed invalid-bundle cases rejected by all three JVMs; actual owned user-fit cancellation retains FAIL2 and complete cleanup. Root integration adds only two reversible route/help insertions: 1,361 tests per environment, three actual-main fits, three native verifies and two copied Windows verifies; all 19,618 other artifact entries and scientific classes are unchanged. Earlier host-change, harness and compile failures remain retained. Eight source papers/13 named paragraphs reviewed; P102 1.3.6 corrected, P110 TPM edition unspecified, P109 mapping states unclear, P171 literal -rf failure and P183 reference/guide conflict preserved; GffCompare and RNA-SeQC remain separate. Synthetic controls do not certify biology or whole-paper reproduction.')

STRINGTIE_WARNING='''## StringTie: exact original fitting, source ambiguity and recovery

**Current-source stringtie-tools is available; older canonical/released JARs lack it.** Read STRINGTIE_TOOLS.md, STRINGTIE_WORKFLOW.md and examples/stringtie/README.md. Use Linux x86_64 Java for assembly, guided assembly, reference-only estimation and merge. Windows Java also exports the separate standalone setup kit and verifies complete copied bundles. Select an exact edition; installation/download occurs only through the kit where permitted, never automatically during fitting.

**SKIP3 means skip the dependent analysis while an exact permitted runtime, helper, library, filesystem or execution capability is unavailable. FAIL2 means invalid/changed input or evidence, process/audit failure, cancellation, capacity or finalization failure admits no result.** Read WARNINGS.md, FAILURE.json and its stage, metadata/INPUT-AUDIT.json, metadata/OUTPUT-AUDIT.json, fit/process.stderr, fit/process.json and runtime logs. Preserve all attempts, correct the named cause and use NEW output paths. Require exit0, COMPLETED.json and its complete manifest, no INCOMPLETE.json/FAILURE.json, and successful --verify. Host-configuration changes invalidate the attempt; do not disable live integrity checks.

**Explicit reference, strand and population policies are required.** Complete supplied raw inputs and outputs remain captured; no aliases, sorting, read filtering, conversion or missing-value zero filling occurs. Missing/zero NH fallback and unresolved XS require explicit choices; orientation and ts do not fill XS. RETAIN_OMISSIONS preserves every omitted guide identity without inventing zero abundance. Gene-reduction residuals are diagnostic; denominator bounds are conservative, not recovered hidden quantities or forced million-TPM sums. Empty output is not biological absence. Capacities do not guarantee available JVM heap; aggregate RSS is sampled.

**Source ambiguities remain unresolved.** P102 reports1.3.6; P110's unspecified TPM edition is distinct from its 2.1.2 assembly role. P109's mapping states is unclear. P171's literal -rf fails in all seven originals; documented --rf is separate and unavailable through the exact 1.3.0 interface. P183's hg19/GENCODEv38 compatibility remains unresolved, while GffCompare and RNA-SeQC expression are separate. Long/mixed reads, SAM/CRAM/CG transport, GFF3/adjacent-exon normalization and full paper pipelines remain open. Hashes establish integrity, not authenticity, historical execution or biological accuracy. Cell Ranger remains excluded.
'''

STRINGTIE_ACTIONS='''<section id="stringtie-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>StringTie: exact runtime, input and recovery requirements</h2>
<p>Use current-source <strong>stringtie-tools</strong> in the exact development JAR. Older canonical/released executables lack it. Read <code>STRINGTIE_TOOLS.md</code>, <code>STRINGTIE_WORKFLOW.md</code> and <code>examples/stringtie/README.md</code>. Assembly, guided assembly, reference-only estimation and merge require <strong>Linux x86_64 Java</strong>; Windows Java also exports setup and verifies complete copied bundles.</p>
<p>Choose an exact edition: <strong>1.3.0, 1.3.6, 2.1.2, 2.1.7, 2.2.1, 3.0.0 or 3.0.3</strong>. Export <code>--export-runtime-setup NEW_DIRECTORY</code>, read the exported <code>RUN-SETUP.txt</code>, and install/download only where permitted. No installation or download occurs during fitting. Setup or synthetic health completion alone does not admit a user result.</p>
<p><strong>SKIP3: skip dependent analysis until the named runtime, helper, library, filesystem or execution requirement is permitted and available. FAIL2: invalid/changed inputs or evidence, failed process/audit, cancellation, capacity or finalization failure admits no result.</strong> Read <code>WARNINGS.md</code>, <code>FAILURE.json</code> and its named stage, both native audits under <code>metadata/</code>, <code>fit/process.stderr</code>, <code>fit/process.json</code> and runtime logs. Preserve all partials, correct the cause and use <strong>NEW output paths</strong>. Require <code>COMPLETED.json</code>, no <code>INCOMPLETE.json</code>/<code>FAILURE.json</code>, complete manifest hashes and successful <code>--verify</code>.</p>
<p><strong>Declare exact chromosome sizes/build, compatible guide, strand, NH/XS/sort and omission policies.</strong> No aliases, sorting, read filtering, conversion or missing-zero filling occurs. <code>RETAIN_OMISSIONS</code> preserves omitted guide identities without invented abundance. Gene residuals remain diagnostic; denominator constraints do not recover hidden quantities or force TPM sums. Empty output is not biological absence. Complete capacities do not guarantee Java heap; aggregate RSS is sampled.</p>
<p><strong>Paper-specific uncertainty remains visible:</strong> P102 1.3.6 is corrected; P110's unreported TPM edition is separate from 2.1.2 assembly; P109's mapping-state wording is unclear. P171's literal <code>-rf</code> fails in all seven originals; explicit documented <code>--rf</code> is separate and unsupported by the exact 1.3.0 interface. P183's hg19/GENCODEv38 compatibility is unresolved; GffCompare and RNA-SeQC are separate tools. Long/mixed reads, SAM/CRAM/CG, GFF3/adjacent-exon normalization and full study pipelines remain outside this scope.</p>
<p><strong>Evidence:</strong> standalone 27 synthetic fit cases, 75 copied-bundle verifications, 1,353 tests per JVM; root integration 1,361 tests per JVM, three actual-main fits and five explicit verifies, with all scientific classes unchanged. Twenty-four consistently rehashed invalid-bundle rejections and actual owned-process cancellation pass. Verify reads captured inputs/results only and never opens producer paths; hashes prove integrity, not authenticity or biology. <strong>Cell Ranger remains excluded.</strong></p></section>'''

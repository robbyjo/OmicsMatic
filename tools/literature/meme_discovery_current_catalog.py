# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One scoped classic MEME discovery integration; Suite/provider/resource mentions retain their own roles."""
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from catalog_fixed_cohort import verify_fixed_cohort
from collect_omics_review import paragraphs, validate_supporting_evidence
from meme_discovery_workflow_evidence import verify as verify_workflow
from meme_discovery_provider_compatibility import verify_meme_review_extension


def verify(root, data):
    proof = verify_workflow()
    papers = json.loads((data/'papers.json').read_bytes()); verify_fixed_cohort(root,data,papers)
    selected = {p['paper_id']:p for p in papers if any(t['tool_id'] == 'meme' for t in p['tools'])}
    assert set(selected) == {'P007','P013','P133','P139','P155','P215','P217','P235'}
    drafts = {d['paper_id']:d['review'] for d in json.loads((root/'tools/literature/meme-program-use-reviews.json').read_bytes()) if d['review']['tool_id'] == 'meme'}
    reviews = json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    registry = {r['id']:r for r in json.loads((root/'tools/literature/tool-registry.json').read_bytes())}
    verify_meme_review_extension(root, data, reviews)
    assert len([r for r in reviews if r['tool_id'] == 'meme']) == 8
    for pid,paper in selected.items():
        review = next(r for r in reviews if r['tool_id'] == 'meme' and r['pmcid'] == paper['pmcid'])
        assert review == drafts[pid]
        use = next(t for t in paper['tools'] if t['tool_id'] == 'meme')
        for key in ['use_summary','implementation_scope_note','version_reported_near_name','supporting_evidence']:
            assert use.get(key) == review.get(key), (pid,key)
        source = root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')
        if source.exists():
            raw = gzip.decompress(source.read_bytes()); assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
            ps = paragraphs(ET.fromstring(raw)); anchor = next(p for p in ps if p['paragraph_index'] == use['evidence_paragraph_index'])
            assert hashlib.sha256(anchor['text'].encode('utf8')).hexdigest() == use['evidence_paragraph_sha256']
            validate_supporting_evidence(ps,[review],registry)
    return proof


def meme_discovery_metadata(root, data):
    proof = verify(root,data)
    return dict(implementation_status='upstream-adapter-validated', implementation_source='src/main/java/org/jlinalg/cli/MemeToolsCli.java',
        role='Classic de novo motif discovery; distinguish Suite programs and motif resources',
        coverage_note='One scoped original classic MEME discovery adapter with native Java input/site/score audits. Eight reviewed mentions include resource/provider uses; they do not establish eight discovery runs or whole-Suite coverage.',
        implementation_scope='meme-tools DISCOVER/VERIFY runs exact source-built MEME 4.9.1 base, 4.9.1 patch2, 5.5.4 or 5.5.9 on explicit canonical-DNA FASTA/weights and positive order-zero background. Typed OOPS/ZOOPS/ANR, strands, widths, positive Dirichlet prior, iteration/convergence and E-value/site choices are checked against source XML. Native Java independently audits complete contributing-site geometry, weighted counts, prior scores, position tails and sequence diagrams; text PWMs/centibit matrices match XML. Source HTML is bound by exact original-helper replay; PNG carriers are decoded and EPS completeness checked. Genuine E-cutoff empty outputs retain zero motifs and no invented scores/diagrams. Original EM/motif significance and figure pixels remain upstream. No MEME-ChIP pipeline, DREME, STREME, CentriMo, FIMO/Tomtom replacement, higher-order backgrounds, protein/ambiguous DNA, historical whole Suite or whole-paper parity. Read MEME_DISCOVERY_WORKFLOW_DEVELOPMENT.md and its warnings.',
        validation_note='91 actual-JAR contract tests per JVM pass on Windows Java21/25 and Linux Java21, including 40 altered empty-report refusals. Four positive original profile analyses and four full zero-motif workflows each pass 12 portable verifications; the final root example adds one original analysis and three portable checks. Final root checks include real positive verification, required-runtime skips and unchanged existing-output refusals. Recovery controls retain 36 corruptions with both manifest layers reminted, two failed-bundle refusals and two missing-runtime skips. Frozen native parent controls remain separately scoped. Synthetic controls do not certify EM/significance, biological absence, whole papers or execution authenticity.',
        project_adapter_available=True, canonical_adapter_available=False, development_adapter_cli='meme-tools',
        development_candidate=proof['artifact']['path'], development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../meme-discovery-workflow-development/VALIDATION.json', source_role_coverage='meme-program-role-coverage.json', source_roles_reviewed=8,
        native_methods_available=True, original_runtime_adapters_available=True, exact_runtime_profiles=proof['exact_profiles'],
        analysis_installs_software=False, whole_paper_reproduced=False, historical_whole_packages_reproduced=False, biological_accuracy_certified=False)


MEME_DISCOVERY_WARNING = '''
## MEME discovery: exact editions and required recovery

Use the separate **meme-tools** root development JAR, **MEME_DISCOVERY_WORKFLOW_DEVELOPMENT.md**, **examples/meme-discovery/README.md** and **meme-discovery-workflow-development/VALIDATION.json**. The released/canonical JAR lacks this command. DISCOVER requires Linux x86_64 Java plus an exact private source-built MEME home, completed build evidence and declared native/Perl/XML/font/Ghostscript dependencies. VERIFY uses captured files and Java only.

**SKIP3: skip the affected analysis if a required runtime is unavailable or installation is forbidden.** Required action: provision the exact declared source profile and report dependencies on a permitted Linux host, or retain the skip. DISCOVER installs nothing and never substitutes a version or silently falls back to text output. Research/nonprofit license eligibility was confirmed for this project; Cell Ranger remains excluded.

**FAIL2: no result admitted, including partial reports.** Required action: preserve the worker and failed bundle; inspect the exact console cause, WARNINGS/INCOMPLETE/FAILURE markers and original probe/fit/render stdout/stderr. Correct the named source, input, capacity or process cause and retry into NEW paths. Require COMPLETED, no incomplete/failure marker and successful VERIFY. Do not rewrite saved installation prefixes or hashes to make a different installation appear valid.

**Keep source choices explicit:** MEME 5.5.4/5.5.9 set the motif cap to 1,000 when an E-value cutoff is supplied; the request must declare that cap. Legacy 4.9.1 retains its requested count and has no CLI seed/search-size options. Zero accepted motifs at the declared cutoff is a valid scoped report, not evidence of biological absence. Other empty-result stopping paths are unadmitted. No records, identifiers, weights, background probabilities or scientific cutoffs are silently repaired.

**Paper scope:** P007 supplies motif resources; P139 concerns Tomtom; P215/P217 concern FIMO; P235 concerns STREME. P013 includes the broader MEME-ChIP pipeline and source settings that this discovery command does not reproduce. P133 omits discovery settings; P155's 4.9.1 patch is unstated. These eight source mentions do not establish eight reproduced discovery workflows. MEME-ChIP/DREME/STREME/CentriMo retain separate status; HyPhy MEME and BWA-MEME are different programs.

**Validation:** 91 tests per JVM on three JVMs, four positive and four zero-motif profile workflows, one final root example and 27 workflow portable verifications, plus root/recovery controls. Java independently audits input/site/count/score/diagram contracts. Original EM, motif significance, report rendering and figure pixels remain upstream. VERIFY checks capture integrity and consistency; it does not independently refit the model, authenticate execution or certify biology. The broader implementation goal remains active.

'''

MEME_DISCOVERY_ACTIONS = '''<section id="meme-discovery-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>MEME discovery: warnings and required actions</h2>
<p>Use the separate <code>meme-tools</code> root development JAR and read <code>MEME_DISCOVERY_WORKFLOW_DEVELOPMENT.md</code>, <code>examples/meme-discovery/README.md</code> and <code>meme-discovery-workflow-development/VALIDATION.json</code>. DISCOVER requires Linux x86_64 Java, an exact private source-built MEME home/build evidence and complete declared report dependencies. VERIFY uses captured files and Java only. The released/canonical JAR lacks this route.</p>
<p><strong>SKIP3: skip the affected analysis if a required runtime is unavailable or installation is forbidden.</strong> Required action: provision the exact profile and Perl/XML/font/Ghostscript/native dependencies on a permitted Linux host, or retain the skip. Analysis installs nothing and substitutes no editions or text-only reports. Research/nonprofit license eligibility was confirmed for this project. Cell Ranger remains excluded.</p>
<p><strong>FAIL2: no result admitted, including partial reports.</strong> Required action: preserve the worker and failed bundle; read the exact console cause, WARNINGS/INCOMPLETE/FAILURE markers and original probe/fit/render stdout/stderr. Correct the named source, input, capacity or process cause and retry into NEW paths. Require COMPLETED, no incomplete/failure marker and successful VERIFY. Never rewrite installation prefixes or hashes to make another installation appear valid.</p>
<p><strong>Source choices:</strong> a 5.5.4/5.5.9 E-value cutoff requires an explicit motif cap of 1,000. Legacy 4.9.1 has no CLI seed/search-size options. A complete zero-motif E-cutoff report has no invented scores or diagrams and does not establish biological absence. Other empty-result stopping paths remain unadmitted. Preserve literal records, identifiers, weights, background and cutoffs.</p>
<p><strong>Eight reviewed mentions:</strong> P007 is a motif resource; P139 Tomtom; P215/P217 FIMO; P235 STREME. P013's broader MEME-ChIP pipeline is not supplied here. P133 settings and P155's exact 4.9.1 patch remain unresolved. MEME-ChIP, DREME, STREME and CentriMo retain separate status; HyPhy MEME and BWA-MEME are distinct programs.</p>
<p><strong>Validation scope:</strong> 91 tests on each of three JVMs; four positive and four zero-motif original profile workflows; one final root example; 27 workflow portable checks; separate root/recovery controls. Native Java checks input/site/count/score/diagram contracts. Original EM/significance, rendering and figure pixels remain upstream. VERIFY checks integrity and consistency, without independent refitting, execution authentication or biological certification. The broader implementation goal remains active.</p></section>'''

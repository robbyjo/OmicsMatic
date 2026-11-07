# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Credit one scoped HTSeq integration after closed workflows and eight primary roles pass."""
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import verify_source
from collect_omics_review import paragraphs,validate_supporting_evidence
from htseq_tools_evidence import verify as verify_public


def sha(raw):return hashlib.sha256(raw).hexdigest()


def verify(root,data):
    proof=verify_public();roles=json.loads((data/'htseq-role-coverage.json').read_bytes())
    assert roles['status']=='passed-eight-HTSeq-primary-role-reviews' and roles['applied']
    assert (roles['source_papers_XML_hashes_checked'],roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['reviewed_roles'],roles['total_observations'])==(250,890,8,898,8,2446)
    assert roles['recovered_named_aliases']==['P005'] and roles['HTSeq_supporting_pointers']==2
    verify_source(root,data,'tools/literature/reviewed-uses.json',roles['reviews_sha256'])
    papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(root,data,papers)
    selected={p['paper_id']:p for p in papers if any(t['tool_id']=='htseq' for t in p['tools'])}
    assert set(selected)=={'P005','P020','P044','P099','P113','P121','P148','P195'}
    drafts={d['paper_id']:d for d in json.loads((root/'tools/literature/htseq-use-reviews.json').read_bytes())}
    reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];assert len([r for r in reviews if r['tool_id']=='htseq'])==8
    registry={r['id']:r for r in json.loads((root/'tools/literature/tool-registry.json').read_bytes())}
    for row in roles['source_proof']:
        paper=selected[row['paper_id']];tool=next(t for t in paper['tools'] if t['tool_id']=='htseq')
        review=next(r for r in reviews if r['tool_id']=='htseq' and r['pmcid']==paper['pmcid']);assert review==drafts[paper['paper_id']]['review']
        assert (paper['pmcid'],paper['doi'],paper['xml_sha256'])==(row['pmcid'],row['doi'],row['xml_sha256'])
        assert tool['evidence_paragraph_index']==row['paragraph_index'] and tool['evidence_paragraph_sha256']==review['evidence_paragraph_sha256']==row['paragraph_sha256']
        for key in ['use_summary','implementation_scope_note','version_reported_near_name','supporting_evidence']:assert tool[key]==review[key]
        source=root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')
        if source.exists():
            raw=gzip.decompress(source.read_bytes());assert sha(raw)==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw))
            anchor=next(p for p in ps if p['paragraph_index']==row['paragraph_index']);assert sha(anchor['text'].encode('utf8'))==row['paragraph_sha256']
            validate_supporting_evidence(ps,[review],registry)
    use=lambda pid:next(t for t in selected[pid]['tools'] if t['tool_id']=='htseq')
    assert use('P005')['version_reported_near_name']=='0.6.0' and use('P020')['version_reported_near_name']=='0.5.3'
    assert 'custom binary read-presence' in use('P099')['implementation_scope_note'] and 'strict MAPQ>20' in use('P121')['implementation_scope_note']
    assert 'does not separately state' in use('P113')['implementation_scope_note'] and 'major/minor' in use('P195')['implementation_scope_note']
    assert use('P099')['version_reported_near_name'] is None and use('P148')['version_reported_near_name'] is None
    return proof


def htseq_metadata(root,data):
    proof=verify(root,data)
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/HtseqToolsCli.java',
        coverage_note='One scoped HTSeq integration combines original five-edition file counting with native prepared-read and binary peak-by-cell APIs; eight reviewed roles retain missing settings and custom-pipeline boundaries.',
        implementation_scope='htseq-tools COUNT/VERIFY runs exact original 0.5.3/0.6.0/0.11.1/0.13.5/2.1.2 counters on explicit SAM/BAM and plain GTF, with declared strand, MAPQ, overlap, nonunique and flag policies. Original automatic read/pair detection remains explicit. Java-only prepared-read counting replays current deterministic filter/allocation semantics; native sparse binary presence marks a supplied peak/cell iff any supplied block overlaps it, retaining exact axes and zeros. Callers supply filtering, pairing/blocks, cell assignments and peak/region preparation. No random allocation, proper-pair prefilter, native SAM/GTF parser, barcode/UMI processing, normalization/differential model or complete mutable HTSeq API is claimed. Read HTSEQ_TOOLS.md.',
        validation_note='260 new actual-JAR tests pass without skips on each of Windows Java21/25 and Linux Java21. The final root artifact passes 7 real count analyses and 24 positive portable VERIFY calls, 33 corruption/consistency refusals, 3 missing-runtime SKIP3 cases and 4 source/commit refusals. Broader source-bound candidates retain 42 edition controls:33 successful count tables, 9 genuine input failures and 99 portable checks. Original reference evidence has 185 valid calls/171 successes/14 source failures, with 59 invalid Python2 transport calls excluded. Native binary controls match 32 original-library matrices, 742 prepared reads, 234 nonzero coordinates and 1,278 spans; 2 original interval refusals remain. Numerical parent entries are preserved, including the frozen prepared-count foundation. Unknown settings, P099 author code and P195 patch remain unresolved. VERIFY checks integrity/schema/consistency, not execution authenticity or biology.',
        project_adapter_available=True,canonical_adapter_available=False,development_adapter_cli='htseq-tools',development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../htseq-tools-development/VALIDATION.json',source_role_coverage='htseq-role-coverage.json',source_roles_reviewed=8,
        native_methods_available=True,original_runtime_adapters_available=True,original_reference_versions=proof['original_reference_versions'],
        analysis_installs_software=False,whole_paper_reproduced=False,historical_whole_packages_reproduced=False,biological_accuracy_certified=False)


HTSEQ_WARNING='''
## HTSeq: original counting, native presence and required recovery

Use the separate **htseq-tools** development JAR and **HTSEQ_TOOLS.md**, its checked file/native examples and **htseq-tools-development/WARNINGS.md**. Older canonical/released JARs lack this route. COUNT requires Linux Java and the exact declared private Python/HTSeq/NumPy/pysam profile; VERIFY and native prepared-read/binary APIs need Java only. Paths resolve against JSON; retain attempts and use NEW outputs.

**SKIP3: skip dependent analysis if a required runtime is unavailable or installation is forbidden.** COUNT never installs software. Where permitted, provision the exact declared profile/helpers and retry into NEW paths; otherwise retain the skip. Cell Ranger remains excluded.

**FAIL2: no result admitted, including partial reports.** Read the named console cause, WARNINGS/status/INCOMPLETE markers and original count/probe stdout/stderr. Correct the source/input/options/process/capacity cause and retry into NEW paths. Uncommitted reports remain diagnostics; require COMPLETED, no INCOMPLETE and successful VERIFY before admission. Do not reinterpret a source error or zero count as negative biology.

**Preserve edition behavior:** 0.5.3 uses unprefixed summaries and skips unknown chromosomes without incrementing no_feature;0.5.3/0.6.0 score secondary/supplementary records and reject tested header-only SAM and =/X CIGARs. Minimum 21 implements strict MAPQ>20. FRACTION divides by assigned features, not NH, and categories can overlap. RANDOM is not admitted. Never substitute a version, repair identifiers or change filters silently.

**Paper scope:** P099 is custom binary peak/cell presence, not the gene counter; exact author script/edition/barcodes/filters are unknown. P113's EU edition is unstated; P121's proper-pair filter does not resolve read/fragment units; P195's 0.13 does not identify 0.13.5. Native APIs require caller preparation. No peak calling/merging, proper-pair prefilter, UMI processing, normalization or differential model is supplied here.

**Validation:** 260 tests per JVM on three JVMs; 7 final count analyses/24 portable checks; 33 consistency refusals/3 missing-runtime skips/4 source-or-commit refusals. Original-library binary controls include 32 matrices / 742 reads / 234 nonzeros and 2 retained refusals. Broader edition controls and 185 original reference calls are preserved separately. VERIFY checks complete bundle integrity/schema/consistency without independent SAM/GTF parsing/pairing or execution authentication. Historical whole environments/papers and biology are not certified. Logical budgets are not Java-heap guarantees. Implementation of the broader toolbox remains in progress.

'''

HTSEQ_ACTIONS='''<section id="htseq-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>HTSeq counting and binary presence: required recovery</h2>
<p>Use the separate development <code>htseq-tools</code> JAR and read <code>HTSEQ_TOOLS.md</code>, <code>examples/htseq-file-count/README.md</code>, the native examples and <code>htseq-tools-development/VALIDATION.json</code>. Older canonical/released JARs lack the route. COUNT needs Linux Java and an exact private profile; VERIFY and native prepared-read/binary APIs need Java only. Relative paths resolve against JSON. Retain attempts and use NEW outputs.</p>
<p><strong>SKIP3: skip dependent analysis if a required runtime is unavailable or installation is forbidden.</strong> Required action: where permitted, provision the exact declared Python/HTSeq/NumPy/pysam profile/helpers and retry into NEW paths; otherwise retain the skip. COUNT never installs software or substitutes versions. Cell Ranger remains excluded.</p>
<p><strong>FAIL2: no result admitted, including partial reports.</strong> Required action: inspect the exact console cause, WARNINGS/status/INCOMPLETE markers and original count/probe stdout/stderr; preserve the attempt, correct the named source/input/options/process/capacity cause and retry into NEW paths. Require COMPLETED, no INCOMPLETE and successful VERIFY before admission. Zero/empty results and source errors are not negative biology.</p>
<p><strong>Retain source behavior:</strong> 0.5.3 unprefixed summaries and unknown-chromosome skips differ from later editions. 0.5.3/0.6.0 score secondary/supplementary records and reject tested header-only SAM and =/X CIGAR. Minimum 21 implements strict MAPQ&gt;20. FRACTION divides by assigned features, not NH; categories can overlap. RANDOM is not admitted. Do not silently repair IDs, relax filters or substitute releases.</p>
<p><strong>Eight reviewed paper roles:</strong> P099 uses custom binary peak-by-cell read presence, with author code/edition/barcodes/filters unstated. P113 EU edition, P121 read/fragment units and P195 exact 0.13 patch remain unresolved. Native APIs require caller-supplied preparation; no proper-pair prefilter, UMI/barcode processing, peak calling/merging, normalization or differential model is supplied.</p>
<p><strong>Validation:</strong> 260 actual-JAR tests per JVM on Windows 21/25 and Linux 21;7 final count analyses, 24 positive portable VERIFY calls, 33 consistency refusals, 3 missing-runtime skips and 4 source/commit refusals. Native binary controls retain 32 original-library matrices / 742 reads / 234 nonzeros and 2 original refusals. Broader edition controls and 185 original reference calls remain separately scoped. VERIFY checks integrity/schema/consistency, without independent native SAM/GTF parsing/pairing, execution authentication or biological certification. Whole historical environments/papers are not reproduced. Logical bounds are not heap guarantees. The broader implementation goal remains active.</p></section>'''

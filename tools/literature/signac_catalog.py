# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Credit one scoped Signac integration after public-workflow and primary-role checks."""
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import verify_source
from collect_omics_review import paragraphs, validate_supporting_evidence
from signac_tools_evidence import verify as verify_public


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def verify(root, data):
    proof = verify_public()
    roles = json.loads((data/'signac-role-coverage.json').read_bytes())
    assert roles['status']=='passed-ten-Signac-primary-role-reviews' and roles['applied']
    assert (roles['source_papers_XML_hashes_checked'],roles['prior_reviews_preserved'],roles['new_reviews'],
        roles['total_reviews'],roles['reviewed_roles'],roles['total_observations'])==(250,880,10,890,10,2445)
    assert roles['recovered_analysis_captions']==['P134'] and roles['institutional_name_collision_excluded']=='P007'
    assert not roles['implementation_promoted'] and not roles['whole_paper_reproduced']
    for name, field in [('curate_signac_uses.py','curator_sha256'),('signac-use-reviews.json','draft_sha256'),
        ('collect_signac_omics.py','reviewed_extractor_sha256'),('signac-source-additions.json','declaration_ledger_sha256')]:
        assert sha((root/'tools/literature'/name).read_bytes())==roles[field]
    verify_source(root,data,'tools/literature/reviewed-uses.json',roles['reviews_sha256'])
    papers=json.loads((data/'papers.json').read_bytes())
    verify_fixed_cohort(root,data,papers)
    selected={p['paper_id']:p for p in papers if any(t['tool_id']=='signac' for t in p['tools'])}
    assert set(selected)=={'P016','P039','P051','P094','P099','P134','P153','P158','P187','P225'}
    drafts=json.loads((root/'tools/literature/signac-use-reviews.json').read_bytes())
    reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert len([r for r in reviews if r['tool_id']=='signac'])==10
    registry={r['id']:r for r in json.loads((root/'tools/literature/tool-registry.json').read_bytes())}
    for row in roles['source_proof']:
        paper=selected[row['paper_id']]
        tool=next(t for t in paper['tools'] if t['tool_id']=='signac')
        review=next(r for r in reviews if r['tool_id']=='signac' and r['pmcid']==paper['pmcid'])
        draft=next(d for d in drafts if d['paper_id']==paper['paper_id'])
        assert review==draft['review']
        assert (paper['pmcid'],paper['doi'],paper['xml_sha256'])==(row['pmcid'],row['doi'],row['xml_sha256'])
        assert tool['evidence_paragraph_index']==row['paragraph_index']
        assert tool['evidence_paragraph_sha256']==review['evidence_paragraph_sha256']==row['paragraph_sha256']
        for key in ['use_summary','implementation_scope_note','version_reported_near_name','supporting_evidence']:
            assert tool[key]==review[key]
        assert tool['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open'
        source=root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')
        if source.exists():
            raw=gzip.decompress(source.read_bytes())
            assert sha(raw)==paper['xml_sha256']
            ps=paragraphs(ET.fromstring(raw))
            anchor=next(p for p in ps if p['paragraph_index']==row['paragraph_index'])
            assert sha(anchor['text'].encode('utf8'))==row['paragraph_sha256']
            validate_supporting_evidence(ps,[review],registry)
    use=lambda pid:next(t for t in selected[pid]['tools'] if t['tool_id']=='signac')
    assert 'ambiguous' in use('P158')['implementation_scope_note'] and 'NucleosomeSignal' in use('P158')['implementation_scope_note']
    assert 'denominator' in use('P187')['implementation_scope_note'] and 'unstated' in use('P187')['implementation_scope_note']
    assert 'FindMarkers' in use('P225')['use_summary'] and 'relabeling' in use('P225')['implementation_scope_note']
    assert 'Monocle2' in use('P099')['implementation_scope_note'] and use('P134')['version_reported_near_name'] is None
    return proof


def signac_metadata(root, data):
    proof=verify(root,data)
    return dict(implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/SignacToolsCli.java',
        coverage_note='One scoped hybrid Signac integration exposes native Java methods and guarded original upstream fits; ten primary-source roles preserve unknown settings and other-package boundaries.',
        implementation_scope='signac-tools provides 11 explicit workflows: Java RunTFIDF methods 1-4 and FindTopFeatures, fragment/FeatureMatrix/nucleosome counts, TSS/GeneActivity, supplied-population motif enrichment and coverage; guarded original SVD, motif scanning/construction, LinkPeaks backgrounds, current Seurat Wilcoxon/LR and MACS2 peak calling. Existing chromvar-tools is routed without duplicate credit. Native normalization retains complete sparse geometry and explicit selection; selection uses the current 1.17.1 assay-wrapper contract. R/package profiles, references/motifs, exact private MACS/Python and author settings stay explicit. Historical extracted functions on current dependencies do not reproduce whole historical environments. Broader Signac features, custom paper transforms/models, integration and biological interpretation remain outside scope. Read SIGNAC_TOOLS.md.',
        validation_note='1679 actual-JAR tests pass without skips on each of Windows Java 21/25 and Linux Java 21. The 21 real public analyses and 51 portable VERIFY calls pass, with two expected missing-runtime SKIP3 outcomes and two expected FAIL2 refusals. All 40 public normalization coordinates match retained original controls, including 9 retained Inf values. The 1373 frozen-parent source bindings and parent proof/archive hashes are checked; prior scientific classes are byte-identical. Ten paper roles include the P134 explicit LR caption and exclude institutional SignAC P007. Partial marker comparisons remain explicit; upstream fit/RNG/KDE and PNG pixels are not all independently replayed. Hashes do not authenticate execution or biology. Failed attempts remain retained.',
        project_adapter_available=True,canonical_adapter_available=False,development_adapter_cli='signac-tools',
        development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../signac-tools-development/VALIDATION.json',source_role_coverage='signac-role-coverage.json',
        source_roles_reviewed=10,native_methods_available=True,original_runtime_adapters_available=True,
        analysis_installs_software=False,duplicate_chromVAR_credit=False,whole_paper_reproduced=False,
        historical_whole_packages_reproduced=False,biological_accuracy_certified=False)


SIGNAC_WARNING='''
## Signac: native methods, original runtimes and explicit recovery

Use the separate current-source **signac-tools** development executable and read **SIGNAC_TOOLS.md**, its checked examples and **signac-tools-development/WARNINGS.md**. Older released/canonical JARs lack this public route. Choose one of 11 workflows explicitly for both analysis and VERIFY. Paths are relative to the JSON file; use NEW outputs and retain attempts.

**SKIP3: skip dependent analysis if a required runtime is unavailable or installation is forbidden.** Analysis never installs software. Where permitted, provision the exact declared R/package or private MACS/Python profile using the relevant guide, then retry into NEW paths. MACS calling needs Linux Java (WSL on Windows); native coverage needs a full Java desktop/font/PNG runtime. Native normalization, fragment/TSS/gene activity and supplied-population motif methods need Java. Cell Ranger remains excluded.

**FAIL2: no result admitted, including partial files.** Inspect the concrete console cause, WARNINGS.md, status/diagnostics and raw logs. Correct the named input/profile/integrity/process/capacity problem and retry into NEW paths. Review warnings after exit 0. Partial FindAllMarkers output is not a negative result for omitted groups.

**Do not silently repair source behavior.** TF-IDF retains Inf and source NaN-to-zero handling; skip finite-only downstream analyses on Inf. Empty variable-feature selection is valid; skip LSI rather than relax cutoffs. Feed original integer counts to LSI, which normalizes internally. Feature selection follows the current 1.17.1 assay-wrapper contract across historical arithmetic profiles. P158 nuclear signal is ambiguous; P187's fragments-in-peaks denominator is unstated; P225 reports FindMarkers and is not relabeled FindMotifs. Other packages and custom paper steps remain separate.

**Scope:** 1679 actual-JAR tests on each of three JVMs, 21 real public analyses, 51 portable verifications, two expected missing-runtime skips and two refusal controls. Historical whole packages/papers and biology are not certified. Some upstream fits/RNG/KDE are captured and audited without independent native replay. Coverage SVG replays; PNG pixels do not. Hashes prove integrity, not execution authenticity. Existing chromVAR receives no duplicate credit. Full implementation remains in progress.

'''

SIGNAC_ACTIONS='''<section id="signac-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Signac workflows: native methods and required recovery</h2>
<p>Use the separate development <code>signac-tools</code> executable. Read <code>SIGNAC_TOOLS.md</code>, <code>examples/signac-normalize/README.md</code> and <code>signac-tools-development/VALIDATION.json</code>. Older canonical/released JARs lack the public route. Choose one of 11 workflows explicitly, including VERIFY. Relative paths resolve against the JSON file; retain attempts and use NEW outputs.</p>
<p><strong>SKIP3: skip dependent analysis if a required runtime is unavailable or installation is forbidden.</strong> Analysis never installs software. Required action: where permitted, provision the exact declared R/package or private MACS/Python profile using its workflow guide, then retry into NEW paths; otherwise retain the skip. MACS calling requires Linux Java, including WSL on Windows. Coverage needs full Java desktop/font/PNG components. Native normalization and the other Java methods need no R. Cell Ranger remains excluded.</p>
<p><strong>FAIL2: no result admitted, including partial files.</strong> Required action: inspect the concrete console cause, <code>WARNINGS.md</code>, status/diagnostics and raw logs; preserve the attempt, correct the named input/profile/integrity/process/capacity problem and retry into NEW paths. Read warnings after exit 0. Partial FindAllMarkers output does not establish negative results for omitted groups.</p>
<p><strong>Retain source edge behavior.</strong> TF-IDF keeps Inf and source NaN-to-zero handling; skip finite-only downstream analyses on Inf. Empty feature selection is valid: skip LSI instead of silently lowering the cutoff. LSI consumes original integer counts and normalizes internally. Selection uses the current 1.17.1 assay-wrapper contract. P158 nuclear signal remains ambiguous; P187 fraction denominator is unstated; P225 FindMarkers is not relabeled FindMotifs. Custom paper steps and other packages remain separate.</p>
<p><strong>Validation:</strong> 1679 actual-JAR tests on each of three JVMs, 21 public analyses, 51 portable VERIFY calls, two expected missing-runtime skips and two refusal controls. The 1373 parent source bindings are preserved. Ten reviewed paper roles include P134's explicit logistic-regression caption and exclude the P007 institutional collision. Historical whole packages/papers and biological validity are not certified. Upstream fits/RNG/KDE are not all independently replayed. SVG replays; PNG pixels do not. Hashes prove integrity, not execution authenticity. Existing chromVAR gets no duplicate tool credit. The broader implementation goal remains active.</p></section>'''

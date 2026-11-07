# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Credit one scoped ChIPseeker integration after complete controls and seven source roles."""
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import verify_source
from collect_omics_review import paragraphs, validate_supporting_evidence
from chipseeker_workflow_evidence import verify as verify_workflow
from meme_discovery_provider_compatibility import verify_meme_review_extension


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def verify(root, data):
    proof = verify_workflow()
    roles = json.loads((data/'chipseeker-role-coverage.json').read_bytes())
    assert roles['status'] == 'passed-seven-ChIPseeker-primary-role-reviews' and roles['applied']
    assert (roles['source_papers_XML_hashes_checked'], roles['prior_reviews_preserved'], roles['new_reviews'],
            roles['total_reviews'], roles['reviewed_roles'], roles['total_observations'],
            roles['ChIPseeker_supporting_pointers']) == (250, 898, 7, 905, 7, 2446, 1)
    verify_source(root, data, 'tools/literature/reviewed-uses.json', roles['reviews_sha256'])
    papers = json.loads((data/'papers.json').read_bytes())
    verify_fixed_cohort(root, data, papers)
    selected = {p['paper_id']: p for p in papers if any(t['tool_id'] == 'chipseeker' for t in p['tools'])}
    assert set(selected) == {'P020', 'P043', 'P044', 'P124', 'P133', 'P139', 'P195'}
    drafts = {d['paper_id']: d for d in json.loads((root/'tools/literature/chipseeker-use-reviews.json').read_bytes())}
    reviews = json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    verify_meme_review_extension(root, data, reviews)
    assert len([r for r in reviews if r['tool_id'] == 'chipseeker']) == 7
    registry = {r['id']: r for r in json.loads((root/'tools/literature/tool-registry.json').read_bytes())}
    for row in roles['source_proof']:
        paper = selected[row['paper_id']]
        tool = next(t for t in paper['tools'] if t['tool_id'] == 'chipseeker')
        review = next(r for r in reviews if r['tool_id'] == 'chipseeker' and r['pmcid'] == paper['pmcid'])
        assert review == drafts[paper['paper_id']]['review']
        assert (paper['pmcid'], paper['doi'], paper['xml_sha256']) == (row['pmcid'], row['doi'], row['xml_sha256'])
        assert tool['evidence_paragraph_index'] == row['paragraph_index']
        assert tool['evidence_paragraph_sha256'] == review['evidence_paragraph_sha256'] == row['paragraph_sha256']
        for key in ['use_summary', 'implementation_scope_note', 'version_reported_near_name', 'supporting_evidence']:
            assert tool[key] == review[key]
        source = root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')
        if source.exists():
            raw = gzip.decompress(source.read_bytes())
            assert sha(raw) == paper['xml_sha256']
            ps = paragraphs(ET.fromstring(raw))
            anchor = next(p for p in ps if p['paragraph_index'] == row['paragraph_index'])
            assert sha(anchor['text'].encode('utf8')) == row['paragraph_sha256']
            validate_supporting_evidence(ps, [review], registry)
    use = lambda pid: next(t for t in selected[pid]['tools'] if t['tool_id'] == 'chipseeker')
    assert [use(p)['version_reported_near_name'] for p in ['P020', 'P043', 'P044']] == ['1.26.2', '1.34.1', '1.22.1']
    assert all(use(p)['version_reported_near_name'] is None for p in ['P124', 'P133', 'P139', 'P195'])
    assert 'annotation release' in use('P133')['implementation_scope_note']
    support = use('P139')['supporting_evidence']
    assert len(support) == 1 and support[0]['paragraph_index'] == 33 and support[0]['named_anchor']['paragraph_index'] == 32
    assert 'distal' in use('P139')['use_summary'].lower() and 'intron' in use('P139')['use_summary'].lower()
    assert not roles['whole_paper_reproduced'] and not proof['whole_goal_complete']
    return proof


def chipseeker_metadata(root, data):
    proof = verify(root, data)
    return dict(
        implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/ChipSeekerToolsCli.java',
        coverage_note='One scoped ChIPseeker integration combines native Java nearest-feature mapping with exact original four-edition genomic annotation; seven paper roles preserve unknown settings and resource identities.',
        implementation_scope='chipseeker-tools NEAREST uses caller-prepared ordered features and unstranded peaks without R; original per-contig query/feature indexing, ties, dropped queries and boundary dummies are retained. ANNOTATE executes original 1.22.1/1.26.2/1.34.1/1.48.1 annotatePeak with an explicit caller-supplied TxDb SQLite, reference/coordinates, level, strand, promoter, priority and flanking choices. Original RDS, genomic labels, distributions, nearest indices/distances and query dispositions are retained; a separate native nearest audit reports its supported domain or an explicit skip. Optional literal annotation-prefix selection is a caller rule. No inferred enhancer definition, automatic annotation retrieval, annoDb, EnsDb, original plotting or full paper pipeline. See CHIPSEEKER_ANNOTATION_DEVELOPMENT.md and CHIPSEEKER_NEAREST_DEVELOPMENT.md.',
        validation_note='202 native tests pass without skips on Windows Java21/25 and Linux Java21. Independent original annotation references retain 224 calls:175 returns and49 original errors. Actual adapter comparisons retain 400 core outcomes across Windows Java21/25 plus24 extended outcomes, including original failures;20976 native nearest triples and16 recovery controls pass. The final root JAR executes the asymmetric -2000/+1000 original annotation example and native Windows/Linux examples. Exact original bodies/formals and critical dependencies are checked, including refusal of a same-version changed function before analysis. Original annotation was validated with Windows R4.6.1; Linux certification covers the native method only. These controls do not establish execution authentication, historical whole environments, biological accuracy or complete papers.',
        project_adapter_available=True, canonical_adapter_available=False,
        development_adapter_cli='chipseeker-tools', development_candidate=proof['artifact']['path'],
        development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../chipseeker-annotation-development/VALIDATION.json',
        source_role_coverage='chipseeker-role-coverage.json', source_roles_reviewed=7,
        native_methods_available=True, original_runtime_adapters_available=True,
        original_reference_versions=['1.22.1', '1.26.2', '1.34.1', '1.48.1'],
        analysis_installs_software=False, whole_paper_reproduced=False,
        historical_whole_packages_reproduced=False, biological_accuracy_certified=False)


CHIPSEEKER_WARNING = '''
## ChIPseeker: genomic annotation, native nearest features and required recovery

Use the separate **chipseeker-tools** development JAR, **CHIPSEEKER_ANNOTATION_DEVELOPMENT.md**, **CHIPSEEKER_NEAREST_DEVELOPMENT.md** and **examples/chipseeker/README.md**. The canonical/released JAR lacks this route. NEAREST needs Java only; ANNOTATE requires the exact declared original edition and admitted R/dependency profile plus a caller-supplied TxDb SQLite and reference/coordinate declaration. No automatic resource choice or software installation occurs during analysis. Cell Ranger remains excluded.

**SKIP3: skip the dependent analysis if required software is unavailable or installation is forbidden.** Required action: where permitted, provision the exact profile described in the development documentation, pass its Rscript/library paths and retry into a NEW output directory. Retain the skip when provisioning is forbidden. Native nearest mapping remains separately available on its documented domain; it does not replace genomic annotation.

**FAIL2: no result admitted, including partial results from a mixed batch.** Required action: preserve the attempt, inspect the named console cause, WARNINGS/status/INCOMPLETE and original worker reports/stdout/stderr, correct the named input/source/options/process/capacity cause and retry into a NEW directory. Require COMPLETED and result_admitted=true; a successful exit from R alone is insufficient. No portable VERIFY command or independent refitting claim is supplied. Logical budgets are not operating-system memory quotas.

**Preserve original behavior and uncertainty.** The 224 independent original annotation calls include 49 source errors; errors, dropped queries, missing values and dummy indices/distances remain explicit. Raw geneChr/geneStrand can be numeric source codes. Whole input populations and feature order affect ties; do not repair IDs or coerce dummy values to real genes. Older editions differ in downstream annotations and priority behavior. A same-version changed original function is refused. Native audit skips are explicit and produce no substitute result.

**Seven paper roles:** P020 reports -2 kb/+1 kb promoters; P043 does not identify the function or orientation of its 3 kb flanks; P044 omits its annotation options; P124/P133/P139/P195 omit the software edition. P133 GENCODE v30 is an annotation release. P139's random-reference set explicitly uses distal/intron labels; its caQTL/no-effect enhancer mask remains unstated. P195's UCSC.hg38.knownGene label does not identify a precise TxDb package. Supply matching resources and explicit settings; do not infer enhancer rules or claim full-paper reproduction.

**Validation scope:** 202 native tests per JVM on Windows Java21/25 and Linux Java21; 424 actual adapter outcome comparisons and 16 recovery controls. Original annotation is validated on Windows R4.6.1 with current admitted dependencies, not a recreated historical environment. Native Linux checks do not certify the original R adapter on Linux. This is one catalog integration. The broader toolbox goal remains active.

'''

# The same recovery text appears near the top of both offline review pages.
CHIPSEEKER_ACTIONS = '''<section id="chipseeker-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>ChIPseeker annotation and native nearest features: required recovery</h2>
<p>Use the separate development <code>chipseeker-tools</code> JAR, <code>CHIPSEEKER_ANNOTATION_DEVELOPMENT.md</code>, <code>CHIPSEEKER_NEAREST_DEVELOPMENT.md</code> and <code>examples/chipseeker/README.md</code>. The canonical/released JAR lacks this route. NEAREST needs Java only. ANNOTATE requires the exact declared original edition and admitted R/dependencies plus a caller-supplied TxDb SQLite, reference and coordinate declaration. Analysis never installs software or selects a resource automatically. Cell Ranger remains excluded.</p>
<p><strong>SKIP3: skip the dependent analysis if required software is unavailable or installation is forbidden.</strong> Required action: where permitted, provision the exact documented profile, pass its Rscript/library paths and retry into a NEW output directory. Otherwise retain the skip. Native nearest mapping remains a separate method with a stated domain; it does not supply genomic classification.</p>
<p><strong>FAIL2: no result admitted, including partial results from a mixed batch.</strong> Required action: preserve the attempt; inspect the named console cause, WARNINGS/status/INCOMPLETE and original worker reports/stdout/stderr; correct the named input/source/options/process/capacity cause and retry into a NEW directory. Require COMPLETED and <code>result_admitted=true</code>. R exit 0 alone is insufficient. There is no portable VERIFY command or independent refitting claim. Logical budgets are not operating-system memory quotas.</p>
<p><strong>Retain source behavior:</strong> 224 independent original annotation calls contain 175 returns and 49 original errors. Preserve errors, dropped queries, missing values, boundary dummy indices/distances and raw numeric geneChr/geneStrand codes. Whole input populations and feature order affect ties. Older editions differ in downstream labels and priority behavior. A same-version changed original function is refused before analysis. Native audit skips stay explicit; do not repair IDs or replace missing/dummy outputs with genes.</p>
<p><strong>Seven paper roles:</strong> P020 reports -2 kb/+1 kb promoters; P043 omits the function and orientation of 3 kb flanks; P044 omits annotation options. P124/P133/P139/P195 omit the software edition. P133 GENCODE v30 describes annotation. P139 explicitly filters its random-reference set to distal/intron labels; its caQTL/no-effect enhancer mask is unstated. P195 does not resolve a precise TxDb package. Declare matching resources/settings; do not infer enhancer rules or full-paper reproduction.</p>
<p><strong>Validation:</strong> 202 native tests per JVM on Windows Java21/25 and Linux Java21; 424 adapter outcome comparisons and 16 recovery controls. Original annotation is validated on Windows R4.6.1 with current admitted dependencies. Linux coverage certifies the native method only. No historical whole environment, biological accuracy or complete paper is certified. This is one scoped catalog integration; the broader toolbox goal remains active.</p></section>'''


if __name__ == '__main__':
    from pathlib import Path
    root = Path(__file__).resolve().parents[2]
    proof = verify(root, root/'docs/literature/omics-2023-2026')
    print(json.dumps(dict(status='passed-ChIPseeker-workflow-and-seven-paper-roles', source_roles=7,
                         artifact=proof['artifact'], whole_goal_complete=False)))

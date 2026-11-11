# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""DIAMOND catalog extension with exact predecessor metadata and live scientific sources."""
import contextlib, copy, gzip, html, json
from cellchat_catalog import *
import cellchat_catalog as prior
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import verify_source, reverse_delta
from run_mrmega_catalog_checks import ArchivedCatalogView

_previous_verify = prior.verify_review_extension


def _current_extension(root, data, reviews):
    roles = json.loads((data/'diamond-role-coverage.json').read_bytes())
    assert roles['status'] == 'passed-five-diamond-primary-role-reviews' and roles['applied']
    assert (roles['prior_reviews'], roles['unchanged_prior_reviews'], roles['new_reviews'],
            roles['total_reviews'], roles['supporting_pointers'], roles['observations']) == (1024, 1024, 5, 1029, 367, 2466)
    assert pin(data/'diamond-prior-reviews.json.gz') == roles['prior_review_archive']
    assert pin(data/'diamond-prior-papers.json.gz') == roles['prior_papers_archive']
    oldraw = gzip.decompress((data/'diamond-prior-reviews.json.gz').read_bytes())
    old = json.loads(oldraw)
    assert byte_pin(oldraw)['sha256'] == roles['before_reviews_sha256'] and len(old['reviews']) == 1024
    draft = root/'tools/literature/diamond-use-reviews.json'
    assert pin(draft) == roles['draft_pin']
    assert pin(root/'tools/literature/curate_diamond_uses.py')['sha256'] == roles['curator_sha256']
    prepared = json.loads(draft.read_bytes())
    assert prepared['status'] == 'five-primary-bound-diamond-role-reviews'
    assert {r['paper_id'] for r in prepared['reviews']} == {'P017', 'P037', 'P054', 'P171', 'P248'}
    expected = copy.deepcopy(old)
    expected['reviews'].extend(r['review'] for r in prepared['reviews'])
    raw = (root/'tools/literature/reviewed-uses.json').read_bytes()
    assert json.loads(raw) == expected and reviews == expected['reviews']
    assert byte_pin(raw)['sha256'] == roles['reviews_sha256']
    assert len(reviews) == len({(r['pmcid'], r['tool_id']) for r in reviews}) == 1029
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews) == 367
    assert all(r['tool_id'] == 'diamond' for r in reviews[1024:])
    verify_source(root, data, 'tools/literature/reviewed-uses.json', roles['before_reviews_sha256'])
    return oldraw


def _predecessor_view(root, data, oldraw):
    relative = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    descriptor = json.loads((data/'diamond-dispatch-shared-source-compatibility.json').read_bytes())
    assert len(descriptor['paths']) == 1
    row = descriptor['paths'][0]
    assert row['path'] == relative and len(row['edits']) == 2
    before = reverse_delta((root/relative).read_bytes(), row)
    verify_source(root, data, relative, row['before_sha256'])
    return ArchivedCatalogView(root, {relative: before, 'tools/literature/reviewed-uses.json': oldraw})


def verify_review_extension(root, data, reviews):
    raw = (root/'tools/literature/reviewed-uses.json').read_bytes()
    roles = json.loads((data/'diamond-role-coverage.json').read_bytes())
    if byte_pin(raw)['sha256'] != roles['reviews_sha256']:
        # Existing verifiers also call this with exact older archived review views.
        # Their own complete historical population/hash checks remain authoritative.
        return _previous_verify(root, data, reviews)
    oldraw = _current_extension(root, data, reviews)
    _previous_verify(_predecessor_view(root, data, oldraw), data, json.loads(oldraw)['reviews'])


def load_catalog_registry(root):
    data = root/'docs/literature/omics-2023-2026'
    verify_review_extension(root, data, json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    oldraw = gzip.decompress((data/'diamond-prior-reviews.json.gz').read_bytes())
    result = prior.load_catalog_registry(_predecessor_view(root, data, oldraw))
    assert len(result) == len({r['id'] for r in result}) == 438
    return result


@contextlib.contextmanager
def current_catalog_review_context():
    saved = prior.verify_review_extension
    try:
        prior.verify_review_extension = verify_review_extension
        with prior.current_catalog_review_context():
            yield
    finally:
        prior.verify_review_extension = saved


def _wrapped(name):
    def metadata(root, data, *extra, **kwargs):
        raw = _current_extension(root, data, json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
        # Only the two explicitly changed shared metadata paths are reconstructed.
        # Every algorithm, worker, resource and independent verifier stays live.
        view = _predecessor_view(root, data, raw)
        with current_catalog_review_context():
            return getattr(prior, name)(view, data, *extra, **kwargs)
    return metadata


for _name in ['current_meme_metadata', 'current_chipseeker_metadata', 'monocle_development_metadata',
    'normal_fit_metadata', 'cicero_metadata', 'soupx_metadata', 'dndscv_metadata', 'smr_metadata',
    'loftee_metadata', 'biomart_metadata', 'hail_metadata', 'leafcutter_metadata', 'cellphonedb_metadata',
    'palantir_metadata', 'scfates_metadata', 'gseapy_metadata', 'hifiasm_metadata', 'rsubread_metadata',
    'sniffles_metadata', 'mrmega_metadata', 'cistarget_metadata', 'cellchat_metadata']:
    globals()[_name] = _wrapped(_name)


def diamond_metadata(root, data):
    verify_review_extension(root, data, json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    from verify_diamond_evidence import verify
    proof, _ = verify()
    artifact = root/'docs/literature/diamond-development/diamond-root-javac21.jar'
    return dict(implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/DiamondToolsCli.java',
        coverage_note='Scoped original DIAMOND protein/translated-query alignment, database creation and native transport checks; exact source profiles, failed source cases and study-cutoff limits remain explicit.',
        implementation_scope='Java diamond-tools dispatch to unchanged original2.0.0/2.0.15/2.1.8 database creation, blastp and standard-code non-frameshift blastx on complete caller-prepared inputs. Compatible14-field output retains the ordinary12-field population plus sequence lengths. Native Java checks complete input records, literal IDs, coordinates, identity/gap counts, coordinate-derived coverage and query dispositions. Existing native databases remain opaque to independent reference-population auditing. Explicit Linux runtime setup/download is separate; analysis installs nothing. Self-contained standalone and root supplement commands are documented in DIAMOND_INTEGRATION.md.',
        validation_note='Five primary-bound roles distinguish reference annotation, all-versus-all comparison, translated queries and downstream methods. Complete synthetic controls compare78 original source-option cases:72 successes and six preserved original failures, plus six existing-database comparisons and one actual explicit-download alignment. Two compiler builds pass216 routed complete saved-bundle checks on Windows21/25 and Linux21,18 failed-bundle refusals, three dependency skips,15 unchanged CellChat predecessor checks and three routed original alignments;22 protocol controls cover missing dependencies, capacity/time failure and rehashed semantic refusals. Earlier extended-field source crashes, verifier-notation failure and explicit wall-time failure remain retained. Original2.0.0 unmasked/composition-zero crashes and tested2.1.8 paired-FASTQ failures admit no result. Positive min-score overrides E-value; top/max-target conflicts and top100 sentinel remain source behavior. Rounded/zero E-values, duplicate IDs and no-hit limits remain visible. No independent alignment/scoring/E-value refit, producer authentication or historical paper reproduction. P017 reports no source version and has different/unspecified coverage denominators; P054 score100 does not resolve raw/bit/external cutoff interpretation. Those study choices require exact specification; downstream biological classification, DBSCAN/KDE, GraftM and mSynOrths remain separate.',
        project_adapter_available=True, canonical_adapter_available=False, source_roles_reviewed=5,
        source_role_coverage='diamond-role-coverage.json', development_candidate=artifact.relative_to(ROOT).as_posix(),
        development_candidate_sha256=pin(artifact)['sha256'], development_cli_class='org.jlinalg.cli.DiamondToolsCli',
        root_cli_class='org.jlinalg.cli.JLinAlgCli', root_command='diamond-tools',
        workflow_proof='docs/literature/diamond-development/VALIDATION.json',
        native_helpers=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['DiamondAudit', 'DiamondSequences', 'DiamondRequest']],
        original_runtime_required=True, analysis_installs_software=False, supported_source_versions=['2.0.0', '2.0.15', '2.1.8'],
        whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False)


DIAMOND_WARNING = '''## DIAMOND: warnings and required actions

Use diamond-tools with the self-contained development JAR, or the root supplement and its separately recorded canonical dependency. Require exit0, result_admitted=true and successful complete --verify. Read DIAMOND_INTEGRATION.md, WARNINGS.md, complete raw hits, input records, query dispositions, provenance and original logs. Original alignments require explicitly selected Linux x86_64 Java and exact2.0.0/2.0.15/2.1.8 runtime profiles; saved verification is portable. Analysis installs nothing.

SKIP3 means skip dependent alignment analysis. Download/install the named exact prerequisites only where allowed, using explicit setup flags and NEW private paths. If forbidden or unavailable, keep analysis skipped. FAIL2 admits no result, including earlier successful steps: preserve the attempt, inspect its input/source/runtime/process/capacity cause and original logs, correct it explicitly and retry NEW paths. Never substitute a version, drop a query file, change failing options or admit partial output automatically. Logical capacities are not hard host quotas or producer authentication.

Original source failures remain visible. Extended-field experiments crashed2.0.15; the admitted compatible14-field output preserves ordinary12-field populations. Tested2.0.0 unmasked/composition-zero runs crashed even with ordinary output. Original2.1.8 rejected the tested equally populated paired FASTQ fixtures, plain and gzip, while single-file queries worked and2.0.15 paired queries succeeded. These failed attempts admit no result and do not establish failure for every possible input. A tiny-block2.0.0 run exceeded an explicit120-second capacity before a separate300-second retry succeeded.

Positive min-score is a bit-score cutoff that overrides E-value filtering. Top below100 conflicts with explicit max-target-seqs; top100 is the ordinary target-limit sentinel, not unlimited reporting. Keep original rounded/zero E-values and identity/bit-score fields. They do not establish exact strict study thresholds or exact zero tail probabilities. Duplicate IDs remain ambiguous; no reported hit is not proof of biological absence. Existing native databases are retained completely but remain opaque to independent reference-population auditing.

P017 reports no DIAMOND version and describes query, target and unspecified coverage denominators in different steps: require the specific source/settings/database and exact threshold interpretation. P054 score100 does not resolve raw versus bit score or an external filter: require the exact study interpretation before claiming that cutoff. Alignment-score-ratio classification, curated reference construction, abundance, DBSCAN/KDE, GraftM, mSynOrths and biological annotation remain separate methods. These scoped controls do not reproduce whole papers or independently refit alignment placement, scoring or E-values. Cell Ranger stays excluded and the full250-paper goal remains active.

'''
DIAMOND_ACTIONS = '<section id="diamond-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>DIAMOND: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in DIAMOND_WARNING.strip().split('\n\n')[1:])+'</section>'

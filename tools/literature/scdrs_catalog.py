# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""scDRS catalog metadata with exact predecessor data/root views and unchanged scientific gates."""
import contextlib
import gzip
import html
import importlib
import json
from mtag_catalog import *
import mtag_catalog as prior
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import reverse_delta, verify_source
from run_mrmega_catalog_checks import ArchivedCatalogView
from scdrs_registry import entry, BASE_PATTERN, PATTERN

_previous_verify = prior.verify_review_extension


def predecessor_view(root, data, restore_catalog=True):
    overrides = {}
    name = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    descriptor = data / 'scdrs-dispatch-shared-source-compatibility.json'
    if descriptor.exists():
        document = json.loads(descriptor.read_bytes())
        assert len(document['paths']) == 1
        row = document['paths'][0]
        assert row['path'] == name and len(row['edits']) == 2
        raw = (root / name).read_bytes()
        overrides[name] = raw if byte_pin(raw)['sha256'] == row['before_sha256'] else reverse_delta(raw, row)
        verify_source(root, data, name, row['before_sha256'])
    if restore_catalog and (data / 'scdrs-prior-reviews.json.gz').exists():
        overrides['tools/literature/reviewed-uses.json'] = gzip.decompress((data / 'scdrs-prior-reviews.json.gz').read_bytes())
        overrides['docs/literature/omics-2023-2026/papers.json'] = gzip.decompress((data / 'scdrs-prior-papers.json.gz').read_bytes())
        ledger = json.loads((data / 'scdrs-prior-metadata-view.json').read_bytes())
        prior_proof = json.loads((data / 'mtag-catalog-validation.json').read_bytes())
        for name, record in ledger['files'].items():
            archive = data / record['archive']
            assert pin(archive) == record['archive_pin']
            raw = gzip.decompress(archive.read_bytes())
            assert byte_pin(raw) == record['decoded_pin'] == prior_proof['catalog_snapshot'][name]
            overrides['docs/literature/omics-2023-2026/' + name] = raw
    return ArchivedCatalogView(root, overrides)


def _old_context(root, data, restore_catalog=True):
    old = predecessor_view(root, data, restore_catalog)
    return old, old / 'docs/literature/omics-2023-2026'


def _current_extension(root, data, reviews):
    coverage = json.loads((data / 'scdrs-source-role-coverage.json').read_bytes())
    assert coverage['status'] == 'PASS_SIX_SCDRS_PRIMARY_ROLES_AND_PLURAL_CENSUS_DRAFT' and coverage['applied']
    assert (coverage['reviewed_roles'], coverage['supporting_pointers'], coverage['observations'], coverage['prior_reviews_preserved']) == (1049, 375, 2476, 1043)
    for n, key in [('scdrs-prior-reviews.json.gz', 'prior_review_archive'), ('scdrs-prior-papers.json.gz', 'prior_papers_archive'),
        ('scdrs-applied-reviews.json', 'applied_reviews'), ('scdrs-registry-source-review.json', 'registry_review')]:
        assert pin(data / n) == coverage[key]
    assert pin(root / 'tools/literature/curate_scdrs_uses.py') == coverage['curator_pin']
    before = gzip.decompress((data / 'scdrs-prior-reviews.json.gz').read_bytes())
    old = json.loads(before)['reviews']
    actual = json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert len(old) == 1043 and actual == reviews and actual[:1043] == old and len(actual) == 1049
    assert len({(r['pmcid'], r['tool_id']) for r in actual}) == 1049
    assert all(r['tool_id'] == 'scdrs' for r in actual[1043:])
    assert sum(len(r.get('supporting_evidence', [])) for r in actual) == 375
    updates = json.loads((data / 'scdrs-applied-reviews.json').read_bytes())['updates']
    assert len(updates) == 6 and [r['review'] for r in updates] == actual[1043:]
    assert pin(root / 'tools/literature/reviewed-uses.json') == coverage['current_reviews'] and pin(data / 'papers.json') == coverage['current_papers']
    old_papers = json.loads(gzip.decompress((data / 'scdrs-prior-papers.json.gz').read_bytes()))
    papers = json.loads((data / 'papers.json').read_bytes())
    assert len(old_papers) == len(papers) == 250
    by = {r['paper_id']: r for r in updates}
    assert sorted(by) == ['P021', 'P024', 'P055', 'P085', 'P123', 'P169']
    for a, b in zip(old_papers, papers, strict=True):
        assert {k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'}
        assert [t for t in a['tools'] if t['tool_id'] != 'scdrs'] == [t for t in b['tools'] if t['tool_id'] != 'scdrs']
        if b['paper_id'] in by:
            row = by[b['paper_id']]
            assert next((t for t in a['tools'] if t['tool_id'] == 'scdrs'), None) == row['before_observation']
            assert next(t for t in b['tools'] if t['tool_id'] == 'scdrs') == row['after_observation']
        else: assert a == b
    assert sum(len(p['tools']) for p in papers) == 2476 and coverage['new_observations'] == 1
    registry = json.loads((data / 'scdrs-registry-source-review.json').read_bytes())
    assert registry['original_pattern'] == BASE_PATTERN and registry['pattern'] == PATTERN
    assert registry['source']['paper_id'] == 'P169' and not registry['original_registry_file_modified']
    assert pin(root / 'tools/literature/tool-registry.json') == registry['original_registry_file']
    assert pin(root / 'tools/literature/scdrs_registry.py') == registry['extension_helper']
    verify_source(root, data, 'tools/literature/reviewed-uses.json', coverage['reviews_before']['sha256'])
    return before


def verify_review_extension(root, data, reviews):
    coverage = data / 'scdrs-source-role-coverage.json'
    if not coverage.exists() or pin(root / 'tools/literature/reviewed-uses.json') != json.loads(coverage.read_bytes())['current_reviews']:
        old, old_data = _old_context(root, data, False)
        return _previous_verify(old, old_data, reviews)
    before = _current_extension(root, data, reviews)
    old, old_data = _old_context(root, data)
    return _previous_verify(old, old_data, json.loads(before)['reviews'])


def load_catalog_registry(root):
    data = root / 'docs/literature/omics-2023-2026'
    verify_review_extension(root, data, json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    old, _ = _old_context(root, data)
    result = prior.load_catalog_registry(old)
    assert len(result) == len({r['id'] for r in result}) == 440
    return [entry(r) if r['id'] == 'scdrs' else r for r in result]


@contextlib.contextmanager
def current_catalog_review_context():
    saved = prior.verify_review_extension
    try:
        prior.verify_review_extension = verify_review_extension
        with prior.current_catalog_review_context(): yield
    finally: prior.verify_review_extension = saved


def _wrapped(name):
    def metadata(root, data, *args, **kwargs):
        old, old_data = _old_context(root, data)
        return getattr(prior, name)(old, old_data, *args, **kwargs)
    return metadata


for _name in ['current_meme_metadata', 'current_chipseeker_metadata', 'monocle_development_metadata', 'normal_fit_metadata', 'cicero_metadata',
    'soupx_metadata', 'dndscv_metadata', 'smr_metadata', 'loftee_metadata', 'biomart_metadata', 'hail_metadata', 'leafcutter_metadata',
    'cellphonedb_metadata', 'palantir_metadata', 'scfates_metadata', 'gseapy_metadata', 'hifiasm_metadata', 'rsubread_metadata',
    'sniffles_metadata', 'mrmega_metadata', 'cistarget_metadata', 'cellchat_metadata', 'diamond_metadata', 'snprelate_metadata', 'genesis_metadata', 'mtag_metadata']:
    globals()[_name] = _wrapped(_name)


def _direct(module, name):
    original = getattr(importlib.import_module(module), name)
    def metadata(root, data, *args, **kwargs):
        old, old_data = _old_context(root, data)
        return original(old, old_data, *args, **kwargs)
    return metadata


# Global summary/inventory checks must see the same exact catalog as their
# archived paper/review inputs. Scientific paths still resolve to the live tree.
for _module, _name in [
    ('catalog_shared_build_context', 'fuma_metadata'), ('catalog_shared_build_context', 'mashr_metadata'),
    ('eagle_catalog', 'eagle_metadata'), ('shapeit_catalog', 'shapeit_metadata'), ('iqtree_catalog', 'iqtree_metadata'),
    ('mafft_catalog', 'mafft_metadata'), ('pal2nal_catalog', 'pal2nal_metadata'), ('fastp_catalog', 'fastp_metadata'),
    ('nextflow_current_catalog', 'nextflow_metadata'), ('salmon_current_catalog', 'salmon_metadata'),
    ('tomtom_catalog', 'tomtom_metadata'), ('bigwig_catalog', 'bigwig_metadata'), ('stringtie_catalog', 'stringtie_metadata'),
    ('chromvar_catalog', 'chromvar_metadata'), ('deseq2_catalog', 'deseq2_metadata'), ('signac_catalog', 'signac_metadata'),
    ('htseq_catalog', 'htseq_metadata'), ('repeat_catalog', 'repeat_metadata'), ('scvi_catalog', 'scvi_metadata'),
    ('quantification_catalog', 'quantification_metadata')]:
    globals()[_name] = _direct(_module, _name)


def scdrs_metadata(root, data):
    _current_extension(root, data, json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    from verify_scdrs_workflow_evidence import inspect, HOME
    proof, files = inspect()
    artifact = HOME / 'scdrs-root-javac21.jar'
    return dict(implementation_status='upstream-adapter-validated', implementation_source='src/main/java/org/jlinalg/cli/ScdrsToolsCli.java',
        coverage_note='Scoped original scDRS 1.0.0/1.0.1/1.0.2 workflows with separate native numerical components. Six primary paper roles reviewed, including the previously missed plural scDRSs in P169; unreported settings remain explicit.',
        implementation_scope='Typed scdrs-tools supervises unchanged original scDRS 1.0.0/1.0.1/1.0.2 CLI functions in an exact private Linux Python 3.10.16 runtime: gene-set munging, single-dataset H5AD preprocessing/scoring, matched controls, covariates, proportion adjustment, group association/Geary heterogeneity, annotation correlation and gene prioritization. Native Java audits all output coordinates, identifiers, finite values, source processes and provenance; optional group BH uses one trait/annotation family in this dataset with strict q<cutoff. Original scoring, LOESS, covariate fitting and graphs remain upstream. Ordinary Pearson coefficients are separate from corr_mcp/corr_mcz. No automatic batch correction, MAGMA input preparation, covariate repair, historical version replacement or control reduction. Broader package and whole-paper reproduction remain unfinished. Read SCDRS_INTEGRATION.md.',
        validation_note='23 original CLI controls cover 877998 byte-identical numerical/gene-weight coordinates and 69 saved JVM checks, including 1000 genes/1000 controls in 1.0.1 and 1.0.2. One staged root workflow and 3 saved root checks preserve predecessor scientific bytes. 36 refusal/tamper controls admit no failed result. Native explicit-family BH matches 67161 double q values and 67161 strict cutoff decisions across 3 JVMs. All 24 packaged bundles replay on Windows Java 21/25 and Linux Java 21 without Python fitting. Synthetic controls do not establish biological validity, producer authenticity, independent full-model refitting, historical author commits or large-atlas performance.',
        project_adapter_available=True, canonical_adapter_available=False, source_roles_reviewed=6, source_role_coverage='scdrs-source-role-coverage.json',
        development_candidate=artifact.relative_to(ROOT).as_posix(), development_candidate_sha256=pin(artifact)['sha256'],
        development_cli_class='org.jlinalg.cli.ScdrsToolsCli', root_cli_class='org.jlinalg.cli.JLinAlgCli', root_command='scdrs-tools',
        workflow_proof='docs/literature/scdrs-workflow-development/VALIDATION.json', native_proof='docs/literature/scdrs-native-development/validation.json',
        native_components=['ScdrsScores'], supported_source_versions=['1.0.0', '1.0.1', '1.0.2'],
        original_runtime_required=True, analysis_installs_software=False, runtime_setup_export_available=True, download_install_available=True,
        whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False)


SCDRS_WARNING = '''## scDRS: warnings and required actions

Read SCDRS_INTEGRATION.md for scdrs-tools, complete typed requests and separate native Java components. The root supplement requires the separately pinned canonical 0.3.7 JAR; the released executable alone has no new command. Original analysis requires Linux x86_64, including permitted WSL. Windows Java 21/25 and Linux Java 21 can verify completed bundles without Python or refitting.

SKIP3: SKIP dependent analysis when exact permitted software or capabilities are missing or forbidden. Export --export-runtime-helper NEW_DIRECTORY. Use Linux Python 3.11+ to prepare the private Python 3.10.16 runtime and explicitly chosen original 1.0.0, 1.0.1 or 1.0.2 source. Downloads/installation require explicit --allow-download/--allow-install and NEW permitted paths; offline archives and exact existing private environments are supported. If installation is forbidden or cannot be completed, leave analysis skipped. Setup runs no analysis; analysis installs nothing. Runtime/source inventories are checked before and after execution.

FAIL2: NO RESULT ADMITTED for invalid input, integrity, capacity, process or unassessable/nonfinite output, including partial original files. Require wrapper exit0, result_admitted=true, COMPLETED.json and successful complete --verify. Read WARNINGS.md, session.json, analysis-summary.json, inspection/input-contract.json, identifier-dispositions.tsv and original logs. Preserve the entire attempt and unadmitted-original files, resolve the named cause and retry NEW paths. Do not repair IDs/NaN/Inf, encode covariates, replace historical versions or reduce controls automatically. Hashes establish integrity, not producer authenticity; saved verification does not independently refit LOESS, covariates, scores or graphs.

Historical 1.0.0/1.0.1 text-covariate failures are retained. Version 1.0.2 uses its original categorical encoding and covariate imputation with warnings and disposition records. Expression/score nonfinite values are refused. Fewer than 10 overlapping genes is a source skip. Munging floors P at 1e-100, caps converted Z at 10 and writes weights with five significant digits; supplying raw MAGMA Z weights directly is a distinct input decision. Choose original normalization/filtering flags from input provenance. No automatic batch correction, variant exclusion, MAGMA ranking or genome/LD reference is inferred.

Scientific outputs must be distinguished: original group association uses the 95th percentile; corr_mcp/corr_mcz are Monte Carlo quantities rather than ordinary Pearson r. Optional native BH uses one trait and one annotation across all tested cell types within this single dataset, with strict q<declared cutoff. Keep datasets and strata separate. Review literal filtering/intersection and covariate disposition ledgers; do not silently repair identities or pool correction families.

Declare sufficient resource capacities. Working-memory preflight includes dense preprocessing, cell/control matrices and retained control-gene/weight lists. Logical ceilings include 1 million cells, 200000 genes, 10 billion nonzeros and 200 billion matrix elements, but these are not OS RAM quotas or evidence of atlas execution. Native numerical components have a separate 50 million cell/control-pair ceiling. Controls include 1000 disease genes with 1000 control sets, not a 500000-cell atlas benchmark. An admitted declaration alone certifies no memory/time feasibility or scientific result.

Six source-bound paper roles retain distinct settings. P021 explicitly uses 1.0.1 and 200 controls; P085 uses 1.0.0 and 1000 controls; P055/P123 report 1.0.2; P024 leaves the version unstated. P169 writes scDRSs and reports 1.0.1 with separate Tabula Sapiens/Gut Cell Atlas inputs, but no exact command, control count or preprocessing. Do not infer missing settings from software defaults or a release label. Version labels do not establish author commits. Cell Ranger remains excluded. The full 250-paper implementation goal and paper-wide semantic review remain unfinished.

'''
SCDRS_ACTIONS = '<section id="scdrs-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>scDRS: warnings and required actions</h2>' + ''.join('<p>' + html.escape(p) + '</p>' for p in SCDRS_WARNING.strip().split('\n\n')[1:]) + '</section>'

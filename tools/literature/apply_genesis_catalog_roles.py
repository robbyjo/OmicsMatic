# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Update only five GENESIS implementation-scope notes and apply a separately reviewed supplementary-source pointer."""
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import byte_pin
from curate_shapeit_uses import inverse, serialize
from catalog_source_integrity import reverse_delta, verify_source

PAPERS = ['P128', 'P130', 'P142', 'P191', 'P219']
COMMON = 'Scoped original GENESIS2.42.0 PC-AiR, supplied-PC PC-Relate, null models and single-variant association dispatch are validated with typed requests and native admission checks; use GENESIS_INTEGRATION.md. Current2.42.0 does not reproduce a historical or unstated paper version/settings. '
LIMITS = {
    'P128': 'Supplementary Note7 was reviewed separately: it explicitly reports historical GENESIS2.16.1 pcairPartition with kinship cutoff0.044. The additional primary-source receipt is linked in genesis-source-role-coverage.json; this fact is not attributed to the JATS paragraph. Subsequent PCA, adjusted-kinship and PRS-model function/settings remain unstated. Preserve the paper-reported KING IBD wording without relabelling it KING-robust.',
    'P130': 'The paper reports GENESIS2.26.0 PC-AiR and ten PCs; exact partition/PCA settings remain unstated. The separate collective SNPRelate/GWASTools/POLMM/GRAB/SAIGE package list does not identify a GENESIS null-model fit.',
    'P142': 'The paper reports cis/trans GLMM tests with a sparse fourth-degree PC-Relate matrix, but no exact GENESIS version, fitting call or sparsification settings. Do not infer a numerical cutoff or replace the reported model with a generic regression.',
    'P191': 'Non-European PC-AiR uses separately attributed SNPRelate KING-robust relatedness. GENESIS version/settings remain unstated; the European PLINK2 PCA and SAIGE sparse-GRM steps stay separate.',
    'P219': 'The source reports twenty PCs with common HapMap3 SNP/long-range-LD exclusions but does not identify the GENESIS function or version. The current twenty-PC control does not establish that the paper used PC-AiR.',
}


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    base = ROOT / 'build/literature-review/genesis-runtime'; assert not out.exists() and out.is_relative_to(base); out.mkdir(parents=True)
    root = json.loads((base / 'root-examples-validation-2/COMPLETED.json').read_bytes())
    assert root['status'] == 'PASS_GENESIS_ROOT13_LITERAL_ANALYSES11_ADMITTED2_REFUSED33_SAVED_REPLAYS12_HELPS1_SKIP'
    branch = json.loads((base / 'extended-61-branch-controls-1/COMPLETED.json').read_bytes())
    assert branch['status'] == 'PASS61_EXTENDED_GENESIS_DECLARED_BRANCH_DISPOSITIONS_53_POSITIVE_AND8_REFUSALS'
    data = ROOT / 'docs/literature/omics-2023-2026'; review_file = ROOT / 'tools/literature/reviewed-uses.json'; paper_file = data / 'papers.json'
    before = dict(reviews=review_file.read_bytes(), papers=paper_file.read_bytes()); old_reviews = json.loads(before['reviews']); old_papers = json.loads(before['papers'])
    reviews = copy.deepcopy(old_reviews); papers = copy.deepcopy(old_papers)
    assert len(reviews['reviews']) == 1038 and len(papers) == 250 and sum(len(p['tools']) for p in papers) == 2475
    receipt = ROOT / 'docs/literature/genesis-workflow-development/P128-supplement-source-review.json'; supplement = json.loads(receipt.read_bytes())
    assert supplement['status'] == 'REVIEWED_PRIMARY_SUPPLEMENTARY_NOTE7_ADDITIONAL_EVIDENCE_NOT_CATALOG_APPLIED' and supplement['paper_id'] == 'P128'
    assert any(r.get('explicit_function') == 'pcairPartition' and r.get('reported_kinship_cutoff') == .044 and r['reported_version'] == '2.16.1' for r in supplement['reviewed_roles'])
    pdf = base / 'P128-supplement-intake-2/41588_2026_2722_MOESM1_ESM.pdf'; assert pin(pdf) == supplement['pdf']
    by = {p['pmcid']: p for p in papers}; updates = []
    for old, new in zip(old_reviews['reviews'], reviews['reviews'], strict=True):
        if new['tool_id'] != 'genesis':
            continue
        paper = by[new['pmcid']]; name = paper['paper_id']; assert name in PAPERS and old['implementation_scope_note'].startswith('GENESIS workflows remain unimplemented.')
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes()); assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        new['implementation_scope_note'] = COMMON + LIMITS[name]
        assert {k: v for k, v in new.items() if k != 'implementation_scope_note'} == {k: v for k, v in old.items() if k != 'implementation_scope_note'}
        observation = next(t for t in paper['tools'] if t['tool_id'] == 'genesis'); assert observation['evidence_paragraph_sha256'] == new['evidence_paragraph_sha256']
        observation['implementation_scope_note'] = new['implementation_scope_note']; updates.append(dict(paper_id=name, before=old, after=new))
    assert sorted(r['paper_id'] for r in updates) == PAPERS and len(updates) == 5
    assert len(reviews['reviews']) == len({(r['pmcid'], r['tool_id']) for r in reviews['reviews']}) == 1038
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews['reviews']) == 369
    for old, new in zip(old_papers, papers, strict=True):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        for a, b in zip(old['tools'], new['tools'], strict=True):
            assert a == b if b['tool_id'] != 'genesis' else {k: v for k, v in a.items() if k != 'implementation_scope_note'} == {k: v for k, v in b.items() if k != 'implementation_scope_note'}
    paths = {name: data / name for name in ['genesis-prior-reviews.json.gz', 'genesis-prior-papers.json.gz', 'genesis-applied-scope-reviews.json', 'genesis-source-role-coverage.json', 'genesis-reviews-shared-source-compatibility.json']}
    assert not any(path.exists() for path in paths.values())
    for name in ['papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md']:
        (out / name).write_bytes((data / name).read_bytes())
    after = dict(reviews=serialize(reviews, before['reviews']), papers=serialize(papers, before['papers']))
    for key in ['reviews', 'papers']:
        paths['genesis-prior-' + key + '.json.gz'].write_bytes(gzip.compress(before[key], mtime=0))
    save(paths['genesis-applied-scope-reviews.json'], dict(status='five-GENESIS-scope-updates-primary-use-summaries-unchanged', updates=updates))
    row = inverse(before['reviews'], after['reviews']); row['path'] = 'tools/literature/reviewed-uses.json'; assert reverse_delta(after['reviews'], row) == before['reviews']
    save(paths['genesis-reviews-shared-source-compatibility.json'], dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='genesis',
        scope='Only five GENESIS implementation-scope notes. Every primary usage summary, paragraph hash, supporting pointer, other review and scientific source is unchanged. Supplementary facts have separate provenance.', paths=[row]))
    assert review_file.read_bytes() == before['reviews'] and paper_file.read_bytes() == before['papers']
    review_file.write_bytes(after['reviews']); paper_file.write_bytes(after['papers'])
    proof = dict(schema_version=1, status='PASS_FIVE_GENESIS_SCOPES_AND_SEPARATE_PRIMARY_SUPPLEMENT_POINTER', applied=True, papers=PAPERS,
                 reviewed_roles=1038, supporting_pointers=369, observations=2475, primary_use_summaries_changed=False,
                 prior_review_archive=pin(paths['genesis-prior-reviews.json.gz']), prior_papers_archive=pin(paths['genesis-prior-papers.json.gz']),
                 reviews_before=byte_pin(before['reviews']), papers_before=byte_pin(before['papers']), current_reviews=pin(review_file), current_papers=pin(paper_file),
                 applied_updates=pin(paths['genesis-applied-scope-reviews.json']), curator_pin=pin(Path(__file__)),
                 separate_primary_supplement=dict(path=receipt.relative_to(ROOT).as_posix(), **pin(receipt), source_pdf=supplement['pdf'], source_url=supplement['source_url'],
                     pdf_page_1based=33, printed_page=32, explicit_function='pcairPartition', reported_version='2.16.1', reported_kinship_cutoff=.044,
                     pdf_or_article_prose_redistributed=False, downstream_unreported_functions_not_inferred=True),
                 new_catalog_credit=False, whole_goal_complete=False)
    save(paths['genesis-source-role-coverage.json'], proof); verify_source(ROOT, data, row['path'], row['before_sha256'])
    save(out / 'COMPLETED.json', proof); print(proof['status'])


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable complete primary-source, metadata-only or prior-review preservation checks')
    main()

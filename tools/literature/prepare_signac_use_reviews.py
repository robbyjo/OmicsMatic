# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Draft ten checked Signac paper roles without changing the catalog or granting tool credit."""
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from catalog_fixed_cohort import verify_fixed_cohort
from collect_omics_review import paragraphs, validate_supporting_evidence

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / 'tools/literature'
DATA = ROOT / 'docs/literature/omics-2023-2026'
BASE = ROOT / 'build/literature-review/signac-runtime'
# Summary, implementation boundary, explicit reported edition, additional named paragraphs.
NOTES = {
    'P016': (
        'Used Signac 1.6.0 with Seurat 4.1.0 for downstream analysis of joint single-nucleus accessibility and expression data after Cell Ranger ARC processing.',
        'The named Methods paragraph gives a general downstream role and does not specify every Signac call. Seurat weighted nearest neighbors and joint visualization remain separate; Cell Ranger ARC remains user-excluded. Native arithmetic source profiles do not reproduce the whole historical package environment.',
        '1.6.0', [2]),
    'P039': (
        'Used Signac 1.9.0 to import ATAC data, call peaks with MACS2 2.2.7.1, and construct FeatureMatrix counts. RunTFIDF and RunSVD provided LSI; the analysis used dimensions 2–30. Created a motif object with human JASPAR2020, ran chromVAR, and computed GeneActivity.',
        'Declare the actual motif/reference resources and MACS runtime explicitly. RNA median-library scaling and log transformation of gene scores, Seurat multimodal neighbors, and downstream interpretation are separate operations. Native GeneActivity returns a raw accessibility proxy, not measured RNA; current dependency profiles do not certify historical whole-package equivalence.',
        '1.9.0', [78, 79]),
    'P051': (
        'Used Signac 1.14.0 with Seurat for multiome QC and ATAC annotations. Quantified a common peak set with FeatureMatrix, called group-specific peaks with MACS2 2.2.9.1, then applied RunTFIDF, FindTopFeatures with count cutoff 5, and RunSVD.',
        'Supply the mm10/Ensembl79 annotations and explicit cell/group assignments. Standard-chromosome and blacklist filtering, Seurat merging, and author group prediction thresholds remain separate. Count cutoff 5 uses a strict greater-than rule; it is not a five-percent selection. Historical source functions on current dependencies are scoped controls, not complete old packages.',
        '1.14.0', [92, 96, 97, 98]),
    'P094': (
        'Used Signac 1.14.0 with Seurat 5.0 for preprocessing and normalization of joint snRNA/ATAC-seq count matrices produced by Cell Ranger ARC.',
        'The paragraph does not assign every preprocessing step to a named Signac function. Preserve the general role and explicit edition rather than infer undocumented thresholds or calls. Seurat RNA processing and Cell Ranger ARC are separate; the latter remains user-excluded.',
        '1.14.0', []),
    'P099': (
        'Used Signac 1.7.0 RunTFIDF and RunSVD for peak-count LSI and used dimensions 2–30 for UMAP. The motif workflow used chromVAR 1.16.0 with cisBP-derived motifs and Signac processing.',
        'Differential accessible peaks were tested with Monocle2, not Signac marker inference. Custom 0–10 motif rescaling, aggregation and Pearson correlations remain separate. Subcluster motif scanning reports motifmatchr 1.16.0; the available current scanner profile does not certify that older scanner edition. Sampling and UMAP settings stay explicit.',
        '1.7.0', [47]),
    'P134': (
        'Reported differential accessible peaks from logistic regression implemented in Signac in the Figure 5g analysis of lineage-associated states.',
        'The retained evidence is an explicit analysis statement in a Results figure caption; it does not state a Signac edition or complete model/covariates. The public markers workflow exposes guarded current Seurat logistic inference with declared groups, layers and covariates. Bootstrap lineage analysis, VIPER, GSEA and enrichR in the same caption remain separate. No whole-paper or historical-model equivalence is inferred.',
        None, []),
    'P153': (
        'Used Signac with Seurat for multiome QC. Used FeatureMatrix to quantify MACS2 2.2.9 peaks intersecting ABC candidate regions, then BinarizeCounts before a custom Kendall correlation analysis.',
        'The Kendall tau-b estimator and Seurat RNA NormalizeData are separate from Signac feature quantification/binarization; do not substitute LinkPeaks or logistic regression for them. Candidate-region intersection, reference identity and dataset-specific QC thresholds require explicit prepared inputs. Signac edition is unstated.',
        None, [47]),
    'P158': (
        'Used Signac with Seurat for scATAC-seq processing and QC after Cell Ranger ATAC. Derived a gene-activity matrix for downstream cell annotation with MapMyCells.',
        'The source reports a nuclear signal threshold below 4; its exact metric is ambiguous and must not silently become NucleosomeSignal. Retain the reported gene/cell filter wording and obtain effective definitions before applying paper-exact filters. MapMyCells, targeted barcode processing, RNA integration and RNA marker tests are separate. Cell Ranger remains excluded; Signac edition is unstated.',
        None, []),
    'P187': (
        'Used Signac to quantify accessibility over bulk ATAC-derived peaks in 138 individuals and to support nuclei QC, including fragments in peaks and TSS enrichment using EnsDb.Hsapiens.v86 annotations.',
        'The reported filters include more than 500 fragments in peaks, more than 15 percent fragments in peaks and TSS enrichment above 2. The exact fraction denominator and effective ratio procedure are not stated; do not infer them from read-support counts or automatically apply the filter. Supply the explicit annotation/reference and interval identities. Signac edition is unstated.',
        None, []),
    'P225': (
        'Used Signac 1.9 for multiome preprocessing/QC, AddMotifs annotation, reported motif overrepresentation through FindMarkers, and CoveragePlot visualization of accessibility with gene annotations. Used chromVAR 1.5 separately.',
        'Preserve the reported FindMarkers wording instead of relabeling it FindMotifs. Declare the actual tested representation and statistical model; motif enrichment on supplied populations is a distinct workflow. chromVAR 1.5 is not an admitted runtime profile. Harmony integration, SCENIC comparison and custom GSEA/hypergeometric analyses remain separate. Native coverage supports scoped plot data/SVG, with PNG pixel replay explicitly outside verification.',
        '1.9', [12]),
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def write(path, value):
    assert not path.exists(), 'Preserve prior drafts before generating another revision: ' + str(path)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf8', newline='\n')


def main():
    papers = json.loads((DATA / 'papers.json').read_bytes())
    verify_fixed_cohort(ROOT, DATA, papers)
    registry = {r['id']: r for r in json.loads((HERE / 'tool-registry.json').read_bytes())}
    audit = json.loads((BASE / 'primary-use-audit-3/PRIMARY-USES.json').read_bytes())
    assert audit['papers_scanned'] == 250 and audit['nonsoftware_name_collisions'] == 1
    by_paper = {p['paper_id']: p for p in papers}
    records = {r['paper_id']: r for r in audit['records']}
    drafts = []
    proof = []
    declaration = None
    for pid, (summary, scope, version, supporting) in NOTES.items():
        paper = by_paper[pid]
        row = records[pid]
        raw = gzip.decompress((BASE.parent / 'xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert sha(raw) == paper['xml_sha256'] == row['xml_sha256']
        ps = paragraphs(ET.fromstring(raw))
        by_index = {p['paragraph_index']: p for p in ps}
        index = row['selected']['evidence_paragraph_index'] if row['selected'] else 22
        anchor = by_index[index]
        digest = sha(anchor['text'].encode('utf8'))
        assert re.search(registry['signac']['pattern'], anchor['match_text'], re.I)
        if row['selected']:
            assert digest == row['selected']['evidence_paragraph_sha256']
        else:
            assert pid == 'P134' and digest == '3d83dad35fe3fd285bbdcc7da9e3ce6e314ead05c4ed9469b57eb97cdafd2707'
            assert re.search(r'logistic regression.{0,80}Signac', anchor['text'], re.I)
            declaration = dict(paper_id=pid, pmcid=paper['pmcid'], doi=paper['doi'],
                xml_sha256=paper['xml_sha256'], paragraph_index=index, section=anchor['section'],
                paragraph_sha256=digest, usage_context='analysis-method-description',
                analysis_role='Logistic-regression differential accessibility',
                reason='Explicit statistical implementation in the Figure5g Results caption; not an availability list or inferred nearby method.')
        pointers = [dict(paragraph_index=i, section=by_index[i]['section'],
            paragraph_sha256=sha(by_index[i]['text'].encode('utf8'))) for i in supporting]
        review = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='signac',
            evidence_paragraph_sha256=digest, use_summary=summary, implementation_scope_note=scope,
            version_reported_near_name=version, usage_context='analysis-method-description', supporting_evidence=pointers)
        validate_supporting_evidence(ps, [review], registry)
        drafts.append(dict(paper_id=pid, review=review))
        proof.append(dict(paper_id=pid, xml_sha256=paper['xml_sha256'], anchor_paragraph_index=index,
            anchor_paragraph_sha256=digest, supporting_pointers=len(pointers)))
    assert len(drafts) == 10 and declaration and 'P007' not in NOTES
    write(HERE / 'signac-use-reviews.json', drafts)
    write(HERE / 'signac-source-additions.json', dict(schema_version=1,
        status='checked-one-explicit-Signac-analysis-caption', declarations=[declaration],
        frozen_base_collector_sha256=sha((HERE / 'collect_omics_review.py').read_bytes()),
        existing_reviewed_collector_sha256=sha((HERE / 'collect_reviewed_omics.py').read_bytes()),
        institutional_SignAC_excluded='P007', implementation_credit=False))
    write(BASE / 'primary-use-audit-3/DRAFT-REVIEW-PROOF.json', dict(schema_version=1,
        status='drafted-ten-primary-Signac-roles-not-applied', source_audit_pin=dict(
            path='primary-use-audit-3/PRIMARY-USES.json', sha256=sha((BASE / 'primary-use-audit-3/PRIMARY-USES.json').read_bytes())),
        reviewed_roles=10, supporting_pointers=sum(p['supporting_pointers'] for p in proof),
        source_proof=proof, implementation_credit=False, catalog_changed=False, whole_paper_reproduced=False))
    print('Drafted10 exact Signac roles, including P134 explicit LR caption; excluded P007 institutional collision; catalog unchanged')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact primary-source checks')
    main()

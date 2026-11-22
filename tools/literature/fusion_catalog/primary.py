"""Full-cohort FUSION name/command census with explicit homonym classifications."""
from pathlib import Path
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, words
from gsea_catalog.primary import identities, EXTRACTOR_SHA

ROOT = Path(__file__).resolve().parents[3]
NAMED = re.compile(r'(?<![A-Za-z0-9_])FUSION(?![A-Za-z0-9_])')
SPECIFIC = re.compile(r'fusion_twas|FUSION\.(?:assoc_test|compute_weights|post_process)|gusevlab\.(?:org|github\.io)/projects/fusion|gusevlab/fusion', re.I)
DECISIONS = {
    'P108': ('method-baseline-description; upstream-execution-unconfirmed', [36],
             'Describes a univariate FUSION baseline selecting elastic-net, BLUP or SuSiE predictions per isoform by adjusted R-squared. This is a method/baseline description; exact execution of original upstream scripts is not established.'),
    'P110': ('not-software; imaging-instrument-homonym', [54],
             'FUSION Solo S is the western-blot imaging instrument, not the TWAS software.'),
    'P112': ('software-analysis-description', [32],
             'Used FUSION for CanUD TWAS with adult/fetal cortex expression models, EUR 1000 Genomes reference LD and a weighted gene-association test. Complete software edition/caller flags are unreported.'),
    'P120': ('software-analysis-description', [28],
             'Used FUSION TWAS and its internal COLOC analysis for hypothyroidism gene mapping with six GTEx v8 tissue models. The reported gene-test and PP4 thresholds remain paper settings, not inferred command defaults.'),
    'P131': ('software-analysis-description', [16,45],
             'Used univariate FUSION TWAS with default settings and 45 European GTEx v8 tissue weights before genomic SEM T-SEM. Genomic SEM 0.0.5 is a separate package edition, not a FUSION edition.'),
    'P150': ('not-software; cohort-homonym', [59],
             'FUSION names the skeletal-muscle eQTL validation study, not execution of the TWAS software.'),
    'P181': ('software-training-and-association-description', [15,34],
             'Used FUSION to train DDP/rDDP transcriptomic prediction models and test xTWAS across 114 traits; Pantry KDP comparisons and coloc follow-up remain distinct operations. Complete edition/caller flags are unreported.'),
    'P183': ('software-analysis-description', [19,30,31],
             'Used FUSION-TWAS for lncRNA/protein-coding gene prioritization with European 1KGP3 reference LD and stated defaults. CircRNA FUSION-TWAS was explicitly not performed; the HRS reference belongs to separate SMR/OPERA analyses.'),
    'P212': ('not-software; cohort-homonym', [6,8,10,11,12,13,25,26,28],
             'FUSION is the Finland-United States Investigation of Non-Insulin Dependent Diabetes Mellitus cohort. Its samples/eQTLs are analyzed with APEX and other named tools; this does not establish use of FUSION TWAS software.'),
    'P238': ('shared-method-description-and-software-comparator', [11,28,37],
             'Describes the ImpG-Summary-style summary association algorithm also used in FUSION, and separately compares SpliTWAS with LeafCutter paired with FUSION. Shared algorithm wording alone does not establish an original-script execution or a complete FUSION workflow.'),
}


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def audit(cache):
    require(pin((ROOT / 'tools/literature/collect_omics_review.py').read_bytes())['sha256'] == EXTRACTOR_SHA,
            'Frozen source extractor changed')
    sources = identities()
    require(len(sources) == 250, 'Require full 250-paper cohort')
    selected, tables = {}, []
    for identity in sources:
        path = Path(cache) / (identity['pmcid'] + '.xml.gz')
        require(path.stat().st_size <= 64 * 1024 * 1024, 'Compressed source capacity exceeded')
        with gzip.open(path, 'rb') as stream:
            raw = stream.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024 and pin(raw)['sha256'] == identity['xml_sha256'], 'Primary XML changed')
        tree = ET.fromstring(raw)
        dois = [x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type') == 'doi']
        require(len(dois) == 1 and dois[0].lower() == identity['doi'].lower(), 'Primary DOI changed')
        found = []
        for block in paragraphs(tree):
            text = block.get('match_text', block['text'])
            if NAMED.search(text) or SPECIFIC.search(text):
                found.append(dict(paragraph_index=block['paragraph_index'], section=block['section'],
                                  paragraph_sha256=pin(block['text'].encode())['sha256'],
                                  uppercase_name=bool(NAMED.search(text)), command_or_URL=bool(SPECIFIC.search(text))))
        if found:
            selected[identity['paper_id']] = dict(**{k: identity[k] for k in ['paper_id','pmcid','doi','xml_sha256']}, anchors=found)
        for table in tree.iter('table-wrap'):
            text = words(table)
            if NAMED.search(text) or SPECIFIC.search(text):
                tables.append(dict(paper_id=identity['paper_id'], table_id=table.get('id'), text_sha256=pin(text.encode())['sha256']))
    require(set(selected) == set(DECISIONS), 'New/missing FUSION candidate requires individual inspection')
    require([(r['paper_id'], r['table_id']) for r in tables] == [('P212','tbl1')], 'Changed matching table population')
    decisions = []
    for paper_id, (classification, indices, summary) in DECISIONS.items():
        candidate = selected[paper_id]
        require([a['paragraph_index'] for a in candidate['anchors']] == indices, 'Changed complete candidate anchors: ' + paper_id)
        decisions.append(dict(**candidate, classification=classification, source_summary=summary,
                              upstream_edition=None, runtime_qualified=False, whole_paper_reproduced=False))
    return dict(schema_version=1, status='PASS_FULL250_FUSION_SOURCE_CANDIDATE_AND_HOMONYM_AUDIT_ONLY',
                papers_checked=250, identities=[{k:r[k] for k in ['paper_id','pmcid','doi','xml_sha256']} for r in sources],
                candidates=decisions, table_candidates=tables,
                table_classification='P212 Table 1 reports cohort eQTL counts, not FUSION TWAS software',
                actual_software_analysis_or_comparator_papers=['P112','P120','P131','P181','P183','P238'],
                additional_method_baseline_paper='P108', original_upstream_execution_in_P108_unconfirmed=True,
                canonical_false_positive_to_review='P212', canonical_missing_baseline_to_review='P108',
                new_family_credit=0, canonical_reviews_added=0, canonical_catalog_modified=False,
                whole_goal_complete=False)

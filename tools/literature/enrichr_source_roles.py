"""Frozen-cohort Enrichr source census; does not admit implementations or metadata."""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DATA = 'docs/literature/omics-2023-2026'
BASE = '4881cdaa578f7bf7955328f6675fb0be45d39400'
PATTERN = re.compile(r'(?<![A-Za-z0-9_])enrichr(?![A-Za-z0-9_])', re.I)

# Explicit human decisions, not defaults inferred from the software name.
# Anchor indices refer to the established paragraph extractor, including its
# retained captions embedded inside body paragraphs. Raw paragraph hashes are
# saved; a caption is never relabelled as a Methods paragraph.
DECISIONS = {
    'P005': ([54], 'analysis', 'unspecified', [],
        ['GO_Biological Process_2018'],
        'Tested GO enrichment for the eleven co-regulated astrocyte gene modules.',
        'Keep the reported library spelling; no exact service/client edition, background or ontology snapshot is established.'),
    'P008': ([84], 'analysis', 'unspecified', [],
        ['MSigDB Hallmark 2020'],
        'Queried upregulated BPA/BBP-associated genes against Hallmark 2020 and displayed adjusted-P<0.05 pathways.',
        'EnrichR capitalization alone does not identify the R client; background and precise API/library identifier are unstated.'),
    'P009': ([110, 119], 'analysis', 'GSEApy', [(110, '1.0.4')],
        ['GO_Biological_Process_2023'],
        'Queried up- and downregulated TISSUE DEGs separately through GSEApy 1.0.4, with mouse organism and all imputed genes as background; displayed the top five sets per comparison.',
        'The GSEApy edition is not an Enrichr server edition; exact historical library bytes and author results are not recovered.'),
    'P021': ([79, 85, 101, 103, 113], 'analysis', 'enrichR-R', [(79, '3.2')],
        ['GO Biological Process 2021', 'GO Biological Process 2023'],
        'Used enrichR 3.2 for age/subclass DEGs, lifespan-trajectory clusters, shared protein-coding genes, pseudotime traDEG clusters and rhythmic gene groups; GO BP 2021 and the separate traDEG GO BP 2023 branch are retained.',
        'Do not assign GO BP 2021 to the explicitly different 2023 traDEG branch or infer a library for the rhythmic branch; historical server bytes/backgrounds are not established.'),
    'P031': ([55, 57, 71], 'analysis', 'web', [], [],
        'Uploaded human intestinal DEGs, mouse M-cell marker genes and bulk M-cell organoid DEGs for GO/cell-type or pathway annotation.',
        'Keep the separate input lists and their stated DEG criteria; downstream clusterProfiler GSEA and its KEGG/GO v7.4 sets are separate from the Enrichr uploads.'),
    'P039': ([80], 'analysis', 'enrichR-R', [(80, '3.1')],
        ['GO_Biological_Process_2021'],
        'Applied R Enrichr 3.1 GO BP 2021 enrichment to the stated tumour-cluster-2 DEG selection, preserving the literal log2[FC]<-0.5 and adjusted-P<0.05 criteria.',
        'Do not reverse the reported fold-change inequality to match the accompanying enriched-gene wording; exact inputs/background remain required.'),
    'P048': ([8], 'analysis-caption', 'unspecified', [],
        ['MSigDB Hallmark 2020'],
        'The embedded Figure 4a legend reports Enrichr Hallmark 2020 enrichment of human/mouse SPP1hi-TAM DEGs selected with adjusted-P<0.05 and absolute log2FC>0.5, with an adjusted-P=0.05 display line.',
        'This is an explicitly identified figure-legend use inside a body paragraph, not a Methods-derived execution record; host, background and exact settings are unestablished.'),
    'P051': ([17], 'analysis-caption', 'unspecified', [], [],
        'The embedded NeuMap figure legend reports retaining transcription factors identified by both EnrichR and chromatin-accessibility analysis for the trajectory network.',
        'No Enrichr library, host, threshold or full recipe is specified in this legend; an empty immuno-silent-path finding is not a general pathway-absence claim.'),
    'P053': ([72], 'analysis', 'GSEApy', [],
        ['GO biological process 2021'],
        'Used the GSEApy Enrichr module with GO BP 2021 and distinct tested/expressed-gene or ABC-linked backgrounds for each species, cell type and ABC comparison.',
        'Retain the several comparison-specific background definitions; do not substitute one universal background or transfer the nearby edgeR version to GSEApy.'),
    'P055': ([92], 'library-resource', 'unspecified', [],
        ['ARCHS4', 'ENCODE', 'TRRUST'],
        'Combined Enrichr transcription-factor target resources from ARCHS4, ENCODE and TRRUST, retaining human factors found in at least two resources with at least ten unique targets per resource and intersecting targets across pairs; downstream ranked testing used FGSEA 1.20.0.',
        'This establishes library-resource use, not Enrichr enrichment-service execution. Exact database editions, identifiers and pairwise combination details require original resources; FGSEA options are a separate method.'),
    'P058': ([54], 'analysis', 'unspecified', [],
        ['GO_Biological_Process_2021', 'GO_Cellular_Component_2021', 'GO_Molecular_Function_2021'],
        'Used enrichR and the three named GO 2021 libraries to annotate differential projection-target/SPP1 neuronal gene lists.',
        'Do not infer an R-client edition or background from the enrichR spelling or from the preceding MAST/Seurat analysis.'),
    'P061': ([86], 'analysis', 'unspecified', [], [],
        'Tested GO enrichment for genes overlapping selected chromatin bins and used randomly selected gene-length-matched background genes to address long-gene bias.',
        'Keep the prior review object unchanged. Length matching and selection of the 300 bins are separate preparation steps; a generic custom-background API does not reconstruct that random sampling.'),
    'P074': ([51, 9], 'analysis', 'web', [], [],
        'Annotated prioritized causal-gene lists with Enrichr molecular-function ontologies, including the 261 prioritized genes and comparison with the AV-TWAS list.',
        'Separate this additional Enrichr annotation from DEPICT/apcluster testing; exact ontology release, background and settings are unreported.'),
    'P085': ([39, 41, 44, 47, 56], 'analysis', 'enrichR-R', [(39, '3.1')],
        ['GO'],
        'Used enrichR 3.1 GO enrichment for DEGs/shared DEGs, human meta-modules, mouse co-expression modules and amyloid-associated genes; a separate sex-comparison branch also names EnrichR.',
        'Do not infer a GO year or exact historical server library. The sex-comparison paragraph does not separately declare its client version.'),
    'P098': ([42, 10], 'analysis', 'web', [],
        ['Gene Ontology biological processes 2025', 'ChEA'],
        'Submitted Group 4 EnhBiv signature genes for GO BP 2025 enrichment and combined-score ranking; the Results separately report ChEA regulator enrichment of the 666-gene signature and clusters.',
        'Preserve the paper\'s literal two-sided Fisher claim and BH statement. Do not silently substitute one-sided local ORA or infer its exact tested family, ChEA release or background.'),
    'P121': ([47, 15], 'analysis-and-local-recalculation', 'Enrichr.jl', [],
        ['BioPlanet 2019', 'GO_Biological_Process 2018', 'GO_Cellular_Component 2018', 'GO_Molecular_Function 2018', 'Human_Gene_Atlas', 'KEGG_2019_Human'],
        'Used Enrichr.jl API queries and downloaded term/gene definitions, then recalculated right-tail Fisher P values and odds ratios with HypothesisTesting.jl against all DE-tested genes and applied BH with MultipleTesting.jl.',
        'Current remote Enrichr statistics are not these locally recalculated results. Keep literal library names; exact Julia versions, odds-ratio estimator and tested-term family need author-code/source checks.'),
    'P124': ([52, 53], 'analysis', 'unspecified', [],
        ['GO Biological Process 2021'],
        'Annotated upregulated single-cell perturbation DEGs and the separate up/down bulk perturbation DEG lists with EnrichR GO BP 2021.',
        'No client edition, historical library snapshot or background is declared; upstream MAST/DESeq2 fitting is separate.'),
    'P134': ([23, 22], 'analysis', 'unspecified', [],
        ['KEA'],
        'Compared differential-activity/core TF sets with KEA through EnrichR; the embedded Figure 5 legend describes Fisher testing with BH after the separate GSEA leading-edge selection.',
        'Do not relabel KEA as ChEA or copy the neighboring two-sided permutation-GSEA specification onto Enrichr; exact KEA edition/background is unstated.'),
    'P161': ([69], 'analysis', 'GSEApy', [(69, '1.1.2')],
        ['WikiPathway_2023_Human'],
        'Used GSEApy 1.1.2 EnrichR on adjusted-P<0.05 genes, total measured expressed genes as background and the stated WikiPathway_2023_Human pathway list.',
        'Preserve the paper\'s singular WikiPathway spelling; do not silently map it to a currently available differently spelled API identifier or infer original library bytes.'),
    'P163': ([69], 'analysis', 'unspecified', [], [],
        'Performed additional Enrichr gene-list enrichment after separately described ranked FGSEA/MSigDB analyses.',
        'Keep the prior review object unchanged. The FGSEA/MSigDB version and collections are not automatically Enrichr settings; exact Enrichr library, host and background remain unspecified.'),
    'P169': ([53], 'analysis', 'unspecified', [(53, '8 June 2023')], [],
        'Reported pathway enrichment using Enrichr released 8 June 2023.',
        'A reported release date is retained as a date, not converted to a semantic server/client version or evidence that the present server reproduces that release.'),
    'P182': ([23], 'analysis-host-ambiguous', 'declared-ClusterProfiler', [(23, '4.2.2')],
        ['Reactome', 'Hallmark', 'Gene Ontology biological processes'],
        'Reports separate up/down ORA using functions named enrichr and enrichGO attributed to ClusterProfiler 4.2.2, 10-1500-gene sets, all QC-detected genes as background and FDR<0.05.',
        'Retain the literal function/package attribution as unresolved rather than correcting it to enrichR. MSigDB 7.5.1 and ranked FGSEA are separately described; Enrichr service execution is not established.'),
    'P228': ([12], 'analysis', 'unspecified', [], [],
        'Used enrichR ORA for the snRNA-seq DEG analysis after the stated adjusted-P<0.2 selection; ranked FGSEA and GO redundancy reduction are separate analyses.',
        'The 0.2 threshold selects input DEGs, not an inferred enrichment significance cutoff; no Enrichr edition, library or background is established.'),
    'P241': ([53], 'analysis', 'enrichR-R', [], ['GO'],
        'Used the enrichR R package/webserver to annotate snoRNA pull-down interactor lists, ranked functional categories by combined score and depicted the top five per condition.',
        'Top-five display is not the multiple-testing family. Exact library year, background and R/server editions are unstated; do not recompute the combined score with a different method.'),
    'P244': ([45], 'analysis', 'API', [],
        ['GO biological process', 'GO cellular component', 'GO molecular function', 'DisGeNET', 'KEGG', 'GWAS Catalog', 'PanglaoDB'],
        'Queried canonical annotation libraries through the Enrichr API for GLEANR factors, with reported BH adjustment per gene-set library for each single factor.',
        'Do not pool factors/libraries into a different correction family or invent library years, exact submitted genes or historical server settings.'),
}

# These exact contexts establish whose edition/date is reported. They prevent
# nearby unrelated package numbers from becoming Enrichr server versions.
EDITION_CONTEXTS = {
    ('P009', 110): 'gseapy113 (version 1.0.4)',
    ('P021', 79): 'enrichR (v.3.2) R library',
    ('P039', 80): 'Enrichr package (v.3.1) in R',
    ('P085', 39): 'EnrichR69 R package (v3.1)',
    ('P161', 69): 'GSEApy61 (version 1.1.2)',
    ('P169', 53): 'Enrichr (released 8 June 2023)',
    ('P182', 23): 'ClusterProfiler60 (v.4.2.2)',
}


class MissingSource(FileNotFoundError):
    pass


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def source_view():
    result = {}
    for name in [DATA + '/papers.json', 'tools/literature/reviewed-uses.json']:
        try:
            result[name] = subprocess.check_output(
                ['git', '--no-replace-objects', 'show', BASE + ':' + name],
                cwd=ROOT, stderr=subprocess.PIPE)
        except subprocess.CalledProcessError as error:
            raise MissingSource('Restore Git predecessor ' + BASE + ':' + name) from error
    return result


def audit(cache):
    from collect_omics_review import paragraphs
    from catalog_fixed_cohort import verify_fixed_cohort
    before = source_view()
    papers = json.loads(before[DATA + '/papers.json'])
    reviews = json.loads(before['tools/literature/reviewed-uses.json'])['reviews']
    verify_fixed_cohort(ROOT, ROOT / DATA, papers)
    require(len(papers) == 250 and len(reviews) == 1439, 'Unexpected predecessor population')
    existing = {p['paper_id'] for p in papers if any(t['tool_id'] == 'enrichr' for t in p['tools'])}
    require(existing == set(DECISIONS) - {'P048', 'P051', 'P134'}, 'Unexpected old Enrichr membership')
    prior = {(r['pmcid'], r['tool_id']): r for r in reviews}
    identities, named, roles = [], [], []
    for paper in papers:
        path = Path(cache) / (paper['pmcid'] + '.xml.gz')
        if not path.is_file():
            raise MissingSource('Restore exact primary XML ' + str(path))
        with gzip.open(path, 'rb') as stream:
            raw = stream.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024 and digest(raw) == paper['xml_sha256'],
                'Changed primary XML: ' + paper['paper_id'])
        root = ET.fromstring(raw)
        dois = [n.text for n in root.findall('front/article-meta/article-id')
                if n.get('pub-id-type') == 'doi']
        require(len(dois) == 1 and dois[0].lower() == paper['doi'].lower(),
                'Primary DOI differs: ' + paper['paper_id'])
        identities.append({**{k: paper[k] for k in ['paper_id', 'pmcid', 'doi', 'xml_sha256']},
                           'xml_bytes': len(raw)})
        blocks = paragraphs(root)
        hits = [b for b in blocks if PATTERN.search(b.get('match_text', b['text']))]
        if hits:
            named.append(dict(paper_id=paper['paper_id'], paragraphs=[dict(
                paragraph_index=b['paragraph_index'], section=b['section'],
                paragraph_sha256=digest(b['text'].encode())) for b in hits]))
        if paper['paper_id'] not in DECISIONS:
            continue
        indices, role, host, editions, libraries, summary, caution = DECISIONS[paper['paper_id']]
        by_index = {b['paragraph_index']: b for b in blocks}
        require(all(i in by_index and PATTERN.search(by_index[i].get('match_text', by_index[i]['text']))
                    for i in indices), 'Missing selected Enrichr anchor: ' + paper['paper_id'])
        reported = []
        for i, literal in editions:
            context = EDITION_CONTEXTS[(paper['paper_id'], i)]
            require(literal in context and context in by_index[i]['text'],
                    'Edition context differs: ' + paper['paper_id'])
            reported.append(dict(paragraph_index=i, literal=literal, source_context=context,
                                 refers_to=host if paper['paper_id'] != 'P169' else 'reported-release-date'))
        old = prior.get((paper['pmcid'], 'enrichr'))
        roles.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'],
            fulltext_url=paper['fulltext_url'], role=role, declared_host=host,
            reported_editions=reported, reported_library_labels=libraries,
            use_summary=summary, required_caution=caution,
            anchors=[dict(paragraph_index=i, section=by_index[i]['section'],
                          paragraph_sha256=digest(by_index[i]['text'].encode())) for i in indices],
            positive_tool_related_use=True, newly_found_observation=paper['paper_id'] not in existing,
            existing_review_preserved=old is not None,
            previous_review_object_sha256=digest(encoded(old)) if old else None,
            reported_enrichment_analysis=role not in ['library-resource', 'analysis-host-ambiguous'],
            enrichment_service_execution_verified=False,
            author_runtime_executed=False, exact_author_settings_established=False,
            whole_paper_reproduced=False))
    require({r['paper_id'] for r in named} == set(DECISIONS) and
            sum(len(r['paragraphs']) for r in named) == 44, 'Named Enrichr population differs')
    resource_contexts = {'P074': [68, 76], 'P241': [110]}
    require(sum(r['existing_review_preserved'] for r in roles) == 2, 'Prior review population differs')
    return dict(schema_version=1, status='PASS250_IDENTITIES_25_ENRICHR_ROLES_SOURCE_CENSUS_ONLY',
        base_commit=BASE, primary_files=identities, named_body_hits=named, roles=roles,
        named_paragraphs=44, positive_roles=25, enrichment_analysis_roles=23,
        library_resource_roles=1, unresolved_function_host_roles=1,
        supplemental_resource_contexts=resource_contexts, existing_reviews_retained=2,
        potential_new_review_records=23, observations_added=['P048', 'P051', 'P134'],
        canonical_metadata_written=False, scientific_programs_executed=False,
        implementation_qualified=False, new_family_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    owned = False
    try:
        require(__debug__, 'Run without Python -O')
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        result = audit(args.cache)
        (args.out / 'CENSUS.json').write_bytes(encoded(result))
        print(result['status'])
    except Exception as error:
        code = 3 if isinstance(error, (MissingSource, FileNotFoundError, PermissionError)) else 2
        failure = dict(status='SKIP3_ENRICHR_SOURCE_AUDIT_SKIPPED' if code == 3 else
                       'FAIL2_NO_ENRICHR_SOURCE_CENSUS_ADMITTED', error=repr(error),
            required_action='Restore exact permitted XML/Git prerequisites for SKIP3. Retain changed inputs and diagnostics for FAIL2, correct the cause and retry NEW outputs. Do not infer settings, repair labels or refresh old source hashes.')
        if owned:
            (args.out / 'FAILURE.json').write_bytes(encoded(failure))
        print(failure, file=sys.stderr)
        return code
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

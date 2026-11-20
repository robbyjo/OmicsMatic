"""Explicit source-role crosswalk; no catalog mutation or whole-paper credit."""
from pathlib import Path
import argparse
import copy
import hashlib
import json
import sys

from scanpy_source_roles import DECISIONS

ROOT = Path(__file__).resolve().parents[2]
FROZEN_CENSUS = dict(bytes=113086, sha256='3deceadd536c1227d4af8bd96561ab04a782302c5214d978b4704b8b020a379c')
# These are useful currently tested counterparts, not an attribution of modern
# entry points/defaults to papers that only name a method or an older package.
COUNTERPARTS = {
 'P008': ['tl.score_genes'],
 'P009': ['pp.normalize_total','pp.scale','pp.pca','pp.neighbors','tl.leiden','tl.umap'],
 'P010': ['pp.normalize_total','pp.scale'],
 'P015': ['pp.normalize_total','pp.highly_variable_genes:seurat','pp.pca','pp.neighbors','tl.leiden','tl.umap'],
 'P021': ['pp.normalize_total','pp.log1p','pp.pca','pp.neighbors','tl.umap'],
 'P024': [],
 'P031': ['pp.normalize_total','pp.scale','pp.pca','pp.neighbors'],
 'P048': ['pp.scale','pp.pca','tl.leiden','tl.umap','tl.rank_genes_groups:wilcoxon','tl.embedding_density','tl.score_genes'],
 'P055': ['pp.pca','pp.neighbors','tl.rank_genes_groups:wilcoxon'],
 'P060': ['pp.normalize_total','pp.log1p','pp.scale','pp.pca','pp.neighbors','pp.highly_variable_genes:seurat'],
 'P061': ['tl.leiden'],
 'P062': ['pp.normalize_total','tl.leiden','tl.umap','tl.rank_genes_groups:wilcoxon'],
 'P070': ['pp.log1p','pp.scale','pp.pca','pp.neighbors','tl.leiden','tl.umap'],
 'P083': [],
 'P085': ['pp.normalize_total','pp.scale','pp.pca','pp.neighbors','tl.leiden','tl.umap'],
 'P093': ['pp.normalize_total','pp.log1p','pp.scale','pp.pca','tl.umap','tl.rank_genes_groups:wilcoxon'],
 'P094': ['pp.normalize_total','pp.log1p','pp.scale','pp.pca','pp.neighbors','tl.leiden'],
 'P100': ['pp.normalize_total','pp.log1p','pp.pca','pp.neighbors','tl.leiden','tl.umap','tl.rank_genes_groups:wilcoxon'],
 'P104': [],
 'P123': ['pp.normalize_total','pp.log1p','pp.pca','tl.umap'],
 'P161': ['pp.normalize_total','pp.log1p','pp.pca','pp.neighbors','tl.leiden','tl.umap','tl.rank_genes_groups:wilcoxon'],
 'P182': [],
}
REMAINING = {
 'P008': 'Exact signature, control pool, bins and RNG; Visium preparation and separate trend/permutation tests.',
 'P009': 'Historical 1.9.1 behavior, BBKNN, volume correction and separate batch/trajectory populations/settings.',
 'P010': 'Normalization/scaling entry points and settings are unspecified; separate hierarchical k-means is not a Scanpy operation.',
 'P015': 'Historical 1.7.0, literal high-expression exclusion, unspecified HVG flavor, Harmony, force-directed graph and chick/human settings.',
 'P021': 'cell_ranger-flavor batch HVGs/custom wrapper precedence, downsampling, Pegasus/scVI and variance-selected PC count.',
 'P024': 'No functions allocated between Scanpy/Pegasus; reported 8.9-million-cell cohort capacity is not qualified.',
 'P031': 'Literal log10 with unstated pseudocount and most highly expressed genes; do not substitute log1p or HVGs.',
 'P048': 'Reported transformation/normalization order, regression, unspecified HVG/ranking choices, Harmony and cross-species/sample aggregation.',
 'P055': '250-PC/subsampling and custom graph aggregation/Paris; separate OpenTSNE t-SNE; unstated tie correction.',
 'P060': 'Select on literal log dispersion >0, not inferred HVG mask; separate custom CCA/anchors, METIS and transfer confidence.',
 'P061': 'ALLCools-hosted methylome consensus rounds/resolutions and population; standalone RNA clustering does not reproduce this host.',
 'P062': 'Experimental Pearson HVG selection, Scrublet, SysVI and square-root-normalized plotting/DE branch; native Pearson matrix alone is partial.',
 'P070': 'Historical normalize_per_cell and literal misspelled paths; regression/cell cycle, BBKNN, PAGA/diffmap/draw graph, custom Scrublet and scVelo.',
 'P083': 'No exact standalone Scanpy functions established; hosted ArchMap/scArches query/reference model and author requirements remain separate.',
 'P085': 'Unresolved sc.ppl.log1p, ln(CPM) target and cluster-count wording; HVGs, scVI/Harmony and manual curation.',
 'P093': 'Explicit seurat_v3 HVGs, count/log layers, popV host, BBKNN angular metric, default ranking method and dotplots.',
 'P094': 'Historical 1.9.3 versus 1.8.2 branches; raw/denoised matrices, PC1 exclusion, regression, denoising, transfer and SCENIC+.',
 'P100': 'Historical 1.10.3, literal RNA QC AND and >95% MSI pixel rule, exact HVGs/Harmony; RNA and MSI populations remain distinct.',
 'P104': 'No functions allocated between Scanpy/Pegasus; STARsolo and Vireo are separate.',
 'P123': 'Historical 1.9.8 and literal pp.harmony_integrate availability, shared-HVG batching and unstated graph/manual annotation settings.',
 'P161': 'Batch/sample HVGs and literal cuts; separate RNA, CosMx, latent, imputed and ligand matrices; integration/neighborhood/GAM tools.',
 'P182': 'Supplied integrated-object provenance and plotting/reannotation; no de novo normalization/HVG/PCA/neighbor fit is established.',
}
QUALIFIED_CURRENT = {
 'pp.normalize_total','pp.log1p','pp.scale','pp.pca','pp.neighbors','tl.leiden','tl.umap',
 'pp.highly_variable_genes:seurat','tl.score_genes','tl.rank_genes_groups:wilcoxon','tl.embedding_density',
}


def require(condition, message):
    if not condition: raise ValueError(message)


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode()


def build(census):
    require(dict(bytes=len(encoded(census)), sha256=hashlib.sha256(encoded(census)).hexdigest()) == FROZEN_CENSUS,
            'Different frozen source census')
    require(set(COUNTERPARTS) == set(REMAINING) == set(DECISIONS), 'Crosswalk paper population differs')
    require(census['positive_roles'] == 22 and census['potential_new_review_records'] == 21
            and census['existing_reviews_retained'] == 1 and len(census['primary_files']) == 250,
            'Different census disposition')
    roles = copy.deepcopy(census['roles'])
    require({r['paper_id'] for r in roles} == set(COUNTERPARTS), 'Source role population differs')
    for role in roles:
        paper = role['paper_id']
        indices, label, host, editions, operations, summary, caution = DECISIONS[paper]
        require(role['role'] == label and role['declared_host'] == host and role['reported_operations'] == operations
                and role['use_summary'] == summary and role['required_caution'] == caution
                and [a['paragraph_index'] for a in role['anchors']] == indices
                and [(e['paragraph_index'], e['literal'], e['source_context']) for e in role['reported_editions']] == editions,
                'Manual source decision changed: ' + paper)
        require(role['existing_review_preserved'] == (paper == 'P061'), 'Previous review disposition changed')
        counterpart = COUNTERPARTS[paper]
        require(set(counterpart) <= QUALIFIED_CURRENT and len(counterpart) == len(set(counterpart)),
                'Unqualified/duplicate current counterpart: ' + paper)
        identity = next(row for row in census['primary_files'] if row['paper_id'] == paper)
        require(identity['pmcid'] == role['pmcid'] and identity['doi'] == role['doi'], 'Paper identity changed')
        role.update(tool_id='scanpy',source_xml_sha256=identity['xml_sha256'],
            current_1130_tested_function_counterparts=counterpart,
            counterparts_are_not_author_function_attribution=True,
            native_profile_overlap=(['Pearson-matrix-1.10.4','Pearson-matrix-1.13.0'] if paper=='P062' else []),
            reusable_supplied_object_intake=(paper=='P182'),
            remaining_author_roles=[REMAINING[paper],caution,
                'Exact author inputs, parameters, historical/hosted execution and complete workflow are not reproduced.'],
            full_reported_role_qualified=False,complete_author_workflow_qualified=False,
            historical_package_parity=False,catalog_review_appended=False)
    return dict(schema_version=1,status='PASS_SCANPY_22_SOURCE_ROLES_EXPLICIT_PARTIAL_SCOPE_CROSSWALK',
        primary_source_census=FROZEN_CENSUS,primary_files=copy.deepcopy(census['primary_files']),
        named_body_hits=copy.deepcopy(census['named_body_hits']),roles=roles,positive_roles=22,
        named_paragraphs=53,existing_reviews_retained=1,potential_new_review_records=21,
        observations_added=[],catalog_metadata_written=False,new_family_credit=0,
        whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    if args.out.exists(): print('FAIL2: preserve existing output; use NEW output',file=sys.stderr);return 2
    args.out.mkdir(parents=True)
    try:
        census=json.loads((ROOT/'docs/literature/scanpy-preprocessing-checks/CENSUS.json').read_bytes())
        result=build(census)
        (args.out/'COVERAGE.json').write_bytes(encoded(result));print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        action='Restore exact permitted source/census or correct the named crosswalk failure; preserve evidence and retry NEW output. Do not refresh old hashes.'
        (args.out/'FAILURE.json').write_bytes(encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=str(error),required_action=action)))
        print(str(error)+'\n'+action,file=sys.stderr);return code


if __name__=='__main__': raise SystemExit(main())

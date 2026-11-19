"""Frozen-cohort Scanpy source-role census; does not admit an implementation."""
from pathlib import Path
import gzip
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = 'bd26330459c35c5ec6e5be904d349280bb353f6e'
DATA = 'docs/literature/omics-2023-2026'
PATTERN = re.compile(r'(?<![A-Za-z0-9_])scanpy(?![A-Za-z0-9_])', re.I)
# anchors, role, host, editions(index,literal,exact context), operations, summary, caution
DECISIONS = {
 'P008': ([94], 'gene-signature-scoring', 'direct', [], ['tl.score_genes'],
  'Scored a 15-gene fetal fallopian-tube signature in adult Visium epithelial spots using score_genes, then tested rostrocaudal trends separately.',
  'The Jonckheere decreasing/increasing trend tests and 2,000 permutations are separate. No Scanpy edition, control-gene pool, binning or random seed is reported.'),
 'P009': ([66,73,118], 'spatial-clustering-and-normalization', 'direct', [(118,'1.9.1','scanpy (1.9.1)')], ['pp.scale','tl.leiden','external.pp.bbknn','pp.neighbors','tl.umap','pp.normalize_total'],
  'Scaled log-normalized MERFISH expression with max_value=10, used Leiden and BBKNN/UMAP, and used 20 PCs/15 neighbors for the single-batch reprogramming branch. Trajectories first divided counts by segmentation volume, then used normalize_total defaults.',
  'Keep the BBKNN and single-batch neighbor branches separate. Volume correction, trajectory imputation, sklearn StandardScaler/k-means and spline smoothing are outside these Scanpy operations; defaults do not imply target_sum=10,000.'),
 'P010': ([80], 'spatial-preprocessing', 'direct', [], ['normalization-unspecified','scaling-unspecified'],
  'Normalized and scaled STARmap profiles using stated standard Scanpy procedures before a hierarchical three-level marker-based k-means annotation.',
  'No Scanpy edition, normalization target/log base or exact functions are supplied. Do not assign the separate k-means annotation algorithm to a nonexistent Scanpy k-means entry point.'),
 'P015': ([38,45], 'single-cell-preprocessing-and-embedding', 'direct', [(38,'1.7.0','Scanpy81 (v1.7.0)'),(45,'1.7.0','Scanpy81 (v1.7.0)')], ['normalization','highly_variable_genes','pca','neighbors','leiden','umap','draw_graph'],
  'Processed chick and human neuruloid scRNA-seq with Scanpy 1.7.0: distinct QC/high-expression exclusions, target sum 10,000, default HVGs, Harmony integration and graph embeddings. Chick used 10 neighbors/40 PCs and Leiden resolution 3.5; human used 15 neighbors/30 PCs and UMAP-initialized force-directed drawing.',
  'Preserve the literal 1% versus 3% high-expression wording; it does not by itself distinguish a top-gene percentage from max_fraction. Cell Ranger remains excluded; DropEst, Scrublet, Harmony and Slingshot are separate tools.'),
 'P021': ([54,60,87], 'multi-branch-nuclear-preprocessing', 'direct-and-custom-wrapper', [(54,'1.9.1','Scanpy (v.1.9.1)'),(87,'1.9.3','Scanpy object (v.1.9.3)')], ['custom-scanpy_hvf_h5ad','pp.highly_variable_genes','filtering','normalization','log1p','pca','neighbors','umap'],
  'Used Scanpy 1.9.1 with Pegasus in the main cohort, including a custom cell_ranger-flavor batch-aware HVG wrapper with Source batches and 6,000 genes. A separate 1.9.3 replication branch filtered genes, downsampled to 1,000 UMIs, applied CPM/natural-log-plus-one, chose 6,000 HVGs, PCA explaining 50% variance and 100-neighbor UMAP.',
  'The custom wrapper declares both mean/dispersion cuts and n_top_genes; do not invent their precedence. Downsampling, chromosome/protein-coding restriction, Pegasus import and scVI label transfer remain separate author steps.'),
 'P024': ([41], 'downstream-processing-unspecified', 'shared-with-Pegasus', [(41,'1.9.1','scanpy (v.1.9.1)')], [],
  'Reported Scanpy 1.9.1 alongside Pegasus 1.7.0 for downstream processing of an 8,898,978-cell by 60,605-gene snRNA-seq matrix.',
  'The paragraph does not allocate individual processing functions between Pegasus and Scanpy. Large author-cohort capacity and exact options are not established by a bounded synthetic workflow.'),
 'P031': ([56], 'mouse-organoid-preprocessing', 'direct', [], ['normalization','log10-transformation','scaling','pca','neighbors'],
  'Processed mouse intestinal organoid cells in Python 3.11.9: mitochondrial filtering, per-cell normalization, log10 transformation, 2,000 most highly expressed nonmitochondrial/nonribosomal genes, scaling, 50-component PCA and 200-neighbor graphs.',
  'Python 3.11.9 is not a Scanpy edition. Preserve log10 and most highly expressed genes; do not substitute natural log or highly variable genes.'),
 'P048': ([15,37], 'human-mouse-single-cell-analysis', 'direct', [], ['highly-variable-gene-selection','regress_out','scale','pca','Harmony-wrapper','tl.leiden','tl.umap','tl.rank_genes_groups','tl.embedding_density','tl.score_genes'],
  'Processed human and mouse cells with QC, HVGs, regression/scaling, PCA/Harmony, Leiden/UMAP, cluster-marker ranking, embedding density and curated gene scores. Human sample scores used the median of cell scores; cross-species comparison used separate pseudobulk/z-score preparation.',
  'Retain the reported log2-plus-one then normalization order rather than silently rewriting it. No Scanpy edition or rank_genes_groups method is specified. Gene scoring, sample aggregation, CellBender and doublet detection are distinct stages.'),
 'P055': ([55,71], 'nuclear-PCA-graph-and-markers', 'direct', [], ['pca','neighbors-connectivities','tl.rank_genes_groups'],
  'Computed 250 PCs after per-cluster subsampling, used 20-neighbor connectivities for cell-type graph aggregation and obtained top-50 Wilcoxon markers per metacluster.',
  'OpenTSNE 1.0.0 performed the t-SNE, not Scanpy. Cell-type adjacency averaging and the Paris dendrogram are separate scikit-network/custom operations; Scanpy edition and tie-correction settings are unstated.'),
 'P060': ([63,64,65], 'cross-modality-reference-integration', 'direct-and-custom-SeuratIntegration', [], ['normalization','log1p','scale','pca','neighbors','pp.highly_variable_genes'],
  'Normalized selected scRNA-seq/MERFISH genes to 1,000, applied log1p/scaling, computed 100 PCs and 15-neighbor graphs for integration partitions. The second round retained genes with log dispersions greater than zero from highly_variable_genes.',
  'Keep log dispersions >0 distinct from an inferred normalized-dispersion cutoff or HVG mask. METIS partitioning, custom CCA/anchor label transfer and confidence multiplication are not Scanpy package parity.'),
 'P061': ([61,62], 'derived-methylome-consensus-clustering', 'ALLCools-derived', [], ['derived-functions-unspecified','Leiden-clustering'],
  'Used functions derived from ALLCools, Scanpy and scikit-learn during four methylome clustering rounds. The reported Leiden resolutions were 0.25 initially and 0.2–0.5 later within ALLCools ConsensusClustering.',
  'Preserve the existing review. This is methylome-fraction analysis with derived/hosted functions, not proof of a standalone RNA-count pipeline or the whole ALLCools consensus procedure.'),
 'P062': ([43,41], 'nuclear-Pearson-preprocessing-and-markers', 'direct', [(43,'1.10.4','Scanpy v.1.10.4')], ['pp.scrublet','experimental.pp.normalize_pearson_residuals','experimental.pp.highly_variable_genes','tl.leiden','tl.umap','tl.rank_genes_groups','pp.normalize_total'],
  'Used Scanpy 1.10.4 for nuclear QC/Scrublet, Pearson-residual normalization and experimental HVGs before SysVI, then Leiden/UMAP and Wilcoxon markers. Separately used square-rooted normalize_total output for plots and DEG analysis.',
  'Do not replace Pearson residuals or the square-root count branch with log normalization. SysVI, MAGIC, pyWGCNA and GSEApy are separate. Repeated Code availability text is context, not another execution role.'),
 'P070': ([36,32,35,37,41,42], 'spatial-single-cell-and-trajectory-analysis', 'direct-with-external-wrappers', [(36,'1.8.2','Scanpy (v.1.8.2)')], ['pp.normalize_per_cell','pp.log1p','pp.highly_variable_genes','pp.scale','score-gene-cell-cycle-reported','pp.regress_out','tl.pca','tl.leiden','tl.umap','tl.paga','tl.diffmap','pp.neighbors','draw-graph-reported','rank-gene-group-reported'],
  'Used Scanpy 1.8.2 for per-batch 10,000-count normalization/log1p/HVG selection, scaling/cell-cycle regression, 100-PC/BBKNN integration, Leiden/PAGA/UMAP and diffusion-map/force-directed trajectories. Also reported Visium Wilcoxon marker tests and a custom Scrublet overclustering screen.',
  'Keep literal rank_gene_group, score_gene_cell_cycle and scanpt spellings unresolved; do not silently claim execution of corrected paths. BBKNN, custom doublet significance/manual curation and scVelo stochastic velocity are separate stages.'),
 'P083': ([48], 'hosted-query-reference-analysis-stack', 'ArchMap/scArches', [(48,'1.9.1','Scanpy (version 1.9.1)')], [],
  'Reported Scanpy 1.9.1 in the ArchMap query-to-reference mapping and downstream-analysis software stack, alongside scArches, XGBoost, scvi-tools and scIB.',
  'The package/version declaration does not establish exact standalone Scanpy functions or recreate the hosted mapping pipeline. The scArches branch and linked requirements need separate author-code checks.'),
 'P085': ([32,33], 'nuclear-processing-and-integration', 'direct-with-scVI-and-Harmony', [], ['pp.normalize_total','log1p-reported','pp.highly_variable_genes','pp.scale','tl.pca','external.pp.harmony_integrate','pp.neighbors','tl.leiden','tl.umap'],
  'Normalized study counts and used scVI latent variables for Leiden/UMAP; reprocessed external cohorts with normalize_total/log1p, HVGs, scaling, PCA, Harmony, neighbor graphs and repeat Leiden/UMAP after QC.',
  'Retain the literal sc.ppl.log1p path, ln(CPM) wording and inconsistent cluster-number description without repair. normalize_total defaults do not establish target_sum=1e6. scVI fitting and author percentile/cluster curation are separate.'),
 'P093': ([39,31,37,41,56], 'hosted-popV-preprocessing-and-evaluation', 'popV', [(31,'1.9.3','Scanpy (v1.9.3)')], ['pp.highly_variable_genes','normalization','log1p','scaling','pca','external.pp.bbknn','UMAP','tl.rank_genes_groups','pl.rank_genes_groups_dotplot'],
  'Used Scanpy 1.9.3 in popV 0.2.2: seurat_v3 HVGs (default 4,000), 10K normalization, separate log1p layer, scaling/PCA, BBKNN/UMAP, default marker ranking and marker dotplots. UMAP min_dist was 0.3.',
  'Preserve raw-count and log layers, the seurat_v3 flavor and popV preprocessing/model context. BBKNN angular-distance settings and scikit-learn classification are separate; defaults do not imply Wilcoxon markers.'),
 'P094': ([28,30,31,36], 'multi-branch-RNA-spatial-metacell-analysis', 'direct', [(28,'1.9.3','Scanpy package (v1.9.3)'),(36,'1.8.2','Scanpy (v1.8.2)')], ['normalization','log1p','highly-variable-gene-selection','pca','regress_out','pp.neighbors','Leiden-clustering','pp.normalize_total','pp.scale'],
  'Used Scanpy 1.9.3 for main embryo RNA preprocessing/graphs, separate raw/denoised spatial branches and PCA embeddings with a Stereo-seq PC1 exclusion. A distinct SCENIC+ metacell RNA branch used Scanpy 1.8.2 normalize_total/log1p/scale.',
  'Do not assign 1.9.3 to the explicit 1.8.2 metacell branch. Denoised data supplied graph/clustering, normalized raw counts supplied GWAS scores. Harmony, denoising, transfer, Squidpy Moran tests, metacells and SCENIC+ are separate.'),
 'P100': ([43,50,53], 'RNA-and-MALDI-MSI-processing', 'direct-and-AnnData-workflow', [(43,'1.10.3','SCANPY (v.1.10.3)')], ['RNA-normalization','log-transformation','highly-variable-gene-selection','pca','neighbors','Leiden-clustering','UMAP','tl.rank_genes_groups','MSI-total-ion-normalization'],
  'Used Scanpy 1.10.3 for RNA QC, library normalization/log transformation, 6,000 HVGs, Harmony-adjusted 30-PC/10-neighbor Leiden/UMAP and Wilcoxon ranking with reported tie correction. MALDI-MSI AnnData preprocessing used separate pixel/metabolite filters and total-ion normalization.',
  'Keep the literal AND in the first RNA QC rule, tie_correct = TRUE as reported, and removal of metabolites expressed in more than 95% of pixels. Do not turn MSI into RNA counts or attribute Hotspot module fitting to Scanpy.'),
 'P104': ([29], 'downstream-processing-unspecified', 'shared-with-Pegasus', [(29,'1.9.1','scanpy (v.1.9.1)')], [],
  'Reported Scanpy 1.9.1 with Pegasus 1.7.0 for downstream processing of per-library nuclear count matrices.',
  'This paragraph does not name exact Scanpy functions or allocate steps between the two packages. STARsolo alignment and Vireo demultiplexing are separate.'),
 'P123': ([47], 'PBMC-preprocessing-and-annotation', 'direct', [(47,'1.9.8','Scanpy (v.1.9.8)')], ['normalization','log1p','pp.highly_variable_genes','pca','Harmony-path-reported','UMAP','visualization'],
  'Used Scanpy 1.9.8 for 10,000-count normalization/log1p, 3,000 shared HVGs, PCA, reported Harmony integration and UMAP based on 30 adjusted PCs before manual PBMC annotation.',
  'Preserve the stated scanpy.pp.harmony_integrate path as reported; its availability in that edition is not established by a newer root-level function. Shared-HVG batching details, graph settings and manual annotation remain unreported.'),
 'P161': ([65,68,70,71], 'RNA-spatial-and-ligand-neighborhood-analysis', 'direct-with-integration-tools', [(65,'1.9.1','SCANPY (version 1.9.1)')], ['pp.highly_variable_genes','neighbors','Leiden-clustering','UMAP','rank-genes-Wilcoxon','normalization','log-transformation','pca'],
  'Used Scanpy 1.9.1 for RNA HVGs with sample batches and explicit mean/dispersion cuts, then neighbor/Leiden/UMAP analysis. Separate CosMx and ligand-neighborhood branches used expression/latent representations and Wilcoxon rank-gene comparisons across cell types or neighborhoods.',
  'Do not collapse RNA, raw CosMx counts, imputed values, scVI/scANVI latent spaces and CytoSignal ligand matrices into one input population. Harmony/scVI/scANVI fitting, spatial neighborhoods and GAMs are separate tools.'),
 'P182': ([14], 'supplied-integrated-object-visualization', 'direct-on-reused-integrated-object', [], ['UMAP-representation','marker-visualization'],
  'Used a supplied integrated cartilage scRNA-seq object, broad cell-type reannotation and QC, then displayed representative gene markers and UMAP representations created using Scanpy.',
  'This does not establish de novo normalization, HVG selection, PCA, neighbor graphs or clustering in Scanpy. Preserve the supplied-object provenance and avoid inventing a fit or package edition.'),
}


class MissingSource(RuntimeError): pass


def require(value, message):
    if not value: raise ValueError(message)


def digest(raw): return hashlib.sha256(raw).hexdigest()


def source_view():
    result = {}
    for name in [DATA+'/papers.json', 'tools/literature/reviewed-uses.json']:
        try: result[name] = subprocess.check_output(['git','--no-replace-objects','show',BASE+':'+name],cwd=ROOT)
        except (OSError, subprocess.CalledProcessError) as e: raise MissingSource('Restore exact Git source stage '+BASE+':'+name) from e
    return result


def audit(cache):
    from collect_omics_review import paragraphs
    from catalog_fixed_cohort import verify_fixed_cohort
    before = source_view(); papers = json.loads(before[DATA+'/papers.json']); reviews = json.loads(before['tools/literature/reviewed-uses.json'])['reviews']
    verify_fixed_cohort(ROOT, ROOT/DATA, papers)
    require(len(papers)==250 and len(reviews)==1462, 'Different source-stage population')
    existing = {p['paper_id'] for p in papers if any(t['tool_id']=='scanpy' for t in p['tools'])}
    require(existing == set(DECISIONS), 'Different named-family membership')
    prior = {(r['pmcid'],r['tool_id']):r for r in reviews}; identities=[]; named=[]; roles=[]
    for p in papers:
        path=Path(cache)/(p['pmcid']+'.xml.gz')
        if not path.is_file(): raise MissingSource('Restore exact permitted primary XML '+str(path))
        with gzip.open(path,'rb') as f: raw=f.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and digest(raw)==p['xml_sha256'], 'Changed primary XML: '+p['paper_id'])
        root=ET.fromstring(raw); dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi']
        require(len(dois)==1 and dois[0].lower()==p['doi'].lower(), 'Primary DOI differs')
        identities.append({**{k:p[k] for k in ['paper_id','pmcid','doi','xml_sha256']},'xml_bytes':len(raw)})
        blocks=paragraphs(root); hits=[b for b in blocks if PATTERN.search(b.get('match_text',b['text']))]
        if hits: named.append(dict(paper_id=p['paper_id'], paragraphs=[dict(paragraph_index=b['paragraph_index'],section=b['section'],paragraph_sha256=digest(b['text'].encode())) for b in hits]))
        if p['paper_id'] not in DECISIONS: continue
        indices,role,host,editions,operations,summary,caution=DECISIONS[p['paper_id']]; by_index={b['paragraph_index']:b for b in blocks}
        require(all(i in by_index and PATTERN.search(by_index[i].get('match_text',by_index[i]['text'])) for i in indices), 'Changed named anchor: '+p['paper_id'])
        reported=[]
        for index,literal,context in editions:
            require(literal in context and context in by_index[index]['text'], 'Edition context differs: '+p['paper_id'])
            reported.append(dict(paragraph_index=index,literal=literal,source_context=context,refers_to='Scanpy'))
        old=prior.get((p['pmcid'],'scanpy'))
        roles.append(dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],fulltext_url=p['fulltext_url'],role=role,declared_host=host,
                          reported_editions=reported,reported_operations=operations,use_summary=summary,required_caution=caution,
                          anchors=[dict(paragraph_index=i,section=by_index[i]['section'],paragraph_sha256=digest(by_index[i]['text'].encode())) for i in indices],
                          existing_review_preserved=old is not None,previous_review_object_sha256=digest((json.dumps(old,indent=2,ensure_ascii=False)+'\n').encode()) if old else None,
                          positive_tool_related_use=True,author_runtime_executed=False,exact_author_settings_established=False,whole_paper_reproduced=False))
    require({p['paper_id'] for p in named}==set(DECISIONS) and sum(len(p['paragraphs']) for p in named)==53, 'Named source population differs')
    require([r['paper_id'] for r in roles if r['existing_review_preserved']]==['P061'], 'Different old review population')
    return dict(schema_version=1,status='PASS250_IDENTITIES_22_CURATED_SCANPY_ROLES_SOURCE_REVIEW_ONLY',base_commit=BASE,
                primary_files=identities,named_body_hits=named,roles=roles,named_paragraphs=53,positive_roles=22,
                existing_reviews_retained=1,potential_new_review_records=21,observations_added=[],
                extra_contexts=dict(P062=[86,91]),canonical_metadata_written=False,scientific_programs_executed=False,
                implementation_qualified=False,new_family_credit=0,whole_goal_complete=False)


if __name__=='__main__':
    import argparse,sys
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--primary-cache',type=Path,required=True); parser.add_argument('--out',type=Path,required=True); args=parser.parse_args()
    require(not args.out.exists(),'Choose NEW output'); args.out.mkdir(parents=True)
    try:
        result=audit(args.primary_cache); (args.out/'CENSUS.json').write_bytes((json.dumps(result,indent=2,ensure_ascii=False)+'\n').encode()); print(result['status']); sys.exit(0)
    except Exception as e:
        code=3 if isinstance(e,(MissingSource,FileNotFoundError,PermissionError)) else 2
        (args.out/'FAILURE.json').write_bytes((json.dumps(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(e),required_action='Restore exact permitted sources; preserve failed attempts and retry NEW output. Do not repair source statements or refresh old hashes.'),indent=2)+'\n').encode()); print(str(e),file=sys.stderr); sys.exit(code)

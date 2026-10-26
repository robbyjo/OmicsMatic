"""Apply individually read, frozen primary Methods evidence; never resample papers."""
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

ROOT=Path(__file__).resolve().parents[2]
EVIDENCE=ROOT/'build/literature-review/single-cell-qc-runtime/paper-evidence.json'

# Summaries are original paraphrases. Pipeline steps and literal ambiguities remain separate.
USES={
'scrublet':{
'P005':('Removed predicted doublets independently per sample, after mitochondrial/count and gene-prevalence QC; concatenation, normalization to 10000, logarithm and Harmony followed.','Do not infer paper-specific Scrublet settings or pool the 35 loading samples before simulation.'),
'P008':('Called doublets per library using Scrublet with a cited two-step diffusion procedure; the source reports a greater-than-40-percent Scrublet-score filter alongside gene and mitochondrial QC.','The custom diffusion procedure is not the original Scrublet pipeline exposed here; retain the literal threshold wording and reconcile the cited procedure.'),
'P009':('Applied Scrublet separately per MERFISH experiment with a score cutoff of 0.18, before segmentation-volume and expression filters and volume/total-count normalization.','MERFISH measurement and segmentation assumptions differ from droplet UMI doublets. Explicit threshold support does not establish this whole spatial pipeline.'),
'P015':('Removed predicted chick-cell doublets by developmental stage with Scrublet thresholds between 0.2 and 0.3 after CellRanger or DropEst count generation.','Reconstruct the stage-specific thresholds and input layers; Scanpy, Harmony and Leiden are separate.'),
'P021':('Used Scrublet 0.2.3 with 10 principal components independently per batch; subsequent downsampling to 1000 UMIs, CPM and log transformation were separate.','Use the correct pre-downsampling count input and batch unit. scvi-tools reference label transfer is a separate method.'),
'P024':('Applied Scrublet to infer further doublets after initial quality filtering, normalization/log transformation and an initial Leiden clustering.','The reported processing stage differs from a default raw-count run; exact matrix and modified preprocessing settings require reconstruction.'),
'P043':('Called doublets with the modified Scrublet workflow in SnapATAC2 on selected 500-bp accessibility tiles, with expected rate 0.08 and score cutoff 0.5.','This is an ATAC-specific SnapATAC2 implementation, not execution of the original RNA Scrublet 0.2.3 adapter.'),
'P060':('Applied Scrublet to MERFISH profiles after volume correction and normalization to 250, using a score cutoff greater than 0.25 and removing about four percent.','Processed fractional input must be explicitly declared as estimates. Raw-UMI simulation assumptions and this spatial pipeline are not established by accepting those values.'),
'P062':('Called doublets using scanpy.pp.scrublet with defaults in Scanpy 1.10.4; later Pearson residuals and SysVI analyses were separate.','The Scanpy wrapper is distinct from the original Scrublet package adapter and may change preprocessing and defaults.'),
'P070':('Computed Scrublet scores per 10x lane, then replaced scores by medians within Scanpy clusters/reclusters and used a right-tail normal approximation, MAD-derived spread and BH adjustment; marker review removed further clusters.','The cluster-median statistical postprocessing and manual decisions are not implemented by the original score/threshold adapter. Exact units and tail calibration need source-code reconciliation.'),
'P085':('Applied Scrublet 0.2.3 with defaults per experiment after splitpipe and CellBender 0.2.0; percentile-based doublet/count/mitochondrial QC followed.','A percentile QC rule differs from the package automatic threshold. Keep preprocessing, per-experiment grouping and downstream filters explicit.'),
'P094':('Called doublets independently per sample with Scrublet score threshold 0.15 before joining 295141 cells and subsequent normalization, regression and Harmony.','The adapter exposes the threshold and library unit; cell-cycle regression and batch integration remain separate.'),
'P096':('Combined dataset-tuned Scrublet 0.0.2, DoubletDecon 1.1.5 and DoubletFinder 3 calls: removed all DoubletFinder calls or the intersection of DoubletDecon and Scrublet calls.','Original Scrublet 0.2.3 is a different version; the multi-tool Boolean rule and other callers are separate pending workflows.'),
'P099':('Used Scrublet for doublet removal after RT-primer-aware cell merging and quality filtering in a large single-cell dataset.','The RT-primer cell construction, exact settings and downstream Monocle testing are separate contracts.'),
'P100':('Used default Scrublet during Scanpy 1.10.3 preprocessing and also manually removed clusters expressing mixed-lineage markers.','Automatic doublet scores do not replace the manual mixed-marker review or the separate Harmony/Leiden stages.'),
'P123':('Ran Scrublet 0.2.3 and scds 1.18 per sample after CellRanger 7 and CellBender 0.1.0 processing.','The source does not specify the combination rule; do not invent a union or intersection of the two callers.'),
'P161':('Used Scrublet 0.2.3 to remove predicted doublets in the single-cell preprocessing workflow.','Harmony and cell-cycle principal-component regression are separate; their reported versions do not establish Scrublet settings.'),
'P182':('Ran Scrublet separately per sequencing run after CellRanger 3.0.2 and SoupX 1.3, before Seurat 3 analysis.','Ambient-RNA correction and the exact run-level input matrix must be retained separately; no legacy Seurat/Scrublet parity is inferred.')},
'sctransform':{
'P018':('Used Seurat 5.1 SCTransform defaults for clustering and visualization after CellRanger 8.0.1 aggregation; differential expression and other analyses used a separately NormalizeData-processed RNA layer.','Keep SCT residuals, corrected counts, logdata and the separate RNA analysis layer distinct.'),
'P032':('Applied Seurat 4.3.0.1 SCTransform while regressing S.Score and G2M.Score. ADT CLR normalization, HTODemux, Harmony and WNN were separate.','Seurat post-fit vars.to.regress differs from standalone vst latent variables. The adapter tests phase-score regression, not WNN or demultiplexing.'),
'P039':('Used SCTransform-normalized counts in spatial receptor-ligand expression correlations with a 100-micrometre adjacency definition.','Establish the exact SCT count layer; LIANA candidate selection, distances and correlations are separate.'),
'P046':('Reported SCTransform 0.4.3 normalization/scaling before scDist 1.1.5 differential-state analysis.','SCTransform fitting does not implement scDist or the separate scCODA composition analysis.'),
'P051':('Used Seurat 5.1 SCTransform for spatial PDAC and myocardial-infarction profiles, followed by a top-ten-percent variable-gene choice, scaling, 20-PC analysis and Leiden clustering.','HVG selection, additional ScaleData and neighborhood/clustering choices must be reconstructed independently.'),
'P053':('Used Seurat 4 SCTransform with 3000 highly variable genes; DoubletFinder removed the top ten percent per sample and Harmony/orthologue/RPCA integration followed.','DoubletFinder is separate from Scrublet, and SCT fitting does not implement the integration pipeline.'),
'P058':('Applied SCTransform separately to 10x and SSv4 profiles, using 25 versus 30 principal components and an SCT integration workflow with 3000 anchors.','PrepSCTIntegration, FindIntegrationAnchors and IntegrateData are separate operations, not implied by a fitted SCT model.'),
'P082':('Used Seurat SCTransform regularized negative-binomial normalization of RNA before highly variable-gene PCA; antibody-derived tags used CLR separately.','The adapter handles RNA count models, not ADT CLR or the full multimodal workflow.'),
'P087':('Applied standalone sctransform 0.4.2 vst() to bulk liver counts for PCA-based outlier detection. edgeR TMM/CPM and rank-based testing were separate.','This is standalone vst on bulk data, not Seurat SCTransform or DESeq2 variance stabilization. Current 0.4.3 fitting and biological assumptions need version/input reconciliation.'),
'P094':('Applied SCTransform and PCA in the merged Seurat RNA workflow, which also describes NormalizeData/ScaleData and a literal nFeature_RNA less-than-1000 AND less-than-5000 filter.','Preserve the conflicting/redundant filter wording for reconciliation; do not silently replace it with a conventional interval.'),
'P096':('Repeated SCTransform and integration using NS-Forest-selected marker anchors versus random anchors for comparison.','NS-Forest marker discovery and anchor-based integration are separate from normalization.'),
'P117':('Applied SCTransform to RCTD-derived cell-type expression estimates, including summed adrenocortical/mesenchymal estimates and a spot-total filter of at least 1500.','These are weighted estimated matrices, not raw UMI counts. Declare estimate input and justify model assumptions; RCTD deconvolution and spatial aggregation remain separate.'),
'P145':('Used Seurat 5.2.1 SCTransform with mitochondrial-percent regression, then top-3000 residual features, 20 principal components and Harmony.','Supply the exact mitochondrial metadata explicitly. Later SoupX adjustment and ambient-RNA covariates are separate stages.'),
'P158':('Used SCTransform independently per library with 3000 highly variable genes after Parse Trailmaker 1.4.1 processing against an augmented mm10 reference.','Reference augmentation, MapMyCells annotation, integration and FindMarkers are separate workflows.'),
'P165':('Applied SCTransform per Visium FFPE slide after SpaceRanger 3 processing, before Harmony and downstream clustering/markers.','Retain the unusual literal neighbor/marker settings for source reconciliation; Loupe Browser pseudobulk analysis is separate.'),
'P167':('Used Seurat 5.0.1 SCTransform and PCA on CosMx data before Harmony and Louvain; the dataset reports very sparse profiles.','CosMx measurement, segmentation and sparse-count assumptions require explicit review; batch correction and clustering remain separate.'),
'P213':('Used Seurat 4.4 batch processing and SCTransform on OneK1K counts aligned with CellRanger 2.2 to hg19 before Azimuth reference annotation.','The exact batch integration procedure is unspecified here; SCT alone does not reproduce Azimuth annotation.'),
'P228':('Applied SCTransform and HarmonyIntegration after scDblFinder doublet handling, followed by 15-PC analysis, clustering and marker testing.','scDblFinder and sccomp composition analysis are separate tools. SCTransform is not differential expression or integration by itself.')}
}

EXTRA=[
('snapatac2','SnapATAC2','SnapATAC2','ATAC processing, modified doublet detection and embedding','P043','scrublet','Used SnapATAC2 modified Scrublet on selected 500-bp accessibility tiles, expected doublet rate 0.08 and score cutoff 0.5.','The original RNA Scrublet adapter does not implement this ATAC variant.'),
('dropest','DropEst','DropEst','Droplet RNA count processing','P015','scrublet','Generated chick droplet RNA counts using DropEst 0.8.6 in the preprocessing workflow.','Count construction is separate from downstream doublet scoring.'),
('sysvi','SysVI','SysVI','Variational single-cell integration','P062','scrublet','Used SysVI on Pearson-normalized highly variable genes, with 100-200 training epochs for total clusters and 5-30 for individual cell types.','A Scrublet run does not provide this scvi-tools integration model.'),
('pywgcna','PyWGCNA','PyWGCNA','Python coexpression network analysis','P062','scrublet','Used PyWGCNA for coexpression-network analysis in the single-cell workflow.','Existing Java network methods do not establish PyWGCNA package equivalence.'),
('gseapy','gseapy','gseapy','Python gene-set enrichment analysis','P062','scrublet','Used gseapy for gene-set enrichment downstream of single-cell processing.','Exact enrichment inputs/options need reconstruction; unrelated Java enrichment methods do not establish package parity.'),
('magic','MAGIC','(?-i:MAGIC|magic(?=[ -]imputed))','Graph-based expression imputation','P062','scrublet','Used MAGIC-imputed counts to visualize violin plots in the single-cell workflow.','This is a distinct graph diffusion/imputation operation, not SCTransform normalization.'),
('splitpipe','splitpipe','split[- ]?pipe','Combinatorial single-cell read processing','P085','scrublet','Processed single-cell reads with splitpipe before CellBender and per-experiment doublet detection.','The count-generation pipeline remains separate from the doublet caller.'),
('scds','scds','scds','Single-cell doublet detection','P123','scrublet','Ran scds 1.18 alongside Scrublet per sample after CellRanger and CellBender.','The caller combination rule is unspecified; scds methods are not implemented by Scrublet.'),
('scdist','scDist','scDist','Differential single-cell state analysis','P046','sctransform','Applied scDist 1.1.5 following SCTransform normalization/scaling.','The differential-state model is separate from normalization.'),
('sccoda','scCODA','scCODA','Compositional single-cell abundance analysis','P046','sctransform','Used scCODA for differential cell-composition analysis.','Composition inference is separate from expression normalization and scDist.'),
('ns-forest','NS-Forest','NS[- ]Forest','Random-forest cell-type marker selection','P096','sctransform','Selected NS-Forest marker genes for anchor-based integration comparisons against random anchor choices.','The marker classifier and integration experiment remain separate from SCT fitting.'),
('trailmaker','Trailmaker','Trailmaker','Parse read processing and reference quantification','P158','sctransform','Used Parse Trailmaker 1.4.1 with an augmented mm10 reference before per-library SCTransform.','Reference construction and read processing remain unimplemented by SCT.'),
('mapmycells','MapMyCells','MapMyCells','Reference-based cell annotation','P158','sctransform','Used MapMyCells for reference cell annotation in the Parse single-cell workflow.','Reference annotation is separate from per-library normalization.'),
('sccomp','sccomp','sccomp','Differential cell-composition analysis','P228','sctransform','Used sccomp to compare cell composition after single-cell preprocessing and annotation.','The composition model is separate from SCTransform and HarmonyIntegration.')]

def main():
    evidence=json.loads(EVIDENCE.read_text(encoding='utf-8'));by={(e['paper_id'],e['tool']):e for e in evidence}
    papers={p['paper_id']:p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))}
    for e in evidence:
        paper=papers[e['paper_id']];raw=gzip.decompress((ROOT/'build/literature-review/xml'/f"{e['pmcid']}.xml.gz").read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256']
        source=paragraphs(ET.fromstring(raw))[e['source']['paragraph_index']-1];assert source==e['source'] and e['doi']==paper['doi']
    registry_path=Path(__file__).with_name('tool-registry.json');registry=json.loads(registry_path.read_text(encoding='utf-8'));ids={r['id'] for r in registry}
    for tool,name,pattern,role,*_ in EXTRA:
        if tool not in ids:registry.append(dict(id=tool,name=name,pattern=r'(?<![A-Za-z0-9_])(?:'+pattern+r')(?![A-Za-z0-9_])',role=role,current_coverage='missing',existing_source=None,coverage_note='No validated equivalent workflow identified in the current source inventory.'))
    patterns={r['id']:r['pattern'] for r in registry};rows=[]
    def add(pid,tool,anchor,summary,note):
        e=by[pid,anchor];assert re.search(patterns[tool],e['source']['match_text'],re.I),(pid,tool)
        rows.append(dict(pmcid=e['pmcid'],doi=e['doi'],tool_id=tool,evidence_paragraph_sha256=hashlib.sha256(e['source']['text'].encode()).hexdigest(),use_summary=summary,implementation_scope_note=note))
    for tool,uses in USES.items():
        for pid,(summary,note) in uses.items():add(pid,tool,tool,summary,note)
    for tool,_,_,_,pid,anchor,summary,note in EXTRA:add(pid,tool,anchor,summary,note)
    uses_path=Path(__file__).with_name('reviewed-uses.json');review=json.loads(uses_path.read_text(encoding='utf-8'));old={(r['pmcid'],r['tool_id']):r for r in review['reviews']};added=0
    for row in rows:
        key=row['pmcid'],row['tool_id']
        if key in old:assert old[key]==row,key
        else:review['reviews'].append(row);added+=1
    registry_path.write_text(json.dumps(registry,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');uses_path.write_text(json.dumps(review,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(added_reviews=added,total_reviews=len(review['reviews']),registry_names=len(registry))))

if __name__=='__main__':main()

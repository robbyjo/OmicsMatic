"""Individually reviewed enrichment uses in the frozen primary-source cohort.

This records supplied ranks, backgrounds, thresholds and explicitly stated
software, without equating current APIs with old versions or full pipelines.
"""
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

ROOT = Path(__file__).resolve().parents[2]
# paper, tool, primary paragraph, supporting paragraphs, version, use, boundary
USES = [
 ('P018','clusterprofiler',48,[56],'4.10.0',
  'Performed bulk GSEA on genes ranked by descending log2 fold change against GO, Reactome and KEGG; bulk ORA used up/down DEG lists and genes with mean FPKM above one in at least one group. Applied BH-adjusted P below 0.05 and q below 0.01 for bulk ORA; separate nuclear enrichGO used 4.12.6.',
  'Keep bulk 4.10.0 and nuclear 4.12.6 analyses distinct. Supply the exact gene-set snapshots and expression-derived backgrounds. The local generic APIs do not retrieve GO/KEGG or reproduce those historical versions, DEG construction or visualization selections.'),
 ('P020','clusterprofiler',34,[35],'3.18.1',
  'Ran GSEA on xenograft DEGs ranked by log2 fold change using Hallmark, GR and PR signatures, with pvalueCutoff 0.05 and BH correction. The clinical post/pre analysis instead ranked by absolute log10 P times the fold-change sign and used permutation inference.',
  'The two rankings are different inputs; preserve the separately reported sample comparisons and signatures. Current enrichit or legacy DOSE/fgsea backends do not establish 3.18.1 results. GSVA, paired Wilcoxon and Spearman analyses are separate.'),
 ('P023','clusterprofiler',79,[89],None,
  'Used enrichGO and enricher to compare GO and Hallmark enrichment among MCH GWAS and loss-of-function hits, with GO sets of 20–2,000 genes. Also tested the top 200 Perturb-seq genes using genes expressed in K562 cells as background.',
  'Supply each analysis’s selected genes, exact memberships and background separately; do not replace the K562 background with the whole genome. The generic local enricher workflow does not perform enrichGO annotation retrieval or gene selection.'),
 ('P031','clusterprofiler',71,[],'3.14.3',
  'Performed mature-M-cell versus enterocyte GSEA using log2 fold changes and KEGG/all-GO gene sets version 7.4.',
  'DESeq2 processing, the separate Enrichr GO analysis and exact reference-gene sets remain separate inputs/workflows. Algorithm and sampling parameters are not stated; current engines do not establish 3.14.3 parity.'),
 ('P032','clusterprofiler',42,[],'4.10.1',
  'Ran GSEA with all human gene sets obtained through msigdbr 7.5.1 in the bulk RNA-seq analysis.',
  'The ranking metric and GSEA sampling options are not given in this paragraph. PathfindR, one-sided regulator Fisher tests and Cytoscape visualization are separate analyses; supply the actual ranks and memberships.'),
 ('P037','fgsea',50,[],'3.14',
  'Ranked WLZ-associated MAG transcripts by fold-change direction times negative log10 P and tested metabolic-pathway transcript sets of at least ten genes across treatment, response-quartile, time and interaction contrasts; used BH q below 0.1.',
  'The source reports fgsea version 3.14; its package-version identity is unverified and is not silently interpreted as a Bioconductor release. Supply each contrast’s transcript identities and pathway sets. Mixed models, MAG selection and old-version inference are separate.'),
 ('P039','clusterprofiler',60,[],'4.6.0',
  'Applied enrichGO with default parameters to spatially varying genes with Z above seven, using org.Hs.eg.db 3.16.0 biological-process annotations; selected displayed terms with adjusted P below 1e-8 in at least one cell type.',
  'Exact spatial-gene selection, annotation snapshot and tested universe are required. The typed supplied-membership enricher adapter does not invoke enrichGO or construct this org.Hs.eg.db annotation, nor establish historical/default parameter parity.'),
 ('P040','clusterprofiler',33,[],'4.3.3',
  'Used GSEA with default settings against custom dendritic-cell, macrophage and CD8 T-cell signatures and applied BH correction.',
  'The rank definition and historical defaults must be recovered separately. Supply exact custom signatures; current explicitly selected backends and sampling settings are not asserted to reproduce 4.3.3 defaults.'),
 ('P043','clusterprofiler',63,[],None,
  'Performed GO enrichment with analysis-specific background genes, Fisher exact P values and BH multiple-comparison correction.',
  'Preserve each background defined elsewhere in the study; the package version and annotation source are unspecified here. Generic local ORA requires supplied sets and does not reconstruct biological gene selection.'),
 ('P044','clusterprofiler',57,[],'3.14.3',
  'Performed Gene Ontology enrichment in the R-loop Cut&Tag analysis after alignment, signal generation, peak calling and peak annotation.',
  'The source does not specify the selected gene list, background or enrichment arguments. ChIPseeker annotation, MACS2 peaks, deepTools tracks and circlise plots are separate; supply their derived gene sets and exact GO edition.'),
 ('P049','clusterprofiler',35,[36,37],None,
  'Used GSEA of ranked healthy-control cluster markers against top-50 marker sets from three reference retinal datasets for cell annotation. Separately used 4.12.6 for biological-theme enrichment of pseudobulk DEGs and GO analysis of six resilience/susceptibility expression modules.',
  'Do not merge healthy-marker GSEA with module-wise ORA or donor pseudobulk contrasts. Reference marker selection, cluster merging, DESeq2, AUCell and GO retrieval are separate; only the theme analysis explicitly names 4.12.6.'),
 ('P055','fgsea',92,[],'1.20.0',
  'Tested curated transcription-factor target sets using genes ranked by average Fos correlation in excitatory cells relative to inhibitory background, reversing the inhibitory ranking. Restricted sets to 15–500 genes and used positive one-tailed tests with FDR correction.',
  'Supply the normalized pseudocell coding-gene universe and targets supported by at least two ARCHS4/ENCODE/TRRUST resources. Explicit positive-tail fgsea is supported; correlation construction, target curation and 1.20.0 parity remain separate.'),
 ('P069','clusterprofiler',34,[40],'4.2.1',
  'Assessed Hallmark enrichment of the 30 highest-weight genes in cytokine-responsive NMF programs. Separately ran GO biological-process GSEA on IL-18-minus-PBS mean-expression differences in NK cells and displayed terms with FDR-adjusted P below 0.1.',
  'Keep selected NMF program genes separate from the ranked NK-cell mean differences. NMF, Wilcoxon program selection, MSigDB/GO construction and historical 4.2.1 inference are outside the generic supplied-set adapters.'),
 ('P082','clusterprofiler',47,[],None,
  'Used enricher hypergeometric overrepresentation to test Hallmark overlap with genes differentially expressed between subclones, controlling FDR at 0.05.',
  'Provide the exact differential-gene query, tested universe and MSigDB snapshot. AUCell and mean-expression LSC stemness scores in the same paragraph are separate methods; package version and background are unspecified.'),
 ('P089','clusterprofiler',38,[],'4.4.4',
  'Used clusterProfiler for gene set enrichment analysis in the study’s additional-software workflow.',
  'The paragraph does not give ranks, gene-set sources, background, algorithm or sampling parameters. Current typed APIs require those choices and do not establish the 4.4.4 workflow from this software list alone.'),
 ('P092','clusterprofiler',48,[],None,
  'Called GSEA on topic-specific gene scores built from distance-weighted chromatin accessibility and normalized by the weights’ L2 norm, using MSigDB Hallmark, curated C2 and ontology C5 collections retrieved with msigdbr 7.5.1.',
  'Supply the final weighted, normalized topic ranks and the exact MSigDB memberships. Peak-to-TSS weighting, topic modeling and annotation retrieval are separate; package version and sampling parameters are not stated.'),
 ('P101','clusterprofiler',40,[],'4.2.2',
  'Applied hypergeometric enrichment to GO and KEGG annotations of wild-versus-domesticated millet population DEGs, with P at most 0.05.',
  'The paragraph states a P threshold without specifying multiplicity adjustment; do not silently label it FDR. InterProScan/BlastKOALA/KofamKOALA annotation, DEG selection, background and 4.2.2 parity remain separate.'),
 ('P102','clusterprofiler',45,[],'3.10.1',
  'Tested GO enrichment of Brassica napus genes regulated by 495 trans-eQTL hotspots using Arabidopsis ortholog-derived GO annotations and FDR 0.01.',
  'Supply the exact orthology mapping, hotspot-specific gene lists, annotation and backgrounds. The adapter does not infer orthologs or hotspots and does not reproduce 3.10.1 defaults.'),
 ('P109','clusterprofiler',43,[],'4.0',
  'Performed GO and GSEA enrichment at FDR below 0.05 to assess GWAS/divergent-selection candidates using fruit transcriptomes, with whole-genome proteins supplying the GO background.',
  'Keep the whole-genome annotation background distinct from expression ranks. Supply gene identities and contrasts; RNA alignment, expression normalization, GO construction and historical 4.0 behavior are separate.'),
 ('P117','clusterprofiler',52,[],'4.6.2',
  'Extended sexual-dimorphism GSEA to KEGG and WikiPathways with clusterProfiler 4.6.2 and DOSE 3.24.2, excluding mitochondrial, ribosomal and X/Y chromosome genes.',
  'Preserve those feature exclusions and source pathway editions. cacoa compositional bootstrapping and density/Wilcoxon analyses are separate. Current enrichit and explicitly selected legacy DOSE 4.5 APIs do not establish 4.6.2/3.24.2 parity.'),
 ('P117','dose',52,[],'3.24.2',
  'Used DOSE together with clusterProfiler to extend sexual-dimorphism GSEA to KEGG and WikiPathways after excluding mitochondrial, ribosomal and X/Y chromosome genes.',
  'This is an explicitly named package use, not a generic drug dose. Ranking, exact algorithm and pathway snapshot are unspecified here. The separate legacy DOSE 4.5 generic APIs and current 4.6 dependency profile do not establish historical 3.24.2 parity.'),
 ('P120','clusterprofiler',29,[],None,
  'Tested Gene Ontology enrichment among genes supported by at least two lines of gene-mapping evidence.',
  'Supply the separately derived mapping-evidence gene list and exact annotation/background. This paragraph does not specify the package version or enrichment parameters; the adapter does not perform gene mapping.'),
 ('P133','fgsea',25,[],None,
  'Ran preranked fgsea on DESeq2 Wald statistics against downloaded MSigDB Hallmark gene sets.',
  'The paragraph names fgsea but links a GSEABase DOI; retain this citation mismatch without inferring a package version. Supply the actual Hallmark snapshot and ranks. STRING and historical/default sampling behavior are separate.'),
 ('P134','viper',49,[],None,
  'Used Student’s t-test implemented in the VIPER package on normalized unperturbed GLS-versus-MOS cell-line expression profiles to construct a differential-expression signature; metaVIPER then converted that signature to protein activities.',
  'The named VIPER package t-test is distinct from the metaVIPER network integration and two-sided aREA comparison. No VIPER runtime was installed or executed: its restricted noncommercial/nonprofit license requires eligibility confirmation before original-engine validation.'),
 ('P134','area',43,[49],None,
  'Used analytic rank-based enrichment analysis to assess conservation of Elyada-derived cell-state signatures across independent malignant-cell cohorts, cell lines and a PDX model. Also compared the 50 most activated and 50 most inactivated patient-signature proteins with cell-line protein-activity ranks using a two-sided aREA test.',
  'aREA is a named analytic rank-based method, separate from GSEA running scores and the metaVIPER network ensemble. Native GSEA scores are not its implementation. Original VIPER/aREA engine validation remains held pending applicable license eligibility; signatures, signed targets and reference ranks must be explicit.'),
 ('P144','fgsea',48,[],'1.30.0',
  'Ranked genes by decreasing Spearman correlation between expression and m.1227G>A heteroplasmy, then ran fgsea with the C5 GO collection to identify enriched or downregulated pathways.',
  'Supply the gene-level correlation ranks and exact C5 snapshot. Correlation estimation, heteroplasmy processing and historical 1.30.0 algorithms/options are not recreated by the current 1.38 profile.'),
 ('P163','fgsea',69,[],None,
  'Ranked genes by negative logarithm of adjusted P-value times the sign of fold change, then ran FGSEA against MSigDB 7.4 c1–8 and hallmark gene lists.',
  'The logarithm base and exact FGSEA algorithm/permutation settings are not stated. Preserve the chosen ranks and gene-list edition; the original 1.38 profile and native running-score audit do not establish unspecified historical defaults or the separate Enrichr analysis.'),
 ('P170','fgsea',38,[],None,
  'Computed gene–perturbation-score Pearson correlations among 3,000 variable genes, retained genes with correlation Q below 0.05, and ran fgsea on their correlation ranks with set sizes 15–500 and 100,000 permutations using Hallmark and C7 resources.',
  'Supply the filtered ranking universe rather than silently restoring all genes. The simple-permutation adapter exposes the requested count; correlation/Q filtering, exact resource selection, source’s collection wording and unspecified package-version parity remain separate.'),
 ('P182','clusterprofiler',23,[],'4.2.2',
  'Performed separate up/down DEG overrepresentation analyses, naming enrichr and enrichGO from ClusterProfiler, with all QC-detected genes as background, set sizes 10–1,500 and FDR-adjusted P below 0.05.',
  'The source spells one function enrichr; do not silently equate it with the local enricher API without resolving the source-code call. GO BP/Reactome/Hallmark membership construction, DEG selection, enrichment-resource licensing and 4.2.2 parity remain separate.'),
 ('P182','fgsea',23,[],'1.20.0',
  'Performed fgsea on differential-expression genes ranked by log2 fold-change effect size, using Reactome/Hallmark resources, set sizes 10–1,500 and FDR-adjusted P below 0.05.',
  'This preranked analysis is separate from up/down ORA with a QC-detected background. Supply exact memberships and ranks; 1.20.0 algorithm/default parity is not implied by the current profile.'),
 ('P189','clusterprofiler',14,[],None,
  'Tested GO and KEGG functional enrichment of primary-MR PCOS therapeutic targets with nominal MR P below 0.05, using Fisher exact tests and FDR below 0.05.',
  'The MR selection threshold differs from the pathway FDR threshold. Supply target identities, annotation and the intended background; the adapter does not run MR or retrieve GO/KEGG and the package version is unspecified.'),
 ('P197','clusterprofiler',13,[],None,
  'Used clusterProfiler GO analysis and GSEA for genes meeting the RNA-seq differential-expression criteria P below 0.05 and absolute log2 fold change above 1.5.',
  'The source does not specify the GSEA ranking, universe, pathway snapshot or engine settings. Salmon quantification and DESeq2 are separate; supply their derived ranks/query and explicit local memberships.'),
 ('P201','clusterprofiler',17,[],'4.0.5',
  'Tested GO and KEGG overrepresentation with all tested genes as universe and FDR 0.05, then simplified GO results with REVIGO using similarity 0.9 and the SimRel metric.',
  'Supply the exact tested-gene universe and memberships; REVIGO redundancy reduction and semantic visualization are separate. The generic local adapter does not retrieve those annotations or establish 4.0.5 parity.'),
 ('P225','clusterprofiler',9,[],None,
  'Performed GO enrichment after per-cluster single-nucleus differential-expression analysis and treated pathways with BH-adjusted P below 0.05 as enriched.',
  'Supply cluster-specific query sets, tested background and GO edition. SoupX/scds QC, Seurat Wilcoxon marker tests and scCODA composition modeling are separate; package version and exact enrichment arguments are unspecified.'),
 ('P228','fgsea',12,[],None,
  'Ran fgsea on genes preranked by t statistics from batch-adjusted pseudobulk limma analyses.',
  'The DE adjusted-P threshold 0.2 is not an asserted fgsea pathway threshold. Supply the t-statistic ranking and memberships; TMMwsp/voomLmFit, enrichR ORA and rrvgo semantic GO reduction are separate.'),
 ('P248','clusterprofiler',56,[],'3.18.1',
  'Performed GO enrichment following the RNA-seq/eQTL/TWAS analysis and differential-gene selection with adjusted P below 0.05 and absolute log2 fold change at least one.',
  'Supply the exact gene query, tested background and GO memberships. Alignment, expression quantification, DESeq2 and eQTL/TWAS are separate; generic current ORA does not establish historical 3.18.1 behavior.')
]


def main():
    papers = {p['paper_id']: p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))}
    source = {}
    for pid in {u[0] for u in USES}:
        paper = papers[pid]
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/f"{paper['pmcid']}.xml.gz").read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256'], pid
        source[pid] = paragraphs(ET.fromstring(raw))
    path = Path(__file__).with_name('reviewed-uses.json'); prior = path.read_bytes(); data = json.loads(prior)
    old = {(r['pmcid'], r['tool_id']): r for r in data['reviews']}
    history = ROOT/'build/literature-review/catalog-history'; history.mkdir(exist_ok=True)
    backup = history/(hashlib.sha256(prior).hexdigest()+'-reviewed-uses.json.gz')
    if not backup.exists(): backup.write_bytes(gzip.compress(prior, mtime=0))
    counts = dict(added=0, updated=0, unchanged=0)
    for pid, tool, index, supporting, version, summary, note in USES:
        paper = papers[pid]; primary = source[pid][index-1]
        pattern = r'(?<![A-Za-z0-9_])'+dict(area='aREA', dose='DOSE', viper='VIPER').get(tool,tool)+r'(?![A-Za-z0-9_])'
        assert re.search(pattern, primary['match_text'], 0 if tool in ['dose','viper','area'] else re.I), (pid, tool, index)
        row = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id=tool,
                   evidence_paragraph_sha256=hashlib.sha256(primary['text'].encode()).hexdigest(),
                   use_summary=summary, implementation_scope_note=note, version_reported_near_name=version)
        if supporting:
            row['supporting_evidence'] = []
            for i in supporting:
                paragraph = source[pid][i-1]
                assert re.search(pattern, paragraph['match_text'], 0 if tool in ['dose','viper','area'] else re.I), (pid, tool, i)
                row['supporting_evidence'].append(dict(paragraph_index=i, section=paragraph['section'], paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest()))
        key = paper['pmcid'], tool
        if key not in old: data['reviews'].append(row); counts['added'] += 1
        elif old[key] == row: counts['unchanged'] += 1
        else:
            previous = old[key]
            assert tool=='fgsea' and pid=='P163' and previous['evidence_paragraph_sha256']==row['evidence_paragraph_sha256'], key
            data['reviews'][data['reviews'].index(previous)] = row; counts['updated'] += 1
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
    report = dict(status='individually-reviewed-primary-enrichment-uses; paper-wide-completeness-open',
                  uses=len(USES), papers=len(source), changes=counts, total_reviews=len(data['reviews']),
                  vipER_runtime='Not installed or executed; applicable license eligibility remains pending.')
    (ROOT/'build/literature-review/enrichment-runtime/source-usage-audit.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report))


if __name__ == '__main__': main()

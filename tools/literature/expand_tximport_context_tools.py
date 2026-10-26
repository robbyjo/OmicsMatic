"""Recover distinct named software in the reviewed transcript-import contexts."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
ROOT=Path(__file__).resolve().parents[2];HERE=Path(__file__).parent
EXTRA=[
 ('biomart','biomaRt',r'(?-i:biomaRt)','Programmatic BioMart annotation/data retrieval through the named R package; BioMart web/database names are not execution evidence'),
 ('shortread','ShortRead',r'(?-i:ShortRead)','Sequencing-read quality assessment and IO in R/Bioconductor; ordinary short-read terminology is not package evidence'),
 ('metaviper','metaVIPER',r'metaVIPER','Protein/regulator activity inference across multiple regulatory networks'),
 ('aracne','ARACNe',r'ARACNe(?:-AP)?','Regulatory-network inference from expression profiles'),
 ('annotationhub','AnnotationHub',r'AnnotationHub','Discovery and retrieval of versioned annotation resources'),
 ('biocfilecache','BiocFileCache',r'BiocFileCache','Local cache management for retrieved research resources'),
 ('biocgenerics','BiocGenerics',r'BiocGenerics','Bioconductor S4 generic infrastructure; named supporting dependency,not a stand-alone scientific analysis')]
REVIEWS={
 ('P049','biomart'):(57,'Annotated gene lists with biomaRt2.60.1 in neuronal Cfh-cKO culture RNA-seq analysis.','The exact annotation attributes,query and Ensembl release are unspecified; preserve supplied gene-list IDs and species rather than assuming transcript quantification or a nearest-gene model.'),
 ('P117','biomart'):(52,'Used biomaRt when excluding mitochondrial,ribosomal and X/Y chromosome genes from the sexual-dimorphism pathway analysis.','This is annotation-based gene exclusion,not RNA-seq transcript import. Exact filtering lists and annotation release remain to be recovered; KEGG/WikiPathways enrichment through DOSE/clusterProfiler is separate.'),
 ('P162','biomart'):(42,'Filtered hypothyroid/cis-eQTL colocalization results to protein-coding genes according to gene IDs using the biomaRt R package.','Gene-biotype annotation/filtering is separate from colocalization inference; exact Ensembl release and query settings are unspecified.'),
 ('P223','biomart'):(10,'Assigned Ensembl identifiers to gene symbols with biomaRt for short-read RNA-seq differential-expression analysis using edgeR.','ID-to-symbol mapping is separate from edgeR testing. The source does not specify one-to-many mapping resolution or annotation release; retain original IDs rather than silently merging symbols.'),
 ('P238','biomart'):(17,'Downloaded Ensembl gene-ontology terms for each query and control gene through biomaRt.','Term retrieval is separate from downstream GO testing and the query/control background. Preserve annotation release,term identities and multiple-mapping decisions; no automatic enrichment choice is established.'),
 ('P250','biomart'):(65,'Annotated the closest gene for GWAS results using Ensembl GRCh37 BioMart through the biomaRt package.','Preserve the declared GRCh37 coordinates. The exact closest-gene distance/tie rule and annotation release are not specified; this is not transcript import or evidence of a generic gene-window equivalence.'),
 ('P134','biomart'):(44,'Used biomaRt together with kallisto0.44.0 and tximport for GRCh38 mRNA/ncRNA gene-expression quantification from PLATE-seq drug-perturbation libraries.','Exact annotation queries,release and mapping are unspecified; importing an existing transcript-to-gene TSV does not implement biomaRt.'),
 ('P134','shortread'):(44,'Assessed each PLATE-seq sample\'s sequencing-data quality with the ShortRead package.','Specific ShortRead functions,quality metrics and filtering actions are unspecified; FastQC or native trimming is not evidence of a ShortRead implementation.'),
 ('P134','metaviper'):(43,'Transformed gene-expression profiles into protein activities with metaVIPER using four bulk PDA regulatory networks and one single-cell PDA network,then analyzed malignant-cell states.','Network construction,metaVIPER integration and supplied network provenance are distinct from transcript import or a generic enrichment score; exact metaVIPER settings remain to be recovered.'),
 ('P134','aracne'):(43,'Inferred the PDA regulatory networks used for metaVIPER protein-activity analysis from PDA samples with ARACNe.','The paragraph points to supplementary methods for exact inference settings. A supplied-network visualization or enrichment helper does not implement ARACNe inference.'),
 ('P227','annotationhub'):(35,'Used AnnotationHub2.18.0 with tximport1.14.2,BiocFileCache and BiocGenerics to obtain transcript- or gene-level count matrices after Salmon1.1.0 quantification.','Specific hub resource IDs,snapshot,annotation mappings and package calls are unspecified. Native TSV annotation import does not retrieve or verify AnnotationHub resources.'),
 ('P227','biocfilecache'):(35,'Listed BiocFileCache1.10.2 among the R packages applied with tximport to obtain count matrices from Salmon1.1.0 quantifications.','The source does not identify specific cached resources or calls. Retain its role as a named supporting package rather than inventing a new normalization or scientific method.'),
 ('P227','biocgenerics'):(35,'Listed BiocGenerics0.32.0 among supporting R packages applied with tximport,AnnotationHub and BiocFileCache for transcript/gene count matrices.','This is generic-method infrastructure; no stand-alone scientific analysis or concrete call is specified. Java transcript arithmetic is not execution of this R package.')}

def main():
    history=ROOT/'build/literature-review/catalog-history';history.mkdir(exist_ok=True)
    def preserve(path,suffix):
        target=history/(hashlib.sha256(path.read_bytes()).hexdigest()+suffix)
        if not target.exists():target.write_bytes(gzip.compress(path.read_bytes(),mtime=0))
    registryPath=HERE/'tool-registry.json';registry=json.loads(registryPath.read_text(encoding='utf-8'));preserve(registryPath,'-registry.json.gz');ids={r['id'] for r in registry}
    for tid,name,pattern,role in EXTRA:
        expression=r'(?<![A-Za-z0-9_])(?:'+pattern+r')(?![A-Za-z0-9_])'
        if tid not in ids:registry.append(dict(id=tid,name=name,pattern=expression,role=role,current_coverage='missing',existing_source=None,coverage_note='Distinct named algorithm/annotation/IO/support package recovered in individually reviewed primary methods. Supporting dependency versus scientific analysis is explicit; integration and independent validation pending.'))
        else:
            entry=next(r for r in registry if r['id']==tid);assert entry['name']==name and entry['current_coverage']=='missing';entry.update(pattern=expression,role=role)
    papers={p['paper_id']:p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))};patterns={r['id']:r['pattern'] for r in registry};sources={}
    for pid in {key[0] for key in REVIEWS}:
        p=papers[pid];raw=gzip.decompress((ROOT/'build/literature-review/xml'/f"{p['pmcid']}.xml.gz").read_bytes());assert hashlib.sha256(raw).hexdigest()==p['xml_sha256'];sources[pid]=paragraphs(ET.fromstring(raw))
    reviewPath=HERE/'reviewed-uses.json';reviews=json.loads(reviewPath.read_text(encoding='utf-8'));preserve(reviewPath,'-reviewed-uses.json.gz');old={(r['pmcid'],r['tool_id']):r for r in reviews['reviews']};added=0
    for (pid,tid),(index,summary,note) in REVIEWS.items():
        p=papers[pid];source=sources[pid][index-1];assert source['paragraph_index']==index and re.search(patterns[tid],source['match_text'],re.I),(pid,tid,index)
        row=dict(pmcid=p['pmcid'],doi=p['doi'],tool_id=tid,evidence_paragraph_sha256=hashlib.sha256(source['text'].encode()).hexdigest(),use_summary=summary,implementation_scope_note=note);key=p['pmcid'],tid
        if key in old:assert old[key]==row
        else:reviews['reviews'].append(row);added+=1
    registryPath.write_text(json.dumps(registry,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');reviewPath.write_text(json.dumps(reviews,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print(json.dumps(dict(registry_names=len(registry),added_reviews=added,total_reviews=len(reviews['reviews']))))
if __name__=='__main__':main()

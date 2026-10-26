"""Individually reviewed tximport uses, anchored to unchanged primary paragraphs."""
import gzip
import hashlib
import json
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs

ROOT=Path(__file__).resolve().parents[2]
USES={
 'P046':(31,'Imported bulk vRG,oRG,OPC and MG RSEM estimates with tximport for CIBERSORTx cell-composition analysis against a matched single-cell reference.',
 'The paper calls these raw counts but RSEM provides estimated counts; retain fractional estimates and the actual input level. Preceding STAR2.7.10a/RSEM1.2.28 uses hg38/GENCODE38. Native isoform import does not implement RSEM gene-file import,CIBERSORTx B-mode/relative fractions or100permutations.'),
 'P134':(44,'Used tximport with kallisto0.44.0 and biomaRt to quantify GRCh38 mRNA/ncRNA gene expression from drug-perturbed pancreatic-cell PLATE-seq libraries.',
 'The paragraph does not specify tximport version,countsFromAbundance or transcript-to-gene mapping. Quantification,biomaRt annotation,z-score drug signatures and metaVIPER protein activities are separate; preserve the exact mRNA/ncRNA reference.'),
 'P151':(48,'Imported Salmon transcript counts and collapsed them to gene counts for DESeq2 analysis of human hg38 and mouse mm10 RNA-seq, with separate CRISPRi/CRISPRa condition contrasts.',
 'Keep species,transcriptome/annotation and experimental comparisons separate. Count scaling and tximport version are unspecified; gene import is distinct from low-count filtering,DESeq2 model/contrasts and adjusted-P thresholds.'),
 'P212':(24,'Used tximport to calculate what the paper calls length-scaled TPM for SGBS RNA-seq, then used median expression across replicates to measure INHBB expression.',
 'Retain the source wording without equating abundance.tsv TPM with countsFromAbundance lengthScaledTPM count-scale output. Exact tximport arguments and earlier alignment/quantification are unspecified; replicate medians and reused ENCODE LHCN-M2 TPM are separate.'),
 'P227':(35,'Used tximport1.14.2 with AnnotationHub,BiocFileCache and BiocGenerics to obtain transcript- and gene-level count matrices from Salmon1.1.0 GRCh38-cDNA quantifications.',
 'Current native references use tximport1.40.0,not historical1.14.2. Annotation retrieval,Salmon quantification and transcript-percentage calculations are separate. The source defines percentages using a total-TPM denominator; do not infer a new within-gene denominator or a counts scaling choice.')
}


def main():
    papers={p['paper_id']:p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))}
    rows=[]
    for pid,(index,summary,note) in USES.items():
        p=papers[pid];raw=gzip.decompress((ROOT/'build/literature-review/xml'/f"{p['pmcid']}.xml.gz").read_bytes());assert hashlib.sha256(raw).hexdigest()==p['xml_sha256']
        ps=paragraphs(ET.fromstring(raw));matches=[x for x in ps if 'tximport' in x['text'].lower()];assert len(matches)==1
        evidence=matches[0];assert evidence['paragraph_index']==index,(pid,evidence['paragraph_index'])
        rows.append(dict(pmcid=p['pmcid'],doi=p['doi'],tool_id='tximport',evidence_paragraph_sha256=hashlib.sha256(evidence['text'].encode()).hexdigest(),use_summary=summary,implementation_scope_note=note))
    path=Path(__file__).with_name('reviewed-uses.json');data=json.loads(path.read_text(encoding='utf-8'));prior=path.read_bytes();keys={(x['pmcid'],x['tool_id']):x for x in data['reviews']};added=0
    history=ROOT/'build/literature-review/catalog-history';history.mkdir(exist_ok=True);saved=history/(hashlib.sha256(prior).hexdigest()+'-reviewed-uses.json.gz')
    if not saved.exists():saved.write_bytes(gzip.compress(prior,mtime=0))
    for row in rows:
        key=row['pmcid'],row['tool_id']
        if key in keys:assert keys[key]==row,key
        else:data['reviews'].append(row);added+=1
    path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');print(json.dumps(dict(added=added,total_reviews=len(data['reviews']))))


if __name__=='__main__':main()

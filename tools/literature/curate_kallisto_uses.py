"""Primary-source reviewed execution scopes, preserving non-execution decisions."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
ROOT=Path(__file__).resolve().parents[2]
USES={
 'P037':(43,['34','37','47'],'Used kallisto0.43.0 for metagenomic contig/MAG abundance and metatranscriptomic transcript quantification. DNA TPM prevalence/abundance filters selected837MAGs; later RNA counts excluded rRNA and low-DNA-abundance MAGs.',
 'Preserve DNA-contig versus RNA-transcript references and read units. TPM thresholds,CheckM/MAG assembly/binning,DESeq2 VST,mixed models,rRNA exclusion and per-sample zero assignments are separate. The0.48.0 bulk adapter does not reproduce those filters or old0.43.0 outputs.','0.43.0'),
 'P061':(100,[],'Quantified SMART-seq at isoform level with kallisto using the same GTF-backed transcript models as the transcriptome/methylome analysis,then calculated cell-group TPM and exon PSI from transcript counts.',
 'The kallisto version/fragment/strand/bias/bootstrap settings are unspecified. STAR-generated gene counts/browser tracks,cell-group aggregation and exon-PSI construction are separate; plate-based SMART-seq is not BUS/UMI counting.',None),
 'P085':(33,[],'Used the count workflow from kallisto bustools to pseudoalign published Alzheimer snRNA-seq libraries against GRCh38 and obtain gene/UMI matrices.',
 'This is a barcode/UMI workflow,not bulk kallisto quant. Library-specific kb/bustools settings remain in the cited repository; CellBender ambient correction,Scrublet filtering,AnnData/Scanpy and Harmony are separate. The bulk adapter does not implement this count workflow.',None),
 'P110':(46,[],'Used kallisto0.46 transcriptome-based estimated counts (est_counts) for cross-species differential-expression analysis with DESeq2.',
 'Keep species and transcriptome annotation separate. HISAT2/StringTie steady-state TPM and stranded deepTools tracks are distinct workflows. Source wording says aligning,but kallisto uses pseudoalignment; the0.48.0 adapter is not historical0.46 or downstream DESeq2 parity.','0.46'),
 'P132':(59,[],'Estimated transcript abundances with kallisto after the study\'s personalized-genome RNA-seq workflow,then used BOLT-LMM for additive cis-eQTL tests with artifact/demographic/blood-cell/100LOCO-PC adjustment.',
 'STAR personalized-genome alignment and construction of the personalized transcript/reference inputs are not implemented by declaring a reference kind. Preserve sample/reference matching and LOCO covariates; quantification is separate from BOLT-LMM and top-cis selection.',None),
 'P134':(49,['44'],'Used kallisto0.44.0 with sequence-specific bias correction for single-end PLATE-seq ORF perturbations,then aggregated transcript counts by Entrez IDs. A related drug PLATE-seq experiment used kallisto with tximport/biomaRt.',
 'The0.48.0 profile retains the legacy --bias option absent from0.52.0,but is not old0.44.0 numerical parity. Fragment distribution,strand,Entrez mapping and transcriptome release require explicit inputs. VIPER/metaVIPER,aREA and drug/ORF contrasts remain separate.','0.44.0'),
 'P149':(54,['63'],'Computed blood/adipose gene expression from personalized transcript abundances with kallisto; the software table reports0.46. cis-eQTL regression used normal-quantile expression and chromosome-specific LOCO expression PCs.',
 'A software table alone is not execution evidence; the primary analysis paragraph supplies actual use. Personalized transcriptome construction,normal-quantile transforms,covariates,LOCO PCs and additive association are separate. The0.48.0 adapter does not establish0.46 or full-study parity.','0.46'),
 'P215':(11,[],'Quantified gene-mapping RNA-seq reads with kallisto after Trim Galore/FastQC; estimated count-scale matrices fed DESeq2 and TPMs were used in gene violin plots.',
 'The source calls the counts raw,but kallisto reports fractional estimates. The gene aggregation,version,strand,fragment/bias/uncertainty settings are unspecified. Native transcript import requires explicit annotation/scaling and does not perform the paper\'s DESeq2 analysis or plots.',None)
}


def main():
    data=ROOT/'docs/literature/omics-2023-2026';papers={p['paper_id']:p for p in json.loads((data/'papers.json').read_text(encoding='utf-8'))};source={}
    for pid in [*USES,'P108']:
        p=papers[pid];raw=gzip.decompress((ROOT/'build/literature-review/xml'/f"{p['pmcid']}.xml.gz").read_bytes());assert hashlib.sha256(raw).hexdigest()==p['xml_sha256'];source[pid]=paragraphs(ET.fromstring(raw))
    path=Path(__file__).with_name('reviewed-uses.json');prior=path.read_bytes();reviews=json.loads(prior);existing={(r['pmcid'],r['tool_id']):r for r in reviews['reviews']};added=0;updated=0
    history=ROOT/'build/literature-review/catalog-history';saved=history/(hashlib.sha256(prior).hexdigest()+'-reviewed-uses.json.gz')
    if not saved.exists():saved.write_bytes(gzip.compress(prior,mtime=0))
    for pid,(index,support,summary,note,version) in USES.items():
        p=papers[pid];paragraph=source[pid][index-1];assert paragraph['paragraph_index']==index and re.search(r'\bkallisto\b',paragraph['match_text'],re.I),(pid,index)
        row=dict(pmcid=p['pmcid'],doi=p['doi'],tool_id='kallisto',evidence_paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(),use_summary=summary,implementation_scope_note=note,version_reported_near_name=version)
        if support:
            row['supporting_evidence']=[]
            for value in support:
                item=source[pid][int(value)-1];assert re.search(r'\bkallisto\b',item['match_text'],re.I);row['supporting_evidence'].append(dict(paragraph_index=int(value),section=item['section'],paragraph_sha256=hashlib.sha256(item['text'].encode()).hexdigest()))
        key=p['pmcid'],'kallisto'
        if key in existing:
            if existing[key]!=row:
                old=existing[key];assert key==('PMC10719113','kallisto') and old['evidence_paragraph_sha256']==row['evidence_paragraph_sha256'] and old['use_summary']=='Quantified SMART-seq transcript isoforms using the shared gene annotation.',key
                reviews['reviews'][reviews['reviews'].index(old)]=row;updated+=1
        else:reviews['reviews'].append(row);added+=1
    path.write_text(json.dumps(reviews,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    p=papers['P108'];paragraph=source['P108'][33];assert 'quantification algorithm, like Salmon or kallisto' in paragraph['match_text']
    result=dict(status='reviewed-execution-versus-compatible-method-example',execution_reviews=8,added_reviews=added,updated_reviews=updated,total_reviews=len(reviews['reviews']),nonexecution_decision=dict(paper_id='P108',pmcid=p['pmcid'],doi=p['doi'],paragraph_index=34,paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest(),decision='Salmon/kallisto are compatible algorithm examples in the mathematical formulation,not execution evidence in this paragraph; re-extract to retain only any separate actual uses.'),remaining_scope='Bulk index/quant does not implement kb/BUS/UMI,personalized reference construction,downstream DE/PSI/association,or historical/full-paper parity.')
    (ROOT/'build/literature-review/kallisto-runtime/source-usage-audit.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))


if __name__=='__main__':main()

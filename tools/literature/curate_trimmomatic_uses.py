"""Read primary trimming paragraphs; preserve literal order, mate and version scope."""
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from pathlib import Path
from collect_omics_review import paragraphs
ROOT=Path(__file__).resolve().parents[2]
USES={
'P011':('Trimmed END-seq reads with Trimmomatic0.39 before Bowtie2 alignment to hg19. The paragraph does not specify trimming steps or thresholds.','Supply the actual assay adapters and pipeline explicitly. Mitochondrial/blacklist removal, ecDNA selection, MACS2 peak calling and BAMscale RPM tracks are separate.'),
'P091':('In Galaxy, removed ten bases from the start of forward reads with HEADCROP10, then merged the trimmed forward and original reverse reads using FLASH: overlap10-200bases and maximum mismatch density0.25.','Apply HEADCROP10 to the forward file alone, or use explicit first/second-mate settings. Do not trim ten bases from both mates. FLASH merging, target-adapter demultiplexing, BWA-MEM mapping and mutation/enrichment scoring are separate.'),
'P101':('Used Trimmomatic0.39 to remove adapters and low-quality sequence from150-base paired-end WGS of516millet accessions before BWA-MEM alignment in SpeedSeq0.0.2.','Trimming thresholds/adapters are unspecified. Supply them explicitly; legacy GATK3.8 UnifiedGenotyper calls and the reported clustered/annotation filters are separate from this adapter.'),
'P102':('Used Trimmomatic0.36 for adapter/low-quality RNA-seq filtering before HISAT2 alignment to ZS11; SAMtools mapping filters preceded StringTie TPM calculation.','The validated original profiles are0.39/0.41; no0.36 parity is inferred. HISAT2/MAPQ10/unique-read filtering and StringTie quantification remain separate stages.'),
'P124':('Removed RNA-seq adapters with Trimmomatic0.32 before STAR GRCh38 alignment, featureCounts and DESeq2. A separate ATAC-seq paragraph also reports Trimmomatic adapter trimming before Bowtie1 and peak analysis.','No0.32 or unspecified ATAC-version parity is asserted. Keep RNA and ATAC pipelines distinct; Bowtie1 is not Bowtie2, and downstream count/peak/differential/motif analysis is separate.'),
'P133':('Trimmed ChIP-seq FASTQ reads with Trimmomatic0.39 before BWA-MEM hg38 alignment, MAPQ30/blacklist/duplicate filtering and MACS3 peaks; deepTools later supplied RPGC tracks and heatmaps.','The source does not give trimming parameters. Adapter/quality choices need explicit assay inputs; RPGC normalization and feature heatmaps are not trimming operations.'),
'P171':('Used Trimmomatic0.39 on CENH3 ChIP-seq in the literal reported order ILLUMINACLIP:adapter.fa:2:30:10, MINLEN36, SLIDINGWINDOW4:20, before Bowtie2 and MACS3 BAMPE peaks.','Supply the actual adapter.fa. MINLEN precedes SLIDINGWINDOW in the source; shortening after MINLEN can retain a read below36bases. Do not silently reorder or append a final length filter. Centromere merging, coverage and StainedGlass repeat analysis are separate.'),
'P201':('Used Trimmomatic0.36 to remove leading/trailing RNA-seq bases below Phred20, then STAR with EndToEnd and sjdbOverhang100 against GRCh38 Ensembl110.','LEADING20 then TRAILING20 is explicit here; actual0.36 parity remains unvalidated. The native/upstream0.39/0.41 TRAILING one-base rejection is preserved and warned, not assumed to describe every historical version.'),
'P226':('Removed adapters with Trimmomatic0.39 after FastQC, then quantified against a human v38 transcriptome using Salmon1.8.0. The source describes TPM-form transcript counts analyzed with DESeq2 in Galaxy.','Adapter sequences/settings are unspecified. Retain the reported TPM/count wording as an unresolved downstream-input ambiguity; do not infer that TPM is valid raw DESeq2 count input or run differential analysis automatically.')}

def main():
    papers={p['paper_id']:p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_text(encoding='utf-8'))}
    evidence=json.loads((ROOT/'build/literature-review/track-trimming-runtime/paper-evidence.json').read_text(encoding='utf-8'));by={e['paper_id']:e for e in evidence if e['tool']=='trimmomatic'};assert set(by)==set(USES)
    registry_path=Path(__file__).with_name('tool-registry.json');registry=json.loads(registry_path.read_text(encoding='utf-8'))
    if not any(r['id']=='flash-merge' for r in registry):registry.append(dict(id='flash-merge',name='FLASH',pattern=r'(?<![A-Za-z0-9_])(?-i:FLASH)(?![A-Za-z0-9_])',role='Overlap-based paired-read merging',current_coverage='missing',existing_source=None,coverage_note='Distinct paired-read merger; not FlashPCA or HEADCROP. Integration and independent scientific validation pending.'))
    patterns={r['id']:r['pattern'] for r in registry};rows=[];sources={}
    def add(pid,tid,summary,note):
        e=by[pid];assert re.search(patterns[tid],e['source']['match_text'],re.I),(pid,tid);row=dict(pmcid=e['pmcid'],doi=e['doi'],tool_id=tid,evidence_paragraph_sha256=hashlib.sha256(e['source']['text'].encode()).hexdigest(),use_summary=summary,implementation_scope_note=note);rows.append(row);return row
    for pid,(summary,note) in USES.items():
        e=by[pid];raw=gzip.decompress((ROOT/'build/literature-review/xml'/f"{e['pmcid']}.xml.gz").read_bytes());assert hashlib.sha256(raw).hexdigest()==papers[pid]['xml_sha256'];sources[pid]=paragraphs(ET.fromstring(raw));assert sources[pid][e['source']['paragraph_index']-1]==e['source'];row=add(pid,'trimmomatic',summary,note)
        if pid=='P124':
            support=sources[pid][54];assert support['paragraph_index']==55 and 'Trimmomatic' in support['match_text'];row['supporting_evidence']=[dict(paragraph_index=55,section=support['section'],paragraph_sha256=hashlib.sha256(support['text'].encode()).hexdigest())]
    add('P011','bamscale','Generated reads-per-million-normalized coverage tracks for END-seq visualization/downstream analysis using BAMscale.','This is a distinct coverage program after alignment/filtering; Trimmomatic and generic supplied-bedGraph helpers do not implement BAMscale normalization.')
    add('P091','flash-merge','Merged forward HEADCROP10 reads with the original reverse reads using FLASH, requiring overlap10-200bases and maximum mismatch density0.25.','Preserve the upstream one-mate trimming and pairing. FLASH overlap merging is a distinct missing algorithm; not FlashPCA or a concatenation of mate sequences.')
    p75=papers['P075'];raw=gzip.decompress((ROOT/'build/literature-review/xml'/f"{p75['pmcid']}.xml.gz").read_bytes());assert hashlib.sha256(raw).hexdigest()==p75['xml_sha256'];source=paragraphs(ET.fromstring(raw))[51];assert source['paragraph_index']==52 and 'FLASH (v1.2.11)' in source['text'];by['P075']=dict(pmcid=p75['pmcid'],doi=p75['doi'],source=source)
    add('P075','flash-merge','Reconstructed paired reads using FLASH1.2.11 after Trim Galore0.3.1 adapter removal for single-allele MCC analysis. BLAT mini-chromosome mapping and custom SNP/ligation-junction scripts followed.','FLASH merging does not implement the custom MCC pipeline, alternate-genome bias correction, PCR duplicate/junction selection or allele-specific normalization. Trimming alone does not merge read pairs.')
    p=Path(__file__).with_name('reviewed-uses.json');d=json.loads(p.read_text(encoding='utf-8'));old={(r['pmcid'],r['tool_id']):r for r in d['reviews']};added=0
    for row in rows:
        key=row['pmcid'],row['tool_id']
        if key in old:assert old[key]==row,key
        else:d['reviews'].append(row);added+=1
    registry_path.write_text(json.dumps(registry,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');p.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');print(json.dumps(dict(added_reviews=added,total_reviews=len(d['reviews']),registry_names=len(registry))))
if __name__=='__main__':main()

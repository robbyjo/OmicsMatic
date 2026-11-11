# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind manually reviewed MEME-related program roles to exact primary paragraphs."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from collect_omics_review import paragraphs, tool_matches, validate_supporting_evidence

# paper, tool, primary paragraph, named supporting paragraphs, reported edition,
# source context, reviewed use, remaining scientific/implementation boundary.
ROLES = [
 ('P007','meme',54,[],None,'reused-motif-resource-format',
  'Downloaded JASPAR 2024 CORE vertebrate nonredundant frequency matrices in MEME format. Chose one representative per TF by assay provenance, then original nsites and motif length.',
  'This is use of a motif file format and supplied models, not a MEME discovery run. The separately reviewed FIMO scan and custom allele-score completion are distinct operations. Do not infer a MEME executable edition or discovery settings.'),
 ('P013','meme',102,[109,111,113,114],None,'suite-workflow-and-discovered-model-reuse',
  'Used a local Linux MEME-ChIP workflow on central 200-bp ChIP-seq peak sequences, with 200-bp background regions 1 kb upstream, blacklist filtering, a Markov model and JASPAR motifs. MEME and DREME discovered motifs, CentriMo checked central enrichment, and Tomtom compared known motifs. Reused discovered PWMs in separate nucleosome scans and positional summaries.',
  'The suite edition is unstated. Preserve the reported pipeline options, including -nmeme 600, -meme-mod zoops, widths 6–18, -meme-maxsize 50000000, -dreme-e 0.00001 and -dreme-m 20. These are historical pipeline tokens, not a current-version command inferred by translation. seqPattern/Biostrings scanning, strand-specific counts and downstream smoothing remain separate.'),
 ('P133','meme',42,[],None,'motif-discovery',
  'Discovered motifs de novo in differentially accessible ATAC-seq peaks assigned to promoter/intergenic regions, then used Tomtom to annotate similarities to known JASPAR motifs.',
  'Edition, complete discovery command, alphabet/background/site model, width and significance choices are unstated. The author-linked ATAC scripts captured at commit 3aeb503348e52b1f0b88d47839427380250c0ea5 contain no MEME/Tomtom command; they do not resolve those settings or authenticate historical execution.'),
 ('P139','meme',41,[],None,'suite-attribution-for-Tomtom',
  'Named MEME Suite as the provider of Tomtom for motif-similarity comparisons. Hierarchically clustered similarities and summarized TF-class motif pairs passing q < 0.1.',
  'This paragraph does not document a MEME de novo discovery run. Tomtom, ggseqlogo, clustering and class-pair summaries remain distinct. The suite edition and the exact comparison/correction and pair-counting families are unstated.'),
 ('P155','meme',36,[],'4.9.1','motif-discovery',
  'Used MEME 4.9.1 to examine known and newly discovered binding motifs in tag-enriched ChIP-seq sequences following Bowtie2 alignment and MACS2 peak selection.',
  'The exact MEME command, input/background population, alphabet, widths and occurrence model are unstated. The description of known motifs does not establish a separate scanning program or matching threshold. DiffBind and tag-density visualization are separate analyses; a newer MEME build does not establish 4.9.1 parity.'),
 ('P215','meme',21,[40],None,'suite-attribution-for-FIMO',
  'Attributed transcription-factor motif quantification in lost ARID1A-bound regions to FIMO from MEME Suite. The resulting motif occurrences informed the reported ZIC-family enhancer interpretation.',
  'This is FIMO occurrence scanning, not an additional MEME discovery run. The separate HOMER analysis used shuffled sequences and E < 0.001; neither that background construction nor the E cutoff is assigned to FIMO. Suite edition and FIMO P/q, strand and pseudocount settings are unstated.'),
 ('P217','meme',12,[],'5.5.4 (suite for FIMO)','suite-attribution-for-FIMO',
  'Used FIMO from MEME Suite 5.5.4 to scan supplied sequences with JASPAR/HOCOMOCO PWMs and retain individual motif matches at P < 0.001 in the TMEM216 promoter-variant analysis.',
  'No MEME discovery run is described. This is an occurrence P-value cutoff, not a stated q cutoff. Exact sequence/background/strand/pseudocount choices and the independent promoter and experimental interpretation remain separate.'),
 ('P235','meme',8,[],'5.5.4 (suite for STREME)','suite-attribution-for-STREME',
  'Used STREME from MEME Suite 5.5.4 to detect H3-associated k-mers in an intronic MUC5AC VNTR analysis.',
  'The named program is STREME, not MEME EM discovery or HyPhy MEME. H3 does not establish a motif width of three. Width/background/control/objective settings are unstated. The listed repeat-finder parameters belong to tandem repeats finder, not STREME.'),
 ('P013','meme-chip',102,[109],None,'motif-discovery-workflow',
  'Ran meme-chip on central 200-bp ChIP-seq sequences with upstream background regions, a Markov model and JASPAR. Reported -nmeme 600, -meme-mod zoops, -meme-minw 6, -meme-maxw 18, -meme-maxsize 50000000, -dreme-e 0.00001 and -dreme-m 20. Reused the discovered PWM for nucleosome motif scans.',
  'The edition, Markov order and complete command are unstated. Historical options remain verbatim and are not silently replaced with newer tokens or STREME. This combined workflow requires its actual MEME/DREME/CentriMo/Tomtom stages; running MEME alone does not reproduce it. seqPattern scanning and smoothing remain downstream.'),
 ('P013','dreme',102,[],None,'motif-discovery',
  'Discovered short motifs with DREME inside the reported MEME-ChIP workflow, using pipeline options -dreme-e 0.00001 and -dreme-m 20, before positional enrichment and motif comparison.',
  'The edition, complete standalone command and control/background handling are unstated. The E-value setting is not a FIMO P/q threshold. DREME is a separate program; substituting STREME or MEME is not historical pipeline reproduction.'),
 ('P013','centrimo',102,[],None,'positional-motif-enrichment',
  'Used CentriMo after MEME/DREME discovery to check whether the enriched motifs were centrally enriched relative to the described background sequences.',
  'Edition, score and region search settings, exact significance threshold and test/correction families are unstated. Central enrichment is separate from de novo discovery, occurrence scanning, Tomtom comparison and later nucleosome counts.'),
 ('P013','tomtom',102,[],None,'motif-comparison',
  'Compared motifs discovered by MEME/DREME to canonical JASPAR motifs with Tomtom in a MEME-ChIP analysis.',
  'Edition, distance, background/pseudocount, overlap/strand settings and significance threshold/family are unstated. The pipeline discovery width and DREME E cutoff are not Tomtom settings; similarity is not proof of TF occupancy.'),
 ('P017','hyphy',47,[],None,'phylogenetic-selection-model',
  'Ran HyPhy BUSTED with the paper’s stated default settings on back-translated codon alignments and previously reconstructed FastTree2 trees. Retrieved the reported full-codon-model dN/dS summary and discarded gene families with values above 0.5.',
  'The HyPhy edition and exact dN/dS field or rate-class aggregation are unstated. Preserve the reported greater-than-0.5 exclusion rather than replacing it with a positive-selection P-value rule. BUSTED gene-wide testing, tree construction and the retrieved rate summary are distinct; this is unrelated to MEME Suite motifs.'),
 ('P066','hyphy',32,[33],'2.5.48 (aBSREL only; other methods unstated)','phylogenetic-selection-models',
  'Used aBSREL, reported as 2.5.48, on 15,734 filtered gene alignments and pruned/foreground-labeled mammal trees with --code Universal --branches Foreground. Separately ran BUSTED-MH on multiple orthologue cohorts and HyPhy MEME with --resample 100 to identify diversifying-selection codons in CD45.',
  'The aBSREL paragraph states an FDR-corrected P ≤ 0.05 rule and ω > 1 criterion; the full correction family and exact extraction remain unstated. Do not propagate the aBSREL edition to MEME/BUSTED-MH. Foreground branch, gene-wide and site-level hypotheses and their cohorts differ. HyPhy MEME is an evolutionary model, not the motif-discovery program.'),
 ('P047','tomtom',83,[],None,'reused-precomputed-motif-clusters',
  'Reused the SCENIC+ ensemble motif resource: its authors had clustered 49,504 PWMs from 29 sources into 8,045 clusters using Tomtom distances. The paper then scanned DMRs with Cluster-Buster in SCENIC+.',
  'This describes provenance of precomputed motif clusters, not a new Tomtom run in the paper. Edition, clustering linkage, distance and significance settings are unstated. Cluster-Buster occurrence scanning and TF-gene cluster annotations are separate capabilities.'),
 ('P061','tomtom',94,[],None,'reused-precomputed-motif-clusters',
  'Reused SCENIC+ motif clusters created by the SCENIC+ authors from 49,504 PWMs and 29 sources, producing 8,045 clusters based on Tomtom distances. Scanned DMRs separately with Cluster-Buster.',
  'Do not count the resource-construction statement as a new Tomtom execution. The edition, comparison settings and clustering construction remain unstated. Motif occurrence, TF annotation and downstream enrichment require their own inputs and methods.'),
 ('P065','tomtom',44,[],None,'motif-comparison',
  'Matched TF-Modisco patterns to similar TF-binding motifs with Tomtom using the cisTarget motif collection.',
  'The RRID identifies the tool, not its software edition. Distance, pseudocount/background, overlap/strand and significance/correction settings are unstated. TF-Modisco discovery and the cisTarget resource are separate from motif comparison.'),
 ('P067','tomtom',25,[],None,'motif-comparison',
  'Matched TF-Modisco-derived PWMs to the linked motif-clustering database with tomtom -dist kullback -motif-pseudo 0.1 -text -min-overlap 1. The upstream patterns were trimmed at flanking information content below 0.4.',
  'The Tomtom edition, exact database release, background, strand choice, output significance cutoff and correction family are unstated. The information-content trimming is upstream processing, not a Tomtom option; do not infer its logarithm units or treat the closest match as evidence of occupancy.'),
 ('P133','tomtom',42,[],None,'motif-comparison',
  'Annotated MEME-discovered motifs from differentially accessible promoter/intergenic ATAC peaks by comparing them to known JASPAR motifs with Tomtom.',
  'Edition, JASPAR release and complete comparison parameters are unstated. The captured author ATAC scripts do not supply a Tomtom command. Motif discovery, genomic region annotation and actual TF binding are separate claims.'),
 ('P139','tomtom',41,[],None,'motif-comparison',
  'Computed motif similarities with Tomtom using stated default settings, visualized them by hierarchical clustering, and summarized similar pairs passing q < 0.1 as fractions of possible motif pairs within TF classes.',
  'The edition is unstated, so defaults are not converted into a fixed command. Preserve the original query/target and q-value families and class-pair denominator; direction, self-pair and multiplicity conventions are not specified. ggseqlogo and clustering are separate steps.'),
 ('P113','bwa-meme',78,[],'1.0.4','read-alignment',
  'Aligned EU-seq reads to mm10 with BWA-MEME 1.0.4 and soft clipping of supplementary alignments. Subsequently filtered mapping quality below 10 with SAMtools, removed rRNA/tRNA overlaps with BEDTools and counted gene-body reads with HTSeq.',
  'The spelling bwa meme is supported by the cited BWA-MEME publication, not an ordinary BWA 0.7.x run. The exact alignment command, model/index provenance and read parameters are unstated; no specific clipping flag is invented. Existing BWA integration does not supply BWA-MEME, and the downstream filtering/counting/DESeq2 normalization remains separate.'),
 ('P235','streme',8,[],'5.5.4','motif-discovery',
  'Detected H3-associated k-mers in intronic MUC5AC VNTR sequences with STREME from MEME Suite 5.5.4.',
  'The STREME width, alphabet/background, positive/control construction, objective, hold-out and significance settings are unstated. H3 is not taken as a three-base motif-width option. The match/mismatch/indel/period parameters in the paragraph belong to tandem repeats finder; STREME is distinct from MEME and HyPhy MEME.'),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out == ROOT / 'tools/literature/meme-program-use-reviews.json' and not out.exists()
    registry = json.loads((args.preview / 'registry-proposed.json').read_bytes())
    papers = {p['paper_id']: p for p in json.loads((ROOT / 'docs/literature/omics-2023-2026/papers.json').read_bytes())}
    rows = []
    for pid, tool, primary, support, edition, context, summary, boundary in ROLES:
        paper = papers[pid]
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        ps = paragraphs(ET.fromstring(raw)); by_index = {p['paragraph_index']: p for p in ps}
        review = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id=tool, evidence_paragraph_sha256=hashlib.sha256(by_index[primary]['text'].encode()).hexdigest(),
                      use_summary=summary, implementation_scope_note=boundary, version_reported_near_name=edition, usage_context=context,
                      supporting_evidence=[dict(paragraph_index=n, section=by_index[n]['section'], paragraph_sha256=hashlib.sha256(by_index[n]['text'].encode()).hexdigest()) for n in support])
        validate_supporting_evidence(ps, [review], {t['id']: t for t in registry})
        candidates = tool_matches(ps, [t for t in registry if t['id'] == tool], {tool: review['evidence_paragraph_sha256']})
        assert len(candidates) == 1 and candidates[0]['evidence_paragraph_index'] == primary
        rows.append(dict(paper_id=pid, primary_paragraph=primary, review=review))
    assert len(rows) == len({(r['paper_id'], r['review']['tool_id']) for r in rows}) == 22
    out.write_text(json.dumps(rows, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')
    print('22 source-specific roles drafted; catalog and implementation credit unchanged.')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source review checks')
    main()

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.*;

/** Source facts from Ensembl Variation API105 Constants, not a pathogenicity model or transcript picker. */
public final class VepConsequences105 {
    private VepConsequences105() { }
    public enum Impact { HIGH, MODERATE, LOW, MODIFIER }
    public record Consequence(String term,int rank,Impact impact) { }
    private static final Map<String,Consequence> TERMS=Map.ofEntries(
        Map.entry("transcript_ablation",new Consequence("transcript_ablation",1,Impact.HIGH)),
        Map.entry("splice_acceptor_variant",new Consequence("splice_acceptor_variant",3,Impact.HIGH)),
        Map.entry("splice_donor_variant",new Consequence("splice_donor_variant",3,Impact.HIGH)),
        Map.entry("splice_donor_5th_base_variant",new Consequence("splice_donor_5th_base_variant",4,Impact.LOW)),
        Map.entry("stop_gained",new Consequence("stop_gained",4,Impact.HIGH)),
        Map.entry("frameshift_variant",new Consequence("frameshift_variant",5,Impact.HIGH)),
        Map.entry("stop_lost",new Consequence("stop_lost",6,Impact.HIGH)),
        Map.entry("start_lost",new Consequence("start_lost",7,Impact.HIGH)),
        Map.entry("transcript_amplification",new Consequence("transcript_amplification",8,Impact.HIGH)),
        Map.entry("inframe_insertion",new Consequence("inframe_insertion",10,Impact.MODERATE)),
        Map.entry("inframe_deletion",new Consequence("inframe_deletion",11,Impact.MODERATE)),
        Map.entry("missense_variant",new Consequence("missense_variant",12,Impact.MODERATE)),
        Map.entry("protein_altering_variant",new Consequence("protein_altering_variant",12,Impact.MODERATE)),
        Map.entry("splice_polypyrimidine_tract_variant",new Consequence("splice_polypyrimidine_tract_variant",13,Impact.LOW)),
        Map.entry("splice_region_variant",new Consequence("splice_region_variant",13,Impact.LOW)),
        Map.entry("incomplete_terminal_codon_variant",new Consequence("incomplete_terminal_codon_variant",14,Impact.LOW)),
        Map.entry("splice_donor_region_variant",new Consequence("splice_donor_region_variant",14,Impact.LOW)),
        Map.entry("start_retained_variant",new Consequence("start_retained_variant",15,Impact.LOW)),
        Map.entry("stop_retained_variant",new Consequence("stop_retained_variant",15,Impact.LOW)),
        Map.entry("synonymous_variant",new Consequence("synonymous_variant",15,Impact.LOW)),
        Map.entry("coding_sequence_variant",new Consequence("coding_sequence_variant",16,Impact.MODIFIER)),
        Map.entry("mature_miRNA_variant",new Consequence("mature_miRNA_variant",17,Impact.MODIFIER)),
        Map.entry("5_prime_UTR_variant",new Consequence("5_prime_UTR_variant",18,Impact.MODIFIER)),
        Map.entry("3_prime_UTR_variant",new Consequence("3_prime_UTR_variant",19,Impact.MODIFIER)),
        Map.entry("non_coding_transcript_exon_variant",new Consequence("non_coding_transcript_exon_variant",20,Impact.MODIFIER)),
        Map.entry("intron_variant",new Consequence("intron_variant",21,Impact.MODIFIER)),
        Map.entry("NMD_transcript_variant",new Consequence("NMD_transcript_variant",22,Impact.MODIFIER)),
        Map.entry("non_coding_transcript_variant",new Consequence("non_coding_transcript_variant",23,Impact.MODIFIER)),
        Map.entry("upstream_gene_variant",new Consequence("upstream_gene_variant",24,Impact.MODIFIER)),
        Map.entry("downstream_gene_variant",new Consequence("downstream_gene_variant",25,Impact.MODIFIER)),
        Map.entry("TFBS_ablation",new Consequence("TFBS_ablation",26,Impact.MODERATE)),
        Map.entry("TFBS_amplification",new Consequence("TFBS_amplification",28,Impact.MODIFIER)),
        Map.entry("TF_binding_site_variant",new Consequence("TF_binding_site_variant",30,Impact.MODIFIER)),
        Map.entry("regulatory_region_ablation",new Consequence("regulatory_region_ablation",31,Impact.MODIFIER)),
        Map.entry("regulatory_region_amplification",new Consequence("regulatory_region_amplification",33,Impact.MODIFIER)),
        Map.entry("feature_elongation",new Consequence("feature_elongation",36,Impact.MODIFIER)),
        Map.entry("regulatory_region_variant",new Consequence("regulatory_region_variant",36,Impact.MODIFIER)),
        Map.entry("feature_truncation",new Consequence("feature_truncation",37,Impact.MODIFIER)),
        Map.entry("intergenic_variant",new Consequence("intergenic_variant",38,Impact.MODIFIER)),
        Map.entry("sequence_variant",new Consequence("sequence_variant",39,Impact.MODIFIER))
    );
    /** Exact40-term API105 table; ranks can tie and do not determine impact ordering. */
    public static Map<String,Consequence> terms(){return TERMS;}
    public static Consequence consequence(String term){var result=TERMS.get(Objects.requireNonNull(term));if(result==null)throw new IllegalArgumentException("Unknown Ensembl105 consequence; no rank/impact inferred: "+term);return result;}
    /** Returns every distinct minimum-rank term in supplied order; does not implement VEP --pick or choose one tie. */
    public static List<String> minimumRankTerms(Iterable<String> terms){var result=new LinkedHashSet<String>();int minimum=Integer.MAX_VALUE;for(String term:terms){var item=consequence(term);if(item.rank()<minimum){minimum=item.rank();result.clear();}if(item.rank()==minimum)result.add(term);}return List.copyOf(result);}
    /** HIGH, MODERATE, LOW, MODIFIER ordering over the supplied105 terms, independently of rank ties. */
    public static Optional<Impact> maximumImpact(Iterable<String> terms){Impact result=null;for(String term:terms){Impact candidate=consequence(term).impact();if(result==null||candidate.ordinal()<result.ordinal())result=candidate;}return Optional.ofNullable(result);}
}

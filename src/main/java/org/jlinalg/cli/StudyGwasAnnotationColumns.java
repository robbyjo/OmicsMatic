/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Retain the inferential meaning of externally produced annotation statistics. */
final class StudyGwasAnnotationColumns {
    private StudyGwasAnnotationColumns() { }
    static final Set<String> NUMERIC=Set.of("reference_snp_proportion","category_heritability","heritability_proportion","heritability_proportion_se","enrichment","enrichment_se","per_snp_contrast","per_snp_contrast_se","annotation_sum","annotation_sd","coefficient","coefficient_se","coefficient_p","coefficient_log_p","standardized_coefficient","standardized_coefficient_se");
    static final Set<String> LABELS=Set.of("annotation_type","inference_target");
    static void validate(List<String> header,String[] row)throws IOException {
        for(String field:List.of("annotation_sd","coefficient_se","standardized_coefficient_se","enrichment_se","heritability_proportion_se","per_snp_contrast_se"))if(present(header,row,field)&&number(header,row,field)<0)throw new IOException("Negative supplied GWAS annotation uncertainty/SD: "+field);
        if(present(header,row,"coefficient_p")){double value=number(header,row,"coefficient_p");if(value<0||value>1)throw new IOException("Invalid supplied GWAS conditional coefficient P");}
        for(String field:List.of("log_p_value","log_adjusted_p","coefficient_log_p"))if(present(header,row,field)&&number(header,row,field)>0)throw new IOException("A supplied log annotation P cannot be positive: "+field);
        if(!header.contains("annotation_type"))return;
        String kind=row[header.indexOf("annotation_type")];
        if(!Set.of("binary","continuous").contains(kind))throw new IOException("Supplied annotation_type must be binary or continuous");
        if(header.contains("inference_target")&&!Set.of("conditional_coefficient","category_vs_complement").contains(row[header.indexOf("inference_target")]))throw new IOException("Unknown supplied GWAS annotation inference_target");
        if(!kind.equals("continuous"))return;
        if(!header.contains("inference_target")||!row[header.indexOf("inference_target")].equals("conditional_coefficient"))throw new IOException("Continuous annotations need an explicit conditional_coefficient inference target");
        for(String field:List.of("reference_snp_proportion","category_heritability","heritability_proportion","heritability_proportion_se","enrichment","enrichment_se","per_snp_contrast","per_snp_contrast_se"))if(present(header,row,field))throw new IOException("A continuous annotation cannot carry binary-category/complement statistics: "+field);
        if(present(header,row,"annotation_sd")&&number(header,row,"annotation_sd")<=0)throw new IOException("Continuous annotation SD must be positive when supplied");
    }
    private static boolean present(List<String> header,String[] row,String field){if(!header.contains(field))return false;String value=row[header.indexOf(field)];return !value.isBlank()&&!Set.of("na","nan","null",".").contains(value.toLowerCase(java.util.Locale.ROOT));}
    private static double number(List<String> header,String[] row,String field)throws IOException{try{double value=Double.parseDouble(row[header.indexOf(field)]);if(!Double.isFinite(value))throw new NumberFormatException();return value;}catch(NumberFormatException invalid){throw new IOException("Nonfinite/non-numeric supplied GWAS annotation "+field);}}
}

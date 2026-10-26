/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Format checks and retention only; supplied signal posteriors are never refitted or renormalized. */
final class StudySuppliedColocalization {
    private StudySuppliedColocalization() { }
    private static final Set<String> PROBABILITIES=Set.of("pp_h0","pp_h1","pp_h2","pp_h3","pp_h4","pp_h4_given_h3_h4","trait1_prior","trait2_prior","shared_prior","pp_h4_min","pp_h4_max","posterior_threshold","exposure_variant_match_fraction","outcome_variant_match_fraction","outcome_candidate_match_fraction","exposure_export_p_threshold","outcome_export_p_threshold");
    private static final Set<String> COUNTS=Set.of("trait1_effect_index","trait2_effect_index","variants","exposure_observed_variants","outcome_observed_variants","aligned_variants","exposure_sample_size","outcome_sample_size","region_start","region_end","aligned_largest_gap_bp","aligned_left_edge_bp","aligned_right_edge_bp");
    private static final Set<String> BOOLEANS=Set.of("exposure_converged","outcome_converged","convergence_fallback_used","exposure_lead_aligned","outcome_lead_aligned","exposure_complete_region_declared","outcome_complete_region_declared");
    static void validate(List<String> header,String[] row,Map<String,Object> definition,List<String> identity)throws IOException {
        for(String key:List.of("pair_id","signal_pair","trait1_effect_index","trait2_effect_index","shared_prior","method"))if(header.contains(key))identity.add(row[header.indexOf(key)]);
        for(int i=0;i<header.size();i++){
            String key=header.get(i);if(missing(row[i]))continue;
            if(PROBABILITIES.contains(key)||COUNTS.contains(key)){double v=number(row[i]);if(v<0||PROBABILITIES.contains(key)&&v>1||COUNTS.contains(key)&&v!=Math.rint(v))throw new IOException("Invalid supplied colocalization "+key);}
            if(BOOLEANS.contains(key)){String value=row[i].toLowerCase(java.util.Locale.ROOT);if(!Set.of("true","false").contains(value))throw new IOException("Supplied "+key+" must be true/false or an explicit missing sentinel");row[i]=value;}
        }
        double tolerance=1e-6;var params=map(map(definition.get("method")).get("parameters"));if(params.containsKey("posterior_sum_tolerance"))tolerance=number(params.get("posterior_sum_tolerance").toString());if(tolerance<0||tolerance>.05)throw new IOException("Source posterior_sum_tolerance must be in [0,.05]");
        double sum=0;boolean all=true;for(String key:List.of("pp_h0","pp_h1","pp_h2","pp_h3","pp_h4")){if(!header.contains(key)||missing(row[header.indexOf(key)])){all=false;break;}sum+=number(row[header.indexOf(key)]);}
        if(all&&Math.abs(sum-1)>tolerance)throw new IOException("Supplied H0-H4 do not sum to one within the source posterior_sum_tolerance; supply unrounded values or declare their precision, without renormalizing them");
        if(header.contains("pp_h4_min")&&header.contains("pp_h4_max")&&!missing(row[header.indexOf("pp_h4_min")])&&!missing(row[header.indexOf("pp_h4_max")])&&number(row[header.indexOf("pp_h4_min")])>number(row[header.indexOf("pp_h4_max")]))throw new IOException("Supplied pp_h4_min exceeds pp_h4_max");
    }
    static boolean retain(String key){return StudyColocalizationReview.COLUMNS.contains(key);}
    private static boolean missing(String value){return value.isBlank()||Set.of("na","nan","null",".","not_declared","not_registered","not_recorded").contains(value.toLowerCase(java.util.Locale.ROOT));}
    private static double number(String value)throws IOException{try{double v=Double.parseDouble(value);if(!Double.isFinite(v))throw new NumberFormatException();return v;}catch(NumberFormatException bad){throw new IOException("Invalid finite supplied colocalization numeric value",bad);}}
}

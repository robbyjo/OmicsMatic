/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Streaming regional inventories count source observations before candidate restriction. */
final class StudyRegionalCoverage {
    private StudyRegionalCoverage() { }
    record Scan(Map<String,StudyGeneticEvidence.Variant> retained,int observed,long[] positions,
            String lead,double leadP,Path inventory,Map<String,Object> summary) { }

    static Scan scan(Path source,Path inventory,String chromosome,int start,int end,String feature,
            Set<String> allowed,int maximumRetained,int maximumObserved,Map<String,Object> declaration)throws IOException {
        var retained=new LinkedHashMap<String,StudyGeneticEvidence.Variant>();var ids=new HashSet<String>();var positions=new TreeSet<Long>();String lead="";double leadP=Double.POSITIVE_INFINITY;long rows=0,regional=0;
        try(var reader=Files.newBufferedReader(source);var writer=Files.newBufferedWriter(inventory)){
            String line=reader.readLine();if(line==null)throw new IOException("Regional source is empty");char delimiter=source.getFileName().toString().endsWith(".csv")?',':'\t';var header=DelimitedData.parse(line,delimiter,1,source);var columns=new LinkedHashMap<String,Integer>();
            for(int i=0;i<header.size();i++)if(columns.putIfAbsent(header.get(i),i)!=null)throw new IOException("Duplicate regional source column");
            for(String key:List.of("variant_id","chromosome","position","effect_allele","other_allele","beta","se"))if(!columns.containsKey(key))throw new IOException("Missing regional source column: "+key);
            if(!columns.containsKey("p_value")&&!columns.containsKey("log10_p"))throw new IOException("Regional adapter requires p_value or log10_p");
            writer.write("variant_id\tposition\teffect_allele\tother_allele\tp_value\tretained_for_alignment\n");
            while((line=reader.readLine())!=null){rows++;if(line.isBlank())continue;var row=DelimitedData.parse(line,delimiter,Math.toIntExact(Math.min(Integer.MAX_VALUE,rows+1)),source);if(row.size()!=header.size())throw new IOException("Regional source row width mismatch");
                if(feature!=null&&columns.containsKey("feature_id")&&!feature.equals(row.get(columns.get("feature_id"))))continue;
                String chr=row.get(columns.get("chromosome")).replaceFirst("^chr","");long position;try{position=Long.parseLong(row.get(columns.get("position")));}catch(NumberFormatException bad){throw new IOException("Variant positions must be 1-based integers",bad);}
                if(!chr.equals(chromosome)||position<start||position>end)continue;
                String id=row.get(columns.get("variant_id"));String[] parts=id.split(":",-1);if(parts.length>1&&parts[parts.length-1].matches("rs[1-9][0-9]*"))id=parts[parts.length-1];
                if(id.isBlank()||id.chars().anyMatch(Character::isISOControl)||!ids.add(id))throw new IOException("Regional variant identities must be nonblank, unique and contain no control characters");
                regional++;if(regional>maximumObserved)throw new IOException("Regional source exceeds the registered "+maximumObserved+"-variant coverage-audit budget; no truncated inference was run");
                String ea=row.get(columns.get("effect_allele")).toUpperCase(java.util.Locale.ROOT),oa=row.get(columns.get("other_allele")).toUpperCase(java.util.Locale.ROOT);
                double beta=number(row.get(columns.get("beta"))),se=number(row.get(columns.get("se"))),p;
                if(columns.containsKey("p_value"))p=number(row.get(columns.get("p_value")));else{double log=number(row.get(columns.get("log10_p")));if(log>0)throw new IOException("log10_p must be <=0");p=Math.pow(10,log);}
                if(!ea.matches("[ACGT]")||!oa.matches("[ACGT]")||ea.equals(oa)||se<=0||p<0||p>1)throw new IOException("Regional adapter requires biallelic SNPs, finite beta, positive SE and p in [0,1]");
                boolean selected=allowed==null||allowed.contains(id);if(selected){retained.put(id,new StudyGeneticEvidence.Variant(id,chr,position,ea,oa,beta,se,p));if(retained.size()>maximumRetained)throw new IOException("Region exceeds "+maximumRetained+"-variant in-memory LD budget");}
                positions.add(position);if(p<leadP||p==leadP&&id.compareTo(lead)<0){lead=id;leadP=p;}
                writer.write(id+"\t"+position+"\t"+ea+"\t"+oa+"\t"+p+"\t"+selected+"\n");
            }
        }catch(NumberFormatException bad){throw new IOException("Nonfinite or invalid regional numeric input",bad);}
        if(regional==0)throw new IOException("Source has no variants in the declared region");
        var summary=new LinkedHashMap<String,Object>();summary.put("observed_regional_variants",regional);summary.put("retained_for_alignment",retained.size());summary.put("candidate_restriction_applied",allowed!=null);summary.put("source_rows_scanned",rows);summary.put("source_sha256",FollowupSupport.hash(source));summary.put("inventory",inventory.getFileName().toString());summary.put("inventory_sha256",FollowupSupport.hash(inventory));summary.put("lead_variant",lead);summary.put("lead_p_value",leadP);summary.put("lead_retained_for_alignment",retained.containsKey(lead));summary.put("complete_region_declared",Boolean.TRUE.equals(declaration.get("complete_region")));
        for(String field:List.of("export_p_threshold","selection_basis","regional_variant_count","source_id","genome_build","ancestry","sample_size"))summary.put(field,declaration.getOrDefault(field,"not_declared"));
        if(declaration.containsKey("export_p_threshold")){double cutoff=number(declaration.get("export_p_threshold").toString());if(cutoff<=0||cutoff>1)throw new IOException("export_p_threshold must be in (0,1]");if(Boolean.TRUE.equals(declaration.get("complete_region"))&&cutoff<1)throw new IOException("complete_region:true conflicts with a significance-filtered export threshold");}
        if(declaration.containsKey("regional_variant_count")){double count=number(declaration.get("regional_variant_count").toString());if(count!=Math.rint(count)||count<regional)throw new IOException("regional_variant_count must be an integer at least as large as observed regional variants");summary.put("observed_fraction_of_declared_universe",regional/count);}
        summary.put("variant_identity_basis","Normalized source IDs; positional/allelic aliases under different identifiers are not independently resolved. Distinct-position counts are reported separately.");
        summary.put("interpretation","Observed counts precede candidate restriction. Observed or declared coverage does not independently certify a dense, unselected region.");
        return new Scan(Map.copyOf(retained),Math.toIntExact(regional),positions.stream().mapToLong(Long::longValue).toArray(),lead,leadP,inventory,summary);
    }

    static Map<String,Object> write(Path directory,Scan x,Scan y,List<StudyGeneticEvidence.Aligned> aligned,
            List<Object> exclusions,int start,int end,int bins)throws IOException {
        var result=new LinkedHashMap<String,Object>();result.put("exposure",x.summary());result.put("outcome",y.summary());result.put("aligned_variants",aligned.size());result.put("exposure_observed_match_fraction",aligned.size()/(double)x.observed());result.put("outcome_observed_match_fraction",aligned.size()/(double)y.observed());
        result.put("outcome_candidate_match_fraction",y.retained().isEmpty()?"NA":aligned.size()/(double)y.retained().size());result.put("observed_regional_overlap_fraction",aligned.size()/(double)Math.max(x.observed(),y.observed()));result.put("denominator","All valid distinct source variants in the declared region, before candidate restriction; missing unexported variants remain unknown");
        var reasons=new LinkedHashMap<String,Integer>();for(Object raw:exclusions){String reason=text(map(raw).get("reason"));reasons.merge(reason,1,Integer::sum);}result.put("alignment_exclusion_counts",reasons);
        var positions=new TreeSet<Long>();var alignedIds=new HashSet<String>();for(var a:aligned){positions.add(a.exposure().position());alignedIds.add(a.exposure().id());}
        result.put("exposure_lead_aligned",alignedIds.contains(x.lead()));result.put("outcome_lead_aligned",alignedIds.contains(y.lead()));result.put("aligned_position_gaps",gaps(positions.stream().mapToLong(Long::longValue).toArray(),start,end));result.put("exposure_observed_position_gaps",gaps(x.positions(),start,end));result.put("outcome_observed_position_gaps",gaps(y.positions(),start,end));
        var rows=new ArrayList<String[]>();long width=end-(long)start+1;long[] alignedPositions=positions.stream().mapToLong(Long::longValue).toArray();
        for(int i=0;i<bins;i++){long left=start+width*i/bins,right=start+width*(i+1)/bins-1;if(right<left)continue;rows.add(new String[]{String.valueOf(left),String.valueOf(right),String.valueOf(count(x.positions(),left,right)),String.valueOf(count(y.positions(),left,right)),String.valueOf(count(alignedPositions,left,right))});}
        FollowupSupport.table(directory.resolve("regional-coverage-bins.tsv"),List.of("start","end","exposure_observed_positions","outcome_observed_positions","aligned_positions"),rows);
        result.put("coverage_bins",bins);result.put("position_counts","Distinct base-pair positions; multiple SNP alleles at one position count once in this display");result.put("interpretation","Endpoint and internal gaps describe observed variant positions, not callable-base coverage. Missingness can hide a different lead signal; no density threshold certifies completeness.");StudyPublicEvidence.write(directory.resolve("regional-coverage.yaml"),result);return result;
    }
    private static Map<String,Object> gaps(long[] positions,int start,int end){if(positions.length==0)return Map.of("positions",0,"status","unavailable");long max=0;for(int i=1;i<positions.length;i++)max=Math.max(max,positions[i]-positions[i-1]);return Map.of("positions",positions.length,"first_position",positions[0],"last_position",positions[positions.length-1],"left_edge_bp",positions[0]-start,"right_edge_bp",end-positions[positions.length-1],"largest_between_observed_positions_bp",max);}
    private static long count(long[] values,long left,long right){return lower(values,right+1)-lower(values,left);}
    private static int lower(long[] values,long target){int left=0,right=values.length;while(left<right){int mid=(left+right)>>>1;if(values[mid]<target)left=mid+1;else right=mid;}return left;}
    private static double number(String raw)throws IOException{try{double v=Double.parseDouble(raw);if(!Double.isFinite(v))throw new NumberFormatException();return v;}catch(NumberFormatException invalid){throw new IOException("Nonfinite or invalid regional numeric input",invalid);}}
}

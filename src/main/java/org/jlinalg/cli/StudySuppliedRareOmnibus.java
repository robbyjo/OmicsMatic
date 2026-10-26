/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jlinalg.enrichment.EnrichmentAnalysis;

/** Typed external omnibus reuse; families and log tails stay scoped to their producer. */
final class StudySuppliedRareOmnibus {
    private StudySuppliedRareOmnibus() { }
    static void normalize(List<String> h,List<String[]> rows,Map<String,Object> definition)throws IOException {
        String unit=text(map(definition.get("method")).get("unit"));
        if(!Set.of("set","gene").contains(unit))throw new IOException("Supplied rare_set_omnibus requires method.unit: set or gene; set names are not inferred as genes");
        for(var entry:map(map(definition.get("method")).get("scope_families")).entrySet()){
            if(!entry.getKey().equals("discovery")&&!entry.getKey().matches("cohort:[A-Za-z][A-Za-z0-9_.-]*"))throw new IOException("External scope_families must name discovery or a cohort scope");
            var family=map(entry.getValue());label(text(family.get("id")));
            if(!Set.of("complete","selected_only","unknown").contains(text(family.getOrDefault("coverage","unknown")))||!Set.of("BH","BY","BONFERRONI","NONE","PROVIDED").contains(text(family.getOrDefault("adjustment","PROVIDED")).toUpperCase(java.util.Locale.ROOT)))throw new IOException("Invalid external scoped-family coverage/adjustment");
            if(family.containsKey("tested_count")&&(!(family.get("tested_count") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()!=n.longValue()))throw new IOException("Scoped tested_count must be a nonnegative integer");
            if(family.containsKey("adjusted_p_from_full_family")&&!(family.get("adjusted_p_from_full_family") instanceof Boolean))throw new IOException("Scoped adjusted_p_from_full_family must be boolean");
            if(family.containsKey("alpha")&&(!(family.get("alpha") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<=0||n.doubleValue()>1))throw new IOException("Scoped alpha must be in (0,1]");
        }
        add(h,rows,"scope","primary");add(h,rows,"mask_id","omnibus");add(h,rows,"test","provided-omnibus");
        add(h,rows,"result_type","unsigned_set");add(h,rows,"status","ok");add(h,rows,"unit_type",unit);
        boolean flags=h.contains("is_primary_test");add(h,rows,"is_primary_test","false");
        for(String[] row:rows){
            String scope=row[h.indexOf("scope")];label(scope);label(row[h.indexOf("feature_id")]);
            for(String field:List.of("test","effective_method"))if(h.contains(field)&&!missing(row[h.indexOf(field)])&&!row[h.indexOf(field)].matches("[A-Za-z][A-Za-z0-9_.:+/-]{0,127}"))throw new IOException("Supplied omnibus method/test fields require bounded scientific identifiers");
            if(h.contains("source_set_ids")&&!missing(row[h.indexOf("source_set_ids")])){Object raw=SimpleJson.parse(row[h.indexOf("source_set_ids")]);if(!(raw instanceof List<?> ids)||ids.size()>10000)throw new IOException("Supplied source_set_ids requires a bounded JSON identifier list");for(Object id:ids){if(!(id instanceof String value))throw new IOException("Supplied source set identities must be strings");label(value);}}
            if(!scope.equals("primary")&&!scope.equals("discovery")&&!scope.matches("cohort:[A-Za-z][A-Za-z0-9_.-]*"))throw new IOException("Supplied omnibus has an unsupported scientific scope");
            boolean primary=scope.equals("primary");if(flags&&!row[h.indexOf("is_primary_test")].equalsIgnoreCase(String.valueOf(primary)))throw new IOException("Omnibus primary flag conflicts with its scope");row[h.indexOf("is_primary_test")]=String.valueOf(primary);
            if(!row[h.indexOf("unit_type")].equals(unit)||!row[h.indexOf("result_type")].equals("unsigned_set")||!row[h.indexOf("mask_id")].equals("omnibus"))throw new IOException("Supplied omnibus unit/type/mask conflicts with its declared unsigned unit contract");
            for(String field:List.of("expected_components","component_count","ineligible_components","unresolved_components","testing_denominator"))if(h.contains(field)&&!missing(row[h.indexOf(field)])){double value=number(row[h.indexOf(field)]);if(!Double.isFinite(value)||value<0||value!=Math.rint(value))throw new IOException("Omnibus counts must be finite nonnegative integers");}
            if(h.containsAll(List.of("expected_components","component_count","ineligible_components","unresolved_components"))){double expected=value(h,row,"expected_components"),sum=value(h,row,"component_count")+value(h,row,"ineligible_components")+value(h,row,"unresolved_components");if(Double.isFinite(expected)&&Double.isFinite(sum)&&expected!=sum)throw new IOException("Supplied omnibus component counts do not cover the declared grid");}
            if(valid(row[h.indexOf("status")])&&Double.isFinite(log(h,row,"p_value"))&&(value(h,row,"unresolved_components")>0||value(h,row,"component_count")==0))throw new IOException("A completed omnibus P value conflicts with unresolved or zero included components");
            for(String field:List.of("p_value","adjusted_p"))log(h,row,field);
        }
    }
    static Map<String,Object> families(List<String> h,List<String[]> rows,Map<String,Object> definition,Map<String,Object> metadata,List<String> needs)throws IOException {
        boolean hasQ=h.contains("adjusted_p")||h.contains("log_adjusted_p");add(h,rows,"adjusted_p","NA");add(h,rows,"log_adjusted_p","NA");add(h,rows,"log_p_value","NA");
        var scopes=new TreeSet<String>();scopes.add("primary");for(String[] row:rows)scopes.add(row[h.indexOf("scope")]);var result=new ArrayList<Map<String,Object>>();Map<String,Object> primary=Map.of();
        for(String scope:scopes){
            var family=new LinkedHashMap<>(map(scope.equals("primary")?definition.get("family"):map(map(definition.get("method")).get("scope_families")).get(scope)));
            var scoped=rows.stream().filter(r->r[h.indexOf("scope")].equals(scope)).toList();var estimable=new ArrayList<String[]>();var logs=new ArrayList<Double>();boolean resolved=true;
            for(String[] row:scoped){double logP=log(h,row,"p_value");if(Double.isFinite(logP)&&valid(row[h.indexOf("status")])){estimable.add(row);logs.add(logP);row[h.indexOf("log_p_value")]=String.valueOf(logP);if(missing(row[h.indexOf("p_value")]))row[h.indexOf("p_value")]=String.valueOf(Math.exp(logP));}else if(!row[h.indexOf("status")].equals("not_eligible"))resolved=false;else if(Double.isFinite(logP))throw new IOException("An independently ineligible omnibus unit cannot carry a finite P value");}
            long denominator=family.get("tested_count") instanceof Number n?n.longValue():-1;String correction=text(family.getOrDefault("adjustment","PROVIDED")).toUpperCase(java.util.Locale.ROOT);
            boolean source=Boolean.TRUE.equals(family.get("adjusted_p_from_full_family"))||hasQ&&!Boolean.FALSE.equals(family.get("adjusted_p_from_full_family"));
            boolean declared="complete".equals(family.get("coverage"));boolean selection=resolved&&denominator>=estimable.size()&&(source||declared&&denominator==estimable.size());
            if(source){for(String[] row:estimable){double q=log(h,row,"adjusted_p");if(!Double.isFinite(q))selection=false;else row[h.indexOf("log_adjusted_p")]=String.valueOf(q);}}
            else if(selection){
                double[] q;if(Set.of("BH","BY","NONE").contains(correction))q=EnrichmentAnalysis.adjustLog(logs.stream().mapToDouble(Double::doubleValue).toArray(),EnrichmentAnalysis.Fdr.valueOf(correction));
                else if(correction.equals("BONFERRONI"))q=logs.stream().mapToDouble(v->Math.min(0,v+Math.log(denominator))).toArray();
                else {q=new double[0];selection=false;}
                if(selection)for(int i=0;i<estimable.size();i++){estimable.get(i)[h.indexOf("log_adjusted_p")]=String.valueOf(q[i]);estimable.get(i)[h.indexOf("adjusted_p")]=String.valueOf(Math.exp(q[i]));}
            }
            boolean complete=selection&&declared&&denominator==estimable.size();if(declared&&denominator>estimable.size())family.put("coverage","incomplete_export");family.put("scope",scope);family.put("complete",complete);family.put("selection_eligible",selection);family.put("testing_denominator",denominator<0?"not_declared":denominator);family.put("supplied_estimable_tests",estimable.size());family.put("adjusted_values_origin",source?"supplied_full_family":"computed_from_declared_complete_family");
            if(!selection)needs.add("The "+scope+" supplied omnibus needs its complete scoped family or retained full-family adjusted P values, denominator and finite log tails; no missing scope is substituted");
            if(scope.equals("primary")){primary=family;metadata.put("rare_primary_complete",selection);}
            result.add(family);
        }
        metadata.put("families",result);metadata.put("primary_identity",Map.of("scope","primary","artifact","rare-omnibus-results.tsv","rule","Source-declared unit omnibus and independent full-family adjustment; no component refitting"));metadata.put("required_manuscript_caveat",metadata.get("required_manuscript_caveat")+" "+StudyRareOmnibus.CAVEAT);return primary;
    }
    static double log(List<String> h,String[] row,String field)throws IOException {
        double p=value(h,row,field),log=value(h,row,"log_"+field);if(Double.isFinite(p)&&(p<0||p>1)||Double.isFinite(log)&&log>0)throw new IOException("Invalid supplied omnibus probability");
        if(Double.isFinite(log)){if(Double.isFinite(p)&&(p==0?Math.exp(log)!=0:Math.abs(Math.log(p)-log)>Math.max(1e-8,Math.abs(log)*1e-7)))throw new IOException("Supplied omnibus probability/log probability conflict");return log;}
        return p>0?Math.log(p):Double.NaN;
    }
    private static boolean valid(String status){return Set.of("ok","tested","complete","single_cohort").contains(status);}
    private static boolean missing(String value){return Set.of("","NA","NaN",".").contains(value);}
    private static double number(String value)throws IOException {if(missing(value))return Double.NaN;try{double n=Double.parseDouble(value);if(!Double.isFinite(n))throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IOException("Invalid supplied omnibus number",e);}}
    private static double value(List<String> h,String[] row,String field)throws IOException{return h.contains(field)?number(row[h.indexOf(field)]):Double.NaN;}
    private static void label(String label)throws IOException{if(label.isBlank()||label.length()>512||label.chars().anyMatch(Character::isISOControl))throw new IOException("Supplied omnibus identities must be bounded scientific labels");}
    private static void add(List<String> h,List<String[]> rows,String field,String value){if(h.contains(field))return;h.add(field);for(int i=0;i<rows.size();i++){var row=Arrays.copyOf(rows.get(i),h.size());row[h.size()-1]=value;rows.set(i,row);}}
}

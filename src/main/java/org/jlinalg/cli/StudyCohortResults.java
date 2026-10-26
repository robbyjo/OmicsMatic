/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** Producer-verified cohort inputs and complete, source-preserving testing families. */
final class StudyCohortResults {
    private StudyCohortResults() { }
    record Row(String feature,Map<String,String> values,double p,double beta,double se,boolean estimable) {
        double number(String field){return numeric(values.get(field));}
        String label(String field){return values.getOrDefault(field,"");}
    }
    record Table(Path source,String sha256,Map<String,Row> rows,Map<String,Double> adjusted,Map<String,Object> family,String adjustment) { }
    static Map<String,Path> sources(Context c)throws IOException {
        var sources=new TreeMap<String,Path>();
        StudyPrimaryResults.cohorts(c).forEach((id,source)->sources.put(id,source.path()));
        return sources;
    }
    static Table read(Path source,String adjustment)throws IOException {
        var table=DelimitedData.readResult(source);table.column("feature_id");table.column("p_value");String hash=FollowupSupport.hash(source);var rows=new TreeMap<String,Row>();var p=new LinkedHashMap<String,Double>();var scopedRows=new ArrayList<String[]>();
        for(String[] values:table.rows()){
            if(table.header().contains("term")&&!Set.of("","pooled").contains(values[table.column("term")]))continue;scopedRows.add(values);var row=new LinkedHashMap<String,String>();for(int i=0;i<table.header().size();i++)row.put(table.header().get(i),values[i]);String feature=row.get("feature_id");if(feature.isBlank()||feature.chars().anyMatch(Character::isISOControl))throw new IOException("Cohort feature identities must be nonblank and contain no control characters");double probability=numeric(row.get("p_value")),beta=numeric(row.get("beta")),se=numeric(row.get("se"));if(Double.isFinite(probability)&&(probability<0||probability>1))throw new IOException("Cohort P value outside [0,1]");String status=row.getOrDefault("status","ok");boolean estimable=Double.isFinite(probability)&&Set.of("ok","tested","complete","single_cohort").contains(status);Row r=new Row(feature,Map.copyOf(row),probability,beta,se,estimable);if(rows.putIfAbsent(feature,r)!=null)throw new IOException("Ambiguous cohort feature/contrast: "+feature);if(estimable)p.put(feature,probability);
        }
        var family=StudySuppliedResultPolicy.family(source);var q=StudySuppliedFamilies.adjusted(source,table.selectRows(scopedRows),p,adjust(p,adjustment));if(!hash.equals(FollowupSupport.hash(source)))throw new IOException("Cohort source changed while reading its testing family");return new Table(source,hash,Map.copyOf(rows),Map.copyOf(q),family,family.isEmpty()?adjustment:text(family.getOrDefault("adjustment","PROVIDED")));
    }
    static Map<String,Double> adjust(Map<String,Double> p,String method){if(method.equals("NONE"))return new LinkedHashMap<>(p);if(!Set.of("BH","BY").contains(method))throw new IllegalArgumentException("Cohort diagnostics support BH, BY or NONE; no adjustment was substituted");var q=StudyCliExecutor.bh(p);if(method.equals("BY")){double harmonic=0;for(int i=1;i<=p.size();i++)harmonic+=1.0/i;double h=harmonic;q.replaceAll((id,value)->Math.min(1,value*h));}return q;}
    static double numeric(String value){if(value==null||value.isBlank()||Set.of("NA","NaN","nan",".","?").contains(value))return Double.NaN;try{double n=Double.parseDouble(value);return Double.isFinite(n)?n:Double.NaN;}catch(NumberFormatException invalid){return Double.NaN;}}
    static Object finite(double n){return Double.isFinite(n)?n:"NA";}
    static String method(Context c){return text(map(StudyResultTables.analysis(c).get("multiple_testing")).getOrDefault("method","BH")).toUpperCase(java.util.Locale.ROOT);}
    static double alpha(Context c){return ((Number)map(StudyResultTables.analysis(c).get("multiple_testing")).getOrDefault("alpha",.05)).doubleValue();}
    static Map<String,Object> receipt(Context c,Table t){return Map.of("artifact",c.project().relativize(t.source()).toString().replace('\\','/'),"sha256",t.sha256(),"source_rows",t.rows().size(),"estimable_tests",t.adjusted().size(),"adjustment",t.adjustment(),"source_testing_family",t.family());}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal UTF-8 identities, explicit float64 data and original sample order. */
final class LimmaWorkflowFiles {
    private LimmaWorkflowFiles() { }
    record Prepared(List<String> features,List<String> samples,List<String> coefficients,List<String> resultIds,List<String> setIds) { }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        String op=text(r,"operation");int maxF=((Number)r.get("maximum_features")).intValue(),maxS=((Number)r.get("maximum_samples")).intValue();
        List<String> features,samples=List.of(),coefficients=List.of(),sets=List.of();int g,n=0,d=0;var resultIds=new ArrayList<String>();
        if(op.equals("CAMERAPR")) {
            var stats=SusieWorkflowFiles.table(resolve(root,text(r,"statistics")),maxF,2);
            if(!Arrays.equals(stats.header(),new String[]{"feature_id","statistic"})||stats.rows().size()<3)throw new IOException("Statistics require feature_id,statistic and at least three unique features");
            features=identities(stats.rows().stream().map(x->x[0]).toList());g=features.size();var text=new StringBuilder("feature_id\tstatistic\n");
            for(var row:stats.rows())text.append(row[0]).append('\t').append(SusieWorkflowFiles.number(row[1])).append('\n');transport(root,"statistics.tsv",text.toString(),compare);
            var membership=SusieWorkflowFiles.table(resolve(root,text(r,"gene_sets")),((Number)r.get("maximum_memberships")).intValue(),2);
            if(!Arrays.equals(membership.header(),new String[]{"set_id","feature_id"})||membership.rows().isEmpty())throw new IOException("Gene sets require nonempty set_id,feature_id memberships");
            var featureSet=new HashSet<>(features);var members=new LinkedHashMap<String,Set<String>>();text=new StringBuilder("set_id\tfeature_id\n");
            for(var row:membership.rows()) {
                SusieWorkflowFiles.exactIdentity(row[0]);if(!featureSet.contains(row[1]))throw new IOException("Gene-set member is absent from the exact supplied universe; no intersection or ID repair");
                if(!members.computeIfAbsent(row[0],k->new HashSet<>()).add(row[1]))throw new IOException("Repeated gene within one set; no deduplication");text.append(row[0]).append('\t').append(row[1]).append('\n');
            }
            if(members.size()>((Number)r.get("maximum_sets")).intValue())throw new IOException("maximum_sets exceeded");sets=List.copyOf(members.keySet());transport(root,"gene_sets.tsv",text.toString(),compare);
            Object correlations=object(r.get("camera"),"camera").get("inter_gene_cor");if(correlations instanceof List<?> values&&values.size()!=sets.size())throw new IOException("Per-set correlations must match exact first-occurrence set order");
            work(r,(long)g*sets.size()*Math.max(1,Math.round(Math.log(g)+1)));
        }else {
            var matrix=SusieWorkflowFiles.table(resolve(root,text(r,"matrix")),maxF,maxS+1);
            if(matrix.header().length<3||!matrix.header()[0].equals("feature_id")||matrix.rows().isEmpty())throw new IOException("Matrix requires feature_id, at least two sample columns and nonempty features");
            features=identities(matrix.rows().stream().map(x->x[0]).toList());samples=identities(Arrays.asList(matrix.header()).subList(1,matrix.header().length));g=features.size();n=samples.size();
            if((long)g*n>((Number)r.get("maximum_entries")).longValue())throw new IOException("maximum_entries exceeded");
            transport(root,"matrix.tsv",numeric(matrix,features,samples,LimmaWorkflowContracts.counts(op),r.get("units").equals("raw-counts")),compare);
            if(r.containsKey("weights")) {
                var weights=SusieWorkflowFiles.table(resolve(root,text(r,"weights")),g,n+1);
                dimensions(weights,"feature_id",features,samples);transport(root,"weights.tsv",numeric(weights,features,samples,true,false),compare);
            }
            if(r.containsKey("design")) {
                var design=SusieWorkflowFiles.table(resolve(root,text(r,"design")),n,((Number)r.get("maximum_design_columns")).intValue()+1);
                if(design.header().length<2||!design.header()[0].equals("sample_id"))throw new IOException("Design requires sample_id and at least one numeric coefficient");
                coefficients=identities(Arrays.asList(design.header()).subList(1,design.header().length));d=coefficients.size();dimensions(design,"sample_id",samples,coefficients);
                if(d>=n)throw new IOException("Design requires positive residual sample degrees of freedom");transport(root,"design.tsv",numeric(design,samples,coefficients,false,false),compare);
            }
            if(r.containsKey("covariates")) {
                var cov=SusieWorkflowFiles.table(resolve(root,text(r,"covariates")),n,((Number)r.get("maximum_design_columns")).intValue()+1);
                if(cov.header().length<2)throw new IOException("Covariates require numeric columns");var columns=identities(Arrays.asList(cov.header()).subList(1,cov.header().length));dimensions(cov,"sample_id",samples,columns);transport(root,"covariates.tsv",numeric(cov,samples,columns,false,false),compare);
            }
            for(String key:List.of("block","batch","batch2"))if(r.containsKey(key))factor(root,r,key,samples,compare);
            for(String key:List.of("libraries","norm_factors"))if(r.containsKey(key))sidecar(root,r,key,"sample_id",key.equals("libraries")?"library_size":"norm_factor",samples,true,compare);
            if(r.containsKey("trend_covariate"))sidecar(root,r,"trend_covariate","feature_id","covariate",features,false,compare);
            if(LimmaWorkflowContracts.fitted(op))for(Object value:list(r.get("contrasts"),"contrasts")) {
                var row=object(value,"contrast");if(list(row.get("values"),"contrast values").size()!=d)throw new IOException("Contrast must match every design column, without recycling");resultIds.add(text(row,"id"));
            }
            if(LimmaWorkflowContracts.counts(op)) {
                var normalization=object(r.get("normalization"),"normalization");if(normalization.containsKey("reference_column")&&((Number)normalization.get("reference_column")).intValue()>n)throw new IOException("reference_column exceeds sample population");
            }
            try{work(r,Math.multiplyExact(Math.multiplyExact((long)g,n),Math.multiplyExact(Math.max(1L,(long)d*d),Math.max(1L,resultIds.size()+4L))));}
            catch(ArithmeticException e){throw new IOException("Declared dense work overflows capacity",e);}
            if(LimmaWorkflowContracts.fitted(op)&&r.get("adjust_method").equals("hommel"))try {
                long family=r.get("adjustment_scope").equals("all-contrasts")?Math.multiplyExact((long)g,resultIds.size()):g;
                work(r,Math.multiplyExact(Math.multiplyExact(family,family),r.get("adjustment_scope").equals("all-contrasts")?1:resultIds.size()));
            }catch(ArithmeticException e){throw new IOException("Declared quadratic Hommel work overflows capacity",e);}
        }
        var p=new LinkedHashMap<String,Object>();for(String key:List.of("profile","operation","units","normalization","voom","correlation","moderation","contrasts","adjust_method","adjustment_scope","camera","normalize_method","block_levels","batch_levels","batch2_levels","seed"))if(r.containsKey(key))p.put(key,r.get(key));
        for(String key:LimmaWorkflowContracts.INPUTS)p.put("has_"+key,r.containsKey(key));p.put("features",features.size());p.put("samples",samples.size());p.put("design_columns",coefficients.size());p.put("sets",sets.size());
        transport(root,"parameters.json",new String(RsemJson.bytes(p),StandardCharsets.UTF_8),compare);return new Prepared(features,samples,coefficients,List.copyOf(resultIds),sets);
    }
    private static void dimensions(SusieWorkflowFiles.Table t,String first,List<String> rows,List<String> columns)throws IOException {
        if(t.header().length!=columns.size()+1||!t.header()[0].equals(first)||!Arrays.asList(t.header()).subList(1,t.header().length).equals(columns)||t.rows().size()!=rows.size())throw new IOException("Exact matrix header/population differs");
        for(int i=0;i<rows.size();i++)if(!t.rows().get(i)[0].equals(rows.get(i)))throw new IOException("Exact matrix row population/order differs");
    }
    private static String numeric(SusieWorkflowFiles.Table table,List<String> rows,List<String> columns,boolean nonnegative,boolean integer)throws IOException {
        if(table.rows().size()!=rows.size()||table.header().length!=columns.size()+1)throw new IOException("Numeric dimensions differ");
        var out=new StringBuilder(table.header()[0]).append('\t').append(String.join("\t",columns)).append('\n');
        for(int i=0;i<rows.size();i++) {
            var row=table.rows().get(i);if(!row[0].equals(rows.get(i)))throw new IOException("Numeric row identity/order differs");out.append(row[0]);
            for(int j=0;j<columns.size();j++) {
                double v=SusieWorkflowFiles.number(row[j+1]);if(nonnegative&&v<0)throw new IOException("Negative counts/weights; no repair");
                if(integer) {
                    if(v!=Math.rint(v)||v>9007199254740991d)throw new IOException("Raw counts require exact nonnegative integers <=2^53-1; declare estimated-counts for fractional estimates");
                    try{new BigDecimal(row[j+1]).toBigIntegerExact();}catch(ArithmeticException e){throw new IOException("Raw decimal count is fractional before float64 transport",e);}
                }out.append('\t').append(v);
            }out.append('\n');
        }return out.toString();
    }
    private static void factor(Path root,Map<String,Object> r,String key,List<String> samples,boolean compare)throws IOException {
        var t=SusieWorkflowFiles.table(resolve(root,text(r,key)),samples.size(),2);var levels=strings(r.get(key+"_levels"),key+"_levels",2,10000);
        if(!Arrays.equals(t.header(),new String[]{"sample_id",key})||t.rows().size()!=samples.size())throw new IOException("Factor requires exact sample_id,"+key+" population");
        var used=new HashSet<String>();var out=new StringBuilder("sample_id\t").append(key).append('\n');
        for(int i=0;i<samples.size();i++){var row=t.rows().get(i);if(!row[0].equals(samples.get(i))||!levels.contains(row[1]))throw new IOException("Factor identity/order/level differs: "+key);used.add(row[1]);out.append(row[0]).append('\t').append(row[1]).append('\n');}
        if(used.size()!=levels.size())throw new IOException("Unused declared factor levels; no dropping");if(key.equals("block")&&used.size()==samples.size())throw new IOException("Blocking requires replicated observations");transport(root,key+".tsv",out.toString(),compare);
    }
    private static void sidecar(Path root,Map<String,Object> r,String key,String id,String field,List<String> ids,boolean positive,boolean compare)throws IOException {
        var t=SusieWorkflowFiles.table(resolve(root,text(r,key)),ids.size(),2);if(!Arrays.equals(t.header(),new String[]{id,field})||t.rows().size()!=ids.size())throw new IOException("Exact sidecar header/population differs: "+key);
        var out=new StringBuilder(id).append('\t').append(field).append('\n');for(int i=0;i<ids.size();i++){var row=t.rows().get(i);if(!row[0].equals(ids.get(i)))throw new IOException("Sidecar identity/order differs: "+key);double v=SusieWorkflowFiles.number(row[1]);if(positive&&!(v>0))throw new IOException("Sidecar values must be positive: "+key);out.append(row[0]).append('\t').append(v).append('\n');}transport(root,key+".tsv",out.toString(),compare);
    }
    private static List<String> identities(List<String> values)throws IOException {if(values.isEmpty())throw new IOException("Empty identity population");var seen=new HashSet<String>();for(String id:values){SusieWorkflowFiles.exactIdentity(id);if(!seen.add(id))throw new IOException("Repeated exact identity");}return List.copyOf(values);}
    private static void work(Map<String,Object> r,long value)throws IOException {if(value<0||value>((Number)r.get("maximum_work")).longValue())throw new IOException("maximum_work exceeded by explicit dense operation estimate");}
    private static void transport(Path root,String name,String content,boolean compare)throws IOException {var p=root.resolve("worker/"+name);byte[] raw=content.getBytes(StandardCharsets.UTF_8);if(compare){if(!Arrays.equals(raw,Files.readAllBytes(p)))throw new IOException("Prepared transport differs: "+name);}else Files.write(p,raw,StandardOpenOption.CREATE_NEW);}
}

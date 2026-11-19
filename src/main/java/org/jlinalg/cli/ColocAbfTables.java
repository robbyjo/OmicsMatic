/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.coloc.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Exact unquoted TSV transport and complete numerical result rederivation. */
final class ColocAbfTables {
    private ColocAbfTables() { }
    record Parsed(ColocAbfInput input,double[] weights,List<String> warnings) { }
    private static final List<String> NUMERIC=List.of("beta","varbeta","pvalues","MAF","prior_weight");
    static Parsed read(Path path,Map<String,Object> request,long maximumRows,String trait)throws IOException {
        var columns=object(request.get("columns"),"columns");
        List<String> ids=new ArrayList<>();List<double[]> numbers=new ArrayList<>();List<Long> positions=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        try(var in=new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder()))){
            String first=in.readLine();if(first==null)throw new IOException("Empty summary table: "+path);
            String[] header=first.split("\t",-1);if(header.length>512)throw new IOException("Summary header exceeds 512 columns");
            var byName=new HashMap<String,Integer>();
            for(int i=0;i<header.length;i++){
                ColocAbfContracts.tableIdentity(header[i],"header");
                if(byName.put(header[i],i)!=null)throw new IOException("Duplicate exact summary header: "+header[i]);
            }
            var index=new HashMap<String,Integer>();
            for(var e:columns.entrySet()){
                Integer c=byName.get(e.getValue());if(c==null)throw new IOException("Mapped source column missing: "+e.getValue());index.put(e.getKey(),c);
            }
            if(header.length>columns.size())warnings.add("UNMAPPED_COLUMNS_TRAIT_"+trait+": "+(header.length-columns.size())+" captured annotation columns are not used in ABF");
            long row=1;
            for(String line;(line=in.readLine())!=null;){
                row++;if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Summary parsing interrupted");
                if(ids.size()>=maximumRows)throw new IOException("Complete variant population exceeds maximum_variants in trait "+trait);
                String[] fields=line.split("\t",-1);
                if(fields.length!=header.length)throw new IOException("Ragged/blank unquoted TSV row "+row+" in trait "+trait);
                String id=fields[index.get("snp")];ColocAbfContracts.tableIdentity(id,"variant ID");ids.add(id);
                double[] values=new double[NUMERIC.size()];
                for(int k=0;k<NUMERIC.size();k++)if(index.containsKey(NUMERIC.get(k)))values[k]=decimal(fields[index.get(NUMERIC.get(k))],NUMERIC.get(k)+" row "+row);
                numbers.add(values);
                if(index.containsKey("position")){
                    String value=fields[index.get("position")];
                    try {if(!value.matches("[0-9]+"))throw new NumberFormatException();positions.add(Long.parseLong(value));}
                    catch(NumberFormatException e){throw new IOException("position must be a nonnegative long integer at row "+row,e);}
                }
            }
        }
        long[] pos=columns.containsKey("position")?positions.stream().mapToLong(Long::longValue).toArray():null;
        double[][] vectors=new double[NUMERIC.size()][];
        for(int k=0;k<NUMERIC.size();k++)if(columns.containsKey(NUMERIC.get(k))){vectors[k]=new double[ids.size()];for(int i=0;i<ids.size();i++)vectors[k][i]=numbers.get(i)[k];}
        boolean estimates=columns.containsKey("beta");
        if(estimates&&columns.containsKey("pvalues"))warnings.add("BETA_VARIANCE_PRECEDENCE_TRAIT_"+trait+": supplied P values are validated but do not determine ABFs");
        if(!estimates&&request.containsKey("sdY"))warnings.add("SDY_UNUSED_IN_P_VALUE_MODE_TRAIT_"+trait+": source quantitative approximation assumes unit variance");
        if(estimates&&request.get("type").equals("cc")&&(request.containsKey("N")||request.containsKey("s")))warnings.add("CASE_CONTROL_REGRESSION_SCALE_TRAIT_"+trait+": supplied N/s do not determine regression ABFs");
        var input=new ColocAbfInput(request.get("type").equals("quant")?ColocAbfInput.TraitType.QUANTITATIVE:ColocAbfInput.TraitType.CASE_CONTROL,
            ids,vectors[0],vectors[1],vectors[2],vectors[3],optional(request,"N"),optional(request,"sdY"),optional(request,"s"),pos);
        return new Parsed(input,vectors[4],List.copyOf(warnings));
    }
    private static Double optional(Map<String,Object> request,String key){return request.containsKey(key)?((Number)request.get(key)).doubleValue():null;}
    private static double decimal(String text,String label)throws IOException {
        if(!text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require literal finite decimal "+label+"; no missing-value replacement or whitespace trimming");
        double value;
        try {value=Double.parseDouble(text);}catch(NumberFormatException e){throw new IOException("Invalid decimal "+label,e);}
        if(!Double.isFinite(value))throw new IOException("Nonfinite decimal "+label);
        if(value==0&&new java.math.BigDecimal(text).signum()!=0)throw new IOException("Nonzero decimal underflows to zero: "+label);
        return value;
    }
    static Map<String,Object> result(ColocAbfResult value,List<String> warnings){
        var result=new LinkedHashMap<String,Object>();result.put("profile",ColocAbfContracts.PROFILE);
        result.put("trait1_variants",value.trait1Variants());result.put("trait2_variants",value.trait2Variants());
        result.put("common_variants",value.variants().size());result.put("position_key_mismatches",value.positionKeyMismatches());
        result.put("hypothesis_posteriors",Arrays.stream(value.hypothesisPosteriors()).boxed().toList());
        result.put("effective_priors",Arrays.stream(value.effectivePriors()).boxed().toList());
        result.put("sdY1",value.trait1StandardDeviation());result.put("sdY2",value.trait2StandardDeviation());
        result.put("warnings",warnings);result.put("scope",ColocAbfContracts.SCOPE);return result;
    }
    static void tables(Path root,ColocAbfResult value,ColocAbfInput firstInput,ColocAbfInput secondInput,long maximumBytes,boolean compare)throws IOException {
        long[] remaining={maximumBytes};
        try(var out=new Rows(root.resolve("summary.tsv"),Set.of(1),compare,remaining)){
            out.header("field","value");out.row("trait1_variants",Integer.toString(value.trait1Variants()));out.row("trait2_variants",Integer.toString(value.trait2Variants()));
            out.row("common_variants",Integer.toString(value.variants().size()));
            for(int h=0;h<5;h++)out.row("PP.H"+h+".abf",Double.toString(value.hypothesisPosteriors()[h]));
            for(int p=0;p<3;p++)out.row(List.of("p1","p2","p12").get(p),Double.toString(value.effectivePriors()[p]));
            out.row("sdY1",value.trait1StandardDeviation()==null?".":value.trait1StandardDeviation().toString());
            out.row("sdY2",value.trait2StandardDeviation()==null?".":value.trait2StandardDeviation().toString());
        }
        var firstIndex=new HashMap<String,Integer>();var secondIndex=new HashMap<String,Integer>();
        for(int i=0;i<firstInput.variants().size();i++)firstIndex.put(firstInput.variants().get(i),i);
        for(int i=0;i<secondInput.variants().size();i++)secondIndex.put(secondInput.variants().get(i),i);
        double[] firstP=firstInput.usesEffectEstimates()?null:firstInput.pValues(),secondP=secondInput.usesEffectEstimates()?null:secondInput.pValues();
        double[] firstMaf=firstP==null?null:firstInput.frequencies(),secondMaf=secondP==null?null:secondInput.frequencies();
        try(var out=new Rows(root.resolve("variants.tsv"),Set.of(4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19),compare,remaining)){
            out.header("snp","position1","position2","position","pvalues.df1","MAF.df1","N.df1","V.df1","z.df1","r.df1","lABF.df1","pvalues.df2","MAF.df2","N.df2","V.df2","z.df2","r.df2","lABF.df2","internal.sum.lABF","SNP.PP.H4");
            for(var v:value.variants()){
                int i=firstIndex.get(v.id()),j=secondIndex.get(v.id());Long position=v.position1()!=null?v.position1():v.position2();
                out.row(v.id(),v.position1()==null?".":v.position1().toString(),v.position2()==null?".":v.position2().toString(),position==null?".":position.toString(),
                    firstP==null?".":Double.toString(firstP[i]),firstMaf==null?".":Double.toString(firstMaf[i]),firstP==null?".":firstInput.sampleSize().toString(),
                    Double.toString(v.variance1()),Double.toString(v.z1()),Double.toString(v.shrinkage1()),Double.toString(v.logAbf1()),
                    secondP==null?".":Double.toString(secondP[j]),secondMaf==null?".":Double.toString(secondMaf[j]),secondP==null?".":secondInput.sampleSize().toString(),
                    Double.toString(v.variance2()),Double.toString(v.z2()),Double.toString(v.shrinkage2()),Double.toString(v.logAbf2()),
                    Double.toString(v.logAbf1()+v.logAbf2()),Double.toString(v.sourceConditionalSharedPosterior()));
            }
        }
        double[] first=value.trait1Weights(),second=value.trait2Weights();
        try(var out=new Rows(root.resolve("matched-weights.tsv"),Set.of(1,2),compare,remaining)){
            out.header("snp","prior_weight1","prior_weight2");
            for(int i=0;i<value.variants().size();i++)out.row(value.variants().get(i).id(),first==null?".":Double.toString(first[i]),second==null?".":Double.toString(second[i]));
        }
    }
    static boolean close(double a,double b){
        if(a==b)return true;
        if(!Double.isFinite(a)||!Double.isFinite(b)||a==0||b==0)return false;
        return Math.abs(a-b)<=Math.max(16*Math.ulp(a),2e-12*Math.abs(a));
    }
    static void compareJson(Object expected,Object actual)throws IOException {
        if(expected instanceof Number a&&actual instanceof Number b){if(close(a.doubleValue(),b.doubleValue()))return;}
        else if(expected instanceof Map<?,?> a&&actual instanceof Map<?,?> b){
            if(a.keySet().equals(b.keySet())){for(Object key:a.keySet())compareJson(a.get(key),b.get(key));return;}
        }else if(expected instanceof List<?> a&&actual instanceof List<?> b){
            if(a.size()==b.size()){for(int i=0;i<a.size();i++)compareJson(a.get(i),b.get(i));return;}
        }else if(Objects.equals(expected,actual))return;
        // Only the source sdY warning contains a recomputed floating value.
        if(expected instanceof String a&&actual instanceof String b&&a.startsWith("SDY_ESTIMATED_TRAIT_")&&b.startsWith("SDY_ESTIMATED_TRAIT_")){
            int colon=a.indexOf(": ");
            if(colon>=0&&a.substring(0,colon+2).equals(b.substring(0,Math.min(colon+2,b.length())))){
                try {if(close(Double.parseDouble(a.substring(colon+2)),Double.parseDouble(b.substring(colon+2))))return;}
                catch(NumberFormatException ignored){/* Report the complete semantic mismatch below. */}
            }
        }
        throw new IOException("Recomputed COLOC JSON differs from captured scientific output");
    }
    private static final class Rows implements AutoCloseable {
        final BufferedReader input;final BufferedWriter output;final Set<Integer> numeric;final long[] remaining;
        Rows(Path path,Set<Integer> numeric,boolean compare,long[] remaining)throws IOException {
            this.numeric=numeric;this.remaining=remaining;
            input=compare?Files.newBufferedReader(path,StandardCharsets.UTF_8):null;
            output=compare?null:Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }
        void header(String... fields)throws IOException {line(fields,true);}
        void row(String... fields)throws IOException {line(fields,false);}
        private void line(String[] fields,boolean header)throws IOException {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("COLOC table publication/audit interrupted");
            String line=String.join("\t",fields)+"\n";remaining[0]-=line.getBytes(StandardCharsets.UTF_8).length;
            if(remaining[0]<0)throw new IOException("Complete output tables exceed maximum_output_bytes");
            if(output!=null){output.write(line);return;}
            String saved=input.readLine();if(saved==null)throw new IOException("Incomplete COLOC output table");String[] actual=saved.split("\t",-1);
            if(actual.length!=fields.length)throw new IOException("COLOC output table width differs");
            for(int i=0;i<fields.length;i++){
                if(fields[i].equals(actual[i]))continue;
                if(!header&&numeric.contains(i)&&!fields[i].equals(".")&&!actual[i].equals(".")){
                    try{if(ColocAbfTables.close(Double.parseDouble(fields[i]),Double.parseDouble(actual[i])))continue;}
                    catch(NumberFormatException ignored){/* Refuse invalid captured scientific values. */}
                }
                throw new IOException("Recomputed COLOC table field differs at column "+(i+1));
            }
        }
        public void close()throws IOException {
            if(output!=null)output.close();
            if(input!=null){try{if(input.readLine()!=null)throw new IOException("Unexpected extra COLOC output rows");}finally{input.close();}}
        }
    }
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Closed marker requests: literal identities and typed covariates, never user R code. */
final class SeuratDifferentialContracts {
    private SeuratDifferentialContracts() { }
    static final String PROFILE="Seurat-5.6.0-R4.6.1-base";
    static final List<String> INPUTS=List.of("counts","features","cells","metadata","data","scale_data","test_features");
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","out","counts","features","cells","metadata","group_column","group_levels","covariates","contrast","data_source","data","scale_data","test_features","tfidf_method","tfidf_scale","test","test_slot","fc_slot","fc_normalization","pseudocount","log_base","min_pct","min_diff_pct","logfc_threshold","only_positive","max_cells_per_group","min_cells_group","return_threshold","seed","densify","save_source_objects","rscript","r_libraries","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_features","maximum_cells","maximum_entries","maximum_dense_entries","maximum_work","maximum_result_rows"));
        if(integer(raw,"schema_version",0,1,1)!=1||!Set.of("PAIR","ALL").contains(text(raw,"mode"))||!PROFILE.equals(raw.get("profile")))throw new IOException("Require schema_version1, mode PAIR/ALL and explicit profile "+PROFILE);
        var r=new LinkedHashMap<String,Object>(raw);
        for(String key:List.of("out","counts","features","cells","metadata","group_column","rscript"))text(raw,key);
        for(String key:INPUTS)if(raw.containsKey(key))text(raw,key);
        var levels=strings(raw.get("group_levels"),"group_levels",2,512);r.put("group_levels",levels);identity(text(raw,"group_column"));
        var covariates=new ArrayList<Map<String,Object>>();var names=new HashSet<String>();
        for(Object item:list(raw.getOrDefault("covariates",List.of()),"covariates")) {
            var c=object(item,"covariate");keys(c,Set.of("name","kind","levels"));String name=text(c,"name");identity(name);if(!names.add(name))throw new IOException("Repeated covariate");
            String kind=choice(c,"kind","",Set.of("numeric","factor"));var v=new LinkedHashMap<String,Object>();v.put("name",name);v.put("kind",kind);
            if(kind.equals("factor"))v.put("levels",strings(c.get("levels"),"covariate levels",1,128));else if(c.containsKey("levels"))throw new IOException("Numeric covariates cannot have levels");covariates.add(v);
        }
        if(covariates.size()>32)throw new IOException("At most32 typed covariates");r.put("covariates",covariates);
        if("PAIR".equals(r.get("mode"))) {
            var c=object(raw.get("contrast"),"contrast");keys(c,Set.of("numerator","denominator"));var a=strings(c.get("numerator"),"contrast numerator",1,512);if(!levels.containsAll(a))throw new IOException("Unknown numerator group");
            var normalized=new LinkedHashMap<String,Object>();normalized.put("numerator",a);
            if(c.containsKey("denominator")){var b=strings(c.get("denominator"),"contrast denominator",1,512);if(!levels.containsAll(b)||b.stream().anyMatch(a::contains))throw new IOException("Denominator unknown or overlapping numerator");normalized.put("denominator",b);}
            else if(a.size()==levels.size())throw new IOException("Rest denominator is empty");r.put("contrast",normalized);
        }else if(raw.containsKey("contrast"))throw new IOException("ALL does not accept a pair contrast");
        String source=choice(raw,"data_source","native_tfidf",Set.of("native_tfidf","provided","counts"));r.put("data_source",source);
        if(source.equals("provided")!=raw.containsKey("data"))throw new IOException("Provided data_source requires data; other sources refuse unused data");
        if(!source.equals("native_tfidf")&&(raw.containsKey("tfidf_method")||raw.containsKey("tfidf_scale")))throw new IOException("TF-IDF options require native_tfidf");
        if(source.equals("native_tfidf")){r.put("tfidf_method",integer(raw,"tfidf_method",1,1,4));r.put("tfidf_scale",real(raw,"tfidf_scale",10000,0,1e100,false));}
        String test=choice(raw,"test","wilcox",Set.of("wilcox","LR"));r.put("test",test);
        if(!test.equals("LR")&&!covariates.isEmpty())throw new IOException("This Wilcoxon backend does not use covariates; no setting is silently ignored");
        for(String slot:List.of("test_slot","fc_slot"))r.put(slot,choice(raw,slot,"data",Set.of("counts","data","scale.data")));
        boolean scale=r.get("test_slot").equals("scale.data")||r.get("fc_slot").equals("scale.data");if(scale!=raw.containsKey("scale_data"))throw new IOException("scale_data required exactly when a selected slot needs it");
        String norm=choice(raw,"fc_normalization","unrecorded",Set.of("unrecorded","LogNormalize","other"));if(r.get("mode").equals("ALL")&&!norm.equals("unrecorded"))throw new IOException("Original ALL obtains normalization from object history; prepared objects have no history. Choose unrecorded explicitly");r.put("fc_normalization",norm);
        r.put("pseudocount",real(raw,"pseudocount",1,0,1e100,true));double base=real(raw,"log_base",2,0,1e100,false);if(base==1)throw new IOException("Log base cannot equal one");r.put("log_base",base);
        r.put("min_pct",real(raw,"min_pct",.01,0,1,true));Object difference=raw.getOrDefault("min_diff_pct","-Inf");if(!"-Inf".equals(difference))difference=real(Map.of("value",Objects.requireNonNullElse(difference,"invalid")),"value",0,-1,1,true);r.put("min_diff_pct",difference);
        r.put("logfc_threshold",real(raw,"logfc_threshold",.1,0,1e100,true));r.put("only_positive",bool(raw,"only_positive",false));
        Object max=raw.getOrDefault("max_cells_per_group","Inf");if(!"Inf".equals(max))max=integer(Map.of("value",Objects.requireNonNullElse(max,"invalid")),"value",0,1,2_000_000);r.put("max_cells_per_group",max);
        r.put("min_cells_group",integer(raw,"min_cells_group",3,1,2_000_000));
        if(raw.containsKey("return_threshold")&&!r.get("mode").equals("ALL"))throw new IOException("return_threshold is an ALL raw-p filter, not a pairwise adjustment option");if(r.get("mode").equals("ALL"))r.put("return_threshold",real(raw,"return_threshold",.01,0,2,true));
        r.put("seed",integer(raw,"seed",1,0,Integer.MAX_VALUE));r.put("densify",bool(raw,"densify",false));r.put("save_source_objects",bool(raw,"save_source_objects",true));
        var libraries=strings(raw.get("r_libraries"),"r_libraries",1,16);r.put("r_libraries",libraries);
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512_000_000,1,20_000_000_000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2_000_000_000L,1,40_000_000_000L));
        r.put("maximum_features",integer(raw,"maximum_features",2_000_000,1,2_000_000));r.put("maximum_cells",integer(raw,"maximum_cells",2_000_000,2,2_000_000));r.put("maximum_entries",integer(raw,"maximum_entries",10_000_000,1,20_000_000));r.put("maximum_dense_entries",integer(raw,"maximum_dense_entries",10_000_000,1,200_000_000));r.put("maximum_work",integer(raw,"maximum_work",500_000_000,1,10_000_000_000L));r.put("maximum_result_rows",integer(raw,"maximum_result_rows",2_000_000,1,20_000_000));return r;
    }
    static List<String> strings(Object raw,String label,int min,int max)throws IOException {
        var values=list(raw,label);if(values.size()<min||values.size()>max)throw new IOException("Invalid array size: "+label);var result=new ArrayList<String>();var unique=new HashSet<String>();
        for(Object v:values){if(!(v instanceof String s))throw new IOException("Require string: "+label);identity(s);if(!unique.add(s))throw new IOException("Repeated identity: "+label);result.add(s);}return List.copyOf(result);
    }
    static Map<String,Object> read(Path p)throws IOException{return read(p,2_000_000);}
    static Map<String,Object> read(Path p,long cap)throws IOException {if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>cap)throw new IOException("Require a bounded regular UTF-8 JSON document");return object(SimpleJson.parse(Files.readString(p,StandardCharsets.UTF_8)),"JSON");}
    static Map<String,Object> object(Object value,String label)throws IOException {if(!(value instanceof Map<?,?> m))throw new IOException("Require object: "+label);var r=new LinkedHashMap<String,Object>();for(var e:m.entrySet()){if(!(e.getKey() instanceof String key))throw new IOException("Require text keys");r.put(key,e.getValue());}return r;}
    static List<?> list(Object value,String label)throws IOException {if(!(value instanceof List<?> l))throw new IOException("Require array: "+label);return l;}
    static void keys(Map<String,Object> value,Set<String> allowed)throws IOException {for(String key:value.keySet())if(!allowed.contains(key))throw new IOException("Unknown field: "+key);}
    static String text(Map<String,Object> value,String key)throws IOException {if(!(value.get(key) instanceof String s)||s.isBlank()||s.indexOf(0)>=0)throw new IOException("Require nonblank text: "+key);return s;}
    static boolean bool(Map<String,Object> value,String key,boolean fallback)throws IOException {if(!value.containsKey(key))return fallback;if(!(value.get(key) instanceof Boolean b))throw new IOException("Require boolean: "+key);return b;}
    static long integer(Map<String,Object> value,String key,long fallback,long min,long max)throws IOException {if(!value.containsKey(key))return fallback;Object n=value.get(key);if(!(n instanceof Number))throw new IOException("Require integer: "+key);try{long x=new BigDecimal(n.toString()).longValueExact();if(x<min||x>max)throw new ArithmeticException();return x;}catch(ArithmeticException e){throw new IOException("Integer outside range: "+key,e);}}
    static double real(Map<String,Object> value,String key,double fallback,double min,double max,boolean includeMin)throws IOException {if(!value.containsKey(key))return fallback;if(!(value.get(key) instanceof Number n))throw new IOException("Require number: "+key);double x=n.doubleValue();if(!Double.isFinite(x)||x>max||(includeMin?x<min:x<=min))throw new IOException("Numeric setting outside range: "+key);return x;}
    static String choice(Map<String,Object> value,String key,String fallback,Set<String> choices)throws IOException {String s=value.containsKey(key)?text(value,key):fallback;if(!choices.contains(s))throw new IOException("Unsupported "+key+": "+s);return s;}
    static void identity(String s)throws IOException {if(s.isBlank()||s.length()>4094||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require exact nonblank identities without controls");}
    static Path resolve(Path parent,String name){Path p=Path.of(name);return(p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static void json(Path p,Object value)throws IOException {Files.write(p,RsemJson.bytes(value));}
    static boolean same(Object a,Object b){if(a instanceof Number x&&b instanceof Number y)return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()))==0;if(a instanceof Map<?,?> x&&b instanceof Map<?,?> y)return x.keySet().equals(y.keySet())&&x.keySet().stream().allMatch(k->same(x.get(k),y.get(k)));if(a instanceof List<?> x&&b instanceof List<?> y){if(x.size()!=y.size())return false;for(int i=0;i<x.size();i++)if(!same(x.get(i),y.get(i)))return false;return true;}return Objects.equals(a,b);}
}

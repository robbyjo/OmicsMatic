/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Typed additive models and explicit expression units; no executable caller formula. */
final class Monocle131ModelRequest {
    private Monocle131ModelRequest() { }
    record Request(Map<String,Object> parameters,Map<String,Object> types,List<Map<String,Object>> analyses,
                   Path expression,Path cells,Path genes,Path out,Path rscript,List<Path> libraries,Path source,
                   String format,String units,boolean execute,long inputBytes,long outputBytes,int entries,int jsonBytes,long operations,int seconds,long addressSpaceBytes,long sampledRssBytes) { }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","mode","version","expression","expression_format","expression_units","expression_provenance","population_declaration","cells","genes","metadata_types","analyses","out","rscript","r_libraries","source_root","execution_policy","maximum_input_bytes","maximum_output_bytes","maximum_entries","maximum_json_bytes","maximum_native_operations","timeout_seconds","maximum_address_space_bytes","maximum_sampled_rss_bytes");
        integer(m.get("schema_version"),1,1);require(text(m.get("mode")).equals("RUN")&&text(m.get("version")).equals("1.3.1"),"Require RUN and original Monocle3 1.3.1; no edition substitution");
        String format=text(m.get("expression_format")),units=text(m.get("expression_units"));require(Set.of("TSV","MATRIX_MARKET").contains(format),"Declare TSV or MATRIX_MARKET");require(Set.of("raw-integer-counts","nonnegative-normalized-expression").contains(units),"Declare raw counts or prepared nonnegative normalized expression; no unit inference");label(m.get("expression_provenance"));label(m.get("population_declaration"));
        var types=object(m.get("metadata_types"));require(types.size()<=128,"Metadata column capacity exceeded");
        for(var e:types.entrySet()){
            column(e.getKey());require(!Set.of("cell_id","f_expression","Size_Factor","cluster","partition","pseudotime").contains(e.getKey()),"Reserved original metadata column: "+e.getKey());var spec=object(e.getValue());allowed(spec,"type","levels");String type=text(spec.get("type"));require(Set.of("numeric","factor").contains(type),"Choose numeric or factor metadata");if(type.equals("factor"))labels(spec.get("levels"),1000000);else require(!spec.containsKey("levels"),"Numeric metadata has no factor levels");
        }
        var analyses=new ArrayList<Map<String,Object>>();var ids=new HashSet<String>();
        for(Object x:list(m.get("analyses"))){
            require(analyses.size()<32,"At most 32 complete model batches");var a=object(x);allowed(a,"id","seed","family","clean_model","intercept","terms","selection");String id=text(a.get("id"));require(id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")&&ids.add(id.toLowerCase(Locale.ROOT)),"Unique portable model IDs required");integer(a.get("seed"),1,2147483646);
            require(Set.of("ORIGINAL_DEFAULT","quasipoisson","poisson","negbinomial","gaussian").contains(text(a.get("family"))),"Choose the original default or a supported single-component family");Object clean=a.get("clean_model");require(clean instanceof Boolean||"ORIGINAL_DEFAULT".equals(clean),"Declare original-default or boolean clean_model");boolean intercept=bool(a.get("intercept"));var terms=labelsOrEmpty(a.get("terms"),64);require(intercept||!terms.isEmpty(),"Empty no-intercept model");for(String term:terms)require(types.containsKey(term),"Undeclared model column: "+term);
            if(a.containsKey("selection")){
                var s=object(a.get("selection"));allowed(s,"term","correlation_column","maximum_q","minimum_pearson");String term=text(s.get("term")),cor=text(s.get("correlation_column"));require(terms.contains(term)&&types.containsKey(term)&&text(object(types.get(term)).get("type")).equals("numeric"),"Selection requires a declared numeric slope term");require(types.containsKey(cor)&&text(object(types.get(cor)).get("type")).equals("numeric"),"Pearson score must be a declared numeric column");double q=finite(s.get("maximum_q")),r=finite(s.get("minimum_pearson"));require(q>0&&q<=1&&r>=-1&&r<=1,"Selection thresholds outside q/r domains");
            }
            analyses.add(Collections.unmodifiableMap(a));
        }
        require(!analyses.isEmpty(),"Require complete model batches");var libraries=new ArrayList<Path>();for(Object x:list(m.get("r_libraries"))){require(libraries.size()<16,"Too many declared R libraries");libraries.add(path(parent,x));}require(!libraries.isEmpty(),"Declare private R libraries");String policy=text(m.get("execution_policy"));require(Set.of("ALLOW","FORBID").contains(policy),"Declare execution policy ALLOW or FORBID");
        var p=new LinkedHashMap<String,Object>();for(String k:List.of("schema_version","version","expression_format","expression_units","expression_provenance","population_declaration","metadata_types","analyses"))p.put(k,m.get(k));
        return new Request(Collections.unmodifiableMap(p),Collections.unmodifiableMap(types),List.copyOf(analyses),path(parent,m.get("expression")),path(parent,m.get("cells")),path(parent,m.get("genes")),path(parent,m.get("out")),path(parent,m.get("rscript")),List.copyOf(libraries),path(parent,m.get("source_root")),format,units,policy.equals("ALLOW"),number(m,"maximum_input_bytes",67108864,1,137438953472L),number(m,"maximum_output_bytes",1073741824,1,137438953472L),(int)number(m,"maximum_entries",10000000,1,1000000000),(int)number(m,"maximum_json_bytes",67108864,1,268435456),number(m,"maximum_native_operations",50000000,1,1000000000000L),(int)number(m,"timeout_seconds",900,1,604800),number(m,"maximum_address_space_bytes",17179869184L,268435456,1099511627776L),number(m,"maximum_sampled_rss_bytes",8589934592L,16777216,1099511627776L));
    }
    static Map<String,Object> object(Object x)throws IOException{var result=new LinkedHashMap<String,Object>();for(var e:NextflowFiles.object(x).entrySet())result.put(text(e.getKey()),e.getValue());return result;}
    static String text(Object x)throws IOException{return NextflowFiles.text(x);}
    static List<?> list(Object x)throws IOException{return NextflowFiles.list(x);}
    static long integer(Object x,long min,long max)throws IOException{return NextflowFiles.integer(x,min,max);}
    static long number(Map<String,Object> m,String key,long fallback,long min,long max)throws IOException{return m.containsKey(key)?integer(m.get(key),min,max):fallback;}
    static boolean bool(Object x)throws IOException{require(x instanceof Boolean,"Require a JSON boolean");return (Boolean)x;}
    static double finite(Object x)throws IOException{String s=x instanceof NextflowParameters.NumberToken n?n.text():x instanceof Number?x.toString():"";try{double v=Double.parseDouble(s);require(Double.isFinite(v),"Require finite JSON number");return v;}catch(NumberFormatException e){throw new IOException("Require numeric JSON value",e);}}
    static List<String> labels(Object x,int max)throws IOException{var out=labelsOrEmpty(x,max);require(!out.isEmpty(),"Empty factor/term list");return out;}
    static List<String> labelsOrEmpty(Object x,int max)throws IOException{var out=new ArrayList<String>();var seen=new HashSet<String>();for(Object v:list(x)){String s=label(v);require(out.size()<max&&seen.add(s),"Duplicate or excessive complete factor/term labels");out.add(s);}return List.copyOf(out);}
    static String label(Object x)throws IOException{String s=text(x);require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Invalid literal label");return s;}
    static String column(String s)throws IOException{require(s.matches("[A-Za-z][A-Za-z0-9_.]*")&&!Set.of("if","else","repeat","while","function","for","in","next","break","TRUE","FALSE","NULL","Inf","NaN","NA").contains(s),"Use literal supported metadata column names, never R expressions");return s;}
    static Path path(Path parent,Object x)throws IOException{Path p=Path.of(label(x));return (p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static void allowed(Map<String,Object> m,String... keys)throws IOException{var valid=Set.of(keys);for(String k:m.keySet())require(valid.contains(k),"Unknown field: "+k);}
    static void require(boolean b,String s)throws IOException{if(!b)throw new IOException(s);}
}

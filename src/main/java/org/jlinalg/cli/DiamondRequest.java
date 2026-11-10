/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit original DIAMOND options and logical capacities. */
final class DiamondRequest {
    private DiamondRequest() { }
    static final List<String> VERSIONS=List.of("2.0.0","2.0.15","2.1.8");
    static final List<String> FIELDS=List.of("qseqid","sseqid","pident","length","mismatch","gapopen","qstart","qend","sstart","send","evalue","bitscore","qlen","slen");
    record Limits(long inputBytes,long decodedBytes,long outputBytes,long wallSeconds,int files,int records,int recordLength,int lineCharacters,long rows) { }
    record Request(Path out,Path profile,String version,String databaseKind,Path database,List<Path> queries,Map<String,Object> search,Limits limits) { }

    static Request parse(Object value,Path base)throws IOException {
        Map<?,?> root=object(value);keys(root,"schema_version","out","runtime_profile","analysis","limits");integer(root.get("schema_version"),1,1);
        Map<?,?> a=object(root.get("analysis"));keys(a,"schema_version","source_label","database","queries","search");integer(a.get("schema_version"),1,1);String version=text(a.get("source_label"));need(VERSIONS.contains(version),"Unknown original DIAMOND source label");
        Map<?,?> d=object(a.get("database"));keys(d,"kind","path");String kind=text(d.get("kind"));need(Set.of("protein-fasta","diamond-db").contains(kind),"Require protein-fasta or diamond-db input");
        var queries=new ArrayList<Path>();for(Object p:list(a.get("queries")))queries.add(path(base,p));need(!queries.isEmpty()&&queries.size()<=2,"Require one or two query files");need(!version.equals("2.0.0")||queries.size()==1,"Original2.0.0 does not provide the later two-query-file profile");
        Map<?,?> s=object(a.get("search"));keys(s,"mode","sensitivity","algorithm","evalue","min_bitscore","max_target_seqs","top_percent","max_hsps","min_identity","query_cover","subject_cover","no_self_hits","matrix","gap_open","gap_extend","comp_based_stats","masking","query_gencode","block_size","index_chunks","threads");
        choice(s,"mode",Set.of("blastp","blastx"));choice(s,"sensitivity",version.equals("2.0.0")?Set.of("default","sensitive","more-sensitive","very-sensitive","ultra-sensitive"):Set.of("default","mid-sensitive","sensitive","more-sensitive","very-sensitive","ultra-sensitive"));choice(s,"algorithm",version.equals("2.0.0")?Set.of("auto","0","1"):Set.of("auto","0","1","ctg"));
        numeric(s.get("evalue"),Double.MIN_VALUE,Double.MAX_VALUE);optionalNumber(s,"min_bitscore",0,1000000000);optionalInteger(s,"max_target_seqs",0,1000000000);optionalNumber(s,"top_percent",0,100);integer(s.get("max_hsps"),0,1000000);for(String field:List.of("min_identity","query_cover","subject_cover"))optionalNumber(s,field,0,100);bool(s.get("no_self_hits"));need(text(s.get("matrix")).matches("[A-Za-z][A-Za-z0-9_+-]{0,49}"),"Require an explicit built-in matrix name");optionalInteger(s,"gap_open",0,1000);optionalInteger(s,"gap_extend",0,1000);integer(s.get("comp_based_stats"),0,version.equals("2.0.0")?1:4);integer(s.get("query_gencode"),1,1);optionalNumber(s,"block_size",0.000000001,128);optionalInteger(s,"index_chunks",1,64);integer(s.get("threads"),0,1024);
        if(s.get("masking")!=null)need((version.equals("2.0.0")?Set.of("0","1"):Set.of("0","1","none","tantan","seg")).contains(text(s.get("masking"))),"Unknown source-version masking setting");
        Map<?,?> l=object(root.get("limits"));keys(l,"input_bytes","decoded_bytes","output_bytes","wall_seconds","max_files","max_records","max_record_length","max_line_characters","max_alignment_rows");
        Limits limits=new Limits(integer(l.get("input_bytes"),1,1099511627776L),integer(l.get("decoded_bytes"),1,1099511627776L),integer(l.get("output_bytes"),1024,4398046511104L),integer(l.get("wall_seconds"),1,604800),(int)integer(l.get("max_files"),100,500000),(int)integer(l.get("max_records"),1,100000000),(int)integer(l.get("max_record_length"),1,Integer.MAX_VALUE),(int)integer(l.get("max_line_characters"),1024,16777216),integer(l.get("max_alignment_rows"),0,500000000));
        var settings=new LinkedHashMap<String,Object>();for(var e:s.entrySet())settings.put(text(e.getKey()),e.getValue());return new Request(path(base,root.get("out")),path(base,root.get("runtime_profile")),version,kind,path(base,d.get("path")),List.copyOf(queries),settings,limits);
    }
    static Path path(Path base,Object value)throws IOException {String raw=text(value);need(!raw.isEmpty()&&raw.chars().noneMatch(c->c<32||c==127),"Empty/control-character DIAMOND path");return base.resolve(raw).toAbsolutePath().normalize();}
    static void need(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
    static boolean bool(Object value)throws IOException {need(value instanceof Boolean,"Expected explicit DIAMOND boolean");return (Boolean)value;}
    static double numeric(Object value,double min,double max)throws IOException {double x;try{x=value instanceof NextflowParameters.NumberToken n?Double.parseDouble(n.text()):value instanceof Number n?n.doubleValue():Double.NaN;}catch(NumberFormatException error){throw new IOException("Invalid DIAMOND number",error);}need(Double.isFinite(x)&&x>=min&&x<=max,"DIAMOND number outside its declared range");return x;}
    private static void optionalNumber(Map<?,?> s,String key,double min,double max)throws IOException {if(s.get(key)!=null)numeric(s.get(key),min,max);}
    private static void optionalInteger(Map<?,?> s,String key,long min,long max)throws IOException {if(s.get(key)!=null)integer(s.get(key),min,max);}
    private static void choice(Map<?,?> s,String key,Set<String> choices)throws IOException {need(choices.contains(text(s.get(key))),"Unknown/unavailable DIAMOND "+key+" setting for this source version");}
    static List<String> searchArguments(Request r)throws IOException {
        var args=new ArrayList<String>();Map<String,Object> s=r.search();
        for(String[] pair:new String[][]{{"threads","threads"},{"evalue","evalue"},{"min_bitscore","min-score"},{"max_target_seqs","max-target-seqs"},{"top_percent","top"},{"max_hsps","max-hsps"},{"min_identity","id"},{"query_cover","query-cover"},{"subject_cover","subject-cover"},{"matrix","matrix"},{"gap_open","gapopen"},{"gap_extend","gapextend"},{"comp_based_stats","comp-based-stats"},{"masking","masking"},{"query_gencode","query-gencode"},{"block_size","block-size"},{"index_chunks","index-chunks"}})if(s.get(pair[0])!=null){args.add("--"+pair[1]);Object v=s.get(pair[0]);args.add(v instanceof NextflowParameters.NumberToken n?n.text():v.toString());}
        String sensitivity=text(s.get("sensitivity")),algorithm=text(s.get("algorithm"));if(!sensitivity.equals("default"))args.add("--"+sensitivity);if(!algorithm.equals("auto")){args.add("--algo");args.add(algorithm);}if(bool(s.get("no_self_hits")))args.add("--no-self-hits");return args;
    }
}

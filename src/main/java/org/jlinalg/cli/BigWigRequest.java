/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.genetics.BigWigWriter;

/** Complete typed native conversion settings; captured producer paths are opaque text. */
final class BigWigRequest {
    private BigWigRequest() { }
    static final String PROFILE="native-java-bigwig-v4-raw-1";
    record Spec(String input,String sizes,String build,String units,String format,String precision,String dictionary,String compression,
                int records,int lineChars,long inputBytes,long decodedBytes,long outputBytes,long workspaceBytes,int section,int fanout) {
        BigWigWriter.Options options(){return new BigWigWriter.Options(compression.equals("zlib"),section,fanout,outputBytes,precision.equals("exact"),dictionary.equals("all"));}
        String member(){return format.equals("gzip")?"inputs/signal.bedGraph.gz":"inputs/signal.bedGraph";}
        long ledgerBytes(){return Math.min(workspaceBytes,Math.addExact(decodedBytes,256L*(records+100000L)));}
        Map<String,Object> document(){var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("profile",PROFILE);value.put("producer_input",input);value.put("producer_sizes",sizes);value.put("genome_build",build);value.put("signal_units",units);value.put("input_format",format);value.put("float32",precision);value.put("dictionary",dictionary);value.put("compression",compression);value.put("maximum_records",records);value.put("maximum_line_characters",lineChars);value.put("maximum_input_bytes",inputBytes);value.put("maximum_decoded_bytes",decodedBytes);value.put("maximum_output_bytes",outputBytes);value.put("maximum_workspace_bytes",workspaceBytes);value.put("items_per_section",section);value.put("tree_fanout",fanout);return value;}
    }
    static Spec cli(Map<String,String> values)throws IOException {
        return checked(new Spec(required(values,"--input"),required(values,"--sizes"),required(values,"--genome-build"),required(values,"--units"),values.getOrDefault("--input-format","plain"),required(values,"--float32"),required(values,"--dictionary"),required(values,"--compression"),
            (int)number(values,"--records",1000000,1,10000000),(int)number(values,"--line-chars",65536,32,1048576),number(values,"--input-bytes",268435456L,1,1073741824L),number(values,"--decoded-bytes",268435456L,1,1073741824L),number(values,"--output-bytes",1073741824L,256,1099511627776L),number(values,"--workspace-bytes",4294967296L,1,1099511627776L),(int)number(values,"--items-per-section",1024,1,65535),(int)number(values,"--tree-fanout",256,2,1024)));
    }
    static Spec captured(Object raw)throws IOException {
        var m=NextflowFiles.object(raw);NextflowFiles.keys(m,"schema_version","profile","producer_input","producer_sizes","genome_build","signal_units","input_format","float32","dictionary","compression","maximum_records","maximum_line_characters","maximum_input_bytes","maximum_decoded_bytes","maximum_output_bytes","maximum_workspace_bytes","items_per_section","tree_fanout");NextflowFiles.integer(m.get("schema_version"),1,1);require(PROFILE.equals(m.get("profile")),"Unsupported captured native bigWig profile");
        return checked(new Spec(text(m,"producer_input"),text(m,"producer_sizes"),text(m,"genome_build"),text(m,"signal_units"),text(m,"input_format"),text(m,"float32"),text(m,"dictionary"),text(m,"compression"),(int)integer(m,"maximum_records",1,10000000),(int)integer(m,"maximum_line_characters",32,1048576),integer(m,"maximum_input_bytes",1,1073741824L),integer(m,"maximum_decoded_bytes",1,1073741824L),integer(m,"maximum_output_bytes",256,1099511627776L),integer(m,"maximum_workspace_bytes",1,1099511627776L),(int)integer(m,"items_per_section",1,65535),(int)integer(m,"tree_fanout",2,1024)));
    }
    private static Spec checked(Spec s)throws IOException {for(String path:new String[]{s.input,s.sizes})require(path.length()<=32768&&path.chars().noneMatch(c->c<32||c==127)&&(path.startsWith("/")||path.matches("[A-Za-z]:[\\\\/].*")),"Original input/reference paths must be absolute literal text");for(String value:new String[]{s.build,s.units})require(!value.isBlank()&&value.length()<=256&&value.chars().noneMatch(c->c<32||c==127),"Declare nonempty build and units without control characters");require(java.util.Set.of("plain","gzip").contains(s.format)&&java.util.Set.of("exact","round").contains(s.precision)&&java.util.Set.of("used","all").contains(s.dictionary)&&java.util.Set.of("zlib","none").contains(s.compression),"Declare input-format plain|gzip, float32 exact|round, dictionary used|all and compression zlib|none");require(s.outputBytes+BigWigFiles.FINAL_RESERVE<=s.workspaceBytes,"Workspace must cover the requested maximum output and16MiB final metadata reserve, plus prepared evidence; review capacities");return s;}
    private static String required(Map<String,String> m,String key)throws IOException {String value=m.get(key);require(value!=null,"Missing required native bigWig option: "+key);return value;}
    private static long number(Map<String,String> m,String key,long fallback,long minimum,long maximum)throws IOException {String value=m.get(key);if(value==null)return fallback;require(value.matches("[0-9]+"),"Expected positive integer capacity: "+key);try{long n=Long.parseLong(value);require(n>=minimum&&n<=maximum,"Native bigWig capacity outside supported range: "+key);return n;}catch(NumberFormatException failure){throw new IOException("Capacity overflow: "+key,failure);}}
    private static long integer(Map<?,?> m,String key,long minimum,long maximum)throws IOException{return NextflowFiles.integer(m.get(key),minimum,maximum);}
    private static String text(Map<?,?> m,String key)throws IOException{return NextflowFiles.text(m.get(key));}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

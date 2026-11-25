/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.proteomics.MaxLfq;
import static org.jlinalg.cli.NextflowFiles.*;

/** Prepared, already normalized log2 ion quantities; no upstream acquisition policy is inferred. */
final class IqRequest {
    private IqRequest() { }
    record Capacity(long rows, int proteins, int samples, int ions, long cells, long pairs,
                    long numericBytes, long work, int iterations, long inputBytes,
                    long decodedBytes, long outputBytes, long wallMillis) {
        MaxLfq.Limits limits() { return new MaxLfq.Limits(samples, ions, cells, pairs, numericBytes, work, iterations); }
    }
    record Spec(Map<String,Object> value, String file, boolean gzip, String separator,
                Map<String,String> columns, String preparation, Capacity capacity) { }
    static Spec read(Path file, boolean source) throws IOException {
        return parse(SimpleJson.parse(utf8(bytes(file, 1048576)).replaceFirst("^\\uFEFF", "")), source);
    }
    static Spec parse(Object raw, boolean source) throws IOException {
        var map = object(raw); keys(map,"schema_version","engine","edition","mode","units","preparation","input","capacity","process");
        integer(map.get("schema_version"),1,1);
        require("native".equals(map.get("engine")) && "iq-2.3.0".equals(map.get("edition")) && "fast-maxlfq".equals(map.get("mode")), "Require the qualified native iq2.3.0 fast-maxlfq profile; no edition/method substitution");
        require("already-normalized-log2".equals(map.get("units")) && map.get("process") == null, "Declare already-normalized-log2 quantities and process:null; no analysis-time installation or normalization is inferred");
        String preparation = text(map.get("preparation")); require(!preparation.isBlank() && preparation.length() <= 8192 && preparation.chars().noneMatch(c -> c < 32 || c == 127), "Declare the actual input preparation without controls; this is provenance, not a validated pipeline");
        var input = object(map.get("input")); keys(input,"file","compression","separator","columns");
        String file = text(input.get("file")), separator = text(input.get("separator")), compression = text(input.get("compression"));
        require(!file.isBlank() && file.length() <= 32768 && file.chars().noneMatch(c -> c < 32 || c == 127), "Require an explicit bounded ion input path");
        if (source) { Path path=Path.of(file); require(path.isAbsolute() && path.normalize().equals(path), "Require canonical absolute ion input path"); }
        require(Set.of("none","gzip").contains(compression) && Set.of("tab","comma","whitespace").contains(separator), "Declare supported compression and separator explicitly");
        var names = object(input.get("columns")); keys(names,"protein","sample","ion","quant"); var columns = new LinkedHashMap<String,String>();
        for (String role : List.of("protein","sample","ion","quant")) { String name = text(names.get(role)); require(!name.isBlank() && name.length() <= 1024 && name.chars().noneMatch(c -> c < 32 || c == 127), "Require literal bounded iq column names without controls"); columns.put(role,name); }
        require(new HashSet<>(columns.values()).size()==4,"Distinct role columns required; no duplicated or guessed column mapping");
        var cap=object(map.get("capacity")); keys(cap,"rows","proteins","samples","ions_per_protein","cells_per_protein","pairs_per_protein","numeric_bytes","work","iterations","input_bytes","decoded_bytes","output_bytes","wall_millis");
        var c=new Capacity(number(cap,"rows",Integer.MAX_VALUE-1),asInt(cap,"proteins"),asInt(cap,"samples"),asInt(cap,"ions_per_protein"),number(cap,"cells_per_protein",Integer.MAX_VALUE-1),number(cap,"pairs_per_protein",Long.MAX_VALUE/64),number(cap,"numeric_bytes",Long.MAX_VALUE/64),number(cap,"work",Long.MAX_VALUE/64),asInt(cap,"iterations"),number(cap,"input_bytes",Long.MAX_VALUE/64),number(cap,"decoded_bytes",Long.MAX_VALUE/64),number(cap,"output_bytes",Long.MAX_VALUE/64),number(cap,"wall_millis",604800000));
        c.limits(); var copy=new LinkedHashMap<String,Object>(); map.forEach((k,v)->copy.put(k.toString(),v));
        return new Spec(Collections.unmodifiableMap(copy),file,compression.equals("gzip"),separator,Collections.unmodifiableMap(columns),preparation,c);
    }
    private static long number(Map<?,?> map,String name,long max)throws IOException{return integer(map.get(name),1,max);}
    private static int asInt(Map<?,?> map,String name)throws IOException{return Math.toIntExact(number(map,name,Integer.MAX_VALUE-1));}
    static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}

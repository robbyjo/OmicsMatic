/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

final class AllcoolsRegionRequest {
    private AllcoolsRegionRequest() { }
    record Capacity(long inputBytes,long outputBytes,long wallMillis) { }
    record Spec(Map<String,Object> value,String counts,String preparation,int cells,int types,int features,
                double hypoCutoff,double hyperCutoff,Capacity capacity) { }
    static Spec read(Path path,boolean original)throws IOException {
        return parse(SimpleJson.parse(utf8(bytes(path,1048576)).replaceFirst("^\\uFEFF","")),original);
    }
    static Spec parse(Object raw,boolean original)throws IOException {
        var doc=object(raw);keys(doc,"schema_version","engine","mode","source_behavior","preparation","input","dimensions","settings","capacity","process");
        integer(doc.get("schema_version"),1,1);
        require("native".equals(doc.get("engine"))&&"prepared-region-quantification".equals(doc.get("mode"))
            &&"ALLCools-1.0.8-uint32-binary16".equals(doc.get("source_behavior"))&&doc.get("process")==null,"Explicit native ALLCools region quantification profile and process:null required");
        String preparation=AllcoolsPosteriorRequest.scalar(doc.get("preparation"),8192);
        var input=object(doc.get("input"));keys(input,"counts","format");require("literal-utf8-tsv".equals(input.get("format")),"Declare literal-utf8-tsv");
        String counts=AllcoolsPosteriorRequest.scalar(input.get("counts"),32768);
        if(original){Path p=Path.of(counts);require(p.isAbsolute()&&p.normalize().equals(p),"Canonical absolute count path required");}
        var dims=object(doc.get("dimensions"));keys(dims,"cells","mc_types","features");
        int cells=Math.toIntExact(integer(dims.get("cells"),1,Integer.MAX_VALUE-8)),types=Math.toIntExact(integer(dims.get("mc_types"),1,Integer.MAX_VALUE-8)),features=Math.toIntExact(integer(dims.get("features"),1,Integer.MAX_VALUE-8));
        try{Math.multiplyExact(Math.multiplyExact((long)cells,types),features);}catch(ArithmeticException e){throw new IOException("Complete row population overflow",e);}
        var s=object(doc.get("settings"));keys(s,"hypo_cutoff","hyper_cutoff","survival","probability_epsilon","count_output","score_output","count_domain");
        require("P(X>mc)".equals(s.get("survival"))&&finite(s.get("probability_epsilon"))==0.000001&&"saturate-uint32".equals(s.get("count_output"))&&"numpy-1.23.5-binary16".equals(s.get("score_output"))&&"raw-nonnegative-int64-no-sum-overflow".equals(s.get("count_domain")),"Declare the source strict survival, 1e-6 pool epsilon, uint32 saturation, binary16 score behavior and nonoverflowing raw int64 domain");
        double low=finite(s.get("hypo_cutoff")),high=finite(s.get("hyper_cutoff"));require(low>=0&&low<=1&&high>=0&&high<=1,"Score cutoffs must lie in [0,1]");
        var c=object(doc.get("capacity"));keys(c,"input_bytes","output_bytes","wall_millis");
        var cap=new Capacity(integer(c.get("input_bytes"),1,Long.MAX_VALUE/64),integer(c.get("output_bytes"),1,Long.MAX_VALUE/64),integer(c.get("wall_millis"),1,604800000));
        var copy=new LinkedHashMap<String,Object>();doc.forEach((k,v)->copy.put(k.toString(),v));
        return new Spec(Collections.unmodifiableMap(copy),counts,preparation,cells,types,features,low,high,cap);
    }
    private static double finite(Object value)throws IOException {require(value instanceof Number,"Explicit finite JSON number required");double x=((Number)value).doubleValue();require(Double.isFinite(x),"Nonfinite setting refused");return x;}
}

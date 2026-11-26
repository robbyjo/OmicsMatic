/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.omics.methylation.AllcoolsPosterior;
import static org.jlinalg.cli.NextflowFiles.*;

final class AllcoolsPosteriorRequest {
    private AllcoolsPosteriorRequest() {}
    record Capacity(long inputBytes,long outputBytes,long wallMillis) {}
    record Spec(Map<String,Object> value,String counts,String preparation,int cells,int types,int features,
                AllcoolsPosterior.Settings settings,Capacity capacity) {}
    static Spec read(Path path,boolean original)throws IOException {
        return parse(SimpleJson.parse(utf8(bytes(path,1048576)).replaceFirst("^\\uFEFF","")),original);
    }
    static Spec parse(Object raw,boolean original)throws IOException {
        var doc=object(raw);keys(doc,"schema_version","engine","mode","source_behavior","preparation","input","dimensions","settings","capacity","process");
        integer(doc.get("schema_version"),1,1);
        require("native".equals(doc.get("engine"))&&"prepared-methylation-posterior".equals(doc.get("mode"))
            &&"ALLCools-1.0.8-float64".equals(doc.get("source_behavior"))&&doc.get("process")==null,"Explicit native ALLCools-1.0.8-float64 profile and process:null required");
        String preparation=scalar(doc.get("preparation"),8192);
        var input=object(doc.get("input"));keys(input,"counts","format");require("literal-utf8-tsv".equals(input.get("format")),"Declare literal-utf8-tsv");
        String counts=scalar(input.get("counts"),32768);
        if(original){Path p=Path.of(counts);require(p.isAbsolute()&&p.normalize().equals(p),"Canonical absolute count path required");}
        var dims=object(doc.get("dimensions"));keys(dims,"cells","mc_types","features");
        int cells=Math.toIntExact(integer(dims.get("cells"),1,Integer.MAX_VALUE-8)),types=Math.toIntExact(integer(dims.get("mc_types"),1,Integer.MAX_VALUE-8)),features=Math.toIntExact(integer(dims.get("features"),1,Integer.MAX_VALUE-8));
        try{Math.multiplyExact(Math.multiplyExact((long)cells,types),features);}catch(ArithmeticException e){throw new IOException("Declared complete row population overflow",e);}
        var s=object(doc.get("settings"));keys(s,"representation","operation","normalize_per_cell","clip_norm_value","m_value_alpha","variance_ddof","reduction","count_domain");
        require(s.get("normalize_per_cell")instanceof Boolean&&integer(s.get("variance_ddof"),0,0)==0&&"contiguous-feature-axis-float64-nanmean-nanvar".equals(s.get("reduction"))&&"uint32-mc-le-cov".equals(s.get("count_domain")),"Explicit source normalization, ddof0, reduction and count domain required");
        var repr=switch(text(s.get("representation"))){case "numpy"->AllcoolsPosterior.Representation.NUMPY;case "xarray"->AllcoolsPosterior.Representation.XARRAY;default->throw new IOException("Representation must be numpy or xarray");};
        var operation=switch(text(s.get("operation"))){case "posterior-fraction"->AllcoolsPosterior.Operation.POSTERIOR_FRACTION;case "m-value"->AllcoolsPosterior.Operation.M_VALUE;default->throw new IOException("Operation must be posterior-fraction or m-value");};
        var settings=new AllcoolsPosterior.Settings(repr,operation,(Boolean)s.get("normalize_per_cell"),s.get("clip_norm_value")==null?null:finite(s.get("clip_norm_value")),finite(s.get("m_value_alpha")));
        var c=object(doc.get("capacity"));keys(c,"input_bytes","output_bytes","wall_millis");
        var cap=new Capacity(integer(c.get("input_bytes"),1,Long.MAX_VALUE/64),integer(c.get("output_bytes"),1,Long.MAX_VALUE/64),integer(c.get("wall_millis"),1,604800000));
        var copy=new LinkedHashMap<String,Object>();doc.forEach((k,v)->copy.put(k.toString(),v));return new Spec(Collections.unmodifiableMap(copy),counts,preparation,cells,types,features,settings,cap);
    }
    private static double finite(Object v)throws IOException {require(v instanceof Number,"Explicit finite JSON number required");double d=((Number)v).doubleValue();require(Double.isFinite(d),"Nonfinite JSON number refused");return d;}
    static String scalar(Object v,int maximum)throws IOException {String s=text(v);require(!s.isBlank()&&s.length()<=maximum&&s.chars().noneMatch(c->c<32||c==127),"Bounded nonblank literal without controls required");return s;}
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

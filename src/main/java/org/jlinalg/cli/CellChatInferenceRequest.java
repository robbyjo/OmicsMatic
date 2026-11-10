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

/** Explicit prepared-expression and original-package settings; no user R code. */
final class CellChatInferenceRequest {
    private CellChatInferenceRequest() { }
    record Limits(long inputBytes,long outputBytes,long wallSeconds,int genes,int cells,int files,long runtimeBytes,long projectionWorkingBytes,long projectionUpdates) { }
    record Request(Path out,Path runtimeProfile,Path expression,Path metadata,
            Map<String,Object> analysis,Limits limits) { }

    static Request parse(Object value,Path base) throws IOException {
        Map<?,?> root=object(value);keys(root,"schema_version","out","runtime_profile","analysis","limits");
        require(integer(root.get("schema_version"),1,1)==1,"Unsupported schema");
        Map<?,?> limit=object(root.get("limits"));keys(limit,"input_bytes","output_bytes","wall_seconds","max_genes","max_cells","max_files","runtime_bytes","projection_working_bytes","projection_arithmetic_updates");
        Limits limits=new Limits(integer(limit.get("input_bytes"),1,1073741824L),integer(limit.get("output_bytes"),1024,4294967296L),
            integer(limit.get("wall_seconds"),1,86400), (int)integer(limit.get("max_genes"),3,1000000),(int)integer(limit.get("max_cells"),4,1000000),
            (int)integer(limit.get("max_files"),100,500000),integer(limit.get("runtime_bytes"),1,1099511627776L),integer(limit.get("projection_working_bytes"),1,1099511627776L),integer(limit.get("projection_arithmetic_updates"),1,1000000000000L));
        Map<?,?> a=object(root.get("analysis"));keys(a,"schema_version","source_label","expression_units","mode","expression","metadata","group_order","sample_order","condition_order","species","database_categories","prefilter_min_group_cells","prefilter_scope","overexpression","projection","inference","filter","network");
        integer(a.get("schema_version"),1,1);
        require(Set.of("1.0.0","2.1.2").contains(text(a.get("source_label"))),"Unsupported explicit original source edition");equal(a,"expression_units","external-log-normalized-nonnegative");equal(a,"mode","condition-inference");
        require(Set.of("human","mouse").contains(text(a.get("species"))),"Unsupported explicit species");
        List<String> groups=names(a.get("group_order"),2,10000);names(a.get("condition_order"),1,32);names(a.get("sample_order"),1,10000);
        List<String> categories=names(a.get("database_categories"),1,4);require(Set.of("Secreted Signaling","ECM-Receptor","Cell-Cell Contact","Non-protein Signaling").containsAll(categories),"Unknown legacy database category");
        if(text(a.get("source_label")).equals("1.0.0"))require(categories.size()==1&&!categories.contains("Non-protein Signaling")||Set.copyOf(categories).equals(Set.of("Secreted Signaling","ECM-Receptor","Cell-Cell Contact")),"Original1.0.0 accepts one category or the complete original database; partial multi-category selection is refused because original subsetDB recycles a vector");
        integer(a.get("prefilter_min_group_cells"),0,1000000);require(Set.of("each-condition","any-condition").contains(text(a.get("prefilter_scope"))),"Explicit prefilter scope required");
        Map<?,?> over=object(a.get("overexpression"));keys(over,"only_positive","thresh_pc","thresh_fc","thresh_p");bool(over.get("only_positive"));number(over.get("thresh_pc"),0,1,false);number(over.get("thresh_fc"),0,100,false);number(over.get("thresh_p"),0,1,true);
        if(a.get("projection")!=null) {Map<?,?> projection=object(a.get("projection"));keys(projection,"species","alpha","normalization");require(Set.of("human","mouse").contains(text(projection.get("species"))),"Unsupported original PPI species");require(Set.of("rows","columns").contains(text(projection.get("normalization"))),"Unknown PPI normalization");double alpha=number(projection.get("alpha"),0,1,true);require(alpha<1,"Original projection requires alpha < 1");}
        Map<?,?> infer=object(a.get("inference"));keys(infer,"type","trim","raw_use","population_size","nboot","seed","Kh","n");String type=text(infer.get("type"));require(Set.of("triMean","truncatedMean","thresholdedMean","median").contains(type),"Unsupported original averaging method");require(!type.equals("thresholdedMean")||text(a.get("source_label")).equals("2.1.2"),"Original1.0.0 has no thresholdedMean");if(infer.get("trim")==null)require(type.equals("triMean")||type.equals("median"),"Selected method requires numeric trim");else number(infer.get("trim"),0,type.equals("thresholdedMean")?1:0.5,false);boolean raw=bool(infer.get("raw_use"));require(raw||a.get("projection")!=null,"raw_use=false requires explicit original PPI projection");bool(infer.get("population_size"));integer(infer.get("nboot"),2,10000);integer(infer.get("seed"),0,Integer.MAX_VALUE);number(infer.get("Kh"),0,1000,true);number(infer.get("n"),0,100,true);
        if(a.get("filter")!=null){Map<?,?> f=object(a.get("filter"));keys(f,"min_cells","min_samples","rare_keep");integer(f.get("min_cells"),0,1000000);if(f.get("min_samples")!=null)integer(f.get("min_samples"),1,10000);bool(f.get("rare_keep"));if(text(a.get("source_label")).equals("1.0.0"))require(f.get("min_samples")==null&&!bool(f.get("rare_keep")),"Original1.0.0 has no sample/rare filtering parameters");}
        Map<?,?> network=object(a.get("network"));keys(network,"pvalue_threshold");number(network.get("pvalue_threshold"),0,1,true);
        var analysis=new LinkedHashMap<String,Object>();for(var entry:a.entrySet())analysis.put(text(entry.getKey()),entry.getValue());
        return new Request(path(base,root.get("out")),path(base,root.get("runtime_profile")),path(base,a.get("expression")),path(base,a.get("metadata")),analysis,limits);
    }
    static List<String> names(Object value,int minimum,int maximum)throws IOException {
        List<?> rows=list(value);require(rows.size()>=minimum&&rows.size()<=maximum,"Identifier population outside declared bounds");
        var names=new ArrayList<String>();for(Object raw:rows){String name=text(raw);identifier(name);require(!names.contains(name),"Duplicate identifier: "+name);names.add(name);}return names;
    }
    static void identifier(String name)throws IOException {
        require(!name.isEmpty()&&name.length()<=10000&&name.equals(name.strip())&&name.chars().noneMatch(c->Character.isISOControl(c)),"Invalid literal identifier");
    }
    static boolean bool(Object value)throws IOException {require(value instanceof Boolean,"Expected literal boolean");return (Boolean)value;}
    static double number(Object value,double minimum,double maximum,boolean strictMinimum)throws IOException {
        String raw=value instanceof NextflowParameters.NumberToken n?n.text():value instanceof Number?ntext((Number)value):null;
        require(raw!=null,"Expected JSON number");double number;
        try{number=Double.parseDouble(raw);}catch(NumberFormatException bad){throw new IOException("Invalid scientific parameter",bad);}
        require(Double.isFinite(number)&&(strictMinimum?number>minimum:number>=minimum)&&number<=maximum,"Scientific parameter outside explicit bounds");return number;
    }
    private static String ntext(Number number){return number.toString();}
    static Path path(Path base,Object value)throws IOException {
        try{return base.resolve(text(value)).toAbsolutePath().normalize();}catch(java.nio.file.InvalidPathException bad){throw new IOException("Invalid explicit path",bad);}
    }
    static void equal(Map<?,?> value,String field,String expected)throws IOException {require(text(value.get(field)).equals(expected),"Unsupported "+field);}
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

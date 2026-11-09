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
final class CellChatWorkflowRequest {
    private CellChatWorkflowRequest() { }
    record Limits(long inputBytes,long outputBytes,long wallSeconds,int genes,int cells,int files,long runtimeBytes) { }
    record Request(Path out,Path runtimeProfile,Path expression,Path metadata,
            Map<String,Object> analysis,Limits limits) { }

    static Request parse(Object value,Path base) throws IOException {
        Map<?,?> root=object(value);keys(root,"schema_version","out","runtime_profile","analysis","limits");
        require(integer(root.get("schema_version"),1,1)==1,"Unsupported schema");
        Map<?,?> limit=object(root.get("limits"));keys(limit,"input_bytes","output_bytes","wall_seconds","max_genes","max_cells","max_files","runtime_bytes");
        Limits limits=new Limits(integer(limit.get("input_bytes"),1,1073741824L),integer(limit.get("output_bytes"),1024,4294967296L),
            integer(limit.get("wall_seconds"),1,86400), (int)integer(limit.get("max_genes"),3,1000000),(int)integer(limit.get("max_cells"),4,1000000),
            (int)integer(limit.get("max_files"),100,500000),integer(limit.get("runtime_bytes"),1,1099511627776L));
        Map<?,?> a=object(root.get("analysis"));keys(a,"schema_version","source_label","expression_units","mode","expression","metadata","group_order","sample_order","condition_order","species","database_categories","prefilter_min_group_cells","overexpression","inference","condition_filter","sample_filter","network","comparison");
        integer(a.get("schema_version"),1,1);
        equal(a,"source_label","2.2.0-2025-release");equal(a,"expression_units","external-log-normalized-nonnegative");equal(a,"mode","condition-and-sample");
        require(Set.of("human","mouse","zebrafish").contains(text(a.get("species"))),"Unsupported explicit species");
        names(a.get("group_order"),2,10000);names(a.get("sample_order"),4,256);names(a.get("condition_order"),2,2);
        List<String> categories=names(a.get("database_categories"),1,4);
        require(Set.of("Secreted Signaling","ECM-Receptor","Cell-Cell Contact","Non-protein Signaling").containsAll(categories),"Unknown database category");
        integer(a.get("prefilter_min_group_cells"),0,1000000);
        Map<?,?> over=object(a.get("overexpression"));keys(over,"only_positive","thresh_pc","thresh_fc","thresh_p","do_fast","variable_both");
        bool(over.get("only_positive"));bool(over.get("do_fast"));bool(over.get("variable_both"));
        number(over.get("thresh_pc"),0,1,false);number(over.get("thresh_fc"),0,100,false);number(over.get("thresh_p"),0,1,true);
        Map<?,?> infer=object(a.get("inference"));keys(infer,"type","trim","population_size","nboot","seed","Kh","n");
        String type=text(infer.get("type"));require(Set.of("triMean","truncatedMean","thresholdedMean","median").contains(type),"Unsupported source averaging method");
        number(infer.get("trim"),0,type.equals("thresholdedMean")?1:0.5,false);bool(infer.get("population_size"));integer(infer.get("nboot"),2,10000);integer(infer.get("seed"),0,Integer.MAX_VALUE);
        number(infer.get("Kh"),0,1000,true);number(infer.get("n"),0,100,true);
        for(String field:List.of("condition_filter","sample_filter")) {
            Map<?,?> filter=object(a.get(field));keys(filter,"min_cells","min_samples","rare_keep");
            integer(filter.get("min_cells"),0,1000000);integer(filter.get("min_samples"),1,256);bool(filter.get("rare_keep"));
        }
        require(integer(object(a.get("sample_filter")).get("min_samples"),1,1)==1,"Per-sample filtering must explicitly use min_samples1");
        Map<?,?> network=object(a.get("network"));keys(network,"pvalue_threshold");number(network.get("pvalue_threshold"),0,1,true);
        Map<?,?> compare=object(a.get("comparison"));keys(compare,"alternative","exact","continuity_correction","strength_difference_threshold");
        require(Set.of("two.sided","less","greater").contains(text(compare.get("alternative"))),"Unsupported Wilcoxon alternative");
        bool(compare.get("exact"));bool(compare.get("continuity_correction"));number(compare.get("strength_difference_threshold"),0,1000000,false);
        Map<String,Object> analysis=new LinkedHashMap<>();for(var e:a.entrySet())analysis.put(text(e.getKey()),e.getValue());
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

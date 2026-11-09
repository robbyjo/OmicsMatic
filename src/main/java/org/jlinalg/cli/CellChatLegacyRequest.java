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
final class CellChatLegacyRequest {
    private CellChatLegacyRequest() { }
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
        Map<?,?> a=object(root.get("analysis"));keys(a,"schema_version","source_label","expression_units","mode","expression","metadata","group_order","condition_order","species","database_categories","prefilter_min_group_cells","prefilter_scope","overexpression","projection","inference","min_cells","network","similarity","rank_net","differential","bubble_sources","bubble_targets");
        integer(a.get("schema_version"),1,1);
        equal(a,"source_label","1.1.3");equal(a,"expression_units","external-log-normalized-nonnegative");equal(a,"mode","condition-comparison");
        require(Set.of("human","mouse","zebrafish").contains(text(a.get("species"))),"Unsupported explicit species");
        List<String> groups=names(a.get("group_order"),2,10000);names(a.get("condition_order"),2,2);
        List<String> categories=names(a.get("database_categories"),1,3);require(Set.of("Secreted Signaling","ECM-Receptor","Cell-Cell Contact").containsAll(categories),"Unknown legacy database category");
        integer(a.get("prefilter_min_group_cells"),0,1000000);integer(a.get("min_cells"),0,1000000);require(Set.of("each-condition","any-condition").contains(text(a.get("prefilter_scope"))),"Explicit prefilter scope required");
        Map<?,?> over=object(a.get("overexpression"));keys(over,"only_positive","thresh_pc","thresh_fc","thresh_p");bool(over.get("only_positive"));number(over.get("thresh_pc"),0,1,false);number(over.get("thresh_fc"),0,100,false);number(over.get("thresh_p"),0,1,true);
        Map<?,?> projection=object(a.get("projection"));keys(projection,"species","alpha","normalization");require(Set.of("human","mouse").contains(text(projection.get("species"))),"Unsupported original PPI species");require(Set.of("rows","columns").contains(text(projection.get("normalization"))),"Unknown PPI normalization");double alpha=number(projection.get("alpha"),0,1,true);require(alpha<1,"Original projectData requires alpha < 1");
        Map<?,?> infer=object(a.get("inference"));keys(infer,"type","trim","raw_use","population_size","do_fast","nboot","seed","Kh","n");require(Set.of("triMean","truncatedMean","median").contains(text(infer.get("type"))),"Unsupported original averaging method");if(infer.get("trim")==null)require(!text(infer.get("type")).equals("truncatedMean"),"Truncated mean needs an explicit numeric trim");else number(infer.get("trim"),0,0.5,false);bool(infer.get("raw_use"));bool(infer.get("population_size"));bool(infer.get("do_fast"));integer(infer.get("nboot"),2,10000);integer(infer.get("seed"),0,Integer.MAX_VALUE);number(infer.get("Kh"),0,1000,true);number(infer.get("n"),0,100,true);
        Map<?,?> network=object(a.get("network"));keys(network,"pvalue_threshold");number(network.get("pvalue_threshold"),0,1,true);
        Map<?,?> similarity=object(a.get("similarity"));keys(similarity,"k","trim_fraction");if(similarity.get("k")!=null)integer(similarity.get("k"),2,100000);if(similarity.get("trim_fraction")!=null)number(similarity.get("trim_fraction"),0,0.25,false);
        Map<?,?> rank=object(a.get("rank_net"));keys(rank,"stacked","do_stat","relative_tolerance","cutoff_pvalue","flip");bool(rank.get("stacked"));bool(rank.get("do_stat"));bool(rank.get("flip"));number(rank.get("relative_tolerance"),0,1,false);number(rank.get("cutoff_pvalue"),0,1,true);
        Map<?,?> diff=object(a.get("differential"));keys(diff,"thresh_pc","thresh_fc","thresh_p","up_ligand_logfc","down_ligand_logfc","down_receptor_logfc");number(diff.get("thresh_pc"),0,1,false);number(diff.get("thresh_fc"),0,100,false);number(diff.get("thresh_p"),0,1,true);number(diff.get("up_ligand_logfc"),0,100,false);number(diff.get("down_ligand_logfc"),-100,0,false);number(diff.get("down_receptor_logfc"),-100,0,false);
        require(groups.containsAll(names(a.get("bubble_sources"),1,groups.size())) && groups.containsAll(names(a.get("bubble_targets"),1,groups.size())),"Bubble group selection lies outside explicit group population");
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

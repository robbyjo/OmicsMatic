/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.genetics.BioMartQueries;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Test transport for the real native API; it does not contact an annotation service. */
public final class BioMartQueryMatrix {
    private BioMartQueryMatrix() { }
    public static void main(String[] args)throws Exception {
        if(!BioMartQueries.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong native candidate JAR");
        var input=list(NextflowParameters.parse(Files.readString(Path.of(args[1]),StandardCharsets.UTF_8),16777216,2000000));var result=new ArrayList<Map<String,Object>>();
        for(String version:List.of("2.60.1","2.68.0"))for(Object raw:input){
            var c=object(raw);var row=new LinkedHashMap<String,Object>();row.put("version",version);row.put("id",text(c.get("id")));
            try{
                var filters=new ArrayList<BioMartQueries.Filter>();
                for(Object rawFilter:list(c.get("filters"))){var f=object(rawFilter);var values=new ArrayList<String>();for(Object value:list(f.get("values")))values.add(text(value));filters.add(new BioMartQueries.Filter(text(f.get("name")),BioMartQueries.ValueType.valueOf(text(f.get("type"))),values));}
                var attributes=new ArrayList<String>();for(Object value:c.containsKey("attributes")?list(c.get("attributes")):List.of("gene","symbol"))attributes.add(text(value));
                var r=new BioMartQueries.Request(version.equals("2.60.1")?BioMartQueries.Edition.BIOMART_2_60_1:BioMartQueries.Edition.BIOMART_2_68_0,c.containsKey("dataset")?text(c.get("dataset")):"synthetic_dataset","default",attributes,filters,(Boolean)c.get("unique_rows"),(Boolean)c.get("check_filters"));
                var batches=BioMartQueries.plan(r,Set.of("gene","symbol","metric"),Map.of("gene","text","secondary","text","with_flag","boolean_list","numeric_filter","text","logical_text","text"),BioMartQueries.Limits.defaults());var outputs=new ArrayList<Map<String,Object>>();
                for(var batch:batches){var item=new LinkedHashMap<String,Object>();item.put("ordinal",batch.ordinal());item.put("xml",batch.xml());item.put("source_warnings",batch.sourceWarnings());item.put("filter_value_counts",batch.filters().stream().map(f->f.values().size()).toList());item.put("coercions",batch.coercions().stream().map(v->Map.of("filter",v.filter(),"value_index_in_batch",v.valueIndexInBatch(),"original",v.original(),"encoded",v.encoded(),"reason",v.reason())).toList());outputs.add(item);}
                row.put("status","PASS_NATIVE_QUERY_PLAN_ONLY");row.put("batches",outputs);
            }catch(IllegalArgumentException failure){row.put("status","NATIVE_QUERY_PLAN_ERROR");row.put("error",failure.toString());}
            result.add(row);
        }
        Files.writeString(Path.of(args[2]),NextflowJson.json(result)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        System.out.println("Recorded native query plans for "+result.size()+" versioned cases; no annotation request executed");
    }
}

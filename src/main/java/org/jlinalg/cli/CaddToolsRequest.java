/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed native CADD score and conversion-table requests; prediction is a separate prerequisite. */
final class CaddToolsRequest {
    private CaddToolsRequest(){ }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","out","assembly","cadd_version","input_vcf","representation","score_tables","input_scores","conversion_table","conversion_sha256","maximum_input_bytes","maximum_resource_bytes","maximum_output_bytes","maximum_decoded_characters","maximum_line_characters","maximum_variants","maximum_hits","maximum_table_rows"));
        if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version1");var r=new LinkedHashMap<String,Object>(raw);text(raw,"out");String mode=choice(raw,"mode","",Set.of("LOOKUP","CALIBRATE"));text(raw,"assembly");text(raw,"cadd_version");
        if(!Set.of("GRCh37","GRCh38").contains(raw.get("assembly"))||!text(raw,"cadd_version").equals("1.6"))throw new CaddToolsCli.Unavailable("Only the separately qualified CADD1.6 GRCh37/GRCh38 source-component semantics are available; do not substitute another assembly/version");
        Set<String> used=new HashSet<>(Set.of("schema_version","mode","out","assembly","cadd_version","maximum_input_bytes","maximum_resource_bytes","maximum_output_bytes","maximum_decoded_characters","maximum_line_characters","maximum_variants","maximum_hits","maximum_table_rows"));
        if(mode.equals("LOOKUP")){
            text(raw,"input_vcf");r.put("representation",choice(raw,"representation","",Set.of("literal","original-CADD1.6-preparation")));used.addAll(Set.of("input_vcf","representation","score_tables"));var tables=new ArrayList<Map<String,Object>>();Set<String> ids=new HashSet<>();
            for(Object item:list(raw.get("score_tables"),"score_tables")){var t=object(item,"score table");keys(t,Set.of("id","file","index","sha256","index_sha256"));String id=text(t,"id");if(!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")||!ids.add(id))throw new IOException("Require unique portable table IDs");for(String key:List.of("file","index"))text(t,key);for(String key:List.of("sha256","index_sha256"))if(!text(t,key).matches("[0-9a-f]{64}"))throw new IOException("Require explicit complete-table/index SHA256");tables.add(t);}
            if(tables.isEmpty()||tables.size()>8)throw new IOException("Require1..8 complete explicitly ordered CADD score tables");r.put("score_tables",tables);
        }else{
            for(String key:List.of("input_scores","conversion_table"))text(raw,key);if(!text(raw,"conversion_sha256").matches("[0-9a-f]{64}"))throw new IOException("Require explicit complete conversion-table SHA256");used.addAll(Set.of("input_scores","conversion_table","conversion_sha256"));
        }
        for(String key:raw.keySet())if(!used.contains(key))throw new IOException("Unused CADD option for selected mode: "+key);
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",1000000000L,1,137438953472L));r.put("maximum_resource_bytes",integer(raw,"maximum_resource_bytes",256000000000L,1,2000000000000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",10000000000L,1,2199023255552L));r.put("maximum_decoded_characters",integer(raw,"maximum_decoded_characters",10000000000L,1,2199023255552L));r.put("maximum_line_characters",integer(raw,"maximum_line_characters",1000000,256,16000000));r.put("maximum_variants",integer(raw,"maximum_variants",10000000,1,100000000));r.put("maximum_hits",integer(raw,"maximum_hits",100000000,1,1000000000));r.put("maximum_table_rows",integer(raw,"maximum_table_rows",1000000,1,10000000));return r;
    }
    static long number(Map<String,Object> r,String key){return((Number)r.get(key)).longValue();}
}

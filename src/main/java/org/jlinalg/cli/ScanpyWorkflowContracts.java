/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit ordered current-Scanpy requests; no inferred paper recipe or dtype. */
final class ScanpyWorkflowContracts {
    private ScanpyWorkflowContracts() { }
    static final String PROFILE="Scanpy-1.13.0-CPU-v1";
    static final Set<String> INPUT_FILES=Set.of("matrix","cells","features");
    static final Set<String> OPERATIONS=Set.of("snapshot_raw","copy_layer","set_X","subset","sqrt",
        "pp.calculate_qc_metrics","pp.filter_cells","pp.filter_genes","pp.normalize_total","pp.log1p","pp.scale",
        "pp.highly_variable_genes","experimental.pp.highly_variable_genes","experimental.pp.normalize_pearson_residuals",
        "pp.regress_out","pp.pca","pp.neighbors","tl.leiden","tl.umap","tl.tsne","tl.diffmap","tl.paga",
        "tl.draw_graph","tl.score_genes","tl.score_genes_cell_cycle","tl.rank_genes_groups","tl.embedding_density",
        "pp.downsample_counts","pp.scrublet");
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","out","python","input","steps","limits","timeout_seconds","maximum_input_bytes","maximum_output_bytes"));
        if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode",choice(raw,"mode","",Set.of("RUN")));
        r.put("profile",choice(raw,"profile","",Set.of(PROFILE)));
        for(String k:List.of("out","python"))r.put(k,text(raw,k));
        var input=object(raw.get("input"),"input");String format=choice(input,"format","",Set.of("h5ad","matrix_market"));
        keys(input,format.equals("h5ad")?Set.of("format","matrix"):Set.of("format","matrix","cells","features","dtype","orientation","obs_columns","var_columns"));
        var normalized=new LinkedHashMap<String,Object>();normalized.put("format",format);normalized.put("matrix",text(input,"matrix"));
        if(format.equals("matrix_market")) {
            for(String name:List.of("cells","features"))normalized.put(name,text(input,name));
            normalized.put("dtype",choice(input,"dtype","",Set.of("float64")));
            normalized.put("orientation",choice(input,"orientation","",Set.of("features_by_cells","cells_by_genes")));
            for(String name:List.of("obs_columns","var_columns")) {
                var columns=object(input.getOrDefault(name,Map.of()),name);if(columns.size()>256)throw new IOException("Too many typed metadata columns");
                for(var e:columns.entrySet()) {
                    identity(e.getKey());Object v=e.getValue();
                    if(v instanceof String s){if(!Set.of("string","float64","bool").contains(s))throw new IOException("Unsupported explicit metadata kind: "+s);}
                    else {var c=object(v,"category");keys(c,Set.of("levels","ordered"));strings(c.get("levels"),"category levels",1,100000);bool(c,"ordered",false);}
                }normalized.put(name,columns);
            }
        }r.put("input",normalized);
        var steps=list(raw.get("steps"),"steps");if(steps.isEmpty()||steps.size()>64)throw new IOException("Declare 1..64 actual ordered steps");
        var accepted=new ArrayList<Map<String,Object>>();
        for(Object item:steps) {
            var step=object(item,"step");keys(step,Set.of("operation","params","input_kind"));
            String operation=choice(step,"operation","",OPERATIONS);var params=object(step.getOrDefault("params",Map.of()),"params");
            if(params.size()>64)throw new IOException("Too many operation parameters");finiteJson(params,0,new int[]{0});
            var record=new LinkedHashMap<String,Object>();record.put("operation",operation);record.put("params",params);
            if(step.containsKey("input_kind"))record.put("input_kind",choice(step,"input_kind","",Set.of("raw_counts","log1p","provided_transformed","provided")));
            accepted.add(record);
        }r.put("steps",accepted);
        var limits=object(raw.getOrDefault("limits",Map.of()),"limits");keys(limits,Set.of("maximum_cells","maximum_features","maximum_entries","maximum_dense_entries","maximum_work","maximum_working_bytes"));
        var capacities=new LinkedHashMap<String,Object>();
        for(var entry:Map.of("maximum_cells",100000L,"maximum_features",100000L,"maximum_entries",20000000L,"maximum_dense_entries",20000000L,"maximum_work",2000000000L,"maximum_working_bytes",1073741824L).entrySet()) {
            long upper=switch(entry.getKey()){case "maximum_cells","maximum_features"->1000000L;case "maximum_entries","maximum_dense_entries"->200000000L;default->40000000000L;};
            capacities.put(entry.getKey(),integer(limits,entry.getKey(),entry.getValue(),1,upper));
        }r.put("limits",capacities);
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512000000,1,40000000000L));
        r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2000000000,1,40000000000L));
        return r;
    }
    private static void finiteJson(Object value,int depth,int[] visited)throws IOException {
        if(depth>12||++visited[0]>200000)throw new IOException("Parameter nesting/population capacity exceeded");
        if(value==null||value instanceof Boolean)return;
        if(value instanceof Number n){if(!Double.isFinite(n.doubleValue()))throw new IOException("Nonfinite request parameter");return;}
        if(value instanceof String s){if(s.length()>4094||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid parameter text");return;}
        if(value instanceof Map<?,?> m){for(var e:m.entrySet()){if(!(e.getKey() instanceof String))throw new IOException("Require parameter text keys");finiteJson(e.getValue(),depth+1,visited);}return;}
        if(value instanceof List<?> list){for(Object entry:list)finiteJson(entry,depth+1,visited);return;}
        throw new IOException("Unsupported JSON parameter type");
    }
}

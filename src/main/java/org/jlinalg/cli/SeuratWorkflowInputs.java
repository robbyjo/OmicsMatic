/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.SignacMatrixIO;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SeuratDifferentialInputs.*;

/** Exact sparse RNA intake and explicit transport; no fabricated biological groups. */
final class SeuratWorkflowInputs {
    private SeuratWorkflowInputs() { }
    record Prepared(Layer counts,List<String> warnings) { }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        Path input=root.resolve("inputs"),worker=root.resolve("worker");
        var intake=SignacMatrixIO.readCounts(input.resolve("counts"),input.resolve("features"),input.resolve("cells"),new SignacMatrixIO.Limits(((Number)r.get("maximum_features")).intValue(),((Number)r.get("maximum_cells")).intValue(),((Number)r.get("maximum_entries")).intValue(),((Number)r.get("maximum_input_bytes")).longValue(),16384),()->Thread.currentThread().isInterrupted());
        var m=intake.counts();for(var ids:List.of(m.features(),m.cells()))for(String id:ids)identity(id);
        Layer counts=new Layer(m.features(),m.cells(),m.columnPointers(),m.rowIndices(),m.values());int nf=counts.features().size(),nc=counts.cells().size();
        long dense=(long)nf*nc;if(nc<4||nf<3||dense>((Number)r.get("maximum_dense_entries")).longValue())throw new IOException("Require at least 3 features/4 cells and full RNA population within maximum_dense_entries");
        var pca=object(r.get("pca"),"pca");long pc=((Number)pca.get("components")).longValue();
        if(dense>Long.MAX_VALUE/pc||dense*pc>((Number)r.get("maximum_work")).longValue())throw new IOException("Full-population PCA audit estimate exceeds maximum_work");
        // SCT v2 returns the full retained-feature residual matrix in this profile.
        // Dense Gram allocation depends on the selected features, not just cells.
        long selected=Math.min(nf,((Number)r.get("variable_features")).longValue());
        if(pca.get("solver").equals("source_default")&&selected*selected>((Number)r.get("maximum_dense_entries")).longValue())throw new IOException("Source-default Gram allocation exceeds maximum_dense_entries; request an explicit permitted solver or larger capacity");
        var neighbors=object(r.get("neighbors"),"neighbors");long k=((Number)neighbors.get("k")).longValue();
        if(k>nc)throw new IOException("Neighbor k exceeds cells; no silent source clipping");
        if((long)nc*k>((Number)r.get("maximum_graph_edges")).longValue())throw new IOException("NN edges exceed maximum_graph_edges");
        var umap=object(r.get("umap"),"umap");if(Boolean.TRUE.equals(umap.get("enabled"))&&((Number)umap.get("neighbors")).longValue()>=nc)throw new IOException("UMAP neighbors must be below cell count; no silent source truncation");
        var tsne=object(r.get("tsne"),"tsne");if(Boolean.TRUE.equals(tsne.get("enabled"))&&3*((Number)tsne.get("perplexity")).doubleValue()>nc-1)throw new IOException("Rtsne requires 3*perplexity <= cells-1");
        List<String> warnings=new ArrayList<>();
        warnings.add("EXACT_TRANSPORT: public feature/cell/metadata identities remain captured. Only explicit f#/c#/v#/l# codes enter Seurat; public outputs restore exact IDs. No identifier repair or inferred cell-type annotation occurs.");
        warnings.add("SOURCE_FILTERS: original VST/SCT/PCA can exclude features. Read feature-disposition.tsv and diagnostics.json; exclusions are source outcomes, not negative biological findings. Whole-paper reproduction and historical author editions are not established.");
        warnings.add("CAPACITY_SCOPE: explicit input/dense/graph/work/output and wall limits are admission controls, not an operating-system RAM quota. Output may exceed its polling bound briefly before owned-process cleanup.");
        if(intake.duplicateCoordinates()>0)warnings.add("DUPLICATE_COUNT_COORDINATES: "+intake.duplicateCoordinates()+" exact integer coordinates were summed by the intake reader; original tokens remain captured.");
        var covariates=list(r.get("regressors"),"regressors");String[][] values=new String[nc][covariates.size()];List<String> extra=new ArrayList<>();
        if(r.containsKey("metadata"))try(var in=reader(input.resolve("metadata"))) {
            String[] header=fields(line(in));if(header.length<1||!header[0].equals("cell"))throw new IOException("Metadata first column must be cell");Map<String,Integer> columns=new LinkedHashMap<>();
            for(int i=1;i<header.length;i++){identity(header[i]);if(header[i].equals("cell")||columns.put(header[i],i)!=null)throw new IOException("Duplicate/reserved metadata column");}
            Set<String> used=new HashSet<>();for(Object item:covariates){var c=object(item,"regressor");String name=text(c,"name");if(!columns.containsKey(name))throw new IOException("Missing regressor column: "+name);used.add(name);}
            for(String name:columns.keySet())if(!used.contains(name))extra.add(name);
            for(int cell=0;cell<nc;cell++) {
                check();String[] f=fields(line(in));if(f.length!=header.length||!f[0].equals(counts.cells().get(cell)))throw new IOException("Metadata rows must match exact count-cell identities/order");
                for(int j=0;j<covariates.size();j++) {
                    var c=object(covariates.get(j),"regressor");String raw=f[columns.get(text(c,"name"))];
                    if(c.get("kind").equals("numeric"))values[cell][j]=Double.toString(numeric(raw));
                    else {int index=list(c.get("levels"),"factor levels").indexOf(raw);if(index<0)throw new IOException("Unknown exact factor level; no imputation or dropped cells");values[cell][j]="l"+(index+1);}
                }
            }
            if(line(in)!=null)throw new IOException("Extra metadata rows");
        }
        if(!extra.isEmpty())warnings.add("UNMODELED_METADATA: captured columns not used by regression: "+String.join(", ",extra));
        List<Map<String,Object>> transport=new ArrayList<>();
        for(int j=0;j<covariates.size();j++){var c=object(covariates.get(j),"regressor");Map<String,Object> v=new LinkedHashMap<>();v.put("name","v"+(j+1));v.put("kind",c.get("kind"));if(c.containsKey("levels"))v.put("levels",java.util.stream.IntStream.rangeClosed(1,list(c.get("levels"),"factor levels").size()).mapToObj(i->"l"+i).toList());transport.add(v);}
        var parameters=new LinkedHashMap<String,Object>();for(String key:List.of("profile","normalization","variable_features","pca","neighbors","clustering","umap","tsne","seed","maximum_dense_entries","maximum_work","maximum_graph_edges"))parameters.put(key,r.get(key));
        for(String key:List.of("scale_factor","scale_center","scale_scale","scale_max","sct_cells","sct_min_cells"))if(r.containsKey(key))parameters.put(key,r.get(key));
        parameters.put("n_features",nf);parameters.put("n_cells",nc);parameters.put("regressors",transport);
        var summary=new LinkedHashMap<String,Object>();summary.put("features",counts.features());summary.put("cells",counts.cells());summary.put("regressors",covariates);summary.put("unmodeled_metadata",extra);summary.put("warnings",warnings);summary.put("duplicate_coordinates",intake.duplicateCoordinates());summary.put("explicit_zero_entries",intake.explicitZeroEntries());
        if(!compare)Files.createDirectory(worker);writeLayer(worker.resolve("counts.mtx"),counts,compare);
        try(var out=new Lines(worker.resolve("cells.txt"),compare)){for(int i=1;i<=nc;i++)out.line("c"+i);}
        try(var out=new Lines(worker.resolve("metadata.tsv"),compare)) {
            StringBuilder header=new StringBuilder("cell");for(int j=1;j<=covariates.size();j++)header.append("\tv").append(j);out.line(header.toString());
            for(int cell=0;cell<nc;cell++){StringBuilder row=new StringBuilder("c"+(cell+1));for(String value:values[cell])row.append('\t').append(value);out.line(row.toString());}
        }
        if(compare){if(!same(parameters,read(worker.resolve("parameters.json"),64000000))||!same(summary,read(root.resolve("prepared-inputs.json"),64000000)))throw new IOException("Prepared request/identity map differs from captured inputs");}
        else {for(var e:Map.of(worker.resolve("parameters.json"),parameters,root.resolve("prepared-inputs.json"),summary).entrySet()){byte[] raw=RsemJson.bytes(e.getValue());if(raw.length>64000000)throw new IOException("Prepared protocol exceeds 64MB");Files.write(e.getKey(),raw,StandardOpenOption.CREATE_NEW);}}
        return new Prepared(counts,List.copyOf(warnings));
    }
}

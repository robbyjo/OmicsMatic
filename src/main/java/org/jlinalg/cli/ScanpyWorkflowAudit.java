/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Exported identities, dimensions, finite numeric tables and complete step ledger. No model refit. */
final class ScanpyWorkflowAudit {
    private ScanpyWorkflowAudit() { }
    static void outputs(Path root,Map<String,Object> request)throws IOException {
        Path worker=root.resolve("worker");var report=read(worker.resolve("report.json"),16000000);var state=read(worker.resolve("status.json"));
        if(!ScanpyWorkflowContracts.PROFILE.equals(report.get("profile"))||!"completed-with-warnings".equals(report.get("status"))||!Boolean.TRUE.equals(report.get("results_admitted"))||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted"))||!Objects.equals(report.get("required_action"),state.get("required_action")))throw new IOException("Original worker did not admit a complete result");
        var profile=object(SimpleJson.parse(new String(ScanpyWorkflowRuntime.resource("SOURCE-PROFILE.json"),StandardCharsets.UTF_8)),"profile");
        var runtime=object(report.get("runtime"),"runtime");
        if(!same(profile.get("versions"),runtime.get("versions"))||integer(runtime,"scanpy_source_files_checked",0,1,10000)!=object(profile.get("scanpy_sources"),"source pins").size())throw new IOException("Recorded original package/source profile differs");
        var steps=list(request.get("steps"),"steps");var actual=list(report.get("steps"),"step results");
        if(steps.size()!=actual.size())throw new IOException("Original step population incomplete");
        Object previous=report.get("input");
        for(int i=0;i<actual.size();i++) {
            var step=object(actual.get(i),"step result");var declared=object(steps.get(i),"step declaration");
            if(integer(step,"index",-1,0,63)!=i||!"completed".equals(step.get("status"))||!same(declared,step.get("declaration"))||!Objects.equals(declared.get("operation"),step.get("operation"))||!same(previous,step.get("before")))throw new IOException("Original ordered step/settings chain differs");
            var detail=step.get("details");if(declared.get("operation").toString().contains(".")) {
                var d=object(detail,"call details");if(!same(declared.get("params"),d.get("provided_params"))||text(d,"signature").isEmpty())throw new IOException("Original call settings/signature incomplete");
                var executed=object(d.get("executed_params"),"executed params");var expected=new LinkedHashMap<>(object(declared.get("params"),"params"));expected.put("out",false);
                if(!same(executed,expected))throw new IOException("Original executed settings differ from explicit output-storage control");
            }
            previous=step.get("after");
        }
        if(!same(previous,report.get("output")))throw new IOException("Final output differs from step ledger");
        var summary=object(report.get("output"),"summary");var limits=object(request.get("limits"),"limits");
        long cells=integer(summary,"cells",0,1,integer(limits,"maximum_cells",0,1,1000000));
        long genes=integer(summary,"features",0,1,integer(limits,"maximum_features",0,1,1000000));
        if(!text(summary,"matrix_and_identity_sha256").matches("[0-9a-f]{64}"))throw new IOException("Missing exact matrix/identity fingerprint");
        Path output=worker.resolve("outputs");var metadata=object(report.get("metadata"),"metadata schema");
        var cellIds=table(output.resolve("cells.tsv"),"cell",cells,list(metadata.get("cells"),"cell columns"));
        var geneIds=table(output.resolve("features.tsv"),"gene",genes,list(metadata.get("features"),"gene columns"));
        var input=object(request.get("input"),"input");if(input.get("format").equals("matrix_market")) {
            subsequence(firstColumn(root.resolve(text(input,"cells")),"cell"),cellIds);
            subsequence(firstColumn(root.resolve(text(input,"features")),"gene"),geneIds);
        }
        market(output.resolve("matrix.mtx"),genes,cells,limits);
        byte[] magic=new byte[8];try(var stream=Files.newInputStream(output.resolve("result.h5ad"))){if(stream.readNBytes(magic,0,8)!=8)throw new IOException("Missing H5AD result");}
        if(!Arrays.equals(magic,new byte[]{(byte)137,72,68,70,13,10,26,10}))throw new IOException("Invalid HDF5 signature");
        Set<String> names=new HashSet<>(Set.of("result.h5ad","cells.tsv","features.tsv","matrix.mtx"));
        for(Object value:list(report.get("exports"),"exports")) {
            var e=object(value,"export");String path=text(e,"path");if(!names.add(path)||Path.of(path).getNameCount()!=1)throw new IOException("Repeated/unsafe exported path");
            Path file=ScanpyWorkflowCli.safe(output,path);String slot=text(e,"slot");
            if(slot.equals("uns"))marker(file,integer(e,"rows",0,0,40000000000L));
            else {
                if(!Set.of("obsm","varm","obsp","varp").contains(slot))throw new IOException("Unknown exported slot");
                var shape=list(e.get("shape"),"shape");if(shape.size()!=2)throw new IOException("Require 2D exported arrays");
                long rows=integer(Map.of("x",shape.get(0)),"x",0,1,1000000),cols=integer(Map.of("x",shape.get(1)),"x",0,1,1000000);
                if(rows!=(slot.startsWith("ob")?cells:genes)||slot.endsWith("p")&&cols!=rows)throw new IOException("Exported array dimensions differ from slot/population");
                if("sparse".equals(e.get("storage")))market(file,rows,cols,limits);else dense(file,rows,cols,limits);
            }
        }
        if(!ScanpyWorkflowRuntime.footprint(output).keySet().equals(names))throw new IOException("Exported result file population differs");
    }
    private static List<String> table(Path file,String first,long count,List<?> schema)throws IOException {
        List<String> ids=new ArrayList<>();Set<String> unique=new HashSet<>();
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            List<String> header=parse(reader.readLine(),file,1);if(header.size()!=schema.size()+1||!header.get(0).equals(first))throw new IOException("Exported typed metadata header differs");
            Set<String> names=new HashSet<>(header);if(names.size()!=header.size())throw new IOException("Repeated metadata headers");
            for(int j=0;j<schema.size();j++)if(!header.get(j+1).equals(text(object(schema.get(j),"column"),"name")))throw new IOException("Metadata column order differs");
            String line;long row=1;while((line=reader.readLine())!=null) {
                if(++row-1>count)throw new IOException("Excess exported identities");List<String> fields=parse(line,file,row);if(fields.size()!=header.size())throw new IOException("Invalid exported metadata width");
                String id=fields.get(0);identity(id);if(!unique.add(id))throw new IOException("Duplicate output identity");ids.add(id);
                for(int j=0;j<schema.size();j++) {
                    String kind=text(object(schema.get(j),"column"),"kind"),value=fields.get(j+1);
                    switch(kind){case "numeric"->finite(value);case "boolean"->{if(!Set.of("True","False","true","false").contains(value))throw new IOException("Invalid exported boolean");}case "text"->{}default->throw new IOException("Unknown metadata kind");}
                }
            }if(ids.size()!=count)throw new IOException("Missing exported identities");
        }return ids;
    }
    private static List<String> firstColumn(Path path,String first)throws IOException {
        List<String> ids=new ArrayList<>();try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){String line=reader.readLine();if(line==null||!line.split("\t",-1)[0].equals(first))throw new IOException("Captured input identity header differs");while((line=reader.readLine())!=null)ids.add(line.split("\t",-1)[0]);}return ids;
    }
    private static void subsequence(List<String> original,List<String> result)throws IOException {int index=0;for(String value:original)if(index<result.size()&&value.equals(result.get(index)))index++;if(index!=result.size())throw new IOException("Output renamed/reordered identities rather than an ordered subset");}
    private static List<String> parse(String line,Path file,long row)throws IOException {if(line==null||line.length()>2000000)throw new IOException("Missing/excessive exported row");return DelimitedData.parse(line,'\t',row,file);}
    private static double finite(String value)throws IOException {try{double x=Double.parseDouble(value);if(!Double.isFinite(x))throw new NumberFormatException();return x;}catch(NumberFormatException e){throw new IOException("Nonfinite/invalid exported numeric token: "+value,e);}}
    private static void dense(Path file,long rows,long cols,Map<String,Object> limits)throws IOException {
        if(rows*cols>integer(limits,"maximum_dense_entries",0,1,200000000))throw new IOException("Dense export capacity exceeded");
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){String line;long n=0;while((line=reader.readLine())!=null){if(++n>rows)throw new IOException("Excess dense rows");String[] values=line.split("\t",-1);if(values.length!=cols)throw new IOException("Dense width differs");for(String value:values)finite(value);}if(n!=rows)throw new IOException("Missing dense rows");}
    }
    private static void marker(Path file,long expected)throws IOException {
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            List<String> header=parse(reader.readLine(),file,1);if(!header.contains("names")||!header.contains("scores"))throw new IOException("Incomplete original marker columns");
            String line;long n=0;while((line=reader.readLine())!=null){if(++n>expected)throw new IOException("Excess original marker rows");var row=parse(line,file,n+1);if(row.size()!=header.size())throw new IOException("Invalid marker width");for(int j=0;j<header.size();j++){String name=header.get(j),value=row.get(j);if(Set.of("group","names").contains(name)){identity(value);continue;}double x=finite(value);if(Set.of("pvals","pvals_adj","pct_nz_group","pct_nz_reference").contains(name)&&(x<0||x>1))throw new IOException("Original marker probability outside [0,1]");}}
            if(n!=expected)throw new IOException("Missing original marker rows");
        }
    }
    private static void market(Path file,long rows,long cols,Map<String,Object> limits)throws IOException {
        try(var reader=Files.newBufferedReader(file,StandardCharsets.US_ASCII)) {
            if(!"%%MatrixMarket matrix coordinate real general".equals(reader.readLine()))throw new IOException("Require complete real general coordinate export");
            String line;do{line=reader.readLine();}while(line!=null&&line.startsWith("%"));if(line==null)throw new IOException("Missing exported matrix dimensions");
            String[] dimensions=line.trim().split("\\s+");if(dimensions.length!=3)throw new IOException("Invalid matrix dimensions");long nr=Long.parseLong(dimensions[0]),nc=Long.parseLong(dimensions[1]),entries=Long.parseLong(dimensions[2]);
            if(nr!=rows||nc!=cols||entries<0||entries>Math.max(integer(limits,"maximum_entries",0,1,200000000),integer(limits,"maximum_dense_entries",0,1,200000000)))throw new IOException("Exported matrix population/capacity differs");
            if(entries>integer(limits,"maximum_working_bytes",0,1,40000000000L)/64)throw new IOException("Complete coordinate audit exceeds working-byte capacity");
            Set<Long> seen=new HashSet<>();long count=0;
            while((line=reader.readLine())!=null){if(++count>entries)throw new IOException("Excess exported matrix entries");String[] fields=line.trim().split("\\s+");if(fields.length!=3)throw new IOException("Invalid coordinate");long i=Long.parseLong(fields[0])-1,j=Long.parseLong(fields[1])-1;if(i<0||i>=rows||j<0||j>=cols||!seen.add(i*cols+j))throw new IOException("Invalid/duplicate output coordinate");finite(fields[2]);}
            if(count!=entries)throw new IOException("Missing exported matrix entries");
        }catch(NumberFormatException e){throw new IOException("Invalid matrix integer",e);}
    }
}

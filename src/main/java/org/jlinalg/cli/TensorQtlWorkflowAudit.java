/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Saved integrity and original export checks; never loads Python pickle or refits models. */
final class TensorQtlWorkflowAudit {
    private TensorQtlWorkflowAudit() { }
    private record Element(long rows,int width,List<String> dtypes,List<String> names,int indexes,String label) { }
    static void outputs(Path root,Map<String,Object> r,boolean saved)throws IOException {
        TensorQtlWorkflowFiles.prepare(root,r,true);var report=read(root.resolve("worker/report.json"),32_000_000);var profile=TensorQtlWorkflowContracts.profile(r);
        if(!"completed-with-warnings".equals(report.get("status"))||!Boolean.TRUE.equals(report.get("results_admitted"))||!Boolean.TRUE.equals(report.get("namespace_unchanged"))||!r.get("profile").equals(report.get("profile"))||!r.get("operation").equals(report.get("operation"))||!profile.get("edition").equals(report.get("edition")))throw new IOException("Original worker did not admit the requested unchanged workflow");
        if(!same(r.get("seed"),report.get("project_seed"))||!Boolean.FALSE.equals(report.get("author_seed_inferred"))||!r.get("device").equals(report.get("requested_device")))throw new IOException("Original seed/device record differs");
        if(!same(profile.get("packages"),report.get("packages")))throw new IOException("Original package versions differ");
        for(String key:List.of("optimizer_failures","nonconverged_models","nonfinite_outputs"))if(!list(report.get(key),key).isEmpty())throw new IOException("Original numerical failure is not an admitted result: "+key);
        var counts=object(profile.get("runtime_file_counts"),"runtime counts");var expected=new LinkedHashMap<String,Object>();expected.put("python",counts.get("python"));
        var binding=read(root.resolve("worker/runtime-bindings.json"));if(binding.containsKey("r_home")){expected.put("bridge",counts.get("bridge"));expected.put("R",counts.get("R"));}
        if(!same(expected,report.get("runtime_files_prechecked"))||!same(expected,report.get("runtime_files_checked")))throw new IOException("Exact runtime file guards were not completed");
        if(integer(report,"namespace_functions_checked",0,1,10000)<1)throw new IOException("Original code guards absent");
        long cuda=integer(report,"observed_cuda_returns",-1,0,Long.MAX_VALUE);
        if(r.get("device").equals("CUDA")){if(cuda<1||integer(report,"cuda_max_memory_allocated",0,1,Long.MAX_VALUE)<1)throw new IOException("Original CUDA execution not established");text(report,"gpu_name");}
        else if(cuda!=0||integer(report,"cuda_max_memory_allocated",-1,0,0)!=0)throw new IOException("CPU execution used CUDA unexpectedly");
        var nodes=new TreeMap<Long,Element>();node(read(root.resolve("results/objects.json"),128_000_000),"result",nodes,0);values(root,nodes,r);journal(root,report);
        try(var paths=Files.walk(root.resolve("results"))){for(Path p:paths.filter(Files::isRegularFile).toList()){
            String relative=root.resolve("results").relativize(p).toString().replace('\\','/');
            if(relative.equals("objects.json")||relative.equals("values.tsv"))continue;
            if(!r.get("operation").equals("CIS_NOMINAL")||!relative.matches("original/analysis[.]cis_qtl_(?:pairs[.][A-Za-z0-9_+.-]+[.]parquet|top_assoc[.]txt[.]gz)"))throw new IOException("Unexpected original result member: "+relative);
        }}
    }
    private static void node(Map<String,Object> value,String label,Map<Long,Element> elements,int depth)throws IOException {
        if(depth>256)throw new IOException("Result tree depth exceeded");String kind=text(value,"kind");
        switch(kind){
            case "dict" -> {for(Object item:list(value.get("items"),"dict items")){var entry=object(item,"entry");var key=object(entry.get("key"),"key");scalar(key);node(object(entry.get("value"),"value"),label+"/"+String.valueOf(key.get("value")),elements,depth+1);}}
            case "list","tuple" -> {int i=0;for(Object item:list(value.get("items"),"items"))node(object(item,"item"),label+"/"+i++,elements,depth+1);}
            case "scalar" -> scalar(value);
            case "nonfinite" -> throw new IOException("Original nonfinite result not admitted: "+label);
            case "dataframe","series" -> {
                List<?> shape=list(value.get("shape"),"shape");if(shape.size()!=2)throw new IOException("Data-frame dimensions differ");long rows=dimension(shape.get(0));int columns=Math.toIntExact(dimension(shape.get(1)));int indexes=(int)integer(value,"index_levels",0,1,128);
                List<?> names=list(value.get("columns"),"columns"),types=list(value.get("dtypes"),"dtypes");if(names.size()!=columns||types.size()!=columns||list(value.get("index_names"),"index names").size()!=indexes)throw new IOException("Original frame column/index population differs");
                var dtypes=new ArrayList<String>();var labels=new ArrayList<String>();
                for(int j=0;j<columns;j++){if(!(types.get(j) instanceof String dtype))throw new IOException("Invalid original dtype");dtypes.add(dtype);var key=object(names.get(j),"column");scalar(key);labels.add(String.valueOf(key.get("value")));}
                long id=integer(value,"element",0,1,Integer.MAX_VALUE);if(elements.put(id,new Element(rows,columns+indexes,dtypes,labels,indexes,label))!=null)throw new IOException("Repeated export element");
            }
            case "ndarray","tensor" -> {
                long length=1;for(Object d:list(value.get("shape"),"array shape"))try{length=Math.multiplyExact(length,dimension(d));}catch(ArithmeticException e){throw new IOException("Array shape overflows",e);}
                if(integer(value,"length",-1,0,Long.MAX_VALUE)!=length||!"C".equals(value.get("order")))throw new IOException("Original flat-array shape/order differs");
                long id=integer(value,"element",0,1,Integer.MAX_VALUE);if(elements.put(id,new Element(length,1,List.of(text(value,"dtype")),List.of(label),0,label))!=null)throw new IOException("Repeated array element");
            }
            default -> throw new IOException("Unrecognized original result kind: "+kind);
        }
    }
    private static void scalar(Map<String,Object> value)throws IOException {
        if(!"scalar".equals(value.get("kind")))throw new IOException("Original scalar/label kind differs");String type=text(value,"type");Object x=value.get("value");
        if(!Set.of("NoneType","str","bool","int","float").contains(type)||type.equals("NoneType")&&x!=null||type.equals("str")&&!(x instanceof String)||type.equals("bool")&&!(x instanceof Boolean)||Set.of("int","float").contains(type)&&!(x instanceof Number))throw new IOException("Original scalar type/value differs");
        if(x instanceof Number n&&!Double.isFinite(n.doubleValue()))throw new IOException("Nonfinite original scalar");
    }
    private static long dimension(Object x)throws IOException {return integer(Map.of("v",x),"v",-1,0,Integer.MAX_VALUE);}
    private static void values(Path root,Map<Long,Element> elements,Map<String,Object> request)throws IOException {
        var counters=new HashMap<Long,Long>();long prior=0;long samples=integer(read(root.resolve("worker/parameters.json")),"samples",0,0,100000);
        try(var reader=Files.newBufferedReader(root.resolve("results/values.tsv"),StandardCharsets.UTF_8)){
            if(!"element\trow\tvalues".equals(reader.readLine()))throw new IOException("Numeric journal header differs");String line;
            while((line=reader.readLine())!=null){if(line.length()>8_000_000)throw new IOException("Numeric journal row exceeds8-million-character audit capacity");String[] fields=line.split("\t",-1);if(fields.length!=3)throw new IOException("Numeric journal width differs");long id=index(fields[0]),row=index(fields[1]);Element item=elements.get(id);
                if(item==null||id<prior||row!=counters.getOrDefault(id,0L)||row>=item.rows())throw new IOException("Numeric journal element/order/population differs");prior=id;var cells=list(SimpleJson.parse(fields[2]),"cells");if(cells.size()!=item.width())throw new IOException("Original numeric export width differs");
                for(int j=0;j<cells.size();j++){
                    if(!(cells.get(j) instanceof String text))throw new IOException("Numeric export cells must be literal strings");if(j<item.indexes())continue;int col=j-item.indexes();String dtype=item.dtypes().get(col),name=item.names().get(col);
                    if(dtype.equals("bool")){if(!Set.of("TRUE","FALSE").contains(text))throw new IOException("Invalid logical original value");}
                    else if(dtype.matches("(?:u?int|float)[0-9]+")||name.startsWith("pval")||Set.of("qval","num_var","beta_shape1","beta_shape2","true_df","maf","af","pip","slope","slope_se","b","b_se","rank","group_size","ma_count","ma_samples").contains(name)){
                        if(text.equals("NaN")&&request.get("operation").equals("CIS_PERMUTE")&&Boolean.FALSE.equals(request.get("beta_approx"))&&item.label().equals("result/original_cis_permutations")&&Set.of("beta_shape1","beta_shape2","true_df","pval_true_df","pval_beta").contains(name))continue;
                        double x=SusieWorkflowFiles.number(text);
                        if(dtype.contains("int")&&x!=Math.rint(x))throw new IOException("Nonintegral original integer export");
                        if((name.startsWith("pval")||name.equals("qval"))&&(x<0||x>1))throw new IOException("Original probability outside [0,1]");
                        // Original 1.0.5 dense interaction labels ma_samples and
                        // ma_count Series as "maf" too. Preserve those literal
                        // names; their tuple slots have count, not AF, bounds.
                        boolean legacyCount=name.equals("maf")&&request.get("profile").toString().startsWith("tensorqtl-1.0.5-")&&request.get("operation").equals("TRANS_NOMINAL")&&request.containsKey("interaction")&&Boolean.FALSE.equals(request.get("return_sparse"))&&Set.of("result/original_trans/4","result/original_trans/5").contains(item.label());
                        if(legacyCount){long maximum=item.label().endsWith("/4")?samples:2*samples;if(x<0||x>maximum||x!=Math.rint(x))throw new IOException("Original legacy interaction count bound failed; no relabeling or repair");}
                        else if((name.equals("maf")||name.equals("af")||name.equals("pip"))&&(x< -1e-5||x>1+1e-5))throw new IOException("Original float32 allele/PIP bound failed; no clipping");
                        if((name.equals("b_se")||name.equals("slope_se")||name.startsWith("beta_shape")||name.equals("true_df"))&&x<0)throw new IOException("Invalid original standard error/beta parameter");
                    }
                }
                counters.put(id,row+1);
            }
        }
        for(var e:elements.entrySet())if(counters.getOrDefault(e.getKey(),0L)!=e.getValue().rows())throw new IOException("Incomplete original numeric export");
    }
    private static long index(String text)throws IOException {try{long n=Long.parseLong(text);if(n<0||!text.equals(Long.toString(n)))throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IOException("Invalid literal numeric-journal index",e);}}
    private static void journal(Path root,Map<String,Object> report)throws IOException {
        byte[] magic="OMICS-TORCH-JOURNAL1\n".getBytes(StandardCharsets.UTF_8);long position=magic.length;List<?> events=list(report.get("original_events"),"events");
        if(integer(report,"original_event_count",-1,0,Integer.MAX_VALUE)!=events.size())throw new IOException("Original event population differs");
        try(var file=FileChannel.open(root.resolve("worker/objects.journal"),StandardOpenOption.READ)){
            ByteBuffer buffer=ByteBuffer.allocate(1048576);ByteBuffer first=ByteBuffer.allocate(magic.length);fully(file,first,0);if(!Arrays.equals(first.array(),magic))throw new IOException("Original object journal header differs");
            for(Object item:events){var event=object(item,"event");String path=text(event,"path");
                if(path.endsWith(".rds")){if(!path.matches("worker/objects/[0-9]+-original-R-qvalue[.]rds")||!Files.isRegularFile(root.resolve(path),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing original R object");continue;}
                if(!path.equals("worker/objects.journal"))throw new IOException("Unsafe original object path");long bytes=integer(event,"payload_bytes",-1,0,Long.MAX_VALUE),offset=integer(event,"payload_offset",-1,0,Long.MAX_VALUE);
                ByteBuffer header=ByteBuffer.allocate(8);fully(file,header,position);if(header.getLong(0)!=bytes||offset!=position+8||offset>file.size()||bytes>file.size()-offset)throw new IOException("Original object record boundary differs");
                MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}long remaining=bytes,cursor=offset;
                while(remaining>0){buffer.clear();buffer.limit((int)Math.min(buffer.capacity(),remaining));int n=file.read(buffer,cursor);if(n<1)throw new IOException("Original object record ended early");digest.update(buffer.array(),0,n);remaining-=n;cursor+=n;}
                if(!HexFormat.of().formatHex(digest.digest()).equals(event.get("sha256")))throw new IOException("Original object record hash differs");
                if(event.containsKey("source_success")&&!Boolean.TRUE.equals(event.get("source_success"))||event.containsKey("source_converged")&&!Boolean.TRUE.equals(event.get("source_converged")))throw new IOException("Failed original optimizer/model return is not admitted");position=offset+bytes;
            }
            if(position!=file.size())throw new IOException("Unindexed original object bytes remain");
        }
    }
    private static void fully(FileChannel file,ByteBuffer out,long position)throws IOException {while(out.hasRemaining()){int n=file.read(out,position);if(n<1)throw new IOException("Original journal ended early");position+=n;}}
}

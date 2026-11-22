/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.genetics.QtltoolsFunctionalDensity;
import htsjdk.variant.vcf.VCFFileReader;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Complete unchanged input capture and bounded literal metadata/native preparation. */
final class QtltoolsWorkflowFiles {
    private QtltoolsWorkflowFiles() { }
    @FunctionalInterface interface Line { boolean accept(String value)throws IOException; }
    static void lines(Path path,Map<String,Object> r,Line consumer)throws IOException {
        try(InputStream source=Files.newInputStream(path);var buffered=new BufferedInputStream(source)) {
            buffered.mark(2);int first=buffered.read(),second=buffered.read();buffered.reset();
            try(InputStream input=first==31&&second==139?new GZIPInputStream(buffered):buffered) {
                var line=new ByteArrayOutputStream();long rows=0,bytes=0,cap=QtltoolsWorkflowContracts.limit(r,"maximum_input_bytes");
                for(int value;(value=input.read())!=-1;) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("QTLtools text scan interrupted");
                    if(++bytes>cap)throw new IOException("Decoded text exceeds maximum_input_bytes;increase the declared capacity without changing the data");
                    if(value==10){if(++rows>QtltoolsWorkflowContracts.limit(r,"maximum_rows"))throw new IOException("Text row capacity exceeded");if(!consumer.accept(decode(line.toByteArray())))return;line.reset();}
                    else {if(line.size()>=QtltoolsWorkflowContracts.limit(r,"maximum_line_bytes"))throw new IOException("Text line capacity exceeded");line.write(value);}
                }
                if(line.size()>0){if(++rows>QtltoolsWorkflowContracts.limit(r,"maximum_rows"))throw new IOException("Text row capacity exceeded");consumer.accept(decode(line.toByteArray()));}
            }
        }
    }
    private static String decode(byte[] bytes)throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    static Path safe(Path root,String name)throws IOException {
        Path relative=Path.of(name);if(relative.isAbsolute()||!relative.normalize().equals(relative)||name.contains("\\")||name.contains("..")||!root.resolve(relative).normalize().startsWith(root))throw new IOException("Unsafe QTLtools capture member");return root.resolve(relative);
    }
    static Path newOutput(Path root)throws IOException {
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Preserve existing output;choose a NEW directory");
        Path existing=root.getParent();var suffix=new ArrayDeque<Path>();while(existing!=null&&!Files.exists(existing,LinkOption.NOFOLLOW_LINKS)){suffix.addFirst(existing.getFileName());existing=existing.getParent();}
        if(existing==null)throw new IOException("Cannot resolve output parent");
        for(Path node=existing.toRealPath();node!=null;node=node.getParent())if(Files.isRegularFile(node.resolve("capture-files.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Output cannot be inside a retained capture");
        Files.createDirectories(root.getParent());return Files.createDirectory(root);
    }
    static long capture(Path source,Path target,long maximum)throws IOException {
        if(!Files.exists(source)||!Files.isReadable(source))throw new QtltoolsWorkflowRuntime.Unavailable("Required permitted input is missing/inaccessible: "+source);
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>maximum)throw new IOException("Nonregular or oversized input: "+source);
        String hash=FollowupSupport.hash(source);long count=0;Files.createDirectories(target.getParent());
        try(var input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("QTLtools capture interrupted");if(n>maximum-count)throw new IOException("Input grew beyond capacity");output.write(buffer,0,n);count+=n;}
        }
        if(!hash.equals(FollowupSupport.hash(source))||!hash.equals(FollowupSupport.hash(target)))throw new IOException("Input changed during capture");return count;
    }
    static Map<String,Object> prepare(Path root,Map<String,Object> r)throws IOException {
        var info=new LinkedHashMap<String,Object>();var samples=new LinkedHashMap<String,List<String>>();String operation=text(r,"operation");
        for(var input:QtltoolsWorkflowContracts.inputs(r)) {
            Path path=root.resolve(input.captured());if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Incomplete captured input");
            String format=text(input.descriptor(),"format");
            if(input.key().equals("genotypes")&&format.startsWith("VCF")||input.key().equals("genotypes")&&format.equals("BCF")) {
                try(var reader=new VCFFileReader(path,false)){samples.put("genotypes",unique(reader.getFileHeader().getGenotypeSamples(),"VCF/BCF samples"));}
                catch(RuntimeException failed){throw new IOException("Cannot read the declared original genotype header",failed);}
            }else if(input.key().equals("phenotypes")||input.key().equals("genotypes"))samples.put(input.key(),header(path,r,6));
            else if(input.key().equals("covariates"))samples.put("covariates",header(path,r,1));
            if(input.capturedIndex()!=null&&Files.size(root.resolve(input.capturedIndex()))==0)throw new IOException("Empty declared index");
        }
        if(operation.startsWith("CIS_")||operation.startsWith("TRANS_")) {
            var common=new ArrayList<>(samples.get("genotypes"));for(var ids:samples.values()){var set=new HashSet<>(ids);common.removeIf(id->!set.contains(id));}
            if(common.size()<3)throw new IOException("Fewer than three common prepared samples;no complete regression result can be admitted");
            info.put("input_sample_headers",samples);info.put("common_samples_in_genotype_header_order",common);var excluded=new LinkedHashMap<String,Object>();var commonSet=new HashSet<>(common);for(var entry:samples.entrySet())excluded.put(entry.getKey(),entry.getValue().stream().filter(id->!commonSet.contains(id)).toList());info.put("header_identities_outside_common_set",excluded);
            info.put("sample_policy","Original source intersects/aligns names;headers and exclusion/filter inputs are retained. This header intersection is not a claim about original internal residual or permutation order. No Java reordering or imputation.");
        }
        info.put("operation",operation);info.put("original_handling_acknowledged",true);return info;
    }
    private static List<String> unique(List<String> values,String label)throws IOException {
        if(values.isEmpty())throw new IOException("No identities in "+label);var seen=new HashSet<String>();for(String value:values){identity(value);if(!seen.add(value))throw new IOException("Duplicate literal identity in "+label);}return List.copyOf(values);
    }
    private static List<String> header(Path path,Map<String,Object> r,int skip)throws IOException {
        var result=new ArrayList<String>();boolean[] found={false};lines(path,r,line->{if(line.startsWith("##"))return true;String[] f=line.strip().split("\\s+");if(f.length<=skip)throw new IOException("Insufficient fixed columns in prepared header");for(int i=skip;i<f.length;i++)result.add(f[i]);found[0]=true;return false;});if(!found[0])throw new IOException("Missing prepared header");return unique(result,"prepared sample header");
    }
    static Map<String,Object> nativeRun(Path root,Map<String,Object> r)throws IOException {
        var inputs=new LinkedHashMap<String,QtltoolsWorkflowContracts.Input>();for(var input:QtltoolsWorkflowContracts.inputs(r))inputs.put(input.key(),input);
        var qtls=bed(root.resolve(inputs.get("qtl").captured()),r,true);var annotations=bed(root.resolve(inputs.get("annotations").captured()),r,false);var params=object(r.get("parameters"),"parameters");
        var result=QtltoolsFunctionalDensity.calculate(qtls,annotations,((Number)params.get("window")).intValue(),((Number)params.get("bin")).intValue(),(int)QtltoolsWorkflowContracts.limit(r,"maximum_bins"));
        var output=new StringBuilder();for(var bin:result.bins())output.append(bin.start()).append(' ').append(bin.end()).append(' ').append(bin.qtlCount()).append('\n');Files.writeString(root.resolve("outputs/result"),output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        return Map.of("qtl_rows",result.qtlRows(),"annotation_rows",result.annotationRows(),"chromosome_mapped_annotations",result.chromosomeMappedAnnotationRows(),"candidate_overlaps",result.candidateOverlaps(),"source_order_guarded_qtl_rows",result.sourceOrderGuardedQtlRows(),"engine","native-java");
    }
    static List<GenomicIntervals.Interval> bed(Path path,Map<String,Object> r,boolean qtl)throws IOException {
        var result=new ArrayList<GenomicIntervals.Interval>();lines(path,r,line->{if(line.startsWith("#"))return true;String[] f=line.strip().split("\\s+");if(f.length<(qtl?6:3))throw new IOException("Invalid BED column count");try{result.add(new GenomicIntervals.Interval(f[0],Long.parseLong(f[1]),Long.parseLong(f[2]),qtl?f[3]:"annotation-"+result.size(),qtl?f[5]:"."));}catch(RuntimeException error){throw new IOException("Require valid literal BED coordinates/strand;no repair",error);}return true;});return List.copyOf(result);
    }
    static void seal(Path root)throws IOException {
        var files=new TreeMap<String,Object>();for(String name:PeerWorkflowRuntime.footprint(root).keySet())if(!name.equals("capture-files.json"))files.put(name,FollowupSupport.hash(safe(root,name)));json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",files));
    }
}

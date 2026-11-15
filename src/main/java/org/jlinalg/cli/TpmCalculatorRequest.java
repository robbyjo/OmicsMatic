/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import org.jlinalg.sequence.TpmCalculator;

/** Explicit native counting choices; the raw original request is retained separately. */
final class TpmCalculatorRequest {
    private TpmCalculatorRequest() { }
    static final String PROFILE="native-java-tpmcalculator-0.0.4-gnu64";
    record Limits(long inputBytes,long bundleBytes,int files,long wallSeconds,int heapMiB) { }
    record Input(String name,String member,String original) { }
    record Request(String annotation,List<Input> samples,TpmCalculator.Options options,boolean extended,boolean all,Limits limits) {
        Request { samples=List.copyOf(samples); }
        Map<String,Object> effective() {
            var rows=new ArrayList<Object>();for(Input s:samples)rows.add(Map.of("name",s.name,"bam",s.member));
            var p=new LinkedHashMap<String,Object>();p.put("schema",1);p.put("profile",PROFILE);p.put("annotation","inputs/annotation.gtf");p.put("samples",rows);
            p.put("options",Map.of("gene_key",options.geneKey(),"transcript_key",options.transcriptKey(),"intron_cutoff",options.intronCutoff(),
                "minimum_mapq",options.minimumMapq(),"minimum_overlap",options.minimumOverlap(),"properly_paired_only",options.properlyPairedOnly(),"extended_output",extended,"all_features",all));
            p.put("limits",Map.of("input_bytes",limits.inputBytes,"bundle_bytes",limits.bundleBytes,"files",limits.files,"wall_seconds",limits.wallSeconds,"heap_mib",limits.heapMiB));return p;
        }
    }
    static Object parse(byte[] raw,int maximum)throws IOException {
        return NextflowParameters.parse(NextflowFiles.utf8(raw),maximum,Math.max(100,maximum));
    }
    static Request read(Object value,boolean captured)throws IOException {
        Map<?,?> m=NextflowFiles.object(value);NextflowFiles.keys(m,"schema","profile","annotation","samples","options","limits");
        if(integer(m,"schema",1,1)!=1||!PROFILE.equals(text(m,"profile")))throw new IOException("Require explicit schema1 and "+PROFILE);
        String annotation=text(m,"annotation");if(annotation.isEmpty())throw new IOException("Empty GTF path");
        if(captured&&!annotation.equals("inputs/annotation.gtf"))throw new IOException("Recorded GTF member differs from the native profile");
        var samples=new ArrayList<Input>();var names=new HashSet<String>();
        for(Object item:NextflowFiles.list(m.get("samples"))) {
            Map<?,?> row=NextflowFiles.object(item);NextflowFiles.keys(row,"name","bam");String name=text(row,"name"),bam=text(row,"bam");
            if(name.isEmpty()||bam.isEmpty()||!names.add(name))throw new IOException("Require nonempty paths and distinct exact sample names");
            String member=String.format(java.util.Locale.ROOT,"inputs/sample-%06d.bam",samples.size()+1);
            if(captured&&!bam.equals(member))throw new IOException("Recorded sample member/order differs from the native profile");
            samples.add(new Input(name,member,bam));
        }
        if(samples.isEmpty())throw new IOException("At least one explicit BAM sample is required");
        Map<?,?> p=NextflowFiles.object(m.get("options"));NextflowFiles.keys(p,"gene_key","transcript_key","intron_cutoff","minimum_mapq","minimum_overlap","properly_paired_only","extended_output","all_features");
        TpmCalculator.Options options;
        try { options=new TpmCalculator.Options(text(p,"gene_key"),text(p,"transcript_key"),(int)integer(p,"intron_cutoff",0,Integer.MAX_VALUE),
                (int)integer(p,"minimum_mapq",0,65535),(int)integer(p,"minimum_overlap",0,65535),bool(p,"properly_paired_only")); }
        catch(IllegalArgumentException e){throw new IOException("Invalid native TPMCalculator counting choices",e);}
        Map<?,?> l=NextflowFiles.object(m.get("limits"));NextflowFiles.keys(l,"input_bytes","bundle_bytes","files","wall_seconds","heap_mib");
        var limits=new Limits(integer(l,"input_bytes",1,Long.MAX_VALUE),integer(l,"bundle_bytes",1,Long.MAX_VALUE),(int)integer(l,"files",1,Integer.MAX_VALUE),
            integer(l,"wall_seconds",1,Integer.MAX_VALUE),(int)integer(l,"heap_mib",64,Integer.MAX_VALUE));
        return new Request(annotation,samples,options,bool(p,"extended_output"),bool(p,"all_features"),limits);
    }
    static String text(Map<?,?> m,String key)throws IOException{return NextflowFiles.text(m.get(key));}
    static long integer(Map<?,?> m,String key,long lo,long hi)throws IOException{return NextflowFiles.integer(m.get(key),lo,hi);}
    static boolean bool(Map<?,?> m,String key)throws IOException{if(!(m.get(key) instanceof Boolean b))throw new IOException("Require a boolean "+key);return b;}
    static Path original(Path request,String value)throws IOException {
        try {Path p=Path.of(value);return (p.isAbsolute()?p:request.getParent().resolve(p)).toAbsolutePath().normalize();}
        catch(java.nio.file.InvalidPathException e){throw new IOException("Invalid local input path",e);}
    }
}

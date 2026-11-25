/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit original-edition conversion contract; original paths stay opaque in portable readback. */
final class CrossmapRequest {
    private CrossmapRequest() { }
    record Spec(Map<?,?> document,String edition,String format,String input,String chain,String fasta,
            String inputEncoding,String chainEncoding,String chromStyle,boolean compareAlleles,
            String sourceBuild,String targetBuild,String unmappedPolicy,NextflowProcess.Limits process,
            long inputBytes,long outputBytes,int records,int blockBytes) {
        String inputMember(){return "inputs/input."+(format.equals("bigwig")?"bw":format)+(inputEncoding.equals("gzip")?".gz":"");}
        String chainMember(){return "inputs/mapping.chain"+(chainEncoding.equals("gzip")?".gz":"");}
        String fastaMember(){return "inputs/reference.fa";}
        String outputMember(){return "outputs/mapped"+(format.equals("bigwig")?"":"."+format);}
        Map<String,String> originals(){var result=new LinkedHashMap<String,String>();result.put(inputMember(),input);result.put(chainMember(),chain);if(fasta!=null)result.put(fastaMember(),fasta);return result;}
    }
    static Spec read(Path file,boolean live)throws IOException{return parse(object(json(file,1048576)),live);}
    static Spec parse(Map<?,?> source,boolean live)throws IOException {
        String edition=text(source.get("edition")),format=text(source.get("format"));
        if(!Set.of("0.5.2","0.6.3").contains(edition))throw new CrossmapRuntime.Unavailable("Exact CrossMap edition unavailable: "+edition+"; select an explicitly qualified original edition");
        if(!Set.of("bed","vcf","bigwig").contains(format))throw new CrossmapRuntime.Unavailable("This adapter has no qualified original "+format+" conversion contract; dependent analysis skipped");
        var expected=new HashSet<>(Set.of("schema_version","edition","format","input","chain","input_encoding","chain_encoding","chrom_id_style","source_build","target_build","unmapped_policy","process","capture"));if(format.equals("vcf"))expected.addAll(Set.of("target_fasta","compare_alleles"));
        if(!source.keySet().equals(expected))throw new IOException("Missing/unknown CrossMap request fields: "+source.keySet());integer(source.get("schema_version"),1,1);
        String input=original(source.get("input"),live),chain=original(source.get("chain"),live),fasta=format.equals("vcf")?original(source.get("target_fasta"),live):null;
        String encoding=text(source.get("input_encoding")),chainEncoding=text(source.get("chain_encoding")),style=text(source.get("chrom_id_style"));
        require((format.equals("bigwig")?Set.of("binary"):Set.of("plain","gzip")).contains(encoding)&&Set.of("plain","gzip").contains(chainEncoding),"Unsupported or ambiguous input/chain encoding");
        if(edition.equals("0.5.2")&&!style.equals("source-default"))throw new CrossmapRuntime.Unavailable("Original0.5.2 has no0.6.3 --chromid option; select source-default or another explicit qualified edition");
        require(!edition.equals("0.6.3")||Set.of("a","s","l").contains(style),"Original0.6.3 requires explicit chrom_id_style a,s or l");
        boolean compare=true;if(format.equals("vcf")){if(!(source.get("compare_alleles")instanceof Boolean value))throw new IOException("compare_alleles must be an explicit boolean");compare=value;}
        String from=label(source.get("source_build")),to=label(source.get("target_build")),policy=text(source.get("unmapped_policy"));require(Set.of("retain","error").contains(policy),"Choose explicit unmapped_policy retain or error; no default negative finding is inferred");
        var p=object(source.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        NextflowProcess.Limits limits;
        try{limits=new NextflowProcess.Limits(number(p,"wall_millis"),number(p,"address_space_bytes"),number(p,"cpu_seconds"),number(p,"file_bytes"),small(p,"open_files"),number(p,"workspace_bytes"),small(p,"file_count"),small(p,"processes"),small(p,"threads"),number(p,"sampled_rss_bytes"),small(p,"monitor_millis"));}
        catch(IllegalArgumentException e){throw new IOException("Invalid explicit CrossMap process capacities",e);}
        var c=object(source.get("capture"));keys(c,"input_bytes","output_bytes","records","bigwig_block_bytes");long inputBytes=integer(c.get("input_bytes"),1,1000000000000L),outputBytes=integer(c.get("output_bytes"),1,1000000000000L);int records=(int)integer(c.get("records"),1,10000000),block=(int)integer(c.get("bigwig_block_bytes"),24,67108864);
        try{require(Math.addExact(Math.addExact(inputBytes,outputBytes),16777216L)<=limits.workspaceBytes(),"workspace_bytes must retain complete selected input/output capacities and16MiB metadata reserve");}catch(ArithmeticException e){throw new IOException("CrossMap capture capacity overflow",e);}
        return new Spec(Collections.unmodifiableMap(new LinkedHashMap<>(source)),edition,format,input,chain,fasta,encoding,chainEncoding,style,compare,from,to,policy,limits,inputBytes,outputBytes,records,block);
    }
    private static long number(Map<?,?> map,String key)throws IOException{return integer(map.get(key),1,Long.MAX_VALUE);}
    private static int small(Map<?,?> map,String key)throws IOException{return(int)integer(map.get(key),1,Integer.MAX_VALUE);}
    private static String label(Object value)throws IOException {String s=text(value);require(!s.isBlank()&&s.length()<=4096&&s.chars().noneMatch(c->c<32||c==127),"Require explicit nonempty assembly labels; labels do not verify assembly identity");return s;}
    private static String original(Object value,boolean live)throws IOException {
        String s=text(value);require(!s.isBlank()&&s.length()<=32768&&s.indexOf(0)<0,"Require an explicit original local file path");
        if(live){Path p=Path.of(s);require(p.isAbsolute()&&p.normalize().equals(p)&&p.toRealPath().equals(p),"Require canonical absolute original file paths; no URL download or path repair during conversion");}return s;
    }
    private static void require(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FavorVcfAudit;
import org.jlinalg.genetics.FavorVcfReader;

/** Strict bounded admission of owned read-only GDS audits; never repairs source nodes. */
final class FavorGdsAudit {
    private static final String ANNOTATION="annotation/info/FunctionalAnnotation";
    private static final Set<String> STRING_ROLES=Set.of("sample.id","chromosome","$ref","$alt","annotation/id");
    private static final Set<String> INTEGER_ROLES=Set.of("variant.id","position","genotype","phase");
    record Limits(int bytes,int nodes,long nodeValues,long canonicalBytes) {
        Limits {if(bytes<1||nodes<1||nodeValues<1||nodeValues>4_503_599_627_370_496L||canonicalBytes<1||canonicalBytes>4_503_599_627_370_496L)throw new IllegalArgumentException("Invalid complete FAVOR GDS audit capacity");}
    }
    record Node(String storage,String trait,String type,boolean array,boolean sparse,List<Integer> dimensions,
            String encoder,String compression,boolean hidden,String parameters,String attributes,long values,String data) {
        Node {dimensions=List.copyOf(dimensions);}
    }
    record Snapshot(FavorVcfReader.Mode mode,int variants,int samples,Map<String,FavorVcfAudit.Digest> digests,
            long canonicalBytes,String rootAttributes,Map<String,Node> nodes,Map<String,String> runtime) {
        Snapshot {digests=Map.copyOf(digests);nodes=Map.copyOf(nodes);runtime=Map.copyOf(runtime);}
    }
    record Preservation(int originalNodes,int originalHiddenNodes,int newAnnotationNodes,long genotypeAlleles,long phaseValues) { }
    private FavorGdsAudit() { }

    static Snapshot read(Path file,Limits limits,Runnable check)throws IOException {
        check.run();Map<?,?> m=AdmixtureInputs.json(file,limits.bytes());
        keys(m,Set.of("schema","mode","variants","samples","digests","canonical_bytes","root_attributes_sha256","all_nodes_including_hidden","runtime"));
        if(!FavorVcfAudit.SCHEMA.equals(text(m,"schema",false)))throw new IOException("Unsupported FAVOR GDS canonical value schema");
        FavorVcfReader.Mode mode;
        try{mode=FavorVcfReader.Mode.valueOf(text(m,"mode",false));}catch(IllegalArgumentException invalid){throw new IOException("Unknown FAVOR GDS audit mode",invalid);}
        int variants=(int)whole(m,"variants",1,Integer.MAX_VALUE),samples=(int)whole(m,"samples",0,1_000_000);
        if(mode==FavorVcfReader.Mode.DIPLOID_GT&&samples<1||mode==FavorVcfReader.Mode.VARIANTS_ONLY&&samples!=0)throw new IOException("GDS scientific mode and sample population differ");
        Map<?,?> rawHashes=object(m.get("digests"));var roles=new java.util.HashSet<>(STRING_ROLES);roles.addAll(Set.of("variant.id","position"));
        if(mode==FavorVcfReader.Mode.DIPLOID_GT)roles.addAll(Set.of("genotype","phase"));keys(rawHashes,roles);
        var digests=new LinkedHashMap<String,FavorVcfAudit.Digest>();long minimumBytes=0;
        for(String role:roles) {
            check.run();Map<?,?> raw=object(rawHashes.get(role));keys(raw,Set.of("kind","values","sha256"));
            FavorVcfAudit.Kind kind=INTEGER_ROLES.contains(role)?FavorVcfAudit.Kind.INT32_LE:FavorVcfAudit.Kind.UTF8_LENGTH_INT32_LE;
            if(!kind.name().equals(text(raw,"kind",false)))throw new IOException("Incorrect FAVOR GDS canonical value kind: "+role);
            long expected=role.equals("sample.id")?samples:role.equals("genotype")?2L*samples*variants:role.equals("phase")?(long)samples*variants:variants;
            long count=whole(raw,"values",0,4_503_599_627_370_496L);if(count!=expected)throw new IOException("Incomplete FAVOR GDS canonical value population: "+role);
            if(count>(limits.canonicalBytes()-minimumBytes)/4)throw new IOException("Complete GDS canonical value byte capacity exceeded");minimumBytes+=4*count;
            digests.put(role,new FavorVcfAudit.Digest(kind,count,hash(raw,"sha256")));
        }
        long canonical=whole(m,"canonical_bytes",minimumBytes,limits.canonicalBytes());
        Map<?,?> rawNodes=object(m.get("all_nodes_including_hidden"));
        if(rawNodes.isEmpty()||rawNodes.size()>limits.nodes())throw new IOException("Complete FAVOR GDS node population capacity exceeded");
        var nodes=new LinkedHashMap<String,Node>();
        for(var entry:rawNodes.entrySet()) {
            check.run();if(!(entry.getKey() instanceof String name)||name.isEmpty()||name.length()>4096||name.codePoints().anyMatch(Character::isISOControl)||List.of(name.split("/",-1)).stream().anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("Invalid exact FAVOR GDS node name");
            Map<?,?> raw=object(entry.getValue());keys(raw,Set.of("storage","trait","type","is_array","is_sparse","dimensions","encoder","compression","hidden","parameter_sha256","attributes_sha256","values","data_sha256"));
            boolean array=flag(raw,"is_array");Object dimensions=raw.get("dimensions");
            if(!(dimensions instanceof List<?> list)||list.size()>16||array&&list.isEmpty()||!array&&!list.isEmpty())throw new IOException("Invalid FAVOR GDS node dimensions: "+name);
            var shape=new ArrayList<Integer>();long population=array?1:0;
            for(Object value:list){long d=whole(value,0,Integer.MAX_VALUE);shape.add((int)d);if(population>0&&d>limits.nodeValues()/population)throw new IOException("Complete GDS node value capacity exceeded: "+name);population*=d;}
            long count=whole(raw,"values",0,limits.nodeValues());if(count!=population)throw new IOException("GDS node count differs from its complete dimensions: "+name);
            String data=array?hash(raw,"data_sha256"):null;if(!array&&raw.get("data_sha256")!=null)throw new IOException("Nonarray GDS node must not invent a data digest");
            nodes.put(name,new Node(text(raw,"storage",true),text(raw,"trait",true),text(raw,"type",false),array,flag(raw,"is_sparse"),shape,nullable(raw,"encoder"),nullable(raw,"compression"),flag(raw,"hidden"),hash(raw,"parameter_sha256"),hash(raw,"attributes_sha256"),count,data));
        }
        for(String name:nodes.keySet()){int slash=name.lastIndexOf('/');if(slash>=0&&!nodes.containsKey(name.substring(0,slash)))throw new IOException("GDS audit omits an original parent node: "+name);}
        for(String name:List.of("sample.id","variant.id","chromosome","position","allele","annotation/id"))if(!nodes.containsKey(name))throw new IOException("GDS audit omits required original identity node: "+name);
        shape(nodes,"sample.id",List.of(samples));shape(nodes,"variant.id",List.of(variants));
        if(mode==FavorVcfReader.Mode.DIPLOID_GT){shape(nodes,"genotype/data",List.of(2,samples,variants));shape(nodes,"phase/data",List.of(samples,variants));}
        else if(nodes.containsKey("genotype/data")||nodes.containsKey("phase/data"))throw new IOException("Variant-only GDS must not invent genotype or phase arrays");
        Map<?,?> rawRuntime=object(m.get("runtime"));keys(rawRuntime,Set.of("R","gdsfmt","SeqArray","digest"));var runtime=new LinkedHashMap<String,String>();
        for(String name:List.of("R","gdsfmt","SeqArray","digest"))runtime.put(name,text(rawRuntime,name,false));
        return new Snapshot(mode,variants,samples,digests,canonical,hash(m,"root_attributes_sha256"),nodes,runtime);
    }
    static void matches(FavorVcfAudit.Result expected,Snapshot actual)throws IOException {
        if(!expected.input().complete()||!FavorVcfAudit.SCHEMA.equals(expected.schema())||expected.mode()!=actual.mode()||expected.variants()!=actual.variants()||expected.samples()!=actual.samples()||!expected.digests().equals(actual.digests()))throw new IOException("GDS conversion differs from complete native VCF identity,allele,genotype,missingness or phase audit");
    }
    static Preservation preserved(FavorVcfAudit.Result expected,Snapshot before,Snapshot after)throws IOException {
        matches(expected,before);matches(expected,after);
        if(!before.rootAttributes().equals(after.rootAttributes())||!before.runtime().equals(after.runtime()))throw new IOException("Original GDS root attributes or audit runtime changed");
        if(before.nodes().keySet().stream().anyMatch(FavorGdsAudit::annotation))throw new IOException("Original GDS already contains FAVOR annotation;require an unannotated protected working copy");
        int hidden=0,added=0;
        for(var node:before.nodes().entrySet()) {
            if(!node.getValue().equals(after.nodes().get(node.getKey())))throw new IOException("Original GDS node,attribute,type,shape,storage or complete value digest changed: "+node.getKey());
            if(node.getValue().hidden())hidden++;
        }
        for(String name:after.nodes().keySet())if(!before.nodes().containsKey(name)){if(!annotation(name))throw new IOException("Unexpected new GDS node outside declared FAVOR annotation: "+name);added++;}
        if(added<2||!after.nodes().containsKey(ANNOTATION)||!after.nodes().containsKey(ANNOTATION+"/FunctionalAnnotation"))throw new IOException("Missing new source FAVOR annotation subtree");
        return new Preservation(before.nodes().size(),hidden,added,expected.digests().containsKey("genotype")?expected.digests().get("genotype").values():0,expected.digests().containsKey("phase")?expected.digests().get("phase").values():0);
    }
    private static boolean annotation(String name){return name.equals(ANNOTATION)||name.startsWith(ANNOTATION+"/");}
    private static void shape(Map<String,Node> nodes,String name,List<Integer> shape)throws IOException {Node node=nodes.get(name);if(node==null||!node.array()||!node.dimensions().equals(shape))throw new IOException("Missing or mismatched complete GDS dimensions: "+name);}
    private static void keys(Map<?,?> value,Set<String> expected)throws IOException {if(!value.keySet().equals(expected))throw new IOException("Unknown or missing FAVOR GDS audit fields;expected "+expected);}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require FAVOR GDS audit object");return map;}
    private static String text(Map<?,?> map,String key,boolean empty)throws IOException {if(!(map.get(key) instanceof String s)||!empty&&s.isEmpty()||s.length()>4096||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid FAVOR GDS audit text: "+key);return s;}
    private static String nullable(Map<?,?> map,String key)throws IOException {return map.get(key)==null?null:text(map,key,true);}
    private static String hash(Map<?,?> map,String key)throws IOException {String value=text(map,key,false);if(!value.matches("[a-f0-9]{64}"))throw new IOException("Invalid FAVOR GDS SHA256: "+key);return value;}
    private static boolean flag(Map<?,?> map,String key)throws IOException {if(!(map.get(key) instanceof Boolean value))throw new IOException("Invalid FAVOR GDS boolean: "+key);return value;}
    private static long whole(Map<?,?> map,String key,long min,long max)throws IOException {return whole(map.get(key),min,max);}
    private static long whole(Object value,long min,long max)throws IOException {if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IOException("Invalid complete FAVOR GDS population/count");return (long)n.doubleValue();}
}

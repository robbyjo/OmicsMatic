/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.MafftSequences;
import org.jlinalg.sequence.Pal2Nal;

/** Typed closed native recipes; unresolved editions/capabilities never choose a fallback. */
final class Pal2NalRecipe {
    private Pal2NalRecipe() { }
    static final String PROFILE="native-pal2nal-v14-matching";
    static final Set<String> PROFILES=Set.of(PROFILE,"native-pal2nal-v14.1-matching");
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }
    enum Format { FASTA, CLUSTAL }
    enum Selection { NONE, CLUSTAL_HASH, MASK_FILE }
    record Limits(long inputBytes,MafftSequences.Limits parser,Pal2Nal.Limits engine,long bundleBytes,int bundleFiles) {
        Limits {
            if(inputBytes<1||inputBytes>1073741824L||bundleBytes<1048576||bundleBytes>1099511627776L||bundleFiles<32||bundleFiles>100000)
                throw new IllegalArgumentException("Invalid complete PAL2NAL file/bundle capacities");
        }
        static Limits defaults(){return new Limits(268435456,MafftSequences.Limits.defaults(),Pal2Nal.Limits.defaults(),1073741824,512);}
        Limits forHeap()throws IOException {
            return atHeap(Runtime.getRuntime().maxMemory());
        }
        Limits atHeap(long heap)throws IOException {
            if(heap<33554432)throw new IOException("PAL2NAL needs a Java heap of at least32MiB; increase -Xmx and retry to a NEW path");
            long memory=heap/5;
            return new Limits(inputBytes,new MafftSequences.Limits(parser.sequences(),parser.sequenceCharacters(),parser.cells(),parser.decodedCharacters(),parser.lineCharacters(),parser.headerCharacters(),Math.min(parser.estimatedRetainedBytes(),memory)),
                    new Pal2Nal.Limits(engine.sequences(),engine.proteinColumns(),engine.dnaCharactersPerRow(),engine.inputCells(),engine.outputCells(),engine.workSteps(),Math.min(engine.estimatedRetainedBytes(),memory)),bundleBytes,bundleFiles);
        }
    }
    record Spec(Path source,String profile,Path protein,Format format,MafftSequences.Headers proteinHeaders,List<Path> dna,
                MafftSequences.Headers dnaHeaders,Selection selection,Path mask,Pal2Nal.Options options,Limits limits) {
        Spec {dna=List.copyOf(dna);}
        List<Path> inputs(){var paths=new ArrayList<Path>();paths.add(protein);paths.addAll(dna);if(mask!=null)paths.add(mask);return paths;}
    }
    static Spec read(Path source)throws IOException {
        byte[] raw;try(var in=java.nio.file.Files.newInputStream(source)){raw=in.readNBytes(2097153);}
        if(raw.length>2097152)throw new IOException("PAL2NAL recipe exceeds2MiB");return parse(raw,source);
    }
    static Spec parse(byte[] raw,Path source)throws IOException {
        Map<?,?> r=Pal2NalJson.object(raw);
        for(String unsupported:List.of("partial","reverse_complement","splice_alignment","automatic_codon_table"))if(r.containsKey(unsupported))throw new Unavailable("Requested native PAL2NAL capability is unavailable: "+unsupported+"; skip this analysis instead of discarding the option");
        keys(r,Set.of("schema_version","profile","protein","dna","selection","options","limits"),Set.of("limits"));
        String profile=text(r,"profile");
        if(integer(r.get("schema_version"),1,Integer.MAX_VALUE)!=1||!PROFILES.contains(profile))throw new Unavailable("Requested PAL2NAL edition/profile is unresolved or unavailable; choose an explicitly supported native matching profile, not an inferred replacement or the current Python port");
        Map<?,?> p=object(r.get("protein")),d=object(r.get("dna")),s=object(r.get("selection")),o=object(r.get("options"));
        keys(p,Set.of("path","format","headers"),Set.of());keys(d,Set.of("paths","headers"),Set.of());
        keys(s,Set.of("mode","path"),Set.of("path"));
        var format=enumeration(Format.class,text(p,"format"));var selection=enumeration(Selection.class,text(s,"mode"));
        if((selection==Selection.MASK_FILE)!=s.containsKey("path")||selection==Selection.CLUSTAL_HASH&&format!=Format.CLUSTAL)throw new IOException("PAL2NAL selection mode, format and mask-file roles conflict");
        if(!(d.get("paths") instanceof List<?> paths)||paths.isEmpty()||paths.size()>128)throw new IOException("Require1..128 complete PAL2NAL DNA FASTA files");
        var dna=new ArrayList<Path>();for(Object path:paths){if(!(path instanceof String name)||name.isBlank())throw new IOException("DNA paths must be nonblank strings");dna.add(resolve(source,name));}
        keys(o,Set.of("codon_table","pairing","matching","placement","gap_stop_filter","remove_mismatch_columns","normalize_documented_symbols","normalize_tfastx","minimum_verified_residues","minimum_verified_fraction"),Set.of());
        int table=(int)integer(o.get("codon_table"),1,1000);if(!Pal2Nal.supportedCodonTables().contains(table))throw new Unavailable("Native v14 genetic-code table unavailable: "+table+"; no replacement code is selected");
        Pal2Nal.Options options=new Pal2Nal.Options(table,enumeration(Pal2Nal.Pairing.class,text(o,"pairing")),enumeration(Pal2Nal.Matching.class,text(o,"matching")),
                enumeration(Pal2Nal.Placement.class,text(o,"placement")),enumeration(Pal2Nal.GapStopFilter.class,text(o,"gap_stop_filter")),bool(o,"remove_mismatch_columns"),null,
                bool(o,"normalize_documented_symbols"),bool(o,"normalize_tfastx"),(int)integer(o.get("minimum_verified_residues"),0,Integer.MAX_VALUE),fraction(o.get("minimum_verified_fraction")));
        Limits limits=r.containsKey("limits")?limits(object(r.get("limits"))):Limits.defaults();
        return new Spec(source,profile,resolve(source,text(p,"path")),format,enumeration(MafftSequences.Headers.class,text(p,"headers")),dna,enumeration(MafftSequences.Headers.class,text(d,"headers")),selection,s.containsKey("path")?resolve(source,text(s,"path")):null,options,limits);
    }
    static Limits limits(Map<?,?> map)throws IOException {
        keys(map,Set.of("input_bytes","parser","engine","bundle_bytes","bundle_files"),Set.of());
        try{return new Limits(integer(map.get("input_bytes"),1,1073741824L),record(MafftSequences.Limits.class,object(map.get("parser"))),record(Pal2Nal.Limits.class,object(map.get("engine"))),integer(map.get("bundle_bytes"),1048576,1099511627776L),(int)integer(map.get("bundle_files"),32,100000));}
        catch(IllegalArgumentException e){throw new IOException("Invalid PAL2NAL capacities",e);}
    }
    static <T> T record(Class<T> type,Map<?,?> map)throws IOException {
        // The only reflected classes are compile-time-selected primitive capacity records.
        var components=type.getRecordComponents();var names=new java.util.HashSet<String>();for(var part:components)names.add(part.getName());keys(map,names,Set.of());
        var classes=new Class<?>[components.length];var args=new Object[components.length];
        for(int i=0;i<components.length;i++){classes[i]=components[i].getType();long n=integer(map.get(components[i].getName()),1,classes[i]==int.class?Integer.MAX_VALUE:Long.MAX_VALUE);if(classes[i]==int.class)args[i]=Integer.valueOf((int)n);else args[i]=Long.valueOf(n);}
        try{return type.getDeclaredConstructor(classes).newInstance(args);}catch(ReflectiveOperationException e){throw new IOException("Invalid PAL2NAL primitive capacity record",e);}
    }
    @SuppressWarnings("unchecked") static byte[] replay(byte[] original)throws IOException {
        var r=new LinkedHashMap<>((Map<String,Object>)Pal2NalJson.object(original));
        var p=new LinkedHashMap<>((Map<String,Object>)object(r.get("protein")));p.put("path","inputs/protein.original");r.put("protein",p);
        var d=new LinkedHashMap<>((Map<String,Object>)object(r.get("dna")));if(!(d.get("paths") instanceof List<?> paths))throw new IOException("DNA path list required");
        var names=new ArrayList<String>();for(int i=0;i<paths.size();i++)names.add(String.format(java.util.Locale.ROOT,"inputs/dna-%04d.fasta",i+1));d.put("paths",names);r.put("dna",d);
        var s=new LinkedHashMap<>((Map<String,Object>)object(r.get("selection")));if(s.containsKey("path"))s.put("path","inputs/selection.mask");r.put("selection",s);return Pal2NalJson.bytes(r);
    }
    static Path resolve(Path source,String value){Path p=Path.of(value);return (p.isAbsolute()?p:source.toAbsolutePath().getParent().resolve(p)).toAbsolutePath().normalize();}
    static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("PAL2NAL object required");return map;}
    static String text(Map<?,?> map,String key)throws IOException {if(!(map.get(key) instanceof String text)||text.isBlank())throw new IOException("PAL2NAL nonblank string required: "+key);return text;}
    static boolean bool(Map<?,?> map,String key)throws IOException {if(!(map.get(key) instanceof Boolean value))throw new IOException("PAL2NAL boolean required: "+key);return value;}
    static long integer(Object value,long low,long high)throws IOException {if(!(value instanceof Long number)||number<low||number>high)throw new IOException("PAL2NAL bounded literal integer required");return number;}
    static double fraction(Object value)throws IOException {if(!(value instanceof Long||value instanceof Double))throw new IOException("PAL2NAL finite fraction required");double number=((Number)value).doubleValue();if(!Double.isFinite(number)||number<0||number>1)throw new IOException("PAL2NAL verification fraction must be in[0,1]");return number;}
    static <E extends Enum<E>> E enumeration(Class<E> type,String value)throws IOException {try{return Enum.valueOf(type,value);}catch(IllegalArgumentException e){throw new IOException("Unknown PAL2NAL "+type.getSimpleName()+": "+value,e);}}
    static void keys(Map<?,?> map,Set<String> all,Set<String> optional)throws IOException {if(!all.containsAll(map.keySet())||!map.keySet().containsAll(all.stream().filter(key->!optional.contains(key)).toList()))throw new IOException("Unknown or missing PAL2NAL keys: "+map.keySet());}
}

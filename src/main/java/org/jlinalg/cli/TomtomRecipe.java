/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.MemeMotifInputs;
import org.jlinalg.sequence.MemeXml;
import org.jlinalg.sequence.TomtomResults;

/** Explicit Tomtom scientific settings and capacities; no opaque flag runner or inferred paper defaults. */
final class TomtomRecipe {
    private TomtomRecipe() { }
    static final String PROFILE="linux-x86_64-tomtom-5.5.9";
    enum Mode { TEXT, FULL_REPORT }
    enum Background { QUERY, FILE, UNIFORM }
    enum Threshold { QVALUE, EVALUE }
    private static final Set<String> DISTANCES=Set.of("ed","kullback","pearson","allr","sandelin","blic1","blic5","llr1","llr5");
    record Request(Path source,NextflowFiles.Pin sourcePin,String query,List<String> targets,String distance,
                   boolean reverseComplement,BigDecimal pseudocount,int minimumOverlap,boolean internal,boolean completeScores,
                   Threshold thresholdKind,BigDecimal threshold,Background background,String backgroundFile,Mode mode,Map<String,Long> capacities) {
        Request {targets=List.copyOf(targets);capacities=Map.copyOf(capacities);}
        long capacity(String key){Long value=capacities.get(key);if(value==null)throw new IllegalArgumentException("Undeclared Tomtom capacity: "+key);return value;}
        void verify(Runnable check)throws IOException {require(sourcePin.equals(NextflowFiles.pin(source,2097152,check)),"Original Tomtom recipe changed");}
        List<String> arguments(String executable,String capturedQuery,List<String> capturedTargets,String capturedBackground,String newOutput)throws IOException {
            nativePath(executable);nativePath(capturedQuery);require(capturedTargets.size()==targets.size(),"Complete prepared Tomtom database population differs");for(String target:capturedTargets)nativePath(target);
            require((background==Background.FILE)==(capturedBackground!=null),"Prepared Tomtom background role differs");
            var argv=new ArrayList<>(List.of(executable,"-dist",distance,"-motif-pseudo",pseudocount.toString(),"-min-overlap",Integer.toString(minimumOverlap),"-thresh",threshold.toString()));
            if(!reverseComplement)argv.add("-norc");if(internal)argv.add("-internal");if(!completeScores)argv.add("-incomplete-scores");if(thresholdKind==Threshold.EVALUE)argv.add("-evalue");
            if(background==Background.FILE){nativePath(capturedBackground);argv.addAll(List.of("-bfile",capturedBackground));}
            else if(background==Background.UNIFORM)argv.addAll(List.of("-bfile","--uniform--"));
            if(mode==Mode.TEXT){require(newOutput==null,"Text-only Tomtom must not request a report directory");argv.add("-text");}
            else{require(newOutput!=null,"Full Tomtom report requires a NEW output directory");nativePath(newOutput);argv.addAll(List.of("-o",newOutput));}
            argv.add(capturedQuery);argv.addAll(capturedTargets);return List.copyOf(argv);
        }
        MemeMotifInputs.Limits inputLimits(){return new MemeMotifInputs.Limits((int)capacity("input_bytes"),(int)capacity("decoded_characters"),(int)capacity("motifs"),(int)capacity("width"),capacity("matrix_values"),xmlLimits());}
        MemeXml.Limits xmlLimits(){return new MemeXml.Limits((int)capacity("input_bytes"),(int)capacity("decoded_characters"),(int)capacity("xml_elements"),(int)capacity("xml_depth"),(int)capacity("xml_attributes"));}
        TomtomResults.Limits outputLimits(){return new TomtomResults.Limits(xmlLimits(),(int)capacity("table_bytes"),(int)capacity("reported_rows"),(int)capacity("pairs"),capacity("matrix_values"));}
        RsemProcess.Limits processLimits()throws IOException {return process(capacities);}
    }
    static Request read(Path path,Runnable check)throws IOException {return read(path,check,false);}
    static Request readCaptured(Path path,Runnable check)throws IOException {return read(path,check,true);}
    private static Request read(Path source,Runnable check,boolean portable)throws IOException {
        var pin=NextflowFiles.pin(source,2097152,check);var raw=NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(source,2097152)),2097152,100000));
        NextflowFiles.keys(raw,"schema_version","profile","query","targets","distance","reverse_complement","pseudocount","minimum_overlap","internal","complete_scores","threshold","background","output_mode","limits");
        NextflowFiles.integer(raw.get("schema_version"),1,1);require(NextflowFiles.text(raw.get("profile")).equals(PROFILE),"Unsupported exact Tomtom profile; no automatic edition replacement");
        String query=path(raw.get("query"),portable);var targets=new ArrayList<String>();for(Object target:NextflowFiles.list(raw.get("targets")))targets.add(path(target,portable));
        require(!targets.isEmpty()&&targets.size()<=128,"Declare 1..128 complete Tomtom target database files");
        String distance=NextflowFiles.text(raw.get("distance"));require(DISTANCES.contains(distance),"Unsupported original Tomtom distance");
        var t=NextflowFiles.object(raw.get("threshold"));NextflowFiles.keys(t,"kind","value");Threshold thresholdKind=choice(Threshold.class,t.get("kind"));BigDecimal threshold=decimal(t.get("value"),new BigDecimal("9007199254740991"));
        require(thresholdKind!=Threshold.QVALUE||threshold.compareTo(BigDecimal.ONE)<=0,"Tomtom q-value threshold must be at most one");
        var bg=NextflowFiles.object(raw.get("background"));NextflowFiles.keys(bg,"source","path");Background background=choice(Background.class,bg.get("source"));String file=bg.get("path")==null?null:path(bg.get("path"),portable);
        require((background==Background.FILE)==(file!=null),"Declare a background file exactly for FILE background selection");
        require(background==Background.QUERY||!Set.of("ed","kullback","pearson","sandelin").contains(distance),"Original Tomtom forbids -bfile for this distance; do not silently replace background settings");
        Request result=new Request(source,pin,query,targets,distance,bool(raw.get("reverse_complement")),decimal(raw.get("pseudocount"),new BigDecimal("1000000")),(int)NextflowFiles.integer(raw.get("minimum_overlap"),1,512),bool(raw.get("internal")),bool(raw.get("complete_scores")),thresholdKind,threshold,background,file,choice(Mode.class,raw.get("output_mode")),capacities(NextflowFiles.object(raw.get("limits"))));
        try{result.inputLimits();result.outputLimits();result.processLimits();}catch(IllegalArgumentException invalid){throw new IOException("Invalid complete Tomtom input/output capacities",invalid);}result.verify(check);return result;
    }
    private static Map<String,Long> capacities(Map<?,?> raw)throws IOException {
        Map<String,Long> defaults=Map.ofEntries(Map.entry("input_bytes",268435456L),Map.entry("decoded_characters",268435456L),Map.entry("motifs",100000L),Map.entry("width",512L),Map.entry("matrix_values",20000000L),
            Map.entry("xml_elements",2000000L),Map.entry("xml_depth",64L),Map.entry("xml_attributes",128L),Map.entry("table_bytes",268435456L),Map.entry("reported_rows",2000000L),Map.entry("pairs",2000000L),
            Map.entry("wall_seconds",86400L),Map.entry("cpu_seconds",86400L),Map.entry("address_space_bytes",17179869184L),Map.entry("file_bytes",1073741824L),Map.entry("open_files",4096L),Map.entry("workspace_bytes",17179869184L),
            Map.entry("files",100000L),Map.entry("processes",64L),Map.entry("threads",256L),Map.entry("sampled_rss_bytes",8589934592L),Map.entry("poll_millis",100L));
        require(defaults.keySet().containsAll(raw.keySet()),"Unknown Tomtom limit; no silent capacity substitution");var result=new LinkedHashMap<>(defaults);
        for(var entry:raw.entrySet())result.put(NextflowFiles.text(entry.getKey()),NextflowFiles.integer(entry.getValue(),1,1099511627776L));
        // Typed constructors enforce their tighter whole-population ranges before any launch.
        for(String key:List.of("input_bytes","decoded_characters","motifs","width","xml_elements","xml_depth","xml_attributes","table_bytes","reported_rows","pairs"))require(result.get(key)<=Integer.MAX_VALUE,"Tomtom int capacity overflow: "+key);
        process(result);return Map.copyOf(result);
    }
    private static RsemProcess.Limits process(Map<String,Long> v)throws IOException {
        try{return new RsemProcess.Limits(Math.multiplyExact(v.get("wall_seconds"),1000),v.get("address_space_bytes"),v.get("cpu_seconds"),v.get("file_bytes"),Math.toIntExact(v.get("open_files")),v.get("workspace_bytes"),Math.toIntExact(v.get("files")),Math.toIntExact(v.get("processes")),Math.toIntExact(v.get("threads")),v.get("sampled_rss_bytes"),Math.toIntExact(v.get("poll_millis")));}
        catch(IllegalArgumentException|ArithmeticException invalid){throw new IOException("Invalid explicit Tomtom process capacities",invalid);}
    }
    static void nativePath(String path)throws IOException {
        require(path!=null&&path.matches("/(?:[A-Za-z0-9._+-]+/)*[A-Za-z0-9._+-]+"),"Prepared Tomtom command paths require canonical absolute ASCII carriers without whitespace or shell metacharacters");
        for(String part:path.substring(1).split("/"))require(!part.equals(".")&&!part.equals(".."),"Prepared Tomtom path has a relative segment");
    }
    private static String path(Object raw,boolean portable)throws IOException {
        String text=NextflowFiles.text(raw);require(!text.isEmpty()&&text.length()<=32768&&text.chars().noneMatch(c->c<32||c==127),"Invalid original Tomtom path text");
        if(portable){require(text.startsWith("/")||text.matches("[A-Za-z]:[\\\\/].*"),"Captured Tomtom producer path must be absolute text");return text;}
        try{Path path=Path.of(text);require(path.isAbsolute()&&path.normalize().equals(path),"Declare normalized absolute original Tomtom paths");return text;}
        catch(java.nio.file.InvalidPathException invalid){throw new IOException("Invalid original Tomtom path",invalid);}
    }
    private static BigDecimal decimal(Object raw,BigDecimal maximum)throws IOException {
        if(!(raw instanceof NextflowParameters.NumberToken token)||token.text().length()>1024)throw new IOException("Require an exact bounded Tomtom numeric token");
        try{BigDecimal value=new BigDecimal(token.text());require(value.signum()>=0&&value.compareTo(maximum)<=0&&Math.abs((long)value.scale())<=4096,"Tomtom numeric value outside its bounded domain");
            require(value.signum()==0||Double.isFinite(value.doubleValue())&&value.doubleValue()>0,"Tomtom option cannot underflow or overflow the original double parser");return value;}
        catch(NumberFormatException invalid){throw new IOException("Invalid Tomtom option decimal",invalid);}
    }
    private static boolean bool(Object value)throws IOException {if(!(value instanceof Boolean result))throw new IOException("Declare explicit Tomtom boolean settings");return result;}
    private static <E extends Enum<E>> E choice(Class<E> type,Object value)throws IOException {try{return Enum.valueOf(type,NextflowFiles.text(value));}catch(IllegalArgumentException invalid){throw new IOException("Unsupported Tomtom "+type.getSimpleName(),invalid);}}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

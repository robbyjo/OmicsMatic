/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Explicit method, engine, image axes policy and analysis purpose; no paper-setting inference. */
final class CorrectBleach210Request {
    private CorrectBleach210Request() { }
    record Request(Path image,Path roi,Path provider,Path out,String method,String engine,String roiMode,
                   String purpose,String units,String provenance,boolean execute,Object baseline,int seconds,
                   int heap,long inputBytes,long outputBytes,long pixels,long comparisons,int files) { }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","version","imagej_version","mode","image","roi_mode","roi_file","provider_root","out","method","engine","purpose","input_units","input_provenance","axes_policy","execution_policy","baseline","timeout_seconds","heap_megabytes","maximum_input_bytes","maximum_output_bytes","maximum_decoded_pixels","maximum_native_comparisons_per_map","maximum_files");
        NextflowFiles.integer(m.get("schema_version"),1,1);require(text(m.get("version")).equals("2.1.0")&&text(m.get("imagej_version")).equals("1.53t")&&text(m.get("mode")).equals("RUN"),"Require original CorrectBleach2.1.0/ImageJ1.53t RUN profile; no edition substitution");
        String method=text(m.get("method")),engine=text(m.get("engine")),roi=text(m.get("roi_mode")),purpose=text(m.get("purpose"));require(Set.of("SIMPLE_RATIO","EXPONENTIAL_FIT","HISTOGRAM_MATCHING").contains(method),"Select one original correction method");require(Set.of("ORIGINAL","JAVA_NATIVE_HISTOGRAM210").contains(engine),"Select ORIGINAL or the explicit native histogram engine");require(engine.equals("ORIGINAL")||method.equals("HISTOGRAM_MATCHING"),"Native histogram engine applies only to HISTOGRAM_MATCHING");require(Set.of("IMAGE_ROI","NONE","ROI_FILE").contains(roi),"Select IMAGE_ROI, NONE or ROI_FILE");require(Set.of("INTENSITY_ANALYSIS","SEGMENTATION","VISUALIZATION").contains(purpose),"Declare the actual analysis purpose");require(!method.equals("HISTOGRAM_MATCHING")||!purpose.equals("INTENSITY_ANALYSIS"),"Upstream warns histogram matching is inappropriate for intensity quantification; choose an appropriate method, never relabel the purpose");require(text(m.get("axes_policy")).equals("ORIGINAL_SOURCE"),"Acknowledge ORIGINAL_SOURCE axis handling; no automatic channel/time reinterpretation");
        if(method.equals("SIMPLE_RATIO"))finite(m.get("baseline"));else require(!m.containsKey("baseline"),"Baseline is an explicit simple-ratio parameter only");require(roi.equals("ROI_FILE")==m.containsKey("roi_file"),"roi_file is required only for ROI_FILE");String policy=text(m.get("execution_policy"));require(Set.of("ALLOW","FORBID").contains(policy),"Declare ALLOW or FORBID execution policy");
        return new Request(path(parent,m.get("image")),roi.equals("ROI_FILE")?path(parent,m.get("roi_file")):null,path(parent,m.get("provider_root")),path(parent,m.get("out")),method,engine,roi,purpose,label(m.get("input_units")),label(m.get("input_provenance")),policy.equals("ALLOW"),m.get("baseline"),(int)number(m,"timeout_seconds",900,1,604800),(int)number(m,"heap_megabytes",2048,64,1048576),number(m,"maximum_input_bytes",1073741824,1,137438953472L),number(m,"maximum_output_bytes",2147483648L,1,137438953472L),number(m,"maximum_decoded_pixels",268435456,1,1000000000000L),number(m,"maximum_native_comparisons_per_map",500000000,1,1000000000000L),(int)number(m,"maximum_files",20000,32,1000000));
    }
    static Map<String,Object> object(Object x)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:NextflowFiles.object(x).entrySet())result.put(text(e.getKey()),e.getValue());return result;}
    static String text(Object x)throws IOException{return NextflowFiles.text(x);}
    static String label(Object x)throws IOException {String s=text(x);require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Require a nonempty literal label");return s;}
    static Path path(Path parent,Object x)throws IOException {Path p=Path.of(label(x));return (p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static long number(Map<String,Object> m,String key,long fallback,long min,long max)throws IOException{return m.containsKey(key)?NextflowFiles.integer(m.get(key),min,max):fallback;}
    static double finite(Object x)throws IOException {try{double v=Double.parseDouble(x instanceof NextflowParameters.NumberToken n?n.text():x instanceof Number?x.toString():"");require(Double.isFinite(v),"Require finite baseline in the declared image intensity units");return v;}catch(NumberFormatException e){throw new IOException("Require numeric finite baseline",e);}}
    static void allowed(Map<String,Object> m,String... keys)throws IOException {var valid=Set.of(keys);for(String k:m.keySet())require(valid.contains(k),"Unknown request field: "+k);}
    static void require(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
}

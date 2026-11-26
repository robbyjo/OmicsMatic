/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.alignment.SambambaStreamingBamAudit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit additive source designs; no executable expressions or input R objects. */
final class DiffbindRequest {
    private DiffbindRequest() { }
    static final Set<String> FACTORS=Set.of("Tissue","Factor","Condition","Treatment","Caller","Replicate");
    record Sample(String id,Map<String,Object> attributes,Path bam,Path peaks,Path control,String controlId) { }
    record Spec(Map<String,Object> value,Path runtime,List<Sample> samples,Map<?,?> settings,long inputBytes,
                long outputBytes,int files,long wallMillis,NextflowProcess.Limits process,SambambaStreamingBamAudit.Limits bamLimits) { }
    static Path absolute(Object v)throws IOException {
        Path p=Path.of(scalar(v));require(p.isAbsolute()&&p.normalize().equals(p),"Canonical absolute DiffBind path required");return p;
    }
    static String scalar(Object v)throws IOException {
        String s=text(v);require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Nonempty scalar text without controls required");return s;
    }
    static boolean bool(Object v)throws IOException {require(v instanceof Boolean,"Explicit boolean required");return (Boolean)v;}
    static double real(Object v,double low,double high)throws IOException {
        require(v instanceof Number,"Finite numeric setting required");double d=((Number)v).doubleValue();require(Double.isFinite(d)&&d>=low&&d<=high,"Numeric setting outside supported range");return d;
    }
    static Spec read(Path path)throws IOException {return parse(json(path,16777216));}
    static Spec parse(Object value)throws IOException {
        var v=object(value);keys(v,"schema_version","runtime","preparation","samples","settings","capacity","process");integer(v.get("schema_version"),1,1);scalar(v.get("preparation"));
        var samples=new ArrayList<Sample>();var ids=new HashSet<String>();
        for(Object item:list(v.get("samples"))) {
            var row=object(item);keys(row,"id","attributes","bam_reads","peaks","bam_control","control_id");
            String id=scalar(row.get("id"));require(ids.add(id),"Duplicate sample ID refused: "+id);
            var attrs=object(row.get("attributes"));require(FACTORS.containsAll(attrs.keySet())&&!attrs.containsKey("Caller"),"Declare source metadata factors; Caller is fixed by the BED peak caller");
            var copy=new TreeMap<String,Object>();for(var a:attrs.entrySet())copy.put(text(a.getKey()),a.getKey().equals("Replicate")?integer(a.getValue(),1,Integer.MAX_VALUE):scalar(a.getValue()));
            Path control=row.get("bam_control")==null?null:absolute(row.get("bam_control"));String controlId=row.get("control_id")==null?null:scalar(row.get("control_id"));
            require((control==null)==(controlId==null),"Control BAM and control ID must be specified together");
            samples.add(new Sample(id,Collections.unmodifiableMap(copy),absolute(row.get("bam_reads")),absolute(row.get("peaks")),control,controlId));
        }
        require(samples.size()>=4&&samples.size()<=10000,"Require the complete sample population, 4..10000 samples");
        Set<String> attributes=samples.get(0).attributes().keySet();require(samples.stream().allMatch(s->s.attributes().keySet().equals(attributes)),"Every sample requires the same metadata columns");
        var s=object(v.get("settings"));keys(s,"engine","seed","threshold","use_pvalue","mapq","fragment_size","min_overlap","summits","count_filter","remove_duplicates","scale_controls","subtract_controls","min_count","normalization","library_size","design","contrast_factor","numerator","denominator","min_members","test_fold");
        String engine=text(s.get("engine"));require(Set.of("DESeq2","edgeR").contains(engine),"Select original DESeq2 or edgeR");integer(s.get("seed"),0,Integer.MAX_VALUE);real(s.get("threshold"),0,1);bool(s.get("use_pvalue"));
        integer(s.get("mapq"),0,255);integer(s.get("fragment_size"),0,1000000);integer(s.get("min_overlap"),1,samples.size());integer(s.get("summits"),-1,1000000);real(s.get("count_filter"),0,Double.MAX_VALUE);bool(s.get("remove_duplicates"));bool(s.get("scale_controls"));
        boolean subtract=bool(s.get("subtract_controls"));require(!subtract||samples.stream().allMatch(x->x.control()!=null),"Control subtraction requires an explicit control for every sample");integer(s.get("min_count"),0,Integer.MAX_VALUE);real(s.get("test_fold"),0,1000);
        String normalization=text(s.get("normalization"));require(Set.of("LIB",engine.equals("DESeq2")?"RLE":"TMM").contains(normalization),"Select LIB or the engine's RLE/TMM normalization");require(Set.of("FULL","PEAKREADS").contains(text(s.get("library_size"))),"Select FULL or PEAKREADS library size");
        List<String> design=strings(s.get("design"));require(!design.isEmpty()&&new HashSet<>(design).size()==design.size()&&attributes.containsAll(design),"Design must be an ordered, unique list of present source factors");
        String factor=text(s.get("contrast_factor")),num=scalar(s.get("numerator")),den=scalar(s.get("denominator"));require(design.contains(factor)&&!num.equals(den),"Contrast factor must be in the explicit design with distinct levels");
        int members=(int)integer(s.get("min_members"),2,samples.size());
        require(samples.stream().filter(x->String.valueOf(x.attributes().get(factor)).equals(num)).count()>=members&&samples.stream().filter(x->String.valueOf(x.attributes().get(factor)).equals(den)).count()>=members,"Both contrasted levels require the declared minimum members");
        var c=object(v.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","bam_decoded_bytes","bam_records","bam_record_bytes");long input=integer(c.get("input_bytes"),28,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000);int files=(int)integer(c.get("files"),32,1000000);
        var bamLimits=new SambambaStreamingBamAudit.Limits(input,integer(c.get("bam_decoded_bytes"),12,1000000000000L),integer(c.get("bam_records"),0,Long.MAX_VALUE),(int)integer(c.get("bam_record_bytes"),32,1073741824),16777216,16777216);
        var p=object(v.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        var limits=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),4,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),1,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        var copy=new LinkedHashMap<String,Object>();v.forEach((k,x)->copy.put(k.toString(),x));return new Spec(Collections.unmodifiableMap(copy),absolute(v.get("runtime")),List.copyOf(samples),s,input,output,files,wall,limits,bamLimits);
    }
}

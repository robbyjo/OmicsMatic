/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.GemmaKinship;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit bounded PLINK/univariate contract. No option or author version inference. */
final class GemmaRequest {
    private GemmaRequest(){ }
    record Spec(Map<?,?> document,String edition,String engine,String mode,String build,
            Map<String,String> originals,GemmaKinship.Options numeric,int samples,int variants,
            long inputBytes,long outputBytes,long wallMillis,NextflowProcess.Limits process){
        boolean association(){return mode.startsWith("lmm");}
        boolean nativeEngine(){return engine.equals("native");}
    }
    static Spec read(Path path,boolean live)throws IOException{return parse(object(json(path,1048576)),live);}
    static Spec parse(Map<?,?> source,boolean live)throws IOException{
        keys(source,"schema_version","edition","engine","mode","genome_build","bed_prefix","sample_ids","phenotype","covariates","kinship","kinship_ids","optimizer","qc","capacity","process");integer(source.get("schema_version"),1,1);
        String edition=text(source.get("edition")),engine=text(source.get("engine")),mode=text(source.get("mode")),build=text(source.get("genome_build"));
        if(!Set.of("0.98.1","0.98.3","0.98.5").contains(edition))throw new GemmaRuntime.Unavailable("Exact GEMMA edition is unqualified: "+edition+". P176 reports literal 0.983; its release must be resolved separately, without assuming 0.98.3");
        require(Set.of("native","original").contains(engine),"Choose explicit native or original engine");
        if(!Set.of("gk1","gk2","lmm1","lmm2","lmm3","lmm4").contains(mode)||engine.equals("native")&&!mode.startsWith("gk"))throw new GemmaRuntime.Unavailable("No qualified GEMMA engine/mode contract: "+engine+"/"+mode+"; dependent analysis skipped");
        require(!build.isBlank()&&build.length()<=1024&&build.chars().noneMatch(c->c<32||c==127),"Require an explicit genome-build declaration; the label does not verify assembly identity");
        var originals=new LinkedHashMap<String,String>();String prefix=text(source.get("bed_prefix"));
        for(String suffix:List.of("bed","bim","fam"))originals.put("inputs/data."+suffix,original(prefix+"."+suffix,live));
        originals.put("inputs/sample-ids.tsv",original(source.get("sample_ids"),live));originals.put("inputs/phenotype.txt",original(source.get("phenotype"),live));
        if(source.get("covariates")!=null)originals.put("inputs/covariates.txt",original(source.get("covariates"),live));
        boolean association=mode.startsWith("lmm");
        if(association){originals.put("inputs/kinship.txt",original(source.get("kinship"),live));originals.put("inputs/kinship-ids.tsv",original(source.get("kinship_ids"),live));require("source-defaults-1e-5-1e5-10-regions".equals(source.get("optimizer")),"Select the explicit qualified source-default optimizer profile; it does not establish author-unreported settings");}
        else require(source.get("kinship")==null&&source.get("kinship_ids")==null&&source.get("optimizer")==null,"Kinship construction requires null kinship, kinship_ids and optimizer");
        var c=object(source.get("capacity"));keys(c,"samples","variants","input_bytes","output_bytes","numeric_bytes","multiply_adds","native_wall_millis");int samples=(int)integer(c.get("samples"),2,1000000),variants=(int)integer(c.get("variants"),1,10000000);long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),numeric=integer(c.get("numeric_bytes"),1,1099511627776L),work=integer(c.get("multiply_adds"),1,Long.MAX_VALUE),wall=integer(c.get("native_wall_millis"),1,604800000);
        var qc=object(source.get("qc"));keys(qc,"minimum_frequency","maximum_missingness","minimum_hwe","maximum_covariate_fraction");
        var options=new GemmaKinship.Options(mode.equals("gk1")?GemmaKinship.Mode.CENTERED:GemmaKinship.Mode.STANDARDIZED,number(qc.get("minimum_frequency"),0,.5),number(qc.get("maximum_missingness"),0,1),number(qc.get("minimum_hwe"),0,1),number(qc.get("maximum_covariate_fraction"),0,1),numeric,work);
        NextflowProcess.Limits process=null;
        if(engine.equals("original")){
            var p=object(source.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
            try{process=new NextflowProcess.Limits(positive(p,"wall_millis"),positive(p,"address_space_bytes"),positive(p,"cpu_seconds"),positive(p,"file_bytes"),small(p,"open_files"),positive(p,"workspace_bytes"),small(p,"file_count"),small(p,"processes"),small(p,"threads"),positive(p,"sampled_rss_bytes"),small(p,"monitor_millis"));}
            catch(IllegalArgumentException e){throw new IOException("Invalid GEMMA process capacities",e);}
            require(Math.addExact(Math.addExact(input,output),16777216)<=process.workspaceBytes(),"workspace_bytes must retain complete input/output capacities and 16MiB metadata reserve");
        }else require(source.get("process")==null,"Native computation runs in the caller JVM; process must be null. Numeric/work/time and captured-byte bounds apply, not a separate Linux process limit");
        return new Spec(Collections.unmodifiableMap(new LinkedHashMap<>(source)),edition,engine,mode,build,Collections.unmodifiableMap(originals),options,samples,variants,input,output,wall,process);
    }
    static double number(Object raw,double min,double max)throws IOException{if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IOException("Require finite numeric value in "+min+".."+max);return n.doubleValue();}
    private static long positive(Map<?,?> p,String name)throws IOException{return integer(p.get(name),1,Long.MAX_VALUE);}
    private static int small(Map<?,?> p,String name)throws IOException{return(int)integer(p.get(name),1,Integer.MAX_VALUE);}
    private static String original(Object raw,boolean live)throws IOException{String s=text(raw);require(!s.isBlank()&&s.length()<32768&&s.indexOf(0)<0,"Require an explicit original local file path");if(live){Path p=Path.of(s);require(p.isAbsolute()&&p.normalize().equals(p)&&p.toRealPath().equals(p)&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Require canonical absolute original regular files");}return s;}
    private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
}

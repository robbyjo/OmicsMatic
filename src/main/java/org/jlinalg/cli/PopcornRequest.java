/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit full original two-population workflow and total-population capacities. */
final class PopcornRequest {
    private PopcornRequest() { }
    record Spec(Map<?,?> value,String profile,Path runtime,String mode,Map<?,?> options,long inputBytes,long outputBytes,int files,long wallMillis,NextflowProcess.Limits process) {
        boolean compute(){return !mode.equals("fit");}boolean fit(){return !mode.equals("compute");}
    }
    static boolean bool(Object value)throws IOException{require(value instanceof Boolean,"Explicit Popcorn Boolean required");return(Boolean)value;}
    static double real(Object value,double min,double max)throws IOException{require(value instanceof Number,"Explicit numeric Popcorn setting required");double d=((Number)value).doubleValue();require(Double.isFinite(d)&&d>=min&&d<=max,"Popcorn setting outside its finite source domain");return d;}
    static String scalar(Object raw)throws IOException {String value=text(raw);require(!value.isBlank()&&value.length()<=32768&&value.chars().noneMatch(c->c<32&&c!='\n'&&c!='\t'),"Explicit bounded preparation/provenance text required");return value;}
    static Spec parse(Object raw)throws IOException {
        var value=object(raw);keys(value,"schema_version","profile","runtime","mode","preparation","score_provenance","references","score_file","summaries","extract","options","capacity","process");integer(value.get("schema_version"),1,1);
        String profile=text(value.get("profile"));PopcornRuntime.profile(profile);Path runtime=PopcornRuntime.absolute(value.get("runtime"));String mode=text(value.get("mode"));require(Set.of("compute","fit","compute-fit").contains(mode),"Choose explicit compute, fit or compute-fit");boolean compute=!mode.equals("fit"),fit=!mode.equals("compute");scalar(value.get("preparation"));
        var provenance=object(value.get("score_provenance"));keys(provenance,"correlation","description");scalar(provenance.get("description"));
        var references=list(value.get("references"));require(references.size()==(compute?2:0),"Two original PLINK reference prefixes required for score computation; otherwise use an empty list");for(Object path:references)PopcornRuntime.absolute(path);
        require(compute?value.get("score_file")==null:value.get("score_file")!=null,"Precomputed score file and generated-score mode are mutually exclusive");if(!compute)PopcornRuntime.absolute(value.get("score_file"));
        var summaries=list(value.get("summaries"));require(summaries.size()==(fit?2:0),"Supply exactly two summary files for fitting; otherwise an empty list");for(Object path:summaries)PopcornRuntime.absolute(path);
        require(compute||value.get("extract")==null,"Extract applies only to reference-score computation");if(value.get("extract")!=null)PopcornRuntime.absolute(value.get("extract"));
        var options=object(value.get("options"));keys(options,"correlation","maf","window_size","window_type","read_snps","store_snps","use_bias","h2weight","from_bp","to_bp","estimator","intercept","jackknife","verbosity");
        require(Set.of("genetic-effect","genetic-impact").contains(options.get("correlation"))&&options.get("correlation").equals(provenance.get("correlation")),"Declare matching effect/impact mode and actual score provenance; numeric scores cannot identify their mode");
        require(real(options.get("maf"),0,.5)<.5,"MAF must be below .5");integer(options.get("window_size"),1,Integer.MAX_VALUE);require(Set.of("KBP","SNP").contains(options.get("window_type")),"Choose source KBP or SNP windows");integer(options.get("read_snps"),1,10000000);integer(options.get("store_snps"),1,10000000);bool(options.get("use_bias"));boolean h2weight=bool(options.get("h2weight"));require(!h2weight||compute,"h2weight requires original PLINK-prefix weight files and compute mode");require(!h2weight||options.get("correlation").equals("genetic-impact"),"Original genetic-effect mode overrides h2weight weighting; choose the intended explicit model instead of silently overriding");
        require((options.get("from_bp")==null)==(options.get("to_bp")==null),"Both original score-range endpoints or neither are required");if(options.get("from_bp")!=null){require(compute,"Range endpoints apply to original score computation, not an inferred regional fit");long from=integer(options.get("from_bp"),0,Integer.MAX_VALUE),to=integer(options.get("to_bp"),1,Integer.MAX_VALUE);require(to>from,"Range endpoints are not ordered");}
        require(Set.of("original-regression","original-mle").contains(options.get("estimator")),"Choose original regression or MLE explicitly");boolean intercept=bool(options.get("intercept"));require(intercept||options.get("estimator").equals("original-mle"),"Original cross-population no-intercept regression indexes a missing slope; no repaired model is substituted");bool(options.get("jackknife"));integer(options.get("verbosity"),0,3);
        var c=object(value.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","rows","line_characters","variants","samples","genotype_cells");long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000);int files=(int)integer(c.get("files"),128,1000000);integer(c.get("rows"),2,1000000000);integer(c.get("line_characters"),256,16777216);integer(c.get("variants"),2,100000000);integer(c.get("samples"),3,10000000);integer(c.get("genotype_cells"),6,1000000000000L);
        var p=object(value.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");var process=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),128,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),1,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        return new Spec(value,profile,runtime,mode,options,input,output,files,wall,process);
    }
}

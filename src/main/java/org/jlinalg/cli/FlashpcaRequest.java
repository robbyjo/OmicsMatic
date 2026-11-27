/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.FlashpcaPcaAudit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit PCA source edition, genotype preparation and complete work capacities. */
final class FlashpcaRequest {
    private FlashpcaRequest() { }
    record Spec(Map<?,?> value,String profile,Path runtime,Path prefix,Map<?,?> settings,
                FlashpcaPcaAudit.Options options,FlashpcaPcaAudit.Limits audit,
                long inputBytes,long outputBytes,int files,long wallMillis,
                NextflowProcess.Limits process,boolean execute) { }
    static Spec parse(Object raw)throws IOException {
        var v=object(raw);keys(v,"schema_version","profile","runtime","bed_prefix","population","preparation","execution_policy","settings","capacity","process");integer(v.get("schema_version"),1,1);
        String id=text(v.get("profile"));var profile=FlashpcaRuntime.profile(id);boolean legacy=profile.get("edition").equals("1.2.6");
        for(String field:List.of("population","preparation")){String s=text(v.get(field));require(!s.isBlank()&&s.length()<=32768,"Declare actual "+field+" including cohort, variant QC/filtering and allele/build provenance");}
        require(Set.of("ALLOW","FORBID").contains(v.get("execution_policy")),"Explicit ALLOW/FORBID execution policy required");
        var s=object(v.get("settings"));keys(s,"components","seed","iterations","tolerance","standardization","divisor","block_snps","legacy_extra_dimensions","legacy_force_transpose","legacy_method","legacy_memory","audit_tolerance");
        int k=(int)integer(s.get("components"),1,1000);integer(s.get("seed"),1,Integer.MAX_VALUE);integer(s.get("iterations"),1,1000000);
        double tolerance=GraphtyperRequest.number(s.get("tolerance"));require(tolerance>0&&tolerance<1,"Positive source tolerance below one required");
        var standard=FlashpcaPcaAudit.Standardization.valueOf(text(s.get("standardization")));var divisor=FlashpcaPcaAudit.Divisor.valueOf(text(s.get("divisor")));
        boolean transpose=false;
        if(legacy){require(s.get("legacy_force_transpose")instanceof Boolean,"Explicit legacy transpose flag required");transpose=(Boolean)s.get("legacy_force_transpose");integer(s.get("legacy_extra_dimensions"),0,10000);require(s.get("block_snps")==null,"Legacy PCA loads the full matrix; block_snps must be null");require(Set.of("EIGEN","SVD").contains(s.get("legacy_method"))&&Set.of("HIGH","LOW").contains(s.get("legacy_memory")),"Explicit original legacy small-matrix method and memory mode required");}
        else{integer(s.get("block_snps"),1,10000000);for(String field:List.of("legacy_extra_dimensions","legacy_force_transpose","legacy_method","legacy_memory"))require(s.get(field)==null,"Legacy-only settings must be null for original online v2 PCA");}
        var options=new FlashpcaPcaAudit.Options(legacy?FlashpcaPcaAudit.Edition.V1_2_6:FlashpcaPcaAudit.Edition.V2_0,k,standard,divisor,transpose,GraphtyperRequest.number(s.get("audit_tolerance")));
        var c=object(v.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","samples","variants","metadata_bytes","retained_bytes","products","line_bytes");
        long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000);
        int files=(int)integer(c.get("files"),128,1000000);
        var audit=new FlashpcaPcaAudit.Limits((int)integer(c.get("samples"),1,10000000),(int)integer(c.get("variants"),1,50000000),integer(c.get("metadata_bytes"),1,input),integer(c.get("retained_bytes"),1,1099511627776L),integer(c.get("products"),1,Long.MAX_VALUE),(int)integer(c.get("line_bytes"),64,16777216));
        var p=object(v.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        var process=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),128,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),1,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        return new Spec(v,id,FlashpcaRuntime.absolute(v.get("runtime")),FlashpcaRuntime.absolute(v.get("bed_prefix")),s,options,audit,input,output,files,wall,process,v.get("execution_policy").equals("ALLOW"));
    }
}

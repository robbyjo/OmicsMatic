/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Closed prepared-data interface to the original pinned GenomicSEM R package. */
final class GenomicsemContracts {
    private GenomicsemContracts() { }
    static final String PROFILE="genomicsem-da95d43-r461-win64-lavaan0619-v1";
    static final String COMPLETE="COMPLETE_SCOPED_ORIGINAL_GENOMICSEM_OPERATION";
    static final String ACTION="REVIEW REQUIRED: inspect WARNINGS.md,input-audit.json,scientific-audit.json,inference.tsv and original worker logs/tables. "
        +"This is original GenomicSEM da95d431a4709693d07eae55e172ade55526948b (DESCRIPTION0.0.5),R4.6.1 Windows x86_64,lavaan0.6-19;historical author editions remain unqualified. "
        +"Supply a full multivariate sampling covariance matrix in the declared trait/vech order,not independently assembled pairwise LDSC estimates. Confirm scales,allele/effect alignment,study design and model identification. "
        +"Source smoothing,variance constraints,bounded P strings,zero P endpoints,fixed/missing-inference fields and warnings retain upstream semantics. No independent optimizer,global-optimum certificate,biological calibration or whole-paper reproduction is supplied. "
        +"SKIP3: the dependent analysis was skipped;restore/install the exact named permitted runtime/input using an allowed method,otherwise retain the skip. "
        +"FAIL2: no complete admitted result;retain original/partial evidence,resolve the stated input,numerical,capacity,process or integrity cause and retry NEW output. "
        +"The analysis never downloads or installs dependencies. Limits are periodically monitored,not OS job/RAM quotas. Hashes establish integrity,not authentication or host attestation.";
    static boolean association(String op){return op.endsWith("GWAS")||op.endsWith("TSEM");}
    static boolean user(String op){return op.startsWith("USER");}
    static boolean twas(String op){return op.endsWith("TSEM");}
    static Set<String> roles(String op){var r=new TreeSet<>(Set.of("covariance","sampling_covariance"));if(association(op))r.addAll(Set.of("intercepts","associations"));if(user(op))r.add("model");return r;}
    static String filename(String role){return switch(role){case "covariance"->"S.tsv";case "sampling_covariance"->"V.tsv";case "intercepts"->"I.tsv";case "associations"->"associations.tsv";case "model"->"model.txt";default->throw new IllegalArgumentException(role);};}
    static Path input(Path root,String role){return root.resolve("inputs/"+filename(role));}
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    static double finite(Object x)throws IOException{double d=decimal(x).doubleValue();require(Double.isFinite(d),"Require finite numeric option");return d;}
    static boolean bool(Map<String,Object> m,String key)throws IOException{require(m.get(key) instanceof Boolean,"Require explicit boolean "+key);return (boolean)m.get(key);}
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        require(raw.keySet().equals(Set.of("schema_version","mode","profile","operation","runtime_home","out","inputs","options","limits"))||raw.keySet().equals(Set.of("schema_version","mode","profile","operation","runtime_home","out","inputs","options")),"Declare exactly schema_version,mode,profile,operation,runtime_home,out,inputs,options and optional limits");
        integer(raw.get("schema_version"),1,1);require("RUN".equals(raw.get("mode")),"Require RUN or a separate VERIFY/REPLAY control request");
        if(!PROFILE.equals(raw.get("profile")))throw new GenomicsemRuntime.Unavailable("Unsupported GenomicSEM source/runtime profile;no substitution for0.0.3f or the manuscript literal0.5.0");
        String op=text(raw.get("operation"),"operation");require(Set.of("COMMONFACTOR","USERMODEL","COMMONFACTOR_GWAS","USER_GWAS","COMMONFACTOR_TSEM","USER_TSEM").contains(op),"Unsupported prepared GenomicSEM operation");
        for(String k:List.of("runtime_home","out"))literal(text(raw.get(k),k),k);
        var inputs=object(raw.get("inputs"));require(inputs.keySet().equals(roles(op)),"Declare exactly input roles "+roles(op));for(Object v:inputs.values())literal(text(v,"input path"),"input path");
        var o=object(raw.get("options"));var fields=new HashSet<>(Set.of("estimation","seed","covariance_scale","sampling_order","input_reviewed","smoothing_policy","warning_policy"));
        if(user(op))fields.add("std_lv");
        if(op.equals("USERMODEL"))fields.addAll(Set.of("CFIcalc","fix_resid","tolerance","Q_Factor"));
        if(association(op)){fields.addAll(Set.of("cores","parallel","GC","SNPSE","tolerance","effects_aligned"));if(user(op))fields.addAll(Set.of("fix_measurement","Q_SNP","sub"));}
        require(o.keySet().equals(fields),"Declare exactly scientific options "+fields);
        choice(o,"estimation",Set.of("DWLS","ML"));integer(o.get("seed"),1,Integer.MAX_VALUE);
        choice(o,"covariance_scale",Set.of("observed","liability","correlation"));require("lower-column-including-diagonal".equals(o.get("sampling_order")),"Declare sampling_order=lower-column-including-diagonal");
        require(bool(o,"input_reviewed"),"Declare input_reviewed=true after checking full multivariate covariance,scales and study suitability");choice(o,"smoothing_policy",Set.of("FAIL","ALLOW_UPSTREAM"));choice(o,"warning_policy",Set.of("FAIL_FIT_WARNINGS","RETAIN_AND_FLAG"));
        if(user(op))bool(o,"std_lv");
        if(op.equals("USERMODEL")){bool(o,"CFIcalc");bool(o,"fix_resid");bool(o,"Q_Factor");Object tol=o.get("tolerance");require(tol==null||finite(tol)>0,"USERMODEL tolerance must be null or positive");}
        if(association(op)){
            long cores=integer(o.get("cores"),1,64);boolean parallel=bool(o,"parallel");require(parallel||cores==1,"Sequential execution requires explicit cores=1");choice(o,"GC",Set.of("standard","conserv","none"));
            for(String k:List.of("SNPSE","tolerance")){Object v=o.get(k);require(Boolean.FALSE.equals(v)||(v!=null&&!(v instanceof Boolean)&&finite(v)>0),k+" must be false (original default) or positive");}
            require(bool(o,"effects_aligned"),"Declare effects_aligned=true;the adapter never aligns alleles or rescales effects");
            if(user(op)){
                bool(o,"fix_measurement");bool(o,"Q_SNP");var sub=list(o.get("sub"));require(sub.size()<=128,"Too many requested association paths");Set<String> seen=new HashSet<>();
                for(Object v:sub){String s=text(v,"sub path").replaceAll("\\s+","");require(s.matches("[A-Za-z][A-Za-z0-9_]*~"+(twas(op)?"Gene":"SNP"))&&seen.add(s),"sub must be unique factor/trait ~ "+(twas(op)?"Gene":"SNP")+" paths,or [] for all source rows");}
            }
        }
        var defaults=map("maximum_input_bytes",8589934592L,"maximum_member_bytes",17179869184L,"maximum_output_bytes",107374182400L,"maximum_files",10000L,"timeout_seconds",86400L,"maximum_rows",10000000L,"maximum_traits",64L,"maximum_values",200000000L,"maximum_line_characters",16777216L,"maximum_result_rows",100000000L);
        var supplied=raw.containsKey("limits")?object(raw.get("limits")):map();keys(supplied,defaults.keySet());var limits=map();for(var e:defaults.entrySet())limits.put(e.getKey(),integer(supplied.getOrDefault(e.getKey(),e.getValue()),1,Long.parseLong(e.getValue().toString())));
        var result=new LinkedHashMap<>(raw);result.put("limits",limits);return result;
    }
    static Map<String,Object> canonical(Map<String,Object> validated)throws IOException {
        var r=new LinkedHashMap<>(validated);r.put("out",".");r.put("runtime_home","external-runtime");var in=map();for(String role:roles(text(r.get("operation"),"operation")))in.put(role,"inputs/"+filename(role));r.put("inputs",in);return r;
    }
}

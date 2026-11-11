/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Closed original SMR/HEIDI choices; source defaults, namespaces and reference provenance remain explicit. */
final class SmrWorkflowRequest {
    private SmrWorkflowRequest() { }
    static final String PROFILE="original-SMR-Linux-x86_64-1.3.1-1.4.3";
    record Request(String version, Path gwas, Map<String,Object> qtl, Map<String,Object> reference,
                   Map<String,Object> probes, String summaryAssembly, String exposureUnits, String outcomeUnits,
                   List<Map<String,Object>> analyses, Path source, Path runtime, Path out,
                   long inputBytes, int tableBytes, int variants, int probesLimit, int samples, int qtlEntries,
                   long nativeOperations, long outputBytes, int outputFiles, int timeoutMillis, int threads) {
        Map<String,Path> inputs(){
            var m=new LinkedHashMap<String,Path>();m.put("gwas.ma",gwas);
            if(qtl.get("kind").equals("qfile"))m.put("qtl.qfile",Path.of((String)qtl.get("file")));
            else for(String suffix:List.of("besd","epi","esi"))m.put("qtl."+suffix,Path.of(qtl.get("prefix")+"."+suffix));
            if(reference.get("kind").equals("plink-bed"))for(String suffix:List.of("bed","bim","fam"))m.put("reference."+suffix,Path.of(reference.get("prefix")+"."+suffix));
            if(probes.get("kind").equals("supplied"))m.put("probes.list",Path.of((String)probes.get("file")));
            return m;
        }
        Map<String,Object> effective(){
            var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("mode","ANALYZE");m.put("profile",PROFILE);m.put("version",version);m.put("gwas",gwas.toString());m.put("qtl",qtl);m.put("reference",reference);m.put("probe_selection",probes);m.put("summary_assembly_label",summaryAssembly);m.put("exposure_units",exposureUnits);m.put("outcome_units",outcomeUnits);m.put("allele_policy","source-exact-or-swap");m.put("analyses",analyses);m.put("source",source.toString());m.put("runtime",runtime.toString());m.put("out",out.toString());m.put("maximum_input_bytes",inputBytes);m.put("maximum_table_bytes",tableBytes);m.put("maximum_variants",variants);m.put("maximum_probes",probesLimit);m.put("maximum_samples",samples);m.put("maximum_qtl_entries",qtlEntries);m.put("maximum_native_operations",nativeOperations);m.put("maximum_output_bytes",outputBytes);m.put("maximum_output_files",outputFiles);m.put("timeout_millis",timeoutMillis);m.put("threads",threads);return m;
        }
    }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        fields(m,"schema_version","mode","profile","version","gwas","qtl","reference","probe_selection","summary_assembly_label","exposure_units","outcome_units","allele_policy","analyses","source","runtime","out","maximum_input_bytes","maximum_table_bytes","maximum_variants","maximum_probes","maximum_samples","maximum_qtl_entries","maximum_native_operations","maximum_output_bytes","maximum_output_files","timeout_millis","threads");
        integer(m.get("schema_version"),1,1);require(text(m.get("mode")).equals("ANALYZE"),"Declare ANALYZE mode");require(text(m.get("profile")).equals(PROFILE),"Select the exact original SMR source/runtime profile; no historical version substitution");String version=choice(m.get("version"),"1.3.1","1.4.3");require(text(m.get("allele_policy")).equals("source-exact-or-swap"),"Declare source-exact-or-swap allele matching; no inferred strand complementation");
        var qtl=object(m.get("qtl"));String kind=choice(qtl.get("kind"),"qfile","besd");require(text(qtl.get("precision")).equals("source-binary32"),"Acknowledge original BESD binary32 beta/SE storage");
        if(kind.equals("qfile")){fields(qtl,"kind","file","storage","precision","preparation_policy");require(text(qtl.get("preparation_policy")).equals("source-version-with-ledger"),"Declare source-version-with-ledger Qfile preparation: original1.3.1 may derive SE from beta/P, while1.4.3 uses supplied SE");choice(qtl.get("storage"),"sparse","dense");file(qtl,"file",parent);}
        else{fields(qtl,"kind","prefix","precision","missing_policy");require(text(qtl.get("missing_policy")).equals("source-sentinel-ledger"),"BESD missing/sentinel observations require an explicit complete ledger");file(qtl,"prefix",parent);}
        var ref=object(m.get("reference"));String reference=choice(ref.get("kind"),"none","plink-bed");
        if(reference.equals("none"))fields(ref,"kind");
        else{fields(ref,"kind","prefix","assembly_label","population_label","coordinate_policy","missing_genotypes");label(ref.get("assembly_label"));label(ref.get("population_label"));choice(ref.get("coordinate_policy"),"require-equal","source-ID-and-alleles-with-ledger");require(text(ref.get("missing_genotypes")).equals("source-mean-impute-LD"),"Declare source mean imputation for derived LD; original calls remain unchanged");file(ref,"prefix",parent);}
        var probes=object(m.get("probe_selection"));if(choice(probes.get("kind"),"all","supplied").equals("all"))fields(probes,"kind");else{fields(probes,"kind","file");file(probes,"file",parent);}
        var analyses=new ArrayList<Map<String,Object>>();var ids=new HashSet<String>();
        for(Object raw:list(m.get("analyses"))){
            require(analyses.size()<16,"Require at most16 original analyses");var a=object(raw);fields(a,"id","kind","qtl_p","window_kb","minimum_maf","frequency_check","sample_overlap","heidi","multi");String id=text(a.get("id"));require(id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")&&ids.add(id.toLowerCase(Locale.ROOT)),"Require unique portable case-insensitive analysis IDs");boolean multi=choice(a.get("kind"),"single","multi").equals("multi");finite(a.get("qtl_p"),0,1);integer(a.get("window_kb"),1,2000000);double maf=finite(a.get("minimum_maf"),0,.5);
            var h=object(a.get("heidi"));String method=choice(h.get("method"),"off","original","top20");
            if(method.equals("off"))fields(h,"method");
            else{
                if(method.equals("original")){fields(h,"method","qtl_p","ld_upper","minimum_snps","skip_smr_p");require(!multi,"Original multi-SNP SMR uses the newer HEIDI path; --heidi-mtd0 is not an applied multi-SNP option");}
                else{fields(h,"method","qtl_p","ld_upper","ld_lower","minimum_snps","maximum_snps","skip_smr_p","maximum_ld_candidates");double lo=finite(h.get("ld_lower"),0,1),hi=finite(h.get("ld_upper"),0,1);require(lo<=hi,"HEIDI lower LD bound exceeds upper bound");integer(h.get("maximum_snps"),1,1000000);require(integer(h.get("minimum_snps"),1,1000000)<=integer(h.get("maximum_snps"),1,1000000),"HEIDI minimum exceeds maximum");if(version.equals("1.4.3"))integer(h.get("maximum_ld_candidates"),500,10000);else require(!h.containsKey("maximum_ld_candidates"),"SMR1.3.1 does not expose the current maximum-LD-candidate option");}
                finite(h.get("qtl_p"),0,1);finite(h.get("ld_upper"),0,1);integer(h.get("minimum_snps"),1,1000000);finite(h.get("skip_smr_p"),0,1);
            }
            if(multi){require(!reference.equals("none"),"Multi-SNP SMR requires an explicit LD reference even when HEIDI is off");var mm=object(a.get("multi"));fields(mm,"window_kb","ld_r2");integer(mm.get("window_kb"),1,2000000);finite(mm.get("ld_r2"),0,1);a.put("multi",Map.copyOf(mm));}
            else require(!a.containsKey("multi"),"A single-SNP case cannot carry unapplied multi-SNP options");
            if(!method.equals("off"))require(!reference.equals("none"),"HEIDI requires an explicit LD reference");
            if(!multi&&method.equals("off"))require(maf==0,"Single-SNP HEIDI-off does not use BED/MAF; declare minimum_maf0 rather than an unapplied filter");
            var f=object(a.get("frequency_check"));if(choice(f.get("kind"),"source-check","disabled").equals("disabled"))fields(f,"kind");else{fields(f,"kind","maximum_difference","maximum_excluded_fraction");finite(f.get("maximum_difference"),0,1);finite(f.get("maximum_excluded_fraction"),0,1);}
            var overlap=object(a.get("sample_overlap"));String overlapKind=choice(overlap.get("kind"),"none","estimate-original","source-flag-without-estimation");
            if(overlapKind.equals("none"))fields(overlap,"kind");
            else if(overlapKind.equals("source-flag-without-estimation")){fields(overlap,"kind");require(multi,"The explicit unestimated original overlap flag belongs only to the active multi-SNP path");}
            else{fields(overlap,"kind","null_p","minimum_null_snps");require(!multi,"Both selected original multi-SNP entry paths leave theta0 and do not apply null-P/minimum-null-SNP correlation estimation; declare source-flag-without-estimation to reproduce that flag or none to omit it");finite(overlap.get("null_p"),0,1);integer(overlap.get("minimum_null_snps"),2,1000000);}
            a.put("heidi",Map.copyOf(h));a.put("frequency_check",Map.copyOf(f));a.put("sample_overlap",Map.copyOf(overlap));analyses.add(Collections.unmodifiableMap(a));
        }
        require(!analyses.isEmpty(),"Require1..16 original analyses");
        return new Request(version,path(m,"gwas",parent),Map.copyOf(qtl),Map.copyOf(ref),Map.copyOf(probes),label(m.get("summary_assembly_label")),label(m.get("exposure_units")),label(m.get("outcome_units")),List.copyOf(analyses),path(m,"source",parent),path(m,"runtime",parent),path(m,"out",parent),number(m,"maximum_input_bytes",8589934592L,1,137438953472L),(int)number(m,"maximum_table_bytes",268435456,1,536870912),(int)number(m,"maximum_variants",10000000,1,50000000),(int)number(m,"maximum_probes",100000,1,1000000),(int)number(m,"maximum_samples",1000000,1,10000000),(int)number(m,"maximum_qtl_entries",5000000,1,100000000),number(m,"maximum_native_operations",1000000000,1,100000000000L),number(m,"maximum_output_bytes",4294967296L,1,137438953472L),(int)number(m,"maximum_output_files",100000,1,1000000),(int)number(m,"timeout_millis",900000,1,86400000),(int)number(m,"threads",1,1,64));
    }
    private static void fields(Map<String,Object> m,String... known)throws IOException{var set=Set.of(known);for(String key:m.keySet())require(set.contains(key),"Unknown SMR field: "+key);}
    private static String choice(Object x,String... choices)throws IOException{String s=text(x);require(Set.of(choices).contains(s),"Choose one of "+String.join(", ",choices));return s;}
    private static Path path(Map<String,Object> m,String key,Path parent)throws IOException{return resolve(parent,label(m.get(key)));}
    private static void file(Map<String,Object> m,String key,Path parent)throws IOException{m.put(key,path(m,key,parent).toString());}
}

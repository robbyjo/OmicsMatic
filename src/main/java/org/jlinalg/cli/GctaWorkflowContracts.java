/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed prepared-data requests. Edition, engine and scientific settings remain explicit. */
final class GctaWorkflowContracts {
    static final String PROFILE = "GCTA-1.94.1";
    static final Set<String> MODELS = Set.of("GRM", "PCA", "COJO_SELECT", "COJO_JOINT",
        "COJO_CONDITION", "REML", "LD_SCORE", "LD_SCORE_REGION", "SPARSE_GRM", "FASTGWA_BINARY");
    private static final Set<String> COMMON = Set.of("schema_version", "mode", "profile", "engine",
        "model", "out", "runtime", "preparation_note", "timeout_seconds", "maximum_input_bytes",
        "maximum_output_bytes", "maximum_samples", "maximum_variants", "maximum_metadata_bytes",
        "maximum_retained_metadata_bytes", "maximum_line_bytes", "maximum_native_cells",
        "maximum_sample_pairs", "maximum_work", "maximum_result_rows", "maximum_log_bytes", "maximum_audit_entries");
    private GctaWorkflowContracts() { }

    static Map<String,Object> validate(Map<String,Object> raw) throws IOException {
        if (integer(raw,"schema_version",0,1,1)!=1 || !"RUN".equals(text(raw,"mode")))
            throw new IOException("Require schema_version 1 and mode RUN");
        String model=choice(raw,"model","",MODELS),engine=choice(raw,"engine","",Set.of("native","original"));
        String profile=text(raw,"profile");
        if(!profile.matches("GCTA-[A-Za-z0-9][A-Za-z0-9.-]{0,63}"))
            throw new IOException("Require an explicit GCTA edition profile");
        text(raw,"out");identity(text(raw,"preparation_note"));
        if(engine.equals("native")&&!Set.of("GRM","SPARSE_GRM").contains(model))
            throw new IOException("The native engine implements GRM and SPARSE_GRM; choose original explicitly for this model");
        if(engine.equals("original"))text(raw,"runtime");
        else if(raw.containsKey("runtime"))throw new IOException("Native GRM has no runtime/fallback setting");

        var allowed=new LinkedHashSet<>(COMMON);
        boolean bed=model.equals("GRM")||model.startsWith("COJO_")||model.startsWith("LD_SCORE")||model.equals("FASTGWA_BINARY");
        if(bed){allowed.add("genotypes");allowed.add("autosome_number");}
        if(model.equals("GRM"))allowed.addAll(Set.of("grm_algorithm","extract"));
        if(Set.of("PCA","REML","SPARSE_GRM").contains(model))allowed.add("grms");
        if(model.equals("PCA"))allowed.add("pca_components");
        if(model.startsWith("COJO_"))allowed.addAll(Set.of("summary","extract","cojo_window_kb","cojo_collinear","diff_frequency"));
        if(model.equals("COJO_SELECT"))allowed.add("cojo_p");
        if(model.equals("COJO_CONDITION"))allowed.add("condition");
        if(model.equals("REML"))allowed.addAll(Set.of("phenotype","qcovar","covar","reml_unconstrained","reml_max_iterations"));
        if(model.startsWith("LD_SCORE"))allowed.addAll(Set.of("extract","ld_window_kb","ld_rsq_cutoff","ld_adjusted"));
        if(model.equals("LD_SCORE_REGION"))allowed.add("ld_region_kb");
        if(model.equals("SPARSE_GRM"))allowed.add("sparse_cutoff");
        if(model.equals("FASTGWA_BINARY"))allowed.addAll(Set.of("phenotype","qcovar","covar","sparse","seed","random_vectors","binary_coding"));
        keys(raw,allowed);
        var r=new LinkedHashMap<String,Object>(raw);
        if(bed) {
            var files=object(raw.get("genotypes"),"genotypes");keys(files,Set.of("bed","bim","fam"));
            for(String key:List.of("bed","bim","fam"))text(files,key);
            r.put("genotypes",files);r.put("autosome_number",integer(raw,"autosome_number",22,1,1000));
        }
        if(allowed.contains("grms")) {
            var grms=new ArrayList<Map<String,Object>>();
            for(Object item:list(raw.get("grms"),"grms")) {
                var files=object(item,"GRM");keys(files,Set.of("values","counts","ids"));
                text(files,"values");text(files,"ids");if(files.containsKey("counts"))text(files,"counts");grms.add(files);
            }
            if(grms.isEmpty()||grms.size()>(model.equals("REML")?16:1))
                throw new IOException("Require one GRM, or 1..16 explicitly prepared components for REML");
            r.put("grms",grms);
        }
        if(model.equals("FASTGWA_BINARY")) {
            var sparse=object(raw.get("sparse"),"sparse GRM");keys(sparse,Set.of("values","ids"));
            text(sparse,"values");text(sparse,"ids");r.put("sparse",sparse);
            r.put("seed",integer(raw,"seed",2026,0,Integer.MAX_VALUE));
            r.put("random_vectors",integer(raw,"random_vectors",10,1,100000));
            if(!"0/1".equals(raw.get("binary_coding")))throw new IOException("The original GCTA1.94.1 binary profile requires explicit binary_coding 0/1. Preserve other encodings and prepare a documented 0/1 input before RUN; no automatic recoding");
            r.put("binary_coding","0/1");
        }
        for(String key:List.of("phenotype","summary","condition"))if(allowed.contains(key))text(raw,key);
        for(String key:List.of("extract","qcovar","covar"))if(raw.containsKey(key))text(raw,key);
        if(model.equals("COJO_JOINT")&&!raw.containsKey("extract"))
            throw new IOException("COJO_JOINT requires an explicit extract list of joint-model variants");
        if(model.equals("GRM"))r.put("grm_algorithm",integer(raw,"grm_algorithm",0,0,1));
        if(model.equals("PCA"))r.put("pca_components",integer(raw,"pca_components",20,1,Integer.MAX_VALUE));
        if(model.startsWith("COJO_")) {
            if(model.equals("COJO_SELECT"))r.put("cojo_p",real(raw,"cojo_p",5e-8,0,.05,false));
            r.put("cojo_window_kb",integer(raw,"cojo_window_kb",10000,1,100000));
            r.put("cojo_collinear",real(raw,"cojo_collinear",.9,.01,.99,true));
            r.put("diff_frequency",real(raw,"diff_frequency",.2,0,1,true));
        }
        if(model.equals("REML")) {
            r.put("reml_unconstrained",bool(raw,"reml_unconstrained",false));
            r.put("reml_max_iterations",integer(raw,"reml_max_iterations",1000,1,10000));
        }
        if(model.startsWith("LD_SCORE")) {
            r.put("ld_window_kb",real(raw,"ld_window_kb",1000,1,20000,true));
            r.put("ld_rsq_cutoff",real(raw,"ld_rsq_cutoff",0,0,1,true));
            r.put("ld_adjusted",bool(raw,"ld_adjusted",false));
            if(model.equals("LD_SCORE_REGION"))r.put("ld_region_kb",integer(raw,"ld_region_kb",200,10,2147483));
        }
        if(model.equals("SPARSE_GRM"))r.put("sparse_cutoff",real(raw,"sparse_cutoff",.05,-Float.MAX_VALUE,Float.MAX_VALUE,true));
        for(var setting:List.of(
                new Bound("timeout_seconds",3600,1,604800),
                new Bound("maximum_input_bytes",1_073_741_824L,1,Long.MAX_VALUE),
                new Bound("maximum_output_bytes",536_870_912L,1,Long.MAX_VALUE),
                new Bound("maximum_samples",1_000_000,1,Integer.MAX_VALUE),
                new Bound("maximum_variants",50_000_000,1,Integer.MAX_VALUE),
                new Bound("maximum_metadata_bytes",268_435_456,1,Long.MAX_VALUE),
                new Bound("maximum_retained_metadata_bytes",268_435_456,1,Long.MAX_VALUE),
                new Bound("maximum_line_bytes",16384,16,16777216),
                new Bound("maximum_log_bytes",16777216,1,Integer.MAX_VALUE),
                new Bound("maximum_audit_entries",5_000_000,1,Integer.MAX_VALUE),
                new Bound("maximum_native_cells",5_000_000,1,Long.MAX_VALUE),
                new Bound("maximum_sample_pairs",2_000_000,1,Long.MAX_VALUE),
                new Bound("maximum_work",2_000_000_000,1,Long.MAX_VALUE),
                new Bound("maximum_result_rows",1_000_000,1,Integer.MAX_VALUE)))
            r.put(setting.name,integer(raw,setting.name,setting.fallback,setting.min,setting.max));
        return r;
    }
    private record Bound(String name,long fallback,long min,long max) { }
    record Binding(String name,String source) { }

    static List<Binding> bindings(Map<String,Object> request) throws IOException {
        var result=new ArrayList<Binding>();
        if(request.containsKey("genotypes")) {
            var f=object(request.get("genotypes"),"genotypes");
            for(String key:List.of("bed","bim","fam"))result.add(new Binding("inputs/genotypes."+key,text(f,key)));
        }
        if(request.containsKey("grms")) {
            var grms=list(request.get("grms"),"grms");
            for(int i=0;i<grms.size();i++) {
                var f=object(grms.get(i),"GRM");String prefix="inputs/grm"+i;
                result.add(new Binding(prefix+".grm.bin",text(f,"values")));
                result.add(new Binding(prefix+".grm.id",text(f,"ids")));
                if(f.containsKey("counts"))result.add(new Binding(prefix+".grm.N.bin",text(f,"counts")));
            }
        }
        if(request.containsKey("sparse")) {
            var f=object(request.get("sparse"),"sparse");
            result.add(new Binding("inputs/sparse.grm.sp",text(f,"values")));
            result.add(new Binding("inputs/sparse.grm.id",text(f,"ids")));
        }
        for(String key:List.of("phenotype","qcovar","covar","summary","condition","extract"))
            if(request.containsKey(key))result.add(new Binding("inputs/"+key,text(request,key)));
        return result;
    }

    static Map<String,Object> captured(Map<String,Object> raw) throws IOException {
        var r=new LinkedHashMap<String,Object>(raw);r.put("out",".");
        if(raw.containsKey("genotypes"))r.put("genotypes",Map.of("bed","inputs/genotypes.bed","bim","inputs/genotypes.bim","fam","inputs/genotypes.fam"));
        if(raw.containsKey("grms")) {
            var grms=new ArrayList<Map<String,Object>>();var old=list(raw.get("grms"),"grms");
            for(int i=0;i<old.size();i++) {
                var f=new LinkedHashMap<String,Object>();String prefix="inputs/grm"+i;
                f.put("values",prefix+".grm.bin");f.put("ids",prefix+".grm.id");
                if(object(old.get(i),"GRM").containsKey("counts"))f.put("counts",prefix+".grm.N.bin");grms.add(f);
            }
            r.put("grms",grms);
        }
        if(raw.containsKey("sparse"))r.put("sparse",Map.of("values","inputs/sparse.grm.sp","ids","inputs/sparse.grm.id"));
        for(String key:List.of("phenotype","qcovar","covar","summary","condition","extract"))if(raw.containsKey(key))r.put(key,"inputs/"+key);
        return r;
    }
}

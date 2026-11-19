/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit ordinary-summary-statistic contracts; no aliases or inferred scale. */
final class ColocAbfContracts {
    private ColocAbfContracts() { }
    static final String PROFILE="native-coloc-abf-6.0.3-v1";
    static final Set<String> ROLES=Set.of("snp","beta","varbeta","pvalues","MAF","position","prior_weight");
    static final String SCOPE="Single-causal-variant native ABF, pinned coloc 6.0.3 behavior. Supplied regional summary statistics only. No allele harmonization, GWAS/QTL fit, LD generation, multi-causal fit, historical-edition parity, whole-paper reproduction or biological validation.";
    static final String ACTION="Read WARNINGS.md and analysis-status.json. SKIP3: the dependent analysis was skipped; restore the named exact permitted prerequisite and retry a NEW output. FAIL2: no complete result admitted; retain original inputs, partials and errors, correct the stated cause and retry a NEW output. Never silently repair IDs/alleles/probabilities/NaN/Inf or substitute algorithms. VERIFY rederives the native calculation from captured inputs; hashes are integrity checks, not signatures or authentication against coordinated fabrication. REPLAY first verifies, then recomputes into a NEW output.";
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","out","trait1","trait2","priors","maximum_input_bytes","maximum_variants","maximum_output_bytes"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"RUN".equals(raw.get("mode")))throw new IOException("Require schema_version 1 and mode RUN");
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("out",text(raw,"out"));
        for(String name:List.of("trait1","trait2"))r.put(name,trait(object(raw.get(name),name)));
        var priors=raw.containsKey("priors")?object(raw.get("priors"),"priors"):Map.<String,Object>of();keys(priors,Set.of("p1","p2","p12"));
        var p=new LinkedHashMap<String,Object>();
        for(String k:List.of("p1","p2","p12")){
            double value=real(priors,k,k.equals("p12")?1e-5:1e-4,0,1,false);
            if(value>=1)throw new IOException("Prior must be in (0,1): "+k);p.put(k,value);
        }
        r.put("priors",p);
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",256_000_000,1,1_000_000_000));
        r.put("maximum_variants",integer(raw,"maximum_variants",1_000_000,1,1_000_000));
        r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",1_000_000_000,1,4_000_000_000L));
        return r;
    }
    private static Map<String,Object> trait(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("file","type","columns","N","sdY","s"));
        String type=choice(raw,"type","",Set.of("quant","cc"));
        var columns=object(raw.get("columns"),"columns");keys(columns,ROLES);
        if(!columns.containsKey("snp"))throw new IOException("columns.snp is required");
        var unique=new HashSet<String>();
        for(String key:columns.keySet()){
            String name=text(columns,key);tableIdentity(name,"column name");
            if(!unique.add(name))throw new IOException("Each source column must have one role");
        }
        boolean estimates=columns.containsKey("beta");
        if(estimates!=columns.containsKey("varbeta"))throw new IOException("Map beta and varbeta together; variance is SE squared");
        if(!estimates&&(!columns.containsKey("pvalues")||!columns.containsKey("MAF")||!raw.containsKey("N")||type.equals("cc")&&!raw.containsKey("s")))
            throw new IOException("P-value input needs mapped pvalues/MAF, scalar N and case fraction s for cc");
        if(estimates&&type.equals("quant")&&!raw.containsKey("sdY")&&(!columns.containsKey("MAF")||!raw.containsKey("N")))
            throw new IOException("Quantitative estimates need sdY or mapped MAF plus scalar N");
        if(type.equals("quant")&&raw.containsKey("s")||type.equals("cc")&&raw.containsKey("sdY"))
            throw new IOException("s applies to cc and sdY to quant only");
        var r=new LinkedHashMap<String,Object>();r.put("file",text(raw,"file"));r.put("type",type);r.put("columns",columns);
        for(String k:List.of("N","sdY","s"))if(raw.containsKey(k)){
            double value=real(raw,k,0,0,Double.MAX_VALUE,false);
            if(k.equals("s")&&value>=1)throw new IOException("s must be in (0,1)");r.put(k,value);
        }
        return r;
    }
    static void tableIdentity(String value,String label)throws IOException {
        if(value.isBlank()||value.length()>4094||value.codePoints().anyMatch(Character::isISOControl))
            throw new IOException("Require exact nonblank "+label+" without controls, at most 4094 UTF-16 units");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(++i==value.length()||!Character.isLowSurrogate(value.charAt(i)))throw new IOException("Malformed Unicode "+label);
            }else if(Character.isLowSurrogate(c))throw new IOException("Malformed Unicode "+label);
        }
    }
    static Map<String,Object> portable(Map<String,Object> r)throws IOException {
        var result=new LinkedHashMap<>(r);result.put("out",".");
        for(String key:List.of("trait1","trait2")){
            var t=new LinkedHashMap<>(object(r.get(key),key));t.put("file","inputs/"+key+".tsv");result.put(key,t);
        }
        return result;
    }
}

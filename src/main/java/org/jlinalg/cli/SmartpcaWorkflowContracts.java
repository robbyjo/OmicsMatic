/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Explicit native PCA scientific choices, source defaults and full-input bounds. */
final class SmartpcaWorkflowContracts {
    private SmartpcaWorkflowContracts() { }
    static final String PROFILE="eig-7.2.1-smartpca-16000-native-pca-v1";
    static final String SCOPE="Prepared human-autosomal EIGENSTRAT hard-call PCA, source outlier passes, ordinary/least-squares projection and perturbation shrinkmode. No genotype merging/harmonization, LD pruning, FST, autoshrink/fastshrink, ancestry assignment, historical-edition equivalence or whole-paper reproduction. Native FP64 CPU calculation; no original C runtime is executed.";
    static final String ACTION="Read WARNINGS.md, analysis-status.json and every field/sample admission flag. SKIP3: supply the exact named permitted input or packaged producer; if unavailable or installation is forbidden, retain the skipped analysis. FAIL2: no complete result admitted; preserve original/raw/partial/failed/undefined evidence, fix the named input/numerical/resource/integrity cause and retry NEW output. Never repair IDs, recode alleles, replace NA/NaN/Inf or reduce the declared population silently. SHA256 provides integrity, not authenticity.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }
    static final List<String> INPUTS=List.of("genotypes","markers","samples");
    static Map<String,Object> validate(Map<String,Object> raw,String base,String style)throws IOException {
        keys(raw,Set.of("schema_version","mode","engine","profile","operation","out","inputs","options","limits","policies"));
        integer(raw.get("schema_version"),1,1);choice(raw,"mode",Set.of("RUN"));choice(raw,"engine",Set.of("native"));
        choice(raw,"profile",Set.of(PROFILE));choice(raw,"operation",Set.of("PCA"));
        var result=new LinkedHashMap<>(raw);result.put("out",resolve(base,text(raw.get("out"),"out"),style));
        var input=object(raw.get("inputs"));keys(input,Set.of("genotypes","markers","samples","reference_populations","genome_build"));
        var normalized=new LinkedHashMap<String,Object>();
        for(String name:INPUTS)normalized.put(name,resolve(base,text(input.get(name),name),style));
        require(input.containsKey("reference_populations"),"Declare reference_populations as a literal population array, or explicit null to fit all samples");
        Object reference=input.get("reference_populations");
        if(reference!=null) {
            var names=new ArrayList<String>();for(Object item:list(reference))names.add(label(item,"reference population",39));
            require(!names.isEmpty()&&names.size()<=1000&&names.stream().distinct().count()==names.size(),"Require1..1000 distinct literal reference populations");
            reference=names;
        }
        normalized.put("reference_populations",reference);normalized.put("genome_build",label(input.getOrDefault("genome_build","unspecified"),"genome build",128));
        result.put("inputs",normalized);
        var options=raw.containsKey("options")?object(raw.get("options")):map();
        keys(options,Set.of("numoutevec","usenorm","altnormstyle","numoutlieriter","numoutlierevec","outliersigmathresh","outliermode","lsqproject","shrinkmode","minallelecnt","maxmissing"));
        double threshold=decimal(options.getOrDefault("outliersigmathresh",6.)).doubleValue();require(Double.isFinite(threshold)&&threshold>0,"Positive finite outliersigmathresh required");
        result.put("options",map("numoutevec",bound(options,"numoutevec",10,1,1000),"usenorm",bool(options,"usenorm",true),
            "altnormstyle",bool(options,"altnormstyle",true),"numoutlieriter",bound(options,"numoutlieriter",5,0,100),
            "numoutlierevec",bound(options,"numoutlierevec",10,0,1000),"outliersigmathresh",threshold,"outliermode",bound(options,"outliermode",0,0,2),
            "lsqproject",bool(options,"lsqproject",false),"shrinkmode",bool(options,"shrinkmode",false),
            "minallelecnt",bound(options,"minallelecnt",1,1,Integer.MAX_VALUE),"maxmissing",bound(options,"maxmissing",9_999_999,0,Integer.MAX_VALUE)));
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();
        keys(limits,Set.of("maximum_input_bytes","maximum_total_input_bytes","maximum_output_bytes","maximum_samples","maximum_markers","maximum_matrix_cells","maximum_line_bytes","maximum_estimated_work","maximum_retained_bytes","timeout_seconds"));
        result.put("limits",map("maximum_input_bytes",bound(limits,"maximum_input_bytes",1_073_741_824L,1,2_147_483_648L),
            "maximum_total_input_bytes",bound(limits,"maximum_total_input_bytes",2_147_483_648L,1,6_442_450_944L),
            "maximum_output_bytes",bound(limits,"maximum_output_bytes",536_870_912L,1,2_147_483_648L),
            "maximum_samples",bound(limits,"maximum_samples",10_000,2,100_000),"maximum_markers",bound(limits,"maximum_markers",1_000_000,1,10_000_000),
            "maximum_matrix_cells",bound(limits,"maximum_matrix_cells",32_000_000,1,2_000_000_000L),
            "maximum_line_bytes",bound(limits,"maximum_line_bytes",1_048_576,1,16_777_216),
            "maximum_estimated_work",bound(limits,"maximum_estimated_work",2_000_000_000L,1,1_000_000_000_000_000L),
            "maximum_retained_bytes",bound(limits,"maximum_retained_bytes",1_073_741_824L,1,137_438_953_472L),
            "timeout_seconds",bound(limits,"timeout_seconds",1800,1,86400)));
        var policies=raw.containsKey("policies")?object(raw.get("policies")):map();keys(policies,Set.of("allow_unreportable_fields"));
        result.put("policies",map("allow_unreportable_fields",bool(policies,"allow_unreportable_fields",false)));return result;
    }
    static boolean bool(Map<String,Object> values,String key,boolean fallback)throws IOException {
        Object value=values.getOrDefault(key,fallback);require(value instanceof Boolean,"Require Boolean "+key);return (Boolean)value;
    }
    static long limit(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    static String label(Object value,String name,int maximum)throws IOException {
        String text=text(value,name);require(!text.isEmpty()&&text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=maximum&&
            text.codePoints().noneMatch(c->c<32||c==127||Character.isWhitespace(c)||c>=0xd800&&c<=0xdfff),"Require literal "+name+" without whitespace/control/unpaired surrogates, at most "+maximum+" UTF-8 bytes");return text;
    }
    static String address(java.nio.file.Path path){return MrPressoWorkflowContracts.address(path);}
    static String style(){return MrPressoWorkflowContracts.pathStyle();}
    static String resolve(String base,String value,String style)throws IOException{return MrPressoWorkflowContracts.resolveAddress(base,value,style);}
}

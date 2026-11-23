/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.jlinalg.mrpresso.MrPressoRandom;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Closed native PRESSO request, literal column binding and explicit RNG profile. */
final class MrPressoWorkflowContracts {
    private MrPressoWorkflowContracts() { }
    static final String PROFILE="MRPRESSO_1.0_3e3c92d";
    static final String SCOPE="Native Java original-profile MRPRESSO1.0, commit3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87, with one outcome and1..64 exposures. Prepared associations only; harmonization, allele selection, weak-instrument filtering, multiple-testing correction, biological causality and study-specific method selection are separate. Original R source is captured as provenance and is not executed. Paper editions/settings remain unreported where unknown. Older analytic PRESSO-style commands are separate methods. Exact input IDs, complete-case row mapping, RNG state, original branch conventions and source probability bounds are retained. No whole-tool/paper or author-environment reproduction, OS memory containment, signature or host-authentication claim.";
    static final String ACTION="SKIP3: DEPENDENT ANALYSIS SKIPPED. Supply the exact named permitted file/Java17+ or complete qualified producer JAR; if unavailable or installation is prohibited, keep the skip. FAIL2: NO COMPLETE RESULT. Retain requests, inputs, raw/partial diagnostics, NA/NaN/Inf and failed/superseded evidence; fix the stated input, numerical, resource, timeout or integrity problem and retry NEW output. Read analysis-status.json and WARNINGS.md. Do not repair aliases, impute omitted associations, replace strict probability bounds by zero, add Monte Carlo pseudocounts, clamp original multivariable distortion values or substitute a different engine. VERIFY recomputes the full bounded native calculation; it uses no R process or network. Retain the exact producer JAR for future verification/replay. SHA256 checks byte integrity, not authenticity.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }

    static Map<String,Object> validate(Map<String,Object> raw,Path directory)throws IOException {
        return validate(raw,address(directory.toAbsolutePath().normalize()),pathStyle());
    }
    static Map<String,Object> validate(Map<String,Object> raw,String base,String style)throws IOException {
        keys(raw,Set.of("schema_version","mode","engine","profile","operation","out","inputs","options","rng","limits","policies"));
        integer(raw.get("schema_version"),1,1);choice(raw,"mode",Set.of("RUN"));choice(raw,"engine",Set.of("native"));
        choice(raw,"profile",Set.of(PROFILE));choice(raw,"operation",Set.of("ANALYZE"));
        var result=new LinkedHashMap<>(raw);result.put("out",resolveAddress(base,text(raw.get("out"),"out"),style));
        var input=object(raw.get("inputs"));keys(input,Set.of("associations","id_column","beta_outcome","se_outcome","beta_exposure","se_exposure"));
        var selected=new LinkedHashMap<>(input);selected.put("associations",resolveAddress(base,text(input.get("associations"),"associations"),style));
        var labels=new ArrayList<String>();for(String key:List.of("id_column","beta_outcome","se_outcome"))labels.add(column(input.get(key)));
        var exposures=columns(input.get("beta_exposure"));var errors=columns(input.get("se_exposure"));
        require(exposures.size()==errors.size(),"Exposure/error column counts differ");labels.addAll(exposures);labels.addAll(errors);
        require(labels.stream().distinct().count()==labels.size(),"Selected columns must be distinct literal names; no inferred binding or repair");
        selected.put("beta_exposure",exposures);selected.put("se_exposure",errors);result.put("inputs",selected);
        var options=raw.containsKey("options")?object(raw.get("options")):map();
        keys(options,Set.of("OUTLIERtest","DISTORTIONtest","SignifThreshold","NbDistribution"));
        double alpha=probability(options.getOrDefault("SignifThreshold",.05)).doubleValue();
        result.put("options",map("OUTLIERtest",bool(options,"OUTLIERtest",false),"DISTORTIONtest",bool(options,"DISTORTIONtest",false),
            "SignifThreshold",alpha,"NbDistribution",bound(options,"NbDistribution",1000,1,1_000_000)));
        var rng=object(raw.get("rng"));keys(rng,Set.of("kind","sample_kind","seed","state"));
        choice(rng,"kind",Set.of("Mersenne-Twister/Inversion"));String sampling=choice(rng,"sample_kind",Set.of("Rejection","Rounding"));
        require(rng.containsKey("seed")!=rng.containsKey("state"),"Declare exactly one explicit R seed or continuation state");
        var declared=new LinkedHashMap<>(rng);
        if(rng.containsKey("seed"))declared.put("seed",integer(rng.get("seed"),Integer.MIN_VALUE+1L,Integer.MAX_VALUE));
        else{
            var values=list(rng.get("state"));require(values.size()==626,"Require626 R RNG integers");var state=new ArrayList<Object>();int[] parsed=new int[626];
            for(int i=0;i<parsed.length;i++){parsed[i]=(int)integer(values.get(i),Integer.MIN_VALUE,Integer.MAX_VALUE);state.add(parsed[i]);}
            require(parsed[0]==(sampling.equals("Rejection")?10403:403),"R state and declared sample kind differ");
            try{new MrPressoRandom(parsed);}catch(IllegalArgumentException bad){throw new IOException(bad.getMessage(),bad);}declared.put("state",state);
        }
        result.put("rng",declared);
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();
        keys(limits,Set.of("maximum_input_bytes","maximum_rows","maximum_cells","maximum_estimated_work","timeout_seconds"));
        result.put("limits",map("maximum_input_bytes",bound(limits,"maximum_input_bytes",8_388_608,1,67_108_864),
            "maximum_rows",bound(limits,"maximum_rows",20_000,1,100_000),"maximum_cells",bound(limits,"maximum_cells",1_000_000,1,8_000_000),
            "maximum_estimated_work",bound(limits,"maximum_estimated_work",1_000_000_000,1,1_000_000_000_000L),
            "timeout_seconds",bound(limits,"timeout_seconds",1800,1,86400)));
        var policies=raw.containsKey("policies")?object(raw.get("policies")):map();keys(policies,Set.of("allow_unreportable_fields"));
        result.put("policies",map("allow_unreportable_fields",bool(policies,"allow_unreportable_fields",false)));return result;
    }
    static boolean bool(Map<String,Object> value,String key,boolean fallback)throws IOException {
        Object raw=value.getOrDefault(key,fallback);require(raw instanceof Boolean,"Require Boolean "+key);return (Boolean)raw;
    }
    static String column(Object value)throws IOException {
        String name=text(value,"column name");require(!name.isEmpty()&&name.length()<=256&&name.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Require literal nonempty column name without control/unpaired-surrogate characters");return name;
    }
    static List<String> columns(Object value)throws IOException {var result=new ArrayList<String>();for(Object item:list(value))result.add(column(item));require(!result.isEmpty()&&result.size()<=64,"Require1..64 exposure columns");return result;}
    static long limit(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    static String pathStyle(){return java.io.File.separatorChar=='\\'?"WINDOWS":"POSIX";}
    static String address(Path path){String value=path.toString();return pathStyle().equals("WINDOWS")?value.replace('\\','/'):value;}
    /** Pure lexical binding also works when a saved Windows capture is verified on Linux. */
    static String resolveAddress(String base,String value,String style)throws IOException {
        require(Set.of("WINDOWS","POSIX").contains(style),"Unknown saved path style");
        require(!value.isEmpty()&&value.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Require literal local path without controls/unpaired surrogates");
        boolean windows=style.equals("WINDOWS");String raw=windows?value.replace('\\','/'):value;String combined,prefix;
        if(windows){
            require(base.matches("^[A-Za-z]:/.*")&&!raw.startsWith("/"),"Use explicit drive-absolute or relative Windows paths; UNC/drive-root-relative paths are not supported");
            boolean absolute=raw.matches("^[A-Za-z]:/.*");require(absolute||!raw.matches("^[A-Za-z]:.*"),"Drive-relative paths are ambiguous; use a full absolute path");
            combined=absolute?raw:base+"/"+raw;prefix=combined.substring(0,3);combined=combined.substring(3);
        }else{require(base.startsWith("/"),"Require absolute saved POSIX base");combined=raw.startsWith("/")?raw:base+"/"+raw;prefix="/";combined=combined.substring(1);}
        var parts=new java.util.ArrayDeque<String>();for(String part:combined.split("/",-1)){
            if(part.isEmpty()||part.equals("."))continue;
            if(part.equals("..")){require(!parts.isEmpty(),"Path traverses above its absolute root");parts.removeLast();}else parts.addLast(part);
        }
        return prefix+String.join("/",parts);
    }
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Typed scientific choices for original, single-trait diploid BED workflows. */
final class PrsiceContracts {
    private PrsiceContracts() { }
    static final String PROFILE = "prsice-2.3.5-9dbf55e";
    static final String COMPLETE = "COMPLETE_SCOPED_ORIGINAL_PRSICE_WITH_REVIEWED_WARNINGS";
    static final String ACTION = "Required action: read WARNINGS.md, analysis-status.json, scientific-audit.json, sample-ledger.tsv, variant-ledger.tsv and original worker logs before using results. Preserve partial, nonfinite and original values; correct the named input/policy/capacity cause and retry NEW output. Missing or prohibited exact runtime/JAR/input means SKIP3 and dependent analysis skipped: install/stage it only where permitted, otherwise retain the skip. Do not refresh old producer/source hashes, repair alleles or identifiers silently, infer missing scientific choices, or treat in-sample tuning as held-out prediction. Original GLM convergence is not exposed; finite fields are not a convergence certificate. Keep genotype/phenotype captures and original paths private.";
    static final Map<String,String> INPUT_FILES;
    static {
        var files = new LinkedHashMap<String,String>();
        files.put("base","base.data");
        for (String group : List.of("target","reference"))
            for (String ext : List.of("bed","bim","fam")) files.put(group+"_"+ext,group+"."+ext);
        for (String role : List.of("phenotype","covariates","target_keep","target_remove","reference_keep","reference_remove","extract","exclude")) files.put(role,role+".txt");
        INPUT_FILES = Collections.unmodifiableMap(files);
    }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message,Throwable cause) { super(message,cause); }
    }
    static Map<String,Object> validate(Map<String,Object> raw) throws IOException {
        exact(raw,Set.of("schema_version","mode","profile","operation","runtime_home","bundle","inputs","parameters","policies","limits","out"),"RUN request");
        integer(raw.get("schema_version"),1,1);
        require("RUN".equals(raw.get("mode")) && PROFILE.equals(raw.get("profile")),"Require schema 1, mode RUN and exact "+PROFILE);
        String operation = choice(raw,"operation",Set.of("CLUMP","SCORE","PREDICT"));
        for (String field : List.of("runtime_home","bundle","out")) require(!text(raw.get(field),field).isBlank(),"Declare nonblank "+field);
        var inputs = object(raw.get("inputs")); keys(inputs,INPUT_FILES.keySet());
        for (String role : List.of("base","target_bed","target_bim","target_fam")) require(inputs.containsKey(role),"Missing input role "+role);
        for (var entry : inputs.entrySet()) ScenicWorkflowFiles.relative(text(entry.getValue(),entry.getKey()));
        for (String group : List.of("target","reference")) require(!(inputs.containsKey(group+"_keep") && inputs.containsKey(group+"_remove")),"Keep/remove are mutually exclusive for "+group);
        require(!(inputs.containsKey("extract") && inputs.containsKey("exclude")),"Extract/exclude are mutually exclusive");

        var p = object(raw.get("parameters"));
        exact(p,Set.of("assembly","target_population","reference_population","ignore_fid","id_delimiter","base","clumping","filters","alleles","scoring","phenotype","regression","include_nonfounders","seed","threads","memory_mib"),"parameters");
        label(p.get("assembly"),"assembly"); label(p.get("target_population"),"target_population");
        boolean ignoreFid = flag(p,"ignore_fid");
        if (ignoreFid) require(p.get("id_delimiter")==null,"ignore_fid requires id_delimiter=null; it is unused");
        else { String delimiter=token(p.get("id_delimiter"),"id_delimiter"); require(delimiter.length()<=16,"Use an explicit ID delimiter of at most 16 characters"); }
        flag(p,"include_nonfounders"); integer(p.get("seed"),0,4294967295L); integer(p.get("threads"),1,256); integer(p.get("memory_mib"),16,1048576);
        var base = object(p.get("base")); exact(base,Set.of("kind","effect_scale","columns"),"base");
        String kind = choice(base,"kind",Set.of("GWAS","PREPARED_WEIGHTS")); choice(base,"effect_scale",Set.of("BETA","OR"));
        var columns = object(base.get("columns")); exact(columns,Set.of("snp","chromosome","position","a1","a2","effect","pvalue"),"base columns");
        for (String name : List.of("snp","a1","a2","effect")) column(columns.get(name),"base "+name);
        if (kind.equals("GWAS")) for (String name : List.of("chromosome","position","pvalue")) column(columns.get(name),"base "+name);
        else { require(columns.get("pvalue")==null,"Prepared weights use an inclusion marker P=1, not an association P-value"); require((columns.get("chromosome")==null)==(columns.get("position")==null),"Declare both prepared-weight coordinate columns or neither"); if(columns.get("chromosome")!=null){column(columns.get("chromosome"),"chromosome");column(columns.get("position"),"position");} }
        var distinctColumns = new HashSet<String>(); for(Object value:columns.values()) if(value!=null)require(distinctColumns.add((String)value),"Base column roles must be distinct");

        var clumping=object(p.get("clumping")); exact(clumping,Set.of("mode","r2","window_kb","pvalue"),"clumping");
        String clump=choice(clumping,"mode",Set.of("NONE","TARGET","REFERENCE"));
        if(clump.equals("NONE")) require(clumping.get("r2")==null && clumping.get("window_kb")==null && clumping.get("pvalue")==null,"Unused clumping fields must be null");
        else { for(String key:List.of("r2","pvalue")){double n=finite(clumping.get(key),key);require(n>=0&&n<=1,"Clumping "+key+" outside [0,1]");} integer(clumping.get("window_kb"),1,1000000000L); }
        require(!operation.equals("CLUMP")||!clump.equals("NONE"),"CLUMP requires target or reference clumping");
        var alleles=object(p.get("alleles")); exact(alleles,Set.of("palindromic","strand"),"alleles");choice(alleles,"palindromic",Set.of("DROP","KEEP_ORIGINAL_WITH_WARNING"));choice(alleles,"strand",Set.of("EXACT","ALLOW_ORIGINAL_COMPLEMENT_WITH_WARNING"));

        Map<String,Object> scoring=null;
        if(operation.equals("CLUMP"))require(p.get("scoring")==null && p.get("phenotype")==null && p.get("regression")==null,"CLUMP has no scoring/phenotype/regression choices");
        else {
            scoring=object(p.get("scoring")); exact(scoring,Set.of("model","missing","scale","cumulative","frequency_source","thresholds"),"scoring");
            choice(scoring,"model",Set.of("add","dom","rec","het"));choice(scoring,"missing",Set.of("MEAN_IMPUTE","SET_ZERO","CENTER"));choice(scoring,"scale",Set.of("sum","avg","std","con-std"));flag(scoring,"cumulative");
            String frequency=choice(scoring,"frequency_source",Set.of("TARGET","REFERENCE"));require(!(frequency.equals("REFERENCE")&&clump.equals("TARGET")),"Original --ld also selects the clumping panel; target clumping with separate reference frequencies cannot be represented");
            thresholds(object(scoring.get("thresholds")));
        }
        boolean reference=clump.equals("REFERENCE") || scoring!=null&&"REFERENCE".equals(scoring.get("frequency_source"));
        for(String ext:List.of("bed","bim","fam"))require(inputs.containsKey("reference_"+ext)==reference,"Reference BED/BIM/FAM must be supplied exactly when used");
        if(reference)label(p.get("reference_population"),"reference_population");else require(p.get("reference_population")==null && !inputs.containsKey("reference_keep") && !inputs.containsKey("reference_remove"),"Unused reference population/selection must be absent/null");
        var filters=object(p.get("filters")); exact(filters,Set.of("target","reference","base_info","base_maf"),"filters"); genotypeFilter(object(filters.get("target")));
        if(reference)genotypeFilter(object(filters.get("reference")));else require(filters.get("reference")==null,"Unused reference filters must be null");
        for(String name:List.of("base_info","base_maf"))if(filters.get(name)!=null){var filter=object(filters.get(name));exact(filter,Set.of("column","minimum"),name);require(!column(filter.get("column"),name+" column").contains(":"),"Original base-filter syntax cannot represent a colon in its column name");double min=finite(filter.get("minimum"),name);require(min>=0 && min<=(name.equals("base_maf")?.5:1),"Invalid "+name+" minimum");require(kind.equals("GWAS"),"Prepared-weight inputs have no base GWAS INFO/MAF filters");}

        boolean needsPheno=operation.equals("PREDICT") || scoring!=null&&"con-std".equals(scoring.get("scale"));
        if(needsPheno){
            var pheno=object(p.get("phenotype"));exact(pheno,Set.of("kind","source","column","prevalence"),"phenotype");String trait=choice(pheno,"kind",Set.of("BINARY","QUANTITATIVE"));String origin=choice(pheno,"source",Set.of("FAM","FILE"));
            require(inputs.containsKey("phenotype")==origin.equals("FILE"),"Phenotype input is required exactly for source FILE");if(origin.equals("FILE"))column(pheno.get("column"),"phenotype column");else require(pheno.get("column")==null,"FAM phenotype has no named column");
            if(scoring!=null&&"con-std".equals(scoring.get("scale")))require(trait.equals("BINARY"),"Control standardization requires an explicitly binary phenotype");
            if(pheno.get("prevalence")!=null){double prevalence=finite(pheno.get("prevalence"),"prevalence");require(operation.equals("PREDICT")&&trait.equals("BINARY")&&prevalence>0&&prevalence<1,"Prevalence requires binary prediction and 0<p<1");}
        }else require(p.get("phenotype")==null && !inputs.containsKey("phenotype"),"No unused phenotype is accepted; ordinary SCORE standardization follows original selection masks without outcome loading");
        if(operation.equals("PREDICT")){
            var regression=object(p.get("regression"));exact(regression,Set.of("covariates","categorical","permutations","logistic_permutations","evaluation_role"),"regression");
            Set<String> cov=columnList(regression.get("covariates")), categorical=columnList(regression.get("categorical"));require(cov.containsAll(categorical),"Categorical covariates must be selected covariates");require(inputs.containsKey("covariates")==!cov.isEmpty(),"Covariate input is required exactly when covariates are selected");
            long permutations=integer(regression.get("permutations"),0,10000000);boolean logit=flag(regression,"logistic_permutations");require(!logit || permutations>0&&"BINARY".equals(object(p.get("phenotype")).get("kind")),"Logistic permutations require positive permutations and binary prediction");
            String role=choice(regression,"evaluation_role",Set.of("IN_SAMPLE_TUNING","FIXED_SCORE_ASSOCIATION"));
            if(role.equals("FIXED_SCORE_ASSOCIATION")){var threshold=object(scoring.get("thresholds"));require("LEVELS".equals(threshold.get("kind"))&&list(threshold.get("levels")).size()==1,"Fixed-score association requires exactly one explicit P threshold");}
        }else require(p.get("regression")==null && !inputs.containsKey("covariates"),"SCORE/CLUMP do not fit regressions or consume covariates");
        if(kind.equals("PREPARED_WEIGHTS")){require(!operation.equals("CLUMP")&&clump.equals("NONE")&&Boolean.TRUE.equals(scoring.get("cumulative")),"Prepared weights require cumulative, unclumped scoring/association");var th=object(scoring.get("thresholds"));require("LEVELS".equals(th.get("kind"))&&list(th.get("levels")).size()==1&&finite(list(th.get("levels")).get(0),"inclusion marker")==1,"Prepared weights require the single inclusion marker threshold 1");}
        var policies=object(raw.get("policies"));exact(policies,Set.of("unmatched_variants","incomplete_samples","original_warnings","solver_convergence","standardization"),"policies");
        for(String name:List.of("unmatched_variants","incomplete_samples","original_warnings"))choice(policies,name,Set.of("ERROR","RETAIN_WITH_WARNING"));
        choice(policies,"solver_convergence",Set.of("SOURCE_UNREPORTED_WITH_WARNING","REQUIRE_REPORTED_CONVERGENCE"));choice(policies,"standardization",Set.of("ORIGINAL_MASK_WITH_WARNING","FAIL_ON_INCOMPLETE_FIT_ROWS"));
        var result=new LinkedHashMap<>(raw);var supplied=object(raw.get("limits"));keys(supplied,LIMITS.keySet());var limits=map();for(String key:new TreeSet<>(LIMITS.keySet())){long[] range=LIMITS.get(key);limits.put(key,bound(supplied,key,range[0],range[1],range[2]));}result.put("limits",limits);
        if(scoring!=null){var t=object(scoring.get("thresholds"));if("LEVELS".equals(t.get("kind")))require(list(t.get("levels")).size()<=number(limits,"maximum_thresholds"),"Requested threshold count exceeds maximum_thresholds; review the bound explicitly");}
        return result;
    }
    private static final Map<String,long[]> LIMITS=Map.ofEntries(
        Map.entry("timeout_seconds",new long[]{3600,1,604800}),Map.entry("maximum_files",new long[]{100000,1,5000000}),
        Map.entry("maximum_member_bytes",new long[]{8589934592L,1,1125899906842624L}),Map.entry("maximum_input_bytes",new long[]{34359738368L,1,1125899906842624L}),Map.entry("maximum_output_bytes",new long[]{68719476736L,1,1125899906842624L}),
        Map.entry("maximum_rows",new long[]{10000000,1,1000000000}),Map.entry("maximum_samples",new long[]{2000000,1,50000000}),Map.entry("maximum_variants",new long[]{20000000,1,100000000}),
        Map.entry("maximum_line_bytes",new long[]{4194304,32,67108864}),Map.entry("maximum_thresholds",new long[]{10000,1,1000000}),Map.entry("maximum_native_cells",new long[]{200000000,1,100000000000000L}),
        Map.entry("maximum_scratch_bytes",new long[]{17179869184L,1,1125899906842624L}),Map.entry("scratch_block_bytes",new long[]{16777216,1048576,268435456}));
    static void exact(Map<String,Object> value,Set<String> expected,String label)throws IOException {require(value.keySet().equals(expected),label+" must declare exactly "+new TreeSet<>(expected));}
    static boolean flag(Map<String,Object> map,String key)throws IOException {Object value=map.get(key);require(value instanceof Boolean,"Declare Boolean "+key);return (Boolean)value;}
    static boolean equal(double a,double b){return a==b||Math.abs(a-b)<Math.abs(Math.min(a,b))*Math.ulp(1.0);}
    static double finite(Object value,String label)throws IOException {BigDecimal exact=decimal(value);double n=exact.doubleValue();require(Double.isFinite(n),"Nonfinite "+label);if(n==0)require(exact.signum()==0,"Nonzero "+label+" underflows source double; no implicit flooring");else require(Math.abs(n)>=Double.MIN_NORMAL,"Subnormal "+label+" is rejected by the original PRSice double parser; no implicit flooring");return n;}
    static String label(Object value,String name)throws IOException {String s=text(value,name);require(!s.isBlank()&&s.length()<=4096&&s.codePoints().noneMatch(Character::isISOControl),"Require a nonblank label without control characters: "+name);return s;}
    static String token(Object value,String label)throws IOException {String text=text(value,label);require(!text.isBlank()&&text.codePoints().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)),"Require a nonblank single token for "+label);return text;}
    static String column(Object value,String label)throws IOException {String s=token(value,label);require(!s.contains(",")&&!s.startsWith("@"),"Declare one literal column name, without comma or @ expansion: "+label);return s;}
    static Set<String> columnList(Object value)throws IOException {var names=new LinkedHashSet<String>();for(Object item:list(value))require(names.add(column(item,"covariate")),"Duplicate covariate name");return names;}
    static void genotypeFilter(Map<String,Object> value)throws IOException {exact(value,Set.of("maf","missing"),"genotype filter");double maf=finite(value.get("maf"),"MAF"),missing=finite(value.get("missing"),"missingness");require(maf>=0&&maf<=.5&&missing>=0&&missing<=1,"Require MAF in [0,.5] and missingness in [0,1]");}
    static void thresholds(Map<String,Object> value)throws IOException {
        String kind=choice(value,"kind",Set.of("LEVELS","GRID"));
        if(kind.equals("LEVELS")){exact(value,Set.of("kind","levels"),"threshold levels");var levels=list(value.get("levels"));require(!levels.isEmpty(),"Require explicit P thresholds");double previous=-1;for(Object item:levels){double n=finite(item,"P threshold");require(n>=0&&n<=1&&n>previous,"P thresholds must be distinct and increasing in [0,1]");previous=n;}}
        else {exact(value,Set.of("kind","lower","upper","interval","include_full"),"threshold grid");double lower=finite(value.get("lower"),"lower"),upper=finite(value.get("upper"),"upper"),step=finite(value.get("interval"),"interval");require(lower>=0&&upper<=1&&lower<=upper&&step>=Double.MIN_NORMAL,"Require 0<=lower<=upper<=1 and a positive normal-double interval");flag(value,"include_full");}
    }
    static long cap(Map<String,Object> request,String name)throws IOException {return number(object(request.get("limits")),name);}
    static Map<String,Object> canonical(Map<String,Object> validated) {var result=new LinkedHashMap<>(validated);result.remove("runtime_home");result.remove("bundle");result.remove("out");var inputs=new LinkedHashMap<String,Object>();for(String role:objectUnchecked(validated.get("inputs")).keySet())inputs.put(role,INPUT_FILES.get(role));result.put("inputs",inputs);return result;}
    @SuppressWarnings("unchecked") private static Map<String,Object> objectUnchecked(Object value){return (Map<String,Object>)value;}
}

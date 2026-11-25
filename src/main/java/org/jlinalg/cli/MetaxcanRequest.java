/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Typed original-source contracts; no inferred release, model, allele or regression choice. */
final class MetaxcanRequest {
    static final Set<String> EDITIONS=Set.of("tag-0.7.3","commit-0.7.4","tag-0.8.1");
    static final Set<String> MODES=Set.of("summary","summary-joint","predict","predict-associate","associate","associate-joint");
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String s){super(s);}}
    record Named(String name,String member) { }
    record Text(String member,boolean gzip) { }
    record Columns(String snp,String effectAllele,String otherAllele,String zscore,String beta,
                   String pvalue,String se,String oddsRatio,String betaSign,String separator) { }
    record Capacity(int models,int genes,int variants,int samples,long inputBytes,long decodedBytes,
                    long outputBytes,long numericBytes,long multiplyAdds,long wallMillis) { }
    record Spec(Map<?,?> document,String edition,String mode,String build,String engine,List<Named> models,
                List<Named> expressions,Map<String,Text> texts,Map<String,String> originals,
                Columns columns,String phenotypeColumn,List<String> covariateColumns,
                Map<?,?> settings,Capacity capacity,NextflowProcess.Limits process) {
        boolean summary(){return mode.startsWith("summary");}
        boolean predicts(){return mode.startsWith("predict");}
        boolean association(){return !mode.equals("predict");}
        boolean joint(){return mode.endsWith("-joint");}
        boolean individual(){return !summary();}
        boolean nativeEngine(){return engine.equals("native");}
    }
    private MetaxcanRequest(){ }
    static Spec read(Path file,boolean live)throws IOException{String raw=utf8(bytes(file,1048576));if(raw.startsWith("\ufeff"))raw=raw.substring(1);return parse(object(SimpleJson.parse(raw)),live);}
    static Spec parse(Map<?,?> source,boolean live)throws IOException {
        keys(source,"schema_version","edition","mode","genome_build","engine","data","settings","capacity","process");integer(source.get("schema_version"),1,1);
        String edition=text(source.get("edition")),mode=text(source.get("mode")),build=text(source.get("genome_build")),engine=text(source.get("engine"));require(Set.of("native","original").contains(engine),"Choose explicit native or original execution");
        if(!EDITIONS.contains(edition))throw new Unavailable("No qualified exact MetaXcan source selection: "+edition+". Select a pinned tag/commit; tag v0.8.1 has internal package version0.7.5, not0.8.1");
        if(!MODES.contains(mode))throw new Unavailable("No qualified MetaXcan workflow mode: "+mode+". Prediction-model training belongs to a separate training workflow; it is not inferred from the MetaXcan name");
        if(engine.equals("native")&&!mode.startsWith("summary"))throw new Unavailable("The native MetaXcan workflow currently supports summary and summary-joint. Individual prediction/association requires its separately qualified original adapter; no engine is substituted");
        require(!build.isBlank()&&build.length()<=1024&&build.chars().noneMatch(c->c<32||c==127),"Require an explicit assembly/input-provider label; this does not verify assembly identity");
        var raw=object(source.get("capacity"));keys(raw,"models","genes","variants","samples","input_bytes","decoded_bytes","output_bytes","numeric_bytes","multiply_adds","wall_millis");
        var cap=new Capacity(count(raw,"models",64),count(raw,"genes",1000000),count(raw,"variants",100000000),count(raw,"samples",10000000),bound(raw,"input_bytes",1000000000000L),bound(raw,"decoded_bytes",10000000000000L),bound(raw,"output_bytes",1000000000000L),bound(raw,"numeric_bytes",1099511627776L),bound(raw,"multiply_adds",Long.MAX_VALUE),bound(raw,"wall_millis",604800000));
        var data=object(source.get("data"));var originals=new LinkedHashMap<String,String>();var texts=new LinkedHashMap<String,Text>();var models=new ArrayList<Named>();var expressions=new ArrayList<Named>();Columns columns=null;String phenotype=null;var covariates=new ArrayList<String>();
        boolean summary=mode.startsWith("summary"),predicts=mode.startsWith("predict"),association=!mode.equals("predict");
        if(summary){
            if(mode.equals("summary-joint"))keys(data,"models","gwas","covariance","model_product");else keys(data,"models","gwas","covariance");models.addAll(models(data.get("models"),originals,live));
            var g=object(data.get("gwas"));keys(g,"file","compression","columns","separator");texts.put("gwas",textFile(g,"inputs/gwas.tsv",originals,live,false));
            var c=object(g.get("columns"));keys(c,"snp","effect_allele","non_effect_allele","zscore","beta","pvalue","se","odds_ratio","beta_sign");
            String snp=column(c.get("snp")),ea=column(c.get("effect_allele")),oa=column(c.get("non_effect_allele")),z=optionalColumn(c.get("zscore")),beta=optionalColumn(c.get("beta")),pvalue=optionalColumn(c.get("pvalue")),se=optionalColumn(c.get("se")),odds=optionalColumn(c.get("odds_ratio")),sign=optionalColumn(c.get("beta_sign")),separator=text(g.get("separator"));
            var distinct=new HashSet<String>();for(String col:new String[]{snp,ea,oa,z,beta,pvalue,se,odds,sign})require(col==null||distinct.add(col),"GWAS columns must be explicit and distinct");
            require(z!=null||(pvalue!=null&&(beta!=null||odds!=null||sign!=null))||(se!=null&&(beta!=null||odds!=null)),"Require source Z, or P with an effect/sign, or SE with BETA/OR; source precedence is Z then P then BETA/SE");
            require(Set.of("tab","comma","whitespace").contains(separator),"Declare tab, comma or whitespace GWAS separation");columns=new Columns(snp,ea,oa,z,beta,pvalue,se,odds,sign,separator);
            texts.put("covariance",textFile(object(data.get("covariance")),"inputs/covariance.tsv",originals,live,true));
            if(mode.equals("summary-joint"))texts.put("model_product",textFile(object(data.get("model_product")),"inputs/model-product.tsv",originals,live,true));
        }else {
            var wanted=new HashSet<String>();if(predicts)wanted.addAll(Set.of("models","genotypes"));else wanted.addAll(Set.of("expressions","sample_ids"));if(association)wanted.addAll(Set.of("phenotype","covariates"));require(data.keySet().equals(wanted),"Unexpected/missing individual workflow data fields");
            if(predicts){
                models.addAll(models(data.get("models"),originals,live));var g=object(data.get("genotypes"));keys(g,"file","compression","sample_ids");
                // This retained capture name satisfies the original0.7.3/.4 file
                // sorter; chromosome values remain the unchanged line contents.
                texts.put("genotypes",textFile(g,"inputs/genotypes.chr1.txt",originals,live,false));texts.put("sample_ids",textFile(object(g.get("sample_ids")),"inputs/sample-ids.tsv",originals,live,true));
            }else {
                var used=new HashSet<String>();
                for(Object item:list(data.get("expressions"))){var f=object(item);keys(f,"name","file","compression");String name=name(f.get("name"));require(used.add(name.toLowerCase(Locale.ROOT)),"Expression names must be unique across Windows and Linux without case folding");Text t=textFile(f,"inputs/expressions/"+name+".txt",originals,live,false);texts.put("expression:"+name,t);expressions.add(new Named(name,t.member()));}
                require(!expressions.isEmpty()&&expressions.size()<=64,"Require1..64 named expression inputs");texts.put("sample_ids",textFile(object(data.get("sample_ids")),"inputs/sample-ids.tsv",originals,live,true));
            }
            if(association){var p=object(data.get("phenotype"));keys(p,"file","compression","column");texts.put("phenotype",textFile(p,"inputs/phenotype.tsv",originals,live,false));phenotype=column(p.get("column"));
                if(data.get("covariates")!=null){var c=object(data.get("covariates"));keys(c,"file","compression","columns");texts.put("covariates",textFile(c,"inputs/covariates.tsv",originals,live,false));for(Object value:list(c.get("columns")))covariates.add(formulaColumn(value));require(!covariates.isEmpty()&&covariates.size()<=64&&new HashSet<>(covariates).size()==covariates.size(),"Require1..64 distinct explicit covariates");}
            }
        }
        require(models.size()<=cap.models()&&expressions.size()<=cap.models(),"Declared model capacity exceeded");
        var settings=object(source.get("settings"));
        if(summary){
            keys(settings,"calibration","joint_cutoff","regularization","keep_non_rsid","pvalue_policy");bool(settings.get("keep_non_rsid"));require(Set.of("source-default-divergent-floor","discard-divergent").contains(settings.get("pvalue_policy")),"Declare the original default divergent-P floor or discard-divergent (--input_pvalue_fix 0)");var calibration=object(settings.get("calibration"));String type=text(calibration.get("mode"));
            if(type.equals("source-uncalibrated"))keys(calibration,"mode");else if(type.equals("source-phi")){keys(calibration,"mode","gwas_samples","gwas_heritability");require(edition.equals("tag-0.8.1"),"Phi calibration belongs to explicit release-tag v0.8.1; no historical correction is inferred");integer(calibration.get("gwas_samples"),1,Long.MAX_VALUE);finite(calibration.get("gwas_heritability"));}else throw new IOException("Choose explicit source-uncalibrated or source-phi calibration");
            if(mode.equals("summary-joint")){jointCutoff(object(settings.get("joint_cutoff")));if(settings.get("regularization")!=null)require(finite(settings.get("regularization"))>=0,"Regularization must be nonnegative");}
            else require(settings.get("joint_cutoff")==null&&settings.get("regularization")==null,"Single-tissue summary requests require null joint_cutoff and regularization");
        }else {
            var fields=new HashSet<String>();if(predicts)fields.addAll(Set.of("prediction_format","skip_palindromic"));if(association)fields.addAll(Set.of("regression","missing","covariate_method"));if(mode.equals("associate-joint"))fields.addAll(Set.of("pca_cutoff","standardize_expression"));require(settings.keySet().equals(fields),"Unexpected/missing individual workflow settings");
            if(predicts){require("source-text".equals(settings.get("prediction_format")),"Select qualified source-text prediction output; HDF5 source-format capability is separately audited");bool(settings.get("skip_palindromic"));}
            if(association){String regression=text(settings.get("regression"));require(Set.of("linear","logistic").contains(regression),"Select explicit linear or logistic association");require("source-default-pandas-and-minus999".equals(settings.get("missing")),"Declare the original missing-value policy explicitly; numeric -999 within1e-3 is treated as missing");String method=text(settings.get("covariate_method"));if(covariates.isEmpty())require(method.equals("none"),"No covariate file requires explicit covariate_method none");else require(method.equals("source-phenotype-residualization")&&regression.equals("linear"),"Original covariates residualize only phenotype and FORCE LINEAR regression; choose that explicit method or supply a separately prepared phenotype");}
            if(mode.equals("associate-joint")){var c=object(settings.get("pca_cutoff"));keys(c,"kind","value");String kind=text(c.get("kind"));if(kind.equals("condition-number"))integer(c.get("value"),1,Integer.MAX_VALUE);else require(kind.equals("eigen-ratio")&&finite(c.get("value"))>0&&finite(c.get("value"))<=1,"Choose explicit positive source PCA eigen-ratio or integer condition-number cutoff");bool(settings.get("standardize_expression"));}
        }
        NextflowProcess.Limits process=null;
        if(engine.equals("native"))require(source.get("process")==null,"Native Java execution requires explicit null process; Java numerical work uses the declared capacity fields");else {
        var p=object(source.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        try{process=new NextflowProcess.Limits(positive(p,"wall_millis"),positive(p,"address_space_bytes"),positive(p,"cpu_seconds"),positive(p,"file_bytes"),small(p,"open_files"),positive(p,"workspace_bytes"),small(p,"file_count"),small(p,"processes"),small(p,"threads"),positive(p,"sampled_rss_bytes"),small(p,"monitor_millis"));}catch(IllegalArgumentException e){throw new IOException("Invalid original MetaXcan process capacities",e);}
        require(Math.addExact(Math.addExact(cap.inputBytes(),cap.outputBytes()),16777216)<=process.workspaceBytes(),"Process workspace must include full captured input/output bounds and16MiB metadata reserve");
        }
        var document=object(SimpleJson.parse(NextflowJson.json(source)));
        return new Spec(Collections.unmodifiableMap(new LinkedHashMap<>(document)),edition,mode,build,engine,List.copyOf(models),List.copyOf(expressions),Collections.unmodifiableMap(texts),Collections.unmodifiableMap(originals),columns,phenotype,List.copyOf(covariates),Collections.unmodifiableMap(new LinkedHashMap<>(settings)),cap,process);
    }
    private static List<Named> models(Object value,Map<String,String> originals,boolean live)throws IOException {
        var result=new ArrayList<Named>();var names=new HashSet<String>();for(Object item:list(value)){var f=object(item);keys(f,"name","file");String name=name(f.get("name"));require(names.add(name.toLowerCase(Locale.ROOT)),"Model names must be unique across Windows and Linux without case folding");String member="inputs/models/"+name+".db";originals.put(member,original(f.get("file"),live));result.add(new Named(name,member));}require(!result.isEmpty()&&result.size()<=64,"Require1..64 explicitly named original models");return result;
    }
    private static Text textFile(Map<?,?> value,String member,Map<String,String> originals,boolean live,boolean exact)throws IOException {
        if(exact)keys(value,"file","compression");String compression=text(value.get("compression"));require(Set.of("none","gzip").contains(compression),"Declare none or gzip compression; no format is guessed from a path");boolean gzip=compression.equals("gzip");String name=member+(gzip?".gz":"");require(originals.put(name,original(value.get("file"),live))==null,"Duplicate captured input slot");return new Text(name,gzip);
    }
    static void jointCutoff(Map<?,?> c)throws IOException {keys(c,"kind","value");String kind=text(c.get("kind"));double value=finite(c.get("value"));require(value>=0,"Source cutoff must be nonnegative");if(kind.equals("condition-number"))require(value>0,"Condition-number cutoff must be positive");else if(kind.equals("variance-threshold"))require(value<=1,"Variance threshold must be at most1");else require(Set.of("eigen-ratio","trace-ratio").contains(kind),"Unknown original joint cutoff");}
    static double finite(Object raw)throws IOException{if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException("Require a finite numeric option");return n.doubleValue();}
    static boolean bool(Object value)throws IOException{if(!(value instanceof Boolean b))throw new IOException("Require an explicit boolean");return b;}
    static String name(Object value)throws IOException{String n=text(value);require(n.matches("[A-Za-z][A-Za-z0-9_]{0,95}"),"Use an explicit unique model/expression label compatible with original Python formulas; no name is repaired");return n;}
    static String column(Object value)throws IOException{String n=text(value);require(!n.isBlank()&&n.length()<=1024&&n.chars().noneMatch(c->c<32||c==127),"Require a bounded explicit table header without control characters");return n;}
    private static String optionalColumn(Object value)throws IOException{return value==null?null:column(value);}
    private static String formulaColumn(Object value)throws IOException{String n=column(value);require(n.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"),"Covariate names must be explicit original Python formula identifiers; no name is repaired");return n;}
    private static int count(Map<?,?> p,String key,int max)throws IOException{return(int)integer(p.get(key),1,max);}
    private static long bound(Map<?,?> p,String key,long max)throws IOException{return integer(p.get(key),1,max);}
    private static long positive(Map<?,?> p,String key)throws IOException{return bound(p,key,Long.MAX_VALUE);}
    private static int small(Map<?,?> p,String key)throws IOException{return count(p,key,Integer.MAX_VALUE);}
    private static String original(Object value,boolean live)throws IOException{String name=text(value);require(!name.isBlank()&&name.length()<32768&&name.indexOf(0)<0,"Require an explicit local original input file");if(live){Path p=Path.of(name);require(p.isAbsolute()&&p.normalize().equals(p)&&p.toRealPath().equals(p)&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Require canonical absolute regular original input files");}return name;}
    private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
}

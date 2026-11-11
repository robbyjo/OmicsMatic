/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit SNPRelate1.46 requests; no paper settings, chromosome sets or random seeds are inferred. */
final class SnprelateRequest {
    private SnprelateRequest() { }
    record Limits(long inputBytes,long outputBytes,int outputFiles,int samples,int variants,int rows,long operations,long workingBytes,int seconds) { }
    record Request(String mode,Path bfile,Path profile,Path out,String layout,boolean autosomes,boolean removeMono,
            double maf,double missing,String method,String start,int seed,int windowBp,Integer windowVariants,double threshold,String family,SnprelatePcaAudit.Options pca,Limits limits) { }
    static void need(boolean condition,String cause)throws IOException {if(!condition)throw new IOException(cause);}
    static boolean bool(Object value)throws IOException {need(value instanceof Boolean,"Require explicit JSON boolean");return (Boolean)value;}
    static Path path(Path base,Object value)throws IOException {
        String s=text(value);need(!s.isEmpty()&&s.chars().noneMatch(c->c<32||c==127),"Invalid explicit SNPRelate path");return base.resolve(s).toAbsolutePath().normalize();
    }
    static double numeric(Object value)throws IOException {
        if(value instanceof String s)return switch(s){case "NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->throw new IOException("Numeric strings support NaN/Inf/-Inf only");};
        need(value instanceof NextflowParameters.NumberToken,"Require JSON number or explicit nonfinite token");
        double d=Double.parseDouble(((NextflowParameters.NumberToken)value).text());need(Double.isFinite(d),"Finite JSON number overflows double precision");return d;
    }
    static Request parse(Object value,Path base)throws IOException {
        Map<?,?> m=object(value);keys(m,"schema_version","version","mode","bfile","runtime_profile","out","layout","autosomes_only","remove_monomorphic","maf","missing_rate","threads","options","limits");
        integer(m.get("schema_version"),1,1);need(text(m.get("version")).equals("1.46.0"),"Require explicit SNPRelate version1.46.0; paper versions are not substituted");
        String mode=text(m.get("mode")),layout=text(m.get("layout"));need(Set.of("king-robust","ld-pruning","grm","pca").contains(mode),"Unsupported SNPRelate workflow");
        need(Set.of("snp-first","sample-first").contains(layout),"Declare original physical GDS layout explicitly");integer(m.get("threads"),1,1);
        Map<?,?> options=object(m.get("options"));String method="",start="",family="";int seed=0,bp=0;Integer window=null;double threshold=0;SnprelatePcaAudit.Options pca=null;
        if(mode.equals("ld-pruning")) {
            keys(options,"method","start","seed","window_bp","window_variants","ld_threshold");method=text(options.get("method"));start=text(options.get("start"));
            need(Set.of("composite","r","dprime","corr").contains(method),"Declare original SNPRelate LD measure; no r-squared or PLINK substitution");
            need(Set.of("random.f500","random","first","last").contains(start),"Unsupported original pruning start rule");
            seed=(int)integer(options.get("seed"),0,Integer.MAX_VALUE);bp=(int)integer(options.get("window_bp"),0,Integer.MAX_VALUE);
            if(options.get("window_variants")!=null)window=(int)integer(options.get("window_variants"),0,Integer.MAX_VALUE);
            threshold=numeric(options.get("ld_threshold"));need(Double.isFinite(threshold)&&threshold>=0&&threshold<=1,"Declare finite absolute LD threshold0..1");
        } else if(mode.equals("grm")) {
            keys(options,"method");method=text(options.get("method"));need(Set.of("GCTA","Eigenstrat","EIGMIX","Weighted","Corr","IndivBeta").contains(method),"Unsupported original SNPRelate GRM estimator");
        } else if(mode.equals("pca")) {
            pca=SnprelatePcaAudit.options(options);seed=pca.seed();
        } else {
            keys(options,"family_mode");family=text(options.get("family_mode"));need(Set.of("singletons","original-fid").contains(family),"Declare family context; no inference from input FIDs");
        }
        Map<?,?> l=object(m.get("limits"));keys(l,"input_bytes","output_bytes","output_files","samples","variants","rows","pair_variant_operations","working_bytes","wall_seconds");
        var limits=new Limits(integer(l.get("input_bytes"),1,1_000_000_000),integer(l.get("output_bytes"),1,10_000_000_000L),
            (int)integer(l.get("output_files"),1,100_000),(int)integer(l.get("samples"),2,5000),(int)integer(l.get("variants"),1,1_000_000),
            (int)integer(l.get("rows"),1,100_000_000),integer(l.get("pair_variant_operations"),1,Long.MAX_VALUE),integer(l.get("working_bytes"),1,Long.MAX_VALUE),
            (int)integer(l.get("wall_seconds"),1,604800));
        return new Request(mode,path(base,m.get("bfile")),path(base,m.get("runtime_profile")),path(base,m.get("out")),layout,bool(m.get("autosomes_only")),
            bool(m.get("remove_monomorphic")),numeric(m.get("maf")),numeric(m.get("missing_rate")),method,start,seed,bp,window,threshold,family,pca,limits);
    }
    static Map<String,String> parameters(Request r) {
        var p=new LinkedHashMap<String,String>();p.put("mode",r.mode());p.put("layout",r.layout());p.put("autosomes_only",Boolean.toString(r.autosomes()));
        p.put("remove_monomorphic",Boolean.toString(r.removeMono()));p.put("maf",number(r.maf()));p.put("missing_rate",number(r.missing()));p.put("threads","1");
        p.put("method",r.method());p.put("start",r.start());p.put("seed",Integer.toString(r.seed()));p.put("window_bp",Integer.toString(r.windowBp()));
        p.put("window_variants",r.windowVariants()==null?"NA":r.windowVariants().toString());p.put("ld_threshold",number(r.threshold()));p.put("family_mode",r.family());
        if(r.pca()!=null) {
            var o=r.pca();p.put("pca_algorithm",o.algorithm());p.put("pca_eigen_count",Integer.toString(o.eigenCount()));p.put("pca_bayesian",Boolean.toString(o.bayesian()));
            p.put("pca_eigen_method",o.solver());p.put("pca_aux_dim",Integer.toString(o.auxiliary()));p.put("pca_iterations",Integer.toString(o.iterations()));p.put("pca_need_genmat",Boolean.toString(o.genmat()));
        }return p;
    }
    static String number(double n){return Double.isNaN(n)?"NaN":n==Double.POSITIVE_INFINITY?"Inf":n==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(n);}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.MtagJson.*;

/** Typed original MTAG choices; no arbitrary argument/shell/program interpolation. */
final class MtagRequest {
    private MtagRequest() { }
    record Trait(String name,Path input) { }
    record Limits(int rows,long inputBytes,long outputBytes,long workingBytes,int seconds,int files,int gridPoints) { }
    record Request(Path source,Path profile,Path out,List<Trait> traits,Map<String,String> columns,Map<?,?> filters,
                   Map<?,?> covariance,Map<?,?> fdr,boolean standardized,boolean force,double median,Limits limits,List<Path> extraInputs,
                   boolean noFlipping,boolean union) { }
    static String choice(Object value,String... options)throws IOException {String s=text(value);need(Set.of(options).contains(s),"Unsupported explicit MTAG choice: "+s);return s;}
    static Path path(Path base,Object value)throws IOException {
        String s=text(value);need(!s.isEmpty()&&s.chars().noneMatch(c->c<32||c==127),"Invalid MTAG path");return base.resolve(s).toAbsolutePath().normalize();
    }
    static Path optional(Path base,Object value)throws IOException {return value==null?null:path(base,value);}
    static List<Path> paths(Path base,Object value)throws IOException {var result=new ArrayList<Path>();for(Object x:list(value))result.add(path(base,x));need(new HashSet<>(result).size()==result.size(),"Duplicate explicit MTAG input paths");return List.copyOf(result);}
    static List<Double> values(Object value,int traits,double lo,double hi,boolean nullable)throws IOException {
        if(nullable&&value==null)return null;
        List<?> xs=list(value);need(xs.size()==traits,"Supply one explicit numeric choice per trait in declared order");var result=new ArrayList<Double>();
        for(Object x:xs){double d=number(x);need(d>=lo&&d<=hi,"MTAG trait-specific numeric choice is outside its range");result.add(d);}return List.copyOf(result);
    }
    static Request read(Path source)throws IOException {
        source=source.toAbsolutePath().normalize();Path base=source.getParent();Map<?,?> r=object(MtagJson.read(source,1048576));
        keys(r,"schema_version","runtime_profile","output_dir","traits","columns","filters","covariance","fdr","standardized_effects","force_low_signal","median_z_cutoff","limits","no_allele_flipping","meta_format");
        need(integer(r.get("schema_version"),1,1)==1,"Unsupported MTAG request schema");
        var traits=new ArrayList<Trait>();var names=new HashSet<String>();var files=new HashSet<Path>();
        for(Object item:list(r.get("traits"))){Map<?,?> t=object(item);keys(t,"name","input");String name=text(t.get("name"));need(name.matches("[A-Za-z][A-Za-z0-9_.-]{0,79}")&&names.add(name),"Declare unique literal MTAG trait labels");Path f=path(base,t.get("input"));need(files.add(f),"A summary file cannot silently represent multiple traits");traits.add(new Trait(name,f));}
        need(traits.size()>=2&&traits.size()<=8,"This original common-SNP workflow requires2 through8 declared traits");int count=traits.size();
        Map<?,?> rawColumns=object(r.get("columns"));keys(rawColumns,"snp","chr","bp","a1","a2","z","n","freq","p","info");var columns=new java.util.LinkedHashMap<String,String>();
        for(String k:List.of("snp","chr","bp","a1","a2","z","n","freq","p","info")){
            Object value=rawColumns.get(k);if(k.equals("info")&&value==null){columns.put(k,null);continue;}
            String v=text(value);need(v.matches("[A-Za-z][A-Za-z0-9_.-]{0,79}"),"Use explicit whitespace-free source column names");columns.put(k,v);
        }
        need(new HashSet<>(columns.values()).size()==columns.size(),"Duplicate declared MTAG source columns");
        // MTAG exposes no --info_name option: retain its actual fixed input name.
        need(columns.get("info")==null||columns.get("info").equals("info"),"Original MTAG requires the literal info column for INFO filtering; no renaming inferred");
        Map<?,?> f=object(r.get("filters"));keys(f,"maf_min","n_min","n_max","info_min","homogeneous_kind","homogeneous_values","include","exclude","chromosomes","include_ambiguous");
        values(f.get("maf_min"),count,0,.5,false);values(f.get("n_min"),count,0,1e12,true);values(f.get("info_min"),count,0,1,true);
        if(f.get("n_max")!=null)need(number(f.get("n_max"))>0,"Positive explicit maximum N required");
        String homogeneous=choice(f.get("homogeneous_kind"),"none","fraction","distance");
        if(homogeneous.equals("none"))need(f.get("homogeneous_values")==null,"No homogeneous-N values when kind is none");else values(f.get("homogeneous_values"),count,0,1e12,false);
        if(f.get("info_min")!=null)need(columns.get("info")!=null,"An explicit literal info column is required for INFO filtering");
        bool(f.get("include_ambiguous"));List<Path> include=paths(base,f.get("include")),exclude=paths(base,f.get("exclude"));var extra=new ArrayList<Path>();extra.addAll(include);extra.addAll(exclude);
        var chroms=new HashSet<Long>();for(Object x:list(f.get("chromosomes")))need(chroms.add(integer(x,1,22)),"Duplicate explicit chromosome selection");
        Map<?,?> c=object(r.get("covariance"));keys(c,"sigma","sigma_file","omega","omega_file","constraint","no_overlap","tolerance","ld_reference","ld_reference_dir");
        String sigma=choice(c.get("sigma"),"ldsc","provided"),omega=choice(c.get("omega"),"gmm","numerical","provided"),constraint=choice(c.get("constraint"),"none","perfect","equal_h2");
        Path sigmaFile=optional(base,c.get("sigma_file")),omegaFile=optional(base,c.get("omega_file"));
        need(sigma.equals("provided")== (sigmaFile!=null)&&omega.equals("provided")== (omegaFile!=null),"Supply covariance files exactly for provided modes");
        need(!bool(c.get("no_overlap"))||sigma.equals("ldsc"),"Original no_overlap only affects LDSC estimation; cannot combine it with provided Sigma");
        String reference=choice(c.get("ld_reference"),"bundled_1kg_eur","provided");Path referenceDir=optional(base,c.get("ld_reference_dir"));
        need(reference.equals("provided")== (referenceDir!=null),"Explicitly select bundled1000Genomes EUR or a complete supplied LD-score directory");
        if(referenceDir!=null)extra.add(referenceDir);
        need(!omega.equals("provided")||constraint.equals("none"),"Original provided Omega bypasses genetic constraints; supply an unambiguous explicit mode");
        need(!constraint.equals("equal_h2")||omega.equals("gmm"),"Equal-heritability perfect-correlation mode bypasses numerical Omega; declare GMM mode");
        double tolerance=number(c.get("tolerance"));need(tolerance>0&&tolerance<=.1,"Declare a positive numerical-Omega tolerance no larger than0.1");
        if(sigmaFile!=null)extra.add(sigmaFile);if(omegaFile!=null)extra.add(omegaFile);
        Map<?,?> d=object(r.get("fdr"));keys(d,"mode","intervals","grid_file","p_significant","cores");String mode=choice(d.get("mode"),"none","grid","fit_ss","custom");
        integer(d.get("intervals"),2,100);integer(d.get("cores"),1,32);double p=number(d.get("p_significant"));need(p>0&&p<1,"Significance P must lie strictly between0 and1");
        Path grid=optional(base,d.get("grid_file"));need(mode.equals("custom")== (grid!=null),"Provide a probability grid exactly for custom maxFDR mode");if(grid!=null)extra.add(grid);
        double median=number(r.get("median_z_cutoff"));need(median>=0&&median<=100,"Declare a nonnegative median-Z check");
        Map<?,?> l=object(r.get("limits"));keys(l,"rows_per_trait","input_bytes","output_bytes","working_bytes","wall_seconds","maximum_files","maximum_fdr_grid_points");
        var limits=new Limits((int)integer(l.get("rows_per_trait"),1,50_000_000),integer(l.get("input_bytes"),1,100_000_000_000L),
            integer(l.get("output_bytes"),1,100_000_000_000L),integer(l.get("working_bytes"),1048576,1_000_000_000_000L),
            (int)integer(l.get("wall_seconds"),1,604800),(int)integer(l.get("maximum_files"),100,1_000_000),
            (int)integer(l.get("maximum_fdr_grid_points"),1,100_000_000));
        return new Request(source,path(base,r.get("runtime_profile")),path(base,r.get("output_dir")),List.copyOf(traits),java.util.Collections.unmodifiableMap(columns),f,c,d,
            bool(r.get("standardized_effects")),bool(r.get("force_low_signal")),median,limits,List.copyOf(extra),bool(r.get("no_allele_flipping")),bool(r.get("meta_format")));
    }
}

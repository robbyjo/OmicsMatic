/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.MtagJson.*;

/** Exact supervised source argument vector, derived only from typed scientific choices. */
final class MtagCommand {
    private MtagCommand() { }
    static void capability(MtagRequest.Request r)throws IOException {
        if(r.noFlipping())throw new MtagRuntime.Unavailable("Original MTAG1.0.8 declares but does not use no_allele_flipping; the requested no-flip analysis is skipped rather than silently running with flips");
        if(r.union())throw new MtagRuntime.Unavailable("The original union-of-traits meta_format workflow is not yet admitted in this typed profile; dependent union analysis skipped; no automatic intersection substitution");
        if(r.filters().get("n_max")!=null)throw new MtagRuntime.Unavailable("Original Python2 MTAG n_max is parsed as text and its NumPy comparison produces a scalar boolean, making extract_gwas_sumstats fail. The requested original maximum-N analysis is skipped without a source patch or silent prefilter");
        if(text(r.fdr().get("mode")).equals("custom"))throw new MtagRuntime.Unavailable("Original MTAG1.0.8 custom maxFDR grids hit a NumPy bitwise-and/precedence error before fitting the grid. Custom-grid analysis is skipped; preserve the source and choose a scientifically authorized supported mode explicitly, never an automatic replacement grid");
        need(!r.out().toString().contains(","),"MTAG comma-separated source input arguments require an output path without commas; original input paths are staged without renaming their IDs");
        if(!text(r.fdr().get("mode")).equals("none")&&!text(r.fdr().get("mode")).equals("custom")) {
            int states=1<<r.traits().size(),intervals=(int)integer(r.fdr().get("intervals"),2,100);
            BigInteger points=BigInteger.ONE;
            for(int i=1;i<states;i++)points=points.multiply(BigInteger.valueOf(intervals+i)).divide(BigInteger.valueOf(i));
            need(points.compareTo(BigInteger.valueOf(r.limits().gridPoints()))<=0,"Complete unpruned original maxFDR grid exceeds declared capacity; no automatic smaller grid or fit");
            BigInteger bytes=points.multiply(BigInteger.valueOf((512L+64L*states)*r.traits().size()));
            need(bytes.compareTo(BigInteger.valueOf(r.limits().workingBytes()))<=0,"Original maxFDR grid allocation estimate exceeds declared capacity; no implicit grid reduction");
        }
    }
    private static String joined(List<?> values)throws IOException {var s=new ArrayList<String>();for(Object v:values){String x=v.toString();need(!x.contains(","),"Unexpected comma in a staged MTAG argument value");s.add(x);}return String.join(",",s);}
    private static void option(List<String> args,String name,Object value){args.add(name);args.add(value.toString());}
    static List<String> options(MtagRequest.Request r,MtagInputs.Staged s,Path prefix)throws IOException {
        var args=new ArrayList<String>();option(args,"--sumstats",joined(s.traits()));option(args,"--out",prefix);
        Map<String,String> flag=Map.of("snp","--snp_name","chr","--chr_name","bp","--bpos_name","a1","--a1_name","a2","--a2_name","z","--z_name","n","--n_name","freq","--eaf_name","p","--p_name");
        for(String key:List.of("snp","chr","bp","a1","a2","z","n","freq","p"))option(args,flag.get(key),r.columns().get(key));
        option(args,"--maf_min",joined(list(r.filters().get("maf_min"))));
        for(String name:List.of("n_min","info_min"))if(r.filters().get(name)!=null)option(args,"--"+name,joined(list(r.filters().get(name))));
        if(bool(r.filters().get("include_ambiguous")))args.add("--incld_ambig_snps");
        if(!list(r.filters().get("chromosomes")).isEmpty())option(args,"--only_chr",joined(list(r.filters().get("chromosomes"))));
        if(!s.include().isEmpty())option(args,"--include",joined(s.include()));if(!s.exclude().isEmpty())option(args,"--exclude",joined(s.exclude()));
        String hom=text(r.filters().get("homogeneous_kind"));if(!hom.equals("none"))option(args,hom.equals("fraction")?"--homogNs_frac":"--homogNs_dist",joined(list(r.filters().get("homogeneous_values"))));
        if(s.sigma()!=null)option(args,"--residcov_path",s.sigma());if(s.omega()!=null)option(args,"--gencov_path",s.omega());
        if(bool(r.covariance().get("no_overlap")))args.add("--no_overlap");
        String constraint=text(r.covariance().get("constraint"));if(!constraint.equals("none"))args.add("--perfect_gencov");if(constraint.equals("equal_h2"))args.add("--equal_h2");
        if(text(r.covariance().get("omega")).equals("numerical"))args.add("--numerical_omega");option(args,"--tol",r.covariance().get("tolerance"));
        option(args,"--ld_ref_panel",s.ldReference().toString()+"/");
        String fdr=text(r.fdr().get("mode"));if(!fdr.equals("none")){
            args.add("--fdr");if(fdr.equals("fit_ss"))args.add("--fit_ss");if(s.grid()!=null)option(args,"--grid_file",s.grid());
            option(args,"--intervals",r.fdr().get("intervals"));option(args,"--p_sig",r.fdr().get("p_significant"));option(args,"--cores",r.fdr().get("cores"));
        }
        if(r.standardized())args.add("--std_betas");if(r.force())args.add("--force");option(args,"--median_z_cutoff",r.median());
        option(args,"--chunksize",10000000);args.add("--verbose");args.add("--stream_stdout");return List.copyOf(args);
    }
}

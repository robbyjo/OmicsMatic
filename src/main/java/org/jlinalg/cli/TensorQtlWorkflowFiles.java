/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal prepared matrices and explicit coordinate semantics, without implicit harmonization. */
final class TensorQtlWorkflowFiles {
    private TensorQtlWorkflowFiles() { }
    record Prepared(List<String> variants,List<String> phenotypes,List<String> samples) { }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        String op=text(r,"operation");List<String> variants=List.of(),phenotypes=List.of(),samples=List.of();int n=0,g=0,p=0,c=0;
        if(!op.equals("QVALUES")) {
            int maxV=((Number)r.get("maximum_variants")).intValue(),maxP=((Number)r.get("maximum_phenotypes")).intValue(),maxN=((Number)r.get("maximum_samples")).intValue();
            var geno=SusieWorkflowFiles.table(resolve(root,text(r,"genotypes")),maxV,maxN+1);var pheno=SusieWorkflowFiles.table(resolve(root,text(r,"phenotypes")),maxP,maxN+1);
            if(!geno.header()[0].equals("variant_id")||!pheno.header()[0].equals("phenotype_id")||geno.header().length<6||!Arrays.asList(geno.header()).subList(1,geno.header().length).equals(Arrays.asList(pheno.header()).subList(1,pheno.header().length)))throw new IOException("Require variant_id/phenotype_id matrices with >=5 exact ordered sample columns");
            variants=ids(geno.rows().stream().map(x->x[0]).toList());phenotypes=ids(pheno.rows().stream().map(x->x[0]).toList());samples=ids(Arrays.asList(geno.header()).subList(1,geno.header().length));n=samples.size();g=variants.size();p=phenotypes.size();
            if((long)(g+p)*n>((Number)r.get("maximum_entries")).longValue())throw new IOException("maximum_entries exceeded by dense input");
            transport(root,"genotypes.tsv",numeric(geno,true),compare);transport(root,"phenotypes.tsv",numeric(pheno,false),compare);
            if(r.containsKey("variants"))positions(root,r,"variants",variants,false,true,compare);
            if(r.containsKey("phenotype_positions"))positions(root,r,"phenotype_positions",phenotypes,r.get("position_kind").equals("interval"),false,compare);
            if(r.containsKey("covariates")){var t=SusieWorkflowFiles.table(resolve(root,text(r,"covariates")),n,1025);if(!t.header()[0].equals("sample_id")||t.header().length<2||!t.rows().stream().map(x->x[0]).toList().equals(samples))throw new IOException("Covariates require exact sample_id order and explicit numeric columns; no added intercept");ids(Arrays.asList(t.header()).subList(1,t.header().length));c=t.header().length-1;transport(root,"covariates.tsv",numeric(t,false),compare);}
            if(n-c-(r.containsKey("interaction")?4:2)<=0)throw new IOException("Positive original residual degrees of freedom required");
            if(r.containsKey("interaction")){var t=SusieWorkflowFiles.table(resolve(root,text(r,"interaction")),n,4);if(!t.header()[0].equals("sample_id")||t.header().length<2||!t.rows().stream().map(x->x[0]).toList().equals(samples))throw new IOException("Interaction requires exact sample_id order");ids(Arrays.asList(t.header()).subList(1,t.header().length));if(t.header().length>2&&(op.equals("TRANS_NOMINAL")||text(r,"profile").startsWith("tensorqtl-1.0.5-")))throw new IOException("This original edition/operation accepts one interaction vector");if(n-c-2-2*(t.header().length-1)<=0)throw new IOException("Positive residual degrees of freedom required for every supplied interaction");transport(root,"interaction.tsv",numeric(t,false),compare);}
            if(r.containsKey("groups")){var t=SusieWorkflowFiles.table(resolve(root,text(r,"groups")),p,2);if(!Arrays.equals(t.header(),new String[]{"phenotype_id","group_id"})||!t.rows().stream().map(x->x[0]).toList().equals(phenotypes))throw new IOException("Groups require exact phenotype_id order and group_id");for(var row:t.rows())identity(row[1]);transport(root,"groups.tsv",literal(t),compare);}
            if(r.containsKey("paired_covariates")){var t=SusieWorkflowFiles.table(resolve(root,text(r,"paired_covariates")),p,n+1);var selected=ids(t.rows().stream().map(x->x[0]).toList());if(!t.header()[0].equals("phenotype_id")||!Arrays.asList(t.header()).subList(1,t.header().length).equals(samples)||!new HashSet<>(phenotypes).containsAll(selected))throw new IOException("Paired covariates require exact sample columns and distinct supplied phenotype IDs; no intersection");transport(root,"paired_covariates.tsv",numeric(t,false),compare);}
            if(r.containsKey("trans_filter_positions"))positions(root,r,"trans_filter_positions",variants,false,true,compare);
            try {
                long work=Math.multiplyExact(Math.multiplyExact((long)g,p),Math.max(1,n));
                if(TensorQtlWorkflowContracts.permutation(op))work=Math.multiplyExact(work,((Number)r.get("nperm")).longValue());
                if(op.equals("CIS_SUSIE")){var s=object(r.get("susie"),"susie");work=Math.multiplyExact(work,Math.multiplyExact(((Number)s.get("L")).longValue(),((Number)s.get("maximum_iterations")).longValue()));}
                if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("maximum_work exceeded by conservative dense mapping estimate; change explicit capacities without changing the model");
            }catch(ArithmeticException e){throw new IOException("Mapping work overflows declared capacity",e);}
        }
        if(r.containsKey("cis_results")) {
            var t=SusieWorkflowFiles.table(resolve(root,text(r,"cis_results")),((Number)r.get("maximum_phenotypes")).intValue(),128);ids(Arrays.asList(t.header()));ids(t.rows().stream().map(x->x[0]).toList());
            if(!t.header()[0].equals("phenotype_id")||!Arrays.asList(t.header()).contains("pval_beta"))throw new IOException("Original cis results require phenotype_id index and pval_beta");
            Set<String> strings=Set.of("phenotype_id","variant_id","group_id","chrom","chr");
            for(var row:t.rows())for(int j=0;j<row.length;j++)if(strings.contains(t.header()[j]))identity(row[j]);else SusieWorkflowFiles.number(row[j]);
            transport(root,"cis_results.tsv",literal(t),compare);
        }
        var parameters=new LinkedHashMap<String,Object>();for(String key:List.of("profile","operation","position_kind","seed","window","maf_threshold","nperm","beta_approx","random_tiebreak","qvalues","qvalue_lambda","fdr","fdr_column","return_sparse","pval_threshold","alleles","return_r2","batch_size","maf_threshold_interaction","run_eigenmt","susie","trans_filter_position_kind","cis_exclusion_window","device","cuda_device"))if(r.containsKey(key))parameters.put(key,r.get(key));
        for(String key:TensorQtlWorkflowContracts.INPUTS)parameters.put("has_"+key,r.containsKey(key));parameters.put("requires_R",TensorQtlWorkflowContracts.requiresR(r));parameters.put("variants",g);parameters.put("phenotypes",p);parameters.put("samples",n);parameters.put("covariates",c);transport(root,"parameters.json",new String(RsemJson.bytes(parameters),StandardCharsets.UTF_8),compare);
        return new Prepared(variants,phenotypes,samples);
    }
    private static void positions(Path root,Map<String,Object> r,String key,List<String> ids,boolean interval,boolean variant,boolean compare)throws IOException {
        var t=SusieWorkflowFiles.table(resolve(root,text(r,key)),ids.size(),interval?4:3);String id=variant?"variant_id":"phenotype_id";String[] header=interval?new String[]{id,"chr","start","end"}:new String[]{id,"chr","pos"};
        if(!Arrays.equals(t.header(),header)||!t.rows().stream().map(x->x[0]).toList().equals(ids))throw new IOException("Exact "+key+" identities/order/coordinate header required");
        String priorChr=null;double prior=0;Set<String> seen=new HashSet<>();boolean center=key.equals("trans_filter_positions")&&r.get("trans_filter_position_kind").equals("declared-region-center");
        for(var row:t.rows()){
            if(!row[1].matches("[A-Za-z0-9_+.-]{1,128}")||row[1].equals(".")||row[1].equals(".."))throw new IOException("Contigs must be literal safe filename segments; no recoding is performed");
            double start=SusieWorkflowFiles.number(row[2]);if(start<=0||start>Integer.MAX_VALUE||!center&&start!=Math.rint(start))throw new IOException("One-based positive coordinates required; only declared region centers may be fractional");
            if(interval){double end=SusieWorkflowFiles.number(row[3]);if(end<start||end>Integer.MAX_VALUE||end!=Math.rint(end))throw new IOException("Invalid one-based closed interval");}
            if(key.equals("variants")){
                if(!row[1].equals(priorChr)){if(!seen.add(row[1]))throw new IOException("Variant contigs must be contiguous; no sorting");priorChr=row[1];prior=0;}
                if(start<prior)throw new IOException("Variants must be ordered by supplied position within contig; no sorting");prior=start;
            }
        }
        transport(root,key+".tsv",literal(t),compare);
    }
    private static List<String> ids(List<String> values)throws IOException {if(values.isEmpty())throw new IOException("Empty input population");var seen=new HashSet<String>();for(String id:values){SusieWorkflowFiles.exactIdentity(id);if(!seen.add(id))throw new IOException("Repeated literal identity");}return List.copyOf(values);}
    private static String numeric(SusieWorkflowFiles.Table t,boolean genotype)throws IOException {var out=new StringBuilder(String.join("\t",t.header())).append('\n');for(var row:t.rows()){out.append(row[0]);for(int j=1;j<row.length;j++){double x=SusieWorkflowFiles.number(row[j]);if(genotype&&x!=-9&&(x<0||x>2))throw new IOException("Dosages require [0,2] or the original explicit -9 missing sentinel; no imputation by the adapter");out.append('\t').append(x);}out.append('\n');}return out.toString();}
    private static String literal(SusieWorkflowFiles.Table t){var out=new StringBuilder(String.join("\t",t.header())).append('\n');for(var row:t.rows())out.append(String.join("\t",row)).append('\n');return out.toString();}
    private static void transport(Path root,String name,String value,boolean compare)throws IOException {Path p=root.resolve("worker/"+name);byte[] raw=value.getBytes(StandardCharsets.UTF_8);if(compare){if(!Arrays.equals(Files.readAllBytes(p),raw))throw new IOException("Prepared transport differs: "+name);}else Files.write(p,raw,StandardOpenOption.CREATE_NEW);}
}

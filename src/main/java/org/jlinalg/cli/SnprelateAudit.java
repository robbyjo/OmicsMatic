/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.KingRobustKinship;
import org.jlinalg.genetics.SnprelateKingRobust;
import org.jlinalg.genetics.SnprelateVariantQc;
import static org.jlinalg.cli.SnprelateRequest.need;

/** Complete hard-call/identity/QC audit and workflow-specific original-output contracts. */
final class SnprelateAudit {
    private SnprelateAudit() { }
    record Data(List<String[]> fam,List<String[]> bim,byte[] bed,int width,List<SnprelateVariantQc.Counts> counts,
                List<Integer> qc,int[] chromosomes,int[] positions,long candidateEdges,long estimatedBytes) {
        int samples(){return fam.size();}int variants(){return bim.size();}
        byte[] calls(int v) {
            byte[] row=new byte[samples()];
            for(int i=0;i<row.length;i++)row[i]=switch(((bed[3+v*width+i/4]&255) >>> (2*(i%4)))&3){case 0->2;case 2->1;case 3->0;default->-1;};
            return row;
        }
    }
    static Map<String,Object> map(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    static SnprelateVariantQc.Layout layout(SnprelateRequest.Request r){return r.layout().equals("snp-first")?SnprelateVariantQc.Layout.SNP_BY_SAMPLE:SnprelateVariantQc.Layout.SAMPLE_BY_SNP;}
    static SnprelateVariantQc.Filters filters(SnprelateRequest.Request r){return new SnprelateVariantQc.Filters(r.removeMono(),r.maf(),r.missing());}
    private static List<String[]> fields(Path file,int maximum)throws IOException {
        var rows=new ArrayList<String[]>();try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            for(String line;(line=reader.readLine())!=null;) {
                need(rows.size()<maximum&&line.length()<=65536,"Complete FAM/BIM row/line capacity exceeded");
                String[] f=line.strip().split("[ \\t]+");need(f.length==6,"Require six complete FAM/BIM fields; no identifier repair");
                for(String s:f)need(!s.isEmpty()&&s.chars().noneMatch(c->c<32||c==127),"Invalid FAM/BIM field");rows.add(f);
            }
        }return List.copyOf(rows);
    }
    static Data inputs(Path root,SnprelateRequest.Request r,Runnable check)throws IOException {
        Path home=root.resolve("inputs");var fam=fields(home.resolve("cohort.fam"),r.limits().samples());var bim=fields(home.resolve("cohort.bim"),r.limits().variants());
        int n=fam.size(),m=bim.size();need(n>=2&&m>=1,"Require at least two participants and one variant");
        var keys=new HashSet<String>();for(String[] f:fam)need(keys.add(f[0]+"\0"+f[1]),"Duplicate FID/IID pair; do not silently merge participants");
        int[] chr=new int[m],bp=new int[m];int previousChr=0,previousBp=0;
        for(int v=0;v<m;v++) {
            check.run();String[] f=bim.get(v);
            try {need(f[0].matches("[1-9][0-9]*")&&f[3].matches("[1-9][0-9]*"),"Require explicit numeric chromosome1..26 and positive int32 position");chr[v]=Integer.parseInt(f[0]);bp[v]=Integer.parseInt(f[3]);}
            catch(NumberFormatException error){throw new IOException("Chromosome/position capacity exceeded",error);}
            need(chr[v]<=26&&chr[v]>=previousChr&&(chr[v]!=previousChr||bp[v]>=previousBp),"Require chromosome/position sorted input for the tested pruning profile; no sorting/renaming is performed");
            need(f[4].matches("[ACGT]")&&f[5].matches("[ACGT]")&&!f[4].equals(f[5]),"Require explicit distinct A/C/G/T biallelic hard-call SNP alleles");
            previousChr=chr[v];previousBp=bp[v];
        }
        long genotypeCells=Math.multiplyExact((long)n,m),pairs=Math.multiplyExact((long)n,n-1)/2;
        long bytes=Math.addExact(65536,Math.addExact(Math.multiplyExact(genotypeCells,16),Math.addExact(Math.multiplyExact((long)m,768),Math.multiplyExact((long)n*n,64))));
        if(r.mode().equals("king-robust"))bytes=Math.addExact(bytes,Math.multiplyExact(pairs,176));
        need(bytes<=r.limits().workingBytes(),"Estimated complete native/R matrix working-storage capacity exceeded; logical bound is not an OS memory quota");
        int width=(n+3)/4;long bedBytes=Math.addExact(3,Math.multiplyExact((long)width,m));
        need(bedBytes<=r.limits().inputBytes()&&bedBytes<Integer.MAX_VALUE,"Complete BED byte capacity exceeded");
        byte[] bed=Files.readAllBytes(home.resolve("cohort.bed"));need(bed.length==bedBytes&&bed[0]==0x6c&&bed[1]==0x1b&&bed[2]==1,"Require exact complete variant-major BED; no padding/genotype repair");
        var counts=new ArrayList<SnprelateVariantQc.Counts>();var qc=new ArrayList<Integer>();byte[] calls=new byte[n];
        for(int v=0;v<m;v++) {
            check.run();for(int i=0;i<n;i++)calls[i]=switch(((bed[3+v*width+i/4]&255) >>> (2*(i%4)))&3){case 0->2;case 2->1;case 3->0;default->-1;};
            var c=SnprelateVariantQc.count(calls);counts.add(c);
            if((!r.autosomes()||chr[v]<=22)&&SnprelateVariantQc.selected(SnprelateVariantQc.decide(SnprelateVariantQc.statistics(c,layout(r)),filters(r))))qc.add(v);
        }
        long edges=0;
        if(r.mode().equals("ld-pruning")) {
            int lo=0;for(int hi=0;hi<qc.size();hi++) {
                check.run();int v=qc.get(hi);
                while(lo<hi&&(chr[qc.get(lo)]!=chr[v]||(long)bp[v]-bp[qc.get(lo)]>r.windowBp()))lo++;
                int start=lo;if(r.windowVariants()!=null)start=Math.max(start,hi-r.windowVariants());
                edges=Math.addExact(edges,hi-start);
            }
        }
        var provisional=new Data(fam,bim,bed,width,List.copyOf(counts),List.copyOf(qc),chr,bp,edges,bytes);
        long operations=Math.addExact(genotypeCells,Math.multiplyExact((long)n,r.mode().equals("ld-pruning")?Math.multiplyExact(edges,4):Math.multiplyExact((long)qc.size(),n)));
        if(r.pca()!=null&&!qc.isEmpty()) {
            bytes=Math.addExact(bytes,SnprelatePcaAudit.additionalBytes(provisional,r.pca()));
            operations=Math.addExact(operations,SnprelatePcaAudit.additionalOperations(provisional,r.pca()));
        }
        if(r.mode().equals("ld-pruning"))bytes=Math.addExact(bytes,Math.multiplyExact(edges,64));
        need(bytes<=r.limits().workingBytes(),"Complete LD edge bookkeeping exceeds declared working-storage capacity");
        need(operations<=r.limits().operations(),"Complete declared pair/variant-operation capacity exceeded");
        long rows=Math.addExact(6L*m+2L*n,r.mode().equals("ld-pruning")?edges:r.pca()!=null?SnprelatePcaAudit.outputRows(provisional,r.pca()):(long)n*n);
        need(rows<=r.limits().rows(),"Complete transport/QC/workflow report-row capacity exceeded");
        return new Data(fam,bim,bed,width,List.copyOf(counts),List.copyOf(qc),chr,bp,edges,bytes);
    }
    static void writeInputs(Path root,Data d,SnprelateRequest.Request r,Runnable check)throws IOException {
        try(var calls=Files.newBufferedWriter(root.resolve("inputs/native-calls.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var qc=Files.newBufferedWriter(root.resolve("native-variant-qc.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            calls.write("input_variant_row");for(int i=0;i<d.samples();i++)calls.write("\t"+(i+1));calls.write("\n");
            qc.write("input_variant_row\tsamples\tcalled\tallele_copies\tAF_hex\tMAF_hex\tmissing_hex\tselected\tdisposition\n");
            var selected=new HashSet<>(d.qc());
            for(int v=0;v<d.variants();v++) {
                check.run();calls.write(Integer.toString(v+1));for(byte value:d.calls(v))calls.write("\t"+(value<0?"3":Byte.toString(value)));calls.write("\n");
                var c=d.counts().get(v);var s=SnprelateVariantQc.statistics(c,layout(r));String disposition=r.autosomes()&&d.chromosomes()[v]>22?"NON_AUTOSOME":SnprelateVariantQc.decide(s,filters(r)).name();
                qc.write((v+1)+"\t"+c.samples()+"\t"+c.called()+"\t"+c.alleleCopies()+"\t"+Double.toHexString(s.alleleFrequency())+"\t"+Double.toHexString(s.minorAlleleFrequency())+"\t"+Double.toHexString(s.missingRate())+"\t"+selected.contains(v)+"\t"+disposition+"\n");
            }
        }
    }
    @FunctionalInterface private interface Row {void accept(String[] values,int index)throws IOException;}
    private static int table(Path path,int maximum,Runnable check,String[] header,Row row)throws IOException {
        int count=0;try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            need(java.util.Arrays.equals(reader.readLine().split("\t",-1),header),"Complete source TSV header differs: "+path.getFileName());
            for(String line;(line=reader.readLine())!=null;) {
                check.run();need(count<maximum&&line.length()<=1_000_000,"Complete source TSV row/line capacity exceeded");String[] f=line.split("\t",-1);
                need(f.length==header.length,"Complete source TSV shape differs: "+path.getFileName());row.accept(f,count++);
            }
        }return count;
    }
    private static int index(String raw,int maximum)throws IOException {
        try {int value=Integer.parseInt(raw);need(value>=1&&value<=maximum&&Integer.toString(value).equals(raw),"Source input-row identity differs");return value-1;}
        catch(NumberFormatException error){throw new IOException("Invalid source row index",error);}
    }
    static double number(String raw)throws IOException {
        try {return switch(raw){case "NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;case "NA"->throw new IOException("R NA is distinct from NaN; no repair");default->Double.parseDouble(raw);};}
        catch(NumberFormatException error){throw new IOException("Invalid original floating-point field",error);}
    }
    private static void same(double expected,String raw,String cause)throws IOException {need(Double.doubleToLongBits(expected)==Double.doubleToLongBits(number(raw)),"SCIENTIFIC DISCREPANCY: "+cause);}
    private static void flags(String value,String isNa,String isNan)throws IOException {
        boolean nan=Double.isNaN(number(value));need(isNa.equals(nan?"TRUE":"FALSE")&&isNan.equals(nan?"TRUE":"FALSE"),"SCIENTIFIC DISCREPANCY: R NA/NaN flags differ");
    }
    static Map<String,Object> outputs(Path root,Path work,SnprelateRequest.Request r,Data d,Runnable check)throws IOException {
        int n=d.samples(),m=d.variants();need(!d.qc().isEmpty(),"SCIENTIFIC DISCREPANCY: empty QC population admits no dependent analysis");
        var expectedParameters=SnprelateRequest.parameters(r);var gotParameters=new LinkedHashMap<String,String>();
        table(work.resolve("effective-parameters.tsv"),100,check,new String[]{"parameter","value"},(f,i)->{need(gotParameters.put(f[0],f[1])==null,"Duplicate effective parameter");});
        need(gotParameters.keySet().equals(expectedParameters.keySet()),"Effective parameter population differs");
        for(var e:expectedParameters.entrySet()) {
            if(Set.of("maf","missing_rate","ld_threshold").contains(e.getKey()))same(number(e.getValue()),gotParameters.get(e.getKey()),"effective numeric option "+e.getKey());
            else need(e.getValue().equals(gotParameters.get(e.getKey())),"Effective original option differs: "+e.getKey());
        }
        var versions=new HashMap<String,String>();table(work.resolve("original-package-versions.tsv"),3,check,new String[]{"package","version"},(f,i)->need(versions.put(f[0],f[1])==null,"Duplicate package version"));
        need(versions.equals(Map.of("SNPRelate","1.46.0","gdsfmt","1.48.2","RhpcBLASctl","0.23-42")),"Exact original package versions differ");
        need(table(work.resolve("original-sample-map.tsv"),n,check,new String[]{"input_row","gds_sample_id","FID","IID"},(f,i)->{
            need(index(f[0],n)==i&&index(f[1],n)==i&&f[2].equals(d.fam().get(i)[0])&&f[3].equals(d.fam().get(i)[1]),"SCIENTIFIC DISCREPANCY: original FID/IID identity/order changed");
        })==n,"Incomplete original sample population");
        need(table(work.resolve("original-variant-map.tsv"),m,check,new String[]{"input_row","gds_snp_id","original_snp_id","chr","position","allele"},(f,i)->{
            String[] b=d.bim().get(i);need(index(f[0],m)==i&&index(f[1],m)==i&&f[2].equals(b[1])&&Integer.parseInt(f[3])==d.chromosomes()[i]&&Integer.parseInt(f[4])==d.positions()[i]&&f[5].equals(b[4]+"/"+b[5]),"SCIENTIFIC DISCREPANCY: original variant identity/order changed");
        })==m,"Incomplete original variant population");
        String[] genotypeHeader=new String[n+1];genotypeHeader[0]="input_variant_row";for(int i=1;i<=n;i++)genotypeHeader[i]=Integer.toString(i);
        need(table(work.resolve("original-genotypes.tsv"),m,check,genotypeHeader,(f,v)->{
            need(index(f[0],m)==v,"Source genotype variant order differs");byte[] calls=d.calls(v);
            for(int i=0;i<n;i++)need(f[i+1].equals(calls[i]<0?"NA":Byte.toString(calls[i])),"SCIENTIFIC DISCREPANCY: complete original genotype transport changed");
        })==m,"Incomplete original genotype matrix");
        need(table(work.resolve("original-variant-statistics.tsv"),m,check,new String[]{"input_variant_row","AF_hex","MAF_hex","missing_hex"},(f,v)->{
            need(index(f[0],m)==v,"Source statistic variant order differs");var s=SnprelateVariantQc.statistics(d.counts().get(v),layout(r));
            same(s.alleleFrequency(),f[1],"allele frequency");same(s.minorAlleleFrequency(),f[2],"minor allele frequency");same(s.missingRate(),f[3],"layout-specific missing rate");
        })==m,"Incomplete source statistics");
        need(table(work.resolve("original-qc-population.tsv"),m,check,new String[]{"result_row","input_variant_row","original_snp_id"},(f,i)->{
            need(i<d.qc().size()&&index(f[0],m)==i&&index(f[1],m)==d.qc().get(i)&&f[2].equals(d.bim().get(d.qc().get(i))[1]),"SCIENTIFIC DISCREPANCY: complete QC population/order changed");
        })==d.qc().size(),"SCIENTIFIC DISCREPANCY: source QC population incomplete");
        var selected=new ArrayList<Integer>();var unique=new HashSet<Integer>();var qcSet=new HashSet<>(d.qc());
        String[] selectedHeader=r.mode().equals("ld-pruning")?new String[]{"result_row","input_variant_row","original_snp_id","chromosome"}:new String[]{"result_row","input_variant_row","original_snp_id"};
        table(work.resolve("original-selected-variants.tsv"),m,check,selectedHeader,(f,i)->{
            int v=index(f[1],m);need(index(f[0],m)==i&&unique.add(v)&&qcSet.contains(v)&&f[2].equals(d.bim().get(v)[1]),"SCIENTIFIC DISCREPANCY: selected SNP identity/order/population changed");
            need(selected.isEmpty()||v>selected.get(selected.size()-1),"Selected SNP order changed");
            if(f.length==4)need(f[3].equals("chr"+d.chromosomes()[v]),"Original LD chromosome group differs");selected.add(v);
        });
        need(!selected.isEmpty(),"SCIENTIFIC DISCREPANCY: empty original SNP selection admits no dependent analysis");
        if(!r.mode().equals("ld-pruning"))need(selected.equals(d.qc()),"Complete source matrix variant population differs");
        long[] unassessable={0},matrixCells={0},edges={0},nonfiniteMatrixValues={0};String[] indivBetaAverage={""};Map<String,Object> pcaAudit=null;
        if(r.mode().equals("ld-pruning")) {
            // Require every retained in-window pair exactly once in deterministic
            // chromosome/input order. Source LD values are not replaced by r-squared.
            int[] qIndex=new int[m];for(int i=0;i<d.qc().size();i++)qIndex[d.qc().get(i)]=i;
            var expectedEdges=new ArrayList<Long>();int lo=0;
            for(int hi=0;hi<selected.size();hi++) {
                int v=selected.get(hi);while(lo<hi&&(d.chromosomes()[selected.get(lo)]!=d.chromosomes()[v]||(long)d.positions()[v]-d.positions()[selected.get(lo)]>r.windowBp()
                    ||r.windowVariants()!=null&&qIndex[v]-qIndex[selected.get(lo)]>r.windowVariants()))lo++;
                for(int j=lo;j<hi;j++)expectedEdges.add(((long)v<<32)|(selected.get(j)&0xffff_ffffL));
            }
            need(expectedEdges.size()<=d.candidateEdges(),"Retained LD edge population exceeds declared bound");
            int count=table(work.resolve("original-retained-LD-edges.tsv"),r.limits().rows(),check,new String[]{"first_variant_row","second_variant_row","ld_hex","pA_A_hex","pA_B_hex","pB_A_hex","pB_B_hex"},(f,i)->{
                int a=index(f[0],m),b=index(f[1],m);need(i<expectedEdges.size()&&expectedEdges.get(i)==(((long)a<<32)|(b&0xffff_ffffL)),"SCIENTIFIC DISCREPANCY: retained LD edge population/order changed");
                double ld=number(f[2]);if(Double.isFinite(ld))need(Math.abs(ld)<=r.threshold(),"SCIENTIFIC DISCREPANCY: retained in-window absolute LD exceeds original threshold");else unassessable[0]++;
                for(int k=3;k<7;k++)if(Set.of("r","dprime").contains(r.method()))number(f[k]);else need(f[k].equals("ABSENT"),"Unexpected original haplotype fields");edges[0]++;
            });need(count==expectedEdges.size(),"Incomplete retained LD edge population");
        } else if(r.pca()!=null) {
            pcaAudit=SnprelatePcaAudit.verify(work,d,r.pca(),r.limits().rows(),check);
            int components=r.pca().eigenCount()==0?n:r.pca().eigenCount();matrixCells[0]=(long)n*components+(r.pca().genmat()?(long)n*n:0);
        } else {
            KingRobustKinship.Accumulator counter=null;
            if(r.mode().equals("king-robust")) {
                var pairs=new ArrayList<KingRobustKinship.Pair>();for(int hi=1;hi<n;hi++)for(int low=0;low<hi;low++)pairs.add(new KingRobustKinship.Pair(hi,low));
                counter=new KingRobustKinship.Accumulator(n,pairs,d.qc().size(),r.limits().workingBytes());
                for(int v:d.qc()){check.run();counter.accept(d.calls(v));}counter.finish();
            } else {
                String reported=r.method().equals("Weighted")?"EIGMIX":r.method();
                need(table(work.resolve("original-GRM-method.tsv"),1,check,new String[]{"requested","reported"},(f,i)->need(f[0].equals(r.method())&&f[1].equals(reported),"Original requested/reported GRM method differs"))==1,"Missing source GRM method");
                if(r.method().equals("IndivBeta"))need(table(work.resolve("original-average.tsv"),1,check,new String[]{"avg_val_hex"},(f,i)->{number(f[0]);indivBetaAverage[0]=f[0];})==1,"Incomplete original IndivBeta average");
                else need(!Files.exists(work.resolve("original-average.tsv")),"Unexpected source GRM average");
            }
            final var kernel=counter;double[] triangle=new double[n*(n+1)/2];boolean[] seen=new boolean[n*n];
            String[] header=r.mode().equals("king-robust")?new String[]{"row_index","col_index","value_hex","value_na","value_nan","IBS0_hex","IBS0_na","IBS0_nan"}:new String[]{"row_index","col_index","value_hex","value_na","value_nan"};
            int cells=table(work.resolve("original-complete-matrix.tsv"),r.limits().rows(),check,header,(f,k)->{
                int a=index(f[0],n),b=index(f[1],n);need(!seen[a*n+b]&&a==k%n&&b==k/n,"Original complete matrix cell order/uniqueness differs");seen[a*n+b]=true;
                double value=number(f[2]);flags(f[2],f[3],f[4]);if(!Double.isFinite(value)){nonfiniteMatrixValues[0]++;if(a!=b)unassessable[0]++;}
                int hi=Math.max(a,b),low=Math.min(a,b),t=hi*(hi+1)/2+low;
                if(a>=b)triangle[t]=value;else need(Double.doubleToLongBits(triangle[t])==Double.doubleToLongBits(value),"SCIENTIFIC DISCREPANCY: source matrix is not symmetric");
                if(kernel!=null) {
                    double expected,ibs0;
                    if(a==b){expected=SnprelateKingRobust.DIAGONAL_KINSHIP;ibs0=SnprelateKingRobust.DIAGONAL_IBS0;}
                    else {
                        var context=r.family().equals("original-fid")&&d.fam().get(a)[0].equals(d.fam().get(b)[0])?SnprelateKingRobust.Context.WITHIN_FAMILY:SnprelateKingRobust.Context.BETWEEN_OR_SINGLETONS;
                        var e=SnprelateKingRobust.estimate(kernel.estimate(hi*(hi-1)/2+low).counts(),context);expected=e.kinship();ibs0=e.ibs0();
                    }
                    same(expected,f[2],"complete KING kinship coefficient");same(ibs0,f[5],"complete KING IBS0 proportion");flags(f[5],f[6],f[7]);
                }
                matrixCells[0]++;
            });need(cells==n*n,"Incomplete complete original matrix");
        }
        var originalIDs=new HashSet<String>();boolean uniqueIDs=true;for(String[] v:d.bim())if(!originalIDs.add(v[1]))uniqueIDs=false;
        var result=map("status","passed-complete-native-transport-QC-and-original-output-contracts","samples",n,"input_variants",m,"qc_variants",d.qc().size(),
            "selected_variants",selected.size(),"matrix_cells",matrixCells[0],"retained_LD_edges",edges[0],"unassessable_off_diagonal_values",unassessable[0],
            "source_variant_IDs_unique",uniqueIDs,"nonfinite_matrix_values",nonfiniteMatrixValues[0],"original_indivbeta_average_hex",indivBetaAverage[0],
            "downstream_eligible",unassessable[0]==0&&nonfiniteMatrixValues[0]==0&&uniqueIDs&&(indivBetaAverage[0].isEmpty()||Double.isFinite(number(indivBetaAverage[0]))),"native_KING_coefficients_independently_recomputed",r.mode().equals("king-robust"),
            "GRM_or_LD_estimator_independently_refitted",false,"estimated_working_bytes",d.estimatedBytes(),"source_nonfinite_values_repaired",false);
        if(pcaAudit!=null)result.put("PCA",pcaAudit);return result;
    }
}

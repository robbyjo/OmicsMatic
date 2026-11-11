/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.SnprelateRequest.need;
import static org.jlinalg.cli.SnprelateAudit.map;

/** Original PCA field/invariant checks; no eigen decomposition, sign changes or source-value repair. */
final class SnprelatePcaAudit {
    private SnprelatePcaAudit() { }
    record Options(String algorithm,int eigenCount,boolean bayesian,String solver,int auxiliary,int iterations,int seed,boolean genmat) { }
    static Options options(Object value)throws IOException {
        Map<?,?> p=object(value);keys(p,"algorithm","eigen_count","bayesian","eigen_method","aux_dim","iterations","seed","need_genmat");
        String algorithm=text(p.get("algorithm")),solver=text(p.get("eigen_method"));
        need(Set.of("exact","randomized").contains(algorithm)&&Set.of("DSPEVX","DSPEV").contains(solver),"Declare original PCA algorithm and eigen solver explicitly");
        int count=(int)integer(p.get("eigen_count"),0,5000);boolean bayesian=SnprelateRequest.bool(p.get("bayesian")),genmat=SnprelateRequest.bool(p.get("need_genmat"));
        if(algorithm.equals("randomized"))need(count>0&&!bayesian&&!genmat&&solver.equals("DSPEVX"),"Randomized PCA requires positive eigen_count, bayesian=false, need_genmat=false and the explicit unused DSPEVX sentinel; source ignored options are not substituted");
        else need(genmat,"Exact PCA profile requires need_genmat=true for saved eigen-equation verification");
        return new Options(algorithm,count,bayesian,solver,(int)integer(p.get("aux_dim"),1,5000),(int)integer(p.get("iterations"),1,100),
            (int)integer(p.get("seed"),0,Integer.MAX_VALUE),genmat);
    }
    static long additionalBytes(SnprelateAudit.Data d,Options o)throws IOException {
        int n=d.samples(),k=o.eigenCount()==0?n:o.eigenCount();need(k<=n,"Requested PCA components exceed the declared sample population; no clamping is performed");
        if(o.algorithm().equals("randomized")) {
            long h=Math.multiplyExact((long)o.auxiliary(),o.iterations()+1);
            need(o.auxiliary()>=k&&o.auxiliary()<=n&&h<=d.qc().size(),"Randomized PCA requires eigen_count<=aux_dim<=samples and aux_dim*(iterations+1)<=QC variants for this tested profile");
            return Math.multiplyExact(16,Math.addExact(Math.multiplyExact(h,d.qc().size()),Math.multiplyExact(h,n)));
        }
        return Math.multiplyExact(16L*n,n);
    }
    static long additionalOperations(SnprelateAudit.Data d,Options o) {
        long n=d.samples(),h=Math.multiplyExact((long)o.auxiliary(),o.iterations()+1);
        return o.algorithm().equals("exact")?Math.multiplyExact(n,Math.multiplyExact(n,n)):
            Math.addExact(Math.multiplyExact(Math.multiplyExact(n,d.qc().size()),4*h),Math.multiplyExact(d.qc().size()+n,Math.multiplyExact(h,h)));
    }
    static long outputRows(SnprelateAudit.Data d,Options o) {
        long n=d.samples(),k=o.eigenCount()==0?n:o.eigenCount();return Math.addExact(n,Math.addExact(n*k,o.genmat()?n*n:0));
    }
    @FunctionalInterface private interface Row {void accept(String[] values,int index)throws IOException;}
    private static int table(Path file,int maximum,Runnable check,String[] header,Row row)throws IOException {
        int count=0;try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            String first=reader.readLine();need(first!=null&&java.util.Arrays.equals(first.split("\t",-1),header),"Original PCA header differs: "+file.getFileName());
            for(String line;(line=reader.readLine())!=null;) {
                check.run();need(count<maximum&&line.length()<=65536,"Complete PCA row/line capacity exceeded");String[] fields=line.split("\t",-1);
                need(fields.length==header.length,"Original PCA TSV shape differs");row.accept(fields,count++);
            }
        }return count;
    }
    private static int index(String raw,int maximum)throws IOException {
        try{int value=Integer.parseInt(raw);need(value>0&&value<=maximum&&Integer.toString(value).equals(raw),"Original PCA row/component identity differs");return value-1;}
        catch(NumberFormatException failure){throw new IOException("Invalid original PCA row index",failure);}
    }
    private static double numeric(String raw)throws IOException{return SnprelateAudit.number(raw);}
    static Map<String,Object> verify(Path work,SnprelateAudit.Data d,Options o,int maximumRows,Runnable check)throws IOException {
        int n=d.samples(),k=o.eigenCount()==0?n:o.eigenCount();long[] negatives={0},unusedNonfinite={0};String[] traceText={""};
        need(table(work.resolve("original-PCA-summary.tsv"),1,check,new String[]{"trace_xtx_hex","reported_bayesian","genmat_present","eigenvalues","eigenvector_columns","rng_changed"},(f,i)->{
            double trace=numeric(f[0]);need(Double.isFinite(trace)&&trace>0,"SCIENTIFIC DISCREPANCY: original PCA has no finite positive genotype variance");traceText[0]=f[0];
            need(f[1].equals(o.bayesian()?"TRUE":"FALSE")&&f[2].equals(o.genmat()?"TRUE":"FALSE")&&Integer.parseInt(f[3])==n&&Integer.parseInt(f[4])==k,
                "SCIENTIFIC DISCREPANCY: original PCA reported options or dimensions differ");
            need(f[5].equals(o.algorithm().equals("randomized")?"TRUE":"FALSE"),"SCIENTIFIC DISCREPANCY: original PCA RNG-consumption disposition differs");
        })==1,"Missing original PCA summary");
        double[] eigenvalues=new double[n],variance=new double[n];
        need(table(work.resolve("original-eigenvalues.tsv"),n,check,new String[]{"index","eigenvalue_hex","varprop_hex"},(f,i)->{
            need(index(f[0],n)==i,"Original eigenvalue order differs");eigenvalues[i]=numeric(f[1]);variance[i]=numeric(f[2]);
            if(i<k) {
                need(Double.isFinite(eigenvalues[i])&&Double.isFinite(variance[i]),"SCIENTIFIC DISCREPANCY: requested original PCA component is nonfinite");
                if(eigenvalues[i]<0)negatives[0]++;
            } else if(!Double.isFinite(eigenvalues[i])||!Double.isFinite(variance[i]))unusedNonfinite[0]++;
            if(o.algorithm().equals("exact")&&o.solver().equals("DSPEVX")&&i>=k)
                need(Double.isNaN(eigenvalues[i])&&Double.isNaN(variance[i]),"SCIENTIFIC DISCREPANCY: original uncomputed DSPEVX placeholders differ");
            if(o.algorithm().equals("randomized"))need(Double.doubleToLongBits(eigenvalues[i])==Double.doubleToLongBits((n-1)*variance[i]),"SCIENTIFIC DISCREPANCY: randomized eigenvalue/variance source relationship differs");
        })==n,"Incomplete original PCA eigenvalue population");
        double[][] vectors=new double[n][k];
        need(table(work.resolve("original-eigenvectors.tsv"),maximumRows,check,new String[]{"sample_row","component","value_hex","value_na","value_nan"},(f,i)->{
            int sample=index(f[0],n),component=index(f[1],k);need(sample==i%n&&component==i/n,"SCIENTIFIC DISCREPANCY: complete PCA sample/component order differs");
            double value=numeric(f[2]);need(Double.isFinite(value)&&f[3].equals("FALSE")&&f[4].equals("FALSE"),"SCIENTIFIC DISCREPANCY: requested original PCA loading is nonfinite");vectors[sample][component]=value;
        })==n*k,"Incomplete original PCA loading matrix");
        double orthogonality=0;
        for(int a=0;a<k;a++)for(int b=0;b<=a;b++) {
            check.run();double dot=0;for(int i=0;i<n;i++)dot+=vectors[i][a]*vectors[i][b];orthogonality=Math.max(orthogonality,Math.abs(dot-(a==b?1:0)));
        }
        need(orthogonality<=1e-8,"SCIENTIFIC DISCREPANCY: source PCA loadings fail the declared1e-8 orthogonality bound");
        Double equationError=null;double matrixTrace=0;
        if(o.genmat()) {
            double[][] matrix=new double[n][n];
            need(table(work.resolve("original-genotype-matrix.tsv"),maximumRows,check,new String[]{"row_index","col_index","value_hex"},(f,i)->{
                int a=index(f[0],n),b=index(f[1],n);need(a==i%n&&b==i/n,"Original normalized genotype-matrix order differs");
                double value=numeric(f[2]);need(Double.isFinite(value),"SCIENTIFIC DISCREPANCY: nonfinite normalized PCA genotype matrix");matrix[a][b]=value;
            })==n*n,"Incomplete original normalized PCA genotype matrix");
            for(int i=0;i<n;i++) {
                matrixTrace+=matrix[i][i];
                for(int j=0;j<i;j++)need(Double.doubleToLongBits(matrix[i][j])==Double.doubleToLongBits(matrix[j][i]),"SCIENTIFIC DISCREPANCY: source normalized PCA matrix is asymmetric");
            }
            need(Math.abs(matrixTrace-(n-1))<=1e-8*Math.max(1,n-1),"SCIENTIFIC DISCREPANCY: normalized PCA trace differs from n-1");
            double error=0;
            for(int c=0;c<k;c++) {
                check.run();double squared=0;
                for(int i=0;i<n;i++) {double sum=0;for(int j=0;j<n;j++)sum+=matrix[i][j]*vectors[j][c];double residual=sum-eigenvalues[c]*vectors[i][c];squared+=residual*residual;}
                double relative=Math.sqrt(squared)/Math.max(1,Math.abs(eigenvalues[c]));error=Math.max(error,relative);
                need(Math.abs(variance[c]-eigenvalues[c]/matrixTrace)<=1e-12*Math.max(1,Math.abs(variance[c])),"SCIENTIFIC DISCREPANCY: source PCA variance proportions differ from matrix trace");
            }
            need(error<=1e-8,"SCIENTIFIC DISCREPANCY: original exact PCA fails the declared1e-8 relative eigen-equation residual");equationError=error;
        } else need(!Files.exists(work.resolve("original-genotype-matrix.tsv")),"Randomized source unexpectedly supplied an exact normalized genotype matrix");
        return map("algorithm",o.algorithm(),"eigen_solver",o.solver(),"computed_components",k,"reported_eigenvalues",n,"unused_nonfinite_eigenvalues",unusedNonfinite[0],
            "negative_computed_eigenvalues_retained",negatives[0],"trace_XTX_hex",traceText[0],"normalized_genotype_matrix_present",o.genmat(),
            "maximum_orthogonality_error",orthogonality,"maximum_relative_eigen_equation_error",equationError,
            "randomized_projection_dimension_bound",o.algorithm().equals("randomized")?Math.min(n,Math.multiplyExact(o.auxiliary(),o.iterations()+1)):null,
            "source_eigenvector_signs_or_values_repaired",false,"independent_decomposition_performed",false);
    }
}

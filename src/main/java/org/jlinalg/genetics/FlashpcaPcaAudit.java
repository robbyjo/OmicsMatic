/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source behavior: Gad Abraham et al., FlashPCA v1.2.6 / v2.0 (GPL-3.0-or-later).
 * Native audit only: no replacement partial eigensolver or ancestry assignment. */
package org.jlinalg.genetics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete SNP-major BED and PCA-output audit with O(samples*variants*k+samples*k*k)
 * arithmetic and O(samples*k+variants) storage; no dense GRM.
 * Preserves edition-specific imputation, binomial scaling, divisors and PC scores. */
public final class FlashpcaPcaAudit {
    private FlashpcaPcaAudit() { }
    public enum Edition { V1_2_6, V2_0 }
    public enum Standardization { BINOM, BINOM2 }
    public enum Divisor { N1, P, NONE }
    public record Options(Edition edition, int components, Standardization standardization,
                          Divisor divisor, boolean forceTranspose, double auditTolerance) {
        public Options {
            Objects.requireNonNull(edition); Objects.requireNonNull(standardization); Objects.requireNonNull(divisor);
            require(components > 0 && components <= 1000 && Double.isFinite(auditTolerance)
                && auditTolerance > 0 && auditTolerance <= 1e-3, "Invalid component/audit tolerance");
            if (edition == Edition.V1_2_6) require(standardization == Standardization.BINOM && divisor == Divisor.N1,
                "Legacy profile uses original BINOM and N-1 divisor");
            else require(!forceTranspose, "Modern original online PCA has no transpose option");
        }
    }
    public record Limits(int samples, int variants, long metadataBytes, long retainedBytes,
                         long products, int lineBytes) {
        public Limits { require(samples>0 && variants>0 && metadataBytes>0 && retainedBytes>0 && products>0 && lineBytes>=64,"Positive complete audit capacities required"); }
    }
    public record Input(int samples, int variants, long calledGenotypes, long missingGenotypes,
                        int monomorphicVariants, boolean legacyTransposed, double divisor) { }
    public record Report(Input input, int components, double covarianceTrace, double maximumOrthogonalityError,
                         double maximumRelativeEigenResidual, double maximumPcScalingError,
                         double maximumLoadingError, double maximumMeanSdError,
                         double maximumPveError, double retainedVarianceFraction,
                         boolean completeGenotypesAudited, boolean partialPcaRefit,
                         boolean globalLargestEigenvalueOrderCertified) { }
    public static Input inspect(Path prefix, Options options, Limits limits, Runnable check) throws IOException {
        try (var data=open(prefix,limits,check)) {
            dimensions(data,options,limits); long called=0,missing=0;int monomorphic=0;
            for (var variant:data.selectedVariants()) {
                check.run();var row=data.readAllele1Dosages(variant);var moments=moments(row);
                called+=moments.called;missing+=row.length-moments.called;if(moments.sd==0)monomorphic++;
            }
            return input(data,options,called,missing,monomorphic);
        }
    }
    public static Report audit(Path prefix, Path output, Options options, Limits limits, Runnable check) throws IOException {
        try (var data=open(prefix,limits,check)) {
            dimensions(data,options,limits); int n=data.samples().size(),m=data.selectedVariants().size(),k=options.components();
            double[] values=vector(output.resolve("eigenvalues.txt"),k,limits,check);
            double[] pve=vector(output.resolve("pve.txt"),k,limits,check);
            for(int j=0;j<k;j++) require(values[j]>0 && pve[j]>0 && pve[j]<=1 && (j==0 || values[j]<=values[j-1]),"Nonpositive/unsorted original eigenvalues or invalid PVE");
            double[][] u=new double[n][k],ku=new double[n][k];
            try(var reader=new Rows(output.resolve("eigenvectors.txt"),limits.lineBytes())) {
                if(modern(options))reader.header("FID","IID",sequence("U",k));
                for(int i=0;i<n;i++){check.run();var row=reader.row(k+(modern(options)?2:0));if(modern(options))sample(row,data.samples().get(i));for(int j=0;j<k;j++)u[i][j]=number(row[j+(modern(options)?2:0)]);}
                reader.end();
            }
            var baseline=input(data,options,0,0,0);double pcError=0;
            try(var reader=new Rows(output.resolve("pcs.txt"),limits.lineBytes())) {
                if(modern(options))reader.header("FID","IID",sequence("PC",k));
                for(int i=0;i<n;i++) {
                    check.run();var row=reader.row(k+(modern(options)?2:0));if(modern(options))sample(row,data.samples().get(i));
                    for(int j=0;j<k;j++) {
                        double multiplier=modern(options)?Math.sqrt(values[j]):baseline.legacyTransposed()?Math.sqrt(values[j]*(n-1.0)):values[j];
                        pcError=Math.max(pcError,error(number(row[j+(modern(options)?2:0)]),u[i][j]*multiplier));
                    }
                }reader.end();
            }
            double orthogonality=0;
            for(int a=0;a<k;a++)for(int b=0;b<=a;b++){check.run();double sum=0;for(int i=0;i<n;i++)sum+=u[i][a]*u[i][b];orthogonality=Math.max(orthogonality,Math.abs(sum-(a==b?1:0)));}
            double trace=0,loadingError=0,momentError=0;long called=0,missing=0;int monomorphic=0;
            try(var load=new Rows(output.resolve("loadings.txt"),limits.lineBytes());var momentsFile=new Rows(output.resolve("meansd.txt"),limits.lineBytes())) {
                if(modern(options)){load.header("SNP","RefAllele",sequence("V",k));momentsFile.header("SNP","RefAllele",new String[]{"Mean","SD"});}
                for(var variant:data.selectedVariants()) {
                    check.run();double[] x=data.readAllele1Dosages(variant);var moments=moments(x);called+=moments.called;missing+=n-moments.called;
                    double sd=moments.sd*(options.standardization()==Standardization.BINOM2?Math.sqrt(2):1);
                    if(sd==0)monomorphic++;
                    var mr=momentsFile.row(modern(options)?4:2);if(modern(options))variant(mr,variant);
                    momentError=Math.max(momentError,error(number(mr[modern(options)?2:0]),moments.mean));
                    double suppliedSd=number(mr[modern(options)?3:1]);require(suppliedSd>=0,"Negative original standard deviation");
                    momentError=Math.max(momentError,error(suppliedSd,sd));
                    for(int i=0;i<n;i++) {
                        double imputed=Double.isNaN(x[i])?moments.mean:x[i];
                        // v1 leaves the imputed constant column in place; v2's zero-initialized lookup stays zero.
                        x[i]=sd>1e-9?(imputed-moments.mean)/sd:modern(options)?0:imputed;
                        trace+=x[i]*x[i]/baseline.divisor();
                    }
                    double[] cross=new double[k];
                    for(int i=0;i<n;i++)for(int j=0;j<k;j++)cross[j]+=x[i]*u[i][j];
                    var lr=load.row(k+(modern(options)?2:0));if(modern(options))variant(lr,variant);
                    for(int j=0;j<k;j++)loadingError=Math.max(loadingError,error(number(lr[j+(modern(options)?2:0)]),cross[j]/Math.sqrt(values[j]*baseline.divisor())));
                    for(int i=0;i<n;i++)for(int j=0;j<k;j++)ku[i][j]+=x[i]*cross[j]/baseline.divisor();
                }load.end();momentsFile.end();
            }
            require(Double.isFinite(trace)&&trace>0,"Nonpositive/nonfinite source covariance trace");
            double residual=0,pveError=0,totalPve=0;
            for(int j=0;j<k;j++) {
                check.run();double squared=0;
                for(int i=0;i<n;i++){double d=ku[i][j]-u[i][j]*values[j];squared+=d*d;}
                residual=Math.max(residual,Math.sqrt(squared)/values[j]);pveError=Math.max(pveError,error(pve[j],values[j]/trace));totalPve+=pve[j];
            }
            double tolerance=options.auditTolerance();
            require(orthogonality<=tolerance,"Original eigenvector orthogonality check failed: "+orthogonality);
            require(residual<=tolerance,"Original complete covariance/eigenvector residual check failed: "+residual);
            require(pcError<=tolerance&&loadingError<=tolerance&&momentError<=tolerance&&pveError<=tolerance,"Original PC/loadings/moments/PVE source identity check failed");
            require(totalPve<=1+tolerance,"Retained variance fraction exceeds total source trace");
            return new Report(input(data,options,called,missing,monomorphic),k,trace,orthogonality,residual,pcError,loadingError,momentError,pveError,totalPve,true,false,false);
        }
    }
    private static PlinkBedDataset open(Path prefix,Limits limits,Runnable check)throws IOException {
        require(Math.addExact(Files.size(Path.of(prefix+".bim")),Files.size(Path.of(prefix+".fam")))<=limits.metadataBytes(),"Complete BIM/FAM metadata byte capacity exceeded");
        var ids=new LinkedHashSet<String>();
        try(var rows=new Rows(Path.of(prefix+".bim"),limits.lineBytes())) {
            long retained=0;
            for(String[] row;(row=rows.optional(6))!=null;){check.run();retained=Math.addExact(retained,384L+2L*row[1].length());require(retained<=limits.retainedBytes(),"Complete variant-ID capacity exceeded");require(ids.size()<limits.variants()&&ids.add(row[1]),"Complete BIM population exceeds capacity or has duplicate variant ID");}
        }
        // Read both complete metadata populations before source execution; no blank-line repair.
        try(var rows=new Rows(Path.of(prefix+".fam"),limits.lineBytes())) {int n=0;while(rows.optional(6)!=null){check.run();require(++n<=limits.samples(),"Complete FAM capacity exceeded");}}
        return PlinkBedDataset.open(prefix,ids,new PlinkBedDataset.Limits(limits.samples(),limits.variants(),limits.variants(),limits.metadataBytes(),limits.retainedBytes(),limits.lineBytes()));
    }
    private static void dimensions(PlinkBedDataset data,Options options,Limits limits) {
        int n=data.samples().size(),m=data.selectedVariants().size(),k=options.components();
        require(n>k&&m>k,"Insufficient full input dimensions for requested components");
        if(modern(options))require(k<=Math.min(n,m)/3,"Original v2 would silently reduce ndim; supply an admissible complete dimension");
        long products=Math.multiplyExact(2L,Math.multiplyExact((long)n,Math.multiplyExact((long)m,k)));
        products=Math.addExact(products,Math.multiplyExact((long)n,Math.multiplyExact((long)k,k)));
        require(products<=limits.products(),"Complete native PCA audit work capacity exceeded; no sampling/truncation");
        long retained=Math.addExact(Math.multiplyExact(24L,Math.multiplyExact((long)n,k)),Math.multiplyExact(1024L,n+(long)m));
        require(retained<=limits.retainedBytes(),"Complete native PCA audit retained-memory capacity exceeded");
    }
    private static Input input(PlinkBedDataset data,Options o,long called,long missing,int monomorphic) {
        int n=data.samples().size(),m=data.selectedVariants().size();double divisor=o.divisor()==Divisor.N1?n-1.0:o.divisor()==Divisor.P?m:1;
        return new Input(n,m,called,missing,monomorphic,!modern(o)&&(o.forceTranspose()||n>m),divisor);
    }
    private record Moments(int called,double mean,double sd) { }
    private static Moments moments(double[] values) {
        int called=0;double sum=0;for(double x:values)if(!Double.isNaN(x)){called++;sum+=x;}
        require(called>0,"All-missing SNP cannot produce a finite source PCA; resolve input QC explicitly");
        double mean=sum/called,p=mean/2;return new Moments(called,mean,Math.sqrt(p*(1-p)));
    }
    private static boolean modern(Options o){return o.edition()==Edition.V2_0;}
    private static double error(double a,double b){return Math.abs(a-b)/Math.max(1,Math.abs(b));}
    private static void sample(String[] row,PlinkBedDataset.Sample sample){require(row[0].equals(sample.familyId())&&row[1].equals(sample.individualId()),"Original FID/IID population or order differs");}
    private static void variant(String[] row,PlinkBedDataset.Variant variant){require(row[0].equals(variant.id())&&row[1].equals(variant.allele1()),"Original SNP/A1 population or order differs; RefAllele is not proof of genome reference allele");}
    private static String[] sequence(String prefix,int n){String[] result=new String[n];for(int i=0;i<n;i++)result[i]=prefix+(i+1);return result;}
    private static double number(String s){double value=Double.parseDouble(s);require(Double.isFinite(value),"Nonfinite original PCA output; no value replacement");return value;}
    private static double[] vector(Path p,int count,Limits limits,Runnable check)throws IOException{
        double[] result=new double[count];try(var rows=new Rows(p,limits.lineBytes())){for(int i=0;i<count;i++){check.run();result[i]=number(rows.row(1)[0]);}rows.end();}return result;
    }
    private static final class Rows implements AutoCloseable {
        final InputStream reader;final int maximum;
        Rows(Path p,int maximum)throws IOException{this.maximum=maximum;reader=new BufferedInputStream(Files.newInputStream(p));}
        String[] optional(int width)throws IOException{
            var raw=new ByteArrayOutputStream();int b;
            while((b=reader.read())!=-1&&b!='\n'){require(raw.size()<maximum,"Source line byte capacity exceeded");raw.write(b);}
            if(b==-1&&raw.size()==0)return null;
            String line=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw.toByteArray())).toString();
            require(!line.isBlank(),"Blank source record");String[] fields=line.strip().split("\\s+");require(fields.length==width,"Source table width differs; no inferred column repair");return fields;
        }
        String[] row(int width)throws IOException{String[] result=optional(width);require(result!=null,"Truncated source output");return result;}
        void header(String a,String b,String[] names)throws IOException{String[] actual=row(names.length+2);require(actual[0].equals(a)&&actual[1].equals(b),"Unexpected source table header");for(int i=0;i<names.length;i++)require(actual[i+2].equals(names[i]),"Unexpected source numeric column order");}
        void end()throws IOException{require(reader.read()==-1,"Extra source records; complete population differs");}
        public void close()throws IOException{reader.close();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.*;

/** Complete positional input admission; no coercion, ID repair or hidden sample removal. */
final class GemmaInputs implements AutoCloseable {
    final PlinkBedDataset data;
    final boolean[] mask;
    final double[][] design;
    final double[] phenotype;
    final GemmaKinship.Result screened;
    final double[] suppliedKinship;
    private GemmaInputs(PlinkBedDataset data,boolean[] mask,double[][] design,double[] phenotype,
            GemmaKinship.Result screened,double[] supplied){this.data=data;this.mask=mask;this.design=design;this.phenotype=phenotype;this.screened=screened;this.suppliedKinship=supplied;}
    static GemmaInputs read(Path root,GemmaRequest.Spec spec,Runnable check)throws IOException{
        Path prefix=root.resolve("inputs/data");var ids=new LinkedHashSet<String>();
        try(var lines=new Lines(Path.of(prefix+".bim"),Math.min(spec.inputBytes(),67108864))){String line;while((line=lines.next())!=null){String[] f=fields(line);require(f.length==6&&ids.size()<spec.variants()&&ids.add(f[1]),"BIM requires six fields, unique IDs and admitted variant capacity");}}
        try(var lines=new Lines(Path.of(prefix+".fam"),Math.min(spec.inputBytes(),67108864))){String line;while((line=lines.next())!=null)require(fields(line).length==6,"FAM requires six fields per nonblank row");}
        var limits=new PlinkBedDataset.Limits(spec.samples(),spec.variants(),spec.variants(),Math.min(spec.inputBytes(),67108864),134217728,1048576);
        PlinkBedDataset data=PlinkBedDataset.open(prefix,ids,limits);
        try{
            int n=data.samples().size();long cells;
            try{cells=Math.multiplyExact((long)n,n);require(cells<Integer.MAX_VALUE-8&&Math.multiplyExact(8L,Math.addExact(3*cells,600L*n))<=spec.numeric().maximumNumericBytes(),"Complete native screening, input and validation buffers exceed numeric_bytes");if(spec.association())require(Math.multiplyExact(cells,n)<=spec.numeric().maximumMultiplyAdds(),"Kinship validation work exceeds multiply_adds");}catch(ArithmeticException e){throw new IOException("Complete GEMMA input dimensions overflow",e);}
            sampleIds(root.resolve("inputs/sample-ids.tsv"),data,spec);boolean[] mask=new boolean[n];double[] y=new double[n];double[][] design=new double[n][];
            try(var lines=new Lines(root.resolve("inputs/phenotype.txt"),spec.inputBytes())){for(int i=0;i<n;i++){String line=lines.next();require(line!=null,"Phenotype has fewer rows than FAM");String[] f=fields(line);require(f.length==1,"Require one explicit phenotype column in FAM order");y[i]=value(f[0]);mask[i]=Double.isFinite(y[i]);}require(lines.next()==null,"Phenotype has more rows than FAM");}
            if(spec.originals().containsKey("inputs/covariates.txt")){
                try(var lines=new Lines(root.resolve("inputs/covariates.txt"),spec.inputBytes())){int columns=-1;for(int i=0;i<n;i++){String line=lines.next();require(line!=null,"Covariates have fewer rows than FAM");String[] f=fields(line);if(columns==-1)columns=f.length;require(columns>0&&columns<=64&&f.length==columns,"Require rectangular covariates with at most64 columns including explicit first intercept");design[i]=new double[columns];for(int c=0;c<columns;c++){design[i][c]=value(f[c]);mask[i]&=Double.isFinite(design[i][c]);}}require(lines.next()==null,"Covariates have more rows than FAM");}
            }else for(int i=0;i<n;i++)design[i]=new double[]{1};
            var options=spec.numeric();
            if(spec.association()){
                long projectionWork=8L*n*design[0].length*(design[0].length+1L);
                long remaining=options.maximumMultiplyAdds()-Math.addExact(Math.multiplyExact(cells,n),projectionWork);
                require(remaining>0,"Complete GEMMA screening plus matrix validation exceeds multiply_adds");
                options=new GemmaKinship.Options(options.mode(),options.minimumFrequency(),options.maximumMissingness(),options.minimumHwe(),options.maximumCovariateFraction(),options.maximumNumericBytes(),remaining);
            }
            var result=GemmaKinship.calculate(data,mask,design,options,check);
            double[] supplied=null;
            if(spec.association()){
                require(result.qcSamples()>design[0].length+1,"Association requires positive residual degrees of freedom");double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(int i=0;i<n;i++)if(mask[i]){min=Math.min(min,y[i]);max=Math.max(max,y[i]);}require(max>min,"Constant complete-case phenotype: original GEMMA may exit0 with nan p-values; no association admitted");
                residualVariation(mask,design,y,result.qcSamples(),check);
                sampleIds(root.resolve("inputs/kinship-ids.tsv"),data,spec);supplied=matrix(root.resolve("inputs/kinship.txt"),n,spec.inputBytes(),check);positiveSemidefinite(supplied,n,check);
            }
            return new GemmaInputs(data,mask,design,y,result,supplied);
        }catch(IOException|RuntimeException|Error e){data.close();throw e;}
    }
    private static void sampleIds(Path path,PlinkBedDataset data,GemmaRequest.Spec spec)throws IOException{
        try(var lines=new Lines(path,spec.inputBytes())){for(var sample:data.samples()){String row=lines.next();require(row!=null,"Sample-ID sidecar has fewer rows than FAM");String[] f=fields(row);require(f.length==2&&f[0].equals(sample.familyId())&&f[1].equals(sample.individualId()),"Exact FID/IID sidecar order differs from FAM; no reordering is inferred");}require(lines.next()==null,"Sample-ID sidecar has extra rows");}
    }
    static double[] matrix(Path path,int n,long bytes,Runnable check)throws IOException{
        double[] result=new double[Math.multiplyExact(n,n)];
        try(var lines=new Lines(path,bytes)){for(int i=0;i<n;i++){check.run();String line=lines.next();require(line!=null,"Kinship matrix has fewer rows than FAM");String[] fields=fields(line);require(fields.length==n,"Kinship matrix is not FAM-by-FAM square");for(int j=0;j<n;j++){double v=finite(fields[j]);result[i*n+j]=v;}}require(lines.next()==null,"Kinship matrix has extra rows");}
        for(int i=0;i<n;i++)for(int j=0;j<i;j++)require(Math.abs(result[i*n+j]-result[j*n+i])<=1e-10*Math.max(1,Math.max(Math.abs(result[i*n+j]),Math.abs(result[j*n+i]))),"Asymmetric kinship matrix; no symmetrization is performed");return result;
    }
    private static void positiveSemidefinite(double[] original,int n,Runnable check)throws IOException{
        double scale=1;for(double v:original)scale=Math.max(scale,Math.abs(v));double tolerance=1e-6*n*scale;double[] a=original.clone();
        // Validation tolerance accommodates the original text matrix's six-digit
        // output. The supplied matrix bytes passed to GEMMA are NEVER changed.
        for(int i=0;i<n;i++){
            check.run();for(int j=0;j<=i;j++){
                double v=a[i*n+j]+(i==j?tolerance:0);for(int k=0;k<j;k++)v-=a[i*n+k]*a[j*n+k];
                if(i==j){require(Double.isFinite(v)&&v>0,"Kinship is indefinite beyond the declared text-rounding validation tolerance; supply a valid explicitly ordered matrix");a[i*n+j]=Math.sqrt(v);}else a[i*n+j]=v/a[j*n+j];
            }
        }
    }
    private static void residualVariation(boolean[] mask,double[][] design,double[] y,int q,Runnable check)throws IOException{
        int k=design[0].length;double[][] basis=new double[k][q];double[] residual=new double[q];double originalNorm=0;
        for(int i=0,r=0;i<mask.length;i++)if(mask[i]){residual[r]=y[i];originalNorm+=y[i]*y[i];for(int c=0;c<k;c++)basis[c][r]=design[i][c];r++;}
        require(Double.isFinite(originalNorm)&&originalNorm>0,"Nonfinite/zero phenotype norm; supply an explicitly prepared finite-scale trait");
        for(int c=0;c<k;c++){
            check.run();for(int pass=0;pass<2;pass++)for(int previous=0;previous<c;previous++){double dot=0;for(int r=0;r<q;r++)dot+=basis[c][r]*basis[previous][r];for(int r=0;r<q;r++)basis[c][r]-=dot*basis[previous][r];}
            double norm=0;for(double v:basis[c])norm+=v*v;norm=Math.sqrt(norm);require(Double.isFinite(norm)&&norm>0,"Unresolved phenotype design projection");for(int r=0;r<q;r++)basis[c][r]/=norm;
        }
        for(int pass=0;pass<2;pass++)for(double[] column:basis){double dot=0;for(int r=0;r<q;r++)dot+=column[r]*residual[r];for(int r=0;r<q;r++)residual[r]-=dot*column[r];}
        double rss=0;for(double v:residual)rss+=v*v;require(Double.isFinite(rss)&&rss>1e-24*originalNorm,"Phenotype has zero or numerically unresolved variation after covariate projection; no association admitted. Preserve the source and explicitly prepare a valid finite-scale trait");
    }
    static String[] fields(String line)throws IOException{require(!line.isBlank(),"Blank positional input row is not admitted");return line.strip().split("[ \\t]+");}
    static double finite(String s)throws IOException{try{require(s.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require a finite decimal numeric value: "+s);double v=Double.parseDouble(s);require(Double.isFinite(v),"Nonfinite numeric input/output: "+s);return v;}catch(NumberFormatException e){throw new IOException("Invalid exact numeric value: "+s,e);}}
    private static double value(String s)throws IOException{return s.equals("NA")?Double.NaN:finite(s);}
    static final class Lines implements AutoCloseable{
        private final InputStream input;private final long maximum;private long bytes;
        Lines(Path p,long maximum)throws IOException{this.input=new BufferedInputStream(Files.newInputStream(p));this.maximum=maximum;}
        String next()throws IOException{var out=new ByteArrayOutputStream();int c;while((c=input.read())!=-1){require(++bytes<=maximum,"Complete text exceeds input/output byte capacity");if(c==10)break;require(out.size()<1048576,"GEMMA text line exceeds1MiB capacity");out.write(c);}if(c==-1&&out.size()==0)return null;String s=NextflowFiles.utf8(out.toByteArray());if(s.endsWith("\r"))s=s.substring(0,s.length()-1);require(s.indexOf('\r')<0,"Embedded carriage return in positional input/output");return s;}
        public void close()throws IOException{input.close();}
    }
    public void close()throws IOException{data.close();}
    private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
}

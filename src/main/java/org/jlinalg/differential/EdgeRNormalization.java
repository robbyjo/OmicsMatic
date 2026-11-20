/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Normalization algorithms derived from edgeR (GPL >= 2), Mark Robinson,
 * Gordon Smyth and the edgeR team. See docs/literature/EDGER_NORMALIZATION.md.
 */
package org.jlinalg.differential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Native double-precision count-matrix normalization from the declared edgeR
 * sources. Rows are features; columns are samples. No R process is invoked.
 * This class does not estimate dispersions or perform differential tests.
 * Non-finite original outcomes are retained and explicitly reported by
 * {@link Result#finite()}, rather than replaced with successful values.
 */
public final class EdgeRNormalization {
    private static final double LN2 = Math.log(2), LNMILLION = Math.log(1e6);
    private EdgeRNormalization() { }

    /** Exact source editions with the same supported matrix algorithms. */
    public enum Edition { EDGER_3_32_1, EDGER_3_36_0, EDGER_3_40_2, EDGER_3_42_4, EDGER_4_10_7 }
    public enum Method { TMM, TMMWSP, RLE, UPPER_QUARTILE, NONE }

    /** referenceColumn is zero-based; null selects the original reference rule. */
    public record Options(Method method, Integer referenceColumn, double logratioTrim,
            double sumTrim, boolean weighting, double aCutoff, double quantile) {
        public Options {
            Objects.requireNonNull(method, "method");
            if (!Double.isFinite(logratioTrim) || logratioTrim < 0 || logratioTrim >= .5
                    || !Double.isFinite(sumTrim) || sumTrim < 0 || sumTrim >= .5)
                throw new IllegalArgumentException("Trimming fractions must be finite and in [0,0.5)");
            if (Double.isNaN(aCutoff) || !Double.isFinite(quantile) || quantile < 0 || quantile > 1)
                throw new IllegalArgumentException("Require non-NaN A cutoff and quantile in [0,1]");
        }
        public static Options defaults(Method method) { return new Options(method, null, .3, .05, true, -1e10, .75); }
    }

    /** Arrays are defensively copied, including when accessed. */
    public record Result(double[] librarySizes, double[] factors, double[] effectiveLibrarySizes,
            Integer referenceColumn, List<String> warnings) {
        public Result {
            librarySizes = librarySizes.clone(); factors = factors.clone();
            effectiveLibrarySizes = effectiveLibrarySizes.clone(); warnings = List.copyOf(warnings);
        }
        @Override public double[] librarySizes() { return librarySizes.clone(); }
        @Override public double[] factors() { return factors.clone(); }
        @Override public double[] effectiveLibrarySizes() { return effectiveLibrarySizes.clone(); }
        /** False means no finite normalization result; inspect the warnings. */
        public boolean finite() {
            for (int j = 0; j < factors.length; j++)
                if (!Double.isFinite(factors[j]) || factors[j] <= 0 || !Double.isFinite(effectiveLibrarySizes[j])) return false;
            return true;
        }
    }

    /**
     * Calculates original matrix normalization factors. Supplied library sizes
     * must match all columns exactly; unlike R, this typed API never recycles
     * them. Nonnegative finite counts may be fractional. An empty feature matrix
     * requires explicit library sizes to declare its sample dimension.
     */
    public static Result normalize(double[][] counts, double[] librarySizes, Edition edition, Options options) {
        Objects.requireNonNull(edition, "edition"); Objects.requireNonNull(options, "options");
        int samples = dimensions(counts, librarySizes);
        double[] libs = libraries(counts, librarySizes, samples);
        if (options.referenceColumn != null && (options.referenceColumn < 0 || options.referenceColumn >= samples))
            throw new IllegalArgumentException("Reference column is outside the count matrix");
        List<String> warnings = new ArrayList<>();
        if (edition == Edition.EDGER_4_10_7) warnings.add("Original 4.10.7 message: calcNormFactors has been renamed to normLibSizes");
        double[][] x = Arrays.stream(counts).filter(row -> Arrays.stream(row).anyMatch(v -> v > 0)).toArray(double[][]::new);
        double[] factors = new double[samples]; Arrays.fill(factors, 1);
        Integer reference = null;
        if (x.length > 0 && samples > 1 && options.method != Method.NONE) {
            for (double lib : libs) if (lib <= 0)
                throw new IllegalArgumentException("Nondegenerate normalization requires positive library sizes; preserve zero libraries and choose an explicit supported analysis");
            switch (options.method) {
                case TMM -> {
                    reference = options.referenceColumn;
                    if (reference == null) {
                        double[] q = quantiles(x, libs, .75, null);
                        reference = quantile(q, .5) < 1e-20 ? maximumSquareRootColumn(x, samples) : closestToMean(q);
                    }
                    for (int j = 0; j < samples; j++) factors[j] = tmm(column(x,j), column(x,reference), libs[j], libs[reference], options, warnings);
                }
                case TMMWSP -> {
                    reference = options.referenceColumn == null ? maximumSquareRootColumn(x,samples) : options.referenceColumn;
                    if (options.aCutoff != -1e10) warnings.add("Original TMMwsp accepts Acutoff but does not apply it; the supplied value is retained without filtering");
                    for (int j = 0; j < samples; j++) factors[j] = tmmwsp(column(x,j),column(x,reference),libs[j],libs[reference],options);
                }
                case RLE -> {
                    List<double[]> positive = new ArrayList<>();
                    for (double[] row : x) {
                        double[] logs = Arrays.stream(row).map(Math::log).toArray();
                        double gm = Math.exp(mean(logs));
                        if (gm > 0) {
                            double[] ratio = new double[samples];
                            for (int j = 0; j < samples; j++) ratio[j] = row[j] / gm;
                            positive.add(ratio);
                        }
                    }
                    for (int j = 0; j < samples; j++) factors[j] = quantile(column(positive.toArray(double[][]::new),j),.5) / libs[j];
                }
                case UPPER_QUARTILE -> factors = quantiles(x,libs,options.quantile,warnings);
                case NONE -> { }
            }
        }
        double scale = Math.exp(mean(Arrays.stream(factors).map(Math::log).toArray()));
        double[] effective = new double[samples];
        for (int j = 0; j < samples; j++) { factors[j] /= scale; effective[j] = libs[j] * factors[j]; }
        if (Arrays.stream(factors).anyMatch(v -> !Double.isFinite(v) || v <= 0)
                || Arrays.stream(effective).anyMatch(v -> !Double.isFinite(v)))
            warnings.add("NO COMPLETE RESULT: original normalization is non-finite or non-positive; values are retained without repair");
        return new Result(libs,factors,effective,reference,warnings);
    }

    /** Raw CPM, or original library-scaled-prior log2 CPM, with no offsets. */
    public static double[][] cpm(double[][] counts, double[] librarySizes, Edition edition, boolean log, double priorCount) {
        Objects.requireNonNull(edition,"edition");
        int samples = dimensions(counts,librarySizes); double[] libs = libraries(counts,librarySizes,samples);
        for (double lib : libs) if (lib <= 0) throw new IllegalArgumentException("CPM library sizes must be positive");
        if (!Double.isFinite(priorCount) || priorCount < 0) throw new IllegalArgumentException("Prior count must be finite and nonnegative");
        // Original add_prior.cpp uses an ordinary double sum, not a scaled shortcut.
        double average = 0; for (double lib : libs) average += lib; average /= samples;
        double[][] result = new double[counts.length][samples];
        for (int i = 0; i < counts.length; i++) for (int j = 0; j < samples; j++) {
            if (!log) result[i][j] = edition==Edition.EDGER_4_10_7 ? counts[i][j]*1e6/libs[j] : counts[i][j]*(1e6/libs[j]);
            else {
                double prior = priorCount * libs[j] / average, value = counts[i][j] + prior;
                result[i][j] = value > 0 ? (Math.log(value) - Math.log(libs[j] + 2*prior) + LNMILLION) / LN2 : Double.NaN;
            }
        }
        return result;
    }

    /** Original matrix RPKM with literal, positive per-feature lengths in bases. */
    public static double[][] rpkm(double[][] counts, double[] librarySizes, double[] geneLengths, Edition edition, boolean log, double priorCount) {
        Objects.requireNonNull(geneLengths,"geneLengths");
        if (geneLengths.length != counts.length) throw new IllegalArgumentException("Require one literal gene length per feature; no recycling or annotation inference");
        for (double value : geneLengths) if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("Gene lengths must be finite and positive");
        double[][] result = cpm(counts,librarySizes,edition,log,priorCount);
        for (int i = 0; i < result.length; i++) for (int j = 0; j < result[i].length; j++)
            result[i][j] = log ? result[i][j] - log2(geneLengths[i]/1000) : result[i][j] / (geneLengths[i]/1000);
        return result;
    }

    private static double tmm(double[] obs,double[] ref,double nO,double nR,Options o,List<String> warnings) {
        double[] m = new double[obs.length], a = new double[obs.length], v = new double[obs.length]; int n=0;
        for (int i=0;i<obs.length;i++) {
            double logR=log2((obs[i]/nO)/(ref[i]/nR));
            double absE=(log2(obs[i]/nO)+log2(ref[i]/nR))/2;
            if (Double.isFinite(logR) && Double.isFinite(absE) && absE>o.aCutoff) {
                m[n]=logR; a[n]=absE; v[n]=(nO-obs[i])/nO/obs[i]+(nR-ref[i])/nR/ref[i]; n++;
            }
        }
        if (n==0) { warnings.add("Original TMM has no finite shared features after A filtering; its max(empty) warning and unity-factor outcome are retained"); return 1; }
        double maximum=0;for(int i=0;i<n;i++) maximum=Math.max(maximum,Math.abs(m[i]));if(maximum<1e-6)return 1;
        m=Arrays.copyOf(m,n);a=Arrays.copyOf(a,n);double[] rankM=ranks(m),rankA=ranks(a);
        int loM=(int)Math.floor(n*o.logratioTrim)+1,hiM=n+1-loM,loA=(int)Math.floor(n*o.sumTrim)+1,hiA=n+1-loA;
        double[] num=new double[n],den=new double[n];int used=0;
        for(int i=0;i<n;i++)if(rankM[i]>=loM&&rankM[i]<=hiM&&rankA[i]>=loA&&rankA[i]<=hiA) {
            num[used]=o.weighting?m[i]/v[i]:m[i];den[used]=o.weighting?1/v[i]:1;used++;
        }
        double f=sumWithoutNaN(num,used)/sumWithoutNaN(den,used);
        // The original explicitly maps missing TMM means to log-factor zero.
        if(Double.isNaN(f))f=0;
        return Math.pow(2,f);
    }

    private static double tmmwsp(double[] obs,double[] ref,double nO,double nR,Options o) {
        double[] x=new double[obs.length],y=new double[obs.length],singleX=new double[obs.length],singleY=new double[obs.length];
        int n=0,nx=0,ny=0;
        for(int i=0;i<obs.length;i++) {
            boolean px=obs[i]>1e-14,py=ref[i]>1e-14;
            if(px&&py) {x[n]=obs[i];y[n++]=ref[i];}
            else if(px)singleX[nx++]=obs[i];else if(py)singleY[ny++]=ref[i];
        }
        Arrays.sort(singleX,0,nx);Arrays.sort(singleY,0,ny);
        for(int k=0;k<Math.min(nx,ny);k++) {x[n]=singleX[nx-1-k];y[n++]=singleY[ny-1-k];}
        if(n==0)return 1;
        double[] m=new double[n],a=new double[n],shrunk=new double[n],px=new double[n],py=new double[n];double maximum=0;
        for(int i=0;i<n;i++) {
            px[i]=x[i]/nO;py[i]=y[i]/nR;m[i]=log2(px[i]/py[i]);a[i]=.5*log2(px[i]*py[i]);
            shrunk[i]=log2(((x[i]+.5)/(nO+.5))/((y[i]+.5)/(nR+.5)));maximum=Math.max(maximum,Math.abs(m[i]));
        }
        if(maximum<1e-6)return 1;
        Integer[] orderM=order(m,shrunk),orderA=order(a,null);boolean[] keepM=new boolean[n],keepA=new boolean[n];
        int loM=(int)(n*o.logratioTrim),hiM=n-loM,loA=(int)(n*o.sumTrim),hiA=n-loA;
        for(int k=loM;k<hiM;k++)keepM[orderM[k]]=true;for(int k=loA;k<hiA;k++)keepA[orderA[k]]=true;
        double[] num=new double[n],den=new double[n];int used=0;
        for(int i=0;i<n;i++)if(keepM[i]&&keepA[i]) {
            double v=(1-px[i])/px[i]/nO+(1-py[i])/py[i]/nR,w=o.weighting?(1+1e-6)/(v+1e-6):1;
            num[used]=w*m[i];den[used++]=w;
        }
        return Math.pow(2,sum(num,used)/sum(den,used));
    }

    private static double[] ranks(double[] values) {
        Integer[] order=order(values,null);double[] result=new double[values.length];
        for(int start=0;start<values.length;) {
            int end=start+1;while(end<values.length&&values[order[start]]==values[order[end]])end++;
            double rank=(start+1+end)/2.0;for(int j=start;j<end;j++)result[order[j]]=rank;start=end;
        }
        return result;
    }
    private static Integer[] order(double[] first,double[] second) {
        Integer[] order=new Integer[first.length];Arrays.setAll(order,i->i);
        Comparator<Integer> cmp=(i,j)->compare(first[i],first[j]);
        if(second!=null)cmp=cmp.thenComparing((i,j)->compare(second[i],second[j]));
        Arrays.sort(order,cmp);return order;
    }
    private static int compare(double a,double b) {
        if(Double.isNaN(a))return Double.isNaN(b)?0:1;if(Double.isNaN(b))return -1;
        return a<b?-1:a>b?1:0;
    }
    private static double[] quantiles(double[][] x,double[] libs,double p,List<String> warnings) {
        double[] q=new double[libs.length];boolean zero=false;
        for(int j=0;j<q.length;j++) {q[j]=quantile(column(x,j),p);zero|=q[j]==0;q[j]/=libs[j];}
        if(zero&&warnings!=null)warnings.add("Original edgeR warning: One or more quantiles are zero");return q;
    }
    private static int closestToMean(double[] x) {
        double average=mean(x),best=Double.POSITIVE_INFINITY;int which=0;
        for(int i=0;i<x.length;i++){double distance=Math.abs(x[i]-average);if(distance<best){best=distance;which=i;}}return which;
    }
    private static int maximumSquareRootColumn(double[][] x,int samples) {
        int which=0;double best=Double.NEGATIVE_INFINITY;
        for(int j=0;j<samples;j++){double[] values=column(x,j);for(int i=0;i<values.length;i++)values[i]=Math.sqrt(values[i]);double value=sum(values,values.length);if(value>best){best=value;which=j;}}return which;
    }
    private static double quantile(double[] values,double p) {
        if(values.length==0)return Double.NaN;double[] sorted=values.clone();Arrays.sort(sorted);
        double h=(sorted.length-1)*p;int low=(int)Math.floor(h),high=(int)Math.ceil(h);
        return low==high||sorted[low]==sorted[high]?sorted[low]:(1-(h-low))*sorted[low]+(h-low)*sorted[high];
    }
    private static double log2(double x){return EdgeRLog2.value(x);}
    private static double[] column(double[][] x,int j){double[] v=new double[x.length];for(int i=0;i<x.length;i++)v[i]=x[i][j];return v;}
    private static double mean(double[] values){return sum(values,values.length)/values.length;}
    private static double sumWithoutNaN(double[] values,int n){double[] good=new double[n];int k=0;for(int i=0;i<n;i++)if(!Double.isNaN(values[i]))good[k++]=values[i];return sum(good,k);}
    private static double sum(double[] values,int n) {
        double result=0,correction=0;
        for(int i=0;i<n;i++) {
            double x=values[i],next=result+x;
            if(!Double.isFinite(x)||!Double.isFinite(next)){for(int k=i;k<n;k++)result+=values[k];return result;}
            correction+=Math.abs(result)>=Math.abs(x)?(result-next)+x:(x-next)+result;result=next;
        }
        return result+correction;
    }
    private static int dimensions(double[][] counts,double[] sizes) {
        Objects.requireNonNull(counts,"counts");int n=counts.length==0?(sizes==null?0:sizes.length):Objects.requireNonNull(counts[0],"row").length;
        if(n==0)throw new IllegalArgumentException("Require at least one sample; empty features require explicit sample library sizes");
        if(sizes!=null&&sizes.length!=n)throw new IllegalArgumentException("Library sizes must match sample columns exactly; no recycling");
        for(double[] row:counts) {
            if(row==null||row.length!=n)throw new IllegalArgumentException("Count matrix must be rectangular");
            for(double x:row)if(!Double.isFinite(x)||x<0)throw new IllegalArgumentException("Counts must be finite and nonnegative; no repair");
        }
        return n;
    }
    private static double[] libraries(double[][] counts,double[] sizes,int n) {
        double[] libs=sizes==null?new double[n]:sizes.clone();
        if(sizes==null)for(int j=0;j<n;j++){double[] column=column(counts,j);libs[j]=sum(column,column.length);}
        for(double lib:libs)if(!Double.isFinite(lib)||lib<0)throw new IllegalArgumentException("Library sizes must be finite and nonnegative; no repair");return libs;
    }
}

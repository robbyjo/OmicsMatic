/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Independent Java implementation of the documented/source-audited dispersion
 * algorithms in Scanpy 1.9.3 and 1.13.0. Original source identities and notices
 * accompany the reference controls; no upstream runtime is invoked here. */
package org.jlinalg.singlecell;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;

/** Float64 dispersion-based HVGs, with explicit editions, storage and batch categories.
 * Input is the supplied logarithmized expression matrix; only the SEURAT
 * flavor applies the original expm1 inversion. No normalization, log base,
 * population, batch order, NaN repair or alternative HVG flavor is inferred.
 * The immutable CSC container is the transpose of cells-by-genes CSR storage.
 */
public final class ScanpyHighlyVariableGenes {
    private ScanpyHighlyVariableGenes() { }
    public enum Profile { SCANPY_1_9_3, SCANPY_1_13_0 }
    public enum Storage { DENSE, CSR }
    public enum Flavor { SEURAT, CELL_RANGER }
    public enum Export { DATAFRAME, ANNDATA }
    public record Options(Flavor flavor,Integer topGenes,int bins,double minMean,double maxMean,
                          double minDispersion,double maxDispersion,Double logBase,
                          Boolean filterUnexpressed,Export export) {
        public Options {
            Objects.requireNonNull(flavor);Objects.requireNonNull(export);
            if(topGenes!=null&&topGenes<1)throw new IllegalArgumentException("n_top_genes must be a positive integer or null");
            if(bins<1||bins>Integer.MAX_VALUE-8||Double.isNaN(minMean)||Double.isNaN(maxMean)||Double.isNaN(minDispersion)||Double.isNaN(maxDispersion))
                throw new IllegalArgumentException("Require positive n_bins and declared non-NaN cutoffs");
            if(logBase!=null&&(!Double.isFinite(logBase)||logBase<=0||logBase==1))
                throw new IllegalArgumentException("Declare a finite positive log base other than one, or null for natural log");
        }
        public static Options defaults(Flavor flavor) {return new Options(flavor,null,20,.0125,3,.5,Double.POSITIVE_INFINITY,null,null,Export.ANNDATA);}
    }
    /** Categories and every cell label are explicit; empty/missing/unknown labels are not repaired. */
    public record Batches(List<String> labels,List<String> categories) {
        public Batches {
            labels=List.copyOf(labels);categories=List.copyOf(categories);
            if(categories.isEmpty()||new HashSet<>(categories).size()!=categories.size())
                throw new IllegalArgumentException("Declare nonempty unique batch categories");
            for(String category:categories)if(category.isBlank())throw new IllegalArgumentException("Blank batch category");
            Set<String> known=new HashSet<>(categories);
            for(String label:labels)if(label.isBlank()||!known.contains(label))throw new IllegalArgumentException("Missing/unknown batch label");
        }
    }
    public record Limits(int features,int cells,int storedValues,int batches,long workingBytes,long maximumWork) {
        public Limits {
            if(features<1||cells<1||storedValues<0||batches<1||features>Integer.MAX_VALUE-8||cells>Integer.MAX_VALUE-8
                    ||storedValues>Integer.MAX_VALUE-8||workingBytes<0||maximumWork<0)
                throw new IllegalArgumentException("Invalid complete HVG capacities");
        }
        public static Limits defaults(){return new Limits(1_000_000,10_000_000,10_000_000,10_000,1_073_741_824L,2_000_000_000L);}
    }
    public record Warning(String code,long affected,String message,String requiredAction) { }
    private static final String ACTION="Preserve supplied values, edition, flavor, cutoffs, batch categories and diagnostics. Correct the named source/data/numerical/capacity cause and retry a NEW analysis; do not silently repair missing statistics, alter cutoffs, select another flavor or substitute an author population.";

    public static final class Result {
        private final List<String> features;
        private final Profile profile;
        private final Options options;
        private final double[] means,dispersions,normalized;
        private final boolean[] selected,intersection;
        private final int[] batchCounts;
        private final List<BatchResult> batches;
        private final List<Warning> warnings;
        private final boolean usable;
        private Result(Matrix matrix,Profile profile,Options options,double[] means,double[] dispersions,double[] normalized,
                       boolean[] selected,int[] batchCounts,boolean[] intersection,List<BatchResult> batches,
                       List<Warning> warnings,boolean usable) {
            features=matrix.features();this.profile=profile;this.options=options;this.means=means;this.dispersions=dispersions;
            this.normalized=normalized;this.selected=selected;this.batchCounts=batchCounts;this.intersection=intersection;
            this.batches=List.copyOf(batches);this.warnings=List.copyOf(warnings);this.usable=usable;
        }
        public Profile profile(){return profile;}
        public Options options(){return options;}
        public List<String> features(){return features;}
        public boolean usable(){return usable;}
        public double[] means(){return means.clone();}
        public double[] dispersions(){return dispersions.clone();}
        /** The declared source export: ANNDATA rounds this field through float32 after selection. */
        public double[] normalizedDispersions(){return normalized.clone();}
        public boolean[] diagnosticSelected(){return selected.clone();}
        public boolean[] selected(){if(!usable)throw new ArithmeticException("No admitted HVG selection: inspect diagnostics. "+ACTION);return selected.clone();}
        public List<String> selectedFeatures(){
            boolean[] mask=selected();List<String> result=new ArrayList<>();
            for(int g=0;g<mask.length;g++)if(mask[g])result.add(features.get(g));return List.copyOf(result);
        }
        /** Empty when no batch grouping was requested; zero is never substituted for an absent field. */
        public int[] highlyVariableBatchCounts(){return batchCounts.clone();}
        public boolean[] highlyVariableIntersection(){return intersection.clone();}
        public List<BatchResult> batchDiagnostics(){return batches;}
        public List<Warning> warnings(){return warnings;}
    }
    public static final class BatchResult {
        private final String category;
        private final int cells;
        private final double[] means,dispersions,normalized,edges;
        private final boolean[] selected,filtered,sourceMissing;
        private final int[] bins;
        private final boolean usable;
        private BatchResult(String category,int cells,double[] means,double[] dispersions,double[] normalized,double[] edges,
                            boolean[] selected,boolean[] filtered,boolean[] sourceMissing,int[] bins,boolean usable) {
            this.category=category;this.cells=cells;this.means=means;this.dispersions=dispersions;this.normalized=normalized;
            this.edges=edges;this.selected=selected;this.filtered=filtered;this.sourceMissing=sourceMissing;this.bins=bins;this.usable=usable;
        }
        public String category(){return category;}
        public int cells(){return cells;}
        public double[] means(){return means.clone();}
        public double[] dispersions(){return dispersions.clone();}
        public double[] normalizedDispersions(){return normalized.clone();}
        /** Full-precision pd.cut boundaries, not rounded display-label endpoints. */
        public double[] binEdges(){return edges.clone();}
        /** Zero-based intervals (edge[i],edge[i+1]]; -1 denotes a filtered gene. */
        public int[] meanBinCodes(){return bins.clone();}
        public boolean[] filteredUnexpressed(){return filtered.clone();}
        public boolean[] sourceMissingZeroVariance(){return sourceMissing.clone();}
        public boolean[] diagnosticSelected(){return selected.clone();}
        public boolean usable(){return usable;}
    }

    public static Result select(Matrix matrix,Storage storage,Profile profile,Options options,Batches grouping,
                                Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(matrix);Objects.requireNonNull(storage);Objects.requireNonNull(profile);
        Objects.requireNonNull(options);Objects.requireNonNull(limits);
        Poll poll=new Poll(limits.maximumWork,cancelled);poll.check();
        int nf=matrix.features().size(),nc=matrix.cells().size(),nnz=matrix.storedValues(),nb=grouping==null?1:grouping.categories.size();
        if(nf<1||nc<1||nf>limits.features||nc>limits.cells||nnz>limits.storedValues||nb>limits.batches)
            throw new IllegalArgumentException("Complete HVG input exceeds declared dimensions/storage/batches; no truncation");
        long geneBatches=(long)nf*nb;
        long binBatches=(options.flavor==Flavor.SEURAT?options.bins+1L:21L)*nb;
        if(geneBatches>Integer.MAX_VALUE-8||geneBatches>limits.workingBytes/160
                ||binBatches>limits.workingBytes/96
                ||32L*nnz+160L*geneBatches+64L*nc+96L*binBatches>limits.workingBytes)
            throw new IllegalArgumentException("Complete HVG workspace exceeds workingBytes/Java capacity; no partial gene population");
        long scanWork;
        try{scanWork=Math.multiplyExact(24L*nnz+4L*nc,nb);}
        catch(ArithmeticException error){throw new IllegalArgumentException("Complete HVG work estimate exceeds long capacity",error);}
        if(scanWork>limits.maximumWork||64*geneBatches+64*binBatches>limits.maximumWork-scanWork)
            throw new IllegalArgumentException("Complete HVG estimate exceeds maximumWork; no partial analysis");
        if(profile==Profile.SCANPY_1_9_3&&options.filterUnexpressed!=null)
            throw new IllegalArgumentException("filter_unexpressed_genes is not an original Scanpy 1.9.3 parameter");
        if(grouping!=null&&grouping.labels.size()!=nc)throw new IllegalArgumentException("Require exactly one explicit batch label per cell");
        if(storage==Storage.DENSE&&(long)nf*nc!=nnz)throw new IllegalArgumentException("DENSE requires all cell/feature entries, including zeros");
        int[] pointers=matrix.columnPointers(),rows=matrix.rowIndices(),cellBatch=new int[nc];double[] values=matrix.values();
        if(grouping!=null){Map<String,Integer> codes=new HashMap<>();for(int b=0;b<nb;b++){poll.step();codes.put(grouping.categories.get(b),b);}for(int c=0;c<nc;c++){poll.step();cellBatch[c]=codes.get(grouping.labels.get(c));}}
        if(storage==Storage.DENSE)for(int c=0;c<nc;c++){
            poll.step();if(pointers[c]!=(long)c*nf)throw new IllegalArgumentException("Dense pointer declaration differs");
            for(int k=pointers[c];k<pointers[c+1];k++){poll.step();if(rows[k]!=k-pointers[c])throw new IllegalArgumentException("Dense feature order differs");}
        }
        List<Warning> warnings=new ArrayList<>();List<BatchResult> batches=new ArrayList<>();
        if(options.topGenes!=null&&(options.minMean!=.0125||options.maxMean!=3||options.minDispersion!=.5||options.maxDispersion!=Double.POSITIVE_INFINITY))
            warnings.add(warning("SOURCE_CUTOFFS_IGNORED",1,"n_top_genes takes precedence over all four expression/dispersion cutoffs."));
        if(profile==Profile.SCANPY_1_9_3&&options.flavor==Flavor.SEURAT&&options.logBase!=null)
            warnings.add(warning("SOURCE_LEGACY_LOG_BASE_SIDE_EFFECT",1,"Original unbatched 1.9.3 can multiply its supplied array by log(base) in place. This Java API keeps its explicitly immutable input; its statistics retain the original multiplication/inversion order."));
        if(grouping!=null&&Boolean.FALSE.equals(options.filterUnexpressed))
            warnings.add(warning("SOURCE_BATCH_FILTER_FORCED",nb,"Original current batch-aware HVGs filter unexpressed genes even when filter_unexpressed_genes=False."));
        boolean usable=true;
        for(int b=0;b<nb;b++){
            BatchResult result=single(matrix,storage,profile,options,pointers,rows,values,cellBatch,b,
                grouping==null?null:grouping.categories.get(b),grouping!=null,poll,warnings);
            batches.add(result);usable&=result.usable;
        }
        double[] means,disp,norm;boolean[] selected,intersection;int[] counts;
        if(grouping==null){BatchResult r=batches.get(0);means=r.means.clone();disp=r.dispersions.clone();norm=r.normalized.clone();selected=r.selected.clone();counts=new int[0];intersection=new boolean[0];}
        else {
            means=new double[nf];disp=new double[nf];norm=new double[nf];selected=new boolean[nf];counts=new int[nf];intersection=new boolean[nf];
            for(int g=0;g<nf;g++){
                poll.step();double[] m=new double[nb],d=new double[nb],n=new double[nb];
                for(int b=0;b<nb;b++){poll.step();BatchResult r=batches.get(b);m[b]=r.means[g];d[b]=r.dispersions[g];n[b]=r.normalized[g];if(r.selected[g])counts[g]++;}
                means[g]=groupMean(m,profile);disp[g]=groupMean(d,profile);norm[g]=groupMean(n,profile);intersection[g]=counts[g]==nb;
            }
            if(options.topGenes!=null){
                Integer[] order=new Integer[nf];for(int g=0;g<nf;g++)order[g]=g;
                Arrays.sort(order,(a,b)->{
                    poll.step();int result=Integer.compare(counts[b],counts[a]);
                    if(result==0)result=descendingMissingLast(norm[a],norm[b]);
                    if(result==0)result=profile==Profile.SCANPY_1_9_3?codePointCompare(matrix.features().get(a),matrix.features().get(b)):Integer.compare(a,b);
                    return result;
                });
                for(int i=0;i<Math.min(nf,options.topGenes);i++)selected[order[i]]=true;
            } else for(int g=0;g<nf;g++){poll.step();if(Double.isNaN(norm[g]))norm[g]=0;selected[g]=inBounds(means[g],norm[g],options);}
        }
        if(options.export==Export.ANNDATA)for(int g=0;g<nf;g++){
            poll.step();double old=norm[g];norm[g]=(float)old;
            if(Double.isFinite(old)&&!Double.isFinite(norm[g]))usable=false;
        }
        if(!usable)warnings.add(warning("UNDEFINED_HVG_ARITHMETIC",1,"Source diagnostics contain invalid model arithmetic, lost positive terms or float32 range failure; no complete selection is admitted even if aggregation/selection looks finite."));
        poll.check();return new Result(matrix,profile,options,means,disp,norm,selected,counts,intersection,batches,warnings,usable);
    }

    private static BatchResult single(Matrix matrix,Storage storage,Profile profile,Options options,int[] pointers,int[] rows,
                                      double[] data,int[] cellBatch,int batch,String category,boolean grouped,Poll poll,List<Warning> warnings) {
        int nf=matrix.features().size(),nc=cellBatch.length,n=0;boolean[] filtered=new boolean[nf];
        boolean filter=grouped||Boolean.TRUE.equals(options.filterUnexpressed);int[] expressed=new int[nf];
        for(int c=0;c<nc;c++){
            poll.step();if(cellBatch[c]!=batch)continue;
            n++;for(int k=pointers[c];k<pointers[c+1];k++){poll.step();if(data[k]>0)expressed[rows[k]]++;}
        }
        if(n==0)throw new ArithmeticException("Original HVG cannot cut/estimate an empty batch; retain explicit unused categories");
        if(n==1&&profile==Profile.SCANPY_1_9_3)throw new ArithmeticException("Original Scanpy 1.9.3 mean/variance raises for one cell");
        int active=0;for(int g=0;g<nf;g++){filtered[g]=filter&&expressed[g]==0;if(!filtered[g])active++;}
        if(active==0)throw new ArithmeticException("Original HVG cannot cut an empty expressed-gene population");
        double[] means=new double[nf],var=new double[nf],dispersions=new double[nf],normalized=new double[nf];
        boolean[] sourceMissing=new boolean[nf];int[] stored=new int[nf];boolean usable=true;long lost=0,missing=0;
        double logBase=options.logBase==null?1:Math.log(options.logBase);
        for(int c=0;c<nc;c++){
            poll.step();if(cellBatch[c]!=batch)continue;
            for(int k=pointers[c];k<pointers[c+1];k++){
                poll.step();int g=rows[k];if(filtered[g])continue;double v=transform(data[k],options.flavor,logBase);
                means[g]+=v;stored[g]++;if(!Double.isFinite(v)||v<0)usable=false;
                if(profile!=Profile.SCANPY_1_9_3||storage!=Storage.CSR){double square=v*v;var[g]+=square;if(v!=0&&square==0)lost++;}
            }
        }
        for(int g=0;g<nf;g++){poll.step();if(!filtered[g])means[g]/=n;}
        if(profile==Profile.SCANPY_1_9_3&&storage==Storage.CSR){
            for(int c=0;c<nc;c++){
                poll.step();if(cellBatch[c]!=batch)continue;
                for(int k=pointers[c];k<pointers[c+1];k++){
                    poll.step();int g=rows[k];if(filtered[g])continue;
                    double difference=transform(data[k],options.flavor,logBase)-means[g],square=difference*difference;
                    var[g]+=square;if(difference!=0&&square==0)lost++;
                }
            }
            for(int g=0;g<nf;g++){poll.step();if(!filtered[g])var[g]=(var[g]+(n-stored[g])*(means[g]*means[g]))/n;}
        }else for(int g=0;g<nf;g++){poll.step();if(!filtered[g])var[g]=var[g]/n-means[g]*means[g];}
        double correction=n==1?1:(double)n/(n-1);
        for(int g=0;g<nf;g++)if(!filtered[g]){
            poll.step();var[g]*=correction;
            if(!Double.isFinite(means[g])||!Double.isFinite(var[g])||var[g]<0)usable=false;
            if(means[g]==0)means[g]=1e-12;
            dispersions[g]=var[g]/means[g];
            if(var[g]>0&&dispersions[g]==0){usable=false;lost++;}
            if(options.flavor==Flavor.SEURAT){
                if(dispersions[g]==0&&Double.isFinite(var[g])){
                    dispersions[g]=Double.NaN;
                    if(var[g]==0){sourceMissing[g]=true;missing++;}
                }
                dispersions[g]=Math.log(dispersions[g]);means[g]=Math.log1p(means[g]);
            }
            if(!Double.isFinite(means[g])||(!Double.isFinite(dispersions[g])&&!sourceMissing[g]))usable=false;
        }
        if(lost>0){usable=false;warnings.add(warning("LOST_POSITIVE_MOMENT_TERM",lost,"Positive source second-moment or dispersion terms underflowed to zero; no variance floor is substituted."));}
        double[] activeMeans=new double[active];for(int g=0,j=0;g<nf;g++)if(!filtered[g])activeMeans[j++]=means[g];
        double[] edges=edges(activeMeans,options,poll);int[] bins=new int[nf];Arrays.fill(bins,-1);
        int binCount=edges.length-1;int[] sizes=new int[binCount];
        for(int g=0;g<nf;g++)if(!filtered[g]){poll.step();int bin=bin(means[g],edges);if(bin<0)throw new ArithmeticException("Original mean bin is missing/outside supplied source edges");bins[g]=bin;sizes[bin]++;}
        double[][] groups=new double[binCount][];for(int b=0;b<binCount;b++)groups[b]=new double[sizes[b]];
        Arrays.fill(sizes,0);for(int g=0;g<nf;g++)if(!filtered[g])groups[bins[g]][sizes[bins[g]]++]=dispersions[g];
        double[] centers=new double[binCount],deviations=new double[binCount];
        for(int b=0;b<binCount;b++){
            poll.step();if(options.flavor==Flavor.SEURAT){
                centers[b]=groupMean(groups[b],profile);deviations[b]=groupStandardDeviation(groups[b]);
                if(Double.isNaN(deviations[b])){deviations[b]=centers[b];centers[b]=0;}
            }else {
                centers[b]=medianIgnoringNaN(groups[b],poll);
                double center=median(groups[b].clone(),poll);double[] errors=new double[groups[b].length];
                for(int j=0;j<errors.length;j++)errors[j]=Math.abs(groups[b][j]-center)/.6744897501960817;
                deviations[b]=median(errors,poll);
            }
        }
        for(int g=0;g<nf;g++)if(!filtered[g]){
            poll.step();normalized[g]=(dispersions[g]-centers[bins[g]])/deviations[bins[g]];
            if(!Double.isFinite(normalized[g])&&!sourceMissing[g])usable=false;
        }
        boolean[] selected=new boolean[nf];
        if(options.topGenes!=null){
            double[] valid=new double[active];int count=0;
            for(int g=0;g<nf;g++)if(!filtered[g]&&!Double.isNaN(normalized[g]))valid[count++]=normalized[g];
            if(count==0)throw new ArithmeticException("Original n_top_genes has no defined normalized dispersions; source index is empty");
            valid=Arrays.copyOf(valid,count);sort(valid,poll);int top=Math.min(options.topGenes,Math.min(active,count));double cutoff=valid[count-top];
            if(options.topGenes>active)warnings.add(warning("SOURCE_TOP_GENES_CLAMPED_TO_VARIABLES",1,"n_top_genes exceeds the active gene count and is clamped by the source."));
            if(Math.min(options.topGenes,active)>count)warnings.add(warning("SOURCE_TOP_GENES_CLAMPED_TO_DEFINED",1,"n_top_genes exceeds the defined normalized-dispersion count and is clamped by the source."));
            for(int g=0;g<nf;g++)if(!filtered[g]){
                double v=profile==Profile.SCANPY_1_9_3?finiteSelection(normalized[g],0):
                    Double.isNaN(normalized[g])?Double.NEGATIVE_INFINITY:normalized[g];
                selected[g]=v>=cutoff;
            }
        }else for(int g=0;g<nf;g++)if(!filtered[g]){
            poll.step();double v=normalized[g];
            if(profile==Profile.SCANPY_1_9_3){if(Double.isNaN(v))normalized[g]=v=0;}
            else v=finiteSelection(v,0);
            selected[g]=inBounds(means[g],v,options);
        }
        if(missing>0)warnings.add(warning("SOURCE_ZERO_VARIANCE_MISSING_DISPERSION",missing,
            "Seurat explicitly marks zero-variance dispersion as missing. Diagnostic NaNs are retained. Original 1.9.3 maps normalized NaN to zero for selection and can select such genes with a nonpositive top-gene cutoff; current top-gene selection maps NaN to negative infinity. These literal source conventions are not missing-value repair."));
        return new BatchResult(category,n,means,dispersions,normalized,edges,selected,filtered,sourceMissing,bins,usable);
    }

    private static double transform(double value,Flavor flavor,double logBase){return flavor==Flavor.SEURAT?Math.expm1(value*logBase):value;}
    private static boolean inBounds(double mean,double norm,Options o){return mean>o.minMean&&mean<o.maxMean&&norm>o.minDispersion&&norm<o.maxDispersion;}
    private static double finiteSelection(double value,double missing){return Double.isNaN(value)?missing:value==Double.POSITIVE_INFINITY?Double.MAX_VALUE:value==Double.NEGATIVE_INFINITY?-Double.MAX_VALUE:value;}
    private static int descendingMissingLast(double a,double b){if(Double.isNaN(a))return Double.isNaN(b)?0:1;if(Double.isNaN(b))return -1;return a==b?0:Double.compare(b,a);}
    private static int codePointCompare(String a,String b){
        int i=0,j=0;while(i<a.length()&&j<b.length()){int ac=a.codePointAt(i),bc=b.codePointAt(j);if(ac!=bc)return Integer.compare(ac,bc);i+=Character.charCount(ac);j+=Character.charCount(bc);}return Integer.compare(a.length()-i,b.length()-j);
    }
    private static double groupMean(double[] values,Profile profile){
        double sum=0,compensation=0;int count=0;
        for(double value:values)if(!Double.isNaN(value)){
            count++;double y=value-compensation,next=sum+y;compensation=(next-sum)-y;
            if(profile==Profile.SCANPY_1_13_0&&Double.isNaN(compensation))compensation=0;sum=next;
        }
        return count==0?Double.NaN:sum/count;
    }
    private static double groupStandardDeviation(double[] values){
        double mean=0,squares=0;int count=0;
        for(double value:values)if(!Double.isNaN(value)){count++;double old=mean;mean+=(value-old)/count;squares+=(value-mean)*(value-old);}
        return count<=1?Double.NaN:Math.sqrt(squares/(count-1));
    }
    private static double medianIgnoringNaN(double[] values,Poll poll){
        double[] finite=new double[values.length];int count=0;for(double value:values){poll.step();if(!Double.isNaN(value))finite[count++]=value;}
        return median(Arrays.copyOf(finite,count),poll);
    }
    private static double median(double[] values,Poll poll){
        if(values.length==0)return Double.NaN;for(double value:values){poll.step();if(Double.isNaN(value))return Double.NaN;}
        sort(values,poll);int middle=values.length/2;return (values.length&1)==1?values[middle]:(values[middle-1]+values[middle])/2;
    }
    private static double[] edges(double[] means,Options options,Poll poll){
        double[] result;
        if(options.flavor==Flavor.CELL_RANGER){
            double[] ordered=means.clone();sort(ordered,poll);result=new double[21];result[0]=Double.NEGATIVE_INFINITY;result[20]=Double.POSITIVE_INFINITY;
            for(int p=10,j=1;p<=100;p+=5,j++){
                double index=(ordered.length-1)*(p/100.0);int low=(int)Math.floor(index),high=Math.min(low+1,ordered.length-1);double fraction=index-low,difference=ordered[high]-ordered[low];
                result[j]=fraction>=.5?ordered[high]-difference*(1-fraction):ordered[low]+difference*fraction;
            }
        }else {
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(double value:means){poll.step();if(!Double.isFinite(value))throw new ArithmeticException("Original equal-width HVG bins require finite means");low=Math.min(low,value);high=Math.max(high,value);}
            boolean same=low==high;
            if(same){low-=low!=0?.001*Math.abs(low):.001;high+=high!=0?.001*Math.abs(high):.001;}
            result=new double[options.bins+1];double step=(high-low)/options.bins;
            for(int i=0;i<result.length;i++){poll.step();result[i]=low+i*step;}result[options.bins]=high;
            if(!same)result[0]-=(high-low)*.001;
        }
        for(int i=1;i<result.length;i++){poll.step();if(Double.isNaN(result[i])||!(result[i]>result[i-1]))throw new ArithmeticException("Original HVG bin edges are nonfinite/nonunique; no deduplication or changed bin count");}
        return result;
    }
    private static int bin(double value,double[] edges){
        int low=0,high=edges.length;
        while(low<high){int middle=(low+high)>>>1;if(edges[middle]<value)low=middle+1;else high=middle;}
        return low==0||low==edges.length?-1:low-1;
    }
    private static void sort(double[] values,Poll poll){
        // Numeric heapsort has bounded workspace and polls throughout long sorts.
        for(int start=values.length/2-1;start>=0;start--)sift(values,start,values.length,poll);
        for(int end=values.length-1;end>0;end--){double value=values[end];values[end]=values[0];values[0]=value;sift(values,0,end,poll);}
        poll.check();
    }
    private static void sift(double[] values,int root,int end,Poll poll){
        while(root<end/2){
            poll.step();int child=root*2+1;
            if(child+1<end&&Double.compare(values[child],values[child+1])<0)child++;
            if(Double.compare(values[root],values[child])>=0)return;
            double value=values[root];values[root]=values[child];values[child]=value;root=child;
        }
    }
    private static Warning warning(String code,long affected,String message){return new Warning(code,affected,message,ACTION);}
    private static final class Poll {
        private final long maximum;private final BooleanSupplier cancelled;private long work;
        Poll(long maximum,BooleanSupplier cancelled){this.maximum=maximum;this.cancelled=Objects.requireNonNull(cancelled);}
        void step(){add(1);}
        void add(long amount){if(amount>maximum-work)throw new IllegalArgumentException("Complete HVG maximumWork exceeded; no partial selection");work+=amount;if((work&1023)==0)check();}
        void check(){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("HVG analysis cancelled; no complete selection");}
    }
}

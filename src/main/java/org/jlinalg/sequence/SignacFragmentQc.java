/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Source-compatible NucleosomeSignal ratios/ECDF on explicit fragment mappings, without a Seurat object. */
public final class SignacFragmentQc {
    private SignacFragmentQc() { }
    public enum NumberKind { FINITE, POSITIVE_INFINITY, NAN, NA }
    public record Value(NumberKind kind,double value) {
        public Value {Objects.requireNonNull(kind);if((kind==NumberKind.FINITE&&!Double.isFinite(value))||(kind==NumberKind.POSITIVE_INFINITY&&value!=Double.POSITIVE_INFINITY)||((kind==NumberKind.NA||kind==NumberKind.NAN)&&!Double.isNaN(value)))throw new IllegalArgumentException("QC number kind/value disagreement");}
        static Value of(double value){return Double.isNaN(value)?new Value(NumberKind.NAN,value):Double.isInfinite(value)?new Value(NumberKind.POSITIVE_INFINITY,value):new Value(NumberKind.FINITE,value);}
        static Value na(){return new Value(NumberKind.NA,Double.NaN);}
    }
    public record CellScore(String cell,Value ratio,Value percentile) { }
    public record Result(List<CellScore> scores,List<SignacFragments.Scan> scans,List<SignacFragments.Warning> warnings,long maxLinesPerFile) {
        public Result{scores=List.copyOf(scores);scans=List.copyOf(scans);warnings=List.copyOf(warnings);}
    }
    /** Zero maxLines means all; the original default is object-cell count times5000. */
    public static Result nucleosomeSignal(List<SignacFragments.Source> sources,List<String> objectCells,long maxLines,
                                          SignacFragments.Limits limits,BooleanSupplier cancelled)throws IOException {
        SignacFragments.check(cancelled);if(sources.isEmpty()||objectCells.isEmpty()||objectCells.size()>limits.cells()||maxLines<0)throw new IllegalArgumentException("Require sources, a bounded object cell universe and nonnegative line limit");
        Set<String> universe=new LinkedHashSet<>();for(String cell:objectCells){SignacFragments.identity(cell);if(!universe.add(cell))throw new IllegalArgumentException("Object cells must be unique");}
        Map<String,Double> ratios=new HashMap<>();Set<String> attached=new HashSet<>();List<SignacFragments.Scan> scans=new ArrayList<>();List<SignacFragments.Warning> warnings=new ArrayList<>();
        for(var source:sources){
            List<SignacFragments.Cell> mapping=source.map();
            LinkedHashMap<String,String> converter=new LinkedHashMap<>();
            if(mapping!=null)for(var cell:mapping)if(universe.contains(cell.cell()))converter.put(cell.barcode(),cell.cell());
            var result=SignacFragments.countFragments(List.of(source.path()),mapping==null?null:new ArrayList<>(converter.keySet()),maxLines,limits,cancelled);scans.addAll(result.scans());warnings.addAll(result.warnings());
            for(var count:result.counts()){
                String cell=mapping==null?count.barcode():converter.get(count.barcode());if(!universe.contains(cell))continue;
                if(!attached.add(cell))throw new IOException("Repeated object cells across Fragment QC rows are not pooled: use disjoint source mappings or analyze each source separately");
                ratios.put(cell,(double)count.mononucleosomal()/count.nucleosomeFree());
            }
        }
        double[] distribution=ratios.values().stream().filter(v->!Double.isNaN(v)).mapToDouble(Double::doubleValue).sorted().toArray();
        if(distribution.length==0)throw new IOException("NucleosomeSignal ECDF has no nonmissing ratios, matching the original ecdf failure; inspect fragment lengths and the sampling prefix");
        List<CellScore> scores=new ArrayList<>();boolean nonfinite=false,missing=false;
        for(String cell:objectCells){
            SignacFragments.check(cancelled);Double ratio=ratios.get(cell);Value value=ratio==null?Value.na():Value.of(ratio),percentile=ratio!=null&&Double.isNaN(ratio)?Value.of(Double.NaN):Value.na();
            if(ratio!=null&&!Double.isNaN(ratio)){
                int low=0,high=distribution.length;while(low<high){int mid=(low+high)>>>1;if(distribution[mid]<=ratio)low=mid+1;else high=mid;}
                percentile=Value.of(roundPercentile((double)low/distribution.length));
            }
            nonfinite|=value.kind()==NumberKind.NAN||value.kind()==NumberKind.POSITIVE_INFINITY;missing|=value.kind()==NumberKind.NA;scores.add(new CellScore(cell,value,percentile));
        }
        if(nonfinite)warnings.add(new SignacFragments.Warning("NONFINITE_NUCLEOSOME_SIGNAL","Zero nucleosome-free denominators produced source NaN or infinity; values were retained","Inspect per-cell fragment lengths and sampling. Do not replace undefined values with zero or automatically interpret infinity as acceptable QC."));
        if(missing)warnings.add(new SignacFragments.Warning("UNOBSERVED_OR_UNMAPPED_OBJECT_CELLS","Some object cells have no QC row after mapping and sampled barcode discovery, and receive NA metadata","Review cell mappings and the sampling prefix; no counts or ratios were imputed."));
        return new Result(scores,scans,warnings,maxLines);
    }
    /** R's two-digit nearest-candidate rounding for an ECDF in [0,1], including binary ties. */
    public static double roundPercentile(double x){
        if(!Double.isFinite(x)||x<0||x>1)throw new IllegalArgumentException("Require an ECDF percentile in [0,1]");
        if(x==0)return x;double x100=x*100,i=Math.floor(x100),down=i/100,up=Math.ceil(x100)/100,du=up-x,dd=x-down;
        return du<dd||((i%2)==1&&du==dd)?up:down;
    }
    public record Binarized(SignacNormalization.Counts counts,int storedZerosChanged,List<SignacFragments.Warning> warnings){public Binarized{warnings=List.copyOf(warnings);}}
    /** Sparse BinarizeCounts semantics: every stored slot becomes1, even an explicitly stored zero. */
    public static Binarized binarizeCsc(SignacNormalization.Counts input,BooleanSupplier cancelled){
        SignacFragments.check(cancelled);double[] values=input.values();int zeros=0;
        for(int k=0;k<values.length;k++){if((k&4095)==0)SignacFragments.check(cancelled);if(values[k]==0)zeros++;values[k]=1;}
        var result=new SignacNormalization.Counts(input.features(),input.cells(),input.columnPointers(),input.rowIndices(),values,
                new SignacNormalization.Limits(input.features().size(),input.cells().size(),values.length),cancelled);
        List<SignacFragments.Warning> warnings=zeros==0?List.of():List.of(new SignacFragments.Warning("STORED_ZERO_BINARIZED_TO_ONE",zeros+" explicitly stored sparse zeros became1, matching BinarizeCounts.default","Review sparse storage. If a positive-count indicator is intended, explicitly canonicalize/remove stored zeros and record that input transformation before binarization."));
        return new Binarized(result,zeros,warnings);
    }
}

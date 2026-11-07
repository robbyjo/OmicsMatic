/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native original Signac coverage endpoint, normalization, rolling-sum and sampling profile. */
public final class SignacCoverage {
    private SignacCoverage() { }
    public record Cell(String cell,String group,double assayCount) {
        public Cell {SignacFragments.identity(cell);SignacFragments.identity(group);if(!Double.isFinite(assayCount)||assayCount<0)throw new IllegalArgumentException("Require a finite nonnegative supplied assay-wide nCount per object cell");}
    }
    public record Options(Integer window,double downsampleRate,int maximumDownsample,Object ymax,long signalValues,long smoothingOperations) {
        public Options {if(window!=null&&(window<1||window>2_000_000)||!Double.isFinite(downsampleRate)||downsampleRate<0||downsampleRate>1||maximumDownsample<0||maximumDownsample>2_000_000||signalValues<1||signalValues>100_000_000)throw new IllegalArgumentException("Invalid explicit coverage capacities/window/sampling");
            if(smoothingOperations<1||smoothingOperations>1_000_000_000_000L)throw new IllegalArgumentException("Require positive explicit smoothing operation capacity");
            if(ymax!=null&&!(ymax instanceof Number)&&!(ymax instanceof String))throw new IllegalArgumentException("ymax must be null, finite nonnegative numeric, or qXX");
            if(ymax instanceof Number n&&(!Double.isFinite(n.doubleValue())||n.doubleValue()<0))throw new IllegalArgumentException("Require nonnegative finite numeric ymax");
            if(ymax instanceof String s){if(!s.matches("q(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))throw new IllegalArgumentException("Require qXX source clipping fraction");double p=Double.parseDouble(s.substring(1));if(p<0||p>100)throw new IllegalArgumentException("Require q fraction in0..100");}}
        public static Options defaults(){return new Options(100,0.1,3000,null,20_000_000,250_000_000);}
    }
    public record Scaling(List<String> names,List<Double> factors,List<String> averageNames,List<Double> averages,List<Long> cellCounts,double scaleFactor,List<String> warnings) {
        public Scaling {names=List.copyOf(names);factors=List.copyOf(factors);averageNames=List.copyOf(averageNames);averages=List.copyOf(averages);cellCounts=List.copyOf(cellCounts);warnings=List.copyOf(warnings);}
        public Map<String,Double> byGroup(){Map<String,Double> m=new LinkedHashMap<>();for(int i=0;i<names.size()-1;i++)m.put(names.get(i),factors.get(i));return Collections.unmodifiableMap(m);}
    }
    public record Point(String group,int position,long count,double normalized,double coverage) { }
    public record Result(List<String> groups,List<Point> points,double ymax,List<String> warnings) {public Result{groups=List.copyOf(groups);points=List.copyOf(points);warnings=List.copyOf(warnings);}}
    public record Cuts(List<String> groups,long[][] counts,List<SignacFragments.Scan> scans,List<String> warnings) {
        public Cuts {groups=List.copyOf(groups);counts=copy(counts);scans=List.copyOf(scans);warnings=List.copyOf(warnings);}
        @Override public long[][] counts(){return copy(counts);}
    }
    /** Explicit levels preserve source factor recycling, including unused levels and the extra NA table slot. */
    public static Scaling scaling(List<Cell> cells,List<String> levels,Double scaleFactor) {
        validateCells(cells);List<String> names=levels==null?cells.stream().map(Cell::group).distinct().sorted(C_ORDER).toList():List.copyOf(levels);
        if(names.isEmpty()||new HashSet<>(names).size()!=names.size()||cells.stream().anyMatch(c->!names.contains(c.group)))throw new IllegalArgumentException("Require explicit unique levels covering every object group");
        for(String n:names)SignacFragments.identity(n);List<Long> sizes=new ArrayList<>();List<Double> means=new ArrayList<>();List<String> averageNames=new ArrayList<>();
        for(String group:names){double[] counts=cells.stream().filter(c->c.group.equals(group)).mapToDouble(Cell::assayCount).toArray();sizes.add((long)counts.length);if(counts.length>0){means.add(mean(counts));averageNames.add(group);}}
        sizes.add(0L);List<Double> factors=new ArrayList<>();for(int i=0;i<sizes.size();i++)factors.add(means.get(i%means.size())*sizes.get(i));
        List<String> factorNames=new ArrayList<>(names);factorNames.add("<SOURCE_NA_LEVEL>");double sf=scaleFactor==null?median(factors):scaleFactor;
        if(!Double.isFinite(sf)||sf<0)throw new IllegalArgumentException("Require finite nonnegative supplied/default scale factor");
        List<String> warnings=new ArrayList<>(List.of("SOURCE_SCALE_NA_SLOT: original CellsPerGroup uses table(useNA='always'); its zero NA slot remains in the recycled group-scale vector and default median. Assay counts are supplied for the complete object population, not estimated from this region or raw fragment support."));
        if(names.size()!=means.size())warnings.add("SOURCE_UNUSED_FACTOR_RECYCLING: unused group levels are preserved; original positional mean/count vector multiplication recycles values. Do not silently replace it by a named group sum.");
        return new Scaling(factorNames,factors,averageNames,means,sizes,sf,warnings);
    }
    /** Count source endpoints directly into group rows; support values do not weight fragments. */
    public static Cuts cuts(List<SignacFragments.Source> sources,List<Cell> cells,SignacFragments.Region region,long maximumValues,SignacFragments.Limits limits,BooleanSupplier cancel)throws IOException {
        validateCells(cells);if(sources.isEmpty())throw new IllegalArgumentException("Require declared fragment sources");int width=region.end()-region.start()+1;
        List<String> groups=cells.stream().map(Cell::group).distinct().toList();if(width>2_000_000||maximumValues<1||(long)width*groups.size()>maximumValues)throw new IOException("Complete coverage signal exceeds explicit capacity");
        Map<String,Integer> cellGroups=new HashMap<>();for(Cell c:cells)cellGroups.put(c.cell,groups.indexOf(c.group));
        long[][] counts=new long[groups.size()][width];List<SignacFragments.Scan> scans=new ArrayList<>();List<String> warnings=new ArrayList<>();boolean active=true,any=false;
        for(var source:sources){SignacFragments.check(cancel);var survey=SignacFragments.scan(source.path(),limits,true,0,cancel,f->{});scans.add(survey);
            if(!survey.chromosomes().contains(region.chromosome())){active=false;warnings.add("SOURCE_PERSISTENT_CHROMOSOME_PRUNING: a fragment source lacks the requested chromosome; the original CutMatrix region is empty for this and every subsequent source. File order is retained.");}
            if(!active)continue;any=true;Map<String,Integer> mapping=new HashMap<>();if(source.map()==null)mapping.putAll(cellGroups);else for(var c:source.map())if(cellGroups.containsKey(c.cell()))mapping.put(c.barcode(),cellGroups.get(c.cell()));
            long[] hits={0};var counted=SignacFragments.scan(source.path(),limits,true,0,cancel,f->{Integer group=mapping.get(f.barcode());if(group==null||!f.chromosome().equals(region.chromosome())||f.start()>=region.end()||f.end()<region.start())return;
                for(int endpoint:new int[]{f.start(),f.end()}){long position=(long)endpoint-region.start()-1;if(position>0&&position<=width){if(++hits[0]>limits.contributions())throw new IOException("Coverage endpoint contributions exceed explicit capacity");int j=(int)position-1;counts[group][j]=SignacFragments.exact(counts[group][j],1);}}});
            if(!counted.equals(survey))throw new IOException("Coverage fragment population changed between complete scans");
        }
        if(!any)throw new IOException("Original CutMatrix has no retained region/matrix after source chromosome pruning");
        warnings.add("SOURCE_ENDPOINT_OFFSET: SingleFileCutMatrix uses raw BED endpoints minus (region.start+1), retains columns1..width and labels them region.start..end. Preserve this source offset; no canonical Tn5 shift, full-fragment pileup or coordinate repair is substituted.");
        return new Cuts(groups,counts,scans,warnings);
    }
    /** Source CoverageTrack on complete group sums; sampled order is the exact R4.6.1 fixed-seed profile. */
    public static Result track(List<String> groups,long[][] counts,int start,Map<String,Double> factors,double scaleFactor,Options options,BooleanSupplier cancel) {
        if(groups.isEmpty()||new HashSet<>(groups).size()!=groups.size()||counts.length!=groups.size()||start<1||!Double.isFinite(scaleFactor)||scaleFactor<0)throw new IllegalArgumentException("Require unique groups and nonnegative declared normalization");
        int width=counts[0].length;if(width<1||width>2_000_000||(long)width*groups.size()>options.signalValues||(long)start+width-1>536_870_912)throw new IllegalArgumentException("Coverage complete signal/coordinate capacity exceeded");
        if(options.window!=null&&(long)Math.max(0,width-options.window+1)*options.window*groups.size()>options.smoothingOperations)throw new IllegalArgumentException("Complete source rolling-sum operations exceed the declared capacity");
        int size=(int)Math.min(options.maximumDownsample,width*options.downsampleRate);var random=new SignacCoverageSampling(1234);List<Point> points=new ArrayList<>();List<String> warnings=new ArrayList<>();
        for(String group:groups.stream().sorted(C_ORDER).toList()) {
            SignacFragments.check(cancel);SignacFragments.identity(group);int g=groups.indexOf(group);if(counts[g].length!=width||!factors.containsKey(group)||!Double.isFinite(factors.get(group))||factors.get(group)<0)throw new IllegalArgumentException("Require a complete nonnegative finite source group-scale binding");
            double[] normalized=new double[width];for(int i=0;i<width;i++){if(counts[g][i]<0||counts[g][i]>SignacFragments.MAX_EXACT)throw new IllegalArgumentException("Cut counts require exact nonnegative integers");normalized[i]=counts[g][i]/factors.get(group)*scaleFactor;}
            double[] rolled=roll(normalized,options.window,cancel);List<Integer> eligible=new ArrayList<>();for(int i=0;i<width;i++)if(!Double.isNaN(rolled[i]))eligible.add(i);
            int[] sample=random.sample(eligible.size(),Math.min(size,eligible.size()));for(int index:sample){int i=eligible.get(index);points.add(new Point(group,start+i,counts[g][i],normalized[i],rolled[i]));}
        }
        double max=Double.NEGATIVE_INFINITY;for(Point p:points)max=Math.max(max,p.coverage);double rounded=significant(max,2),ymax=options.ymax==null?rounded:options.ymax instanceof Number n?n.doubleValue():rounded*Double.parseDouble(options.ymax.toString().substring(1))/100;
        List<Point> clipped=new ArrayList<>();for(Point p:points)clipped.add(new Point(p.group,p.position,p.count,p.normalized,p.coverage>ymax?ymax:p.coverage));
        if(points.isEmpty())warnings.add("SOURCE_EMPTY_COVERAGE: no sampled nonmissing positions remain; original max(empty) is -Inf. This is not a plotted negative-accessibility result.");
        if(!Double.isFinite(ymax)||points.stream().anyMatch(p->!Double.isFinite(p.coverage)||!Double.isFinite(p.normalized)))warnings.add("SOURCE_NONFINITE_COVERAGE: source normalization/smoothing retains nonfinite values. Do not repair zero denominators or infer usable plot limits.");
        if(options.ymax instanceof String)warnings.add("SOURCE_Q_CLIPPING: qXX multiplies the two-significant-digit sampled maximum by XX/100; it is not an empirical quantile.");
        warnings.add("SOURCE_FIXED_SAMPLING: original CoverageTrack resets R seed1234 per assay; sorted group draws use R4.6.1 Mersenne-Twister/Rejection without replacement. Full source signal remains distinct from the sampled plot.");
        return new Result(groups.stream().sorted(C_ORDER).toList(),clipped,ymax,warnings);
    }
    private static double[] roll(double[] values,Integer window,BooleanSupplier cancel){if(window==null)return values.clone();double[] out=new double[values.length];Arrays.fill(out,Double.NaN);int before=(window-1)/2;for(int from=0;from+window<=values.length;from++){if((from&255)==0)SignacFragments.check(cancel);double sum=0;for(int j=0;j<window;j++){if((j&65535)==0)SignacFragments.check(cancel);sum+=values[from+j];}out[from+before]=sum;}return out;}
    private static double significant(double x,int digits){if(x==0||!Double.isFinite(x))return x;return new BigDecimal(x).round(new MathContext(digits,RoundingMode.HALF_EVEN)).doubleValue();}
    private static double median(List<Double> values){double[] a=values.stream().mapToDouble(Double::doubleValue).sorted().toArray();return a.length%2==1?a[a.length/2]:(a[a.length/2-1]+a[a.length/2])/2;}
    private static double mean(double[] values){BigDecimal total=BigDecimal.ZERO;for(double x:values)total=total.add(new BigDecimal(x));return total.divide(BigDecimal.valueOf(values.length),new MathContext(40)).doubleValue();}
    private static void validateCells(List<Cell> cells){if(cells.isEmpty()||cells.size()>2_000_000||cells.stream().map(Cell::cell).distinct().count()!=cells.size())throw new IllegalArgumentException("Require a nonempty unique complete object-cell population");}
    private static long[][] copy(long[][] x){long[][] out=x.clone();for(int i=0;i<x.length;i++)out[i]=x[i].clone();return out;}
    static final Comparator<String> C_ORDER=(a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.jlinalg.genetics.GenomicIntervals;

/** Native legacy TSSEnrichment fast and position-profile contracts, including source boundary behavior. */
public final class SignacTssEnrichment {
    private SignacTssEnrichment() { }
    public record Position(String chromosome,int start,int end,String strand,Integer sequenceLength) {
        public Position {SignacFragments.identity(chromosome);SignacGeneActivity.strand(strand);if(start<1||end<start||end>536_870_912||sequenceLength!=null&&(sequenceLength<1||sequenceLength>536_870_912))throw new IllegalArgumentException("Require positive closed TSS positions and optional linear sequence lengths within the TBI domain");}
    }
    public record Options(boolean fast,int processN,int extension,Integer firstN,List<String> selectedCells,long profileValues) {
        public Options {selectedCells=selectedCells==null?null:List.copyOf(selectedCells);if(processN<1||extension<0||extension>1_000_000||firstN!=null&&firstN<1||profileValues<1||profileValues>100_000_000)throw new IllegalArgumentException("Require positive process_n/n, bounded nonnegative extension and bounded profile values");}
        public static Options defaults(){return new Options(true,2000,1000,null,null,20_000_000);}
    }
    public record Window(String chromosome,int start,int end,String strand) {public int width(){return end-start+1;}}
    public record Score(String cell,SignacFragmentQc.Value enrichment,SignacFragmentQc.Value percentile) { }
    public record Pass(int source,String phase,SignacFragments.Scan scan) { }
    public record Profile(List<String> cells,List<Integer> positions,long[][] raw,double[][] normalized) {
        public Profile {cells=List.copyOf(cells);positions=List.copyOf(positions);raw=copy(raw);normalized=copy(normalized);}
        @Override public long[][] raw(){return copy(raw);}@Override public double[][] normalized(){return copy(normalized);}
        private static long[][] copy(long[][] value){long[][] result=value.clone();for(int i=0;i<result.length;i++)result[i]=result[i].clone();return result;}
        private static double[][] copy(double[][] value){double[][] result=value.clone();for(int i=0;i<result.length;i++)result[i]=result[i].clone();return result;}
    }
    public record Result(List<Score> scores,List<Position> selectedPositions,Profile profile,List<Pass> passes,List<SignacFragments.Warning> warnings) {
        public Result {scores=List.copyOf(scores);selectedPositions=List.copyOf(selectedPositions);passes=List.copyOf(passes);warnings=List.copyOf(warnings);}
    }
    /** Source Extend casts fractional endpoints toward zero; unknown sequence lengths leave negative coordinates. */
    public static Window extend(Position row,int upstream,int downstream,boolean midpoint){
        boolean plus=!row.strand().equals("-");double middle=row.start()+((long)row.end()-row.start()+1)/2.0;
        double a=(midpoint?middle:row.start())-(plus?upstream:downstream),b=(midpoint?middle:row.end())+(plus?downstream:upstream);
        if(a<Integer.MIN_VALUE||b>Integer.MAX_VALUE||a>Integer.MAX_VALUE||b<Integer.MIN_VALUE)throw new IllegalArgumentException("Extended source coordinates exceed integer admission bounds");int start=(int)a,end=(int)b;
        if(end<start-1L)throw new IllegalArgumentException("Extended range has negative width");
        if(row.sequenceLength()!=null){int length=row.sequenceLength();start=Math.max(1,Math.min(start,length+1));end=Math.max(0,Math.min(end,length));}
        return new Window(row.chromosome(),start,end,row.strand());
    }
    private record Query(Window window,int width,int kind,int row,long[][] matrix) { }
    private record Group(List<String> cells,long[][] counts,int width) { }
    public static Result enrich(List<SignacFragments.Source> sources,List<Position> positions,List<String> objectCells,Options options,SignacFragments.Limits limits,BooleanSupplier cancelled)throws IOException {
        SignacFragments.check(cancelled);Objects.requireNonNull(options);if(sources.isEmpty()||positions.isEmpty()||positions.size()>limits.features()||objectCells.isEmpty()||objectCells.size()>limits.cells())throw new IllegalArgumentException("Require sources, TSS positions and a bounded object cell universe");
        LinkedHashMap<String,Integer> universe=new LinkedHashMap<>();for(String cell:objectCells){SignacFragments.identity(cell);if(universe.putIfAbsent(cell,universe.size())!=null)throw new IllegalArgumentException("Object cells must be unique");}
        int first=options.firstN()==null?positions.size():Math.min(options.firstN(),positions.size());List<Position> selected=new ArrayList<>();Set<String> originalSeqlevels=new HashSet<>();Map<String,Integer> sequenceLengths=new HashMap<>();for(var p:positions){originalSeqlevels.add(p.chromosome());if(sequenceLengths.containsKey(p.chromosome())&&!Objects.equals(sequenceLengths.get(p.chromosome()),p.sequenceLength()))throw new IllegalArgumentException("TSS positions require one consistent known/unknown sequence length per chromosome");sequenceLengths.put(p.chromosome(),p.sequenceLength());}
        for(var row:positions.subList(0,first))if(!Set.of("chrM","Mt","MT").contains(row.chromosome()))selected.add(row);
        if(selected.isEmpty())throw new IOException("No TSS positions remain after first-n selection and mitochondrial exclusion; the original legacy workflow fails");
        List<SignacFragments.Warning> warnings=new ArrayList<>();warnings.add(new SignacFragments.Warning("LEGACY_TSS_SOURCE_BOUNDARIES","Fast and profile modes use different center windows and source endpoint offsets; this is the legacy TSSEnrichment implementation, not ATACqc","Record the mode and settings. Do not substitute a generic endpoint window, Tn5 shift or the other mode when reproducing a paper."));
        if(options.selectedCells()!=null)warnings.add(new SignacFragments.Warning("SOURCE_CELLS_ARGUMENT_IGNORED","The original legacy TSS implementations do not apply the supplied cells argument in these paths","The explicit object cell universe and Fragment mappings determine populations. Subset the object universe explicitly if scientifically intended."));
        if(selected.stream().anyMatch(r->r.strand().equals("*")))warnings.add(new SignacFragments.Warning("UNSTRANDED_TSS_TREATED_AS_PLUS","Source '*' TSS ranges are treated as '+'","Review strand annotations; no strand was inferred from fragment strand fields."));
        List<Pass> passes=new ArrayList<>();List<SignacFragments.Scan> surveys=new ArrayList<>();for(int i=0;i<sources.size();i++){var scan=SignacFragments.scan(sources.get(i).path(),limits,true,0,cancelled,f->{});surveys.add(scan);passes.add(new Pass(i,"chromosome-survey",scan));}
        if(options.fast())return fast(sources,selected,originalSeqlevels,objectCells,universe,options,limits,cancelled,surveys,passes,warnings);
        return profile(sources,selected,objectCells,options,limits,cancelled,surveys,passes,warnings);
    }
    private static Result fast(List<SignacFragments.Source> sources,List<Position> selected,Set<String> originalSeqlevels,List<String> cells,Map<String,Integer> universe,Options options,SignacFragments.Limits limits,BooleanSupplier cancelled,List<SignacFragments.Scan> surveys,List<Pass> passes,List<SignacFragments.Warning> warnings)throws IOException {
        long[] center=new long[cells.size()],flank=new long[cells.size()];int chunks=(selected.size()+options.processN()-1)/options.processN(),size=selected.size()/chunks;
        for(int source=0;source<sources.size();source++){
            var input=sources.get(source);Set<String> available=new HashSet<>(surveys.get(source).chromosomes());boolean common=originalSeqlevels.stream().anyMatch(available::contains);List<Query> queries=new ArrayList<>();
            if(common)for(int chunk=0;chunk<chunks;chunk++){
                int from=chunk*size,to=chunk==chunks-1?selected.size():(chunk+1)*size;List<Position> local=selected.subList(from,to).stream().filter(r->available.contains(r.chromosome())).toList();
                if(local.isEmpty())throw new IOException("Original TSSFast chunk has no regions on available chromosomes despite shared retained seqlevels; rowSums(NULL) fails. Review process_n and chromosome inventory rather than silently skipping it");
                List<Window> centers=windows(local,500,500),upstream=windows(local,options.extension(),-(options.extension()-99)),downstream=windows(local,-(options.extension()-99),options.extension());
                int cw=centers.get(0).width(),fw=upstream.get(0).width();for(var w:centers)queries.add(new Query(w,cw,0,0,null));for(var w:upstream)queries.add(new Query(w,fw,1,0,null));for(var w:downstream)queries.add(new Query(w,fw,1,0,null));
            }
            Map<String,Integer> converter=new HashMap<>();if(input.map()==null)converter.putAll(universe);else for(var entry:input.map()){Integer index=universe.get(entry.cell());if(index!=null)converter.put(entry.barcode(),index);}
            var index=index(queries);long[] hits={0};var scan=SignacFragments.scan(input.path(),limits,true,0,cancelled,f->{Integer cell=converter.get(f.barcode());if(cell==null)return;for(var interval:index.overlaps(new GenomicIntervals.Interval(f.chromosome(),f.start(),f.end(),"fragment","."),false)){SignacFragments.check(cancelled);if(++hits[0]>limits.contributions())throw new IOException("TSS query contributions exceed the explicit bound");Query query=queries.get(Integer.parseInt(interval.id()));for(int endpoint:new int[]{f.start(),f.end()}){long position=(long)endpoint-query.window.start()-1;if(position>0&&position<=query.width){long[] target=query.kind==0?center:flank;target[cell]=SignacFragments.exact(target[cell],1);}}}});passes.add(new Pass(source,"endpoint-count",scan));
        }
        double[] means=new double[cells.size()];for(int i=0;i<means.length;i++)means[i]=flank[i]/200.0;double population=mean(means);double[] values=new double[cells.size()];boolean infinite=false;
        for(int i=0;i<values.length;i++){double normalized=center[i]/(flank[i]==0?population:means[i]);if(Double.isNaN(normalized))normalized=0;values[i]=normalized/1001;infinite|=Double.isInfinite(values[i]);}
        if(infinite)warnings.add(new SignacFragments.Warning("NONFINITE_TSS_SCORE","Zero whole-population background produced source infinity for cells with center cuts; source NaN center normalization became0","Inspect background coverage and mappings; do not interpret infinity or a zero-imputed undefined score as a passing biological QC result."));
        return new Result(scores(cells,values,cells,cancelled),selected,null,passes,warnings);
    }
    private static Result profile(List<SignacFragments.Source> sources,List<Position> selected,List<String> objectCells,Options options,SignacFragments.Limits limits,BooleanSupplier cancelled,List<SignacFragments.Scan> surveys,List<Pass> passes,List<SignacFragments.Warning> warnings)throws IOException {
        List<Window> full=windows(selected,options.extension(),options.extension()),plus=full.stream().filter(w->!w.strand.equals("-")).toList(),minus=full.stream().filter(w->w.strand.equals("-")).toList();List<Group> plusGroups=new ArrayList<>(),minusGroups=new ArrayList<>();long allocated=0;
        for(int source=0;source<sources.size();source++){
            var input=sources.get(source);Set<String> available=new HashSet<>(surveys.get(source).chromosomes());plus=plus.stream().filter(w->available.contains(w.chromosome)).toList();minus=minus.stream().filter(w->available.contains(w.chromosome)).toList();
            List<String> localCells=input.map()==null?objectCells:input.map().stream().map(SignacFragments.Cell::cell).toList();Map<String,Integer> converter=new HashMap<>();for(int i=0;i<localCells.size();i++)converter.put(input.map()==null?localCells.get(i):input.map().get(i).barcode(),i);
            List<Query> queries=new ArrayList<>();
            for(int strand=0;strand<2;strand++){
                List<Window> local=strand==0?plus:minus;if(local.isEmpty())continue;int width=local.get(0).width();allocated=Math.addExact(allocated,(long)localCells.size()*width);if(allocated>options.profileValues())throw new IOException("TSS raw profile allocation exceeds the declared profile-value bound");
                long[][] counts=new long[localCells.size()][width];Group group=new Group(localCells,counts,width);(strand==0?plusGroups:minusGroups).add(group);for(var w:local)queries.add(new Query(w,width,2,0,counts));
            }
            var index=index(queries);long[] hits={0};var scan=SignacFragments.scan(input.path(),limits,true,0,cancelled,f->{Integer cell=converter.get(f.barcode());if(cell==null)return;for(var interval:index.overlaps(new GenomicIntervals.Interval(f.chromosome(),f.start(),f.end(),"fragment","."),false)){SignacFragments.check(cancelled);if(++hits[0]>limits.contributions())throw new IOException("TSS profile contributions exceed the explicit bound");Query query=queries.get(Integer.parseInt(interval.id()));for(int endpoint:new int[]{f.start(),f.end()}){long position=(long)endpoint-query.window.start()-1;if(position>0&&position<=query.width)query.matrix[cell][(int)position-1]=SignacFragments.exact(query.matrix[cell][(int)position-1],1);}}});passes.add(new Pass(source,"position-count",scan));
        }
        Group p=bind(plusGroups),m=bind(minusGroups);if(p==null&&m==null)throw new IOException("Original profile TSS workflow has no common regions/cut matrix");Group main=p==null?m:p;int width=main.width,rows=main.cells.size();if(rows<2)throw new IOException("Original single-row profile subsetting drops matrix dimensions before rowMeans");
        if(p!=null&&m!=null&&(p.width!=m.width||p.cells.size()!=m.cells.size()))throw new IOException("Original plus/minus TSS matrices have nonconformable dimensions");if(width!=full.get(0).width())throw new IOException("Original profile column naming differs from the first supplied region width");
        int expected=2*options.extension()+1;if(width<expected||options.extension()<500)throw new IOException("Original profile flank/center subsetting is out of bounds for these extended/trimmed windows");
        if((long)(rows+2)*width>options.profileValues())throw new IOException("Normalized TSS profile exceeds the declared profile-value bound");long[][] raw=new long[rows][width];double[] means=new double[rows];
        for(int row=0;row<rows;row++){SignacFragments.check(cancelled);for(int col=0;col<width;col++)raw[row][col]=SignacFragments.exact(p==null?0:p.counts[row][col],m==null?0:m.counts[row][width-1-col]);long sum=0;for(int col=0;col<100;col++)sum=SignacFragments.exact(sum,raw[row][col]);for(int col=expected-100;col<expected;col++)sum=SignacFragments.exact(sum,raw[row][col]);means[row]=sum/200.0;}
        double population=mean(means);if(population==0)throw new IOException("Original zero-background profile normalization becomes a dense Matrix class rejected by SetAssayData; no successful profile is admitted");
        double[][] normalized=new double[rows+2][width];double[] values=new double[rows];int centerFrom=Math.max(1,options.extension()-500),centerTo=options.extension()+500;
        for(int row=0;row<rows;row++){SignacFragments.check(cancelled);double denominator=means[row]==0?population:means[row];for(int col=0;col<width;col++)normalized[row][col]=raw[row][col]/denominator;values[row]=mean(Arrays.copyOfRange(normalized[row],centerFrom-1,centerTo));}
        Arrays.fill(normalized[rows],1);normalized[rows+1][options.extension()]=1;List<String> labels=new ArrayList<>(main.cells);labels.add("expected");labels.add("motif");List<Integer> offsets=new ArrayList<>();int midpoint=(int)Math.rint(width/2.0);for(int col=1;col<=width;col++)offsets.add(col-midpoint);
        warnings.add(new SignacFragments.Warning("PROFILE_SOURCE_POPULATION_AND_PRUNING","Profile rows follow source-map/file order, duplicate cell names attach their first metadata row, and chromosome pruning persists across fragment files","Review profile row IDs and file order. Percentiles use object metadata after first-match profile attachment; do not silently pool duplicate cells or restore source-pruned chromosomes."));
        return new Result(scores(main.cells,values,objectCells,cancelled),selected,new Profile(labels,offsets,raw,normalized),passes,warnings);
    }
    private static List<Window> windows(List<Position> rows,int upstream,int downstream){List<Window> result=new ArrayList<>();for(var row:rows)result.add(extend(row,upstream,downstream,true));return result;}
    private static GenomicIntervals.Index index(List<Query> queries){List<GenomicIntervals.Interval> intervals=new ArrayList<>();for(int i=0;i<queries.size();i++){Window w=queries.get(i).window;long start=Math.max(0L,(long)w.start-1),end=Math.min(536_870_912L,w.end);if(start<end)intervals.add(new GenomicIntervals.Interval(w.chromosome,start,end,Integer.toString(i),"."));}return new GenomicIntervals.Index(intervals);}
    private static Group bind(List<Group> groups)throws IOException{if(groups.isEmpty())return null;int width=groups.get(0).width;List<String> cells=new ArrayList<>();List<long[]> counts=new ArrayList<>();for(var group:groups){if(group.width!=width)throw new IOException("Original rbind TSS profile widths differ across files");cells.addAll(group.cells);counts.addAll(Arrays.asList(group.counts));}return new Group(cells,counts.toArray(long[][]::new),width);}
    private static List<Score> scores(List<String> rows,double[] values,List<String> objectCells,BooleanSupplier cancelled)throws IOException {
        Map<String,Double> first=new HashMap<>();for(int i=0;i<rows.size();i++)first.putIfAbsent(rows.get(i),values[i]);double[] distribution=objectCells.stream().map(first::get).filter(Objects::nonNull).filter(v->!Double.isNaN(v)).mapToDouble(Double::doubleValue).sorted().toArray();if(distribution.length==0)throw new IOException("Original TSS ECDF has no nonmissing attached object scores");
        List<Score> result=new ArrayList<>();for(String cell:objectCells){SignacFragments.check(cancelled);Double value=first.get(cell);if(value==null){result.add(new Score(cell,SignacFragmentQc.Value.na(),SignacFragmentQc.Value.na()));continue;}SignacFragmentQc.Value percentile=SignacFragmentQc.Value.of(Double.NaN);if(!Double.isNaN(value)){int low=0,high=distribution.length;while(low<high){int middle=(low+high)>>>1;if(distribution[middle]<=value)low=middle+1;else high=middle;}percentile=SignacFragmentQc.Value.of(SignacFragmentQc.roundPercentile((double)low/distribution.length));}result.add(new Score(cell,SignacFragmentQc.Value.of(value),percentile));}return result;
    }
    private static double mean(double[] values){if(values.length==0)return Double.NaN;BigDecimal sum=BigDecimal.ZERO;for(double value:values){if(!Double.isFinite(value))return Arrays.stream(values).average().orElse(Double.NaN);sum=sum.add(new BigDecimal(value));}return sum.divide(BigDecimal.valueOf(values.length),new MathContext(40)).doubleValue();}
}

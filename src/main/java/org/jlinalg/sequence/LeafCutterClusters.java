/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;

/**
 * Native clustering/counting following LeafCutter's unchanged RegTools script at
 * commit 2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb (repository Apache-2.0).
 * This is a modification/Java implementation; retain the packaged notice and
 * source profile. It is not the R differential-splicing/Stan model, phenotype
 * normalization, junction extraction, a historical0.2.9 release or a complete
 * LeafCutter/paper workflow. Supplied source BED12 rows and sample order remain
 * available in the result; input files are neither written nor repaired.
 */
public final class LeafCutterClusters {
    private LeafCutterClusters() { }
    public record Axis(String chromosome,String strand) {
        public Axis { token(chromosome,"chromosome");token(strand,"strand"); }
        @Override public String toString(){return chromosome+":"+strand;}
    }
    public record Intron(Axis axis,long start,long end) {
        public Intron { Objects.requireNonNull(axis);require(start>=0&&end>=start,"Source intron coordinates must be nonnegative and ordered; no repair"); }
    }
    /** Explicit source exclusion before coordinate/count parsing for BED blockCount greater than two. */
    public record Input(String originalLine,Intron intron,Long reads,boolean extraBlocks) {
        public Input {Objects.requireNonNull(originalLine);require(extraBlocks?(intron==null&&reads==null):(intron!=null&&reads!=null&&reads>=0),"Invalid typed source row; this native profile requires nonnegative read counts");}
    }
    public record Sample(String id,List<Input> rows) {
        public Sample {Objects.requireNonNull(id);require(id.indexOf('\0')<0&&id.indexOf('\n')<0&&id.indexOf('\r')<0,"Invalid sample identifier");rows=List.copyOf(rows);}
    }
    public record Options(long maximumIntronLength,long minimumReads,double minimumRatio,boolean checkChromosomes) {
        public Options {require(maximumIntronLength>0&&minimumReads>=0&&Double.isFinite(minimumRatio)&&minimumRatio>=0&&minimumRatio<=1,"Require explicit positive intron length, nonnegative read threshold and finite ratio in[0,1]");}
    }
    public record Limits(int maximumSamples,long maximumInputRows,long maximumDistinctIntrons,long maximumWork,long maximumOutputCells) {
        public Limits {require(maximumSamples>0&&maximumInputRows>0&&maximumDistinctIntrons>0&&maximumWork>0&&maximumOutputCells>0,"Positive complete-input/work/output capacities required");}
        public static Limits defaults(){return new Limits(100000,10000000,1000000,100000000,10000000);}
    }
    public record Counted(Intron intron,long reads) {public Counted {Objects.requireNonNull(intron);require(reads>=0,"Negative read count");}}
    /** One-based ordinal is the line order in the corresponding source pooled/refined file. */
    public record Cluster(int ordinal,Axis axis,List<Counted> introns) {public Cluster {introns=List.copyOf(introns);}}
    public enum Disposition { POOLED,SOURCE_EXCLUDES_EXTRA_BLOCKS,SOURCE_EXCLUDES_AMBIGUOUS_STRAND,SOURCE_EXCLUDES_NONCANONICAL_CHROMOSOME,SOURCE_EXCLUDES_LONG_INTRON }
    public record InputDisposition(int sampleOrdinal,int rowOrdinal,Input source,Disposition disposition) { }
    public record Quantified(String id,Intron intron,int cluster,List<Long> numerators,List<Long> denominators) {
        public Quantified {numerators=List.copyOf(numerators);denominators=List.copyOf(denominators);}
    }
    public record Result(List<String> samples,List<Cluster> pooled,List<Cluster> refined,List<Quantified> rows,
                         List<InputDisposition> inputDispositions,long overlapClustersScanned,List<String> warnings) {
        public Result {samples=List.copyOf(samples);pooled=List.copyOf(pooled);refined=List.copyOf(refined);rows=List.copyOf(rows);inputDispositions=List.copyOf(inputDispositions);warnings=List.copyOf(warnings);}
    }

    /**
     * Parse the source's twelve whitespace-separated BED fields. BED source
     * arithmetic is start+leftBlock and end-rightBlock+1, retained literally.
     * The native profile rejects malformed rows, negative counts, colon/space
     * identifiers and int64 overflow atomically. The upstream parser can instead
     * print/skip some malformed rows; no equivalence is claimed for those inputs.
     * Extra-block rows remain explicit source exclusions, with their full text.
     */
    public static Input parseRegtools(String line) {
        Objects.requireNonNull(line);require(line.length()<=1048576&&line.indexOf('\0')<0,"Source BED12 line capacity/NUL violation");String[] f=line.strip().split("\\s+");require(f.length==12,"Require exactly12 source BED fields; malformed rows are not silently omitted");long blocks=Long.parseLong(f[9]);
        if(blocks>2)return new Input(line,null,null,true);
        require(blocks==2,"This native valid-BED12 profile requires exactly two blocks");String[] sizes=f[10].split(",",-1);require(sizes.length==2,"Source blockSizes must contain exactly two integers without a trailing comma");long start=Long.parseLong(f[1]),end=Long.parseLong(f[2]),left=Long.parseLong(sizes[0]),right=Long.parseLong(sizes[1]),reads=Long.parseLong(f[4]);require(start>=0&&end>=start&&left>=0&&right>=0&&reads>=0,"Invalid source BED coordinates/block size/read count; no repair");Intron intron=new Intron(new Axis(f[0],f[5]),Math.addExact(start,left),Math.addExact(Math.subtractExact(end,right),1));return new Input(line,intron,reads,false);
    }

    /**
     * Return complete source-derived pooled/refined clusters and every retained
     * numerator/denominator. No result is returned for an invalid input, int64
     * count overflow or exhausted capacity. Duplicate sample IDs remain distinct
     * by ordinal; this in-memory API does not perform source filename collapsing.
     */
    public static Result compute(List<Sample> samples,Options options,Limits limits) {
        samples=List.copyOf(samples);Objects.requireNonNull(options);Objects.requireNonNull(limits);require(!samples.isEmpty()&&samples.size()<=limits.maximumSamples(),"Complete sample capacity exceeded or sample family empty");Work work=new Work(limits.maximumWork());var byAxis=new LinkedHashMap<Axis,LinkedHashMap<Intron,Long>>();var perSample=new ArrayList<Map<Intron,Long>>();var dispositions=new ArrayList<InputDisposition>();long nInput=0,nDistinct=0;
        for(int s=0;s<samples.size();s++){
            var counts=new HashMap<Intron,Long>();var sample=samples.get(s);perSample.add(counts);
            for(int r=0;r<sample.rows().size();r++){
                work.tick();require(++nInput<=limits.maximumInputRows(),"Complete source-row capacity exceeded; no partial result");Input row=sample.rows().get(r);Disposition decision;
                if(row.extraBlocks())decision=Disposition.SOURCE_EXCLUDES_EXTRA_BLOCKS;
                else if(row.intron().axis().strand().equals("?"))decision=Disposition.SOURCE_EXCLUDES_AMBIGUOUS_STRAND;
                else if(options.checkChromosomes()&&!canonicalChromosome(row.intron().axis().chromosome()))decision=Disposition.SOURCE_EXCLUDES_NONCANONICAL_CHROMOSOME;
                else if(row.intron().end()-row.intron().start()>options.maximumIntronLength())decision=Disposition.SOURCE_EXCLUDES_LONG_INTRON;
                else {
                    decision=Disposition.POOLED;Intron key=row.intron();var group=byAxis.computeIfAbsent(key.axis(),ignored->new LinkedHashMap<>());if(!group.containsKey(key)){require(++nDistinct<=limits.maximumDistinctIntrons(),"Complete pooled-intron capacity exceeded; no partial result");}
                    group.merge(key,row.reads(),Math::addExact);counts.merge(key,row.reads(),Math::addExact);
                }
                dispositions.add(new InputDisposition(s,r,row,decision));
            }
        }
        var pooled=new ArrayList<Cluster>();var refined=new ArrayList<Cluster>();long nOverlap=0;
        for(var axis:byAxis.entrySet()){
            var selected=new ArrayList<Intron>();for(var entry:axis.getValue().entrySet())if(entry.getValue()>=3)selected.add(entry.getKey());
            for(var group:intervalClusters(selected,work)){
                nOverlap=Math.addExact(nOverlap,1);if(group.size()<2)continue;var counted=new ArrayList<Counted>();long total=0;for(Intron intron:group){long reads=axis.getValue().get(intron);total=Math.addExact(total,reads);counted.add(new Counted(intron,reads));}pooled.add(new Cluster(pooled.size()+1,axis.getKey(),counted));
                if(total<options.minimumReads())continue;
                for(var connected:linked(counted,work))for(var cluster:refine(connected,options.minimumRatio(),options.minimumReads(),work))refined.add(new Cluster(refined.size()+1,axis.getKey(),cluster));
            }
        }
        long nRows=0;for(var c:refined)nRows=Math.addExact(nRows,c.introns().size());require(nRows<=limits.maximumOutputCells()/samples.size(),"Complete native count-matrix capacity exceeded; no partial result");var output=new ArrayList<Quantified>();
        for(var cluster:refined){
            var introns=new ArrayList<Counted>(cluster.introns());
            // Source sort_junctions sorts (chrom,A,B) tuples with A/B stored as
            // strings. Preserve that lexical row order, including100 before20.
            introns.sort((a,b)->{work.tick();int c=Long.toString(a.intron().start()).compareTo(Long.toString(b.intron().start()));return c!=0?c:Long.toString(a.intron().end()).compareTo(Long.toString(b.intron().end()));});var denominators=new ArrayList<Long>();
            for(var counts:perSample){long total=0;for(var intron:introns){work.tick();total=Math.addExact(total,counts.getOrDefault(intron.intron(),0L));}denominators.add(total);}
            for(var entry:introns){var nums=new ArrayList<Long>();for(var counts:perSample){work.tick();nums.add(counts.getOrDefault(entry.intron(),0L));}Intron i=entry.intron();output.add(new Quantified(i.axis().chromosome()+":"+i.start()+":"+i.end()+":clu_"+cluster.ordinal()+"_"+i.axis().strand(),i,cluster.ordinal(),nums,denominators));}
        }
        var warnings=new ArrayList<String>();warnings.add("Source behavior: pooled introns require at least3 reads; minimumReads also filters each intron during refinement. Overlap includes touching endpoints; linked refinement shares either endpoint. First-seen chromosome/strand group order and lexical output-coordinate order are retained. Source counts with denominator0 remain0/0; no imputation, pseudocount, phenotype normalization, PCA or differential test is performed.");warnings.add("Native valid-BED12/int64 in-memory component only. This is not junction extraction, an original R/Stan workflow, a historical paper release or full LeafCutter/tool admission. Invalid/malformed/negative/overflow/capacity inputs throw before any Result is returned; preserve original input and correct the named cause rather than using partial output.");if(output.isEmpty())warnings.add("No alternative-intron cluster survived the explicit source filters. The complete empty result is retained; it is not evidence that no biological splicing occurs.");return new Result(samples.stream().map(Sample::id).toList(),pooled,refined,output,dispositions,nOverlap,warnings);
    }

    private static boolean canonicalChromosome(String chromosome) {
        String value=chromosome.startsWith("chr")?chromosome.substring(3):chromosome;if(value.equals("X")||value.equals("Y"))return true;for(int i=1;i<=22;i++)if(value.equals(Integer.toString(i)))return true;return false;
    }
    private static List<List<Intron>> intervalClusters(List<Intron> input,Work work) {
        var sorted=new ArrayList<Intron>(input);sorted.sort((a,b)->{work.tick();int c=Long.compare(a.start(),b.start());return c!=0?c:Long.compare(a.end(),b.end());});var result=new ArrayList<List<Intron>>();if(sorted.isEmpty())return result;var group=new ArrayList<Intron>();long end=sorted.get(0).end();
        for(Intron intron:sorted){work.tick();if(!group.isEmpty()&&intron.start()>end){result.add(group);group=new ArrayList<>();}group.add(intron);end=Math.max(end,intron.end());}result.add(group);return result;
    }
    /** Literal source scan order and singleton-return behavior are deliberate. */
    private static List<List<Counted>> linked(List<Counted> input,Work work) {
        var unassigned=new ArrayList<>(input.subList(1,input.size()));var current=new ArrayList<Counted>();current.add(input.get(0));var sites=new HashSet<Long>();sites.add(current.get(0).intron().start());sites.add(current.get(0).intron().end());var result=new ArrayList<List<Counted>>();
        while(!unassigned.isEmpty()){
            boolean finished=false;
            while(!finished){finished=true;var remove=new ArrayList<Counted>();for(Counted intron:unassigned){work.tick();if(sites.contains(intron.intron().start())||sites.contains(intron.intron().end())){current.add(intron);sites.add(intron.intron().start());sites.add(intron.intron().end());finished=false;remove.add(intron);}}for(Counted intron:remove){work.tick();unassigned.remove(intron);}}
            result.add(current);current=new ArrayList<>();if(!unassigned.isEmpty()){current.add(unassigned.remove(0));sites=new HashSet<>();sites.add(current.get(0).intron().start());sites.add(current.get(0).intron().end());}
        }
        return result;
    }
    private static List<List<Counted>> refine(List<Counted> input,double ratio,long minimum,Work work) {
        work.tick();long total=0;for(Counted intron:input){work.tick();total=Math.addExact(total,intron.reads());}var keep=new ArrayList<Intron>();var counts=new HashMap<Intron,Long>();boolean removed=false;
        for(Counted intron:input){work.tick();if(intron.reads()/(double)total>=ratio&&intron.reads()>=minimum){keep.add(intron.intron());counts.put(intron.intron(),intron.reads());}else removed=true;}if(keep.isEmpty())return List.of();var connected=new ArrayList<List<Intron>>();
        for(var intervals:intervalClusters(keep,work)){var zero=new ArrayList<Counted>();for(Intron intron:intervals)zero.add(new Counted(intron,0));for(var group:linked(zero,work))if(!group.isEmpty())connected.add(group.stream().map(Counted::intron).toList());}
        if(connected.size()==1){var rc=withCounts(connected.get(0),counts);if(rc.size()<2)return List.of();return removed?refine(rc,ratio,minimum,work):List.of(rc);}
        var result=new ArrayList<List<Counted>>();for(var group:connected)if(group.size()>1)result.addAll(refine(withCounts(group,counts),ratio,minimum,work));return result;
    }
    private static List<Counted> withCounts(List<Intron> introns,Map<Intron,Long> counts){return introns.stream().map(i->new Counted(i,counts.get(i))).toList();}
    private static final class Work {
        final long maximum;long count;Work(long maximum){this.maximum=maximum;}
        void tick(){require(count<maximum,"Complete refinement/sort/work capacity exceeded; no partial result");count++;}
    }
    private static void token(String value,String name){Objects.requireNonNull(value,name);require(!value.isEmpty()&&value.indexOf(':')<0&&value.codePoints().noneMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0),"Unsupported colon/whitespace/NUL in source "+name+"; identifiers are not repaired");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}

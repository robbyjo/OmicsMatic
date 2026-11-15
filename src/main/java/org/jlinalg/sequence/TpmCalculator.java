/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Native annotation modelling and BAM quantification translated from TPMCalculator
 * tag 0.0.4, commit b275fb3d749dc46e7e0efeca9bdd1c230702339d (which prints 0.0.3).
 * This is a development implementation; catalog admission requires the independent
 * original-source comparison and complete public workflow qualification.
 * Coordinates are zero-based inclusive. Source counting quirks and nonfinite
 * feature TPMs are retained. This is not a generic fragment counter.
 */
public final class TpmCalculator {
    private TpmCalculator() { }
    public static final String SOURCE_COMMIT = "b275fb3d749dc46e7e0efeca9bdd1c230702339d";
    private static final long UINT = 0xffffffffL;
    private static final Comparator<F> ORDER = Comparator.comparingLong((F f) -> f.start).thenComparingLong(f -> f.end);
    private static final Comparator<Block> BLOCK_ORDER = Comparator.comparingLong(Block::start).thenComparingLong(Block::end);

    /** Caller choices; no library strandedness or author edition is inferred. */
    public record Options(String geneKey, String transcriptKey, int intronCutoff,
                          int minimumMapq, int minimumOverlap, boolean properlyPairedOnly) {
        public Options {
            Objects.requireNonNull(geneKey); Objects.requireNonNull(transcriptKey);
            if (geneKey.isEmpty() || transcriptKey.isEmpty() || geneKey.equals(transcriptKey)
                    || intronCutoff < 0 || minimumMapq < 0 || minimumMapq > 65535
                    || minimumOverlap < 0 || minimumOverlap > 65535)
                throw new IllegalArgumentException("Require distinct annotation keys, nonnegative cutoff and uint16 MAPQ/overlap");
        }
        public static Options defaults() { return new Options("gene_id", "transcript_id", 16, 0, 8, false); }
    }
    public record Sample(String name, Path bam) {
        public Sample { Objects.requireNonNull(name); Objects.requireNonNull(bam); if (name.isEmpty()) throw new IllegalArgumentException("Empty sample name"); }
    }
    /** Seven source counters and their separate TPM denominators. */
    public record Metrics(long reads, double tpm, long exonReads, double exonTpm,
                          long intronReads, double intronTpm, long uniqueReads, double uniqueTpm,
                          long uniqueExonReads, double uniqueExonTpm, long uniqueIntronReads,
                          double uniqueIntronTpm, long bridgeReads, double bridgeTpm) { }
    public record Feature(String type, char strand, long start, long end, long length, boolean dataPresent, Metrics metrics) { }
    public record Transcript(String id, char strand, long start, long end, long length,
                             long exonLength, long intronLength, boolean processed, boolean dataPresent,
                             Metrics metrics, List<Feature> features) {
        public Transcript { features = List.copyOf(features); }
    }
    public record Gene(String id, String chromosome, char strand, long start, long end, long length,
                       long exonLength, long intronLength, long uniqueLength, long uniqueExonLength,
                       long uniqueIntronLength, boolean processed, boolean dataPresent, Metrics metrics,
                       List<Feature> features, List<Feature> uniqueFeatures, List<Transcript> transcripts) {
        public Gene { features = List.copyOf(features); uniqueFeatures = List.copyOf(uniqueFeatures); transcripts = List.copyOf(transcripts); }
    }
    public record SampleResult(String name, long processedAlignments, long ignoredAnnotationChromosomeAlignments,
                               List<Gene> genes) {
        public SampleResult { genes = List.copyOf(genes); }
    }
    public record Result(List<SampleResult> samples, List<String> excludedAntisenseGeneIds, Options options) {
        public Result { samples = List.copyOf(samples); excludedAntisenseGeneIds = List.copyOf(excludedAntisenseGeneIds); }
    }
    public record Block(long start, long end) { }

    private static class F {
        final String type;
        long start, end;
        char strand;
        F(String type, long start, long end, char strand) { this.type = type; this.start = start; this.end = end; this.strand = strand; }
        long length() { return u(end - start + 1); }
        boolean inside(Block b, int overlap) {
            return b.start >= start && b.start <= u(end - overlap)
                    || b.end >= u(start + overlap) && b.end <= end || b.start <= start && b.end >= end;
        }
        F segment(long s, long e) { return new F(type, u(s), u(e), strand); }
    }
    private static class T extends F {
        final String id;
        final TreeSet<F> features = new TreeSet<>(ORDER);
        long length, exonLength, intronLength;
        T(String id, long start, long end, char strand) { super("isoform", start, end, strand); this.id = id; }
        @Override long length() { return length; }
        void insert(F f) {
            if (features.add(f)) {
                start = Math.min(start, f.start); end = Math.max(end, f.end);
                length = u(length + f.length());
                if (f.type.equals("exon")) exonLength = u(exonLength + f.length());
                if (f.type.equals("intron")) intronLength = u(intronLength + f.length());
            }
        }
        void gaps() {
            for (F f = features.isEmpty() ? null : features.first(); f != null; f = features.higher(f)) {
                if (!f.type.equals("exon")) continue;
                for (F next = features.higher(f); next != null; next = features.higher(next)) {
                    if (!next.type.equals("exon")) { if (!f.inside(new Block(next.start, next.end), 0)) break; }
                    else { insert(new F("intron", u(f.end + 1), u(next.start - 1), f.strand)); break; }
                }
            }
        }
    }
    private static final class G extends T {
        final List<T> transcripts;
        final TreeSet<F> unique = new TreeSet<>(ORDER);
        long uniqueLength, uniqueExonLength, uniqueIntronLength;
        G(String id, long start, long end, List<T> transcripts) {
            super(id, start, end, transcripts.get(0).strand); this.transcripts = transcripts; length = u(end - start + 1);
        }
    }
    private static final class Chr {
        final String name;
        final Map<String, Map<String,T>> inputs = new LinkedHashMap<>();
        final List<G> genes = new ArrayList<>();
        T current;
        Chr(String name) { this.name = name; }
    }
    private static long u(long value) { return value & UINT; }
    private static List<String> split(String text, String delimiter) {
        var result = new ArrayList<String>();
        for (String value : text.split(java.util.regex.Pattern.quote(delimiter), -1)) if (!value.isEmpty()) result.add(value);
        return result;
    }
    private static String trim(String text) {
        int a = 0, b = text.length();
        while (a < b && " \t\r\n\f\u000b".indexOf(text.charAt(a)) >= 0) a++;
        while (b > a && " \t\r\n\f\u000b".indexOf(text.charAt(b-1)) >= 0) b--;
        return text.substring(a,b);
    }
    private static String rawLine(BufferedReader reader) throws IOException {
        var line = new StringBuilder(); int ch;
        while ((ch = reader.read()) != -1 && ch != '\n') line.append((char) ch);
        return ch == -1 && line.isEmpty() ? null : line.toString();
    }
    private static int position(String value) throws IOException {
        // atoi overflow and malformed lexical coordinates have undefined/ambiguous original behaviour.
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            int p = Integer.parseInt(value);
            if (p < 1) throw new NumberFormatException();
            return p - 1;
        } catch (NumberFormatException e) { throw new IOException("Require positive signed-int GTF coordinates; original atoi ambiguities are not silently repaired: " + value, e); }
    }

    /** Immutable after construction; each quantification keeps independent counters. */
    public static final class Model {
        private final Options options;
        private final List<Chr> chromosomes;
        private final List<Chr> sourceChromosomes;
        private final Map<String, Chr> byName;
        private final List<String> excluded;
        private Model(Options options, List<Chr> chromosomes, List<String> excluded) {
            this.options = options; this.chromosomes = List.copyOf(chromosomes); this.excluded = List.copyOf(excluded);
            var index = new HashMap<String, Chr>(); for (Chr c : chromosomes) index.put(c.name,c); byName = Map.copyOf(index);
            var original = new LinkedHashMap<String,Chr>(); for (Chr c:chromosomes) original.put(c.name,c);
            sourceChromosomes = TpmCalculatorOrder.entries(original).stream().map(Map.Entry::getValue).toList();
        }
        public Options options() { return options; }
        public List<String> excludedAntisenseGeneIds() { return excluded; }
        public Result quantify(List<Sample> samples) throws IOException {
            Objects.requireNonNull(samples); samples = List.copyOf(samples);
            if (samples.isEmpty()) throw new IllegalArgumentException("At least one BAM sample is required");
            var names = new HashSet<String>(); for (Sample s : samples) if (!names.add(s.name)) throw new IllegalArgumentException("Duplicate sample name: " + s.name);
            State state = new State(); var all = new ArrayList<IdentityHashMap<F,C>>(); var totals = new ArrayList<long[]>();
            for (Sample sample : samples) {
                var counts = new IdentityHashMap<F,C>(); all.add(counts);
                long[] count = new long[2]; totals.add(count);
                try (SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(sample.bam)) {
                    if (reader.type() != SamReader.Type.BAM_TYPE) throw new IOException("TPMCalculator quantification requires BAM, not " + reader.type());
                    for (SAMRecord record : reader) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("TPMCalculator quantification cancelled; no complete result");
                        int flags = record.getFlags();
                        if ((flags & (4 | 512)) != 0 || record.getMappingQuality() < options.minimumMapq || options.properlyPairedOnly && (flags & 2) == 0) continue;
                        List<Block> blocks = alignmentBlocks(record, options.minimumOverlap);
                        if (blocks.isEmpty()) continue;
                        count[0] = Math.addExact(count[0],1);
                        if (count[0] > Integer.MAX_VALUE) throw new IOException("Original processed-alignment signed counter would overflow; no complete result");
                        Chr chr = byName.get(record.getReferenceName());
                        if (chr == null) { count[1]++; continue; }
                        if (chr.genes.isEmpty()) throw new IOException("Original gene search on an annotation chromosome without genes is undefined: " + chr.name);
                        scan(chr.genes, blocks, counts, state, options.minimumOverlap);
                    }
                } catch (RuntimeException e) { throw new IOException("BAM parsing/counting failed; no complete TPMCalculator result: " + sample.bam, e); }
                normalize(sourceChromosomes, counts, state); // Original normalizes after each sample, before the next BAM.
            }
            var output = new ArrayList<SampleResult>();
            for (int index=0; index<samples.size(); index++) {
                var counts = all.get(index); var genes = new ArrayList<Gene>();
                for (Chr chr : chromosomes) for (G g : chr.genes) {
                    var transcripts = new ArrayList<Transcript>();
                    for (T t : g.transcripts) transcripts.add(new Transcript(t.id,t.strand,t.start,t.end,t.length(),t.exonLength,t.intronLength,
                        state.processed.contains(t),counts.containsKey(t),metrics(counts.get(t)),features(t.features,counts)));
                    genes.add(new Gene(g.id,chr.name,g.strand,g.start,g.end,g.length(),g.exonLength,g.intronLength,g.uniqueLength,g.uniqueExonLength,g.uniqueIntronLength,
                        state.processed.contains(g),counts.containsKey(g),metrics(counts.get(g)),features(g.features,counts),features(g.unique,counts),transcripts));
                }
                output.add(new SampleResult(samples.get(index).name,totals.get(index)[0],totals.get(index)[1],genes));
            }
            return new Result(output,excluded,options);
        }
    }

    /** Parse the selected source's exon-only GTF model. Blank lines and missing keys fail visibly. */
    public static Model readAnnotation(Path gtf, Options options) throws IOException {
        Objects.requireNonNull(options); var chromosomes = new LinkedHashMap<String,Chr>(); var excluded = new TreeSet<String>();
        try (BufferedReader reader = Files.newBufferedReader(gtf,StandardCharsets.UTF_8)) {
            String line; long number=0;
            while ((line=rawLine(reader)) != null) {
                number++; if (line.startsWith("#")) continue;
                List<String> cols = split(line,"\t");
                if (cols.size()!=9) throw new IOException("GTF line " + number + " must have nine nonempty tab-separated fields");
                if (!cols.get(2).equals("exon")) continue;
                long start = position(cols.get(3)), end = position(cols.get(4));
                if (start > end) throw new IOException("Inverted input GTF coordinates at line " + number);
                var attributes = new HashMap<String,String>();
                for (String attribute : split(cols.get(8),";")) {
                    List<String> tokens = split(attribute,"\"");
                    if (tokens.size()==2) attributes.put(trim(tokens.get(0)),trim(tokens.get(1)));
                }
                String gene = attributes.get(options.geneKey), transcript = attributes.get(options.transcriptKey);
                if (gene==null || gene.isEmpty() || transcript==null || transcript.isEmpty()) throw new IOException("Missing quoted gene/transcript key at GTF line " + number);
                Chr chr = chromosomes.computeIfAbsent(cols.get(0),Chr::new);
                if (gene.contains("-AS")) { excluded.add(gene); continue; }
                char strand = cols.get(6).charAt(0);
                Map<String,T> map = chr.inputs.get(gene);
                if (map==null) {
                    map=new LinkedHashMap<>(); chr.inputs.put(gene,map);
                    chr.current = new T(transcript,start,end,strand); map.put(transcript,chr.current);
                } else if (chr.current==null || !chr.current.id.equals(transcript)) {
                    chr.current = map.computeIfAbsent(transcript,id -> new T(id,start,end,strand));
                }
                // Deliberately retains the source's current-transcript reuse across gene IDs.
                chr.current.insert(new F("exon",start,end,strand));
            }
        }
        try { for (Chr chr : chromosomes.values()) build(chr,options.intronCutoff); }
        catch (ArithmeticException e) { throw new IOException("Original signed feature-overlap arithmetic would overflow; no complete annotation model",e); }
        return new Model(options,new ArrayList<>(chromosomes.values()),new ArrayList<>(excluded));
    }

    private static void build(Chr chr, int cutoff) throws IOException {
        Set<String> ids = new HashSet<>();
        for (var entry : TpmCalculatorOrder.entries(chr.inputs)) {
            var tx = new ArrayList<T>(); for (var e:TpmCalculatorOrder.entries(entry.getValue())) tx.add(e.getValue()); tx.sort(ORDER);
            var intervals = new ArrayList<Block>();
            for (T t : tx) {
                if (intervals.isEmpty() || intervals.get(intervals.size()-1).end < t.start) intervals.add(new Block(t.start,t.end));
                else { Block b = intervals.remove(intervals.size()-1); intervals.add(new Block(b.start,Math.max(b.end,t.end))); }
            }
            int copy=1;
            for (Block b : intervals) {
                var inside = new ArrayList<T>();
                for (T t : tx) if (t.start>=b.start && t.start<=b.end) { t.gaps(); inside.add(t); }
                String id=entry.getKey()+(intervals.size()>1 ? "#"+copy++ : "");
                if (!ids.add(id)) throw new IOException("Original disconnected-gene naming collision: " + id);
                G gene = new G(id,b.start,b.end,inside); geneFeatures(gene,cutoff); chr.genes.add(gene);
            }
        }
        chr.genes.sort(ORDER); chr.inputs.clear(); chr.current=null;
        for (G g : chr.genes) for (F f : g.features) {
            var segments = new TreeSet<F>(ORDER); boolean covered=false;
            for (G other : chr.genes) if (g!=other) {
                if (other.start>f.end) break;
                if (other.end<f.start) continue; // feature_disunion empty; source features_union returns the existing segments.
                if (other.start<=f.start && other.end>=f.end) { segments.clear(); covered=true; break; }
                var pieces = new TreeSet<F>(ORDER);
                long a=Math.max(f.start,other.start), b=Math.min(f.end,other.end);
                if (sourceOverlap(f,other)) {
                    if (f.end==b) pieces.add(f.segment(f.start,a-1));
                    else if (f.start==a) pieces.add(f.segment(b+1,f.end));
                    else { pieces.add(f.segment(f.start,a-1)); pieces.add(f.segment(b+1,f.end)); }
                }
                if (segments.isEmpty()) segments.addAll(pieces);
                else if (!pieces.isEmpty()) {
                    var intersections = new TreeSet<F>(ORDER);
                    for (F s : segments) for (F p : pieces) {
                        long left=Math.max(s.start,p.start), right=Math.min(s.end,p.end);
                        if (sourceOverlap(s,p)) intersections.add(f.segment(left,right));
                    }
                    segments=intersections;
                    if (segments.isEmpty()) { covered=true; break; }
                }
            }
            if (!covered) { if (segments.isEmpty()) g.unique.add(f.segment(f.start,f.end)); else g.unique.addAll(segments); }
        }
        for (G g : chr.genes) for (F f : g.unique) {
            g.uniqueLength=u(g.uniqueLength+f.length());
            if (f.type.equals("exon")) g.uniqueExonLength=u(g.uniqueExonLength+f.length());
            if (f.type.equals("intron")) g.uniqueIntronLength=u(g.uniqueIntronLength+f.length());
        }
    }

    private static boolean sourceOverlap(F left,F right) {
        // GenomeFactory::feature_overlap explicitly casts uint32 endpoints to int.
        // Derived gaps can wrap an end to 0xffffffff even for valid positive GTF rows.
        return Math.subtractExact((int)left.end,(int)right.start)>=0
            && Math.subtractExact((int)right.end,(int)left.start)>=0;
    }

    private static boolean unionOverlap(F other,long a,long b) {
        return other.end>a && other.end<=b || other.start>=a && other.start<b || other.start<a && other.end>b;
    }
    private static long[] clip(long a,long b,F exon) {
        if (exon.start<=a && exon.end>a && exon.end<b) a=u(exon.end+1);
        else if (exon.start>a && exon.start<b && exon.end>=b) b=u(exon.start-1);
        else if (exon.start<=a && exon.end>=b) { a=0; b=0; }
        return new long[]{a,b};
    }
    private static void geneFeatures(G g,int cutoff) {
        for (T t : g.transcripts) for (F f : t.features) {
            long a=f.start,b=f.end; var parts=new TreeSet<Block>(BLOCK_ORDER);
            for (T other : g.transcripts) if (t!=other) {
                for (F x : other.features) {
                    if (f.type.equals("exon") && x.type.equals("exon") && unionOverlap(x,a,b)) { a=Math.min(a,x.start); b=Math.max(b,x.end); }
                    if (f.type.equals("intron") && x.type.equals("exon")) {
                        if (x.start>a && x.start<b && x.end<b) { parts.add(new Block(a,u(x.start-1))); a=u(x.end+1); b=f.end; }
                        else { long[] pair=clip(a,b,x); a=pair[0]; b=pair[1]; }
                    }
                    if (x.start>b || a==0 && b==0) break;
                }
            }
            boolean changed=a!=f.start || b!=f.end || !parts.isEmpty();
            for (Block part : parts) {
                long left=part.start,right=part.end;
                for (T other : g.transcripts) for (F x : other.features) {
                    if (x.type.equals("exon")) { long[] pair=clip(left,right,x); left=pair[0]; right=pair[1]; }
                    if (x.start>right || left==0 && right==0) break;
                }
                if (left!=0 && right!=0 && u(right-left+1)>cutoff) g.features.add(new F("intron",left,right,g.strand));
            }
            if ((!changed || a!=0 && b!=0) && (!f.type.equals("intron") || u(b-a+1)>=cutoff)) g.features.add(new F(f.type,a,b,g.strand));
        }
        for (F f=g.features.isEmpty()?null:g.features.first(); f!=null;) {
            long a=f.start,b=f.end;
            for (F x : g.features) if (x!=f && unionOverlap(x,a,b)) { a=Math.min(a,x.start); b=Math.max(b,x.end); }
            if (a!=f.start || b!=f.end) { F next=g.features.higher(f); g.features.remove(f); g.features.add(new F(f.type,a,b,g.strand)); f=next; }
            else f=g.features.higher(f);
        }
        for (F f : g.features) {
            if (f.type.equals("exon")) g.exonLength=u(g.exonLength+f.length());
            if (f.type.equals("intron")) g.intronLength=u(g.intronLength+f.length());
        }
    }

    /** Raw source CIGAR rules: I contributes to the block span, S/H/P do not; N splits. */
    public static List<Block> alignmentBlocks(SAMRecord record,int minimumOverlap) throws IOException {
        if (minimumOverlap<0 || minimumOverlap>65535) throw new IllegalArgumentException("Require uint16 minimum overlap");
        int start=record.getAlignmentStart()-1, end=start, length=0;
        if (start<0) throw new IOException("Mapped BAM record has no positive alignment start");
        // ReadFactory::coordinateLessCMP compares only the starting coordinate.
        var blocks=new TreeSet<Block>(Comparator.comparingLong(Block::start));
        try {
            for (var op : record.getCigar().getCigarElements()) {
                char type=op.getOperator().toString().charAt(0); int n=op.getLength();
                if (type!='S' && type!='H' && type!='P') length=Math.addExact(length,n);
                if (type=='N') {
                    if ((long)end-start+1>=minimumOverlap) blocks.add(new Block(u(start),u(end)));
                    start=Math.addExact(Math.addExact(end,1),n); length=0;
                } else end=Math.subtractExact(Math.addExact(start,length),1);
            }
            if ((long)end-start+1>=minimumOverlap) blocks.add(new Block(u(start),u(end)));
        } catch (ArithmeticException e) { throw new IOException("Original signed CIGAR arithmetic would overflow; no complete result",e); }
        return List.copyOf(blocks);
    }

    private static final class C {
        final int[] reads=new int[7]; final double[] tpm=new double[7];
        void add(int k) { reads[k]=Math.incrementExact(reads[k]); }
    }
    private static final class State { final Set<F> processed=java.util.Collections.newSetFromMap(new IdentityHashMap<>()); }
    private static C counter(Map<F,C> counts,F f) { return counts.computeIfAbsent(f,unused->new C()); }
    private static void scan(List<G> genes,List<Block> blocks,Map<F,C> counts,State state,int overlap) {
        Block last=blocks.get(blocks.size()-1); int lo=0,hi=genes.size();
        while (lo<hi) { int mid=(lo+hi)>>>1; F g=genes.get(mid); if (g.start<last.start || g.start==last.start && g.end<last.end) lo=mid+1; else hi=mid; }
        int bound=Math.min(lo,genes.size()-1); boolean done=false;
        for (int k=bound; k>=0; k--) {
            countGene(genes.get(k),blocks,counts,state,overlap);
            if (done) break;
            if (bound-k>6 && genes.get(k).end<blocks.get(0).start) done=true;
        }
    }
    private static void countGene(G g,List<Block> blocks,Map<F,C> counts,State state,int overlap) {
        boolean[] counted=new boolean[7];
        for (Block block : blocks) {
            if (!g.inside(block,overlap)) return; // Earlier block mutations are deliberately retained.
            state.processed.add(g); C c=counter(counts,g); once(c,counted,0);
            countFeatures(g.features,block,overlap,counts,c,counted);
            for (F f : g.unique) {
                C cf=counter(counts,f);
                if (f.inside(block,overlap)) { cf.add(0); once(c,counted,3); once(c,counted,f.type.equals("exon")?4:5); }
            }
        }
        for (T t : g.transcripts) {
            state.processed.add(t); boolean[] seen=new boolean[7];
            for (Block block : blocks) if (t.inside(block,overlap)) {
                C c=counter(counts,t); once(c,seen,0); countFeatures(t.features,block,overlap,counts,c,seen);
            }
        }
    }
    private static void once(C c,boolean[] counted,int k) { if (!counted[k]) { c.add(k); counted[k]=true; } }
    private static void countFeatures(TreeSet<F> features,Block block,int overlap,Map<F,C> counts,C c,boolean[] seen) {
        for (F f : features) {
            C cf=counter(counts,f);
            if (f.inside(block,overlap)) {
                cf.add(0); if (block.end>f.end && f!=features.last()) once(c,seen,6);
                once(c,seen,f.type.equals("exon")?1:2);
            }
        }
    }
    private static long[] lengths(T t) {
        return t instanceof G g ? new long[]{g.length(),g.exonLength,g.intronLength,g.uniqueLength,g.uniqueExonLength,g.uniqueIntronLength,g.features.size()-1L}
                : new long[]{t.length(),t.exonLength,t.intronLength,0,0,0,t.features.size()-1L};
    }
    private static void normalize(List<Chr> chromosomes,Map<F,C> counts,State state) {
        double[][] sums=new double[2][9];
        for (Chr chr : chromosomes) for (G g : chr.genes) if (state.processed.contains(g)) {
            rate(g,counts,sums[0],false);
            for (T t : g.transcripts) if (state.processed.contains(t)) rate(t,counts,sums[1],true);
        }
        for (Chr chr : chromosomes) for (G g : chr.genes) if (state.processed.contains(g)) {
            scale(g,counts,sums[0],false);
            for (T t : g.transcripts) if (state.processed.contains(t)) scale(t,counts,sums[1],true);
        }
    }
    private static void rate(T t,Map<F,C> counts,double[] sums,boolean create) {
        C c=create?counter(counts,t):counts.get(t); if (c==null) return;
        long[] len=lengths(t);
        for (int k=0; k<7; k++) if (k==0 || len[k]>0) sums[k]+=(double)c.reads[k]/len[k];
        for (F f : t.features) { C cf=create?counter(counts,f):counts.get(f); if (cf==null) return; sums[7]+=(double)cf.reads[0]/f.length(); }
        if (t instanceof G g) for (F f : g.unique) { C cf=counts.get(f); if (cf==null) return; sums[8]+=(double)cf.reads[0]/f.length(); }
    }
    private static void scale(T t,Map<F,C> counts,double[] sums,boolean create) {
        C c=counts.get(t); if (c==null) return; long[] len=lengths(t);
        for (int k=0; k<6; k++) if (c.reads[k]!=0) c.tpm[k]=c.reads[k]*1e6/(len[k]*sums[k]);
        if (create && len[6]>0) c.tpm[6]=(float)(c.reads[6]*1e6/(len[6]*sums[6])); // Original SampleData stores ONLY bridge TPM in float.
        for (F f : t.features) { C cf=create?counter(counts,f):counts.get(f); if (cf!=null) cf.tpm[0]=cf.reads[0]*1e6/(f.length()*sums[7]); }
        if (t instanceof G g) for (F f : g.unique) { C cf=counts.get(f); if (cf!=null) cf.tpm[0]=cf.reads[0]*1e6/(f.length()*sums[8]); }
    }
    private static Metrics metrics(C c) {
        if (c==null) c=new C();
        return new Metrics(c.reads[0],c.tpm[0],c.reads[1],c.tpm[1],c.reads[2],c.tpm[2],c.reads[3],c.tpm[3],c.reads[4],c.tpm[4],c.reads[5],c.tpm[5],c.reads[6],c.tpm[6]);
    }
    private static List<Feature> features(TreeSet<F> features,Map<F,C> counts) {
        var result=new ArrayList<Feature>();
        for (F f : features) result.add(new Feature(f.type,f.strand,f.start,f.end,f.length(),counts.containsKey(f),metrics(counts.get(f))));
        return result;
    }
}

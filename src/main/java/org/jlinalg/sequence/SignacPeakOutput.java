/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Original Signac MACS output geometry and GenomicRanges 1.64.0 reduction on unknown-genome peak ranges. */
public final class SignacPeakOutput {
    private SignacPeakOutput() { }
    public record Peak(String chromosome,int start,int end,String strand,String name,String score,
                       String foldChange,String negativeLog10P,String negativeLog10Q,Integer relativeSummit) {
        public Peak {SignacFragments.identity(chromosome);SignacFragments.identity(name);if(start<1||end<start||!Set.of("+","-","*").contains(strand))throw new IllegalArgumentException("Require nonempty one-based closed peak geometry and source strand");for(String x:List.of(score,foldChange,negativeLog10P,negativeLog10Q))number(x);}
    }
    public record Group(String name,List<Peak> peaks) {public Group{SignacFragments.identity(name);peaks=List.copyOf(peaks);}}
    public record Reduced(String chromosome,int start,int end,String strand,List<String> groups) {public Reduced{groups=List.copyOf(groups);}}
    public record Combined(List<String> sequenceLevels,List<Reduced> peaks,List<String> warnings) {
        public Combined{sequenceLevels=List.copyOf(sequenceLevels);peaks=List.copyOf(peaks);warnings=List.copyOf(warnings);}
    }
    private record Indexed(Peak peak,String group,int index) { }
    /** Complete non-prefix fragment validation without accumulating per-barcode statistics. */
    public static SignacFragments.Scan validateFragments(Path path,long records,long decodedBytes,BooleanSupplier cancelled)throws IOException {
        return SignacFragments.scan(path,new SignacFragments.Limits(1,1,0,records,1,decodedBytes,4094,1),false,0,cancelled,fragment->{});
    }
    /** Empty output is a valid empty table. Coordinates convert from BED zero-based starts exactly once. */
    public static List<Peak> read(Path path,boolean broad,int maximumRows,long maximumBytes,BooleanSupplier cancelled)throws IOException {
        if(maximumRows<1||maximumBytes<1||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maximumBytes)throw new IOException("Require bounded regular original peak output");
        List<Peak> peaks=new ArrayList<>();
        try(var bytes=new Limited(Files.newInputStream(path),maximumBytes);var reader=new BufferedReader(new InputStreamReader(bytes,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            String line;while((line=line(reader))!=null){check(cancelled);if(peaks.size()>=maximumRows)throw new IOException("Peak row capacity exceeded");String[] f=line.strip().split("\\s+");if(f.length!=(broad?9:10))throw new IOException("Original peak output has the wrong complete column count");
                for(String value:f)if(value.indexOf('"')>=0||value.indexOf('\'')>=0||value.indexOf('#')>=0)throw new IOException("Peak identity/value outside unambiguous source read.table domain");
                try {int start=integer(f[1]),end=integer(f[2]);if(start<0||start==Integer.MAX_VALUE||end<=start)throw new IllegalArgumentException("Invalid BED peak coordinates");String strand=f[5].equals(".")?"*":f[5];peaks.add(new Peak(f[0],start+1,end,strand,f[3],f[4],f[6],f[7],f[8],broad?null:integer(f[9])));}catch(IllegalArgumentException e){throw new IOException("Invalid original peak range/statistic; no repair",e);}
            }
        }
        return List.copyOf(peaks);
    }
    /** Source Reduce(c,groups) retains sequence-level union order; reduction merges adjacent ranges by strand. */
    public static Combined combine(List<Group> groups,int maximumRows,BooleanSupplier cancelled) {
        if(groups.isEmpty())throw new IllegalArgumentException("Original reduce(NULL) has no method: all source groups were omitted/empty");
        if(maximumRows<1)throw new IllegalArgumentException("Require positive peak capacity");var levels=new ArrayList<String>();List<String> warnings=new ArrayList<>();List<Indexed> all=new ArrayList<>();boolean first=true;
        for(Group group:groups){check(cancelled);List<String> current=SignacSequenceOrder.levels(group.peaks.stream().map(Peak::chromosome).toList());
            if(!first){var common=new HashSet<>(levels);common.retainAll(current);List<String> x=levels.stream().filter(n->!common.contains(n)).toList(),y=current.stream().filter(n->!common.contains(n)).toList();
                if(!x.isEmpty()&&!y.isEmpty())warnings.add(common.isEmpty()?"The 2 combined objects have no sequence levels in common. (Use\n  suppressWarnings() to suppress this warning.)":"Each of the 2 combined objects has sequence levels not in the other:\n  - in 'x': "+String.join(", ",x)+"\n  - in 'y': "+String.join(", ",y)+"\n  Make sure to always combine/compare objects based on the same reference\n  genome (use suppressWarnings() to suppress this warning).");}
            first=false;for(String name:current)if(!levels.contains(name))levels.add(name);
            for(Peak peak:group.peaks){if(all.size()>=maximumRows)throw new IllegalArgumentException("Complete grouped peak row capacity exceeded");all.add(new Indexed(peak,group.name,all.size()));}
        }
        var order=new HashMap<String,Integer>();for(int i=0;i<levels.size();i++)order.put(levels.get(i),i);
        all.sort(Comparator.comparingInt((Indexed i)->order.get(i.peak.chromosome)).thenComparingInt(i->strand(i.peak.strand)).thenComparingInt(i->i.peak.start).thenComparingInt(i->i.peak.end).thenComparingInt(Indexed::index));
        List<Reduced> result=new ArrayList<>();Peak previous=null;int start=0,end=0;var labels=new LinkedHashSet<String>();
        for(var row:all){check(cancelled);Peak peak=row.peak;if(previous==null||!previous.chromosome.equals(peak.chromosome)||!previous.strand.equals(peak.strand)||peak.start>(long)end+1L){if(previous!=null)result.add(new Reduced(previous.chromosome,start,end,previous.strand,new ArrayList<>(labels)));previous=peak;start=peak.start;end=peak.end;labels.clear();}else end=Math.max(end,peak.end);labels.add(row.group);}
        if(previous!=null)result.add(new Reduced(previous.chromosome,start,end,previous.strand,new ArrayList<>(labels)));return new Combined(levels,result,warnings);
    }
    public static double number(String text){return switch(text){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->{if(!text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("Invalid source number");double n=Double.parseDouble(text);if(!Double.isFinite(n))throw new IllegalArgumentException("Nonfinite number must use explicit source kind");yield n;}};}
    private static int integer(String token){if(!token.matches("-?[0-9]+"))throw new IllegalArgumentException("Require source decimal coordinate/summit integer");return Integer.parseInt(token);}
    private static int strand(String value){return value.equals("+")?0:value.equals("-")?1:2;}
    private static String line(Reader reader)throws IOException{var b=new StringBuilder();int c;while((c=reader.read())>=0&&c!='\n'){if(b.length()>=65536)throw new IOException("Peak line capacity exceeded");b.append((char)c);}if(c<0&&b.isEmpty())return null;if(!b.isEmpty()&&b.charAt(b.length()-1)=='\r')b.setLength(b.length()-1);return b.toString();}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel!=null&&cancel.getAsBoolean())throw new java.util.concurrent.CancellationException("Peak output audit cancelled");}
    private static final class Limited extends FilterInputStream {private final long maximum;private long count;Limited(InputStream input,long maximum){super(input);this.maximum=maximum;}private void add(long n)throws IOException{if(n>0){count+=n;if(count>maximum)throw new IOException("Peak bytes exceed capacity");}}@Override public int read()throws IOException{int n=in.read();if(n>=0)add(1);return n;}@Override public int read(byte[] b,int o,int n)throws IOException{int read=in.read(b,o,n);add(read);return read;}}
}

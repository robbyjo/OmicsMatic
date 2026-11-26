/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics.contact;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Complete prepared sparse-map insulation, with explicit source-compatible numeric settings. */
public final class PreparedInsulation {
    private PreparedInsulation() {}
    public record Settings(long binWidth, List<Long> windowsBp, int ignoreDiagonals,
                           int minDistanceBad, double minFractionValid, double strengthThreshold,
                           int maxBins, long maxPixelRows, long maxInputBytes) {
        public Settings {
            Objects.requireNonNull(windowsBp, "windowsBp"); windowsBp=List.copyOf(windowsBp);
            if(binWidth<1 || windowsBp.isEmpty() || windowsBp.size()>64 || new HashSet<>(windowsBp).size()!=windowsBp.size()
                || ignoreDiagonals<0 || minDistanceBad<0 || !Double.isFinite(minFractionValid)
                || minFractionValid<0 || minFractionValid>1 || !Double.isFinite(strengthThreshold)
                || strengthThreshold<0 || maxBins<1 || maxPixelRows<1 || maxInputBytes<1)
                throw new IllegalArgumentException("Explicit valid settings and complete-population capacities required");
            for(long window:windowsBp) if(window<binWidth || window%binWidth!=0 || window/binWidth>Integer.MAX_VALUE)
                throw new IllegalArgumentException("Windows must be distinct positive integer multiples of binWidth");
        }
    }
    public record InputBin(int id,String chrom,long start,long end,String region,double weight) {}
    public record Row(InputBin bin,long windowBp,int sourceOutputOrdinal,double score,double log2Score,
                      double nValidPixels,double sumBalanced,double sumCounts,boolean badBin,
                      int distanceToBad,boolean boundaryEligible,double strength,boolean isBoundary) {}
    public record Window(long windowBp,List<Row> rows,double minimumValidPixels) {
        public Window { rows=List.copyOf(rows); }
    }
    public record Result(List<Window> windows,long pixelRecords,long cisPixelRecords,long outsideRegionRecords,
                         int bins,int regions,int badBins,long decodedInputBytes) {
        public Result { windows=List.copyOf(windows); }
    }
    private static final Pattern INTEGER=Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern NUMBER=Pattern.compile("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final class Region {
        final String name,chrom; final int first; final List<InputBin> bins=new ArrayList<>();
        final List<CooltoolsDiamond.Pixel> pixels=new ArrayList<>();
        Region(InputBin bin){name=bin.region();chrom=bin.chrom();first=bin.id();}
    }

    /**
     * Bin header: bin_id,chrom,start,end,region,weight; pixel header: bin1_id,bin2_id,count (tabs).
     * Literal UTF-8, optional header BOM, CR/LF accepted. No row is dropped or consolidated.
     * Each region is one contiguous sorted bin block. Windows use median-normalized balanced scores.
     * Pixels outside a single region are validated, retained by the caller, and counted separately.
     */
    public static Result calculate(Path binFile,Path pixelFile,Settings settings,Runnable check)throws IOException {
        Objects.requireNonNull(settings,"settings");Objects.requireNonNull(check,"check");
        List<InputBin> bins=new ArrayList<>();List<Region> regions=new ArrayList<>();
        List<Integer> binRegion=new ArrayList<>();Set<String> seen=new HashSet<>();long decoded;int badBins=0;
        try(Table in=new Table(binFile,settings.maxInputBytes(),check)) {
            header(in,"bin_id\tchrom\tstart\tend\tregion\tweight");String line;
            while((line=in.line())!=null) {
                check.run();require(bins.size()<settings.maxBins(),"Complete bin capacity exceeded");
                String[] f=cells(line,6);long id=integer(f[0]),start=integer(f[2]),end=integer(f[3]);
                require(id==bins.size() && end>start && end-start<=settings.binWidth(),"Require contiguous zero-based IDs and positive bins no longer than binWidth");
                require(!f[1].isBlank()&&!f[4].isBlank(),"Explicit literal chrom and region required");
                double weight=f[5].equals("NaN")?Double.NaN:number(f[5]);
                require(Double.isNaN(weight)||weight>=0,"Nonnegative finite weight or literal NaN required");
                InputBin bin=new InputBin((int)id,f[1],start,end,f[4],weight);Region region;
                if(regions.isEmpty()||!regions.get(regions.size()-1).name.equals(bin.region())) {
                    require(seen.add(bin.region()),"Region occurs in multiple disjoint input blocks");
                    region=new Region(bin);regions.add(region);
                } else region=regions.get(regions.size()-1);
                require(region.chrom.equals(bin.chrom()),"A region spans different chromosome labels");
                if(!region.bins.isEmpty()) {
                    InputBin previous=region.bins.get(region.bins.size()-1);
                    require(previous.end()-previous.start()==settings.binWidth()&&previous.end()==start,
                            "Require sorted adjacent full-width interior bins; a shorter bin is allowed only at a region end");
                }
                region.bins.add(bin);bins.add(bin);binRegion.add(regions.size()-1);
                if(Double.isNaN(weight))badBins++;
            }
            require(!bins.isEmpty(),"Nonempty complete bin table required");decoded=in.bytes;
        }
        // Distinct views with overlapping coordinates would duplicate an axis. Refuse rather than repair.
        Map<String,List<Region>> chromosomes=new HashMap<>();
        for(Region region:regions)chromosomes.computeIfAbsent(region.chrom,k->new ArrayList<>()).add(region);
        for(List<Region> axis:chromosomes.values()) {
            axis.sort(Comparator.comparingLong(r->r.bins.get(0).start()));
            for(int i=1;i<axis.size();i++) {
                Region left=axis.get(i-1),right=axis.get(i);
                require(left.bins.get(left.bins.size()-1).end()<=right.bins.get(0).start(),"Overlapping prepared regions on one chromosome are not supported");
            }
        }
        long pixels=0,cis=0,outside=0;
        require(decoded<settings.maxInputBytes(),"Complete bin and pixel input byte capacity exceeded");
        try(Table in=new Table(pixelFile,settings.maxInputBytes()-decoded,check)) {
            header(in,"bin1_id\tbin2_id\tcount");String line;
            while((line=in.line())!=null) {
                check.run();require(pixels<settings.maxPixelRows(),"Complete pixel row capacity exceeded");pixels++;
                String[] f=cells(line,3);long first=integer(f[0]),second=integer(f[1]);double count=number(f[2]);
                require(first<=second&&second<bins.size()&&count>=0,"Invalid global upper-triangle pixel axis/count at row "+pixels);
                int a=(int)first,b=(int)second;
                if(binRegion.get(a).equals(binRegion.get(b))) {
                    Region region=regions.get(binRegion.get(a));
                    region.pixels.add(new CooltoolsDiamond.Pixel(a-region.first,b-region.first,count));cis++;
                } else outside++;
            }
            decoded=Math.addExact(decoded,in.bytes);
        }
        List<Window> windows=new ArrayList<>();
        for(long windowBp:settings.windowsBp()) {
            check.run();double[] scores=new double[bins.size()],logs=new double[bins.size()],valid=new double[bins.size()];
            double[] balanced=new double[bins.size()],raw=new double[bins.size()];int[] distance=new int[bins.size()];
            List<CooltoolsBoundaries.Bin> boundaryBins=new ArrayList<>();
            for(Region region:regions) {
                check.run();double[] weights=new double[region.bins.size()];boolean[] bad=new boolean[weights.length];
                for(int i=0;i<weights.length;i++){weights[i]=region.bins.get(i).weight();bad[i]=Double.isNaN(weights[i]);}
                Iterable<CooltoolsDiamond.Pixel> cancellable=()->new Iterator<>() {
                    final Iterator<CooltoolsDiamond.Pixel> input=region.pixels.iterator();
                    public boolean hasNext(){check.run();return input.hasNext();}
                    public CooltoolsDiamond.Pixel next(){check.run();return input.next();}
                };
                var diamond=CooltoolsDiamond.calculate(weights.length,weights,cancellable,Math.toIntExact(windowBp/settings.binWidth()),settings.ignoreDiagonals(),true);
                double[] score=diamond.score(),log=CooltoolsDiamond.log2Track(score),n=diamond.nValidPixels(),sum=diamond.sumBalanced(),count=diamond.sumCounts();
                int[] distances=CooltoolsBoundaries.distanceToBad(bad);
                for(int i=0;i<weights.length;i++) {
                    int global=region.first+i;check.run();
                    if(settings.minDistanceBad()>0&&distances[i]<settings.minDistanceBad())log[i]=Double.NaN;
                    scores[global]=score[i];logs[global]=log[i];valid[global]=n[i];balanced[global]=sum[i];raw[global]=count[i];distance[global]=distances[i];
                    InputBin bin=bins.get(global);boundaryBins.add(new CooltoolsBoundaries.Bin(bin.region(),bin.start(),log[i],n[i],bad[i]));
                }
            }
            check.run();var boundaries=CooltoolsBoundaries.call(boundaryBins,settings.minFractionValid(),settings.minDistanceBad(),settings.strengthThreshold());
            int[] order=boundaries.sourceOutputRowOrder();double[] strengths=boundaries.strength();
            boolean[] eligible=boundaries.eligible(),called=boundaries.isBoundary();List<Row> rows=new ArrayList<>();
            for(int ordinal=0;ordinal<order.length;ordinal++) {
                check.run();int i=order[ordinal];InputBin bin=bins.get(i);
                rows.add(new Row(bin,windowBp,ordinal,scores[i],logs[i],valid[i],balanced[i],raw[i],Double.isNaN(bin.weight()),distance[i],eligible[i],strengths[i],called[i]));
            }
            windows.add(new Window(windowBp,rows,boundaries.minimumValidPixels()));
        }
        check.run();require(pixels==cis+outside,"Pixel accounting differs");
        return new Result(windows,pixels,cis,outside,bins.size(),regions.size(),badBins,decoded);
    }
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
    private static long integer(String value)throws IOException {
        require(INTEGER.matcher(value).matches(),"Require nonnegative literal integer axis/coordinate");
        try{return Long.parseLong(value);}catch(NumberFormatException e){throw new IOException("Integer axis/coordinate overflow",e);}
    }
    private static double number(String value)throws IOException {
        require(NUMBER.matcher(value).matches(),"Require explicit finite decimal numeric value");double result;
        try{result=Double.parseDouble(value);}catch(NumberFormatException e){throw new IOException("Invalid decimal numeric value",e);}
        require(Double.isFinite(result),"Nonfinite numeric input is not permitted here");return result;
    }
    private static String[] cells(String value,int count)throws IOException {
        require(!value.isEmpty(),"Blank record is not dropped");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);require(c=='\t'||c>=32&&c!=127,"Control character in literal input");}
        String[] cells=value.split("\t",-1);require(cells.length==count,"Different literal input column count");return cells;
    }
    private static void header(Table table,String expected)throws IOException {
        String first=table.line();require(first!=null,"Missing complete input header");
        if(first.startsWith("\ufeff"))first=first.substring(1);require(first.equals(expected),"Require exact typed input header: "+expected);
    }
    private static final class Table implements AutoCloseable {
        final PushbackReader input;final Runnable check;long bytes;
        Table(Path path,long maximum,Runnable check)throws IOException {
            this.check=check;InputStream raw=Files.newInputStream(path);InputStream bounded=new FilterInputStream(raw) {
                private void add(int count)throws IOException {if(count>0){check.run();require(count<=maximum-bytes,"Complete input byte capacity exceeded");bytes+=count;}}
                @Override public int read()throws IOException {int value=in.read();if(value>=0)add(1);return value;}
                @Override public int read(byte[] data,int offset,int length)throws IOException {int count=in.read(data,offset,length);add(count);return count;}
            };
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            input=new PushbackReader(new BufferedReader(new InputStreamReader(bounded,decoder),65536),1);
        }
        String line()throws IOException {
            check.run();StringBuilder line=new StringBuilder();int value;
            while((value=input.read())!=-1) {
                if(value=='\n')return line.toString();
                if(value=='\r'){int next=input.read();if(next!=-1&&next!='\n')input.unread(next);return line.toString();}
                require(line.length()<1048576,"Typed record exceeds one MiB characters");line.append((char)value);
            }
            return line.isEmpty()?null:line.toString();
        }
        @Override public void close()throws IOException {input.close();}
    }
}

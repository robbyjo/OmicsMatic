/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.util.SortingCollection;
import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Bounded-memory canonicalization of sparse gene-by-cell Matrix Market counts and 10x ID tables. */
public final class SparseCounts {
    private SparseCounts() { }
    public enum Kind { RAW_UMI, NONNEGATIVE_ESTIMATE }
    public record Feature(String id,String name,String type) { }
    public record Summary(List<Feature> features,List<String> cells,long inputEntries,long outputEntries,
                          long selectedInputEntries,long zeroInputEntries,int excludedFeatures,
                          double[] cellTotals,int[] detectedGenes) {
        public Summary { features=List.copyOf(features);cells=List.copyOf(cells);cellTotals=cellTotals.clone();detectedGenes=detectedGenes.clone(); }
        @Override public double[] cellTotals(){return cellTotals.clone();}
        @Override public int[] detectedGenes(){return detectedGenes.clone();}
    }
    private record Entry(int cell,int gene,double value) { }
    private static final double MAX_EXACT_INTEGER=9_007_199_254_740_991d;
    /** Writes matrix.mtx/features.tsv/barcodes.tsv in a new output directory. Duplicate coordinates
     * are summed before totals/detected genes are calculated. No cell QC or library pooling occurs.
     * featureType is applied only to typed three-column feature tables; null retains all features.
     */
    public static Summary canonicalize(Path matrix,Path featureFile,Path cellFile,Path output,
                                       Kind kind,String featureType,int maximumInMemory) throws IOException {
        Objects.requireNonNull(kind);if(maximumInMemory<1)throw new IllegalArgumentException("Positive sparse sort memory capacity required");
        var all=features(featureFile);var cells=cells(cellFile);var selected=new ArrayList<Feature>();int[] remap=new int[all.size()];Arrays.fill(remap,-1);
        for(int i=0;i<all.size();i++)if(featureType==null||all.get(i).type().isEmpty()||all.get(i).type().equals(featureType)){remap[i]=selected.size();selected.add(all.get(i));}
        if(selected.isEmpty())throw new IOException("No features match the explicitly selected feature type");
        Files.createDirectory(output);Path spill=Files.createDirectory(output.resolve("sort"));
        var entries=SortingCollection.newInstance(Entry.class,new Codec(),Comparator.comparingInt(Entry::cell).thenComparingInt(Entry::gene),maximumInMemory,spill);
        long inputEntries=0,selectedEntries=0,zeros=0,declared=-1;
        try {
            try(var in=reader(matrix)){
                String header=in.readLine();
                if(header==null||!header.matches("(?i)%%MatrixMarket matrix coordinate (?:integer|real) general"))throw new IOException("Require Matrix Market coordinate integer|real general, genes by cells");
                String line=next(in);if(line==null)throw new IOException("Missing Matrix Market dimensions");String[] d=line.trim().split("\\s+");
                if(d.length!=3)throw new IOException("Require three Matrix Market dimensions");
                int rows=positive(d[0]),cols=positive(d[1]);declared=Long.parseLong(d[2]);
                if(rows!=all.size()||cols!=cells.size()||declared<0||declared>500_000_000L)throw new IOException("Matrix dimensions/entry count differ from feature/barcode tables or exceed the 500-million-entry bound");
                while((line=next(in))!=null){
                    if(++inputEntries>declared)throw new IOException("Matrix contains more entries than declared");
                    if(line.length()>2_000_000)throw new IOException("Excessive Matrix Market row length");String[] f=line.trim().split("\\s+");if(f.length!=3)throw new IOException("Require gene, cell and count on each sparse row");
                    int gene=positive(f[0])-1,cell=positive(f[1])-1;if(gene>=rows||cell>=cols)throw new IOException("Sparse coordinate is outside the declared gene/cell bounds");double value=Double.parseDouble(f[2]);validate(value,kind);
                    if(value==0){zeros++;continue;}if(remap[gene]<0)continue;selectedEntries++;entries.add(new Entry(cell,remap[gene],value));
                }
            }
            if(inputEntries!=declared)throw new IOException("Matrix contains fewer entries than declared");entries.doneAdding();
            double[] totals=new double[cells.size()],compensation=new double[cells.size()];int[] detected=new int[cells.size()];long unique=0;
            Path body=output.resolve("matrix-body.tmp");
            try(var it=entries.iterator();var out=Files.newBufferedWriter(body,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
                Entry pending=null;double sum=0,c=0;
                while(it.hasNext()){
                    Entry e=it.next();
                    if(pending!=null&&(e.cell()!=pending.cell()||e.gene()!=pending.gene())){write(out,pending,sum,kind,totals,compensation,detected);unique++;sum=0;c=0;}
                    double adjusted=e.value()-c,newSum=sum+adjusted;c=(newSum-sum)-adjusted;sum=newSum;pending=e;
                }
                if(pending!=null){write(out,pending,sum,kind,totals,compensation,detected);unique++;}
            }
            try(var target=Files.newOutputStream(output.resolve("matrix.mtx"),StandardOpenOption.CREATE_NEW)){
                target.write(("%%MatrixMarket matrix coordinate "+(kind==Kind.RAW_UMI?"integer":"real")+" general\n% Canonical gene-by-cell counts; duplicate coordinates summed\n"+selected.size()+" "+cells.size()+" "+unique+"\n").getBytes(StandardCharsets.US_ASCII));Files.copy(body,target);
            }
            Files.delete(body);
            try(var out=Files.newBufferedWriter(output.resolve("features.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
                for(var f:selected)out.write(f.id()+"\t"+f.name()+(f.type().isEmpty()?"":"\t"+f.type())+"\n");
            }
            try(var out=Files.newBufferedWriter(output.resolve("barcodes.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){for(String cell:cells)out.write(cell+"\n");}
            return new Summary(selected,cells,inputEntries,unique,selectedEntries,zeros,all.size()-selected.size(),totals,detected);
        }catch(IllegalArgumentException|ArithmeticException failure){throw new IOException("Malformed count-matrix numeric value: "+failure.getMessage(),failure);}
        finally{entries.cleanup();Files.deleteIfExists(spill);}
    }
    private static void write(BufferedWriter out,Entry e,double value,Kind kind,double[] totals,double[] compensation,int[] detected)throws IOException{
        validate(value,kind);double adjusted=value-compensation[e.cell()],next=totals[e.cell()]+adjusted;compensation[e.cell()]=(next-totals[e.cell()])-adjusted;totals[e.cell()]=next;validate(next,kind);detected[e.cell()]=Math.addExact(detected[e.cell()],1);
        out.write((e.gene()+1)+" "+(e.cell()+1)+" "+(kind==Kind.RAW_UMI?Long.toString((long)value):Double.toString(value))+"\n");
    }
    private static void validate(double value,Kind kind)throws IOException{
        if(!Double.isFinite(value)||value<0)throw new IOException("Counts/estimates must be finite and nonnegative");
        if(kind==Kind.RAW_UMI&&(value!=Math.rint(value)||value>MAX_EXACT_INTEGER))throw new IOException("Raw UMI counts and cell totals require exact nonnegative integers <=2^53-1; fractional estimates must be explicitly declared");
    }
    private static int positive(String value)throws IOException{int n=Integer.parseInt(value);if(n<1)throw new IOException("Matrix dimensions/coordinates must be positive integers");return n;}
    private static String next(BufferedReader input)throws IOException{String line;while((line=input.readLine())!=null){if(!line.isBlank()&&!line.stripLeading().startsWith("%"))return line;}return null;}
    private static BufferedReader reader(Path path)throws IOException{
        InputStream raw=Files.newInputStream(path);
        try{return new BufferedReader(new InputStreamReader(path.toString().toLowerCase(Locale.ROOT).endsWith(".gz")?new GZIPInputStream(raw):raw,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
        catch(IOException|RuntimeException failure){raw.close();throw failure;}
    }
    private static List<Feature> features(Path file)throws IOException{
        var list=new ArrayList<Feature>();var ids=new HashSet<String>();int fields=0;
        try(var in=reader(file)){String line;while((line=in.readLine())!=null){String[] f=line.split("\t",-1);if(fields==0)fields=f.length;if(fields<1||fields>3||f.length!=fields||!id(f[0])||!ids.add(f[0])||f.length>1&&f[1].isEmpty()||f.length>2&&f[2].isEmpty())throw new IOException("Features require unique nonempty IDs and consistent one/two/three-column 10x rows, without a header");list.add(new Feature(f[0],f.length>1?f[1]:f[0],f.length>2?f[2]:""));if(list.size()>2_000_000)throw new IOException("Feature table exceeds two million rows");}}
        if(list.isEmpty())throw new IOException("Feature table is empty");return list;
    }
    private static List<String> cells(Path file)throws IOException{
        var list=new ArrayList<String>();var seen=new HashSet<String>();
        try(var in=reader(file)){String line;while((line=in.readLine())!=null){if(!id(line)||!seen.add(line))throw new IOException("Barcodes require one unique nonempty ID per line, without a header");list.add(line);if(list.size()>10_000_000)throw new IOException("Barcode table exceeds ten million rows; process independent libraries separately");}}
        if(list.isEmpty())throw new IOException("Barcode table is empty");return list;
    }
    private static boolean id(String text){return !text.isEmpty()&&text.chars().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c));}
    private static final class Codec implements SortingCollection.Codec<Entry>{
        private DataInputStream input;private DataOutputStream output;
        @Override public void setInputStream(InputStream stream){input=new DataInputStream(stream);}
        @Override public void setOutputStream(OutputStream stream){output=new DataOutputStream(stream);}
        @Override public void encode(Entry e){try{output.writeInt(e.cell());output.writeInt(e.gene());output.writeDouble(e.value());}catch(IOException failure){throw new UncheckedIOException(failure);}}
        @Override public Entry decode(){try{int first=input.read();if(first<0)return null;int cell=first<<24|input.readUnsignedByte()<<16|input.readUnsignedByte()<<8|input.readUnsignedByte();return new Entry(cell,input.readInt(),input.readDouble());}catch(IOException failure){throw new UncheckedIOException(failure);}}
        @Override public Codec clone(){return new Codec();}
    }
}

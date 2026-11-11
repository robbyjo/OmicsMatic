/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Bounded integer Matrix Market intake and normalized sparse exchange for Signac LSI. */
public final class SignacMatrixIO {
    private SignacMatrixIO() { }
    public record Limits(int features,int cells,int inputEntries,long inputBytes,int lineCharacters) {
        public Limits {if(features<1||cells<1||inputEntries<0||inputEntries>20_000_000||inputBytes<1||lineCharacters<32||lineCharacters>1_000_000)throw new IllegalArgumentException("Invalid Signac Matrix Market admission bounds (at most20 million input entries)");}
        public static Limits defaults(){return new Limits(2_000_000,2_000_000,10_000_000,2_000_000_000L,16384);}
    }
    public record Intake(SignacNormalization.Counts counts,int inputEntries,int duplicateCoordinates,int explicitZeroEntries) { }
    private record Entry(int cell,int row,long value) { }
    public static Intake readCounts(Path matrix,Path features,Path cells,Limits limits,BooleanSupplier cancelled)throws IOException {
        Objects.requireNonNull(limits);check(cancelled);
        List<String> f=identities(features,limits.features(),limits,cancelled),c=identities(cells,limits.cells(),limits,cancelled);
        long size=Files.size(matrix);if(size>limits.inputBytes())throw new IOException("Count matrix exceeds input byte bound");
        Entry[] entries;int zeros=0;
        try(var reader=reader(matrix,limits.inputBytes())){
            String header=line(reader,limits.lineCharacters());
            if(header==null||!header.matches("(?i)%%MatrixMarket matrix coordinate integer general"))throw new IOException("Require uncompressed Matrix Market coordinate integer general, features by cells");
            String dimensions=next(reader,limits.lineCharacters(),cancelled);if(dimensions==null)throw new IOException("Missing Matrix Market dimensions");
            String[] dim=dimensions.strip().split("\\s+");if(dim.length!=3)throw new IOException("Require exactly three Matrix Market dimensions");
            int rows=integer(dim[0],1,limits.features()),cols=integer(dim[1],1,limits.cells()),n=integer(dim[2],0,limits.inputEntries());
            if(rows!=f.size()||cols!=c.size())throw new IOException("Matrix dimensions disagree with explicit identity files");
            entries=new Entry[n];String raw;int k=0;
            while((raw=next(reader,limits.lineCharacters(),cancelled))!=null){
                if((k&4095)==0)check(cancelled);if(k>=n)throw new IOException("More sparse entries than declared");
                String[] fields=raw.strip().split("\\s+");if(fields.length!=3)throw new IOException("Require row, column and integer count");
                int row=integer(fields[0],1,rows)-1,cell=integer(fields[1],1,cols)-1;long value;
                try{if(!fields[2].matches("[0-9]+"))throw new NumberFormatException();value=Long.parseLong(fields[2]);}catch(NumberFormatException bad){throw new IOException("Require nonnegative integer count tokens",bad);}
                if(value>9_007_199_254_740_991L)throw new IOException("Count exceeds exact integer domain");if(value==0)zeros++;
                entries[k++]=new Entry(cell,row,value);
            }
            if(k!=n)throw new IOException("Fewer sparse entries than declared");
        }
        if(Files.size(matrix)!=size)throw new IOException("Input matrix size changed during intake");
        final int[] comparisons={0};Arrays.sort(entries,(a,b)->{if((++comparisons[0]&4095)==0)check(cancelled);int byCell=Integer.compare(a.cell(),b.cell());return byCell!=0?byCell:Integer.compare(a.row(),b.row());});
        int unique=0;for(int k=0;k<entries.length;k++){if((k&4095)==0)check(cancelled);if(k==0||entries[k].cell()!=entries[k-1].cell()||entries[k].row()!=entries[k-1].row())unique++;}
        int[] p=new int[c.size()+1],indices=new int[unique];double[] values=new double[unique];int at=0;
        for(int k=0;k<entries.length;){
            if((k&4095)==0)check(cancelled);Entry first=entries[k++];long sum=first.value();
            while(k<entries.length&&entries[k].cell()==first.cell()&&entries[k].row()==first.row()){
                if((k&4095)==0)check(cancelled);if(sum>9_007_199_254_740_991L-entries[k].value())throw new IOException("Duplicate-coordinate sum exceeds exact integer domain");sum+=entries[k++].value();
            }
            indices[at]=first.row();values[at++]=sum;p[first.cell()+1]++;
        }
        for(int cell=0;cell<c.size();cell++){if((cell&4095)==0)check(cancelled);p[cell+1]+=p[cell];}
        try{return new Intake(new SignacNormalization.Counts(f,c,p,indices,values,new SignacNormalization.Limits(limits.features(),limits.cells(),limits.inputEntries()),cancelled),entries.length,entries.length-unique,zeros);}
        catch(IllegalArgumentException error){throw new IOException("Count admission failed: "+error.getMessage(),error);}
    }
    /** Immutable selected matrix, in metadata/selection order, still compressed sparse columns. */
    public record Selected(List<String> features,List<String> cells,int[] pointers,int[] rows,double[] values) {
        public Selected{
            features=List.copyOf(features);cells=List.copyOf(cells);pointers=pointers.clone();rows=rows.clone();values=values.clone();
            if(features.isEmpty()||cells.isEmpty()||pointers.length!=cells.size()+1||rows.length!=values.length||pointers[0]!=0||pointers[cells.size()]!=values.length
                    ||new java.util.HashSet<>(features).size()!=features.size()||new java.util.HashSet<>(cells).size()!=cells.size())throw new IllegalArgumentException("Invalid selected sparse matrix dimensions/identities");
            for(var ids:List.of(features,cells))for(String id:ids)if(id.isBlank()||id.length()>4096||id.chars().anyMatch(c->c=='\t'||c=='\r'||c=='\n'||c==0))throw new IllegalArgumentException("Invalid selected identity");
            for(int cell=0;cell<cells.size();cell++){
                if(pointers[cell]<0||pointers[cell+1]<pointers[cell]||pointers[cell+1]>values.length)throw new IllegalArgumentException("Invalid selected CSC pointers");int previous=-1;
                for(int k=pointers[cell];k<pointers[cell+1];k++){if(rows[k]<=previous||rows[k]>=features.size()||!Double.isFinite(values[k])||values[k]<0)throw new IllegalArgumentException("Invalid selected sparse coordinate/value");previous=rows[k];}
            }
        }
        @Override public int[] pointers(){return pointers.clone();}@Override public int[] rows(){return rows.clone();}@Override public double[] values(){return values.clone();}
    }
    public static Selected select(SignacNormalization.NormalizedMatrix normalized,List<String> selected,BooleanSupplier cancelled)throws IOException {
        check(cancelled);Map<String,Integer> index=new HashMap<>();for(int i=0;i<normalized.features().size();i++){if((i&4095)==0)check(cancelled);index.put(normalized.features().get(i),i);}
        int[] remap=new int[normalized.features().size()];Arrays.fill(remap,-1);
        for(int i=0;i<selected.size();i++){check(cancelled);Integer old=index.get(selected.get(i));if(old==null||remap[old]>=0)throw new IOException("Selected features must be unique and known");remap[old]=i;}
        if(selected.isEmpty())throw new IOException("No selected features; do not silently relax the cutoff");
        int[] oldP=normalized.columnPointers(),oldRows=normalized.rowIndices();double[] oldValues=normalized.values();int n=0;
        for(int k=0;k<oldRows.length;k++){if((k&4095)==0)check(cancelled);if(remap[oldRows[k]]>=0)n++;}
        int[] p=new int[oldP.length],rows=new int[n];double[] values=new double[n];int at=0;
        for(int cell=0;cell<normalized.cells().size();cell++){
            check(cancelled);int start=at;
            for(int k=oldP[cell];k<oldP[cell+1];k++){
                if((k&4095)==0)check(cancelled);if(remap[oldRows[k]]>=0){if(!Double.isFinite(oldValues[k]))throw new IOException("Nonfinite normalization: skip LSI and inspect scale/IDF and QC warnings");rows[at]=remap[oldRows[k]];values[at++]=oldValues[k];}
            }
            // Sort paired values by the new feature order without a dense row-by-cell matrix.
            Entry[] order=new Entry[at-start];for(int j=0;j<order.length;j++)order[j]=new Entry(0,rows[start+j],Double.doubleToRawLongBits(values[start+j]));
            Arrays.sort(order,(a,b)->{check(cancelled);return Integer.compare(a.row(),b.row());});
            for(int j=0;j<order.length;j++){if((j&4095)==0)check(cancelled);rows[start+j]=order[j].row();values[start+j]=Double.longBitsToDouble(order[j].value());}p[cell+1]=at;
        }
        return new Selected(selected,normalized.cells(),p,rows,values);
    }
    /** Writes only into NEW files; no directory creation or replacement. */
    public static void write(Selected matrix,Path values,Path features,Path cells,BooleanSupplier cancelled)throws IOException {
        check(cancelled);
        try(var out=Files.newBufferedWriter(values,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("%%MatrixMarket matrix coordinate real general\n% Native Signac normalization; explicit zeros retained\n"+matrix.features.size()+" "+matrix.cells.size()+" "+matrix.values.length+"\n");
            for(int cell=0;cell<matrix.cells.size();cell++)for(int k=matrix.pointers[cell];k<matrix.pointers[cell+1];k++){if((k&4095)==0)check(cancelled);out.write((matrix.rows[k]+1)+" "+(cell+1)+" "+Double.toString(matrix.values[k])+"\n");}
        }
        try(var out=Files.newBufferedWriter(features,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){for(String id:matrix.features){check(cancelled);out.write(id+"\n");}}
        try(var out=Files.newBufferedWriter(cells,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){for(String id:matrix.cells){check(cancelled);out.write(id+"\n");}}
    }
    private static List<String> identities(Path path,int maximum,Limits limits,BooleanSupplier cancelled)throws IOException {
        if(Files.size(path)>limits.inputBytes())throw new IOException("Identity file exceeds byte bound");List<String> result=new ArrayList<>();
        try(var in=reader(path,limits.inputBytes())){String line;while((line=line(in,Math.min(limits.lineCharacters(),4096)))!=null){check(cancelled);if(result.size()>=maximum)throw new IOException("Too many identities");result.add(line);}}
        return result;
    }
    private static BufferedReader reader(Path path,long maximum)throws IOException{
        var bounded=new java.io.FilterInputStream(Files.newInputStream(path)){
            private long bytes;
            private void count(int n)throws IOException{if(n>0){bytes+=n;if(bytes>maximum)throw new IOException("Input grew past the configured byte bound");}}
            @Override public int read()throws IOException{int value=in.read();count(value<0?0:1);return value;}
            @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);count(n);return n;}
        };
        return new BufferedReader(new java.io.InputStreamReader(bounded,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }
    private static String line(BufferedReader in,int maximum)throws IOException{StringBuilder b=new StringBuilder();int c;while((c=in.read())!=-1){if(c=='\n')return b.toString();if(c=='\r'){in.mark(1);if(in.read()!='\n')in.reset();return b.toString();}if(b.length()>=maximum)throw new IOException("Line exceeds configured character bound");b.append((char)c);}return b.isEmpty()?null:b.toString();}
    private static String next(BufferedReader in,int maximum,BooleanSupplier cancelled)throws IOException{String line;while((line=line(in,maximum))!=null){check(cancelled);if(!line.isBlank()&&!line.startsWith("%"))return line;}return null;}
    private static int integer(String token,int min,int max)throws IOException{try{if(!token.matches("[0-9]+"))throw new NumberFormatException();long value=Long.parseLong(token);if(value<min||value>max)throw new NumberFormatException();return(int)value;}catch(NumberFormatException error){throw new IOException("Sparse dimension/index exceeds declared bounds",error);}}
    private static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||Objects.requireNonNull(cancelled).getAsBoolean())throw new CancellationException("Signac sparse exchange cancelled; retain any partial output");}
}

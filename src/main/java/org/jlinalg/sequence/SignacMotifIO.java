/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded UTF-8 peaks-by-motifs Matrix Market intake, including pattern matrices. */
public final class SignacMotifIO {
    private SignacMotifIO() { }
    public record Limits(int peaks,int motifs,int entries,long bytes,int lineCharacters) {
        public Limits {if(peaks<1||peaks>2_000_000||motifs<1||motifs>1_000_000||entries<0||entries>20_000_000||bytes<1||lineCharacters<64||lineCharacters>1_000_000)throw new IllegalArgumentException("Invalid motif intake capacity");}
        public static Limits defaults(){return new Limits(2_000_000,1_000_000,10_000_000,2_000_000_000L,16384);}
    }
    public record Input(SignacMotifEnrichment.Matrix matrix,String field,int duplicateCoordinates) { }
    private record Entry(int column,int row,double value) { }
    public static Input read(Path matrix,Path peakIds,Path motifLabels,Limits limits,BooleanSupplier cancelled)throws IOException {
        Objects.requireNonNull(limits);Objects.requireNonNull(cancelled);List<String> peaks=SignacLinkIO.ids(peakIds,limits.peaks,new SignacLinkIO.Limits(limits.peaks,2,limits.entries,limits.bytes,limits.lineCharacters,1),cancelled);List<String> motifs=new ArrayList<>(),names=new ArrayList<>();
        try(var in=reader(motifLabels,limits)){
            if(!"motif_id\tmotif_name".equals(line(in,limits,cancelled)))throw new IOException("Require motif_id/motif_name TSV header");String row;
            while((row=line(in,limits,cancelled))!=null){String[] values=row.split("\t",-1);if(values.length!=2||motifs.size()>=limits.motifs)throw new IOException("Ragged/excessive motif labels");SignacFragments.identity(values[0]);SignacFragments.identity(values[1]);motifs.add(values[0]);names.add(values[1]);}
        }
        Entry[] entries;String field;int nr,nc;
        try(var in=reader(matrix,limits)){
            String header=line(in,limits,cancelled);String[] h=header==null?new String[0]:header.trim().split("\\s+");
            if(h.length!=5||!h[0].equalsIgnoreCase("%%MatrixMarket")||!h[1].equalsIgnoreCase("matrix")||!h[2].equalsIgnoreCase("coordinate")||!h[4].equalsIgnoreCase("general")||!Set.of("integer","real","pattern").contains(h[3].toLowerCase(Locale.ROOT)))throw new IOException("Require general coordinate integer, real or pattern motif Matrix Market");field=h[3].toLowerCase(Locale.ROOT);
            String shape=next(in,limits,cancelled);String[] dims=split(shape,3);nr=integer(dims[0],1,limits.peaks);nc=integer(dims[1],0,limits.motifs);int nnz=integer(dims[2],0,limits.entries);if(nr!=peaks.size()||nc!=motifs.size())throw new IOException("Motif matrix dimensions disagree with peak/motif IDs");entries=new Entry[nnz];int count=0;String row;
            while((row=next(in,limits,cancelled))!=null){if(count==nnz)throw new IOException("Excess motif coordinate entries");String[] values=split(row,field.equals("pattern")?2:3);double value=1;
                if(!field.equals("pattern"))try{value=Double.parseDouble(values[2]);if(!Double.isFinite(value)||value<0||value!=Math.floor(value)||value>SignacFragments.MAX_EXACT||field.equals("integer")&&!values[2].matches("[+]?[0-9]+"))throw new NumberFormatException();}catch(NumberFormatException error){throw new IOException("Motif values require nonnegative exact integer counts",error);}
                entries[count++]=new Entry(integer(values[1],1,nc)-1,integer(values[0],1,nr)-1,value);
            }
            if(count!=nnz)throw new IOException("Too few motif coordinate entries");
        }
        int[] comparisons={0};Arrays.sort(entries,(a,b)->{if((++comparisons[0]&1023)==0)SignacFragments.check(cancelled);int c=Integer.compare(a.column,b.column);return c==0?Integer.compare(a.row,b.row):c;});
        int[] pointers=new int[nc+1],rows=new int[entries.length];double[] values=new double[entries.length];int at=0,duplicates=0;
        for(int i=0;i<entries.length;){SignacFragments.check(cancelled);Entry e=entries[i++];double value=e.value;
            while(i<entries.length&&entries[i].column==e.column&&entries[i].row==e.row){SignacFragments.check(cancelled);duplicates++;value=field.equals("pattern")?1:value+entries[i].value;i++;if(value>SignacFragments.MAX_EXACT)throw new IOException("Aggregated motif count exceeds exact integer domain");}
            pointers[e.column+1]++;rows[at]=e.row;values[at++]=value;
        }
        for(int column=0;column<nc;column++)pointers[column+1]+=pointers[column];
        return new Input(new SignacMotifEnrichment.Matrix(peaks,motifs,names,pointers,Arrays.copyOf(rows,at),Arrays.copyOf(values,at)),field,duplicates);
    }
    public static void write(SignacMotifEnrichment.Matrix matrix,Path target,BooleanSupplier cancelled)throws IOException {
        int[] pointers=matrix.pointers(),rows=matrix.rows();double[] values=matrix.values();
        try(var out=Files.newBufferedWriter(target,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("%%MatrixMarket matrix coordinate real general\n"+matrix.peaks().size()+" "+matrix.motifs().size()+" "+values.length+"\n");
            for(int column=0;column<matrix.motifs().size();column++)for(int i=pointers[column];i<pointers[column+1];i++){if((i&1023)==0)SignacFragments.check(cancelled);out.write((rows[i]+1)+" "+(column+1)+" "+values[i]+"\n");}
        }
    }
    private static BufferedReader reader(Path path,Limits limits)throws IOException{if(!Files.isRegularFile(path)||Files.size(path)>limits.bytes)throw new IOException("Motif input unavailable or exceeds byte capacity");return new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
    private static String line(BufferedReader in,Limits limits,BooleanSupplier cancelled)throws IOException{SignacFragments.check(cancelled);StringBuilder value=new StringBuilder();int c;while((c=in.read())>=0){if(c=='\n')break;if(value.length()>=limits.lineCharacters)throw new IOException("Motif line exceeds capacity");value.append((char)c);}if(c<0&&value.isEmpty())return null;if(!value.isEmpty()&&value.charAt(value.length()-1)=='\r')value.setLength(value.length()-1);return value.toString();}
    private static String next(BufferedReader in,Limits limits,BooleanSupplier cancelled)throws IOException{String row;while((row=line(in,limits,cancelled))!=null)if(!row.startsWith("%"))return row;return null;}
    private static String[] split(String value,int columns)throws IOException{if(value==null)throw new IOException("Missing motif Matrix Market shape");String[] fields=value.trim().split("\\s+");if(fields.length!=columns)throw new IOException("Ragged motif Matrix Market row");return fields;}
    private static int integer(String value,int minimum,int maximum)throws IOException{try{if(!value.matches("[0-9]+"))throw new NumberFormatException();long n=Long.parseLong(value);if(n<minimum||n>maximum)throw new NumberFormatException();return(int)n;}catch(NumberFormatException error){throw new IOException("Motif index/size outside declared capacity",error);}}
}

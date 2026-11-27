/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Complete native count-layer audit and exact spliced-plus-unspliced export.
 * An original HDF5 reader exports the union of nonzero entries as little-endian
 * records (index,u64 S,u64 U,u64 A,f32 original matrix). Dense hashes include every
 * implied zero. This API performs no gene assignment or velocity modeling.
 */
public final class VelocytoCountMatrices {
    private VelocytoCountMatrices() { }
    private static final List<String> NAMES=List.of("spliced","unspliced","ambiguous","matrix");
    private static final byte[] ZEROES=new byte[65536];
    public enum Counter { ORIGINAL_CLI_UINT16_BUFFERS, ORIGINAL_COUNTER_API_UINT64 }
    public record Limits(int genes,int cells,long entries,long sparseRecords,long decodedBytes) {
        public Limits {if(genes<1||cells<1||entries<1||sparseRecords<1||decodedBytes<1)throw new IllegalArgumentException("Positive complete matrix capacities required");}
    }
    public record Contract(int genes,int cells,Counter counter,long maximumCellMolecules,Map<String,String> denseSha256) {
        public Contract {
            Objects.requireNonNull(counter);denseSha256=Map.copyOf(denseSha256);
            if(genes<1||cells<1||maximumCellMolecules<0||!denseSha256.keySet().equals(Set.copyOf(NAMES))||denseSha256.values().stream().anyMatch(v->!v.matches("[0-9a-f]{64}")))throw new IllegalArgumentException("Complete count shape, actual molecule upper bound and four original dense SHA256 values required");
        }
    }
    public record Result(int genes,int cells,long denseEntries,long sparseRecords,String sparseSha256,
            Map<String,String> denseSha256,Map<String,Long> nonzeroEntries,Map<String,Long> layerTotals,
            long[] splicedByCell,long[] unsplicedByCell,long[] ambiguousByCell,
            long[] expressionTotalByGene,long[] expressionTotalByCell,long matrixFloat32RoundedEntries) {
        public Result {
            denseSha256=Map.copyOf(denseSha256);nonzeroEntries=Map.copyOf(nonzeroEntries);layerTotals=Map.copyOf(layerTotals);
            splicedByCell=splicedByCell.clone();unsplicedByCell=unsplicedByCell.clone();ambiguousByCell=ambiguousByCell.clone();expressionTotalByGene=expressionTotalByGene.clone();expressionTotalByCell=expressionTotalByCell.clone();
        }
        @Override public long[] splicedByCell(){return splicedByCell.clone();}
        @Override public long[] unsplicedByCell(){return unsplicedByCell.clone();}
        @Override public long[] ambiguousByCell(){return ambiguousByCell.clone();}
        @Override public long[] expressionTotalByGene(){return expressionTotalByGene.clone();}
        @Override public long[] expressionTotalByCell(){return expressionTotalByCell.clone();}
    }
    private record Entry(long index,long spliced,long unspliced,long ambiguous,int matrixBits) { }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(MessageDigest value){return HexFormat.of().formatHex(value.digest());}
    private static void zeroes(MessageDigest digest,long bytes,Runnable check){while(bytes>0){check.run();int n=(int)Math.min(bytes,ZEROES.length);digest.update(ZEROES,0,n);bytes-=n;}}
    private static void integer(MessageDigest digest,long value,int bytes){for(int i=0;i<bytes;i++){digest.update((byte)value);value>>>=8;}}
    private static Entry entry(DataInputStream in)throws IOException{return new Entry(Long.reverseBytes(in.readLong()),Long.reverseBytes(in.readLong()),Long.reverseBytes(in.readLong()),Long.reverseBytes(in.readLong()),Integer.reverseBytes(in.readInt()));}
    private static void valid(Entry row,long last,long entries)throws IOException {
        if(row.index<=last||row.index>=entries||row.spliced<0||row.unspliced<0||row.ambiguous<0)throw new IOException("Sparse count coordinates/order or unsigned-count supported range invalid");
        if(row.spliced==0&&row.unspliced==0&&row.ambiguous==0&&row.matrixBits==0)throw new IOException("Canonical sparse stream must omit entries whose four layers are all positive zero");
    }
    /** Verify every original layer value, implied zero and float32 source total. */
    public static Result audit(Path sparse,Contract contract,Limits limits,Runnable check)throws IOException {
        Objects.requireNonNull(check);long entries,decoded;
        if(contract.counter==Counter.ORIGINAL_CLI_UINT16_BUFFERS&&contract.maximumCellMolecules>65535)throw new IOException("Original uint16 counter can silently wrap even with uint32 output. Preserve the attempt and select explicit original Counter API uint64; no source result admitted");
        try{entries=Math.multiplyExact((long)contract.genes,contract.cells);decoded=Math.multiplyExact(entries,28L);}catch(ArithmeticException e){throw new IOException("Complete count shape overflows",e);}
        long bytes=Files.size(sparse),records=bytes/36;
        if(contract.genes>limits.genes||contract.cells>limits.cells||entries>limits.entries||decoded>limits.decodedBytes||bytes%36!=0||records>limits.sparseRecords)throw new IOException("Complete count shape/record/decoded capacity exceeded or sparse file truncated");
        var hashes=new MessageDigest[]{digest(),digest(),digest(),digest()};var rawDigest=digest();long[] nz=new long[4],totals=new long[4];long[] sCell=new long[contract.cells],uCell=new long[contract.cells],aCell=new long[contract.cells],eGene=new long[contract.genes],eCell=new long[contract.cells];long last=-1,rounded=0;
        try(var in=new DataInputStream(new BufferedInputStream(new DigestInputStream(Files.newInputStream(sparse),rawDigest)))) {
            for(long k=0;k<records;k++){
                check.run();Entry row=entry(in);valid(row,last,entries);long gap=row.index-last-1;
                for(int j=0;j<4;j++)zeroes(hashes[j],Math.multiplyExact(gap,j==3?4L:8L),check);
                long expression=Math.addExact(row.spliced,row.unspliced),all=Math.addExact(expression,row.ambiguous);
                if(contract.counter==Counter.ORIGINAL_CLI_UINT16_BUFFERS&&(row.spliced>65535||row.unspliced>65535||row.ambiguous>65535||all>65535))throw new IOException("Original uint16 buffers/main total cannot represent these counts; preserve source output, choose explicit original Counter API uint64 and rerun NEW paths");
                int expected=Float.floatToRawIntBits((float)all);if(row.matrixBits!=expected)throw new IOException("Original matrix differs from float32(spliced+unspliced+ambiguous); possible source overflow or changed export");
                float main=Float.intBitsToFloat(row.matrixBits);if(main>=0x1p63f||(long)main!=all)rounded++;
                long[] values={row.spliced,row.unspliced,row.ambiguous,expression};for(int j=0;j<4;j++){if(values[j]!=0)nz[j]++;totals[j]=Math.addExact(totals[j],values[j]);}
                for(int j=0;j<3;j++)integer(hashes[j],values[j],8);integer(hashes[3],row.matrixBits&0xffffffffL,4);
                int gene=(int)(row.index/contract.cells),cell=(int)(row.index%contract.cells);sCell[cell]=Math.addExact(sCell[cell],row.spliced);uCell[cell]=Math.addExact(uCell[cell],row.unspliced);aCell[cell]=Math.addExact(aCell[cell],row.ambiguous);eCell[cell]=Math.addExact(eCell[cell],expression);eGene[gene]=Math.addExact(eGene[gene],expression);last=row.index;
            }
            if(in.read()!=-1)throw new IOException("Sparse count population changed during read");
        }catch(ArithmeticException e){throw new IOException("Complete native count arithmetic overflow",e);}
        long trailing=entries-last-1;for(int j=0;j<4;j++)zeroes(hashes[j],Math.multiplyExact(trailing,j==3?4L:8L),check);
        var actual=new LinkedHashMap<String,String>();for(int j=0;j<4;j++)actual.put(NAMES.get(j),hex(hashes[j]));if(!actual.equals(contract.denseSha256))throw new IOException("Complete dense source-layer hashes differ, including zero entries");
        try{for(int cell=0;cell<contract.cells;cell++)if(Math.addExact(Math.addExact(sCell[cell],uCell[cell]),aCell[cell])>contract.maximumCellMolecules)throw new IOException("Counted molecules exceed the actual native input molecule bound");}catch(ArithmeticException e){throw new IOException("Complete cell molecule sum overflow",e);}
        var nonzero=new LinkedHashMap<String,Long>();var sums=new LinkedHashMap<String,Long>();for(int j=0;j<4;j++){String name=j==3?"spliced_unspliced_total":NAMES.get(j);nonzero.put(name,nz[j]);sums.put(name,totals[j]);}
        return new Result(contract.genes,contract.cells,entries,records,hex(rawDigest),actual,nonzero,sums,sCell,uCell,aCell,eGene,eCell,rounded);
    }
    /** Export four exact integer MatrixMarket matrices to a NEW directory.
     * The fourth is S+U (the expression total), excluding ambiguous molecules.
     * Original Loom matrix S+U+A and its float32 storage remain in the source file.
     */
    public static Path writeMatrixMarket(Path sparse,Result audited,Path output,Runnable check)throws IOException {
        if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("Matrix output exists; preserve it and choose a NEW directory");Files.createDirectory(output);var raw=digest();
        try(var s=writer(output,"spliced",audited);var u=writer(output,"unspliced",audited);var a=writer(output,"ambiguous",audited);var e=writer(output,"spliced_unspliced_total",audited);var in=new DataInputStream(new BufferedInputStream(new DigestInputStream(Files.newInputStream(sparse),raw)))) {
            long last=-1;for(long k=0;k<audited.sparseRecords;k++){
                check.run();Entry row=entry(in);valid(row,last,audited.denseEntries);String coordinate=(row.index/audited.cells+1)+" "+(row.index%audited.cells+1)+" ";long expression=Math.addExact(row.spliced,row.unspliced);
                emit(s,coordinate,row.spliced);emit(u,coordinate,row.unspliced);emit(a,coordinate,row.ambiguous);emit(e,coordinate,expression);last=row.index;
            }
            if(in.read()!=-1)throw new IOException("Audited sparse count population grew during export");
        }catch(ArithmeticException error){throw new IOException("Native expression total overflow",error);}
        if(!hex(raw).equals(audited.sparseSha256))throw new IOException("Sparse count bytes changed between audit and export; retain partial outputs, no complete admission");return output;
    }
    private static BufferedWriter writer(Path root,String name,Result result)throws IOException {
        var out=Files.newBufferedWriter(root.resolve(name+".mtx"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);out.write("%%MatrixMarket matrix coordinate integer general\n% Exact original count layers; identifiers retained in separate metadata.\n"+result.genes+" "+result.cells+" "+result.nonzeroEntries.get(name)+"\n");return out;
    }
    private static void emit(BufferedWriter stream,String coordinate,long value)throws IOException {if(value!=0){stream.write(coordinate);stream.write(Long.toString(value));stream.write('\n');}}
}

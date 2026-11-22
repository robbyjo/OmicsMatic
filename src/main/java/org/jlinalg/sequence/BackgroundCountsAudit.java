/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Independent integer-count and cell-call admission for supplied background-removal outputs. */
public final class BackgroundCountsAudit {
    private BackgroundCountsAudit() { }
    /** One analyzed original barcode, its model probability and reported removed fraction. */
    public record Probability(String cell,double probability,double removedFraction) { }
    /** An original barcode is always retained, including zeros and barcodes not analyzed by the model. */
    public record Cell(String id,long raw,long corrected,int rawFeatures,int correctedFeatures,
            boolean analyzed,double probability,boolean called,double removedFraction) { }
    /** Every supplied feature is retained in the marginal audit. */
    public record Feature(String id,long raw,long corrected) { }
    /** Distinct all-input and called-cell populations; these are not copied upstream metrics. */
    public record Result(List<Cell> cells,List<Feature> features,long raw,long corrected,
            long calledRaw,long calledCorrected,int analyzed,int called,List<String> filteredOrder,
            List<String> filteredCountSha256) {
        public Result {cells=List.copyOf(cells);features=List.copyOf(features);filteredOrder=List.copyOf(filteredOrder);filteredCountSha256=List.copyOf(filteredCountSha256);}
    }

    /** Audit canonical cell-major sparse matrices, whose repeated coordinates were already summed.
     * @param raw original canonical gene-by-cell integer matrix
     * @param corrected full canonical corrected matrix, with identical feature/barcode order
     * @param filtered canonical corrected matrix in the separately supplied filtered barcode order
     * @param features unique original feature IDs in matrix row order
     * @param cells unique original barcode IDs in matrix column order
     * @param probabilities unique analyzed barcodes; omitted barcodes remain explicitly not analyzed
     * @param filteredCells original filtered output order, which need not equal original barcode order
     * @param csvCalls original barcode CSV entries
     * @return exact count conservation, marginal summaries and independently verified cell calls
     * @throws IOException on invalid IDs, counts, probabilities, summaries or filtered contents
     */
    public static Result audit(Path raw,Path corrected,Path filtered,List<String> features,List<String> cells,
            List<Probability> probabilities,List<String> filteredCells,List<String> csvCalls)throws IOException {
        try {
            ids(features,"feature",false);ids(cells,"barcode",false);ids(filteredCells,"filtered barcode",true);ids(csvCalls,"barcode CSV",true);if(probabilities==null)throw new IOException("Missing analyzed barcode probabilities");
            Map<String,Integer> indices=new HashMap<>();for(int i=0;i<cells.size();i++)indices.put(cells.get(i),i);
            Probability[] model=new Probability[cells.size()];boolean[] called=new boolean[cells.size()];int nCalled=0;
            for(Probability p:probabilities){Integer i=indices.get(p.cell());if(i==null||model[i]!=null||!Double.isFinite(p.probability())||p.probability()<0||p.probability()>1||!Double.isFinite(p.removedFraction())||p.removedFraction()<0||p.removedFraction()>1)throw new IOException("Unknown/duplicate model barcode or nonfinite/out-of-range probability/background fraction: "+p.cell());model[i]=p;called[i]=p.probability()>.5;if(called[i])nCalled++;}
            var expected=new HashSet<String>();for(int i=0;i<cells.size();i++)if(called[i])expected.add(cells.get(i));
            if(!expected.equals(new HashSet<>(filteredCells))||!expected.equals(new HashSet<>(csvCalls)))throw new IOException("Filtered matrix and barcode CSV must equal strict probability>0.5 cell calls;no threshold or barcode synthesis is inferred");
            int[] filteredIndex=new int[cells.size()];Arrays.fill(filteredIndex,-1);for(int i=0;i<filteredCells.size();i++)filteredIndex[indices.get(filteredCells.get(i))]=i;
            MessageDigest[] fullDigest=new MessageDigest[nCalled],filteredDigest=new MessageDigest[nCalled];for(int i=0;i<nCalled;i++){fullDigest[i]=digest();filteredDigest[i]=digest();}ByteBuffer digestBuffer=ByteBuffer.allocate(12);
            long[] rTotals=new long[cells.size()],cTotals=new long[cells.size()],rGenes=new long[features.size()],cGenes=new long[features.size()];int[] rDetected=new int[cells.size()],cDetected=new int[cells.size()];long totalRaw=0,totalCorrected=0,calledRaw=0,calledCorrected=0;
            try(Cursor input=new Cursor(raw,features.size(),cells.size());Cursor output=new Cursor(corrected,features.size(),cells.size())){
                Entry next=output.next();Entry current;
                while((current=input.next())!=null){
                    if(next!=null&&next.key()<current.key())throw new IOException("Corrected matrix introduces a coordinate absent from original counts");
                    long value=0;if(next!=null&&next.key()==current.key()){value=next.value();if(value>current.value())throw new IOException("Corrected count exceeds original count at feature="+(current.gene()+1)+",barcode="+(current.cell()+1));next=output.next();}
                    int cell=current.cell(),gene=current.gene();rTotals[cell]=Math.addExact(rTotals[cell],current.value());rGenes[gene]=Math.addExact(rGenes[gene],current.value());rDetected[cell]=Math.incrementExact(rDetected[cell]);totalRaw=Math.addExact(totalRaw,current.value());
                    if(called[cell])calledRaw=Math.addExact(calledRaw,current.value());
                    if(value>0){cTotals[cell]=Math.addExact(cTotals[cell],value);cGenes[gene]=Math.addExact(cGenes[gene],value);cDetected[cell]=Math.incrementExact(cDetected[cell]);totalCorrected=Math.addExact(totalCorrected,value);if(called[cell]){calledCorrected=Math.addExact(calledCorrected,value);update(fullDigest[filteredIndex[cell]],gene,value,digestBuffer);}}
                }
                if(next!=null)throw new IOException("Corrected matrix introduces a coordinate after the original matrix ends");
            }
            try(Cursor selected=new Cursor(filtered,features.size(),filteredCells.size())){Entry entry;while((entry=selected.next())!=null)update(filteredDigest[entry.cell()],entry.gene(),entry.value(),digestBuffer);}
            var fingerprints=new ArrayList<String>();for(int i=0;i<nCalled;i++){byte[] full=fullDigest[i].digest(),selected=filteredDigest[i].digest();if(!MessageDigest.isEqual(full,selected))throw new IOException("Filtered corrected contents differ from full corrected counts for "+filteredCells.get(i));fingerprints.add(HexFormat.of().formatHex(full));}
            var outputCells=new ArrayList<Cell>();for(int i=0;i<cells.size();i++){
                double fraction=(rTotals[i]-cTotals[i])/(rTotals[i]+.001);Probability p=model[i];
                if(p!=null&&Math.abs(p.removedFraction()-fraction)>1e-12)throw new IOException("Reported background fraction differs from immutable original/full corrected totals for "+cells.get(i)+";inspect original CSR mutation or changed population/feature scope");
                outputCells.add(new Cell(cells.get(i),rTotals[i],cTotals[i],rDetected[i],cDetected[i],p!=null,p==null?Double.NaN:p.probability(),called[i],fraction));
            }
            var outputFeatures=new ArrayList<Feature>();for(int i=0;i<features.size();i++)outputFeatures.add(new Feature(features.get(i),rGenes[i],cGenes[i]));
            return new Result(outputCells,outputFeatures,totalRaw,totalCorrected,calledRaw,calledCorrected,probabilities.size(),nCalled,filteredCells,fingerprints);
        }catch(ArithmeticException failure){throw new IOException("Integer count/marginal capacity exceeded;no rounded or overflowed summary is admitted",failure);}
    }
    private static void ids(List<String> values,String role,boolean empty)throws IOException {if(values==null||!empty&&values.isEmpty())throw new IOException("Missing "+role+" IDs");var seen=new HashSet<String>();for(String id:values)if(id==null||id.isBlank()||id.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||!seen.add(id))throw new IOException("Invalid/duplicate "+role+" ID");}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static void update(MessageDigest digest,int gene,long count,ByteBuffer buffer){buffer.clear();buffer.putInt(gene).putLong(count);digest.update(buffer.array());}
    private record Entry(int gene,int cell,long value) {long key(){return ((long)cell<<32)|Integer.toUnsignedLong(gene);}}
    private static final class Cursor implements AutoCloseable {
        final BufferedReader reader;final int genes,cells;final long entries;long read,previous=-1;boolean ended;
        Cursor(Path path,int genes,int cells)throws IOException {
            this.genes=genes;this.cells=cells;reader=Files.newBufferedReader(path);boolean valid=false;
            try{String header=reader.readLine();if(!"%%MatrixMarket matrix coordinate integer general".equals(header))throw new IOException("Audit requires canonical integer Matrix Market counts");String text=line();if(text==null)throw new IOException("Missing canonical matrix dimensions");String[] f=text.split("\\s+");if(f.length!=3||Integer.parseInt(f[0])!=genes||Integer.parseInt(f[1])!=cells)throw new IOException("Canonical matrix dimensions differ from exact feature/barcode IDs");entries=Long.parseLong(f[2]);if(entries<0||entries>500000000L)throw new IOException("Canonical entry count outside admitted bounds");valid=true;}catch(NumberFormatException error){throw new IOException("Invalid canonical matrix dimensions",error);}finally{if(!valid)reader.close();}
        }
        Entry next()throws IOException {
            if(ended)return null;String text=line();if(text==null){ended=true;if(read!=entries)throw new IOException("Canonical matrix is truncated");return null;}if(++read>entries)throw new IOException("Canonical matrix contains excess entries");
            try{String[] f=text.split("\\s+");if(f.length!=3)throw new IOException("Malformed canonical matrix row");int gene=Integer.parseInt(f[0])-1,cell=Integer.parseInt(f[1])-1;long value=Long.parseLong(f[2]);Entry entry=new Entry(gene,cell,value);if(gene<0||gene>=genes||cell<0||cell>=cells||value<=0||value>9007199254740991L||entry.key()<=previous)throw new IOException("Canonical count coordinate/order/uniqueness/value contract failed");previous=entry.key();return entry;}catch(NumberFormatException failure){throw new IOException("Canonical count is not an exact integer",failure);}
        }
        String line()throws IOException {String text;while((text=reader.readLine())!=null){if(text.length()>1048576)throw new IOException("Oversized canonical count row");text=text.strip();if(!text.isEmpty()&&!text.startsWith("%"))return text;}return null;}
        @Override public void close()throws IOException{reader.close();}
    }
}

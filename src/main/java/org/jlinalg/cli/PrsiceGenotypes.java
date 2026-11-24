/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** PLINK hard calls and original ID, founder and allele matching conventions. */
final class PrsiceGenotypes {
    private PrsiceGenotypes() { }
    record Bim(int index,String id,String rawChromosome,int chromosome,long position,String rawA1,String rawA2) {
        String a1(){return PrsiceTables.asciiUpper(rawA1);}
        String a2(){return PrsiceTables.asciiUpper(rawA2);}
    }
    record Sample(int index,String fid,String iid,String father,String mother,String sex,String phenotype,String key,boolean selected,boolean founder) { }
    record Orientation(boolean matched,boolean flipped,boolean complemented,boolean palindromic) { }
    static final class Dataset implements AutoCloseable {
        final List<Bim> variants;
        final Map<String,Bim> byId;
        final Set<String> duplicateVariantIds;
        final List<Sample> samples,selected;
        final boolean[] selectedMask;
        final long unknownSelectionIds;
        final Bed bed;
        Dataset(List<Bim> variants,Map<String,Bim> byId,Set<String> duplicateVariantIds,List<Sample> samples,long unknown,Path path)throws IOException {
            this.variants=List.copyOf(variants);this.byId=Collections.unmodifiableMap(byId);this.duplicateVariantIds=Set.copyOf(duplicateVariantIds);this.samples=List.copyOf(samples);this.selected=samples.stream().filter(Sample::selected).toList();this.selectedMask=new boolean[samples.size()];for(Sample s:samples)selectedMask[s.index()]=s.selected();this.unknownSelectionIds=unknown;
            require(!selected.isEmpty(),"Declared sample selection retained no samples");bed=new Bed(path,samples.size(),variants.size());
        }
        @Override public void close()throws IOException {bed.close();}
        long selectedFounders(){return selected.stream().filter(Sample::founder).count();}
    }
    static Dataset read(Path root,Map<String,Object> request,String group)throws IOException {
        require(Set.of("target","reference").contains(group),"Unknown genotype dataset role");
        var parameters=object(request.get("parameters"));var inputs=object(request.get("inputs"));boolean ignore=Boolean.TRUE.equals(parameters.get("ignore_fid"));String delimiter=ignore?"":text(parameters.get("id_delimiter"),"ID delimiter");
        var variants=new ArrayList<Bim>();var byId=new LinkedHashMap<String,Bim>();var duplicates=new TreeSet<String>();
        try(var rows=new PrsiceTables.Rows(PrsiceRuntime.input(root,group+"_bim"),request,false)){
            for(String[] row;(row=rows.next())!=null;){
                require(row.length==6,"Require six BIM fields at physical line "+rows.lineNumber());require(variants.size()<PrsiceContracts.cap(request,"maximum_variants"),"BIM exceeds maximum_variants");
                long bp=nonnegative(row[3],"BIM position");var variant=new Bim(variants.size(),row[1],row[0],chromosome(row[0]),bp,row[4],row[5]);
                require(!row[1].isEmpty()&&!row[4].isEmpty()&&!row[5].isEmpty(),"Empty BIM ID/allele");variants.add(variant);if(byId.putIfAbsent(variant.id(),variant)!=null)duplicates.add(variant.id());
            }
        }
        require(!variants.isEmpty(),"Empty BIM dataset");var rawSamples=new ArrayList<String[]>();var keys=new HashSet<String>();
        try(var rows=new PrsiceTables.Rows(PrsiceRuntime.input(root,group+"_fam"),request,false)){
            for(String[] row;(row=rows.next())!=null;){
                require(row.length==6,"Require six FAM fields at physical line "+rows.lineNumber());require(rawSamples.size()<PrsiceContracts.cap(request,"maximum_samples"),"FAM exceeds maximum_samples");
                String key=id(row[0],row[1],ignore,delimiter);require(keys.add(key),"Duplicate/colliding original sample key "+key+"; select an explicit noncolliding delimiter or correct duplicate IDs, never rename them silently");rawSamples.add(row);
            }
        }
        require(!rawSamples.isEmpty(),"Empty FAM dataset");String keepRole=group+"_keep",removeRole=group+"_remove";Set<String> selection=null;
        if(inputs.containsKey(keepRole))selection=selection(PrsiceRuntime.input(root,keepRole),request,ignore,delimiter);
        if(inputs.containsKey(removeRole))selection=selection(PrsiceRuntime.input(root,removeRole),request,ignore,delimiter);
        long unknown=selection==null?0:selection.stream().filter(k->!keys.contains(k)).count();var samples=new ArrayList<Sample>();
        for(int i=0;i<rawSamples.size();i++){
            String[] row=rawSamples.get(i);String key=id(row[0],row[1],ignore,delimiter);
            boolean selected=selection==null || (inputs.containsKey(keepRole)?selection.contains(key):!selection.contains(key));
            // Original has_parent tests membership in the original full FAM,
            // not only kept samples, and does not special-case a literal IID 0.
            boolean founder=ignore || !(keys.contains(id(row[0],row[2],false,delimiter)) || keys.contains(id(row[0],row[3],false,delimiter)));
            samples.add(new Sample(i,row[0],row[1],row[2],row[3],row[4],row[5],key,selected,founder));
        }
        return new Dataset(variants,byId,duplicates,samples,unknown,PrsiceRuntime.input(root,group+"_bed"));
    }
    static Set<String> selection(Path path,Map<String,Object> request,boolean ignore,String delimiter)throws IOException {
        var result=new LinkedHashSet<String>();try(var rows=new PrsiceTables.Rows(path,request,false)){
            for(String[] row;(row=rows.next())!=null;){require(row.length==(ignore?1:2),"Selection lists require "+(ignore?"IID":"FID IID")+" without a header or ignored extra columns");result.add(ignore?row[0]:id(row[0],row[1],false,delimiter));}
        }return result;
    }
    static String id(String fid,String iid,boolean ignore,String delimiter){return ignore?iid:fid+delimiter+iid;}
    static int chromosome(String raw) {
        String value=PrsiceTables.asciiUpper(raw);if(value.startsWith("CHR"))value=value.substring(3);
        return switch(value){case "X","0X"->23;case "Y","0Y"->24;case "XY"->25;case "M","MT","0M"->26;default->{try{yield Integer.parseInt(value);}catch(NumberFormatException error){yield -1;}}};
    }
    static long nonnegative(String value,String label)throws IOException {
        require(value.matches("[+]?[0-9]+"),"Require nonnegative integer "+label+": "+value);
        try{return Long.parseLong(value);}catch(NumberFormatException error){throw new IOException("Integer outside capacity: "+label,error);}
    }
    static boolean palindromic(String a,String b){return a.length()==1&&b.length()==1&&(a.equals("A")&&b.equals("T")||a.equals("T")&&b.equals("A")||a.equals("C")&&b.equals("G")||a.equals("G")&&b.equals("C"));}
    static String complement(String value){return switch(value){case "A"->"T";case "T"->"A";case "C"->"G";case "G"->"C";default->value;};}
    static Orientation orient(String sourceA1,String sourceA2,String targetA1,String targetA2,boolean keepPalindromic) {
        String a=PrsiceTables.asciiUpper(sourceA1),b=PrsiceTables.asciiUpper(sourceA2),c=PrsiceTables.asciiUpper(targetA1),d=PrsiceTables.asciiUpper(targetA2);boolean pal=palindromic(c,d);boolean matched=false,flip=false,comp=false;
        if(a.equals(c))matched=b.equals(d);
        else if(complement(a).equals(c)){matched=complement(b).equals(d);comp=matched;}
        else if(!d.isEmpty()){
            if(a.equals(d)&&b.equals(c)){matched=true;flip=true;}
            else if(complement(a).equals(d)&&complement(b).equals(c)){matched=true;flip=true;comp=true;}
        }
        if(pal){if(!keepPalindromic)matched=false;else if(matched){flip=!c.equals(a);comp=false;}}
        return new Orientation(matched,flip,comp,pal);
    }
    static final class Bed implements AutoCloseable {
        final FileChannel channel;
        final int samples,variants,stride;
        Bed(Path path,int samples,int variants)throws IOException {
            require(samples>0&&variants>0,"Nonempty BED dimensions required");require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular captured BED");
            this.samples=samples;this.variants=variants;this.stride=Math.toIntExact((samples+3L)/4);
            long expected=Math.addExact(3,Math.multiplyExact((long)stride,variants));require(Files.size(path)==expected,"BED byte size differs from complete BIM/FAM dimensions");
            FileChannel opened=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
            try{ByteBuffer header=ByteBuffer.allocate(3);read(opened,header,0);byte[] bytes=header.array();require((bytes[0]&255)==0x6c&&(bytes[1]&255)==0x1b,"Invalid PLINK BED magic");if(bytes[2]!=1)throw new PrsiceContracts.Unavailable("This original workflow profile requires SNP-major diploid BED; explicitly convert the input by a permitted method or keep dependent analysis skipped");channel=opened;}catch(IOException|RuntimeException error){opened.close();throw error;}
        }
        byte[] buffer(){return new byte[stride];}
        void row(int index,byte[] destination)throws IOException {require(index>=0&&index<variants&&destination.length==stride,"Invalid BED row/buffer");read(channel,ByteBuffer.wrap(destination),3+Math.multiplyExact((long)index,stride));}
        static int copies(byte[] row,int sample){int code=(row[sample>>>2]>>((sample&3)*2))&3;return switch(code){case 0->2;case 1->-1;case 2->1;default->0;};}
        private static void read(FileChannel channel,ByteBuffer buffer,long position)throws IOException {while(buffer.hasRemaining()){int n=channel.read(buffer,position);if(n<0)throw new EOFException("Captured BED truncated during audit");if(n==0){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("PRSice BED audit interrupted");continue;}position+=n;}}
        @Override public void close()throws IOException {channel.close();}
    }
}

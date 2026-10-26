/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Native sample/header checks before passing original genotype bytes to REGENIE. */
public final class RegenieInputs {
    private RegenieInputs() { }
    /** Sample keys are exactly the upstream FID_IID keys, in genotype order. */
    public record Genotypes(String format,Path input,Path sample,List<Path> files,List<String> sampleKeys,long variants) {
        public Genotypes {files=List.copyOf(files);sampleKeys=List.copyOf(sampleKeys);}
    }
    /** BED SNP-major, uncompressed PVAR/PSAM PGEN, or layout-2 BGEN with exact sample identity. */
    public static Genotypes inspect(String format,Path input,Path sample)throws IOException {
        input=input.toAbsolutePath().normalize();if(sample!=null)sample=sample.toAbsolutePath().normalize();
        var files=new ArrayList<Path>();List<String> keys;long variants;
        if(format.equals("bed")){
            if(sample!=null)throw new IOException("External sample file applies only to BGEN");
            Path bed=file(Path.of(input+".bed")),bim=file(Path.of(input+".bim")),fam=file(Path.of(input+".fam"));files.addAll(List.of(bed,bim,fam));
            keys=pairs(fam,false);variants=variants(bim,false);
            try(var bytes=Files.newInputStream(bed)){if(bytes.read()!=0x6c||bytes.read()!=0x1b||bytes.read()!=1)throw new IOException("BED must have the PLINK SNP-major magic header");}
            if(Files.size(bed)!=Math.addExact(3,Math.multiplyExact((keys.size()+3L)/4,variants)))throw new IOException("BED byte count differs from FAM samples and BIM variants");
        }else if(format.equals("pgen")){
            if(sample!=null)throw new IOException("External sample file applies only to BGEN");
            Path pgen=file(Path.of(input+".pgen")),pvar=file(Path.of(input+".pvar")),psam=file(Path.of(input+".psam"));files.addAll(List.of(pgen,pvar,psam));
            keys=pairs(psam,true);variants=variants(pvar,true);
            try(var bytes=Files.newInputStream(pgen)){if(bytes.read()!=0x6c||bytes.read()!=0x1b||bytes.read()<0)throw new IOException("PGEN magic header is invalid");}
        }else if(format.equals("bgen")){
            files.add(file(input));try(var bytes=new RandomAccessFile(input.toFile(),"r")){
                long offset=u32(bytes),header=u32(bytes);variants=u32(bytes);long count=u32(bytes);
                byte[] magic=new byte[4];bytes.readFully(magic);
                if(header<20||header>bytes.length()-4||offset<header||offset>bytes.length()-4||count<1||count>Integer.MAX_VALUE||variants<1)throw new IOException("BGEN header sizes/counts are invalid");
                if(!new String(magic,StandardCharsets.US_ASCII).equals("bgen")&&!java.util.Arrays.equals(magic,new byte[4]))throw new IOException("BGEN magic header is invalid");
                bytes.seek(header);long flags=u32(bytes);if(((flags>>>2)&15)!=2||(flags&3)>2)throw new IOException("This contract requires BGEN layout 2 with uncompressed/zlib/zstd blocks");
                boolean embedded=(flags&0x80000000L)!=0;
                if(sample!=null){keys=oxford(sample);files.add(file(sample));if(keys.size()!=count)throw new IOException("Oxford sample count differs from BGEN header");}
                else{
                    if(!embedded)throw new IOException("BGEN without embedded IDs requires an explicit Oxford sample file");
                    long block=u32(bytes),samples=u32(bytes),end=Math.addExact(header+4,block);
                    if(block<8||samples!=count||end>offset+4)throw new IOException("BGEN sample block sizes/counts are invalid");
                    keys=new ArrayList<>();for(int i=0;i<count;i++){
                        int length=bytes.readUnsignedByte()|(bytes.readUnsignedByte()<<8);if(length<1||bytes.getFilePointer()+length>end)throw new IOException("Invalid BGEN sample ID length");
                        byte[] id=new byte[length];bytes.readFully(id);keys.add(new String(id,StandardCharsets.UTF_8));
                    }
                    if(bytes.getFilePointer()!=end)throw new IOException("BGEN sample block length differs from its encoded IDs");
                }
            }
        }else throw new IOException("Genotype format must be bed, bgen or pgen");
        var seen=new HashSet<String>();for(String key:keys)if(key.isBlank()||key.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||!seen.add(key))throw new IOException("Empty, duplicate or colliding genotype FID_IID key: "+key);
        if(keys.isEmpty()||variants<1)throw new IOException("Genotypes contain no samples or variants");
        return new Genotypes(format,input,sample,files,keys,variants);
    }
    private static List<String> pairs(Path path,boolean psam)throws IOException {
        var result=new ArrayList<String>();try(var reader=Files.newBufferedReader(path)){
            String line;if(psam){line=reader.readLine();if(line==null||!line.strip().matches("#FID\\s+IID(?:\\s+.*)?"))throw new IOException("Pinned REGENIE requires PSAM header #FID IID; IID-only files are not accepted");}
            while((line=reader.readLine())!=null){String[] f=fields(line);if(f.length<(psam?2:6)||!psam&&f.length!=6)throw new IOException("Malformed FAM/PSAM sample row");result.add(new RegenieTables.Sample(f[0],f[1]).upstreamKey());}
        }return result;
    }
    private static List<String> oxford(Path path)throws IOException {
        var result=new ArrayList<String>();try(var reader=Files.newBufferedReader(file(path))){
            String[] header=fields(reader.readLine()),types=fields(reader.readLine());
            if(header.length<2||!header[0].equals("ID_1")||!header[1].equals("ID_2")||types.length!=header.length||!types[0].equals("0")||!types[1].equals("0"))throw new IOException("Oxford sample header/types must start ID_1 ID_2 and 0 0");
            String line;while((line=reader.readLine())!=null){String[] f=fields(line);if(f.length!=header.length)throw new IOException("Oxford sample row width differs from header");result.add(new RegenieTables.Sample(f[0],f[1]).upstreamKey());}
        }return result;
    }
    private static long variants(Path path,boolean pvar)throws IOException {
        long count=0;var ids=new HashSet<String>();boolean header=!pvar;
        try(var reader=Files.newBufferedReader(path)){String line;while((line=reader.readLine())!=null){
            if(pvar&&line.startsWith("##"))continue;String[] f=fields(line);
            if(!header){if(f.length<5||!String.join(" ",java.util.Arrays.copyOf(f,5)).equals("#CHROM POS ID REF ALT"))throw new IOException("PVAR must start #CHROM POS ID REF ALT after optional metadata");header=true;continue;}
            if(f.length<(pvar?5:6)||!pvar&&f.length!=6)throw new IOException("Malformed BIM/PVAR variant row");
            String id=f[pvar?2:1];if(id.equals(".")||!ids.add(id))throw new IOException("Require unique nonmissing BIM/PVAR variant IDs: "+id);
            try{if(Long.parseLong(f[pvar?1:3])<1)throw new NumberFormatException();}catch(NumberFormatException failure){throw new IOException("Variant position must be a positive integer",failure);}
            if(pvar&&f[4].contains(","))throw new IOException("This PGEN contract requires biallelic PVAR rows; split multiallelic records explicitly before analysis");count++;
        }}return count;
    }
    private static long u32(RandomAccessFile file)throws IOException {return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));}
    private static String[] fields(String line){return line==null||line.isBlank()?new String[0]:line.strip().split("\\s+");}
    private static Path file(Path path)throws IOException {if(!Files.isRegularFile(path))throw new IOException("Missing local genotype/sample component: "+path);return path;}
}

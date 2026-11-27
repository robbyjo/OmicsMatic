/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.tribble.readers.TabixReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.GraphtyperAlignmentAudit.Region;

/** Literal REF/reference matching and complete indexed-query checks for supplied SNP/indel sites. */
public final class GraphtyperVcfReferenceAudit {
    private GraphtyperVcfReferenceAudit() { }
    public record Query(String region,long records,String allDataFieldsSha256) { }
    public static Set<String> samples(Path vcf,int lineCap,int headerCap,Runnable check)throws IOException {
        try(var raw=Files.newInputStream(vcf);var probe=new PushbackInputStream(new BufferedInputStream(raw),2)){
            byte[] head=probe.readNBytes(2);probe.unread(head);InputStream stream=head.length==2&&(head[0]&255)==31&&(head[1]&255)==139?new GZIPInputStream(probe):probe;
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){int n=0;for(String line;(line=line(reader,lineCap))!=null;){check.run();if(++n>headerCap)throw new IOException("Complete VCF header capacity exceeded");if(line.startsWith("#CHROM\t")){String[] f=line.split("\t",-1);var samples=new LinkedHashSet<String>();for(int i=9;i<f.length;i++)if(!samples.add(f[i]))throw new IOException("Duplicate literal VCF sample ID");return Set.copyOf(samples);}if(!line.startsWith("##"))throw new IOException("Complete original VCF sample header absent");}}
        }throw new IOException("Complete original VCF sample header absent");
    }
    /** All record-table REF alleles must match the actual indexed reference; source headers are not rewritten. */
    public static long reference(Path recordTable,Path fasta,Map<String,Long> lengths,int lineCap,long maxRecords,Runnable check)throws IOException {
        long n=0;try(var reader=Files.newBufferedReader(recordTable,StandardCharsets.UTF_8);var reference=new IndexedFastaSequenceFile(fasta)){
            if(line(reader,lineCap)==null)throw new IOException("Complete native VCF record table absent");
            for(String line;(line=line(reader,lineCap))!=null;){check.run();if(++n>maxRecords)throw new IOException("Complete VCF REF validation capacity exceeded");String[] f=line.split("\t",-1);if(f.length!=13)throw new IOException("Complete native VCF record table changed");long position=Long.parseLong(f[2]);String allele=f[3];Long length=lengths.get(f[1]);long end=Math.addExact(position,allele.length()-1);if(length==null||end>length||!allele.matches("[ACGTNacgtn]+"))throw new IOException("Original VCF REF is outside or incompatible with actual reference: "+f[1]+":"+position);String actual=new String(reference.getSubsequenceAt(f[1],position,end).getBases(),StandardCharsets.US_ASCII);if(!allele.equalsIgnoreCase(actual))throw new IOException("Original VCF REF disagrees with actual reference: "+f[1]+":"+position);}
        }catch(RuntimeException e){throw new IOException("Original VCF/reference population is invalid: "+e.getMessage(),e);}return n;
    }
    /** Supplied small-variant sites must have exactly the indexed overlap populations of the full raw VCF. */
    public static List<Query> sitesIndex(Path bgzip,Path tbi,List<Region> regions,long recordCap,long decodedCap,int lineCap,Runnable check)throws IOException {
        long records=0,decoded=0;long[] counts=new long[regions.size()];var digests=new ArrayList<MessageDigest>();for(var r:regions)digests.add(digest());
        try(var raw=Files.newInputStream(bgzip);var gzip=new GZIPInputStream(raw);var reader=new BufferedReader(new InputStreamReader(gzip,StandardCharsets.UTF_8))){
            for(String line;(line=line(reader,lineCap))!=null;){check.run();decoded=Math.addExact(decoded,line.getBytes(StandardCharsets.UTF_8).length+1L);if(decoded>decodedCap)throw new IOException("Complete decoded prior VCF capacity exceeded");if(line.startsWith("#"))continue;if(++records>recordCap)throw new IOException("Complete prior VCF record capacity exceeded");String[] f=line.split("\t",-1);if(f.length<8||!f[3].matches("[ACGTNacgtn]+")||Arrays.stream(f[4].split(",",-1)).anyMatch(a->!a.matches("[ACGTNacgtn]+")))throw new IOException("This graph-initialization profile requires literal SNP/indel REF/ALT alleles; no symbolic/breakend/site rewriting is performed");long position=Long.parseLong(f[1]),end=Math.addExact(position,f[3].length()-1);for(int i=0;i<regions.size();i++){var r=regions.get(i);if(f[0].equals(r.contig())&&position<=r.end()&&end>=r.start()){counts[i]++;append(digests.get(i),line);}}}
        }catch(RuntimeException e){throw new IOException("Invalid original SNP/indel site population: "+e.getMessage(),e);}
        var result=new ArrayList<Query>();try(var reader=new TabixReader(bgzip.toString(),tbi.toString())){
            for(int i=0;i<regions.size();i++){
                var r=regions.get(i);var digest=digest();long n=0,bytes=0;var iterator=reader.query(r.contig(),r.start()-1,r.end());String row;
                if(iterator!=null)while((row=iterator.next())!=null){check.run();if(++n>recordCap||row.length()>lineCap)throw new IOException("Complete indexed prior VCF capacity exceeded");bytes=Math.addExact(bytes,row.getBytes(StandardCharsets.UTF_8).length+1L);if(bytes>decodedCap)throw new IOException("Complete indexed prior VCF byte capacity exceeded");append(digest,row);}
                String expected=HexFormat.of().formatHex(digests.get(i).digest());if(n!=counts[i]||!expected.equals(HexFormat.of().formatHex(digest.digest())))throw new IOException("Actual supplied TBI query disagrees with complete sequential SNP/indel overlap population: "+r);result.add(new Query(r.toString(),n,expected));
            }
        }catch(RuntimeException e){throw new IOException("Invalid original site index: "+e.getMessage(),e);}return List.copyOf(result);
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void append(MessageDigest d,String s){d.update(s.getBytes(StandardCharsets.UTF_8));d.update((byte)'\n');}
    private static String line(BufferedReader in,int maximum)throws IOException {var s=new StringBuilder();for(int c;(c=in.read())!=-1;){if(c=='\n'){if(!s.isEmpty()&&s.charAt(s.length()-1)=='\r')s.setLength(s.length()-1);return s.toString();}if(s.length()>=maximum)throw new IOException("Complete VCF/reference table line capacity exceeded");s.append((char)c);}return s.isEmpty()?null:s.toString();}
}

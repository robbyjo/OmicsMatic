/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete FASTA-to-seed-index audit for the original MALT0.5.2 DNA loader.
 * This projects source DNA5 bytes and whitespace handling without rewriting inputs.
 * Blank prefixes, empty records and ambiguous accession identities never admit a
 * smaller population when the original loader silently consumes zero records.
 */
public final class Malt052FastaIndexAudit {
    private Malt052FastaIndexAudit() { }
    public record Limits(long fileBytes,int files,int references,long bases,int lineCharacters) {
        public Limits {if(fileBytes<1||files<1||references<1||bases<1||lineCharacters<1)throw new IllegalArgumentException("Positive complete FASTA capacities required");}
    }
    public record Reference(int fileOrdinal,int ordinal,String header,String accession,int bases,String dna5Sha256) {
        public Map<String,Object> document(){return Map.of("file_ordinal",fileOrdinal,"ordinal",ordinal,"header_base64",Base64.getEncoder().encodeToString(header.getBytes(StandardCharsets.UTF_8)),"accession",accession,"bases",bases,"dna5_sha256",dna5Sha256);}
    }
    public record Population(List<Reference> references,long bases,long ignoredSequenceWhitespace,
                             List<MaltIndexTaxonomy.Pin> inputs) {
        public Population {references=List.copyOf(references);inputs=List.copyOf(inputs);}
        public Map<String,Object> document(){var out=new LinkedHashMap<String,Object>();out.put("references",references.size());out.put("reference_bases",bases);out.put("ignored_sequence_whitespace",ignoredSequenceWhitespace);out.put("reference_rows",references.stream().map(Reference::document).toList());return out;}
    }
    /** The source DNA5 lookup, including its retained gap byte; not a generic DNA encoder. */
    public static byte sourceDna5(int c)throws IOException {
        if(c<0||c>=127)throw invalid("Original DNA5 cannot normalize a non-ASCII sequence byte");
        return switch(c){case 'A','a' -> (byte)'A';case 'C','c' -> (byte)'C';case 'G','g' -> (byte)'G';case 'T','t','U','u' -> (byte)'T';case '-' -> (byte)'-';default -> (byte)'N';};
    }
    public static Population read(List<Path> fasta,Limits limits)throws IOException {
        Objects.requireNonNull(limits);if(fasta.isEmpty()||fasta.size()>limits.files())throw invalid("Complete FASTA file count is empty or exceeds capacity");
        var pins=new ArrayList<MaltIndexTaxonomy.Pin>();var paths=new HashSet<Path>();var references=new ArrayList<Reference>();var accessions=new HashSet<String>();long total=0,ignored=0;
        for(int fileOrdinal=0;fileOrdinal<fasta.size();fileOrdinal++) {
            Path path=fasta.get(fileOrdinal);if(!path.isAbsolute()||!path.normalize().equals(path)||!path.toRealPath().equals(path)||!paths.add(path))throw invalid("Require distinct canonical FASTA files in the declared order");
            pins.add(MaltIndexTaxonomy.pin(path,limits.fileBytes()));
            try(var input=reader(path,limits.fileBytes())) {
                String line=line(input,limits.lineCharacters());if(line==null||line.isEmpty()||horizontalOnly(line))throw invalid("Original0.5.2 consumes zero references from a blank-prefixed/empty FASTA; no prefix is silently repaired");
                String header=null;int length=0;MessageDigest digest=digest();
                for(;line!=null;line=line(input,limits.lineCharacters())) {
                    int start=0;while(start<line.length()&&horizontal(line.charAt(start)))start++;
                    if(start<line.length()&&line.charAt(start)=='>') {
                        if(header!=null){references.add(reference(fileOrdinal,references.size(),header,length,digest,accessions,limits));total=Math.addExact(total,length);if(total>limits.bases())throw invalid("Complete FASTA base capacity exceeded");}
                        header=line.substring(start);length=0;digest=digest();
                        if(header.indexOf('\0')>=0||header.contains("|tax|"))throw invalid("NUL or pre-existing taxon tags cannot enter the explicit native mapping alternative");
                    } else {
                        if(header==null)throw invalid("Non-header content precedes the first FASTA record");
                        for(int i=0;i<line.length();i++) {
                            char value=line.charAt(i);if(horizontal(value)){ignored++;continue;}
                            if(value=='>')throw invalid("Interior header marker cannot be interpreted as a complete FASTA record");
                            if(length==Integer.MAX_VALUE||Math.addExact(total,length+1L)>limits.bases())throw invalid("Complete FASTA sequence/base capacity exceeded");
                            digest.update(sourceDna5(value));length++;
                        }
                    }
                }
                if(header==null)throw invalid("FASTA has no complete record");
                references.add(reference(fileOrdinal,references.size(),header,length,digest,accessions,limits));total=Math.addExact(total,length);if(total>limits.bases())throw invalid("Complete FASTA base capacity exceeded");
            }
        }
        for(int i=0;i<fasta.size();i++)if(!pins.get(i).equals(MaltIndexTaxonomy.pin(fasta.get(i),limits.fileBytes())))throw invalid("Original FASTA changed during complete intake");
        return new Population(references,total,ignored,pins);
    }
    private static Reference reference(int file,int ordinal,String header,int length,MessageDigest digest,Set<String> accessions,Limits limits)throws IOException {
        if(length<1)throw invalid("Empty FASTA record; original0.5.2 can silently omit a population, so no result is admitted");
        if(ordinal>=limits.references())throw invalid("Complete reference count capacity exceeded");
        String accession=header.substring(1).split("\\s+",2)[0];if(accession.isEmpty()||!accessions.add(accession))throw invalid("Empty or duplicate first-word accession; native mapping requires unambiguous complete identities");
        return new Reference(file,ordinal,header,accession,length,HexFormat.of().formatHex(digest.digest()));
    }
    /** Check every original sequence byte, header, reference ordinal, offset and count. */
    public static Map<String,Object> audit(List<Path> fasta,Path index,Limits limits)throws IOException {
        Population input=read(fasta,limits);var files=MaltIndexTaxonomy.inventory(index,limits.fileBytes());
        if(Files.size(index.resolve("ref.idx"))!=8L*input.references().size())throw invalid("Original index omits/adds FASTA references");
        long offset=0;byte[] buffer=new byte[65536];
        try(var offsets=new DataInputStream(new BufferedInputStream(Files.newInputStream(index.resolve("ref.idx"))));
            var db=new DataInputStream(new BufferedInputStream(Files.newInputStream(index.resolve("ref.db"))))) {
            for(Reference reference:input.references()) {
                if(offsets.readLong()!=offset)throw invalid("Original seed index offsets do not cover the complete database");
                int length=db.readInt();if(length!=reference.bases())throw invalid("Original reference length differs from complete FASTA at ordinal "+reference.ordinal());
                var hash=digest();for(int left=length;left>0;){int n=db.read(buffer,0,Math.min(left,buffer.length));if(n<1)throw invalid("Truncated original reference sequence");hash.update(buffer,0,n);left-=n;}
                if(!HexFormat.of().formatHex(hash.digest()).equals(reference.dna5Sha256()))throw invalid("Original DNA5 reference bytes differ from complete FASTA at ordinal "+reference.ordinal());
                int headerBytes=db.readInt();if(headerBytes<2||headerBytes>limits.lineCharacters()*4L)throw invalid("Original reference header exceeds complete capacity");
                byte[] raw=db.readNBytes(headerBytes);if(raw.length!=headerBytes||!utf8(raw).equals(reference.header()))throw invalid("Original complete header/order differs at ordinal "+reference.ordinal());
                offset=Math.addExact(offset,8L+length+headerBytes);
            }
            if(db.read()!=-1||offsets.read()!=-1)throw invalid("Trailing original reference/index bytes");
        }
        if(!Files.readString(index.resolve("ref.inf"),StandardCharsets.UTF_8).lines().sorted().toList().equals(List.of("letters\t"+input.bases(),"sequences\t"+input.references().size())))throw invalid("Original complete reference-info counters differ");
        if(!files.equals(MaltIndexTaxonomy.inventory(index,limits.fileBytes())))throw invalid("Original seed index changed during complete audit");
        for(int i=0;i<fasta.size();i++)if(!input.inputs().get(i).equals(MaltIndexTaxonomy.pin(fasta.get(i),limits.fileBytes())))throw invalid("Complete original FASTA changed during index audit");
        return input.document();
    }
    private static boolean horizontal(char c){return c==' '||c=='\t'||c=='\f'||c=='\u000b';}
    private static boolean horizontalOnly(String text){for(int i=0;i<text.length();i++)if(!horizontal(text.charAt(i)))return false;return true;}
    private static PushbackReader reader(Path path,long maximum)throws IOException {
        var raw=new BufferedInputStream(Files.newInputStream(path));raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();InputStream decoded;
        if((a==31&&b==139)!=path.toString().endsWith(".gz")){raw.close();throw invalid("FASTA compression and the explicit .gz filename must agree; source interpretation is never changed by renaming");}
        try{decoded=a==31&&b==139?new GZIPInputStream(raw):raw;}catch(IOException error){raw.close();throw error;}
        var bounded=new FilterInputStream(decoded){private long count;private void add(int n)throws IOException{if(n>0&&(count=Math.addExact(count,n))>maximum)throw invalid("Complete decoded FASTA byte capacity exceeded");} @Override public int read()throws IOException{int value=in.read();if(value!=-1)add(1);return value;} @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);add(n);return n;}};
        return new PushbackReader(new BufferedReader(new InputStreamReader(bounded,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))),1);
    }
    private static String line(PushbackReader reader,int maximum)throws IOException {
        var out=new StringBuilder();for(int c;(c=reader.read())!=-1;){if(c=='\n')return out.toString();if(c=='\r'){int next=reader.read();if(next!='\n'&&next!=-1)throw invalid("Bare CR FASTA line endings are not qualified for this source profile");return out.toString();}if(out.length()>=maximum)throw invalid("Complete FASTA line/header capacity exceeded");out.append((char)c);}return out.length()==0?null:out.toString();
    }
    private static String utf8(byte[] raw)throws IOException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException error){throw new AssertionError(error);}}
    private static IOException invalid(String cause){return new IOException("MALT052 FASTA/INDEX FAIL2: "+cause+". Required action: retain the original inputs, partial seed index and diagnostics; resolve the named complete-input/identity/capacity issue and retry a NEW directory. No header, sequence, prefix or reference population is silently repaired or dropped.");}
}

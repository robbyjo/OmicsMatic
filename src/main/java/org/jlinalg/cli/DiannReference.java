/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Literal selected three-field sp/tr FASTA input; no identifier or sequence rewriting.
 * Header syntax and hashes cannot establish taxon, database release or target authenticity.
 */
final class DiannReference {
    private DiannReference() { }
    record Limits(int records,long residues,long bytes,int lineChars) {
        Limits {if(records<1||records>10000000||residues<1||residues>10000000000L||bytes<1||bytes>100000000000L||lineChars<1||lineChars>1048576)throw new IllegalArgumentException("Invalid explicit FASTA capacities");}
    }
    record Protein(String header,String firstToken,String accession,String entryName,String gene,String sequence) { }
    record Reference(Map<String,Protein> proteins,long residues) {
        Reference {proteins=Collections.unmodifiableMap(new LinkedHashMap<>(proteins));}
    }
    private static final Pattern GENE=Pattern.compile("(?:^| )GN=([^ ]*)(?= |$)");
    static Reference read(Path supplied,Limits limits,Runnable check)throws IOException {
        Path path=supplied.toAbsolutePath().normalize();
        require(path.toRealPath().equals(path)&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require canonical regular uncompressed reference FASTA");
        require(Files.size(path)<=limits.bytes(),"Complete reference exceeds FASTA byte capacity");check.run();
        var proteins=new LinkedHashMap<String,Protein>();long residues=0;String header=null;var sequence=new StringBuilder();
        try(var bytes=new Limited(Files.newInputStream(path),limits.bytes());var input=new BufferedReader(new InputStreamReader(bytes,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            for(String line;(line=line(input,limits.lineChars(),check))!=null;){
                check.run();require(!line.isEmpty(),"Blank FASTA lines are outside the selected literal format; no lines are removed");
                if(line.charAt(0)=='>') {
                    if(header!=null)store(proteins,header,sequence,limits);
                    header=line.substring(1);sequence=new StringBuilder();
                    require(!header.isEmpty()&&header.chars().noneMatch(c->c<32||c==127),"Invalid literal FASTA header");
                }else {
                    require(header!=null,"Reference residues precede the first FASTA header");
                    require(line.chars().allMatch(c->c>='A'&&c<='Z'),"Require literal uppercase residue letters; no case conversion, trimming, gap/stop removal or biological repair");
                    residues=Math.addExact(residues,line.length());require(residues<=limits.residues(),"Complete FASTA residue capacity exceeded");sequence.append(line);
                }
            }
            if(header!=null)store(proteins,header,sequence,limits);
        }
        require(!proteins.isEmpty(),"Reference FASTA contains no complete records");
        return new Reference(proteins,residues);
    }
    private static void store(Map<String,Protein> proteins,String header,StringBuilder sequence,Limits limits)throws IOException {
        require(!sequence.isEmpty(),"Empty FASTA protein sequence; no record is dropped");
        String token=header.split(" ",2)[0];String[] fields=token.split("\\|",-1);
        require(fields.length==3&&Set.of("sp","tr").contains(fields[0])&&!fields[1].isEmpty()&&!fields[2].isEmpty()
                &&fields[1].indexOf(';')<0&&fields[2].indexOf(';')<0,"Require explicit three-field sp/tr header tokens with unambiguous accession and entry name; no identifier is inferred");
        String gene=null;var matches=GENE.matcher(header);
        if(matches.find()){gene=matches.group(1);require(!gene.isEmpty()&&gene.indexOf(';')<0&&!matches.find(),"Empty, ambiguous or duplicate GN header attribute");}
        require(!proteins.containsKey(fields[1]),"Duplicate literal FASTA accession; no first/last record selection is performed: "+fields[1]);
        require(proteins.size()<limits.records(),"Complete FASTA record capacity exceeded");
        proteins.put(fields[1],new Protein(header,token,fields[1],fields[2],gene,sequence.toString()));
    }
    private static String line(BufferedReader input,int maximum,Runnable check)throws IOException {
        var result=new StringBuilder();
        for(int c;(c=input.read())!=-1;){
            if(c=='\n')return result.toString();
            if(c=='\r'){input.mark(1);int next=input.read();if(next!='\n'&&next!=-1)input.reset();return result.toString();}
            require(result.length()<maximum,"FASTA line capacity exceeded");result.append((char)c);if((result.length()&4095)==0)check.run();
        }
        return result.isEmpty()?null:result.toString();
    }
    private static final class Limited extends FilterInputStream {
        final long maximum;long read;
        Limited(InputStream input,long maximum){super(input);this.maximum=maximum;}
        void count(int n)throws IOException {if(n>0){read=Math.addExact(read,n);require(read<=maximum,"FASTA grew beyond complete byte capacity");}}
        @Override public int read()throws IOException {int value=in.read();if(value!=-1)count(1);return value;}
        @Override public int read(byte[] value,int offset,int length)throws IOException {int n=in.read(value,offset,length);count(n);return n;}
    }
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

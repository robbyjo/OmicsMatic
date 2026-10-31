/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

/** Strict bounded FASTA admission for native HOMER scans and later original-tool staging. */
final class HomerFasta {
    private HomerFasta() { }
    record Limits(long bytes,long decoded,long residues,int sequences,int perSequence,int line) { }
    record Sequence(long ordinal,String id,String header,String residues) { }
    record Summary(long sequences,long residues,long duplicatedIds,long decodedCharacters) { }
    @FunctionalInterface interface Visitor { void accept(Sequence sequence)throws Exception; }
    static Summary read(Path path,Limits limits,boolean allowDuplicates,Visitor visitor)throws Exception{
        regular(path,limits.bytes);long count=0,total=0,characters=0,duplicates=0;
        var seen=new HashSet<String>();String header=null;var sequence=new StringBuilder();
        try(var raw=new BufferedInputStream(Files.newInputStream(path))){
            raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();
            try(var reader=new BufferedReader(new InputStreamReader(a==31&&b==139?new GZIPInputStream(raw):raw,
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))){
                for(;;){
                    var line=new StringBuilder();int c;
                    while((c=reader.read())!=-1){
                        if(++characters>limits.decoded)throw new IOException("FASTA decoded-character capacity exceeded");
                        if(c=='\n')break;if(line.length()>=limits.line)throw new IOException("FASTA line exceeds declared capacity");
                        if(Character.isISOControl(c)&&c!='\r'&&c!='\t')throw new IOException("Control character in FASTA");
                        line.append((char)c);
                    }
                    if(c<0&&line.isEmpty())break;
                    if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);
                    if(line.indexOf("\r")>=0)throw new IOException("Embedded carriage return in FASTA");
                    String text=line.toString();if(text.isEmpty())continue;
                    if(text.startsWith(">")){
                        if(header!=null)visitor.accept(record(count,header,sequence.toString()));
                        if(++count>limits.sequences)throw new IOException("FASTA sequence count capacity exceeded");
                        header=text.substring(1);if(header.length()>8192)throw new IOException("FASTA header exceeds8192characters");
                        String id=record(count,header,"").id;
                        if(!seen.add(id)){duplicates++;if(!allowDuplicates)throw new IOException("Duplicate FASTA identifier; explicitly choose ordinal retention if intended: "+id);}
                        sequence=new StringBuilder();
                    }else{
                        if(header==null)throw new IOException("FASTA residues before first header");
                        for(int i=0;i<text.length();i++)if(Character.isWhitespace(text.charAt(i)))throw new IOException("Whitespace inside a FASTA sequence line; no gap removal is inferred");
                        total=Math.addExact(total,text.length());
                        if(total>limits.residues||(long)sequence.length()+text.length()>limits.perSequence)throw new IOException("FASTA total/per-sequence residue capacity exceeded");
                        sequence.append(text);
                    }
                }
            }
        }
        if(header!=null)visitor.accept(record(count,header,sequence.toString()));
        if(count==0)throw new IOException("FASTA has no records");
        return new Summary(count,total,duplicates,characters);
    }
    private static Sequence record(long ordinal,String header,String residues)throws IOException{
        if(header.isBlank()||Character.isWhitespace(header.charAt(0))||header.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\t'))
            throw new IOException("Require a nonempty FASTA identifier without leading whitespace or controls");
        String id=header.split("[ \t]",2)[0];
        if(id.length()>256)throw new IOException("FASTA identifier exceeds256characters");
        if(id.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)))throw new IOException("Whitespace within the FASTA identifier token");
        return new Sequence(ordinal,id,header,residues);
    }
    static void regular(Path path,long maximum)throws IOException{
        Path absolute=path.toAbsolutePath().normalize();
        if(!Files.isRegularFile(absolute,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(absolute)||!absolute.toRealPath().equals(absolute)||Files.size(absolute)>maximum)
            throw new IOException("Require a real regular input within its explicit byte capacity: "+path);
    }
}


/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.TranscriptSiteMapping.Reference;
import org.jlinalg.genetics.TranscriptSiteMapping.Transcript;
import org.jlinalg.genetics.TranscriptSiteMapping.Variant;

/** Bounded strict TSV admission with protected original bytes and exact decimal P values. */
final class TranscriptSiteInputs {
    record Prepared(List<Variant> variants,Reference reference) { }
    @FunctionalInterface private interface Row {void accept(String[] values,List<String> header)throws Exception;}
    final FumaAssociationInputs transport;
    private final FumaAssociationInputs.Limits limits;
    private final Runnable check;
    private long cells,rows;
    private String context="transcript-site options";

    TranscriptSiteInputs(Path directory,FumaAssociationInputs.Limits limits,Runnable check)throws IOException {
        this.limits=limits;this.check=check;transport=new FumaAssociationInputs(directory,limits,check);
    }
    private void read(Path source,String role,List<String> required,Row consumer)throws Exception {
        context=role+": "+source;Path snapshot=transport.capture(source,role,limits.bytes());
        try(var stream=new BufferedInputStream(Files.newInputStream(snapshot),8192)) {
            String first=line(stream);if(first==null)throw new IOException("Missing transcript-site TSV header: "+context);
            var header=List.of(first.split("\t",257));
            if(header.size()>256||new HashSet<>(header).size()!=header.size()||header.stream().anyMatch(s->s.isBlank()||s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Invalid/duplicate transcript-site header (maximum256 columns): "+context);
            for(String name:required)if(!header.contains(name))throw new IOException("Missing transcript-site column "+name+": "+context);
            count(header.size());int row=1;String value;
            while((value=line(stream))!=null) {
                check.run();context=role+": "+source+", line "+(++row);String[] fields=value.split("\t",257);
                if(fields.length!=header.size()||Arrays.stream(fields).anyMatch(s->s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Malformed transcript-site TSV row: "+context);
                if(++rows>limits.rows())throw new IOException("Combined transcript-site --max-rows exceeded: "+context);
                count(fields.length);consumer.accept(fields,header);
            }
        }
    }
    private String line(BufferedInputStream stream)throws IOException {
        var bytes=new ByteArrayOutputStream();int value;boolean any=false;
        while((value=stream.read())!=-1){any=true;if(value=='\n')break;if(bytes.size()>=limits.line())throw new IOException("Transcript-site input exceeds --max-line-bytes: "+context);bytes.write(value);if((bytes.size()&8191)==0)check.run();}
        if(!any)return null;byte[] raw=bytes.toByteArray();int size=raw.length;if(size>0&&raw[size-1]=='\r')size--;
        try{return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,size)).toString();}catch(IOException invalid){throw new IOException("Invalid transcript-site UTF-8: "+context,invalid);}
    }
    private void count(long count)throws IOException {if(count>limits.cells()-cells)throw new IOException("Combined transcript-site --max-cells exceeded: "+context);cells+=count;}
    private static String field(String[] values,List<String> header,String name){return values[header.indexOf(name)];}
    static String identity(String value)throws IOException {
        if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IOException("Require bounded transcript-site identity without whitespace/control characters");return value;
    }
    static long whole(String value)throws IOException {
        if(!value.matches("[0-9]+"))throw new IOException("Require exact nonnegative decimal transcript-site integer: "+value);
        try{return Long.parseLong(value);}catch(NumberFormatException invalid){throw new IOException("Transcript-site integer exceeds signed64 range",invalid);}
    }
    static BigDecimal decimal(String value)throws IOException {
        if(!value.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require exact decimal transcript-site P value or literal NA for missing input P");
        try{return new BigDecimal(value);}catch(NumberFormatException invalid){throw new IOException("Transcript-site decimal exceeds supported precision/exponent representation",invalid);}
    }
    Prepared prepare(Map<String,String> options)throws Exception {
        var variants=new ArrayList<Variant>();
        read(Path.of(options.get("variants")),"variant-associations",List.of("context","variant_id","chromosome","position","p"),(values,header)->{
            String p=field(values,header,"p");variants.add(new Variant(field(values,header,"context"),field(values,header,"variant_id"),field(values,header,"chromosome"),whole(field(values,header,"position")),p.equals("NA")?null:decimal(p)));
        });
        if(variants.size()!=whole(options.get("expected-variant-rows")))throw new IOException("Variant population differs from --expected-variant-rows: got "+variants.size());
        var transcripts=new ArrayList<Transcript>();
        read(Path.of(options.get("transcripts")),"transcript-reference",List.of("gene_id","transcript_id","chromosome","start","end","strand","gene_biotype"),(values,header)->transcripts.add(new Transcript(field(values,header,"gene_id"),field(values,header,"transcript_id"),field(values,header,"chromosome"),whole(field(values,header,"start")),whole(field(values,header,"end")),field(values,header,"strand"),field(values,header,"gene_biotype"))));
        if(transcripts.size()!=whole(options.get("expected-transcript-rows")))throw new IOException("Transcript population differs from --expected-transcript-rows: got "+transcripts.size());
        return new Prepared(List.copyOf(variants),new Reference(options.get("reference-id"),options.get("reference-build"),options.get("gene-namespace"),options.get("transcript-namespace"),transcripts));
    }
    void verify()throws Exception{transport.verify();}
    long cells(){return cells;}
    String context(){return context;}
}

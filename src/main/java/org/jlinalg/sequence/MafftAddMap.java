/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Complete original --mapout audit for explicitly requested --keeplength.
 * Positions are one-based; an aligned column of -1 means a source-deleted
 * insertion. Every input residue, including every deletion, must be present.
 * Callback rows are provisional until EOF and successful close return an audit.
 * Name bindings are snapshotted before callbacks can modify caller-owned maps.
 */
public final class MafftAddMap {
    private MafftAddMap() { }
    public record Limits(long characters,int lineCharacters,long residues,int records){
        public Limits {
            if(characters<1||lineCharacters<1||residues<1||records<1)
                throw new IllegalArgumentException("Positive complete MAFFT map capacities are required");
        }
        public static Limits defaults(){return new Limits(1_073_741_824,1_048_576,100_000_000,1_000_000);}
    }
    public record Mapping(String originalTaxon,int originalResiduePosition,int inputCharacterColumn,
                          char originalResidue,char sourcePrintedResidue,int alignedColumn) { }
    public record Audit(int records,long residues,long mappedResidues,long deletedResidues,
                        long sourcePrintedCaseChanges,Map<String,Long> deletionsByTaxon){
        public Audit {deletionsByTaxon=Collections.unmodifiableMap(new LinkedHashMap<>(deletionsByTaxon));}
    }
    public static Audit read(Path map,MafftSequences.Data added,MafftSequences.Data aligned,
                             Map<String,String> outputToOriginal,Limits limits,
                             Consumer<Mapping> sink,Runnable cancellation)throws IOException {
        Objects.requireNonNull(map);Objects.requireNonNull(added);Objects.requireNonNull(aligned);
        Objects.requireNonNull(outputToOriginal);Objects.requireNonNull(limits);Objects.requireNonNull(sink);Objects.requireNonNull(cancellation);
        var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return read(new InputStreamReader(Files.newInputStream(map),decoder),added,aligned,outputToOriginal,limits,sink,cancellation);
    }
    public static Audit read(Reader source,MafftSequences.Data added,MafftSequences.Data aligned,
                             Map<String,String> outputToOriginal,Limits limits,
                             Consumer<Mapping> sink,Runnable cancellation)throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(added);Objects.requireNonNull(aligned);
        Objects.requireNonNull(outputToOriginal);Objects.requireNonNull(limits);Objects.requireNonNull(sink);Objects.requireNonNull(cancellation);
        var seen=new HashSet<String>();var deletions=new LinkedHashMap<String,Long>();
        MafftSequences.Row original=null,output=null;String id=null;int position=0,inputColumn=0,outputColumn=0;
        boolean description=false;long[] counts={0,0,0,0,0}; // chars, residues, mapped, deleted, printed case changes
        try(var reader=new BufferedReader(source)){
            outputToOriginal=new LinkedHashMap<>(outputToOriginal);
            if(aligned.layout()!=MafftSequences.Layout.ALIGNMENT||added.alphabet()!=aligned.alphabet()
                    ||!outputToOriginal.keySet().equals(aligned.taxa())
                    ||outputToOriginal.values().stream().anyMatch(Objects::isNull)
                    ||outputToOriginal.values().stream().distinct().count()!=outputToOriginal.size()
                    ||!new HashSet<>(outputToOriginal.values()).containsAll(added.taxa()))
                throw new IllegalArgumentException("Require an aligned output and a complete one-to-one added-row identity mapping");
            String line;
            while((line=line(reader,limits,counts,cancellation))!=null){
                cancellation.run();if(line.isEmpty())continue;
                if(line.startsWith(">")){
                    if(original!=null)finish(original,output,position,inputColumn,outputColumn,description);
                    String opaque=line.substring(1);id=outputToOriginal.get(opaque);
                    if(id==null||!added.taxa().contains(id)||!seen.add(id)||seen.size()>limits.records())
                        throw new IOException("Unknown, duplicate or over-capacity source MAFFT map row");
                    original=added.byTaxon().get(id);output=aligned.byTaxon().get(opaque);
                    position=0;inputColumn=0;outputColumn=0;description=false;deletions.put(id,0L);continue;
                }
                if(original==null)throw new IOException("MAFFT map data precedes its exact taxon header");
                if(line.equals("# letter, position in the original sequence, position in the reference alignment")){
                    if(description||position!=0)throw new IOException("Duplicate/out-of-order MAFFT map description");
                    description=true;continue;
                }
                if(!description)throw new IOException("Missing MAFFT map coordinate description");
                int last=line.lastIndexOf(','),previous=last<0?-1:line.lastIndexOf(',',last-1);
                if(previous<0)throw new IOException("Incomplete MAFFT map data row");
                String letter=line.substring(0,previous).trim(),sourcePosition=line.substring(previous+1,last).trim(),target=line.substring(last+1).trim();
                if(letter.length()!=1||!sourcePosition.matches("[1-9][0-9]*")||!target.equals("-")&&!target.matches("[1-9][0-9]*"))
                    throw new IOException("Malformed MAFFT map symbol/coordinate");
                int sourceIndex=integer(sourcePosition),column=target.equals("-")?-1:integer(target);
                if(sourceIndex!=position+1||++counts[1]>limits.residues())throw new IOException("Incomplete/out-of-order/over-capacity source residue family");
                while(inputColumn<original.sequence().length()&&original.sequence().charAt(inputColumn)=='-')inputColumn++;
                if(inputColumn>=original.sequence().length())throw new IOException("Source MAFFT map has extra input residues");
                char expected=original.sequence().charAt(inputColumn),printed=letter.charAt(0);
                if(upper(expected)!=upper(printed))throw new IOException("MAFFT map changed the original residue symbol");
                if(expected!=printed)counts[4]++;
                if(column==-1){counts[3]++;deletions.put(id,deletions.get(id)+1);}
                else{
                    if(column<=outputColumn||column>aligned.columns())throw new IOException("MAFFT mapped alignment columns are invalid/out of order");
                    for(int i=outputColumn;i<column-1;i++)if(output.sequence().charAt(i)!='-')
                        throw new IOException("Unaccounted output residue before a MAFFT mapped column");
                    if(upper(output.sequence().charAt(column-1))!=upper(expected))
                        throw new IOException("Mapped MAFFT output residue differs from its original");
                    outputColumn=column;counts[2]++;
                }
                position++;inputColumn++;
                sink.accept(new Mapping(id,sourceIndex,inputColumn,expected,printed,column));
            }
            if(original==null)throw new IOException("MAFFT map has no complete row");
            finish(original,output,position,inputColumn,outputColumn,description);
            if(!seen.equals(added.taxa()))throw new IOException("MAFFT map omitted an added taxon");
            if(counts[1]!=added.statistics().residues()||counts[2]+counts[3]!=counts[1])
                throw new IOException("MAFFT map does not cover every original added residue");
            cancellation.run();
        }
        return new Audit(seen.size(),counts[1],counts[2],counts[3],counts[4],deletions);
    }
    private static void finish(MafftSequences.Row original,MafftSequences.Row output,int position,
                               int inputColumn,int outputColumn,boolean description)throws IOException {
        if(!description)throw new IOException("Missing complete MAFFT map coordinate description");
        while(inputColumn<original.sequence().length()&&original.sequence().charAt(inputColumn)=='-')inputColumn++;
        if(inputColumn!=original.sequence().length())throw new IOException("Truncated MAFFT map source residue family");
        for(int i=outputColumn;i<output.sequence().length();i++)if(output.sequence().charAt(i)!='-')
            throw new IOException("Unaccounted late MAFFT output residue");
        if(position<0)throw new IOException("Invalid source residue count");
    }
    private static int integer(String text)throws IOException {
        try{return Integer.parseInt(text);}catch(NumberFormatException failure){throw new IOException("MAFFT map coordinate exceeds its native capacity",failure);}
    }
    private static String line(Reader source,Limits limits,long[] counts,Runnable cancellation)throws IOException {
        var line=new StringBuilder();int value;
        while((value=source.read())>=0){
            cancellation.run();if(++counts[0]>limits.characters())throw new IOException("Complete MAFFT map character capacity exceeded");
            if(value=='\r'||value=='\n')return line.toString();
            if(line.length()>=limits.lineCharacters())throw new IOException("Complete MAFFT map line capacity exceeded");
            line.append((char)value);
        }
        return line.isEmpty()?null:line.toString();
    }
    private static char upper(char c){return c>='a'&&c<='z'?(char)(c-'a'+'A'):c;}
}

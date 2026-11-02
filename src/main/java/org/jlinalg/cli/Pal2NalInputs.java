/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.MafftSequences;
import org.jlinalg.sequence.Pal2Nal;
import org.jlinalg.sequence.Pal2NalClustal;

/** Stable complete snapshots, explicit headers and complete input-file populations. */
final class Pal2NalInputs {
    private Pal2NalInputs() { }
    record Capture(String role,String originalPath,IqtreeFiles.Identity original,String snapshot) { }
    record Header(String header,String snapshot) { }
    record Data(List<Pal2Nal.Sequence> proteins,List<Pal2Nal.Sequence> dna,Map<String,Header> proteinHeaders,
                Map<String,Header> dnaHeaders,String selection,List<String> warnings,List<Map<String,Object>> statistics) {
        Data {proteins=List.copyOf(proteins);dna=List.copyOf(dna);proteinHeaders=Map.copyOf(proteinHeaders);dnaHeaders=Map.copyOf(dnaHeaders);warnings=List.copyOf(warnings);statistics=List.copyOf(statistics);}
    }
    static Capture capture(Path root,Path source,String role,String name,Pal2NalRecipe.Limits limits,long maximum,Runnable check)throws IOException {
        if(source.startsWith(root))throw new IOException("PAL2NAL cannot capture one of its owned output files as a user input");
        var before=IqtreeFiles.identity(source,maximum,check);var used=Pal2NalBundle.inventory(root,limits,check);
        if(used.members()+9>limits.bundleFiles()||before.bytes()+used.bytes()+Pal2NalBundle.RESERVE>limits.bundleBytes())throw new IOException("PAL2NAL snapshot exceeds complete workspace capacities");
        Path destination=root.resolve(name);long count=0;
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){check.run();count=Math.addExact(count,n);if(count>maximum||count>before.bytes())throw new IOException("PAL2NAL source grew during bounded capture");out.write(buffer,0,n);}
        }
        if(!before.equals(IqtreeFiles.identity(source,maximum,check)))throw new IOException("PAL2NAL original file changed during capture");
        var copied=Pal2NalBundle.pin(destination,maximum,check);if(copied.bytes()!=before.bytes()||!copied.sha256().equals(before.sha256()))throw new IOException("PAL2NAL snapshot does not match every original byte");
        return new Capture(role,source.toString(),before,name);
    }
    static void verifyOriginals(List<Capture> captures,long maximum,Runnable check)throws IOException {
        for(Capture capture:captures)if(!capture.original().equals(IqtreeFiles.identity(Path.of(capture.originalPath()),Math.max(maximum,2097152),check)))throw new IOException("PAL2NAL original changed before finalization: "+capture.role());
    }
    static InputStreamReader reader(Path path)throws IOException {
        return new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
    }
    static Data read(Pal2NalRecipe.Spec spec,Pal2NalRecipe.Limits effective,Runnable check)throws IOException {
        var proteins=new ArrayList<Pal2Nal.Sequence>();var dna=new ArrayList<Pal2Nal.Sequence>();var proteinHeaders=new LinkedHashMap<String,Header>();var dnaHeaders=new LinkedHashMap<String,Header>();var warnings=new ArrayList<String>();var stats=new ArrayList<Map<String,Object>>();long retained=0,totalCells=0,decoded=0;String mask=null;
        if(spec.format()==Pal2NalRecipe.Format.FASTA){
            var data=MafftSequences.readFasta(reader(spec.protein()),MafftSequences.Alphabet.PROTEIN,MafftSequences.Symbols.ANY_PRINTABLE,spec.proteinHeaders(),MafftSequences.Layout.ALIGNMENT,MafftSequences.Gaps.ALLOW,effective.parser(),check);
            for(var row:data.rows()){proteins.add(new Pal2Nal.Sequence(row.taxon(),row.sequence()));proteinHeaders.put(row.taxon(),new Header(row.originalHeader(),"inputs/protein.original"));}
            stats.add(Map.of("snapshot","inputs/protein.original","format","FASTA","statistics",data.statistics()));retained=data.statistics().estimatedRetainedBytes();totalCells=data.statistics().cells();decoded=data.statistics().decodedCharacters();
        }else{
            var data=Pal2NalClustal.read(reader(spec.protein()),effective.parser(),check);proteins.addAll(data.rows());
            for(var row:data.rows())proteinHeaders.put(row.id(),new Header(row.id(),"inputs/protein.original"));mask=data.selection();
            stats.add(Map.of("snapshot","inputs/protein.original","format","CLUSTAL","statistics",data.statistics()));retained=data.statistics().estimatedRetainedBytes();totalCells=data.statistics().cells();decoded=data.statistics().decodedCharacters();
            if(spec.selection()==Pal2NalRecipe.Selection.NONE&&mask.indexOf('#')>=0)warnings.add("EXPLICITLY_UNUSED_CLUSTAL_SELECTION: '#' marks are retained in the raw input but selection mode NONE applies every column");
        }
        for(int i=0;i<spec.dna().size();i++){
            long left=effective.parser().estimatedRetainedBytes()-retained;if(left<65536)throw new IOException("Complete combined PAL2NAL input-storage capacity exceeded before the next DNA file");
            var p=effective.parser();var current=new MafftSequences.Limits(p.sequences(),p.sequenceCharacters(),p.cells(),p.decodedCharacters(),p.lineCharacters(),p.headerCharacters(),left);
            var data=MafftSequences.readFasta(reader(spec.dna().get(i)),MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,spec.dnaHeaders(),MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.FORBID,current,check);
            retained=Math.addExact(retained,data.statistics().estimatedRetainedBytes());totalCells=Math.addExact(totalCells,data.statistics().cells());decoded=Math.addExact(decoded,data.statistics().decodedCharacters());
            if(retained>p.estimatedRetainedBytes()||totalCells>p.cells()||decoded>p.decodedCharacters()||(long)dna.size()+data.rows().size()>p.sequences())throw new IOException("Complete combined PAL2NAL input population exceeds storage/cell/character/row capacity");
            String name=String.format(Locale.ROOT,"inputs/dna-%04d.fasta",i+1);
            for(var row:data.rows()){
                if(dnaHeaders.putIfAbsent(row.taxon(),new Header(row.originalHeader(),name))!=null)throw new IOException("Duplicate DNA ID across complete FASTA files: "+row.taxon());
                dna.add(new Pal2Nal.Sequence(row.taxon(),row.sequence()));
            }
            stats.add(Map.of("snapshot",name,"format","FASTA","statistics",data.statistics()));
        }
        if(spec.selection()==Pal2NalRecipe.Selection.NONE)mask=null;
        else if(spec.selection()==Pal2NalRecipe.Selection.MASK_FILE){
            StringBuilder value=new StringBuilder();long maskDecoded=0;
            try(var in=reader(spec.mask())){for(int c=in.read();c>=0;c=in.read()){
                check.run();maskDecoded++;
                if(value.length()>proteins.get(0).sequence().length()+2)throw new IOException("Complete PAL2NAL selection mask exceeds alignment width");
                if(Math.addExact(decoded,maskDecoded)>effective.parser().decodedCharacters()||Math.addExact(retained,Math.multiplyExact(maskDecoded,8))>effective.parser().estimatedRetainedBytes())throw new IOException("Complete combined PAL2NAL selection character/storage capacity exceeded");
                value.append((char)c);
            }}
            mask=value.toString();if(mask.endsWith("\r\n"))mask=mask.substring(0,mask.length()-2);else if(mask.endsWith("\n")||mask.endsWith("\r"))mask=mask.substring(0,mask.length()-1);
            stats.add(Map.of("snapshot","inputs/selection.mask","format","LITERAL_COLUMN_MASK","decoded_characters",maskDecoded,"positions",mask.length()));
        }
        if(spec.selection()!=Pal2NalRecipe.Selection.NONE&&(mask==null||mask.length()!=proteins.get(0).sequence().length()||mask.indexOf('#')<0||mask.chars().anyMatch(c->c!='#'&&c!=' ')))throw new IOException("PAL2NAL selection needs one literal '#' or space per column and at least one selected column");
        check.run();return new Data(proteins,dna,proteinHeaders,dnaHeaders,mask,warnings,stats);
    }
    static Pal2Nal.Options options(Pal2Nal.Options source,String mask){return new Pal2Nal.Options(source.codonTable(),source.pairing(),source.matching(),source.placement(),source.gapStopFilter(),source.removeMismatchColumns(),mask,source.normalizeDocumentedSymbols(),source.normalizeTfastx(),source.minimumVerifiedResidues(),source.minimumVerifiedFraction());}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Streaming source-compatible SAM-to-tag checks for original HOMER 5.1.
 * This deliberately follows the original matching-block/CIGAR projection,
 * which differs from a standard SAM reference-span projection. It does not
 * perform alignment, deduplication, peak calling or statistical calibration.
 */
public final class HomerSamTags {
    private HomerSamTags() { }
    /** Original map-quality/secondary filtering modes; KEEP_ONE also retains supplementary rows. */
    public enum Mapping { UNIQUE, KEEP_ONE, KEEP_ALL }
    /** Original mate-flag selection; SECOND excludes rows without flag0x80, including unpaired reads. */
    public enum Mates { BOTH, FIRST, SECOND }
    /** Stored length is the selected matching block or absolute nonzero SAM TLEN with SEQ-length fallback. */
    public enum Length { MATCHING_BLOCK, PAIRED_TEMPLATE }
    /** Both original default and -rmsoft ignore S; OMIT_SN implements the original -omitSN choice. */
    public enum SoftClips { ORIGINAL_DEFAULT, ORIGINAL_RMSOFT, OMIT_SN }
    /** Explicit original parser controls; input alignment preprocessing is not inferred. */
    public record Options(Mapping mapping,Mates mates,Length length,SoftClips softClips,int minimumMapq,
                          int minimumOriginalLength,int maximumOriginalLength,boolean flipStrands,
                          boolean strandSpecificPaired) {
        public Options {if(mapping==null||mates==null||length==null||softClips==null||minimumMapq<0||minimumMapq>255||minimumOriginalLength<0||maximumOriginalLength<minimumOriginalLength||maximumOriginalLength>1_000_000_000)throw new IllegalArgumentException("Invalid explicit original SAM options");}
    }
    /** Streaming capacities; no alignment population or aggregate tag map is retained internally. */
    public record Limits(long records,long decodedCharacters,int lineCharacters,int cigarOperations) {
        public Limits {if(records<1||records>100_000_000||decodedCharacters<1||decodedCharacters>1_000_000_000_000L||lineCharacters<32||lineCharacters>1_048_576||cigarOperations<1||cigarOperations>100_000)throw new IllegalArgumentException("Invalid bounded SAM audit capacities");}
    }
    /** Source tag identity; length remains a full integer even though the original comparator truncates it. */
    public record Tag(String contig,int position1,char strand,int storedLength) { }
    /** Every alignment receives an explicit disposition; filtered records remain available to the sink. */
    public enum Disposition { ACCEPTED, UNMAPPED_REFERENCE, UNMAPPED_FLAG, LENGTH_FILTER, ZERO_POSITION,
        MISSING_CIGAR, MATE_FILTER, SECONDARY_FILTER, MAPQ_FILTER, CIGAR_OMITTED }
    /** One source alignment and its unchanged text; only accepted rows have a translated tag/weight. */
    public record Row(long ordinal,long sourceLine,String sourceText,String readName,int flags,
                      Disposition disposition,Tag tag,double weight) { }
    /** Complete input counts and two distinct source populations; neither count implies unique fragments. */
    public record Summary(long alignmentRecords,long headerRecords,long acceptedRecords,double acceptedWeight,
                          double originalAllPrimaryWeight,long decodedCharacters,Map<Disposition,Long> dispositions) {
        public Summary {var copy=new EnumMap<Disposition,Long>(Disposition.class);copy.putAll(dispositions);dispositions=Collections.unmodifiableMap(copy);}
    }
    /** Caller can stream every original row to a bounded provenance ledger or external sort. */
    @FunctionalInterface public interface Sink {void accept(Row row)throws IOException;}
    /** Caller deadline/cancellation/immutable-resource check. */
    @FunctionalInterface public interface Check {void run()throws IOException;}
    /** Original selected matching-block projection, separate from a standard reference span. */
    public record Cigar(int anchorOffset,int matchingBlockLength,boolean omitted) { }

    /**
     * Check a complete canonical SAM stream with original 5.1 tag semantics.
     * Every alignment, including excluded rows, reaches the sink. Headers and
     * source lines are counted; raw input bytes remain the caller's provenance
     * responsibility. Only translated tag anchors are checked against the exact
     * dictionary; this is not a full SAM alignment/reference-span validator.
     * Paired-flag rows carry weight0.5 even with one mate selected. The source
     * does not remove duplicate/supplementary flags here. No aggregate map is
     * retained, so callers can use bounded disk sorting for large populations.
     * @param source reader, owned and closed by the caller
     * @param referenceLengths exact reference dictionary, without inferred aliases
     * @param options explicit original projection and filtering choices
     * @param limits input and operation capacities
     * @param sink consumer of every alignment disposition
     * @param check caller deadline or immutable-resource check
     * @return complete original input/eligible weighted population counters
     * @throws IOException on malformed/unsupported input, bounds or caller failure
     */
    public static Summary read(Reader source,Map<String,Long> referenceLengths,Options options,Limits limits,
                               Sink sink,Check check)throws IOException {
        if(source==null||referenceLengths==null||referenceLengths.isEmpty()||options==null||limits==null||sink==null||check==null)throw new IllegalArgumentException("Require source, exact reference, explicit controls and streaming callbacks");
        for(var e:referenceLengths.entrySet()){identity(e.getKey());if(e.getValue()==null||e.getValue()<1||e.getValue()>1_000_000_000)throw new IllegalArgumentException("Invalid bounded SAM reference length");}
        var counts=new EnumMap<Disposition,Long>(Disposition.class);long records=0,headers=0,accepted=0,characters=0,lineNumber=0;double weight=0,allPrimary=0;var reader=source instanceof BufferedReader b?b:new BufferedReader(source);check.run();
        for(TextLine raw;(raw=next(reader,limits.lineCharacters()))!=null;){
            String line=raw.text();lineNumber++;characters=Math.addExact(characters,raw.characters());if(characters>limits.decodedCharacters())throw new IOException("SAM decoded-character capacity exceeded");
            if((lineNumber&4095)==0)check.run();if(line.startsWith("@")){headers++;if(headers>1_000_000)throw new IOException("SAM header capacity exceeded");continue;}
            if(line.isBlank())throw new IOException("Blank SAM alignment lines are not admitted");if(records>=limits.records())throw new IOException("SAM alignment-record capacity exceeded");records++;
            String[] f=line.split("\t",-1);if(f.length<11||f.length>1000)throw new IOException("Require canonical SAM with11to1000 fields");identity(f[0]);if(f[0].getBytes(StandardCharsets.UTF_8).length>254)throw new IOException("SAM read name exceeds254UTF8bytes");identity(f[2]);
            int flags=integer(f[1],0,65535),position=integer(f[3],0,1_000_000_000),mapq=integer(f[4],0,255),template=integer(f[8],-1_000_000_000,1_000_000_000);if(f[9].isEmpty())throw new IOException("Empty SAM sequence field");
            for(int i=0;i<f[9].length();i++){char base=f[9].charAt(i);if(base>127||Character.isWhitespace(base))throw new IOException("Require ASCII SAM sequence for original byte-length semantics");}
            int originalLength=f[9].length();if(options.length()==Length.PAIRED_TEMPLATE&&template!=0)originalLength=Math.abs(template);Disposition disposition=null;Tag tag=null;double rowWeight=0;
            if(f[2].equals("*"))disposition=Disposition.UNMAPPED_REFERENCE;
            else if((flags&4)!=0)disposition=Disposition.UNMAPPED_FLAG;
            else if(originalLength<options.minimumOriginalLength()||originalLength>options.maximumOriginalLength())disposition=Disposition.LENGTH_FILTER;
            else if(position==0)disposition=Disposition.ZERO_POSITION;
            else if(f[5].equals("*"))disposition=Disposition.MISSING_CIGAR;
            else if(options.mates()==Mates.FIRST&&(flags&128)!=0||options.mates()==Mates.SECOND&&(flags&128)==0)disposition=Disposition.MATE_FILTER;
            else {
                double potential=(flags&1)!=0?0.5:1.0;if((flags&256)==0)allPrimary+=potential;
                if(options.mapping()!=Mapping.KEEP_ALL&&(flags&256)!=0)disposition=Disposition.SECONDARY_FILTER;
                else if(options.mapping()==Mapping.UNIQUE&&mapq<options.minimumMapq())disposition=Disposition.MAPQ_FILTER;
                else {
                    boolean minus=(flags&16)!=0;if(options.flipStrands())minus=!minus;if(options.strandSpecificPaired()&&(flags&128)!=0)minus=!minus;char direction=minus?'-':'+';Cigar cigar=coordinates(f[5],direction,options.softClips(),limits.cigarOperations());
                    if(cigar.omitted())disposition=Disposition.CIGAR_OMITTED;
                    else {
                        long point=(long)position+cigar.anchorOffset();Long length=referenceLengths.get(f[2]);if(length==null||point<1||point>length)throw new IOException("Original translated SAM tag anchor is outside exact reference: "+f[2]+":"+point);
                        int stored=options.length()==Length.PAIRED_TEMPLATE?originalLength:cigar.matchingBlockLength();if(stored<1||stored>1_000_000_000)throw new IOException("Original SAM stored length is outside admitted positive bounds");tag=new Tag(f[2],(int)point,direction,stored);rowWeight=potential;weight+=potential;accepted++;disposition=Disposition.ACCEPTED;
                    }
                }
            }
            counts.merge(disposition,1L,Math::addExact);sink.accept(new Row(records,lineNumber,line,f[0],flags,disposition,tag,rowWeight));
        }
        if(records==0)throw new IOException("SAM has no alignment records");check.run();return new Summary(records,headers,accepted,weight,allPrimary,characters,counts);
    }

    /**
     * Original5.1 CIGAR matching-block projection. I subtracts position; H/P/X
     * advance and break blocks; S is ignored by both original default/-rmsoft.
     * These are recorded source semantics, not a standard SAM interpretation.
     * Unsupported/no-matching-block inputs fail instead of fabricating a tag.
     * @param text canonical positive-length CIGAR operators
     * @param strand effective original strand after any explicit flips
     * @param softClips original S/N disposition choice
     * @param maximumOperations maximum CIGAR operator count
     * @return selected original anchor offset and matching-block length
     * @throws IOException on malformed/unsupported or source-unsafe coordinates
     */
    public static Cigar coordinates(String text,char strand,SoftClips softClips,int maximumOperations)throws IOException {
        if(text==null||text.isEmpty()||strand!='+'&&strand!='-'||softClips==null||maximumOperations<1||maximumOperations>100_000)throw new IllegalArgumentException("Require bounded original CIGAR controls");
        long total=0,firstStart=-1,firstEnd=-1,lastStart=-1,lastEnd=-1,number=0;boolean firstActive=false,lastActive=false,digits=false,matched=false;int operations=0;
        for(int i=0;i<text.length();i++){
            char code=text.charAt(i);if(code>='0'&&code<='9'){number=number*10+code-'0';if(number>1_000_000_000)throw new IOException("CIGAR operator length exceeds source-safe capacity");digits=true;continue;}
            if(!digits||number==0||++operations>maximumOperations)throw new IOException("Malformed CIGAR or operator capacity exceeded");
            if("MIDNSHP=X".indexOf(code)<0)throw new IOException("Unsupported CIGAR operator");if(softClips==SoftClips.OMIT_SN&&(code=='S'||code=='N'))return new Cigar(0,0,true);
            if(code=='M'||code=='='){
                if(firstStart<0){firstStart=total;firstActive=true;}if(firstActive)firstEnd=total+number;if(!lastActive){lastStart=total;lastActive=true;}if(lastActive)lastEnd=total+number;total+=number;matched=true;
            }else if(code=='D'||code=='N'||code=='X'||code=='H'||code=='P'){total+=number;firstActive=false;lastActive=false;}
            else if(code=='I')total-=number;
            if(total< -1_000_000_000||total>1_000_000_000||firstEnd>1_000_000_000||lastEnd>1_000_000_000)throw new IOException("Original CIGAR coordinate arithmetic capacity exceeded");number=0;digits=false;
        }
        if(digits||!matched)throw new IOException("CIGAR has no complete original M/= matching block");long offset=strand=='+'?firstStart:lastEnd-1,span=strand=='+'?firstEnd-firstStart:lastEnd-lastStart;
        if(offset== -12345678||offset== -12345679)throw new IOException("CIGAR coordinate collides with an original parser error/omit sentinel; recovery is outside this profile");
        if(offset< -1_000_000_000||offset>1_000_000_000||span<1||span>1_000_000_000)throw new IOException("Unsupported original CIGAR matching-block projection");return new Cigar((int)offset,(int)span,false);
    }
    private static int integer(String text,int minimum,int maximum)throws IOException{try{if(text.isEmpty()||!text.matches("-?[0-9]+"))throw new NumberFormatException();int value=Integer.parseInt(text);if(value<minimum||value>maximum)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("SAM integer outside admitted bounds: "+text,failure);}}
    private static void identity(String text)throws IOException{if(text==null||text.isBlank())throw new IOException("Empty SAM identity");for(int i=0;i<text.length();i++)if(Character.isWhitespace(text.charAt(i))||Character.isISOControl(text.charAt(i)))throw new IOException("Whitespace/control in SAM identity");}
    private record TextLine(String text,int characters) { }
    private static TextLine next(BufferedReader reader,int maximum)throws IOException{var line=new StringBuilder();int c,count=0;while((c=reader.read())!=-1){count++;if(c=='\n')break;if(c=='\r'){int after=reader.read();if(after!=-1)count++;if(after!='\n'&&after!=-1)throw new IOException("Embedded SAM carriage return");break;}if(line.length()>=maximum||c<32&&c!='\t')throw new IOException("Oversized/control-containing SAM line");line.append((char)c);}return count==0?null:new TextLine(line.toString(),count);}
}

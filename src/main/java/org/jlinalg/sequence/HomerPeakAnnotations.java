/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Independent identity and nearest-TSS checks for original HOMER supplied-GTF annotation.
 * Feature labels and annotation enrichment statistics remain original-engine results.
 */
public final class HomerPeakAnnotations {
    private HomerPeakAnnotations() { }
    private static final Pattern GENE=Pattern.compile("(?:^|;)\\s*gene_id \"([^\"]+)\"(?=;|$)");
    private static final Pattern TRANSCRIPT=Pattern.compile("(?:^|;)\\s*transcript_id \"([^\"]+)\"(?=;|$)");
    /** A transcript aggregated from compatible selected exon rows; original parseGTF sorts before grouping. */
    public record Transcript(String id,String gene,String contig,char strand,int start1,int end1,int sourceBlocks) {
        public int tss1(){return strand=='+'?start1:end1;}
        /** Original annotatePeaks metadata removes a terminal dot followed by decimal digits. */
        public String metadataGeneId(){return unversionedMetadataId(gene);}
    }
    public static String unversionedMetadataId(String id){return id.replaceFirst("\\.[0-9]+$","");}
    public record Summary(long records,long exons,long plusAssignedExons,int noncontiguousSourceBlocks,
                          List<Transcript> transcripts) {
        public Summary {transcripts=List.copyOf(transcripts);}
    }
    public record Candidate(Transcript transcript,long signedDistance) { }
    public record Nearest(long midpoint1,long absoluteDistance,List<Candidate> candidates) {
        public Nearest {candidates=List.copyOf(candidates);}
        /** An original choice is valid only if both its unmodified ID and signed distance match. */
        public boolean accepts(String id,long distance){return candidates.stream().anyMatch(c->c.transcript.id.equals(id)&&c.signedDistance==distance);}
        public long distinctAssignments(){return candidates.stream().map(c->c.transcript.id+"\t"+c.signedDistance).distinct().count();}
    }
    /** Indexed exact-contig TSS lookup. No contig aliases, genomic feature priorities or gene symbols are inferred. */
    public static final class Index {
        private final Map<String,TreeMap<Integer,List<Transcript>>> contigs=new HashMap<>();
        public Index(Summary summary){for(Transcript t:summary.transcripts)contigs.computeIfAbsent(t.contig,k->new TreeMap<>()).computeIfAbsent(t.tss1(),k->new ArrayList<>()).add(t);}
        public Nearest nearest(String contig,int start1,int end1){
            if(start1<1||end1<start1)throw new IllegalArgumentException("Require positive one-based inclusive peak coordinates");
            long midpoint=((long)start1+end1)/2;var positions=contigs.get(contig);
            if(positions==null)return new Nearest(midpoint,-1,List.of());
            var left=positions.floorEntry((int)midpoint);var right=positions.ceilingEntry((int)midpoint);
            long distance=Math.min(left==null?Long.MAX_VALUE:midpoint-left.getKey(),right==null?Long.MAX_VALUE:right.getKey()-midpoint);
            var result=new ArrayList<Candidate>();
            if(left!=null&&midpoint-left.getKey()==distance)add(result,left.getValue(),midpoint);
            if(right!=null&&right.getKey()-midpoint==distance&&(left==null||!right.getKey().equals(left.getKey())))add(result,right.getValue(),midpoint);
            return new Nearest(midpoint,distance,result);
        }
        private static void add(List<Candidate> result,List<Transcript> source,long midpoint){for(Transcript t:source)result.add(new Candidate(t,(midpoint-t.tss1())*(t.strand=='+'?1:-1)));}
    }
    /** Read unchanged GTF9 rows. Only lowercase exon features define transcript groups, as in the original.
     * Original parseGTF sorts by contig,gene,transcript,start,end before grouping.
     * Aggregate compatible transcript rows independently while retaining source-block counts.
     * The caller chooses whether the original dot-to-plus strand convention is permitted.
     */
    public static Summary readGtf(Reader source,Map<String,Long> referenceLengths,boolean allowDotStrand,
                                  long maximumRecords,int maximumSegments,int maximumLineCharacters)throws IOException {
        if(referenceLengths.isEmpty()||maximumRecords<1||maximumSegments<1||maximumLineCharacters<1)throw new IllegalArgumentException("Positive GTF capacities and exact reference lengths are required");
        var rows=new HashMap<String,Transcript>();var identities=new HashMap<String,String>();var metadataGenes=new HashMap<String,String>();
        long records=0,exons=0,dots=0,lineNumber=0;String previousId=null;int extraBlocks=0;
        BufferedReader input=source instanceof BufferedReader b?b:new BufferedReader(source);
        for(String line;(line=boundedLine(input,maximumLineCharacters))!=null;){
            lineNumber++;if(line.isBlank()||line.startsWith("#"))continue;
            if(++records>maximumRecords)throw error(lineNumber,"GTF record capacity exceeded");
            String[] f=line.split("\t",-1);if(f.length!=9)throw error(lineNumber,"Require nine tab-delimited GTF fields");
            Long length=referenceLengths.get(f[0]);if(length==null)throw error(lineNumber,"GTF contig is absent from the exact reference dictionary");
            int start=coordinate(f[3],lineNumber),end=coordinate(f[4],lineNumber);
            if(start<1||end<start||end>length||end>1_000_000_000)throw error(lineNumber,"GTF one-based inclusive interval is outside reference or original integer-work bounds");
            if(!List.of("+","-",".").contains(f[6]))throw error(lineNumber,"GTF strand must be +,- or dot");
            if(!f[2].equals("exon"))continue;
            if(f[6].equals(".")&&!allowDotStrand)throw error(lineNumber,"Dot-strand exon needs explicit original-plus permission");
            if(f[6].equals("."))dots++;
            String gene=attribute(GENE,f[8],lineNumber),id=attribute(TRANSCRIPT,f[8],lineNumber);char strand=f[6].equals("-")?'-':'+';
            if(id.contains("-HOMER"))throw error(lineNumber,"Transcript ID contains HOMER's reserved truncation/suffix delimiter");
            String metadataId=unversionedMetadataId(id),metadataGene=unversionedMetadataId(gene);String priorGene=metadataGenes.putIfAbsent(metadataId,metadataGene);
            if(priorGene!=null&&!priorGene.equals(metadataGene))throw error(lineNumber,"Version-stripped transcript IDs map to different genes;original metadata would conflate them");
            String identity=gene+"\t"+f[0]+"\t"+strand;String prior=identities.putIfAbsent(id,identity);
            if(prior!=null&&!prior.equals(identity))throw error(lineNumber,"A transcript ID has inconsistent gene,contig or strand; original parsing would overwrite or omit records");
            Transcript priorRow=rows.get(id);boolean newBlock=!id.equals(previousId);
            if(priorRow==null){
                if(rows.size()>=maximumSegments)throw error(lineNumber,"GTF transcript capacity exceeded");
                rows.put(id,new Transcript(id,gene,f[0],strand,start,end,1));
            }else{
                if(newBlock)extraBlocks++;
                rows.put(id,new Transcript(id,gene,f[0],strand,Math.min(priorRow.start1,start),Math.max(priorRow.end1,end),priorRow.sourceBlocks+(newBlock?1:0)));
            }
            previousId=id;
            exons++;
        }
        if(exons==0)throw new IOException("GTF has no usable lowercase exon records");
        var ordered=new ArrayList<>(rows.values());ordered.sort(Comparator.comparing(Transcript::contig).thenComparing(Transcript::gene).thenComparing(Transcript::id));
        return new Summary(records,exons,dots,extraBlocks,ordered);
    }
    private static String attribute(Pattern pattern,String text,long line)throws IOException{
        var matcher=pattern.matcher(text);if(!matcher.find())throw error(line,"Exons require single-space quoted gene_id/transcript_id attributes accepted by the original parser");
        String value=matcher.group(1);if(value.isBlank()||value.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||matcher.find())throw error(line,"GTF parent identifiers must be unique attributes without whitespace/control characters");
        return value;
    }
    private static int coordinate(String text,long line)throws IOException{try{return Integer.parseInt(text);}catch(NumberFormatException failure){throw error(line,"GTF coordinates must be exact bounded integers");}}
    private static IOException error(long line,String text){return new IOException(text+" at GTF line "+line);}
    private static String boundedLine(BufferedReader input,int maximum)throws IOException{
        var line=new StringBuilder();int c;
        while((c=input.read())!=-1&&c!='\n'){
            if(c=='\r'){int after=input.read();if(after!='\n'&&after!=-1)throw new IOException("Unexpected embedded GTF carriage return");break;}
            if(line.length()>=maximum||c<32&&c!='\t')throw new IOException("Oversized/control-containing GTF line");line.append((char)c);
        }
        return c==-1&&line.isEmpty()?null:line.toString();
    }
}

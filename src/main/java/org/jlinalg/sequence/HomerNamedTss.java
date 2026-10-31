/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Native nearest-TSS checks for original named-genome default promoter resources.
 * Raw resource IDs and promoter windows remain separate from the original
 * wrapper's collapsed promoter IDs. No gene namespace or symbol is inferred.
 */
public final class HomerNamedTss {
    private HomerNamedTss() { }
    /** One unchanged source promoter window and its default 2000-base-offset anchor. */
    public record Tss(long sourceOrdinal,String rawId,String contig,int windowStart1,int windowEnd1,
                      char strand,int position1) {
        /** Original annotatePeaks removes everything from the first -HOMER marker. */
        public String originalPromoterId(){int i=rawId.indexOf("-HOMER");return i<0?rawId:rawId.substring(0,i);}
    }
    /** Complete resource records and explicit irregularities, without filtering or clipping. */
    public record Dataset(List<Tss> records,Map<String,Long> referenceLengths,long duplicateRawIdRows,
                          int collapsedOriginalIdGroups,long reservedNameRows,long outOfReferenceAnchors,
                          long missingReferenceRecords,long nonpositiveWindowStarts,long windowEndOverruns) {
        public Dataset {records=List.copyOf(records);referenceLengths=Map.copyOf(referenceLengths);}
    }
    /** A compatible raw source record and the original signed distance in gene orientation. */
    public record Candidate(Tss source,long signedDistance) { }
    /** All minimum-absolute-distance records, including collapsed-ID and strand ambiguities. */
    public record Nearest(int midpoint1,long absoluteDistance,List<Candidate> compatible) {
        public Nearest {compatible=List.copyOf(compatible);}
        public boolean acceptsOriginal(String promoterId,long signedDistance){return compatible.stream().anyMatch(c->c.source.originalPromoterId().equals(promoterId)&&c.signedDistance==signedDistance);}
        public long distinctOriginalAssignments(){return compatible.stream().map(c->c.source.originalPromoterId()+"\t"+c.signedDistance).distinct().count();}
    }
    /** Exact-contig anchor index; no cross-contig match or invented single tie choice. */
    public static final class Index {
        private final Map<String,TreeMap<Integer,List<Tss>>> contigs=new HashMap<>();
        private final Map<String,Long> lengths;
        private final int effectiveRecords,inactiveDuplicateRows;
        public Index(Dataset dataset){lengths=dataset.referenceLengths();var last=new HashMap<String,Tss>();for(var t:dataset.records())last.put(t.contig()+"\t"+t.rawId(),t);effectiveRecords=last.size();inactiveDuplicateRows=dataset.records().size()-effectiveRecords;for(var t:last.values())contigs.computeIfAbsent(t.contig(),k->new TreeMap<>()).computeIfAbsent(t.position1(),k->new ArrayList<>()).add(t);}
        /** Original helper retains the last row per exact contig/raw ID for lookup. */
        public int effectiveRecords(){return effectiveRecords;}
        /** All overwritten raw rows remain in Dataset; this count records their lookup exclusion. */
        public int inactiveDuplicateRows(){return inactiveDuplicateRows;}
        public Nearest nearest(HomerNamedFeatures.Query query){
            Long length=lengths.get(query.contig());if(length==null||query.end1()>length)throw new IllegalArgumentException("TSS query outside exact reference dictionary");
            int middle=query.midpoint1();var positions=contigs.get(query.contig());if(positions==null)return new Nearest(middle,-1,List.of());
            var left=positions.floorEntry(middle);var right=positions.ceilingEntry(middle);long best=Math.min(left==null?Long.MAX_VALUE:(long)middle-left.getKey(),right==null?Long.MAX_VALUE:(long)right.getKey()-middle);var candidates=new ArrayList<Candidate>();
            if(left!=null&&(long)middle-left.getKey()==best)add(candidates,left.getValue(),middle);
            if(right!=null&&(long)right.getKey()-middle==best&&(left==null||!left.getKey().equals(right.getKey())))add(candidates,right.getValue(),middle);
            return new Nearest(middle,best,candidates);
        }
        private static void add(List<Candidate> result,List<Tss> source,int middle){for(var t:source)result.add(new Candidate(t,((long)middle-t.position1())*(t.strand()=='+'?1:-1)));}
    }

    /**
     * Read original HOMER5 default TSS windows without removing source records.
     * The admitted hg38/mm10 resources have five columns and end-start=4000;
     * the wrapper uses its fixed -2000 promoter offset. Promoter windows may
     * extend beyond reference ends while their anchors remain valid. Invalid
     * anchors/missing contigs and duplicate raw IDs have independent explicit
     * retention permissions. Original -HOMER ID collapse is recorded, never
     * applied to the preserved raw resource IDs. If duplicates are retained,
     * Index applies the original per-contig last-row rule only to lookup;
     * Dataset still retains every original row.
     * @param source reader owned and closed by the caller
     * @param referenceLengths exact named-reference dictionary
     * @param maximumRecords maximum number of source promoters
     * @param maximumLineCharacters bound on a resource line
     * @param retainOutOfReferenceAnchors retain and flag original invalid anchors
     * @param retainDuplicateRawIds retain and flag repeated identical source IDs
     * @param check caller deadline or immutable-marker check
     * @return every source promoter and complete resource irregularity counts
     * @throws IOException if source shape, capacities or explicit policies fail
     */
    public static Dataset read(Reader source,Map<String,Long> referenceLengths,int maximumRecords,
                               int maximumLineCharacters,boolean retainOutOfReferenceAnchors,
                               boolean retainDuplicateRawIds,HomerNamedFeatures.Check check)throws IOException {
        if(source==null||referenceLengths==null||referenceLengths.isEmpty()||maximumRecords<1||maximumRecords>1_000_000||maximumLineCharacters<32||maximumLineCharacters>1_048_576||check==null)throw new IllegalArgumentException("Require bounded original TSS resource controls");
        for(var e:referenceLengths.entrySet()){identity(e.getKey());if(e.getValue()==null||e.getValue()<1||e.getValue()>1_000_000_000)throw new IllegalArgumentException("Invalid exact TSS reference length");}
        var records=new ArrayList<Tss>();var rawIds=new HashSet<String>();var displayFirstRaw=new HashMap<String,String>();var collapsed=new HashSet<String>();long duplicates=0,reserved=0,invalid=0,missing=0,nonpositive=0,overruns=0,heapEstimate=0;var reader=source instanceof BufferedReader b?b:new BufferedReader(source);check.run();
        for(String line;(line=next(reader,maximumLineCharacters))!=null;){
            if(line.isBlank()||line.startsWith("#"))continue;if(records.size()>=maximumRecords)throw new IOException("Original TSS record capacity exceeded");
            String[] f=line.split("\t",-1);if(f.length!=5)throw new IOException("Require unchanged original five-column named TSS resource");identity(f[0]);identity(f[1]);
            int start=integer(f[2]),end=integer(f[3]);if((long)end-start!=4000||(long)start+end<Integer.MIN_VALUE||(long)start+end>Integer.MAX_VALUE)throw new IOException("Named default TSS resource requires4000-base span and safe original32-bit midpoint arithmetic;no promoter offset is inferred");
            char strand=switch(f[4]){case "+","0"->'+';case "-","1"->'-';default->throw new IOException("Invalid original named TSS strand");};
            int anchor=strand=='+'?Math.addExact(start,2000):Math.subtractExact(end,2000);var record=new Tss(records.size()+1L,f[0],f[1],start,end,strand,anchor);String display=record.originalPromoterId();if(display.isBlank())throw new IOException("Original TSS ID collapses to an empty observed promoter ID");
            Long length=referenceLengths.get(f[1]);boolean outside=length==null||anchor<1||anchor>length;if(outside)invalid++;if(length==null)missing++;if(start<1)nonpositive++;if(length==null||end>length)overruns++;
            if(outside&&!retainOutOfReferenceAnchors)throw new IOException("Original TSS row "+record.sourceOrdinal()+" ("+record.rawId()+","+record.contig()+":"+anchor+") has missing-reference or out-of-reference anchor;review and explicitly retain original anchors only where appropriate");
            if(!rawIds.add(f[0])){duplicates++;if(!retainDuplicateRawIds)throw new IOException("Duplicate original TSS raw ID;explicit retention is required,not a deduplicated resource");}
            if(f[0].contains("-HOMER"))reserved++;String previous=displayFirstRaw.putIfAbsent(display,f[0]);if(previous!=null&&!previous.equals(f[0]))collapsed.add(display);
            heapEstimate=Math.addExact(heapEstimate,512L+4L*(f[0].length()+f[1].length()));if(heapEstimate>Runtime.getRuntime().maxMemory()/4)throw new IOException("Original TSS resource exceeds conservative native record/index heap capacity");records.add(record);if((records.size()&4095)==0)check.run();
        }
        if(records.isEmpty())throw new IOException("Original TSS resource has no usable records");check.run();return new Dataset(records,referenceLengths,duplicates,collapsed.size(),reserved,invalid,missing,nonpositive,overruns);
    }
    private static int integer(String text)throws IOException{try{int first=text.startsWith("-")?1:0;if(first==text.length())throw new NumberFormatException();for(int i=first;i<text.length();i++)if(text.charAt(i)<'0'||text.charAt(i)>'9')throw new NumberFormatException();return Integer.parseInt(text);}catch(NumberFormatException failure){throw new IOException("Invalid32-bit named TSS coordinate: "+text,failure);}}
    private static void identity(String text)throws IOException{if(text==null||text.isBlank())throw new IOException("Empty TSS identity");for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i)))throw new IOException("Control character in TSS identity");}
    private static String next(BufferedReader reader,int maximum)throws IOException{
        var line=new StringBuilder();int c;while((c=reader.read())!=-1&&c!='\n'){if(c=='\r'){int after=reader.read();if(after!='\n'&&after!=-1)throw new IOException("Embedded TSS carriage return");break;}if(line.length()>=maximum||c<32&&c!='\t')throw new IOException("Oversized/control-containing TSS line");line.append((char)c);}return c==-1&&line.isEmpty()?null:line.toString();
    }
}

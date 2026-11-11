/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.SequenceUtil;
import org.jlinalg.sequence.FastqIO;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Complete host-filter/unmapped-FASTQ/MALT-SAM consistency for the original eager screening branch.
 * This checks every admitted record and literal identity. It does not rescore MALT,
 * implement its LCA assignment, infer HOPS authenticity or admit a biological result.
 */
public final class NfcoreEagerScreeningReads {
    private static final Set<String> REFERENCE_COUNTS=Set.of("sequences","letters");
    private NfcoreEagerScreeningReads() { }
    public record Limits(long bamRecords, long queries, long storedQueryBases, long maltAlignments) {
        public Limits { if(bamRecords<1 || queries<1 || storedQueryBases<1 || maltAlignments<1)
            throw new IllegalArgumentException("Declare positive complete-population capacities"); }
    }
    public record Host(long records, long mappedRecords, long filteredRecords, long unmappedQueries,
            long unmappedQueryBases, long qcPassingMapped, long qcFailedMapped) {
        public Map<String,Object> document() { return Map.of("records",records,"mapped_records",mappedRecords,
            "filtered_records",filteredRecords,"unmapped_queries",unmappedQueries,"unmapped_query_bases",unmappedQueryBases,
            "qc_passing_mapped",qcPassingMapped,"qc_failed_mapped",qcFailedMapped); }
    }
    public record Malt(long alignments, long alignedQueries, long alignedReferences,
            long hardClippedAlignments, long reverseAlignments, long gappedAlignments,
            Integer minimumIdentity, Integer maximumIdentity) {
        public Map<String,Object> document() {var result=new LinkedHashMap<String,Object>();
            result.put("alignments",alignments);result.put("aligned_queries",alignedQueries);result.put("aligned_references",alignedReferences);
            result.put("hard_clipped_alignments",hardClippedAlignments);result.put("reverse_alignments",reverseAlignments);result.put("gapped_alignments",gappedAlignments);
            result.put("minimum_identity",minimumIdentity);result.put("maximum_identity",maximumIdentity);return java.util.Collections.unmodifiableMap(result);}
    }
    public record Inspection(Host host, Malt malt, List<String> warnings) {
        public Inspection { warnings=List.copyOf(warnings); }
        public Map<String,Object> document() { return Map.of("host",host.document(),"malt",malt.document(),"warnings",warnings); }
    }
    private record Query(String header,String sequence,String quality) { }

    /** Source samtools view -F4 -q threshold, optional eager query-length helper,
     * and samtools1.12 fastq -tN. Nonunique eligible identities refuse assessment;
     * no read is renamed, selected as a representative or silently deduplicated.
     * Provide a complete original MALT reference-name/length dictionary explicitly.
     */
    public static Inspection inspect(Path mappedBam,Path filteredBam,Path unmappedFastq,
            Path maltSamGzip,Map<String,Long> maltReferences,int minimumMapq,long minimumLength,
            Limits limits) throws IOException {
        if(minimumMapq<0 || minimumMapq>255 || minimumLength<0 || maltReferences.isEmpty())
            throw invalid("Invalid explicit filter/reference contract");
        maltReferences=Map.copyOf(maltReferences);
        var queries=new LinkedHashMap<String,Query>();long records=0,mapped=0,kept=0,bases=0,passMapped=0,failMapped=0;
        var factory=SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS)
            .validationStringency(ValidationStringency.STRICT);
        try(var original=factory.open(mappedBam);var filtered=factory.open(filteredBam)) {
            if(!original.getFileHeader().getSequenceDictionary().equals(filtered.getFileHeader().getSequenceDictionary())
                    || !original.getFileHeader().getReadGroups().equals(filtered.getFileHeader().getReadGroups()))
                throw invalid("Host-filter reference/read-group metadata differ");
            var actual=filtered.iterator();
            for(var record:original) {
                if(++records>limits.bamRecords())throw invalid("Complete original BAM exceeds record capacity");
                if(!record.getReadUnmappedFlag()) {mapped++;if(record.getReadFailsVendorQualityCheckFlag())failMapped++;else passMapped++;}
                if(!record.getReadUnmappedFlag() && record.getMappingQuality()>=minimumMapq && record.getReadLength()>=minimumLength) {
                    if(!actual.hasNext() || !canonical(record).equals(canonical(actual.next())))
                        throw invalid("Complete ordered host-filter BAM differs from source flag/MAPQ/query-length selection");
                    kept++;
                }
                if(record.getReadUnmappedFlag() && !record.isSecondaryOrSupplementary()) {
                    String name=record.getReadName()+readSuffix(record),sequence=record.getReadLength()==0?"":record.getReadString();
                    String quality=record.getBaseQualities().length==0?"\"".repeat(record.getReadLength()):record.getBaseQualityString();
                    if(record.getReadNegativeStrandFlag()) {sequence=SequenceUtil.reverseComplement(sequence);quality=new StringBuilder(quality).reverse().toString();}
                    String header="@"+name;
                    for(String tag:List.of("RG","BC","QT")) {
                        Object value=record.getAttribute(tag);
                        if(value!=null) {if(!(value instanceof String))throw invalid("Non-string source FASTQ copy tag: "+tag);header+="\t"+tag+":Z:"+value;}
                    }
                    if(queries.putIfAbsent(name,new Query(header,sequence,quality))!=null)
                        throw invalid("Nonunique unmapped query identity is unassessable: "+name+"; no identifier repaired or representative selected");
                    bases=Math.addExact(bases,sequence.length());if(queries.size()>limits.queries() || bases>limits.storedQueryBases())
                        throw invalid("Complete unmapped-query population exceeds declared memory capacity");
                }
            }
            if(actual.hasNext())throw invalid("Host-filter BAM has extra records");
        } catch(RuntimeException malformed) {throw invalid("Original/filtered BAM cannot be assessed: "+malformed);}
        var observed=new HashSet<String>();
        try(var reader=new FastqIO.Reader(unmappedFastq)) {
            FastqIO.Read read;
            while((read=reader.next())!=null) {
                String name=read.header().substring(1).split("\\s+",2)[0];Query expected=queries.get(name);
                if(expected==null || !observed.add(name) || !read.header().equals(expected.header())
                        || !read.sequence().equals(expected.sequence()) || !read.quality().equals(expected.quality()))
                    throw invalid("Complete unmapped FASTQ identity, copied tags, orientation, bases or qualities differ at "+name);
            }
        }
        if(!observed.equals(queries.keySet()))throw invalid("Unmapped FASTQ omits original eligible BAM records");
        long alignments=0,hard=0,reverse=0,gapped=0;Integer min=null,max=null;var aligned=new HashSet<String>();var usedReferences=new HashSet<String>();
        try(var stream=new GZIPInputStream(Files.newInputStream(maltSamGzip));var reader=factory.open(SamInputResource.of(stream))) {
            for(var sequence:reader.getFileHeader().getSequenceDictionary().getSequences())
                if(!Long.valueOf(sequence.getSequenceLength()).equals(maltReferences.get(sequence.getSequenceName())))
                    throw invalid("MALT SAM declares an unknown/changed literal reference or length");
            for(var record:reader) {
                if(++alignments>limits.maltAlignments())throw invalid("Complete MALT alignment capacity exceeded");
                Query query=queries.get(record.getReadName());Long length=maltReferences.get(record.getReferenceName());
                if(query==null || record.getReadUnmappedFlag() || length==null || record.getAlignmentStart()<1 || record.getAlignmentEnd()>length)
                    throw invalid("MALT alignment is outside its complete input/reference contract");
                List<CigarElement> cigar=record.getCigar().getCigarElements();if(cigar.isEmpty())throw invalid("Missing MALT CIGAR");
                int left=0,right=0;long m=0,inserted=0,deleted=0;
                for(int i=0;i<cigar.size();i++) {
                    var element=cigar.get(i);var op=element.getOperator();
                    if(op==CigarOperator.H) {if(i==0)left=element.getLength();else if(i==cigar.size()-1)right=element.getLength();else throw invalid("Internal hard clip in original MALT SAM");}
                    else if(op==CigarOperator.M)m=Math.addExact(m,element.getLength());
                    else if(op==CigarOperator.I)inserted=Math.addExact(inserted,element.getLength());
                    else if(op==CigarOperator.D)deleted=Math.addExact(deleted,element.getLength());
                    else if(op!=CigarOperator.S)throw invalid("Unassessable original BlastN CIGAR operator: "+op);
                }
                String sequence=query.sequence(),quality=query.quality();
                if(record.getReadNegativeStrandFlag()) {sequence=SequenceUtil.reverseComplement(sequence);quality=new StringBuilder(quality).reverse().toString();reverse++;}
                if((long)left+right>sequence.length())throw invalid("MALT hard clips exceed the full original query");
                sequence=sequence.substring(left,sequence.length()-right);quality=quality.substring(left,quality.length()-right);
                if(!sequence.equals(record.getReadString()) || !quality.equals(record.getBaseQualityString()))
                    throw invalid("MALT query sequence/qualities differ after explicit strand/hard-clip projection");
                Integer nm=record.getIntegerAttribute("NM"),zi=record.getIntegerAttribute("ZI"),zl=record.getIntegerAttribute("ZL");String md=record.getStringAttribute("MD");
                if(nm==null || nm<0 || zi==null || zi<0 || zi>100 || zl==null || !Long.valueOf(zl).equals(length) || md==null || md.isEmpty())
                    throw invalid("MALT source NM/ZI/ZL/MD fields are absent or invalid; no value imputed");
                long matches=0,mismatches=0,mdDeleted=0;
                for(int i=0;i<md.length();) {
                    char c=md.charAt(i);
                    if(c>='0' && c<='9') {int j=i+1;while(j<md.length() && md.charAt(j)>='0' && md.charAt(j)<='9')j++;matches=Math.addExact(matches,Long.parseLong(md.substring(i,j)));i=j;}
                    else if(c=='^') {int start=++i;while(i<md.length() && "ACGTRYSWKMBDHVNU".indexOf(md.charAt(i))>=0){mdDeleted++;i++;}if(i==start)throw invalid("Empty MALT MD deletion");}
                    else if("ACGTRYSWKMBDHVNU".indexOf(c)>=0) {mismatches++;i++;}
                    else throw invalid("Malformed literal MALT MD token");
                }
                // MALT0.4.1 omits zero runs between adjacent MD mismatches. Parse
                // those source tokens directly; never rewrite the saved SAM.
                long span=Math.addExact(m,Math.addExact(inserted,deleted));
                if(matches+mismatches!=m || mdDeleted!=deleted || mismatches+inserted+deleted!=nm || span<=0
                        || Math.multiplyExact(100,matches)/span!=zi)
                    throw invalid("MALT CIGAR/MD/NM/percent-identity populations disagree");
                if(left+right>0)hard++;if(inserted+deleted>0)gapped++;
                min=min==null?zi:Math.min(min,zi);max=max==null?zi:Math.max(max,zi);aligned.add(record.getReadName());usedReferences.add(record.getReferenceName());
            }
        } catch(RuntimeException malformed) {throw invalid("MALT SAM cannot be assessed: "+malformed);}
        var warnings=new ArrayList<String>();
        warnings.add("MALT SAM alignment and query counts differ from all-input queries and filtered HOPS populations. MAPQ255 is retained as the source token, not interpreted as genomic mapping confidence.");
        if(alignments==0)warnings.add("No aligned queries: identity statistics remain absent; no negative biological finding or authenticity assessment is admitted.");
        return new Inspection(new Host(records,mapped,kept,queries.size(),bases,passMapped,failMapped),
            new Malt(alignments,aligned.size(),usedReferences.size(),hard,reverse,gapped,min,max),warnings);
    }
    /** Read complete original MALT0.4.1 reference-name/length metadata without
     * retaining genomic sequences in memory. Every sequence byte is consumed.
     */
    public static Map<String,Long> references(Path database,Path index,Path info,long maximumReferences,long maximumBases) throws IOException {
        if(maximumReferences<1 || maximumBases<1 || Files.size(index)%8!=0)throw invalid("Invalid complete MALT reference capacities/index");
        long count=Files.size(index)/8;if(count<1 || count>maximumReferences)throw invalid("Complete MALT reference count exceeds capacity");
        var result=new LinkedHashMap<String,Long>();long offset=0,bases=0;byte[] buffer=new byte[8192];
        try(var input=new DataInputStream(Files.newInputStream(database));var offsets=new DataInputStream(Files.newInputStream(index))) {
            for(long i=0;i<count;i++) {
                if(offsets.readLong()!=offset)throw invalid("MALT reference offsets do not cover the complete database");
                int length=input.readInt();if(length<1)throw invalid("Empty/invalid original MALT reference sequence");bases=Math.addExact(bases,length);if(bases>maximumBases)throw invalid("Complete MALT reference bases exceed capacity");
                int remaining=length;
                while(remaining>0) {int n=input.read(buffer,0,Math.min(buffer.length,remaining));if(n<1)throw invalid("Truncated original MALT sequence");for(int j=0;j<n;j++)if("ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(buffer[j])<0)throw invalid("Non-IUPAC original MALT reference byte");remaining-=n;}
                int headerLength=input.readInt();if(headerLength<2 || headerLength>1048576)throw invalid("Unassessable complete MALT reference header length");
                byte[] header=input.readNBytes(headerLength);if(header.length!=headerLength)throw invalid("Truncated original MALT reference header");String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(header)).toString();
                if(!text.startsWith(">"))throw invalid("Original MALT reference header has no marker");String name=text.substring(1).split("\\s+",2)[0];
                if(name.isEmpty() || result.putIfAbsent(name,(long)length)!=null)throw invalid("Nonunique/empty literal MALT reference identity");offset=Math.addExact(offset,8L+length+headerLength);
            }
            if(input.read()!=-1 || offsets.read()!=-1)throw invalid("Unexpected trailing original MALT reference bytes");
        } catch(RuntimeException malformed) {throw invalid("Original MALT reference metadata cannot be assessed: "+malformed);}
        if(Files.size(info)>1048576)throw invalid("Complete original MALT reference-info metadata exceed capacity");
        var counts=new LinkedHashMap<String,Long>();
        try {
            for(String line:Files.readString(info,StandardCharsets.UTF_8).lines().toList()) {
                String[] fields=line.split("\t",-1);
                if(fields.length!=2 || !REFERENCE_COUNTS.contains(fields[0]) || !fields[1].matches("[0-9]+")
                        || counts.putIfAbsent(fields[0],Long.parseLong(fields[1]))!=null)
                    throw invalid("Original MALT reference-info count fields are malformed/nonunique");
            }
        } catch(NumberFormatException malformed) {throw invalid("Original MALT reference-info integer count is unassessable: "+malformed);}
        if(!counts.keySet().equals(REFERENCE_COUNTS) || !Long.valueOf(count).equals(counts.get("sequences")) || !Long.valueOf(bases).equals(counts.get("letters")))
            throw invalid("Complete original MALT sequence/base counts differ from reference-info metadata");
        return java.util.Collections.unmodifiableMap(result);
    }
    private static String readSuffix(SAMRecord record) {
        boolean first=(record.getFlags()&64)!=0,second=(record.getFlags()&128)!=0;
        return first==second?"":first?"/1":"/2";
    }
    private static List<String> canonical(SAMRecord record) {
        String text=record.getSAMString();if(text.endsWith("\n"))text=text.substring(0,text.length()-1);String[] fields=text.split("\t",-1);Arrays.sort(fields,11,fields.length);return Arrays.asList(fields);
    }
    private static IOException invalid(String cause) {return new IOException("EAGER SCREENING AUDIT FAIL2: "+cause+". Required action: retain complete original inputs and diagnostics; resolve the named identity, format, capacity or source-contract discrepancy before retrying. No read is repaired, omitted or replaced and no biological result is admitted.");}
}

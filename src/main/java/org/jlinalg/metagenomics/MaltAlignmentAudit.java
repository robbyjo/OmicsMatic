/* Source DNA5 projection: Copyright (C) 2018 Daniel H. Huson;
 * Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import htsjdk.samtools.*;
import htsjdk.samtools.util.SequenceUtil;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.workflow.NfcoreEagerScreeningReads;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete FASTQ/SAM population and source CIGAR/MD/NM/ZI geometry audit.
 * Alignment fitting/scoring remain in unchanged upstream MALT. MAPQ255 is a
 * source token, not genomic mapping confidence. No biological admission.
 */
public final class MaltAlignmentAudit {
 private MaltAlignmentAudit(){ }
 public record Limits(long queries,long queryBases,long alignments,long references,long referenceBases) {
  public Limits {if(queries<1||queryBases<1||alignments<1||references<1||referenceBases<1)throw new IllegalArgumentException("Positive complete-population capacities required");}
 }
 public record Result(long queries,long queryBases,long alignments,long alignedQueries,long usedReferences,
    long reverseAlignments,long hardClippedAlignments,long gappedAlignments,Integer minimumIdentity,Integer maximumIdentity,
    Map<String,Integer> counts,Map<String,Integer> queryLengths) {
  public Result {counts=Collections.unmodifiableMap(new TreeMap<>(counts));queryLengths=Collections.unmodifiableMap(new TreeMap<>(queryLengths));}
 }
 private record Query(String sequence,String quality){ }
 public static Result audit(Path fastq,Path samGzip,Path database,Limits limits)throws IOException {
  var refs=NfcoreEagerScreeningReads.references(database.resolve("ref.db"),database.resolve("ref.idx"),database.resolve("ref.inf"),limits.references(),limits.referenceBases());
  var queries=new HashMap<String,Query>();long bases=0;
  try(var reader=new FastqIO.Reader(fastq)) {
   FastqIO.Read read;
   while((read=reader.next())!=null){String name=read.header().substring(1).split("\\s+",2)[0];if(queries.putIfAbsent(name,new Query(sourceDna5(read.sequence()),read.quality()))!=null)throw invalid("Nonunique query identity cannot be cross-checked: "+name);bases=Math.addExact(bases,read.sequence().length());if(queries.size()>limits.queries()||bases>limits.queryBases())throw invalid("Complete FASTQ population exceeds declared capacity");}
  }
  var counts=new TreeMap<String,Integer>();var used=new HashSet<String>();long alignments=0,reverse=0,hard=0,gapped=0;Integer min=null,max=null;
  try(var stream=new GZIPInputStream(Files.newInputStream(samGzip));var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(SamInputResource.of(stream))) {
   var declared=new HashSet<String>();for(var sequence:reader.getFileHeader().getSequenceDictionary().getSequences())if(!declared.add(sequence.getSequenceName())||!Long.valueOf(sequence.getSequenceLength()).equals(refs.get(sequence.getSequenceName())))throw invalid("SAM reference dictionary differs from complete index");
   for(var row:reader) {
    if(++alignments>limits.alignments())throw invalid("Complete alignment population exceeds capacity");Query query=queries.get(row.getReadName());Long length=refs.get(row.getReferenceName());if(query==null||length==null||row.getReadUnmappedFlag()||row.getAlignmentStart()<1||row.getAlignmentEnd()>length)throw invalid("SAM read/reference population or coordinates differ");
    var cigar=row.getCigar().getCigarElements();if(cigar.isEmpty())throw invalid("Missing original MALT CIGAR");int left=0,right=0;long m=0,inserted=0,deleted=0;
    for(int i=0;i<cigar.size();i++){var element=cigar.get(i);switch(element.getOperator()) {
     case H -> {if(i==0)left=element.getLength();else if(i==cigar.size()-1)right=element.getLength();else throw invalid("Internal original MALT hard clip");}
     case M -> m=Math.addExact(m,element.getLength());
     case I -> inserted=Math.addExact(inserted,element.getLength());
     case D -> deleted=Math.addExact(deleted,element.getLength());
     case S -> { }
     default -> throw invalid("Unsupported original BlastN CIGAR operator");
    }}
    String sequence=query.sequence(),quality=query.quality();if(row.getReadNegativeStrandFlag()){sequence=SequenceUtil.reverseComplement(sequence);quality=new StringBuilder(quality).reverse().toString();reverse++;}
    if((long)left+right>sequence.length())throw invalid("Hard clips exceed the original query");sequence=sequence.substring(left,sequence.length()-right);quality=quality.substring(left,quality.length()-right);
    if(!sequence.equals(row.getReadString())||!quality.equals(row.getBaseQualityString()))throw invalid("SAM query bases/qualities differ after source DNA5/strand/clip projection at "+row.getReadName()+"; original query length="+query.sequence().length()+", CIGAR="+row.getCigarString()+", expected sequence/quality length="+sequence.length()+", actual="+row.getReadLength()+"/"+row.getBaseQualities().length+". Original MALT Local output can contain an unaccounted base; do not repair its clips or trim the input implicitly");
    Integer nm=row.getIntegerAttribute("NM"),zi=row.getIntegerAttribute("ZI"),zl=row.getIntegerAttribute("ZL");String md=row.getStringAttribute("MD");if(nm==null||nm<0||zi==null||zi<0||zi>100||zl==null||!Long.valueOf(zl).equals(length)||md==null||md.isEmpty())throw invalid("Source NM/ZI/ZL/MD fields absent or invalid");
    long matches=0,mismatches=0,mdDeleted=0;for(int i=0;i<md.length();) {
     char c=md.charAt(i);
     if(c>='0'&&c<='9'){int j=i+1;while(j<md.length()&&md.charAt(j)>='0'&&md.charAt(j)<='9')j++;matches=Math.addExact(matches,Long.parseLong(md.substring(i,j)));i=j;}
     else if(c=='^'){int start=++i;while(i<md.length()&&"ACGTRYSWKMBDHVNU".indexOf(md.charAt(i))>=0){mdDeleted++;i++;}if(i==start)throw invalid("Empty original MD deletion");}
     else if("ACGTRYSWKMBDHVNU".indexOf(c)>=0){mismatches++;i++;}else throw invalid("Malformed original MD token");
    }
    long span=Math.addExact(m,Math.addExact(inserted,deleted));if(matches+mismatches!=m||mdDeleted!=deleted||mismatches+inserted+deleted!=nm||span<=0||Math.multiplyExact(100,matches)/span!=zi)throw invalid("Source CIGAR/MD/NM/identity geometry differs");
    if(left+right>0)hard++;if(inserted+deleted>0)gapped++;min=min==null?zi:Math.min(min,zi);max=max==null?zi:Math.max(max,zi);used.add(row.getReferenceName());counts.merge(row.getReadName(),1,Math::addExact);
   }
  }catch(RuntimeException error){throw invalid("Malformed/unassessable complete SAM: "+error);}
  var lengths=new TreeMap<String,Integer>();queries.forEach((name,value)->lengths.put(name,value.sequence().length()));return new Result(queries.size(),bases,alignments,counts.size(),used.size(),reverse,hard,gapped,min,max,counts,lengths);
 }
 /** Original FastAReader normalizes FASTQ using DNA5 before alignment.
  * This projects expected bytes; it never rewrites the captured FASTQ.
  * Source DNA5 maps U/u to T, ambiguity codes to N and lower case to upper.
  */
 private static String sourceDna5(String sequence) {
  var out=new StringBuilder(sequence.length());for(int i=0;i<sequence.length();i++)out.append(switch(sequence.charAt(i)) {case 'A','a' -> 'A';case 'C','c' -> 'C';case 'G','g' -> 'G';case 'T','t','U','u' -> 'T';default -> 'N';});return out.toString();
 }
 private static IOException invalid(String text){return new IOException("MALT ALIGNMENT AUDIT FAIL2: "+text+". Required action: retain the complete input/output and diagnostics, resolve the named identity/format/capacity problem and retry a NEW directory; no subset or repaired record is substituted.");}
}

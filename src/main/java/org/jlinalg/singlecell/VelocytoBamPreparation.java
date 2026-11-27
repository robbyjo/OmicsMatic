/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Original read-side rules: velocyto0.17.17, BSD-2-Clause, copyright2017
 * Peter Kharchenko, Sten Linnarsson and Gioele La Manno.
 * Original notice: META-INF/licenses/Velocyto-BSD-2-Clause.txt. */
package org.jlinalg.singlecell;

import htsjdk.samtools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Literal optional UMI-tag preparation and complete original-counting-domain audit.
 * No gene assignment, read realignment, identifier repair or RNA velocity is inferred.
 * Barcode truncation below is reported original velocyto0.17.17 behavior, not a
 * rewritten output identifier. Collision-prone inputs fail instead of merging cells.
 */
public final class VelocytoBamPreparation {
    private VelocytoBamPreparation() { }
    public record Limits(long records,long samCharacters,int cells,long moleculeKeys) {
        public Limits {if(records<1||samCharacters<1||cells<1||moleculeKeys<1)throw new IllegalArgumentException("Positive complete BAM capacities required");}
    }
    public record Settings(String umiTag,Set<String> excludedContigs,boolean includeMultimappers,
            Set<String> selectedBarcodePrefixes,boolean checkUint16MoleculeBound) {
        public Settings {
            if(!Set.of("UB","MA").contains(umiTag))throw new IllegalArgumentException("Declare literal original UB or explicit BD Rhapsody MA-to-UB preparation");
            excludedContigs=Set.copyOf(excludedContigs);selectedBarcodePrefixes=selectedBarcodePrefixes==null?null:Set.copyOf(selectedBarcodePrefixes);
            if(selectedBarcodePrefixes!=null&&selectedBarcodePrefixes.isEmpty())throw new IllegalArgumentException("A supplied barcode selection must be nonempty");
        }
    }
    public record Barcode(String original,int distinctMoleculeKeys,long eligibleRecords) { }
    public record Report(long inputRecords,long samCharacters,long outputRecords,long excludedRecords,long renamedTags,
            long mappedRecords,long taggedForOriginalPeek,long skippedNonunique,long skippedMissingTags,
            long skippedUnselected,long longReferenceSpans,long distinctMoleculeKeys,Map<String,Barcode> barcodes,
            Map<String,String> sourceChromosomeMapping,Map<String,Long> eligibleRecordsByContig,List<String> excludedContigs,List<String> warnings) {
        public Report{barcodes=Map.copyOf(barcodes);sourceChromosomeMapping=Map.copyOf(sourceChromosomeMapping);eligibleRecordsByContig=Map.copyOf(eligibleRecordsByContig);excludedContigs=List.copyOf(excludedContigs);warnings=List.copyOf(warnings);}
    }
    /** Write to a new file only. The caller retains and seals any failed attempt. */
    public static Report prepare(Path input,Path output,Settings settings,Limits limits,Runnable check)throws IOException {
        Objects.requireNonNull(settings);Objects.requireNonNull(limits);Objects.requireNonNull(check);
        if(!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)||Files.exists(output,LinkOption.NOFOLLOW_LINKS)||output.toAbsolutePath().normalize().equals(input.toAbsolutePath().normalize()))throw new IOException("Require a complete original BAM and a NEW distinct output file");
        long records=0,written=0,excluded=0,renamed=0,mapped=0,tagged=0,nonunique=0,missing=0,unselected=0,longSpan=0,keys=0,characters=0;
        var rawByPrefix=new LinkedHashMap<String,String>();var molecules=new LinkedHashMap<String,Set<String>>();var eligible=new LinkedHashMap<String,Long>();
        var chromosomeMapping=new LinkedHashMap<String,String>();var eligibleByContig=new LinkedHashMap<String,Long>();
        int lastReference=-1,lastPosition=-1;boolean unplacedSeen=false;
        try(var reader=SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(input)) {
            SAMFileHeader header=reader.getFileHeader().clone();
            if(header.getSortOrder()!=SAMFileHeader.SortOrder.coordinate)throw new IOException("Original velocyto intron markup requires a coordinate-sorted BAM; supply the actual sorted input");
            var references=new HashSet<String>();var normalized=new HashMap<String,String>();
            for(var sequence:header.getSequenceDictionary().getSequences()){
                String raw=sequence.getSequenceName();references.add(raw);if(settings.excludedContigs.contains(raw))continue;String canonical=sourceChromosome(raw);String previous=normalized.putIfAbsent(canonical,raw);
                chromosomeMapping.put(raw,canonical);
                if(previous!=null)throw new IOException("Original velocyto chromosome conversion would collide: "+previous+" / "+raw+" -> "+canonical+". Prepare/document distinct compatible inputs; no automatic repair");
            }
            if(!references.containsAll(settings.excludedContigs))throw new IOException("An explicitly excluded antibody contig is absent from the complete BAM dictionary");
            var factory=new SAMFileWriterFactory().setCreateIndex(false).setCreateMd5File(false);
            try(var writer=factory.makeBAMWriter(header,true,output.toFile())){
                for(var read:reader){
                    check.run();if(++records>limits.records)throw new IOException("Complete BAM record capacity exceeded");characters=Math.addExact(characters,read.getSAMString().length());if(characters>limits.samCharacters)throw new IOException("Complete native SAM-character capacity exceeded");
                    int reference=read.getReferenceIndex(),position=read.getAlignmentStart();
                    if(reference<0){unplacedSeen=true;}else{if(unplacedSeen||reference<lastReference||reference==lastReference&&position<lastPosition)throw new IOException("Actual BAM coordinate order disagrees with source intron-markup requirement");lastReference=reference;lastPosition=position;}
                    if(settings.excludedContigs.contains(read.getReferenceName())){excluded++;continue;}
                    if(settings.umiTag.equals("MA")&&read.hasAttribute("MA")){
                        Object value=read.getAttribute("MA");if(!(value instanceof String))throw new IOException("MA UMI must be a literal string, without coercion");Object existing=read.getAttribute("UB");if(existing!=null&&!existing.equals(value))throw new IOException("Existing UB disagrees with MA; no silent UMI replacement");read.setAttribute("UB",value);read.setAttribute("MA",null);renamed++;
                    }
                    writer.addAlignment(read);written++;
                    if(read.getReadUnmappedFlag())continue;mapped++;
                    Object cellValue=read.getAttribute("CB"),umiValue=read.getAttribute("UB");
                    if(cellValue instanceof String&&umiValue instanceof String)tagged++;
                    if(!settings.includeMultimappers){Object nh=read.getAttribute("NH");if(!(nh instanceof Integer))throw new IOException("Original unique-mapping mode requires actual integer NH tags on every retained mapped record");if(((Integer)nh)!=1){nonunique++;continue;}}
                    if(cellValue==null||umiValue==null){missing++;continue;}
                    if(!(cellValue instanceof String cell)||!(umiValue instanceof String umi))throw new IOException("Original CB and UB tags must be literal strings");literal(cell,"cell barcode");literal(umi,"UMI");String prefix=cell.split("-",-1)[0];literal(prefix,"original barcode prefix");
                    if(settings.selectedBarcodePrefixes!=null&&!settings.selectedBarcodePrefixes.contains(prefix)){unselected++;continue;}
                    // Keep these keys in the conservative input bound. Original Read.span
                    // is computed after its own CIGAR parsing and can differ at boundaries.
                    if(read.getCigar().getReferenceLength()>3000000)longSpan++;
                    String previous=rawByPrefix.putIfAbsent(prefix,cell);if(previous!=null&&!previous.equals(cell))throw new IOException("Original velocyto splits CB at the first hyphen and would merge distinct cells "+previous+" / "+cell+". Count actual samples/GEM groups separately and retain their identities");
                    if(rawByPrefix.size()>limits.cells)throw new IOException("Complete original barcode population exceeds capacity");var set=molecules.computeIfAbsent(prefix,k->new HashSet<>());if(set.add(umi)){if(++keys>limits.moleculeKeys)throw new IOException("Complete native cell/UMI-key capacity exceeded");}
                    eligible.merge(prefix,1L,Math::addExact);
                    eligibleByContig.merge(read.getReferenceName(),1L,Math::addExact);
                }
            }
        }catch(ArithmeticException e){throw new IOException("Native complete BAM census overflow",e);}
        var barcodes=new LinkedHashMap<String,Barcode>();for(var entry:rawByPrefix.entrySet()){
            int count=molecules.get(entry.getKey()).size();barcodes.put(entry.getKey(),new Barcode(entry.getValue(),count,eligible.get(entry.getKey())));
        }
        var warnings=new ArrayList<String>();warnings.add("Original counting uses CB before its first hyphen and deduplicates by normalized cell plus UMI; this audit preserves actual tags and refuses distinct-cell collisions.");warnings.add("Original unique mode uses NH, without substituting MAPQ, duplicate or primary-read filters.");
        if(missing>0)warnings.add(missing+" retained mapped records lack CB/UB and are skipped by original counting.");
        if(tagged<=1000)warnings.add("Original peek does not select CB/UB until more than 1000 mapped tagged records; this file alone cannot qualify as the first counting BAM.");
        if(settings.checkUint16MoleculeBound&&barcodes.values().stream().anyMatch(b->b.distinctMoleculeKeys>65535))warnings.add("SOURCE_UINT16_OVERFLOW_RISK: at least one cell exceeds the safe unique-UMI bound. Original CLI completeness must fail; choose the explicit original Counter API uint64 workflow after reviewing the dtype difference. No cells or reads are discarded to meet this bound.");
        return new Report(records,characters,written,excluded,renamed,mapped,tagged,nonunique,missing,unselected,longSpan,keys,barcodes,chromosomeMapping,eligibleByContig,new ArrayList<>(new TreeSet<>(settings.excludedContigs)),warnings);
    }
    /** The original read-side rule; canonicalization is reported, never written to tags/header. */
    public static String sourceChromosome(String chromosome){
        if(!chromosome.startsWith("chr"))return chromosome;
        if(chromosome.contains("_"))return chromosome.split("_",-1)[1];String value=chromosome.substring(3);return value.equals("M")?"MT":value;
    }
    private static void literal(String value,String role)throws IOException {
        if(value.isEmpty()||value.length()>32768||value.indexOf('$')>=0||value.chars().anyMatch(c->c<=32||c>=127))throw new IOException("Require literal nonempty ASCII "+role+" without whitespace or the source molecule-key delimiter $");
    }
}

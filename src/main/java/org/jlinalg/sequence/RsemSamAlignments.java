/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Streaming original RSEM alignment semantics over complete canonical SAM text.
 * Binary BAM/CRAM decoding belongs to an explicitly admitted decoder; this class
 * never launches or installs one. It preserves original parser projections and
 * separately reports input conditions that must be resolved before fitting.
 */
public final class RsemSamAlignments {
    private RsemSamAlignments() { }

    /**
     * Explicit original parser/model controls.
     * @param release exact source release
     * @param readType original 0/1 single or 2/3 paired, odd types use qualities
     * @param readTypeTag original optional integer tag classifying suppressed unmapped groups, empty to disable
     * @param seedLength original low-quality seed threshold, without dropping parser groups
     */
    public record Options(RsemAbundance.Release release, int readType, String readTypeTag, int seedLength) {
        /** Requires an admitted release, read type, tag and positive seed. */
        public Options {
            Objects.requireNonNull(release,"release"); Objects.requireNonNull(readTypeTag,"readTypeTag");
            if (readType < 0 || readType > 3 || seedLength < 1 || !(readTypeTag.isEmpty() || readTypeTag.matches("[A-Za-z][A-Za-z0-9]")))
                throw new IllegalArgumentException("Require read type0..3, positive seed and an empty or two-character SAM tag");
        }
        /** @return whether adjacent mate records are required */
        public boolean paired() {return readType >= 2;}
        /** @return whether original read export consumes qualities */
        public boolean qualities() {return (readType & 1) != 0;}
    }

    /**
     * Complete stream, group, dictionary and retained-name capacities.
     * @param maxRecords physical SAM records
     * @param maxGroups source parser groups
     * @param maxHits source alignments, not mate records
     * @param maxHitsPerGroup largest retained hit population
     * @param maxReferences complete reference population
     * @param maxReadLength per-record sequence characters
     * @param maxLineCharacters per-source line characters
     * @param maxDecodedCharacters all decoded characters, including framing
     * @param maxDistinctNames retained canonical query identities
     * @param maxNameCharacters total retained canonical name characters
     * @param maxHistogramBins distinct positive multiplicities
     * @param maxSourceBytes complete source file bytes for the Path overload
     */
    public record Limits(long maxRecords, long maxGroups, long maxHits, int maxHitsPerGroup, int maxReferences,
                         int maxReadLength, int maxLineCharacters, long maxDecodedCharacters, int maxDistinctNames,
                         long maxNameCharacters, int maxHistogramBins, long maxSourceBytes) {
        /** Requires positive whole-source capacities. */
        public Limits {
            if (maxRecords < 1 || maxGroups < 1 || maxHits < 1 || maxHitsPerGroup < 1 || maxReferences < 1
                    || maxReadLength < 1 || maxLineCharacters < 1 || maxDecodedCharacters < 1 || maxDistinctNames < 1
                    || maxNameCharacters < 1 || maxHistogramBins < 1 || maxSourceBytes < 1)
                throw new IllegalArgumentException("Positive complete alignment capacities required");
        }
    }

    /** Findings are retained independently of source parser count compatibility. */
    public enum Finding {
        OMITTED_REFERENCES(false), POLYA_PRE_TAIL_DICTIONARY(false), HEADER_LENGTH_MISMATCH(true), DECODED_HEADER_LENGTH_DISAGREEMENT(true),
        CANONICALIZED_QUERY_NAME(false), NONCONTIGUOUS_QUERY_NAME(true), REPEATED_UNMAPPED_QUERY_NAME(true),
        MATE_NAME_MISMATCH(true), HISTORICAL_LENGTH_MISMATCH(true), IGNORED_SECONDARY_SEQUENCE(false),
        IGNORED_SECONDARY_QUALITY(false), MISSING_QUALITY(true), EMPTY_SEQUENCE(true),
        OUTSIDE_REFERENCE(true), SAME_STRAND_MATES(true), TEMPLATE_SPAN_OUTSIDE_REFERENCE(true),
        NONPOSITIVE_INFERRED_INSERT(false), AMBIGUOUS_MATE_FLAGS(true), SOURCE_INTEGER_WRAP(true),
        SOURCE_IGNORED_TRAILING_MATE(true), SOURCE_IGNORED_MAPPED_HITS(true), BELOW_SEED_OR_POLYA(false);
        private final boolean blocks;
        Finding(boolean blocks) {this.blocks=blocks;}
        /** @return whether protected fitting must resolve this input condition */
        public boolean blocksFitting() {return blocks;}
    }

    /**
     * Original header mapping; source coordinates use declared length.
     * @param name exact header target
     * @param declaredLength original header length
     * @param internalSid one-based prepared-reference transcript index
     */
    public record Target(String name, int declaredLength, int internalSid) { }

    /**
     * Every physical source record reaches the sink before group interpretation.
     * @param ordinal one-based physical record index
     * @param fields complete original SAM fields, with no auxiliary filtering
     * @param effectiveReferenceLength source decoder's current target length, distinct from header text
     */
    public record Record(long ordinal, List<String> fields, int effectiveReferenceLength) {
        /** Copies all source fields. */
        public Record {fields=List.copyOf(fields);}
    }

    /**
     * Original .dat hit coordinates, preserving orientation in the SID sign.
     * @param signedSid one-based transcript index, negative for reverse mate1/read
     * @param position original zero-based strand-oriented position
     * @param insertLength original paired fragment length, zero for single reads
     * @param mate1Record original physical index of mate1 or the single record
     * @param mate2Record original physical index of mate2, zero for single reads
     */
    public record Hit(int signedSid, int position, int insertLength, long mate1Record, long mate2Record) { }

    /**
     * Original first-record read data and every source hit, without deduplication.
     * @param ordinal one-based parser group
     * @param category original N0/N1/N2 category
     * @param name original canonical first/single read name
     * @param mate2Name original canonical second-mate name or null
     * @param sequence source-restored first/single read sequence
     * @param mate2Sequence source-restored second sequence or null
     * @param quality source-oriented ASCII quality, null for quality-free types
     * @param mate2Quality second quality or null
     * @param lowQuality original seed/poly-A flag, without changing counts
     * @param hits every original hit, including duplicates and secondary flags
     */
    public record Group(long ordinal, int category, String name, String mate2Name, String sequence,
                        String mate2Sequence, String quality, String mate2Quality, boolean lowQuality, List<Hit> hits) {
        /** Copies the complete hit population. */
        public Group {hits=List.copyOf(hits);}
    }

    /** Every callback remains partial until the complete read returns successfully. */
    public interface Sink {
        /** @param row complete physical source record @throws IOException destination failure */
        void record(Record row) throws IOException;
        /** @param group complete original parser group @throws IOException destination failure */
        void group(Group group) throws IOException;
        /** @param finding actual source condition @param ordinal physical record, or zero for a dictionary finding
         * @param detail diagnostic @throws IOException destination failure */
        void finding(Finding finding,long ordinal,String detail) throws IOException;
    }
    /** Caller-owned deadline, cancellation or immutable-resource checks. */
    @FunctionalInterface public interface Check {/** @throws IOException caller check failed */ void run() throws IOException;}
    /** Exact per-record metadata supplied by a source-linked binary decoder. */
    public interface RecordMetadata {
        /**
         * @param ordinal one-based physical index
         * @param fields complete canonical SAM fields
         * @param textualReferenceLength header-text length, zero for no target
         * @return actual source decoder target length for this physical record
         * @throws IOException metadata/record identity or capacity disagreement
         */
        int bind(long ordinal,List<String> fields,int textualReferenceLength)throws IOException;
        /** @param records complete physical population @throws IOException missing or inconsistent completion */
        default void finish(long records)throws IOException{ }
    }
    private static final RecordMetadata TEXT_LENGTH=(ordinal,fields,length)->length;

    /**
     * Reads bounded source-linked decoder metadata in exact physical-record order.
     * The caller owns the reader and the decoder process/input/reference evidence.
     * @param source complete metadata stream from the pinned decoder bridge
     * @param limits complete capacities
     * @param check caller checks
     * @return binding that requires a matching terminal record count
     * @throws IOException unsupported or incomplete metadata header
     */
    public static RecordMetadata decodedRecordMetadata(Reader source,Limits limits,Check check)throws IOException{
        return new MetadataLines(source,limits,check);
    }
    private static final Sink DISCARD=new Sink(){
        @Override public void record(Record row){ }
        @Override public void group(Group group){ }
        @Override public void finding(Finding finding,long ordinal,String detail){ }
    };

    /**
     * Complete source .cnt accounting. nIsoMulti includes cross-gene/duplicate hits.
     * @param unalignable N0
     * @param alignable N1
     * @param excluded N2
     * @param uniqueGenes N1 minus gene-multimapping groups
     * @param multipleGenes gene-multimapping groups
     * @param multipleAlignments all groups with more than one hit
     * @param hits source hits, without doubling paired fragments
     * @param readType original read type
     * @param histogram complete positive multiplicity population
     */
    public record Accounting(long unalignable,long alignable,long excluded,long uniqueGenes,long multipleGenes,
                             long multipleAlignments,long hits,int readType,List<RsemCounts.Bin> histogram) {
        /** Copies the complete ordered histogram. */
        public Accounting {histogram=List.copyOf(histogram);}
        /** @return complete original .cnt text with LF framing */
        public String originalText(){
            StringBuilder s=new StringBuilder();s.append(unalignable).append(' ').append(alignable).append(' ').append(excluded).append(' ').append(Math.addExact(Math.addExact(unalignable,alignable),excluded)).append('\n');
            s.append(uniqueGenes).append(' ').append(multipleGenes).append(' ').append(multipleAlignments).append('\n').append(hits).append(' ').append(readType).append('\n').append("0\t").append(unalignable).append('\n');
            for(var bin:histogram)s.append(bin.mappings()).append('\t').append(bin.readGroups()).append('\n');return s.append("Inf\t").append(excluded).append('\n').toString();
        }
        /**
         * @param original complete independently parsed original .cnt
         * @throws IOException any original accounting quantity differs
         */
        public void requireMatch(RsemCounts.Counts original)throws IOException{
            if(unalignable!=original.unalignableGroups()||alignable!=original.alignableGroups()||excluded!=original.excludedGroups()
                    ||uniqueGenes!=original.uniqueGeneGroups()||multipleGenes!=original.multipleGeneGroups()
                    ||multipleAlignments!=original.multipleIsoformGroups()||hits!=original.alignmentHits()
                    ||readType!=original.readType()||!histogram.equals(original.histogram()))throw new IOException("Complete alignment accounting differs from original .cnt");
        }
    }

    /**
     * Completed semantic scan; source-compatible counts do not certify safe fitting.
     * @param records every physical source record
     * @param groups every source parser group
     * @param decodedCharacters all consumed text characters
     * @param ignoredTrailingRecords source-discarded dangling mate records
     * @param accounting complete original-compatible counters
     * @param targets original ordered dictionary mapping
     * @param omittedSids every prepared reference absent from the header
     * @param findings all condition counts
     * @param safeForFitting false when any blocking condition remains
     */
    public record Summary(long records,long groups,long decodedCharacters,long ignoredTrailingRecords,Accounting accounting,
                          List<Target> targets,List<Integer> omittedSids,Map<Finding,Long> findings,boolean safeForFitting){
        /** Copies all complete populations. */
        public Summary{targets=List.copyOf(targets);omittedSids=List.copyOf(omittedSids);var copy=new EnumMap<Finding,Long>(Finding.class);copy.putAll(findings);findings=Collections.unmodifiableMap(copy);}
    }
    /** @param summary complete SAM semantics @param evidence every original source byte */
    public record FileAudit(Summary summary,RsemEvidence evidence){ }

    /**
     * Scans a regular complete UTF-8 SAM file and retains its raw-byte evidence.
     * @param source canonical SAM text, not a binary BAM/CRAM stream
     * @param reference complete ordinary prepared reference
     * @param options exact original controls
     * @param limits complete capacities
     * @return complete scan and source evidence
     * @throws IOException source, syntax, semantic or capacity failure
     */
    public static FileAudit read(Path source,RsemReference.Reference reference,Options options,Limits limits)throws IOException{
        Objects.requireNonNull(limits,"limits");if(Files.isSymbolicLink(source))throw new IOException("SAM source must not be a final symlink");
        Path path=source.toRealPath();var before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()>limits.maxSourceBytes())throw new IOException("SAM source must be regular and within the complete byte capacity");
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        Counter input=new Counter(Files.newInputStream(path),limits.maxSourceBytes());Summary summary;
        try(var reader=new InputStreamReader(new DigestInputStream(input,digest),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){summary=read(reader,reference,options,limits,DISCARD,()->{});}
        var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(input.count!=before.size()||after.size()!=before.size()||!after.isRegularFile()||!Objects.equals(after.fileKey(),before.fileKey())||!after.lastModifiedTime().equals(before.lastModifiedTime()))throw new IOException("Complete SAM source identity changed during scanning");
        return new FileAudit(summary,new RsemEvidence(path.toString(),HexFormat.of().formatHex(digest.digest()),input.count));
    }

    /**
     * Consumes canonical SAM from a caller-owned reader. An admitted original
     * decoder can supply BAM/CRAM text; its process, binary/reference snapshots
     * and byte evidence remain the caller's responsibility. No names are sorted,
     * records filtered, mates repaired, qualities imputed, or hits deduplicated.
     * @param source caller-owned complete canonical SAM stream
     * @param reference complete prepared reference
     * @param options exact original controls
     * @param limits complete capacities
     * @param sink all physical records, parser groups and conditions
     * @param check caller checks
     * @return complete original-compatible accounting and explicit input findings
     * @throws IOException unsupported source, parser inconsistency, capacity or caller failure
     */
    public static Summary read(Reader source,RsemReference.Reference reference,Options options,Limits limits,Sink sink,Check check)throws IOException{
        return read(source,reference,options,limits,sink,check,TEXT_LENGTH);
    }

    /**
     * Consumes source-linked decoded SAM plus effective per-record dictionary state.
     * Raw header text remains retained; source length disagreements are findings.
     * @param source complete canonical SAM
     * @param reference complete prepared reference
     * @param options original controls
     * @param limits complete capacities
     * @param sink all source records,groups and findings
     * @param check caller checks
     * @param metadata exact decoder record binding
     * @return complete original source projection
     * @throws IOException source,metadata,semantic,capacity or caller failure
     */
    public static Summary read(Reader source,RsemReference.Reference reference,Options options,Limits limits,Sink sink,Check check,RecordMetadata metadata)throws IOException{
        Objects.requireNonNull(source,"source");Objects.requireNonNull(reference,"reference");Objects.requireNonNull(options,"options");Objects.requireNonNull(limits,"limits");Objects.requireNonNull(sink,"sink");Objects.requireNonNull(check,"check");
        Objects.requireNonNull(metadata,"metadata");
        if(reference.type()<0||reference.type()>1||reference.transcripts().isEmpty()||reference.transcripts().size()>limits.maxReferences())throw new IOException("Require a complete ordinary reference within capacity");
        State state=new State(reference,options,limits,sink,check,metadata);Lines lines=new Lines(source,limits,check);String line;boolean body=false;
        while((line=lines.next())!=null){
            if(line.startsWith("@")&&!body){state.header(line);continue;}
            if(!body){state.finishHeader();body=true;}
            Sam first=state.record(line);Sam second=null;
            if(options.paired()){
                line=lines.next();if(line==null){state.ignoredTrailing++;state.note(Finding.SOURCE_IGNORED_TRAILING_MATE,first.ordinal,"Original parser discards a final unpaired physical record;repair adjacent mates before fitting");break;}
                second=state.record(line);
            }
            state.entry(first,second);
        }
        if(!body)state.finishHeader();state.finish();metadata.finish(state.records);check.run();
        return state.summary(lines.characters);
    }

    private record Sam(long ordinal,String rawName,String name,int flags,String target,int pos,String cigar,
                       String mateTarget,int matePos,int tlen,String sequence,String quality,Map<String,String> tags,int effectiveLength){
        boolean paired(){return(flags&1)!=0;}boolean mapped(){return(flags&4)==0;}boolean reverse(){return(flags&16)!=0;}
        boolean first(){return(flags&64)!=0;}boolean second(){return(flags&128)!=0;}
        int length(){return sequence.equals("*")?0:sequence.length();}
    }
    private static final class Pending{
        final long ordinal;final int category;final String name,mateName,seq,seq2,quality,quality2;final boolean low;
        final List<Hit> hits=new ArrayList<>();
        Pending(long ordinal,int category,Sam a,Sam b,String seq,String seq2,String quality,String quality2,boolean low){this.ordinal=ordinal;this.category=category;name=a.name;mateName=b==null?null:b.name;this.seq=seq;this.seq2=seq2;this.quality=quality;this.quality2=quality2;this.low=low;}
        Group group(){return new Group(ordinal,category,name,mateName,seq,seq2,quality,quality2,low,hits);}
    }
    private static final class State{
        final RsemReference.Reference reference;final Options options;final Limits limits;final Sink sink;final Check check;final RecordMetadata metadata;
        final Map<String,Integer> internal=new HashMap<>();final Map<String,Target> targets=new LinkedHashMap<>();final List<Integer> omitted=new ArrayList<>();
        final Map<String,Long> seen=new HashMap<>();final Map<Finding,Long> findings=new EnumMap<>(Finding.class);final TreeMap<Long,Long> histogram=new TreeMap<>();
        final int[] gene;final boolean polyA;long records,groups,n0,n1,n2,nHits,nMulti,nIso,nameChars,ignoredTrailing;Pending pending;
        State(RsemReference.Reference reference,Options options,Limits limits,Sink sink,Check check,RecordMetadata metadata)throws IOException{
            this.reference=reference;this.options=options;this.limits=limits;this.sink=sink;this.check=check;this.metadata=metadata;gene=new int[reference.transcripts().size()+1];boolean tails=false;
            for(int j=0;j<reference.transcripts().size();j++){var t=reference.transcripts().get(j);if(internal.put(t.id(),j+1)!=null)throw new IOException("Duplicate exact reference transcript");tails|=t.polyATail()>0;}
            int previous=0;var geneIds=new java.util.HashSet<String>();
            for(int g=0;g<reference.groups().size();g++){
                var group=reference.groups().get(g);
                if(group.begin()!=previous||group.end()<=group.begin()||group.end()>reference.transcripts().size()||!geneIds.add(group.id()))throw new IOException("Incomplete or noncontiguous reference gene groups");
                for(int j=group.begin();j<group.end();j++){if(!reference.transcripts().get(j).geneId().equals(group.id()))throw new IOException("Mixed exact gene identifiers in prepared-reference group");gene[j+1]=g;}
                previous=group.end();
            }
            if(previous!=reference.transcripts().size())throw new IOException("Reference groups omit transcripts");
            polyA=tails;
        }
        void note(Finding f,long record,String detail)throws IOException{findings.merge(f,1L,Math::addExact);sink.finding(f,record,detail);}
        void header(String line)throws IOException{
            if(!line.startsWith("@SQ\t"))return;String name=null,len=null;for(String field:line.split("\t",-1)){if(field.startsWith("SN:")){if(name!=null)throw new IOException("Duplicate header SN field");name=field.substring(3);}if(field.startsWith("LN:")){if(len!=null)throw new IOException("Duplicate header LN field");len=field.substring(3);}}
            if(name==null||len==null||!internal.containsKey(name)||targets.containsKey(name)||targets.size()>=limits.maxReferences())throw new IOException("Missing,unknown,duplicate or over-capacity SAM dictionary name");
            int length=number(len,1,Integer.MAX_VALUE,"header length"),sid=internal.get(name);targets.put(name,new Target(name,length,sid));var ref=reference.transcripts().get(sid-1);
            if(length!=ref.length()&&length!=ref.totalLength())note(Finding.HEADER_LENGTH_MISMATCH,0,"Declared length differs from both pre-tail and tail-inclusive prepared reference: "+name);
            else if(ref.polyATail()>0&&length==ref.length())note(Finding.POLYA_PRE_TAIL_DICTIONARY,0,"Pre-tail alignment dictionary is retained without coordinate conversion: "+name);
        }
        void finishHeader()throws IOException{
            if(targets.isEmpty()||targets.size()>reference.transcripts().size())throw new IOException("Original RSEM requires one or more recognized header targets,at most the full reference population");
            var present=new boolean[reference.transcripts().size()+1];for(Target t:targets.values())present[t.internalSid]=true;for(int j=1;j<present.length;j++)if(!present[j])omitted.add(j);
            if(!omitted.isEmpty())note(Finding.OMITTED_REFERENCES,0,"Header omits "+omitted.size()+" prepared transcripts;retain the complete reference and original omission list");
        }
        Sam record(String line)throws IOException{
            check.run();if(++records>limits.maxRecords())throw new IOException("Complete SAM record capacity exceeded");String[] f=line.split("\t",-1);
            if(f.length<11||f[0].isEmpty()||f[0].getBytes(StandardCharsets.UTF_8).length>254)throw new IOException("Incomplete SAM record or invalid query-name capacity at record "+records);
            int flags=number(f[1],0,65535,"SAM flags"),pos=number(f[3],0,Integer.MAX_VALUE,"SAM position"),matePos=number(f[7],0,Integer.MAX_VALUE,"mate position");
            number(f[4],0,255,"mapping quality");int tlen=signed(f[8],"template length");String name=canonical(f[0]);if(name.isEmpty())throw new IOException("Empty original canonical query name");
            int length=f[9].equals("*")?0:f[9].length();if(length>limits.maxReadLength())throw new IOException("Complete record read length exceeds capacity");
            if(f[9].isEmpty()||f[10].isEmpty()||(!f[10].equals("*")&&f[10].length()!=length))throw new IOException("SAM sequence/quality field lengths are inconsistent");
            if(!f[10].equals("*"))for(int i=0;i<f[10].length();i++)if(f[10].charAt(i)<33||f[10].charAt(i)>126)throw new IOException("Invalid original SAM quality byte");
            Map<String,String> tags=new HashMap<>();for(int j=11;j<f.length;j++){String[] tag=f[j].split(":",3);if(tag.length!=3||!tag[0].matches("[A-Za-z][A-Za-z0-9]")||tag[1].length()!=1||tags.put(tag[0],tag[1]+":"+tag[2])!=null)throw new IOException("Malformed or duplicate auxiliary field");}
            Target target=targets.get(f[2]);int textualLength=target==null?0:target.declaredLength();List<String> fields=List.of(f);
            int effectiveLength=metadata.bind(records,fields,textualLength);
            if(effectiveLength<0||!f[2].equals("*")&&effectiveLength==0)throw new IOException("Invalid source decoder reference length");
            if(effectiveLength!=textualLength)note(Finding.DECODED_HEADER_LENGTH_DISAGREEMENT,records,"Source decoder target length "+effectiveLength+" differs from retained SAM header text "+textualLength+";review and bind the original reference");
            if(target!=null){var ref=reference.transcripts().get(target.internalSid()-1);if(effectiveLength!=ref.length()&&effectiveLength!=ref.totalLength()&&effectiveLength!=textualLength)note(Finding.HEADER_LENGTH_MISMATCH,records,"Effective decoder length also differs from the prepared reference");}
            sink.record(new Record(records,fields,effectiveLength));
            if(!name.equals(f[0]))note(Finding.CANONICALIZED_QUERY_NAME,records,"Original parser retains only the first whitespace-delimited query-name word");
            return new Sam(records,f[0],name,flags,f[2],pos,f[5],f[6],matePos,tlen,f[9],f[10],tags,effectiveLength);
        }
        int category(Sam a,Sam b)throws IOException{
            if(a.mapped()&&(b==null||b.mapped()))return 1;if(options.readTypeTag().isEmpty())return 0;
            return positiveTag(a)||b!=null&&positiveTag(b)?2:0;
        }
        boolean positiveTag(Sam a)throws IOException{
            String tag=a.tags.get(options.readTypeTag());if(tag==null||!tag.startsWith("i:"))return false;
            try{long value=Long.parseLong(tag.substring(2));if(value<Integer.MIN_VALUE||value>4294967295L)throw new NumberFormatException();return value>0;}catch(NumberFormatException e){throw new IOException("Original integer read-type tag is out of range",e);}
        }
        void entry(Sam a,Sam b)throws IOException{
            if(options.paired()){
                if(!a.first()){Sam tmp=a;a=b;b=tmp;}
                if(!a.paired()||!b.paired()||!a.first()||!b.second())throw new IOException("Original paired parser requires adjacent first/second mate records");
                if(a.mapped()!=b.mapped())throw new IOException("Original RSEM does not support partial mate alignments");
                if(!a.name.equals(b.name))note(Finding.MATE_NAME_MISMATCH,a.ordinal,"Original parser warns but retains different mate names: "+a.name+" / "+b.name);
                if(a.second()||b.first())note(Finding.AMBIGUOUS_MATE_FLAGS,a.ordinal,"Both mate-role flags are set;resolve the source flags before fitting");
            }else if(a.paired())throw new IOException("Original single-read parser encountered a paired record");
            int category=category(a,b);boolean fresh=category!=1||pending==null||!pending.name.equals(a.name);
            if(fresh){
                if(pending!=null&&pending.category!=1&&!pending.hits.isEmpty())throw new IOException("Original parser rejects accumulated mapped hits in an unalignable group when a following entry begins");
                finish();if(++groups>limits.maxGroups())throw new IOException("Complete parser group capacity exceeded");
                Long previous=seen.get(a.name);
                if(previous!=null){if(previous!=groups-1)note(Finding.NONCONTIGUOUS_QUERY_NAME,a.ordinal,"A canonical query identity reappears after another group");else note(Finding.REPEATED_UNMAPPED_QUERY_NAME,a.ordinal,"Original parser counts repeated non-alignable/name-category entries separately");}
                else{if(seen.size()>=limits.maxDistinctNames()||a.name.length()>limits.maxNameCharacters()-nameChars)throw new IOException("Complete retained query-name capacity exceeded");nameChars+=a.name.length();}
                seen.put(a.name,groups);String seq=sequence(a),seq2=b==null?null:sequence(b);String q=quality(a),q2=b==null?null:quality(b);
                boolean low=low(seq,polyA,options.seedLength())||(b!=null&&seq2.length()<options.seedLength());
                if(b!=null)low=seq.length()<options.seedLength()||seq2.length()<options.seedLength()||low(seq,polyA,options.seedLength())&&low(seq2,polyA,options.seedLength());
                if(low)note(Finding.BELOW_SEED_OR_POLYA,a.ordinal,"Original low-quality seed/poly-A flag is retained without removing the parser group");
                pending=new Pending(groups,category,a,b,seq,seq2,q,q2,low);
            }else{
                if(a.length()!=pending.seq.length()||b!=null&&b.length()!=pending.seq2.length()){
                    if(options.release()!=RsemAbundance.Release.V1_2_28)throw new IOException("Original RSEM1.3.x rejects inconsistent lengths among alignments of one query group");
                    note(Finding.HISTORICAL_LENGTH_MISMATCH,a.ordinal,"Original1.2.28 retains first alignment read lengths;resolve the conflicting source lengths before fitting");
                }
                String next=permissiveSequence(a),next2=b==null?null:permissiveSequence(b);
                if(!pending.seq.equals(next)||b!=null&&!pending.seq2.equals(next2))note(Finding.IGNORED_SECONDARY_SEQUENCE,a.ordinal,"Original parser retains first alignment sequence and ignores later sequence content");
                if(options.qualities()&&(!Objects.equals(pending.quality,rawQuality(a))||b!=null&&!Objects.equals(pending.quality2,rawQuality(b))))note(Finding.IGNORED_SECONDARY_QUALITY,a.ordinal,"Original parser retains first alignment quality and ignores later quality content");
            }
            if(category==1){
                if(pending.hits.size()>=limits.maxHitsPerGroup())throw new IOException("Complete group hit capacity exceeded");
                Hit hit=hit(a,b);pending.hits.add(hit);
            }
        }
        Hit hit(Sam a,Sam b)throws IOException{
            Target t=targets.get(a.target);if(t==null||a.pos<1)throw new IOException("Mapped source target/position is absent from the complete dictionary");cigar(a);
            if(b!=null){if(!a.target.equals(b.target))throw new IOException("Original RSEM rejects discordant mate transcript targets");cigar(b);}
            int sourceLength=b==null?a.effectiveLength:a.ordinal>b.ordinal?a.effectiveLength:b.effectiveLength;
            if((long)a.pos-1+a.length()>sourceLength)note(Finding.OUTSIDE_REFERENCE,a.ordinal,"Read span exceeds the original effective reference length");
            if(b!=null){
                if(b.pos<1||(long)b.pos-1+b.length()>sourceLength)note(Finding.OUTSIDE_REFERENCE,b.ordinal,"Mate span exceeds original effective reference length");
                if(a.reverse()==b.reverse())note(Finding.SAME_STRAND_MATES,a.ordinal,"Original input validator rejects mates aligned to the same strand");
                Sam left=a.pos<b.pos?a:b;if((long)left.pos-1+Math.abs((long)left.tlen)>sourceLength)note(Finding.TEMPLATE_SPAN_OUTSIDE_REFERENCE,left.ordinal,"Original input validator's template span exceeds the effective reference");
            }
            long position=a.reverse()?(long)sourceLength-(a.pos-1)-a.length():a.pos-1;
            long insert=b==null?0:a.reverse()?(long)(a.pos-1)+a.length()-(b.pos-1):(long)(b.pos-1)+b.length()-(a.pos-1);
            if(position<Integer.MIN_VALUE||position>Integer.MAX_VALUE||insert<Integer.MIN_VALUE||insert>Integer.MAX_VALUE)note(Finding.SOURCE_INTEGER_WRAP,a.ordinal,"Source32-bit coordinate arithmetic is outside its safe range");
            if(b!=null&&insert<=0)note(Finding.NONPOSITIVE_INFERRED_INSERT,a.ordinal,"Original parser emits a nonpositive inferred fragment length;review input and original fitting diagnostics");
            return new Hit(a.reverse()?-t.internalSid:t.internalSid,(int)position,(int)insert,a.ordinal,b==null?0:b.ordinal);
        }
        void cigar(Sam a)throws IOException{
            if(!a.cigar.matches("[0-9]+[M=X]"))throw new IOException("Original RSEM requires exactly one M,= or X CIGAR operation");
            try{if(Long.parseLong(a.cigar.substring(0,a.cigar.length()-1))!=a.length())throw new IOException("Original CIGAR match length differs from sequence length");}catch(NumberFormatException e){throw new IOException("CIGAR match length overflow",e);}
        }
        String sequence(Sam a)throws IOException{
            if(a.length()==0)note(Finding.EMPTY_SEQUENCE,a.ordinal,"Original parser retained an empty sequence;resolve it before fitting");String seq=permissiveSequence(a);
            for(int i=0;i<seq.length();i++)if("ACGTN".indexOf(seq.charAt(i))<0)throw new IOException("Original RSEM sequence decoder supports only A,C,G,T,N");return seq;
        }
        String quality(Sam a)throws IOException{
            if(!options.qualities())return null;if(a.quality.equals("*")&&a.length()>0)note(Finding.MISSING_QUALITY,a.ordinal,"Original parser's missing-quality byte wraps to ASCII space;resolve qualities or explicitly select a quality-free read type");return rawQuality(a);
        }
        void finish()throws IOException{
            if(pending==null)return;check.run();Group group=pending.group();
            if(group.category()==0)n0++;else if(group.category()==2)n2++;else{
                n1++;long count=group.hits().size();if(count<1)throw new IOException("Alignable parser group has no source hits");if(count>limits.maxHits()-nHits)throw new IOException("Complete hit population exceeds capacity");nHits+=count;
                if(count>1)nIso++;int gid=gene[Math.abs(group.hits().get(0).signedSid())];for(Hit hit:group.hits())if(gene[Math.abs(hit.signedSid())]!=gid){nMulti++;break;}
                if(!histogram.containsKey(count)&&histogram.size()>=limits.maxHistogramBins())throw new IOException("Complete multiplicity histogram exceeds capacity");histogram.merge(count,1L,Math::addExact);
            }
            if(group.category()!=1&&!group.hits().isEmpty())note(Finding.SOURCE_IGNORED_MAPPED_HITS,group.hits().get(0).mate1Record(),"Original parser drops mapped hits accumulated in a non-alignable final group;resolve the mixed-category source");
            sink.group(group);pending=null;
        }
        Summary summary(long chars){var bins=new ArrayList<RsemCounts.Bin>();histogram.forEach((k,v)->bins.add(new RsemCounts.Bin(k,v)));var accounting=new Accounting(n0,n1,n2,n1-nMulti,nMulti,nIso,nHits,options.readType(),bins);boolean safe=findings.keySet().stream().noneMatch(Finding::blocksFitting);return new Summary(records,groups,chars,ignoredTrailing,accounting,new ArrayList<>(targets.values()),omitted,findings,safe);}
    }

    private static String canonical(String name){for(int i=0;i<name.length();i++)if(" \t\n\r\f\u000b".indexOf(name.charAt(i))>=0)return name.substring(0,i);return name;}
    private static String permissiveSequence(Sam a){String s=a.sequence.equals("*")?"":a.sequence.toUpperCase(java.util.Locale.ROOT);if(!a.reverse())return s;StringBuilder out=new StringBuilder(s.length());for(int i=s.length()-1;i>=0;i--){char c=s.charAt(i);out.append(c=='A'?'T':c=='T'?'A':c=='C'?'G':c=='G'?'C':c);}return out.toString();}
    private static String rawQuality(Sam a){String q=a.quality.equals("*")?" ".repeat(a.length()):a.quality;return a.reverse()?new StringBuilder(q).reverse().toString():q;}
    private static boolean low(String seq,boolean polyA,int seed){
        if(seq.length()<seed)return true;if(!polyA)return false;int a=0,t=0,ao=0,to=0;for(int i=0;i<seq.length();i++){char c=seq.charAt(i);if(c=='A'){a++;if(i<25)ao++;}if(c=='T'){t++;if(i>=seq.length()-25)to++;}}
        int threshold=(int)(0.9*seq.length()-1.5*Math.sqrt(seq.length())+0.5);return a>=threshold?ao>=13:t>=threshold&&to>=13;
    }
    private static int number(String text,int min,int max,String role)throws IOException{if(!text.matches("[0-9]+"))throw new IOException("Noninteger "+role);try{long n=Long.parseLong(text);if(n<min||n>max)throw new NumberFormatException();return(int)n;}catch(NumberFormatException e){throw new IOException("Out-of-range "+role,e);}}
    private static int signed(String text,String role)throws IOException{if(!text.matches("-?[0-9]+"))throw new IOException("Noninteger "+role);try{return Integer.parseInt(text);}catch(NumberFormatException e){throw new IOException("Out-of-range "+role,e);}}
    private static final class Lines{
        final BufferedReader reader;final Limits limits;final Check check;long characters;
        Lines(Reader reader,Limits limits,Check check){this.reader=reader instanceof BufferedReader b?b:new BufferedReader(reader);this.limits=limits;this.check=check;}
        int nextChar()throws IOException{int c=reader.read();if(c>=0){if(++characters>limits.maxDecodedCharacters())throw new IOException("Complete decoded SAM character capacity exceeded");}return c;}
        String next()throws IOException{check.run();StringBuilder s=new StringBuilder();for(;;){int c=nextChar();if(c<0)return s.length()==0?null:s.toString();if(c=='\n')return s.toString();if(c=='\r'){if(nextChar()!='\n')throw new IOException("Bare CR in canonical SAM input");return s.toString();}if(c==0||(c<32&&c!='\t'))throw new IOException("Unexpected SAM control character");if(s.length()>=limits.maxLineCharacters())throw new IOException("Complete SAM line capacity exceeded");s.append((char)c);}}
    }
    private static final class Counter extends FilterInputStream{
        final long max;long count;Counter(InputStream in,long max){super(in);this.max=max;}
        void add(long n)throws IOException{if(n>0){if(n>max-count)throw new IOException("Complete SAM byte capacity exceeded");count+=n;}}
        @Override public int read()throws IOException{int b=in.read();if(b>=0)add(1);return b;}
        @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);add(n);return n;}
    }
    private static final class MetadataLines implements RecordMetadata{
        final Lines lines;
        MetadataLines(Reader source,Limits limits,Check check)throws IOException{
            lines=new Lines(source,limits,check);
            if(!"rsem-decoded-records-v1".equals(lines.next())||!"ordinal\tquery_name\tflags\treference\tposition\tread_length\tcigar\teffective_reference_length".equals(lines.next()))throw new IOException("Unsupported source decoder metadata schema");
        }
        @Override public int bind(long ordinal,List<String> fields,int textLength)throws IOException{
            String line=lines.next();if(line==null)throw new IOException("Truncated source decoder metadata");String[] m=line.split("\t",-1);
            try{
                if(m.length!=8||Long.parseLong(m[0])!=ordinal||!m[1].equals(fields.get(0))||number(m[2],0,65535,"metadata flags")!=number(fields.get(1),0,65535,"SAM flags")
                        ||!m[3].equals(fields.get(2))||number(m[4],0,Integer.MAX_VALUE,"metadata position")!=number(fields.get(3),0,Integer.MAX_VALUE,"SAM position")
                        ||number(m[5],0,Integer.MAX_VALUE,"metadata read length")!=(fields.get(9).equals("*")?0:fields.get(9).length())||!m[6].equals(fields.get(5)))throw new IOException("Source decoder metadata differs from the complete physical SAM record");
            }catch(NumberFormatException e){throw new IOException("Invalid source decoder record ordinal",e);}
            int length=number(m[7],0,Integer.MAX_VALUE,"effective source reference length");if(fields.get(2).equals("*")&&length!=0)throw new IOException("No-target decoder metadata has a reference length");return length;
        }
        @Override public void finish(long records)throws IOException{
            if(!("END\t"+records).equals(lines.next())||lines.next()!=null)throw new IOException("Source decoder metadata lacks complete matching terminal population");
        }
    }
}

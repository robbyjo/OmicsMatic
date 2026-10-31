/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Bounded structural VCF intake for the verified offline FAVOR profile. Supports
 * explicit variant-only or full diploid-GT inputs on one selected autosome.
 * Preserves source order and raw metadata/fields; no variant, allele, genotype,
 * missingness, phase, dosage or INFO/FORMAT-value normalization is performed.
 * The original converter remains responsible for its additional VCF semantics.
 */
public final class FavorVcfReader implements AutoCloseable {
    /** Variant-only input has exactly eight columns and never invents sample/genotype data. */
    public enum Mode { VARIANTS_ONLY, DIPLOID_GT }
    /** Exact canonical autosome, or the observed source's case-insensitive leading chr removal. */
    public enum Chromosomes { EXACT, SOURCE_IGNORE_CHR }
    /** Encoded/decoded bytes, line/header, sample, variant and genotype-call capacities. */
    public record Limits(long maximumBytes,long maximumDecodedBytes,int maximumLineBytes,
            int maximumHeaderLines,long maximumHeaderBytes,int maximumSamples,long maximumVariants,long maximumCalls) {
        public Limits {
            if(maximumBytes<1||maximumDecodedBytes<1||maximumLineBytes<1||maximumHeaderLines<1||maximumHeaderBytes<1
                    ||maximumSamples<0||maximumSamples>1_000_000||maximumVariants<0||maximumCalls<0)throw new IllegalArgumentException("Invalid complete VCF byte,line,header,sample,variant or call capacity");
        }
        /** Conservative streaming defaults; no accepted population is truncated. */
        public static Limits defaults(){return new Limits(1_073_741_824L,4_294_967_296L,16_777_216,10000,33_554_432,100000,1_000_000,100_000_000L);}
    }
    /** The analysis mode and chromosome conversion are explicit admission decisions. */
    public record Options(Mode mode,int chromosome,Chromosomes chromosomes,Limits limits) {
        public Options {
            Objects.requireNonNull(mode,"mode");Objects.requireNonNull(chromosomes,"chromosomes");Objects.requireNonNull(limits,"limits");
            if(chromosome<1||chromosome>22)throw new IllegalArgumentException("Require one explicitly selected autosome1..22");
        }
    }
    /** -1 is a missing allele. Raw GT and all supplied FORMAT strings remain available. */
    public record Call(String sample,String rawGenotype,int first,int second,boolean phased,List<String> formatValues) {
        public Call {formatValues=List.copyOf(formatValues);}
    }
    /** One original-order biallelic DNA record, retaining source chromosome and opaque metadata values. */
    public record Row(long row,String sourceChromosome,FavorCsvJoin.Variant variant,String id,
            String quality,String filter,String info,List<String> formats,List<Call> calls) {
        public Row {formats=List.copyOf(formats);calls=List.copyOf(calls);}
        /** Observed source annotation/id representation; original literal ID remains in id(). */
        public String sourceGdsId(){return id.equals(".")?"":id;}
    }
    /** Byte counters include buffered input; completion requires observing decoded EOF. */
    public record Report(long encodedBytes,long decodedBytes,long variants,long genotypeCalls,
            long chromosomePrefixRows,boolean gzip,boolean complete) { }

    private static final List<String> FIXED=List.of("#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO");
    private final Path path;
    private final Options options;
    private final Runnable check;
    private final Counted encoded,decoded;
    private final InputStream stream;
    private final boolean gzip;
    private final List<String> headerLines,samples;
    private final Set<String> declaredFormats;
    private long lineNumber,variants,calls,prefixRows,lastLineBytes;
    private boolean complete,closed,failed;

    private FavorVcfReader(Path supplied,Options options,Runnable check)throws IOException {
        path=supplied.toAbsolutePath().normalize();this.options=options;this.check=check;Limits limits=options.limits();
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>limits.maximumBytes())throw failure("Missing,linked or oversized VCF input");
        encoded=new Counted(Files.newInputStream(path),limits.maximumBytes(),"encoded");var raw=new BufferedInputStream(encoded,8192);InputStream owned=raw;
        try {
            raw.mark(2);int first=raw.read(),second=raw.read();raw.reset();gzip=first==31&&second==139;
            if(gzip)owned=new GZIPInputStream(raw,8192);decoded=new Counted(owned,limits.maximumDecodedBytes(),"decoded");stream=new BufferedInputStream(decoded,8192);
            var lines=new ArrayList<String>();var formats=new HashSet<String>();Map<String,String> gtDefinition=null;long bytes=0;
            String firstLine=line();if(firstLine==null||!firstLine.matches("##fileformat=VCFv4[.][0-9]+"))throw failure("Require literal UTF-8 VCFv4 fileformat as the first line;no BOM or encoding repair");
            String value=firstLine;List<String> foundSamples=null;
            for(;;) {
                if(lines.size()>=limits.maximumHeaderLines()||lastLineBytes>limits.maximumHeaderBytes()-bytes)throw failure("Complete VCF header capacity exceeded");bytes+=lastLineBytes;lines.add(value);
                if(value.startsWith("##FORMAT=")) {
                    var definition=definition(value.substring("##FORMAT=".length()));String id=definition.get("ID");if(!token(id)||!formats.add(id))throw failure("Require distinct FORMAT header IDs");if(id.equals("GT"))gtDefinition=definition;
                } else if(!value.startsWith("##")) {
                    String[] fields=value.split("\t",limits.maximumSamples()+10);
                    if(fields.length<8||!List.of(java.util.Arrays.copyOfRange(fields,0,8)).equals(FIXED))throw failure("Invalid fixed VCF column header");
                    if(options.mode()==Mode.VARIANTS_ONLY){if(fields.length!=8)throw failure("VARIANTS_ONLY requires exactly eight columns;sample columns are never silently dropped");foundSamples=List.of();}
                    else {
                        if(fields.length<10||!fields[8].equals("FORMAT")||fields.length-9>limits.maximumSamples())throw failure("DIPLOID_GT requires FORMAT and a bounded nonempty sample population");
                        foundSamples=List.of(java.util.Arrays.copyOfRange(fields,9,fields.length));
                        if(foundSamples.stream().anyMatch(s->!token(s))||new HashSet<>(foundSamples).size()!=foundSamples.size())throw failure("VCF sample IDs must be distinct nonempty tokens");
                        if(gtDefinition==null||!"1".equals(gtDefinition.get("Number"))||!"String".equals(gtDefinition.get("Type")))throw failure("DIPLOID_GT requires a declared FORMAT GT,Number=1,Type=String");
                    }
                    break;
                }
                value=line();if(value==null)throw failure("Missing VCF column header");
            }
            headerLines=List.copyOf(lines);samples=List.copyOf(foundSamples);declaredFormats=Set.copyOf(formats);
        } catch(IOException|RuntimeException failure){try{owned.close();}catch(IOException closing){failure.addSuppressed(closing);}throw failure;}
    }
    /**
     * Admit a plain or gzip/BGZF VCF header with explicit mode/chromosome policy.
     * @param path protected regular original/snapshot file
     * @param options complete resource capacities and required scientific input mode
     * @param cancellation cooperative deadline/cancellation check
     * @return owned streaming reader; the caller must close it and verify external hashes
     * @throws IOException invalid file/header/encoding/compression or exceeded capacity
     */
    public static FavorVcfReader open(Path path,Options options,Runnable cancellation)throws IOException {
        Objects.requireNonNull(path,"path");Objects.requireNonNull(options,"options");Objects.requireNonNull(cancellation,"cancellation");return new FavorVcfReader(path,options,cancellation);
    }
    /** Immutable original header lines, without record terminators. */
    public List<String> headerLines(){return headerLines;}
    /** Immutable original sample order; empty in explicit variant-only mode. */
    public List<String> samples(){return samples;}
    /** Current bounded counters; partial scans do not report completion. */
    public Report report(){return new Report(encoded.bytes,decoded.bytes,variants,calls,prefixRows,gzip,complete);}
    /**
     * Read one original-order record. GT must have exactly two explicit allele
     * fields,including fully missing ./.,because the tested original conversion
     * shifts subsequent phase flags after shorter calls. No padding or repair is
     * inferred. Non-GT FORMAT and INFO values remain opaque original strings.
     * @return complete row or null after EOF and compression CRC validation
     * @throws IOException invalid record/call, failed scan or exceeded capacity
     */
    public Row next()throws IOException {
        if(closed)throw failure("VCF reader is closed");if(failed)throw failure("Failed VCF scans cannot be resumed");if(complete)return null;
        try {
            String value=line();if(value==null){complete=true;return null;}if(value.isEmpty()||value.startsWith("#"))throw failure("Unexpected blank/header line after VCF column admission");
            if(variants>=options.limits().maximumVariants())throw failure("Complete VCF variant-row capacity exceeded");
            int width=options.mode()==Mode.VARIANTS_ONLY?8:9+samples.size();String[] fields=value.split("\t",width+1);
            if(fields.length!=width)throw failure("VCF data width differs from exact header/sample population");
            String rawChromosome=fields[0],chromosome=rawChromosome;
            if(options.chromosomes()==Chromosomes.SOURCE_IGNORE_CHR&&chromosome.length()>=3&&chromosome.regionMatches(true,0,"chr",0,3))chromosome=chromosome.substring(3);
            if(!chromosome.equals(Integer.toString(options.chromosome())))throw failure("VCF chromosome differs from the explicit canonical autosome/prefix policy");
            if(!fields[1].matches("[1-9][0-9]*"))throw failure("VCF position must be a canonical positive one-based integer");int position;
            try{position=Integer.parseInt(fields[1]);}catch(NumberFormatException overflow){throw failure("VCF position exceeds supported signed32 range");}
            FavorCsvJoin.Variant variant;
            try{variant=new FavorCsvJoin.Variant(options.chromosome(),position,fields[3],fields[4]);}catch(IllegalArgumentException unsupported){throw failure("Require explicit uppercase biallelic DNA alleles;normalize/split or select a separately supported profile");}
            for(int i:new int[]{2,5,6,7})if(!token(fields[i]))throw failure("VCF fixed ID/QUAL/FILTER/INFO fields require nonempty tokens");
            if(!fields[5].equals(".")){try{if(!fields[5].matches("[+]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new NumberFormatException();double quality=Double.parseDouble(fields[5]);if(!Double.isFinite(quality)||quality<0)throw new NumberFormatException();}catch(NumberFormatException invalid){throw failure("Invalid nonnegative decimal VCF QUAL");}}
            var formats=new ArrayList<String>();var genotypes=new ArrayList<Call>();
            if(options.mode()==Mode.DIPLOID_GT) {
                formats.addAll(List.of(fields[8].split(":",1025)));if(formats.size()>1024||!formats.get(0).equals("GT")||new HashSet<>(formats).size()!=formats.size()||!declaredFormats.containsAll(formats))throw failure("Require unique declared FORMAT fields with GT first on every genotype record");
                if(samples.size()>options.limits().maximumCalls()-calls)throw failure("Complete VCF genotype-call capacity exceeded");
                for(int index=0;index<samples.size();index++) {
                    check.run();String[] values=fields[9+index].split(":",formats.size()+1);if(values.length>formats.size())throw failure("Extra sample FORMAT fields at sample column "+(index+1));
                    String gt=values[0];if(!gt.matches("[01.][/|][01.]"))throw failure("Require two explicit diploid GT allele fields at sample column "+(index+1)+";single-dot/haploid calls can corrupt original phase alignment and are not silently padded");
                    genotypes.add(new Call(samples.get(index),gt,allele(gt.charAt(0)),allele(gt.charAt(2)),gt.charAt(1)=='|',List.of(values)));
                }
            }
            calls+=genotypes.size();variants++;if(!chromosome.equals(rawChromosome))prefixRows++;
            return new Row(variants,rawChromosome,variant,fields[2],fields[5],fields[6],fields[7],formats,genotypes);
        } catch(IOException|RuntimeException failure){failed=true;throw failure;}
    }
    private String line()throws IOException {
        check.run();var bytes=new ByteArrayOutputStream();int value;boolean any=false;lastLineBytes=0;
        while((value=stream.read())!=-1){any=true;lastLineBytes++;if(value=='\n')break;if(bytes.size()>=options.limits().maximumLineBytes())throw failure("Complete VCF line byte capacity exceeded");bytes.write(value);if((bytes.size()&8191)==0)check.run();}
        if(!any)return null;lineNumber++;byte[] raw=bytes.toByteArray();int size=raw.length;if(size>0&&raw[size-1]=='\r')size--;
        String result;try{result=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,size)).toString();}catch(IOException invalid){throw new IOException("Invalid VCF UTF-8: "+path+", line "+lineNumber,invalid);}
        if(result.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\t'))throw failure("Unexpected control character in VCF logical line");return result;
    }
    private Map<String,String> definition(String value)throws IOException {
        if(!value.startsWith("<")||!value.endsWith(">"))throw failure("Invalid structured FORMAT header");
        var out=new LinkedHashMap<String,String>();var field=new StringBuilder();boolean quoted=false,escaped=false;
        for(int i=1;i<value.length();i++) {
            char c=value.charAt(i);boolean end=i==value.length()-1;
            if(end||c==','&&!quoted){if(quoted||escaped)throw failure("Unclosed FORMAT header quote");String item=field.toString().trim();int equal=item.indexOf('=');if(equal<1||out.putIfAbsent(item.substring(0,equal).trim(),item.substring(equal+1).trim())!=null)throw failure("Invalid/duplicate FORMAT header attribute");field.setLength(0);continue;}
            field.append(c);if(escaped){escaped=false;continue;}if(quoted&&c=='\\'){escaped=true;continue;}if(c=='"')quoted=!quoted;
        }
        return out;
    }
    private static int allele(char value){return value=='.'?-1:value-'0';}
    private static boolean token(String value){return value!=null&&!value.isEmpty()&&value.codePoints().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c));}
    private IOException failure(String cause){return new IOException(cause+": "+path+", line "+lineNumber+", variants admitted="+variants);}
    private final class Counted extends InputStream {
        private final InputStream source;
        private final long maximum;
        private final String role;
        private long bytes;
        Counted(InputStream source,long maximum,String role){this.source=source;this.maximum=maximum;this.role=role;}
        @Override public int read()throws IOException {check.run();int value=source.read();if(value>=0){if(bytes>=maximum)throw failure("Complete VCF "+role+" byte capacity exceeded");bytes++;}return value;}
        @Override public int read(byte[] buffer,int offset,int length)throws IOException {
            Objects.checkFromIndexSize(offset,length,buffer.length);if(length==0)return 0;check.run();long remaining=maximum-bytes;int allowed=remaining>=length?length:(int)Math.min(length,remaining+1);int count=source.read(buffer,offset,allowed);if(count>0){if(count>remaining)throw failure("Complete VCF "+role+" byte capacity exceeded");bytes+=count;}return count;
        }
        @Override public void close()throws IOException{source.close();}
    }
    /** Close owned decoding/file handles; early closure never certifies completion. */
    @Override public void close()throws IOException {if(!closed){closed=true;stream.close();}}
}

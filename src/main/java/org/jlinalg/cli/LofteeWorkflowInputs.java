/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.reference.*;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.GZIIndex;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete immutable input copies, exact FASTA/FAI/GZI validation and per-variant reference checks. */
final class LofteeWorkflowInputs {
    private LofteeWorkflowInputs() { }
    record Binding(Path original,Path copy,NextflowFiles.Pin pin) {
        void verify()throws IOException{require(pin.equals(NextflowFiles.pin(original,pin.bytes(),()->{}))&&pin.equals(NextflowFiles.pin(copy,pin.bytes(),()->{})),"Original/captured LOFTEE input changed: "+original);}
        Map<String,Object> describe(){return Map.of("original",original.toString(),"copy",copy.toString(),"bytes",pin.bytes(),"sha256",pin.sha256());}
    }
    record Prepared(Path directory,List<Binding> bindings,Map<String,Object> inspection) {
        void verify()throws IOException{for(Binding b:bindings)b.verify();}
    }
    static Prepared prepare(Path stage,LofteeWorkflowRequest.Request r,List<String> warnings)throws IOException {
        Path inputs=Files.createDirectory(stage.resolve("inputs"));var bindings=new ArrayList<Binding>();long remaining=r.resourceBytes();
        for(var e:r.files().entrySet()){
            long capacity=e.getKey().equals(r.inputName())?Math.min(r.inputBytes(),remaining):remaining;var b=capture(e.getValue(),inputs.resolve(e.getKey()),capacity);remaining-=b.pin().bytes();bindings.add(b);
        }
        if(r.transcripts().get("kind").equals("vep-cache")){
            Path original=Path.of(text(r.transcripts().get("directory")));NextflowFiles.directory(original);Path target=Files.createDirectory(inputs.resolve("cache"));
            try(var walk=Files.walk(original)){
                for(Path p:walk.sorted().toList()){
                    require(!Files.isSymbolicLink(p),"Cache symlinks require an explicitly prepared canonical resource tree; no external resource is inferred: "+p);if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;require(bindings.size()<r.outputFiles(),"Cache/input file capacity exceeded; no files may be omitted");var b=capture(p,target.resolve(original.relativize(p)),remaining);remaining-=b.pin().bytes();bindings.add(b);
                }
            }
        }else{bgzf(inputs.resolve("genes.gtf.gz"));try(var in=new GZIPInputStream(Files.newInputStream(inputs.resolve("genes.gtf.gz.tbi")))){require(Arrays.equals(in.readNBytes(4),new byte[]{'T','B','I',1}),"GTF requires a complete BGZF tabix TBI index; do not relabel another index");}}
        if(r.gerp().get("kind").equals("bigwig")){Path file=inputs.resolve("gerp.bw");try(var in=Files.newInputStream(file)){byte[] first=in.readNBytes(4);require(first.length==4&&(ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).getInt()==0x888ffc26||ByteBuffer.wrap(first).order(ByteOrder.BIG_ENDIAN).getInt()==0x888ffc26),"GERP resource is not a BigWig file");}}
        if(r.conservation().get("kind").equals("sqlite"))try(var in=Files.newInputStream(inputs.resolve("conservation.sqlite"))){require(Arrays.equals(in.readNBytes(16),"SQLite format 3\0".getBytes(StandardCharsets.US_ASCII)),"Conservation resource is not SQLite format3; schema/query failures remain original failures");}
        long[] decoded={0};var main=indexed(inputs.resolve(r.fastaName("reference",r.fasta())),r.fasta(),r,decoded);ReferenceSequenceFile ancestor=null;
        try{
            if(r.ancestor().get("kind").equals("fasta"))ancestor=indexed(inputs.resolve(r.fastaName("ancestor",r.ancestor())),r.ancestor(),r,decoded);
            var inspection=inspectVcf(inputs.resolve(r.inputName()),main,ancestor,r,decoded);inspection.put("FASTA_FAI_validated_against_complete_sequence",true);inspection.put("BGZF_GZI_validated_against_complete_block_boundaries",r.fasta().get("compression").equals("bgzip"));inspection.put("decoded_preflight_characters",decoded[0]);inspection.put("input_files",bindings.stream().map(Binding::describe).toList());inspection.put("resource_labels_are_caller_assertions",true);inspection.put("reference_origin",r.referenceOrigin());NextflowFiles.writeJson(stage.resolve("native-input-audit.json"),inspection);
            warnings.add("REFERENCE/RELEASE LABELS ARE CALLER ASSERTIONS. Complete original inputs and indices are retained; native checks do not certify genome-release provenance, cache contents, biological transcript completeness or paper resource identity.");
            if(ancestor!=null)warnings.add("ORIGINAL ANCESTRAL SCOPE: the captured plugin skips indels and non-biallelic-SNP variation-feature allele strings. No ancestral state is imputed when its original check is not applied.");
            if(r.transcripts().get("kind").equals("vep-cache"))warnings.add("TRUSTED ORIGINAL CACHE REQUIRED: original VEP deserializes its cache objects. This explicit trust declaration and file integrity checks are not independent cache authenticity or biological coverage certification.");
            return new Prepared(inputs,List.copyOf(bindings),Collections.unmodifiableMap(inspection));
        }finally{main.close();if(ancestor!=null)ancestor.close();}
    }
    private static Binding capture(Path original,Path copy,long maximum)throws IOException {
        require(maximum>=0,"Complete LOFTEE resource/input capacity exceeded");var pin=NextflowFiles.pin(original,maximum,()->{});Files.createDirectories(copy.getParent());
        try(var in=Files.newInputStream(original,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(copy,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];long count=0;for(int n=in.read(buffer);n>=0;n=in.read(buffer)){require(n<=maximum-count,"Input grew beyond complete capture capacity");out.write(buffer,0,n);count+=n;}require(count==pin.bytes(),"Input changed during capture");}
        var result=new Binding(original,copy,pin);result.verify();return result;
    }
    private static void bgzf(Path file)throws IOException {try(var in=new BufferedInputStream(Files.newInputStream(file))){require(BlockCompressedInputStream.isValidFile(in),"Indexed sequence/GTF requires BGZF, not ordinary gzip or relabeled data: "+file);}}
    private static ReferenceSequenceFile indexed(Path fasta,Map<String,Object> config,LofteeWorkflowRequest.Request r,long[] decoded)throws IOException {
        boolean compressed=config.get("compression").equals("bgzip");try(var in=Files.newInputStream(fasta)){byte[] magic=in.readNBytes(2);require(compressed||!(magic.length==2&&magic[0]==31&&magic[1]==(byte)139),"FASTA compression differs from declared none");}if(compressed)bgzf(fasta);
        try(var stream=compressed?new BlockCompressedInputStream(fasta.toFile()):Files.newInputStream(fasta)){byte[] buffer=new byte[65536];for(int n=stream.read(buffer);n>=0;n=stream.read(buffer)){require(n<=r.decodedCharacters()-decoded[0],"Complete decoded reference/VCF capacity exceeded; no sequence may be omitted");decoded[0]+=n;}}
        FastaSequenceIndex supplied,actual;
        try{supplied=new FastaSequenceIndex(Path.of(fasta+".fai"));actual=FastaSequenceIndexCreator.buildFromFasta(fasta);}catch(RuntimeException e){throw new IOException("Invalid complete FASTA/FAI: "+fasta,e);}
        require(actual.size()>0&&actual.size()<=1000000&&actual.equals(supplied),"FAI differs from complete FASTA lengths, offsets, sequence order or LF/CRLF widths; preserve the supplied index and explicitly prepare a correct new one");
        for(var entry:actual)require(entry.getSize()>0,"Empty reference sequence: "+entry.getContig());
        if(!compressed)return new IndexedFastaSequenceFile(fasta,supplied);
        var provided=GZIIndex.loadIndex(Path.of(fasta+".gzi"));var built=GZIIndex.buildIndex(fasta);var entries=new ArrayList<>(built.getIndexEntries());
        // HTSJDK includes a terminal empty BGZF block; htslib omits that exact EOF marker from .gzi.
        if(!entries.equals(provided.getIndexEntries())&&!entries.isEmpty()){
            var last=entries.get(entries.size()-1);long size=Files.size(fasta);byte[] eof=HexFormat.of().parseHex("1f8b08040000000000ff0600424302001b0003000000000000000000");
            try(var channel=Files.newByteChannel(fasta)){if(size>=eof.length&&last.getCompressedOffset()==size-eof.length){channel.position(size-eof.length);ByteBuffer tail=ByteBuffer.allocate(eof.length);while(tail.hasRemaining()&&channel.read(tail)>=0){/* complete exact terminal marker */}if(Arrays.equals(tail.array(),eof))entries.remove(entries.size()-1);}}
        }
        require(entries.equals(provided.getIndexEntries()),"GZI differs from the complete BGZF block/decoded-offset index; no compressed reference seek is inferred");return new BlockCompressedIndexedFastaSequenceFile(fasta,supplied,provided);
    }
    private static Map<String,Object> inspectVcf(Path input,ReferenceSequenceFile reference,ReferenceSequenceFile ancestor,LofteeWorkflowRequest.Request r,long[] decoded)throws IOException {
        long variants=0,alts=0;int samples=0;boolean version=false,header=false;String[] columns=null;
        try(var lines=new Lines(input,r.inputCompression().equals("gzip"),r.lineCharacters(),r.decodedCharacters(),decoded)){
            for(String line;(line=lines.next())!=null;){
                if(!header){
                    if(line.startsWith("##fileformat=VCFv4.")){require(!version,"Duplicate VCF version header");version=true;}
                    else if(line.startsWith("##INFO=<ID=CSQ,"))throw new IOException("Input has prior CSQ; preserve it and explicitly supply the intended unannotated input rather than replacing calls silently");
                    else if(line.startsWith("##")){/* complete original metadata bytes are captured */}
                    else if(line.startsWith("#CHROM\t")){columns=line.split("\t",-1);require(version&&(columns.length==8||columns.length>=10),"Require a complete VCF4 header and original sample columns");String[] names={"#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO"};for(int i=0;i<8;i++)require(columns[i].equals(names[i]),"Unexpected VCF site column");if(columns.length>8){require(columns[8].equals("FORMAT"),"Missing FORMAT header");var seen=new HashSet<String>();for(int i=9;i<columns.length;i++)require(!columns[i].isEmpty()&&seen.add(columns[i]),"Empty/duplicate original sample ID");samples=columns.length-9;}header=true;}
                    else throw new IOException("Invalid/incomplete VCF header");continue;
                }
                require(!line.isEmpty()&&!line.startsWith("#"),"Blank/header line within VCF records");String[] row=line.split("\t",-1);require(row.length==columns.length,"VCF row width differs from original sample header");require(++variants<=r.variants(),"Variant capacity exceeded; do not omit records");require(row[0].matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,255}"),"Original LOFTEE faidx shell query requires a portable exact chromosome ID; no chromosome renaming is inferred");long pos;try{pos=Long.parseLong(row[1]);}catch(NumberFormatException e){throw new IOException("Invalid original VCF position",e);}require(pos>=1&&pos<=Integer.MAX_VALUE,"Require supported positive32-bit VCF position");require(row[3].matches("[ACGTN]+"),"Require literal uppercase sequence REF; symbolic/ambiguous allele profiles are separate");var alleles=new HashSet<String>();for(String alt:row[4].split(",",-1)){require(alt.matches("[ACGTN]+")&&!alt.equals(row[3])&&alleles.add(alt),"Invalid, duplicate, unchanged or symbolic ALT; no repair or allele omission");alts++;}
                var info=new HashSet<String>();if(!row[7].equals("."))for(String field:row[7].split(";",-1)){String key=field.split("=",2)[0];require(!key.isEmpty()&&!key.equals("CSQ")&&info.add(key),"Empty/duplicate/stale CSQ INFO field");}
                require(!row[2].isEmpty()&&!row[5].isEmpty()&&!row[6].isEmpty(),"Missing original ID/QUAL/FILTER token; literal dot is permitted");
                try{byte[] expected=reference.getSubsequenceAt(row[0],pos,Math.addExact(pos,row[3].length()-1L)).getBases();require(new String(expected,StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT).equals(row[3]),"REF does not match exact supplied indexed FASTA at "+row[0]+":"+row[1]+"; no strand/identifier/allele correction is inferred");if(ancestor!=null&&row[3].length()==1&&alleles.stream().allMatch(a->a.length()==1))ancestor.getSubsequenceAt(row[0],pos,pos);}catch(RuntimeException e){throw new IOException("Missing/out-of-range reference or ancestral coverage at "+row[0]+":"+row[1],e);}
            }
        }
        require(header&&variants>0,"Require a nonempty complete VCF family");return new LinkedHashMap<>(Map.of("variants",variants,"ALT_alleles",alts,"samples",samples,"exact_supplied_REF_matches",variants,"source_identity_policy","preserve-original-rows-alleles-INFO-samples-and-genotypes"));
    }
    static final class Lines implements AutoCloseable {
        private final Reader reader;private final char[] buffer=new char[8192];private int offset,length;private final int lineLimit;private final long maximum;private final long[] used;
        Lines(Path file,boolean compressed,int lineLimit,long maximum,long[] used)throws IOException {
            this.lineLimit=lineLimit;this.maximum=maximum;this.used=used;var stream=new BufferedInputStream(Files.newInputStream(file));try{stream.mark(2);boolean gzip=stream.read()==31&&stream.read()==139;stream.reset();require(gzip==compressed,"Text compression differs from explicit request: "+file);InputStream raw=compressed?new GZIPInputStream(stream):stream;reader=new InputStreamReader(raw,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));}catch(IOException|RuntimeException e){stream.close();throw e;}
        }
        String next()throws IOException {var result=new StringBuilder();boolean seen=false;while(true){if(offset>=length){length=reader.read(buffer);offset=0;if(length<0)return seen?line(result):null;}char c=buffer[offset++];require(used[0]<maximum,"Complete decoded text capacity exceeded");used[0]++;seen=true;if(c=='\n')return line(result);require(result.length()<lineLimit,"Complete text line capacity exceeded");result.append(c);}}
        private static String line(StringBuilder value){if(!value.isEmpty()&&value.charAt(value.length()-1)=='\r')value.setLength(value.length()-1);return value.toString();}
        @Override public void close()throws IOException{reader.close();}
    }
}

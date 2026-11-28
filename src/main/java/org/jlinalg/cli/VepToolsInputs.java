/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.BlockCompressedInputStream;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.genetics.VepVcf;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Captured literal input families and exact-resource references, with complete independent REF checks. */
final class VepToolsInputs {
    private VepToolsInputs() { }
    record Binding(Path original,Path captured,String sha256){void verify()throws IOException{if(!FollowupSupport.hash(original).equals(sha256)||!FollowupSupport.hash(captured).equals(sha256))throw new IOException("VEP original/captured input bytes changed: "+original);}}
    record Reference(Path cache,Path fasta,Path fai,Path annotation,String kind,String species,String assembly,VepReferenceBundle external) {
        void verify()throws IOException{if(external!=null)external.verify();}
    }
    record Prepared(Path input,Path annotated,Map<String,Object> captured,List<Binding> bindings,Reference reference,List<Map<String,Object>> custom,Map<String,Object> inputSummary) {
        void verify()throws IOException{for(var binding:bindings)binding.verify();if(reference!=null)reference.verify();}
    }
    static Prepared prepare(Path root,Path parent,Map<String,Object> raw,Map<String,Object> r)throws IOException {
        List<Binding> bindings=new ArrayList<>();var captured=new LinkedHashMap<String,Object>(raw);captured.put("out",".");long[] inputRemaining={VepToolsRequest.number(r,"maximum_input_bytes")};Path input=null,annotated=null;Reference reference=null;List<Map<String,Object>> custom=new ArrayList<>();
        if(text(r,"backend").equals("NATIVE")){
            annotated=capture(root,parent,raw,"annotated_vcf","annotated",inputRemaining,bindings,captured);
            if(raw.containsKey("original_vcf"))input=capture(root,parent,raw,"original_vcf","original",inputRemaining,bindings,captured);
        }else {
            var runtime=new LinkedHashMap<String,Object>();var originalRuntime=object(raw.get("runtime"),"runtime");for(String key:List.of("environment","bioperl","helpers"))runtime.put(key,resolve(parent,text(originalRuntime,key)).toString());captured.put("runtime",runtime);
            input=capture(root,parent,raw,"input_vcf","original",inputRemaining,bindings,captured);long[] resourceRemaining={VepToolsRequest.number(r,"maximum_resource_bytes")};var data=object(raw.get("reference"),"reference");var effectiveReference=new LinkedHashMap<String,Object>(data);String kind=text(data,"kind");
            if(kind.startsWith("human-cache")){
                var external=VepReferenceBundle.bind(resolve(parent,text(data,"bundle")),resourceRemaining[0]);resourceRemaining[0]-=external.bytes();external.capture(root.resolve("reference-bindings.json"));reference=new Reference(external.cache(),external.fasta(),external.fai(),null,kind,"homo_sapiens","GRCh38",external);effectiveReference.put("bundle",external.root().toString());
            }else {
                Path directory=Files.createDirectory(root.resolve("inputs/reference"));Path fasta=copy(resolve(parent,text(data,"fasta")),directory.resolve("genome.fa"),resourceRemaining,bindings);Path fai=copy(resolve(parent,text(data,"fai")),directory.resolve("genome.fa.fai"),resourceRemaining,bindings);Path originalAnnotation=resolve(parent,text(data,"annotation"));String annotationName=annotationName(originalAnnotation.getFileName().toString());Path transcripts=Files.createDirectory(directory.resolve("transcripts"));Path annotation=copy(originalAnnotation,transcripts.resolve(annotationName),resourceRemaining,bindings);bgzip(annotation);FastaIndexes.validate(fasta,fai);
                reference=new Reference(null,fasta,fai,annotation,kind,text(data,"species"),text(data,"assembly"),null);for(String key:List.of("fasta","fai","annotation"))effectiveReference.put(key,"inputs/reference/"+switch(key){case "fasta"->"genome.fa";case "fai"->"genome.fa.fai";default->"transcripts/"+annotationName;});
            }
            captured.put("reference",effectiveReference);int index=0;for(Object value:list(r.get("custom_annotations"),"custom annotations")){
                var item=object(value,"custom annotation");var c=new LinkedHashMap<String,Object>(item);Path directory=Files.createDirectory(root.resolve("inputs/custom-"+(index++)));String format=text(item,"format");String leaf=format.equals("bigwig")?"annotation.bw":"annotation."+format+".gz";Path file=copy(resolve(parent,text(item,"file")),directory.resolve(leaf),resourceRemaining,bindings);c.put("file",root.relativize(file).toString().replace('\\','/'));
                if(!format.equals("bigwig"))bgzip(file);custom.add(c);
            }
            captured.put("custom_annotations",custom);
        }
        Map<String,Object> summary=new LinkedHashMap<>();if(input!=null&&reference!=null){try(var sequence=ReferenceSequenceFileFactory.getReferenceSequenceFile(reference.fasta(),true,true);var ledger=Files.newBufferedWriter(root.resolve("reference-checks.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            FollowupSupport.row(ledger,new String[]{"input_row","CHROM","POS","original_REF","reference_bases","status"});long[] uncertain={0};
            var population=VepVcf.inspectOriginal(input,limits(r),v->{var row=v.fields();String chrom=row.get(0),originalRef=row.get(3);if(!chrom.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,255}"))throw new IOException("Original shell-backed lookup requires portable exact chromosome IDs; no alias or name repair");long pos=Long.parseLong(row.get(1));String bases;String state;
                validateAlternates(row.get(4));if(originalRef.matches("[ACGTNacgtn]+")){
                    long end;try{end=Math.addExact(pos,originalRef.length()-1L);}catch(ArithmeticException e){throw new IOException("REF coordinate overflow",e);}try{bases=new String(sequence.getSubsequenceAt(chrom,pos,end).getBases(),StandardCharsets.US_ASCII);}catch(RuntimeException e){throw new IOException("Exact reference lookup failed; no contig aliasing/assembly conversion: "+chrom+":"+pos,e);}
                    if(originalRef.toUpperCase(Locale.ROOT).indexOf('N')>=0||bases.toUpperCase(Locale.ROOT).indexOf('N')>=0){state="UNASSESSABLE_N_BASES_RETAINED";uncertain[0]++;}
                    else{if(!originalRef.equalsIgnoreCase(bases))throw new IOException("Original REF does not match exact supplied FASTA at row "+v.row()+"; no correction or allele swap");state="MATCHED_LITERAL_SEQUENCE";}
                }else throw new IOException("REF must be literal nucleotide sequence; no symbolic REF repair");
                FollowupSupport.row(ledger,new String[]{Long.toString(v.row()),chrom,row.get(1),originalRef,bases,state});
            });summary.put("variants",population.variants());summary.put("alternate_alleles",population.alternateAlleles());summary.put("reference_unassessable_rows",uncertain[0]);summary.put("contig_aliasing_or_allele_repair",false);summary.put("assembly_labels_independently_certified",false);
        }}
        json(root.resolve("input-summary.json"),summary);return new Prepared(input,annotated,captured,List.copyOf(bindings),reference,List.copyOf(custom),summary);
    }
    static VepVcf.Limits limits(Map<String,Object> r){return new VepVcf.Limits(VepToolsRequest.number(r,"maximum_decoded_characters"),VepToolsRequest.number(r,"maximum_variants"),VepToolsRequest.number(r,"maximum_annotations"),(int)VepToolsRequest.number(r,"maximum_line_characters"));}
    static String annotationName(String name)throws IOException{if(!name.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,255}"))throw new IOException("Require a portable literal GTF/GFF basename; it is retained because original VEP uses it in SOURCE and CSQ field names");return name;}
    private static void validateAlternates(String raw)throws IOException {
        for(String alt:raw.split(",",-1))if(!alt.matches("[ACGTNacgtn]+|[.*]|<[A-Za-z0-9_][A-Za-z0-9_.:-]*>|[ACGTNacgtn]+\\.|\\.[ACGTNacgtn]+|[ACGTNacgtn]*[\\[\\]][A-Za-z0-9_][A-Za-z0-9_.-]*:[1-9][0-9]*[\\[\\]][ACGTNacgtn]*"))throw new IOException("Unsupported literal ALT representation; retain input and use a separately qualified representation rather than rewriting alleles");
    }
    private static Path capture(Path root,Path parent,Map<String,Object> raw,String key,String leaf,long[] remaining,List<Binding> bindings,Map<String,Object> request)throws IOException {
        Path source=resolve(parent,text(raw,key));if(!Files.exists(source))throw new VepToolsRuntime.Unavailable("Required permitted original VCF unavailable: "+source);boolean compressed;try(var stream=Files.newInputStream(source)){compressed=stream.read()==31&&stream.read()==139;}Path destination=root.resolve("inputs/"+leaf+".vcf"+(compressed?".gz":""));copy(source,destination,remaining,bindings);request.put(key,root.relativize(destination).toString().replace('\\','/'));return destination;
    }
    private static Path copy(Path source,Path destination,long[] remaining,List<Binding> bindings)throws IOException {
        if(!Files.exists(source)||!Files.isReadable(source))throw new VepToolsRuntime.Unavailable("Required permitted VEP input/resource unavailable: "+source);if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>remaining[0])throw new IOException("VEP input/resource is nonregular or exceeds complete byte capacity");String expected=FollowupSupport.hash(source);long size=0;
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){if(n>remaining[0]-size)throw new IOException("Original resource grew beyond capacity");out.write(buffer,0,n);size+=n;}}
        remaining[0]-=size;var binding=new Binding(source,destination,expected);binding.verify();bindings.add(binding);return destination;
    }
    private static void bgzip(Path path)throws IOException{try(var in=new BufferedInputStream(Files.newInputStream(path))){if(!BlockCompressedInputStream.isValidFile(in))throw new IOException("Indexed annotations require original BGZF; ordinary gzip is not relabeled");}}
}

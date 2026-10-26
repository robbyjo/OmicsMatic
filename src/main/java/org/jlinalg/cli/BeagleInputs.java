/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.FollowupSupport.hash;

/** Bounded GT-only diploid VCF inputs, exact marker identity and explicit map. */
final class BeagleInputs {
    private BeagleInputs() { }
    record Limits(int samples,int variants,long cells,long bytes,long retained,int line) {
        Limits {if(samples<1||variants<1||cells<1||bytes<1||retained<1||line<64||16L*line>retained)throw new IllegalArgumentException("Positive Beagle bounds and retained storage >=16*line bytes are required");}
    }
    record Marker(int position,String id,String ref,String alt,long callOffset,int phaseFlags) { }
    record Dataset(String chromosome,List<String> samples,Map<Integer,Marker> markers,int[] observed,boolean missing,boolean phased,boolean unphased) { }
    record Prepared(Dataset target,Dataset reference,Map<Integer,Marker> expected,Map<Integer,String> stagedIds,
            Map<String,Path> sources,Map<String,String> hashes,Map<String,String> stagedHashes,int excludedTargetMarkers,Path callStore,Limits limits) { }
    private static final class Budget {
        long cells,bytes,retained;
        final Limits limits;
        Budget(Limits limits){this.limits=limits;retained=16L*limits.line();}
        void retain(long value)throws IOException{retained=Math.addExact(retained,value);if(retained>limits.retained())throw new IOException("Beagle retained-input byte bound exceeded");}
    }
    static InputStream open(Path path,long maximum)throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>maximum)throw new IOException("Missing/oversized Beagle input: "+path);
        var input=new BufferedInputStream(Files.newInputStream(path));input.mark(2);int first=input.read(),second=input.read();input.reset();
        InputStream decoded;
        try{decoded=first==31&&second==139?new GZIPInputStream(input):input;}catch(IOException failure){input.close();throw failure;}
        return new FilterInputStream(decoded){private long bytes;private void count(int n)throws IOException{if(n>0){bytes+=n;if(bytes>maximum)throw new IOException("Beagle decompressed input/report byte bound exceeded");}}@Override public int read()throws IOException{int n=super.read();count(n<0?0:1);return n;}@Override public int read(byte[] b,int offset,int length)throws IOException{int n=in.read(b,offset,length);count(n);return n;}};
    }
    static Prepared prepare(Path targetPath,Path referencePath,Path mapPath,Path directory,String family,String method,
            String phasePolicy,String targetOnly,String overlap,int minimumObserved,Limits limits)throws Exception {
        var sources=new LinkedHashMap<String,Path>();sources.put("target",targetPath);if(referencePath!=null)sources.put("reference",referencePath);sources.put("genetic_map",mapPath);
        var hashes=new LinkedHashMap<String,String>();for(var e:sources.entrySet()){if(!Files.isRegularFile(e.getValue())||Files.size(e.getValue())>limits.bytes())throw new IOException("Missing or oversized Beagle source: "+e.getValue());hashes.put(e.getKey(),hash(e.getValue()));}
        var budget=new Budget(limits);Path callStore=directory.resolve("target.calls.bin");Dataset target=read(targetPath,false,budget,directory.resolve("target.vcf"),callStore),reference=referencePath==null?null:read(referencePath,true,budget,directory.resolve("reference.vcf"),null);
        if(reference!=null&&!reference.chromosome().equals(target.chromosome()))throw new IOException("Target/reference chromosomes differ;allele/build harmonization is separate");
        if(reference!=null&&overlap.equals("error")&&target.samples().stream().anyMatch(new java.util.HashSet<>(reference.samples())::contains))throw new IOException("Target/reference sample names overlap under explicit error policy");
        var union=new TreeMap<Integer,Marker>(target.markers());
        if(reference!=null)for(var entry:reference.markers().entrySet()) {
            Marker existing=target.markers().get(entry.getKey());
            if(existing!=null&&(!existing.ref().equals(entry.getValue().ref())||!existing.alt().equals(entry.getValue().alt())))throw new IOException("Target/reference REF/ALT mismatch at "+entry.getKey()+";no allele flip,complement or coordinate correction is inferred");
            union.putIfAbsent(entry.getKey(),entry.getValue());
        }
        var expected=new LinkedHashMap<Integer,Marker>();int excluded=0;
        for(var entry:union.entrySet()) {
            boolean inTarget=target.markers().containsKey(entry.getKey()),inReference=reference!=null&&reference.markers().containsKey(entry.getKey());
            if(reference!=null&&inTarget&&!inReference){excluded++;continue;}
            if(method.equals("impute")&&inReference||inTarget)expected.put(entry.getKey(),entry.getValue());
        }
        if(excluded>0&&targetOnly.equals("error"))throw new IOException(excluded+" target markers are absent from the reference and would be excluded upstream;choose an explicit reviewed panel or drop-with-warning policy");
        if(expected.isEmpty()||expected.size()>limits.variants())throw new IOException("Beagle expected output marker family is empty or exceeds bounds");
        int[] observed=target.observed().clone();int phaseFlags=0;
        try(var stored=java.nio.channels.FileChannel.open(callStore,java.nio.file.StandardOpenOption.READ)) {
            for(var m:target.markers().values())if(expected.containsKey(m.position()))phaseFlags|=m.phaseFlags();else{byte[] row=calls(stored,m,target.samples().size());for(int i=0;i<row.length;i++)if(called(row[i]))observed[i]--;}
        }
        for(int i=0;i<observed.length;i++)if(observed[i]<minimumObserved)throw new IOException("Target sample "+target.samples().get(i)+" has fewer than the declared minimum observed markers after explicit exclusions");
        if(phasePolicy.equals("preserve-provided")&&!family.equals("4.1")&&(phaseFlags&2)!=0&&(phaseFlags&5)!=0)throw new IOException("This modern Beagle profile may rephase provided phase when other retained genotypes are missing/unphased;use complete prephased input or explicitly select allow-rephase");
        budget.retain(512L*union.size());var stagedIds=new LinkedHashMap<Integer,String>();for(int position:union.keySet())stagedIds.put(position,"V"+position);
        map(mapPath,directory.resolve("map.txt"),target.chromosome(),union.firstKey(),union.lastKey(),budget);
        try(var writer=Files.newBufferedWriter(directory.resolve("variant-ledger.tsv"))) {
            writer.write("chromosome\tposition\ttarget_id\treference_id\tupstream_id\tref\talt\ttarget_present\treference_present\toutput_expected\tdecision\n");
            for(var entry:union.entrySet()){int pos=entry.getKey();Marker t=target.markers().get(pos),r=reference==null?null:reference.markers().get(pos),m=entry.getValue();writer.write(target.chromosome()+"\t"+pos+"\t"+(t==null?"":t.id())+"\t"+(r==null?"":r.id())+"\t"+stagedIds.get(pos)+"\t"+m.ref()+"\t"+m.alt()+"\t"+(t!=null)+"\t"+(r!=null)+"\t"+expected.containsKey(pos)+"\t"+(expected.containsKey(pos)?t==null?"REFERENCE_ONLY_IMPUTATION":"TARGET_RETAINED":t!=null?"EXPLICIT_TARGET_ONLY_EXCLUSION":"REFERENCE_ONLY_NOT_REQUESTED")+"\n");}
        }
        try(var writer=Files.newBufferedWriter(directory.resolve("sample-ledger.tsv"))){writer.write("source\tsample\tupstream_sample\tobserved_retained_target_markers\n");for(int i=0;i<target.samples().size();i++)writer.write("target\t"+target.samples().get(i)+"\tT"+(i+1)+"\t"+observed[i]+"\n");if(reference!=null)for(int i=0;i<reference.samples().size();i++)writer.write("reference\t"+reference.samples().get(i)+"\tR"+(i+1)+"\t\n");}
        var staged=new LinkedHashMap<String,String>();for(String name:List.of("target.vcf","target.calls.bin","map.txt","variant-ledger.tsv","sample-ledger.tsv"))staged.put(name,hash(directory.resolve(name)));if(reference!=null)staged.put("reference.vcf",hash(directory.resolve("reference.vcf")));
        var result=new Prepared(target,reference,java.util.Collections.unmodifiableMap(expected),Map.copyOf(stagedIds),Map.copyOf(sources),Map.copyOf(hashes),Map.copyOf(staged),excluded,callStore,limits);verify(result,directory);return result;
    }
    private static Dataset read(Path path,boolean reference,Budget budget,Path staged,Path callStore)throws IOException {
        List<String> samples=null;int[] observed=null;long callOffset=0;String chromosome=null;int last=0;boolean missing=false,phased=false,unphased=false;var markers=new LinkedHashMap<Integer,Marker>();
        try(var input=open(path,budget.limits.bytes());var writer=Files.newBufferedWriter(staged);var callsOutput=callStore==null?java.io.OutputStream.nullOutputStream():Files.newOutputStream(callStore)) {
            String first=PlinkGlmInputs.line(input,budget.limits.line());if(first==null||!first.matches("##fileformat=VCFv4\\.[123]"))throw new IOException("Require explicit VCFv4.1/4.2/4.3 header");
            budget.bytes+=first.length()+1L;
            for(String line;(line=PlinkGlmInputs.line(input,budget.limits.line()))!=null;) {
                budget.bytes+=line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+1L;if(budget.bytes>budget.limits.bytes())throw new IOException("Combined decompressed Beagle input-byte bound exceeded");
                if(line.startsWith("##")){if(samples!=null)throw new IOException("VCF metadata after sample header");continue;}
                String[] f=line.split("\t",-1);
                if(line.startsWith("#CHROM\t")) {
                    if(samples!=null||f.length<10||!java.util.Arrays.equals(java.util.Arrays.copyOf(f,9),new String[]{"#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO","FORMAT"}))throw new IOException("Invalid/duplicate Beagle VCF sample header");
                    var names=new ArrayList<String>();for(int i=9;i<f.length;i++){token(f[i],"sample");names.add(f[i]);}
                    if(names.size()>budget.limits.samples()||new java.util.HashSet<>(names).size()!=names.size())throw new IOException("Duplicate or oversized Beagle sample family");budget.retain(256L*names.size()+4L*line.length());samples=List.copyOf(names);observed=new int[names.size()];
                    writer.write("##fileformat=VCFv4.2\n##contig=<ID=1>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");for(int i=0;i<names.size();i++)writer.write("\t"+(reference?"R":"T")+(i+1));writer.write("\n");continue;
                }
                if(samples==null||f.length!=samples.size()+9)throw new IOException("VCF row width/sample header differs");
                token(f[0],"chromosome");if(f[0].codePoints().anyMatch(c->c==44||c==34||c==60||c==62||c==91||c==93||c==59||c==61))throw new IOException("Chromosome identifier contains reserved header delimiters");token(f[2],"variant ID");token(f[3],"REF");token(f[4],"ALT");
                if(chromosome==null)chromosome=f[0];if(!chromosome.equals(f[0]))throw new IOException("Provide exactly one chromosome per Beagle analysis");
                int position;try{position=Integer.parseInt(f[1]);}catch(NumberFormatException failure){throw new IOException("Invalid Beagle position",failure);}
                if(position<=last||f[4].equals("<NON_REF>")||f[4].equals("<*>")||f[3].equals(f[4])||f[4].equals(".")||f[4].equals("*")||f[4].contains(",")||f[3].equals("."))throw new IOException("Require positive strictly increasing positions and resolved biallelic REF/ALT");last=position;
                if(!f[6].equals("PASS")&&!f[6].equals("."))throw new IOException("Non-PASS VCF filters require explicit prior QC;no filter is inferred or silently cleared");
                String[] format=f[8].split(":",-1);int gt=-1;var unique=new java.util.HashSet<String>();for(int i=0;i<format.length;i++){if(!unique.add(format[i]))throw new IOException("Duplicate VCF FORMAT key");if(format[i].equals("GT"))gt=i;}
                if(gt<0)throw new IOException("GT-only Beagle workflow requires GT at every marker;GL/PL inference is separate");
                budget.cells=Math.addExact(budget.cells,samples.size());if(budget.cells>budget.limits.cells()||markers.size()>=budget.limits.variants())throw new IOException("Beagle genotype-cell/marker bound exceeded");
                budget.retain(1024L+4L*(f[0].length()+f[2].length()+f[3].length()+f[4].length()));byte[] calls=new byte[samples.size()];int rowPhaseFlags=0;
                for(int i=0;i<calls.length;i++) {
                    String[] values=f[i+9].split(":",-1);if(values.length!=format.length)throw new IOException("VCF sample FORMAT width differs");byte value=genotype(values[gt]);calls[i]=value;rowPhaseFlags|=!called(value)?1:(value&4)!=0?2:4;
                    if(!called(value)){missing=true;if(reference)throw new IOException("Reference panel genotypes must be nonmissing and phased");}
                    else {observed[i]++;if((value&4)!=0)phased=true;else{unphased=true;if(reference)throw new IOException("Reference panel genotypes must be phased");}}
                }
                writer.write("1\t"+position+"\tV"+position+"\t"+f[3]+"\t"+f[4]+"\t.\tPASS\t.\tGT");for(byte value:calls)writer.write("\t"+text(value));writer.write("\n");
                if(!reference)callsOutput.write(calls);
                markers.put(position,new Marker(position,f[2],f[3],f[4],reference?-1:callOffset,rowPhaseFlags));callOffset+=calls.length;
            }
        }
        if(samples==null||markers.isEmpty())throw new IOException("Empty Beagle VCF genotype dataset");
        return new Dataset(chromosome,samples,java.util.Collections.unmodifiableMap(markers),observed,missing,phased,unphased);
    }
    private static void map(Path path,Path out,String chromosome,int minimum,int maximum,Budget budget)throws IOException {
        int last=0,count=0;double firstCm=Double.NaN,lastCm=-1;int firstPosition=0;
        try(var input=open(path,budget.limits.bytes());var writer=Files.newBufferedWriter(out)) {
            for(String line;(line=PlinkGlmInputs.line(input,budget.limits.line()))!=null;) {
                budget.bytes+=line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+1L;if(budget.bytes>budget.limits.bytes())throw new IOException("Combined Beagle VCF/map byte bound exceeded");if(line.isBlank())throw new IOException("Blank genetic-map row");String[] row=line.strip().split("\\s+");if(row.length!=4||!row[0].equals(chromosome))throw new IOException("Genetic map must name the exact single input chromosome with four PLINK fields");
                double cm;int bp;try{cm=Double.parseDouble(row[2]);bp=Integer.parseInt(row[3]);}catch(NumberFormatException failure){throw new IOException("Invalid cM/bp map value",failure);}
                if(!Double.isFinite(cm)||cm<0||cm<lastCm||bp<=last)throw new IOException("Genetic map requires nonnegative nondecreasing cM and positive strictly increasing bp");
                if(++count>budget.limits.variants())throw new IOException("Genetic-map row bound exceeded");if(count==1){firstCm=cm;firstPosition=bp;}lastCm=cm;last=bp;writer.write("1 M"+count+" "+Double.toString(cm)+" "+bp+"\n");
            }
        }
        if(count<2||!(lastCm>firstCm)||firstPosition>minimum||last<maximum)throw new IOException("Explicit cM map must bracket all target/reference positions with a positive total genetic span;no constant map or extrapolation is inferred");
    }
    static byte[] calls(java.nio.channels.FileChannel channel,Marker marker,int samples)throws IOException {
        var buffer=java.nio.ByteBuffer.allocate(samples);long offset=marker.callOffset();if(offset<0)throw new IOException("No target call row for reference-only marker");
        while(buffer.hasRemaining()){int count=channel.read(buffer,offset+buffer.position());if(count<0)throw new IOException("Truncated staged target call store");}return buffer.array();
    }
    static byte genotype(String value)throws IOException {
        if(value.length()!=3||value.charAt(1)!='/'&&value.charAt(1)!='|')throw new IOException("Only diploid GT allele indices0/1 or fully missing are admitted;haploid/polyploid/partial missing calls require a separate explicit workflow");
        int phase=value.charAt(1)=='|'?4:0;if(value.charAt(0)=='.'&&value.charAt(2)=='.')return (byte)(phase|8);
        if((value.charAt(0)!='0'&&value.charAt(0)!='1')||(value.charAt(2)!='0'&&value.charAt(2)!='1'))throw new IOException("Only biallelic diploid GT or fully missing calls are admitted");return (byte)(phase|2*(value.charAt(0)-'0')+value.charAt(2)-'0');
    }
    static boolean called(byte value){return (value&8)==0;}
    static int dosage(byte value){return ((value>>1)&1)+(value&1);}
    static String text(byte value){return !called(value)?((value&4)!=0?".|.":"./."):Integer.toString((value>>1)&1)+((value&4)!=0?'|':'/')+(value&1);}
    static void verify(Prepared p,Path directory)throws IOException {
        for(var e:p.sources().entrySet())if(!hash(e.getValue()).equals(p.hashes().get(e.getKey())))throw new IOException("Beagle scientific source changed during analysis: "+e.getKey());
        for(var e:p.stagedHashes().entrySet())if(!hash(directory.resolve(e.getKey())).equals(e.getValue()))throw new IOException("Beagle staged input/ledger changed during analysis: "+e.getKey());
    }
    private static void token(String value,String label)throws IOException{if(value.isEmpty()||value.length()>8192||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IOException("Invalid/oversized VCF "+label+" token");}
}

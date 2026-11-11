/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Streaming integrity audit for full-transcript VEP/LOFTEE VCF output.
 * Does not predict consequences, assign confidence, filter flags, or establish biological correctness.
 * Sink output is provisional until this method returns and the owning workflow admits the whole batch.
 */
public final class LofteeVcfAudit {
    private LofteeVcfAudit() { }
    private static final Set<String> TARGETS=Set.of("stop_gained","frameshift_variant","splice_donor_variant","splice_acceptor_variant");
    private static final Pattern FORMAT=Pattern.compile("(?:^| )Format: ([^\"<>]+)");
    private static final Set<String> REQUIRED=Set.of("Allele","Consequence","Feature_type","Feature","Gene","BIOTYPE","ALLELE_NUM","LoF","LoF_filter","LoF_flags","LoF_info");

    public record Limits(long maximumCharacters,long maximumVariants,long maximumAnnotations,int maximumLineCharacters) {
        public Limits {
            if(maximumCharacters<1||maximumVariants<1||maximumAnnotations<1||maximumLineCharacters<256)
                throw new IllegalArgumentException("Positive complete-family capacities and a line capacity >=256 are required");
        }
    }
    public record Annotation(long row,int annotation,int alleleNumber,String chromosome,String position,String id,
                             String reference,String alternate,String gene,String feature,String consequence,
                             String confidence,String filters,String flags,String info,String rawCsq) { }
    public record Variant(long row,String chromosome,String position,String id,String reference,String alternates,
                          int annotations,int targetTranscripts,int highConfidence,int lowConfidence,int otherSplice) { }
    public record Summary(long variants,long annotations,long targetTranscripts,long highConfidence,long lowConfidence,
                          long otherSplice,long flaggedAnnotations,long unclassifiedAnnotations,List<String> csqFields) {
        public Summary { csqFields=List.copyOf(csqFields); }
    }
    @FunctionalInterface public interface Sink<T> { void accept(T value)throws IOException; }

    /** All original variants/alleles and genotype columns must survive in order; no canonical/MANE selection is inferred. */
    public static Summary audit(Path input,Path output,List<Path> originalLogs,boolean extendedSplicePredictions,
                                Limits limits,Sink<Variant> variants,Sink<Annotation> annotations)throws IOException {
        if(input==null||output==null||originalLogs==null||limits==null||variants==null||annotations==null)
            throw new IllegalArgumentException("Input, output, explicit logs/capacities and complete-ledger sinks are required");
        Budget budget=new Budget(limits);
        for(Path log:originalLogs) {
            try(Lines lines=new Lines(log,budget)) {
                for(String line;(line=lines.next())!=null;) {
                    String lower=line.toLowerCase(java.util.Locale.ROOT);
                    if((lower.contains("plugin")&&(lower.contains("lof")||lower.contains("loss-of-function")))
                        &&(lower.contains("went wrong")||lower.contains("failed")||lower.contains("can't locate")||lower.contains("could not")))
                        throw failure("Original LOFTEE plugin failure in "+log+": "+line);
                }
            }
        }
        long count=0,total=0,targetTotal=0,hc=0,lc=0,os=0,flagged=0,empty=0;
        try(Vcf before=new Vcf(input,budget,false);Vcf after=new Vcf(output,budget,true)) {
            require(Arrays.equals(before.columns,after.columns),"Original sample/FORMAT column header changed");
            Map<String,Integer> fields=after.fields;
            for(String[] a;(a=before.next())!=null;) {
                require(++count<=limits.maximumVariants(),"Variant capacity exceeded; do not omit records to conceal the failure");
                String[] b=after.next();require(b!=null,"Original output dropped input row "+count);
                require(a.length==b.length,"Original output changed column count at row "+count);
                for(int c=0;c<a.length;c++)if(c!=7)require(a[c].equals(b[c]),"Original identity/quality/filter/genotype changed at row "+count+", column "+(c+1));
                Map<String,String> original=info(a[7]),returned=info(b[7]);require(!original.containsKey("CSQ"),"Input already contains CSQ; preserve it separately and supply an explicitly unannotated input");
                String encoded=returned.remove("CSQ");require(original.equals(returned),"Original non-CSQ INFO fields changed at row "+count);
                require(encoded!=null&&!encoded.isEmpty()&&!encoded.equals("CSQ"),"Missing complete CSQ annotations at row "+count);
                require(encoded.startsWith("CSQ="),"Malformed CSQ at row "+count);String[] alleles=a[4].split(",",-1);boolean[] covered=new boolean[alleles.length];int rowAnnotations=0,rowTargets=0,rowHc=0,rowLc=0,rowOs=0;
                for(String csq:encoded.substring(4).split(",",-1)) {
                    require(++total<=limits.maximumAnnotations(),"Annotation capacity exceeded; no transcript reduction is allowed");
                    String[] values=csq.split("\\|",-1);require(values.length==fields.size(),"CSQ field count differs from declared header at row "+count);
                    int allele=positiveInt(value(values,fields,"ALLELE_NUM"),"CSQ ALLELE_NUM");require(allele<=alleles.length,"CSQ refers to an absent ALT at row "+count);covered[allele-1]=true;
                    String consequence=value(values,fields,"Consequence"),biotype=value(values,fields,"BIOTYPE"),featureType=value(values,fields,"Feature_type"),call=value(values,fields,"LoF"),filters=value(values,fields,"LoF_filter"),flags=value(values,fields,"LoF_flags");
                    require(!consequence.isEmpty(),"Empty consequence at row "+count);boolean isTarget=Arrays.stream(consequence.split("&",-1)).anyMatch(TARGETS::contains)&&biotype.equals("protein_coding")&&featureType.equals("Transcript");
                    require(Set.of("","HC","LC","OS").contains(call),"Unknown original LOFTEE confidence at row "+count+": "+call);
                    require(!isTarget||!call.isEmpty(),"Missing LOFTEE call for protein-coding target at row "+count+", transcript "+value(values,fields,"Feature")+"; inspect original warnings, do not impute HC/LC");
                    require(call.isEmpty()||biotype.equals("protein_coding")&&featureType.equals("Transcript"),"LOFTEE call on an ineligible feature at row "+count);
                    require(!call.equals("HC")||filters.isEmpty(),"HC annotation carries low-confidence filters at row "+count);
                    require(!call.equals("HC")||isTarget,"HC on a non-target transcript at row "+count);
                    require(!call.equals("OS")||extendedSplicePredictions,"OS call without declared extended splice predictions at row "+count);
                    require(!call.equals("OS")||!isTarget,"OS on a VEP target that should have HC/LC confidence at row "+count);
                    require(!call.equals("OS")||filters.isEmpty(),"OS annotation carries filters but was not classified LC at row "+count);
                    require(!call.equals("LC")||!filters.isEmpty(),"LC annotation has no original filter reason at row "+count);
                    require(extendedSplicePredictions||call.isEmpty()||isTarget,"Non-target confidence without declared extended predictions at row "+count);
                    rowAnnotations++;if(isTarget){rowTargets++;targetTotal++;}if(call.equals("HC")){hc++;rowHc++;}if(call.equals("LC")){lc++;rowLc++;}if(call.equals("OS")){os++;rowOs++;}if(call.isEmpty())empty++;if(!flags.isEmpty())flagged++;
                    annotations.accept(new Annotation(count,rowAnnotations,allele,a[0],a[1],a[2],a[3],alleles[allele-1],value(values,fields,"Gene"),value(values,fields,"Feature"),consequence,call,filters,flags,value(values,fields,"LoF_info"),csq));
                }
                for(int i=0;i<covered.length;i++)require(covered[i],"Original output omitted ALT "+(i+1)+" at row "+count);
                variants.accept(new Variant(count,a[0],a[1],a[2],a[3],a[4],rowAnnotations,rowTargets,rowHc,rowLc,rowOs));
            }
            require(after.next()==null,"Original output appended/reordered an unexpected variant");
            require(count>0,"No variant input was supplied; no empty success is inferred");
            return new Summary(count,total,targetTotal,hc,lc,os,flagged,empty,after.fieldNames);
        }
    }
    private static String value(String[] values,Map<String,Integer> fields,String name){return values[fields.get(name)];}
    private static IOException failure(String message){return new IOException("FAIL2: "+message+". No complete result is admitted; preserve all original inputs, logs and provisional ledgers and retry NEW paths after correcting the cause.");}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw failure(message);}
    private static int positiveInt(String text,String what)throws IOException {
        require(text.matches("[1-9][0-9]*"),"Invalid "+what);try{return Integer.parseInt(text);}catch(NumberFormatException e){throw failure(what+" exceeds supported positive integer range");}
    }
    private static Map<String,String> info(String raw)throws IOException {
        Map<String,String> result=new LinkedHashMap<>();if(raw.equals("."))return result;require(!raw.isEmpty(),"Empty INFO token");
        for(String item:raw.split(";",-1)){String key=item.split("=",2)[0];require(!key.isEmpty()&&result.putIfAbsent(key,item)==null,"Empty/duplicate INFO key: "+key);}return result;
    }
    private static final class Vcf implements AutoCloseable {
        private final Lines lines;private final String[] columns;private final Map<String,Integer> fields=new HashMap<>();private final List<String> fieldNames=new ArrayList<>();
        Vcf(Path file,Budget budget,boolean annotated)throws IOException {
            lines=new Lines(file,budget);String[] header=null;boolean format=false,csq=false;
            try {
                for(String s;(s=lines.next())!=null;) {
                    if(s.startsWith("##fileformat=VCFv4.")){require(!format,"Duplicate VCF version header");format=true;}
                    else if(s.startsWith("##INFO=<ID=CSQ,")) {
                        require(!csq,"Duplicate CSQ header");csq=true;var matcher=FORMAT.matcher(s);require(matcher.find(),"Missing CSQ Format declaration");
                        for(String name:matcher.group(1).strip().split("\\|",-1)){require(!name.isEmpty()&&name.chars().allMatch(c->c>=33&&c<=126&&"|\"<>".indexOf(c)<0)&&!fields.containsKey(name),"Invalid/duplicate CSQ field: "+name);fields.put(name,fieldNames.size());fieldNames.add(name);}
                    } else if(s.startsWith("##")) { /* retain raw headers in original VCF; no semantic rewrite */ }
                    else if(s.startsWith("#CHROM\t")){header=s.split("\t",-1);break;}
                    else throw failure("Missing/invalid VCF header before data in "+file);
                }
                require(format&&header!=null,"Incomplete VCF header in "+file);require(header.length==8||header.length>=10,"VCF requires8 site columns or FORMAT plus named samples");String[] expected={"#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO"};for(int i=0;i<8;i++)require(header[i].equals(expected[i]),"Unexpected VCF site header");
                if(header.length>8){require(header[8].equals("FORMAT"),"Missing VCF FORMAT column");Set<String> samples=new HashSet<>();for(int i=9;i<header.length;i++)require(!header[i].isEmpty()&&samples.add(header[i]),"Empty/duplicate sample identity");}
                require(annotated?csq:!csq,annotated?"Original output has no CSQ declaration":"Input contains a prior CSQ header; no silent replacement allowed");if(annotated)require(fields.keySet().containsAll(REQUIRED),"Required LOFTEE/identity/BIOTYPE/allele fields are missing");columns=header;
            }catch(IOException|RuntimeException e){lines.close();throw e;}
        }
        String[] next()throws IOException {
            String line=lines.next();if(line==null)return null;require(!line.isEmpty()&&!line.startsWith("#"),"Blank/header record within VCF data");String[] row=line.split("\t",-1);require(row.length==columns.length,"VCF record width differs from header");require(!row[0].isEmpty(),"Missing chromosome");positiveInt(row[1],"VCF position");require(!row[2].isEmpty(),"Missing raw variant ID token");require(row[3].matches("[ACGTN]+"),"This audit profile requires literal uppercase sequence REF; symbolic alleles require a separate validated profile");Set<String> alleles=new HashSet<>();for(String alt:row[4].split(",",-1))require(alt.matches("[ACGTN]+")&&!alt.equals(row[3])&&alleles.add(alt),"Missing, duplicate, symbolic or unchanged ALT; no repair inferred");return row;
        }
        @Override public void close()throws IOException{lines.close();}
    }
    private static final class Budget {final Limits limits;long used;Budget(Limits limits){this.limits=limits;}}
    /** Counts decoded UTF-16 code units across both VCFs and every log, including skipped headers. */
    private static final class Lines implements AutoCloseable {
        private final Reader reader;private final Budget budget;private final char[] buffer=new char[8192];private int offset,length;
        Lines(Path path,Budget budget)throws IOException {
            require(path!=null&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular source file: "+path);this.budget=budget;BufferedInputStream stream=new BufferedInputStream(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS));
            try {stream.mark(2);int a=stream.read(),b=stream.read();stream.reset();InputStream decoded=a==31&&b==139?new GZIPInputStream(stream):stream;reader=new InputStreamReader(decoded,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));}catch(IOException|RuntimeException e){stream.close();throw e;}
        }
        String next()throws IOException {
            StringBuilder line=new StringBuilder();boolean read=false;
            while(true){if(offset>=length){length=reader.read(buffer);offset=0;if(length<0)return read?stripCr(line):null;}
                char c=buffer[offset++];require(budget.used<budget.limits.maximumCharacters(),"Combined uncompressed text capacity exceeded");budget.used++;read=true;if(c=='\n')return stripCr(line);require(line.length()<budget.limits.maximumLineCharacters(),"Line capacity exceeded");line.append(c);}
        }
        private static String stripCr(StringBuilder line){if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);return line.toString();}
        @Override public void close()throws IOException{reader.close();}
    }
}

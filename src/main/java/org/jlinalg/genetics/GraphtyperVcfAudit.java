/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Streaming, complete-record VCF audit and literal GT allele accounting.
 * Every data field contributes to the logical SHA256; original VCF files remain
 * authoritative. Counts are derived from GT, separately from source INFO counters,
 * and do not infer phase, impute missing alleles, recalculate genotype likelihoods,
 * or reconstruct GraphTyper's unrounded quality-classifier features.
 * The enclosing workflow must bind immutable input bytes and the producing runtime.
 */
public final class GraphtyperVcfAudit {
    private GraphtyperVcfAudit() { }
    public record Limits(long decodedBytes,long records,long genotypeCells,int lineCharacters,int headerLines,
                         int samples,int alleles) {
        public Limits {if(decodedBytes<1||records<1||genotypeCells<1||lineCharacters<1||headerLines<1||samples<0||alleles<1)
            throw new IllegalArgumentException("Positive VCF capacities required (sample capacity may be zero)");}
    }
    public record SampleCount(String id,long completeGenotypes,long fullyMissingGenotypes,long partiallyMissingGenotypes,
                              long phasedGenotypes,long calledAlleles,long alternateAlleles) { }
    public record Result(long records,long genotypeCells,List<String> sampleIds,List<SampleCount> sampleCounts,
                         List<String> canonicalHeader,List<String> retainedClockHeader,List<String> warnings,
                         String allDataFieldsSha256,long decodedBytes) {
        public Result {sampleIds=List.copyOf(sampleIds);sampleCounts=List.copyOf(sampleCounts);canonicalHeader=List.copyOf(canonicalHeader);
            retainedClockHeader=List.copyOf(retainedClockHeader);warnings=List.copyOf(warnings);}
    }
    /** expectedSamples is the complete expected population; an empty set requires a sites-only VCF. */
    public static Result audit(Path input,Set<String> expectedSamples,Path newRecordTable,Limits limits,Runnable cancellation)throws IOException {
        expectedSamples=Set.copyOf(expectedSamples);Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);
        var header=new ArrayList<String>();var clocks=new ArrayList<String>();var warnings=new ArrayList<String>();
        var infos=new HashMap<String,String>();var formats=new HashMap<String,String>();var unknownInfo=new TreeSet<String>();var unknownFormat=new TreeSet<String>();
        List<String> samples=null;long[][] totals=null;long rows=0,cells=0;MessageDigest digest=sha256();
        try(var raw=Files.newInputStream(input);var probe=new PushbackInputStream(new BufferedInputStream(raw),2)){
            byte[] magic=probe.readNBytes(2);probe.unread(magic);InputStream decoded=magic.length==2&&(magic[0]&255)==31&&(magic[1]&255)==139?new GZIPInputStream(probe,65536):probe;
            var counted=new Counted(decoded,limits.decodedBytes());
            try(var reader=new BufferedReader(new InputStreamReader(counted,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),65536);
                var output=Files.newBufferedWriter(newRecordTable,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
                output.write("record\tchromosome\tposition\treference\talternates\tcomplete_GT\tfully_missing_GT\tpartial_GT\tcalled_alleles\tGT_allele_counts\tsource_INFO_AC\tsource_INFO_AN\tall_fields_sha256\n");
                for(String line;(line=boundedLine(reader,limits.lineCharacters()))!=null;){
                    cancellation.run();if(line.isEmpty())throw new IOException("Blank VCF line; original attempt retained");
                    if(line.charAt(0)=='#'){
                        require(samples==null,"VCF metadata appears after the sample header");require(header.size()+clocks.size()<limits.headerLines(),"Complete VCF header capacity exceeded");
                        if(line.startsWith("##fileDate=")){clocks.add(line);continue;}
                        header.add(line);
                        if(line.startsWith("##INFO=<"))definition(line,"##INFO=<",infos);
                        else if(line.startsWith("##FORMAT=<"))definition(line,"##FORMAT=<",formats);
                        else if(line.startsWith("#CHROM\t")){
                            String[] f=line.split("\t",-1);require(f.length>=8,"Eight literal VCF columns required");
                            require(Arrays.equals(Arrays.copyOf(f,8),new String[]{"#CHROM","POS","ID","REF","ALT","QUAL","FILTER","INFO"}),"VCF fixed header columns differ");
                            require(f.length==8||f.length>=10&&f[8].equals("FORMAT"),"VCF FORMAT/sample population differs");
                            samples=f.length==8?List.of():List.of(Arrays.copyOfRange(f,9,f.length));require(samples.size()<=limits.samples(),"Complete sample capacity exceeded");
                            require(samples.stream().allMatch(s->!s.isEmpty()&&s.chars().noneMatch(Character::isWhitespace))&&new HashSet<>(samples).size()==samples.size(),"Empty, whitespace or duplicate source sample identifier");
                            require(new HashSet<>(samples).equals(expectedSamples),"Complete original VCF sample population differs from actual input samples");totals=new long[samples.size()][6];
                        }
                        continue;
                    }
                    require(samples!=null,"VCF data before complete sample header");rows=Math.addExact(rows,1);require(rows<=limits.records(),"Complete VCF record capacity exceeded");
                    cells=Math.addExact(cells,samples.size());require(cells<=limits.genotypeCells(),"Complete genotype-cell capacity exceeded");
                    String[] f=line.split("\t",-1);require(f.length==(samples.isEmpty()?8:9+samples.size()),"Complete original VCF row field population differs at record "+rows);
                    require(!f[0].isEmpty()&&!f[3].isEmpty(),"Literal chromosome and reference allele required");long position=integer(f[1],"VCF position");require(position>=1,"VCF positions are one-based positive integers");
                    String[] alt=f[4].equals(".")?new String[0]:f[4].split(",",-1);require(alt.length+1<=limits.alleles(),"Allele capacity exceeded");
                    require(Arrays.stream(alt).allMatch(a->!a.isEmpty())&&new HashSet<>(Arrays.asList(alt)).size()==alt.length,"Empty or duplicate alternate allele");
                    numeric(f[5],"Float","QUAL");var sourceInfo=new HashMap<String,String>();
                    if(!f[7].equals("."))for(String item:f[7].split(";",-1)){
                        String[] pair=item.split("=",2);require(!pair[0].isEmpty()&&sourceInfo.putIfAbsent(pair[0],pair.length==1?"":pair[1])==null,"Empty or duplicate INFO key");
                        String type=infos.get(pair[0]);if(type==null)unknownInfo.add(pair[0]);else if(pair.length==2)numeric(pair[1],type,"INFO/"+pair[0]);
                    }
                    long[] alleleCounts=new long[alt.length+1];long complete=0,missing=0,partial=0,called=0;
                    if(!samples.isEmpty()){
                        String[] keys=f[8].split(":",-1);require(new HashSet<>(Arrays.asList(keys)).size()==keys.length,"Duplicate FORMAT key");int gt=-1;
                        for(int k=0;k<keys.length;k++){require(!keys[k].isEmpty(),"Empty FORMAT key");if(keys[k].equals("GT"))gt=k;if(!formats.containsKey(keys[k]))unknownFormat.add(keys[k]);}
                        require(gt>=0,"Original genotype output must retain the GT field");
                        for(int s=0;s<samples.size();s++){
                            if((s&511)==0)cancellation.run();String[] values=f[9+s].split(":",-1);require(values.length<=keys.length,"More genotype fields than FORMAT keys");
                            for(int k=0;k<values.length;k++)numeric(values[k],formats.get(keys[k]),"FORMAT/"+keys[k]);
                            require(gt<values.length&&!values[gt].isEmpty(),"Missing literal GT field");String value=values[gt];
                            require(!(value.indexOf('/')>=0&&value.indexOf('|')>=0),"Mixed genotype separators are not repaired");String[] copies=value.split("[/|]",-1);long n=0,absent=0,nonref=0;
                            for(String a:copies){if(a.equals(".")){absent++;continue;}require(!a.isEmpty(),"Empty GT allele");long ix=integer(a,"GT allele index");require(ix>=0&&ix<alleleCounts.length,"GT allele index outside literal allele population");alleleCounts[(int)ix]=Math.addExact(alleleCounts[(int)ix],1);n++;if(ix!=0)nonref++;}
                            if(absent==0){complete++;totals[s][0]++;}else if(n==0){missing++;totals[s][1]++;}else{partial++;totals[s][2]++;}
                            if(value.indexOf('|')>=0)totals[s][3]++;totals[s][4]=Math.addExact(totals[s][4],n);totals[s][5]=Math.addExact(totals[s][5],nonref);called=Math.addExact(called,n);
                        }
                    }
                    byte[] bytes=(line+"\n").getBytes(StandardCharsets.UTF_8);digest.update(bytes);String rowHash=hex(sha256().digest(bytes));
                    output.write(rows+"\t"+f[0]+"\t"+position+"\t"+f[3]+"\t"+f[4]+"\t"+complete+"\t"+missing+"\t"+partial+"\t"+called+"\t"+join(alleleCounts)+"\t"+sourceInfo.getOrDefault("AC",".")+"\t"+sourceInfo.getOrDefault("AN",".")+"\t"+rowHash+"\n");
                }
                require(samples!=null,"Complete #CHROM header absent");var summaries=new ArrayList<SampleCount>();for(int s=0;s<samples.size();s++){long[] t=totals[s];summaries.add(new SampleCount(samples.get(s),t[0],t[1],t[2],t[3],t[4],t[5]));}
                if(!unknownInfo.isEmpty())warnings.add("Original INFO keys without header definitions are retained: "+unknownInfo);
                if(!unknownFormat.isEmpty())warnings.add("Original FORMAT keys without header definitions are retained: "+unknownFormat);
                if(rows==0)warnings.add("Original VCF contains no variant records; no variant or association finding is inferred.");
                return new Result(rows,cells,samples,summaries,header,clocks,warnings,hex(digest.digest()),counted.total);
            }
        }catch(ArithmeticException e){throw new IOException("Complete native VCF accounting exceeded signed 64-bit capacity",e);}
    }
    private static String join(long[] values){var s=new StringBuilder();for(long v:values){if(s.length()>0)s.append(',');s.append(v);}return s.toString();}
    private static long integer(String value,String field)throws IOException {try{return Long.parseLong(value);}catch(NumberFormatException e){throw new IOException("Invalid literal "+field+": "+value,e);}}
    private static void numeric(String value,String type,String field)throws IOException {
        if(type==null||!type.equals("Float")&&!type.equals("Integer"))return;
        for(String token:value.split(",",-1)){if(token.equals("."))continue;if(type.equals("Integer"))integer(token,field);else try{if(!Double.isFinite(Double.parseDouble(token)))throw new NumberFormatException("Nonfinite original numeric value");}catch(NumberFormatException e){throw new IOException("Unreportable original numeric "+field+" value: "+token+"; preserve the source output and investigate, without substitution",e);}}
    }
    private static void definition(String line,String prefix,Map<String,String> target)throws IOException {
        require(line.endsWith(">"),"Unterminated VCF metadata definition");String body=line.substring(prefix.length(),line.length()-1);var parts=new ArrayList<String>();var token=new StringBuilder();boolean quote=false,escape=false;
        for(char c:body.toCharArray()){if(escape){token.append(c);escape=false;continue;}if(c=='\\'&&quote){token.append(c);escape=true;continue;}if(c=='\"')quote=!quote;if(c==','&&!quote){parts.add(token.toString());token.setLength(0);}else token.append(c);}require(!quote&&!escape,"Unterminated quoted VCF definition");parts.add(token.toString());var fields=new HashMap<String,String>();
        for(String p:parts){String[] f=p.split("=",2);require(f.length==2&&fields.putIfAbsent(f[0],f[1])==null,"Invalid duplicate VCF definition field");}
        String id=fields.get("ID"),type=fields.get("Type");require(id!=null&&!id.isEmpty()&&type!=null,"VCF ID/Type definition absent");String old=target.putIfAbsent(id,type);require(old==null||old.equals(type),"Conflicting VCF numeric field definitions");
    }
    private static String boundedLine(BufferedReader in,int cap)throws IOException {var s=new StringBuilder();for(int c;(c=in.read())!=-1;){if(c=='\n')return s.toString();if(c=='\r'){in.mark(1);int next=in.read();if(next!='\n'&&next!=-1)in.reset();return s.toString();}if(s.length()>=cap)throw new IOException("Complete VCF line capacity exceeded");s.append((char)c);}return s.length()==0?null:s.toString();}
    private static final class Counted extends FilterInputStream {long total;final long cap;Counted(InputStream in,long cap){super(in);this.cap=cap;}private void add(int n)throws IOException {if(n>0){total=Math.addExact(total,n);if(total>cap)throw new IOException("Complete decoded VCF byte capacity exceeded");}}@Override public int read()throws IOException {int x=in.read();add(x<0?0:1);return x;}@Override public int read(byte[] b,int off,int len)throws IOException {int n=in.read(b,off,len);add(n);return n;}}
    private static MessageDigest sha256(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String hex(byte[] data){return HexFormat.of().formatHex(data);}
    private static void require(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.splicing.RmatsResults;
import org.jlinalg.splicing.RmatsResultScanner;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete source-table checks and streamed selections; original fitting remains upstream. */
final class NfcoreRnasplice104Postfit {
    private NfcoreRnasplice104Postfit() { }
    private static final int LINE=1048576;
    static List<Map<String,Object>> quantification(NfcoreRnasplice104Files source,NfcoreRnasplice104Request.Request request)throws IOException{
        var reports=new ArrayList<Map<String,Object>>();for(String family:List.of("salmon","star_salmon/salmon")){Object census=null;for(var sample:request.samples()){String member=family+"/"+sample.id()+"/quant.sf";Map<String,Object> report=quantificationFile(source.output(member),request.quantificationMaximumRows());if(census==null)census=report.get("target_population_sha256");else if(!census.equals(report.get("target_population_sha256")))throw new IOException("Complete transcript population differs within original quantifier "+family+" for "+sample.id()+"; no row intersection/subset is admitted");var row=new TreeMap<String,Object>(report);row.put("family",family);row.put("sample",sample.id());row.put("source_output",member);reports.add(row);}}return List.copyOf(reports);
    }
    static Map<String,Object> quantificationFile(Path input,int maximumRows)throws IOException{
        var names=new java.util.TreeSet<String>();BigDecimal tpm=BigDecimal.ZERO,reads=BigDecimal.ZERO,rounding=BigDecimal.ZERO;int rows=0;try(var reader=Files.newBufferedReader(input,StandardCharsets.UTF_8)){String header=line(reader);if(!"Name\tLength\tEffectiveLength\tTPM\tNumReads".equals(header))throw new IOException("Unexpected complete source Salmon quant.sf schema");String raw;while((raw=line(reader))!=null){if(Thread.currentThread().isInterrupted())throw new IOException("Complete RNA quantification check interrupted");if(rows>=maximumRows)throw new IOException("Complete RNA quantification row capacity exceeded; no target truncation admitted");String[] cells=raw.split("\t",-1);if(cells.length!=5||cells[0].isEmpty()||!names.add(cells[0]))throw new IOException("Ragged, empty or duplicated original transcript identifier");if(!cells[1].matches("[0-9]+"))throw new IOException("Invalid original transcript length");try{if(Long.parseLong(cells[1])<=0)throw new IOException("Nonpositive original transcript length");}catch(NumberFormatException failure){throw new IOException("Original transcript length exceeds complete integer capacity",failure);}BigDecimal effective=nonnegative(cells[2]),abundance=nonnegative(cells[3]),estimated=nonnegative(cells[4]);if(effective.signum()==0&&(abundance.signum()>0||estimated.signum()>0))throw new IOException("Original quantified transcript has zero effective length");tpm=tpm.add(abundance);reads=reads.add(estimated);rounding=rounding.add(abundance.ulp().multiply(new BigDecimal("0.5")));rows++;}}
        if(tpm.signum()!=0&&tpm.subtract(new BigDecimal("1000000")).abs().compareTo(rounding.add(new BigDecimal("0.000001")))>0)throw new IOException("Original complete TPM total differs beyond its printed-decimal rounding bound");if(tpm.signum()==0&&reads.signum()>0)throw new IOException("Original quantifier estimated positive reads but every TPM is zero");MessageDigest hash;try{hash=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException failure){throw new IOException(failure);}for(String name:names){hash.update(name.getBytes(StandardCharsets.UTF_8));hash.update((byte)'\n');}return Map.of("targets",rows,"target_population_sha256",java.util.HexFormat.of().formatHex(hash.digest()),"target_census_order","sorted_for_population_digest_only_original_table_unchanged","TPM_total",tpm.toString(),"estimated_read_total",reads.toString(),"printed_TPM_rounding_bound",rounding.toString(),"zero_abundance_unassessable",tpm.signum()==0,"source_pin",pin(input,1000000000000L,()->{}));
    }
    private static BigDecimal nonnegative(String raw)throws IOException{if(raw.length()>128||!raw.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Invalid original finite nonnegative quantification token: "+raw);try{BigDecimal value=new BigDecimal(raw);if(value.signum()<0||Math.abs((long)value.scale())>1000||!Double.isFinite(Double.parseDouble(raw)))throw new IOException("Original quantification numeric domain/capacity failure");return value;}catch(NumberFormatException failure){throw new IOException("Invalid original quantification decimal",failure);}}
    private static String line(java.io.Reader reader)throws IOException{var out=new StringBuilder();int c;boolean any=false;while((c=reader.read())!=-1){any=true;if(c=='\n')break;if(out.length()>=LINE+1L)throw new IOException("Original complete quantification line capacity exceeded");out.append((char)c);}if(!any)return null;int size=out.length();if(size>0&&out.charAt(size-1)=='\r')out.setLength(--size);if(size>LINE||out.indexOf("\r")>=0||out.indexOf("\u0000")>=0)throw new IOException("Invalid original quantification physical line");return out.toString();}
    static List<Map<String,Object>> rmats(NfcoreRnasplice104Files source,NfcoreRnasplice104Request.Request request,Path out,boolean write,long maximumFile)throws IOException {
        if(write)Files.createDirectory(out);var groups=new HashMap<String,Integer>();for(var sample:request.samples())groups.merge(sample.condition(),1,Math::addExact);var reports=new ArrayList<Map<String,Object>>();int number=0;
        for(var contrast:request.contrasts()){number++;for(var event:RmatsResults.Event.values())for(String mode:List.of("JC","JCEC")){
            String name="star_salmon/rmats/"+contrast.rmatsPrefix()+"/rmats_post/"+event.name()+".MATS."+mode+".txt";Path input=source.output(name);String relative=String.format(java.util.Locale.ROOT,"contrast-%06d/%s-%s",number,event.name(),mode);Path directory=out.resolve(relative);if(write)Files.createDirectories(directory);Pin before=pin(input,maximumFile,()->{});var files=new TreeMap<String,Pin>();RmatsResultScanner.Summary summary;
            try(var reader=Files.newBufferedReader(input,StandardCharsets.UTF_8);var selected=new Sink(directory.resolve("selected.tsv"),write,maximumFile);var unresolved=new Sink(directory.resolve("unassessable.tsv"),write,maximumFile);var decisions=new Sink(directory.resolve("decisions.tsv"),write,maximumFile)){
                reader.mark(4096);StringBuilder header=new StringBuilder();int ch;while((ch=reader.read())!=-1&&ch!='\n'){if(header.length()>2048)throw new IOException("Unexpected complete rMATS header capacity");header.append((char)ch);}if(header.length()>0&&header.charAt(header.length()-1)=='\r')header.setLength(header.length()-1);reader.reset();selected.line(header.toString());unresolved.line(header.toString());decisions.line("source_line\tdecision");
                var thresholds=new RmatsResults.Threshold(new BigDecimal(request.rmatsMaximumP()),new BigDecimal(request.rmatsMinimumPsi()));summary=RmatsResultScanner.scan(reader,event,new RmatsResults.Limits(request.rmatsMaximumRows(),LINE,groups.get(contrast.treatment()),groups.get(contrast.control())),thresholds,row->{decisions.line(row.sourceLine()+"\t"+row.decision().name());if(row.decision()==RmatsResults.Decision.SELECTED)selected.line(row.rawLine());if(row.decision()==RmatsResults.Decision.UNASSESSABLE)unresolved.line(row.rawLine());});files.put("selected.tsv",selected.finish());files.put("unassessable.tsv",unresolved.finish());files.put("decisions.tsv",decisions.finish());
            }
            if(!pin(input,maximumFile,()->{}).equals(before))throw new IOException("Captured complete rMATS source table changed during streamed check");var report=new TreeMap<String,Object>();report.put("contrast_id",contrast.id());report.put("treatment",contrast.treatment());report.put("control",contrast.control());report.put("source_publication_prefix",contrast.rmatsPrefix());report.put("event",event.name());report.put("mode",mode);report.put("source_output",name);report.put("source_pin",before);report.put("rows",summary.rows());report.put("decisions",new TreeMap<>(summary.decisions()));report.put("source_NA_fields",new TreeMap<>(summary.missingFields()));report.put("original_ID_positions",summary.idPositions());report.put("selection_maximum_raw_p",request.rmatsMaximumP());report.put("selection_minimum_absolute_psi",request.rmatsMinimumPsi());report.put("FDR_substituted",false);report.put("files",files);byte[] raw=(NextflowJson.json(report)+"\n").getBytes(StandardCharsets.UTF_8);if(write)Files.write(directory.resolve("summary.json"),raw,StandardOpenOption.CREATE_NEW);else if(!java.util.Arrays.equals(raw,bytes(directory.resolve("summary.json"),67108864)))throw new IOException("RNA streamed rMATS summary semantic replay differs");if(!write)for(var entry:files.entrySet())if(!entry.getValue().equals(pin(directory.resolve(entry.getKey()),maximumFile,()->{})))throw new IOException("RNA streamed rMATS semantic replay differs: "+relative+"/"+entry.getKey());reports.add(Map.of("path",relative,"summary",pin(directory.resolve("summary.json"),67108864,()->{}),"rows",summary.rows(),"decisions",summary.decisions(),"source_NA_fields",summary.missingFields()));
        }}return List.copyOf(reports);
    }
    /** Streaming digest writer used identically for production and saved semantic replay. */
    private static final class Sink implements AutoCloseable {
        private final MessageDigest digest;
        private final BufferedWriter writer;
        private final Limited stream;
        private Pin result;
        Sink(Path path,boolean write,long maximum)throws IOException{try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException failure){throw new IOException(failure);}stream=new Limited(write?Files.newOutputStream(path,StandardOpenOption.CREATE_NEW):OutputStream.nullOutputStream(),maximum);writer=new BufferedWriter(new OutputStreamWriter(new DigestOutputStream(stream,digest),StandardCharsets.UTF_8),65536);}
        void line(String value)throws IOException{writer.write(value);writer.write('\n');}
        Pin finish()throws IOException{if(result==null){writer.flush();result=new Pin(stream.count,java.util.HexFormat.of().formatHex(digest.digest()));}return result;}
        @Override public void close()throws IOException{writer.close();}
    }
    private static final class Limited extends OutputStream {
        private final OutputStream output;private final long maximum;private long count;
        Limited(OutputStream output,long maximum){this.output=output;this.maximum=maximum;}
        @Override public void write(int value)throws IOException{check(1);output.write(value);}
        @Override public void write(byte[] values,int offset,int length)throws IOException{check(length);output.write(values,offset,length);}
        private void check(long bytes)throws IOException{count=Math.addExact(count,bytes);if(count>maximum)throw new IOException("Complete streamed RNA derived-file capacity exceeded; no truncation admitted");}
        @Override public void flush()throws IOException{output.flush();}
        @Override public void close()throws IOException{output.close();}
    }
}

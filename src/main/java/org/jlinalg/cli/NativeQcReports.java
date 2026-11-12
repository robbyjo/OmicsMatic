/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.jlinalg.sequence.FastqIO;
import org.jlinalg.sequence.FastqQualityMetrics;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native raw FASTQ metrics and bounded aggregation of complete source QC reports. */
final class NativeQcReports {
    private NativeQcReports() { }
    private static final String METRIC_SCOPE="Raw unbinned Phred+33 FASTQ measurements from every record. Quality percentiles use the inverse empirical CDF; mean-read qualities are floored integers; GC bins round canonical-base GC percentages to the nearest integer. No FastQC threshold, duplication, contamination, adapter-content or overrepresentation decision is inferred. INFO means measured without a pass/fail threshold.";
    private static final long MEMBER_BYTES=32L*1024*1024;
    static void fastq(Map<String,String> options,Path directory,Map<String,Object> manifest)throws Exception {
        for(String key:List.of("fastqc-home","java","threads","memory-mb"))if(options.containsKey(key))throw new IOException("--"+key+" does not apply to native FASTQ QC");
        Path first=reads(required(options,"reads")),second=options.containsKey("reads2")?reads(options.get("reads2")):null;
        var inputs=new LinkedHashMap<String,String>();inputs.put("reads",hash(first));if(second!=null)inputs.put("reads2",hash(second));
        // Pair identity is checked independently before either report is admitted.
        FastqIO.inspect(first,second);
        int maximum=integer(options,"maximum-positions",100000,1,1000000);
        var rows=new ArrayList<String[]>();int ordinal=0;
        for(Path input:second==null?List.of(first):List.of(first,second)) {
            NativeToolSupport.check();String label="read"+(++ordinal);Path data=Files.createDirectory(directory.resolve(label));
            var report=FastqQualityMetrics.inspect(input,maximum,NativeToolSupport::check);
            var perBase=new ArrayList<String[]>();
            for(var p:report.positions())perBase.add(new String[]{Integer.toString(p.base()),Long.toString(p.observations()),Double.toString(p.mean()),Integer.toString(p.q10()),Integer.toString(p.q25()),Integer.toString(p.median()),Integer.toString(p.q75()),Integer.toString(p.q90()),Long.toString(p.a()),Long.toString(p.c()),Long.toString(p.g()),Long.toString(p.t()),Long.toString(p.n()),Long.toString(p.other())});
            table(data.resolve("per-base.tsv"),List.of("base","observations","mean_phred","q10","q25","median","q75","q90","A","C","G","T","N","other_iupac"),perBase);
            histogram(data.resolve("lengths.tsv"),"length",report.lengths());histogram(data.resolve("mean-quality.tsv"),"floor_mean_phred",report.meanQualities());histogram(data.resolve("gc-percent.tsv"),"rounded_canonical_gc_percent",report.gcPercent());
            String filename=input.getFileName().toString();
            if(filename.indexOf('\t')>=0||filename.indexOf('\n')>=0||filename.indexOf('\r')>=0)throw new IOException("QC report filenames must not contain tabs or newlines");
            String basic="##OmicsMatic-Native-QC\t1\n>>Basic Statistics\tinfo\n#Measure\tValue\nFilename\t"+filename+"\nTotal Sequences\t"+report.reads()+"\nTotal Bases\t"+report.bases()+"\nMinimum Length\t"+report.minLength()+"\nMaximum Length\t"+report.maxLength()+"\n>>END_MODULE\n";
            Files.writeString(data.resolve("fastqc_data.txt"),basic);Files.writeString(data.resolve("summary.txt"),"INFO\tRaw FASTQ measurements\t"+filename+"\n");
            Path archive=directory.resolve(label+"_native_qc.zip");
            try(var zip=new ZipOutputStream(Files.newOutputStream(archive))) {
                for(String name:List.of("fastqc_data.txt","summary.txt","per-base.tsv","lengths.tsv","mean-quality.tsv","gc-percent.tsv")) {
                    Path file=data.resolve(name);if(Files.size(file)>MEMBER_BYTES)throw new IOException("Native QC report member exceeds32MiB; reduce maximum-positions or use a separately scoped report");
                    zip.putNextEntry(new ZipEntry("native_qc/"+name));Files.copy(file,zip);zip.closeEntry();
                }
            }
            var details=new StringBuilder("<h2>"+NativeToolSupport.html(filename)+"</h2><p>Reads: "+report.reads()+"; bases: "+report.bases()+"; lengths: "+report.minLength()+"–"+report.maxLength()+"</p>");
            for(String name:List.of("per-base.tsv","lengths.tsv","mean-quality.tsv","gc-percent.tsv"))details.append("<details><summary>").append(name).append("</summary><pre>").append(NativeToolSupport.html(Files.readString(data.resolve(name)))).append("</pre></details>");
            Files.writeString(directory.resolve(label+"_native_qc.html"),html("Native FASTQ quality measurements",METRIC_SCOPE,details.toString()));
            rows.add(new String[]{label,filename,Long.toString(report.reads()),Long.toString(report.bases()),Integer.toString(report.minLength()),Integer.toString(report.maxLength()),"INFO"});
        }
        if(!inputs.get("reads").equals(hash(first))||second!=null&&!inputs.get("reads2").equals(hash(second)))throw new IOException("FASTQ inputs changed while measuring quality");
        manifest.put("input_sha256",inputs);manifest.put("scope",METRIC_SCOPE);
        table(directory.resolve("results.tsv"),List.of("sample","input_filename","reads","bases","minimum_length","maximum_length","qc_status"),rows);
        Files.writeString(directory.resolve("WARNINGS.md"),METRIC_SCOPE+"\nZero reads or zero-length reads have no quality mean and no GC observation. Inspect the raw tables before choosing study-specific acceptance criteria.\n");
    }
    private static void histogram(Path file,String value,Map<Integer,Long> counts)throws IOException {
        var rows=new ArrayList<String[]>();for(var e:new TreeMap<>(counts).entrySet())rows.add(new String[]{e.getKey().toString(),e.getValue().toString()});table(file,List.of(value,"reads"),rows);
    }
    static void aggregate(Map<String,String> options,Path directory,Map<String,Object> manifest)throws Exception {
        if(options.containsKey("python"))throw new IOException("--python does not apply to native report aggregation");
        Path reports=Path.of(required(options,"reports")).toAbsolutePath().normalize();String reportsHash=hash(reports);var input=DelimitedData.read(reports);
        int maximumCharacters=integer(options,"maximum-report-characters",16777216,1024,268435456);
        if(!input.header().equals(List.of("sample_id","fastqc_zip","cutadapt_json"))||input.rows().isEmpty()||input.rows().size()>10000)throw new IOException("Require 1..10000 rows with sample_id, fastqc_zip, cutadapt_json columns");
        Path sources=Files.createDirectory(directory.resolve("source-reports"));var ids=new HashSet<String>();var paths=new HashSet<Path>();
        var rows=new ArrayList<String[]>();var findings=new ArrayList<String[]>();var pins=new ArrayList<String[]>();var warnings=new ArrayList<String>();var content=new StringBuilder();long bytes=0;
        for(String[] row:input.rows()) {
            NativeToolSupport.check();String id=row[0];
            if(!id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")||!ids.add(id.toLowerCase(Locale.ROOT)))throw new IOException("Sample IDs must be unique ignoring case and use1..100 ASCII letters/digits/underscore/dot/hyphen");
            if(row[1].isBlank()&&row[2].isBlank())throw new IOException("Sample has no report: "+id);
            for(int column=1;column<=2;column++)if(!row[column].isBlank()) {
                Path original=reports.getParent().resolve(row[column]).toRealPath();if(!Files.isRegularFile(original)||!paths.add(original))throw new IOException("Every report must be a distinct regular file");
                bytes=Math.addExact(bytes,Files.size(original));if(Files.size(original)>64L*1024*1024||bytes>2L*1024*1024*1024)throw new IOException("Source reports exceed64MiB each or2GiB total");
                String pin=hash(original),name=id+(column==1?".qc.zip":".cutadapt.json");Path copy=sources.resolve(name);Files.copy(original,copy);
                if(!pin.equals(hash(copy))||!pin.equals(hash(original)))throw new IOException("Source report changed while captured");
                pins.add(new String[]{id,column==1?"fastqc":"cutadapt",original.toString(),"source-reports/"+name,pin});
                if(column==1) {
                    String data=zipText(copy,"fastqc_data.txt"),summary=zipText(copy,"summary.txt");boolean nativeQc=data.startsWith("##OmicsMatic-Native-QC\t1\n");
                    if(!nativeQc&&!data.startsWith("##FastQC\t"))throw new IOException("Unknown QC ZIP producer/version marker");
                    String filename=singleField(data,"Filename");long total=nonnegative(singleField(data,"Total Sequences"));
                    var modules=new HashSet<String>();
                    for(String line:summary.lines().toList()) {
                        String[] fields=line.split("\t",-1);if(fields.length!=3||!fields[2].equals(filename)||fields[1].isBlank()||!modules.add(fields[1])||!(SetHolder.QC.contains(fields[0])||nativeQc&&fields[0].equals("INFO")))throw new IOException("Malformed/duplicate QC summary or filename");
                        findings.add(new String[]{id,fields[1],fields[0],filename});if(!fields[0].equals("PASS")&&!fields[0].equals("INFO"))warnings.add(id+": "+fields[0]+" - "+fields[1]);
                    }
                    if(modules.isEmpty())throw new IOException("QC ZIP has no module summaries");
                    rows.add(new String[]{id,nativeQc?"native-fastq-qc":"fastqc",Long.toString(total),""});
                    content.append("<h2>").append(NativeToolSupport.html(id)).append(nativeQc?" — native FASTQ QC":" — FastQC").append("</h2><pre>").append(NativeToolSupport.html(summary)).append("</pre><details><summary>Complete source module data</summary><pre>").append(NativeToolSupport.html(data)).append("</pre></details>");
                    if(nativeQc)for(String member:List.of("per-base.tsv","lengths.tsv","mean-quality.tsv","gc-percent.tsv"))content.append("<details><summary>").append(member).append("</summary><pre>").append(NativeToolSupport.html(zipText(copy,member))).append("</pre></details>");
                }else {
                    String raw=Files.readString(copy);Object parsed=SimpleJson.parse(raw);
                    if(!(parsed instanceof Map<?,?> report)||!List.of("Cutadapt report","OmicsMatic trimming report").contains(report.get("tag"))||!(report.get("read_counts") instanceof Map<?,?> counts))throw new IOException("Not an original Cutadapt or native trimming JSON report");
                    boolean nativeTrim="OmicsMatic trimming report".equals(report.get("tag"));
                    long total=nonnegative(counts.get("input")),retained=nonnegative(counts.get("output"));if(retained>total)throw new IOException("Cutadapt output count exceeds input");
                    rows.add(new String[]{id,nativeTrim?"native-adapter-trimming":"cutadapt",Long.toString(total),Long.toString(retained)});
                    String status=total==0||retained==0?"WARN":"PASS";findings.add(new String[]{id,"Cutadapt retained reads/pairs",status,original.getFileName().toString()});
                    if(!status.equals("PASS"))warnings.add(id+": Cutadapt "+(total==0?"processed no input":"retained no reads/pairs"));
                    content.append("<h2>").append(NativeToolSupport.html(id)).append(nativeTrim?" — native adapter trimming":" — Cutadapt").append("</h2><p>Input: ").append(total).append("; retained: ").append(retained).append("</p><details><summary>Complete source JSON</summary><pre>").append(NativeToolSupport.html(raw)).append("</pre></details>");
                }
                if(content.length()>maximumCharacters)throw new IOException("Native aggregate report character capacity exceeded; no report is admitted");
                if(!pin.equals(hash(original))||!pin.equals(hash(copy)))throw new IOException("Source QC report changed while aggregated");
            }
        }
        if(!reportsHash.equals(hash(reports)))throw new IOException("Report mapping changed while aggregated");
        table(directory.resolve("results.tsv"),List.of("sample_id","module","input_reads_or_pairs","retained_reads_or_pairs"),rows);
        table(directory.resolve("source-qc.tsv"),List.of("sample_id","module","qc_status","input_filename"),findings);
        table(directory.resolve("report-sources.tsv"),List.of("sample_id","module","source","captured","sha256"),pins);
        var overview=new StringBuilder("<table><thead><tr><th>Sample</th><th>Module</th><th>Input reads/pairs</th><th>Retained reads/pairs</th></tr></thead><tbody>");
        for(String[] row:rows){overview.append("<tr>");for(String cell:row)overview.append("<td>").append(NativeToolSupport.html(cell)).append("</td>");overview.append("</tr>");}overview.append("</tbody></table>");
        String scope="Native aggregation of explicitly supplied FastQC ZIP, OmicsMatic native-QC ZIP and Cutadapt JSON reports. Complete source files, module statuses and data are retained. INFO means native measurements without FastQC thresholds. Original WARN/FAIL findings are preserved. No reads are filtered and no MultiQC plugin or automatic report discovery is used.";
        String rendered=html("OmicsMatic native QC aggregation",scope,overview.append(content).toString());if(rendered.length()>maximumCharacters)throw new IOException("Complete native report exceeds maximum-report-characters");
        Files.writeString(directory.resolve("multiqc_report.html"),rendered);
        Files.writeString(directory.resolve("WARNINGS.md"),scope+"\n\n"+String.join("\n",warnings)+"\n\nRequired action: inspect source-qc.tsv and the preserved reports with assay/library context. Report aggregation does not establish that input libraries pass QC.\n");
        manifest.put("scope",scope);manifest.put("source_qc_findings",warnings);manifest.put("report_mapping_sha256",reportsHash);
    }
    private static final class SetHolder {private static final java.util.Set<String> QC=java.util.Set.of("PASS","WARN","FAIL");}
    private static Path reads(String path)throws IOException {Path file=Path.of(path).toAbsolutePath().normalize();if(!Files.isRegularFile(file))throw new IOException("FASTQ file does not exist: "+file);return file;}
    private static String singleField(String text,String name)throws IOException {
        var values=text.lines().filter(line->line.startsWith(name+"\t")).map(line->line.substring(name.length()+1)).toList();
        if(values.size()!=1||values.get(0).isBlank())throw new IOException("QC report requires one "+name+" field");return values.get(0);
    }
    private static long nonnegative(Object value)throws IOException {
        try {if(value instanceof String text)return nonnegative(Long.valueOf(text));if(!(value instanceof Number n)||n.longValue()<0||n.doubleValue()!=n.longValue())throw new IOException("Invalid report count: "+value);return n.longValue();}
        catch(NumberFormatException failure){throw new IOException("Invalid report count: "+value,failure);}
    }
    private static String zipText(Path file,String suffix)throws IOException {
        try(var zip=new ZipFile(file.toFile())) {
            var entries=zip.stream().filter(entry->!entry.isDirectory()&&entry.getName().endsWith("/"+suffix)).toList();
            if(entries.size()!=1||entries.get(0).getSize()<0||entries.get(0).getSize()>MEMBER_BYTES)throw new IOException("Invalid or excessive QC ZIP member: "+suffix);
            try(var input=zip.getInputStream(entries.get(0))) {byte[] data=input.readNBytes((int)MEMBER_BYTES+1);if(data.length>MEMBER_BYTES)throw new IOException("QC ZIP member exceeds32MiB");return NextflowFiles.utf8(data);}
        }
    }
    private static String html(String title,String scope,String body) {
        return "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>"+NativeToolSupport.html(title)+"</title><style>body{font:16px system-ui;max-width:1100px;margin:2rem auto;padding:0 1rem;color:#17283b}table{border-collapse:collapse;width:100%}th,td{padding:.5rem;border-bottom:1px solid #ddd;text-align:left}pre{overflow:auto;background:#f3f6f8;padding:1rem}details{margin:1rem 0}</style><h1>"+NativeToolSupport.html(title)+"</h1><p>"+NativeToolSupport.html(scope)+"</p>"+body+"</html>";
    }
}

/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jlinalg.sequence.DnaMotifScanner;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed original FIMO adapter; original q-values are preserved, never re-estimated. */
final class FimoToolsCli {
    private FimoToolsCli() { }
    private static final Set<String> OPTIONS=Set.of("motifs","fasta","home","out","background","background-file","strands","coordinates",
            "threshold-type","threshold","format","q-values","pseudocount","maximum-stored-scores","motif-id","timeout","maximum-residues","maximum-sequences","maximum-input-bytes","options-file");
    private static final List<String> PRODUCTS=List.of("results.tsv","sequence-metadata.tsv","models.tsv","upstream","working");
    private static final List<String> HEADER=List.of("motif_id","motif_alt_id","sequence_name","start","stop","strand","score","p-value","q-value","matched_sequence");
    private static final String INPUT_RECOVERY="Required action: inspect fimo.stdout/fimo.stderr, session.yaml and the unchanged source inputs; correct the named cause and rerun to a new output directory. If a stored-score cap was reached, raise --maximum-stored-scores, restrict the explicitly declared input cohort or choose a scientifically justified threshold. A truncated or empty capped table cannot establish absence of motif binding. Partial scientific files are quarantined under partial/ and are not successful results. Original FIMO q-values, population handling and estimator warnings are retained; no replacement BH or missing-allele zero score is inferred.";

    static int run(String[] arguments,PrintStream output,PrintStream error) {
        if(arguments.length==1 && arguments[0].equals("--help")) { output.println(help());return 0; }
        try {
            var o=parse(arguments);required(o,"out");int[] code={0};boolean[] warned={false};
            externalTransaction(o,"fimo-original-motif-scan",(directory,manifest) -> {
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                session.put("engine","original-fimo");session.put("installation_policy","No installation or network access during analysis; explicitly provision an admitted profile first");
                session.put("statistical_scope","Original FIMO5.5.9 DNA PSSM scores/zero-order tails and original per-motif q-value/cohort/estimator behavior. Text mode has no q-values. No PSPs/protein/custom alphabets/max-strand sampling/full-format or full-host parity is claimed.");
                try {
                    String background=required(o,"background"),strands=required(o,"strands"),coordinates=required(o,"coordinates"),type=required(o,"threshold-type"),format=o.getOrDefault("format","full");
                    if(!Set.of("uniform","nrdb","motif","file").contains(background))throw new IllegalArgumentException("--background must be uniform, nrdb, motif or file");
                    if(!Set.of("forward","both").contains(strands) || !Set.of("sequence","genomic").contains(coordinates))throw new IllegalArgumentException("Explicit --strands forward|both and --coordinates sequence|genomic required");
                    if(!Set.of("p","q").contains(type) || !Set.of("text","full").contains(format))throw new IllegalArgumentException("--threshold-type must be p or q and --format must be text or full");
                    double threshold=Double.parseDouble(required(o,"threshold")),pseudo=number(o,"pseudocount",.1);
                    if(!Double.isFinite(threshold) || threshold<=0 || threshold>1 || pseudo<0)throw new IllegalArgumentException("Require threshold in (0,1] and finite nonnegative pseudocount");
                    boolean qValues=bool(o,"q-values",format.equals("full"));
                    if(format.equals("text") && qValues || type.equals("q") && (!qValues || !format.equals("full")))throw new IllegalArgumentException("Q-value thresholds/calculation require --format full --q-values true; original text mode has no q-values");
                    if(background.equals("file")!=o.containsKey("background-file"))throw new IllegalArgumentException("--background file requires --background-file, which is not applicable to other backgrounds");
                    if(format.equals("text") && o.containsKey("maximum-stored-scores"))throw new IllegalArgumentException("--maximum-stored-scores applies only to full output; text mode does not store/truncate hits");
                    int cap=integer(o,"maximum-stored-scores",100000,10,10000000),timeout=integer(o,"timeout",3600,1,86400),maxSequences=integer(o,"maximum-sequences",1000000,1,10000000);
                    long maxResidues=positive(o,"maximum-residues",10_000_000_000L),maxBytes=positive(o,"maximum-input-bytes",16_000_000_000L);
                    String motifId=o.get("motif-id");if(motifId!=null && (!motifId.matches("[!-~&&[^=]]{1,255}") || motifId.startsWith("--")))throw new IllegalArgumentException("--motif-id requires one printable ASCII identifier without '=' or an option prefix");
                    Path motifs=source(o,"motifs"),fasta=source(o,"fasta");if(Files.size(motifs)>16_777_216)throw new IOException("Motif input exceeds 16 MiB");
                    String motifText=StandardCharsets.US_ASCII.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(motifs))).toString();
                    if(!motifText.stripLeading().startsWith("MEME version ") || !Pattern.compile("(?m)^\\s*ALPHABET\\s*=\\s*ACGT\\s*$").matcher(motifText).find() || motifText.indexOf(0)>=0)throw new IOException("This adapter accepts plain minimal MEME text with explicit ALPHABET= ACGT; HTML/protein/custom alphabets are outside its tested contract");
                    if(motifId!=null && !Pattern.compile("(?m)^\\s*MOTIF\\s+"+Pattern.quote(motifId)+"(?:\\s|$)").matcher(motifText).find())throw new IOException("Requested --motif-id is absent from the minimal MEME input");
                    var hashes=new LinkedHashMap<String,String>();hashes.put("motifs",hash(motifs));hashes.put("fasta",hash(fasta));
                    Path bgFile=background.equals("file")?source(o,"background-file"):null;if(bgFile!=null) { validateBackground(bgFile);hashes.put("background-file",hash(bgFile)); }
                    if(o.containsKey("options-file")) { Path recipe=source(o,"options-file");hashes.put("options-file",hash(recipe));Files.copy(recipe,directory.resolve("options-input.json")); }
                    manifest.put("input_sha256",hashes);
                    if(!o.containsKey("home"))throw new FimoRuntime.Unavailable("--home for the authenticated private FIMO runtime is required");
                    var runtime=FimoRuntime.open(Path.of(o.get("home")));FimoRuntime.provenance(runtime,session);
                    int versionCode=FimoRuntime.execute(runtime,List.of("--version"),directory,"version",30,session);
                    if(versionCode!=0 || !Files.readString(directory.resolve("version.stdout")).trim().equals("5.5.9"))throw new FimoRuntime.Unavailable("Original FIMO version probe failed; inspect version.stdout/version.stderr");runtime.verify();
                    Path working=directory.resolve("working");Files.createDirectory(working);Path scanFasta=prepareFasta(fasta,working,maxBytes);
                    var identifiers=new HashMap<String,Integer>();final int[] collided={0};DnaMotifScanner.Inspection inspection;
                    try(var ledger=Files.newBufferedWriter(directory.resolve("sequence-metadata.tsv"))) {
                        ledger.write("ordinal\toriginal_id_json\tsequence_name_json\theader_json\tcoordinate_start\tdeclared_end\tobserved_length\n");
                        inspection=DnaMotifScanner.inspect(scanFasta,coordinates.equals("genomic"),maxResidues,true,seq -> {
                            if(seq.ordinal()>maxSequences)throw new IOException("FASTA exceeds --maximum-sequences; raise the explicit limit or restrict the declared cohort");
                            if(identifiers.merge(seq.name(),1,Integer::sum)==2)collided[0]++;
                            ledger.write(seq.ordinal()+"\t"+RegenieRuntime.json(seq.originalId())+"\t"+RegenieRuntime.json(seq.name())+"\t"+RegenieRuntime.json(seq.header())+"\t"+seq.start()+"\t"+seq.declaredEnd()+"\t"+seq.residues()+"\n");
                        });
                    }
                    session.put("input_sequences",inspection.sequences());session.put("input_residues",inspection.residues());session.put("canonical_residues",inspection.canonicalResidues());
                    session.put("threshold_type",type);session.put("threshold",threshold);session.put("format",format);session.put("q_values",qValues);session.put("maximum_stored_scores",format.equals("text")?"not applicable":cap);
                    session.put("scanning_strands",strands);session.put("coordinate_policy",coordinates);session.put("background_source",background);session.put("motif_pseudocount",pseudo);
                    session.put("motif_preprocessing","Original file reader; input probabilities/rounding/background are not normalized or repaired by this adapter");
                    if(inspection.uracilAliases()+inspection.ambiguityAliases()+inspection.convertedInvalid()!=0)warnings.add("FASTA preflight found U/u scoring aliases="+inspection.uracilAliases()+", X/x/dot ambiguity aliases="+inspection.ambiguityAliases()+", other invalid ASCII residues interpreted by original FIMO as N="+inspection.convertedInvalid()+". Original input bytes were retained for the engine. Required action: review the original FASTA and fimo.stderr before interpreting masked windows.");
                    if(inspection.coordinateSpanMismatches()!=0)warnings.add("FASTA has "+inspection.coordinateSpanMismatches()+" genomic header spans inconsistent with observed lengths. Required action: inspect sequence-metadata.tsv and correct headers or choose sequence coordinates before genomic interpretation.");
                    if(collided[0]!=0)warnings.add(collided[0]+" emitted sequence names occur in multiple input records. Original output has no input ordinal. Required action: use the preserved header/coordinate ledger to inspect possible ambiguous joins; give alleles distinct identifiers before comparing binding scores. An omitted allele is not a zero score.");
                    var command=new ArrayList<String>();
                    if(format.equals("text"))command.add("--text");else command.addAll(List.of("--oc",FimoRuntime.path(directory.resolve("upstream"))));
                    command.addAll(List.of("--thresh",Double.toString(threshold),"--motif-pseudo",Double.toString(pseudo),"--bfile",bgFile==null?"--"+background+"--":FimoRuntime.path(bgFile)));
                    if(!format.equals("text"))command.addAll(List.of("--max-stored-scores",Integer.toString(cap)));
                    if(!qValues && !format.equals("text"))command.add("--no-qvalue");if(type.equals("q"))command.add("--qv-thresh");
                    if(strands.equals("forward"))command.add("--norc");if(coordinates.equals("sequence"))command.add("--no-pgc");if(motifId!=null)command.addAll(List.of("--motif",motifId));
                    command.addAll(List.of(FimoRuntime.path(motifs),FimoRuntime.path(scanFasta)));
                    int exit=FimoRuntime.execute(runtime,command,directory,"fimo",timeout,session);runtime.verify();
                    if(exit!=0)throw new IOException("Original FIMO analysis exited "+exit+". "+tail(directory.resolve("fimo.stderr")));
                    String stderr=tail(directory.resolve("fimo.stderr"));var log=inspectLog(directory.resolve("fimo.stderr"));
                    if(log.incomplete())throw new IOException("INCOMPLETE ORIGINAL FIMO OUTPUT: the stored-score limit discarded matches. Even an empty table cannot support a negative result. "+stderr);
                    if(log.warning())warnings.add("Original FIMO reported warnings in fimo.stderr. Required action: inspect the complete original log before downstream interpretation.\n\n"+stderr);
                    if(log.smallCohort())warnings.add("Original FIMO could not estimate pi_0 accurately for a small motif population and used pi_zero=1.0. Its q-values are preserved. Required action: inspect the per-motif estimator messages in fimo.stderr and report the declared cohort and this estimator condition; do not substitute a different cohort or estimator silently.");
                    if(!qValues && format.equals("full"))warnings.add("Q-values were explicitly disabled. Original FIMO full TSV uses zero placeholders in its q-value field; the project results.tsv leaves those uncomputed fields blank and retains the raw upstream report. Required action: do not interpret those upstream placeholders as zero FDR; enable original q-value calculation when the analysis requires it.");
                    if(format.equals("full"))validateXml(directory.resolve("upstream/fimo.xml"),inspection,motifId,session);
                    else validateTextMotifs(directory.resolve("fimo.stderr"),motifId,strands,session);
                    Path raw=format.equals("text")?directory.resolve("fimo.stdout"):directory.resolve("upstream/fimo.tsv");
                    long count=validateTable(raw,directory.resolve("results.tsv"),type,threshold,qValues,strands,motifId,format.equals("full"),session);
                    for(var entry:hashes.entrySet())if(!hash(source(o,entry.getKey())).equals(entry.getValue()))throw new IOException("Source input changed during analysis: "+entry.getKey());
                    removeWorking(working);session.put("reported_sites",count);warned[0]=!warnings.isEmpty();String state=warned[0]?"completed-with-qc-warnings":"completed";
                    manifest.put("analysis_status",state);status(directory,state);
                    if(warned[0])Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL FIMO WARNINGS\n\n"+String.join("\n\n",warnings)+"\n");
                } catch(Exception failure) {
                    code[0]=failure instanceof FimoRuntime.Unavailable?3:2;String state=code[0]==3?"skipped-dependency-unavailable":"failed";
                    manifest.put("analysis_status",state);manifest.put("error",failure.toString());session.put("error",failure.toString());
                    for(String name:PRODUCTS)if(Files.exists(directory.resolve(name))) { Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve(name),directory.resolve("partial").resolve(name)); }
                    status(directory,state);Files.writeString(directory.resolve("WARNINGS.md"),"# "+(code[0]==3?"FIMO ANALYSIS SKIPPED - DEPENDENCY UNAVAILABLE":"FIMO ANALYSIS FAILED")+"\n\n"+failure+"\n\n"+(code[0]==3?FimoRuntime.recovery():INPUT_RECOVERY)+"\n");
                }
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if(code[0]!=0)error.println((code[0]==3?"SKIPPED FIMO ANALYSIS":"FIMO ANALYSIS FAILED")+": see "+Path.of(o.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");
            else { output.println("Original FIMO analysis completed: "+o.get("out"));if(warned[0])error.println("FIMO WARNINGS: review "+Path.of(o.get("out")).resolve("WARNINGS.md")+" before downstream analysis."); }
            return code[0];
        } catch(Exception failure) { error.println("FIMO ANALYSIS NOT STARTED: "+failure.getMessage()+". "+INPUT_RECOVERY+" Review fimo-tools --help.");return 2; }
    }
    private static Path prepareFasta(Path source,Path work,long maximum) throws IOException {
        try(var raw=new BufferedInputStream(Files.newInputStream(source))) {
            raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();
            if(a!=31 || b!=139) { if(Files.size(source)>maximum)throw new IOException("FASTA exceeds --maximum-input-bytes");return source; }
            Path target=work.resolve("decompressed.fa");long size=0;
            try(var input=new GZIPInputStream(raw);var output=Files.newOutputStream(target)) {
                byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1) { size=Math.addExact(size,n);if(size>maximum)throw new IOException("Decompressed FASTA exceeds --maximum-input-bytes");output.write(buffer,0,n); }
            }
            return target;
        }
    }
    private static void validateBackground(Path path) throws IOException {
        if(Files.size(path)>1_048_576)throw new IOException("Background file exceeds 1 MiB");var tokens=new ArrayList<String>();
        for(String line:Files.readAllLines(path,StandardCharsets.US_ASCII)) { String text=line.split("#",2)[0].trim();if(!text.isEmpty())tokens.addAll(List.of(text.split("\\s+"))); }
        if(tokens.size()!=8)throw new IOException("Require a zero-order background with exactly A/C/G/T probability pairs; higher-order terms are outside this adapter contract");
        var seen=new java.util.HashSet<String>();double sum=0;
        for(int i=0;i<8;i+=2) {
            String base=tokens.get(i);if(!Set.of("A","C","G","T").contains(base) || !seen.add(base))throw new IOException("Invalid/duplicate background letter");
            double value;try { value=Double.parseDouble(tokens.get(i+1)); } catch(NumberFormatException failure) { throw new IOException("Invalid background probability",failure); }
            if(!Double.isFinite(value) || value<0 || value>1)throw new IOException("Require finite background probabilities in [0,1]");sum+=value;
        }
        if(Math.abs(sum-1)>1e-5)throw new IOException("Background probabilities must sum to one within 1e-5; input is left unchanged for the original reader");
    }
    private static void validateXml(Path path,DnaMotifScanner.Inspection inspection,String selected,Map<String,Object> session) throws Exception {
        if(!Files.isRegularFile(path) || Files.size(path)>16_777_216)throw new IOException("Missing or oversized original FIMO XML summary");
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var document=factory.newDocumentBuilder().parse(path.toFile());var root=document.getDocumentElement();
        if(!root.getTagName().equals("fimo") || !root.getAttribute("version").equals("5.5.9"))throw new IOException("Original FIMO XML version/root mismatch");
        var alphabet=document.getElementsByTagName("alphabet");if(alphabet.getLength()!=1 || !((org.w3c.dom.Element)alphabet.item(0)).getAttribute("name").equals("DNA"))throw new IOException("Expected original DNA alphabet summary");
        var data=document.getElementsByTagName("sequence-data");if(data.getLength()!=1)throw new IOException("Missing original sequence count summary");var counts=(org.w3c.dom.Element)data.item(0);
        long n=Long.parseLong(counts.getAttribute("num-sequences")),r=Long.parseLong(counts.getAttribute("num-residues"));
        if(n!=inspection.sequences() || r!=inspection.residues())throw new IOException("Original FIMO cohort counts differ from preflight: original "+n+"/"+r+", preflight "+inspection.sequences()+"/"+inspection.residues());
        session.put("original_sequences",n);session.put("original_residues",r);var motifs=document.getElementsByTagName("motif");boolean found=selected==null;var models=new ArrayList<Map<String,String>>();
        for(int i=0;i<motifs.getLength();i++) { var motif=(org.w3c.dom.Element)motifs.item(i);String id=motif.getAttribute("name");if(id.equals(selected))found=true;models.add(Map.of("id",id,"alternate_id",motif.getAttribute("alt"),"width",motif.getAttribute("width"))); }
        if(!found || models.isEmpty())throw new IOException("Requested motif was not scanned, or no original motifs were loaded");session.put("original_motifs",models);
    }
    private static long validateTable(Path path,Path output,String type,double threshold,boolean qValues,String strands,String selected,boolean fullOutput,Map<String,Object> session) throws IOException {
        long count=0;boolean header=false;
        try(var input=Files.newBufferedReader(path);var out=Files.newBufferedWriter(output)) {
            String line;while((line=input.readLine())!=null) {
                if(line.isBlank() || line.startsWith("#"))continue;
                if(line.length()>1_048_576)throw new IOException("Original FIMO TSV row exceeds 1 MiB");String[] fields=line.split("\t",-1);
                if(!header) { if(!List.of(fields).equals(HEADER))throw new IOException("Original FIMO TSV header mismatch");header=true;out.write(line+"\n");continue; }
                if(fields.length!=10)throw new IOException("Original FIMO TSV column count mismatch");
                try {
                    long start=Long.parseLong(fields[3]),stop=Long.parseLong(fields[4]);double score=Double.parseDouble(fields[6]),p=Double.parseDouble(fields[7]);
                    if(fields[0].isEmpty() || selected!=null && !fields[0].equals(selected) || fields[2].isEmpty() || start<1 || stop<start || stop-start+1!=fields[9].length() || !Set.of("+","-").contains(fields[5]) || strands.equals("forward") && !fields[5].equals("+") || !Double.isFinite(score) || !Double.isFinite(p) || p<0 || p>1)throw new IOException("Invalid original FIMO site identity/geometry/score/probability");
                    double value=p;
                    if(qValues) { double q=Double.parseDouble(fields[8]);if(!Double.isFinite(q) || q<0 || q>1)throw new IOException("Invalid original q-value");if(type.equals("q"))value=q; }
                    else if(!fields[8].isEmpty()) {
                        if(!fullOutput || Double.parseDouble(fields[8])!=0)throw new IOException("Unexpected non-placeholder q-value in original no-q/text output");
                        fields[8]="";session.put("uncomputed_q_field_policy","Original no-q full-output zero placeholders are blank in results.tsv; raw upstream table retained");
                    }
                    double rounding=value==0?0:.5*Math.pow(10,Math.floor(Math.log10(value))-2);
                    if(value>threshold+rounding+threshold*1e-12)throw new IOException("Original FIMO output exceeds the declared threshold beyond TSV rounding precision");
                } catch(NumberFormatException failure) { throw new IOException("Invalid numeric original FIMO output",failure); }
                out.write(String.join("\t",fields)+"\n");count++;
            }
            if(!header) { out.write(String.join("\t",HEADER)+"\n");session.put("upstream_headerless_empty_table",true);header=true; }
        }
        if(!header)throw new IOException("Original FIMO output has no TSV header");session.put("original_text_precision","Scores6/P-and-q3significant digits; original values are preserved without fabricated extra precision");return count;
    }
    private static void removeWorking(Path work) throws IOException { try(var paths=Files.walk(work)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p); } }
    private static String tail(Path path) throws IOException {
        if(!Files.isRegularFile(path))return "No upstream stderr was produced";
        try(var in=Files.newInputStream(path)) { long size=Files.size(path);if(size>32768)in.skipNBytes(size-32768);return new String(in.readNBytes(32768),StandardCharsets.UTF_8); }
    }
    private record LogFindings(boolean incomplete,boolean warning,boolean smallCohort) { }
    private static void validateTextMotifs(Path path,String selected,String strands,Map<String,Object> session) throws IOException {
        var pattern=Pattern.compile("^Using motif ([+-])(.+) of width ([0-9]+)\\.$");var models=new LinkedHashMap<String,Map<String,String>>();
        try(var reader=Files.newBufferedReader(path)) {
            String line;while((line=reader.readLine())!=null) {
                var match=pattern.matcher(line);if(!match.matches())continue;
                String strand=match.group(1),id=match.group(2),width=match.group(3);if(Integer.parseInt(width)<1 || selected!=null && !id.equals(selected))throw new IOException("Unexpected original text-mode motif identity/width");
                models.computeIfAbsent(id,k->new LinkedHashMap<>()).put(strand,width);
            }
        }
        if(models.isEmpty())throw new IOException("Original text scan did not confirm any loaded DNA motif; an empty output cannot be admitted");
        for(var entry:models.entrySet()) {
            var value=entry.getValue();if(!value.containsKey("+") || strands.equals("both") && !value.get("+").equals(value.get("-")) || strands.equals("forward") && value.containsKey("-"))throw new IOException("Original text-mode motif/strand summary is incomplete: "+entry.getKey());
        }
        session.put("original_text_motif_strands_and_widths",models);
    }
    private static LogFindings inspectLog(Path path) throws IOException {
        if(!Files.isRegularFile(path) || Files.size(path)>268_435_456)throw new IOException("Missing or oversized original stderr log");
        boolean incomplete=false,warning=false,small=false;
        try(var reader=Files.newBufferedReader(path)) {
            String line;while((line=reader.readLine())!=null) {
                if(line.length()>1_048_576)throw new IOException("Original stderr line exceeds 1 MiB");String lower=line.toLowerCase(Locale.ROOT);
                incomplete|=lower.contains("reached max stored scores") || lower.contains("dropped to reclaim memory") || lower.contains("matches have been discarded");
                warning|=lower.contains("warning:") && !lower.contains("text mode turns off computation of q-values");
                small|=lower.contains("cannot estimate pi_0 accurately");
            }
        }
        return new LogFindings(incomplete,warning,small);
    }
    private static Map<String,String> parse(String[] args) throws Exception {
        var direct=options(args,OPTIONS);if(!direct.containsKey("options-file"))return direct;Path path=source(direct,"options-file");if(Files.size(path)>1_048_576)throw new IOException("Options recipe exceeds 1 MiB");
        Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString());if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("Require a UTF-8 JSON object of option:string values");var result=new LinkedHashMap<String,String>();
        for(var entry:map.entrySet()) { String key=String.valueOf(entry.getKey());if(!OPTIONS.contains(key) || key.equals("options-file") || !(entry.getValue() instanceof String text))throw new IllegalArgumentException("Unknown/recursive/non-string recipe option: "+key);result.put(key,text); }
        result.putAll(direct);return result;
    }
    private static Path source(Map<String,String> o,String key) throws IOException { Path path=Path.of(required(o,key)).toAbsolutePath().normalize();if(!Files.isRegularFile(path))throw new IOException("Input file unavailable: "+path);return path; }
    private static long positive(Map<String,String> o,String key,long fallback) { long value=Long.parseLong(o.getOrDefault(key,Long.toString(fallback)));if(value<1)throw new IllegalArgumentException("--"+key+" must be positive");return value; }
    private static void status(Path directory,String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\""+state+"\"}\n"); }
    static String help() { return """
        fimo-tools --motifs MOTIFS.meme --fasta SEQUENCES.fa[.gz] --home PRIVATE_HOME
          --background uniform|nrdb|motif|file [--background-file ZERO_ORDER.txt]
          --strands both|forward --coordinates genomic|sequence
          --threshold-type p|q --threshold NUMBER --out NEW_DIR
          [--format full|text] [--q-values true] [--pseudocount 0.1]
          [--maximum-stored-scores 100000] [--motif-id ID] [--timeout 3600]
          [--maximum-residues 10000000000] [--maximum-sequences 1000000]
          [--maximum-input-bytes 16000000000] [--options-file UTF8_OPTIONS.json]
        Original FIMO5.5.9 DNA P/q scanning, exact tested Windows x86_64 private
        Cygwin/GNU-regex profile. No implicit installation/network/OS changes.
        Educational/research/nonprofit terms require a declared permitted profile;
        this is not commercial permission or an alternate-host validation claim.
        Input is plain minimal MEME DNA probability text with explicit alphabet;
        original probability/background preprocessing is preserved, never repaired.
        FASTA is preflighted; gzip is decompressed within the explicit byte limit.
        Full output preserves original reports and computed q-values. Disabled
        full-mode q-value zero placeholders are blank in the project result table;
        raw upstream reports are retained. A complete headerless empty upstream
        table receives the canonical header after summary/truncation validation.
        Text mode has blank
        q-values and no stored-hit cap. Q thresholds require full/q-enabled output.
        Stored-hit truncation FAILS even if original exit0and an empty table were
        returned. Raise the cap or restrict the declared analysis and rerun; an
        incomplete table cannot establish absence of binding. No BH substitution,
        background-cohort substitution, PSP or missing-allele zero score is inferred.
        Complete headers/coordinates/ordinals, source/runtime hashes, exact commands,
        cohort counts, original estimator logs and actionable warnings are retained.
        Exit0complete (warnings may require review),2failed,3dependency unavailable
        and analysis skipped. Failed scientific streams are quarantined under partial/.
        Windows Unicode paths: use an ASCII-named UTF-8 JSON recipe; CLI overrides.
        """; }
}

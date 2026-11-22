/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.TranscriptImport;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native transcript point-estimate import and tximport gene summarization. */
final class TranscriptImportCli {
    private TranscriptImportCli() { }
    static int run(String[] arguments,PrintStream out,PrintStream err) {
        if(Arrays.asList(arguments).contains("--help")){out.println(help());return 0;}
        try {
            var o=options(arguments,Set.of("samples","format","tx2gene","level","counts-from-abundance","id-policy","unmapped","transcriptome-id","inferential-replicates","max-cells","max-bytes","out"));
            for(String key:List.of("samples","format","level","counts-from-abundance","id-policy","transcriptome-id","inferential-replicates","out"))required(o,key);
            String format=choice(o,"format",Set.of("salmon","kallisto","rsem","tsv"));
            String level=choice(o,"level",Set.of("gene","transcript"));
            String mode=choice(o,"counts-from-abundance",Set.of("no","scaledTPM","lengthScaledTPM","dtuScaledTPM"));
            String policy=choice(o,"id-policy",Set.of("exact","strip-version","strip-bar"));
            if(!o.get("inferential-replicates").equals("drop"))throw new IllegalArgumentException("--inferential-replicates drop is required: only point estimates are imported, not uncertainty draws");
            if(level.equals("gene")&&mode.equals("dtuScaledTPM"))throw new IllegalArgumentException("dtuScaledTPM requires --level transcript");
            boolean needsMap=level.equals("gene")||mode.equals("dtuScaledTPM");
            if(needsMap)required(o,"tx2gene");else if(o.containsKey("tx2gene"))throw new IllegalArgumentException("--tx2gene applies to gene output or transcript dtuScaledTPM only");
            if(level.equals("gene"))choice(o,"unmapped",Set.of("error","drop"));else if(o.containsKey("unmapped"))throw new IllegalArgumentException("--unmapped applies only to gene output; DTU requires every transcript mapped");
            int maxCells=integer(o,"max-cells",10_000_000,1,100_000_000),maxBytes=integer(o,"max-bytes",268_435_456,1,Integer.MAX_VALUE);
            var scaling=switch(mode){case "no"->TranscriptImport.Scaling.NO;case "scaledTPM"->TranscriptImport.Scaling.SCALED_TPM;case "lengthScaledTPM"->TranscriptImport.Scaling.LENGTH_SCALED_TPM;default->TranscriptImport.Scaling.DTU_SCALED_TPM;};
            var idPolicy=switch(policy){case "exact"->TranscriptImport.IdPolicy.EXACT;case "strip-version"->TranscriptImport.IdPolicy.STRIP_VERSION;default->TranscriptImport.IdPolicy.STRIP_BAR;};
            int[] code={0};
            transaction(o,"transcript-import",(dir,manifest)->{
                var sources=new LinkedHashMap<Path,String>();var warnings=new ArrayList<String>();var session=new LinkedHashMap<String,Object>();
                session.put("engine","native-java");session.put("reference_semantics","tximport1.40.0 point estimates");session.put("choices",o);
                session.put("scope","Dense Salmon quant.sf,kallisto abundance.tsv,RSEM isoforms.results or canonical TSV point estimates. No quantifier,DE test,uncertainty draws,HDF5,sparse/alevin,StringTie or historical/full-paper parity.");
                try {
                    Path samplesPath=source(o.get("samples"),sources,maxBytes);var sampleTable=read(samplesPath,maxBytes,1000);
                    if(!sampleTable.header.equals(List.of("sample","path")))throw new IOException("Sample manifest requires exactly sample and path columns");
                    if(sampleTable.rows.isEmpty())throw new IOException("Empty sample manifest");
                    var samples=new ArrayList<String>();var quantPaths=new ArrayList<Path>();var sampleSet=new HashSet<String>();
                    for(String[] row:sampleTable.rows){String sample=text(row[0],"Sample ID");if(sample.equals("feature")||!sampleSet.add(sample))throw new IOException("Duplicate/reserved sample ID: "+sample);samples.add(sample);Path path=Path.of(row[1]);if(!path.isAbsolute())path=samplesPath.getParent().resolve(path);quantPaths.add(path.toAbsolutePath().normalize());}
                    List<String> tx=null;double[][] counts=null,abundance=null,lengths=null;int clamped=0;
                    String[] columns=switch(format){case "salmon"->new String[]{"Name","NumReads","TPM","EffectiveLength"};case "kallisto"->new String[]{"target_id","est_counts","tpm","eff_length"};case "rsem"->new String[]{"transcript_id","expected_count","TPM","effective_length"};default->new String[]{"transcript","counts","abundance","length"};};
                    for(int sample=0;sample<samples.size();sample++) {
                        Path path=source(quantPaths.get(sample).toString(),sources,maxBytes);var data=read(path,maxBytes,Math.min(1_000_000,maxCells/samples.size()));
                        int[] indexes=Arrays.stream(columns).mapToInt(data::column).toArray();
                        if(data.rows.isEmpty())throw new IOException("Empty transcript quantification: "+path);
                        var ids=new ArrayList<String>();for(String[] row:data.rows)ids.add(text(row[indexes[0]],"Transcript ID"));
                        if(new HashSet<>(ids).size()!=ids.size())throw new IOException("Duplicate transcript ID in "+path);
                        if(sample==0){tx=ids;if((long)tx.size()*samples.size()>maxCells)throw new IOException("Transcript-by-sample cells exceed --max-cells");counts=new double[tx.size()][samples.size()];abundance=new double[tx.size()][samples.size()];lengths=new double[tx.size()][samples.size()];}
                        else if(!ids.equals(tx))throw new IOException("Transcript IDs/order differ in sample "+samples.get(sample)+"; use the same transcriptome and exact quantification order");
                        for(int row=0;row<data.rows.size();row++) {
                            counts[row][sample]=number(data.rows.get(row)[indexes[1]],"estimated count",row,path);
                            abundance[row][sample]=number(data.rows.get(row)[indexes[2]],"TPM",row,path);
                            double length=number(data.rows.get(row)[indexes[3]],"effective length",row,path);
                            if(format.equals("rsem")&&length<1){length=1;clamped++;}
                            if(!(length>0))throw new IOException("Effective length must be positive in "+path+" row "+(row+2));lengths[row][sample]=length;
                        }
                    }
                    var annotation=new LinkedHashMap<String,String>();int duplicateMappings=0;
                    if(needsMap) {
                        var mapping=read(source(o.get("tx2gene"),sources,maxBytes),maxBytes,1_000_000);
                        if(!mapping.header.equals(List.of("transcript","gene")))throw new IOException("Annotation TSV requires exactly transcript and gene columns");
                        for(String[] row:mapping.rows){String transcript=text(row[0],"Annotation transcript"),gene=text(row[1],"Annotation gene");String prior=annotation.putIfAbsent(transcript,gene);if(prior!=null){if(!prior.equals(gene))throw new IOException("Conflicting gene mappings for transcript "+transcript+"; resolve explicitly");duplicateMappings++;}}
                    }
                    var input=new TranscriptImport.Estimates(tx,samples,counts,abundance,lengths);
                    var result=TranscriptImport.summarize(input,annotation,level.equals("gene")?TranscriptImport.Level.GENE:TranscriptImport.Level.TRANSCRIPT,scaling,idPolicy,
                            o.getOrDefault("unmapped","error").equals("drop")?TranscriptImport.Unmapped.DROP:TranscriptImport.Unmapped.ERROR);
                    for(var e:sources.entrySet())if(!hash(e.getKey()).equals(e.getValue()))throw new IOException("Input changed during import: "+e.getKey());
                    writeMatrix(dir.resolve("results.tsv"),result.estimates().features(),samples,result.estimates().counts());
                    writeMatrix(dir.resolve("abundance.tsv"),result.estimates().features(),samples,result.estimates().abundance());
                    writeMatrix(dir.resolve("length.tsv"),result.estimates().features(),samples,result.estimates().length());
                    var rows=new ArrayList<String[]>();for(var status:result.transcripts())rows.add(new String[]{status.transcript(),status.matchingId(),status.gene(),status.retained()?"retained":"omitted-unmapped"});
                    table(dir.resolve("transcript-status.tsv"),List.of("transcript","matching_id","gene","status"),rows);
                    rows=new ArrayList<>();for(var fallback:result.fallbacks())rows.add(new String[]{fallback.gene(),fallback.sample(),fallback.method(),Double.toString(fallback.length())});
                    table(dir.resolve("length-fallbacks.tsv"),List.of("gene","sample","method","length"),rows);
                    if(needsMap){var used=new HashSet<String>();for(var status:result.transcripts())used.add(status.matchingId());rows=new ArrayList<>();for(var e:annotation.entrySet())rows.add(new String[]{e.getKey(),e.getValue(),used.contains(e.getKey())?"matched":"unused-annotation"});table(dir.resolve("annotation-status.tsv"),List.of("transcript","gene","status"),rows);}
                    var summary=new ArrayList<String[]>();double[][] imported=input.counts(),returned=result.estimates().counts();
                    for(int sample=0;sample<samples.size();sample++){double total=0,retained=0,outputTotal=0;for(int row=0;row<tx.size();row++){total+=imported[row][sample];if(result.transcripts().get(row).retained())retained+=imported[row][sample];}for(double[] row:returned)outputTotal+=row[sample];if(!Double.isFinite(total)||!Double.isFinite(retained)||!Double.isFinite(outputTotal))throw new IOException("Library total exceeds finite range");summary.add(new String[]{samples.get(sample),Double.toString(total),Double.toString(retained),Double.toString(outputTotal)});}
                    table(dir.resolve("library-totals.tsv"),List.of("sample","input_estimated_counts","retained_estimated_counts","output_counts"),summary);
                    long omitted=result.transcripts().stream().filter(s->!s.retained()).count();
                    session.put("input_transcripts",tx.size());session.put("output_features",result.estimates().features().size());session.put("samples",samples);session.put("omitted_transcripts",omitted);session.put("length_fallback_cells",result.fallbacks().size());session.put("rsem_lengths_clamped_to_one",clamped);session.put("duplicate_identical_annotation_rows",duplicateMappings);
                    warnings.add("POINT ESTIMATES ONLY: --inferential-replicates drop explicitly discards bootstrap/Gibbs uncertainty. No quantification or differential-expression test ran. Counts remain fractional estimates; never label them raw observed reads or apply integer rounding silently.");
                    warnings.add(mode.equals("no")?"LENGTH OFFSET ACTION: counts-from-abundance no preserves estimated counts. Downstream gene DE models must use an appropriate effective-length offset/normalization workflow; length.tsv is effective length,not itself a ready-to-use log offset.":"NO DOUBLE LENGTH CORRECTION: counts have been reconstructed from TPM and scaled to the retained estimated library. Do not also supply length.tsv as a length offset for those counts. TPMs themselves are not a negative-binomial count matrix.");
                    warnings.add("REFERENCE/ID ACTION: verify the declared transcriptome and annotation release against quantification provenance. Matching IDs alone cannot prove a shared reference. Gene order is Java String order independent of locale. Review transcript-status.tsv,annotation-status.tsv where present,length-fallbacks.tsv and library-totals.tsv.");
                    if(!policy.equals("exact"))warnings.add("ID TRANSFORMATION: "+policy+" applies only to quantification matching IDs; annotation keys are unchanged. strip-bar also changes transcript output IDs; strip-version leaves transcript output IDs intact. Collisions/conflicting mappings fail rather than selecting a first mapping.");
                    if(omitted>0)warnings.add("TRANSCRIPTS OMITTED: "+omitted+" unannotated transcripts were explicitly dropped. Gene scaling uses retained counts/TPMs,not the full input library. Inspect every omitted ID and lost library total before interpretation.");
                    if(!result.fallbacks().isEmpty())warnings.add("ZERO-ABUNDANCE LENGTH FALLBACK: "+result.fallbacks().size()+" gene/sample lengths use tximport's other-sample geometric mean or all-sample/isoform arithmetic mean. This borrows information across samples; fit the import on the intended cohort and inspect length-fallbacks.tsv.");
                    if(clamped>0)warnings.add("RSEM LENGTH CLAMP: "+clamped+" effective lengths below1 were set to1 following original tximport1.40.0. Inspect the original quantifications.");
                    if(duplicateMappings>0)warnings.add("ANNOTATION DUPLICATES: "+duplicateMappings+" identical transcript/gene rows were deduplicated; conflicting mappings fail.");
                    manifest.put("analysis_status","completed-with-warnings");
                }catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(dir);warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage());}
                var hashes=new LinkedHashMap<String,String>();sources.forEach((path,digest)->hashes.put(path.toString(),digest));session.put("source_sha256",hashes);session.put("warnings",warnings);
                Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
                Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
                Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==0?"TRANSCRIPT IMPORT WARNINGS REQUIRE REVIEW":"TRANSCRIPT IMPORT FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+(code[0]==0?"Review units,length correction,transcriptome/annotation identity,omissions and fallback records before downstream analysis. Retain these point-estimate sources and exact cohort. Native execution needs no R or quantifier runtime.":"Correct the named file/header/ID order,annotation mapping,finite nonnegative estimates,positive lengths or resource limit. For an all-zero scaling denominator use no scaling or resolve the sample explicitly. Rerun into a new directory; partial/ files are failed diagnostics,not successful results.")+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));
            if(code[0]==0)out.println("Native transcript import completed: "+o.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage());return 2;}
    }
    private record Tsv(List<String> header,List<String[]> rows) {
        int column(String name){int index=header.indexOf(name);if(index<0)throw new IllegalArgumentException("Missing quantification column: "+name);return index;}
    }
    private static Tsv read(Path path,int maxBytes,int maxRows)throws IOException {
        if(maxRows<1)throw new IOException("Transcript-by-sample cells exceed --max-cells");
        try(InputStream file=Files.newInputStream(path);InputStream expanded=path.toString().endsWith(".gz")?new GZIPInputStream(file):file;
            var limited=new LimitedStream(expanded,maxBytes);var reader=new BufferedReader(new InputStreamReader(limited,StandardCharsets.UTF_8))) {
            String line=reader.readLine();if(line==null)throw new IOException("Empty TSV: "+path);
            var header=List.of(line.split("\t",-1));if(new HashSet<>(header).size()!=header.size()||header.stream().anyMatch(String::isBlank))throw new IOException("Duplicate/empty TSV header: "+path);
            var rows=new ArrayList<String[]>();while((line=reader.readLine())!=null){if(rows.size()>=maxRows)throw new IOException("TSV row count exceeds --max-cells or declared row limit: "+path);String[] row=line.split("\t",-1);if(row.length!=header.size())throw new IOException("TSV field count mismatch in "+path+" row "+(rows.size()+2));rows.add(row);}return new Tsv(header,rows);
        }
    }
    private static final class LimitedStream extends FilterInputStream {
        private long left;
        LimitedStream(InputStream input,long limit){super(input);left=limit;}
        @Override public int read()throws IOException{int value=super.read();if(value>=0&&--left<0)throw new IOException("Expanded TSV exceeds --max-bytes");return value;}
        @Override public int read(byte[] buffer,int offset,int length)throws IOException{int n=in.read(buffer,offset,(int)Math.min(length,left+1));if(n>0&&(left-=n)<0)throw new IOException("Expanded TSV exceeds --max-bytes");return n;}
    }
    private static Path source(String name,Map<Path,String> hashes,int maxBytes)throws IOException {Path path=Path.of(name).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>maxBytes)throw new IOException("Source missing or exceeds --max-bytes: "+path);String digest=hash(path),prior=hashes.putIfAbsent(path,digest);if(prior!=null&&!prior.equals(digest))throw new IOException("Source changed between uses: "+path);return path;}
    private static String text(String value,String name)throws IOException{if(value.isBlank()||value.chars().anyMatch(Character::isISOControl))throw new IOException(name+" must be nonblank without control characters");return value;}
    private static double number(String text,String kind,int row,Path path)throws IOException{try{double value=Double.parseDouble(text);if(!Double.isFinite(value)||value<0)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("Nonfinite/negative/nonnumeric "+kind+" in "+path+" row "+(row+2),failure);}}
    private static String choice(Map<String,String> o,String key,Set<String> choices){String value=required(o,key);if(!choices.contains(value))throw new IllegalArgumentException("--"+key+" requires "+choices);return value;}
    private static void writeMatrix(Path path,List<String> features,List<String> samples,double[][] values)throws IOException{var headers=new ArrayList<String>();headers.add("feature");headers.addAll(samples);var rows=new ArrayList<String[]>();for(int row=0;row<features.size();row++){String[] fields=new String[samples.size()+1];fields[0]=features.get(row);for(int column=0;column<samples.size();column++)fields[column+1]=Double.toString(values[row][column]);rows.add(fields);}table(path,headers,rows);}
    private static String help(){return """
        Native transcript point-estimate import; tximport1.40.0 arithmetic, no R required.
        transcript-import --samples MANIFEST --format salmon|kallisto|rsem|tsv
          --level gene|transcript --counts-from-abundance no|scaledTPM|lengthScaledTPM|dtuScaledTPM
          --id-policy exact|strip-version|strip-bar --transcriptome-id DECLARED_REFERENCE
          --inferential-replicates drop --out NEW_DIRECTORY
          [--tx2gene MAP] [--unmapped error|drop] [--max-cells 10000000] [--max-bytes 268435456]
        Manifest is unquoted TSV sample/path; relative paths resolve beside the manifest.
        MAP is transcript/gene TSV. Gene output requires MAP and explicit unmapped policy.
        dtuScaledTPM requires transcript output and a complete MAP. It is not gene DE scaling.
        Canonical TSV columns: transcript,counts,abundance,length (TPM,effective length).
        Salmon quant.sf,kallisto abundance.tsv,RSEM isoforms.results and .gz text accepted.
        Every sample must have identical ordered transcript IDs. Fractional estimates retained.
        results.tsv holds counts; abundance.tsv holds TPM; length.tsv holds effective lengths.
        Read WARNINGS.md: reconstructed counts must not also use a length offset.
        Point estimates only; uncertainty draws explicitly dropped. No HDF5,sparse/alevin,
        StringTie,quantification,annotation discovery,DE or historical/full-paper equivalence.
        Failures return2 with recovery and partial diagnostics; no dependency is downloaded.
        """;}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.FumaExpressionPreparation;
import static org.jlinalg.cli.FollowupSupport.*;

/** Stream sample-level expression into documented FUMA heatmap/tissue covariates. */
final class FumaExpressionPrepareCli {
    private FumaExpressionPrepareCli() { }
    private static final Set<String> ALLOWED=Set.of("method","matrix","format","samples","labels","extra-samples","build","annotation-id","expression-id","gene-namespace","units","empty","max-genes","max-samples","max-labels","max-values","max-input-bytes","max-output-bytes","max-owned-bytes","max-line-bytes","out");
    private static final String SCOPE="NATIVE EXPRESSION PREPARATION ONLY: documented raw mean >1 eligibility, cap50, log2(x+1), sample centering and label averages. No DEG generation, t-tests, heatmap clustering, identifier conversion, ancestry/build inference, count-to-TPM normalization, batch correction or historical-resource reconstruction. Required action: supply correctly quantified TPM/RPKM, exact sample/label selections and namespace/release declarations; inspect all gene/sample decisions and threshold boundaries before downstream use.";
    private static void check(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Expression preparation cancelled; no partial resource is admitted");}
    private static String choice(Map<String,String> o,String name,Set<String> allowed){String v=required(o,name);if(!allowed.contains(v))throw new IllegalArgumentException("--"+name+" must be one of "+allowed);return v;}
    private static long positive(Map<String,String> o,String k,long d){long n=Long.parseLong(o.getOrDefault(k,Long.toString(d)));if(n<1)throw new IllegalArgumentException("--"+k+" must be positive");return n;}
    private static void identity(String s){if(s.length()>256||s.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid bounded resource identity");}
    static int run(String[] args,PrintStream out,PrintStream err){
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try{
            Map<String,String> o=options(args,ALLOWED);if(!required(o,"method").equals("prepare-expression"))throw new IllegalArgumentException("--method must be prepare-expression");
            for(String k:List.of("matrix","samples","labels","build","annotation-id","expression-id","gene-namespace","out"))required(o,k);
            for(String k:List.of("build","annotation-id","expression-id","gene-namespace"))identity(o.get(k));
            String format=choice(o,"format",Set.of("tsv","gct12")),units=choice(o,"units",Set.of("tpm","rpkm")),extra=choice(o,"extra-samples",Set.of("error","exclude")),empty=choice(o,"empty",Set.of("error","retain"));
            int maxGenes=integer(o,"max-genes",100000,1,1000000),maxSamples=integer(o,"max-samples",25000,1,1000000),maxLabels=integer(o,"max-labels",1000,2,100000),maxLine=integer(o,"max-line-bytes",8388608,16,67108864);
            long maxInput=positive(o,"max-input-bytes",17179869184L),maxOutput=positive(o,"max-output-bytes",1073741824),maxValues=positive(o,"max-values",2000000000),owned=positive(o,"max-owned-bytes",268435456);
            var limits=new FumaExpressionPreparation.Limits(maxSamples,maxLabels,owned);
            int[] code={0};transaction(o,"fuma-prepare-expression",(directory,manifest)->{
                Map<String,Object> session=new LinkedHashMap<>();Map<Path,String> sources=new LinkedHashMap<>();List<String> warnings=new ArrayList<>(List.of(SCOPE));
                try{
                    long metadataBound=Math.min(16777216,Math.min(owned/8,Runtime.getRuntime().maxMemory()/16));
                    var labels=labels(Path.of(o.get("labels")),maxLabels,metadataBound,sources);
                    var samples=samples(Path.of(o.get("samples")),maxSamples,metadataBound,sources);
                    Path matrix=Path.of(o.get("matrix")).toAbsolutePath().normalize();pin(matrix,maxInput,sources);
                    int effectiveLine=(int)Math.min(maxLine,Math.min(owned/16,Runtime.getRuntime().maxMemory()/16));
                    var output=new Output(directory,maxOutput);long rows=0,retained=0,capped=0,nearBoundary=0,extremeRaw=0,geneChars=0;long declaredRows=-1;int declaredSamples=-1;
                    try(var input=new Lines(matrix,effectiveLine,maxInput);var log=output.open("log2.tsv");var norm=output.open("norm.tsv");var properties=output.open("gene-covariates.tsv");var audit=output.open("gene-audit.tsv")){
                        String header=input.next();if(format.equals("gct12")){
                            if(!"#1.2".equals(header))throw new IOException("GCT input requires exact #1.2 version");String dimensions=input.next();if(dimensions==null)throw new IOException("Missing GCT dimensions");String[] shape=dimensions.split("\t",-1);if(shape.length!=2)throw new IOException("GCT requires two integer dimensions");declaredRows=Long.parseLong(shape[0]);declaredSamples=Integer.parseInt(shape[1]);if(declaredRows<0||declaredRows>maxGenes||declaredSamples<1||declaredSamples>maxSamples||Math.multiplyExact(declaredRows,declaredSamples)>maxValues)throw new IOException("Complete GCT dimensions exceed gene/sample/value capacity");header=input.next();
                        }
                        if(header==null)throw new IOException("Missing expression matrix header");int offset=format.equals("gct12")?2:1;String[] fields=header.split("\t",-1);
                        if(fields.length<=offset||!fields[0].equals(format.equals("gct12")?"Name":"gene")||offset==2&&!fields[1].equals("Description"))throw new IOException("Expected gene<TAB>samples TSV header or Name<TAB>Description<TAB>samples GCT header");
                        List<String> inputSamples=List.copyOf(Arrays.asList(fields).subList(offset,fields.length));if(declaredSamples>=0&&inputSamples.size()!=declaredSamples)throw new IOException("GCT sample dimension differs from header");
                        var frame=FumaExpressionPreparation.frame(inputSamples,samples,labels,extra.equals("exclude"),limits,FumaExpressionPrepareCli::check);
                        long base=FumaExpressionPreparation.estimatedBytes(inputSamples.size(),labels.size());admit(Math.addExact(base,8L*header.length()),owned);
                        sampleAudit(frame,output);
                        line(log,append(List.of("ensg"),labels));line(norm,append(List.of("ensg"),labels));line(properties,append(append(List.of("GENE"),labels),List.of("Average")));
                        line(audit,append(List.of("gene","description","disposition","capped_selected_values","sample_weighted_log2_center","unweighted_label_mean_log2"),labels.stream().map(s->"raw_mean:"+s).toList()));
                        Set<String> genes=new HashSet<>();
                        for(String text;(text=input.next())!=null;){
                            check();if(++rows>maxGenes||Math.multiplyExact(rows,inputSamples.size())>maxValues)throw new IOException("Complete expression gene/value capacity exceeded; no prefix resource is admitted");
                            admit(Math.addExact(base,Math.addExact(Math.multiplyExact(rows,512),Math.multiplyExact(8L,text.length()))),owned);
                            String[] r=text.split("\t",-1);if(r.length!=fields.length)throw new IOException("Expression row width differs at data row "+rows);geneChars=Math.addExact(geneChars,r[0].length());admit(Math.addExact(base,Math.addExact(Math.multiplyExact(rows,512),Math.addExact(Math.multiplyExact(geneChars,2),Math.multiplyExact(8L,text.length())))),owned);if(!genes.add(r[0]))throw new IOException("Duplicate exact expression gene ID: "+r[0]);String description=offset==2?r[1]:".";if(description.length()>16384||description.indexOf('\0')>=0||description.indexOf('\r')>=0)throw new IOException("Invalid expression description");
                            double[] raw=new double[inputSamples.size()];for(int i=0;i<raw.length;i++){if((i&255)==0)check();raw[i]=Double.parseDouble(r[i+offset]);}
                            var result=FumaExpressionPreparation.prepare(r[0],raw,frame,FumaExpressionPrepareCli::check);capped=Math.addExact(capped,result.cappedSelectedValues());
                            for(double value:result.rawMeans())if(Math.abs(value-1)<=1e-12){nearBoundary++;break;}
                            for(double value:result.rawMeans())if(value>=1e300){extremeRaw++;break;}
                            List<String> decisions=new ArrayList<>(List.of(result.gene(),description,result.disposition().name(),""+result.cappedSelectedValues(),""+result.sampleMeanLog2(),""+result.labelMeanLog2()));for(double v:result.rawMeans())decisions.add(Double.toString(v));line(audit,decisions);
                            if(result.disposition()==FumaExpressionPreparation.Disposition.RETAINED){retained++;line(log,values(result.gene(),result.log2Means(),null));line(norm,values(result.gene(),result.centeredMeans(),null));line(properties,values(result.gene(),result.log2Means(),result.labelMeanLog2()));}
                        }
                        if(declaredRows>=0&&declaredRows!=rows)throw new IOException("GCT declared gene count differs from complete observed rows");
                        session.put("input_samples",inputSamples.size());session.put("selected_samples",frame.selectedSamples());session.put("label_order",labels);session.put("label_sample_counts",Arrays.stream(frame.labelCounts()).boxed().toList());
                    }
                    if(retained==0&&empty.equals("error"))throw new IOException("No gene passed strict raw mean >1 in any selected label; review gene-audit.tsv or explicitly choose --empty retain");
                    for(var e:sources.entrySet()){check();if(!Files.isRegularFile(e.getKey(),LinkOption.NOFOLLOW_LINKS)||!hash(e.getKey()).equals(e.getValue()))throw new IOException("Input changed during complete expression preparation: "+e.getKey());}
                    session.put("input_gene_rows",rows);session.put("retained_gene_rows",retained);session.put("excluded_gene_rows",rows-retained);session.put("capped_selected_values",capped);session.put("raw_mean_boundary_rows",nearBoundary);session.put("extreme_raw_mean_rows",extremeRaw);session.put("payload_bytes",output.used);session.put("units",units);session.put("options",o);
                    warnings.add("TWO DIFFERENT AVERAGES: norm.tsv centers each gene over SELECTED SAMPLES before averaging by label. gene-covariates.tsv Average is the UNWEIGHTED mean of LABEL means, as in the documented MAGMA model. These differ for unequal label sample sizes. Required action: retain label-samples.tsv and do not substitute a sample-weighted center as the MAGMA Average covariate.");
                    warnings.add("GENE ELIGIBILITY AND NAMESPACE: "+retained+" of "+rows+" genes passed strict RAW mean >1 in at least one selected label. The criterion precedes capping/logging; excluded genes remain in gene-audit.tsv. Version suffixes and case are preserved. IDs in gene-covariates.tsv must match the separately fitted MAGMA gene bundle; no Ensembl/Entrez conversion or historical gene universe is inferred. Required action: inspect exclusions and provide the intended annotation/namespace mapping before fitting.");
                    if(nearBoundary>0)warnings.add("RAW-MEAN BOUNDARY REVIEW: "+nearBoundary+" gene rows have a label mean within1e-12 of1. Arithmetic uses stable scaled means; a rounding difference from a historical preprocessing implementation can alter a strict >1 decision. Required action: inspect gene-audit.tsv and compare the intended exact resource before claiming historical membership parity; do not reinterpret >1 as >=1.");
                    if(extremeRaw>0)warnings.add("EXTREME RAW EXPRESSION: "+extremeRaw+" gene rows have a label mean >=1e300. Stable scaled means remain finite; independent base-R controls at maximum-double inputs overflow some raw means. This is an explicit numerical departure, not historical floating-point parity. Required action: check the source measurement units and raw gene rows before biological use; do not treat capping/logging as a repair of invalid quantification.");
                    if(retained==0)warnings.add("EMPTY PREPARED RESOURCE: explicitly retained zero admitted genes. Required action: inspect all exclusions; an empty matrix is not evidence of absent biological expression and cannot support a fitted MAGMA property analysis.");
                    manifest.put("analysis_status",retained==0?"completed-empty-with-review-warnings":"completed-with-review-warnings");
                }catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(directory);Files.createDirectories(directory.resolve("partial"));warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and retained partial/ diagnostics, correct the named matrix/selection/identity/numerical/capacity cause and rerun the COMPLETE request to a new directory. No partial expression resource is admitted.");}
                Map<String,String> pins=new LinkedHashMap<>();sources.forEach((p,h)->pins.put(p.toString(),h));manifest.put("input_sha256",pins);session.put("input_sha256",pins);session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# FUMA EXPRESSION PREPARATION "+(code[0]==0?"WARNINGS REQUIRE REVIEW":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Native sample-level FUMA expression preparation completed: "+o.get("out"));return code[0];
        }catch(Exception failure){err.println("ANALYSIS FAILED: "+failure.getMessage()+"\nRequired action: correct the named options/files and choose a new writable --out directory.\n"+SCOPE);return 2;}
    }
    private static List<String> append(List<String> a,List<String> b){var r=new ArrayList<>(a);r.addAll(b);return r;}
    private static List<String> values(String gene,double[] v,Double average){var r=new ArrayList<String>();r.add(gene);for(double n:v)r.add(Double.toString(n));if(average!=null)r.add(Double.toString(average));return r;}
    private static void line(OutputStream output,List<String> values)throws IOException{output.write((String.join("\t",values)+"\n").getBytes(StandardCharsets.UTF_8));}
    private static void pin(Path p,long maximum,Map<Path,String> sources)throws IOException{p=p.toAbsolutePath().normalize();if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>maximum)throw new IOException("Missing/nonregular or oversized expression input: "+p);String h=hash(p),old=sources.putIfAbsent(p,h);if(old!=null&&!old.equals(h))throw new IOException("Expression input changed before reading: "+p);}
    private static List<String[]> metadata(Path p,String header,int width,int rows,long bytes,Map<Path,String> sources)throws IOException{
        p=p.toAbsolutePath().normalize();pin(p,bytes,sources);byte[] raw=AdmixtureInputs.raw(p,bytes);if(!AdmixtureInputs.digest(raw).equals(sources.get(p)))throw new IOException("Expression metadata changed during parsing");var lines=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString().lines().toList();if(lines.isEmpty()||!lines.get(0).equals(header))throw new IOException("Expected exact TSV header: "+header);var result=new ArrayList<String[]>();for(int i=1;i<lines.size();i++){String[] r=lines.get(i).split("\t",-1);if(r.length!=width||result.size()>=rows)throw new IOException("Expression metadata width/row capacity exceeded");result.add(r);}return result;
    }
    private static List<String> labels(Path p,int maximum,long bytes,Map<Path,String> sources)throws IOException{var result=new ArrayList<String>();for(var r:metadata(p,"label",1,maximum,bytes,sources)){if(Set.of("Average","GENE","gene","ensg").contains(r[0]))throw new IOException("Reserved output-column label; explicitly provide a distinct label: "+r[0]);result.add(r[0]);}return result;}
    private static List<FumaExpressionPreparation.Sample> samples(Path p,int maximum,long bytes,Map<Path,String> sources)throws IOException{var result=new ArrayList<FumaExpressionPreparation.Sample>();for(var r:metadata(p,"sample\tlabel",2,maximum,bytes,sources))result.add(new FumaExpressionPreparation.Sample(r[0],r[1]));return result;}
    private static void sampleAudit(FumaExpressionPreparation.Frame f,Output output)throws IOException{int[] indices=f.selectedIndices(),groups=f.selectedLabelIndices(),counts=f.labelCounts();Map<Integer,String> selected=new LinkedHashMap<>();for(int i=0;i<indices.length;i++)selected.put(indices[i],f.labels().get(groups[i]));try(var s=output.open("sample-selection.tsv")){line(s,List.of("input_index","sample","selected","label"));for(int i=0;i<f.inputSamples().size();i++)line(s,List.of(""+i,f.inputSamples().get(i),""+selected.containsKey(i),selected.getOrDefault(i,"NA")));}try(var s=output.open("label-samples.tsv")){line(s,List.of("label","samples"));for(int i=0;i<counts.length;i++)line(s,List.of(f.labels().get(i),""+counts[i]));}}
    private static void admit(long bytes,long declared){if(bytes>declared||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete expression stream storage estimate exceeds --max-owned-bytes or half the heap");}
    private static final class Output {
        final Path directory;final long maximum;long used;
        Output(Path directory,long maximum){this.directory=directory;this.maximum=maximum;}
        OutputStream open(String name)throws IOException{OutputStream file=new BufferedOutputStream(Files.newOutputStream(directory.resolve(name),StandardOpenOption.CREATE_NEW));return new OutputStream(){private void admit(int n)throws IOException{check();if(n>maximum-used)throw new IOException("Complete expression output exceeds --max-output-bytes");used+=n;}public void write(int n)throws IOException{admit(1);file.write(n);}public void write(byte[] b,int offset,int size)throws IOException{admit(size);file.write(b,offset,size);}public void close()throws IOException{file.close();}};}
    }
    private static final class Lines implements AutoCloseable {
        final BufferedInputStream input;final int maximum;final long totalMaximum;long consumed;
        Lines(Path p,int maximum,long totalMaximum)throws IOException{input=new BufferedInputStream(Files.newInputStream(p));this.maximum=maximum;this.totalMaximum=totalMaximum;}
        private int read()throws IOException{int n=input.read();if(n!=-1&&++consumed>totalMaximum)throw new IOException("Complete expression stream exceeds --max-input-bytes");return n;}
        String next()throws IOException{var out=new ByteArrayOutputStream();int n;while((n=read())!=-1&&n!='\n'){if((out.size()&65535)==0)check();if(out.size()>=maximum)throw new IOException("Expression effective line-byte capacity exceeded: "+maximum+" (declared budget/heap sublimits apply)");out.write(n);}if(n==-1&&out.size()==0)return null;byte[] raw=out.toByteArray();int size=raw.length;if(size>0&&raw[size-1]=='\r')size--;return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,size)).toString();}
        public void close()throws IOException{input.close();}
    }
    private static String help(){return """
        Native sample-level expression preparation (documented FUMA transformations; Java only).
          fuma-tools --method prepare-expression --matrix INPUT --format tsv|gct12
            --samples samples.tsv --labels labels.tsv --extra-samples error|exclude
            --build GRCh37 --annotation-id DECLARED_RELEASE --expression-id DECLARED_DATASET
            --gene-namespace EXACT_NAMESPACE --units tpm|rpkm --empty error|retain --out NEW_DIRECTORY
        Matrix TSV: gene<TAB>sample1<TAB>sample2...; GCT1.2: #1.2, exact dimensions,
          Name<TAB>Description<TAB>samples. Plain UTF-8 files; no automatic decompression.
        Samples: exact sample<TAB>label header; labels: label header, in intended output order.
        Sample metadata is an explicit selection; unknown/duplicate IDs or empty labels fail.
        Raw finite nonnegative values required in EVERY input column, including excluded samples.
        Gene eligibility: raw mean >1 in at least one selected label, before any transformation.
        Transform selected sample values: replace x>50 with50; log2(x+1); average within labels.
        Normalized resource centers each gene over selected SAMPLES before label averaging.
        MAGMA Average is a separate UNWEIGHTED mean of label log2 means; no ID conversion.
        Outputs: log2.tsv, norm.tsv, gene-covariates.tsv, complete gene/sample decision audits.
        No DEG generation, t-test, clustering, count normalization or historical-resource parity.
        Limits: --max-genes 100000 --max-samples 25000 --max-labels 1000 --max-values 2000000000
          --max-input-bytes 17179869184 --max-output-bytes 1073741824
          --max-owned-bytes 268435456 --max-line-bytes 8388608.
        Rows stream; complete family/value/output limits apply, not prefix truncation.
        Read WARNINGS.md and every audit. Exit0 completed with review warnings;2 failed.
        Failed output is retained in partial/; fix the named cause and rerun to a new directory.
        """;}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.TranscriptSiteMapping;
import org.jlinalg.genetics.TranscriptSiteMapping.Boundary;
import org.jlinalg.genetics.TranscriptSiteMapping.Limits;
import org.jlinalg.genetics.TranscriptSiteMapping.Missing;
import org.jlinalg.genetics.TranscriptSiteMapping.Options;
import org.jlinalg.genetics.TranscriptSiteMapping.Site;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed native transcript-site workflow with complete provenance, populations and failure records. */
final class TranscriptSiteCli {
    private TranscriptSiteCli() { }
    private static final Set<String> ALLOWED=Set.of("method","site-options-file","analysis-id","variants","transcripts","expected-variant-rows","expected-transcript-rows","variants-sha256","transcripts-sha256","build","reference-id","reference-build","gene-namespace","transcript-namespace","variant-namespace","context-namespace","p-cutoff","radius-bp","boundary","sites","gene-biotypes","missing-p","max-bytes","max-rows","max-line-bytes","max-cells","max-snapshot-bytes","max-output-bytes","max-output-rows","max-hits","max-operations","timeout","out");
    private static final List<String> REQUIRED=List.of("analysis-id","variants","transcripts","expected-variant-rows","expected-transcript-rows","build","reference-id","reference-build","gene-namespace","transcript-namespace","variant-namespace","context-namespace","p-cutoff","radius-bp","boundary","sites","gene-biotypes","missing-p","out");
    private static final String SCOPE="SUPPLIED TRANSCRIPT-SITE MAPPING ONLY. Required action: verify the exact assembly,transcript resource,ID/context namespaces,gene biotypes,one-based closed coordinates,strand,complete input populations and every cutoff/site/radius/boundary/exclusion choice. Gene-body windows are a distinct model. No resource completeness,gene causality,annotation acquisition,historical FAVOR/FUMA or whole-paper reproduction is certified. No external runtime,network or installation is used.";
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        if(Arrays.asList(args).contains("--help")){stdout.println(help());return 0;}
        try {
            var o=parse(args);if(o.containsKey("method")&&!o.get("method").equals("transcript-sites"))throw new IOException("--method must be transcript-sites");
            for(String key:REQUIRED)required(o,key);
            int[] code={0};externalTransaction(o,"transcript-site-mapping",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();warnings.add(SCOPE);TranscriptSiteInputs input=null;String phase="options and resource admission";
                try {
                    for(String key:List.of("analysis-id","build","reference-id","reference-build","gene-namespace","transcript-namespace","variant-namespace","context-namespace")){TranscriptSiteInputs.identity(o.get(key));session.put(key,o.get(key));}
                    long started=System.nanoTime(),timeout=integer(o,"timeout",300,1,86400);
                    Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-started>timeout*1000000000L)throw new IllegalStateException("Transcript-site interrupted or --timeout exceeded;review complete populations/capacities and rerun to a NEW output");};
                    var limits=new FumaAssociationInputs.Limits(integer(o,"max-bytes",268435456,1,1073741824),integer(o,"max-rows",1000000,1,10000000),integer(o,"max-line-bytes",1048576,64,16777216),capacity(o,"max-cells",50000000,500000000),capacity(o,"max-snapshot-bytes",1073741824,1099511627776L),capacity(o,"max-output-bytes",1073741824,1099511627776L),capacity(o,"max-output-rows",5000000,100000000));
                    for(String key:List.of("expected-variant-rows","expected-transcript-rows"))if(TranscriptSiteInputs.whole(o.get(key))<1)throw new IOException("--"+key+" must declare a positive complete input population");
                    long hits=TranscriptSiteInputs.whole(o.getOrDefault("max-hits","2000000"));if(hits>100000000)throw new IOException("--max-hits exceeds supported capacity");
                    var sites=new LinkedHashSet<Site>();for(String value:selection(o.get("sites")))sites.add(Site.valueOf(value));
                    var model=new Options(o.get("build"),TranscriptSiteInputs.decimal(o.get("p-cutoff")),TranscriptSiteInputs.whole(o.get("radius-bp")),Boundary.valueOf(o.get("boundary")),sites,selection(o.get("gene-biotypes")),Missing.valueOf(o.get("missing-p")),new Limits(limits.rows(),Math.toIntExact(hits),capacity(o,"max-operations",100000000,10000000000L)));
                    input=new TranscriptSiteInputs(directory,limits,check);
                    if(o.containsKey("site-options-file")){Path captured=input.transport.capture(Path.of(o.get("site-options-file")),"transcript-site options carrier",1048576);if(!hash(captured).equals(o.get("options-sha256")))throw new IOException("Transcript-site options carrier changed after parsing");}
                    phase="streamed complete variant and transcript populations";var data=input.prepare(o);input.verify();
                    for(String key:List.of("variants","transcripts"))if(o.containsKey(key+"-sha256")){String expected=o.get(key+"-sha256");if(!expected.matches("[0-9a-f]{64}")||!expected.equals(input.transport.hashes.get(Path.of(o.get(key)).toAbsolutePath().normalize())))throw new IOException("Pinned --"+key+"-sha256 differs from the exact input snapshot");}
                    phase="complete native transcript-site mapping";var result=TranscriptSiteMapping.map(data.variants(),data.reference(),model,check);warnings.addAll(result.warnings());
                    phase="complete mapping publication";publish(directory,input,o,model,result);
                    phase="final original/snapshot verification";input.verify();
                    session.put("p_cutoff_decimal",model.pCutoff().toString());session.put("radius_bp",model.radiusBp());session.put("boundary",model.boundary().toString());session.put("sites",orderedSites(model));session.put("gene_biotypes",model.geneBiotypes().stream().sorted().toList());session.put("missing_p",model.missing().toString());session.put("variant_rows",result.variants().size());session.put("transcript_rows",result.transcripts().size());session.put("site_hits",result.hits().size());session.put("unique_variant_gene_mappings",result.genes().size());session.put("mapping_operations",result.operations());session.put("input_cells",input.cells());session.put("snapshot_bytes",input.transport.snapshotBytes());session.put("scientific_files_and_snapshot_bytes",input.transport.output.bytes());session.put("scientific_rows",input.transport.output.rows());session.put("status","completed-with-review-warnings");manifest.put("analysis_status","completed-with-review-warnings");
                } catch(Exception failure) {
                    code[0]=2;session.put("status","failed");session.put("cause",failure.toString());session.put("failed_phase",phase);if(input!=null)session.put("last_input_context",input.context());session.put("retained_files_location","partial/");manifest.put("analysis_status","failed");manifest.put("error",failure.toString());RnaToolRuntime.quarantine(directory);Files.createDirectories(directory.resolve("partial"));
                    warnings.add(0,"TRANSCRIPT-SITE MAPPING FAILED during "+phase+": "+failure.getMessage()+". Required action: inspect the named option,input,coordinate,population,model or capacity cause and retained partial/ diagnostics;correct it and rerun to a NEW output. No complete result is admitted.");
                    stderr.println("TRANSCRIPT-SITE MAPPING FAILED during "+phase+": "+failure.getMessage()+"\nRequired action: inspect WARNINGS.md and partial/ in the requested output;correct this cause and rerun to a NEW output directory.");
                }
                var sources=new LinkedHashMap<String,String>();if(input!=null)input.transport.hashes.forEach((path,digest)->sources.put(path.toString(),digest));manifest.put("source_sha256",sources);session.put("source_sha256",sources);session.put("external_runtime_required",false);session.put("historical_defaults_inferred",false);session.put("warnings",warnings);
                Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",session.get("status"),"exit_code",code[0]))+"\n");
                Files.writeString(directory.resolve("WARNINGS.md"),"# TRANSCRIPT-SITE MAPPING "+(code[0]==0?"REVIEW REQUIRED":"FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect every original variant/transcript disposition,site hit and unique gene mapping. Preserve raw input snapshots and their hashes. Correct failures and rerun to a NEW output. No analysis installs software. If Java or a separately required upstream runtime is unavailable or forbidden,SKIP the affected analysis and provide that runtime on a permitted host.\n");
            });
            stderr.println((code[0]==0?"TRANSCRIPT-SITE MAPPING REVIEW REQUIRED: ":"TRANSCRIPT-SITE MAPPING FAILED: ")+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)stdout.println("Transcript-site mapping completed: "+o.get("out"));return code[0];
        } catch(Exception failure){stderr.println("TRANSCRIPT-SITE MAPPING FAILED: "+failure.getMessage()+"\nRequired action: correct the named options/path/output cause and rerun to a NEW output directory. Existing outputs remain intact.\n"+SCOPE);return 2;}
    }
    private static void publish(Path directory,TranscriptSiteInputs input,Map<String,String> o,Options model,TranscriptSiteMapping.Result result)throws Exception {
        var output=input.transport.output;
        output.table(directory.resolve("mapping-policy.tsv"),List.of("analysis_id","variant_namespace","context_namespace","variant_build","reference_id","reference_build","gene_namespace","transcript_namespace","p_cutoff_decimal","radius_bp","boundary","sites","gene_biotypes","missing_p","expected_variant_rows","expected_transcript_rows"),1,i->fields(o.get("analysis-id"),o.get("variant-namespace"),o.get("context-namespace"),o.get("build"),o.get("reference-id"),o.get("reference-build"),o.get("gene-namespace"),o.get("transcript-namespace"),model.pCutoff(),model.radiusBp(),model.boundary(),String.join(",",orderedSites(model)),String.join(",",model.geneBiotypes().stream().sorted().toList()),model.missing(),o.get("expected-variant-rows"),o.get("expected-transcript-rows")),true);
        output.table(directory.resolve("variant-dispositions.tsv"),List.of("variant_row","context","variant_id","chromosome","position","p_decimal","status","site_hits","transcript_hits","gene_hits","nearest_hit_distance_bp"),result.variants().size(),i->{var row=result.variants().get(i);var v=row.variant();return fields(row.row(),v.context(),v.id(),v.chromosome(),v.position(),v.p(),row.status(),row.siteHits(),row.transcripts(),row.genes(),row.nearestHitDistanceBp());},true);
        output.table(directory.resolve("transcript-dispositions.tsv"),List.of("transcript_row","gene_id","transcript_id","chromosome","start","end","strand","gene_biotype","tss","tes","selected_biotype","requested_tss","requested_tes","tss_hits","tes_hits"),result.transcripts().size(),i->{var row=result.transcripts().get(i);var t=row.transcript();return fields(row.row(),t.gene(),t.transcript(),t.chromosome(),t.start(),t.end(),t.strand(),t.geneBiotype(),t.tss(),t.tes(),row.selectedBiotype(),model.sites().contains(Site.TSS),model.sites().contains(Site.TES),row.tssHits(),row.tesHits());},true);
        output.table(directory.resolve("site-hits.tsv"),List.of("variant_row","transcript_row","context","variant_id","gene_id","transcript_id","chromosome","site","site_position","strand","signed_distance_bp","distance_bp"),result.hits().size(),i->{var hit=result.hits().get(i);return fields(hit.variantRow(),hit.transcriptRow(),hit.variant().context(),hit.variant().id(),hit.transcript().gene(),hit.transcript().transcript(),hit.variant().chromosome(),hit.site(),hit.sitePosition(),hit.transcript().strand(),hit.signedDistanceBp(),hit.distanceBp());},true);
        output.table(directory.resolve("gene-mappings.tsv"),List.of("variant_row","context","variant_id","gene_id","transcript_hits","site_hits","nearest_hit_distance_bp"),result.genes().size(),i->{var row=result.genes().get(i);return fields(row.variantRow(),row.variant().context(),row.variant().id(),row.gene(),row.transcripts(),row.siteHits(),row.nearestHitDistanceBp());},true);
    }
    private static List<String> orderedSites(Options o){return Arrays.stream(Site.values()).filter(o.sites()::contains).map(Enum::name).toList();}
    private static String[] fields(Object... values){String[] out=new String[values.length];for(int i=0;i<out.length;i++)out[i]=values[i]==null?"NA":values[i].toString();return out;}
    private static Set<String> selection(String value)throws IOException {var out=new LinkedHashSet<String>();for(String item:value.split(",",-1))if(!out.add(TranscriptSiteInputs.identity(item)))throw new IOException("Require distinct comma-separated transcript-site selections");return out;}
    private static long capacity(Map<String,String> o,String key,long fallback,long maximum)throws IOException {long value=TranscriptSiteInputs.whole(o.getOrDefault(key,Long.toString(fallback)));if(value<1||value>maximum)throw new IOException("Unsupported positive --"+key+" capacity");return value;}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("site-options-file"))return direct;
        Path path=Path.of(direct.get("site-options-file")).toAbsolutePath().normalize();byte[] raw=FumaAssociationInputs.carrier(path);var result=new LinkedHashMap<String,String>();
        for(var e:AdmixtureInputs.decode(raw).entrySet()){String key=e.getKey().toString();if(!ALLOWED.contains(key)||key.equals("site-options-file")||!(e.getValue() instanceof String value))throw new IOException("Unknown,recursive or non-string transcript-site option: "+key);result.put(key,Set.of("variants","transcripts","out").contains(key)?path.getParent().resolve(value).normalize().toString():value);}
        result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    static String help(){return """
        fuma-tools --method transcript-sites [--site-options-file OPTIONS.json]
        Required: --analysis-id ID --variants VARIANTS.tsv --transcripts TRANSCRIPTS.tsv
          --expected-variant-rows N --expected-transcript-rows N --out NEW_DIRECTORY
          --build BUILD --reference-id ID --reference-build BUILD --gene-namespace ID
          --transcript-namespace ID --variant-namespace ID --context-namespace ID
          --p-cutoff DECIMAL --radius-bp NONNEGATIVE_INTEGER --boundary INCLUSIVE|STRICT
          --sites TSS|TES|TSS,TES --gene-biotypes COMMA_SEPARATED --missing-p ERROR|EXCLUDE
        Variants: context,variant_id,chromosome,position,p. Only literal NA is missing P.
          P<=cutoff is exact decimal,including positive values below binary64 range.
        Transcripts: gene_id,transcript_id,chromosome,start,end,strand,gene_biotype.
          Coordinates are one-based closed;strand is + or -. Gene interiors are not sites.
          Every transcript ID is unique;each gene has consistent chromosome/strand/biotype.
        Strict plain UTF-8 TSV;no quoting,aliases,transcript selection or coordinate conversion.
          Additional columns remain in exact raw snapshots. All input rows are admitted or
          the complete analysis fails. Every original row,site hit and gene mapping is retained.
        Optional --variants-sha256 and --transcripts-sha256 require exact lowercase SHA-256.
          JSON options are string values;relative data/output paths resolve beside that file.
          Direct flags override JSON. Use an ASCII JSON carrier path for Windows JVM argv.
        Limits: --max-bytes --max-rows --max-line-bytes --max-cells --max-snapshot-bytes
          --max-output-bytes --max-output-rows --max-hits --max-operations --timeout.
          max-rows is combined variants+transcripts;max-hits may be zero. Bounds never truncate.
        Exit0 completed with review warnings;2 invalid options/input/model/budget/publication.
          Inspect WARNINGS.md and retained partial/ diagnostics;rerun to a NEW output.
          Java is the only runtime. If unavailable/forbidden,skip this analysis and provide
          Java17+ on a permitted host;no command silently installs software.
        """+SCOPE;
    }
}

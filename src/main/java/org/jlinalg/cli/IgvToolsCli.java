/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Typed local views rendered by original IGV with native complete-value verification. */
final class IgvToolsCli {
    private IgvToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("recipe","home","out","max-bytes","max-records","timeout","options-file");
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};externalTransaction(options,"original-igv-local-audited-views",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("requested_options",options);
                try {
                    int bytes=integer(options,"max-bytes",536870912,1024,Integer.MAX_VALUE),records=integer(options,"max-records",1_000_000,1,2_000_000),timeout=integer(options,"timeout",300,1,3600);Path recipe=Path.of(required(options,"recipe")).toAbsolutePath().normalize();IgvInputs.Model model=IgvInputs.read(recipe,directory,bytes,records);Files.copy(recipe,directory.resolve("supplied-recipe.json"));warnings.addAll(model.warnings());session.put("inputs",model.provenance());session.put("assembly_declaration",model.assembly());
                    String supplied=options.get("home");if(supplied==null||supplied.isBlank())throw new IgvRuntime.Unavailable("No admitted original IGV --home supplied;analysis never installs dependencies");IgvRuntime runtime=new IgvRuntime(Path.of(supplied),session);runtime.probe(directory);var request=model.worker();request.put("output_directory",directory.toString());Path workerRecipe=directory.resolve("worker-request.json");Files.writeString(workerRecipe,RegenieRuntime.json(request)+"\n",StandardCharsets.UTF_8);String recipeHash=hash(workerRecipe);int exit=runtime.execute(workerRecipe,directory,"igv-render",timeout);model.verify();runtime.verify();if(!hash(workerRecipe).equals(recipeHash))throw new IOException("Native-generated IGV request changed during execution");if(exit!=0)throw new IOException("Original IGV returned"+exit+";inspect retained igv-render.stderr");
                    var audit=IgvAudit.verify(model,directory);Files.writeString(directory.resolve("native-audit.json"),RegenieRuntime.json(audit)+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("FIGURE_LEGEND.md"),legend(model),StandardCharsets.UTF_8);var results=new LinkedHashMap<String,Object>();results.put("status","completed-with-review-warnings");results.put("igv_version","2.19.8");results.put("views",model.views().stream().map(v->Map.of("id",v.id(),"png",v.id()+".png","session",v.id()+".session.xml","coordinates",v.contig()+":"+v.start()+"-"+v.end()+" zero-based half-open")).toList());results.put("native_audit_sha256",hash(directory.resolve("native-audit.json")));results.put("figure_legend_sha256",hash(directory.resolve("FIGURE_LEGEND.md")));Files.writeString(directory.resolve("results.json"),RegenieRuntime.json(results)+"\n",StandardCharsets.UTF_8);session.put("results",results);
                    warnings.add("SCIENTIFIC REVIEW REQUIRED: original IGV figures are manual inspection evidence. This profile creates no variant calls,independent-molecule/allele-specific estimates or methylation calls. BAM MM/ML and phase tags are retained/audited as raw tags;modification-color interpretation is not implemented. Original SEG automatic CNA cohort-frequency summaries and P086 custom synteny summaries are separate unfinished work. Source papers' unstated filters and historical runtime versions are not inferred. Required action: read FIGURE_LEGEND.md,native-audit.json,original-igv.json and original logs;review the full image,coordinate convention,units,display range,read selection and missing regions before scientific use.");
                    for(String stage:List.of("igv-probe","igv-render")){Path log=directory.resolve(stage+".stderr");if(Files.size(log)>8388608)throw new IOException("Original IGV diagnostic log exceeds8MiB");if(Files.size(log)>0)warnings.add("ORIGINAL IGV DIAGNOSTICS: inspect "+stage+".stderr. Disabling cloud provisioning can log a Google OAuth provider error;the original SLF4J binding can report its fallback. Native success requires zero attempted URL requests and a complete scientific audit;these startup diagnostics are retained.");}
                    if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("recipe-sha256")))throw new IOException("CLI options recipe changed during execution");model.verify();runtime.verify();manifest.put("analysis_status","completed-with-review-warnings");
                }catch(IgvRuntime.Unavailable unavailable){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency");session.put("error",unavailable.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS SKIPPED: "+unavailable.getMessage());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage());}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("WARNINGS.md"),"# IGV "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+IgvRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Original IGV views and native complete-value audit completed:"+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg:"+failure.getMessage()+"\n"+IgvRuntime.recovery());return 2;}
    }
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require a UTF8 JSON options object at most1MiB");byte[] raw=Files.readAllBytes(path);Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(value instanceof Map<?,?> map))throw new IOException("Options must be a JSON object");var result=new LinkedHashMap<String,String>();for(var entry:map.entrySet()){String key=String.valueOf(entry.getKey());if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string option");result.put(key,text);}result.putAll(direct);result.put("recipe-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));return result;
    }
    static String legend(IgvInputs.Model model) {
        var text=new StringBuilder("# Original IGV figure legend and required scientific review\n\nAssembly declaration: ").append(model.assembly()).append("\n\nRecipe and audit intervals use zero-based half-open coordinates. Original goto commands use start+1 through end;original session XML stores start through end. The original ruler can print601bp for an actual0..600 frame:use native-audit.json coordinates,not the ruler's length label. Reference names/lengths match exactly;no alias or liftover is inferred. Original PNG pixels are retained unmodified;the accompanying legend supplies units and policies. Saved original sessions use relative inputs paths and the explicitly staged adjacent BAM index. Execution ledger paths record the temporary run;the published inputs and sessions travel together. Reopened interactive sessions can use different global preferences:these PNGs and the audited ledger are the verified output.\n\n|Track|Kind|Declared units|Float32 range|Requested display|\n|---|---|---|---|---|\n");for(var t:model.tracks())text.append('|').append(t.id()).append('|').append(t.kind()).append('|').append(t.units().replace("|","\\|")).append('|').append(t.range()).append('|').append(t.display()).append("|\n");text.append("\nTrack heights are requested panel heights;original alignment layout can compute a different visible height. Actual dimensions and modes are retained in original-igv.json. Raw signal windows use none;exact raw float32 values and gaps are audited,even if original caching returns duplicate identical intervals or splits one interval into subdivisions. Native comparison neither sums duplicate rows nor treats missing signal as measured0. Display ranges can clip values;review source values and scales. SEG mode OTHER preserves already supplied scalar units;the original three-color palette parameters are exact but its251-step lookup/raster gradient can quantize colors near endpoints. No automatic copy-number log conversion/cohort-frequency summary is performed. BED lookup can return features touching the query boundary;raw coordinates remain unchanged.\n");if(model.reads()!=null){var r=model.reads();text.append("\nBAM selection: minimum MAPQ=").append(r.minimumMapq()).append(";include duplicates=").append(r.duplicates()).append(",vendor-failed=").append(r.failed()).append(",secondary=").append(r.secondary()).append(",supplementary=").append(r.supplementary()).append(". Downsampling=false;each alignment counts independently,including overlapping mates. All base qualities count;quality only affects shading. Softclips displayed=").append(r.softClips()).append(" and excluded from coverage;D included in coverage totals=").append(r.deletionsInCoverage()).append(";N skips reference;I counts one event at its current reference cursor. Pair view=").append(r.pairs()).append(";junction track=").append(r.junctions()).append(";color=").append(r.colorBy()).append(". Inspect native and original complete per-base TSVs. Raw MM/ML/MN/SA/HP/PS tags are compared without methylation interpretation. These counts are not an inferred allele fraction,independent molecules or a variant call.\n");}text.append("\nRequired action: inspect every full PNG and original log,confirm biological assembly/units/filters/scales,and record a scientist's review before using the figure. Consult WARNINGS.md for exact failed/skipped dependency recovery.\n");return text.toString();
    }
    static String help(){return """
        igv-tools --recipe LOCAL_TYPED_VIEWS.json --home ADMITTED_PRIVATE_IGV_HOME
          --out NEW_DIRECTORY [--timeout 300] [--max-bytes 536870912]
          [--max-records 1000000] [--options-file ASCII_UTF8_OPTIONS.json]
        Original IGV2.19.8/Java21/Windows x64;hidden frame,local files,no server.
        Typed recipe declares exact FASTA/index,assembly,zero-based half-open
        views,dimensions,sequence visibility and each BED6/bedGraph/BigWig/SEG/BAM
        track with explicit colors,height,display,units/range as applicable.
        Raw scalar window=none;SEG mode=raw-supplied-units with explicit source
        coordinates. BAM requires a supplied index and every explicit read policy,
        downsample=false,overlapping_mates=count-each-alignment and
        base_quality_policy=all-bases;quality-shading-only. Local PNG/session,
        source/staged inputs,actual original ledger and full native audit retained.
        Exact scalar/missing functions,features,reference bases,selected reads/raw
        tags and every base-count column are checked;exit0 alone never suffices.
        Manual visual review required;no variant,allelic or methylation inference.
        Original automatic CNA cohort/synteny summaries remain unfinished.
        Exit0 completed with review warnings;exit3 unavailable/changed dependencies
        SKIP analysis;exit2 invalid inputs/process/timeout/output/audit failure.
        Read WARNINGS.md and its Required action. Failed/skipped partials retained.
        Analysis never installs;python tools/literature/install_igv.py --help.
        Existing outputs are preserved. An ASCII-named UTF8 JSON string-options
        file supports Unicode data/output paths;direct flags override its values.
        """;}
}

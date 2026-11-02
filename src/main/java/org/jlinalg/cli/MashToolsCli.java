/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public native mash analysis with full capture, fit/posterior replay and admission. */
final class MashToolsCli {
    private MashToolsCli() { }
    static final String ACTION="Required action: inspect top-level WARNINGS.md and execution/WARNINGS.md, every fit/proposal/history and posterior table. Require exit0, top-level COMPLETED.json and mashr-tools --verify DIRECTORY before dependent analysis. Native plain EM and ad-hoc residual EM are explicit; no upstream optimizer identity or global residual optimum is claimed. No automatic PCA/ED learning, RDS conversion, missingness, effect/SE derivation or sharing/composite hypothesis is inferred. Unavailable requested capabilities are SKIP3: skip dependent analysis until a supported separately validated workflow is available. FAIL2 admits no result for invalid input, integrity, numerical, convergence, capacity, cancellation or finalization failure. Preserve raw/partial evidence and failed replay scratch; correct the named cause or increase only permitted limits/heap, then retry into NEW output paths. Portable verification needs Java and bounded scratch, replays every captured row/component/history, and leaves the source unchanged. It does not authenticate execution history, certify biological accuracy or reproduce an entire paper. Native-only execution requires no external software download/install.";
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;boolean owns=false;Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Native mash interrupted");};
        try {
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==1&&args[0].equals("--default-limits")){emit(out,MashJson.json(MashLimits.defaults()));return 0;}
            if(args.length==2&&args[0].equals("--export-example")){export(Path.of(args[1]));emit(out,"Native mash input examples exported; no analysis was run.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){var result=MashBundle.verify(Path.of(args[1]),"COMPLETED.json",check);emit(out,"Native mash scoped result verified by complete replay; manifest SHA-256 "+result.manifest().sha256()+"; compared "+result.replay().numericValues()+" numeric values.\n"+ACTION);return 0;}
            var options=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--input","--out","--limits").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique native mash --input, --out and optional --limits value options");
            if(!options.containsKey("--input")||!options.containsKey("--out"))throw new IOException("Native mash requires --input and --out");
            Path input=Path.of(options.get("--input")).toAbsolutePath().normalize();root=Path.of(options.get("--out")).toAbsolutePath().normalize();
            if(root.startsWith(input)||input.startsWith(root))throw new IOException("Native mash input and output trees must be disjoint");
            if(options.containsKey("--limits")){Path source=Path.of(options.get("--limits")).toAbsolutePath().normalize();if(source.startsWith(root)||root.startsWith(source))throw new IOException("Native mash limits source and output tree must be disjoint");}
            MashFiles.create(root);owns=true;
            String limits=options.containsKey("--limits")?MashFiles.text(Path.of(options.get("--limits")).toAbsolutePath().normalize(),262144):MashJson.json(MashLimits.defaults())+"\n";
            MashLimits.parse(SimpleJson.parse(limits));unavailable(input);
            var result=MashBundle.produce(root,input,limits.getBytes(StandardCharsets.UTF_8),check);
            emit(out,"Native mash complete scoped fit/posterior result admitted: "+root+"\n"+ACTION);
            if(result.fitStatus().equals("RESIDUAL_ROLLBACK"))emit(err,"Native mash WARNING RESIDUAL_ROLLBACK: rejected residual proposal retained; preceding accepted model returned. No global residual optimum certified.");return 0;
        }catch(Exception failure){
            int code=failure instanceof Unavailable?3:2;String label=code==3?"SKIP3 — ANALYSIS SKIPPED":"FAIL2 — NO RESULT ADMITTED";String detail=failure.toString();if(detail.length()>16384)detail=detail.substring(0,16384)+" [diagnostic bounded; partial evidence retained]";emit(err,"Native mash "+label+": "+detail+"\n"+ACTION);
            if(owns)try {
                Files.deleteIfExists(root.resolve("COMPLETED.json"));MashFiles.write(root.resolve("FAILURE.json"),Map.of("schema_version",1,"tool",MashBundle.TOOL,"exit_code",code,"status",label,"scoped_result_admitted",false,"error",detail,"required_action",ACTION));
                Files.writeString(root.resolve("WARNINGS.md"),"\n# Native mash "+label+"\n\n"+detail+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }catch(IOException diagnostic){emit(err,"Native mash failure diagnostics could not be fully written: "+diagnostic+"\n"+ACTION);}return code;
        }
    }
    private static final class Unavailable extends IOException {private static final long serialVersionUID=1;Unavailable(String message){super(message);}}
    private static void unavailable(Path input)throws IOException {
        // Recognize explicitly requested learning capabilities; never silently choose native bases.
        Path settings=input.resolve("settings.tsv");if(!Files.isRegularFile(settings))return;
        String text=MashFiles.text(settings,262144);String[] lines=text.split("\\R");if(lines.length==0||!lines[0].equals("key\tvalue"))return;var values=new LinkedHashMap<String,String>();
        for(int i=1;i<lines.length;i++){String[] fields=lines[i].split("\t",-1);if(fields.length!=2||values.putIfAbsent(fields[0],fields[1])!=null)return;}
        if("2".equals(values.get("schema_version"))&&"fit_predict".equals(values.get("operation"))&&Set.of("canonical_pca_ed","pca","ed","original_mashr").contains(values.getOrDefault("basis","")))throw new Unavailable("Requested automatic upstream covariance learning is not available in this native-only workflow. Skip this analysis; supply a justified explicit basis/expanded model or use a separately validated upstream preparation workflow. No replacement was selected.");
    }
    private static void export(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();MashFiles.create(root);
        List<String> names=List.of("fit-input/effects.tsv","fit-input/standard-errors.tsv","fit-input/populations.tsv","fit-input/settings.tsv","fit-input/residual.tsv","fit-input/basis/component-names.tsv","fit-input/basis/component-1.tsv","predict-input/effects.tsv","predict-input/standard-errors.tsv","predict-input/populations.tsv","predict-input/settings.tsv");
        for(String name:names){Path target=root.resolve(name);Files.createDirectories(target.getParent());try(var input=MashToolsCli.class.getResourceAsStream("/org/jlinalg/omics/mashr-native-example/"+name)){if(input==null)throw new IOException("Missing packaged native mash example: "+name);Files.copy(input,target);}}
        Files.writeString(root.resolve("README.md"),"# Native mash examples\n\nRun `mashr-tools --input EXPORTED/fit-input --out NEW_FIT`. Require top-level COMPLETED.json and `mashr-tools --verify NEW_FIT`. For prediction, copy predict-input to a NEW request directory, then copy NEW_FIT/execution/model into that request as model. Run `mashr-tools --input NEW_REQUEST --out NEW_PREDICTION` and verify it. Never copy an entire output bundle as a model. Fit and target populations, grid, normalization, prior, optimizer and residual choices are explicit in settings.tsv. Read full WARNINGS.md.\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        mashr-tools --input NORMALIZED_DIRECTORY --out NEW_DIRECTORY [--limits LIMITS.json]
        mashr-tools --verify DIRECTORY
        mashr-tools --export-example NEW_DIRECTORY
        mashr-tools --default-limits | --help
        Native Java: complete explicit training/strong/target populations; true singleton support.
        Canonical/supplied covariance bases, explicit squared scale grid and priors; plain EM.
        Fixed/simple-null/ad-hoc residual EM; full posterior covariance, sign/zero/LFSR and weights.
        Reusable fully expanded models; complete portable semantic replay before admission.
        Each creation executes the complete native fit/prediction twice; each verify once.
        """+ACTION;}
}

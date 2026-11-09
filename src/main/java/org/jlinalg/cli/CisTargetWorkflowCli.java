/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.CisTargetRecovery;

/** Native complete-rank TSV workflow. Rank generation and SCENIC fitting are separate. */
public final class CisTargetWorkflowCli {
    private CisTargetWorkflowCli() { }
    private record Settings(CisTargetRecovery.Profile profile,double aucParameter,int recoveryBound,
            double minimumAuc,double minimumNes,boolean strictAuc,boolean strictNes,
            double minimumPresentFraction,boolean writeCurves) { }
    private record Annotation(String motif,String tf,String evidence) { }
    private record Input(List<String> databaseFeatures,List<String> motifs,List<String> present,
            LinkedHashMap<String,Double> signature,double[][] ranks,double[] weights) { }

    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }

    public static int run(String[] args,PrintStream output,PrintStream error) {
        if(args.length==1 && args[0].equals("--help")) {
            output.println("Native cisTarget ranked-feature enrichment: --settings JSON --rankings COMPLETE_TSV --signature TSV --annotations TSV --out NEW_DIRECTORY\n"
                +"Complete rankings: motif then every database feature as TSV columns; every motif row defines the NES background. Raw ranks are never shifted.\n"
                +"Signature: feature and weight columns. Annotations: motif, tf and evidence columns; header-only means explicitly no annotations.\n"
                +"Explicit ctxcore0.2.0 or RcisTarget1.29.0 exact iCisTarget profile; no upstream runtime or installation is required.\n"
                +"Read WARNINGS.md, signature-dispositions.tsv and all-motifs.tsv. Undefined NES is retained as NaN. No DNA scoring or SCENIC network fitting is implied.");return 0;
        }
        Path out=null;boolean created=false;
        try {
            require(args.length==10,"Supply exactly --settings JSON --rankings COMPLETE_TSV --signature TSV --annotations TSV --out NEW_DIRECTORY");
            var options=new LinkedHashMap<String,String>();
            for(int i=0;i<args.length;i+=2)require(Set.of("--settings","--rankings","--signature","--annotations","--out").contains(args[i]) && options.putIfAbsent(args[i],args[i+1])==null,"Unknown/repeated option: "+args[i]);
            var paths=new LinkedHashMap<String,Path>();var hashes=new LinkedHashMap<String,String>();
            for(String key:List.of("--settings","--rankings","--signature","--annotations")) {
                Path path=Path.of(options.get(key)).toAbsolutePath().normalize();require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Input must be a regular file, not a symlink: "+path);
                paths.put(key,path);hashes.put(path.toString(),sha256(path));
            }
            Settings settings=settings(Files.readString(paths.get("--settings")));
            out=Path.of(options.get("--out")).toAbsolutePath().normalize();
            require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS),"Output already exists; preserve it and choose NEW_DIRECTORY: "+out);
            require(out.getParent()!=null && Files.isDirectory(out.getParent()) && !Files.isSymbolicLink(out.getParent()),"Output parent must be an existing directory, not a symlink");
            Files.createDirectory(out);created=true;Path partial=out.resolve("partial");Files.createDirectory(partial);
            Files.copy(paths.get("--settings"),out.resolve("settings.json"));
            Input input=read(paths.get("--rankings"),paths.get("--signature"),settings);
            List<Annotation> annotations=annotations(paths.get("--annotations"));
            var result=settings.profile()==CisTargetRecovery.Profile.CTXCORE_0_2_0
                ?CisTargetRecovery.ctxcore(input.ranks(),input.weights(),input.databaseFeatures().size(),settings.aucParameter(),settings.recoveryBound())
                :CisTargetRecovery.rcisTarget(input.ranks(),input.databaseFeatures().size(),settings.aucParameter(),settings.recoveryBound());
            var warnings=new ArrayList<>(result.warnings());
            if(input.present().size()!=input.signature().size())warnings.add((input.signature().size()-input.present().size())+" signature features are absent from this database; they have explicit MISSING_FROM_DATABASE dispositions and do not enter AUC normalization. Check species/build/identifier compatibility.");
            if(annotations.isEmpty())warnings.add("No TF annotations supplied. Motif enrichment and leading-feature results remain available; no TF-target results are inferred. Supply an organism/collection-matched annotation table for TF links.");
            var known=new LinkedHashSet<>(input.motifs());long unknown=annotations.stream().filter(a->!known.contains(a.motif())).count();
            if(unknown>0)warnings.add(unknown+" annotation rows reference motifs absent from this ranking database. They remain in annotation-dispositions.tsv and create no TF link; check collection versions.");
            warnings.add("Thresholds apply after all-motif AUC/NES and recovery-background calculations. Do not supply a prefiltered motif table or compare NES values calculated with different motif populations.");
            warnings.add("Only exact supplied motif-to-TF annotations are joined. Evidence labels are preserved without inferred orthology, motif-similarity transfer, TF-expression/methylation filtering or regulatory causality. DNA scoring, region overlap, genome conversion, SCENIC/SCENIC+ fitting, AUCell and R approximate/smoothed leading edges are separate analyses.");
            int selected=write(partial,input,annotations,result,settings);
            for(var entry:hashes.entrySet())require(sha256(Path.of(entry.getKey())).equals(entry.getValue()),"Input changed during analysis; no result admitted: "+entry.getKey());
            var files=new LinkedHashMap<String,String>();try(var stream=Files.list(partial)){for(Path path:stream.sorted().toList())files.put(path.getFileName().toString(),sha256(path));}
            var receipt=new LinkedHashMap<String,Object>();receipt.put("schema_version",1);receipt.put("status","PASS_NATIVE_CISTARGET_WITH_WARNINGS");
            receipt.put("source_profile",settings.profile().name());receipt.put("database_features",input.databaseFeatures().size());receipt.put("background_motifs",input.motifs().size());
            receipt.put("signature_features",input.signature().size());receipt.put("present_signature_features",input.present().size());receipt.put("selected_motifs",selected);
            receipt.put("undefined_nes_motifs",result.motifs().stream().filter(m->!Double.isFinite(m.nes())).count());
            receipt.put("rounded_auc_count",result.roundedAucCount());receipt.put("source_auc_cutoff",result.sourceAucCutoff());receipt.put("input_sha256",hashes);receipt.put("result_sha256",files);
            receipt.put("upstream_runtime_required",false);receipt.put("software_installed",false);receipt.put("whole_paper_reproduced",false);
            Files.writeString(out.resolve("run.json"),RegenieRuntime.json(receipt)+"\n");warnings(out,"ANALYSIS WARNINGS",warnings);
            // Admission table moves last; a failed write/promotion retains partials and FAIL2.
            try(var stream=Files.list(partial)){for(Path path:stream.toList())if(!path.getFileName().toString().equals("enriched-motifs.tsv"))Files.move(path,out.resolve(path.getFileName()));}
            Files.move(partial.resolve("enriched-motifs.tsv"),out.resolve("enriched-motifs.tsv"));
            error.println("CISTARGET ANALYSIS WARNINGS: read "+out.resolve("WARNINGS.md"));output.println("Native cisTarget completed: "+out);return 0;
        } catch(Exception failure) {
            String detail=String.valueOf(failure.getMessage());
            if(created)try {
                // The admission table is written last. All earlier material remains auditable.
                Files.writeString(out.resolve("run.json"),RegenieRuntime.json(Map.of("schema_version",1,"status","FAIL2_NO_RESULTS_ADMITTED","error",detail))+"\n");
                warnings(out,"ANALYSIS FAILED - NO RESULTS ADMITTED",List.of(detail));
            } catch(IOException secondary){error.println("Unable to record failure receipt: "+secondary.getMessage());}
            error.println("CISTARGET ANALYSIS FAILED: "+detail+"\nRequired action: correct the named input/settings, preserve failed partials and retry a NEW output directory. No failed result is admitted.");return 2;
        }
    }

    private static Settings settings(String raw) throws IOException {
        Object parsed=SimpleJson.parse(raw);require(parsed instanceof Map<?,?>,"Settings must be a JSON object");Map<?,?> values=(Map<?,?>)parsed;
        require(values.keySet().equals(Set.of("schema_version","profile","auc_parameter","recovery_bound","minimum_auc","minimum_nes","strict_auc","strict_nes","minimum_present_fraction","write_recovery_curves")),"Supply all documented settings exactly; unknown/missing keys are refused");
        require(number(values.get("schema_version"))==1,"Unsupported schema_version");Object profile=values.get("profile");require(profile instanceof String,"profile must be an explicit source-profile name");
        double bound=number(values.get("recovery_bound"));require(bound==Math.rint(bound) && bound>=2 && bound<=Integer.MAX_VALUE,"recovery_bound must be an integer >=2");
        double coverage=number(values.get("minimum_present_fraction"));require(coverage>0 && coverage<=1,"minimum_present_fraction must lie in(0,1]");
        return new Settings(CisTargetRecovery.Profile.valueOf((String)profile),number(values.get("auc_parameter")),(int)bound,
            number(values.get("minimum_auc")),number(values.get("minimum_nes")),bool(values.get("strict_auc")),bool(values.get("strict_nes")),coverage,bool(values.get("write_recovery_curves")));
    }

    private static Input read(Path rankings,Path signatureFile,Settings settings) throws IOException {
        var signature=new LinkedHashMap<String,Double>();
        try(var stream=Files.newBufferedReader(signatureFile,StandardCharsets.UTF_8)) {
            require("feature\tweight".equals(stream.readLine()),"Signature header must be feature<TAB>weight");String line;int row=1;
            while((line=stream.readLine())!=null){row++;String[] f=fields(line,2,"Signature row "+row);identifier(f[0]);double w=finite(f[1]);
                require(signature.putIfAbsent(f[0],w)==null,"Repeated signature feature: "+f[0]);
                if(settings.profile()==CisTargetRecovery.Profile.RCISTARGET_1_29_0_ICISTARGET)require(w==1,"R profile requires explicit unit weights; no weight is discarded");}
        }
        require(!signature.isEmpty() && signature.size()<=1000000,"Signature must contain1..1000000 unique features");
        try(var stream=Files.newBufferedReader(rankings,StandardCharsets.UTF_8)) {
            String header=stream.readLine();require(header!=null,"Empty ranking database");String[] columns=header.split("\t",-1);
            require(columns.length>=3 && columns.length<=1000001 && columns[0].equals("motif"),"Ranking header must start motif and contain every database feature (2..1000000)");
            var all=new ArrayList<String>();var seen=new LinkedHashSet<String>();var present=new ArrayList<String>();var positions=new ArrayList<Integer>();
            for(int col=1;col<columns.length;col++){identifier(columns[col]);require(seen.add(columns[col]),"Repeated database feature: "+columns[col]);all.add(columns[col]);}
            // User signature order defines target presentation; database and signature orders are recorded separately.
            var databasePositions=new LinkedHashMap<String,Integer>();for(int i=0;i<all.size();i++)databasePositions.put(all.get(i),i+1);
            for(String feature:signature.keySet())if(databasePositions.containsKey(feature)){present.add(feature);positions.add(databasePositions.get(feature));}
            require(!present.isEmpty() && present.size()/(double)signature.size()>=settings.minimumPresentFraction(),"Signature overlap is below explicit minimum_present_fraction; correct identifiers/species/build before analysis");
            double[] weights=new double[present.size()];for(int i=0;i<weights.length;i++)weights[i]=signature.get(present.get(i));
            var motifs=new ArrayList<String>();var motifSet=new LinkedHashSet<String>();var rows=new ArrayList<double[]>();String line;int lineNumber=1;
            while((line=stream.readLine())!=null){lineNumber++;String[] f=fields(line,columns.length,"Ranking row "+lineNumber);identifier(f[0]);
                require(motifSet.add(f[0]),"Repeated database motif: "+f[0]);motifs.add(f[0]);require(motifs.size()<=100000 && (long)motifs.size()*present.size()<=250000000 && (long)motifs.size()*settings.recoveryBound()<=250000000,"Native ranking/recovery capacity exceeded; no motif is truncated");
                // Check every supplied cell, including unselected features, before accepting the database.
                for(int col=1;col<f.length;col++){double rank=rank(f[col],settings.profile());require(rank==Double.POSITIVE_INFINITY || (rank>=0 && rank==Math.rint(rank) && (settings.profile()==CisTargetRecovery.Profile.CTXCORE_0_2_0?rank<all.size():rank<=all.size())),"Rank outside declared database domain at row "+lineNumber+", column "+col);}
                double[] row=new double[present.size()];for(int i=0;i<row.length;i++)row[i]=rank(f[positions.get(i)],settings.profile());rows.add(row);
            }
            require(motifs.size()>=2,"At least two background motifs are required for an interpretable NES population");
            return new Input(List.copyOf(all),List.copyOf(motifs),List.copyOf(present),signature,rows.toArray(double[][]::new),weights);
        }
    }

    private static List<Annotation> annotations(Path path) throws IOException {
        var result=new ArrayList<Annotation>();var unique=new LinkedHashSet<Annotation>();
        try(var stream=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            require("motif\ttf\tevidence".equals(stream.readLine()),"Annotation header must be motif<TAB>tf<TAB>evidence");String line;int row=1;
            while((line=stream.readLine())!=null){row++;String[] f=fields(line,3,"Annotation row "+row);for(String value:f)identifier(value);
                var a=new Annotation(f[0],f[1],f[2]);require(unique.add(a),"Repeated annotation row: "+a);result.add(a);require(result.size()<=1000000,"Annotation capacity exceeded");}
        }
        return List.copyOf(result);
    }

    private static int write(Path home,Input input,List<Annotation> annotations,CisTargetRecovery.Result result,Settings settings) throws IOException {
        String header="motif\tauc\tnes\tleading_rank\tleading_recovery\tleading_excess\tstatus\tpasses_thresholds\n";int selected=0;
        var byMotif=new LinkedHashMap<String,List<Annotation>>();for(var annotation:annotations)byMotif.computeIfAbsent(annotation.motif(),k->new ArrayList<>()).add(annotation);
        try(var all=Files.newBufferedWriter(home.resolve("all-motifs.tsv"));var passing=Files.newBufferedWriter(home.resolve("enriched-motifs.tsv"));
                var targets=Files.newBufferedWriter(home.resolve("leading-targets.tsv"));var tf=Files.newBufferedWriter(home.resolve("tf-targets.tsv"))) {
            all.write(header);passing.write(header);targets.write("motif\tfeature\traw_rank\tweight\tpasses_thresholds\n");tf.write("tf\tmotif\tfeature\tevidence\n");
            for(int i=0;i<input.motifs().size();i++) {
                String name=input.motifs().get(i);var motif=result.motifs().get(i);
                boolean pass=Double.isFinite(motif.nes()) && (settings.strictAuc()?motif.auc()>settings.minimumAuc():motif.auc()>=settings.minimumAuc()) && (settings.strictNes()?motif.nes()>settings.minimumNes():motif.nes()>=settings.minimumNes());
                String row=name+'\t'+motif.auc()+'\t'+motif.nes()+'\t'+motif.leadingRank()+'\t'+motif.leadingRecovery()+'\t'+motif.leadingExcess()+'\t'+motif.status()+'\t'+pass+'\n';all.write(row);if(pass){selected++;passing.write(row);}
                for(int j:motif.targetIndicesInInputOrder()) {
                    String feature=input.present().get(j);targets.write(name+'\t'+feature+'\t'+input.ranks()[i][j]+'\t'+input.weights()[j]+'\t'+pass+'\n');
                    if(pass)for(var a:byMotif.getOrDefault(name,List.of()))tf.write(a.tf()+'\t'+name+'\t'+feature+'\t'+a.evidence()+'\n');
                }
            }
        }
        try(var writer=Files.newBufferedWriter(home.resolve("signature-dispositions.tsv"))) {
            writer.write("feature\tweight\tdisposition\n");var present=new LinkedHashSet<>(input.present());for(var entry:input.signature().entrySet())writer.write(entry.getKey()+'\t'+entry.getValue()+'\t'+(present.contains(entry.getKey())?"PRESENT":"MISSING_FROM_DATABASE")+'\n');
        }
        try(var writer=Files.newBufferedWriter(home.resolve("database-features.tsv"))){writer.write("feature\n");for(String feature:input.databaseFeatures())writer.write(feature+'\n');}
        try(var writer=Files.newBufferedWriter(home.resolve("annotation-dispositions.tsv"))) {
            writer.write("motif\ttf\tevidence\tdisposition\n");var motifs=new LinkedHashSet<>(input.motifs());for(var a:annotations)writer.write(a.motif()+'\t'+a.tf()+'\t'+a.evidence()+'\t'+(motifs.contains(a.motif())?"MATCHED_MOTIF":"MOTIF_ABSENT_FROM_DATABASE")+'\n');
        }
        if(settings.writeCurves()) {
            double[][] curves=result.recoveryCurves();int[] coordinates=result.recoveryRanks();
            try(var writer=Files.newBufferedWriter(home.resolve("recovery-curves.tsv"))) {
                writer.write("motif");for(int coordinate:coordinates)writer.write("\t"+coordinate);writer.write('\n');
                for(int i=0;i<curves.length;i++){writer.write(input.motifs().get(i));for(double value:curves[i])writer.write("\t"+value);writer.write('\n');}
            }
            try(var writer=Files.newBufferedWriter(home.resolve("recovery-background.tsv"))) {
                double[] mean=result.meanCurve(),sd=result.sdCurve(),boundary=result.meanPlusTwoSd();writer.write("rank\tmean\tsd\tmean_plus_two_sd\n");
                for(int i=0;i<coordinates.length;i++)writer.write(coordinates[i]+"\t"+mean[i]+"\t"+sd[i]+"\t"+boundary[i]+"\n");
            }
        }
        return selected;
    }

    private static void warnings(Path out,String title,List<String> details) throws IOException {
        var text=new StringBuilder("# "+title+"\n\n");for(String warning:details)text.append("- ").append(warning).append("\n\n");
        text.append("Required action: require exit0, run.json PASS_NATIVE_CISTARGET_WITH_WARNINGS and enriched-motifs.tsv before using results. Read all-motifs.tsv and every disposition. NaN NES is undefined enrichment, not a valid null test. Correct named problems and retry NEW output paths; preserve failed partials. Native TSV analysis installs nothing. If an optional database conversion requires an unavailable or forbidden runtime, skip that conversion and its dependent analysis until it is permitted and available.\n");Files.writeString(out.resolve("WARNINGS.md"),text);
    }
    private static String[] fields(String line,int count,String context){String[] result=line.split("\t",-1);require(result.length==count,context+": expected "+count+" tab-separated fields");return result;}
    private static void identifier(String value){require(!value.isEmpty() && value.equals(value.strip()) && value.chars().noneMatch(Character::isISOControl),"Identifiers/evidence must be nonempty, unpadded and free of control characters");}
    private static double finite(String token){require(token.equals(token.strip()),"Numeric tokens must not contain padding");double value=Double.parseDouble(token);require(Double.isFinite(value),"Nonfinite numeric token: "+token);return value;}
    private static double rank(String token,CisTargetRecovery.Profile profile){if(profile==CisTargetRecovery.Profile.RCISTARGET_1_29_0_ICISTARGET && token.equals("Inf"))return Double.POSITIVE_INFINITY;return finite(token);}
    private static double number(Object value){require(value instanceof Number,"Explicit numeric setting required");double result=((Number)value).doubleValue();require(Double.isFinite(result),"Settings must be finite");return result;}
    private static boolean bool(Object value){require(value instanceof Boolean,"Explicit boolean setting required");return (Boolean)value;}
    private static String sha256(Path path) throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))>=0)digest.update(bytes,0,n);}return HexFormat.of().formatHex(digest.digest());}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}

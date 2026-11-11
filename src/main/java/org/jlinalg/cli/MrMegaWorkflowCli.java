/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.meta.MrMegaWorkflow;
import org.jlinalg.meta.MrMegaWorkflow.Column;
import org.jlinalg.meta.MrMegaWorkflow.Settings;

/** Native MR-MEGA workflow entry point; explicit settings, complete ledgers and warnings. */
public final class MrMegaWorkflowCli {
    private MrMegaWorkflowCli() { }
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }

    public static int run(String[] args,PrintStream output,PrintStream error) {
        if (List.of(args).contains("--help")) {
            output.println("Native MR-MEGA: --settings JSON --file-list FILE --out NEW_DIRECTORY\n"
                + "Settings must explicitly declare schema_version=1, trait, axes, ancestry, no_alleles, input_gc, output_gc, standard_names, aliases and filters.\n"
                + "Uses native Java; no upstream installation required. Read WARNINGS.md and every row disposition.\n"
                + "Stable native P-values differ from original approximation fields. No whole-paper reproduction is implied."); return 0;
        }
        Path out=null; boolean created=false;
        try {
            var options=new LinkedHashMap<String,String>();
            require(args.length==6,"Supply exactly --settings JSON --file-list FILE --out NEW_DIRECTORY");
            for(int i=0;i<args.length;i+=2) require(Set.of("--settings","--file-list","--out").contains(args[i]) && options.putIfAbsent(args[i],args[i+1])==null,"Unknown or repeated option: " + args[i]);
            Path settingsPath=Path.of(options.get("--settings")).toAbsolutePath().normalize();
            String settingsHash=MrMegaWorkflow.sha256(settingsPath);String raw=Files.readString(settingsPath);Settings settings=settings(raw);
            out=Path.of(options.get("--out")).toAbsolutePath().normalize();
            require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS),"Output already exists; preserve it and choose a new directory: " + out);
            require(Files.isDirectory(out.getParent()) && !Files.isSymbolicLink(out.getParent()),"Output parent must be an existing directory, not a symlink");
            Files.createDirectory(out);created=true;Files.writeString(out.resolve("settings.json"),raw);
            Path partial=out.resolve("partial");Files.createDirectory(partial);
            var parsed=MrMegaWorkflow.read(Path.of(options.get("--file-list")),settings);var analysis=MrMegaWorkflow.analyze(parsed);
            write(analysis,partial);MrMegaWorkflow.verifyInputs(parsed);
            require(MrMegaWorkflow.sha256(settingsPath).equals(settingsHash),"Settings changed during analysis; no result admitted");
            boolean failed=analysis.variants().stream().anyMatch(r->r.status().startsWith("FAIL2_"));
            var hashes=new LinkedHashMap<String,Object>();for(var entry:parsed.inputSha256().entrySet())hashes.put(entry.getKey().toString(),entry.getValue());hashes.put(settingsPath.toString(),settingsHash);
            var manifest=new LinkedHashMap<String,Object>();manifest.put("schema_version",1);manifest.put("status",failed?"FAIL2_VARIANT_RESULTS_NOT_ADMITTED":"PASS_NATIVE_MRMEGA_WITH_WARNINGS");
            manifest.put("whole_paper_reproduced",false);manifest.put("native_stable_probability_fields",true);manifest.put("output_gc_lambda",analysis.outputLambda());
            manifest.put("cohorts",parsed.cohorts().size());manifest.put("variants",parsed.variants().size());manifest.put("successful_variant_fits",analysis.variants().stream().filter(r->r.status().equals("PASS")).count());
            manifest.put("small_cohort_variants",analysis.variants().stream().filter(r->r.status().equals("SmallCohortCount")).count());manifest.put("input_sha256",hashes);
            Files.writeString(out.resolve("run.json"),RegenieRuntime.json(manifest)+"\n");
            warnings(out,failed?"ANALYSIS FAILED - RESULTS NOT ADMITTED":"ANALYSIS WARNINGS",analysis.warnings());
            if(!failed) {
                // Publish the result table last, after receipts/warnings and all other files.
                // A failure during preparation/promotion leaves it in partial/ and is reported below.
                try(var stream=Files.list(partial)) { for(Path path:stream.toList())if(!path.getFileName().toString().equals("results.tsv"))Files.move(path,out.resolve(path.getFileName())); }
                Files.move(partial.resolve("results.tsv"),out.resolve("results.tsv"));
            }
            error.println((failed?"ANALYSIS FAILED":"ANALYSIS WARNINGS")+": read " + out.resolve("WARNINGS.md"));
            if(!failed)output.println("Native MR-MEGA completed: " + out);
            return failed?2:0;
        } catch(Exception failure) {
            String detail=String.valueOf(failure.getMessage());
            if(created)try {
                warnings(out,"ANALYSIS FAILED - NO RESULTS ADMITTED",List.of(detail));
                Files.writeString(out.resolve("run.json"),RegenieRuntime.json(Map.of("schema_version",1,"status","FAIL2_NO_RESULTS_ADMITTED","error",detail))+"\n");
            } catch(IOException secondary) { error.println("Unable to record failure receipt: " + secondary.getMessage()); }
            error.println("MR-MEGA ANALYSIS FAILED: " + detail + "\nRequired action: correct the named input/settings and retry a NEW output directory. Existing outputs and failed partials remain preserved.");return 2;
        }
    }
    public static Settings settings(String raw) throws IOException {
        Map<String,Object> object=object(SimpleJson.parse(raw));
        keys(object,Set.of("schema_version","trait","axes","ancestry","no_alleles","input_gc","output_gc","standard_names","aliases","filters"));
        require(integer(object.get("schema_version"))==1,"Unsupported settings schema_version");
        String trait=string(object.get("trait"));require(Set.of("quantitative","binary").contains(trait),"trait must be quantitative or binary");
        String ancestry=string(object.get("ancestry"));require(Set.of("inferred","precalculated").contains(ancestry),"ancestry must be inferred or precalculated");
        var aliases=new EnumMap<Column,List<String>>(Column.class);
        for(var entry:object(object.get("aliases")).entrySet()) {
            var values=new ArrayList<String>();for(Object value:list(entry.getValue()))values.add(string(value));aliases.put(Column.valueOf(entry.getKey()),values);
        }
        var filters=new ArrayList<MrMegaWorkflow.Filter>();
        for(Object item:list(object.get("filters"))) {
            var f=object(item);keys(f,Set.of("column","operator","value"));require(f.get("value") instanceof Number,"Filter value must be numeric");
            filters.add(new MrMegaWorkflow.Filter(string(f.get("column")),string(f.get("operator")),((Number)f.get("value")).doubleValue()));
        }
        return new Settings(trait.equals("quantitative")?MrMegaWorkflow.Trait.QUANTITATIVE:MrMegaWorkflow.Trait.BINARY,
            integer(object.get("axes")),ancestry.equals("precalculated"),bool(object.get("no_alleles")),bool(object.get("input_gc")),bool(object.get("output_gc")),bool(object.get("standard_names")),aliases,filters);
    }
    private static void write(MrMegaWorkflow.Analysis analysis,Path out) throws IOException {
        var parsed=analysis.parsed();int axes=parsed.settings().axes();
        var cohorts=new StringBuilder("index\tfile\tchi_square_rows\tlambda\n");
        for(int i=0;i<parsed.cohorts().size();i++) {var c=parsed.cohorts().get(i);cohorts.append(i).append('\t').append(c.path()).append('\t').append(c.chiSquareRows()).append('\t').append(c.lambda()).append('\n');}
        Files.writeString(out.resolve("cohorts.tsv"),cohorts);
        var coordinates=new StringBuilder("cohort");for(int j=0;j<axes;j++)coordinates.append("\taxis_").append(j);coordinates.append('\n');
        double[][] x=analysis.coordinates();for(int i=0;i<x.length;i++){coordinates.append(i);for(double v:x[i])coordinates.append('\t').append(v);coordinates.append('\n');}Files.writeString(out.resolve("coordinates.tsv"),coordinates);
        var rows=new StringBuilder("cohort\tline\tmarker\tstatus\tdetail\n");
        for(var row:parsed.rows())rows.append(row.cohort()).append('\t').append(row.line()).append('\t').append(row.marker()).append('\t').append(row.status()).append('\t').append(row.detail()).append('\n');Files.writeString(out.resolve("rows.tsv"),rows);
        var prepared=new StringBuilder("marker\tcohort\tstored_EAF\tN\tstored_beta\tstored_SE\n");
        for(var variant:parsed.variants()) {
            double[] f=variant.frequencies(),b=variant.effects(),se=variant.standardErrors();int[] n=variant.sampleSizes();
            for(int i=0;i<f.length;i++)prepared.append(variant.id()).append('\t').append(i).append('\t').append(f[i]).append('\t').append(n[i]).append('\t').append(b[i]).append('\t').append(se[i]).append('\n');
        }
        Files.writeString(out.resolve("prepared.tsv"),prepared);
        var used=new StringBuilder("marker\tcohort\teffect\teffective_SE\tweight\n");
        var covariance=new StringBuilder("marker\trow\tcolumn\tcovariance\n");
        var diagnostics=new StringBuilder("marker\tweighted_mean\tcentered_TSS\tzero_TSS\tRSS\tresidual_scale\n");
        var result=new StringBuilder("MarkerName\tChromosome\tPosition\tEA\tNEA\tEAF\tNsample\tNcohort\tEffects");
        for(int j=0;j<=axes;j++)result.append("\tbeta_").append(j).append("\tse_").append(j);
        result.append("\traw_chisq_association\toutput_chisq_association\tndf_association\tstable_output_association_P\tchisq_ancestry_het\tndf_ancestry_het\tstable_ancestry_P\tchisq_residual_het\tndf_residual_het\tstable_residual_P\tlnBF\tstatus\tdetail\n");
        for(var row:analysis.variants()) {
            var v=row.variant();result.append(v.id()).append('\t').append(v.chromosome()).append('\t').append(v.position()).append('\t').append(v.effectAllele()).append('\t').append(v.otherAllele()).append('\t').append(v.averageFrequency()).append('\t').append(v.samples()).append('\t').append(v.cohorts()).append('\t').append(v.directions());
            if(row.regression()==null) {for(int j=0;j<2*(axes+1)+11;j++)result.append("\tNA");}
            else {
                var r=row.regression();double[] b=r.coefficients(),se=r.standardErrors();for(int j=0;j<b.length;j++)result.append('\t').append(b[j]).append('\t').append(se[j]);
                double[][] cv=r.covariance();for(int i=0;i<cv.length;i++)for(int j=0;j<cv.length;j++)covariance.append(v.id()).append('\t').append(i).append('\t').append(j).append('\t').append(cv[i][j]).append('\n');
                diagnostics.append(v.id()).append('\t').append(r.weightedMean()).append('\t').append(r.centeredTotalSquares()).append('\t').append(r.zeroTotalSquares()).append('\t').append(r.residualSquares()).append('\t').append(r.residualScale()).append('\n');
                result.append('\t').append(r.associationChiSquare()).append('\t').append(row.outputAssociationChiSquare()).append('\t').append(r.associationDegrees()).append('\t').append(row.stableOutputAssociationP())
                    .append('\t').append(r.ancestryChiSquare()).append('\t').append(r.ancestryDegrees()).append('\t').append(r.stableAncestryP()).append('\t').append(r.residualChiSquare()).append('\t').append(r.residualDegrees()).append('\t').append(r.stableResidualP()).append('\t').append(r.logBayesFactor());
                int[] indices=row.participatingCohorts();double[] y=row.usedEffects(),s=row.usedStandardErrors(),w=row.weights();
                for(int i=0;i<indices.length;i++)used.append(v.id()).append('\t').append(indices[i]).append('\t').append(y[i]).append('\t').append(s[i]).append('\t').append(w[i]).append('\n');
            }
            result.append('\t').append(row.status()).append('\t').append(row.detail()).append('\n');
        }
        Files.writeString(out.resolve("results.tsv"),result);Files.writeString(out.resolve("used.tsv"),used);
        Files.writeString(out.resolve("covariance.tsv"),covariance);Files.writeString(out.resolve("fit-diagnostics.tsv"),diagnostics);
        if(analysis.selection()!=null) {
            var selection=analysis.selection();var selected=new StringBuilder("source_ordinal\tmarker\n");int[] ordinals=selection.sourceOrdinals();
            for(int i=0;i<ordinals.length;i++)selected.append(ordinals[i]).append('\t').append(selection.ids().get(i)).append('\n');Files.writeString(out.resolve("ancestry-selected.tsv"),selected);
            var disposition=new StringBuilder("source_ordinal\tmarker\tstatus\tsource_slot\n");for(var row:selection.dispositions())disposition.append(row.sourceOrdinal()).append('\t').append(row.id()).append('\t').append(row.reason()).append('\t').append(row.sourceMatrixSlot()).append('\n');Files.writeString(out.resolve("ancestry-dispositions.tsv"),disposition);
        }
    }
    private static void warnings(Path out,String heading,List<String> details) throws IOException {
        Files.writeString(out.resolve("WARNINGS.md"),"# "+heading+"\n\n"+String.join("\n\n",details)+"\n\nRequired action: inspect every cohort/variant/row disposition and the explicit GC/allele/axis settings before interpretation. Correct named failures and rerun a NEW directory. Failed partial files are evidence, not admitted results. Original and native probability fields have different definitions. No genome-build conversion, cohort independence check or whole-paper reproduction is inferred.\n");
    }
    private static Map<String,Object> object(Object value) {
        require(value instanceof Map<?,?>,"Expected JSON object");var result=new LinkedHashMap<String,Object>();
        for(var entry:((Map<?,?>)value).entrySet()){require(entry.getKey() instanceof String,"Object key must be a string");result.put((String)entry.getKey(),entry.getValue());}return result;
    }
    private static List<?> list(Object value) {require(value instanceof List<?>,"Expected JSON array");return (List<?>)value;}
    private static void keys(Map<String,Object> value,Set<String> required) {require(value.keySet().equals(required),"Settings keys must be exactly "+required+"; received "+value.keySet());}
    private static String string(Object value) {require(value instanceof String,"Expected explicit string setting");return (String)value;}
    private static boolean bool(Object value) {require(value instanceof Boolean,"Expected explicit boolean setting");return (Boolean)value;}
    private static int integer(Object value) {require(value instanceof Number,"Expected explicit integer setting");double d=((Number)value).doubleValue();require(Double.isFinite(d)&&d==Math.rint(d)&&d>=0&&d<=Integer.MAX_VALUE,"Expected nonnegative int32 setting");return (int)d;}
    private static void require(boolean value,String message) {if(!value)throw new IllegalArgumentException(message);}
}

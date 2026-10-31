/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.genetics.EagleGenotypes;

/** Typed native preparation, original phasing and complete source-policy output audit. */
final class EaglePhasing {
    private EaglePhasing() { }
    record Input(EagleRecipe.Spec recipe, Path work, EagleCohortInputs.Prepared cohort,
            EagleReferenceInputs.Prepared reference, Path recipeSnapshot,EagleSourceInputs.Prepared sources) {
        Input {
            if ((cohort == null) == (reference == null)) throw new IllegalArgumentException("Require exactly one typed Eagle input mode");
        }
        void verify(Runnable cancellation) throws IOException {
            recipe.verify();
            sources.verify(cancellation);
            if (!EagleCohortInputs.digest(recipeSnapshot, 2097152, cancellation).equals(recipe.recipeSha256())) throw new IOException("Protected Eagle recipe snapshot changed");
            if (cohort != null) cohort.verify(cancellation); else reference.verify(cancellation);
            EagleSourceInputs.combinedWorkspace(sources,work,recipe.workspaceBytes());
        }
        List<String> warnings() { var result=new ArrayList<>(sources.warnings());result.addAll(cohort == null ? reference.warnings() : cohort.warnings());return List.copyOf(result); }
    }
    record Result(String operation, Path output, String outputSha256, EagleProcess.Result process,
            Object completeOutputAudit, List<String> engineArguments, List<String> warnings) {
        Result { engineArguments = List.copyOf(engineArguments); warnings = List.copyOf(warnings); }
    }

    static Input prepare(EagleRecipe.Spec recipe, Path newWork, Runnable cancellation) throws IOException {
        recipe.verify(); EagleCohortInputs.Prepared cohort = null; EagleReferenceInputs.Prepared reference = null;
        var sources=EagleSourceInputs.prepare(recipe,newWork.toAbsolutePath().normalize().getParent().resolve("source-intake"),cancellation);
        if (recipe.mode() == EagleGenotypes.Mode.COHORT) cohort = EagleCohortInputs.prepare(sources.data("target"), recipe.map(), newWork, recipe.cohortSpec(), cancellation);
        else reference = EagleReferenceInputs.prepare(sources.data("target"), sources.data("reference"), sources.data("exclusion"), recipe.map(), newWork, recipe.referenceSpec(), cancellation);
        Path work = cohort == null ? reference.work() : cohort.work(); Path snapshot = work.resolve("recipe-source.json");
        byte[] raw;
        try (var stream = Files.newInputStream(recipe.recipe())) { raw = stream.readNBytes(2097153); }
        if (raw.length > 2097152 || !AdmixtureInputs.digest(raw).equals(recipe.recipeSha256())) throw new IOException("Eagle recipe changed while protecting its snapshot");
        Files.write(snapshot, raw, StandardOpenOption.CREATE_NEW);
        var input = new Input(recipe, work, cohort, reference, snapshot,sources); input.verify(cancellation); return input;
    }

    static List<String> arguments(Input input) {
        var recipe = input.recipe(); var parameters = recipe.parameters();
        int chromosome = input.cohort() == null ? recipe.referenceSpec().chromosome() : recipe.cohortSpec().chromosome();
        int chromosomeX = input.cohort() == null ? recipe.referenceSpec().chromosomeX() : recipe.cohortSpec().chromosomeX();
        Path map = input.cohort() == null ? input.reference().map() : input.cohort().map();
        var args = new ArrayList<>(List.of("--chrom=" + chromosome, "--chromX=" + chromosomeX,
                "--geneticMapFile=" + map, "--outPrefix=" + input.work().resolve("engine/result"), "--vcfOutFormat=v",
                "--numThreads=" + parameters.threads(), "--Kpbwt=" + parameters.conditioningHaplotypes(),
                "--pbwtIters=" + parameters.pbwtIterations(), "--seed=" + parameters.seed()));
        EagleGenotypes.Options genotypes;
        if (input.cohort() != null) {
            args.add("--vcf=" + input.cohort().target()); genotypes = recipe.cohortSpec().genotypes();
            if (parameters.algorithm() == EagleRecipe.Algorithm.COHORT_PBWT_ONLY) args.add("--pbwtOnly");
        } else {
            args.add("--vcfTarget=" + input.reference().target()); args.add("--vcfRef=" + input.reference().reference());
            genotypes = recipe.referenceSpec().genotypes();
            if (input.reference().exclusion() != null) args.add("--vcfExclude=" + input.reference().exclusion());
            if (recipe.referenceSpec().allowReferenceAltSwap()) args.add("--allowRefAltSwap");
            if (recipe.referenceSpec().unmatched() == EagleReferenceInputs.Unmatched.KEEP_UNPHASED) args.add("--outputUnphased");
        }
        if (genotypes.missing() == EagleGenotypes.Missing.KEEP) args.add("--noImpMissing");
        if (genotypes.keepMissingPloidyX()) args.add("--keepMissingPloidyX");
        return List.copyOf(args);
    }

    static Result run(Input input, EagleRuntime.Context runtime, Runnable cancellation) throws IOException {
        input.verify(cancellation); var warnings = new ArrayList<>(input.warnings()); List<String> arguments = arguments(input);
        if (input.recipe().operation().equals("check")) {
            if (runtime != null) throw new IOException("Native input check does not consume an original runtime");
            warnings.add(0, "INPUT CHECK ONLY: no Eagle process was launched. Required action: review the source snapshots, complete input/selection/join ledgers and effective parameters; select phase and a NEW output directory when ready.");
            return new Result("check", null, null, null, null, arguments, warnings);
        }
        if (runtime == null) throw new EagleRuntime.Unavailable("No admitted original runtime supplied; analysis never installs dependencies");
        if (!runtime.profile().equals(input.recipe().profile())) throw new IOException("Requested Eagle profile differs from the admitted runtime");
        var process = EagleProcess.run(runtime, arguments, input.work().resolve("engine"), input.recipe().processLimits(), () -> input.verify(cancellation), cancellation);
        Path output = input.work().resolve("engine/result.vcf");
        String before = EagleCohortInputs.digest(output, input.recipe().processLimits().fileBytes(), cancellation); Object audit;
        if (input.cohort() != null) {
            var prepared = input.cohort(); var spec = prepared.spec();
            var complete = EagleVcfAudit.compare(prepared.snapshots().get(0).copy(), output, spec.genotypes(), spec.vcfLimits(),
                    row -> new EagleVcfAudit.Selection(spec.selected(row) ? EagleVcfAudit.Action.PHASED : EagleVcfAudit.Action.EXCLUDED, "explicit typed POS input selection"),
                    (source, actual, selection) -> { }, cancellation);
            if (complete.genotypes().rows() != prepared.selectedRows() || complete.genotypes().calls() != prepared.stage().calls()) throw new IOException("Complete cohort output population differs from admitted inputs");
            audit = complete;
        } else audit = EagleReferenceInputs.audit(input.reference(), output, cancellation);
        if (!before.equals(EagleCohortInputs.digest(output, input.recipe().processLimits().fileBytes(), cancellation))) throw new IOException("Original Eagle output changed during complete audit");
        input.verify(cancellation); runtime.verify();
        if (input.reference() != null) warnings.add("REFERENCE SEED SEMANTICS: original Eagle 2.4.1 ignores --seed in reference mode. Required action: retain the declared seed and this upstream limitation; it is not a promise of biological phase accuracy or cross-host identical phase.");
        warnings.add("SCIENTIFIC REVIEW REQUIRED: complete native dosage, GT/ploidy, identity/order, missing-call and EOF checks passed under the declared source policies. Required action: review assembly, panel/sample eligibility, region, alleles, map and all retained warnings. These checks do not certify biological phase accuracy, historical edition/server parity or whole-paper reproduction.");
        return new Result("phase", output, before, process, audit, arguments, warnings);
    }
}

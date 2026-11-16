/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.genetics.Pops;
import org.jlinalg.genetics.PopsChunked;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Isolated native CPU computation. Its receipt alone admits no workflow result. */
public final class PopsWorkflowWorker {
    private PopsWorkflowWorker() { }
    public static void main(String[] args) {
        Path root = null; int exit = 2;
        try {
            if (args.length != 1) throw new IllegalArgumentException("Owned workflow root required");
            root = Path.of(args[0]).toRealPath(); Config c = config(load(root.resolve("effective.properties")));
            PopsWorkflowCli.inputsUnchanged(root, c); PopsWorkflowCode.check(root);
            Files.createDirectory(root.resolve("working")); Files.createDirectory(root.resolve("results"));
            System.out.println("Binding captured inputs; native PoPS 0.2, portable Java CPU");
            var data = PopsWorkflowInputs.prepare(root, c, System.out::println); var covariance = data.covariance();
            if (c.magma() && covariance != null) {
                System.out.println("Regularizing complete MAGMA chromosome blocks");
                try (var context = BackendContext.select(BackendPolicy.CPU)) {
                    var adjusted = covariance.regularizeMagma(data.target(), context.backend()); covariance = adjusted.covariance();
                    data.diagnostics().put("magma_covariance_diagonal_shift", Double.toString(adjusted.diagonalShift()));
                    data.diagnostics().put("magma_covariance_minimum_eigenvalue_clipped_at_zero", Double.toString(adjusted.minimumEigenvalue()));
                    data.diagnostics().put("target_population_variance", Double.toString(adjusted.targetVariance()));
                }
            }
            System.out.println("Fitting complete selected model with bounded feature batches");
            var fit = PopsChunked.fit(data.target(), data.features(), data.targetRows(), data.covariates(), covariance,
                data.masks()[0], data.masks()[1], data.masks()[2], data.selection(), c.get("method").equals("ridge") ? Pops.Method.RIDGE : Pops.Method.MINIMUM_NORM_OLS,
                BackendPolicy.CPU, c.integer("max_selected_features"));
            PopsWorkflowResults.write(root, c, data, fit);
            PopsWorkflowCli.inputsUnchanged(root, c); PopsWorkflowCode.check(root);
            properties(root.resolve("WORKER_DONE.properties"), Map.of("profile", PROFILE, "target_genes", Integer.toString(data.targets().size()), "prediction_genes", Integer.toString(data.predictions().size()), "features", Integer.toString(data.features().columns()), "selected_features", Integer.toString(fit.selectedFeatures().length)));
            System.out.println("Native fit finished. Only the parent workflow can admit these provisional outputs.");
            System.out.flush(); if (System.out.checkError() || System.err.checkError()) throw new java.io.IOException("Worker console recording failed");
            exit = 0;
        } catch (Throwable error) {
            exit = error instanceof Prerequisite || error instanceof LinkageError ? 3 : 2;
            if (root != null) try { write(root.resolve("WORKER_FAILURE.txt"), error.toString() + "\nNo accepted workflow model. Retain this bundle, resolve the named cause, and retry NEW output.\n"); } catch (Throwable recording) { System.err.println("FAIL2_RECORDING_UNAVAILABLE " + recording); exit = 2; }
            System.err.println((exit == 3 ? "SKIP3" : "FAIL2") + "_POPS_WORKER " + error); error.printStackTrace(System.err);
        }
        System.exit(exit);
    }
}

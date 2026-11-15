/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.ArrayList;
import java.util.List;
import org.jlinalg.glm.Glm;
import org.jlinalg.glm.GlmFamily;
import org.jlinalg.glm.GlmOptions;
import org.jlinalg.glmm.GlmmLaplace;
import org.jlinalg.glmm.GlmmLaplaceOptions;
import org.jlinalg.glmm.SparseGlmmLaplace;
import org.jlinalg.mixed.RandomEffectTerm;
import org.jlinalg.mixed.SparseLinearMixedModel;
import org.jlinalg.mixed.SparsePrecisionMatrix;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;
import org.jlinalg.pipeline.ConditionalAssociationFitter;
import org.jlinalg.reml.Reml;
import org.jlinalg.reml.RemlOptions;
import org.jlinalg.reml.VarianceComponent;

/** Exact fits with weights, offsets and relatedness aligned to each model mask. */
final class ConditionalOmicsModels {
    private ConditionalOmicsModels() { }

    static ConditionalAssociationFitter independent(String model, GlmFamily family,
            double[] weights, double[] offset) {
        return (response, design, columns, rows, backend) -> {
            double[] w = subset(weights, rows), o = subset(offset, rows);
            if (model.equals("ols"))
                return Ols.fit(response, design, rows.length, columns, w, o,
                    OlsOptions.defaults(), backend).associationStatistics();
            var fit = Glm.fit(response, design, rows.length, columns, family, w, o,
                GlmOptions.defaults(), backend);
            converged(fit.converged());
            return fit.associationStatistics();
        };
    }

    static ConditionalAssociationFitter sparse(String model, GlmFamily family,
            List<RandomEffectTerm> terms, List<SparsePrecisionMatrix> precisions,
            double[] weights, double[] offset, RemlOptions reml) {
        return (response, design, columns, rows, backend) -> {
            List<RandomEffectTerm> selected = terms.stream().map(t -> subset(t, rows)).toList();
            if (model.equals("lmm")) {
                var fit = SparseLinearMixedModel.fitWithPrecision(adjust(response, offset, rows),
                    design, rows.length, columns, selected, precisions, reml, backend);
                converged(fit.converged());
                return fit.associationStatistics();
            }
            var fit = SparseGlmmLaplace.fitWithPrecision(response, design, rows.length,
                columns, family, selected, precisions, subset(weights, rows), subset(offset, rows),
                GlmmLaplaceOptions.defaults(), backend);
            converged(fit.converged());
            return fit.associationStatistics();
        };
    }

    static ConditionalAssociationFitter dense(String model, GlmFamily family,
            List<VarianceComponent> random, double[] weights, double[] offset, RemlOptions reml) {
        // Retain covariance arrays once; feature masks subset BOTH axes.
        List<double[]> covariance = random.stream().map(VarianceComponent::covariance).toList();
        return (response, design, columns, rows, backend) -> {
            List<VarianceComponent> selected = new ArrayList<>();
            for (int k = 0; k < random.size(); k++) {
                VarianceComponent component = random.get(k);
                double[] original = covariance.get(k), values = new double[rows.length * rows.length];
                for (int i = 0; i < rows.length; i++) for (int j = 0; j < rows.length; j++)
                    values[i * rows.length + j] = original[rows[i] * component.dimension() + rows[j]];
                selected.add(new VarianceComponent(component.name(), rows.length, values));
            }
            if (model.equals("lmm")) {
                selected.add(VarianceComponent.identity("residual", rows.length));
                var fit = Reml.fit(adjust(response, offset, rows), design, rows.length,
                    columns, selected, reml, backend);
                converged(fit.converged());
                return fit.associationStatistics();
            }
            var fit = GlmmLaplace.fit(response, design, rows.length, columns, family,
                selected, subset(weights, rows), subset(offset, rows), GlmmLaplaceOptions.defaults(), backend);
            converged(fit.converged());
            return fit.associationStatistics();
        };
    }

    private static void converged(boolean value) {
        if (!value) throw new IllegalArgumentException("conditional association model did not converge");
    }
    private static double[] subset(double[] values, int[] rows) {
        if (values == null) return null;
        double[] result = new double[rows.length];
        for (int i = 0; i < rows.length; i++) result[i] = values[rows[i]];
        return result;
    }
    private static double[] adjust(double[] response, double[] offset, int[] rows) {
        double[] result = response.clone();
        if (offset != null) for (int i = 0; i < rows.length; i++) result[i] -= offset[rows[i]];
        return result;
    }
    private static RandomEffectTerm subset(RandomEffectTerm term, int[] rows) {
        // Keep coefficient-space precision, including unobserved pedigree ancestors.
        if (term.sparse()) {
            int[] pointers = term.rowPointers(), indices = term.columnIndices();
            double[] values = term.sparseValues();
            int count = 0;
            for (int row : rows) count += pointers[row + 1] - pointers[row];
            int[] selectedPointers = new int[rows.length + 1], selectedIndices = new int[count];
            double[] selectedValues = new double[count];
            int position = 0;
            for (int i = 0; i < rows.length; i++) {
                selectedPointers[i] = position;
                for (int j = pointers[rows[i]]; j < pointers[rows[i] + 1]; j++) {
                    selectedIndices[position] = indices[j];
                    selectedValues[position++] = values[j];
                }
            }
            selectedPointers[rows.length] = position;
            return RandomEffectTerm.ofSparseCsr(term.name(), rows.length, term.coefficients(),
                selectedPointers, selectedIndices, selectedValues, term.coefficientNames());
        }
        double[] original = term.design(), selected = new double[rows.length * term.coefficients()];
        for (int i = 0; i < rows.length; i++)
            System.arraycopy(original, rows[i] * term.coefficients(), selected,
                i * term.coefficients(), term.coefficients());
        return RandomEffectTerm.of(term.name(), selected, rows.length, term.coefficients(), term.coefficientNames());
    }
}

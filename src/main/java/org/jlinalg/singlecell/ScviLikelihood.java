/* Copyright (C) 2026 OmicsMatic contributors
 * Portions derived from scvi-tools, BSD-3-Clause; see LICENSES/scvi-tools-BSD-3-Clause.txt.
 * SPDX-License-Identifier: BSD-3-Clause */
package org.jlinalg.singlecell;

import org.apache.commons.math3.special.Gamma;

/**
 * Scalar float64 re-evaluation of the default PyTorch scvi-tools 1.3.0 NB/ZINB kernels.
 * This is an audit primitive, not a variational autoencoder, fitting procedure, or classifier.
 * The upstream additive epsilon, operation order and softplus threshold are retained.
 * In particular, a zero mean with a positive count can have finite pseudomass.
 * Float32 training losses and float64 re-evaluations are different numerical quantities.
 * Large gamma-term cancellation is retained rather than replaced by a different NB formula.
 * Only exact nonnegative integer counts and finite parameters are admitted; nonfinite
 * intermediate values are rejected even when the original masks would multiply them by zero.
 */
public final class ScviLikelihood {
    /** Default numerical stability constant in the original likelihood kernels. */
    public static final double EPSILON = 1e-8;
    private static final long MAXIMUM_COUNT = 9_007_199_254_740_991L;

    private ScviLikelihood() { }

    /**
     * Value and a conditioning warning for downstream audit/reporting. The envelope is an
     * empirical float64 arithmetic diagnostic, not a proved error bound or accuracy guarantee.
     * A flagged evaluation must remain visible in warnings rather than being treated as parity.
     * @param logLikelihood literal source-style float64 evaluation
     * @param cancellationScale sum of absolute expression-term magnitudes
     * @param empiricalRoundingEnvelope larger of 2e-10 and 64 ulps at that scale
     * @param cancellationLimited whether the empirical envelope exceeds 1e-6
     */
    public record Audit(double logLikelihood, double cancellationScale,
                        double empiricalRoundingEnvelope, boolean cancellationLimited) { }

    /**
     * Evaluates the NB kernel and returns the conditioning diagnostic used by native audits.
     * @param count exact count
     * @param mean NB mean
     * @param inverseDispersion positive inverse dispersion
     * @return value plus an explicit conditioning warning
     */
    public static Audit auditNegativeBinomial(long count, double mean, double inverseDispersion) {
        return audit(logNegativeBinomial(count, mean, inverseDispersion), count, mean, inverseDispersion, 0);
    }

    /**
     * Evaluates the ZINB kernel and returns the conditioning diagnostic used by native audits.
     * @param count exact count
     * @param mean NB component mean
     * @param inverseDispersion positive inverse dispersion
     * @param zeroInflationLogit finite extra-zero logit
     * @return value plus an explicit conditioning warning
     */
    public static Audit auditZeroInflatedNegativeBinomial(long count, double mean,
                                                          double inverseDispersion,
                                                          double zeroInflationLogit) {
        return audit(logZeroInflatedNegativeBinomial(count, mean, inverseDispersion, zeroInflationLogit),
                count, mean, inverseDispersion, zeroInflationLogit);
    }

    /**
     * Returns the original-kernel negative-binomial log likelihood in float64 arithmetic.
     * @param count exact nonnegative integer count, at most 2^53-1
     * @param mean nonnegative NB mean before additive epsilon
     * @param inverseDispersion strictly positive inverse dispersion
     * @return log likelihood; extreme gamma cancellation can limit numerical accuracy
     * @throws IllegalArgumentException for an unsupported count, parameter or nonfinite calculation
     */
    public static double logNegativeBinomial(long count, double mean, double inverseDispersion) {
        validate(count, mean, inverseDispersion);
        double common = finite(Math.log(inverseDispersion + mean + EPSILON));
        double value = inverseDispersion * (Math.log(inverseDispersion + EPSILON) - common)
                + count * (Math.log(mean + EPSILON) - common)
                + logGamma(count + inverseDispersion)
                - logGamma(inverseDispersion)
                - logGamma((double) count + 1);
        return finite(value);
    }

    /**
     * Returns the original-kernel zero-inflated NB log likelihood in float64 arithmetic.
     * @param count exact nonnegative integer count, at most 2^53-1
     * @param mean nonnegative mean of the NB component, before additive epsilon
     * @param inverseDispersion strictly positive inverse dispersion
     * @param zeroInflationLogit finite logit of the extra-zero mixture probability
     * @return log likelihood, keeping the upstream epsilon and both finite branch calculations
     * @throws IllegalArgumentException for an unsupported count, parameter or nonfinite calculation
     */
    public static double logZeroInflatedNegativeBinomial(long count, double mean,
                                                         double inverseDispersion,
                                                         double zeroInflationLogit) {
        validate(count, mean, inverseDispersion); finite(zeroInflationLogit);
        double softplusPi = softplus(-zeroInflationLogit);
        double common = finite(Math.log(inverseDispersion + mean + EPSILON));
        double piThetaLog = finite(-zeroInflationLogit
                + inverseDispersion * (Math.log(inverseDispersion + EPSILON) - common));
        double zero = finite(softplus(piThetaLog) - softplusPi);
        double nonzero = finite(-softplusPi + piThetaLog
                + count * (Math.log(mean + EPSILON) - common)
                + logGamma(count + inverseDispersion)
                - logGamma(inverseDispersion)
                - logGamma((double) count + 1));
        return count == 0 ? zero : nonzero;
    }

    private static double softplus(double value) {
        // torch.nn.functional.softplus defaults: beta=1, threshold=20, strict > comparison.
        return finite(value > 20 ? value : Math.log1p(Math.exp(value)));
    }

    private static double logGamma(double value) { return finite(Gamma.logGamma(finite(value))); }

    private static Audit audit(double value, long count, double mean, double theta, double pi) {
        double common = Math.log(theta + mean + EPSILON);
        double scale = Math.abs(theta * (Math.log(theta + EPSILON) - common))
                + Math.abs(count * (Math.log(mean + EPSILON) - common))
                + Math.abs(logGamma(count + theta)) + Math.abs(logGamma(theta))
                + Math.abs(logGamma((double) count + 1)) + 2 * Math.abs(pi) + 1;
        double envelope = Math.max(2e-10, 64 * Math.ulp(scale));
        // An infinite scale/envelope remains an explicit warning, never a finite accuracy claim.
        return new Audit(value, scale, envelope, envelope > 1e-6);
    }

    private static void validate(long count, double mean, double inverseDispersion) {
        if (count < 0 || count > MAXIMUM_COUNT || !Double.isFinite(mean) || mean < 0
                || !Double.isFinite(inverseDispersion) || inverseDispersion <= 0) {
            throw new IllegalArgumentException("Require an exact nonnegative count <=2^53-1, finite mean>=0 and finite inverse dispersion>0");
        }
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite scVI likelihood parameter or intermediate; retain and review the original numeric failure");
        return value;
    }
}

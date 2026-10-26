/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jlinalg.meta.*;

/** The same declared options govern independent and known-covariance pooling. */
final class StudyMetaOptions {
    private StudyMetaOptions() { }
    static MetaAnalysisOptions resolve(Map<String,Object> config) {
        var builder=MetaAnalysisOptions.builder();
        builder.method(switch(text(config.getOrDefault("model","fixed")).toLowerCase(Locale.ROOT)){case "fixed"->MetaAnalysisMethod.FIXED_EFFECT;case "random"->MetaAnalysisMethod.RANDOM_EFFECT;default->throw new IllegalArgumentException("meta_analysis model must be fixed or random");});
        builder.tauSquaredEstimator(switch(text(config.getOrDefault("tau_squared_estimator","reml")).toLowerCase(Locale.ROOT)){case "reml"->TauSquaredEstimator.REML;case "dl"->TauSquaredEstimator.DERSIMONIAN_LAIRD;case "pm"->TauSquaredEstimator.PAULE_MANDEL;default->throw new IllegalArgumentException("tau_squared_estimator must be reml, dl or pm");});
        builder.inferenceMethod(switch(text(config.getOrDefault("inference","normal")).toLowerCase(Locale.ROOT)){case "normal"->MetaInferenceMethod.NORMAL;case "t"->MetaInferenceMethod.STUDENT_T;case "hk"->MetaInferenceMethod.HARTUNG_KNAPP;case "modified-hk"->MetaInferenceMethod.MODIFIED_HARTUNG_KNAPP;default->throw new IllegalArgumentException("inference must be normal, t, hk or modified-hk");});
        if(config.containsKey("confidence_level"))builder.confidenceLevel(number(config,"confidence_level"));
        if(config.containsKey("tolerance"))builder.tolerance(number(config,"tolerance"));
        if(config.containsKey("maximum_iterations")){double value=number(config,"maximum_iterations");if(value!=Math.rint(value)||value>Integer.MAX_VALUE)throw new IllegalArgumentException("maximum_iterations must be an integer");builder.maximumIterations((int)value);}
        return builder.build();
    }
    static void arguments(Map<String,Object> config,List<String> args){
        for(var pair:List.of(List.of("tau_squared_estimator","tau-estimator"),List.of("inference","inference"),List.of("confidence_level","confidence"),List.of("maximum_iterations","max-iterations"),List.of("tolerance","tolerance")))if(config.containsKey(pair.get(0)))args.addAll(List.of("--"+pair.get(1),pair.get(0).equals("maximum_iterations")?Integer.toString(((Number)config.get(pair.get(0))).intValue()):text(config.get(pair.get(0))).toLowerCase(Locale.ROOT)));
    }
    private static double number(Map<String,Object> config,String field){if(!(config.get(field)instanceof Number number)||!Double.isFinite(number.doubleValue()))throw new IllegalArgumentException("meta_analysis."+field+" must be a finite number");return number.doubleValue();}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.*;
import org.jlinalg.singlecell.ScviGeneProjection;

/** Closed scientific requests. Constructing/serializing one does not admit a runtime or model. */
final class ScviModelRequest {
    private ScviModelRequest() { }
    enum Release {
        V1_0_3("1.0.3"), V1_2_0("1.2.0"), V1_3_0("1.3.0");
        final String version; Release(String value){version=value;}
        static Release parse(String value){for(var r:values())if(r.version.equals(value))return r;throw invalid("Declare an exact supported scvi-tools release; no latest/version substitution");}
    }
    enum Family { SCVI, SCANVI, SYSVI }
    enum Likelihood { NB("nb"), ZINB("zinb"), POISSON("poisson");final String value;Likelihood(String x){value=x;} }
    enum Dispersion { GENE("gene"), GENE_BATCH("gene-batch"), GENE_LABEL("gene-label"), GENE_CELL("gene-cell");final String value;Dispersion(String x){value=x;} }
    enum Latent { NORMAL("normal"), LOGISTIC_NORMAL("ln");final String value;Latent(String x){value=x;} }
    enum Norm { NONE("none"), ENCODER("encoder"), DECODER("decoder"), BOTH("both");final String value;Norm(String x){value=x;} }
    enum Variance { FEATURE("feature"), SAMPLE_FEATURE("sample_feature");final String value;Variance(String x){value=x;} }
    enum Optimizer { ADAM("Adam"), ADAMW("AdamW");final String value;Optimizer(String x){value=x;} }
    enum WarmupUnit { NONE, EPOCHS, STEPS }
    enum StopMetric { ELBO_VALIDATION("elbo_validation"), RECONSTRUCTION_VALIDATION("reconstruction_loss_validation"), KL_LOCAL_VALIDATION("kl_local_validation");final String value;StopMetric(String x){value=x;} }
    enum Direction { MIN("min"), MAX("max");final String value;Direction(String x){value=x;} }
    sealed interface Network permits CountNetwork,SysviNetwork { int latent();Map<String,Object> kwargs(); }
    record CountNetwork(int hidden,int latent,int layers,double dropout,Likelihood likelihood,Dispersion dispersion,
                        Latent latentDistribution,boolean logVariational,boolean encodeCovariates,
                        boolean deeplyInjectCovariates,Norm batchNorm,Norm layerNorm,boolean observedLibrary) implements Network {
        CountNetwork{positive(hidden,"hidden");positive(latent,"latent");positive(layers,"layers");ScviModelRequest.dropout(dropout);Objects.requireNonNull(likelihood);Objects.requireNonNull(dispersion);Objects.requireNonNull(latentDistribution);Objects.requireNonNull(batchNorm);Objects.requireNonNull(layerNorm);}
        public Map<String,Object> kwargs(){var m=dimensions(hidden,latent,layers,dropout);m.put("gene_likelihood",likelihood.value);m.put("dispersion",dispersion.value);m.put("latent_distribution",latentDistribution.value);m.put("log_variational",logVariational);m.put("encode_covariates",encodeCovariates);m.put("deeply_inject_covariates",deeplyInjectCovariates);m.put("use_batch_norm",batchNorm.value);m.put("use_layer_norm",layerNorm.value);m.put("use_observed_lib_size",observedLibrary);return immutable(m);}
    }
    sealed interface Prior permits StandardNormal,Vamp { Map<String,Object> kwargs(); }
    record StandardNormal() implements Prior {public Map<String,Object> kwargs(){return Map.of("prior","standard_normal");}}
    record Vamp(int components,List<Integer> pseudoinputCellIndices) implements Prior {
        Vamp{positive(components,"Vamp components");pseudoinputCellIndices=List.copyOf(pseudoinputCellIndices);if(!pseudoinputCellIndices.isEmpty()&&pseudoinputCellIndices.size()!=components)throw invalid("Vamp pseudoinput population must equal its component count");for(int i:pseudoinputCellIndices)if(i<0)throw invalid("Vamp pseudoinput cell indices must be nonnegative");}
        public Map<String,Object> kwargs(){var m=new LinkedHashMap<String,Object>();m.put("prior","vamp");m.put("n_prior_components",components);if(!pseudoinputCellIndices.isEmpty())m.put("pseudoinputs_data_indices",pseudoinputCellIndices);return immutable(m);}
    }
    record SysviNetwork(int hidden,int latent,int layers,double dropout,Prior prior,boolean trainablePriors,
                        boolean embedCategoricalCovariates,Variance variance,boolean weightBatches) implements Network {
        SysviNetwork{positive(hidden,"hidden");positive(latent,"latent");positive(layers,"layers");ScviModelRequest.dropout(dropout);Objects.requireNonNull(prior);Objects.requireNonNull(variance);}
        public Map<String,Object> kwargs(){var m=dimensions(hidden,latent,layers,dropout);m.putAll(prior.kwargs());m.put("trainable_priors",trainablePriors);m.put("embed_categorical_covariates",embedCategoricalCovariates);m.put("out_var_mode",variance.value);return immutable(m);}
        void validatePopulation(int cells){positive(cells,"cells");if(prior instanceof Vamp v)for(int i:v.pseudoinputCellIndices())if(i>=cells)throw invalid("Vamp pseudoinput cell index outside the complete admitted cell population: "+i);}
    }
    sealed interface Classifier permits InheritedClassifier,NeuralClassifier,LinearClassifier { Map<String,Object> kwargs(); }
    record InheritedClassifier() implements Classifier {public Map<String,Object> kwargs(){return Map.of();}}
    record NeuralClassifier(int hidden,int layers,double dropout) implements Classifier {
        NeuralClassifier{positive(hidden,"classifier hidden");positive(layers,"classifier layers");ScviModelRequest.dropout(dropout);}
        public Map<String,Object> kwargs(){return Map.of("linear_classifier",false,"classifier_parameters",Map.of("n_hidden",hidden,"n_layers",layers,"dropout_rate",dropout));}
    }
    record LinearClassifier() implements Classifier {public Map<String,Object> kwargs(){return Map.of("linear_classifier",true);}}
    record Warmup(WarmupUnit unit,int count) {
        Warmup{Objects.requireNonNull(unit);if(unit==WarmupUnit.NONE?count!=0:count<1)throw invalid("Declare no KL warmup or a positive epoch/step warmup");}
        Map<String,Object> kwargs(){var m=new LinkedHashMap<String,Object>();m.put("n_epochs_kl_warmup",unit==WarmupUnit.EPOCHS?Integer.valueOf(count):unit==WarmupUnit.NONE?Integer.valueOf(0):null);m.put("n_steps_kl_warmup",unit==WarmupUnit.STEPS?Integer.valueOf(count):unit==WarmupUnit.NONE?Integer.valueOf(0):null);return immutable(m);}
    }
    record EarlyStopping(StopMetric metric,Direction direction,int patience,double minimumDelta,int checkEveryEpochs) {
        EarlyStopping{Objects.requireNonNull(metric);Objects.requireNonNull(direction);if(patience<0)throw invalid("Early-stop patience must be nonnegative");positive(checkEveryEpochs,"validation frequency");nonnegative(minimumDelta,"early-stop delta");}
    }
    record Training(int epochs,double trainFraction,Double validationFraction,boolean shuffleSplit,int batchSize,long seed,
                    Optimizer optimizer,double learningRate,double weightDecay,double epsilon,Warmup warmup,
                    double minimumKlWeight,double maximumKlWeight,EarlyStopping earlyStopping) {
        Training{
            positive(epochs,"epochs");positive(batchSize,"batch size");Objects.requireNonNull(optimizer);Objects.requireNonNull(warmup);
            if(!Double.isFinite(trainFraction)||trainFraction<=0||trainFraction>1)throw invalid("Training fraction must be in (0,1]");
            if(validationFraction!=null&&(!Double.isFinite(validationFraction)||validationFraction<0||validationFraction>1||trainFraction+validationFraction>1))throw invalid("Validation and training fractions must fit the complete population");
            if(seed<0||seed>4_294_967_295L)throw invalid("Seed must fit the upstream 32-bit seed range; no random replacement");
            finitePositive(learningRate,"learning rate");finitePositive(epsilon,"optimizer epsilon");nonnegative(weightDecay,"weight decay");nonnegative(minimumKlWeight,"minimum KL weight");nonnegative(maximumKlWeight,"maximum KL weight");if(maximumKlWeight<minimumKlWeight)throw invalid("KL weight bounds are reversed");
            if(earlyStopping!=null&&(validationFraction==null?1-trainFraction:validationFraction)<=0)throw invalid("Validation early stopping needs a declared nonempty validation fraction");
        }
        Map<String,Object> planKwargs(){var m=new LinkedHashMap<String,Object>();m.put("optimizer",optimizer.value);m.put("lr",learningRate);m.put("weight_decay",weightDecay);m.put("eps",epsilon);m.putAll(warmup.kwargs());m.put("min_kl_weight",minimumKlWeight);m.put("max_kl_weight",maximumKlWeight);return immutable(m);}
        Map<String,Object> kwargs(){var m=new LinkedHashMap<String,Object>();m.put("max_epochs",epochs);m.put("train_size",trainFraction);m.put("validation_size",validationFraction);m.put("shuffle_set_split",shuffleSplit);m.put("batch_size",batchSize);m.put("early_stopping",earlyStopping!=null);m.put("plan_kwargs",planKwargs());if(earlyStopping!=null){m.put("early_stopping_monitor",earlyStopping.metric.value);m.put("early_stopping_mode",earlyStopping.direction.value);m.put("early_stopping_patience",earlyStopping.patience);m.put("early_stopping_min_delta",earlyStopping.minimumDelta);m.put("check_val_every_n_epoch",earlyStopping.checkEveryEpochs);}return immutable(m);}
    }
    record Annotation(Classifier classifier,Training training,Integer samplesPerLabel) {
        Annotation{Objects.requireNonNull(classifier);Objects.requireNonNull(training);if(samplesPerLabel!=null)positive(samplesPerLabel,"samples per label");}
        Map<String,Object> protocol(){var m=new LinkedHashMap<String,Object>();m.put("model_kwargs",classifier.kwargs());var train=new LinkedHashMap<>(training.kwargs());train.put("n_samples_per_label",samplesPerLabel);m.put("training_kwargs",immutable(train));m.put("seed",training.seed);return immutable(m);}
    }
    /** Investigator-supplied declarations; numeric checks cannot establish expression provenance. */
    record InputReview(ScviMatrix.Kind kind,String suppliedLayer,String preprocessing,String geneSelection,
                       String cellPopulation,String evidenceAndDecision) {
        InputReview{Objects.requireNonNull(kind);text(suppliedLayer);text(preprocessing);text(geneSelection);text(cellPopulation);text(evidenceAndDecision);}
        Map<String,Object> protocol(){return Map.of("matrix_kind",kind.name(),"supplied_layer",suppliedLayer,"preprocessing",preprocessing,"gene_selection",geneSelection,"cell_population",cellPopulation,"evidence_and_decision",evidenceAndDecision,"declaration_does_not_prove_provenance",true);}
    }
    record Fit(Release release,Network network,Training training,Annotation annotation,InputReview inputReview) {
        Fit{
            Objects.requireNonNull(release);Objects.requireNonNull(network);Objects.requireNonNull(training);Objects.requireNonNull(inputReview);
            if(network instanceof SysviNetwork){if(release!=Release.V1_3_0)throw invalid("The declared SysVI profile requires scvi-tools 1.3.0; no historical substitution");if(inputReview.kind!=ScviMatrix.Kind.SIGNED_EXPRESSION||annotation!=null)throw invalid("SysVI uses declared signed/transformed expression and has no SCANVI classifier");if(training.warmup.unit!=WarmupUnit.NONE)throw invalid("SysVI 1.3.0 resets KL warmup to zero: explicitly declare NONE instead of silently changing the request");}
            else if(inputReview.kind!=ScviMatrix.Kind.RAW_COUNTS)throw invalid("SCVI/SCANVI count models require a reviewed raw-count layer");
            if(annotation!=null&&((CountNetwork)network).likelihood==Likelihood.POISSON)throw invalid("SCANVI supports NB/ZINB, not this Poisson SCVI likelihood");
        }
        Family family(){return network instanceof SysviNetwork?Family.SYSVI:annotation==null?Family.SCVI:Family.SCANVI;}
        void validateMetadata(ScviCovariates.Spec metadata){Objects.requireNonNull(metadata);boolean labeled=annotation!=null||network instanceof CountNetwork c&&c.dispersion==Dispersion.GENE_LABEL;if(labeled&&metadata.labelUse()!=ScviCovariates.Labels.REFERENCE)throw invalid("Reference annotation/gene-label dispersion requires admitted reference labels");if(!labeled&&metadata.labelUse()!=ScviCovariates.Labels.NONE)throw invalid("The selected fit has no label role; retain unselected label columns without training on them");}
        Map<String,Object> protocol(){var m=base(release,"FIT",family(),inputReview);m.put("model_kwargs",network.kwargs());m.put("training_kwargs",training.kwargs());m.put("seed",training.seed);m.put("annotation",annotation==null?null:annotation.protocol());if(network instanceof SysviNetwork s)m.put("setup_weight_batches",s.weightBatches);return immutable(m);}
    }
    sealed interface Freeze permits Unfrozen,SelectiveFreeze {Map<String,Object> kwargs();}
    record Unfrozen() implements Freeze {public Map<String,Object> kwargs(){return Map.of("unfrozen",true);}}
    record SelectiveFreeze(boolean dropout,boolean expression,boolean decoderFirstLayer,boolean batchnormEncoder,boolean batchnormDecoder,boolean classifier) implements Freeze {
        public Map<String,Object> kwargs(){return Map.of("unfrozen",false,"freeze_dropout",dropout,"freeze_expression",expression,"freeze_decoder_first_layer",decoderFirstLayer,"freeze_batchnorm_encoder",batchnormEncoder,"freeze_batchnorm_decoder",batchnormDecoder,"freeze_classifier",classifier);}
    }
    record Query(Release release,Family referenceFamily,ScviGeneProjection.Policy genePolicy,Freeze freeze,
                 Training training,InputReview inputReview) {
        Query{Objects.requireNonNull(release);Objects.requireNonNull(referenceFamily);Objects.requireNonNull(genePolicy);Objects.requireNonNull(freeze);Objects.requireNonNull(training);Objects.requireNonNull(inputReview);if(referenceFamily==Family.SYSVI)throw invalid("This SysVI family has no declared scArches query-transfer capability");if(genePolicy==ScviGeneProjection.Policy.SHARED_BEFORE_TRAINING)throw invalid("A fitted reference cannot be shrunk; select shared genes before reference training");if(inputReview.kind!=ScviMatrix.Kind.RAW_COUNTS)throw invalid("Count-model query transfer requires a reviewed raw-count layer");}
        Map<String,Object> protocol(){var m=base(release,"QUERY",referenceFamily,inputReview);m.put("gene_policy",genePolicy.name());m.put("load_query_kwargs",freeze.kwargs());m.put("training_kwargs",training.kwargs());m.put("seed",training.seed);m.put("reference_model_trust_required",true);return immutable(m);}
    }
    private static LinkedHashMap<String,Object> base(Release release,String operation,Family family,InputReview review){var m=new LinkedHashMap<String,Object>();m.put("schema","omicsmatic-scvi-scientific-request-v1");m.put("release",release.version);m.put("operation",operation);m.put("family",family.name());m.put("input_review",review.protocol());m.put("runtime_admission_required",true);m.put("model_analysis_completed",false);return m;}
    private static LinkedHashMap<String,Object> dimensions(int hidden,int latent,int layers,double dropout){var m=new LinkedHashMap<String,Object>();m.put("n_hidden",hidden);m.put("n_latent",latent);m.put("n_layers",layers);m.put("dropout_rate",dropout);return m;}
    // Map.of iteration order varies between JVM processes. Scientific request hashes must not.
    private static Map<String,Object> immutable(Map<?,?> m){var copy=new TreeMap<String,Object>();for(var e:m.entrySet()){if(!(e.getKey() instanceof String key))throw invalid("Scientific request keys must be strings");copy.put(key,canonical(e.getValue()));}return Collections.unmodifiableMap(copy);}
    private static Object canonical(Object value){if(value instanceof Map<?,?> m)return immutable(m);if(value instanceof List<?> list){var copy=new ArrayList<Object>();for(Object x:list)copy.add(canonical(x));return Collections.unmodifiableList(copy);}return value;}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
    private static void positive(int n,String role){if(n<1)throw invalid("Positive "+role+" required");}
    private static void finitePositive(double n,String role){if(!Double.isFinite(n)||n<=0)throw invalid("Finite positive "+role+" required");}
    private static void nonnegative(double n,String role){if(!Double.isFinite(n)||n<0)throw invalid("Finite nonnegative "+role+" required");}
    private static void dropout(double n){if(!Double.isFinite(n)||n<0||n>1)throw invalid("Dropout must be in [0,1]");}
    private static void text(String s){if(s==null||s.isBlank()||s.length()>16_384||s.indexOf('\0')>=0)throw invalid("Explicit bounded expression/gene/population provenance review required");}
}

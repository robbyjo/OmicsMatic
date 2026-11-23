/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Explicit source options for original GRN/CTX and the native pySCENIC scorer. */
final class ScenicWorkflowContracts {
    private ScenicWorkflowContracts(){ }
    static final String PROFILE="pyscenic-0.12.1-arboreto-0.1.6-ctxcore-0.2.0-ledger-v1";
    static final String COMPLETE="COMPLETE_SCOPED_SCENIC_EXECUTION";
    static final String ADMISSION="Numeric activity only where eligibility.bin=1; complete captures may retain explicitly allowed empty or unreportable outcomes. Original network/module/motif outputs have source task ledgers. No biological or whole-paper validity is established.";
    static final String SCOPE="Prepared literal expression matrices and explicitly supplied local TF, motif annotation and Feather-v2 ranking inputs. Original GRNBoost2/GENIE3 and source CTX functions plus native Java pySCENIC AUCell; no automatic filtering, normalization, reference downloads, cell annotation, binarization, SCENIC+, historical-edition equivalence or whole-paper/biological validation.";
    static final String ACTION="Read WARNINGS.md, analysis-status.json, target/module ledgers and every activity admission flag. SKIP3: install/restore the exact named permitted runtime/input, or retain the skipped dependent analysis. FAIL2: no complete result admitted; preserve original/raw/partial/failed/undefined evidence, resolve the named source/input/resource/integrity error and retry NEW output. Never silently repair identifiers, substitute NA/NaN/Inf, infer settings or reduce data. SHA256 establishes integrity, not execution authenticity.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static Map<String,Object> validate(Map<String,Object> raw,String base,String style)throws IOException {
        keys(raw,Set.of("schema_version","mode","engine","profile","operation","python","out","inputs","options","limits","policies"));
        integer(raw.get("schema_version"),1,1);choice(raw,"mode",Set.of("RUN"));choice(raw,"engine",Set.of("upstream-and-native"));
        choice(raw,"profile",Set.of(PROFILE));choice(raw,"operation",Set.of("NETWORK"));
        var result=new LinkedHashMap<>(raw);result.put("out",resolve(base,text(raw.get("out"),"out"),style));result.put("python",resolve(base,text(raw.get("python"),"python"),style));
        var profile=ScenicWorkflowRuntime.profile();result.put("expected_python",profile.get("python"));result.put("expected_pyscenic","0.12.1");result.put("expected_packages",profile.get("expected_packages"));
        var input=object(raw.get("inputs"));keys(input,Set.of("fit_expression","activity_expression","tfs","annotations","databases"));var normalized=map();
        for(String name:List.of("fit_expression","activity_expression","tfs","annotations"))normalized.put(name,resolve(base,text(input.get(name),name),style));
        var databases=new ArrayList<Object>();var names=new java.util.HashSet<String>();
        for(Object value:list(input.get("databases"))){var db=object(value);keys(db,Set.of("name","file"));String name=label(db.get("name"));require(names.add(name),"Duplicate literal database name");databases.add(map("name",name,"file",resolve(base,text(db.get("file"),"database file"),style)));}
        require(!databases.isEmpty()&&databases.size()<=64,"Declare1..64 explicit ranking databases");normalized.put("databases",databases);result.put("inputs",normalized);
        var options=raw.containsKey("options")?object(raw.get("options")):map();keys(options,Set.of("method","seed","workers","modules","ctx","activity"));
        require(options.containsKey("seed"),"Declare an explicit uint32 GRN seed; unknown paper seeds are not inferred");
        String method=options.getOrDefault("method","GRNBOOST2").toString();require(Set.of("GRNBOOST2","GENIE3").contains(method),"Unknown original GRN method");long seed=integer(options.get("seed"),0,0xffff_ffffL);
        var modules=options.containsKey("modules")?object(options.get("modules")):map();keys(modules,Set.of("thresholds","top_n_targets","top_n_regulators","min_genes","absolute_thresholds","rho_dichotomize","keep_only_activating","rho_threshold","rho_mask_dropouts"));
        boolean absolute=bool(modules,"absolute_thresholds",false);
        var moduleOptions=map("thresholds",decimals(modules.getOrDefault("thresholds",List.of(.75,.90)),absolute?Double.POSITIVE_INFINITY:1),
            "top_n_targets",integers(modules.getOrDefault("top_n_targets",List.of(50))),"top_n_regulators",integers(modules.getOrDefault("top_n_regulators",List.of(5,10,50))),
            "min_genes",bound(modules,"min_genes",20,0,10_000_000),"absolute_thresholds",absolute,"rho_dichotomize",bool(modules,"rho_dichotomize",true),
            "keep_only_activating",bool(modules,"keep_only_activating",true),"rho_threshold",finite(modules,"rho_threshold",.03,0,1),"rho_mask_dropouts",bool(modules,"rho_mask_dropouts",false));
        require(!list(moduleOptions.get("thresholds")).isEmpty()||!list(moduleOptions.get("top_n_targets")).isEmpty()||!list(moduleOptions.get("top_n_regulators")).isEmpty(),"Enable at least one explicit original coexpression heuristic");
        var ctx=options.containsKey("ctx")?object(options.get("ctx")):map();keys(ctx,Set.of("rank_threshold","auc_threshold","nes_threshold","motif_similarity_fdr","orthologuous_identity_threshold","weighted_recovery","filter_for_annotation"));
        var ctxOptions=map("rank_threshold",bound(ctx,"rank_threshold",1500,1,10_000_000),"auc_threshold",finite(ctx,"auc_threshold",.05,Double.MIN_VALUE,1),
            "nes_threshold",finite(ctx,"nes_threshold",3,-Double.MAX_VALUE,Double.MAX_VALUE),"motif_similarity_fdr",finite(ctx,"motif_similarity_fdr",.001,0,1),
            "orthologuous_identity_threshold",finite(ctx,"orthologuous_identity_threshold",0,0,1),"weighted_recovery",bool(ctx,"weighted_recovery",false),"filter_for_annotation",bool(ctx,"filter_for_annotation",true));
        var activity=options.containsKey("activity")?object(options.get("activity")):map();keys(activity,Set.of("auc_threshold","no_weights","normalize","seed","layout"));
        String layout=activity.getOrDefault("layout","SERIAL_UNSTACK").toString();require(Set.of("SERIAL_UNSTACK","MULTIPROCESS_INPUT_ORDER").contains(layout),"Unknown source activity layout");
        var activityOptions=map("auc_threshold",finite(activity,"auc_threshold",.05,Double.MIN_VALUE,1),"no_weights",bool(activity,"no_weights",false),"normalize",bool(activity,"normalize",false),
            "seed",activity.containsKey("seed")?integer(activity.get("seed"),0,0xffff_ffffL):seed,"layout",layout);
        result.put("options",map("method",method,"seed",seed,"workers",bound(options,"workers",1,1,32),"modules",moduleOptions,"ctx",ctxOptions,"activity",activityOptions));
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();
        var defaults=map("maximum_member_bytes",8_589_934_592L,"maximum_input_bytes",17_179_869_184L,"maximum_output_bytes",34_359_738_368L,
            "maximum_files",100_000L,"timeout_seconds",7200L,"ack_timeout_seconds",600L,"threads",1L,"maximum_cells",1_000_000L,"maximum_features",100_000L,
            "maximum_dense_entries",32_000_000L,"maximum_line_bytes",16_777_216L,"maximum_working_bytes",4_294_967_296L,"maximum_work",2_000_000_000L,
            "maximum_modules",100_000L,"maximum_motifs",1_000_000L,"maximum_database_entries",2_000_000_000L,"maximum_motif_rows",100_000L,
            "maximum_activity_entries",32_000_000L,"maximum_native_work",2_000_000_000L,"maximum_activity_batch_cells",128L);
        keys(limits,defaults.keySet());var capacities=map();for(var entry:defaults.entrySet())capacities.put(entry.getKey(),bound(limits,entry.getKey(),((Number)entry.getValue()).longValue(),1,1_000_000_000_000_000_000L));
        require(number(capacities,"threads")<=32&&number(capacities,"maximum_features")<=1_000_000&&number(capacities,"maximum_cells")<=10_000_000&&number(capacities,"maximum_line_bytes")<=67_108_864,"Requested dimensions/threads/line width exceed supported bounds");
        require(number(capacities,"timeout_seconds")<=604800&&number(capacities,"ack_timeout_seconds")<=86400&&number(capacities,"maximum_files")<=1_000_000,"Requested process/namespace bounds exceed supported limits");
        require(number(capacities,"maximum_activity_batch_cells")<=8192,"At most8192 activity cells per numeric working batch");
        require(number(object(result.get("options")),"workers")<=number(capacities,"threads"),"Workers exceed declared threads; explicitly raise both rather than changing execution silently");result.put("limits",capacities);
        var policies=raw.containsKey("policies")?object(raw.get("policies")):map();keys(policies,Set.of("allow_unreportable_activity","allow_empty_regulons"));
        result.put("policies",map("allow_unreportable_activity",bool(policies,"allow_unreportable_activity",false),"allow_empty_regulons",bool(policies,"allow_empty_regulons",false)));return result;
    }
    static double finite(Map<String,Object> data,String key,double fallback,double minimum,double maximum)throws IOException {double value=decimal(data.getOrDefault(key,fallback)).doubleValue();require(Double.isFinite(value)&&value>=minimum&&value<=maximum,"Invalid finite original parameter "+key);return value;}
    static List<Object> decimals(Object value,double maximum)throws IOException {var result=new ArrayList<Object>();for(Object item:list(value)){double v=decimal(item).doubleValue();require(Double.isFinite(v)&&v>=0&&v<=maximum,"Invalid source module threshold");result.add(v);}require(result.size()<=64,"At most64 explicit module thresholds; empty disables this heuristic");return result;}
    static List<Object> integers(Object value)throws IOException {var result=new ArrayList<Object>();for(Object item:list(value))result.add(integer(item,1,10_000_000));require(result.size()<=64,"At most64 positive top-N choices; empty disables this heuristic");return result;}
    static boolean bool(Map<String,Object> data,String key,boolean fallback)throws IOException {Object value=data.getOrDefault(key,fallback);require(value instanceof Boolean,"Require Boolean "+key);return (Boolean)value;}
    static String label(Object value)throws IOException {String name=text(value,"literal label");require(!name.isEmpty()&&name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=65536&&name.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Invalid literal label");return name;}
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    static String packageKey(String name){return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[-_.]+","-");}
    static String address(Path path){return MrPressoWorkflowContracts.address(path);}
    static String style(){return MrPressoWorkflowContracts.pathStyle();}
    static String resolve(String base,String value,String style)throws IOException{return MrPressoWorkflowContracts.resolveAddress(base,value,style);}
}

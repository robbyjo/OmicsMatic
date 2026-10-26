/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudySurvivalPolicy;
import org.jlinalg.survival.CoxSurvivalData;

/** Local survival endpoint validation; no participant records enter aggregate reports. */
final class StudySurvivalSupport {
    private StudySurvivalSupport() { }
    static final String CAVEAT="Independent-participant baseline-covariate Cox partial-likelihood inference is asymptotic. Event/censoring, delayed-entry, proportional-hazards and conditional independent-censoring assumptions require scientific review. Log hazard ratios are not log odds ratios, risk ratios or causal effects. A PH diagnostic is not proof of proportional hazards; its log-time transform is separate from the primary association test. No sparse-event tail correction, competing-risk, recurrent-event, time-varying exposure, kinship/frailty or cluster model is inferred. Offsets and mean-imputed predictors are fixed; coefficient and genotype/imputation uncertainty are not propagated. Meta-analysis using log-HR and ordinary-Fisher SE is a separate Wald approximation even when primary association P values use LRTs. The native signed study adapter additionally requires an exact risk-contrast certificate for a finite coefficient maximum; compatible supplied statistics retain upstream attribution. Mathematical coefficient existence is not tail calibration or validation of model assumptions.";
    record Population(List<String> ids,CoxSurvivalData response,Map<String,Object> summary,List<String[]> strataRows) { }
    static String column(Map<String,Object> columns,Map<String,Object> policy,String field) {
        String name=text(policy.get(field));return text(map(columns.get(field.equals("strata_column")?"covariates":"phenotypes")).getOrDefault(name,name));
    }
    static Result prerequisite(Context c,Map<String,Object> policy)throws IOException {
        String missing=StudySurvivalPolicy.missing(policy);
        return missing.isEmpty()?null:waiting(c,"Declare association.survival fields before analysis: "+missing+". Endpoint/assumption text records a scientist's model specification; it is not inferred from event distributions or literature hits.");
    }
    static Result waiting(Context c,String reason)throws IOException {
        Files.createDirectories(c.outputDirectory());
        var need=StudyNeed.review("survival:"+c.operation().get("hypothesis")+":"+c.operation().getOrDefault("dataset_id","study")+":"+StudyFindingInventory.hash(reason).substring(0,16),"Resolve the survival-analysis prerequisite",reason,true);
        var details=Map.<String,Object>of("reason",reason,"needs",List.of(need.document()),"primary_selection_eligible",false,"required_manuscript_caveat",CAVEAT,"method_report",Map.of("performed",false));
        write(c.outputDirectory().resolve("request.yaml"),details);
        return new Result(State.WAITING_FOR_INPUT,reason,outputs(c),details);
    }
    static List<String> outputs(Context c)throws IOException {
        try(var files=Files.walk(c.outputDirectory())){return files.filter(Files::isRegularFile).map(c.outputDirectory()::relativize).map(p->p.toString().replace('\\','/')).sorted().toList();}
    }
    static Population population(DelimitedData table,List<String[]> rows,Map<String,Object> columns,Map<String,Object> policy)throws IOException {
        int n=rows.size();if(n==0)throw new IOException("No survival participants remain");
        var used=new java.util.HashSet<String>();var mapped=new LinkedHashMap<String,Integer>();
        for(String key:List.of("time_column","event_column","entry_column","strata_column"))if(policy.containsKey(key)){
            String name=column(columns,policy,key);if(!used.add(name))throw new IOException("Survival columns map to the same physical column");mapped.put(key,table.column(name));
        }
        int id=table.column(text(columns.getOrDefault("sample_id","id")));var ids=new ArrayList<String>();var unique=new java.util.HashSet<String>();
        double[] start=new double[n],stop=new double[n];boolean[] event=new boolean[n];int[] strata=new int[n];
        var levels=new LinkedHashMap<String,Integer>();int events=0,delayed=0;double personTime=0;
        for(int i=0;i<n;i++){
            String[] row=rows.get(i);String key=row[id];if(key.isBlank()||!unique.add(key))throw new IOException("Independent survival data require unique nonblank participant IDs");ids.add(key);
            stop[i]=finite(row[mapped.get("time_column")]);start[i]=mapped.containsKey("entry_column")?finite(row[mapped.get("entry_column")]):0;
            if(!(start[i]>=0&&stop[i]>start[i]))throw new IOException("Survival intervals require finite 0 <= entry < time; interval-censored and recurrent rows are unsupported");
            String status=row[mapped.get("event_column")].strip();
            if(StudySurvivalPolicy.sameCode(status,policy.get("event_value")))event[i]=true;
            else if(!StudySurvivalPolicy.sameCode(status,policy.get("censor_value")))throw new IOException("Survival event status is outside the declared event/censor codes");
            String stratum=mapped.containsKey("strata_column")?row[mapped.get("strata_column")]:"all";
            if(stratum.isBlank())throw new IOException("Missing survival stratum");strata[i]=levels.computeIfAbsent(stratum,ignored->levels.size());
            if(event[i])events++;if(start[i]>0)delayed++;personTime+=stop[i]-start[i];
        }
        if(!Double.isFinite(personTime))throw new IOException("Total at-risk time is not representable");
        if(events<((Number)policy.get("minimum_events")).intValue())throw new IOException("Analyzed survival event count is below the registered minimum_events");
        var response=new CoxSurvivalData(start,stop,event,strata);var summary=new LinkedHashMap<String,Object>();
        summary.put("analyzed_samples",n);summary.put("events",events);summary.put("censored",n-events);summary.put("strata",levels.size());
        summary.put("delayed_entry_rows",delayed);summary.put("total_at_risk_time",personTime);summary.put("time_units",policy.get("time_units"));
        summary.put("interval_convention","entry < event_time <= stop, within baseline stratum; one interval and at most one event per distinct participant");
        summary.put("required_manuscript_caveat",CAVEAT);var strataRows=new ArrayList<String[]>();
        for(int s=0;s<levels.size();s++){int size=0,count=0;double exposure=0;for(int i=0;i<n;i++)if(strata[i]==s){size++;if(event[i])count++;exposure+=stop[i]-start[i];}strataRows.add(new String[]{Integer.toString(s),Integer.toString(size),Integer.toString(count),Integer.toString(size-count),Double.toString(exposure)});}
        return new Population(List.copyOf(ids),response,summary,strataRows);
    }
    private static double finite(String value)throws IOException {try{double result=Double.parseDouble(value);if(!Double.isFinite(result))throw new NumberFormatException();return result;}catch(NumberFormatException error){throw new IOException("Survival times must be finite numeric values",error);}}
}

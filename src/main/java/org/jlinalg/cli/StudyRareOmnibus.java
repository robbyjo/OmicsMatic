/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.enrichment.EnrichmentAnalysis;
import org.jlinalg.settest.Acat;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyRareOmnibusPolicy;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Prespecified cross-mask set/gene omnibus; source mask families remain immutable. */
final class StudyRareOmnibus implements StudyOperationExecutor {
    static final String CAVEAT="An omnibus tests the union of its registered component alternatives. Bonferroni minimum-P is conservative for valid marginal P values under arbitrary component dependence. ACAT is a Cauchy-tail approximation, not universal finite-sample calibration; source P=1 uses the prespecified Bonferroni fallback. Neither method repairs invalid component tests. Gene grouping requires an explicit reviewed mapping; identical labels do not independently certify biological identity. Omnibus P values have no aggregate beta, SE, effect direction, Q or I-squared. Original mask/test corrections remain distinct. Missing/unresolved components do not become P=1, and incomplete families do not acquire primary gene/set FDR. Separate source families do not establish independent replication or causality.";
    static final List<String> COLUMNS=List.of("scope","feature_id","mask_id","test","result_type","status","p_value","log_p_value","adjusted_p","log_adjusted_p","significant","is_primary_test","unit_type","expected_components","component_count","ineligible_components","unresolved_components","effective_method","source_set_ids","testing_denominator");
    private record Key(String scope,String set,String mask,String test) { }
    private record Input(Key key,double logP,String status,boolean primary) { }
    private record Mapping(String id,String symbol) { }
    private static final class Unit {
        final String scope,id;final Set<String> sets=new TreeSet<>();final List<Double> logs=new ArrayList<>();int expected,ineligible,unresolved;boolean mapped=true;double logP=Double.NaN,logQ=Double.NaN;String method="",status="unresolved_components";
        Unit(String scope,String id){this.scope=scope;this.id=id;}
    }
    static Map<String,Object> policy(Context c){return StudyRareOmnibusPolicy.resolve(map(StudyResultTables.analysis(c).get("rare_omnibus")));}
    @Override public Result execute(Context c)throws Exception {
        Files.createDirectories(c.outputDirectory());var p=policy(c);if(Boolean.FALSE.equals(p.get("enabled")))return new Result(State.NOT_APPLICABLE,"Rare omnibus is disabled",List.of(),Map.of("effective_policy",p,"method_report",Map.of("performed",false)));var source=StudyRareFollowup.sourceMask(c);if(source==null)return waiting(c,"Supply one sealed rare_variant_analysis export; no significant-only or alternate-source fallback is selected");
        var outputs=new ArrayList<String>();Path snapshot=copy(source.results(),c.outputDirectory().resolve("source-rare-results.tsv"));if(!FollowupSupport.hash(snapshot).equals(map(source.manifest().get("output_sha256")).get(source.results().getFileName().toString())))throw new IOException("Rare component snapshot differs from its sealed producer output");outputs.add("source-rare-results.tsv");copy(source.directory().resolve("manifest.yaml"),c.outputDirectory().resolve("source-rare-manifest.yaml"));outputs.add("source-rare-manifest.yaml");
        if(over(snapshot,integer(p,"maximum_rows")))return finish(c,p,source,new ArrayList<>(),new ArrayList<>(),List.of("Complete rare-set export exceeds maximum_rows; no top-hit prefix was analyzed"),outputs);
        var table=DelimitedData.readResult(snapshot);for(String field:List.of("feature_id","mask_id","test","p_value","is_primary_test"))table.column(field);
        var input=new LinkedHashMap<Key,Input>();var sets=new TreeSet<String>();var masks=new LinkedHashSet<String>();var tests=new LinkedHashSet<String>();var scopes=new LinkedHashSet<String>();var primaryTests=new LinkedHashMap<List<String>,String>();
        for(String[] row:table.rows()){
            String scope=table.header().contains("scope")?row[table.column("scope")]:"primary",set=row[table.column("feature_id")],mask=row[table.column("mask_id")],test=row[table.column("test")];for(String label:List.of(scope,set,mask,test))label(label);
            String flag=row[table.column("is_primary_test")];if(!Set.of("true","false").contains(flag.toLowerCase(java.util.Locale.ROOT)))throw new IOException("Invalid rare component primary-test flag");boolean primary=Boolean.parseBoolean(flag);
            String status=table.header().contains("status")?row[table.column("status")]:"ok";double logP=probability(table,row);var key=new Key(scope,set,mask,test);if(input.putIfAbsent(key,new Input(key,logP,status,primary))!=null)throw new IOException("Duplicate source scope/set/mask/test component");
            sets.add(set);masks.add(mask);tests.add(test);scopes.add(scope);if(scope.equals("primary")&&primary){String previous=primaryTests.putIfAbsent(List.of(set,mask),test);if(previous!=null&&!previous.equals(test))throw new IOException("A source set/mask has multiple primary tests");}
        }
        var ledger=ledger(c,p,outputs);for(var entry:ledger.entrySet()){sets.add(entry.getKey().set);masks.add(entry.getKey().mask);tests.add(entry.getKey().test);if(!entry.getKey().scope.equals("*"))scopes.add(entry.getKey().scope);}
        var sourcePolicy=map(map(source.manifest().get("details")).get("effective_policy"));
        var registeredMasks=list(p.get("masks")).stream().map(StudyPublicEvidence::text).toList();if(registeredMasks.isEmpty())registeredMasks=list(sourcePolicy.get("masks")).stream().map(StudyPublicEvidence::map).map(m->text(m.get("id"))).toList();if(!registeredMasks.isEmpty()){if(!masks.containsAll(registeredMasks)&&sets.isEmpty())return waiting(c,"No registered component universe is available");masks=new LinkedHashSet<>(registeredMasks);}
        var registeredTests=list(p.get("tests")).stream().map(StudyPublicEvidence::text).toList();if(registeredTests.isEmpty())registeredTests=list(sourcePolicy.get("tests")).stream().map(StudyPublicEvidence::text).toList();if(!registeredTests.isEmpty())tests=new LinkedHashSet<>(registeredTests);
        String primaryDefault=text(map(map(source.manifest().get("details")).get("primary_identity")).get("test"));if(primaryDefault.isBlank())primaryDefault=text(sourcePolicy.get("primary_test"));if(primaryDefault.isBlank()&&new LinkedHashSet<>(primaryTests.values()).size()==1)primaryDefault=primaryTests.values().iterator().next();
        var mappings=mappings(c,p,source,sets,outputs);var expectedScopes=new LinkedHashSet<String>(scopes);expectedScopes.add("primary");var roles=map(StudyBioinformatics.hypothesis(c).get("inputs"));if(!list(roles.get("discovery")).isEmpty())expectedScopes.add("discovery");for(Object id:list(roles.get("replication")))expectedScopes.add("cohort:"+id);
        long work=(long)expectedScopes.size()*sets.size()*masks.size()*(p.get("component_tests").equals("all")?tests.size():1);if(work>integer(p,"maximum_grid_work"))return finish(c,p,source,new ArrayList<>(),new ArrayList<>(),List.of("Complete component grid exceeds maximum_grid_work; no favorable unit prefix was selected"),outputs);
        var units=new LinkedHashMap<List<String>,Unit>();var components=new ArrayList<String[]>();var needs=new LinkedHashSet<String>();
        for(String field:List.of("gene_map","component_eligibility"))if(p.containsKey(field)&&!Files.isRegularFile(c.project().resolve(text(p.get(field)))))needs.add("The registered "+field+" file is missing; delivery is required before primary selection");
        for(String scope:expectedScopes)for(String set:sets){Mapping mapping=mappings.get(set);String id=p.get("unit").equals("set")?set:mapping==null?"unmapped:"+set:mapping.id;var unit=units.computeIfAbsent(List.of(scope,id),key->new Unit(scope,id));unit.sets.add(set);unit.mapped&=p.get("unit").equals("set")||mapping!=null;
            for(String mask:masks){String chosen=primaryTests.getOrDefault(List.of(set,mask),primaryDefault);List<String> selected=p.get("component_tests").equals("all")?List.copyOf(tests):chosen.isBlank()?List.of("unresolved_primary_test"):List.of(chosen);
                for(String test:selected){unit.expected++;Key key=new Key(scope,set,mask,test);Input row=input.get(key);String excluded=ledger.getOrDefault(key,ledger.get(new Key("*",set,mask,test)));String disposition;
                    if(excluded!=null){if(row!=null&&(!independentExclusion(row.status)||Double.isFinite(row.logP)))throw new IOException("An eligibility ledger cannot discard a tested/failed component based on its result");unit.ineligible++;disposition="independently_ineligible:"+excluded;}
                    else if(row!=null&&independentExclusion(row.status)){if(Double.isFinite(row.logP))throw new IOException("An ineligible component carries a finite P value");unit.ineligible++;disposition="source_independently_ineligible:"+row.status;}
                    else if(row!=null&&valid(row.status)&&Double.isFinite(row.logP)){unit.logs.add(row.logP);disposition="included";}
                    else {unit.unresolved++;disposition=row==null?"missing_registered_component":"unresolved_component_probability_or_status";}
                    components.add(new String[]{scope,id,set,mask,test,disposition,row==null?"NA":string(row.logP)});
                }
            }
        }
        if(units.size()>integer(p,"maximum_units"))return finish(c,p,source,new ArrayList<>(),components,List.of("Complete unit inventory exceeds maximum_units; no selected unit prefix was analyzed"),outputs);
        for(Unit unit:units.values()){
            if(unit.expected>integer(p,"maximum_components")){needs.add("A complete unit exceeds maximum_components");continue;}
            if(!unit.mapped){unit.status="unmapped_gene";needs.add("Every registered set needs one reviewed gene mapping before gene-family correction");continue;}
            if(unit.unresolved>0){needs.add("Registered component coverage/probabilities remain unresolved; no successful-component substitution");continue;}
            if(unit.logs.isEmpty()){unit.status="not_eligible";continue;}
            var combined=combine(unit.logs,p.get("method").toString());unit.logP=combined.logP;unit.method=combined.method;unit.status="ok";
        }
        var families=new ArrayList<Map<String,Object>>();
        boolean filesComplete=List.of("gene_map","component_eligibility").stream().noneMatch(field->p.containsKey(field)&&!Files.isRegularFile(c.project().resolve(text(p.get(field)))));
        for(String scope:expectedScopes){var family=units.values().stream().filter(u->u.scope.equals(scope)).toList();boolean complete=filesComplete&&sourceComplete(source,table,scope)&&family.stream().allMatch(u->u.status.equals("ok")||u.status.equals("not_eligible"));var eligible=family.stream().filter(u->u.status.equals("ok")).toList();
            if(complete){double[] adjusted=EnrichmentAnalysis.adjustLog(eligible.stream().mapToDouble(u->u.logP).toArray(),EnrichmentAnalysis.Fdr.valueOf(text(p.get("adjustment"))));for(int i=0;i<eligible.size();i++)eligible.get(i).logQ=adjusted[i];}
            else needs.add("The "+scope+" omnibus family is incomplete; its adjusted P values remain unavailable");
            families.add(Map.of("scope",scope,"complete",complete,"coverage",complete?"complete":"incomplete","testing_denominator",eligible.size(),"registered_units",family.size(),"adjustment",p.get("adjustment"),"alpha",p.get("alpha")));
        }
        var result=finish(c,p,source,new ArrayList<>(units.values()),components,List.copyOf(needs),outputs,families);
        return result;
    }
    record Combined(double logP,String method) { }
    static Combined combine(List<Double> values,String method){double minimum=values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();if(method.equals("bonferroni_min")||values.stream().anyMatch(v->v==0))return new Combined(Math.min(0,minimum+Math.log(values.size())),method.equals("acat")?"bonferroni_min_boundary_fallback":"bonferroni_min");var acat=Acat.combineLog(values.stream().mapToDouble(Double::doubleValue).toArray());return new Combined(acat.log10PValue()*Math.log(10),"acat");}
    private static Result finish(Context c,Map<String,Object> p,StudyRareFollowup.Source source,List<Unit> units,List<String[]> components,List<String> needs,List<String> outputs)throws Exception{return finish(c,p,source,units,components,needs,outputs,List.of());}
    private static Result finish(Context c,Map<String,Object> p,StudyRareFollowup.Source source,List<Unit> units,List<String[]> components,List<String> needs,List<String> outputs,List<Map<String,Object>> families)throws Exception{
        String test="omnibus-"+p.get("method");var rows=new ArrayList<String[]>();double logAlpha=Math.log(((Number)p.get("alpha")).doubleValue());
        for(Unit u:units){long denominator=families.stream().filter(f->f.get("scope").equals(u.scope)).mapToLong(f->((Number)f.get("testing_denominator")).longValue()).findFirst().orElse(0);rows.add(new String[]{u.scope,u.id,"omnibus",test,"unsigned_set",u.status,prob(u.logP),string(u.logP),prob(u.logQ),string(u.logQ),String.valueOf(Double.isFinite(u.logQ)&&u.logQ<logAlpha),String.valueOf(u.scope.equals("primary")),p.get("unit").toString(),String.valueOf(u.expected),String.valueOf(u.logs.size()),String.valueOf(u.ineligible),String.valueOf(u.unresolved),u.method,StudyAssistance.json(u.sets.stream().toList()),String.valueOf(denominator)});}
        FollowupSupport.table(c.outputDirectory().resolve("rare-omnibus-results.tsv"),COLUMNS,rows);outputs.add("rare-omnibus-results.tsv");FollowupSupport.table(c.outputDirectory().resolve("omnibus-components.tsv"),List.of("scope","unit_id","source_set_id","mask_id","test","disposition","source_log_p_value"),components);outputs.add("omnibus-components.tsv");
        boolean primaryComplete=families.stream().anyMatch(f->f.get("scope").equals("primary")&&Boolean.TRUE.equals(f.get("complete")));var d=new LinkedHashMap<String,Object>();d.put("effective_policy",p);var sourceScope=map(map(source.manifest().get("details")).get("scope"));var retainedScope=new LinkedHashMap<String,Object>();for(String field:List.of("phenotype","exposure","outcome","species","cohorts","datasets","population","ancestry","tissue","assay","genome_build","visit","units"))if(sourceScope.containsKey(field))retainedScope.put(field,sourceScope.get(field));d.put("scope",retainedScope);d.put("families",families);d.put("rare_primary_complete",primaryComplete);d.put("primary_selection_eligible",primaryComplete&&p.get("selection").equals("primary"));d.put("primary_identity",Map.of("artifact","rare-omnibus-results.tsv","scope","primary","test",test,"rule","Registered complete cross-mask unit omnibus; source mask/test family remains separately retained"));d.put("source_manifest_sha256",FollowupSupport.hash(source.directory().resolve("manifest.yaml")));d.put("source_results_sha256",FollowupSupport.hash(source.results()));d.put("result_rows",rows.size());d.put("required_manuscript_caveat",CAVEAT);var references=new ArrayList<String>();if(p.get("method").equals("acat"))references.addAll(List.of("https://doi.org/10.1016/j.ajhg.2019.01.002","https://doi.org/10.1038/s41588-022-01178-w"));if(p.get("adjustment").equals("BY"))references.add("https://doi.org/10.1214/aos/1013699998");if(p.get("adjustment").equals("BH"))references.add("https://doi.org/10.1111/j.2517-6161.1995.tb02031.x");var method=new LinkedHashMap<String,Object>(StudyMethods.record("Registered cross-mask "+p.get("unit")+" omnibus: "+p.get("method"),Map.of("rare_omnibus",p,"multiple_testing",p.get("adjustment"),"scope_families",families,"replication",map(StudyResultTables.analysis(c).get("replication"))),references,null,List.of("rare variant cross-mask omnibus","multiple testing dependent hypotheses")));method.put("performed",units.stream().anyMatch(u->Double.isFinite(u.logP)));method.put("interpretation",CAVEAT);d.put("method_report",method);
        d.put("replication_families",replication(c,p,units,families,outputs));
        d.put("needs",needs.stream().distinct().map(n->StudyNeed.distinctReview("rare-omnibus:"+c.operation().get("hypothesis"),"Resolve rare-set omnibus prerequisites",n,true).document()).toList());write(c.outputDirectory().resolve("rare-omnibus.yaml"),d);outputs.add("rare-omnibus.yaml");return new Result(needs.isEmpty()?State.COMPLETE:State.WAITING_FOR_INPUT,needs.isEmpty()?"Constructed complete registered cross-mask omnibus families":"Retained available omnibus evidence with unresolved coverage/families",List.copyOf(outputs),d);
    }
    private static List<Map<String,Object>> replication(Context c,Map<String,Object> p,List<Unit> units,List<Map<String,Object>> families,List<String> outputs)throws IOException {
        var roles=map(StudyBioinformatics.hypothesis(c).get("inputs"));if(list(roles.get("replication")).isEmpty())return List.of();
        String scope=text(map(StudyResultTables.analysis(c).get("replication")).getOrDefault("adjustment_scope","all_results"));
        if(!Set.of("all_results","discovery_subset").contains(scope))throw new IOException("Unsupported omnibus replication adjustment scope");
        boolean frozen=families.stream().anyMatch(f->f.get("scope").equals("discovery")&&Boolean.TRUE.equals(f.get("complete")));double alpha=((Number)p.get("alpha")).doubleValue();
        var selected=units.stream().filter(u->frozen&&u.scope.equals("discovery")&&Double.isFinite(u.logQ)&&u.logQ<Math.log(alpha)).toList();var rows=new ArrayList<String[]>();var reports=new ArrayList<Map<String,Object>>();
        for(Object raw:list(roles.get("replication"))){String cohort=raw.toString();var targets=new TreeMap<String,Unit>();for(Unit u:units)if(u.scope.equals("cohort:"+cohort))targets.put(u.id,u);
            boolean full=families.stream().anyMatch(f->f.get("scope").equals("cohort:"+cohort)&&Boolean.TRUE.equals(f.get("complete")));var eligible=scope.equals("all_results")?targets.values().stream().filter(u->u.status.equals("ok")).toList():selected.stream().map(u->targets.get(u.id)).filter(java.util.Objects::nonNull).filter(u->u.status.equals("ok")).toList();
            boolean complete=frozen&&full&&(scope.equals("all_results")||eligible.size()==selected.size());var adjusted=new TreeMap<String,Double>();
            if(complete){double[] q=EnrichmentAnalysis.adjustLog(eligible.stream().mapToDouble(u->u.logP).toArray(),EnrichmentAnalysis.Fdr.valueOf(text(p.get("adjustment"))));for(int i=0;i<eligible.size();i++)adjusted.put(eligible.get(i).id,q[i]);}
            reports.add(Map.of("validation_dataset",cohort,"adjustment_scope",scope,"discovery_family_complete",frozen,"complete",complete,"frozen_discovery_units",selected.size(),"testing_denominator",eligible.size(),"adjustment",p.get("adjustment"),"direction_assessment","unavailable_unsigned_omnibus"));
            for(Unit first:selected){Unit second=targets.get(first.id);double q=adjusted.getOrDefault(first.id,Double.NaN);rows.add(new String[]{first.id,String.join(",",list(roles.get("discovery")).stream().map(Object::toString).toList()),cohort,prob(first.logP),string(first.logP),prob(first.logQ),string(first.logQ),second==null?"NA":prob(second.logP),second==null?"NA":string(second.logP),prob(q),string(q),String.valueOf(eligible.size()),scope,"NA",Double.isFinite(q)?String.valueOf(q<Math.log(alpha)):"NA",complete?"tested_registered_unsigned_omnibus":"unresolved_validation_family"});}
        }
        FollowupSupport.table(c.outputDirectory().resolve("rare-omnibus-replication.tsv"),List.of("feature_id","discovery_datasets","validation_dataset","discovery_p","discovery_log_p","discovery_adjusted_p","discovery_log_adjusted_p","validation_p","validation_log_p","validation_adjusted_p","validation_log_adjusted_p","validation_testing_denominator","adjustment_scope","same_sign","replicated","coverage"),rows);outputs.add("rare-omnibus-replication.tsv");return reports;
    }
    private static Map<String,Mapping> mappings(Context c,Map<String,Object> p,StudyRareFollowup.Source source,Set<String> sets,List<String> outputs)throws Exception{
        var result=new TreeMap<String,Mapping>();if(p.get("unit").equals("set")){if(map(source.manifest().get("output_sha256")).containsKey("feature-annotation.tsv")){Path file=StudyRareSupport.verified(source.directory(),source.manifest(),"feature-annotation.tsv");Files.copy(file,c.outputDirectory().resolve("feature-annotation.tsv"));outputs.add("feature-annotation.tsv");}return result;}
        Path file=null;boolean explicit=p.containsKey("gene_map");if(explicit)file=c.project().resolve(text(p.get("gene_map")));else if(map(source.manifest().get("output_sha256")).containsKey("feature-annotation.tsv"))file=StudyRareSupport.verified(source.directory(),source.manifest(),"feature-annotation.tsv");
        if(file!=null&&Files.isRegularFile(file)){if(over(file,integer(p,"maximum_rows")))throw new IOException("Gene mapping exceeds maximum_rows");file=copy(file,c.outputDirectory().resolve("source-gene-map.tsv"));outputs.add("source-gene-map.tsv");var t=DelimitedData.readResult(file);var symbols=new TreeMap<String,Set<String>>();
            for(String[] row:t.rows()){String set=row[t.column("feature_id")];if(!sets.contains(set))continue;String id=t.header().contains("gene_id")?row[t.column("gene_id")]:"",symbol=t.header().contains("gene_symbol")?row[t.column("gene_symbol")]:"";if(id.isBlank())id=symbol;if(id.isBlank()||Set.of("NA",".","NaN").contains(id))continue;label(id);if(t.header().contains("mapping_status")&&!Set.of("located","missing_location").contains(row[t.column("mapping_status")]))continue;var mapped=new Mapping(id,symbol);var old=result.putIfAbsent(set,mapped);if(old!=null&&!old.equals(mapped))throw new IOException("Ambiguous gene mapping for one registered source set");symbols.computeIfAbsent(id,k->new TreeSet<>()).add(symbol);}
            var annotations=new ArrayList<String[]>();for(var e:symbols.entrySet())annotations.add(new String[]{e.getKey(),e.getKey(),e.getValue().size()==1?e.getValue().iterator().next():"","missing_location"});FollowupSupport.table(c.outputDirectory().resolve("feature-annotation.tsv"),List.of("feature_id","gene_id","gene_symbol","mapping_status"),annotations);outputs.add("feature-annotation.tsv");
        }return result;
    }
    private static Map<Key,String> ledger(Context c,Map<String,Object> p,List<String> outputs)throws IOException{
        var result=new LinkedHashMap<Key,String>();if(!p.containsKey("component_eligibility"))return result;Path file=c.project().resolve(text(p.get("component_eligibility")));if(!Files.isRegularFile(file))return result;if(over(file,integer(p,"maximum_rows")))throw new IOException("Eligibility ledger exceeds maximum_rows");file=copy(file,c.outputDirectory().resolve("source-component-eligibility.tsv"));outputs.add("source-component-eligibility.tsv");var t=DelimitedData.readResult(file);
        for(String[] row:t.rows()){var key=new Key(row[t.column("scope")],row[t.column("feature_id")],row[t.column("mask_id")],row[t.column("test")]);for(String value:List.of(key.scope,key.set,key.mask,key.test))label(value);if(!key.scope.equals("*")&&!key.scope.equals("primary")&&!key.scope.equals("discovery")&&!key.scope.matches("cohort:[A-Za-z][A-Za-z0-9_.-]*"))throw new IOException("Eligibility ledger has an unsupported scientific scope");String reason=row[t.column("reason")];if(!Set.of("no_eligible_variants","not_in_predeclared_mask","not_callable_in_scope").contains(reason))throw new IOException("Eligibility exclusions must be independent of test P values/model success");if(result.putIfAbsent(key,reason)!=null)throw new IOException("Duplicate component eligibility declaration");}return result;
    }
    private static boolean sourceComplete(StudyRareFollowup.Source source,DelimitedData t,String scope)throws IOException{
        var d=map(source.manifest().get("details"));var family=org.jlinalg.study.StudySuppliedResultPolicy.family(source.results());if(!family.isEmpty()){if(!scope.equals("primary")||!family.get("coverage").equals("complete"))return false;long rows=t.rows().stream().filter(r->!t.header().contains("scope")||r[t.column("scope")].equals("primary")).count();return family.get("tested_count") instanceof Number n&&n.longValue()<=rows;}
        if("complete".equals(source.manifest().get("state")))return true;if(scope.equals("primary")&&Boolean.TRUE.equals(d.get("rare_primary_complete")))return true;return list(d.get("families")).stream().map(StudyPublicEvidence::map).anyMatch(f->scope.equals(f.get("scope"))&&Boolean.TRUE.equals(f.get("complete")));
    }
    private static boolean independentExclusion(String status){return Set.of("no_eligible_variants","empty_mask","not_in_predeclared_mask","not_callable_in_scope").contains(status);}
    private static boolean valid(String status){return Set.of("ok","tested","complete","single_cohort").contains(status);}
    private static double probability(DelimitedData t,String[] row)throws IOException{
        String raw=row[t.column("p_value")];double p=number(raw);if(Double.isFinite(p)&&(p<0||p>1))throw new IOException("Invalid source rare-set P value");double log=t.header().contains("log_p_value")?number(row[t.column("log_p_value")]):Double.NaN;
        if(Double.isFinite(log)){if(log>0||p==0&&Math.exp(log)!=0||p>0&&Math.abs(Math.log(p)-log)>Math.max(1e-8,Math.abs(log)*1e-7))throw new IOException("Source rare-set P/log-P values conflict");return log;}return p>0?Math.log(p):Double.NaN;
    }
    private static double number(String value)throws IOException{if(Set.of("","NA","NaN",".").contains(value))return Double.NaN;try{double x=Double.parseDouble(value);if(!Double.isFinite(x))throw new NumberFormatException();return x;}catch(NumberFormatException bad){throw new IOException("Malformed/nonfinite source rare-set probability",bad);}}
    private static void label(String value)throws IOException{if(value.isBlank()||value.length()>512||value.chars().anyMatch(Character::isISOControl))throw new IOException("Rare omnibus identities must be bounded scientific labels");}
    private static boolean over(Path path,int maximum)throws IOException{try(var lines=Files.lines(path)){return lines.limit(maximum+2L).count()>maximum+1L;}}
    private static Path copy(Path source,Path target)throws IOException{String digest=FollowupSupport.hash(source);Files.copy(source,target);if(!digest.equals(FollowupSupport.hash(target))||!digest.equals(FollowupSupport.hash(source)))throw new IOException("Rare omnibus input changed during immutable snapshot");return target;}
    private static int integer(Map<String,Object> p,String field){return ((Number)p.get(field)).intValue();}
    private static String string(double x){return Double.isFinite(x)?Double.toString(x):"NA";}
    private static String prob(double log){return Double.isFinite(log)?Double.toString(Math.exp(log)):"NA";}
    private static Result waiting(Context c,String reason)throws IOException{return StudyRareFollowup.waiting(c,reason);}
}

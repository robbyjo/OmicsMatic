/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.LdPred2;
import org.jlinalg.genetics.LdPred2Random;
import org.jlinalg.genetics.LdPred2Seeds;
import static org.jlinalg.cli.LdPred2WorkflowFiles.*;

/** Isolated numerical worker; its receipt alone never admits a workflow bundle. */
public final class LdPred2WorkflowWorker {
    private LdPred2WorkflowWorker() {}
    public static void main(String[] args) {
        try {
            if(args.length!=1)throw new IllegalArgumentException("Owned workflow root required");
            Path root=Path.of(args[0]).toRealPath();Config c=config(load(root.resolve("effective.properties")));
            if(!Files.readString(root.resolve("code.tsv")).equals(LdPred2WorkflowCli.render(LdPred2WorkflowCli.code())))throw new IllegalArgumentException("Worker class/resource identities differ from the parent artifact");
            if(c.original()){models(root,c);LdPred2OriginalRuntime.execute(root,c);return;}
            Data d=data(root,c);Path results=root.resolve("results");Files.createDirectory(results);
            List<Model> plan=new ArrayList<>(d.models());
            if(c.get("method").equals("grid"))plan.sort(Comparator.comparingDouble(Model::p).reversed().thenComparing(Model::sparse).thenComparing(Comparator.comparingDouble(Model::h2).reversed()));
            Map<String,String> outcomes=new LinkedHashMap<>();int[] stream=c.get("method").equals("grid")?LdPred2Seeds.fromInteger(c.integer("seed")):null;
            if(stream!=null)write(results.resolve("rng-before.txt"),seed(stream));
            int valid=0;
            for(Model model:plan) {
                Path folder=results.resolve(model.id());Files.createDirectory(folder);
                String status="accepted",reason="";Map<String,String> diagnostic=new LinkedHashMap<>();
                diagnostic.put("model",model.id());diagnostic.put("heritability",Double.toString(model.h2()));diagnostic.put("p",Double.toString(model.p()));diagnostic.put("sparse",Boolean.toString(model.sparse()));
                double[] effects=null;
                if(c.get("method").equals("inf")) {
                    var fit=LdPred2.infinitesimal(d.correlation(),d.beta(),d.se(),d.n(),model.h2(),new LdPred2.SolveOptions(c.real("relative_tolerance"),c.integer("solver_iterations")));
                    effects=fit.alleleEffects();diagnostic.put("solver_iterations",Integer.toString(fit.iterations()));diagnostic.put("relative_residual",Double.toString(fit.relativeResidual()));
                } else {
                    var random=new LdPred2Random.RStream(stream);diagnostic.put("rng_initial",seed(stream).strip());
                    try{effects=LdPred2.gibbs(d.correlation(),d.beta(),d.se(),d.n(),new LdPred2.GridParameters(model.h2(),model.p(),model.sparse(),c.integer("burn_in"),c.integer("iterations")),random,null).alleleEffects();}
                    catch(LdPred2.DivergenceException e){status="rejected";reason="original-grid-divergence";diagnostic.put("rejected_at_sweep",Integer.toString(e.sweep()));write(folder.resolve("REFUSAL.txt"),e.getMessage()+"\nNo accepted weights for this model. Preserve this refusal and inspect summary/LD/model agreement before a new run.\n");}
                    diagnostic.put("rng_final",seed(random.state()).strip());stream=LdPred2Seeds.nextStream(stream);
                }
                if(effects!=null){weights(folder.resolve("effects.tsv"),d,effects);valid++;}
                diagnostic.put("status",status);diagnostic.put("reason",reason);table(folder.resolve("diagnostics.tsv"),diagnostic);outcomes.put(model.id(),status+"\t"+reason);
            }
            StringBuilder summary=new StringBuilder("model\tstatus\treason\n");for(Model m:d.models())summary.append(m.id()).append('\t').append(outcomes.get(m.id())).append('\n');write(results.resolve("models.tsv"),summary.toString());
            if(stream!=null)write(results.resolve("rng-after.txt"),seed(stream));
            table(root.resolve("WORKER_DONE.tsv"),Map.of("variants",Integer.toString(d.variants().size()),"models",Integer.toString(d.models().size()),"accepted",Integer.toString(valid),"rejected",Integer.toString(d.models().size()-valid),"engine","native","method",c.get("method")));
            if(valid==0)throw new ArithmeticException("All grid candidates were rejected; no accepted weights");
        } catch(Throwable error) {
            error.printStackTrace(System.err);System.exit(error instanceof Prerequisite?3:2);
        }
    }
    static String seed(int[] state){StringBuilder out=new StringBuilder();for(int i=0;i<state.length;i++){if(i>0)out.append(',');out.append(state[i]);}return out.append('\n').toString();}
    static void table(Path file,Map<String,String> values)throws java.io.IOException{StringBuilder out=new StringBuilder("key\tvalue\n");for(var e:new java.util.TreeMap<>(values).entrySet())out.append(e.getKey()).append('\t').append(e.getValue()).append('\n');write(file,out.toString());}
}

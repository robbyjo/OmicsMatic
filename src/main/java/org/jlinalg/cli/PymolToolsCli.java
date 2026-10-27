/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import static org.jlinalg.cli.PymolAudit.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed original PyMOL operations with a sealed dependency and independent native audits. */
final class PymolToolsCli {
    private PymolToolsCli() { }
    private static final Set<String> OPTIONS=Set.of("recipe","home","out","timeout","threads","max-atoms","max-bytes");
    private static final Set<String> SELECTOR=Set.of("structure","chain","segi","resi","resn","name","alt","element");
    private static final Map<String,Set<String>> STEP_FIELDS=Map.of(
        "measure",Set.of("kind","atoms"),"align",Set.of("mobile","target","cutoff","cycles","gap","extend","max_gap","max_skip"),
        "super",Set.of("mobile","target","cutoff","cycles","gap","extend","max_gap","max_skip"),
        "bond",Set.of("atoms","order"),"unbond",Set.of("atoms"),"set_dihedral",Set.of("atoms","degrees"),
        "mutate",Set.of("residue","to","library","rotamer","hydrogens","max_backbone_shift"),
        "render",Set.of("layers","focus","buffer","width","height","background","view"));
    private static final String RECOVERY="Required action: inspect WARNINGS.md, session.json and retained partial/ logs. Correct the named recipe/input, resource limit, original-program or audit failure and rerun to a new output directory. Failed or skipped partials are diagnostic data, not successful analysis. Review camera framing and atom/model/alternate-conformer scope before using figures or structural conclusions.";

    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1 && args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=options(args,OPTIONS);required(options,"recipe");required(options,"out");int[] code={0};
            externalTransaction(options,"original-pymol-tools",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                session.put("requested_options",options);session.put("engine","pinned-open-source-pymol-with-independent-java-audits");
                try {
                    Path recipePath=Path.of(options.get("recipe")).toAbsolutePath().normalize();
                    Map<?,?> raw=read(recipePath,1048576);String recipeHash=hash(recipePath);
                    fields(raw,Set.of("schema_version","structures","steps"),Set.of("schema_version","structures","steps"));
                    require(integer(raw.get("schema_version"),1,1)==1,"Unsupported recipe schema");
                    int maximum=FollowupSupport.integer(options,"max-atoms",25000,1,100000),bytes=FollowupSupport.integer(options,"max-bytes",67108864,1,268435456);
                    int timeout=FollowupSupport.integer(options,"timeout",600,1,86400),threads=FollowupSupport.integer(options,"threads",1,1,64);
                    List<?> inputs=list(raw.get("structures")),steps=list(raw.get("steps"));
                    require(!inputs.isEmpty() && inputs.size()<=8 && !steps.isEmpty() && steps.size()<=32,"Require 1..8 structures and 1..32 steps");
                    Path inputDirectory=Files.createDirectory(directory.resolve("inputs"));var structures=new ArrayList<Object>();
                    var sourceParses=new LinkedHashMap<String,PymolStructureInput.Parsed>();var sourceHashes=new LinkedHashMap<Path,String>();
                    var identities=new HashSet<String>();long totalBytes=0;int totalAtoms=0;int totalSourceAtoms=0;var provenance=new ArrayList<Object>();
                    Path home=Path.of(options.containsKey("home")?options.get("home"):System.getenv().getOrDefault("JLINALG_PYMOL_HOME",".jlinalg-missing-pymol-home")).toAbsolutePath().normalize();
                    Path destination=Path.of(options.get("out")).toAbsolutePath().normalize();
                    require(!destination.startsWith(home) && !home.startsWith(destination),"Output and private runtime home must not overlap");
                    for(Object value:inputs) {
                        Map<?,?> input=object(value);fields(input,Set.of("id","path","format","state","cif_namespace"),Set.of("id","path","format","state","cif_namespace"));
                        String id=id(input.get("id")),format=string(input.get("format")),namespace=string(input.get("cif_namespace"));
                        require(identities.add(id),"Duplicate structure ID");require(Set.of("pdb","cif").contains(format) && Set.of("author","label").contains(namespace),"Explicit pdb|cif format and author|label CIF namespace required");
                        require(!format.equals("pdb") || namespace.equals("author"),"PDB uses author identifiers; cif_namespace must be author");
                        int state=integer(input.get("state"),1,1000000);Path path=Path.of(string(input.get("path")));
                        if(!path.isAbsolute())path=recipePath.getParent().resolve(path);path=path.toAbsolutePath().normalize();
                        require(Files.isRegularFile(path) && (totalBytes+=Files.size(path))<=bytes,"Missing source or combined sources exceed --max-bytes");
                        require(!path.startsWith(destination) && !path.startsWith(home),"Source, output and runtime home must not overlap");
                        String sourceHash=hash(path);Path staged=inputDirectory.resolve(id+"."+format);Files.copy(path,staged);
                        require(hash(staged).equals(sourceHash) && hash(path).equals(sourceHash),"Source changed while staging");
                        var parsed=PymolStructureInput.parse(Files.readString(staged,StandardCharsets.UTF_8),format,state,namespace,maximum);
                        require((totalSourceAtoms+=parsed.sourceAtoms())<=maximum,"Combined complete sources across all states exceed --max-atoms");
                        require((totalAtoms+=parsed.atoms().size())<=maximum,"Combined selected states exceed --max-atoms");sourceParses.put(id,parsed);sourceHashes.put(path,sourceHash);
                        structures.add(Map.of("id",id,"staged_path","inputs/"+staged.getFileName(),"sha256",sourceHash,"format",format,"state",state,"cif_namespace",namespace));
                        provenance.add(Map.of("id",id,"source_path",path.toString(),"sha256",sourceHash,"source_model_labels_by_state_ordinal",parsed.modelLabels(),"selected_state_ordinal",state,"cif_namespace",namespace,"selected_atoms",parsed.atoms().size()));
                    }
                    validateSteps(steps,identities);
                    Map<String,Object> recipe=new LinkedHashMap<>();recipe.put("schema_version",1);recipe.put("structures",structures);recipe.put("steps",steps);recipe.put("max_atoms",maximum);recipe.put("threads",threads);
                    Path stagedRecipe=directory.resolve("admitted-recipe.json");Files.writeString(stagedRecipe,RegenieRuntime.json(recipe)+"\n",StandardCharsets.UTF_8);
                    session.put("source_recipe_path",recipePath.toString());session.put("source_recipe_sha256",recipeHash);session.put("structures",provenance);session.put("admitted_recipe_sha256",hash(stagedRecipe));
                    session.put("source_atoms_across_all_states",totalSourceAtoms);session.put("selected_atoms",totalAtoms);
                    session.put("scope","Explicit source-state ordinals; no assembly generation, alternate-conformer collapse or unit inference. Original align and super retain distinct correspondences/refinement. Graph edits are not geometry/energy optimization. Mutations are explicit library conformations. Rendering is not APBS, molecular dynamics or functional inference.");
                    Path runtimeWork=Files.createDirectory(directory.resolve("runtime"));var runtime=new PymolRuntime(home,runtimeWork,session);
                    Path worker=directory.resolve("pymol-worker.py");
                    try(var stream=PymolToolsCli.class.getResourceAsStream("/org/jlinalg/omics/pymol_worker.py")) {if(stream==null)throw new IOException("Bundled original PyMOL worker missing");Files.copy(stream,worker);}
                    runtime.execute(worker,List.of(runtime.sitePackages().toString(),stagedRecipe.toString(),directory.toString()),directory,"workflow",timeout,threads);
                    Map<String,Object> audit=PymolAudit.audit(directory,recipe,sourceParses);Files.writeString(directory.resolve("native-audit.json"),RegenieRuntime.json(audit)+"\n",StandardCharsets.UTF_8);
                    require(recipeHash.equals(hash(recipePath)),"Recipe changed during analysis");for(var e:sourceHashes.entrySet())require(e.getValue().equals(hash(e.getKey())),"Source changed during analysis: "+e.getKey());
                    runtime.verify();manifest.put("analysis_status","completed-with-warnings");session.put("steps_verified",steps.size());
                    warnings.add("STRUCTURAL REVIEW REQUIRED: original PyMOL execution and native numerical/identity audits passed. Distances and coordinates are Angstrom; angles are degrees. The chosen state ordinal refers to input order, not a PDB MODEL serial. Alternate conformers remain explicit; exact-one atom selections fail if ambiguous. Inspect original-results.json, native-audit.json, camera views and final structures before scientific use.");
                    warnings.add("SCOPE: this admitted open-source PyMOL 3.1.0 build does not establish parity with a paper's older or commercial version. align and super use the original distinct methods. Mutations, torsion and graph edits do not estimate stability, binding, energy or dynamics. APBS electrostatics requires a separate validated solver/protonation/grid workflow and is unavailable here.");
                    warnings.add("EXPORT LIMITATION: original PyMOL mmCIF exports round coordinates to0.001 Angstrom and B factors/occupancy to0.01, and omit explicit bond categories. Required action: retain final-*.json atom/bond ledgers as the authoritative full-precision edited graph; use final-*.cif as the audited coordinate interchange export. Do not assume reloading the mmCIF preserves a deliberately added/removed bond.");
                    if(steps.stream().anyMatch(s->s instanceof Map<?,?> m && "mutate".equals(m.get("operation"))))warnings.add("MUTATION ANNOTATIONS: the original wizard can reassign atom serials and reset B factors/occupancy for the constructed residue. Required action: compare before/after ledgers and retain these changes; do not interpret new B factors as experimental uncertainty or predicted confidence. The caller-declared maximum backbone shift was checked.");
                    for(String name:List.of("workflow.stdout","workflow.stderr")) {
                        Path log=directory.resolve(name);try(var lines=Files.lines(log,StandardCharsets.UTF_8)){lines.filter(s->s.matches("(?i).*(warning|error|not found|fallback|no rotamers).*")).limit(100).forEach(s->warnings.add("UPSTREAM LOG REVIEW ("+name+"): "+s));}
                    }
                }catch(PymolRuntime.Unavailable failure) {
                    code[0]=3;manifest.put("analysis_status","skipped-dependency-required");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);
                    warnings.add("ANALYSIS SKIPPED - ORIGINAL DEPENDENCY UNAVAILABLE: "+failure.getMessage()+PymolRuntime.setup());
                }catch(Exception failure) {
                    code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.getClass().getSimpleName()+": "+failure.getMessage());RnaToolRuntime.quarantine(directory);
                    warnings.add("ANALYSIS FAILED: "+failure.getMessage());
                }
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);session.put("required_action",RECOVERY);
                Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("WARNINGS.md"),"# PYMOL "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));
            if(code[0]==0)out.println("Original PyMOL workflow and native audits completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\n"+RECOVERY);return 2;}
    }

    private static void validateSteps(List<?> steps,Set<String> structures)throws IOException {
        var ids=new HashSet<String>();
        for(Object value:steps) {
            Map<?,?> step=object(value);require(ids.add(id(step.get("id"))),"Duplicate step ID");String operation=string(step.get("operation"));Set<String> allowed=STEP_FIELDS.get(operation);require(allowed!=null,"Unknown typed operation");
            var all=new HashSet<>(allowed);all.addAll(Set.of("id","operation"));fields(step,all,Set.of("id","operation"));
            switch(operation) {
                case "measure"->{String kind=string(step.get("kind"));int n=switch(kind){case "distance"->2;case "angle"->3;case "dihedral"->4;default->throw new IOException("Unknown measurement kind");};atomSelectors(step.get("atoms"),n,structures);}
                case "align","super"->{selector(step.get("mobile"),structures);selector(step.get("target"),structures);}
                case "bond","unbond"->atomSelectors(step.get("atoms"),2,structures);
                case "set_dihedral"->{atomSelectors(step.get("atoms"),4,structures);range(step.get("degrees"),-180,180);}
                case "mutate"->{selector(step.get("residue"),structures);string(step.get("to"));require("backbone-independent".equals(step.get("library")),"Only explicit backbone-independent rotamers are validated");require(Set.of("none","auto").contains(step.get("hydrogens")),"Explicit none|auto hydrogens required");integer(step.get("rotamer"),1,100000);range(step.get("max_backbone_shift"),0,1);}
                case "render"->{selector(step.get("focus"),structures);range(step.get("buffer"),0,1000);integer(step.get("width"),100,4000);integer(step.get("height"),100,4000);rgb(step.get("background"));List<?> layers=list(step.get("layers"));require(!layers.isEmpty() && layers.size()<=32,"Render requires 1..32 layers");for(Object layer:layers){Map<?,?> item=object(layer);fields(item,Set.of("selection","representation","color"),Set.of("selection","representation","color"));selector(item.get("selection"),structures);require(Set.of("cartoon","sticks","surface","spheres","lines","ribbon").contains(item.get("representation")),"Unknown representation");rgb(item.get("color"));}if(step.containsKey("view")){List<?> view=list(step.get("view"));require(view.size()==18,"Camera view needs eighteen values");for(Object n:view)range(n,-1e9,1e9);}}
                default->throw new IOException("Unknown operation");
            }
            if(step.containsKey("cutoff"))range(step.get("cutoff"),.001,1000);if(step.containsKey("cycles"))integer(step.get("cycles"),0,100);
            for(String name:List.of("gap","extend"))if(step.containsKey(name))range(step.get(name),-1000,0);
            for(String name:List.of("max_gap","max_skip"))if(step.containsKey(name))integer(step.get(name),0,1000);
            if(step.containsKey("order"))integer(step.get("order"),1,3);
        }
    }
    private static void atomSelectors(Object value,int count,Set<String> structures)throws IOException {List<?> selectors=list(value);require(selectors.size()==count,"Wrong atom selection count");for(Object spec:selectors)selector(spec,structures);}
    private static void selector(Object value,Set<String> structures)throws IOException {Map<?,?> map=object(value);fields(map,SELECTOR,Set.of("structure"));require(structures.contains(string(map.get("structure"))),"Unknown selected structure");for(var e:map.entrySet())if(!e.getKey().equals("structure")){List<?> values=e.getValue() instanceof List<?> list?list:List.of(e.getValue());require(!values.isEmpty() && values.size()<=10000,"Empty/oversized selection field");for(Object item:values)require(string(item).length()<=256,"Selection value exceeds256 characters");}}
    private static void rgb(Object value)throws IOException {List<?> rgb=list(value);require(rgb.size()==3,"RGB needs three explicit numbers");for(Object n:rgb)range(n,0,1);}
    private static void range(Object value,double low,double high)throws IOException {double n=number(value);require(n>=low && n<=high,"Number outside declared operation bound");}
    private static String id(Object value)throws IOException {String id=string(value);require(id.matches("[A-Za-z][A-Za-z0-9_]{0,47}"),"Require ASCII identifier beginning with a letter, at most48 characters");return id;}
    private static void fields(Map<?,?> map,Set<String> allowed,Set<String> required)throws IOException {require(allowed.containsAll(map.keySet()) && map.keySet().containsAll(required),"Unknown/missing recipe fields");}
    static String help(){return """
        pymol-tools --recipe UTF8_RECIPE.json --home ADMITTED_LINUX_HOME --out NEW_DIRECTORY
          [--timeout 600] [--threads 1] [--max-atoms 25000] [--max-bytes 67108864]
        Runs pinned original open-source PyMOL 3.1.0 in Linux Ubuntu24.04 x86_64;
        use Linux Java inside WSL on Windows. JLINALG_PYMOL_HOME supplies --home.
        Analysis never downloads or installs. Missing/changed/unsupported dependency:
        exit3, prominent WARNINGS.md, skipped analysis and retained partial diagnostics.
        Recipe schema1 has structures and steps. Each structure declares id,path,
        pdb|cif format, one-based state ordinal and author|label cif_namespace
        (author for PDB). Paths are relative to the recipe. All coordinates are Angstrom.
        --max-atoms bounds the complete combined sources across ALL states before load.
        Typed operations: render,measure,align,super,bond,unbond,set_dihedral,mutate.
        Exact-value selectors use structure and optional chain,segi,resi,resn,name,alt,
        element fields, each a string or explicit list. No raw selection expressions,
        user Python, PML, sessions or command execution. Atom measurements require
        exactly one atom per selector. No implicit alternate-conformer collapse.
        Mutations require explicit target,backbone-independent library,rotamer,
        none|auto hydrogens and max_backbone_shift in Angstrom. No energy optimization.
        Native input/identity/numerical audits must pass; exit2 on input/process/audit
        failures, with quarantined partials. Exit0 requires review of WARNINGS.md,
        original-results.json,native-audit.json,figures and final mmCIF structures.
        This adapter does not run APBS or establish historical/commercial version parity.
        Existing outputs are preserved. Install help: tools/literature/install_pymol.py --help
        """;}
}

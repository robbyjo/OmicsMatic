/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.protein.PqrStructure;

/** Explicit original PDB2PQR preparation with native model selection and atom lineage. */
final class Pdb2pqrToolsCli {
    private Pdb2pqrToolsCli() { }
    private static final Set<String> OPTIONS=Set.of("input","home","force-field","mode","protonation","ph","debump","optimize","water","repair","unparameterized","neutral-n","neutral-c","state","alternate","alt-label","length-units","max-heavy-shift","max-atoms","max-bytes","timeout","out","options-file");
    record Choices(String forcefield,String mode,String protonation,double ph,boolean debump,boolean optimize,String water,String repair,String missing,boolean neutralN,boolean neutralC,double heavyShift,int state,String alternate,String altLabel) {
        List<String> arguments() {
            var flags=new ArrayList<>(List.of("--ff",forcefield,"--keep-chain","--whitespace","--pdb-output","prepared.pdb"));
            if(mode.equals("assign-only"))flags.add("--assign-only");if(!debump)flags.add("--nodebump");if(!optimize)flags.add("--noopt");if(water.equals("drop"))flags.add("--drop-water");if(neutralN)flags.add("--neutraln");if(neutralC)flags.add("--neutralc");
            if(protonation.equals("propka"))flags.addAll(List.of("--titration-state-method","propka","--with-ph",Double.toString(ph)));flags.addAll(List.of("selected.pdb","prepared.pqr"));return flags;
        }
    }
    static Choices choices(Map<String,String> options) {
        String ff=required(options,"force-field"),mode=required(options,"mode"),protonation=required(options,"protonation"),water=required(options,"water"),repair=required(options,"repair"),missing=required(options,"unparameterized"),alternate=required(options,"alternate");
        if(!Set.of("AMBER","CHARMM","PARSE","TYL06","PEOEPB","SWANSON").contains(ff))throw new IllegalArgumentException("Require one of the six explicit uppercase original force fields");
        if(!Set.of("prepare","assign-only").contains(mode)||!Set.of("forcefield","propka").contains(protonation)||!Set.of("keep","drop").contains(water)||!Set.of("error","allow").contains(repair)||!Set.of("error","omit").contains(missing))throw new IllegalArgumentException("Unsupported explicit preparation/protonation/water/repair/unparameterized policy");
        if(!required(options,"length-units").equals("angstrom"))throw new IllegalArgumentException("PDB/PQR coordinates require explicit angstrom;no conversion is inferred");
        boolean debump=explicitBoolean(options,"debump"),optimize=explicitBoolean(options,"optimize"),neutralN=explicitBoolean(options,"neutral-n"),neutralC=explicitBoolean(options,"neutral-c");
        if((neutralN||neutralC)&&!ff.equals("PARSE"))throw new IllegalArgumentException("Neutral terminal flags require the PARSE force field");
        double ph=0;if(protonation.equals("propka")){ph=Double.parseDouble(required(options,"ph"));if(!Double.isFinite(ph)||ph<0||ph>14)throw new IllegalArgumentException("Explicit PROPKA pH must be finite in[0,14]");}else if(options.containsKey("ph"))throw new IllegalArgumentException("--ph applies only to --protonation propka;forcefield does not imply pH7");
        double shift=Double.parseDouble(required(options,"max-heavy-shift"));if(!Double.isFinite(shift)||shift<0||shift>10)throw new IllegalArgumentException("Maximum retained-heavy displacement requires finite0..10 Angstrom");int state=Integer.parseInt(required(options,"state"));if(state<1||state>10000)throw new IllegalArgumentException("State ordinal requires1..10000");
        String label=options.get("alt-label");if(!Set.of("error","label").contains(alternate)||alternate.equals("label")&&(label==null||!label.matches("[A-Za-z0-9]"))||alternate.equals("error")&&label!=null)throw new IllegalArgumentException("Require alternate error,or alternate label with one ASCII --alt-label");
        if(mode.equals("assign-only")&&(debump||optimize||!protonation.equals("forcefield")||!repair.equals("error")||!missing.equals("error")))throw new IllegalArgumentException("assign-only requires debump=false,optimize=false,forcefield protonation,repair=error and unparameterized=error;ignored scientific choices are forbidden");
        return new Choices(ff,mode,protonation,ph,debump,optimize,water,repair,missing,neutralN,neutralC,shift,state,alternate,label);
    }
    private static boolean explicitBoolean(Map<String,String> options,String name){required(options,name);return bool(options,name,false);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};
            externalTransaction(options,"original-pdb2pqr-preparation",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("requested_options",options);
                try {
                    Choices choices=choices(options);int maximum=integer(options,"max-atoms",100000,1,1000000),bytes=integer(options,"max-bytes",67108864,1,268435456),timeout=integer(options,"timeout",600,1,3600);
                    Path source=Path.of(required(options,"input")).toAbsolutePath().normalize();if(!Files.isRegularFile(source)||Files.size(source)>bytes)throw new IOException("PDB source is missing or exceeds --max-bytes");String sourceHash=hash(source);Path staged=directory.resolve("original.pdb");Files.copy(source,staged);if(!hash(staged).equals(sourceHash))throw new IOException("PDB changed during staging");
                    Pdb2pqrInput.Selected selected=Pdb2pqrInput.select(Files.readString(staged,StandardCharsets.UTF_8),choices.state,choices.alternate,choices.altLabel,maximum);Files.writeString(directory.resolve("selected.pdb"),selected.pdb(),StandardCharsets.UTF_8);
                    session.put("source_path",source.toString());session.put("source_sha256",sourceHash);session.put("selected_pdb_sha256",hash(directory.resolve("selected.pdb")));session.put("source_model_labels",selected.sourceModels());session.put("source_atoms_all_states",selected.sourceAtoms());session.put("selected_state_ordinal",choices.state);session.put("selected_atoms",selected.atoms().size());session.put("alternate_atoms_excluded",selected.alternateAtomsExcluded());session.put("alternate_labels_cleared_in_staged_PDB",selected.alternateLabelsCleared());
                    session.put("force_field",choices.forcefield);session.put("protonation",choices.protonation);if(choices.protonation.equals("propka"))session.put("explicit_pH",choices.ph);session.put("upstream_debump_enabled",choices.debump);session.put("upstream_full_hydrogen_optimization_enabled",choices.optimize);session.put("terminal_neutralization",Map.of("n",choices.neutralN,"c",choices.neutralC));
                    String home=options.get("home");if(home==null||home.isBlank())throw new Pdb2pqrRuntime.Unavailable("No admitted PDB2PQR --home was supplied;analysis never installs software");var runtime=new Pdb2pqrRuntime(Path.of(home),session);runtime.version(directory);
                    int exit=runtime.execute(choices.arguments(),directory,"prepare",timeout);if(exit!=0)throw new IOException("Original PDB2PQR preparation returned exit"+exit+";inspect retained original logs");
                    Path pqrPath=directory.resolve("prepared.pqr"),pdbPath=directory.resolve("prepared.pdb");for(Path path:List.of(pqrPath,pdbPath))if(!Files.isRegularFile(path)||Files.size(path)>bytes)throw new IOException("Original preparation output is missing or exceeds --max-bytes");
                    PqrStructure pqr;try(var reader=Files.newBufferedReader(pqrPath,StandardCharsets.UTF_8)){pqr=PqrStructure.read(reader,maximum);}
                    var audit=Pdb2pqrInput.audit(selected,pqr,Files.readString(pdbPath,StandardCharsets.UTF_8),choices.mode,choices.water,choices.repair,choices.missing,choices.heavyShift,maximum);warnings.addAll(audit.warnings());
                    var results=new LinkedHashMap<>(audit.summary());results.put("pqr_sha256",hash(pqrPath));results.put("prepared_pdb_sha256",hash(pdbPath));results.put("coordinate_units","angstrom");results.put("charge_units","elementary_charge");results.put("radius_units","angstrom");results.put("scope","Independent identity/coordinate/count/charge/radius bookkeeping;original force-field/protonation assignment,not an independent reimplementation or experimental validation of charges,pKa,repair or chemistry");
                    if(selected.alternateAtomsExcluded()>0||selected.alternateLabelsCleared()>0)warnings.add("EXPLICIT ALTERNATE SELECTION: "+selected.alternateAtomsExcluded()+" alternate atoms excluded and "+selected.alternateLabelsCleared()+" chosen labels cleared only in staged PDB. Required action: retain original.pdb and lineage provenance;no highest-occupancy choice was inferred.");
                    if(selected.atoms().stream().anyMatch(a->!a.segi().isEmpty()))warnings.add("SEGMENT ANNOTATIONS: PQR cannot preserve PDB segment IDs. Unique chain/residue/atom identities were required;retain input_segment in atom-lineage.json and the original PDB.");
                    if(pqr.zeroRadiusAtoms()>0)warnings.add("ZERO RADII RETAINED: "+pqr.zeroRadiusAtoms()+" supplied force-field atoms have radius0,including possible charged PARSE hydrogens. They were retained without replacement;review the chosen model.");
                    for(String name:List.of("prepare.stdout","prepare.stderr")) {
                        Path path=directory.resolve(name);if(Files.size(path)>8388608)throw new IOException("Original preparation log exceeds8MiB;inspect retained diagnostics");
                        try(var lines=Files.lines(path,StandardCharsets.UTF_8)){lines.filter(line->line.matches("(?i).*(?:\\bWARNING\\b|\\bERROR\\b|\\bCRITICAL\\b).*" )).limit(200).forEach(line->warnings.add("UPSTREAM LOG REVIEW("+name+"): "+line));}
                    }
                    if(!hash(source).equals(sourceHash)||!hash(staged).equals(sourceHash))throw new IOException("Original PDB source changed during preparation");
                    if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("recipe-sha256")))throw new IOException("Options recipe changed during preparation");runtime.verify();
                    Files.writeString(directory.resolve("atom-lineage.json"),RegenieRuntime.json(audit.ledger())+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("results.json"),RegenieRuntime.json(results)+"\n",StandardCharsets.UTF_8);session.put("native_audit",results);manifest.put("analysis_status","completed-with-warnings");
                    warnings.add("PREPARATION MODEL REQUIRES REVIEW: original PDB2PQR3.8.0 assigns the declared force field and protonation model. Forcefield protonation does not declare a pH. Explicit PROPKA uses original3.5.1 and the supplied pH;it is a prediction,not a measured state. --noopt disables full hydrogen optimization,but upstream can still optimize retained water. Heavy-atom additions/omissions,naming and shifts require ledger/log review. No custom force field,ligand MOL2 parameterization,remote structure retrieval or mmCIF preparation is exposed by this profile. Prepared PQR is an input to a separate electrostatic solver;this command does not compute a surface potential or solvation/binding energy. Existing results are preserved;choose a new directory for every attempt.");
                }catch(Pdb2pqrRuntime.Unavailable failure){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"PREPARATION SKIPPED: "+failure.getMessage());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"PREPARATION FAILED: "+failure.getMessage());}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("WARNINGS.md"),"# PDB2PQR "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"PREPARATION SKIPPED":"PREPARATION FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+Pdb2pqrRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"PREPARATION WARNINGS: ":code[0]==3?"PREPARATION SKIPPED: ":"PREPARATION FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Original PDB2PQR preparation and native lineage audit completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\n"+Pdb2pqrRuntime.recovery());return 2;}
    }
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,OPTIONS);if(!direct.containsKey("options-file"))return direct;Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require a UTF8 JSON string-options recipe at most1MiB");byte[] raw=Files.readAllBytes(path);Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(value instanceof Map<?,?> fields))throw new IOException("Require an options object");var result=new LinkedHashMap<String,String>();for(var entry:fields.entrySet()){String key=String.valueOf(entry.getKey());if(!OPTIONS.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string recipe field");result.put(key,text);}result.putAll(direct);result.put("recipe-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));return result;
    }
    static String help(){return """
        pdb2pqr-tools --input LOCAL.pdb --home ADMITTED_PRIVATE_HOME
          --force-field PARSE --mode prepare|assign-only
          --protonation forcefield|propka [--ph 7]
          --debump true|false --optimize true|false --water keep|drop
          --repair error|allow --unparameterized error|omit
          --neutral-n true|false --neutral-c true|false
          --state 1 --alternate error|label [--alt-label A]
          --length-units angstrom --max-heavy-shift 0.01 --out NEW_DIRECTORY
          [--max-atoms 100000] [--max-bytes 67108864] [--timeout 600]
          [--options-file UTF8_STRING_OPTIONS.json]
        Untouched original PDB2PQR3.8.0/PROPKA3.5.1/numpy1.26.4 with a private
        CPython3.12 x64 Windows runtime. Analysis never installs or downloads a PDB.
        Six original uppercase force fields:AMBER,CHARMM,PARSE,TYL06,PEOEPB,SWANSON.
        All model policies above are required. Forcefield protonation is not pH7.
        propka requires an explicit pH in[0,14]. Neutral terminal flags need PARSE.
        assign-only requires forcefield,debump=false,optimize=false,repair=error
        and unparameterized=error;no silently ignored optimization/pH choice.
        State is an input-order ordinal,not a MODEL serial. Alternate error rejects
        any labeled conformer;label retains blank+chosen label and clears the
        chosen alternate only in staged PDB. No occupancy heuristic or assembly.
        Original names remain canonical;no custom ffout,user field or MOL2 ligand.
        Native audit joins exact chain,residue/insertion,atom identities and checks
        PQR against independently parsed prepared PDB. Retained heavy atoms must
        satisfy max-heavy-shift plus0.001-Angstrom serialization allowance.
        Output-only heavy atoms fail unless repair=allow. Missing source atoms fail
        unless explicit unparameterized=omit;the resulting incomplete model warns.
        New/missing heavy identities may reflect repair OR naming;no fabricated
        lineage is inferred. Charged zero-radius atoms remain present.
        Original --noopt disables full optimization but can still optimize water.
        Read prepared.pqr,prepared.pdb,results.json,atom-lineage.json,original logs,
        session.json and prominent WARNINGS.md before subsequent APBS calculations.
        Exit0 completed with review warnings;exit3 missing/changed/unsupported or
        forbidden dependencies SKIP preparation;exit2 input/process/timeout/audit
        failure with quarantined partials. Existing outputs are preserved.
        Installer:python tools/literature/install_pdb2pqr.py --help.
        An ASCII-named UTF8 JSON string-options recipe supports Unicode Windows
        paths. Direct flags override recipe values;relative paths use current cwd.
        """;}
}

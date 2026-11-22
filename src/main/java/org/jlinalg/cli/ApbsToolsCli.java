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
import java.util.regex.Pattern;
import org.jlinalg.protein.ElectrostaticUnits;
import org.jlinalg.protein.MolecularGeometry.Point;
import org.jlinalg.protein.OpenDxGrid;
import org.jlinalg.protein.PqrStructure;

/** Original finite-difference solver from an explicitly charged single PQR model. */
final class ApbsToolsCli {
    private ApbsToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("input","home","charge-model","length-units","equation","boundary","nodes","lengths","center","solute-dielectric","solvent-dielectric","probe-radius","surface-density","temperature","salt-molarity","ion-radius","max-atoms","max-bytes","timeout","out","options-file");
    private static final Pattern ENERGY=Pattern.compile("Total electrostatic energy\\s*=\\s*([+\\-0-9.eE]+)\\s*kJ/mol");
    record Parameters(String equation,String boundary,int[] nodes,double[] lengths,double[] center,double solute,double solvent,double probe,double density,double temperature,double salt,double ionRadius) {
        Parameters { nodes=nodes.clone();lengths=lengths.clone();center=center.clone(); }
        @Override public int[] nodes(){return nodes.clone();}
        @Override public double[] lengths(){return lengths.clone();}
        @Override public double[] center(){return center.clone();}
        int points(){return nodes[0]*nodes[1]*nodes[2];}
        String script() {
            String ions=salt==0?"":" ion charge 1 conc "+salt+" radius "+ionRadius+"\n ion charge -1 conc "+salt+" radius "+ionRadius+"\n";
            return "read\n mol pqr source.pqr\nend\nelec name jlinalg\n mg-manual\n dime "+nodes[0]+" "+nodes[1]+" "+nodes[2]+"\n glen "+lengths[0]+" "+lengths[1]+" "+lengths[2]+"\n gcent "+center[0]+" "+center[1]+" "+center[2]+"\n mol 1\n "+equation+"\n bcfl "+boundary+"\n pdie "+solute+"\n sdie "+solvent+"\n chgm spl2\n srfm mol\n srad "+probe+"\n swin 0.3\n sdens "+density+"\n temp "+temperature+"\n"+ions+" calcenergy total\n calcforce no\n write pot dx potential\nend\nquit\n";
        }
        void covers(PqrStructure structure)throws IOException {
            for(var atom:structure.atoms()) {
                double[] coordinate={atom.coordinate().x(),atom.coordinate().y(),atom.coordinate().z()};
                for(int axis=0;axis<3;axis++)if(Math.abs(coordinate[axis]-center[axis])+atom.radius()+probe+lengths[axis]/(nodes[axis]-1)>lengths[axis]/2)throw new IOException("Grid does not cover PQR atom"+atom.number()+" with its radius,probe radius and one grid-cell margin;increase explicit lengths/recenter/refine");
            }
        }
        void audit(OpenDxGrid grid)throws IOException {
            if(grid.nx()!=nodes[0]||grid.ny()!=nodes[1]||grid.nz()!=nodes[2])throw new IOException("Original APBS grid counts differ from requested nodes");
            Point origin=grid.origin();double[] actualOrigin={origin.x(),origin.y(),origin.z()};Point[] delta=grid.deltas();
            for(int axis=0;axis<3;axis++) {
                // Original DX origin/delta text has about six decimal digits; allow its serialization rounding.
                double expected=center[axis]-lengths[axis]/2;if(Math.abs(actualOrigin[axis]-expected)>1e-5*Math.max(1,Math.abs(expected)))throw new IOException("APBS output grid origin differs from requested grid");
                double[] actualDelta={delta[axis].x(),delta[axis].y(),delta[axis].z()};for(int component=0;component<3;component++){expected=axis==component?lengths[axis]/(nodes[axis]-1):0;if(Math.abs(actualDelta[component]-expected)>1e-5*Math.max(1,Math.abs(expected)))throw new IOException("APBS output grid increment differs from requested grid");}
            }
        }
    }
    static Parameters parameters(Map<String,String> options) {
        if(!required(options,"length-units").equals("angstrom"))throw new IllegalArgumentException("PQR/APBS length units require explicit angstrom;no conversion is inferred");
        String equation=required(options,"equation"),boundary=required(options,"boundary");if(!Set.of("lpbe","npbe").contains(equation)||!Set.of("sdh","mdh","zero").contains(boundary))throw new IllegalArgumentException("Require equation lpbe|npbe and boundary sdh|mdh|zero");
        double[] requested=triple(required(options,"nodes"));int[] nodes=new int[3];for(int i=0;i<3;i++){if(requested[i]!=Math.rint(requested[i])||!Set.of(33.0,65.0,97.0,129.0).contains(requested[i]))throw new IllegalArgumentException("Each grid node count must be33,65,97 or129 in this bounded32bit profile");nodes[i]=(int)requested[i];}
        double[] lengths=triple(required(options,"lengths")),center=triple(required(options,"center"));for(int i=0;i<3;i++){if(lengths[i]<=0||lengths[i]>1000||Math.abs(center[i])>1000000)throw new IllegalArgumentException("Grid lengths require(0,1000] Angstrom and abs(center)<=1e6");if(lengths[i]/(nodes[i]-1)<1e-5)throw new IllegalArgumentException("Requested grid spacing is below supported DX text precision");}
        double solute=range(options,"solute-dielectric",1,1000),solvent=range(options,"solvent-dielectric",1,1000),probe=range(options,"probe-radius",0,10),density=range(options,"surface-density",1,100),temperature=range(options,"temperature",1,1000),salt=range(options,"salt-molarity",0,5),radius=0;
        if(salt>0)radius=range(options,"ion-radius",.01,10);else if(options.containsKey("ion-radius"))throw new IllegalArgumentException("--ion-radius requires positive --salt-molarity");
        String model=required(options,"charge-model");if(model.length()>2048||model.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require a bounded single-line charge-model provenance declaration");
        return new Parameters(equation,boundary,nodes,lengths,center,solute,solvent,probe,density,temperature,salt,radius);
    }
    private static double range(Map<String,String> options,String name,double minimum,double maximum){double value=Double.parseDouble(required(options,name));if(!Double.isFinite(value)||value<minimum||value>maximum)throw new IllegalArgumentException(name+" must be finite in["+minimum+","+maximum+"]");return value;}
    private static double[] triple(String text){String[] fields=text.split(",",-1);if(fields.length!=3)throw new IllegalArgumentException("Grid vectors require exactly three comma-separated numbers");double[] values=new double[3];for(int i=0;i<3;i++){values[i]=Double.parseDouble(fields[i]);if(!Double.isFinite(values[i]))throw new IllegalArgumentException("Grid vector numbers must be finite");}return values;}
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};
            externalTransaction(options,"original-apbs-finite-difference",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("requested_options",options);
                try {
                    Parameters parameters=parameters(options);int atoms=integer(options,"max-atoms",100000,1,1000000),bytes=integer(options,"max-bytes",536870912,1,Integer.MAX_VALUE),timeout=integer(options,"timeout",600,1,3600);
                    Path input=Path.of(required(options,"input")).toAbsolutePath().normalize();if(!Files.isRegularFile(input)||Files.size(input)>bytes)throw new IOException("PQR input is missing or exceeds --max-bytes");String sourceHash=hash(input);Path staged=directory.resolve("source.pqr");Files.copy(input,staged);if(!hash(staged).equals(sourceHash))throw new IOException("PQR changed while staging");
                    PqrStructure structure;try(var reader=Files.newBufferedReader(staged,StandardCharsets.US_ASCII)){structure=PqrStructure.read(reader,atoms);}parameters.covers(structure);
                    session.put("source_path",input.toString());session.put("source_sha256",sourceHash);session.put("declared_charge_model",options.get("charge-model"));session.put("source_pqr_remarks",structure.remarks());session.put("atoms",structure.atoms().size());session.put("total_charge_e",structure.totalCharge());session.put("zero_radius_atoms",structure.zeroRadiusAtoms());
                    session.put("solver_method","mg-manual;mol surface;spl2 charges;swin0.3;calcenergy total;calcforce no;single-thread CPU;one scalar potential field");session.put("grid_points",parameters.points());session.put("electrostatic_equation",parameters.equation());session.put("temperature_kelvin",parameters.temperature());session.put("salt_model",parameters.salt()==0?"no mobile ions":"symmetric monovalent ions with explicit equal concentration/radius");
                    Files.writeString(directory.resolve("input.in"),parameters.script(),StandardCharsets.US_ASCII);
                    String home=options.get("home");if(home==null||home.isBlank())throw new ApbsRuntime.Unavailable("No admitted APBS --home was supplied;analysis does not install software");
                    ApbsRuntime.Context runtime=ApbsRuntime.open(Path.of(home));ApbsRuntime.provenance(runtime,session);ApbsRuntime.version(runtime,directory,timeout,session);runtime.verify();
                    int exit=ApbsRuntime.execute(runtime,List.of("input.in"),directory,"solver",timeout,session);runtime.verify();if(exit!=0)throw new IOException("Original APBS scientific process returned exit"+exit+";version/help exit13 never counts as scientific success");
                    Path potential=directory.resolve("potential.dx");if(!Files.isRegularFile(potential)||Files.size(potential)>bytes)throw new IOException("APBS potential output is missing or exceeds --max-bytes");
                    OpenDxGrid grid;try(var reader=Files.newBufferedReader(potential,StandardCharsets.US_ASCII)){grid=OpenDxGrid.read(reader,parameters.points());}parameters.audit(grid);
                    String stdout=boundedLog(directory.resolve("solver.stdout")),stderr=boundedLog(directory.resolve("solver.stderr"));var match=ENERGY.matcher(stdout);if(!match.find())throw new IOException("Missing/nonfinite original total electrostatic energy;inspect solver logs and all grid values");double energy=Double.parseDouble(match.group(1));if(!Double.isFinite(energy)||match.find())throw new IOException("Invalid/ambiguous original total electrostatic energy");
                    var result=new LinkedHashMap<String,Object>();var stats=grid.statistics();result.put("potential_sha256",hash(potential));result.put("grid_points",grid.gridPoints());result.put("value_units","kt-per-e");result.put("temperature_kelvin",parameters.temperature());result.put("volts_per_kt_per_e",ElectrostaticUnits.ktPerEToVolts(1,parameters.temperature()));result.put("arithmetic_node_statistics",Map.of("minimum",stats.minimum(),"maximum",stats.maximum(),"mean",stats.mean(),"population_standard_deviation",stats.populationStandardDeviation()));result.put("raw_total_electrostatic_energy_kj_per_mol",energy);result.put("energy_interpretation","Original self-containing raw total energy;not solvation,binding or interaction free energy. Use a scientifically matched reference calculation and a separately validated subtraction protocol.");
                    if(!stderr.isBlank())warnings.add("UPSTREAM STDERR REQUIRES REVIEW:\n"+stderr);if(Pattern.compile("(?i)\\b(?:warning|error|failed|not converg|diverg)").matcher(stdout+stderr).find())warnings.add("UPSTREAM LOG DIAGNOSTICS: original output contains warning/error/convergence language. Required action: inspect solver.stdout and solver.stderr before scientific interpretation;finite output alone does not establish convergence/model validity.");
                    if(structure.zeroRadiusAtoms()>0)warnings.add("ZERO RADII RETAINED: "+structure.zeroRadiusAtoms()+" supplied atoms have radius0,including possible charged force-field hydrogens. Required action: review the declared charge/radius model;these atoms were retained without charge or radius replacement.");
                    if(!hash(input).equals(sourceHash)||!hash(staged).equals(sourceHash))throw new IOException("Supplied/staged PQR changed during analysis");
                    if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("recipe-sha256")))throw new IOException("Options recipe changed during analysis");runtime.verify();
                    Files.writeString(directory.resolve("results.json"),RegenieRuntime.json(result)+"\n",StandardCharsets.UTF_8);session.put("results",result);manifest.put("analysis_status","completed-with-warnings");
                    warnings.add("MODEL AND ENERGY REVIEW REQUIRED: APBS3.5.0 solves the declared LPBE/NPBE model from supplied PQR coordinates,charges and radii. No force field,protonation,pH,atom repair or PDB2PQR preparation is inferred. The Windows original is32bit x86;only the documented bounded mg-manual/mol/spl2 profile is admitted. Potential values are kT/e at the declared temperature;SI2019 k_B/e supplies the voltage conversion. Raw total energy includes grid self-energy and is not a solvation or binding free energy. Check grid refinement,domain extent,boundaries,dielectrics,salt,charge model and original convergence logs for the intended scientific use. This command does not create a PyMOL surface figure or reproduce the source papers' unstated parameters.");
                }catch(ApbsRuntime.Unavailable unavailable){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency");session.put("error",unavailable.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS SKIPPED: "+unavailable.getMessage());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage());}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("WARNINGS.md"),"# APBS "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+ApbsRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Original APBS finite-difference calculation completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\n"+ApbsRuntime.recovery());return 2;}
    }
    private static String boundedLog(Path path)throws IOException {if(Files.size(path)>8388608)throw new IOException("Original solver log exceeds8MiB;retain diagnostics and review convergence before rerunning");return Files.readString(path,StandardCharsets.UTF_8);}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require a UTF8 JSON options object of at most1MiB");byte[] raw=Files.readAllBytes(path);Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(value instanceof Map<?,?> map))throw new IOException("Options recipe must be a JSON object");var result=new LinkedHashMap<String,String>();for(var entry:map.entrySet()){String key=String.valueOf(entry.getKey());if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string recipe option");result.put(key,text);}result.putAll(direct);result.put("recipe-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));return result;
    }
    static String help(){return """
        apbs-tools --input SINGLE_MODEL.pqr --home ADMITTED_PRIVATE_APBS_HOME
          --charge-model "explicit source/force-field/protonation declaration"
          --length-units angstrom --equation lpbe|npbe --boundary sdh|mdh|zero
          --nodes 65,65,65 --lengths 60,60,60 --center 10,9,6
          --solute-dielectric 2 --solvent-dielectric 80 --probe-radius 1.4
          --surface-density 10 --temperature 298.15 --salt-molarity 0
          --out NEW_DIRECTORY [--ion-radius 2] [--timeout 600]
          [--max-atoms 100000] [--max-bytes 536870912] [--options-file OPTIONS.json]
        Original APBS3.5.0 private Windows profile;32bit x86 executable on Windows
        x86_64. Complete authenticated release;analysis never installs software.
        Explicit PQR charges in electrons,coordinates/radii in Angstrom;no inferred
        pH,protonation,force field,repair or PDB2PQR execution. Zero radii are retained.
        mg-manual with mol surface,spl2 charge mapping,swin0.3,single-thread CPU.
        Each node count is33,65,97 or129. Lengths in(0,1000] Angstrom;spacing>=1e-5.
        Grid must cover every atom with radius+probe+one cell of margin.
        Positive salt adds equal-concentration +1/-1 ions and requires ion-radius.
        No mobile ions at salt0;ion-radius must then be omitted. All model inputs
        above are required. Optional limits bound atom count,bytes and wall time.
        Every output node,grid geometry and finite raw energy is audited.
        Version/help exit13 is expected ONLY for informational probes;scientific
        runs require exit0 plus valid output. NaN maps fail even after exit0.
        Read potential.dx,results.json,session.json,original logs and WARNINGS.md.
        Potential units kT/e;volts use exact SI2019 k_B/e and declared temperature.
        Raw total energy includes self-energy;it is NOT solvation/binding energy.
        Exit0 completed with review warnings;exit3 unavailable/changed/forbidden
        dependencies SKIP analysis;exit2 invalid input/process/output/audit failure.
        Failed/skipped partials are retained under partial/. Existing output is
        preserved. Installer:python tools/literature/install_apbs.py --help.
        An ASCII-named UTF8 JSON string-options recipe supports Unicode Windows
        paths;direct flags override recipe values. Rerun to a new output directory.
        """;}
}

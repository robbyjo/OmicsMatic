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
import org.jlinalg.protein.ElectrostaticUnits;
import org.jlinalg.protein.OpenDxGrid;
import org.jlinalg.protein.PqrStructure;

/** Original map-colored molecular surfaces with native field/frame/radius audits. */
final class PymolPotentialCli {
    private PymolPotentialCli() { }
    private static final Set<String> OPTIONS=Set.of("input","grid","home","charge-model","length-units","value-units","temperature","surface-radii","surface-solvent","probe-radius","sample-location","surface-quality","ramp-range","low-color","middle-color","high-color","background","width","height","buffer","view","max-atoms","max-grid-points","max-bytes","timeout","out","options-file");
    record Choices(String units,double temperature,String radii,String solvent,double probe,int above,int quality,List<Double> levels,List<List<Double>> colors,List<Double> background,int width,int height,double buffer,List<Double> view) { }
    static Choices choices(Map<String,String> options) {
        if(!required(options,"length-units").equals("angstrom"))throw new IllegalArgumentException("PQR/DX surfaces require explicit angstrom units;no coordinate conversion/alignment is inferred");String model=required(options,"charge-model");if(model.length()>2048||model.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require bounded single-line charge-model provenance");
        String units=required(options,"value-units"),radii=required(options,"surface-radii"),solvent=required(options,"surface-solvent"),location=required(options,"sample-location");if(!Set.of("kt-per-e","volt","arbitrary").contains(units)||!Set.of("source-pqr","pymol").contains(radii)||!Set.of("excluded","accessible").contains(solvent)||!Set.of("surface","probe-offset").contains(location))throw new IllegalArgumentException("Unsupported explicit value-unit/radius/surface/sample-location policy");
        double temperature=0;if(units.equals("kt-per-e")){temperature=Double.parseDouble(required(options,"temperature"));ElectrostaticUnits.ktPerEToVolts(1,temperature);}else if(options.containsKey("temperature"))throw new IllegalArgumentException("Temperature applies only to declared kT/e units;no APBS temperature is inferred");
        double probe=finite(options,"probe-radius",0,10),buffer=finite(options,"buffer",0,1000);int quality=Integer.parseInt(required(options,"surface-quality")),width=Integer.parseInt(required(options,"width")),height=Integer.parseInt(required(options,"height"));if(quality<0||quality>2||width<400||width>4000||height<300||height>4000)throw new IllegalArgumentException("Surface quality0..2,width400..4000,height300..4000 required");
        List<Double> levels=vector(required(options,"ramp-range"),3);for(int i=0;i<3;i++)if(!Float.isFinite(levels.get(i).floatValue())||i>0&&!(levels.get(i).floatValue()>levels.get(i-1).floatValue()))throw new IllegalArgumentException("Three ramp levels must remain finite and strictly increasing after original float32 rounding");
        List<List<Double>> colors=List.of(rgb(required(options,"low-color")),rgb(required(options,"middle-color")),rgb(required(options,"high-color")));List<Double> background=rgb(required(options,"background")),view=null;
        if(options.containsKey("view")){view=vector(options.get("view"),18);camera(view);}
        return new Choices(units,temperature,radii,solvent,probe,location.equals("surface")?0:1,quality,levels,colors,background,width,height,buffer,view);
    }
    private static double finite(Map<String,String> options,String key,double minimum,double maximum){double value=Double.parseDouble(required(options,key));if(!Double.isFinite(value)||value<minimum||value>maximum)throw new IllegalArgumentException(key+" requires finite["+minimum+","+maximum+"]");return value;}
    private static List<Double> vector(String text,int count){String[] fields=text.split(",",-1);if(fields.length!=count)throw new IllegalArgumentException("Require exactly"+count+" comma-separated numbers");var result=new ArrayList<Double>();for(String field:fields){double value=Double.parseDouble(field);if(!Double.isFinite(value)||Math.abs(value)>1e6)throw new IllegalArgumentException("Vector values require finite abs<=1e6");result.add(value);}return List.copyOf(result);}
    private static List<Double> rgb(String text){List<Double> values=vector(text,3);for(double value:values)if(value<0||value>1)throw new IllegalArgumentException("RGB channels require[0,1]");return values;}
    static void camera(List<Double> view) {
        if(view.size()!=18)throw new IllegalArgumentException("Camera requires18 numbers");for(int row=0;row<3;row++)for(int other=0;other<3;other++){double dot=0;for(int j=0;j<3;j++)dot+=view.get(3*row+j)*view.get(3*other+j);if(Math.abs(dot-(row==other?1:0))>1e-4)throw new IllegalArgumentException("Camera rotation is not orthonormal");}
        double determinant=view.get(0)*(view.get(4)*view.get(8)-view.get(5)*view.get(7))-view.get(1)*(view.get(3)*view.get(8)-view.get(5)*view.get(6))+view.get(2)*(view.get(3)*view.get(7)-view.get(4)*view.get(6));if(Math.abs(determinant-1)>1e-4||view.get(15)<=0||view.get(16)<=view.get(15))throw new IllegalArgumentException("Camera requires a proper rotation and ordered positive clipping distances");
    }
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};
            externalTransaction(options,"original-pymol-potential-surface",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("requested_options",options);
                try {
                    Choices choices=choices(options);int atoms=integer(options,"max-atoms",100000,1,100000),points=integer(options,"max-grid-points",4194304,8,16777216),bytes=integer(options,"max-bytes",536870912,1,Integer.MAX_VALUE),timeout=integer(options,"timeout",600,1,3600);var originals=new LinkedHashMap<Path,String>();
                    for(String key:List.of("input","grid")){Path source=Path.of(required(options,key)).toAbsolutePath().normalize();if(!Files.isRegularFile(source)||Files.size(source)>bytes)throw new IOException("Missing/oversized "+key+" source");String before=hash(source);Path target=directory.resolve(key.equals("input")?"source.pqr":"potential.dx");Files.copy(source,target);if(!hash(target).equals(before))throw new IOException("Source changed during staging");originals.put(source,before);}
                    PqrStructure pqr;OpenDxGrid grid;try(var reader=Files.newBufferedReader(directory.resolve("source.pqr"),StandardCharsets.UTF_8)){pqr=PqrStructure.read(reader,atoms);}try(var reader=Files.newBufferedReader(directory.resolve("potential.dx"),StandardCharsets.UTF_8)){grid=OpenDxGrid.read(reader,points);}PymolPotentialAudit.geometry(grid);var field=PymolPotentialAudit.field(grid);
                    Path renderGrid=directory.resolve("render-grid.dx");PymolPotentialAudit.stageFloat32Grid(grid,renderGrid);String renderGridHash=hash(renderGrid);
                    var recipe=new LinkedHashMap<String,Object>();recipe.put("pqr",directory.resolve("source.pqr").toString());recipe.put("grid",renderGrid.toString());recipe.put("source_atoms",PymolPotentialAudit.sourceAtoms(pqr));recipe.put("grid_counts",List.of(grid.nx(),grid.ny(),grid.nz()));recipe.put("grid_minimum",PymolPotentialAudit.values(grid.origin()));recipe.put("grid_maximum",PymolPotentialAudit.values(grid.coordinate(grid.nx()-1,grid.ny()-1,grid.nz()-1)));recipe.put("maximum_atoms",atoms);recipe.put("maximum_grid_points",points);recipe.put("surface_radii",choices.radii);recipe.put("surface_solvent",choices.solvent);recipe.put("probe_radius",choices.probe);recipe.put("ramp_above_mode",choices.above);recipe.put("surface_quality",choices.quality);recipe.put("levels",choices.levels);recipe.put("colors",choices.colors);recipe.put("background",choices.background);recipe.put("width",choices.width);recipe.put("height",choices.height);recipe.put("buffer",choices.buffer);recipe.put("view",choices.view);Path stagedRecipe=directory.resolve("worker-recipe.json");Files.writeString(stagedRecipe,RegenieRuntime.json(recipe)+"\n",StandardCharsets.UTF_8);String recipeHash=hash(stagedRecipe);
                    session.put("source_sha256",originals.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),Map.Entry::getValue)));session.put("declared_charge_model",options.get("charge-model"));session.put("value_units",choices.units);if(choices.units.equals("kt-per-e")){session.put("declared_temperature_kelvin",choices.temperature);session.put("volts_per_kt_per_e",ElectrostaticUnits.ktPerEToVolts(1,choices.temperature));}
                    Path home=Path.of(options.getOrDefault("home",System.getenv().getOrDefault("JLINALG_PYMOL_HOME","build/absent-pymol-home")));Path runtimeWork=Files.createDirectory(directory.resolve("runtime"));var runtime=new PymolRuntime(home,runtimeWork,session);Path worker=directory.resolve("pymol-potential-worker.py");try(var input=PymolPotentialCli.class.getResourceAsStream("/org/jlinalg/omics/pymol_potential_worker.py")){if(input==null)throw new IOException("Bundled potential-surface worker missing");Files.copy(input,worker);}String workerHash=hash(worker);runtime.execute(worker,List.of(runtime.sitePackages().toString(),stagedRecipe.toString(),directory.toString()),directory,"surface",timeout,1);
                    var audit=PymolPotentialAudit.audit(directory,pqr,grid,recipe,field);audit.put("canonical_render_grid_sha256",renderGridHash);audit.put("canonical_render_grid_scope","Shortest round-trip float32 ASCII DX values/origins avoid the original3.1.0 parser's20-character numeric-token truncation. Raw potential.dx retained;complete map audit remains exact against every native float32-rounded source node.");if(Files.size(directory.resolve("surface.png"))>bytes)throw new IOException("Original surface PNG exceeds --max-bytes");var figure=PymolPotentialFigure.write(directory,recipe,choices.units,choices.temperature);audit.put("figure",figure);
                    for(var entry:originals.entrySet())if(!hash(entry.getKey()).equals(entry.getValue()))throw new IOException("Original molecular/grid source changed during rendering");if(!hash(stagedRecipe).equals(recipeHash)||!hash(worker).equals(workerHash)||!hash(renderGrid).equals(renderGridHash))throw new IOException("Trusted surface recipe/worker/rendering grid changed during analysis");if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("recipe-sha256")))throw new IOException("Options recipe changed during analysis");runtime.verify();
                    Files.writeString(directory.resolve("native-audit.json"),RegenieRuntime.json(audit)+"\n",StandardCharsets.UTF_8);session.put("native_audit",audit);manifest.put("analysis_status","completed-with-warnings");
                    warnings.add("SURFACE / FRAME / RADIUS REVIEW REQUIRED: original PyMOL3.1.0 renders the supplied map in the supplied PQR coordinate frame;no alignment,charge preparation or PB solve is inferred. Every loaded charge/coordinate,declared radius policy and float32 map node/order was audited. PyMOL loads its own display radii from PQR;source-pqr explicitly overrides them,including zeros. PQR does not encode elements/bonds;original display inference is retained in ledgers. Inspect original-surface.json,native-audit.json,figure.svg and surface.png. Image framing checks are QC heuristics,not biological validation.");
                    warnings.add("UNITS AND SAMPLING: the unit-labeled standalone figure.svg embeds the unchanged original surface PNG and a scalar ramp. Values/units/temperature are caller declarations;no historical APBS constants or source-paper parameters are inferred. Original PyMOL uses float32 map/atom data. sample-location=probe-offset evaluates one probe radius along the original surface normal;surface evaluates vertices. Conservative map coverage prevents outside-grid color admission. Lighting affects apparent RGB. Raw energy,solvation/binding effects and protonation states cannot be inferred from colors.");
                    warnings.add("DX PARSER / PRECISION: original PyMOL3.1.0 truncates ASCII numeric tokens to20 characters and can silently lose a long exponent. Required action: retain raw potential.dx and canonical render-grid.dx. This command writes shortest round-trip float32 rendering tokens and still verifies every loaded node exactly against the float32-rounded raw source. No scientific tolerance was loosened and no solver values were replaced at higher precision.");
                    if(choices.radii.equals("pymol"))warnings.add("DISPLAY RADII DIFFER FROM FORCE-FIELD RADII: explicit pymol policy retains original display radii. They need not equal the PQR radii used by an electrostatic solver. Required action: retain actual ledgers and justify this surface definition;no radii equivalence is claimed.");
                    if(field.underflowedNonzeroValues()>0)warnings.add("FLOAT32 UNDERFLOW: "+field.underflowedNonzeroValues()+" finite nonzero source nodes round to0 in original PyMOL. Required action: review scale/units and retain the precision loss;no replacement precision is fabricated.");
                    for(String name:List.of("surface.stdout","surface.stderr")){Path path=directory.resolve(name);if(Files.size(path)>8388608)throw new IOException("Original surface log exceeds8MiB");try(var lines=Files.lines(path,StandardCharsets.UTF_8)){lines.filter(line->line.matches("(?i).*(warning|error|not found|fallback).*" )).limit(100).forEach(line->warnings.add("UPSTREAM LOG REVIEW("+name+"): "+line));}}
                }catch(PymolRuntime.Unavailable failure){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"SURFACE ANALYSIS SKIPPED: "+failure.getMessage()+PymolRuntime.setup());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"SURFACE ANALYSIS FAILED: "+failure.getMessage());}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("WARNINGS.md"),"# POTENTIAL SURFACE "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: correct the named source,coordinate frame,units,radius policy,ramp,camera,resource or original-program issue and rerun to a new output directory. Read retained partial/ diagnostics;failed/skipped figures are not successful results. "+PymolRuntime.setup()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"SURFACE WARNINGS: ":code[0]==3?"SURFACE ANALYSIS SKIPPED: ":"SURFACE ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Original map-colored molecular surface and native audits completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\nRequired action: correct the named options/input and rerun to a new output directory."+PymolRuntime.setup());return 2;}
    }
    private static Map<String,String> parse(String[] args)throws Exception {var direct=options(args,OPTIONS);if(!direct.containsKey("options-file"))return direct;Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require a UTF8 JSON string-options recipe at most1MiB");byte[] raw=Files.readAllBytes(path);Object parsed=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(parsed instanceof Map<?,?> map))throw new IOException("Require an options object");var result=new LinkedHashMap<String,String>();for(var entry:map.entrySet()){String key=String.valueOf(entry.getKey());if(!OPTIONS.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string recipe field");result.put(key,text);}result.putAll(direct);result.put("recipe-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));return result;}
    static String help(){return """
        potential-surface --input MODEL.pqr --grid POTENTIAL.dx --home PYMOL_HOME
          --charge-model "explicit provenance" --length-units angstrom
          --value-units kt-per-e|volt|arbitrary [--temperature 298.15]
          --surface-radii source-pqr|pymol --surface-solvent excluded|accessible
          --probe-radius 1.4 --sample-location surface|probe-offset
          --surface-quality 1 --ramp-range -5,0,5
          --low-color 1,0,0 --middle-color 1,1,1 --high-color 0,0,1
          --background 1,1,1 --width 1200 --height 1000 --buffer 6
          --out NEW_DIRECTORY [--view 18_COMMA_SEPARATED_NUMBERS]
          [--max-atoms 100000] [--max-grid-points 4194304]
          [--max-bytes 536870912] [--timeout 600] [--options-file UTF8_OPTIONS.json]
        Original PyMOL3.1.0 Linux x86_64 runtime;on Windows use Linux Java in WSL.
        This command renders a supplied map;it does not prepare charges,run APBS,
        align models or compute energies. Analysis never installs software.
        Every model/surface/ramp policy above is required. kT/e requires explicit
        temperature;volt/arbitrary forbid temperature. No unit conversion inferred.
        Positive axis-aligned scalar DX only;all values must fit finite float32.
        Raw potential.dx is retained. Canonical render-grid.dx uses short round-trip
        float32 tokens to avoid original3.1.0's20-character numeric-token truncation;
        every loaded source node is still verified exactly at float32 precision.
        Native electrostatic-grid handles general affine sampling separately.
        PQR loads original PyMOL display radii. source-pqr explicitly applies the
        supplied force-field radii,including zero;you must choose the radius policy.
        Surface radii0..20 Angstrom;probe0..10. Map must conservatively cover actual
        display radii+probe+any probe-offset. No outside-grid color is admitted.
        probe-offset samples one probe radius along the original surface normal;
        surface samples vertices. Three ramp values must remain strictly increasing
        after float32 rounding. RGB colors are explicit channels in[0,1].
        Surface quality0..2;image width400..4000,height300..4000. Optional camera
        requires a proper orthonormal rotation and positive ordered clip planes.
        Original surface.png is retained. Standalone figure.svg embeds that raster
        and an explicit unit/temperature/radius/sampling legend. PNG pixel-QC is a
        framing heuristic;inspect clipping,occlusion,colors and model validity.
        Read original-surface.json,native-audit.json,session.json and WARNINGS.md.
        Exit0 completed with warnings;exit3 unavailable/changed/unsupported or
        forbidden dependencies SKIP analysis;exit2 input/process/timeout/audit
        failures quarantine partials. Existing output directories are preserved.
        An ASCII-named UTF8 string-options recipe supports Unicode paths;direct
        flags override strings and relative paths resolve from current cwd.
        """;}
}

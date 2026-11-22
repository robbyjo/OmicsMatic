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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.protein.ElectrostaticUnits;
import org.jlinalg.protein.MolecularGeometry.Point;
import org.jlinalg.protein.OpenDxGrid;

/** Native scalar-grid inspection/sampling, independent of any APBS runtime. */
final class ElectrostaticGridCli {
    private ElectrostaticGridCli() { }
    private static final Set<String> ALLOWED=Set.of("input","points","length-units","value-units","temperature","outside","max-grid-points","max-points","max-bytes","out","options-file");
    private static final String RECOVERY="Required action: correct the named grid,point,unit,temperature,bounds or resource-limit issue and rerun to a new output directory. Review original coordinates and WARNINGS.md. Failed scientific partials are diagnostic data under partial/,not successful results. Native grid inspection/sampling needs only Java;it does not prepare charges or solve APBS electrostatics.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1 && args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};
            transaction(options,"native-electrostatic-grid",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("engine","native-java");session.put("requested_options",options);
                try {
                    String length=required(options,"length-units"),units=required(options,"value-units");
                    double multiplier=switch(length){case "angstrom"->1;case "nanometer"->10;default->throw new IllegalArgumentException("--length-units requires angstrom or nanometer;units are never inferred");};
                    if(!Set.of("arbitrary","kt-per-e").contains(units))throw new IllegalArgumentException("--value-units requires arbitrary or kt-per-e");
                    double temperature=0;
                    if(units.equals("kt-per-e")){temperature=Double.parseDouble(required(options,"temperature"));ElectrostaticUnits.ktPerEToVolts(0,temperature);}
                    else if(options.containsKey("temperature"))throw new IllegalArgumentException("--temperature applies only to kt-per-e values");
                    boolean sampling=options.containsKey("points");
                    if(!sampling && (options.containsKey("outside") || options.containsKey("max-points")))throw new IllegalArgumentException("--outside/--max-points require --points");
                    String policy=sampling?required(options,"outside"):"error";
                    if(!Set.of("error","missing").contains(policy))throw new IllegalArgumentException("--outside requires error or missing;no extrapolation");
                    int maximum=integer(options,"max-grid-points",4194304,8,67108864),maxPoints=integer(options,"max-points",1000000,1,1000000),maxBytes=integer(options,"max-bytes",536870912,1,Integer.MAX_VALUE);
                    Path input=Path.of(required(options,"input")).toAbsolutePath().normalize();if(!Files.isRegularFile(input) || Files.size(input)>maxBytes)throw new IOException("Missing input or grid exceeds --max-bytes");
                    String inputHash=hash(input);Path original=directory.resolve("original.dx");Files.copy(input,original);if(!hash(original).equals(inputHash))throw new IOException("Grid changed while staging");
                    OpenDxGrid grid;try(var reader=Files.newBufferedReader(original,StandardCharsets.UTF_8)){grid=OpenDxGrid.read(reader,maximum).scaleCoordinates(multiplier);}
                    var statistics=grid.statistics();var result=new LinkedHashMap<String,Object>();
                    result.put("counts",List.of(grid.nx(),grid.ny(),grid.nz()));result.put("grid_points",grid.gridPoints());result.put("origin_angstrom",xyz(grid.origin()));result.put("deltas_angstrom",java.util.Arrays.stream(grid.deltas()).map(ElectrostaticGridCli::xyz).toList());
                    result.put("serialization_order","(x*ny+y)*nz+z;z varies fastest");result.put("source_scalar_type",grid.scalarType());result.put("value_units",units);result.put("arithmetic_node_statistics",Map.of("minimum",statistics.minimum(),"maximum",statistics.maximum(),"mean",statistics.mean(),"population_standard_deviation",statistics.populationStandardDeviation()));
                    if(units.equals("kt-per-e")){result.put("declared_temperature_kelvin",temperature);result.put("volts_per_kt_per_e",ElectrostaticUnits.ktPerEToVolts(1,temperature));result.put("voltage_conversion_constants","exact SI2019 k_B/e;no inference of the original solver's historical CODATA constants");}
                    session.put("source_path",input.toString());session.put("source_sha256",inputHash);session.put("length_input_units",length);session.put("input_length_to_angstrom_multiplier",multiplier);session.put("grid_comments",grid.comments());
                    if(sampling) {
                        Path points=Path.of(options.get("points")).toAbsolutePath().normalize();if(!Files.isRegularFile(points) || Files.size(points)>maxBytes)throw new IOException("Missing point file or points exceed --max-bytes");
                        String pointsHash=hash(points);Path staged=directory.resolve("original-points.tsv");Files.copy(points,staged);if(!hash(staged).equals(pointsHash))throw new IOException("Points changed while staging");
                        long rows=0,missing=0,adjusted=0;var ids=new HashSet<String>();
                        try(var reader=Files.newBufferedReader(staged,StandardCharsets.UTF_8);var writer=Files.newBufferedWriter(directory.resolve("samples.tsv"),StandardCharsets.UTF_8)) {
                            if(!"point_id\tx\ty\tz".equals(reader.readLine()))throw new IOException("Require exact TSV point header:point_id<TAB>x<TAB>y<TAB>z");
                            writer.write("point_id\tx_angstrom\ty_angstrom\tz_angstrom\tinside\tvalue\tvalue_unit\tvolts\tgrid_x\tgrid_y\tgrid_z\troundoff_adjusted\n");String line;
                            while((line=reader.readLine())!=null) {
                                if(++rows>maxPoints || line.length()>4096)throw new IOException("Points exceed declared row/line bound");String[] fields=line.split("\t",-1);if(fields.length!=4 || fields[0].isBlank() || fields[0].length()>1024 || fields[0].chars().anyMatch(Character::isISOControl) || !ids.add(fields[0]))throw new IOException("Require unique nonblank point IDs and four TSV fields at row"+(rows+1));
                                Point point=new Point(Double.parseDouble(fields[1])*multiplier,Double.parseDouble(fields[2])*multiplier,Double.parseDouble(fields[3])*multiplier);var sample=grid.sample(point,policy.equals("error")?OpenDxGrid.Outside.ERROR:OpenDxGrid.Outside.MISSING);
                                if(!sample.inside())missing++;if(sample.roundoffAdjusted())adjusted++;
                                String value=sample.inside()?Double.toString(sample.value()):"",volts=sample.inside() && units.equals("kt-per-e")?Double.toString(ElectrostaticUnits.ktPerEToVolts(sample.value(),temperature)):"";
                                writer.write(fields[0]+"\t"+point.x()+"\t"+point.y()+"\t"+point.z()+"\t"+sample.inside()+"\t"+value+"\t"+units+"\t"+volts+"\t"+sample.fractionalIndex().x()+"\t"+sample.fractionalIndex().y()+"\t"+sample.fractionalIndex().z()+"\t"+sample.roundoffAdjusted()+"\n");
                            }
                        }
                        if(rows==0)throw new IOException("Point table has no data rows");if(!hash(points).equals(pointsHash))throw new IOException("Point source changed during analysis");
                        session.put("point_source_path",points.toString());session.put("point_source_sha256",pointsHash);result.put("point_rows",rows);result.put("out_of_grid_rows",missing);result.put("roundoff_adjusted_rows",adjusted);
                        if(missing>0)warnings.add("OUT-OF-GRID SAMPLES: "+missing+" point(s) have blank values and inside=false under the explicit missing policy. Required action: retain their status,review coordinate frames/units,or supply a covering grid;no extrapolation or zero fill occurred.");
                        if(adjusted>0)warnings.add("INDEX ROUNDOFF: "+adjusted+" point(s) were adjusted within32 ulps of the closed index bounds. Review roundoff_adjusted and the original point coordinates.");
                    }
                    if(!hash(input).equals(inputHash))throw new IOException("Grid source changed during analysis");
                    if(options.containsKey("options-file") && !hash(Path.of(options.get("options-file"))).equals(options.get("recipe-sha256")))throw new IOException("Options recipe changed during analysis");
                    Files.writeString(directory.resolve("grid.json"),RegenieRuntime.json(result)+"\n",StandardCharsets.UTF_8);session.put("grid",result);manifest.put("analysis_status","completed-with-warnings");
                    warnings.add("GRID AND MODEL REVIEW: this native command reads one complete regular scalar OpenDX field and performs trilinear sampling in its affine coordinates. It does not run APBS,assign protonation/charges/radii,infer a force field,compute solvation/binding energies or establish biological validity. Declared units and temperature are caller responsibilities. Statistics describe nodes,not volume averages or energies. Zero/constant grids are valid values,not missing data.");
                    if(grid.scalarType().equals("float"))warnings.add("SOURCE PRECISION: the declared OpenDX float array was rounded to binary32 before promotion to native double sampling. A double array is parsed directly as binary64. Values are not promoted to a claim of additional source precision.");
                    if(multiplier==10)warnings.add("LENGTH CONVERSION: both grid geometry and point coordinates were explicitly multiplied by10 from nanometers to Angstrom;scalar values were unchanged.");
                }catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage());}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);
                Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("WARNINGS.md"),"# ELECTROSTATIC GRID "+(code[0]==0?"WARNINGS REQUIRE REVIEW":"FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Native scalar-grid inspection/sampling completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\n"+RECOVERY);return 2;}
    }
    private static List<Double> xyz(Point point){return List.of(point.x(),point.y(),point.z());}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require a UTF8 JSON options object of at most1MiB");byte[] raw=Files.readAllBytes(path);Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if(!(value instanceof Map<?,?> map))throw new IOException("Options recipe must be a JSON object");var result=new LinkedHashMap<String,String>();
        for(var e:map.entrySet()){String key=String.valueOf(e.getKey());if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String text))throw new IOException("Unknown,recursive or non-string options recipe field");result.put(key,text);}result.putAll(direct);result.put("recipe-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));return result;
    }
    static String help(){return """
        electrostatic-grid --input MAP.dx --length-units angstrom|nanometer
          --value-units arbitrary|kt-per-e --out NEW_DIRECTORY
          [--temperature KELVIN] [--points POINTS.tsv --outside error|missing]
          [--max-grid-points 4194304] [--max-points 1000000]
          [--max-bytes 536870912] [--options-file UTF8_OPTIONS.json]
        Native Java grid inspection/sampling;no APBS or Python runtime is required.
        This command does not prepare charges or solve electrostatics. The input must
        contain one complete APBS-style ASCII regular scalar OpenDX field,at least
        two nodes per axis,z varying fastest;finite nonsingular affine delta vectors.
        Units are required and never inferred from comments. kt-per-e requires an
        explicit positive temperature in kelvin;volts use exact SI2019 k_B/e.
        Nanometers multiply BOTH grid geometry and points by10 to output Angstrom.
        Point TSV header is exactly:point_id<TAB>x<TAB>y<TAB>z. IDs are unique.
        Sampling is trilinear. Closed grid bounds;no extrapolation or zero filling.
        missing retains outside rows with inside=false and blank value/volts.
        Zero/constant grids remain valid. Node statistics are not energies.
        Bounds allow at most32 ulps of index roundoff,reported per affected row.
        Read grid.json,samples.tsv,session.json,analysis-status.json and WARNINGS.md.
        Exit0 completed with review warnings;exit2 input/process/audit failure with
        quarantined partials and named recovery. Existing outputs are preserved.
        An ASCII-named UTF8 options recipe supports Unicode Windows paths;direct
        flags override recipe strings. Rerun into a new output directory.
        """;}
}

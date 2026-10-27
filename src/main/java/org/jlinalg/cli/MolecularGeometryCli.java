/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import jdistlib.accelerator.CpuComputeBackend;
import org.jlinalg.protein.MolecularGeometry;
import org.jlinalg.protein.MolecularGeometry.Point;

/** Explicit-unit measurements and all-pairs proper rigid fitting; no external runtime. */
final class MolecularGeometryCli {
    private MolecularGeometryCli() { }
    private static final Set<String> ALLOWED=Set.of("operation","input","units","degeneracy","max-rows","max-bytes","options-file","out");
    private static final String FIT_HEADER="pair_id\tmobile_x\tmobile_y\tmobile_z\ttarget_x\ttarget_y\ttarget_z";
    private static final String MEASURE_HEADER="measurement_id\tkind\tax\tay\taz\tbx\tby\tbz\tcx\tcy\tcz\tdx\tdy\tdz";
    private static final String RECOVERY="Required action: correct the named input, unit, pairing, degenerate-geometry or resource-limit issue; retain the original coordinates and rerun to a new directory. Failed scientific partials are under partial/ and must not be used as successful output. Native molecular-geometry needs only Java; no PyMOL installation is required.";

    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1 && args[0].equals("--help")){out.println(help());return 0;}
        try {
            var options=parse(args);required(options,"out");int[] code={0};
            transaction(options,"native-molecular-geometry",(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                session.put("engine","native-java");session.put("requested_options",options);
                session.put("scope","Euclidean single-geometry measurements and unweighted proper rigid fitting of supplied explicit coordinate pairs. No PyMOL align/super, sequence/atom matching, scale fitting, reflection, outlier rejection, symmetry, periodic images, force field, uncertainty or biological inference.");
                try {
                    String operation=required(options,"operation"),units=required(options,"units");
                    if(!Set.of("measure","fit").contains(operation))throw new IllegalArgumentException("--operation requires measure or fit");
                    double factor=switch(units){case "angstrom"->1;case "nanometer"->10;default->throw new IllegalArgumentException("--units requires angstrom or nanometer; units are never inferred");};
                    if(operation.equals("measure") && options.containsKey("degeneracy"))throw new IllegalArgumentException("--degeneracy applies only to fit; undefined measurement angles/torsions always fail");
                    String policy=operation.equals("fit")?required(options,"degeneracy"):"error";
                    if(!Set.of("error","allow-nonunique").contains(policy))throw new IllegalArgumentException("--degeneracy requires error or allow-nonunique");
                    int maximum=integer(options,"max-rows",1_000_000,1,1_000_000);
                    int bytes=integer(options,"max-bytes",268_435_456,1,Integer.MAX_VALUE);
                    Path input=Path.of(required(options,"input")).toAbsolutePath().normalize();
                    if(!Files.isRegularFile(input)||Files.size(input)>bytes)throw new IOException("Input is missing or exceeds --max-bytes: "+input);
                    String inputHash=hash(input);session.put("source_path",input.toString());session.put("source_sha256",inputHash);
                    session.put("input_units",units);session.put("coordinate_output_units","angstrom");session.put("angular_output_units","degree");session.put("input_to_angstrom_multiplier",factor);
                    long rows;
                    try(var reader=Files.newBufferedReader(input,StandardCharsets.UTF_8)) {
                        if(operation.equals("measure"))rows=measure(reader,dir,maximum,factor);
                        else rows=fit(reader,dir,maximum,factor,policy,session,warnings);
                    }
                    if(!hash(input).equals(inputHash))throw new IOException("Input changed during analysis; preserve the changed source and choose a stable input snapshot");
                    if(options.containsKey("options-file")) {
                        Path recipe=Path.of(options.get("options-file"));
                        if(!hash(recipe).equals(options.get("recipe-sha256")))throw new IOException("Options file changed during analysis");
                        session.put("options_file_sha256",options.get("recipe-sha256"));
                    }
                    session.put("input_rows",rows);
                    warnings.add("COORDINATE AND CORRESPONDENCE REVIEW: output distances/coordinates are Angstrom and angles are degrees. Input units are a caller declaration. Supplied pair order defines the fit; matching, alignment/refinement, alternate conformers and periodic images are not inferred. A low RMSD alone does not establish structural or functional equivalence.");
                    if(units.equals("nanometer"))warnings.add("UNIT CONVERSION: every supplied coordinate was explicitly multiplied by10 to convert nanometers to Angstrom. Numeric coordinate bounds apply after conversion.");
                    manifest.put("analysis_status","completed-with-warnings");
                }catch(Exception failure) {
                    code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));
                    RnaToolRuntime.quarantine(dir);warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage());
                }
                session.put("warnings",warnings);
                Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session),StandardCharsets.UTF_8);
                Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);
                Files.writeString(dir.resolve("WARNINGS.md"),"# MOLECULAR GEOMETRY "+(code[0]==0?"WARNINGS REQUIRE REVIEW":"FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":"ANALYSIS FAILED: ")+Path.of(required(options,"out"),"WARNINGS.md"));
            if(code[0]==0)out.println("Native molecular geometry completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\n"+RECOVERY);return 2;}
    }

    private static long measure(BufferedReader input,Path dir,int maximum,double factor)throws IOException {
        if(!MEASURE_HEADER.equals(input.readLine()))throw new IOException("Require exact tab-separated measurement header: "+MEASURE_HEADER);
        var ids=new HashSet<String>();long rows=0;
        try(var result=Files.newBufferedWriter(dir.resolve("results.tsv"),StandardCharsets.UTF_8)) {
            result.write("measurement_id\tkind\tvalue\tunit\n");String line;
            while((line=input.readLine())!=null) {
                if(++rows>maximum)throw new IOException("Measurement input exceeds --max-rows");
                String[] f=fields(line,14,rows);identifier(f[0],ids,rows);
                int points=switch(f[1]){case "distance"->2;case "angle"->3;case "dihedral"->4;default->throw new IOException("Unknown measurement kind at input row "+(rows+1));};
                for(int i=2+points*3;i<f.length;i++)if(!f[i].isEmpty())throw new IOException("Unused coordinate fields must be empty at input row "+(rows+1));
                Point a=point(f,2,factor),b=point(f,5,factor);
                double value=switch(f[1]){case "distance"->MolecularGeometry.distance(a,b);case "angle"->MolecularGeometry.angle(a,b,point(f,8,factor));default->MolecularGeometry.dihedral(a,b,point(f,8,factor),point(f,11,factor));};
                result.write(f[0]+"\t"+f[1]+"\t"+value+"\t"+(points==2?"angstrom":"degree")+"\n");
            }
        }
        if(rows==0)throw new IOException("Measurement input has no data rows");return rows;
    }

    private static long fit(BufferedReader input,Path dir,int maximum,double factor,String policy,
            Map<String,Object> session,List<String> warnings)throws IOException {
        if(!FIT_HEADER.equals(input.readLine()))throw new IOException("Require exact tab-separated fit header: "+FIT_HEADER);
        var ids=new HashSet<String>();var ordered=new ArrayList<String>();var mobile=new ArrayList<Point>();var target=new ArrayList<Point>();String line;long rows=0;
        while((line=input.readLine())!=null) {
            if(++rows>maximum)throw new IOException("Fit input exceeds --max-rows");
            String[] f=fields(line,7,rows);identifier(f[0],ids,rows);ordered.add(f[0]);mobile.add(point(f,1,factor));target.add(point(f,4,factor));
        }
        var result=MolecularGeometry.fit(mobile,target,policy.equals("error")?MolecularGeometry.Degeneracy.ERROR:MolecularGeometry.Degeneracy.ALLOW_NONUNIQUE,new CpuComputeBackend());
        session.put("transform_convention","target_column = rotation * mobile_column + translation;Angstrom;all supplied pairs;no scale or reflection");
        session.put("rotation",result.rotation());session.put("translation",result.translation());session.put("relative_horn_eigenvalue_gap",result.relativeEigenvalueGap());
        if(!result.rotationUnique())warnings.add("NONUNIQUE FIT EXPLICITLY ALLOWED: the best rotation is not uniquely determined or its eigenvalue gap is at most1e-12. The reported pose is one solution. Required action: do not infer an identified orientation; provide informative noncollinear pairs or retain this nonuniqueness in all downstream interpretation.");
        table(dir.resolve("results.tsv"),List.of("pairs","initial_rmsd_angstrom","fitted_rmsd_angstrom","maximum_residual_angstrom","rotation_unique"),List.<String[]>of(new String[]{Long.toString(rows),Double.toString(result.initialRmsd()),Double.toString(result.rmsd()),Double.toString(result.maximumResidual()),Boolean.toString(result.rotationUnique())}));
        double[] residuals=result.residuals();
        try(BufferedWriter writer=Files.newBufferedWriter(dir.resolve("fit-pairs.tsv"),StandardCharsets.UTF_8)) {
            writer.write("pair_id\tmobile_x_angstrom\tmobile_y_angstrom\tmobile_z_angstrom\ttarget_x_angstrom\ttarget_y_angstrom\ttarget_z_angstrom\tfitted_x_angstrom\tfitted_y_angstrom\tfitted_z_angstrom\tresidual_angstrom\n");
            for(int i=0;i<mobile.size();i++) {
                var m=mobile.get(i);var t=target.get(i);var p=result.apply(m);
                writer.write(ordered.get(i)+"\t"+m.x()+"\t"+m.y()+"\t"+m.z()+"\t"+t.x()+"\t"+t.y()+"\t"+t.z()+"\t"+p.x()+"\t"+p.y()+"\t"+p.z()+"\t"+residuals[i]+"\n");
            }
        }
        return rows;
    }
    private static String[] fields(String line,int count,long row)throws IOException {
        if(line.length()>4096)throw new IOException("Input row exceeds4096 characters at row "+(row+1));
        String[] f=line.split("\t",-1);if(f.length!=count)throw new IOException("Wrong tab-separated field count at input row "+(row+1));return f;
    }
    private static void identifier(String value,Set<String> ids,long row)throws IOException {
        if(value.isBlank()||value.length()>1024||value.chars().anyMatch(Character::isISOControl)||!ids.add(value))throw new IOException("Require unique nonblank IDs without control characters at input row "+(row+1));
    }
    private static Point point(String[] f,int offset,double factor) {
        return new Point(Double.parseDouble(f[offset])*factor,Double.parseDouble(f[offset+1])*factor,Double.parseDouble(f[offset+2])*factor);
    }
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;
        Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();
        if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require a UTF-8 JSON options object of at most1MiB");
        byte[] raw=Files.readAllBytes(path);Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());
        if(!(value instanceof Map<?,?> fields))throw new IllegalArgumentException("Options file must contain an object of known option:string values");
        var effective=new LinkedHashMap<String,String>();
        for(var e:fields.entrySet()) {
            String key=String.valueOf(e.getKey());if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String text))throw new IllegalArgumentException("Unknown, recursive or non-string options-file entry: "+key);
            effective.put(key,text);
        }
        effective.putAll(direct);effective.put("recipe-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));return effective;
    }
    static String help(){return """
        molecular-geometry --operation measure|fit --input TABLE.tsv
          --units angstrom|nanometer --out NEW_DIRECTORY
          [--degeneracy error|allow-nonunique] [--max-rows 1000000]
          [--max-bytes 268435456] [--options-file UTF8_OPTIONS.json]
        Native Java; no PyMOL installation. Output distances/coordinates are Angstrom;
        angles are degrees. Units are required and never inferred. Nanometers multiply by10.
        Fit requires explicit --degeneracy and exact TSV header:
          pair_id mobile_x mobile_y mobile_z target_x target_y target_z
        Use literal tabs. Each row is one matched pair; IDs are unique and order is retained.
        Unweighted proper rigid fit,3..1000000 pairs; no scale,reflection,outlier rejection,
        sequence/atom matching or PyMOL align/super. Coordinates are bounded to abs<=1e9.
        Measure TSV header (literal tabs):
          measurement_id kind ax ay az bx by bz cx cy cz dx dy dz
        kind is distance,angle or dihedral; unused trailing coordinate fields must be empty.
        Zero-length arms/collinear torsions fail. No periodic images,energy or uncertainty.
        Review WARNINGS.md,session.yaml and analysis-status.json. Nonunique fits require
        explicit permission and warn; a low RMSD alone does not establish biological equivalence.
        Exit0 success with review warnings;exit2 input/process failure and quarantined partials.
        Existing outputs are preserved. Use an ASCII-named UTF8JSON recipe for Windows
        Unicode paths; direct option values override the recipe. Rerun to a new directory.
        """;}
}

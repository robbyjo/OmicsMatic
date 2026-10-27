/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.protein.MolecularGeometry.Point;
import org.jlinalg.protein.OpenDxGrid;
import org.jlinalg.protein.PqrStructure;

/** Full native PQR/float32-map/frame/ramp audits for original surface rendering. */
final class PymolPotentialAudit {
    private PymolPotentialAudit() { }
    record Field(String float32Sha256,double maximumAbsoluteRoundingError,int underflowedNonzeroValues) { }
    static Field field(OpenDxGrid grid)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);double error=0;int underflow=0;
        for(int x=0;x<grid.nx();x++)for(int y=0;y<grid.ny();y++)for(int z=0;z<grid.nz();z++){double source=grid.value(x,y,z);float value=(float)source;if(!Float.isFinite(value))throw new IOException("Potential cannot be represented as finite float32 by original PyMOL");if(source!=0&&value==0)underflow++;error=Math.max(error,Math.abs(source-value));bytes.putFloat(0,value);digest.update(bytes.array());}
        return new Field(HexFormat.of().formatHex(digest.digest()),error,underflow);
    }
    static void geometry(OpenDxGrid grid)throws IOException {
        Point[] deltas=grid.deltas();for(int axis=0;axis<3;axis++){double[] delta=xyz(deltas[axis]);for(int j=0;j<3;j++)if(j==axis?delta[j]<=0:delta[j]!=0)throw new IOException("This original surface profile requires positive axis-aligned DX deltas;use native electrostatic-grid for general affine sampling");}
    }
    static void stageFloat32Grid(OpenDxGrid grid,Path target)throws IOException {
        geometry(grid);Point[] deltas=grid.deltas();for(int axis=0;axis<3;axis++)if(!((float)xyz(deltas[axis])[axis]>0))throw new IOException("Original PyMOL float32 grid spacing collapses;review scale/frame before rendering");
        // Original3.1.0 uses ParseWordCopy(...,20) for ASCII DX values/origins.
        // Long scientific tokens can lose their exponent without an upstream
        // error. Shortest round-trip float32 tokens preserve its actual storage
        // precision and fit the parser;the raw source DX remains separate.
        try(var out=Files.newBufferedWriter(target,StandardCharsets.US_ASCII)) {
            out.write("# JLinAlg canonical float32 rendering grid;raw source retained separately\nobject 1 class gridpositions counts "+grid.nx()+" "+grid.ny()+" "+grid.nz()+"\norigin "+shortVector(grid.origin())+"\n");for(Point delta:deltas)out.write("delta "+shortVector(delta)+"\n");out.write("object 2 class gridconnections counts "+grid.nx()+" "+grid.ny()+" "+grid.nz()+"\nobject 3 class array type float rank 0 items "+grid.gridPoints()+" data follows\n");int count=0;
            for(int x=0;x<grid.nx();x++)for(int y=0;y<grid.ny();y++)for(int z=0;z<grid.nz();z++){out.write(shortFloat(grid.value(x,y,z)));out.write(++count%3==0?'\n':' ');}if(count%3!=0)out.write('\n');out.write("attribute \"dep\" string \"positions\"\nobject \"regular positions regular connections\" class field\ncomponent \"positions\" value 1\ncomponent \"connections\" value 2\ncomponent \"data\" value 3\n");
        }
    }
    private static String shortVector(Point point)throws IOException {return shortFloat(point.x())+" "+shortFloat(point.y())+" "+shortFloat(point.z());}
    private static String shortFloat(double value)throws IOException {float number=(float)value;if(!Float.isFinite(number))throw new IOException("Value exceeds original PyMOL finite float32 precision");String text=Float.toString(number);if(text.length()>19)throw new IOException("Float token exceeds admitted original parser bound");return text;}
    static double[] xyz(Point point){return new double[]{point.x(),point.y(),point.z()};}
    static List<Double> values(Point point){return List.of(point.x(),point.y(),point.z());}
    static List<Map<String,Object>> sourceAtoms(PqrStructure pqr) {
        var atoms=new ArrayList<Map<String,Object>>();for(var atom:pqr.atoms()){var item=new LinkedHashMap<String,Object>();item.put("serial",atom.number());item.put("chain",atom.chain());item.put("residue_id",Integer.toString(atom.residueNumber())+atom.insertionCode());item.put("residue",atom.residue());item.put("name",atom.name());item.put("radius",atom.radius());item.put("charge",atom.charge());item.put("coordinate",values(atom.coordinate()));atoms.add(item);}return atoms;
    }
    static Map<String,Object> audit(Path directory,PqrStructure pqr,OpenDxGrid grid,Map<String,Object> recipe,Field expected)throws Exception {
        Map<?,?> result=map(SimpleJson.parse(Files.readString(directory.resolve("original-surface.json"),StandardCharsets.UTF_8)));require("original-execution-completed-requires-native-audit".equals(result.get("status")),"Original surface result incomplete");
        List<?> loaded=list(result.get("loaded_atoms")),surface=list(result.get("surface_atoms")),finalAtoms=list(result.get("final_atoms"));require(loaded.size()==pqr.atoms().size()&&surface.size()==loaded.size()&&finalAtoms.size()==loaded.size(),"PQR atom count changed during rendering");int zero=0;double maxRadiusDifference=0;
        String radiusPolicy=(String)recipe.get("surface_radii");
        for(int i=0;i<loaded.size();i++) {
            var source=pqr.atoms().get(i);Map<?,?> before=map(loaded.get(i)),after=map(surface.get(i)),last=map(finalAtoms.get(i));
            for(var atom:List.of(before,after,last)) {
                require(integer(atom.get("id"))==source.number()&&source.name().equals(atom.get("name"))&&source.residue().equals(atom.get("resn"))&&source.chain().equals(atom.get("chain"))&&(Integer.toString(source.residueNumber())+source.insertionCode()).equals(atom.get("resi")),"Loaded/surface/final PQR atom identity/order differs");
                List<?> coordinate=list(atom.get("coord"));require(coordinate.size()==3,"Invalid atom coordinate count");double[] xyz=xyz(source.coordinate());for(int axis=0;axis<3;axis++)sameFloat(xyz[axis],number(coordinate.get(axis)),"PQR coordinate changed");sameFloat(source.charge(),number(atom.get("partial_charge")),"PQR charge changed");
            }
            require(before.get("index").equals(after.get("index"))&&after.get("index").equals(last.get("index"))&&before.get("element").equals(after.get("element"))&&after.get("element").equals(last.get("element")),"Rendering changed atom index/element");
            double radius=number(after.get("radius"));require(radius>=0&&radius<=20,"Unsupported display radius");if(radius==0)zero++;maxRadiusDifference=Math.max(maxRadiusDifference,Math.abs(radius-source.radius()));
            sameFloat(radiusPolicy.equals("source-pqr")?source.radius():number(before.get("radius")),radius,"Declared surface-radius policy was not applied");sameFloat(radius,number(last.get("radius")),"Display radius changed during rendering");
            List<?> coordinate=list(after.get("coord"));double margin=radius+number(recipe.get("probe_radius"))*(1+integer(recipe.get("ramp_above_mode")));double[] lower=xyz(grid.origin()),upper=xyz(grid.coordinate(grid.nx()-1,grid.ny()-1,grid.nz()-1));for(int axis=0;axis<3;axis++)require(number(coordinate.get(axis))-margin>=lower[axis]-1e-4&&number(coordinate.get(axis))+margin<=upper[axis]+1e-4,"Map does not cover actual display radius/probe/ramp offset;no outside-grid color is admitted");
        }
        Map<?,?> originalMap=map(result.get("map"));List<?> shape=list(originalMap.get("shape"));require(shape.size()==3&&integer(shape.get(0))==grid.nx()&&integer(shape.get(1))==grid.ny()&&integer(shape.get(2))==grid.nz()&&"float32".equals(originalMap.get("dtype")),"Original map shape/precision differs");Path binary=directory.resolve("map-float32-le.bin");require(Files.size(binary)==4L*grid.gridPoints()&&FollowupSupport.hash(binary).equals(expected.float32Sha256)&&expected.float32Sha256.equals(originalMap.get("float32_le_sha256")),"Original map values/order do not match every native float32-rounded DX node");
        List<?> extent=list(originalMap.get("extent"));require(extent.size()==2,"Original map extent missing");for(int end=0;end<2;end++){List<?> actual=list(extent.get(end));require(actual.size()==3,"Invalid original map extent");double[] truth=xyz(end==0?grid.origin():grid.coordinate(grid.nx()-1,grid.ny()-1,grid.nz()-1));for(int axis=0;axis<3;axis++)sameFloat(truth[axis],number(actual.get(axis)),"Original map coordinate extent differs");}
        Object matrix=originalMap.get("object_matrix");if(matrix!=null){List<?> entries=list(matrix);require(entries.size()==16,"Invalid original map object matrix");for(int i=0;i<16;i++)require(Math.abs(number(entries.get(i))-(i%5==0?1:0))<=1e-6,"Original map has an unexpected coordinate transform");}
        Map<?,?> ramp=map(result.get("ramp"));require(integer(ramp.get("type_code"))==2&&"jl_map".equals(ramp.get("map"))&&integer(ramp.get("state"))==1,"Original ramp is not attached to the declared map/state");List<?> levels=list(ramp.get("levels")),colors=list(ramp.get("colors")),registered=list(ramp.get("registered_colors"));require(levels.size()==3&&colors.size()==3&&registered.size()==3,"Original ramp dimensions differ");List<?> desiredLevels=list(recipe.get("levels")),desiredColors=list(recipe.get("colors"));for(int i=0;i<3;i++){sameFloat(number(desiredLevels.get(i)),number(levels.get(i)),"Original ramp level differs");List<?> a=list(colors.get(i)),b=list(registered.get(i)),wanted=list(desiredColors.get(i));require(a.size()==3&&b.size()==3&&wanted.size()==3,"Original RGB dimensions differ");for(int channel=0;channel<3;channel++){sameFloat(number(wanted.get(channel)),number(a.get(channel)),"Original ramp color differs");sameFloat(number(wanted.get(channel)),number(b.get(channel)),"Original registered color differs");}}
        Map<?,?> settings=map(result.get("settings"));require(integer(settings.get("surface_mode"))==0&&integer(settings.get("surface_solvent"))==("accessible".equals(recipe.get("surface_solvent"))?1:0)&&integer(settings.get("surface_ramp_above_mode"))==integer(recipe.get("ramp_above_mode"))&&integer(settings.get("surface_quality"))==integer(recipe.get("surface_quality"))&&"jl_ramp".equals(settings.get("surface_color"))&&integer(settings.get("orthoscopic"))==1&&integer(settings.get("ray_opaque_background"))==1,"Original surface/camera settings differ");sameFloat(number(recipe.get("probe_radius")),number(settings.get("solvent_radius")),"Original surface probe differs");
        require(radiusPolicy.equals(result.get("surface_radii"))&&recipe.get("surface_solvent").equals(result.get("surface_solvent")),"Original surface policy ledger differs");List<?> view=list(result.get("view"));require(view.size()==18,"Original camera view missing");var actualView=new ArrayList<Double>();for(Object value:view)actualView.add(number(value));try{PymolPotentialCli.camera(actualView);}catch(IllegalArgumentException failure){throw new IOException("Original camera is not a proper finite view",failure);}if(recipe.get("view") instanceof List<?> desiredView){require(desiredView.size()==18,"Invalid requested view");for(int i=0;i<18;i++)sameFloat(number(desiredView.get(i)),number(view.get(i)),"Original requested camera view differs");}
        var audit=new LinkedHashMap<String,Object>();audit.put("status","passed-native-PQR-full-field-frame-ramp-and-settings-audit");audit.put("atoms_checked",pqr.atoms().size());audit.put("grid_nodes_checked",grid.gridPoints());audit.put("float32_field_sha256",expected.float32Sha256);audit.put("maximum_absolute_float32_rounding_error",expected.maximumAbsoluteRoundingError);audit.put("underflowed_nonzero_source_values",expected.underflowedNonzeroValues);audit.put("surface_zero_radius_atoms",zero);audit.put("maximum_surface_radius_difference_from_PQR_angstrom",maxRadiusDifference);audit.put("surface_radii",radiusPolicy);audit.put("actual_settings",settings);audit.put("actual_ramp",ramp);audit.put("camera_view",view);audit.put("scope","Source atom identities,coordinates,charges,radius-policy application,every float32 map node/order,axis-aligned world frame,ramp levels/colors and original settings verified. PQR lacks a bond graph/element declarations;PyMOL display inference is retained,not independently certified. No PB solve,protonation or energy inference occurs here.");return audit;
    }
    static void sameFloat(double expected,double actual,String message)throws IOException {float value=(float)expected;require(Float.isFinite(value)&&Math.abs(actual-value)<=4*Math.ulp(value==0?Float.MIN_NORMAL:value),message);}
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
    static double number(Object value)throws IOException {if(!(value instanceof Number number)||!Double.isFinite(number.doubleValue()))throw new IOException("Nonfinite/missing numeric surface result");return number.doubleValue();}
    static int integer(Object value)throws IOException {double number=number(value);if(number!=Math.rint(number)||number<Integer.MIN_VALUE||number>Integer.MAX_VALUE)throw new IOException("Invalid integer surface result");return (int)number;}
    static Map<?,?> map(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Expected surface result object");return map;}
    static List<?> list(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Expected surface result array");return list;}
}

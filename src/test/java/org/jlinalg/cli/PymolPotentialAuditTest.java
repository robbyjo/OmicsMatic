/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipInputStream;
import org.jlinalg.protein.MolecularGeometry.Point;
import org.jlinalg.protein.OpenDxGrid;
import org.jlinalg.protein.PqrStructure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PymolPotentialAuditTest {
    @TempDir Path root;
    record Fixture(Path directory,PqrStructure pqr,OpenDxGrid grid,Map<String,Object> recipe,Map<String,Object> original,PymolPotentialAudit.Field field) {
        void save()throws Exception {Files.writeString(directory.resolve("original-surface.json"),RegenieRuntime.json(original));}
        Map<String,Object> audit()throws Exception {save();return PymolPotentialAudit.audit(directory,pqr,grid,recipe,field);}
    }
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") static List<Object> list(Object value){return (List<Object>)value;}
    static Fixture fixture(Path directory)throws Exception {
        var properties=new Properties();byte[] archive;try(var in=PymolPotentialAuditTest.class.getResourceAsStream("/org/jlinalg/protein/pymol-potential/reference.properties")){assertNotNull(in);properties.load(in);}try(var in=PymolPotentialAuditTest.class.getResourceAsStream("/org/jlinalg/protein/pymol-potential/actual-surface-ledger.zip")){assertNotNull(in);archive=in.readAllBytes();}assertEquals(properties.getProperty("archive.sha256"),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)));
        Files.createDirectories(directory);try(var zip=new ZipInputStream(new ByteArrayInputStream(archive))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){assertTrue(List.of("source.pqr","potential.dx","original-surface.json","worker-recipe.json","map-float32-le.bin","surface.png").contains(entry.getName()));Files.copy(zip,directory.resolve(entry.getName()));}}
        PqrStructure pqr;OpenDxGrid grid;try(var r=Files.newBufferedReader(directory.resolve("source.pqr"))){pqr=PqrStructure.read(r,1000);}try(var r=Files.newBufferedReader(directory.resolve("potential.dx"))){grid=OpenDxGrid.read(r,300000);}return new Fixture(directory,pqr,grid,object(SimpleJson.parse(Files.readString(directory.resolve("worker-recipe.json")))),object(SimpleJson.parse(Files.readString(directory.resolve("original-surface.json")))),PymolPotentialAudit.field(grid));
    }
    private static Map<String,Object> atom(Fixture f,String phase){return object(list(f.original.get(phase)).get(0));}
    @Test void actualOriginal642AtomsAnd274625NodesPassFullNativeAudit()throws Exception {
        var f=fixture(root.resolve("actual"));var result=f.audit();assertEquals(642,result.get("atoms_checked"));assertEquals(274625,result.get("grid_nodes_checked"));assertEquals(236,result.get("surface_zero_radius_atoms"));assertEquals("b7f3aab36db8682c6aa0da18cf45de743980a9e4cd2e4449e0e94b3bcaa41138",result.get("float32_field_sha256"));assertEquals(0,result.get("underflowed_nonzero_source_values"));
    }
    @Test void loadedSurfaceAndFinalIdentitiesCannotChangeOrReorder()throws Exception {
        int i=0;for(String phase:List.of("loaded_atoms","surface_atoms","final_atoms")){var f=fixture(root.resolve("identity"+i++));atom(f,phase).put("chain","wrong");assertThrows(java.io.IOException.class,f::audit);}
        var f=fixture(root.resolve("order"));var rows=list(f.original.get("loaded_atoms"));java.util.Collections.swap(rows,0,1);assertThrows(java.io.IOException.class,f::audit);
    }
    @Test void everyPhaseRetainsFloat32CoordinatesAndPartialCharges()throws Exception {
        int i=0;for(String phase:List.of("loaded_atoms","surface_atoms","final_atoms"))for(String property:List.of("coord","partial_charge")){var f=fixture(root.resolve("numeric"+i++));var atom=atom(f,phase);if(property.equals("coord"))list(atom.get("coord")).set(0,999.0);else atom.put(property,99.0);assertThrows(java.io.IOException.class,f::audit);}
    }
    @Test void explicitPqrRadiusPolicyIncludingZerosCannotBeIgnored()throws Exception {
        var f=fixture(root.resolve("radius"));atom(f,"surface_atoms").put("radius",1.55);assertThrows(java.io.IOException.class,f::audit);
        var defaults=fixture(root.resolve("defaults"));defaults.recipe.put("surface_radii","pymol");defaults.original.put("surface_radii","pymol");assertThrows(java.io.IOException.class,defaults::audit,"PQR override must not be relabeled as unchanged PyMOL display radii");
    }
    @Test void finalIndexAndElementMustMatchLoadedAtoms()throws Exception {
        for(String key:List.of("index","element")){var f=fixture(root.resolve(key));atom(f,"final_atoms").put(key,key.equals("index")?999:"X");assertThrows(java.io.IOException.class,f::audit);}
    }
    @Test void everyMapByteAndLengthAreVerifiedIndependentlyOfReportedHash()throws Exception {
        var f=fixture(root.resolve("bytes"));Path path=f.directory.resolve("map-float32-le.bin");byte[] data=Files.readAllBytes(path);data[400]^=1;Files.write(path,data);assertThrows(java.io.IOException.class,f::audit);Files.write(path,new byte[4]);assertThrows(java.io.IOException.class,f::audit);
    }
    @Test void shapeWorldExtentAndUnexpectedMapTransformFail()throws Exception {
        var shape=fixture(root.resolve("shape"));object(shape.original.get("map")).put("shape",List.of(64,65,65));assertThrows(java.io.IOException.class,shape::audit);
        var extent=fixture(root.resolve("extent"));list(list(object(extent.original.get("map")).get("extent")).get(0)).set(0,-19.0);assertThrows(java.io.IOException.class,extent::audit);
        var matrix=fixture(root.resolve("matrix"));object(matrix.original.get("map")).put("object_matrix",List.of(1,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1));assertThrows(java.io.IOException.class,matrix::audit);
    }
    @Test void rampMustReferenceTheCorrectMapTypeAndState()throws Exception {
        for(String key:List.of("type_code","map","state")){var f=fixture(root.resolve(key));object(f.original.get("ramp")).put(key,key.equals("map")?"wrong":99);assertThrows(java.io.IOException.class,f::audit);}
    }
    @Test void actualRampValuesAndBothRegisteredAndSerializedColorsAreChecked()throws Exception {
        var level=fixture(root.resolve("level"));list(object(level.original.get("ramp")).get("levels")).set(0,-6.0);assertThrows(java.io.IOException.class,level::audit);
        for(String key:List.of("colors","registered_colors")){var f=fixture(root.resolve(key));list(list(object(f.original.get("ramp")).get(key)).get(0)).set(0,.2);assertThrows(java.io.IOException.class,f::audit);}
    }
    @Test void surfaceSettingsProbeAndProperCameraCannotDrift()throws Exception {
        for(String key:List.of("surface_mode","surface_solvent","surface_ramp_above_mode","surface_quality","orthoscopic","ray_opaque_background")){var f=fixture(root.resolve(key));object(f.original.get("settings")).put(key,99);assertThrows(java.io.IOException.class,f::audit);}
        var probe=fixture(root.resolve("probe"));object(probe.original.get("settings")).put("solvent_radius",2.0);assertThrows(java.io.IOException.class,probe::audit);var camera=fixture(root.resolve("camera"));list(camera.original.get("view")).set(0,0);assertThrows(java.io.IOException.class,camera::audit);
    }
    @Test void float32OverflowIsRejectedAndFiniteUnderflowIsExplicit()throws Exception {
        Point[] axes={new Point(1,0,0),new Point(0,1,0),new Point(0,0,1)};double[] values={1e40,0,0,0,0,0,0,0};var overflow=OpenDxGrid.of(2,2,2,new Point(0,0,0),axes,values);assertThrows(java.io.IOException.class,()->PymolPotentialAudit.field(overflow));values[0]=1e-50;var result=PymolPotentialAudit.field(OpenDxGrid.of(2,2,2,new Point(0,0,0),axes,values));assertEquals(1,result.underflowedNonzeroValues());assertEquals(1e-50,result.maximumAbsoluteRoundingError());
    }
    @Test void originalSurfaceProfileRejectsGeneralOrNegativeAxesWithoutChangingNativeGridApi()throws Exception {
        Point origin=new Point(0,0,0);for(Point[] axes:List.of(new Point[]{new Point(-1,0,0),new Point(0,1,0),new Point(0,0,1)},new Point[]{new Point(1,.1,0),new Point(0,1,0),new Point(0,0,1)})){var grid=OpenDxGrid.of(2,2,2,origin,axes,new double[8]);assertThrows(java.io.IOException.class,()->PymolPotentialAudit.geometry(grid));assertEquals(0,grid.sample(origin,OpenDxGrid.Outside.ERROR).value());}
    }
    @Test void longScientificTokensAreStagedAsShortRoundTripFloat32WithoutExponentLoss()throws Exception {
        Point[] axes={new Point(1,0,0),new Point(0,1,0),new Point(0,0,1)};double[] values={-4.1812206031971598e-6,1.2345678901234567e-20,1e30,-0.0,Double.MIN_VALUE,-Double.MIN_VALUE,Math.PI,-Math.E};var source=OpenDxGrid.of(2,2,2,new Point(-1,-1,-1),axes,values);Path staged=root.resolve("short.dx");PymolPotentialAudit.stageFloat32Grid(source,staged);OpenDxGrid parsed;try(var reader=Files.newBufferedReader(staged)){parsed=OpenDxGrid.read(reader,8);}assertEquals("float",parsed.scalarType());assertEquals(PymolPotentialAudit.field(source).float32Sha256(),PymolPotentialAudit.field(parsed).float32Sha256());assertEquals((double)(float)values[0],parsed.value(0,0,0));assertFalse(Files.readString(staged).contains("-4.1812206031971598e-06"));
    }
    @Test void stagingCannotSilentlyCollapseAnOriginalGridDeltaAtFloat32Precision() {
        var grid=OpenDxGrid.of(2,2,2,new Point(0,0,0),new Point[]{new Point(1e-50,0,0),new Point(0,1,0),new Point(0,0,1)},new double[8]);assertThrows(java.io.IOException.class,()->PymolPotentialAudit.stageFloat32Grid(grid,root.resolve("collapsed.dx")));
    }
}

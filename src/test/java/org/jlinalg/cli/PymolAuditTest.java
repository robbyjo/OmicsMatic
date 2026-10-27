/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PymolAuditTest {
    @TempDir Path root;
    private static final List<String> CASES=List.of("pdb-measure","cif-author","cif-label","pdb-model2","pdb-edits-render","align-rigid","align-gap-outlier","super-rigid","super-gap-outlier");
    @BeforeEach void load()throws Exception {extract(root);}
    static void extract(Path root)throws Exception {
        byte[] raw;
        try(var stream=PymolAuditTest.class.getResourceAsStream("/org/jlinalg/protein/pymol-tools/original-workflows.zip")){assertNotNull(stream);raw=stream.readAllBytes();}
        assertEquals("867e2d6873a4032c3b4145902518c830f6d0264ee43a29b3631ed5e449052185",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        int count=0;long total=0;
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(raw),StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                Path path=root.resolve(entry.getName()).normalize();assertTrue(path.startsWith(root));assertFalse(entry.isDirectory());assertTrue(++count<=200);
                byte[] content=zip.readNBytes(8*1048576+1);assertTrue(content.length<=8*1048576);assertTrue((total+=content.length)<=64L*1048576);
                Files.createDirectories(path.getParent());Files.write(path,content);
            }
        }
        assertEquals(136,count);
    }
    private static Map<String,Object> map(Object value)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:PymolAudit.object(value).entrySet())result.put(PymolAudit.string(e.getKey()),e.getValue());return result;}
    private Map<String,Object> read(Path path)throws IOException {return map(PymolAudit.read(path,64*1048576));}
    private void write(Path path,Object value)throws IOException {Files.writeString(path,RegenieRuntime.json(value)+"\n",StandardCharsets.UTF_8);}
    private Map<String,Object> audit(String name)throws Exception {
        Path folder=root.resolve(name);var recipe=read(folder.resolve("admitted-recipe.json"));var sources=new LinkedHashMap<String,PymolStructureInput.Parsed>();
        var liveStructures=new ArrayList<Object>();
        for(Object raw:PymolAudit.list(recipe.get("structures"))){var input=map(raw);int state=PymolAudit.integer(input.get("state"),1,1000000);input.put("state",state);liveStructures.add(input);sources.put(PymolAudit.string(input.get("id")),PymolStructureInput.parse(Files.readString(folder.resolve(PymolAudit.string(input.get("staged_path")))),PymolAudit.string(input.get("format")),state,PymolAudit.string(input.get("cif_namespace")),100000));}
        // The real CLI constructs Integer state fields while parsed original JSON uses
        // Long fields. Exercise that live boundary rather than only JSON-to-JSON replay.
        recipe.put("structures",liveStructures);
        return PymolAudit.audit(folder,recipe,sources);
    }
    private void editResult(String name,int stepIndex,Consumer<Map<String,Object>> edit)throws Exception {
        Path path=root.resolve(name).resolve("original-results.json");var result=read(path);var steps=new ArrayList<Object>(PymolAudit.list(result.get("steps")));var step=map(steps.get(stepIndex));var details=map(step.get("result"));edit.accept(details);step.put("result",details);steps.set(stepIndex,step);result.put("steps",steps);write(path,result);
    }
    private void editAfterAtom(String name,int stepIndex,String residue,String atomName)throws Exception {
        var result=read(root.resolve(name).resolve("original-results.json"));var step=PymolAudit.object(PymolAudit.list(result.get("steps")).get(stepIndex));String file=PymolAudit.string(PymolAudit.object(step.get("after")).get("mobile"));Path path=root.resolve(name).resolve(file);var ledger=read(path);var atoms=new ArrayList<Object>(PymolAudit.list(ledger.get("atoms")));boolean changed=false;
        for(int i=0;i<atoms.size();i++){var atom=map(atoms.get(i));if(residue.equals(atom.get("resi")) && atomName.equals(atom.get("name"))){var xyz=new ArrayList<Object>(PymolAudit.list(atom.get("coord")));xyz.set(0,PymolAudit.number(xyz.get(0))+1);atom.put("coord",xyz);atoms.set(i,atom);changed=true;break;}}
        assertTrue(changed);ledger.put("atoms",atoms);write(path,ledger);
    }
    @Test void allNineFrozenOriginalWorkflowsPassWithoutPythonOrPymol()throws Exception {for(String name:CASES)assertEquals("passed-independent-native-audit",audit(name).get("status"),name);}
    @Test void alignAndSuperRetainDifferentGappedCorrespondences()throws Exception {
        var a=PymolAudit.object(PymolAudit.list(audit("align-gap-outlier").get("steps")).get(0));var s=PymolAudit.object(PymolAudit.list(audit("super-gap-outlier").get("steps")).get(0));assertNotEquals(a.get("inlier_pairs_verified"),s.get("inlier_pairs_verified"));
    }
    @Test void forgedMeasurementValueFails()throws Exception {editResult("pdb-measure",0,d->d.put("value",99));assertTrue(assertThrows(IOException.class,()->audit("pdb-measure")).getMessage().contains("measurement differs"));}
    @Test void aValidButWrongAlignmentCorrespondenceFailsNumerically()throws Exception {
        editResult("align-rigid",0,d->{try{var pairs=new ArrayList<Object>(PymolAudit.list(d.get("inlier_pairs")));var first=new ArrayList<Object>(PymolAudit.list(pairs.get(0)));var second=new ArrayList<Object>(PymolAudit.list(pairs.get(1)));Object target=first.get(1);first.set(1,second.get(1));second.set(1,target);pairs.set(0,first);pairs.set(1,second);d.put("inlier_pairs",pairs);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
        assertTrue(assertThrows(IOException.class,()->audit("align-rigid")).getMessage().contains("inlier RMSD differs"));
    }
    @Test void movementOfAnUnfittedMobileAtomFailsRigidMotionAudit()throws Exception {editAfterAtom("align-rigid",0,"1","N");assertTrue(assertThrows(IOException.class,()->audit("align-rigid")).getMessage().contains("proper rigid motion"));}
    @Test void mutationMovingAnUnselectedResidueFails()throws Exception {editAfterAtom("pdb-edits-render",6,"2","N");assertTrue(assertThrows(IOException.class,()->audit("pdb-edits-render")).getMessage().contains("unselected atom"));}
    @Test void requestedRotamerIdentityIsChecked()throws Exception {editResult("pdb-edits-render",6,d->d.put("selected_rotamer",2L));assertTrue(assertThrows(IOException.class,()->audit("pdb-edits-render")).getMessage().contains("rotamer differs"));}
    @Test void mutationMustHaveTheCanonicalHeavyAtomSet()throws Exception {
        Path path=root.resolve("pdb-edits-render/s06-after-mobile.json");var ledger=read(path);var atoms=new ArrayList<Object>(PymolAudit.list(ledger.get("atoms")));int removed=0;
        for(int i=0;i<atoms.size();i++){var atom=PymolAudit.object(atoms.get(i));if("1".equals(atom.get("resi")) && "NE2".equals(atom.get("name"))){removed=PymolAudit.integer(atom.get("index"),1,Integer.MAX_VALUE);atoms.remove(i);break;}}
        assertTrue(removed>0);var bonds=new ArrayList<Object>();for(Object value:PymolAudit.list(ledger.get("bonds"))){var edge=PymolAudit.list(value);if(PymolAudit.integer(edge.get(0),1,Integer.MAX_VALUE)!=removed && PymolAudit.integer(edge.get(1),1,Integer.MAX_VALUE)!=removed)bonds.add(value);}ledger.put("atoms",atoms);ledger.put("bonds",bonds);write(path,ledger);
        assertTrue(assertThrows(IOException.class,()->audit("pdb-edits-render")).getMessage().contains("canonical heavy-atom set"));
    }
    @Test void mmcifExportCorruptionFailsRatherThanBeingAcceptedAsRounding()throws Exception {Path path=root.resolve("pdb-measure/final-mobile.cif");Files.writeString(path,Files.readString(path).replace("17.047", "18.047"));assertTrue(assertThrows(IOException.class,()->audit("pdb-measure")).getMessage().contains("rounding"));}
    @Test void anExportCannotReferOutsideItsBundle()throws Exception {Path path=root.resolve("pdb-measure/original-results.json");var result=read(path);result.put("exports",Map.of("mobile","../escape.cif"));write(path,result);assertTrue(assertThrows(IOException.class,()->audit("pdb-measure")).getMessage().contains("output member path"));}
    @Test void originalLoadedStateMustMatchTheRequestedOrdinal()throws Exception {Path path=root.resolve("pdb-measure/original-results.json");var result=read(path);var loaded=new ArrayList<Object>(PymolAudit.list(result.get("loaded")));var item=map(loaded.get(0));item.put("selected_state_ordinal",2);loaded.set(0,item);result.put("loaded",loaded);write(path,result);assertTrue(assertThrows(IOException.class,()->audit("pdb-measure")).getMessage().contains("Loaded state/namespace/source digest differs"));}
    @Test void elementSelectorsIgnoreCaseAcrossOriginalInputFormats()throws Exception {
        var atom=new PymolStructureInput.Atom(1,"CL","LIG","1","A","","","CL",1,new org.jlinalg.protein.MolecularGeometry.Point(3,4,0),0,1);
        var snapshots=Map.of("ligand",new PymolAudit.Snapshot(Map.of(1,atom),java.util.Set.of()));
        assertEquals(1,PymolAudit.select(Map.of("structure","ligand","element","Cl"),snapshots).size());
        assertEquals(1,PymolAudit.select(Map.of("structure","ligand","element",List.of("Br","Cl")),snapshots).size());
        assertEquals(1,PymolAudit.select(Map.of("structure","ligand","element","CL"),snapshots).size());
        assertEquals(1,PymolAudit.select(Map.of("structure","ligand","element","cl"),snapshots).size());
        assertTrue(PymolAudit.select(Map.of("structure","ligand","name","cl"),snapshots).isEmpty());
    }
    @Test void duplicateAtomIdentityAndDanglingBondsFail()throws Exception {
        var ledger=read(root.resolve("pdb-measure/loaded-mobile.json"));var atoms=new ArrayList<Object>(PymolAudit.list(ledger.get("atoms")));var duplicate=map(atoms.get(0));duplicate.put("index",9999);atoms.add(duplicate);ledger.put("atoms",atoms);assertThrows(IOException.class,()->PymolAudit.snapshot(ledger));
        var dangling=read(root.resolve("pdb-measure/loaded-mobile.json"));var bonds=new ArrayList<Object>(PymolAudit.list(dangling.get("bonds")));bonds.add(List.of(1,9999,1));dangling.put("bonds",bonds);assertThrows(IOException.class,()->PymolAudit.snapshot(dangling));
    }
}

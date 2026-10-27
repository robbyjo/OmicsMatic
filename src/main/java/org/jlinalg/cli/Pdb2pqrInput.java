/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.protein.MolecularGeometry;
import org.jlinalg.protein.PqrStructure;

/** Native single-model/alternate selection and independent atom lineage for original preparation. */
final class Pdb2pqrInput {
    private Pdb2pqrInput() { }
    record Selected(String pdb,List<PymolStructureInput.Atom> atoms,List<String> sourceModels,int sourceAtoms,int alternateAtomsExcluded,int alternateLabelsCleared) {
        Selected { atoms=List.copyOf(atoms);sourceModels=List.copyOf(sourceModels); }
    }
    private static boolean water(String name){return Set.of("HOH","WAT","H2O").contains(name);}
    private static boolean hydrogen(PymolStructureInput.Atom atom){return Set.of("H","D").contains(atom.element());}
    private static String residueFamily(String name) {
        return switch(name){case "HIS","HID","HIE","HIP","HSD","HSE","HSP"->"HIS";case "ASP","ASH"->"ASP";case "GLU","GLH"->"GLU";case "LYS","LYN"->"LYS";case "ARG","ARN"->"ARG";case "CYS","CYX","CYM"->"CYS";case "HOH","WAT","H2O"->"WATER";default->name;};
    }
    private static String key(PymolStructureInput.Atom atom){return String.join("\u001f",atom.chain(),atom.resi(),atom.name());}
    private static String key(PqrStructure.Atom atom){return String.join("\u001f",atom.chain(),Integer.toString(atom.residueNumber())+atom.insertionCode(),atom.name());}
    static Selected select(String source,int state,String alternate,String label,int maximum)throws IOException {
        var parsed=PymolStructureInput.parse(source,"pdb",state,"author",maximum);
        if(!Set.of("error","label").contains(alternate)||alternate.equals("label")&&(label==null||!label.matches("[A-Za-z0-9]"))||alternate.equals("error")&&label!=null)throw new IOException("Require alternate error,or alternate label with one ASCII --alt-label");
        var selected=new ArrayList<PymolStructureInput.Atom>();var serials=new HashSet<Integer>();var identities=new HashSet<String>();int excluded=0,cleared=0;
        for(var atom:parsed.atoms()) {
            if(!atom.alt().isEmpty()) {
                if(alternate.equals("error"))throw new IOException("Alternate conformers require an explicit label choice;no occupancy heuristic is inferred");
                if(!atom.alt().equals(label)){excluded++;continue;}cleared++;
            }
            if(!serials.add(atom.id())||!identities.add(key(atom)))throw new IOException("Selected PDB has duplicate serials or chain/residue/atom identities;segment distinctions cannot silently collapse in PQR");
            if(!atom.resi().matches("-?[0-9]+[A-Za-z]?"))throw new IOException("PDB residue requires integer plus optional insertion code");selected.add(atom);
        }
        if(selected.isEmpty())throw new IOException("Chosen model/alternate has no atoms");
        if(alternate.equals("label")&&cleared==0)throw new IOException("Chosen alternate label is absent from the selected model;no conformer is inferred");
        boolean explicit=false,inside=false;int ordinal=0;var pdb=new StringBuilder();
        for(String line:source.split("\\R")) {
            String record=line.substring(0,Math.min(line.length(),6)).trim();
            if(record.equals("MODEL")){explicit=true;inside=true;ordinal++;continue;}
            if(record.equals("ENDMDL")){inside=false;continue;}
            if(record.equals("END"))continue;
            if(explicit&&inside&&ordinal!=state)continue;
            if(Set.of("ATOM","HETATM").contains(record)) {
                if(explicit&&(!inside||ordinal!=state))continue;int serial=Integer.parseInt(line.substring(6,11).trim());if(!serials.contains(serial))continue;
                String alt=line.substring(16,17).trim();if(!alt.isEmpty()&&!alt.equals(label))continue;
                if(!alt.isEmpty())line=line.substring(0,16)+' '+line.substring(17);
            }else if(record.equals("TER")&&explicit&&(!inside||ordinal!=state))continue;
            pdb.append(line).append('\n');
        }
        pdb.append("END\n");var staged=PymolStructureInput.parse(pdb.toString(),"pdb",1,"author",maximum);if(staged.atoms().size()!=selected.size())throw new IOException("Selected PDB staging lost/duplicated atoms");
        return new Selected(pdb.toString(),selected,parsed.modelLabels(),parsed.sourceAtoms(),excluded,cleared);
    }
    record Audit(Map<String,Object> summary,List<Map<String,Object>> ledger,List<String> warnings) { }
    static Audit audit(Selected source,PqrStructure pqr,String preparedPdb,String mode,String waterPolicy,String repairPolicy,String missingPolicy,double maximumHeavyShift,int maximum)throws IOException {
        var prepared=PymolStructureInput.parse(preparedPdb,"pdb",1,"author",maximum);var nativeAtoms=new LinkedHashMap<String,PymolStructureInput.Atom>();var charged=new LinkedHashMap<String,PqrStructure.Atom>();
        for(var atom:prepared.atoms())if(nativeAtoms.put(key(atom),atom)!=null)throw new IOException("Prepared PDB identities collapse after removing segment/alternate annotations");
        for(var atom:pqr.atoms())if(charged.put(key(atom),atom)!=null)throw new IOException("Prepared PQR identities are ambiguous");
        var original=new LinkedHashMap<String,PymolStructureInput.Atom>();for(var atom:source.atoms())original.put(key(atom),atom);
        var ledger=new ArrayList<Map<String,Object>>();var warnings=new ArrayList<String>();int matched=0,addedHydrogens=0,addedHeavy=0,removedHydrogens=0,omittedHeavy=0,droppedWater=0,renamedResidues=0;double maxShift=0;
        for(var atom:pqr.atoms()) {
            String key=key(atom);var coordinate=nativeAtoms.get(key);if(coordinate==null)throw new IOException("PQR atom has no independent prepared-PDB identity:"+key);
            double disagreement=MolecularGeometry.distance(atom.coordinate(),coordinate.point());if(disagreement>.002)throw new IOException("PQR and prepared PDB coordinates disagree:"+key);
            var before=original.get(key);var row=new LinkedHashMap<String,Object>();row.put("chain",atom.chain());row.put("residue_id",Integer.toString(atom.residueNumber())+atom.insertionCode());row.put("output_residue",atom.residue());row.put("atom",atom.name());row.put("element",coordinate.element());row.put("charge_e",atom.charge());row.put("radius_angstrom",atom.radius());row.put("output_serial",atom.number());row.put("output_coordinate_angstrom",List.of(atom.coordinate().x(),atom.coordinate().y(),atom.coordinate().z()));
            if(before==null) {
                boolean h=hydrogen(coordinate);if(h)addedHydrogens++;else addedHeavy++;
                if(mode.equals("assign-only")||!h&&repairPolicy.equals("error"))throw new IOException("Unexpected/forbidden atom addition:"+key+";review repair/protonation mode");
                row.put("lineage",h?"output-only-hydrogen":"output-only-heavy-atom;repair-or-rename");
            }else {
                matched++;double shift=MolecularGeometry.distance(before.point(),atom.coordinate());row.put("lineage","retained-source-identity");row.put("input_serial",before.id());row.put("input_residue",before.resn());row.put("input_alternate",before.alt());row.put("input_segment",before.segi());row.put("coordinate_shift_angstrom",shift);
                if(!before.element().equals(coordinate.element()))throw new IOException("Retained atom element changed:"+key);
                if(!before.resn().equals(atom.residue())){if(!residueFamily(before.resn()).equals(residueFamily(atom.residue())))throw new IOException("Retained residue changed beyond supported protonation/naming family:"+key);renamedResidues++;}
                if(!hydrogen(before)){maxShift=Math.max(maxShift,shift);if(shift>maximumHeavyShift+.001)throw new IOException("Retained heavy atom exceeds declared maximum shift:"+key);}
            }
            ledger.add(row);
        }
        for(var before:source.atoms())if(!charged.containsKey(key(before))) {
            String why;if(water(before.resn())&&waterPolicy.equals("drop")){droppedWater++;why="explicit-water-drop";}
            else if(hydrogen(before)&&mode.equals("prepare")){removedHydrogens++;why="hydrogen-absent-after-original-preparation";}
            else{omittedHeavy++;why="not-parameterized-or-renamed-by-original-program";if(missingPolicy.equals("error")||mode.equals("assign-only"))throw new IOException("Source atom missing from charged output:"+key(before)+";review original missing-parameter/renaming diagnostics");}
            var row=new LinkedHashMap<String,Object>();row.put("lineage",why);row.put("chain",before.chain());row.put("residue_id",before.resi());row.put("input_residue",before.resn());row.put("atom",before.name());row.put("element",before.element());row.put("input_serial",before.id());ledger.add(row);
        }
        if(waterPolicy.equals("drop")&&pqr.atoms().stream().anyMatch(a->water(a.residue())))throw new IOException("Dropped water remains in charged output");
        var expectedIdentities=new HashSet<>(original.keySet());if(waterPolicy.equals("drop"))for(var atom:source.atoms())if(water(atom.resn()))expectedIdentities.remove(key(atom));
        if(mode.equals("assign-only")&&!charged.keySet().equals(expectedIdentities))throw new IOException("assign-only changed atom identities beyond an explicit water drop;no inferred repair is allowed");
        if(omittedHeavy>0)warnings.add("EXPLICIT INCOMPLETE MODEL: "+omittedHeavy+" source atoms have no charged output under the chosen omit policy. Required action: review the lineage ledger and original logs;this PQR represents a changed/incomplete molecular model,not the original full complex. Resolve naming/parameterization or document the intended omission before electrostatics.");
        if(addedHeavy>0)warnings.add("OUTPUT-ONLY HEAVY ATOMS: "+addedHeavy+" charged heavy atoms have no exact source identity under explicit allow policy. They may be repaired or renamed by original PDB2PQR. Required action: inspect the coordinates,topology,naming and original logs;do not claim a measured source atom or an unambiguous construction from this ledger alone.");
        if(renamedResidues>0)warnings.add("RESIDUE STATE/NAMING: "+renamedResidues+" retained atoms carry changed residue labels. Review source/output labels and selected protonation model;labels alone do not establish a measured state.");
        var result=new LinkedHashMap<String,Object>();result.put("source_selected_atoms",source.atoms().size());result.put("charged_atoms",pqr.atoms().size());result.put("prepared_pdb_atoms",prepared.atoms().size());result.put("retained_source_atoms",matched);result.put("output_only_hydrogens",addedHydrogens);result.put("output_only_heavy_atoms",addedHeavy);result.put("removed_source_hydrogens",removedHydrogens);result.put("explicitly_dropped_water_atoms",droppedWater);result.put("omitted_or_renamed_source_atoms",omittedHeavy);result.put("retained_atoms_with_changed_residue_name",renamedResidues);result.put("maximum_retained_heavy_shift_angstrom",maxShift);result.put("total_charge_e",pqr.totalCharge());result.put("zero_radius_atoms",pqr.zeroRadiusAtoms());result.put("total_charge_rounding_bound_e",pqr.atoms().size()*.00005);result.put("nearest_integer_charge_error_e",Math.abs(pqr.totalCharge()-Math.rint(pqr.totalCharge())));
        return new Audit(result,ledger,warnings);
    }
}

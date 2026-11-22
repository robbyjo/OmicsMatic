/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import jdistlib.accelerator.CpuComputeBackend;
import org.jlinalg.protein.MolecularGeometry;
import org.jlinalg.protein.MolecularGeometry.Point;
import org.jlinalg.cli.PymolStructureInput.Atom;

/** Independent native checks of original coordinates, correspondences and explicit edits. */
final class PymolAudit {
    private PymolAudit() { }
    record Snapshot(Map<Integer,Atom> atoms,Set<List<Integer>> bonds) { }
    private static final Set<String> ATOM_FIELDS=Set.of("index","id","name","resn","resi","chain","segi","alt","element","hetatm","b","q","coord");
    private static final Map<String,Set<String>> HEAVY=heavyAtoms();

    static Map<String,Object> audit(Path directory,Map<String,Object> recipe,Map<String,PymolStructureInput.Parsed> sources)throws Exception {
        Map<?,?> result=read(directory.resolve("original-results.json"),32*1048576);
        require("original-execution-completed-requires-java-audit".equals(result.get("status")),"Original workflow status is incomplete");
        require("angstrom".equals(result.get("coordinate_units")) && "degree".equals(result.get("angular_units")),"Unexpected output units");
        Map<String,Snapshot> previous=snapshots(directory,object(result.get("initial")),sources.keySet());
        var sourceRows=new ArrayList<Object>();var loaded=list(result.get("loaded"));
        require(loaded.size()==sources.size(),"Loaded input count differs");
        var seenLoaded=new HashSet<String>();
        for(Object value:loaded) {
            Map<?,?> row=object(value);String id=string(row.get("id"));require(seenLoaded.add(id) && sources.containsKey(id),"Unknown/duplicate loaded structure");
            var source=sources.get(id);Snapshot actual=previous.get(id);
            Map<?,?> declared=null;for(Object input:list(recipe.get("structures"))){Map<?,?> item=object(input);if(id.equals(item.get("id")))declared=item;}
            require(declared!=null && integer(row.get("selected_state_ordinal"),1,1000000)==integer(declared.get("state"),1,1000000) && row.get("cif_namespace").equals(declared.get("cif_namespace")) && row.get("sha256").equals(declared.get("sha256")),"Loaded state/namespace/source digest differs from admitted recipe");
            require(integer(row.get("source_states"),1,Integer.MAX_VALUE)==source.modelLabels().size(),"Original/native source state count differs");
            matchSource(source.atoms(),actual);
            var byIdentity=keyed(actual);for(Atom atom:source.atoms())require(atom.id()==byIdentity.get(atom.key()).id(),"Source atom serial/ID changed during loading");
            sourceRows.add(Map.of("id",id,"source_model_labels_by_state_ordinal",source.modelLabels(),"selected_state_ordinal",row.get("selected_state_ordinal"),"atoms_verified",source.atoms().size()));
        }
        List<?> steps=list(result.get("steps")),requested=list(recipe.get("steps"));
        require(steps.size()==requested.size(),"Step count differs");var audits=new ArrayList<Object>();
        for(int i=0;i<steps.size();i++) {
            Map<?,?> step=object(steps.get(i)),request=object(requested.get(i)),details=object(step.get("result"));
            String op=string(step.get("operation"));require(step.get("id").equals(request.get("id")) && op.equals(request.get("operation")),"Step identity/order differs");
            Map<String,Snapshot> before=snapshots(directory,object(step.get("before")),sources.keySet());
            Map<String,Snapshot> after=snapshots(directory,object(step.get("after")),sources.keySet());
            require(before.equals(previous),"Before ledger differs from preceding output");
            var audit=new LinkedHashMap<String,Object>();audit.put("id",step.get("id"));audit.put("operation",op);
            switch(op) {
                case "measure"->{unchanged(before,after,Set.of());measure(request,details,before,audit);}
                case "render"->{unchanged(before,after,Set.of());render(directory,request,details,audit);}
                case "align","super"->{String mobile=string(details.get("mobile"));unchanged(before,after,Set.of(mobile));alignment(request,details,before,after,audit);}
                case "bond","unbond"->{String id=string(details.get("structure"));unchanged(before,after,Set.of(id));bond(op,request,details,before.get(id),after.get(id),before,audit);}
                case "set_dihedral"->{String id=string(details.get("structure"));unchanged(before,after,Set.of(id));torsion(request,details,before.get(id),after.get(id),before,audit);}
                case "mutate"->{String id=string(details.get("structure"));unchanged(before,after,Set.of(id));mutate(request,details,before.get(id),after.get(id),before,audit);}
                default->throw new IOException("Unknown original operation");
            }
            audit.put("status","passed-independent-native-audit");audits.add(audit);previous=after;
        }
        Map<?,?> exports=object(result.get("exports"));require(exports.keySet().equals(sources.keySet()),"Export object set differs");
        require(snapshots(directory,object(result.get("final_ledgers")),sources.keySet()).equals(previous),"Final authoritative atom/bond ledgers differ");
        var exportRows=new ArrayList<Object>();
        for(String id:sources.keySet()) {
            Path path=output(directory,string(exports.get(id)));require(Files.size(path)>0 && Files.size(path)<=64L*1048576,"Missing/oversized final mmCIF export");
            // Original mmCIF exports always include explicit author identifiers and segment labels.
            var parsed=PymolStructureInput.parse(Files.readString(path,StandardCharsets.UTF_8),"cif",1,"author",100000);
            matchExport(parsed.atoms(),previous.get(id));
            exportRows.add(Map.of("id",id,"path",path.getFileName().toString(),"sha256",FollowupSupport.hash(path),"atoms_verified",parsed.atoms().size(),"scope","Original mmCIF rounds coordinates to0.001 Angstrom and B/occupancy to0.01; no bond categories. Final JSON ledgers retain full original precision and bonds."));
        }
        return Map.of("status","passed-independent-native-audit","sources",sourceRows,"steps",audits,"exports",exportRows,
                "scope","Native input/atom identity and coordinate checks, original inlier correspondences and rigid-motion checks; no force-field, biological-equivalence or historical-version parity claim");
    }

    static void matchSource(List<Atom> expected,Snapshot actual)throws IOException {
        require(actual!=null && expected.size()==actual.atoms().size(),"Source/export atom count differs from original ledger");
        var keyed=new LinkedHashMap<String,Atom>();for(Atom atom:actual.atoms().values())keyed.put(atom.key(),atom);
        for(Atom atom:expected) {
            Atom found=keyed.get(atom.key());require(found!=null,"Source/export chemical identity differs: "+atom.key().replace('\u001f',':'));
            // PyMOL can renumber IDs after edits. Input serial preservation is checked separately by the caller.
            sameProperties(atom,found);nearPoint(atom.point(),found.point(),tolerance(atom.point(),found.point()),"Source/export coordinate differs");
        }
    }
    private static void matchExport(List<Atom> exported,Snapshot actual)throws IOException {
        require(exported.size()==actual.atoms().size(),"Export atom count differs");var keyed=keyed(actual);
        for(Atom atom:exported){Atom found=keyed.get(atom.key());require(found!=null,"Export chemical atom identity differs");require(atom.element().equals(found.element()) && atom.hetatm()==found.hetatm(),"Export element/hetero flag differs");nearPoint(atom.point(),found.point(),.0009+tolerance(atom.point(),found.point()),"Export rounding exceeds declared precision");near(atom.b(),found.b(),.0051,"Export B-factor rounding exceeds declared precision");near(atom.q(),found.q(),.0051,"Export occupancy rounding exceeds declared precision");}
    }

    private static void measure(Map<?,?> request,Map<?,?> result,Map<String,Snapshot> before,Map<String,Object> audit)throws IOException {
        List<?> specs=list(request.get("atoms")),references=list(result.get("atoms"));require(specs.size()==references.size(),"Measurement atom count differs");
        var points=new ArrayList<Point>();
        for(int i=0;i<specs.size();i++) {
            var spec=object(specs.get(i));var selected=select(spec,before);require(selected.size()==1,"Measurement selection is ambiguous/empty");
            Map<?,?> reference=object(references.get(i));require(spec.get("structure").equals(reference.get("structure")),"Measurement object differs");
            int index=integer(reference.get("index"),1,Integer.MAX_VALUE);require(selected.get(0).getKey()==index,"Measurement correspondence differs");points.add(selected.get(0).getValue().point());
        }
        String kind=string(request.get("kind"));require(kind.equals(result.get("kind")),"Measurement kind differs");
        double nativeValue=switch(kind){case "distance"->MolecularGeometry.distance(points.get(0),points.get(1));case "angle"->MolecularGeometry.angle(points.get(0),points.get(1),points.get(2));case "dihedral"->MolecularGeometry.dihedral(points.get(0),points.get(1),points.get(2),points.get(3));default->throw new IOException("Unknown measurement");};
        double original=number(result.get("value"));near(kind.equals("dihedral")?wrap(original-nativeValue):original-nativeValue,0,.001,"Original/native measurement differs");
        audit.put("native_value",nativeValue);audit.put("original_value",original);audit.put("unit",result.get("unit"));
    }
    private static void alignment(Map<?,?> request,Map<?,?> result,Map<String,Snapshot> before,Map<String,Snapshot> after,Map<String,Object> audit)throws Exception {
        String mobile=string(result.get("mobile")),target=string(result.get("target"));
        require(!mobile.equals(target) && object(request.get("mobile")).get("structure").equals(mobile) && object(request.get("target")).get("structure").equals(target),"Alignment objects differ");
        var m=before.get(mobile);var t=before.get(target);var a=after.get(mobile);identicalAtoms(m,a,false);require(m.bonds().equals(a.bonds()),"Alignment changed bonds");
        var mobileSelected=indices(select(object(request.get("mobile")),before));var targetSelected=indices(select(object(request.get("target")),before));
        require(mobileSelected.equals(integerSet(result.get("mobile_selected_indices"))) && targetSelected.equals(integerSet(result.get("target_selected_indices"))),"Alignment selected atom set differs");
        var pairs=list(result.get("inlier_pairs"));var usedM=new HashSet<Integer>();var usedT=new HashSet<Integer>();double sum=0;
        for(Object value:pairs) {
            List<?> pair=list(value);require(pair.size()==2,"Malformed inlier pair");int mi=integer(pair.get(0),1,Integer.MAX_VALUE),ti=integer(pair.get(1),1,Integer.MAX_VALUE);
            require(mobileSelected.contains(mi) && targetSelected.contains(ti) && usedM.add(mi) && usedT.add(ti),"Invalid/duplicate original inlier correspondence");
            double distance=MolecularGeometry.distance(a.atoms().get(mi).point(),t.atoms().get(ti).point());sum+=distance*distance;
        }
        require(pairs.size()>=3,"Fewer than three original inlier atom pairs; orientation is not sufficiently constrained for this adapter");
        List<?> summary=list(result.get("original_summary"));require(summary.size()==7,"Expected original seven-value alignment summary");
        require(integer(summary.get(1),3,Integer.MAX_VALUE)==pairs.size(),"Original inlier count differs");
        for(int i=0;i<summary.size();i++){double value=number(summary.get(i));if(i!=5)require(value>=0,"Negative alignment RMSD/count");}
        double rmsd=Math.sqrt(sum/pairs.size());near(rmsd,number(summary.get(0)),.001,"Original/native fitted inlier RMSD differs");
        var from=new ArrayList<Point>();var to=new ArrayList<Point>();for(int index:m.atoms().keySet()){from.add(m.atoms().get(index).point());to.add(a.atoms().get(index).point());}
        var fit=MolecularGeometry.fit(from,to,MolecularGeometry.Degeneracy.ALLOW_NONUNIQUE,new CpuComputeBackend());
        require(fit.maximumResidual()<=.0005+maxTolerance(from,to),"Alignment did not apply one proper rigid motion to the complete mobile object");
        audit.put("original_summary",summary);audit.put("inlier_pairs_verified",pairs.size());audit.put("native_inlier_rmsd_angstrom",rmsd);
        audit.put("all_mobile_atoms_verified",from.size());audit.put("proper_motion_maximum_residual_angstrom",fit.maximumResidual());audit.put("proper_motion_rotation_unique",fit.rotationUnique());audit.put("rotation",fit.rotation());audit.put("translation",fit.translation());
    }
    private static void bond(String operation,Map<?,?> request,Map<?,?> result,Snapshot before,Snapshot after,Map<String,Snapshot> all,Map<String,Object> audit)throws IOException {
        identicalAtoms(before,after,true);List<Integer> indices=selectedIndices(request,result,all,2);int order=integer(result.get("order"),1,4);
        if(operation.equals("bond"))require(order==integer(request.containsKey("order")?request.get("order"):1,1,3),"Requested bond order differs");
        List<Integer> edge=edge(indices.get(0),indices.get(1),order);var expected=new HashSet<>(before.bonds());
        require(operation.equals("bond")?expected.add(edge):expected.remove(edge),"Invalid graph edit precondition");require(expected.equals(after.bonds()),"Graph edit changed an unintended edge");
        audit.put("edge",edge);audit.put("coordinates_unchanged",true);
    }
    private static void torsion(Map<?,?> request,Map<?,?> result,Snapshot before,Snapshot after,Map<String,Snapshot> all,Map<String,Object> audit)throws IOException {
        identicalAtoms(before,after,false);require(before.bonds().equals(after.bonds()),"Torsion changed the atom graph");
        List<Integer> indices=selectedIndices(request,result,all,4);double old=dihedral(before,indices),value=dihedral(after,indices),requested=number(request.get("degrees"));
        near(wrap(old-number(result.get("original_before"))),0,.001,"Initial original/native torsion differs");near(wrap(value-number(result.get("original_after"))),0,.001,"Final original/native torsion differs");near(wrap(value-requested),0,.001,"Requested torsion not achieved");
        double maximum=0;for(List<Integer> bond:before.bonds()) {
            double delta=Math.abs(MolecularGeometry.distance(before.atoms().get(bond.get(0)).point(),before.atoms().get(bond.get(1)).point())-MolecularGeometry.distance(after.atoms().get(bond.get(0)).point(),after.atoms().get(bond.get(1)).point()));maximum=Math.max(maximum,delta);
        }
        require(maximum<=.001,"Torsion edit distorted a bond length");audit.put("native_before_degrees",old);audit.put("native_after_degrees",value);audit.put("maximum_bond_length_change_angstrom",maximum);
    }
    private static void mutate(Map<?,?> request,Map<?,?> result,Snapshot before,Snapshot after,Map<String,Snapshot> all,Map<String,Object> audit)throws IOException {
        require(before!=null && after!=null,"Mutation object absent");var selected=select(object(request.get("residue")),all);require(!selected.isEmpty(),"Empty mutation residue");String residue=selected.get(0).getValue().residue();
        var selectedKeys=new HashSet<String>();for(var entry:selected){require(residue.equals(entry.getValue().residue()) && entry.getValue().alt().isEmpty(),"Mutation residue ambiguous/alternate");selectedKeys.add(entry.getValue().key());}
        var beforeKeys=keyed(before);var afterKeys=keyed(after);var outsideBefore=new HashSet<String>();var outsideAfter=new HashSet<String>();
        for(var e:beforeKeys.entrySet())if(!e.getValue().residue().equals(residue))outsideBefore.add(e.getKey());else require(selectedKeys.contains(e.getKey()),"Mutation selector omitted residue atoms");
        for(var e:afterKeys.entrySet())if(!e.getValue().residue().equals(residue))outsideAfter.add(e.getKey());
        require(outsideBefore.equals(outsideAfter),"Mutation changed an unselected atom identity");
        for(String key:outsideBefore){sameProperties(beforeKeys.get(key),afterKeys.get(key));nearPoint(beforeKeys.get(key).point(),afterKeys.get(key).point(),.00001,"Mutation moved an unselected atom");}
        String target=string(request.get("to"));require(target.equals(result.get("target_resn")),"Mutation target differs");
        int count=0;var names=new HashSet<String>();var heavy=new HashSet<String>();double shift=0;
        for(Atom atom:after.atoms().values())if(atom.residue().equals(residue)) {
            require(atom.resn().equals(target) && atom.alt().isEmpty(),"Original mutation did not produce requested residue");require(names.add(atom.name()),"Duplicate mutation atom name");count++;
            if(!atom.element().equals("H"))heavy.add(atom.name());else require(!"none".equals(request.get("hydrogens")),"Mutation retained hydrogens despite explicit none policy");
            if(Set.of("N","CA","C","O","OXT").contains(atom.name())) {
                Atom old=selected.stream().map(Map.Entry::getValue).filter(a->a.name().equals(atom.name())).findFirst().orElse(null);
                require(old!=null,"Mutation introduced an unexpected backbone atom");shift=Math.max(shift,MolecularGeometry.distance(old.point(),atom.point()));
            }
        }
        require(count>0 && names.containsAll(Set.of("N","CA","C","O")),"Mutation backbone missing");
        var expectedHeavy=new HashSet<>(HEAVY.getOrDefault(target,Set.of()));if(selected.stream().anyMatch(e->e.getValue().name().equals("OXT")))expectedHeavy.add("OXT");
        require(heavy.equals(expectedHeavy),"Mutation canonical heavy-atom set differs");
        require(shift<=number(request.get("max_backbone_shift"))+.00001,"Original mutation exceeded caller-declared maximum backbone shift");
        integer(result.get("selected_rotamer"),1,integer(result.get("available_rotamers"),1,100000));
        require(integer(result.get("selected_rotamer"),1,100000)==integer(request.get("rotamer"),1,100000),"Original mutation rotamer differs");
        // Atom indices/serials may be reassigned by the original wizard; chemical identities define this check.
        var outsideBondsBefore=chemicalBonds(before,outsideBefore);var outsideBondsAfter=chemicalBonds(after,outsideAfter);
        require(outsideBondsBefore.equals(outsideBondsAfter),"Mutation changed a bond entirely outside the selected residue");
        require(backboneLinks(before,residue).equals(backboneLinks(after,residue)),"Mutation changed a backbone link to an unselected residue");
        audit.put("unselected_atoms_verified",outsideBefore.size());audit.put("new_residue_atoms",count);audit.put("maximum_backbone_shift_angstrom",shift);audit.put("selected_rotamer",result.get("selected_rotamer"));audit.put("available_rotamers",result.get("available_rotamers"));
    }
    private static void render(Path directory,Map<?,?> request,Map<?,?> result,Map<String,Object> audit)throws IOException {
        Path image=output(directory,string(result.get("image")));require(image.getFileName().toString().equals(request.get("id")+".png"),"Image name differs");
        int width=integer(request.get("width"),100,4000),height=integer(request.get("height"),100,4000);
        require(Files.size(image)>8 && Files.size(image)<=128L*1048576,"Missing/oversized render");
        try(var stream=Files.newInputStream(image)){byte[] magic=stream.readNBytes(8);require(java.util.Arrays.equals(magic,new byte[]{(byte)137,80,78,71,13,10,26,10}),"Render is not PNG");}
        var pixels=ImageIO.read(image.toFile());require(pixels!=null && pixels.getWidth()==width && pixels.getHeight()==height,"PNG dimensions differ");
        int first=pixels.getRGB(0,0);long different=0;for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(pixels.getRGB(x,y)!=first)different++;
        require(different>0,"Blank render");audit.put("width",width);audit.put("height",height);audit.put("pixels_differing_from_corner",different);audit.put("image_sha256",FollowupSupport.hash(image));
    }

    private static Map<String,Atom> keyed(Snapshot snapshot)throws IOException {var out=new LinkedHashMap<String,Atom>();for(Atom atom:snapshot.atoms().values())require(out.putIfAbsent(atom.key(),atom)==null,"Duplicate chemical atom identity");return out;}
    private static Map<String,Set<String>> heavyAtoms(){var out=new LinkedHashMap<String,Set<String>>();for(String row:List.of("ALA CB","ARG CB CG CD NE CZ NH1 NH2","ASN CB CG OD1 ND2","ASP CB CG OD1 OD2","CYS CB SG","GLN CB CG CD OE1 NE2","GLU CB CG CD OE1 OE2","GLY","HIS CB CG ND1 CD2 CE1 NE2","ILE CB CG1 CG2 CD1","LEU CB CG CD1 CD2","LYS CB CG CD CE NZ","MET CB CG SD CE","PHE CB CG CD1 CD2 CE1 CE2 CZ","PRO CB CG CD","SER CB OG","THR CB OG1 CG2","TRP CB CG CD1 CD2 NE1 CE2 CE3 CZ2 CZ3 CH2","TYR CB CG CD1 CD2 CE1 CE2 CZ OH","VAL CB CG1 CG2")){String[] fields=row.split(" ");var names=new HashSet<>(Set.of("N","CA","C","O"));for(int i=1;i<fields.length;i++)names.add(fields[i]);out.put(fields[0],Set.copyOf(names));}return Map.copyOf(out);}
    private static Set<List<String>> chemicalBonds(Snapshot snapshot,Set<String> included) {var out=new HashSet<List<String>>();for(var edge:snapshot.bonds()){String a=snapshot.atoms().get(edge.get(0)).key(),b=snapshot.atoms().get(edge.get(1)).key();if(included.contains(a)&&included.contains(b))out.add(a.compareTo(b)<0?List.of(a,b,edge.get(2).toString()):List.of(b,a,edge.get(2).toString()));}return out;}
    private static Set<List<String>> backboneLinks(Snapshot snapshot,String residue){var out=new HashSet<List<String>>();for(var edge:snapshot.bonds()){Atom a=snapshot.atoms().get(edge.get(0)),b=snapshot.atoms().get(edge.get(1));if(a.residue().equals(residue)!=b.residue().equals(residue)){Atom selected=a.residue().equals(residue)?a:b,other=selected==a?b:a;if(Set.of("N","C").contains(selected.name()))out.add(List.of(selected.name(),other.key(),edge.get(2).toString()));}}return out;}
    private static double dihedral(Snapshot snapshot,List<Integer> indices){var p=indices.stream().map(i->snapshot.atoms().get(i).point()).toList();return MolecularGeometry.dihedral(p.get(0),p.get(1),p.get(2),p.get(3));}
    private static List<Integer> selectedIndices(Map<?,?> request,Map<?,?> result,Map<String,Snapshot> before,int count)throws IOException {
        List<?> specs=list(request.get("atoms")),actual=list(result.get("indices"));require(specs.size()==count && actual.size()==count,"Edit atom count differs");var indices=new ArrayList<Integer>();
        for(int i=0;i<count;i++){Map<?,?> spec=object(specs.get(i));var selected=select(spec,before);require(selected.size()==1 && spec.get("structure").equals(result.get("structure")),"Edit atom selection differs");int index=integer(actual.get(i),1,Integer.MAX_VALUE);require(selected.get(0).getKey()==index,"Edit atom identity differs");indices.add(index);}require(new HashSet<>(indices).size()==count,"Repeated edit atom");return indices;
    }
    static List<Map.Entry<Integer,Atom>> select(Map<?,?> spec,Map<String,Snapshot> snapshots)throws IOException {
        Snapshot snapshot=snapshots.get(string(spec.get("structure")));require(snapshot!=null,"Unknown selected structure");var selected=new ArrayList<Map.Entry<Integer,Atom>>();
        var criteria=new LinkedHashMap<String,Set<String>>();
        for(var entry:spec.entrySet())if(!entry.getKey().equals("structure")) {
            String field=string(entry.getKey());require(Set.of("chain","segi","resi","resn","name","alt","element").contains(field),"Unknown atom selection field");
            List<?> values=entry.getValue() instanceof List<?> list?list:List.of(entry.getValue());require(!values.isEmpty() && values.size()<=10000,"Empty/oversized selection field");
            var accepted=new HashSet<String>();for(Object value:values){String s=string(value);accepted.add(field.equals("element")?s.toUpperCase(java.util.Locale.ROOT):s);}criteria.put(field,accepted);
        }
        for(var entry:snapshot.atoms().entrySet()) {
            Atom a=entry.getValue();Map<String,String> fields=Map.of("chain",a.chain(),"segi",a.segi(),"resi",a.resi(),"resn",a.resn(),"name",a.name(),"alt",a.alt(),"element",a.element().toUpperCase(java.util.Locale.ROOT));boolean match=true;
            for(var e:criteria.entrySet())match&=e.getValue().contains(fields.get(e.getKey()));
            if(match)selected.add(entry);
        }
        return selected;
    }
    private static Set<Integer> indices(List<Map.Entry<Integer,Atom>> rows){var set=new HashSet<Integer>();for(var row:rows)set.add(row.getKey());return set;}
    private static Set<Integer> integerSet(Object value)throws IOException {var set=new HashSet<Integer>();for(Object item:list(value))require(set.add(integer(item,1,Integer.MAX_VALUE)),"Duplicate index");return set;}
    private static void unchanged(Map<String,Snapshot> before,Map<String,Snapshot> after,Set<String> allowed)throws IOException {require(before.keySet().equals(after.keySet()) && before.keySet().containsAll(allowed),"Unknown/missing output object");for(String key:before.keySet())if(!allowed.contains(key))require(before.get(key).equals(after.get(key)),"Unrequested change to object "+key);}
    private static void identicalAtoms(Snapshot before,Snapshot after,boolean coordinates)throws IOException {require(before!=null && after!=null && before.atoms().keySet().equals(after.atoms().keySet()),"Atom index/count changed");for(int index:before.atoms().keySet()){Atom a=before.atoms().get(index),b=after.atoms().get(index);require(a.id()==b.id() && a.key().equals(b.key()),"Atom identity changed");sameProperties(a,b);if(coordinates)require(a.point().equals(b.point()),"Graph edit moved coordinates");}}
    private static void sameProperties(Atom a,Atom b)throws IOException {require(a.element().equals(b.element()) && a.hetatm()==b.hetatm(),"Element/hetero flag changed");near(a.b(),b.b(),.0002,"B factor changed");near(a.q(),b.q(),.0002,"Occupancy changed");}
    private static Map<String,Snapshot> snapshots(Path directory,Map<?,?> files,Set<String> expected)throws IOException {require(files.keySet().equals(expected),"Ledger object set differs");var out=new LinkedHashMap<String,Snapshot>();for(String id:expected)out.put(id,snapshot(read(output(directory,string(files.get(id))),64*1048576)));return out;}
    static Snapshot snapshot(Map<?,?> value)throws IOException {
        require(integer(value.get("states"),1,1)==1,"Chosen object is not single state");List<?> rows=list(value.get("atoms"));require(!rows.isEmpty() && rows.size()<=100000,"Atom ledger count outside bound");var atoms=new LinkedHashMap<Integer,Atom>();
        for(Object raw:rows){Map<?,?> row=object(raw);require(row.keySet().equals(ATOM_FIELDS),"Atom ledger fields differ");List<?> xyz=list(row.get("coord"));require(xyz.size()==3,"Coordinate requires three values");
            Atom atom=new Atom(integer(row.get("id"),Integer.MIN_VALUE,Integer.MAX_VALUE),string(row.get("name")),string(row.get("resn")),string(row.get("resi")),string(row.get("chain")),string(row.get("segi")),string(row.get("alt")),string(row.get("element")).toUpperCase(java.util.Locale.ROOT),integer(row.get("hetatm"),0,1),new Point(number(xyz.get(0)),number(xyz.get(1)),number(xyz.get(2))),number(row.get("b")),number(row.get("q")));
            require(atoms.putIfAbsent(integer(row.get("index"),1,Integer.MAX_VALUE),atom)==null,"Duplicate atom ledger index");}
        var bonds=new HashSet<List<Integer>>();for(Object raw:list(value.get("bonds"))){List<?> e=list(raw);require(e.size()==3,"Malformed bond");int a=integer(e.get(0),1,Integer.MAX_VALUE),b=integer(e.get(1),1,Integer.MAX_VALUE),order=integer(e.get(2),1,4);require(a!=b && atoms.containsKey(a) && atoms.containsKey(b) && bonds.add(edge(a,b,order)),"Invalid/duplicate bond");}
        var snapshot=new Snapshot(atoms,bonds);keyed(snapshot);return snapshot;
    }
    private static List<Integer> edge(int a,int b,int order){return List.of(Math.min(a,b),Math.max(a,b),order);}
    private static double maxTolerance(List<Point> a,List<Point> b){double max=0;for(int i=0;i<a.size();i++)max=Math.max(max,tolerance(a.get(i),b.get(i)));return max;}
    private static double tolerance(Point a,Point b){return .000003+8e-8*Math.max(Math.max(Math.max(Math.abs(a.x()),Math.abs(a.y())),Math.abs(a.z())),Math.max(Math.max(Math.abs(b.x()),Math.abs(b.y())),Math.abs(b.z())));}
    private static void nearPoint(Point a,Point b,double tolerance,String error)throws IOException {require(MolecularGeometry.distance(a,b)<=tolerance,error);}
    private static void near(double a,double b,double tolerance,String error)throws IOException {require(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a-b)<=tolerance,error+" ("+a+" vs "+b+")");}
    private static double wrap(double value){return Math.IEEEremainder(value,360);}
    static Path output(Path directory,String name)throws IOException {Path relative=Path.of(name);require(!relative.isAbsolute() && relative.getNameCount()==1 && !name.contains("\\") && !Set.of(".","..").contains(name),"Unexpected output member path");Path path=directory.resolve(relative);require(Files.isRegularFile(path) && !Files.isSymbolicLink(path),"Missing/linked original output");return path;}
    static Map<?,?> read(Path file,int maximum)throws IOException {require(Files.isRegularFile(file) && !Files.isSymbolicLink(file) && Files.size(file)<=maximum,"Missing/oversized JSON: "+file);return object(SimpleJson.parse(Files.readString(file,StandardCharsets.UTF_8)));}
    static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require JSON object");return map;}
    static List<?> list(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Require JSON array");return list;}
    static String string(Object value)throws IOException {if(!(value instanceof String s) || s.length()>10000 || s.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid JSON text");return s;}
    static double number(Object value)throws IOException {if(!(value instanceof Number n) || !Double.isFinite(n.doubleValue()))throw new IOException("Require finite number");return n.doubleValue();}
    static int integer(Object value,int minimum,int maximum)throws IOException {double n=number(value);if(n!=Math.rint(n)||n<minimum||n>maximum)throw new IOException("Require integer within bound");return (int)n;}
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

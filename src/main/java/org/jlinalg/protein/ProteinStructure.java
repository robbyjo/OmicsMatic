/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Protein C-alpha coordinates, explicit sequence mapping and directional PAE summaries. */
public final class ProteinStructure {
    private ProteinStructure() { }
    private static final Map<String,Character> AMINO=amino();
    public record Residue(String chain,int modelPosition,int authorNumber,String insertion,
            char aminoAcid,String residueName,double x,double y,double z,double plddt) { }
    public record Model(List<Residue> residues,Map<String,String> polymerSequences,Set<Integer> modelNumbers,String format) {
        public Model {residues=List.copyOf(residues);polymerSequences=Map.copyOf(polymerSequences);modelNumbers=Set.copyOf(modelNumbers);}
        public List<String> chains(){return residues.stream().map(Residue::chain).distinct().sorted().toList();}
    }
    public record Mapped(Residue residue,int sequencePosition) { }
    public record Assessment(String chain,String sequenceMatch,int targetLength,List<Mapped> residues,
            double coverage,int confidenceResidues,double meanPlddt,double fractionVeryLow,
            double fractionLow,double fractionConfident,double fractionVeryHigh,int ambiguousAminoAcids) {
        public Assessment {residues=List.copyOf(residues);}
    }
    public record Region(String id,int start,int end) {
        public Region {if(id==null||id.isBlank()||start<1||end<start)throw new IllegalArgumentException("Invalid 1-based inclusive protein region");}
    }
    public record PaeSummary(String alignedRegion,String evaluatedRegion,int pairs,double mean,double maximum) { }

    public static String sequence(String input) {
        if(input==null)throw new IllegalArgumentException("Protein sequence is required");String sequence=input.replaceAll("\\s+","").toUpperCase(java.util.Locale.ROOT);
        if(sequence.isEmpty()||!sequence.matches("[ACDEFGHIKLMNPQRSTVWYUBZOJX]+"))throw new IllegalArgumentException("Require a nonempty amino-acid sequence; stops and non-amino-acid symbols need explicit upstream resolution");return sequence;
    }
    public static Map<String,String> fasta(String text,int maxSequences,int maxResidues) {
        var result=new LinkedHashMap<String,String>();String id=null;var value=new StringBuilder();
        for(String line:text.split("\\R",-1)){if(line.isBlank())continue;if(line.startsWith(">")){if(id!=null)putSequence(result,id,value.toString(),maxSequences,maxResidues);String header=line.substring(1).trim();if(header.isEmpty())throw new IllegalArgumentException("Blank FASTA identifier");id=header.split("\\s+",2)[0];value.setLength(0);}else{if(id==null)throw new IllegalArgumentException("FASTA sequence before its identifier");value.append(line.trim());if(value.length()>maxResidues)throw new IllegalArgumentException("Protein sequence exceeds residue bound");}}
        if(id!=null)putSequence(result,id,value.toString(),maxSequences,maxResidues);if(result.isEmpty())throw new IllegalArgumentException("Empty protein FASTA");return java.util.Collections.unmodifiableMap(result);
    }
    private static void putSequence(Map<String,String> sequences,String id,String value,int maximum,int residues){String sequence=sequence(value);if(sequence.length()>residues||sequences.size()>=maximum)throw new IllegalArgumentException("Protein FASTA exceeds registered bounds");if(sequences.putIfAbsent(id,sequence)!=null)throw new IllegalArgumentException("Duplicate FASTA identifier: "+id);}

    public static Model parse(String text,String format,int selectedModel,boolean bFactorPlddt,int maxResidues) {
        if(selectedModel<1||maxResidues<1)throw new IllegalArgumentException("Invalid structure model/residue bounds");
        return switch(format.toLowerCase(java.util.Locale.ROOT)){case "pdb"->pdb(text,selectedModel,bFactorPlddt,maxResidues);case "cif","mmcif"->cif(text,selectedModel,bFactorPlddt,maxResidues);default->throw new IllegalArgumentException("Protein structure format must be pdb or cif");};
    }
    private record Atom(Residue residue,String alternate) { }
    private static void add(Map<String,Atom> atoms,Residue residue,String alternate,int maximum) {
        if(!Double.isFinite(residue.x())||!Double.isFinite(residue.y())||!Double.isFinite(residue.z())||Math.max(Math.abs(residue.x()),Math.max(Math.abs(residue.y()),Math.abs(residue.z())))>1e6)throw new IllegalArgumentException("Invalid/out-of-bound C-alpha coordinate");
        if(Double.isFinite(residue.plddt())&&(residue.plddt()<0||residue.plddt()>100))throw new IllegalArgumentException("Declared pLDDT must lie in [0,100]; experimental B-factors are not pLDDT");
        if(!alternate.isEmpty()&&!alternate.equals("A"))return;String key=residue.chain()+"\u001f"+residue.authorNumber()+"\u001f"+residue.insertion();Atom old=atoms.get(key);
        if(old!=null){if(old.alternate().equals(alternate))throw new IllegalArgumentException("Duplicate primary C-alpha coordinate");if(old.alternate().isEmpty())return;}
        atoms.put(key,new Atom(residue,alternate));if(atoms.size()>maximum)throw new IllegalArgumentException("Structure exceeds registered C-alpha residue bound");
    }
    private static Model finish(Map<String,Atom> atoms,Map<String,String> polymer,Set<Integer> models,String format){if(atoms.isEmpty())throw new IllegalArgumentException("No protein C-alpha coordinates for the selected model");var residues=atoms.values().stream().map(Atom::residue).sorted(Comparator.comparing(Residue::chain).thenComparingInt(r->r.modelPosition()>0?r.modelPosition():r.authorNumber()).thenComparing(Residue::insertion)).toList();return new Model(residues,polymer,models,format);}
    private static Model pdb(String text,int selected,boolean confidence,int bound) {
        var atoms=new LinkedHashMap<String,Atom>();var sequences=new LinkedHashMap<String,StringBuilder>();var models=new java.util.TreeSet<Integer>();int model=1;
        for(String line:text.split("\\R")){String record=part(line,0,6).trim();if(record.equals("MODEL")){model=integer(part(line,10,14).trim(),"PDB model");continue;}if(record.equals("SEQRES")){String chain=part(line,11,12).trim();String residues=part(line,19,line.length()).trim();for(String name:residues.split("\\s+"))if(!name.isBlank())sequences.computeIfAbsent(chain,key->new StringBuilder()).append(amino(name));continue;}
            if(!Set.of("ATOM","HETATM").contains(record)||!part(line,12,16).trim().equals("CA"))continue;String name=part(line,17,20).trim();if(record.equals("HETATM")&&!AMINO.containsKey(name))continue;models.add(model);if(model!=selected)continue;
            int number=integer(part(line,22,26).trim(),"PDB residue number");String insertion=part(line,26,27).trim();double score=confidence?optional(part(line,60,66).trim()):Double.NaN;
            add(atoms,new Residue(part(line,21,22).trim(),insertion.isEmpty()&&number>0?number:0,number,insertion,amino(name),name,decimal(part(line,30,38).trim()),decimal(part(line,38,46).trim()),decimal(part(line,46,54).trim()),score),part(line,16,17).trim(),bound);
        }
        var polymer=new LinkedHashMap<String,String>();sequences.forEach((key,value)->polymer.put(key,value.toString()));return finish(atoms,polymer,models,"pdb");
    }
    private record Token(String value,boolean quoted) { }
    private static List<Token> tokenize(String source){var tokens=new ArrayList<Token>();int i=0;while(i<source.length()){
        char c=source.charAt(i);if(Character.isWhitespace(c)){i++;continue;}if(c=='#'){while(i<source.length()&&source.charAt(i)!='\n')i++;continue;}
        if(c==';'&&(i==0||source.charAt(i-1)=='\n')){int start=++i,end=-1;while(i<source.length()){if(source.charAt(i)==';'&&(i==0||source.charAt(i-1)=='\n')){end=i++;break;}i++;}if(end<0)throw new IllegalArgumentException("Unclosed CIF multiline value");tokens.add(new Token(source.substring(start,end),true));}
        else if(c=='\''||c=='"'){char quote=c;int start=++i,end=-1;while(i<source.length()){if(source.charAt(i)==quote&&(i+1==source.length()||Character.isWhitespace(source.charAt(i+1)))){end=i++;break;}i++;}if(end<0)throw new IllegalArgumentException("Unclosed CIF quoted value");tokens.add(new Token(source.substring(start,end),true));}
        else{int start=i;while(i<source.length()&&!Character.isWhitespace(source.charAt(i)))i++;tokens.add(new Token(source.substring(start,i),false));}
        if(tokens.size()>2000000)throw new IllegalArgumentException("CIF exceeds token storage bound");
    }return tokens;}
    private static boolean control(Token token){String text=token.value();return !token.quoted()&&(text.startsWith("_")||text.equals("loop_")||text.equals("stop_")||text.startsWith("data_")||text.startsWith("save_"));}
    private static Model cif(String source,int selected,boolean confidence,int bound){var tokens=tokenize(source);var atoms=new LinkedHashMap<String,Atom>();var models=new java.util.TreeSet<Integer>();var entitySequences=new LinkedHashMap<String,java.util.TreeMap<Integer,Character>>();var chainEntity=new LinkedHashMap<String,String>();int i=0;
        while(i<tokens.size()){Token token=tokens.get(i++);if(token.quoted()||!token.value().equals("loop_"))continue;var header=new ArrayList<String>();while(i<tokens.size()&&!tokens.get(i).quoted()&&tokens.get(i).value().startsWith("_"))header.add(tokens.get(i++).value());if(header.isEmpty()||header.size()>1000)throw new IllegalArgumentException("Invalid CIF loop headers");
            while(i<tokens.size()&&!control(tokens.get(i))){if(i+header.size()>tokens.size())throw new IllegalArgumentException("Incomplete CIF loop row");var row=new LinkedHashMap<String,String>();for(String name:header){if(control(tokens.get(i)))throw new IllegalArgumentException("Incomplete CIF loop row");row.put(name,tokens.get(i++).value());}
                if(header.get(0).startsWith("_entity_poly_seq.")){String entity=required(row,"_entity_poly_seq.entity_id");int position=integer(required(row,"_entity_poly_seq.num"),"polymer sequence position");var sequence=entitySequences.computeIfAbsent(entity,key->new java.util.TreeMap<>());if(sequence.putIfAbsent(position,amino(required(row,"_entity_poly_seq.mon_id")))!=null)throw new IllegalArgumentException("Duplicate polymer sequence position");}
                else if(header.get(0).startsWith("_struct_asym."))chainEntity.put(required(row,"_struct_asym.id"),required(row,"_struct_asym.entity_id"));
                else if(header.get(0).startsWith("_atom_site.")){
                    String atom=value(row,"_atom_site.label_atom_id",value(row,"_atom_site.auth_atom_id",""));if(!atom.equals("CA"))continue;String name=value(row,"_atom_site.label_comp_id",value(row,"_atom_site.auth_comp_id",""));if(value(row,"_atom_site.group_PDB","ATOM").equals("HETATM")&&!AMINO.containsKey(name))continue;int model=integer(value(row,"_atom_site.pdbx_PDB_model_num","1"),"CIF model");models.add(model);if(model!=selected)continue;
                    String chain=value(row,"_atom_site.label_asym_id",value(row,"_atom_site.auth_asym_id",""));String entity=value(row,"_atom_site.label_entity_id","");if(!entity.isEmpty())chainEntity.putIfAbsent(chain,entity);
                    int position=integer(value(row,"_atom_site.label_seq_id","0"),"CIF polymer position");int author=integer(value(row,"_atom_site.auth_seq_id",String.valueOf(position)),"CIF author residue number");String insertion=value(row,"_atom_site.pdbx_PDB_ins_code","");
                    add(atoms,new Residue(chain,position,author,insertion,amino(name),name,decimal(required(row,"_atom_site.Cartn_x")),decimal(required(row,"_atom_site.Cartn_y")),decimal(required(row,"_atom_site.Cartn_z")),confidence?optional(value(row,"_atom_site.B_iso_or_equiv","")):Double.NaN),value(row,"_atom_site.label_alt_id",""),bound);
                }
            }
        }
        var polymer=new LinkedHashMap<String,String>();for(var chain:chainEntity.entrySet()){var residues=entitySequences.get(chain.getValue());if(residues==null)continue;var sequence=new StringBuilder();for(int position=1;position<=residues.size();position++){Character residue=residues.get(position);if(residue==null)throw new IllegalArgumentException("Non-contiguous declared CIF polymer sequence");sequence.append(residue);}polymer.put(chain.getKey(),sequence.toString());}return finish(atoms,polymer,models,"cif");
    }

    /** Explicit residue maps override numbering, but never amino-acid identity checks. */
    public static Assessment assess(Model model,String chain,String target,int sequenceStart,Map<String,Integer> explicitMapping,String declaredModelSequence) {
        target=sequence(target);if(chain==null||chain.isBlank()){if(model.chains().size()!=1)throw new IllegalArgumentException("Select an explicit chain for a multi-chain structure");chain=model.chains().get(0);}String selected=chain;var residues=model.residues().stream().filter(r->r.chain().equals(selected)).toList();if(residues.isEmpty())throw new IllegalArgumentException("Selected protein chain is absent");if(sequenceStart<1||sequenceStart>target.length())throw new IllegalArgumentException("Invalid target sequence start");
        String polymer=model.polymerSequences().getOrDefault(chain,"");if(declaredModelSequence!=null&&!declaredModelSequence.isBlank()){String declared=sequence(declaredModelSequence);if(!polymer.isEmpty()&&!polymer.equals(declared))throw new IllegalArgumentException("Declared model sequence differs from its coordinate-file polymer");polymer=declared;}
        String coordinateSequence=residues.stream().collect(StringBuilder::new,(s,r)->s.append(r.aminoAcid()),StringBuilder::append).toString();boolean completeCoordinates=coordinateSequence.equals(target)&&sequenceStart==1;boolean completeModelCoordinates=!polymer.isEmpty()&&coordinateSequence.equals(polymer);
        if(!polymer.isEmpty()&&(sequenceStart+polymer.length()-1>target.length()||!target.regionMatches(sequenceStart-1,polymer,0,polymer.length())))throw new IllegalArgumentException("Model polymer sequence differs from the target sequence/isoform");
        var mapped=new ArrayList<Mapped>();var positions=new java.util.HashSet<Integer>();int at=0,confident=0,veryLow=0,low=0,high=0,veryHigh=0;double mean=0;
        for(var residue:residues){String key=residueKey(residue);Integer supplied=explicitMapping.get(key);int position=supplied!=null?supplied:completeCoordinates?at+1:completeModelCoordinates?sequenceStart+at:residue.modelPosition()>0?sequenceStart+residue.modelPosition()-1:0;at++;
            if(position<1||position>target.length())throw new IllegalArgumentException("Unmapped/out-of-range residue; provide an exact residue_map for partial author numbering or insertions");if(!positions.add(position))throw new IllegalArgumentException("Multiple coordinates map to the same target residue");if(target.charAt(position-1)!=residue.aminoAcid())throw new IllegalArgumentException("Coordinate amino acid differs from target at residue "+position);
            mapped.add(new Mapped(residue,position));double plddt=residue.plddt();if(Double.isFinite(plddt)){confident++;mean+=(plddt-mean)/confident;if(plddt<50)veryLow++;else if(plddt<70)low++;else if(plddt<90)high++;else veryHigh++;}
        }
        int ambiguous=(int)target.chars().filter(c->"BXZJ".indexOf(c)>=0).count();String match=completeCoordinates?"exact_complete_coordinate_sequence":!polymer.isEmpty()&&polymer.equals(target)&&sequenceStart==1?"declared_full_polymer_with_partial_coordinates":"coordinate_identity_verified_partial_sequence_context";
        return new Assessment(chain,match,target.length(),mapped,(double)mapped.size()/target.length(),confident,confident>0?mean:Double.NaN,confident>0?(double)veryLow/confident:Double.NaN,confident>0?(double)low/confident:Double.NaN,confident>0?(double)high/confident:Double.NaN,confident>0?(double)veryHigh/confident:Double.NaN,ambiguous);
    }
    public static String residueKey(Residue r){return r.chain()+":"+r.authorNumber()+":"+r.insertion();}
    public static List<Integer> peptidePositions(String target,String peptide){target=sequence(target);peptide=sequence(peptide);var positions=new ArrayList<Integer>();for(int at=target.indexOf(peptide);at>=0;at=target.indexOf(peptide,at+1))positions.add(at+1);return List.copyOf(positions);}
    public static List<PaeSummary> pae(double[][] matrix,List<Region> regions,int maximumCells) {
        int n=matrix.length;if(n<1||(long)n*n>maximumCells)throw new IllegalArgumentException("PAE matrix exceeds registered square-cell bound");for(double[] row:matrix){if(row.length!=n)throw new IllegalArgumentException("PAE matrix must be square");for(double value:row)if(!Double.isFinite(value)||value<0||value>1e6)throw new IllegalArgumentException("PAE requires finite nonnegative Angstrom values within computational bounds");}
        for(var region:regions)if(region.end()>n)throw new IllegalArgumentException("PAE region exceeds model sequence positions");var result=new ArrayList<PaeSummary>();
        // AlphaFold's first index is the alignment frame; the second is the
        // evaluated residue (official PredictedAlignedErrorHead loss).
        // Preserve both directions; AlphaFold PAE need not be symmetric.
        for(var aligned:regions)for(var evaluated:regions){double mean=0,maximum=0;int pairs=0;for(int x=aligned.start()-1;x<aligned.end();x++)for(int y=evaluated.start()-1;y<evaluated.end();y++){double value=matrix[x][y];pairs++;mean+=(value-mean)/pairs;maximum=Math.max(maximum,value);}result.add(new PaeSummary(aligned.id(),evaluated.id(),pairs,mean,maximum));}return List.copyOf(result);
    }
    private static String part(String value,int start,int end){return start>=value.length()?"":value.substring(start,Math.min(end,value.length()));}
    private static String value(Map<String,String> row,String key,String fallback){String value=row.get(key);return value==null||value.equals(".")||value.equals("?")?fallback:value;}
    private static String required(Map<String,String> row,String key){String value=value(row,key,"");if(value.isEmpty())throw new IllegalArgumentException("Missing CIF field: "+key);return value;}
    private static double decimal(String value){double number=Double.parseDouble(value);if(!Double.isFinite(number))throw new IllegalArgumentException("Nonfinite structure coordinate");return number;}
    private static double optional(String value){return value.isBlank()||value.equals(".")||value.equals("?")?Double.NaN:decimal(value);}
    private static int integer(String value,String label){try{return Integer.parseInt(value);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid "+label);}}
    private static char amino(String name){Character value=AMINO.get(name.toUpperCase(java.util.Locale.ROOT));if(value==null)throw new IllegalArgumentException("Unsupported protein residue: "+name);return value;}
    private static Map<String,Character> amino(){String[] names={"ALA","CYS","ASP","GLU","PHE","GLY","HIS","ILE","LYS","LEU","MET","ASN","PRO","GLN","ARG","SER","THR","VAL","TRP","TYR","SEC","PYL","ASX","GLX","XLE","UNK","MSE"};String codes="ACDEFGHIKLMNPQRSTVWYUOBZJXM";var map=new LinkedHashMap<String,Character>();for(int i=0;i<names.length;i++)map.put(names[i],codes.charAt(i));return Map.copyOf(map);}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.protein.MolecularGeometry.Point;

/** Independent coordinate/identity reader. No assemblies, alternate filtering or bond inference. */
final class PymolStructureInput {
    private PymolStructureInput() { }
    record Atom(int id,String name,String resn,String resi,String chain,String segi,String alt,
            String element,int hetatm,Point point,double b,double q) {
        String key(){return String.join("\u001f",segi,chain,resi,resn,name,alt);}
        String residue(){return String.join("\u001f",segi,chain,resi);}
    }
    record Parsed(List<Atom> atoms,List<String> modelLabels,int sourceAtoms) {
        Parsed {atoms=List.copyOf(atoms);modelLabels=List.copyOf(modelLabels);}
    }
    static Parsed parse(String source,String format,int state,String namespace,int maximum)throws IOException {
        if(state<1 || maximum<1)throw new IOException("Invalid state/atom limit");
        Parsed parsed=switch(format){case "pdb"->pdb(source,state,maximum);case "cif"->cif(source,state,namespace,maximum);default->throw new IOException("Require pdb or cif");};
        if(parsed.atoms().isEmpty() || state>parsed.modelLabels().size())throw new IOException("Selected state ordinal is absent/empty");
        var keys=new HashSet<String>();
        for(var atom:parsed.atoms())if(!keys.add(atom.key()))throw new IOException("Duplicate chemical atom identity in the selected state; disambiguate chain, segment, residue and alternate conformer in a new input snapshot");
        return parsed;
    }
    private static Parsed pdb(String source,int selected,int maximum)throws IOException {
        var atoms=new ArrayList<Atom>();var models=new ArrayList<String>();
        boolean explicit=false,inside=false;int ordinal=1,total=0;
        for(String line:source.split("\\R")) {
            String type=part(line,0,6).trim();
            if(type.equals("MODEL")) {
                if(inside || (!explicit && !models.isEmpty()))throw new IOException("Mixed/nested PDB MODEL records");
                explicit=true;inside=true;String label=part(line,10,14).trim();
                if(label.isEmpty())throw new IOException("Blank PDB MODEL identifier");models.add(label);ordinal=models.size();continue;
            }
            if(type.equals("ENDMDL")){if(!inside)throw new IOException("Unmatched ENDMDL");inside=false;continue;}
            if(!Set.of("ATOM","HETATM").contains(type))continue;
            if(++total>maximum)throw new IOException("Complete PDB source across all states exceeds atom bound");
            if(explicit && !inside)throw new IOException("PDB atoms outside explicit MODEL records");
            if(!explicit && models.isEmpty())models.add("implicit-1");
            if(ordinal!=selected)continue;
            if(line.length()<54)throw new IOException("Truncated PDB coordinate record");
            String element=part(line,76,78).trim();
            if(element.isEmpty())throw new IOException("Audited PDB loading requires an explicit element column; supply a documented structure with element identifiers");
            add(atoms,new Atom(intValue(part(line,6,11).trim()),part(line,12,16).trim(),part(line,17,20).trim(),
                    part(line,22,26).trim()+part(line,26,27).trim(),part(line,21,22).trim(),part(line,72,76).trim(),
                    part(line,16,17).trim(),element.toUpperCase(java.util.Locale.ROOT),type.equals("HETATM")?1:0,
                    new Point(decimal(part(line,30,38)),decimal(part(line,38,46)),decimal(part(line,46,54))),
                    optional(part(line,60,66),0),optional(part(line,54,60),1)),maximum);
        }
        if(inside)throw new IOException("Unterminated explicit PDB MODEL");
        return new Parsed(atoms,models,total);
    }
    private record Token(String value,boolean quoted) { }
    private static final class Tokens {
        private final String source;private int at;private Token pending;
        Tokens(String source){this.source=source;}
        Token peek()throws IOException {if(pending==null)pending=nextRaw();return pending;}
        Token next()throws IOException {Token token=peek();pending=null;return token;}
        private Token nextRaw()throws IOException {
            while(at<source.length()) {
                char c=source.charAt(at);
                if(Character.isWhitespace(c)){at++;continue;}
                if(c=='#'){while(at<source.length() && source.charAt(at)!='\n')at++;continue;}
                if(c==';' && (at==0 || source.charAt(at-1)=='\n')) {
                    int start=++at;
                    while(at<source.length()) {
                        if(source.charAt(at)==';' && source.charAt(at-1)=='\n'){String text=source.substring(start,at++);return new Token(text,true);}at++;
                    }
                    throw new IOException("Unterminated CIF multiline value");
                }
                if(c=='\'' || c=='\"') {
                    char quote=c;int start=++at;
                    while(at<source.length()) {
                        if(source.charAt(at)==quote && (at+1==source.length() || Character.isWhitespace(source.charAt(at+1)))) {
                            String text=source.substring(start,at++);return new Token(text,true);
                        }at++;
                    }
                    throw new IOException("Unterminated CIF quote");
                }
                int start=at;while(at<source.length() && !Character.isWhitespace(source.charAt(at)))at++;
                return new Token(source.substring(start,at),false);
            }
            return null;
        }
    }
    private static boolean boundary(Token token){if(token==null || token.quoted())return token==null;String s=token.value().toLowerCase(java.util.Locale.ROOT);return s.startsWith("_")||s.equals("loop_")||s.equals("stop_")||s.startsWith("data_")||s.startsWith("save_")||s.equals("global_");}
    private static Parsed cif(String source,int selected,String namespace,int maximum)throws IOException {
        if(!Set.of("author","label").contains(namespace))throw new IOException("Explicit CIF author|label namespace required");
        var tokens=new Tokens(source);var atoms=new ArrayList<Atom>();var models=new LinkedHashMap<String,Integer>();boolean found=false;int total=0;Token token;
        while((token=tokens.next())!=null) {
            if(token.quoted() || !token.value().equalsIgnoreCase("loop_"))continue;
            var headers=new ArrayList<String>();
            while(tokens.peek()!=null && !tokens.peek().quoted() && tokens.peek().value().startsWith("_"))headers.add(tokens.next().value().toLowerCase(java.util.Locale.ROOT));
            if(headers.isEmpty() || new HashSet<>(headers).size()!=headers.size())throw new IOException("Empty/duplicate CIF loop headers");
            boolean atomSite=headers.stream().anyMatch(s->s.startsWith("_atom_site."));
            if(atomSite) {
                if(found || headers.stream().anyMatch(s->!s.startsWith("_atom_site.")))throw new IOException("Multiple/mixed atom_site loops unsupported; supply one explicit coordinate block");found=true;
            }
            while(!boundary(tokens.peek())) {
                var row=new LinkedHashMap<String,String>();
                for(String header:headers) {
                    if(boundary(tokens.peek()))throw new IOException("Incomplete CIF loop row");
                    String value=tokens.next().value();if(atomSite)row.put(header.substring(11),value);
                }
                if(!atomSite)continue;
                if(++total>maximum)throw new IOException("Complete CIF source across all states exceeds atom bound");
                String label=value(row,"pdbx_pdb_model_num","1");int ordinal=models.computeIfAbsent(label,k->models.size()+1);
                if(ordinal!=selected)continue;
                String prefix=namespace.equals("author")?"auth_":"label_";
                String chain=value(row,row.containsKey(prefix+"asym_id")?prefix+"asym_id":"label_asym_id","");
                String resi=identity(row,prefix,"seq_id");
                String group=required(row,"group_pdb");if(!Set.of("ATOM","HETATM").contains(group))throw new IOException("Unknown CIF atom group");
                add(atoms,new Atom(intValue(required(row,"id")),identity(row,prefix,"atom_id"),identity(row,prefix,"comp_id"),
                        resi+value(row,"pdbx_pdb_ins_code",""),chain,value(row,"label_asym_id",""),value(row,"label_alt_id",""),
                        required(row,"type_symbol").toUpperCase(java.util.Locale.ROOT),group.equals("HETATM")?1:0,
                        new Point(decimal(required(row,"cartn_x")),decimal(required(row,"cartn_y")),decimal(required(row,"cartn_z"))),
                        optional(value(row,"b_iso_or_equiv","0"),0),optional(value(row,"occupancy","1"),1)),maximum);
            }
        }
        if(!found)throw new IOException("No CIF atom_site coordinate loop");
        return new Parsed(atoms,new ArrayList<>(models.keySet()),total);
    }
    private static void add(List<Atom> atoms,Atom atom,int maximum)throws IOException {
        if(atom.name().isEmpty() || atom.resn().isEmpty() || atom.resi().isEmpty() || atom.element().isEmpty())throw new IOException("Missing atom identity field");
        for(String value:List.of(atom.name(),atom.resn(),atom.resi(),atom.chain(),atom.segi(),atom.alt(),atom.element()))
            if(value.length()>256 || value.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid atom identity text");
        if(!Double.isFinite(atom.b()) || !Double.isFinite(atom.q()))throw new IOException("Nonfinite B factor/occupancy");
        atoms.add(atom);if(atoms.size()>maximum)throw new IOException("Selected source state exceeds atom bound");
    }
    private static String part(String s,int start,int end){return start>=s.length()?"":s.substring(start,Math.min(end,s.length()));}
    private static String value(Map<String,String> row,String key,String fallback){String value=row.get(key);return value==null||value.equals(".")||value.equals("?")?fallback:value;}
    private static String required(Map<String,String> row,String key)throws IOException {String value=value(row,key,"");if(value.isEmpty())throw new IOException("Missing CIF atom field "+key+"; use a complete declared namespace (author or label)");return value;}
    // PyMOL's own CIF writer omits auth atom/residue tags. Prefer the declared namespace;
    // use label identifiers only when the preferred COLUMN is absent, never a missing cell.
    private static String identity(Map<String,String> row,String prefix,String name)throws IOException {return required(row,row.containsKey(prefix+name)?prefix+name:"label_"+name);}
    private static int intValue(String text)throws IOException {try{return Integer.parseInt(text);}catch(NumberFormatException failure){throw new IOException("Noninteger atom serial/identifier; decimal identifiers are required",failure);}}
    private static double decimal(String text)throws IOException {try{double value=Double.parseDouble(text.trim());if(!Double.isFinite(value))throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("Invalid finite structure number",failure);}}
    private static double optional(String text,double fallback)throws IOException {return text.isBlank()?fallback:decimal(text);}
}

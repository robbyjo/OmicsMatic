/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jlinalg.protein.MolecularGeometry.Point;

/** Whitespace PQR coordinates (Angstrom), charges (electrons) and radii (Angstrom).
 * No protonation, force-field assignment, atom repair or chemical inference is performed.
 */
public final class PqrStructure {
    /** An explicit original atom; zero radii are valid, including charged PARSE hydrogens. */
    public record Atom(boolean hetero,int number,String name,String residue,String chain,int residueNumber,String insertionCode,
            Point coordinate,double charge,double radius) {
        /** Compatibility constructor for PQR residues without an insertion code. */
        public Atom(boolean hetero,int number,String name,String residue,String chain,int residueNumber,Point coordinate,double charge,double radius) {
            this(hetero,number,name,residue,chain,residueNumber,"",coordinate,charge,radius);
        }
        public Atom {
            if(coordinate==null || !Double.isFinite(charge) || Math.abs(charge)>1e6 || !Double.isFinite(radius) || radius<0 || radius>1e6)
                throw new IllegalArgumentException("PQR requires finite coordinates,abs(charge)<=1e6 and radius in[0,1e6];zero radius is permitted");
            for(String value:List.of(name,residue,chain))if(value.length()>256 || value.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException("Invalid PQR identity token");
            if(name.isEmpty() || residue.isEmpty())throw new IllegalArgumentException("Missing PQR atom/residue name");
            if(insertionCode==null||!insertionCode.matches("[A-Za-z]?"))throw new IllegalArgumentException("PQR insertion code must be empty or one ASCII letter");
        }
        /** Chemical identity without inferring elements or alternate conformers. */
        public String key(){return String.join("\u001f",chain,Integer.toString(residueNumber)+insertionCode,residue,name);}
    }
    /** Bounding box of the supplied coordinates, without atomic-radius padding. */
    public record Bounds(Point minimum,Point maximum) {
        /** Midpoint of the coordinate bounding box; not a charge/mass centroid. */
        public Point center(){return new Point((minimum.x()+maximum.x())/2,(minimum.y()+maximum.y())/2,(minimum.z()+maximum.z())/2);}
    }
    private final List<Atom> atoms;
    private final List<String> remarks;
    private final double totalCharge;
    private final Bounds bounds;
    private PqrStructure(List<Atom> atoms,List<String> remarks) {
        this.atoms=List.copyOf(atoms);this.remarks=List.copyOf(remarks);double sum=0,compensation=0;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(Atom atom:atoms){double corrected=atom.charge()-compensation,next=sum+corrected;compensation=(next-sum)-corrected;sum=next;Point p=atom.coordinate();minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());}
        totalCharge=sum;bounds=new Bounds(new Point(minX,minY,minZ),new Point(maxX,maxY,maxZ));
    }
    /** Reads one charged model; does not close the caller's reader. Both optional-chain forms are accepted. */
    public static PqrStructure read(Reader source,int maximumAtoms)throws IOException {
        if(maximumAtoms<1 || maximumAtoms>1000000)throw new IllegalArgumentException("PQR atom bound must be1..1000000");
        BufferedReader input=source instanceof BufferedReader reader?reader:new BufferedReader(source);
        var atoms=new ArrayList<Atom>();var remarks=new ArrayList<String>();var serials=new HashSet<Integer>();var identities=new HashSet<String>();String line;long row=0;int remarkCharacters=0;
        while((line=input.readLine())!=null) {
            row++;if(line.length()>65536)throw new IOException("PQR line exceeds65536 characters at line"+row);
            String text=line.trim();if(text.isEmpty())continue;String[] fields=text.split("\\s+");String type=fields[0];
            if(Set.of("MODEL","ENDMDL").contains(type))throw new IOException("PQR model records are unsupported;select and export one explicitly charged model before analysis");
            if(type.equals("REMARK") || type.startsWith("#")){if((remarkCharacters+=text.length())>262144)throw new IOException("PQR remarks exceed256KiB");remarks.add(text);continue;}
            if(Set.of("TER","END").contains(type))continue;
            if(!Set.of("ATOM","HETATM").contains(type))throw new IOException("Unexpected PQR record at line"+row+":"+type);
            if(fields.length!=10 && fields.length!=11)throw new IOException("Require10 PQR tokens without chain or11 with chain at line"+row);
            int offset=fields.length==11?1:0;
            try {
                var residue=java.util.regex.Pattern.compile("([+-]?[0-9]+)([A-Za-z]?)").matcher(fields[4+offset]);
                if(!residue.matches())throw new IllegalArgumentException("PQR residue identifier requires an integer and optional single-letter insertion code");
                Atom atom=new Atom(type.equals("HETATM"),Integer.parseInt(fields[1]),fields[2],fields[3],offset==1?fields[4]:"",Integer.parseInt(residue.group(1)),residue.group(2),
                        new Point(decimal(fields[5+offset]),decimal(fields[6+offset]),decimal(fields[7+offset])),decimal(fields[8+offset]),decimal(fields[9+offset]));
                if(!serials.add(atom.number()) || !identities.add(atom.key()))throw new IOException("Duplicate PQR atom serial/chemical identity at line"+row);
                atoms.add(atom);if(atoms.size()>maximumAtoms)throw new IOException("PQR exceeds declared maximum atom count");
            }catch(IllegalArgumentException failure){throw new IOException("Invalid PQR atom at line"+row+":"+failure.getMessage(),failure);}
        }
        if(atoms.isEmpty())throw new IOException("PQR contains no charged atom records");
        return new PqrStructure(atoms,remarks);
    }
    private static double decimal(String text){if(!text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("Require decimal PQR numbers");double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite PQR number");return value;}
    /** Atoms in original file order. */
    public List<Atom> atoms(){return atoms;}
    /** Retained bounded comments; these do not establish a force field or protonation policy. */
    public List<String> remarks(){return remarks;}
    /** Compensated sum in elementary-charge units; no integer-charge assumption. */
    public double totalCharge(){return totalCharge;}
    /** Coordinate bounding box, without padding or radii. */
    public Bounds bounds(){return bounds;}
    /** Number of explicit zero-radius atoms; they are not silently discarded. */
    public long zeroRadiusAtoms(){return atoms.stream().filter(atom->atom.radius()==0).count();}
}

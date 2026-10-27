/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jlinalg.protein.MolecularGeometry.Point;

/** Immutable regular 3D scalar OpenDX grid, with z varying fastest.
 * Values have caller-declared units. Reading/sampling a grid does not solve electrostatics.
 * Supports APBS-style ASCII scalar arrays, including general nonsingular affine delta vectors.
 */
public final class OpenDxGrid {
    private static final int MAXIMUM_POINTS=67108864;
    private static final String ID="(?:\"[^\"\\r\\n]+\"|[^\\s\"]+)";
    private static final Pattern POSITIONS=Pattern.compile("object\\s+("+ID+")\\s+class\\s+gridpositions\\s+counts\\s+([0-9]+)\\s+([0-9]+)\\s+([0-9]+)");
    private static final Pattern CONNECTIONS=Pattern.compile("object\\s+("+ID+")\\s+class\\s+gridconnections\\s+counts\\s+([0-9]+)\\s+([0-9]+)\\s+([0-9]+)");
    private static final Pattern ARRAY=Pattern.compile("object\\s+("+ID+")\\s+class\\s+array\\s+type\\s+(double|float)\\s+rank\\s+0\\s+items\\s+([0-9]+)\\s+data\\s+follows(?:\\s+(.*))?");
    private static final Pattern FIELD=Pattern.compile("object\\s+("+ID+")\\s+class\\s+field");
    private static final Pattern COMPONENT=Pattern.compile("component\\s+\"(positions|connections|data)\"\\s+value\\s+("+ID+")");
    private final int nx,ny,nz;
    private final Point origin;
    private final Point[] deltas;
    private final double[][] inverse;
    private final double[] values;
    private final List<String> comments;
    private final String scalarType;
    /** Explicit treatment of points outside the closed grid parallelepiped. No extrapolation. */
    public enum Outside { ERROR, MISSING }
    /** Dimensionless affine indices; these are not bounded world coordinates. */
    public record Index(double x,double y,double z) {
        public Index {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Nonfinite fractional grid index;unsupported geometry/sample scaling");}
    }
    /** Missing samples have value NaN and inside=false; finite in-grid values are never coerced to zero. */
    public record Sample(boolean inside,double value,Index fractionalIndex,boolean roundoffAdjusted) { }
    /** Arithmetic statistics over nodes, not a spatial integral or electrostatic energy. */
    public record Statistics(int gridPoints,double minimum,double maximum,double mean,double populationStandardDeviation) { }

    private OpenDxGrid(int nx,int ny,int nz,Point origin,Point[] deltas,double[] values,List<String> comments,String scalarType) {
        this.nx=nx;this.ny=ny;this.nz=nz;this.origin=origin;this.deltas=deltas.clone();this.values=values;this.comments=List.copyOf(comments);this.scalarType=scalarType;
        inverse=invert(deltas);
        for(int i:new int[]{0,nx-1})for(int j:new int[]{0,ny-1})for(int k:new int[]{0,nz-1})coordinate(i,j,k);
    }
    /** Creates a grid with defensive copies; input order is (x*ny+y)*nz+z. */
    public static OpenDxGrid of(int nx,int ny,int nz,Point origin,Point[] deltas,double[] values) {
        int points=points(nx,ny,nz,MAXIMUM_POINTS);
        if(origin==null || deltas==null || deltas.length!=3 || values==null || values.length!=points)throw new IllegalArgumentException("Grid geometry/data length mismatch");
        for(Point delta:deltas)if(delta==null)throw new IllegalArgumentException("Missing grid delta");
        double[] copy=values.clone();for(double value:copy)scalar(value);return new OpenDxGrid(nx,ny,nz,origin,deltas,copy,List.of(),"double");
    }
    /** Reads one complete APBS-style regular scalar field; does not close the caller's reader.
     * Rejects incomplete/extra arrays, mismatched counts/references, nonfinite numbers and excessive data.
     */
    public static OpenDxGrid read(Reader source,int maximumGridPoints)throws IOException {
        if(maximumGridPoints<8 || maximumGridPoints>MAXIMUM_POINTS)throw new IllegalArgumentException("Grid point bound must be8..67108864");
        BufferedReader input=source instanceof BufferedReader reader?reader:new BufferedReader(source);Point origin=null;var deltas=new ArrayList<Point>();var comments=new ArrayList<String>();
        int nx=0,ny=0,nz=0,total=0,filled=0;String positionId=null,connectionId=null,arrayId=null,type=null;double[] data=null;boolean field=false,dependency=false,ended=false;Set<String> components=new HashSet<>();String line;long row=0;int commentCharacters=0;
        try {
            while((line=input.readLine())!=null) {
                row++;if(line.length()>65536)throw new IOException("OpenDX line exceeds65536 characters");String text=line.trim();
                if(text.isEmpty())continue;
                if(text.startsWith("#")){if((commentCharacters+=text.length())>262144)throw new IOException("OpenDX comments exceed256KiB");comments.add(text);continue;}
                if(ended)throw new IOException("Content follows OpenDX end");
                if(data!=null && filled<total){filled=values(text,data,filled,type.equals("float"));continue;}
                var positions=POSITIONS.matcher(text);var connections=CONNECTIONS.matcher(text);var array=ARRAY.matcher(text);var fieldMatch=FIELD.matcher(text);var component=COMPONENT.matcher(text);
                if(positions.matches()) {
                    if(positionId!=null || data!=null)throw new IOException("Multiple/misordered OpenDX position objects");positionId=identifier(positions.group(1));nx=Integer.parseInt(positions.group(2));ny=Integer.parseInt(positions.group(3));nz=Integer.parseInt(positions.group(4));total=points(nx,ny,nz,maximumGridPoints);
                }else if(text.startsWith("origin") && text.length()>6 && Character.isWhitespace(text.charAt(6))) {
                    if(positionId==null || origin!=null || connectionId!=null)throw new IOException("Duplicate/misordered OpenDX origin");origin=vector(text.substring(6));
                }else if(text.startsWith("delta") && text.length()>5 && Character.isWhitespace(text.charAt(5))) {
                    if(positionId==null || deltas.size()>=3 || connectionId!=null)throw new IOException("Duplicate/misordered OpenDX delta");deltas.add(vector(text.substring(5)));
                }else if(connections.matches()) {
                    if(positionId==null || origin==null || deltas.size()!=3 || connectionId!=null || data!=null)throw new IOException("Missing/misordered OpenDX geometry");
                    if(nx!=Integer.parseInt(connections.group(2)) || ny!=Integer.parseInt(connections.group(3)) || nz!=Integer.parseInt(connections.group(4)))throw new IOException("OpenDX connection/position counts differ");connectionId=identifier(connections.group(1));if(connectionId.equals(positionId))throw new IOException("Duplicate OpenDX object identifier");
                }else if(array.matches()) {
                    if(connectionId==null || data!=null || Long.parseLong(array.group(3))!=total)throw new IOException("OpenDX data count/array differs from geometry");
                    arrayId=identifier(array.group(1));type=array.group(2);if(Set.of(positionId,connectionId).contains(arrayId))throw new IOException("Duplicate OpenDX array identifier");data=new double[total];if(array.group(4)!=null)filled=values(array.group(4),data,0,type.equals("float"));
                }else if(text.equals("attribute \"dep\" string \"positions\"")) {
                    if(data==null || filled!=total || dependency)throw new IOException("Unexpected OpenDX dependency attribute");dependency=true;
                }else if(fieldMatch.matches()) {
                    if(data==null || filled!=total || field || Set.of(positionId,connectionId,arrayId).contains(identifier(fieldMatch.group(1))))throw new IOException("Multiple/misordered/duplicate OpenDX fields");field=true;
                }else if(component.matches()) {
                    String name=component.group(1),id=identifier(component.group(2));if(!field || !components.add(name))throw new IOException("Duplicate/misordered OpenDX field component");
                    String expected=switch(name){case "positions"->positionId;case "connections"->connectionId;default->arrayId;};if(!id.equals(expected))throw new IOException("OpenDX component references a different object");
                }else if(text.equals("end")) {
                    if(components.size()!=3)throw new IOException("OpenDX end precedes complete field");ended=true;
                }else throw new IOException("Unsupported/extra OpenDX content at line"+row);
            }
            if(data==null || filled!=total || !field || !dependency || components.size()!=3)throw new IOException("Truncated OpenDX scalar field or missing node dependency");
            return new OpenDxGrid(nx,ny,nz,origin,deltas.toArray(Point[]::new),data,comments,type);
        }catch(IllegalArgumentException|ArithmeticException failure){throw new IOException("Invalid OpenDX geometry/number at line"+row+":"+failure.getMessage(),failure);}
    }
    private static int values(String text,double[] data,int at,boolean floatSource)throws IOException {for(String token:text.split("\\s+")){if(at==data.length)throw new IOException("Extra OpenDX scalar values");double value=decimal(token);if(floatSource)value=(float)value;scalar(value);data[at++]=value;}return at;}
    private static String identifier(String text){return text.startsWith("\"")?text.substring(1,text.length()-1):text;}
    private static Point vector(String text){String[] fields=text.trim().split("\\s+");if(fields.length!=3)throw new IllegalArgumentException("Grid vector needs three coordinates");return new Point(decimal(fields[0]),decimal(fields[1]),decimal(fields[2]));}
    private static double decimal(String text){if(!text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("Invalid/nonfinite OpenDX numeric token:"+text.substring(0,Math.min(64,text.length())));double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite OpenDX number");return value;}
    private static void scalar(double value){if(!Double.isFinite(value) || Math.abs(value)>1e100)throw new IllegalArgumentException("Grid scalar must be finite with abs<=1e100");}
    private static int points(int nx,int ny,int nz,int maximum){if(nx<2 || ny<2 || nz<2)throw new IllegalArgumentException("Require at least two grid nodes per axis");long size=Math.multiplyExact(Math.multiplyExact((long)nx,ny),nz);if(size>maximum)throw new IllegalArgumentException("Grid exceeds declared point bound");return (int)size;}
    private static double[][] invert(Point[] d) {
        double a=d[0].x(),b=d[1].x(),c=d[2].x(),e=d[0].y(),f=d[1].y(),g=d[2].y(),h=d[0].z(),i=d[1].z(),j=d[2].z();
        double determinant=a*(f*j-g*i)-b*(e*j-g*h)+c*(e*i-f*h);
        double scale=Math.hypot(Math.hypot(a,e),h)*Math.hypot(Math.hypot(b,f),i)*Math.hypot(Math.hypot(c,g),j);
        if(!Double.isFinite(determinant) || scale==0 || Math.abs(determinant)<=1e-12*scale)throw new IllegalArgumentException("Singular or nearly coplanar grid delta vectors");
        double[][] inverse={{(f*j-g*i)/determinant,(c*i-b*j)/determinant,(b*g-c*f)/determinant},{(g*h-e*j)/determinant,(a*j-c*h)/determinant,(c*e-a*g)/determinant},{(e*i-f*h)/determinant,(b*h-a*i)/determinant,(a*f-b*e)/determinant}};
        for(double[] row:inverse)for(double value:row)if(!Double.isFinite(value))throw new IllegalArgumentException("Grid inverse is outside finite numeric range");return inverse;
    }
    /** Number of x-axis nodes. */
    public int nx(){return nx;}
    /** Number of y-axis nodes. */
    public int ny(){return ny;}
    /** Number of z-axis nodes. */
    public int nz(){return nz;}
    /** Number of scalar nodes, not volume cells. */
    public int gridPoints(){return values.length;}
    /** World coordinate of node0,0,0. */
    public Point origin(){return origin;}
    /** Three affine increment vectors, defensively copied. */
    public Point[] deltas(){return deltas.clone();}
    /** Retained bounded comments; units are still a caller declaration. */
    public List<String> comments(){return comments;}
    /** Declared ASCII array storage type; float values were rounded to binary32 before promotion. */
    public String scalarType(){return scalarType;}
    /** Explicit positive length-unit conversion. Scalar values are unchanged and remain immutable. */
    public OpenDxGrid scaleCoordinates(double factor) {
        if(!Double.isFinite(factor) || factor<=0)throw new IllegalArgumentException("Length multiplier must be positive and finite");
        Point[] scaled=new Point[3];for(int i=0;i<3;i++)scaled[i]=new Point(deltas[i].x()*factor,deltas[i].y()*factor,deltas[i].z()*factor);
        return new OpenDxGrid(nx,ny,nz,new Point(origin.x()*factor,origin.y()*factor,origin.z()*factor),scaled,values,comments,scalarType);
    }
    /** Scalar at a node; z varies fastest in serialized order. */
    public double value(int x,int y,int z){checkIndex(x,y,z);return values[(x*ny+y)*nz+z];}
    private void checkIndex(int x,int y,int z){if(x<0 || x>=nx || y<0 || y>=ny || z<0 || z>=nz)throw new IndexOutOfBoundsException("Grid node outside declared counts");}
    /** World coordinates in the same length units as origin and deltas. */
    public Point coordinate(int x,int y,int z){checkIndex(x,y,z);return new Point(origin.x()+x*deltas[0].x()+y*deltas[1].x()+z*deltas[2].x(),origin.y()+x*deltas[0].y()+y*deltas[1].y()+z*deltas[2].y(),origin.z()+x*deltas[0].z()+y*deltas[1].z()+z*deltas[2].z());}
    /** Trilinear sampling in affine grid coordinates. Bounds are closed; no extrapolation.
     * Clamping is limited to at most32 ulps of index roundoff and is explicitly reported.
     */
    public Sample sample(Point point,Outside outside) {
        if(point==null || outside==null)throw new IllegalArgumentException("Point and explicit outside policy required");
        double[] difference={point.x()-origin.x(),point.y()-origin.y(),point.z()-origin.z()},fractional=new double[3];int[] sizes={nx,ny,nz};boolean adjusted=false;
        for(int axis=0;axis<3;axis++)for(int column=0;column<3;column++)fractional[axis]+=inverse[axis][column]*difference[column];
        Index raw=new Index(fractional[0],fractional[1],fractional[2]);
        for(int axis=0;axis<3;axis++) {
            double index=fractional[axis],maximum=sizes[axis]-1,tolerance=32*Math.ulp(Math.max(1,Math.max(Math.abs(index),maximum)));
            if(index< -tolerance || index>maximum+tolerance){if(outside==Outside.ERROR)throw new IllegalArgumentException("Point lies outside the closed grid;no extrapolation");return new Sample(false,Double.NaN,raw,false);}
            double bounded=Math.max(0,Math.min(maximum,index));adjusted|=bounded!=index;fractional[axis]=bounded;
        }
        int x=Math.min(nx-2,(int)Math.floor(fractional[0])),y=Math.min(ny-2,(int)Math.floor(fractional[1])),z=Math.min(nz-2,(int)Math.floor(fractional[2]));
        double fx=fractional[0]-x,fy=fractional[1]-y,fz=fractional[2]-z,sum=0;
        for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++)for(int dz=0;dz<2;dz++)sum+=(dx==0?1-fx:fx)*(dy==0?1-fy:fy)*(dz==0?1-fz:fz)*value(x+dx,y+dy,z+dz);
        return new Sample(true,sum,raw,adjusted);
    }
    /** Welford node statistics. A zero/constant grid is a valid result. */
    public Statistics statistics(){double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY,mean=0,m2=0;int count=0;for(double value:values){minimum=Math.min(minimum,value);maximum=Math.max(maximum,value);double delta=value-mean;mean+=delta/++count;m2+=delta*(value-mean);}return new Statistics(count,minimum,maximum,mean,Math.sqrt(Math.max(0,m2/count)));}
}

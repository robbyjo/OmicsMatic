/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Workspace interpretation and ellipse polygon conversion informed by FlowKit 1.3.2,
 * Copyright (c) 2018 Scott White, BSD-3-Clause. See META-INF/LICENSE-FlowKit.txt.
 * The biex LUT generator is not ported or distributed by this implementation.
 */
package org.jlinalg.cytometry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Sample-local FlowJo 10 WSP (format 20.0) import using explicit FlowKit-1.3.2 interpretation. */
public final class FlowJoWorkspace {
    public static final String G = "http://www.isac-net.org/std/Gating-ML/v2.0/gating";
    public static final String D = "http://www.isac-net.org/std/Gating-ML/v2.0/datatypes";
    public static final String T = "http://www.isac-net.org/std/Gating-ML/v2.0/transformations";
    public static final class UnsupportedDefinitionException extends IOException {
        private static final long serialVersionUID = 1L;
        public UnsupportedDefinitionException(String message) { super(message+"; complete dependent sample analysis skipped"); }
    }
    public record Limits(long bytes, int samples, int gates, int depth, int vertices, int xmlNodes) {
        public Limits {
            if (bytes < 1 || bytes > 67108864 || samples < 1 || samples > 10000 || gates < 1 || gates > 10000 || depth < 1 || depth > 128 || vertices < 3 || vertices > 100000 || xmlNodes < 1 || xmlNodes > 1000000)
                throw new IllegalArgumentException("Declare complete bounded workspace capacities");
        }
        public static Limits defaults() { return new Limits(16777216, 4096, 4096, 128, 100000, 500000); }
    }
    public record Sample(String id, String name, String uri, String savedCount) { }
    public record TransformDefinition(String parameter, String type, Map<String,String> attributes) {
        public TransformDefinition { attributes = Map.copyOf(attributes); }
    }
    /** Calibration values must be explicitly supplied and tied to the exact saved transform attributes. */
    public record Calibration(TransformDefinition definition, CytometryLookupTable table, String provenance) {
        public Calibration { Objects.requireNonNull(definition); Objects.requireNonNull(table); literal(provenance); }
    }
    public record Gate(List<String> path, String type, String savedCount, boolean complement) {
        public Gate { path = List.copyOf(path); }
    }
    private record Dim(int column, boolean compensated, CytometryTransforms.Scale transform) { }
    private record BoundGate(Gate info, int parent, Dim[] dimensions, CytometryGates.Region region, String booleanOp, int[] dependencies) { }
    private record Spectrum(String prefix, String suffix, CytometryCompensation matrix, int[] columns) { }
    private final Limits limits;
    private final String version;
    private final Map<String,Element> elements;
    private final List<Sample> samples;
    private FlowJoWorkspace(Document doc, Limits limits) throws IOException {
        this.limits = limits; Element root = doc.getDocumentElement();
        require(is(root,"","Workspace"),"Require a FlowJo Workspace root in the empty namespace");
        if (!root.getAttribute("version").equals("20.0") || !root.getAttribute("flowJoVersion").startsWith("10."))
            throw unsupported("Supported profile requires explicit FlowJo 10.x / WSP format 20.0; supplied "+root.getAttribute("flowJoVersion")+" / "+root.getAttribute("version"));
        version = root.getAttribute("flowJoVersion"); elements = new LinkedHashMap<>(); var list = new ArrayList<Sample>();
        for (Element sample : children(one(root,"","SampleList"))) {
            require(is(sample,"","Sample"),"Unexpected SampleList child "+sample.getTagName()); Element node = one(sample,"","SampleNode"), data = one(sample,"","DataSet");
            String id = required(node,"","sampleID"), name = required(node,"","name");
            require(id.equals(required(data,"","sampleID")),"DataSet and SampleNode IDs differ; no repair");
            require(elements.putIfAbsent(id,sample) == null,"Duplicate workspace sampleID "+id);
            list.add(new Sample(id,name,data.getAttribute("uri"),node.hasAttribute("count") ? node.getAttribute("count") : null));
            require(list.size() <= limits.samples(),"Complete sample list exceeds declared capacity");
        }
        require(!list.isEmpty(),"Workspace has no samples"); samples = List.copyOf(list);
    }
    public static FlowJoWorkspace read(Path path, Limits limits) throws IOException {
        Objects.requireNonNull(limits); require(Files.size(path) <= limits.bytes(),"Complete workspace exceeds declared byte capacity");
        try (InputStream input = Files.newInputStream(path)) {
            var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder = factory.newDocumentBuilder(); builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
                @Override public void fatalError(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
            });
            byte[] bytes=input.readNBytes(Math.toIntExact(limits.bytes()+1)); require(bytes.length<=limits.bytes(),"Complete workspace grew beyond byte capacity");
            Document document = builder.parse(new java.io.ByteArrayInputStream(bytes)); int count = 0;
            var queue = new java.util.ArrayDeque<Node>(); queue.add(document.getDocumentElement());
            while (!queue.isEmpty()) { Node n = queue.remove(); require(++count <= limits.xmlNodes(),"Complete workspace XML exceeds declared node capacity"); for (Node c=n.getFirstChild(); c!=null; c=c.getNextSibling()) queue.add(c); }
            return new FlowJoWorkspace(document,limits);
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException error) { throw new IOException("Invalid or unsafe workspace XML",error); }
    }
    public String flowJoVersion() { return version; }
    public List<Sample> samples() { return samples; }
    public List<TransformDefinition> transforms(String sampleId) throws IOException {
        var definitions = new ArrayList<TransformDefinition>(); var names = new HashSet<String>();
        for (Element e : children(one(sample(sampleId),"","Transformations"))) {
            if (!T.equals(e.getNamespaceURI())) throw unsupported("Unrecognized transform namespace "+e.getTagName());
            String parameter = required(one(e,D,"parameter"),D,"name"); require(names.add(parameter),"Duplicate transform for exact channel "+parameter);
            var attrs = new LinkedHashMap<String,String>(); var source = e.getAttributes();
            for (int i=0; i<source.getLength(); i++) { Node a=source.item(i); if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI())) attrs.put(a.getNamespaceURI()==null ? a.getNodeName() : "{"+a.getNamespaceURI()+"}"+a.getLocalName(),a.getNodeValue()); }
            definitions.add(new TransformDefinition(parameter,e.getLocalName(),attrs));
        }
        return List.copyOf(definitions);
    }
    public Program compile(String sampleId, List<String> inputNames, List<Calibration> calibrations, double cutoff) throws IOException {
        require(inputNames.size() >= 1 && inputNames.size() <= 512 && new HashSet<>(inputNames).size()==inputNames.size(),"Require 1..512 unique explicitly mapped source channels");
        var tables = new HashMap<String,Calibration>();
        for (Calibration c : calibrations) require(tables.putIfAbsent(c.definition().parameter(),c)==null,"Duplicate calibration channel");
        var scales = new LinkedHashMap<String,CytometryTransforms.Scale>(); var notes = new ArrayList<String>();
        for (TransformDefinition definition : transforms(sampleId)) {
            CytometryTransforms.Scale scale;
            switch (definition.type()) {
                case "linear" -> {
                    if (definition.attributes().containsKey("gain")) notes.add("FlowKit-1.3.2 WSP linear interpretation for "+definition.parameter()+": maxRange/minRange define Gating-ML T/A; saved display gain="+definition.attributes().get("gain")+" is retained, not applied. This does not replace or ignore FCS PnG preprocessing.");
                    scale = CytometryTransforms.linear(number(definition,"maxRange"),number(definition,"minRange"));
                }
                case "log" -> {
                    double offset=number(definition,"offset"), decades=number(definition,"decades"); require(offset>0 && decades>0,"WSP log needs positive offset and decades");
                    scale = log(offset,decades); notes.add("WSP log "+definition.parameter()+" intrinsically maps values below offset "+offset+" to zero; original measurements remain unchanged.");
                }
                case "logicle" -> scale = CytometryTransforms.logicle(number(definition,"T"),number(definition,"W"),number(definition,"M"),number(definition,"A"));
                case "fasinh" -> {
                    scale = CytometryTransforms.arcsinh(number(definition,"T"),number(definition,"M"),number(definition,"A"));
                    notes.add("FlowKit-1.3.2 fasinh interpretation for "+definition.parameter()+": Gating-ML T/M/A mapping; saved length/maxRange/W retained in definition, not used by this profile. No whole-vendor equivalence claim.");
                }
                case "biex" -> {
                    if (!attribute(definition,"length").equals("256")) throw unsupported("WSP biex length other than literal 256");
                    Calibration calibration=tables.remove(definition.parameter());
                    if (calibration==null) throw unsupported("Missing exact explicit 4097-pair biex calibration for "+definition.parameter()+". Supply a saved/exported or independently generated table with matching transform attributes and provenance");
                    require(definition.equals(calibration.definition()),"Calibration attributes do not exactly match saved WSP transform for "+definition.parameter());
                    require(calibration.table().size()==4097,"WSP biex profile requires all 4097 knots");
                    for (int i=0; i<4097; i++) require(calibration.table().output(i)==i,"WSP biex output knots must equal exact integers 0..4096");
                    scale = calibration.table(); notes.add("Explicit biex table for "+definition.parameter()+": "+calibration.provenance()+". Linear interpolation; intrinsic endpoint saturation; no biex generator or logicle substitution.");
                }
                default -> throw unsupported("Unsupported WSP transform "+definition.type());
            }
            scales.put(definition.parameter(),scale);
        }
        require(tables.isEmpty(),"Unused calibration entries are not admitted"); Spectrum spectrum=spectrum(sample(sampleId),inputNames,cutoff);
        var drafts = new ArrayList<Draft>(); Element root=one(sample(sampleId),"","SampleNode"); Element sub=optional(root,"","Subpopulations");
        if (sub!=null) populations(sub,List.of(),-1,drafts,inputNames,scales,spectrum,notes,0);
        var index = new HashMap<String,Integer>();
        for (int i=0; i<drafts.size(); i++) require(index.putIfAbsent(String.join("/",drafts.get(i).info.path()),i)==null,"Gate paths have an ambiguous slash serialization; supply an unambiguous workspace");
        var gates = new ArrayList<BoundGate>();
        for (Draft d : drafts) {
            int[] deps = new int[d.dependencies.size()];
            for (int j=0; j<deps.length; j++) { Integer dep=index.get(d.dependencies.get(j)); require(dep!=null,"Boolean dependency absent from complete sample gate tree: "+d.dependencies.get(j)); deps[j]=dep; }
            gates.add(new BoundGate(d.info,d.parent,d.dimensions,d.region,d.booleanOp,deps));
        }
        int[] order=order(gates);
        notes.add("All sample-local gates are compiled; group template gates are not substituted. Saved counts are annotations, not fitting targets. Rectangle [min,max); polygon boundary included; Booleans intersect their parent population. Every source event is retained.");
        return new Program(inputNames,gates,spectrum,notes,order);
    }
    private record Draft(Gate info, int parent, Dim[] dimensions, CytometryGates.Region region, String booleanOp, List<String> dependencies) { }
    private void populations(Element sub, List<String> parentPath, int parent, List<Draft> out, List<String> names, Map<String,CytometryTransforms.Scale> scales, Spectrum spectrum, List<String> notes, int depth) throws IOException {
        require(depth<limits.depth(),"Complete gate tree exceeds declared depth capacity");
        for (Element population : children(sub)) {
            String type=population.getLocalName();
            if (is(population,"","Statistic")) {
                require(optional(population,"","Subpopulations")==null && optional(population,"","Gate")==null,"Statistic with nested gates is not a supported metadata-only statistic");
                notes.add("NOT COMPUTED: saved Statistic metadata at "+String.join("/",parentPath)+" ("+population.getAttribute("name")+"). This profile returns complete memberships/counts only. Use a separately qualified statistic procedure on the declared compensated/measurement scale; saved statistics are not imported as new results.");
                continue;
            }
            if (!List.of("Population","AndNode","OrNode","NotNode").contains(type) || population.getNamespaceURI()!=null) throw unsupported("Unsupported population node "+population.getTagName());
            String name=required(population,"","name"); var path=new ArrayList<>(parentPath); path.add(name); int position=out.size(); require(position<limits.gates(),"Complete sample gate tree exceeds declared capacity");
            Dim[] dimensions=new Dim[0]; CytometryGates.Region region=null; boolean complement=false; String op=null; var dependencies=new ArrayList<String>();
            if (!type.equals("Population")) {
                op=type; for (Element dependent : children(one(population,"","Dependents"))) { require(is(dependent,"","Dependent"),"Unexpected Boolean dependent node"); dependencies.add(required(dependent,"","name")); }
                require(!dependencies.isEmpty() && (!type.equals("NotNode") || dependencies.size()==1),"Boolean gate has invalid complete arity");
            } else {
                List<Element> definitions=children(one(population,"","Gate")); require(definitions.size()==1,"Population must have exactly one gate definition"); Element gate=definitions.get(0); type=gate.getLocalName();
                if (!G.equals(gate.getNamespaceURI()) || !List.of("RectangleGate","PolygonGate","EllipsoidGate").contains(type)) throw unsupported("Unsupported gate definition "+gate.getTagName()+"; saved quadrant rectangles are supported");
                String inside=gate.hasAttribute("eventsInside") ? gate.getAttribute("eventsInside") : "1"; require(inside.equals("0")||inside.equals("1"),"eventsInside must be 0 or 1"); complement=inside.equals("0");
                List<Element> ds=selected(gate,G,"dimension"); require(!ds.isEmpty() && ds.size()<=128,"Require complete 1..128 gate dimensions"); dimensions=new Dim[ds.size()];
                for (int i=0; i<ds.size(); i++) {
                    Element dim=ds.get(i); require(!dim.hasAttributeNS(G,"transformation-ref")&&!dim.hasAttributeNS(G,"compensation-ref"),"Explicit Gating-ML references inside WSP dimensions require separate supported semantics");
                    List<Element> refs=children(dim); require(refs.size()==1 && is(refs.get(0),D,"fcs-dimension"),"Require one explicit FCS dimension per workspace dimension");
                    String channel=required(refs.get(0),D,"name");
                    if (spectrum!=null) { if (!spectrum.prefix.isEmpty() && channel.startsWith(spectrum.prefix)) channel=channel.substring(spectrum.prefix.length()); if (!spectrum.suffix.isEmpty() && channel.endsWith(spectrum.suffix)) channel=channel.substring(0,channel.length()-spectrum.suffix.length()); }
                    int column=names.indexOf(channel); if (column<0) throw unsupported("Exact workspace channel absent from explicitly mapped FCS axis: "+channel);
                    dimensions[i]=new Dim(column,spectrum!=null && spectrum.matrix.detectors().contains(channel),scales.get(channel));
                }
                if (type.equals("RectangleGate")) {
                    double[] lower=new double[ds.size()], upper=new double[ds.size()];
                    for (int i=0; i<ds.size(); i++) { lower[i]=ds.get(i).hasAttributeNS(G,"min") ? decimal(ds.get(i).getAttributeNS(G,"min")) : Double.NEGATIVE_INFINITY; upper[i]=ds.get(i).hasAttributeNS(G,"max") ? decimal(ds.get(i).getAttributeNS(G,"max")) : Double.POSITIVE_INFINITY; require(lower[i]<upper[i],"Unordered original rectangle bounds"); lower[i]=transformBound(dimensions[i],lower[i]); upper[i]=transformBound(dimensions[i],upper[i]); }
                    region=rectangle(lower,upper);
                } else {
                    require(dimensions.length==2,"Polygon/ellipse requires exactly two dimensions"); double[][] vertices;
                    if (type.equals("PolygonGate")) { vertices=vertices(gate); for (double[] v : vertices) for (int i=0; i<2; i++) if (dimensions[i].transform!=null) v[i]=dimensions[i].transform.apply(v[i]); }
                    else { vertices=ellipse(gate,dimensions); notes.add("Ellipse "+String.join("/",path)+" is an explicit 128-vertex FlowKit-style display-space polygon approximation, not an analytic vendor ellipse."); }
                    region=CytometryGates.polygon(vertices);
                }
            }
            out.add(new Draft(new Gate(path,type,population.hasAttribute("count") ? population.getAttribute("count") : null,complement),parent,dimensions,region,op,List.copyOf(dependencies)));
            Element children=optional(population,"","Subpopulations"); if (children!=null) populations(children,path,position,out,names,scales,spectrum,notes,depth+1);
        }
    }
    public static final class Program {
        private final List<String> names; private final List<BoundGate> gates; private final Spectrum spectrum; private final List<String> warnings; private final int[] order;
        private Program(List<String> names, List<BoundGate> gates, Spectrum spectrum, List<String> warnings, int[] order) { this.names=List.copyOf(names); this.gates=List.copyOf(gates); this.spectrum=spectrum; this.warnings=List.copyOf(warnings); this.order=order.clone(); }
        public List<Gate> gates() { return gates.stream().map(BoundGate::info).toList(); }
        public List<String> warnings() { return warnings; }
        public CytometryCompensation.Diagnostics compensationDiagnostics() { return spectrum==null ? null : spectrum.matrix.diagnostics(); }
        /** Input is one complete explicitly preprocessed FCS row; no compensation has yet been applied. */
        public boolean[] evaluate(double[] measurements) {
            if (measurements==null || measurements.length!=names.size()) throw new IllegalArgumentException("Require one complete preprocessed source row");
            for (double x : measurements) if (!Double.isFinite(x)) throw new IllegalArgumentException("Nonfinite event; complete analysis refused, no event dropped");
            double[] compensated=measurements.clone();
            if (spectrum!=null) { double[] selected=new double[spectrum.columns.length]; for (int i=0; i<selected.length; i++) selected[i]=measurements[spectrum.columns[i]]; double[] signal=spectrum.matrix.apply(selected); for (int i=0; i<signal.length; i++) compensated[spectrum.columns[i]]=signal[i]; }
            boolean[] result=new boolean[gates.size()];
            for (int i : order) evaluate(i,measurements,compensated,result);
            return result;
        }
        private void evaluate(int i, double[] raw, double[] compensated, boolean[] result) {
            BoundGate g=gates.get(i); boolean parent=g.parent<0 || result[g.parent]; boolean inside;
            if (g.booleanOp!=null) {
                inside=!g.booleanOp.equals("OrNode");
                for (int dependency : g.dependencies) { boolean b=result[dependency]; if (g.booleanOp.equals("OrNode")) inside|=b; else if (g.booleanOp.equals("NotNode")) inside=!b; else inside&=b; }
            } else {
                double[] point=new double[g.dimensions.length];
                for (int j=0; j<point.length; j++) { Dim d=g.dimensions[j]; double x=(d.compensated ? compensated : raw)[d.column]; point[j]=d.transform==null ? x : d.transform.apply(x); }
                inside=g.region.contains(point); if (g.info.complement()) inside=!inside;
            }
            result[i]=parent && inside;
        }
    }
    private Spectrum spectrum(Element sample, List<String> names, double cutoff) throws IOException {
        List<Element> matrices=selected(sample,T,"spilloverMatrix"); if (matrices.isEmpty()) return null;
        if (matrices.size()!=1) throw unsupported("Multiple sample compensation matrices need an explicit unambiguous supported export"); Element m=matrices.get(0);
        if (m.hasAttribute("spectral") && !m.getAttribute("spectral").equals("0")) throw unsupported("Spectral WSP compensation is outside this square-spectrum import profile");
        List<Element> parameters=selected(one(m,D,"parameters"),D,"parameter"); require(!parameters.isEmpty() && parameters.size()<=512,"Complete WSP spectrum exceeds capacity or has no parameters");
        var detectors=new ArrayList<String>(); int[] columns=new int[parameters.size()];
        for (int i=0; i<columns.length; i++) { String name=required(parameters.get(i),D,"name"); require(!detectors.contains(name),"Duplicate WSP spectrum detector"); detectors.add(name); columns[i]=names.indexOf(name); if (columns[i]<0) throw unsupported("WSP spectrum detector absent from exact mapped source axis: "+name); }
        List<Element> rows=selected(m,T,"spillover"); require(rows.size()==columns.length,"WSP spectrum must be complete and square"); double[][] values=new double[rows.size()][columns.length];
        for (int i=0; i<rows.size(); i++) { Element row=rows.get(i); if (row.hasAttributeNS(D,"parameter")) require(row.getAttributeNS(D,"parameter").equals(detectors.get(i)),"Spectrum row label/order differs; no inferred transpose or reordering"); List<Element> co=selected(row,T,"coefficient"); require(co.size()==columns.length,"Incomplete spectrum coefficient row"); for (int j=0; j<co.size(); j++) { if (co.get(j).hasAttributeNS(D,"parameter")) require(co.get(j).getAttributeNS(D,"parameter").equals(detectors.get(j)),"Spectrum column label/order differs"); values[i][j]=decimal(required(co.get(j),T,"value")); } }
        return new Spectrum(requiredAllowEmpty(m,"prefix"),requiredAllowEmpty(m,"suffix"),CytometryCompensation.spectrum(detectors,detectors,values,cutoff),columns);
    }
    private double[][] vertices(Element owner) throws IOException {
        List<Element> vs=selected(owner,G,"vertex"); require(vs.size()<=limits.vertices(),"Complete vertex set exceeds capacity"); double[][] out=new double[vs.size()][2];
        for (int i=0; i<out.length; i++) { List<Element> cs=selected(vs.get(i),G,"coordinate"); require(cs.size()==2,"Require exactly two vertex coordinates"); for (int j=0; j<2; j++) out[i][j]=decimal(required(cs.get(j),D,"value")); }
        return out;
    }
    private double[][] ellipse(Element gate, Dim[] dims) throws IOException {
        double[][] f=vertices(one(gate,G,"foci")), e=vertices(one(gate,G,"edge")); require(f.length==2 && e.length==4,"Ellipse needs exact two foci and four edge points");
        double cx=(f[0][0]+f[1][0])/512, cy=(f[0][1]+f[1][1])/512, dx=f[1][0]-f[0][0], dy=f[1][1]-f[0][1];
        require(dx!=0 || dy!=0,"Coincident ellipse foci do not resolve this imported ellipse profile"); double theta=Math.atan(dy/dx), c=Math.cos(theta), s=Math.sin(theta);
        double fx=(f[0][0]/256-cx)*c+(f[0][1]/256-cy)*s;
        double[] a=rotate(e[0][0]/256-cx,e[0][1]/256-cy,c,s), b=rotate(e[2][0]/256-cx,e[2][1]/256-cy,c,s);
        int axisA=Math.abs(a[0])>=Math.abs(a[1]) ? 0 : 1, axisB=Math.abs(b[0])>=Math.abs(b[1]) ? 0 : 1; require(axisA!=axisB,"Ellipse edge axes are unresolved");
        double major=Math.max(Math.abs(a[axisA]),Math.abs(b[axisB])), radicand=major*major-fx*fx; require(radicand>0 && Double.isFinite(radicand),"Ellipse foci and edges do not resolve positive axes; no absolute-value repair"); double minor=Math.sqrt(radicand); double[][] out=new double[128][2];
        for (int i=0; i<128; i++) { double angle=2*Math.PI*(i/128.0), x=(axisA==0 ? major : minor)*Math.cos(angle), y=(axisA==0 ? minor : major)*Math.sin(angle); out[i][0]=(x*c-y*s+cx)*(dims[0].transform instanceof CytometryLookupTable ? 4096 : 1); out[i][1]=(x*s+y*c+cy)*(dims[1].transform instanceof CytometryLookupTable ? 4096 : 1); }
        return out;
    }
    private static double[] rotate(double x, double y, double c, double s) { return new double[]{x*c+y*s,-x*s+y*c}; }
    private static CytometryTransforms.Scale log(double offset,double decades) { return new CytometryTransforms.Scale() {
        @Override public double apply(double x) { if (!Double.isFinite(x)) throw new IllegalArgumentException("Nonfinite WSP log input"); return (Math.log10(Math.max(x,offset))-Math.log10(offset))/decades; }
        @Override public double inverse(double x) { if (!Double.isFinite(x)) throw new IllegalArgumentException("Nonfinite WSP log inverse input"); double y=offset*Math.pow(10,decades*x); if (!Double.isFinite(y)) throw new ArithmeticException("WSP log inverse overflow"); return y; }
    }; }
    private static CytometryGates.Region rectangle(double[] lower,double[] upper) { return p -> { if (p.length!=lower.length) throw new IllegalArgumentException("Incomplete rectangle point"); for (int i=0; i<p.length; i++) { if (!Double.isFinite(p[i])) throw new IllegalArgumentException("Nonfinite rectangle point"); if (p[i]<lower[i] || p[i]>=upper[i]) return false; } return true; }; }
    private static double transformBound(Dim d, double x) { return d.transform==null || !Double.isFinite(x) ? x : d.transform.apply(x); }
    private static int[] order(List<BoundGate> gates) throws IOException {
        int n=gates.size(); int[] incoming=new int[n], order=new int[n]; var outgoing=new ArrayList<List<Integer>>(); for (int i=0; i<n; i++) outgoing.add(new ArrayList<>());
        for (int i=0; i<n; i++) { BoundGate g=gates.get(i); var dependencies=new HashSet<Integer>(); if (g.parent>=0) dependencies.add(g.parent); for (int d : g.dependencies) dependencies.add(d); incoming[i]=dependencies.size(); for (int d : dependencies) outgoing.get(d).add(i); }
        var ready=new java.util.ArrayDeque<Integer>(); for (int i=0; i<n; i++) if (incoming[i]==0) ready.add(i); int used=0;
        while (!ready.isEmpty()) { int i=ready.remove(); order[used++]=i; for (int child : outgoing.get(i)) if (--incoming[child]==0) ready.add(child); }
        require(used==n,"Cyclic parent/Boolean dependency in complete gate tree"); return order;
    }
    private Element sample(String id) throws IOException { Element e=elements.get(id); require(e!=null,"Exact sampleID absent from workspace: "+id); return e; }
    private static String requiredAllowEmpty(Element e,String a) throws IOException { require(e.hasAttribute(a),"Missing explicit "+a); return e.getAttribute(a); }
    private static String attribute(TransformDefinition d,String a) throws IOException { String v=d.attributes().get("{"+T+"}"+a); if (v==null) v=d.attributes().get(a); require(v!=null,"Missing transform attribute "+a); return v; }
    private static double number(TransformDefinition d,String a) throws IOException { return decimal(attribute(d,a)); }
    private static double decimal(String text) throws IOException { require(text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require finite decimal XML values without inferred trimming"); double v=Double.parseDouble(text); require(Double.isFinite(v),"Nonfinite XML number"); return v; }
    private static String required(Element e,String ns,String a) throws IOException { require(e.hasAttributeNS(ns.isEmpty()?null:ns,a),"Missing "+a+" in "+e.getTagName()); String v=e.getAttributeNS(ns.isEmpty()?null:ns,a); literal(v); return v; }
    private static void literal(String s) { if (s==null || s.isEmpty() || s.length()>32768 || s.chars().anyMatch(c->c<32 || c==127)) throw new IllegalArgumentException("Require complete literal identifiers/provenance without control characters"); }
    private static boolean is(Element e,String ns,String local) { return local.equals(e.getLocalName()) && ns.equals(e.getNamespaceURI()==null?"":e.getNamespaceURI()); }
    private static List<Element> children(Element e) { var out=new ArrayList<Element>(); for (Node c=e.getFirstChild(); c!=null; c=c.getNextSibling()) if (c instanceof Element element) out.add(element); return out; }
    private static List<Element> selected(Element e,String ns,String name) { return children(e).stream().filter(c->is(c,ns,name)).toList(); }
    private static Element optional(Element e,String ns,String name) throws IOException { List<Element> found=selected(e,ns,name); require(found.size()<=1,"Duplicate "+name+" elements"); return found.isEmpty()?null:found.get(0); }
    private static Element one(Element e,String ns,String name) throws IOException { Element found=optional(e,ns,name); require(found!=null,"Missing "+name+" element"); return found; }
    private static UnsupportedDefinitionException unsupported(String s) { return new UnsupportedDefinitionException(s); }
    private static void require(boolean b,String s) throws IOException { if (!b) throw new IOException(s); }
}

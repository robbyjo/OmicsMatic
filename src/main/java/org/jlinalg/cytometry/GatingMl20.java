/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Native Gating-ML 2.0 plan on explicitly supplied, already decoded FCS scale values. */
public final class GatingMl20 {
    public static final String GATING = "http://www.isac-net.org/std/Gating-ML/v2.0/gating";
    private static final String TYPES = "http://www.isac-net.org/std/Gating-ML/v2.0/datatypes";
    private static final String TRANSFORMS = "http://www.isac-net.org/std/Gating-ML/v2.0/transformations";
    private static final Map<String, String> SCHEMAS = Map.of(
        "DataTypes.v2.0.xsd", "11012965ef62aff56a237706dcf160c0a1ff1399f26630f0df667bff204128d6",
        "Gating-ML.v2.0.xsd", "81d97f0931d1cd3b235b92fb0b0ff956b7e8625d57c896df27c9ee4316d95dda",
        "Transformations.v2.0.xsd", "987f2d7b196417b2d03293f200c930ae74f912da43dc379dd57bc1bedf055229");
    public static final class UnsupportedDefinitionException extends IOException {
        private static final long serialVersionUID = 1L;
        public UnsupportedDefinitionException(String message) { super(message); }
    }
    public record Limits(long xmlBytes, int definitions, int dimensions, int polygonVertices) {
        public Limits {
            if (xmlBytes < 1 || xmlBytes > Integer.MAX_VALUE-8 || definitions < 1 || definitions > 65536 || dimensions < 1 || dimensions > 512 || polygonVertices < 3 || polygonVertices > 100000) throw new IllegalArgumentException("Invalid complete Gating-ML capacities");
        }
        public static Limits defaults() { return new Limits(16_777_216, 4096, 128, 100000); }
    }
    /** Caller must establish absence, availability, or unsupported original-file compensation explicitly. */
    public record FcsCompensation(CytometryCompensation matrix, boolean explicitlyAbsent, String unavailableReason) {
        public FcsCompensation {
            int choices = (matrix == null ? 0 : 1)+(explicitlyAbsent ? 1 : 0)+(unavailableReason == null ? 0 : 1);
            if (choices != 1 || unavailableReason != null && unavailableReason.isEmpty()) throw new IllegalArgumentException("Declare exactly one original-file compensation state");
            if (matrix != null && !matrix.detectors().equals(matrix.fluorochromes())) throw new IllegalArgumentException("FCS compensation preserves detector names/order");
        }
        public static FcsCompensation absent() { return new FcsCompensation(null, true, null); }
        public static FcsCompensation available(CytometryCompensation matrix) { return new FcsCompensation(Objects.requireNonNull(matrix), false, null); }
        public static FcsCompensation unavailable(String reason) { return new FcsCompensation(null, false, Objects.requireNonNull(reason)); }
    }
    public record Gate(String id, String parent, String type) { }
    private record Ratio(String x, String y, double a, double b, double c) { }
    private record Dimension(String source, boolean ratio, String compensation, String scale) { }
    private record Primitive(int[] dimensions, CytometryGates.Region region) { }
    private record BooleanRule(String operator, String[] references, boolean[] complements) { }
    private record Draft(Gate gate, Primitive primitive, BooleanRule logical) { }
    private record BoundValue(int compensation, int column) { }
    private record BoundDimension(BoundValue first, BoundValue second, Ratio ratio, CytometryTransforms.Scale scale) { }
    private record BoundCompensation(CytometryCompensation matrix, int[] inputColumns) { }
    private record BoundGate(Gate info, int parent, int[] dimensions, CytometryGates.Region region, String operator, int[] references, boolean[] complements) { }

    private final List<String> inputNames;
    private final List<Gate> gates;
    private final List<String> warnings;
    private final List<BoundDimension> dimensions;
    private final List<BoundCompensation> compensation;
    private final List<BoundGate> plan;
    private final int[] order;

    private GatingMl20(Builder builder) throws IOException {
        inputNames = builder.names; gates = builder.drafts.stream().map(Draft::gate).toList();
        var ids = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < gates.size(); i++) ids.put(gates.get(i).id(), i);
        var matrices = new ArrayList<BoundCompensation>(); var matrixIndices = new HashMap<String, Integer>();
        for (Dimension d : builder.dimensions.keySet()) if (!d.compensation().equals("uncompensated") && !matrixIndices.containsKey(d.compensation())) {
            CytometryCompensation matrix;
            if (d.compensation().equals("FCS")) {
                if (builder.fcs.unavailableReason() != null) throw new UnsupportedDefinitionException("Original-file compensation is unavailable: "+builder.fcs.unavailableReason()+"; dependent analysis skipped");
                matrix = builder.fcs.matrix();
                if (matrix == null) { builder.warnings.add("Gating-ML requests FCS compensation; the caller explicitly established no compensation description in the complete source file. The standard therefore applies none."); matrixIndices.put("FCS", -1); continue; }
            } else { matrix = builder.matrices.get(d.compensation()); if (matrix == null) throw new IOException("Unknown compensation reference: "+d.compensation()); }
            int[] columns = new int[matrix.detectors().size()];
            var diagnostic = matrix.diagnostics();
            if (diagnostic.rank() >= 0 && diagnostic.rank() < diagnostic.fluorochromes()) builder.warnings.add("Compensation "+d.compensation()+" is rank deficient at the declared SVD cutoff. The explicit OLS pseudoinverse is a minimum-norm solution; individual fluorochrome signals are not identifiable.");
            if (diagnostic.rank() < 0) builder.warnings.add("Compensation "+d.compensation()+" uses the supplied already-inverted matrix literally; its original spectrum rank is not established.");
            for (int i = 0; i < columns.length; i++) columns[i] = index(inputNames, matrix.detectors().get(i), "detector");
            matrixIndices.put(d.compensation(), matrices.size()); matrices.add(new BoundCompensation(matrix, columns));
        }
        compensation = List.copyOf(matrices); var bound = new ArrayList<BoundDimension>();
        for (Dimension d : builder.dimensions.keySet()) {
            int matrix = d.compensation().equals("uncompensated") ? -1 : matrixIndices.get(d.compensation());
            Ratio ratio = d.ratio() ? builder.ratios.get(d.source()) : null;
            if (d.ratio() && ratio == null) throw new IOException("New dimension does not reference a ratio: "+d.source());
            BoundValue first = resolveValue(matrix, d.compensation().equals("FCS"), ratio == null ? d.source() : ratio.x());
            BoundValue second = ratio == null ? null : resolveValue(matrix, d.compensation().equals("FCS"), ratio.y());
            CytometryTransforms.Scale scale = d.scale() == null ? null : builder.scales.get(d.scale());
            if (d.scale() != null && scale == null) throw new IOException("Unknown/non-scale transformation: "+d.scale());
            bound.add(new BoundDimension(first, second, ratio, scale));
        }
        dimensions = List.copyOf(bound); var nodes = new ArrayList<BoundGate>();
        for (Draft d : builder.drafts) {
            int parent = d.gate().parent() == null ? -1 : gateIndex(ids, d.gate().parent());
            if (d.primitive() != null) nodes.add(new BoundGate(d.gate(), parent, d.primitive().dimensions(), d.primitive().region(), null, new int[0], new boolean[0]));
            else {
                BooleanRule logic = d.logical(); int[] references = new int[logic.references().length];
                for (int i = 0; i < references.length; i++) references[i] = gateIndex(ids, logic.references()[i]);
                nodes.add(new BoundGate(d.gate(), parent, null, null, logic.operator(), references, logic.complements()));
            }
        }
        plan = List.copyOf(nodes); order = topological(nodes); warnings = List.copyOf(builder.warnings);
    }
    /** Full schema/semantic validation and binding before any event can be evaluated. */
    public static GatingMl20 read(Path xml, List<String> scaleValueNames, FcsCompensation fcsCompensation,
                                  double relativeSvdCutoff, Limits limits) throws IOException {
        Objects.requireNonNull(xml); Objects.requireNonNull(limits); Objects.requireNonNull(fcsCompensation);
        if (!Double.isFinite(relativeSvdCutoff) || relativeSvdCutoff < 0 || relativeSvdCutoff >= 1) throw new IllegalArgumentException("Declare an SVD cutoff in [0,1)");
        if (scaleValueNames == null || scaleValueNames.isEmpty() || scaleValueNames.size() > 65536 || new HashSet<>(scaleValueNames).size() != scaleValueNames.size()) throw new IllegalArgumentException("Require complete unique source scale-value names");
        List<String> names = List.copyOf(scaleValueNames);
        if (names.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("Empty source dimension name");
        long size = Files.size(xml); if (size < 1 || size > limits.xmlBytes()) throw new IOException("Complete Gating-ML file exceeds declared capacity or is empty");
        byte[] raw;
        try (var stream = Files.newInputStream(xml)) { raw = stream.readNBytes((int)size+1); }
        if (raw.length != size) throw new IOException("Gating-ML file changed during complete read");
        try {
            var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "128");
            var parser = factory.newDocumentBuilder(); parser.setErrorHandler(ERRORS);
            var document = parser.parse(new ByteArrayInputStream(raw)); Element root = document.getDocumentElement();
            if (!GATING.equals(root.getNamespaceURI()) || !root.getLocalName().equals("Gating-ML")) throw new UnsupportedDefinitionException("Require an explicit Gating-ML 2.0 document; no FlowJo workspace or edition substitution; dependent analysis skipped");
            exactIdentifiers(root);
            var validator = SchemaHolder.SCHEMA.newValidator(); validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); validator.setErrorHandler(ERRORS); validator.validate(new DOMSource(document));
            return new GatingMl20(new Builder(root, names, fcsCompensation, relativeSvdCutoff, limits));
        } catch (UnsupportedDefinitionException error) { throw error; }
        catch (SAXException | javax.xml.parsers.ParserConfigurationException | IllegalArgumentException error) { throw new IOException("Invalid/unassessable complete Gating-ML definition: "+error.getMessage(), error); }
    }
    public List<Gate> gates() { return gates; }
    public List<String> inputNames() { return inputNames; }
    public List<String> warnings() { return warnings; }
    public List<CytometryCompensation.Diagnostics> compensationDiagnostics() { return compensation.stream().map(c -> c.matrix().diagnostics()).toList(); }
    public Evaluator evaluator() { return new Evaluator(); }
    private BoundValue resolveValue(int matrix, boolean fcs, String name) throws IOException {
        if (matrix < 0) return new BoundValue(-1, index(inputNames, name, "source scale dimension"));
        int channel = compensation.get(matrix).matrix().fluorochromes().indexOf(name);
        if (channel >= 0) return new BoundValue(matrix, channel);
        // FCS file compensation affects the named fluorescence subset; other channels remain unchanged.
        if (fcs) return new BoundValue(-1, index(inputNames, name, "unchanged FCS dimension"));
        throw new IOException("Unknown exact compensated fluorochrome dimension: "+name);
    }

    /** One reusable evaluator per thread. It publishes a membership vector only after the full event succeeds. */
    public final class Evaluator {
        private final double[] valueCache = new double[dimensions.size()];
        private final boolean[] seen = new boolean[dimensions.size()], membership = new boolean[plan.size()];
        private final double[][] compensated = new double[compensation.size()][], detectorBuffers = new double[compensation.size()][], pointBuffers = new double[plan.size()][];
        private double[] input;
        private Evaluator() {
            for (int i = 0; i < compensation.size(); i++) detectorBuffers[i] = new double[compensation.get(i).inputColumns().length];
            for (int i = 0; i < plan.size(); i++) if (plan.get(i).dimensions() != null) pointBuffers[i] = new double[plan.get(i).dimensions().length];
        }
        public void evaluate(double[] scaleValues, boolean[] result) {
            if (scaleValues == null || scaleValues.length != inputNames.size() || result == null || result.length != plan.size()) throw new IllegalArgumentException("Require complete scale-value and membership vectors");
            for (double value : scaleValues) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite source scale value; no dropped event or inferred membership");
            input = scaleValues; Arrays.fill(seen, false); Arrays.fill(compensated, null);
            for (int i : order) {
                BoundGate gate = plan.get(i);
                if (gate.parent() >= 0 && !membership[gate.parent()]) { membership[i] = false; continue; }
                if (gate.region() != null) {
                    double[] point = pointBuffers[i];
                    try {
                        for (int j = 0; j < point.length; j++) point[j] = dimension(gate.dimensions()[j]);
                        membership[i] = gate.region().contains(point);
                    } catch (ArithmeticException failure) {
                        var context = new ArithmeticException("Gate "+gate.info().id()+": "+failure.getMessage()+"; no event membership admitted"); context.initCause(failure); throw context;
                    }
                } else {
                    boolean value = gate.operator().equals("and");
                    for (int j = 0; j < gate.references().length; j++) {
                        boolean part = membership[gate.references()[j]] ^ gate.complements()[j];
                        if (gate.operator().equals("and")) value &= part;
                        else if (gate.operator().equals("or")) value |= part;
                        else value = !part;
                    }
                    membership[i] = value;
                }
            }
            System.arraycopy(membership, 0, result, 0, result.length);
        }
        private double dimension(int index) {
            if (seen[index]) return valueCache[index]; BoundDimension d = dimensions.get(index);
            double value = d.ratio() == null ? value(d.first()) : CytometryTransforms.ratio(value(d.first()), value(d.second()), d.ratio().a(), d.ratio().b(), d.ratio().c());
            if (d.scale() != null) value = d.scale().apply(value);
            if (!Double.isFinite(value)) throw new ArithmeticException("Gating dimension is undefined/nonfinite; no event drop");
            valueCache[index] = value; seen[index] = true; return value;
        }
        private double value(BoundValue reference) {
            if (reference.compensation() >= 0) {
                int id = reference.compensation();
                if (compensated[id] == null) {
                    var c = compensation.get(id); double[] measured = detectorBuffers[id];
                    for (int j = 0; j < measured.length; j++) measured[j] = input[c.inputColumns()[j]];
                    compensated[id] = c.matrix().apply(measured);
                }
                return compensated[id][reference.column()];
            }
            return input[reference.column()];
        }
    }

    private static final class Builder {
        final List<String> names; final FcsCompensation fcs; final Limits limits;
        final List<String> warnings = new ArrayList<>(); final List<Draft> drafts = new ArrayList<>();
        final Map<String, CytometryTransforms.Scale> scales = new HashMap<>(); final Map<String, Ratio> ratios = new HashMap<>();
        final Map<String, CytometryCompensation> matrices = new HashMap<>(); final LinkedHashMap<Dimension, Integer> dimensions = new LinkedHashMap<>();
        Builder(Element root, List<String> names, FcsCompensation fcs, double cutoff, Limits limits) throws IOException {
            this.names = names; this.fcs = fcs; this.limits = limits;
            List<Element> elements = children(root); if (elements.size() > limits.definitions()) throw new IOException("Complete Gating-ML definition count exceeds capacity");
            for (Element element : elements) {
                if (is(element, TRANSFORMS, "transformation")) transformation(element);
                else if (is(element, TRANSFORMS, "spectrumMatrix")) spectrum(element, cutoff);
            }
            for (Element e : elements) if (GATING.equals(e.getNamespaceURI())) {
                String id = attr(e, GATING, "id"), parent = optional(e, GATING, "parent_id"), kind = e.getLocalName();
                try {
                if (kind.equals("RectangleGate")) {
                    List<Element> ds = selected(e, GATING, "dimension"); int[] indices = dimensionIndices(ds); double[] lower = new double[ds.size()], upper = lower.clone();
                    for (int i = 0; i < ds.size(); i++) { lower[i] = numberOr(ds.get(i), GATING, "min", Double.NEGATIVE_INFINITY); upper[i] = numberOr(ds.get(i), GATING, "max", Double.POSITIVE_INFINITY); }
                    add(new Draft(new Gate(id, parent, kind), new Primitive(indices, CytometryGates.rectangle(lower, upper)), null));
                } else if (kind.equals("PolygonGate")) {
                    int[] indices = dimensionIndices(selected(e, GATING, "dimension")); List<Element> vertices = selected(e, GATING, "vertex");
                    if (vertices.size() > limits.polygonVertices()) throw new IOException("Complete polygon exceeds declared vertex capacity");
                    double[][] points = new double[vertices.size()][];
                    for (int i = 0; i < points.length; i++) points[i] = coordinates(vertices.get(i));
                    add(new Draft(new Gate(id, parent, kind), new Primitive(indices, CytometryGates.polygon(points)), null));
                } else if (kind.equals("EllipsoidGate")) {
                    int[] indices = dimensionIndices(selected(e, GATING, "dimension"));
                    double[] mean = coordinates(one(e, GATING, "mean")); List<Element> rows = selected(one(e, GATING, "covarianceMatrix"), GATING, "row"); double[][] covariance = new double[rows.size()][];
                    for (int i = 0; i < rows.size(); i++) covariance[i] = selected(rows.get(i), GATING, "entry").stream().mapToDouble(v -> numeric(attr(v, TYPES, "value"))).toArray();
                    if (indices.length != mean.length) throw new IOException("Ellipsoid dimension/mean populations differ");
                    add(new Draft(new Gate(id, parent, kind), new Primitive(indices, CytometryGates.ellipsoid(mean, covariance, numeric(attr(one(e, GATING, "distanceSquare"), TYPES, "value")))), null));
                } else if (kind.equals("BooleanGate")) {
                    List<Element> ops = children(e).stream().filter(v -> GATING.equals(v.getNamespaceURI())).toList();
                    if (ops.size() != 1) throw new IOException("Require one complete Boolean operation");
                    Element op = ops.get(0); List<Element> refs = selected(op, GATING, "gateReference"); String[] ids = new String[refs.size()]; boolean[] complement = new boolean[refs.size()];
                    for (int i = 0; i < refs.size(); i++) { ids[i] = attr(refs.get(i), GATING, "ref"); complement[i] = bool(optional(refs.get(i), GATING, "use-as-complement")); }
                    add(new Draft(new Gate(id, parent, kind), null, new BooleanRule(op.getLocalName(), ids, complement)));
                } else if (kind.equals("QuadrantGate")) quadrants(e, parent);
                else throw new UnsupportedDefinitionException("Unsupported gate type "+kind+"; dependent analysis skipped");
                } catch (UnsupportedDefinitionException unsupported) { throw unsupported; }
                catch (IOException | IllegalArgumentException invalid) { throw new IOException("Gate "+id+": "+invalid.getMessage(), invalid); }
            }
            if (drafts.isEmpty() || drafts.size() > limits.definitions()) throw new IOException("Require a complete nonempty gate population within capacity");
        }
        private void add(Draft gate) throws IOException {
            if (drafts.size() >= limits.definitions()) throw new IOException("Complete gate population exceeds declared capacity");
            drafts.add(gate);
        }
        private void transformation(Element e) throws IOException {
            String id = attr(e, TRANSFORMS, "id"); List<Element> definitions = children(e).stream().filter(v -> TRANSFORMS.equals(v.getNamespaceURI())).toList();
            if (definitions.size() != 1) throw new IOException("Require one complete transform");
            Element d = definitions.get(0); String type = d.getLocalName();
            if (type.equals("fratio")) {
                List<Element> ds = selected(d, TYPES, "fcs-dimension");
                ratios.put(id, new Ratio(attr(ds.get(0), TYPES, "name"), attr(ds.get(1), TYPES, "name"), numeric(attr(d, TRANSFORMS, "A")), numeric(attr(d, TRANSFORMS, "B")), numeric(attr(d, TRANSFORMS, "C")))); return;
            }
            double t = numeric(attr(d, TRANSFORMS, "T"));
            CytometryTransforms.Scale scale = switch (type) {
                case "flin" -> CytometryTransforms.linear(t, numeric(attr(d, TRANSFORMS, "A")));
                case "flog" -> CytometryTransforms.logarithmic(t, numeric(attr(d, TRANSFORMS, "M")));
                case "fasinh" -> CytometryTransforms.arcsinh(t, numeric(attr(d, TRANSFORMS, "M")), numeric(attr(d, TRANSFORMS, "A")));
                case "logicle" -> CytometryTransforms.logicle(t, numeric(attr(d, TRANSFORMS, "W")), numeric(attr(d, TRANSFORMS, "M")), numeric(attr(d, TRANSFORMS, "A")));
                case "hyperlog" -> CytometryTransforms.hyperlog(t, numeric(attr(d, TRANSFORMS, "W")), numeric(attr(d, TRANSFORMS, "M")), numeric(attr(d, TRANSFORMS, "A")));
                default -> throw new UnsupportedDefinitionException("Unsupported transform "+type+"; dependent analysis skipped");
            };
            scales.put(id, scale);
        }
        private void spectrum(Element e, double cutoff) throws IOException {
            String id = attr(e, TRANSFORMS, "id"); List<String> fluoros = selected(one(e, TRANSFORMS, "fluorochromes"), TYPES, "fcs-dimension").stream().map(d -> attr(d, TYPES, "name")).toList();
            if (id.equals("FCS") || id.equals("uncompensated")) throw new IOException("Spectrum identifier conflicts with a reserved compensation state");
            List<String> detectors = selected(one(e, TRANSFORMS, "detectors"), TYPES, "fcs-dimension").stream().map(d -> attr(d, TYPES, "name")).toList();
            if (fluoros.size() > limits.dimensions() || detectors.size() > limits.dimensions()) throw new IOException("Complete compensation exceeds declared dimension capacity");
            if (fluoros.stream().anyMatch(detectors::contains)) throw new IOException("Gating-ML spectrum detector/fluorochrome names must be distinct sets");
            List<Element> rows = selected(e, TRANSFORMS, "spectrum"); double[][] matrix = new double[rows.size()][];
            for (int i = 0; i < rows.size(); i++) matrix[i] = selected(rows.get(i), TRANSFORMS, "coefficient").stream().mapToDouble(d -> numeric(attr(d, TRANSFORMS, "value"))).toArray();
            boolean inverted = bool(optional(e, TRANSFORMS, "matrix-inverted-already"));
            matrices.put(id, inverted ? CytometryCompensation.alreadyInverted(detectors, fluoros, matrix) : CytometryCompensation.spectrum(detectors, fluoros, matrix, cutoff));
        }
        private void quadrants(Element e, String parent) throws IOException {
            record Divider(int dimension, double[] cuts) { }
            var dividers = new LinkedHashMap<String, Divider>();
            List<Element> dividerElements = selected(e, GATING, "divider"); int[] dividerDimensions = dimensionIndices(dividerElements); int dividerIndex = 0;
            for (Element divider : dividerElements) {
                double[] cuts = selected(divider, GATING, "value").stream().mapToDouble(v -> (double)Float.parseFloat(v.getTextContent().strip())).toArray();
                for (int i = 0; i < cuts.length; i++) if (!Double.isFinite(cuts[i]) || i > 0 && !(cuts[i] > cuts[i-1])) throw new IOException("Quadrant dividers must be finite, unique and ordered");
                dividers.put(attr(divider, GATING, "id"), new Divider(dividerDimensions[dividerIndex++], cuts));
            }
            if (dividers.size() > limits.dimensions()) throw new IOException("Complete quadrant dimensions exceed capacity");
            for (Element q : selected(e, GATING, "Quadrant")) {
                List<Element> positions = selected(q, GATING, "position"); int[] indices = new int[positions.size()]; double[] lower = new double[indices.length], upper = lower.clone(); var seen = new HashSet<String>();
                for (int i = 0; i < positions.size(); i++) {
                    String ref = attr(positions.get(i), GATING, "divider_ref"); Divider divider = dividers.get(ref);
                    if (divider == null || !seen.add(ref)) throw new IOException("Unknown/repeated quadrant divider");
                    double location = numeric(attr(positions.get(i), GATING, "location")); int bucket = Arrays.binarySearch(divider.cuts(), location);
                    if (bucket >= 0) throw new IOException("Quadrant representative location lies on its divider"); bucket = -bucket-1;
                    indices[i] = divider.dimension(); lower[i] = bucket == 0 ? Double.NEGATIVE_INFINITY : divider.cuts()[bucket-1]; upper[i] = bucket == divider.cuts().length ? Double.POSITIVE_INFINITY : divider.cuts()[bucket];
                }
                add(new Draft(new Gate(attr(q, GATING, "id"), parent, "Quadrant"), new Primitive(indices, CytometryGates.rectangle(lower, upper)), null));
            }
        }
        private int[] dimensionIndices(List<Element> elements) throws IOException {
            if (elements.size() > limits.dimensions()) throw new IOException("Complete gate dimensions exceed capacity");
            int[] ids = new int[elements.size()]; var sources = new HashSet<String>();
            for (int i = 0; i < ids.length; i++) {
                ids[i] = dimension(elements.get(i)); Dimension d = dimensions.keySet().stream().skip(ids[i]).findFirst().orElseThrow();
                if (!sources.add((d.ratio() ? "ratio:" : "fcs:")+d.source())) throw new IOException("Repeated source dimension within a gate");
            }
            return ids;
        }
        private int dimension(Element e) throws IOException {
            List<Element> source = children(e).stream().filter(d -> TYPES.equals(d.getNamespaceURI()) && (d.getLocalName().equals("fcs-dimension") || d.getLocalName().equals("new-dimension"))).toList();
            if (source.size() != 1) throw new IOException("Require exactly one dimension source");
            boolean ratio = source.get(0).getLocalName().equals("new-dimension"); String name = attr(source.get(0), TYPES, ratio ? "transformation-ref" : "name");
            Dimension d = new Dimension(name, ratio, attr(e, GATING, "compensation-ref"), optional(e, GATING, "transformation-ref"));
            return dimensions.computeIfAbsent(d, ignored -> dimensions.size());
        }
    }
    private static int[] topological(List<BoundGate> nodes) throws IOException {
        var dependents = new ArrayList<List<Integer>>();
        for (int i = 0; i < nodes.size(); i++) dependents.add(new ArrayList<>());
        int[] pending = new int[nodes.size()]; var ready = new java.util.PriorityQueue<Integer>();
        for (int i = 0; i < nodes.size(); i++) {
            BoundGate node = nodes.get(i); var dependencies = new LinkedHashSet<Integer>();
            if (node.parent() >= 0) dependencies.add(node.parent()); for (int ref : node.references()) dependencies.add(ref);
            pending[i] = dependencies.size(); if (pending[i] == 0) ready.add(i);
            for (int ref : dependencies) dependents.get(ref).add(i);
        }
        int[] order = new int[nodes.size()]; int count = 0;
        while (!ready.isEmpty()) {
            int current = ready.remove(); order[count++] = current;
            for (int dependent : dependents.get(current)) if (--pending[dependent] == 0) ready.add(dependent);
        }
        if (count != nodes.size()) throw new IOException("Circular parent/Boolean gate dependency; no partial gate population");
        return order;
    }
    private static int gateIndex(Map<String, Integer> ids, String ref) throws IOException {
        Integer value = ids.get(ref); if (value == null) throw new IOException("Unknown/nonpopulation gate reference: "+ref); return value;
    }
    private static int index(List<String> names, String value, String label) throws IOException {
        int result = names.indexOf(value); if (result < 0) throw new IOException("Unknown exact "+label+": "+value); return result;
    }
    private static String attr(Element e, String namespace, String name) {
        if (!e.hasAttributeNS(namespace, name)) throw new IllegalArgumentException("Required Gating-ML attribute missing: "+name);
        String value = e.getAttributeNS(namespace, name);
        if (value.isEmpty()) throw new IllegalArgumentException("Empty Gating-ML attribute: "+name);
        return value;
    }
    private static String optional(Element e, String namespace, String name) { return e.hasAttributeNS(namespace, name) ? attr(e, namespace, name) : null; }
    private static double numberOr(Element e, String namespace, String name, double absent) {
        String value = optional(e, namespace, name);
        if (value == null) return absent;
        if (value.strip().equals("INF")) return Double.POSITIVE_INFINITY;
        if (value.strip().equals("-INF")) return Double.NEGATIVE_INFINITY;
        return numeric(value);
    }
    private static double numeric(String text) {
        double value = Double.parseDouble(text.strip()); if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite Gating-ML numerical definition"); return value;
    }
    private static boolean bool(String value) {
        if (value != null) value = value.strip(); // XML Schema Boolean whitespace semantics, not identifier normalization.
        if (value == null || value.equals("false") || value.equals("0")) return false;
        if (value.equals("true") || value.equals("1")) return true;
        throw new IllegalArgumentException("Invalid explicit Boolean value");
    }
    private static boolean is(Element e, String namespace, String name) { return namespace.equals(e.getNamespaceURI()) && e.getLocalName().equals(name); }
    private static void exactIdentifiers(Element root) throws IOException {
        if (is(root, TYPES, "custom_info")) return;
        var attributes = root.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i); String local = attribute.getLocalName();
            String namespace = attribute.getNamespaceURI();
            if ((GATING.equals(namespace) || TYPES.equals(namespace) || TRANSFORMS.equals(namespace)) && local != null && List.of("id", "ref", "parent_id", "transformation-ref", "divider_ref", "compensation-ref").contains(local) && !attribute.getNodeValue().equals(attribute.getNodeValue().strip())) throw new IOException("Whitespace in a scientific identifier/reference is not silently normalized");
        }
        for (Element child : children(root)) exactIdentifiers(child);
    }
    private static List<Element> children(Element parent) {
        var elements = new ArrayList<Element>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element e) elements.add(e);
        return elements;
    }
    private static List<Element> selected(Element parent, String namespace, String name) { return children(parent).stream().filter(e -> is(e, namespace, name)).toList(); }
    private static Element one(Element parent, String namespace, String name) throws IOException {
        List<Element> items = selected(parent, namespace, name); if (items.size() != 1) throw new IOException("Require one complete "+name); return items.get(0);
    }
    private static double[] coordinates(Element e) { return selected(e, GATING, "coordinate").stream().mapToDouble(v -> numeric(attr(v, TYPES, "value"))).toArray(); }
    private static final ErrorHandler ERRORS = new ErrorHandler() {
        @Override public void warning(SAXParseException e) throws SAXException { throw e; }
        @Override public void error(SAXParseException e) throws SAXException { throw e; }
        @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
    };
    private static byte[] schemaBytes(String name) throws IOException {
        String expected = SCHEMAS.get(name); if (expected == null) throw new IOException("Unbound schema resource"); byte[] bytes;
        try (var input = GatingMl20.class.getResourceAsStream("/org/jlinalg/cytometry/gatingml20/"+name)) { if (input == null) throw new IOException("Missing original Gating-ML schema"); bytes = input.readNBytes(65537); }
        try { if (bytes.length > 65536 || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(expected)) throw new IOException("Original Gating-ML schema bytes differ"); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
        return bytes;
    }
    private static final class SchemaHolder {
        static final Schema SCHEMA = load();
        private static Schema load() {
            try {
                var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI); factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                DOMImplementationLS dom = (DOMImplementationLS)DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation().getFeature("LS", "3.0");
                factory.setResourceResolver((type, namespace, publicId, systemId, base) -> {
                    try {
                        String file = systemId.substring(systemId.lastIndexOf('/')+1); byte[] bytes = schemaBytes(file);
                        var input = dom.createLSInput(); input.setByteStream(new ByteArrayInputStream(bytes)); input.setSystemId(file); input.setBaseURI("classpath:/org/jlinalg/cytometry/gatingml20/"); return input;
                    } catch (IOException error) { throw new IllegalStateException("No external schema resolution", error); }
                });
                return factory.newSchema(new StreamSource(new ByteArrayInputStream(schemaBytes("Gating-ML.v2.0.xsd")), "classpath:/org/jlinalg/cytometry/gatingml20/Gating-ML.v2.0.xsd"));
            } catch (Exception error) { throw new ExceptionInInitializerError(error); }
        }
    }
}

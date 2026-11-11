/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Native planning of the original biomaRt 2.60.1/2.68.0 getBM XML batches.
 * The original query strings, numeric-to-integer conversion, quote substitution
 * and first-filter-fastest Cartesian batch order are retained. This class does
 * not contact BioMart, parse annotation tables or choose a release/gene mask.
 * Source behavior: biomaRt getBM and utilityFunctions.R (Artistic-2.0).
 */
public final class BioMartQueries {
    private BioMartQueries() { }
    public static final int ORIGINAL_CHUNK_SIZE = 5000;

    public enum Edition { BIOMART_2_60_1, BIOMART_2_68_0 }
    public enum ValueType { CHARACTER, NUMERIC, LOGICAL }

    /** Raw tokens remain available even when the source encodes a different query value. */
    public record Filter(String name, ValueType type, List<String> values) {
        public Filter {
            require(name != null && !name.isEmpty(), "Empty filter name; use an empty filter list for an unfiltered query");
            Objects.requireNonNull(type, "type");
            values = List.copyOf(values);
            require(!values.isEmpty(), "Values argument contains no data: " + name);
            for (String value : values) require(value.indexOf('\0') < 0, "Embedded NUL is not an R character value");
        }
        public static Filter characters(String name, List<String> values) { return new Filter(name, ValueType.CHARACTER, values); }
        public static Filter numbers(String name, double... values) {
            var tokens = new ArrayList<String>(values.length);
            for (double value : values) tokens.add(Double.toString(value));
            return new Filter(name, ValueType.NUMERIC, tokens);
        }
        public static Filter logical(String name, boolean value) { return new Filter(name, ValueType.LOGICAL, List.of(value ? "TRUE" : "FALSE")); }
    }

    public record Request(Edition edition, String dataset, String virtualSchema, List<String> attributes,
                          List<Filter> filters, boolean uniqueRows, boolean checkFilters) {
        public Request {
            Objects.requireNonNull(edition, "edition");
            require(dataset != null && !dataset.isEmpty() && virtualSchema != null && !virtualSchema.isEmpty(), "Explicit dataset and virtual schema required");
            attributes = List.copyOf(attributes);
            filters = List.copyOf(filters);
            require(!attributes.isEmpty(), "Argument attributes must be specified");
            var names = new HashSet<String>();
            for (Filter filter : filters) require(names.add(filter.name()), "Duplicate filter names are ambiguous; no ignored or overwritten filter is inferred");
        }
    }

    public record Limits(long maximumValues, int maximumQueries, long maximumQueryBytes, long maximumCombinedQueryBytes) {
        public Limits {
            require(maximumValues > 0 && maximumQueries > 0 && maximumQueryBytes > 0 && maximumQueryBytes <= Integer.MAX_VALUE && maximumCombinedQueryBytes >= maximumQueryBytes, "Invalid complete-query capacities");
        }
        public static Limits defaults() { return new Limits(1_000_000, 1024, 8L << 20, 64L << 20); }
    }

    /** Source changes are disclosed; the original input token is never overwritten. */
    public record Coercion(String filter, int valueIndexInBatch, String original, String encoded, String reason) { }
    public record Batch(int ordinal, String xml, List<Filter> filters, List<String> sourceWarnings, List<Coercion> coercions) {
        public Batch {
            filters = List.copyOf(filters);
            sourceWarnings = List.copyOf(sourceWarnings);
            coercions = List.copyOf(coercions);
        }
    }

    /**
     * Plan getBM with the exact original 5000-value splitter. Metadata names and
     * types must come from the intended dataset; an absent boolean type is not
     * inferred from a filter name. No local row deduplication is performed.
     */
    public static List<Batch> plan(Request request, Set<String> availableAttributes,
                                   Map<String,String> filterTypes, Limits limits) {
        Objects.requireNonNull(request, "request");Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(availableAttributes, "availableAttributes");Objects.requireNonNull(filterTypes, "filterTypes");
        long valueCount = 0;
        utf8Bytes(request.dataset());utf8Bytes(request.virtualSchema());
        for (String attribute : request.attributes()) {
            require(availableAttributes.contains(attribute), "Invalid attribute: " + attribute);
            utf8Bytes(attribute);
        }
        for (Filter filter : request.filters()) {
            require(!request.checkFilters() || filterTypes.containsKey(filter.name()), "Invalid filter: " + filter.name());
            valueCount = Math.addExact(valueCount, filter.values().size());
            require(valueCount <= limits.maximumValues(), "Complete filter-value capacity exceeded; no value may be omitted");
            utf8Bytes(filter.name());
            for (String value : filter.values()) require(utf8Bytes(value) <= limits.maximumQueryBytes(), "A complete original value exceeds the query byte capacity");
        }
        List<List<Filter>> chunks = new ArrayList<>();chunks.add(request.filters());
        for (int index = 0; index < request.filters().size(); index++) {
            int count = request.filters().get(index).values().size();
            if (count <= ORIGINAL_CHUNK_SIZE) continue;
            int groups = count / ORIGINAL_CHUNK_SIZE + 1;
            int width = (int)(((long)count + groups - 1) / groups);
            require(chunks.size() <= limits.maximumQueries() / groups, "Complete Cartesian query capacity exceeded; no filter grouping may be omitted");
            var expanded = new ArrayList<List<Filter>>(chunks.size() * groups);
            // Source .splitValues loops over the next split outside earlier batches.
            for (int group = 0; group < groups; group++) {
                int first = Math.multiplyExact(group, width), last = Math.min(count, Math.addExact(first, width));
                for (List<Filter> prior : chunks) {
                    var copy = new ArrayList<>(prior);Filter raw = prior.get(index);
                    copy.set(index, new Filter(raw.name(), raw.type(), raw.values().subList(first, last)));expanded.add(List.copyOf(copy));
                }
            }
            chunks = expanded;
        }
        require(chunks.size() <= limits.maximumQueries(), "Complete query capacity exceeded");
        var result = new ArrayList<Batch>(chunks.size());long combined = 0;
        for (List<Filter> filters : chunks) {
            var xml = new StringBuilder();var warnings = new ArrayList<String>();var coercions = new ArrayList<Coercion>();
            boolean legacy = request.edition() == Edition.BIOMART_2_60_1;
            append(xml,legacy ? "<?xml version='1.0' encoding='UTF-8'?><!DOCTYPE Query><Query  virtualSchemaName = '" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE Query><Query virtualSchemaName = \"",limits);
            append(xml,request.virtualSchema(),limits);
            append(xml,legacy ? "' uniqueRows = '" + (request.uniqueRows() ? "1" : "0") + "' count='0' datasetConfigVersion='0.6' header='1' formatter='TSV' requestid='biomaRt'> <Dataset name = '" : "\" uniqueRows=\"" + (request.uniqueRows() ? "1" : "0") + "\" count=\"0\" datasetConfigVersion=\"0.6\" header=\"1\" formatter=\"TSV\" requestid=\"biomaRt\"><Dataset name=\"",limits);
            append(xml,request.dataset(),limits);append(xml,legacy ? "'>" : "\">",limits);
            for (String attribute : request.attributes()) {append(xml,legacy ? "<Attribute name = '" : "<Attribute name = \"",limits);append(xml,attribute,limits);append(xml,legacy ? "'/>" : "\"/>",limits);}
            for (Filter filter : filters) encodeFilter(xml,filter,filterTypes,warnings,coercions,limits);
            append(xml,"</Dataset></Query>",limits);String text = xml.toString();long bytes = utf8Bytes(text);
            require(bytes <= limits.maximumQueryBytes() && bytes <= limits.maximumCombinedQueryBytes() - combined, "Complete query UTF-8 capacity exceeded; no query may be truncated");
            combined += bytes;result.add(new Batch(result.size()+1,text,filters,warnings,coercions));
        }
        return List.copyOf(result);
    }

    private static void encodeFilter(StringBuilder xml, Filter filter, Map<String,String> types,
                                     List<String> warnings, List<Coercion> coercions, Limits limits) {
        String type = types.get(filter.name());boolean originalBoolean = type != null && type.contains("boolean");
        append(xml,"<Filter name = \"",limits);append(xml,filter.name(),limits);
        if (originalBoolean) {
            require(filter.type() == ValueType.LOGICAL && filter.values().size() == 1, "Original boolean filters require one logical value: " + filter.name());
            String value = filter.values().get(0);require(Set.of("TRUE","FALSE","NA").contains(value), "Invalid original logical token");
            append(xml,"\" excluded = \"" + (value.equals("TRUE") ? "0" : value.equals("FALSE") ? "1" : "NA") + "\" />",limits);return;
        }
        append(xml,"\" value = \"",limits);boolean rangeWarning = false;
        for (int index=0;index<filter.values().size();index++) {
            String original = filter.values().get(index), encoded = original;
            if (filter.type() == ValueType.NUMERIC) {
                if (original.equals("NA") || original.equals("NaN")) encoded="NA";
                else {
                    double value = numeric(original);long truncated=(long)value;
                    if (!Double.isFinite(value) || truncated <= Integer.MIN_VALUE || truncated > Integer.MAX_VALUE) {encoded="NA";rangeWarning=true;}
                    else encoded=Long.toString(truncated);
                }
                if (!original.equals(encoded)) coercions.add(new Coercion(filter.name(),index+1,original,encoded,"Original as.integer numeric-filter conversion"));
            } else if (filter.type() == ValueType.LOGICAL) require(Set.of("TRUE","FALSE","NA").contains(original), "Invalid original logical token");
            if (encoded.indexOf('"')>=0) {String changed=encoded.replace('"','\'');coercions.add(new Coercion(filter.name(),index+1,encoded,changed,"Original double-quote to apostrophe query substitution"));encoded=changed;}
            if (index>0) append(xml,",",limits);append(xml,encoded,limits);
        }
        if (rangeWarning) warnings.add("NAs introduced by coercion to integer range");
        append(xml,"\" />",limits);
    }

    private static double numeric(String token) {
        return switch(token) {case "Inf", "+Inf", "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY;case "-Inf", "-Infinity" -> Double.NEGATIVE_INFINITY;default -> {
            require(token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Numeric input requires an explicit decimal or recorded NA/NaN/Inf token; Java suffixes and alternate encodings are outside this source profile");
            try {yield Double.parseDouble(token);}catch(NumberFormatException error){throw new IllegalArgumentException("Invalid explicit numeric token: " + token,error);}
        }};
    }
    private static void append(StringBuilder text,String value,Limits limits) {require(value.length() <= limits.maximumQueryBytes()-text.length(),"Complete query capacity exceeded; no field may be truncated");text.append(value);}
    private static int utf8Bytes(String text) {
        require(text != null && text.indexOf('\0')<0,"Null/embedded-NUL text cannot be represented as an original R string");
        try{return StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text)).remaining();}
        catch(CharacterCodingException error){throw new IllegalArgumentException("Malformed UTF-16 input; no identifier replacement is allowed",error);}
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalArgumentException(message);}
}

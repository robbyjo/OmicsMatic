/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local Enrichr preparation and inference, without Python or a remote library. */
public final class GseapyLocalEnrichment {
    private GseapyLocalEnrichment() { }

    /** SOURCE follows the pinned original class; LITERAL accepts already prepared identifiers. */
    public enum Identifiers { SOURCE, LITERAL }
    /** Every query transformation is retained, including duplicates. */
    public record QueryId(String original, String effective, boolean integer) { }
    /** Original results plus explicit preparation and omission information. */
    public record Result(List<GseapyStatistics.Row> rows, List<QueryId> query,
            List<String> omittedTerms, boolean uppercased, boolean integerQuery,
            int population) {
        public Result { rows=List.copyOf(rows);query=List.copyOf(query);omittedTerms=List.copyOf(omittedTerms); }
    }

    /**
     * Execute one supplied local library. Background is an explicit collection,
     * an Integer population, or deliberate null for the complete library union.
     * Source failures are preserved, including 1.1.8's integer-background/set
     * slicing errors and the older editions' empty-family concatenation errors.
     */
    public static Result analyze(GseapyStatistics.Edition edition, Collection<String> suppliedQuery,
            Map<String,? extends Collection<String>> suppliedSets, Object background,
            Identifiers policy, double alpha) {
        java.util.Objects.requireNonNull(edition);java.util.Objects.requireNonNull(policy);
        if(suppliedQuery.isEmpty())throw new IllegalArgumentException("Gene list cannot be empty");
        if(suppliedSets.isEmpty())throw new IllegalArgumentException("Require a nonempty local gene-set library");
        List<String> original=new ArrayList<>(suppliedQuery),query=new ArrayList<>();
        for(String id:original)query.add(policy==Identifiers.SOURCE&&edition==GseapyStatistics.Edition.V1_1_8?strip(id):java.util.Objects.requireNonNull(id));
        boolean numeric=policy==Identifiers.SOURCE&&query.stream().allMatch(s->integer(s)!=null);
        var sets=new LinkedHashMap<String,List<String>>();
        for(var entry:suppliedSets.entrySet()) {
            java.util.Objects.requireNonNull(entry.getKey());var ids=new ArrayList<String>();
            for(String id:entry.getValue()) {
                java.util.Objects.requireNonNull(id);
                boolean convert=numeric&&edition!=GseapyStatistics.Edition.V1_0_4;
                if(convert&&integer(id)==null)throw new IllegalArgumentException("Original local Enrichr cannot convert a library member to an integer: "+id);
                ids.add(convert?"i:"+integer(id):"s:"+id);
            }
            sets.put(entry.getKey(),ids);
        }
        boolean uppercase=policy==Identifiers.SOURCE&&edition==GseapyStatistics.Edition.V1_1_8
            &&!mostlyUpper(query)&&suppliedSets.values().stream().limit(10).allMatch(GseapyLocalEnrichment::mostlyUpper);
        if(policy==Identifiers.SOURCE&&edition==GseapyStatistics.Edition.V1_1_8&&(numeric||background instanceof Integer))
            throw new IllegalArgumentException("Original GSEApy 1.1.8 local Enrichr fails on integer queries (set slicing) or numeric backgrounds (int iteration). Use another explicit edition or explicitly prepared LITERAL identifiers; no source result is fabricated.");
        var ledger=new ArrayList<QueryId>();var encodedQuery=new ArrayList<String>();
        for(int i=0;i<query.size();i++) {
            String effective=numeric?integer(query.get(i)):uppercase?query.get(i).toUpperCase(Locale.ROOT):query.get(i);
            ledger.add(new QueryId(original.get(i),effective,numeric));encodedQuery.add((numeric?"i:":"s:")+effective);
        }
        GseapyStatistics.Background universe;
        int population;
        if(background instanceof Integer count) { universe=new GseapyStatistics.Population(count);population=count; }
        else {
            var ids=new LinkedHashSet<String>();
            if(background==null)sets.values().forEach(ids::addAll);
            else if(background instanceof Collection<?> given)for(Object raw:given) {
                if(!(raw instanceof String id))throw new IllegalArgumentException("Background members must be strings");
                ids.add("s:"+(uppercase?id.toUpperCase(Locale.ROOT):id));
            }
            else throw new IllegalArgumentException("Background must be an explicit string collection, Integer population or null");
            // Null background is assembled before the source's optional case conversion.
            if(uppercase&&background==null) {var converted=new LinkedHashSet<String>();for(String id:ids)converted.add("s:"+id.substring(2).toUpperCase(Locale.ROOT));ids=converted;}
            universe=new GseapyStatistics.Universe(ids);population=ids.size();
        }
        var tested=GseapyStatistics.overRepresentation(edition,encodedQuery,sets,universe,alpha);
        if(policy==Identifiers.SOURCE&&tested.isEmpty()&&edition!=GseapyStatistics.Edition.V1_1_8)
            throw new IllegalArgumentException("Original GSEApy local Enrichr in this edition cannot concatenate an empty result family. LITERAL mode can explicitly retain an empty prepared statistical result.");
        var rows=new ArrayList<GseapyStatistics.Row>();var returned=new LinkedHashSet<String>();
        for(var row:tested) {
            returned.add(row.term());rows.add(new GseapyStatistics.Row(row.term(),row.querySize(),row.overlap(),row.termSize(),
                row.hits().stream().map(id->id.substring(2)).toList(),row.pValue(),row.adjustedPValue(),row.oddsRatio(),
                row.combinedScore(),row.combinedScoreAvailable(),row.rejected()));
        }
        var omitted=new ArrayList<String>();for(String term:sets.keySet())if(!returned.contains(term))omitted.add(term);
        return new Result(rows,ledger,omitted,uppercase,numeric,population);
    }

    private static boolean mostlyUpper(Collection<String> ids) {
        if(ids.isEmpty()||ids.stream().allMatch(s->integer(s)!=null))return false;
        return ids.stream().filter(GseapyLocalEnrichment::isUpper).count()/(double)ids.size()>=0.9;
    }
    private static boolean isUpper(String text) {
        boolean cased=false;
        for(int cp:text.codePoints().toArray()) {
            if(Character.isLowerCase(cp)||Character.isTitleCase(cp))return false;
            if(Character.isUpperCase(cp))cased=true;
        }
        return cased;
    }
    private static boolean space(int cp) {return Character.isWhitespace(cp)||Character.isSpaceChar(cp)||cp==0x85;}
    private static String strip(String text) {
        java.util.Objects.requireNonNull(text);int a=0,b=text.length();
        while(a<b&&space(text.codePointAt(a)))a+=Character.charCount(text.codePointAt(a));
        while(b>a&&space(text.codePointBefore(b)))b-=Character.charCount(text.codePointBefore(b));
        return text.substring(a,b);
    }
    private static String integer(String text) {
        String s=strip(text);var digits=new StringBuilder();boolean previous=false;int i=0,count=0;
        if(!s.isEmpty()&&(s.charAt(0)=='+'||s.charAt(0)=='-'))digits.append(s.charAt(i++));
        for(;i<s.length();) {
            int cp=s.codePointAt(i);i+=Character.charCount(cp);
            if(cp=='_'&&previous&&i<s.length()&&Character.getType(s.codePointAt(i))==Character.DECIMAL_DIGIT_NUMBER){previous=false;continue;}
            if(Character.getType(cp)!=Character.DECIMAL_DIGIT_NUMBER)return null;
            if(++count>4300)return null; // pinned Python3.11's default integer-string conversion limit
            int d=Character.digit(cp,10);if(d<0)return null;digits.append((char)('0'+d));previous=true;
        }
        if(!previous)return null;
        try{return new BigInteger(digits.toString()).toString();}catch(NumberFormatException failure){return null;}
    }
}

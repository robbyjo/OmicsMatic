/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Independent raw-row model of FAVORannotator's offline partitioned CSV left join.
 * Selected shards are concatenated in declared order; query order is preserved
 * within each shard and matching database rows retain source order. No row is
 * silently reordered or deduplicated to repair an unsafe GDS attachment.
 * This kernel does not parse CSV, infer readr column types, compute annotation
 * scores, read/write GDS or implement the complete FAVOR pipeline.
 */
public final class FavorCsvJoin {
    private FavorCsvJoin() { }

    /** Canonical autosomal variant key; no normalization or allele conversion occurs. */
    public record Variant(int chromosome,int position,String reference,String alternate) {
        public Variant {
            FavorCsvJoin.chromosome(chromosome);
            if(position<1||reference==null||alternate==null||!reference.matches("[ACGTN]+")||!alternate.matches("[ACGTN]+")||reference.equals(alternate))throw new IllegalArgumentException("Require positive position and distinct explicit uppercase biallelic DNA alleles");
        }
        /** Exact source join key, chromosome-position-reference-alternate. */
        public String key(){return chromosome+"-"+position+"-"+reference+"-"+alternate;}
    }

    /** One decoded logical CSV record, excluding the header. */
    @FunctionalInterface public interface RowConsumer {
        /**
         * Receive one complete original record. Exceptions must stop the scan.
         * @param fields original decoded cell strings, including literal empty/NA cells
         * @throws IOException input/transport failure
         */
        void accept(List<String> fields)throws IOException;
    }
    /**
     * A bounded caller-owned streaming source. Implementations must scan every
     * original logical record exactly once, propagate consumer failures and close
     * their own input handles. External byte/hash/schema admission belongs to the caller.
     */
    @FunctionalInterface public interface Rows {
        /**
         * Scan the complete original database table after its header.
         * @param consumer receives every row in source order
         * @throws IOException source/transport failure; no partial result is admitted
         */
        void scan(RowConsumer consumer)throws IOException;
    }

    /** One declared DBsplit row; bounds are inclusive, and row order is significant. */
    public record Shard(String identity,int chromosome,int start,int end,Rows rows) {
        public Shard {
            FavorCsvJoin.identity(identity);FavorCsvJoin.chromosome(chromosome);Objects.requireNonNull(rows,"rows");
            if(start<1||end<start)throw new IllegalArgumentException("Require positive closed shard coordinates");
        }
    }
    /** Identified resource with shared exact column order and a single literal variant_vcf key. */
    public record Reference(String identity,String build,List<String> columns,List<Shard> shards) {
        public Reference {
            FavorCsvJoin.identity(identity);FavorCsvJoin.identity(build);columns=List.copyOf(columns);shards=List.copyOf(shards);
            if(columns.isEmpty()||columns.size()>1024||!columns.contains("variant_vcf")||columns.contains("VarInfo")||new HashSet<>(columns).size()!=columns.size())throw new IllegalArgumentException("Require unique bounded DB columns including variant_vcf,without reserved VarInfo");
            for(String column:columns)if(column.isBlank()||column.length()>1024||column.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid database column name");
        }
    }
    /** Complete query, database-row/cell, emitted-row and measured-work capacities. */
    public record Limits(int maximumVariants,long maximumDatabaseRows,long maximumCells,
            int maximumJoinedRows,long maximumOperations) {
        public Limits {
            if(maximumVariants<1||maximumDatabaseRows<0||maximumCells<1||maximumJoinedRows<0||maximumOperations<1)throw new IllegalArgumentException("Require positive query/cell/work and nonnegative database/output limits");
        }
        /** Default work/storage bounds; these do not reserve heap or truncate populations. */
        public static Limits defaults(){return new Limits(1_000_000,100_000_000L,1_000_000_000L,2_000_000,2_000_000_000L);}
    }
    /** Exact query build and one selected autosome; no chromosome prefix conversion occurs. */
    public record Options(String build,int chromosome,Limits limits) {
        public Options {FavorCsvJoin.identity(build);FavorCsvJoin.chromosome(chromosome);Objects.requireNonNull(limits,"limits");}
    }
    /** No-shard drops and multiplied source rows are never interpreted as ordinary missing matches. */
    public enum Disposition { DROPPED_NO_SHARD, MISSING_DATABASE_MATCH, MATCHED, MULTIPLE_SOURCE_ROWS }
    /**
     * One original left-join output row. variantRow and shardRow are one-based;
     * databaseRow is one-based within that original shard, or zero for a missing
     * database match. Missing matches contain source-generated empty CSV cells.
     * Literal NA/empty database values remain unchanged and distinguishable by databaseRow.
     */
    public record Joined(int variantRow,int shardRow,long databaseRow,String key,List<String> databaseValues) {
        public Joined {databaseValues=List.copyOf(databaseValues);}
    }
    /** Every original query row and its complete projection into source output row numbers. */
    public record VariantResult(int row,Variant variant,int selectedShards,int outputRows,int databaseMatches,
            Disposition disposition,List<Integer> sourceOutputRows) {
        public VariantResult {sourceOutputRows=List.copyOf(sourceOutputRows);}
    }
    /**
     * Every declared shard row. Unselected chromosome tables are not opened and
     * retain null database counts rather than being described as empty tables.
     */
    public record ShardResult(int row,String identity,int chromosome,int start,int end,boolean selected,
            int selectedVariants,Long databaseRows,Long matchedDatabaseRows,Long unusedDatabaseRows,int outputRows) { }
    /**
     * Complete raw source model. A safe positional GDS attachment requires both
     * exactlyOneRowPerVariant and sourceOrderAligned. No-match rows can satisfy
     * those conditions; zero/multiple shard or DB matches remain explicit evidence.
     */
    public record Result(List<VariantResult> variants,List<ShardResult> shards,List<Joined> joined,
            boolean exactlyOneRowPerVariant,boolean sourceOrderAligned,List<String> warnings,
            long databaseRows,long cells,long operations) {
        public Result {variants=List.copyOf(variants);shards=List.copyOf(shards);joined=List.copyOf(joined);warnings=List.copyOf(warnings);}
    }

    private record Match(long databaseRow,List<String> values) { }
    private static final class Work {
        private final Limits limits;
        private final Runnable check;
        private long operations,rows,cells;
        private int reservedRows;
        Work(Limits limits,Runnable check){this.limits=limits;this.check=check;}
        void tick(){check.run();if(operations>=limits.maximumOperations())throw new IllegalArgumentException("FAVOR join operation budget exceeded;no population was truncated");operations++;}
        void row(int width){tick();if(rows>=limits.maximumDatabaseRows())throw new IllegalArgumentException("Complete FAVOR database-row budget exceeded");rows++;cells(width);}
        void cells(long count){if(count>limits.maximumCells()-cells)throw new IllegalArgumentException("Complete FAVOR cell budget exceeded");cells+=count;}
        void reserve(int count){if(count>limits.maximumJoinedRows()-reservedRows)throw new IllegalArgumentException("Complete FAVOR joined-row budget exceeded;no matches were truncated");reservedRows+=count;}
    }

    /**
     * Independently model all selected-shard raw joins and diagnose row attachment.
     * Only query-matching DB rows are retained in memory; every DB row is scanned,
     * validated and counted. Repeated database keys multiply output exactly as a
     * left join; unmatched queries emit one empty database row. Shard gaps drop
     * queries, overlapping shard membership repeats them, and concatenation may
     * reorder rows. These source behaviors are retained rather than repaired.
     *
     * @param variants nonempty original GDS-order query population
     * @param reference identified exact-build schema and declared DBsplit rows
     * @param options required exact build, selected autosome and complete limits
     * @param cancellation cooperative check during all measured scans and construction
     * @return complete raw rows, projection ledgers and GDS positional-alignment flags
     * @throws IOException any database input/transport failure
     */
    public static Result join(List<Variant> variants,Reference reference,Options options,Runnable cancellation)throws IOException {
        if(variants==null||variants.isEmpty()||reference==null||options==null||cancellation==null)throw new IllegalArgumentException("Require nonempty FAVOR queries,reference,options and cancellation");
        if(!reference.build().equals(options.build()))throw new IllegalArgumentException("Query and FAVOR reference builds differ;explicit harmonization required");
        if(variants.size()>options.limits().maximumVariants())throw new IllegalArgumentException("Complete FAVOR query budget exceeded");
        Work work=new Work(options.limits(),cancellation);work.cells(reference.columns().size());
        int selected=0;var shardIds=new HashSet<String>();
        for(Shard shard:reference.shards()){work.tick();if(!shardIds.add(shard.identity()))throw new IllegalArgumentException("Duplicate FAVOR shard identity");if(shard.chromosome()==options.chromosome())selected++;}
        if(selected<2)throw new IllegalArgumentException("Admitted original offline FAVOR concatenation requires at least two selected shards;single-shard 2:1 loop behavior is not certified");
        var queryKeys=new ArrayList<String>();var uniqueKeys=new HashSet<String>();int repeatedKeys=0;
        for(Variant variant:variants){work.tick();work.cells(4);if(variant.chromosome()!=options.chromosome())throw new IllegalArgumentException("Require one explicitly selected autosome across the complete query population");String key=variant.key();queryKeys.add(key);if(!uniqueKeys.add(key))repeatedKeys++;}
        var joined=new ArrayList<Joined>();var shardResults=new ArrayList<ShardResult>();
        int[] coverage=new int[variants.size()],databaseMatches=new int[variants.size()];var outputRows=new ArrayList<List<Integer>>();for(int i=0;i<variants.size();i++)outputRows.add(new ArrayList<>());
        int keyColumn=reference.columns().indexOf("variant_vcf");
        for(int shardIndex=0;shardIndex<reference.shards().size();shardIndex++) {
            work.tick();Shard shard=reference.shards().get(shardIndex);int shardRow=shardIndex+1;
            if(shard.chromosome()!=options.chromosome()){shardResults.add(new ShardResult(shardRow,shard.identity(),shard.chromosome(),shard.start(),shard.end(),false,0,null,null,null,0));continue;}
            var selectedRows=new ArrayList<Integer>();var queryByKey=new HashMap<String,List<Integer>>();var matches=new LinkedHashMap<Integer,List<Match>>();
            for(int row=0;row<variants.size();row++) {
                work.tick();int position=variants.get(row).position();if(position<shard.start()||position>shard.end())continue;
                work.reserve(1);coverage[row]++;selectedRows.add(row);queryByKey.computeIfAbsent(queryKeys.get(row),ignored->new ArrayList<>()).add(row);matches.put(row,new ArrayList<>());
            }
            long[] counts={0,0};
            shard.rows().scan(fields->{
                work.row(reference.columns().size());counts[0]++;
                if(fields==null||fields.size()!=reference.columns().size())throw new IllegalArgumentException("FAVOR database width differs from declared schema: "+shard.identity()+", row "+counts[0]);
                for(String field:fields){work.tick();if(field==null||field.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid null/NUL FAVOR database cell: "+shard.identity()+", row "+counts[0]);}
                List<Integer> queries=queryByKey.get(fields.get(keyColumn));if(queries==null)return;counts[1]++;List<String> snapshot=List.copyOf(fields);
                for(int row:queries){work.tick();var values=matches.get(row);if(!values.isEmpty())work.reserve(1);values.add(new Match(counts[0],snapshot));databaseMatches[row]++;}
            });
            int before=joined.size();
            for(int row:selectedRows) {
                work.tick();List<Match> found=matches.get(row);
                if(found.isEmpty()){joined.add(new Joined(row+1,shardRow,0,queryKeys.get(row),java.util.Collections.nCopies(reference.columns().size(),"")));outputRows.get(row).add(joined.size());}
                else for(Match match:found){work.tick();joined.add(new Joined(row+1,shardRow,match.databaseRow(),queryKeys.get(row),match.values()));outputRows.get(row).add(joined.size());}
            }
            shardResults.add(new ShardResult(shardRow,shard.identity(),shard.chromosome(),shard.start(),shard.end(),true,selectedRows.size(),counts[0],counts[1],counts[0]-counts[1],joined.size()-before));
        }
        var variantResults=new ArrayList<VariantResult>();boolean populationComplete=true;int dropped=0,multiplied=0,missing=0;
        for(int row=0;row<variants.size();row++) {
            work.tick();int count=outputRows.get(row).size();Disposition disposition;
            if(count==0){disposition=Disposition.DROPPED_NO_SHARD;dropped++;populationComplete=false;}
            else if(count>1){disposition=Disposition.MULTIPLE_SOURCE_ROWS;multiplied++;populationComplete=false;}
            else if(databaseMatches[row]==0){disposition=Disposition.MISSING_DATABASE_MATCH;missing++;}
            else disposition=Disposition.MATCHED;
            variantResults.add(new VariantResult(row+1,variants.get(row),coverage[row],count,databaseMatches[row],disposition,outputRows.get(row)));
        }
        boolean aligned=populationComplete&&joined.size()==variants.size();
        for(int row=0;row<joined.size();row++){work.tick();if(joined.get(row).variantRow()!=row+1)aligned=false;}
        var warnings=new ArrayList<String>();
        warnings.add("RAW FAVOR OFFLINE JOIN MODEL ONLY. Required action: verify exact build,canonical allele keys,resource schema,all shard/query/database row provenance and original readr/GDS types and missingness. No annotation score computation,CSV parsing,GDS mutation,allele normalization or full FAVOR reproduction is certified.");
        if(repeatedKeys>0)warnings.add("REPEATED QUERY KEYS: "+repeatedKeys+" later query rows retained separately. Required action: inspect every original query/genotype identity;no implicit deduplication is performed.");
        if(missing>0)warnings.add("MISSING DATABASE MATCHES: "+missing+" query rows retain source-generated empty DB cells. Required action: preserve their no-match provenance;empty values do not prove lack of biological effect or available annotation.");
        if(dropped>0||multiplied>0||!aligned)warnings.add("UNSAFE POSITIONAL GDS ATTACHMENT: dropped="+dropped+", multiplied="+multiplied+", sourceOrderAligned="+aligned+". Required action: reject this attachment and inspect complete partition/duplicate/order ledgers;do not silently repair,reorder,drop or select source rows.");
        return new Result(variantResults,shardResults,joined,populationComplete,aligned,warnings,work.rows,work.cells,work.operations);
    }

    private static void chromosome(int value){if(value<1||value>22)throw new IllegalArgumentException("Require explicit autosome1..22;no chr/X/Y alias conversion");}
    private static void identity(String value){if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException("Require bounded identity without whitespace/control characters");}
}

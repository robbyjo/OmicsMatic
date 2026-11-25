/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Read-only PredictDB weight/annotation input for the original MetaXcan schema.
 * Allele orientation is never repaired. Explicit alternate-key reads can apply
 * the original source's first-row b38 prefix rule, with reversible provenance.
 * Default reads leave identifiers unchanged. Annotation SNP counts
 * are retained separately from actual weight counts. This does not train models
 * or establish their provenance, genome build or biological suitability.
 */
public final class PredictDb {
    private PredictDb() { }
    public static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    public record Limits(long bytes,int weights,int genes,int seconds) {
        public Limits {if(bytes<100||weights<1||weights==Integer.MAX_VALUE||genes<1||genes==Integer.MAX_VALUE||seconds<1)throw new IllegalArgumentException("Invalid complete PredictDB capacities");}
    }
    public record Weight(String variant,String gene,double value,String referenceAllele,String effectAllele) { }
    public record Extra(String gene,String name,long annotatedSnps,Double performanceR2,
                        Double performanceP,Double performanceQ,Double phi) { }
    public record Model(List<Weight> weights,Map<String,List<Weight>> byGene,Map<String,Extra> extra,boolean hasPhi) { }
    /** A non-null column selects the original --model_db_snp_key path. */
    public record Key(String column) {
        public Key {if(column!=null&&!column.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))throw new IllegalArgumentException("Require one literal ASCII PredictDB column identifier; SQL expressions are not keys");}
    }
    /** The prefix operation applies to ALL rows when the first selected ID triggers it. */
    public record SelectedModel(Model model,String requestedColumn,String effectiveColumn,
                                String firstSourceVariant,boolean sourceB38PrefixApplied) {
        public String sourceVariant(String effectiveVariant) {
            Objects.requireNonNull(effectiveVariant);
            if(!sourceB38PrefixApplied)return effectiveVariant;
            if(!effectiveVariant.startsWith("chr"))throw new IllegalArgumentException("Not an effective ID from this prefixed model");
            return effectiveVariant.substring(3);
        }
    }

    /** Refuse live WAL/journal state rather than capture an incomplete database. */
    public static void requireStandalone(Path file)throws IOException {
        for(String suffix:List.of("-wal","-shm","-journal"))if(Files.exists(Path.of(file.toString()+suffix),LinkOption.NOFOLLOW_LINKS))throw new IOException("PredictDB has a journal/WAL sidecar: "+file+suffix+". Export/checkpoint a separate closed database; no sidecar is silently discarded");
    }
    public static Model read(Path file,Limits limits,Runnable check)throws IOException {
        return readSelected(file,limits,new Key(null),check).model();
    }
    public static SelectedModel readSelected(Path file,Limits limits,Key key,Runnable check)throws IOException {
        Objects.requireNonNull(file);Objects.requireNonNull(limits);Objects.requireNonNull(key);Objects.requireNonNull(check);check.run();
        Path path=file.toAbsolutePath().normalize();
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)||Files.size(path)<100||Files.size(path)>limits.bytes())throw new IOException("Require a bounded canonical regular PredictDB SQLite file: "+path);
        requireStandalone(path);
        try(var in=Files.newInputStream(path)){if(!Arrays.equals(in.readNBytes(16),"SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("Not an ordinary SQLite3 PredictDB: "+path);}
        try {Class.forName("org.sqlite.JDBC");}
        catch(ClassNotFoundException|LinkageError unavailable){throw new Unavailable("SQLite JDBC/native support unavailable. Use the complete OmicsMatic JAR and a permitted writable temporary directory; dependent model analysis skipped",unavailable);}
        String url="jdbc:sqlite:"+path.toUri().toASCIIString()+"?mode=ro&immutable=1";
        try(Connection db=DriverManager.getConnection(url);Statement query=db.createStatement()) {
            query.setQueryTimeout(limits.seconds());query.execute("PRAGMA query_only=ON");query.execute("PRAGMA trusted_schema=OFF");query.execute("PRAGMA mmap_size=0");query.execute("PRAGMA cache_size=-4096");
            for(String name:List.of("weights","extra")) {
                try(var s=db.prepareStatement("SELECT type,sql FROM sqlite_master WHERE name=?")) {
                    s.setString(1,name);try(var r=s.executeQuery()) {
                        if(!r.next()||!"table".equals(r.getString(1))||r.getString(2)==null||!r.getString(2).stripLeading().toUpperCase(Locale.ROOT).matches("CREATE\\s+TABLE[\\s\\S]*")||r.next())throw new IOException("PredictDB requires an ordinary "+name+" table; views/virtual tables are not admitted");
                    }
                }
            }
            String requestedColumn=key.column()==null?"rsid":key.column(),selectedColumn=null;
            try(var r=query.executeQuery("PRAGMA table_info(weights)")){while(r.next()){String actual=r.getString("name");if(actual.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")&&requestedColumn.equalsIgnoreCase(actual))selectedColumn=actual;}}
            if(selectedColumn==null)throw new IOException("PredictDB key column is absent: "+requestedColumn+". Inspect the actual weights schema and declare its literal column; no SNP_ID/varID/rsid alias or replacement is inferred");
            boolean hasPhi=false;try(var r=query.executeQuery("PRAGMA table_info(extra)")){while(r.next())if("phi".equals(r.getString("name")))hasPhi=true;}
            var weights=new ArrayList<Weight>();var grouped=new LinkedHashMap<String,List<Weight>>();var alleles=new HashMap<String,List<String>>();var pairs=new HashSet<List<String>>();
            String firstSourceVariant=null;boolean prefix=false;
            query.setMaxRows(limits.weights()+1);
            // The key is a validated literal existing column, never an SQL expression.
            try(var r=query.executeQuery("SELECT \""+selectedColumn+"\",gene,weight,ref_allele,eff_allele FROM weights")) {
                while(r.next()) {
                    check.run();if(weights.size()>=limits.weights())throw new IOException("Complete PredictDB weight capacity exceeded");
                    String raw=id(r,1);if(firstSourceVariant==null){firstSourceVariant=raw;prefix=key.column()!=null&&raw.contains("_b38")&&!raw.contains("chr");}
                    var w=new Weight(prefix?"chr"+raw:raw,id(r,2),number(r,3,false),allele(r,4),allele(r,5));
                    if(w.referenceAllele().equals(w.effectAllele()))throw new IOException("PredictDB alleles are identical: "+w.variant());
                    if(!pairs.add(List.of(w.gene(),w.variant())))throw new IOException("Duplicate PredictDB gene/variant weight; no deduplication: "+w.gene()+"/"+w.variant());
                    var orientation=List.of(w.referenceAllele(),w.effectAllele());var old=alleles.putIfAbsent(w.variant(),orientation);
                    if(old!=null&&!old.equals(orientation))throw new IOException("Conflicting PredictDB allele orientation: "+w.variant());
                    if(!grouped.containsKey(w.gene())&&grouped.size()>=limits.genes())throw new IOException("Complete PredictDB gene capacity exceeded");
                    grouped.computeIfAbsent(w.gene(),geneId->new ArrayList<>()).add(w);weights.add(w);
                }
            }
            var extra=new LinkedHashMap<String,Extra>();query.setMaxRows(limits.genes()+1);
            try(var r=query.executeQuery("SELECT gene,genename,`n.snps.in.model`,`pred.perf.R2`,`pred.perf.pval`,`pred.perf.qval`"+(hasPhi?",phi":"")+" FROM extra ORDER BY gene")) {
                while(r.next()) {
                    check.run();if(extra.size()>=limits.genes())throw new IOException("Complete PredictDB annotation capacity exceeded");
                    String gene=id(r,1),name=r.getString(2);if(name!=null)text(name);
                    long n=count(r,3);
                    var e=new Extra(gene,name,n,number(r,4,true),number(r,5,true),number(r,6,true),hasPhi?number(r,7,true):null);
                    if(extra.put(gene,e)!=null)throw new IOException("Duplicate PredictDB extra gene; no deduplication: "+gene);
                }
            }
            if(weights.isEmpty()||extra.isEmpty())throw new IOException("Empty PredictDB weights/extra; no complete model admitted");
            var immutable=new LinkedHashMap<String,List<Weight>>();grouped.forEach((k,v)->immutable.put(k,List.copyOf(v)));
            requireStandalone(path);check.run();var model=new Model(List.copyOf(weights),Collections.unmodifiableMap(immutable),Collections.unmodifiableMap(extra),hasPhi);
            return new SelectedModel(model,key.column(),selectedColumn,firstSourceVariant,prefix);
        }catch(SQLException failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause.getMessage()!=null&&cause.getMessage().toLowerCase(Locale.ROOT).matches("[\\s\\S]*(?:no native library|native library.*(?:load|found)|failed to load.*sqlite)[\\s\\S]*"))throw new Unavailable("SQLite native runtime cannot load. Supply the complete platform-supported JAR and a permitted executable temporary directory; dependent analysis skipped",failure);throw new IOException("Cannot read original PredictDB schema/values: "+path+"; no file is created or repaired",failure);}
        catch(LinkageError unavailable){throw new Unavailable("SQLite native library cannot load. Supply the complete platform-supported JAR and a permitted executable temporary directory; dependent analysis skipped",unavailable);}
    }
    private static String id(ResultSet r,int column)throws SQLException,IOException {Object value=r.getObject(column);if(!(value instanceof String s))throw new IOException("PredictDB identifiers must be stored as text; no numeric coercion");text(s);if(s.isBlank())throw new IOException("Blank PredictDB identifier");return s;}
    private static String allele(ResultSet r,int column)throws SQLException,IOException {String s=id(r,column);if(!s.matches("[ACGT]+"))throw new IOException("Unresolved/non-uppercase PredictDB allele: "+s+"; provide a separately prepared resolved model");return s;}
    private static void text(String s)throws IOException {if(s.length()>1048576||s.chars().anyMatch(c->c==0||c=='\r'||c=='\n'||c=='\t'))throw new IOException("Unbounded/control-containing PredictDB text");}
    private static Double number(ResultSet r,int column,boolean nullable)throws SQLException,IOException {Object value=r.getObject(column);if(value==null&&nullable)return null;if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException("Nonfinite/non-numeric PredictDB value at column "+column);return n.doubleValue();}
    private static long count(ResultSet r,int column)throws SQLException,IOException {Object value=r.getObject(column);if(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long){long n=((Number)value).longValue();if(n>=0)return n;}else if(value instanceof Double||value instanceof Float){double n=((Number)value).doubleValue();if(Double.isFinite(n)&&n>=0&&n<=9007199254740991L&&n==Math.rint(n))return(long)n;}throw new IOException("Require a nonnegative exact annotated PredictDB SNP count");}
}

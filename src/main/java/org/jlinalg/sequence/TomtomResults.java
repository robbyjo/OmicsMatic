/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Complete original Tomtom 5.5.9 XML/TSV contract audit against independently
 * supplied input models. This component neither launches a process nor admits
 * a runtime. Original q-values remain original values, not a correction of the
 * displayed best matches. Unreported pairs retain unavailable values.
 */
public final class TomtomResults {
    private TomtomResults() { }
    private static final Set<String> DISTANCES=Set.of("ed","kullback","pearson","allr","sandelin","blic1","blic5","llr1","llr5");
    private static final String HEADER="Query_ID\tTarget_ID\tOptimal_offset\tp-value\tE-value\tq-value\tOverlap\tQuery_consensus\tTarget_consensus\tOrientation";
    private static final String RELEASE="Fri Nov 21 15:33:51 2025 -0800";

    /** A known source-input population needs a separate explicit contract; skip dependent admission. */
    public static final class UnsupportedInput extends IOException {
        private static final long serialVersionUID=1L;
        /** @param message named unsupported source boundary and required recovery */
        public UnsupportedInput(String message){super(message);}
    }

    /**
     * Independent original input motif before source pseudocounts; no normalization.
     * @param id original identifier
     * @param alternateId original alternate identifier, or empty
     * @param sites original site count, including zero (source fallback) and positive fractions at least one
     * @param probabilities original A/C/G/T rows
     * @param evalue original nonnegative E metadata, or null if absent
     * @param url original URL metadata, or empty
     */
    public record Motif(String id,String alternateId,double sites,double[][] probabilities,BigDecimal evalue,String url) {
        /** Checks the supported independent input domain and makes defensive copies. */
        public Motif {
            id=identifier(id);Objects.requireNonNull(alternateId);Objects.requireNonNull(url);
            if(!Double.isFinite(sites)||sites<0||sites>Integer.MAX_VALUE||sites>0&&sites<1)throw new IllegalArgumentException("Original Tomtom requires zero or a site count whose integer truncation is positive and representable");
            if(evalue!=null&&(evalue.signum()<0||Math.abs((long)evalue.scale())>4096))throw new IllegalArgumentException("Invalid bounded input E metadata");
            probabilities=copy(probabilities);if(probabilities.length<1||probabilities.length>512)throw new IllegalArgumentException("Require1..512 DNA positions");
            for(double[] row:probabilities)rawVector(row);
        }
        /** @return defensive original matrix copy */
        @Override public double[][] probabilities(){return copy(probabilities);}
    }

    /**
     * Complete independent file population; producer paths remain text.
     * @param source original recorded database source string; never opened
     * @param name original recorded database display name
     * @param motifs complete unique-ID original input models in file order
     * @param pseudocountBackground independently resolved source preprocessing background
     */
    public record Database(String source,String name,List<Motif> motifs,double[] pseudocountBackground) {
        /** Copies and validates the independently supplied complete population. */
        public Database {
            Objects.requireNonNull(source);Objects.requireNonNull(name);motifs=List.copyOf(motifs);
            if(source.isEmpty()||name.isEmpty()||motifs.isEmpty())throw new IllegalArgumentException("Require named nonempty original database");
            var ids=new HashSet<String>();for(Motif motif:motifs)if(!ids.add(motif.id()))throw new IllegalArgumentException("Duplicate IDs within a database require a separate source-exclusion ledger");
            pseudocountBackground=pseudocountBackground.clone();vector(pseudocountBackground,true);
        }
        /** @return defensive independently resolved background copy */
        @Override public double[] pseudocountBackground(){return pseudocountBackground.clone();}
    }

    /**
     * Expected source settings and independent models, not an arbitrary-command runner.
     * Backgrounds here must come from separately verified input preprocessing.
     * @param commandLine exact original display command, with staged producer names
     * @param distance closed original distance name
     * @param reverseComplement whether reverse target orientations were requested
     * @param thresholdKind qvalue or evalue
     * @param threshold original explicit finite cutoff
     * @param pseudocount original explicit nonnegative pseudocount
     * @param minimumOverlap original positive minimum overlap
     * @param internal whether the shorter motif must be fully contained
     * @param backgroundSource original model background source label
     * @param background independently resolved displayed scoring background
     * @param queries complete query files in original database order
     * @param targets complete target files in original database order
     */
    public record Expected(String commandLine,String distance,boolean reverseComplement,String thresholdKind,
            double threshold,double pseudocount,int minimumOverlap,boolean internal,String backgroundSource,
            double[] background,List<Database> queries,List<Database> targets) {
        /** Validates explicit settings and defensively copies populations. */
        public Expected {
            Objects.requireNonNull(commandLine);Objects.requireNonNull(backgroundSource);
            if(commandLine.isEmpty()||!DISTANCES.contains(distance)||!Set.of("qvalue","evalue").contains(thresholdKind)
                    ||!Double.isFinite(threshold)||threshold<0||thresholdKind.equals("qvalue")&&threshold>1
                    ||!Double.isFinite(pseudocount)||pseudocount<0||minimumOverlap<1||minimumOverlap>512)
                throw new IllegalArgumentException("Invalid explicit Tomtom contract settings");
            background=background.clone();vector(background,true);queries=List.copyOf(queries);targets=List.copyOf(targets);
            if(queries.isEmpty()||targets.isEmpty())throw new IllegalArgumentException("Require complete query and target databases");
        }
        /** @return defensive independently resolved scoring background */
        @Override public double[] background(){return background.clone();}
    }

    /**
     * Source 5.5.9 DNA preprocessing backgrounds; raw inputs remain unchanged.
     * @param original supplied unmodified background
     * @param firstQuery first query pseudocount and displayed scoring background
     * @param followingDatabases pseudocount background for following query/target files
     * @param normalizationPasses number of source epsilon passes for the first query
     */
    public record Backgrounds(double[] original,double[] firstQuery,double[] followingDatabases,int normalizationPasses) {
        /** Makes defensive copies. */
        public Backgrounds {original=original.clone();firstQuery=firstQuery.clone();followingDatabases=followingDatabases.clone();}
        /** @return original raw background copy */
        @Override public double[] original(){return original.clone();}
        /** @return first-query/scoring background copy */
        @Override public double[] firstQuery(){return firstQuery.clone();}
        /** @return following-database preprocessing background copy */
        @Override public double[] followingDatabases(){return followingDatabases.clone();}
    }

    /**
     * Reproduces the tested original 5.5.9 DNA background call order. The motif
     * query source is processed once by parser selection and once by explicit
     * query-source selection. An external source is selected afresh and processed
     * once. Following database readers copy that scoring background and process
     * the copy once more. Every pass averages complementary bases, adds 5e-7 to
     * each base and normalizes, as the original does. This does not normalize an
     * output matrix or establish input-file/runtime admission.
     * @param original explicit raw unit-sum A/C/G/T background
     * @param fromQueryMotif true for the default query motif source; false for an external background
     * @return immutable raw/processed backgrounds and explicit source pass count
     */
    public static Backgrounds backgrounds(double[] original,boolean fromQueryMotif) {
        double[] raw=original.clone();rawVector(raw);double[] selected=raw.clone();unit(selected);
        int passes=fromQueryMotif?2:1;for(int i=0;i<passes;i++)selected=backgroundPass(selected);
        return new Backgrounds(raw,selected,backgroundPass(selected),passes);
    }
    private static double[] backgroundPass(double[] input) {
        double at=(input[0]+input[3])/2,cg=(input[1]+input[2])/2;double[] values={at,cg,cg,at};
        double total=0.0000005*4;for(double value:values)total+=value;
        for(int i=0;i<4;i++)values[i]=(values[i]+0.0000005)/total;unit(values);return values;
    }
    private static void unit(double[] values){double total=0;for(double value:values)total+=value;if(total!=1)for(int i=0;i<values.length;i++)values[i]/=total;}

    /**
     * Applies original 5.5.9 preprocessing to a defensive matrix copy. Rows
     * outside the original inclusive 1 +/- 1e-5 interval are normalized first;
     * closer rounded rows retain their original mass. Zero site metadata uses
     * the original pseudocount fallback of 20; positive counts truncate to int.
     * Raw inputs are never rewritten. This is a component calculation, not
     * input-file, runtime or process admission.
     * @param motif original independently read model
     * @param pseudocount explicit nonnegative source pseudocount
     * @param background independently resolved positive unit-sum source background
     * @return defensive preprocessed probability matrix
     */
    public static double[][] preprocess(Motif motif,double pseudocount,double[] background) {
        Objects.requireNonNull(motif);double[] bg=background.clone();vector(bg,true);
        if(!Double.isFinite(pseudocount)||pseudocount<0)throw new IllegalArgumentException("Invalid original pseudocount");
        double[][] matrix=motif.probabilities();int sites=motif.sites()>0?(int)motif.sites():20;
        for(double[] row:matrix){
            double total=0;for(double value:row)total+=value;
            if(total<1.0-0.00001||total>1.0+0.00001)for(int b=0;b<4;b++)row[b]/=total;
            if(pseudocount!=0)for(int b=0;b<4;b++)row[b]=(row[b]*sites+pseudocount*bg[b])/(sites+pseudocount);
        }
        return matrix;
    }

    /**
     * Whole-output admission capacities, not heap guarantees.
     * @param xml complete XML capacities
     * @param maximumTsvBytes complete raw TSV capacity
     * @param maximumRows complete reported-row capacity
     * @param maximumPairs complete declared query-target Cartesian population capacity
     * @param maximumMatrixValues combined independent query/target probability scalar capacity
     */
    public record Limits(MemeXml.Limits xml,int maximumTsvBytes,int maximumRows,int maximumPairs,long maximumMatrixValues) {
        /** Requires positive bounded capacities. */
        public Limits {
            Objects.requireNonNull(xml);
            if(maximumTsvBytes<1||maximumTsvBytes>268435456||maximumRows<1||maximumRows>2000000||maximumPairs<1||maximumPairs>2000000||maximumMatrixValues<4||maximumMatrixValues>100000000)
                throw new IllegalArgumentException("Invalid explicit Tomtom output capacities");
        }
    }
    /**
     * Original decimal plus a source-format rounding envelope, not a calibrated confidence interval.
     * @param token unchanged artifact token
     * @param value parsed exact decimal
     * @param lower lower possible stored-value bound
     * @param upper upper possible stored-value bound
     * @param float32Stage whether the original TSV casts double to float32 before printing
     */
    public record Number(String token,BigDecimal value,BigDecimal lower,BigDecimal upper,boolean float32Stage) { }
    /**
     * Reported original best-orientation match. Database indices disambiguate duplicate IDs across files.
     * @param queryDatabase zero-based original query database
     * @param queryId original query ID
     * @param targetDatabase zero-based original target database
     * @param targetId original target ID
     * @param offset original query-start offset relative to oriented target coordinates
     * @param overlap original overlapping columns
     * @param reverseTarget original selected target orientation
     * @param pvalue original TSV P with source precision
     * @param evalue original TSV E with source precision
     * @param qvalue original TSV q with source precision
     * @param queryConsensus original float-comparison consensus
     * @param targetConsensus consensus of the selected target orientation
     */
    public record Match(int queryDatabase,String queryId,int targetDatabase,String targetId,int offset,int overlap,
            boolean reverseTarget,Number pvalue,Number evalue,Number qvalue,String queryConsensus,String targetConsensus) { }
    /**
     * Complete input-pair disposition; missing numeric values are never imputed.
     * @param queryDatabase zero-based original query database
     * @param queryId original query ID
     * @param targetDatabase zero-based original target database
     * @param targetId original target ID
     * @param reported whether the original output reports this pair
     */
    public record Pair(int queryDatabase,String queryId,int targetDatabase,String targetId,boolean reported) { }
    /**
     * Complete component receipt; process/runtime/input-preprocessing admission stays separate.
     * @param xml complete unchanged XML and raw identity
     * @param matches complete original reported rows in original order
     * @param pairs complete declared input-pair ledger
     * @param printedModels complete XML models, including all queries and reported targets only
     * @param printedMatrixValues complete audited scalar probabilities
     * @param warnings explicit interpretation boundaries
     */
    public record Audit(MemeXml.Document xml,List<Match> matches,List<Pair> pairs,int printedModels,long printedMatrixValues,List<String> warnings) {
        /** Makes defensive list copies. */
        public Audit {matches=List.copyOf(matches);pairs=List.copyOf(pairs);warnings=List.copyOf(warnings);}
    }

    /**
     * Complete original text table and independent input-pair ledger. There is
     * no original XML model/precision cross-check in a text-only run.
     * @param bytes complete raw UTF-8 table bytes
     * @param sha256 complete original table SHA-256
     * @param matches every reported original best-orientation match
     * @param pairs every declared query-target pair, without zero filling
     * @param warnings source precision, family and admission boundaries
     */
    public record TextAudit(int bytes,String sha256,List<Match> matches,List<Pair> pairs,List<String> warnings) {
        /** Makes defensive copies. */
        public TextAudit {matches=List.copyOf(matches);pairs=List.copyOf(pairs);warnings=List.copyOf(warnings);}
    }

    /**
     * Audits the complete original 5.5.9 text table against independently bound
     * settings and raw models. Does not rerun full-report mode or manufacture
     * XML. IDs must be unique across each side's databases because the original
     * text table has no database indices. Runtime/process admission is separate.
     * @param tsvFile complete captured text output
     * @param expected independently bound original settings and input populations
     * @param limits explicit complete-family capacities
     * @param cancellation caller cancellation check
     * @return whole text contract, retaining unavailable unreported values
     * @throws IOException on changed, incomplete, inconsistent or unsupported output/input
     */
    public static TextAudit readText(Path tsvFile,Expected expected,Limits limits,Runnable cancellation)throws IOException {
        Objects.requireNonNull(expected);Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);cancellation.run();
        long qc=expected.queries().stream().mapToLong(d->d.motifs().size()).sum(),tc=expected.targets().stream().mapToLong(d->d.motifs().size()).sum();
        long pairs=Math.multiplyExact(qc,tc);if(pairs>limits.maximumPairs())throw new IOException("Complete Tomtom text pair ledger exceeds declared capacity");
        long scalars=0;for(int side=0;side<2;side++){var group=side==0?expected.queries():expected.targets();
            var ids=new HashSet<String>();for(Database database:group)for(Motif motif:database.motifs()){
                cancellation.run();if(!ids.add(motif.id()))throw new UnsupportedInput("Original text rows lack database indices for duplicate IDs across databases; explicitly request a full report or skip this comparison");
                scalars=Math.addExact(scalars,motif.probabilities.length*4L);if(scalars>limits.maximumMatrixValues())throw new IOException("Combined Tomtom text input matrix capacity exceeded");
                if(side==0&&motif.probabilities.length>100)throw new UnsupportedInput("Original query width above100 needs a complete source-exclusion ledger");
                if(expected.distance().equals("allr")&&expected.pseudocount()==0)for(double[] row:motif.probabilities)for(double probability:row)if(probability==0)throw new UnsupportedInput("Original ALLR zero-probability exclusion/crash needs a complete source-disposition ledger; do not add an unrequested pseudocount");
            }
        }
        var queries=resolve(expected.queries(),expected.pseudocount());var targets=resolve(expected.targets(),expected.pseudocount());
        var qById=new LinkedHashMap<String,Resolved>();for(var query:queries)qById.put(query.input().id(),query);var tById=new LinkedHashMap<String,Resolved>();for(var target:targets)tById.put(target.input().id(),target);
        String text=table(tsvFile,limits.maximumTsvBytes());String[] lines=text.split("\n",-1);if(lines.length<5||!lines[0].equals(HEADER))throw new IOException("Invalid complete original Tomtom text header");
        var matches=new ArrayList<Match>();var comments=new ArrayList<String>();var reported=new HashSet<Key>();boolean footer=false;
        for(int i=1;i<lines.length;i++){
            cancellation.run();String line=lines[i];if(line.isEmpty()){footer=true;continue;}if(line.startsWith("#")){footer=true;comments.add(line);continue;}
            if(footer||matches.size()>=limits.maximumRows())throw new IOException("Late or excessive original Tomtom text rows");String[] values=line.split("\t",-1);if(values.length!=10)throw new IOException("Require all ten original Tomtom text columns");
            Resolved query=qById.get(values[0]),target=tById.get(values[1]);if(query==null||target==null)throw new IOException("Unknown original Tomtom text query/target ID");
            int offset=integer(values[2],-512,512),overlap=integer(values[6],1,512);boolean reverse=values[9].equals("-");
            if(!Set.of("+","-").contains(values[9])||reverse&&!expected.reverseComplement())throw new IOException("Original Tomtom text orientation differs from requested strands");
            int geometry=Math.max(0,Math.min(target.matrix().length,offset+query.matrix().length)-Math.max(0,offset));
            int shorter=Math.min(query.matrix().length,target.matrix().length);
            if(overlap!=geometry||overlap<Math.min(expected.minimumOverlap(),shorter)||expected.internal()&&overlap!=shorter)throw new IOException("Original text overlap/offset differs from independently known motif geometry");
            Number p=number(values[3],6,true,true),e=number(values[4],6,true,false),q=number(values[5],6,true,true);BigDecimal n=BigDecimal.valueOf(targets.size());
            if(p.lower().multiply(n).compareTo(e.upper())>0||p.upper().multiply(n).compareTo(e.lower())<0)throw new IOException("Original text E does not agree with P times the complete loaded target population");
            Number selected=expected.thresholdKind().equals("qvalue")?q:e;if(selected.lower().compareTo(new BigDecimal(expected.threshold()))>0)throw new IOException("Reported original text match exceeds the requested cutoff envelope");
            equal(values[7],consensus(query.matrix(),false),"text query consensus");equal(values[8],consensus(target.matrix(),reverse),"text oriented target consensus");
            Key key=new Key(query.database(),query.input().id(),target.database(),target.input().id());if(!reported.add(key))throw new IOException("Duplicate original Tomtom text pair");
            matches.add(new Match(query.database(),query.input().id(),target.database(),target.input().id(),offset,overlap,reverse,p,e,q,values[7],values[8]));
        }
        if(comments.size()!=3||!comments.get(0).startsWith("# Tomtom (Motif Comparison Tool): Version 5.5.9 compiled on ")||!comments.get(1).equals("# The format of this file is described at https://meme-suite.org/meme/doc/tomtom-output-format.html.")||!comments.get(2).equals("# "+expected.commandLine()))throw new IOException("Incomplete original Tomtom text footer/command");
        if(expected.thresholdKind().equals("qvalue")&&expected.threshold()==1&&matches.size()!=pairs)throw new IOException("Unfiltered original text comparison omitted a declared pair");
        var ledger=new ArrayList<Pair>();for(var query:queries)for(var target:targets){cancellation.run();ledger.add(new Pair(query.database(),query.input().id(),target.database(),target.input().id(),reported.contains(new Key(query.database(),query.input().id(),target.database(),target.input().id()))));}
        var warnings=new ArrayList<String>();warnings.add("TEXT_OUTPUT_CONTRACT_ONLY: no original XML is available to cross-check fitted model copies or independently printed double precision. Runtime, typed requested command/input binding, supervised process and complete requested text family remain separate admission gates. No full-report run or synthetic XML was substituted.");
        warnings.add("ORIGINAL_TEXT_PRECISION: P/E/q cast to float32 before six-significant-digit printing; zero may be rounding or underflow. Preserve literal tokens and source-format envelopes; these are not confidence intervals or statistical calibration certification.");
        warnings.add("ORIGINAL_Q_FAMILY: q-values use the original complete tested target-orientation population, not only reported best matches. Do not recalculate q-values from this table. Unreported pairs have unavailable values, never imputed zeros or biological absences.");
        if(targets.size()<50)warnings.add("ORIGINAL_SMALL_DATABASE_WARNING: fewer than50 target motifs do not support accurate original P-value calibration; preserve stderr and do not infer biological significance.");
        cancellation.run();if(!table(tsvFile,limits.maximumTsvBytes()).equals(text))throw new IOException("Original Tomtom text changed during complete audit");byte[] raw=text.getBytes(StandardCharsets.UTF_8);
        return new TextAudit(raw.length,sha256(raw),matches,ledger,warnings);
    }

    private static String sha256(byte[] raw){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}

    /**
     * Audits every XML/TSV model, reported match and declared pair. Original source
     * exclusions require a separately implemented input-disposition contract and
     * are rejected here; no omission is relabeled as a zero score or failed match.
     * @param xmlFile complete captured original XML
     * @param tsvFile complete captured original TSV
     * @param expected independently prepared settings/input population
     * @param limits explicit whole-family capacities
     * @param cancellation caller cancellation check
     * @return complete output-contract-only receipt
     * @throws IOException on incomplete, changed, inconsistent or unsupported output
     */
    public static Audit read(Path xmlFile,Path tsvFile,Expected expected,Limits limits,Runnable cancellation)throws IOException {
        Objects.requireNonNull(expected);Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);cancellation.run();
        long queryCount=expected.queries().stream().mapToLong(d->d.motifs().size()).sum(),targetCount=expected.targets().stream().mapToLong(d->d.motifs().size()).sum();
        long pairCount=Math.multiplyExact(queryCount,targetCount);
        if(pairCount>limits.maximumPairs())throw new IOException("Complete Tomtom pair ledger exceeds declared capacity");
        long matrixValues=0;for(List<Database> group:List.of(expected.queries(),expected.targets()))for(Database db:group)for(Motif motif:db.motifs()){
            cancellation.run();matrixValues=Math.addExact(matrixValues,motif.probabilities.length*4L);
            if(matrixValues>limits.maximumMatrixValues())throw new IOException("Combined Tomtom input matrix population exceeds declared scalar capacity");
            if(expected.distance().equals("allr")&&expected.pseudocount()==0)for(double[] row:motif.probabilities)for(double value:row)if(value==0)
                throw new UnsupportedInput("Original ALLR zero-probability inputs need a separate exclusion/failure ledger; singleton targets can crash. Preserve inputs; do not add an unrequested pseudocount or silently remove motifs");
        }
        for(Database db:expected.queries())for(Motif motif:db.motifs())if(motif.probabilities.length>100)throw new UnsupportedInput("Original Tomtom query width above100 needs a separately admitted exclusion ledger");
        List<Resolved> queries=resolve(expected.queries(),expected.pseudocount()),targets=resolve(expected.targets(),expected.pseudocount());
        var document=MemeXml.read(xmlFile,limits.xml(),cancellation);MemeXml.Node root=document.root();
        if(!root.name().equals("tomtom")||document.internalMemeDtd())throw new IOException("Require original Tomtom XML");
        keys(root,"version","release");equal(root.attribute("version"),"5.5.9","edition");equal(root.attribute("release"),RELEASE,"release");
        children(root,"model","query_dbs","target_dbs","queries","targets","matches","runtime");
        checkModel(root.one("model"),expected);
        databases(root.one("query_dbs"),expected.queries());databases(root.one("target_dbs"),expected.targets());
        var qmodels=models(root.one("queries"),queries,cancellation);var tmodels=models(root.one("targets"),targets,cancellation);
        if(qmodels.size()!=queries.size()||!new HashSet<>(qmodels).equals(new HashSet<>(queries)))throw new IOException("Original XML must retain every query model");
        List<XmlMatch> xmlMatches=matches(root.one("matches"),qmodels,tmodels,expected.reverseComplement(),limits.maximumRows());
        var reported=new HashSet<Key>();var reportedTargets=new HashSet<Resolved>();
        String text=table(tsvFile,limits.maximumTsvBytes());String[] lines=text.split("\n",-1);
        if(lines.length<5||!lines[0].equals(HEADER))throw new IOException("Invalid original Tomtom TSV header");
        var output=new ArrayList<Match>();var comments=new ArrayList<String>();boolean footer=false;int next=0;
        for(int i=1;i<lines.length;i++) {
            cancellation.run();String line=lines[i];if(line.isEmpty()){footer=true;continue;}
            if(line.startsWith("#")){footer=true;comments.add(line);continue;}
            if(footer||next>=xmlMatches.size()||output.size()>=limits.maximumRows())throw new IOException("Unexpected late/missing/excess Tomtom TSV row");
            String[] values=line.split("\t",-1);if(values.length!=10)throw new IOException("Require all ten original Tomtom TSV columns");
            XmlMatch original=xmlMatches.get(next++);Resolved query=qmodels.get(original.query()),target=tmodels.get(original.target());
            equal(values[0],query.input().id(),"query ID");equal(values[1],target.input().id(),"target ID");
            int offset=integer(values[2],-1024,1024),overlap=integer(values[6],1,512);if(offset!=original.offset())throw new IOException("XML/TSV offset differs");
            boolean reverse=values[9].equals("-");if(!reverse&&!values[9].equals("+")||reverse!=original.reverse())throw new IOException("XML/TSV original target orientation differs");
            if(reverse&&!expected.reverseComplement())throw new IOException("Unrequested reverse-complement target");
            int qw=query.matrix().length,tw=target.matrix().length;
            int calculated=Math.max(0,Math.min(tw,offset+qw)-Math.max(0,offset));
            if(overlap!=calculated||overlap<Math.min(expected.minimumOverlap(),Math.min(qw,tw))||expected.internal()&&overlap!=Math.min(qw,tw))throw new IOException("Original offset/overlap/containment contract differs");
            Number p=number(values[3],6,true,true),e=number(values[4],6,true,false),q=number(values[5],6,true,true);
            if(!intersects(p,original.p())||!intersects(e,original.e())||!intersects(q,original.q()))throw new IOException("Original XML/TSV probability precision intervals disagree");
            BigDecimal n=BigDecimal.valueOf(targets.size());if(p.lower().multiply(n).compareTo(e.upper())>0||p.upper().multiply(n).compareTo(e.lower())<0)throw new IOException("Original E does not agree with P times the complete loaded target population");
            Number selected=expected.thresholdKind().equals("qvalue")?q:e;
            if(selected.lower().compareTo(new BigDecimal(expected.threshold()))>0)throw new IOException("Reported Tomtom match is outside the requested threshold envelope");
            equal(values[7],consensus(query.matrix(),false),"query consensus");equal(values[8],consensus(target.matrix(),reverse),"oriented target consensus");
            var key=new Key(query.database(),query.input().id(),target.database(),target.input().id());if(!reported.add(key))throw new IOException("Duplicate original query/target database pair");
            reportedTargets.add(target);output.add(new Match(query.database(),query.input().id(),target.database(),target.input().id(),offset,overlap,reverse,p,e,q,values[7],values[8]));
        }
        if(next!=xmlMatches.size()||comments.size()!=3||!comments.get(0).startsWith("# Tomtom (Motif Comparison Tool): Version 5.5.9 compiled on ")
                ||!comments.get(1).equals("# The format of this file is described at https://meme-suite.org/meme/doc/tomtom-output-format.html.")
                ||!comments.get(2).equals("# "+expected.commandLine()))throw new IOException("Incomplete original Tomtom TSV footer or XML row population");
        if(!new HashSet<>(tmodels).equals(reportedTargets))throw new IOException("XML targets must equal the original reported-target union, not the entire loaded database");
        if(expected.thresholdKind().equals("qvalue")&&expected.threshold()==1&&output.size()!=pairCount)throw new IOException("Unfiltered original comparison omitted a declared input pair");
        var ledger=new ArrayList<Pair>();for(Resolved query:queries)for(Resolved target:targets){cancellation.run();ledger.add(new Pair(query.database(),query.input().id(),target.database(),target.input().id(),reported.contains(new Key(query.database(),query.input().id(),target.database(),target.input().id()))));}
        MemeXml.Node runtime=root.one("runtime");keys(runtime,"cycles","seconds");leaf(runtime);number(runtime.attribute("cycles"),15,false,false);number(runtime.attribute("seconds"),15,false,false);
        var warnings=new ArrayList<String>();warnings.add("OUTPUT_CONTRACT_ONLY: complete inputs/preprocessing, current runtime/host, supervised process completion, full report family and final bundle admission require separate gates. Producer path strings were not opened.");
        warnings.add("ORIGINAL PRECISION: XML match values pass through an original six-significant-digit temporary file and three-significant-digit XML formatting. TSV P/E/q cast to float32 before six-significant-digit printing. Preserve all literal values and rounding envelopes; these are not confidence intervals or bitwise statistical certification.");
        warnings.add("ORIGINAL Q FAMILY: q-values use the original complete tested orientation population, not just reported best-orientation rows. Do not recalculate q-values from this table or infer unreported numeric values.");
        warnings.add("SOURCE SMOOTHING AND CONSENSUS: positive fractional site counts use their integer truncation for pseudocounts. Consensus compares full double matrix entries against a float32 running maximum, including source tie behavior. Original backgrounds and copied input metadata require independent provenance.");
        if(targets.size()<50)warnings.add("ORIGINAL SMALL DATABASE WARNING: fewer than50 loaded target motifs do not support accurate source P-value calibration. Preserve original logs and do not infer biological significance from this fixture.");
        if(ledger.size()!=output.size())warnings.add("UNREPORTED PAIRS: the complete ledger includes original unreported comparisons with unavailable numeric values. Absence is not zero, an effect direction or a failed biological match.");
        if(output.stream().anyMatch(m->m.pvalue().value().signum()==0||m.qvalue().value().signum()==0))warnings.add("ZERO PRINTED PROBABILITY: original floating-point underflow or float32 conversion may print zero. This does not establish an exact mathematical zero; retain the independent XML representation.");
        cancellation.run();if(!table(tsvFile,limits.maximumTsvBytes()).equals(text)||!MemeXml.read(xmlFile,limits.xml(),cancellation).sha256().equals(document.sha256()))throw new IOException("Tomtom outputs changed during complete audit");
        long scalars=0;for(Resolved m:qmodels)scalars+=m.matrix().length*4L;for(Resolved m:tmodels)scalars+=m.matrix().length*4L;
        return new Audit(document,output,ledger,qmodels.size()+tmodels.size(),scalars,warnings);
    }

    private record Resolved(int database,Motif input,double[][] matrix) { }
    private record Key(int qdb,String qid,int tdb,String tid) { }
    private record XmlMatch(int query,int target,int offset,boolean reverse,Number p,Number e,Number q) { }
    private static List<Resolved> resolve(List<Database> databases,double pseudo) {
        var result=new ArrayList<Resolved>();
        for(int d=0;d<databases.size();d++)for(Motif motif:databases.get(d).motifs()) {
            double[][] matrix=preprocess(motif,pseudo,databases.get(d).pseudocountBackground());
            result.add(new Resolved(d,motif,matrix));
        }
        return result;
    }
    private static void checkModel(MemeXml.Node model,Expected expected)throws IOException {
        keys(model);children(model,"command_line","distance_measure","threshold","alphabet","strands","background","host","when");
        for(String name:List.of("command_line","strands","host","when"))keys(model.one(name));
        equal(value(model.one("command_line")),expected.commandLine(),"complete original command");
        MemeXml.Node distance=model.one("distance_measure");keys(distance,"value");leaf(distance);equal(distance.attribute("value"),expected.distance(),"distance");
        MemeXml.Node threshold=model.one("threshold");keys(threshold,"type");equal(threshold.attribute("type"),expected.thresholdKind(),"threshold type");
        contains(number(value(threshold),6,false,false),expected.threshold(),"threshold");
        equal(value(model.one("strands")),expected.reverseComplement()?"both":"forward","requested strands");
        MemeXml.Node background=model.one("background");keys(background,"from","A","C","G","T");leaf(background);equal(background.attribute("from"),expected.backgroundSource(),"background source");
        for(int b=0;b<4;b++)contains(number(background.attribute("ACGT".substring(b,b+1)),6,false,true),expected.background()[b],"scoring background");
        alphabet(model.one("alphabet"));if(value(model.one("host")).isEmpty()||value(model.one("when")).isEmpty())throw new IOException("Missing original recorded host/time metadata");
    }
    private static void databases(MemeXml.Node container,List<Database> expected)throws IOException {
        keys(container);if(container.children().size()!=expected.size())throw new IOException("Original database population differs");
        for(int i=0;i<expected.size();i++) {
            MemeXml.Node db=container.children().get(i);equal(db.name(),"db","database element");keys(db,"source","name","loaded","excluded","last_mod_date");leaf(db);
            equal(db.attribute("source"),expected.get(i).source(),"database source");equal(db.attribute("name"),expected.get(i).name(),"database name");
            if(integer(db.attribute("loaded"),0,2000000)!=expected.get(i).motifs().size()||integer(db.attribute("excluded"),0,2000000)!=0)throw new IOException("Original source exclusions or loaded populations need a separately admitted complete input ledger");
            if(db.attribute("last_mod_date").isEmpty())throw new IOException("Missing original database timestamp");
        }
    }
    private static List<Resolved> models(MemeXml.Node container,List<Resolved> expected,Runnable check)throws IOException {
        keys(container);var result=new ArrayList<Resolved>();var seen=new HashSet<String>();int lastDb=-1;String lastId="";
        var byId=new LinkedHashMap<String,Resolved>();for(Resolved model:expected)byId.put(model.database()+":"+model.input().id(),model);
        for(MemeXml.Node node:container.children()) {
            check.run();equal(node.name(),"motif","model element");int db=integer(node.attribute("db"),0,2000000);String id=node.attribute("id");
            if(db<lastDb||db==lastDb&&compareIds(id,lastId)<=0)throw new IOException("Original XML model database/ID order differs");lastDb=db;lastId=id;
            Resolved model=byId.get(db+":"+id);if(model==null)throw new IOException("Unknown original XML model");
            if(!seen.add(db+":"+id))throw new IOException("Repeated original XML model");
            var allowed=new HashSet<>(Set.of("db","id","length"));Motif input=model.input();
            if(input.sites()>0)allowed.add("nsites");
            if(!input.alternateId().isEmpty()){allowed.add("alt");equal(node.attribute("alt"),input.alternateId(),"alternate ID");}
            if(!input.url().isEmpty()){allowed.add("url");equal(node.attribute("url"),input.url(),"copied URL");}
            if(input.evalue()!=null&&input.evalue().signum()>0){allowed.add("evalue");Number ev=number(node.attribute("evalue"),2,false,false);if(input.evalue().compareTo(ev.lower())<0||input.evalue().compareTo(ev.upper())>0)throw new IOException("Copied original input E metadata differs");}
            if(!node.attributes().keySet().equals(allowed))throw new IOException("Unexpected original XML model attributes");
            if(integer(node.attribute("length"),1,512)!=model.matrix().length||node.children().size()!=model.matrix().length)throw new IOException("Incomplete original model positions");
            if(input.sites()>0)contains(number(node.attribute("nsites"),6,false,false),input.sites(),"original fractional nsites");
            for(int i=0;i<model.matrix().length;i++) {
                MemeXml.Node row=node.children().get(i);equal(row.name(),"pos","probability position");keys(row,"A","C","G","T");leaf(row);
                BigDecimal lower=BigDecimal.ZERO,upper=BigDecimal.ZERO;
                for(int b=0;b<4;b++){Number printed=number(row.attribute("ACGT".substring(b,b+1)),6,false,true);contains(printed,model.matrix()[i][b],"resolved original probability");lower=lower.add(printed.lower());upper=upper.add(printed.upper());}
                BigDecimal mass=BigDecimal.ZERO;for(double probability:model.matrix()[i])mass=mass.add(BigDecimal.valueOf(probability));
                if(lower.compareTo(mass)>0||upper.compareTo(mass)<0)throw new IOException("Rounded original model cannot represent the independently resolved source row mass");
            }
            result.add(model);
        }
        return result;
    }
    private static List<XmlMatch> matches(MemeXml.Node container,List<Resolved> queries,List<Resolved> targets,boolean reverse,int maximum)throws IOException {
        keys(container);var result=new ArrayList<XmlMatch>();var seen=new HashSet<Integer>();
        for(MemeXml.Node query:container.children()) {
            equal(query.name(),"query","match query");keys(query,"idx");int qi=integer(query.attribute("idx"),0,queries.size()-1);if(!seen.add(qi))throw new IOException("Repeated original query match group");
            for(MemeXml.Node target:query.children()) {
                if(result.size()>=maximum)throw new IOException("Original match capacity exceeded");equal(target.name(),"target","match target");
                if(reverse)keys(target,"idx","rc","off","pv","ev","qv");else keys(target,"idx","off","pv","ev","qv");leaf(target);
                String orientation=reverse?target.attribute("rc"):"n";if(!Set.of("y","n").contains(orientation))throw new IOException("Unknown original target orientation");
                result.add(new XmlMatch(qi,integer(target.attribute("idx"),0,targets.size()-1),integer(target.attribute("off"),-1024,1024),orientation.equals("y"),number(target.attribute("pv"),3,false,true),number(target.attribute("ev"),3,false,false),number(target.attribute("qv"),3,false,true)));
            }
        }
        return result;
    }
    private static Number number(String token,int digits,boolean float32,boolean probability)throws IOException {
        if(token.length()>1024||!token.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))throw new IOException("Invalid original nonnegative decimal token");
        BigDecimal value;try{value=new BigDecimal(token);}catch(NumberFormatException invalid){throw new IOException("Invalid bounded original decimal",invalid);}
        if(Math.abs((long)value.scale())>4096||value.signum()<0||probability&&value.compareTo(BigDecimal.ONE)>0)throw new IOException("Original decimal outside its supported domain");
        BigDecimal half=value.signum()==0?BigDecimal.ZERO:BigDecimal.ONE.scaleByPowerOfTen(value.precision()-value.scale()-digits).divide(BigDecimal.valueOf(2));
        if(float32){float f=value.floatValue();if(!Float.isFinite(f))throw new IOException("Original TSV float32 overflow");half=half.add(new BigDecimal((double)Math.ulp(f)).divide(BigDecimal.valueOf(2)));}
        BigDecimal lower=value.subtract(half).max(BigDecimal.ZERO),upper=value.add(half);if(probability)upper=upper.min(BigDecimal.ONE);
        return new Number(token,value,lower,upper,float32);
    }
    private static void contains(Number interval,double expected,String field)throws IOException {
        if(!Double.isFinite(expected)||interval.lower().compareTo(new BigDecimal(expected))>0||interval.upper().compareTo(new BigDecimal(expected))<0)throw new IOException("Original "+field+" is outside its source-format rounding envelope");
    }
    private static boolean intersects(Number a,Number b){return a.lower().compareTo(b.upper())<=0&&b.lower().compareTo(a.upper())<=0;}
    private static String consensus(double[][] matrix,boolean reverse) {
        var result=new StringBuilder();for(int row=0;row<matrix.length;row++) {
            double[] values=matrix[reverse?matrix.length-1-row:row];int best=0;float high=(float)values[reverse?3:0];
            for(int b=1;b<4;b++){double value=values[reverse?3-b:b];if(value>high){high=(float)value;best=b;}}
            result.append("ACGT".charAt(best));
        }return result.toString();
    }
    private static String table(Path path,int maximum)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maximum)throw new IOException("Require bounded regular original Tomtom TSV");
        byte[] raw;try(var input=Files.newInputStream(path)){raw=input.readNBytes(maximum+1);}if(raw.length>maximum||raw.length==0||raw[raw.length-1]!='\n')throw new IOException("Incomplete/excessive original TSV");
        try{String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();if(value.indexOf('\r')>=0||value.indexOf('\0')>=0)throw new IOException("Require unchanged original LF TSV");return value;}
        catch(CharacterCodingException invalid){throw new IOException("Original TSV is not complete UTF-8",invalid);}
    }
    private static void alphabet(MemeXml.Node node)throws IOException {
        keys(node,"name","like");equal(node.attribute("name"),"DNA","alphabet");equal(node.attribute("like"),"dna","alphabet type");
        String[] symbols={"A","C","G","T","N","V","H","D","B","M","R","W","S","Y","K"};
        String[] equivalents={"","","","","ACGT","ACG","ACT","AGT","CGT","AC","AG","AT","CG","CT","GT"};
        if(node.children().size()!=symbols.length)throw new IOException("Incomplete original DNA alphabet");
        for(int i=0;i<symbols.length;i++) {
            MemeXml.Node letter=node.children().get(i);equal(letter.name(),"letter","alphabet letter");leaf(letter);equal(letter.attribute("id"),symbols[i],"alphabet ID");equal(letter.attribute("symbol"),symbols[i],"alphabet symbol");
            var attributes=new HashSet<>(Set.of("id","symbol","name"));
            if(i<4){attributes.add("colour");attributes.add("complement");equal(letter.attribute("complement"),"TGCA".substring(i,i+1),"DNA complement");if(i==3){attributes.add("aliases");equal(letter.attribute("aliases"),"U","T alias");}}
            else{attributes.add("equals");equal(letter.attribute("equals"),equivalents[i],"ambiguous DNA composition");if(i==4){attributes.add("aliases");equal(letter.attribute("aliases"),"X.","N aliases");}}
            if(!letter.attributes().keySet().equals(attributes)||letter.attribute("name").isEmpty())throw new IOException("Unexpected original DNA letter metadata");
        }
    }
    private static int integer(String token,int minimum,int maximum)throws IOException {
        if(token.length()>12||!token.matches("-?(?:0|[1-9][0-9]*)"))throw new IOException("Invalid original integer");try{int value=Integer.parseInt(token);if(value<minimum||value>maximum)throw new IOException("Original integer outside declared capacity");return value;}catch(NumberFormatException invalid){throw new IOException("Original integer overflow",invalid);}
    }
    private static String identifier(String value){if(value==null||value.isEmpty()||value.getBytes(StandardCharsets.UTF_8).length>200||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||c>=0xd800&&c<=0xdfff||c>0x10ffff||c==0xfffe||c==0xffff))throw new IllegalArgumentException("Require bounded original UTF-8 motif ID without whitespace or control characters");return value;}
    private static int compareIds(String a,String b){return java.util.Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static void rawVector(double[] values){if(values.length!=4)throw new IllegalArgumentException("Require A/C/G/T vector");double sum=0;for(double value:values){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Invalid original DNA probability");sum+=value;}if(Math.abs(sum-1)>0.1)throw new IllegalArgumentException("Raw DNA probability mass exceeds original text parser tolerance");}
    private static void vector(double[] values,boolean positive){if(values.length!=4)throw new IllegalArgumentException("Require A/C/G/T vector");double sum=0;for(double v:values){if(!Double.isFinite(v)||v<0||v>1||positive&&v==0)throw new IllegalArgumentException("Invalid original DNA probability");sum+=v;}if(Math.abs(sum-1)>1e-12)throw new IllegalArgumentException("Independent input probabilities must sum to one without normalization");}
    private static double[][] copy(double[][] source){Objects.requireNonNull(source);double[][] copy=new double[source.length][];for(int i=0;i<source.length;i++)copy[i]=Objects.requireNonNull(source[i]).clone();return copy;}
    private static void keys(MemeXml.Node node,String... keys)throws IOException{if(!node.attributes().keySet().equals(Set.of(keys)))throw new IOException("Unexpected complete XML attributes: "+node.name());}
    private static void children(MemeXml.Node node,String... names)throws IOException{if(!node.children().stream().map(MemeXml.Node::name).toList().equals(List.of(names)))throw new IOException("Unexpected complete original XML sections: "+node.name());}
    private static void leaf(MemeXml.Node node)throws IOException{if(!node.children().isEmpty()||!node.text().isBlank())throw new IOException("Unexpected content in original XML leaf "+node.name());}
    private static String value(MemeXml.Node node)throws IOException{if(!node.children().isEmpty())throw new IOException("Unexpected original XML scalar children");return node.text();}
    private static void equal(String actual,String expected,String field)throws IOException{if(!actual.equals(expected))throw new IOException("Original "+field+" differs");}
}

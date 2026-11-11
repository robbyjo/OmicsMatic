/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.phylogeny.*;

/** Complete single-alignment preparation, original execution, output audit and identity restoration. */
final class IqtreeAnalysis {
    private IqtreeAnalysis() { }
    record Prepared(PhylogeneticAlignment alignment,NewickTree tree,Path fasta,Path treeFile,
                    Map<Path,IqtreeFiles.Identity> originalFiles,IqtreeBundle.Inventory preparedFiles) {
        Prepared {originalFiles=Map.copyOf(originalFiles);}
        void verify(Path inputs,long bytes,long bundleBytes,int files,Runnable cancellation)throws IOException {
            for(var e:originalFiles.entrySet())if(!e.getValue().equals(IqtreeFiles.identity(e.getKey(),bytes,cancellation)))throw new IOException("IQ-TREE original input changed: "+e.getKey());
            if(!preparedFiles.equals(IqtreeBundle.inventory(inputs,bundleBytes,files,cancellation)))throw new IOException("IQ-TREE prepared input/census changed");
        }
    }
    record Result(IqtreeReport.Result report,IqtreeSiteLikelihoods.Audit sites,IqtreeProcess.Result process,
                  Path restoredTree,long bootstrapTrees,long supportEdges,List<String> warnings) {
        Result {warnings=List.copyOf(warnings);}
    }
    static Result run(IqtreeRecipe.Spec spec,IqtreeRuntime.Context runtime,Path work,Runnable cancellation)throws IOException {
        Path inputs=work.resolve("inputs");IqtreeBundle.create(inputs);Prepared prepared=prepare(spec,runtime,inputs,cancellation);
        var limits=spec.limits();var warnings=new ArrayList<String>();
        IqtreeProcess.Check integrity=()->{cancellation.run();spec.verify();prepared.verify(inputs,limits.inputBytes(),limits.bundleBytes(),limits.bundleFiles(),cancellation);};
        integrity.verify();Path process=work.resolve("process");
        List<String> command=spec.arguments(prepared.fasta(),prepared.treeFile(),process.resolve("result"),prepared.alignment().originalToOpaque());
        var executed=IqtreeProcess.run(runtime,spec.parameters().threads(),command,process,limits.process(),integrity,cancellation);
        var originalOutputs=IqtreeBundle.inventory(process,limits.process().workspaceBytes(),limits.process().fileCount(),cancellation);
        Path cache=process.resolve("result.model.gz");
        var report=IqtreeReport.read(process.resolve("result.iqtree"),Files.exists(cache)?cache:null,process.resolve("result.log"),limits.report(),cancellation);
        var alignment=prepared.alignment();String unit=switch(alignment.kind()){case DNA->"nucleotide";case AMINO_ACID->"amino-acid";case CODON_STANDARD->"codon";};
        if(report.taxa()!=alignment.taxa().size()||report.sites()!=alignment.modelSites()||!report.unit().equals(unit))throw new IOException("IQ-TREE reported dimensions/alphabet differ from the complete input");
        if(!report.ignoredIdenticalTaxa().isEmpty()) {
            if(spec.parameters().identicalSequences()==IqtreeRecipe.IdenticalSequences.KEEP)throw new IOException("Original runtime removed rows despite the explicit KEEP policy");
            for(var entry:report.ignoredIdenticalTaxa().entrySet()) {
                String original=alignment.opaqueToOriginal().get(entry.getKey()),representative=alignment.opaqueToOriginal().get(entry.getValue());
                if(original==null||representative==null||entry.getKey().equals(entry.getValue())||report.ignoredIdenticalTaxa().containsKey(entry.getValue())||!alignment.sourceEquivalent(original,representative))throw new IOException("Source removed an unknown or nonequivalent input taxon");
            }
            warnings.add("SOURCE ANALYZED POPULATION: the requested SOURCE_REMOVE_RESTORE policy let the original runtime omit "+report.ignoredIdenticalTaxa().size()+" source-equivalent rows during fitting. All input rows and final restored taxa are retained. The analyzed population and original relationships are explicit; native JC69 checks target that fitted population.");
        }
        var analyzed=new ArrayList<Map<String,Object>>();int index=0;
        if(!report.ignoredIdenticalTaxa().isEmpty()) {
            Path unique=process.resolve("result.uniqueseq.phy");
            var sourceData=PhylogeneticAlignment.readPhylip(guarded(unique,limits.process().fileBytes(),cancellation),alignment.kind(),PhylogeneticAlignment.Layout.SEQUENTIAL,PhylogeneticAlignment.NameField.RELAXED,false,limits.alignment());
            var expected=new HashSet<>(alignment.opaqueToOriginal().keySet());expected.removeAll(report.ignoredIdenticalTaxa().keySet());
            if(!sourceData.taxa().equals(expected)||sourceData.columns()!=alignment.columns())throw new IOException("Source analyzed alignment changed the declared kept rows/sites");
            for(var row:sourceData.rows()) {
                String original=alignment.opaqueToOriginal().get(row.taxon());if(!alignment.sourceEquivalent(original,sourceData,row.taxon()))throw new IOException("Source analyzed alignment changed an admitted emission-state cell");
                analyzed.add(Map.of("selection_tree_index",index++,"opaque_taxon",row.taxon(),"original_taxon",original));
            }
        } else for(var row:alignment.rows()) {String opaque=alignment.originalToOpaque().get(row.taxon());analyzed.add(Map.of("selection_tree_index",index++,"opaque_taxon",opaque,"original_taxon",row.taxon()));}
        IqtreeBundle.write(work.resolve("analyzed-population.json"),Map.of("input_taxa",alignment.taxa().size(),"analyzed_taxa",analyzed,"source_ignored_relationships",report.ignoredIdenticalTaxa(),"policy",spec.parameters().identicalSequences()));
        if(spec.model().selection()==IqtreeRecipe.ModelSelection.FIXED) {
            if(report.selection()!=null||!sameModel(spec.model().name(),report.model(),alignment.kind()))throw new IOException("Original fitted model differs from the fixed requested model");
            String request=spec.model().name();
            if(request.contains("+FO")&&!report.frequencyDescription().toLowerCase(Locale.ROOT).contains("maximum")&&!report.frequencyDescription().toLowerCase(Locale.ROOT).contains("ml"))warnings.add("Requested frequency optimization is not explicitly identified by the original report; inspect its exact frequency description and original outputs.");
            if(request.contains("+F")&&!request.contains("+FQ")&&report.frequencyDescription().contains("equal frequencies"))warnings.add("ORIGINAL MODEL FREQUENCY SETTING NOT APPLIED: requested "+request+", but the original reports "+report.model()+" with equal frequencies. Source values are retained. Choose a model that supports the intended frequency treatment; do not interpret this as an empirical-frequency fit.");
        } else {
            if(report.selection()==null||!report.selection().criterion().equals(spec.model().criterion().name()))throw new IOException("Source model-selection criterion differs from its explicit recipe");
            if(!spec.model().candidates().isEmpty())for(var row:report.selection().candidates()) {
                if(spec.model().candidates().stream().noneMatch(name->base(name,alignment.kind()).equals(base(row.model(),alignment.kind()))))throw new IOException("Source fitted a candidate outside the explicitly declared model set");
            }
        }
        warnings.addAll(report.warnings());
        Path restored=work.resolve("tree.nwk");NewickTree tree=restoreOne(process.resolve("result.treefile"),restored,alignment,limits,cancellation);
        double total=0,treePrecision=0;for(var node:tree.nodes())if(node.parent()>=0){total+=node.branchLength();treePrecision+=new BigDecimal(tree.branchLengthText(node.id())).ulp().abs().doubleValue()/2+Math.ulp(total);}
        if(!Double.isFinite(total)||Math.abs(total-report.treeLength().value())>report.treeLength().half()+treePrecision)throw new IOException("Source tree length differs from the complete written tree");
        if(prepared.tree()!=null&&spec.operation()==IqtreeRecipe.Operation.EVALUATE) {
            if(!prepared.tree().bipartitions(limits.bootstrap().perTreeMemberships()).equals(tree.bipartitions(limits.bootstrap().perTreeMemberships())))throw new IOException("Original fixed-topology evaluation changed the supplied unrooted topology");
            if(spec.tree().mode()==IqtreeRecipe.TreeMode.FIX_TOPOLOGY_AND_LENGTHS)fixedLengths(prepared.tree(),tree,limits.bootstrap().perTreeMemberships(),warnings);
        }
        double[] independent=null;
        if(alignment.kind()==PhylogeneticAlignment.Kind.DNA&&report.model().equals("JC")) {
            NewickTree evaluated=prepared.tree()!=null&&spec.tree().mode()==IqtreeRecipe.TreeMode.FIX_TOPOLOGY_AND_LENGTHS?prepared.tree():tree;
            var opaque=new LinkedHashMap<String,String>();for(var row:alignment.jc69Sequences().entrySet()){String id=alignment.originalToOpaque().get(row.getKey());opaque.put(id,report.ignoredIdenticalTaxa().containsKey(id)?"N".repeat(alignment.modelSites()):row.getValue());}
            independent=JukesCantorLikelihood.evaluate(evaluated,opaque).siteLogLikelihoods();
        }
        var sites=IqtreeSiteLikelihoods.audit(process.resolve("result.sitelh"),limits.process().fileBytes(),alignment.modelSites(),report.scores().logLikelihood().text(),independent,independent==null?0:1e-7,cancellation);
        if(!sites.totalAgrees()||sites.nativeDisagreements()!=0)throw new IOException("Complete source site/total or native fixed-JC69 audit disagrees");
        if(sites.positiveSourceCells()>0)warnings.add("SIGNED SOURCE ROUNDOFF RETAINED: "+sites.positiveSourceCells()+" site values are positive. Inspect the exact original tokens and model/missing-state context; values were not clamped.");
        Files.copy(process.resolve("result.sitelh"),work.resolve("site-likelihoods.txt"));
        long bootstrapTrees=0,supportEdges=0;
        if(spec.support().bootstrap()!=IqtreeRecipe.Bootstrap.NONE) {
            String suffix=spec.support().bootstrap()==IqtreeRecipe.Bootstrap.STANDARD?"boottrees":"ufboot";Path original=process.resolve("result."+suffix);
            var counts=BootstrapSupport.count(guarded(original,limits.process().fileBytes(),cancellation),alignment.opaqueToOriginal().keySet(),spec.support().replicates(),spec.support().writeBranchLengths(),limits.bootstrap());
            bootstrapTrees=counts.replicates();restoreCollection(original,work.resolve("bootstrap-trees.nwk"),alignment,spec.support().writeBranchLengths(),spec.support().replicates(),limits,cancellation);
            var consensus=restoreOne(process.resolve("result.contree"),work.resolve("consensus-tree.nwk"),alignment,limits,cancellation);
            var supportRows=new ArrayList<Map<String,Object>>();
            for(var entry:Map.of("tree",tree,"consensus",consensus).entrySet()) {
                int columns=entry.getKey().equals("tree")&&spec.support().shAlrtReplicates()>0?2:1;
                for(var comparison:BootstrapSupport.compare(entry.getValue(),counts,columns,columns-1,limits.bootstrap().perTreeMemberships())) {
                    if(!comparison.agreesAtPrintedPrecision())throw new IOException("Reported bootstrap support disagrees with the complete replicate collection");
                    supportRows.add(Map.of("tree_role",entry.getKey(),"child_node",comparison.childNode(),"original_taxa_split",comparison.split().stream().map(alignment.opaqueToOriginal()::get).sorted().toList(),
                            "source_support_columns",comparison.reportedColumns(),"bootstrap_column_zero_based",comparison.bootstrapColumn(),"occurrences",comparison.occurrences(),"replicates",comparison.replicates(),"percentage",comparison.percentage()));supportEdges++;
                }
            }
            IqtreeBundle.write(work.resolve("support-audit.json"),Map.of("replicates",bootstrapTrees,"edges",supportRows,"sh_alrt_independently_reproduced",false));
        } else if(spec.support().shAlrtReplicates()>0) {
            for(int node:tree.edgeBipartitions(limits.bootstrap().perTreeMemberships()).keySet()) {
                String value=tree.nodes().get(node).label();try{double number=Double.parseDouble(value);if(!Double.isFinite(number)||number<0||number>100)throw new NumberFormatException();}catch(RuntimeException invalid){throw new IOException("Missing/invalid original SH-aLRT support",invalid);}supportEdges++;
            }
            warnings.add("SH-aLRT percentages are retained and range-checked; native bootstrap counts do not independently reproduce SH-aLRT.");
        }
        if(report.selection()!=null)writeCandidates(work.resolve("model-candidates.tsv"),report.selection());
        warnings.add("Original rooting and branch-unit notes remain in process/result.iqtree. A displayed first taxon or outgroup does not independently establish a biological root. Tree-search accuracy and non-JC fitted likelihoods are not independently certified.");
        IqtreeBundle.write(work.resolve("output-audit.json"),Map.of("report",report,"sites",sites,"restored_tree",restored.getFileName().toString(),"bootstrap_trees",bootstrapTrees,"support_edges",supportEdges,"scientific_result_admitted",false));
        integrity.verify();runtime.verify();if(!originalOutputs.equals(IqtreeBundle.inventory(process,limits.process().workspaceBytes(),limits.process().fileCount(),cancellation)))throw new IOException("Original IQ-TREE outputs changed during complete audits");
        IqtreeBundle.inventory(work,limits.bundleBytes(),limits.bundleFiles(),cancellation);
        return new Result(report,sites,executed,restored,bootstrapTrees,supportEdges,warnings);
    }
    private static Prepared prepare(IqtreeRecipe.Spec spec,IqtreeRuntime.Context runtime,Path inputs,Runnable cancellation)throws IOException {
        var limits=spec.limits();var identities=new LinkedHashMap<Path,IqtreeFiles.Identity>();Path source=spec.alignment().path();
        if(spec.source().startsWith(inputs.getParent())||spec.source().startsWith(runtime.home()))throw new IOException("IQ-TREE recipe must remain outside runtime/output trees");
        for(Path path:spec.tree()==null?List.of(source):List.of(source,spec.tree().path())) {
            if(path.startsWith(inputs.getParent())||path.startsWith(runtime.home()))throw new IOException("IQ-TREE original inputs must remain outside runtime/output trees");identities.put(path,IqtreeFiles.identity(path,limits.inputBytes(),cancellation));
        }
        var a=spec.alignment();PhylogeneticAlignment alignment=a.format()==IqtreeRecipe.Format.FASTA?
                PhylogeneticAlignment.readFasta(guarded(source,limits.inputBytes(),cancellation),a.kind(),a.headerPolicy(),limits.alignment()):
                PhylogeneticAlignment.readPhylip(guarded(source,limits.inputBytes(),cancellation),a.kind(),a.layout(),a.nameField(),a.namedContinuations(),limits.alignment());
        alignment.requireIqtreeVersion(runtime.version());
        Files.copy(source,inputs.resolve("alignment.original"));Path fasta=inputs.resolve("alignment.fasta");
        try(var writer=Files.newBufferedWriter(fasta,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){alignment.writeFasta(writer,true,80);}
        var rowLedger=new ArrayList<Map<String,Object>>();for(var row:alignment.rows())rowLedger.add(Map.of("original_taxon",row.taxon(),"original_header",row.originalHeader(),"opaque_taxon",alignment.originalToOpaque().get(row.taxon())));
        var symbols=new TreeMap<String,Long>();for(var e:alignment.statistics().symbols().entrySet())symbols.put(String.valueOf(e.getKey()),e.getValue());
        IqtreeBundle.write(inputs.resolve("taxa.json"),Map.of("rows",rowLedger,"columns",alignment.columns(),"model_sites",alignment.modelSites(),"kind",alignment.kind(),"raw_character_cells",alignment.statistics().rawCells(),"symbols",symbols,
                "source_identical_sequence_policy",spec.parameters().identicalSequences()));
        NewickTree tree=null;Path treeFile=null;
        if(spec.tree()!=null) {
            var reader=new NewickReader(guarded(spec.tree().path(),limits.inputBytes(),cancellation),new NewickReader.Limits(limits.tree(),limits.tree().characters(),1));
            try(reader){tree=reader.nextTree();if(tree==null||reader.nextTree()!=null)throw new IOException("Require exactly one complete declared input tree");}
            if(!reader.isComplete()||!tree.taxa().equals(alignment.taxa()))throw new IOException("Input tree does not contain the complete exact alignment taxon set");
            if(spec.tree().mode()==IqtreeRecipe.TreeMode.FIX_TOPOLOGY_AND_LENGTHS)tree.requireMetricLengths();
            Files.copy(spec.tree().path(),inputs.resolve("tree.original"));treeFile=inputs.resolve("tree.nwk");
            Files.writeString(treeFile,tree.relabelLeaves(alignment.originalToOpaque(),limits.tree().characters())+reader.trailingText(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            tree=NewickTree.parse(Files.readString(treeFile,StandardCharsets.UTF_8),limits.tree());
        }
        IqtreeBundle.write(inputs.resolve("original-identities.json"),identities.entrySet().stream().map(e->Map.of("path",e.getKey(),"identity",e.getValue())).toList());
        var prepared=new Prepared(alignment,tree,fasta,treeFile,identities,IqtreeBundle.inventory(inputs,limits.bundleBytes(),limits.bundleFiles(),cancellation));prepared.verify(inputs,limits.inputBytes(),limits.bundleBytes(),limits.bundleFiles(),cancellation);return prepared;
    }
    private static NewickTree restoreOne(Path source,Path output,PhylogeneticAlignment alignment,IqtreeRecipe.Capacities limits,Runnable cancellation)throws IOException {
        var reader=new NewickReader(guarded(source,limits.process().fileBytes(),cancellation),new NewickReader.Limits(limits.tree(),limits.tree().characters(),1));NewickTree tree;
        try(reader){tree=reader.nextTree();if(tree==null||reader.nextTree()!=null)throw new IOException("Expected exactly one complete original output tree");}
        if(!reader.isComplete()||!tree.taxa().equals(alignment.opaqueToOriginal().keySet()))throw new IOException("Output tree has an incomplete/unexpected taxon set");tree.requireMetricLengths();
        Files.writeString(output,tree.relabelLeaves(alignment.opaqueToOriginal(),limits.tree().characters())+reader.trailingText(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);return tree;
    }
    private static void restoreCollection(Path source,Path output,PhylogeneticAlignment alignment,boolean lengths,long expected,IqtreeRecipe.Capacities limits,Runnable cancellation)throws IOException {
        var reader=new NewickReader(guarded(source,limits.process().fileBytes(),cancellation),limits.bootstrap().collection());long count=0;
        try(reader;var writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            NewickTree tree;while((tree=reader.nextTree())!=null){cancellation.run();if(!tree.taxa().equals(alignment.opaqueToOriginal().keySet()))throw new IOException("Incomplete bootstrap taxon set during restoration");if(lengths)tree.requireMetricLengths();writer.write(tree.relabelLeaves(alignment.opaqueToOriginal(),limits.tree().characters()));if(++count>expected)throw new IOException("Extra bootstrap trees during restoration");}
        }
        if(!reader.isComplete()||count!=expected)throw new IOException("Incomplete restored bootstrap collection");
        // The complete document tail is separate from its tree records.
        Files.writeString(output,reader.trailingText(),StandardCharsets.UTF_8,StandardOpenOption.APPEND);
    }
    static void fixedLengths(NewickTree supplied,NewickTree written,long budget,List<String> warnings)throws IOException {
        var first=lengths(supplied,budget);var second=lengths(written,budget);
        if(!first.keySet().equals(second.keySet()))throw new IOException("Fixed branch-length split sets differ");
        for(var entry:first.entrySet()) {
            double[] actual=second.get(entry.getKey());double tolerance=actual[1]+entry.getValue()[2]+actual[2];
            if(Math.abs(entry.getValue()[0]-actual[0])>tolerance)throw new IOException("Original fixed-length evaluation changed a supplied unrooted edge length");
            if(entry.getValue()[0]>0&&actual[0]==0)warnings.add("SOURCE TREE PRINTING PRECISION: a positive fixed input edge is printed as zero. The exact original input and supplied-tree JC69 evaluation are retained; do not treat the rounded written tree as exact.");
        }
    }
    private static Map<Set<String>,double[]> lengths(NewickTree tree,long budget)throws IOException {
        var values=new HashMap<Set<String>,double[]>();for(var entry:tree.edgeSplits(budget).entrySet()) {
            var value=values.computeIfAbsent(entry.getValue(),key->new double[3]);double branch=tree.nodes().get(entry.getKey()).branchLength();value[0]+=branch;value[1]+=new BigDecimal(tree.branchLengthText(entry.getKey())).ulp().abs().doubleValue()/2;value[2]+=Math.ulp(branch)+Math.ulp(value[0]);
            if(!Double.isFinite(value[0])||!Double.isFinite(value[1]))throw new IOException("Fixed-tree length/precision capacity exceeded");
        }return values;
    }
    private static void writeCandidates(Path path,IqtreeReport.Selection selection)throws IOException {
        try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            writer.write("model\tselection_log_likelihood\tparameters\tsource_AIC\tsource_AIC_weight\tsource_AIC_confidence\tsource_AICc\tclassical_AICc_defined\tsource_AICc_weight\tsource_AICc_confidence\tsource_BIC\tsource_BIC_weight\tsource_BIC_confidence\n");
            for(var row:selection.candidates())writer.write(String.join("\t",row.model(),row.logLikelihood().text(),String.valueOf(row.parameters()),row.aic().text(),row.aicWeight().value().text(),row.aicWeight().marker(),row.aicc().text(),String.valueOf(row.classicalAiccDefined()),row.aiccWeight().value().text(),row.aiccWeight().marker(),row.bic().text(),row.bicWeight().value().text(),row.bicWeight().marker())+"\n");
        }
    }
    private static boolean sameModel(String requested,String actual,PhylogeneticAlignment.Kind kind){return base(requested,kind).equals(base(actual,kind))&&suffixes(requested).equals(suffixes(actual));}
    private static Set<String> suffixes(String model){var result=new TreeSet<String>();String[] pieces=model.split("\\+");for(int i=1;i<pieces.length;i++)if(!pieces[i].startsWith("F"))result.add(pieces[i].equals("G")?"G4":pieces[i].equals("R")?"R4":pieces[i]);return result;}
    private static String base(String model,PhylogeneticAlignment.Kind kind) {
        String name=model.split("\\+",2)[0].toUpperCase(Locale.ROOT);
        name=switch(name){case "JC69"->"JC";case "K80"->"K2P";case "HKY85"->"HKY";case "TN93"->"TN";case "K81"->"K3P";case "K81U"->"K3PU";case "MGKAP2"->"MG1KTS";case "MGKAP3"->"MG1KTV";case "MGKAP4"->"MG2K";case "GYKAP1"->"GY0K";case "GYKAP2"->"GY1KTS";case "GYKAP3"->"GY1KTV";case "GYKAP4"->"GY2K";case "ECM","KOSI07"->"ECMK07";case "SCHN05"->"ECMS05";default->name;};
        if(kind!=PhylogeneticAlignment.Kind.DNA)return name;
        var codes=Map.ofEntries(Map.entry("JC","000000"),Map.entry("F81","000000"),Map.entry("K2P","010010"),Map.entry("HKY","010010"),Map.entry("TN","010020"),Map.entry("TNE","010020"),Map.entry("K3P","012210"),Map.entry("K3PU","012210"),Map.entry("TPM2","010212"),Map.entry("TPM2U","010212"),Map.entry("TPM3","012012"),Map.entry("TPM3U","012012"),Map.entry("TIM","012230"),Map.entry("TIME","012230"),Map.entry("TIM2","010232"),Map.entry("TIM2E","010232"),Map.entry("TIM3","012032"),Map.entry("TIM3E","012032"),Map.entry("TVM","012314"),Map.entry("TVME","012314"),Map.entry("GTR","012345"),Map.entry("SYM","012345"));
        String code=codes.getOrDefault(name,name);if(!code.matches("[0-5]{6}"))return code;
        var symbols=new HashMap<Character,Integer>();var normalized=new StringBuilder();for(char c:code.toCharArray())normalized.append(symbols.computeIfAbsent(c,key->symbols.size()));return normalized.toString();
    }
    private static Reader guarded(Path path,long maximum,Runnable cancellation)throws IOException {
        return new FilterReader(Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            long count;
            private void advance(int n)throws IOException {if(n>0&&((count=Math.addExact(count,n))>maximum))throw new IOException("Complete IQ-TREE input/output character capacity exceeded");cancellation.run();}
            @Override public int read()throws IOException {int value=super.read();advance(value<0?0:1);return value;}
            @Override public int read(char[] c,int start,int length)throws IOException {int n=super.read(c,start,length);advance(n);return n;}
        };
    }
}

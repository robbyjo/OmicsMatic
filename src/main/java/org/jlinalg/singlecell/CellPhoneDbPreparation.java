/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.*;

/**
 * CellPhoneDB5.0.1 typed raw-count preparation, bound to pandas2.2.3.
 * Caller supplies the explicit database gene/composition/interaction rows.
 * No database, gene alias, normalization or metadata inference is performed.
 * Group means follow pandas' float32 compensated accumulation, not the
 * numpy-groupies sequential cluster accumulation used by the next stage.
 */
public final class CellPhoneDbPreparation {
    private CellPhoneDbPreparation() { }
    public enum Identifier { ENSEMBL, GENE_NAME, HGNC_SYMBOL }
    public record Gene(long id,String ensembl,String geneName,String hgncSymbol) {
        public Gene {optional(ensembl);optional(geneName);optional(hgncSymbol);}
        String key(Identifier kind){return switch(kind){case ENSEMBL->ensembl;case GENE_NAME->geneName;case HGNC_SYMBOL->hgncSymbol;};}
    }
    public record Composition(long rowId,long complexId,long proteinId,long totalProtein) {
        public Composition {require(totalProtein>0,"Nonpositive declared complex membership count");}
    }
    public record Relation(int sourceRow,int geneRow,Gene gene) { }
    public record Casting(int sourceRow,int cellColumn,double original,double sourceFloat32) { }
    public record GeneDisposition(int sourceRow,String originalGene,List<Long> mappedIds) {
        public GeneDisposition {mappedIds=List.copyOf(mappedIds);}
    }
    public record Counts(List<String> genes,List<String> cells,double[][] expression) {
        public Counts {genes=List.copyOf(genes);cells=List.copyOf(cells);expression=copy(expression);}
        @Override public double[][] expression(){return copy(expression);}
    }
    public record Metadata(List<String> cells,List<String> cellTypes) {
        public Metadata {cells=List.copyOf(cells);cellTypes=List.copyOf(cellTypes);}
    }
    public record Limits(int maximumRows,int maximumCells,int maximumGeneRows,int maximumCompositionRows,
                         int maximumInteractions,int maximumJoins,long maximumMatrixCells,long maximumOperations) {
        public Limits {require(maximumRows>0&&maximumCells>0&&maximumGeneRows>0&&maximumCompositionRows>0&&maximumInteractions>0&&maximumJoins>0&&maximumMatrixCells>0&&maximumOperations>0,"Positive complete-family capacities required");}
        public static Limits defaults(){return new Limits(100000,100000,1000000,1000000,1000000,1000000,10000000,100000000);}
    }
    public record ComplexRows(long id,List<Integer> rows) {
        public ComplexRows {rows=List.copyOf(rows);}
    }
    public record Result(List<String> preprocessedGenes,List<String> preprocessedCells,double[][] preprocessedExpression,
                         List<Long> mappedFeatures,List<String> cells,double[][] mappedExpression,List<Relation> relations,
                         List<CellPhoneDbStatistics.Interaction> reducedInteractions,List<CellPhoneDbStatistics.Interaction> filteredInteractions,
                         List<Long> filteredFeatures,double[][] filteredExpression,List<Composition> filteredComposition,
                         List<ComplexRows> complexMap,List<String> alignedCellTypes,List<String> removedCells,
                         List<GeneDisposition> geneLedger,List<Casting> castingLedger,List<Long> zeroProteins,
                         CellPhoneDbStatistics.Prepared prepared,long operations,List<String> warnings) {
        public Result {preprocessedGenes=List.copyOf(preprocessedGenes);preprocessedCells=List.copyOf(preprocessedCells);preprocessedExpression=copy(preprocessedExpression);mappedFeatures=List.copyOf(mappedFeatures);cells=List.copyOf(cells);mappedExpression=copy(mappedExpression);relations=List.copyOf(relations);reducedInteractions=List.copyOf(reducedInteractions);filteredInteractions=List.copyOf(filteredInteractions);filteredFeatures=List.copyOf(filteredFeatures);filteredExpression=copy(filteredExpression);filteredComposition=List.copyOf(filteredComposition);complexMap=List.copyOf(complexMap);alignedCellTypes=List.copyOf(alignedCellTypes);removedCells=List.copyOf(removedCells);geneLedger=List.copyOf(geneLedger);castingLedger=List.copyOf(castingLedger);zeroProteins=List.copyOf(zeroProteins);warnings=List.copyOf(warnings);}
        @Override public double[][] preprocessedExpression(){return copy(preprocessedExpression);}
        @Override public double[][] mappedExpression(){return copy(mappedExpression);}
        @Override public double[][] filteredExpression(){return copy(filteredExpression);}
    }
    private record Endpoints(long first,long second) { }

    /** Prepare complete finite nonnegative typed inputs. Null database annotations remain null; unknown genes remain in the ledger. */
    public static Result prepare(Counts raw,Metadata meta,List<Gene> genes,Identifier identifier,
                                 List<Composition> composition,List<CellPhoneDbStatistics.Interaction> interactions,Limits limits) {
        Objects.requireNonNull(raw);Objects.requireNonNull(meta);Objects.requireNonNull(identifier);Objects.requireNonNull(limits);genes=List.copyOf(genes);composition=List.copyOf(composition);interactions=List.copyOf(interactions);var meter=new Meter(limits.maximumOperations);
        require(!raw.genes.isEmpty()&&!raw.cells.isEmpty()&&raw.genes.size()<=limits.maximumRows&&raw.cells.size()<=limits.maximumCells,"Empty raw matrix or raw-axis capacity exceeded");require(raw.expression.length==raw.genes.size(),"Raw gene/expression axis mismatch");matrixCapacity(raw.genes.size(),raw.cells.size(),limits);unique(raw.cells);for(String gene:raw.genes)token(gene);
        require(!meta.cells.isEmpty()&&meta.cells.size()==meta.cellTypes.size()&&meta.cells.size()<=limits.maximumCells,"Metadata axes/capacity mismatch");unique(meta.cells);for(String type:meta.cellTypes)token(type);var rawColumns=index(raw.cells);var metadata=index(meta.cells);for(String cell:meta.cells)require(rawColumns.containsKey(cell),"Original metadata cell absent from counts; no identifier repair");
        require(genes.size()<=limits.maximumGeneRows&&composition.size()<=limits.maximumCompositionRows&&interactions.size()<=limits.maximumInteractions,"Complete database row-family capacity exceeded");var rowIds=new HashSet<Long>();for(Composition row:composition)require(rowIds.add(row.rowId),"Duplicate database composition row identity");var interactionIds=new HashSet<String>();for(var row:interactions)require(interactionIds.add(row.id()),"Duplicate original interaction identity");
        var preCells=new ArrayList<String>();var removed=new ArrayList<String>();for(String cell:raw.cells)if(metadata.containsKey(cell))preCells.add(cell);else removed.add(cell);var cells=new ArrayList<>(preCells);cells.sort(CellPhoneDbPreparation::compareText);var aligned=new ArrayList<String>();for(String cell:cells)aligned.add(meta.cellTypes.get(metadata.get(cell)));double[][] cast=new double[raw.genes.size()][raw.cells.size()];var casting=new ArrayList<Casting>();
        for(int i=0;i<raw.expression.length;i++){require(raw.expression[i].length==raw.cells.size(),"Ragged original expression");for(int j=0;j<raw.cells.size();j++){meter.spend(1);double value=raw.expression[i][j];require(Double.isFinite(value)&&value>=0,"Raw expression must be finite and nonnegative in this explicit preparation profile; no missing/negative-value repair");double converted=(float)value;cast[i][j]=converted;if(Double.doubleToLongBits(value)!=Double.doubleToLongBits(converted))casting.add(new Casting(i,j,value,converted));}}
        var byKey=new HashMap<String,List<Integer>>();for(int i=0;i<genes.size();i++){meter.spend(1);String key=genes.get(i).key(identifier);if(key!=null)byKey.computeIfAbsent(key,unused->new ArrayList<>()).add(i);}var relations=new ArrayList<Relation>();var geneLedger=new ArrayList<GeneDisposition>();var grouped=new TreeMap<Long,List<Relation>>();
        for(int i=0;i<raw.genes.size();i++){meter.spend(1);var mappedIds=new ArrayList<Long>();for(int geneRow:byKey.getOrDefault(raw.genes.get(i),List.of())){meter.spend(1);require(relations.size()<limits.maximumJoins,"Complete many-to-many mapping join capacity exceeded");var gene=genes.get(geneRow);var relation=new Relation(i,geneRow,gene);relations.add(relation);mappedIds.add(gene.id);grouped.computeIfAbsent(gene.id,unused->new ArrayList<>()).add(relation);}geneLedger.add(new GeneDisposition(i,raw.genes.get(i),mappedIds));}
        require(!grouped.isEmpty(),"AllCountsFiltered: no raw gene maps to the supplied database identifier field; no synonym or species conversion is inferred");matrixCapacity(grouped.size(),cells.size(),limits);var features=List.copyOf(grouped.keySet());double[][] mapped=new double[features.size()][cells.size()];
        for(int i=0;i<features.size();i++){var members=grouped.get(features.get(i));for(int j=0;j<cells.size();j++){float total=0,compensation=0;int count=0,column=rawColumns.get(cells.get(j));for(Relation member:members){meter.spend(1);float value=(float)cast[member.sourceRow][column];if(!Float.isNaN(value)){count++;float delta=value-compensation;float next=total+delta;compensation=(next-total)-delta;if(Float.isNaN(compensation))compensation=0;total=next;}}mapped[i][j]=count==0?Float.NaN:total/(float)count;}}
        var featureRows=index(features);var positive=new LinkedHashSet<Long>();var zeros=new ArrayList<Long>();for(int i=0;i<features.size();i++){boolean any=false;for(double value:mapped[i]){meter.spend(1);if(value>0)any=true;}if(any)positive.add(features.get(i));else zeros.add(features.get(i));}
        var matchedComposition=new ArrayList<Composition>();var matchCounts=new HashMap<Long,Long>();for(Composition row:composition){meter.spend(1);if(positive.contains(row.proteinId)){matchedComposition.add(row);matchCounts.merge(row.complexId,1L,Math::addExact);}}var keptComposition=new ArrayList<Composition>();var available=new HashSet<>(positive);for(Composition row:matchedComposition){meter.spend(1);if(matchCounts.get(row.complexId)>=row.totalProtein){keptComposition.add(row);available.add(row.complexId);available.add(row.proteinId);}}
        var reduced=new LinkedHashMap<Endpoints,CellPhoneDbStatistics.Interaction>();for(var interaction:interactions){meter.spend(1);reduced.putIfAbsent(new Endpoints(interaction.first(),interaction.second()),interaction);}var filtered=new ArrayList<CellPhoneDbStatistics.Interaction>();for(var interaction:reduced.values()){meter.spend(1);if(available.contains(interaction.first())&&available.contains(interaction.second()))filtered.add(interaction);}
        // The source concatenates all first partners before all second partners, then all complete-complex proteins.
        var direct=new LinkedHashSet<Long>();for(var interaction:filtered)if(positive.contains(interaction.first()))direct.add(interaction.first());for(var interaction:filtered)if(positive.contains(interaction.second()))direct.add(interaction.second());var retained=new LinkedHashSet<>(direct);var complexProteins=new HashSet<Long>();for(Composition row:keptComposition)complexProteins.add(row.proteinId);for(long feature:features)if(positive.contains(feature)&&complexProteins.contains(feature))retained.add(feature);var filteredFeatures=List.copyOf(retained);matrixCapacity(filteredFeatures.size(),cells.size(),limits);double[][] prepared=new double[filteredFeatures.size()][cells.size()];for(int i=0;i<filteredFeatures.size();i++)prepared[i]=mapped[featureRows.get(filteredFeatures.get(i))].clone();
        var preparedRows=index(filteredFeatures);var complexGroups=new TreeMap<Long,List<Long>>();for(Composition row:keptComposition)complexGroups.computeIfAbsent(row.complexId,unused->new ArrayList<>()).add(row.proteinId);var complexMap=new ArrayList<ComplexRows>();var complexes=new ArrayList<CellPhoneDbStatistics.Complex>();for(var entry:complexGroups.entrySet()){var positions=new ArrayList<Integer>();for(long protein:entry.getValue()){meter.spend(1);require(preparedRows.containsKey(protein),"Original kept complex protein missing from complete prepared family");positions.add(preparedRows.get(protein));}complexMap.add(new ComplexRows(entry.getKey(),positions));complexes.add(new CellPhoneDbStatistics.Complex(entry.getKey(),entry.getValue()));}
        var preExpression=new double[raw.genes.size()][preCells.size()];for(int i=0;i<preExpression.length;i++)for(int j=0;j<preCells.size();j++){meter.spend(1);preExpression[i][j]=cast[i][rawColumns.get(preCells.get(j))];}
        var warnings=new ArrayList<String>();warnings.add("Typed raw-gene preparation only: supplied database rows and identifier field remain explicit. No database loading/authenticity, normalization, gene-synonym substitution, species conversion, full workflow or biological claim is inferred. Original CellPhoneDB synonym substitution is disabled and remains disabled here.");warnings.add("Original counts are cast to float32 before extra-count-cell filtering. Many-to-many gene relations are retained and aggregated by sorted multidata ID using pandas2.2.3 float32 compensated means. This differs from sequential numpy-groupies cluster means. Every changed original cell is listed in the casting ledger.");warnings.add("Composition completeness follows source matching-row counts against each row's declared total_protein, including duplicate membership rows. Unused complete complexes can retain their proteins even when no retained interaction references them. No distinct-protein reinterpretation or database repair is applied.");
        var namesByProtein=new HashMap<Long,Set<String>>();for(Relation row:relations)namesByProtein.computeIfAbsent(row.gene.id,unused->new HashSet<>()).add(row.gene.geneName);if(namesByProtein.values().stream().anyMatch(names->names.size()>1))warnings.add("Distinct mapped gene annotations share a protein. Original full result assembly can create duplicate annotation indices and fail with InvalidIndexError. Preserve every alias relation and require the full method to validate its result; never drop an alias to admit an otherwise failing analysis.");
        if(identifier==Identifier.GENE_NAME&&keptComposition.isEmpty())warnings.add("No complete complex remains with gene_name identifiers. The bound original5.0.1 simple-method empty-complex deconvolution can fail with InvalidIndexError; this successful preparation does not admit that full analysis. Keep the source error and original identifiers; do not relabel this input as Ensembl.");
        if(filtered.isEmpty())warnings.add("No interaction remains after source filtering. This preparation result does not admit a CellPhoneDB analysis; the full original method must determine its own empty/error state.");if(nonfinite(mapped)||nonfinite(prepared))warnings.add("Original float32 casting/aggregation produced Inf/NaN. Raw states are retained for audit; no downstream analysis is admitted and no value is replaced with zero.");
        var stats=new CellPhoneDbStatistics.Prepared(filteredFeatures,cells,aligned,prepared,complexes,CellPhoneDbStatistics.Precision.FLOAT32);return new Result(raw.genes,preCells,preExpression,features,cells,mapped,relations,List.copyOf(reduced.values()),filtered,filteredFeatures,prepared,keptComposition,complexMap,aligned,removed,geneLedger,casting,zeros,stats,meter.used,warnings);
    }

    private static final class Meter {final long maximum;long used;Meter(long maximum){this.maximum=maximum;}void spend(long amount){require(!Thread.currentThread().isInterrupted()&&amount>=0&&amount<=maximum-used,"Native preparation operation capacity/cancellation reached; no partial result");used+=amount;}}
    private static void matrixCapacity(int rows,int columns,Limits limits){require(Math.multiplyExact((long)rows,columns)<=limits.maximumMatrixCells,"Complete retained matrix capacity exceeded before allocation");}
    private static boolean nonfinite(double[][] values){for(double[] row:values)for(double value:row)if(!Double.isFinite(value))return true;return false;}
    private static double[][] copy(double[][] values){Objects.requireNonNull(values);double[][] result=new double[values.length][];for(int i=0;i<values.length;i++)result[i]=Objects.requireNonNull(values[i]).clone();return result;}
    private static <T> Map<T,Integer> index(List<T> values){var result=new HashMap<T,Integer>();for(int i=0;i<values.size();i++)result.put(values.get(i),i);return result;}
    private static void unique(List<String> values){var seen=new HashSet<String>();for(String value:values){token(value);require(seen.add(value),"Duplicate declared cell identity; no header/identifier repair");}}
    private static int compareText(String a,String b){var first=a.codePoints().iterator();var second=b.codePoints().iterator();while(first.hasNext()&&second.hasNext()){int x=first.nextInt(),y=second.nextInt();if(x!=y)return Integer.compare(x,y);}return Integer.compare(a.codePointCount(0,a.length()),b.codePointCount(0,b.length()));}
    private static void optional(String value){if(value!=null)token(value);}
    private static void token(String value){Objects.requireNonNull(value);require(!value.isEmpty()&&value.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Empty/control/unpaired-surrogate label; no normalization");}
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
}

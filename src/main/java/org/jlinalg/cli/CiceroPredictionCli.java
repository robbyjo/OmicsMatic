/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.CiceroPredictionSubset;
import org.jlinalg.singlecell.CiceroPredictionSubset.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Literal supplied-prediction reuse with explicit interval policies and complete membership ledger. */
public final class CiceroPredictionCli {
    private CiceroPredictionCli() { }
    static final String SCOPE = "Accessibility-only subsetting of supplied Cicero predictions. Original prediction columns, rows, identifiers, values and row terminators are retained. Cell types, coordinate convention, minimum overlap and source-only/both-endpoints scope are explicit choices, not reconstructed P153 settings. No score, gene-expression, detection, fitting, target annotation or genome-alias rule is inferred.";
    static final String ACTION = "Require exit 0 and result_admitted=true. Read WARNINGS.md, analysis-status.json, membership.tsv, counts.tsv and provenance.json. Supply permitted prediction and accessibility files with literal matching identifiers and chromosomes; declare coordinate and overlap choices. Correct the named cause and retry NEW output paths. Preserve failed attempts; do not repair identifiers or replace original scores to force success.";
    private record Request(Path predictions, Path metadata, Path accessible, Path out, String coordinates, String scope,
                           List<String> cells, long minimum, long bytes, int rows, long decisions, long operations, long outputBytes) { }
    private static final class OutputBudget {
        final long maximum; long used;
        OutputBudget(long maximum){this.maximum=maximum;}
        void write(OutputStream output,byte[] raw)throws IOException {require(raw.length<=maximum-used,"Generated tables exceed maximum_output_table_bytes");output.write(raw);used+=raw.length;}
        void write(OutputStream output,String text)throws IOException {write(output,text.getBytes(StandardCharsets.UTF_8));}
    }
    private record Table(List<String> header, List<String[]> rows, List<byte[]> originalLines, NextflowFiles.Pin pin) { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream output, PrintStream error) {
        if (args.length == 0 || Arrays.equals(args, new String[]{"--help"})) {
            emit(output, "Cicero supplied-prediction reuse (Java only)\njava -cp DEVELOPMENT.jar org.jlinalg.cli.CiceroPredictionCli --request request.json\n\nClosed JSON: schema_version=1, mode=SUBSET, predictions, peak_metadata, accessible_peaks, out (NEW), coordinate_system=one-based-closed|zero-based-half-open, accessibility_scope=source-only|both-endpoints, cell_types (ordered literal IDs), minimum_overlap_bp (positive). Optional maximum_input_bytes=134217728, maximum_rows=1000000, maximum_decisions=10000000, maximum_operations=100000000, maximum_output_table_bytes=1073741824. Relative paths resolve against the request. Predictions: prediction_id,source_peak_id,target_id followed by any unique original columns. Metadata: peak_id,chromosome,start,end. Accessible peaks: cell_type,peak_id,chromosome,start,end. Literal unquoted UTF-8 TSV; no ID/value repair. No dependencies are installed or required.\n\n" + SCOPE + "\n\n" + ACTION); return 0;
        }
        try {
            var options = FollowupSupport.options(args, Set.of("request"));
            Path request = Path.of(FollowupSupport.required(options, "request")).toAbsolutePath().normalize();
            var requestPin = NextflowFiles.pin(request, 4L << 20, () -> { });
            byte[] requestBytes = NextflowFiles.bytes(request,4<<20);
            require(requestPin.bytes()==requestBytes.length&&requestPin.sha256().equals(NextflowFiles.sha(requestBytes)),"Request changed during intake");
            Request r = parse(object(NextflowParameters.parse(NextflowFiles.utf8(requestBytes),4<<20,100000)),request.getParent());
            int[] exit = {0};
            FollowupSupport.transaction(Map.of("out", r.out.toString(), "request", request.toString()), "cicero-supplied-prediction-subset", (stage, manifest) -> {
                var state = new LinkedHashMap<String,Object>();
                state.put("result_admitted", false);
                var warnings = new ArrayList<String>(); warnings.add(SCOPE);
                warnings.add("PREDICTION PAYLOAD NOT VALIDATED: supplied score strings, including NA, NaN and Inf, pass through unchanged. Admission certifies accessibility membership only; it does not establish valid statistical scores, prediction calibration or biological interactions. Inspect the original prediction provenance and limitations before downstream interpretation.");
                try {
                    Path inputs = stage.resolve("inputs"); Files.createDirectory(inputs);
                    Files.write(inputs.resolve("request.json"),requestBytes,StandardOpenOption.CREATE_NEW); verify(inputs.resolve("request.json"),requestPin);
                    Table predictions = capture(r.predictions, inputs.resolve("predictions.tsv"), r.bytes, r.rows);
                    Table metadata = capture(r.metadata, inputs.resolve("peak-metadata.tsv"), r.bytes - predictions.pin.bytes(), r.rows);
                    Table accessible = capture(r.accessible, inputs.resolve("accessible-peaks.tsv"), r.bytes - predictions.pin.bytes() - metadata.pin.bytes(), r.rows);
                    require(predictions.header.size() >= 3 && predictions.header.subList(0,3).equals(List.of("prediction_id","source_peak_id","target_id")), "Prediction header must begin prediction_id, source_peak_id, target_id");
                    require(metadata.header.equals(List.of("peak_id","chromosome","start","end")), "Peak metadata header differs");
                    require(accessible.header.equals(List.of("cell_type","peak_id","chromosome","start","end")), "Accessible-peak header differs");
                    require(!predictions.rows.isEmpty(), "Require supplied prediction rows");
                    require((long)predictions.rows.size() * r.cells.size() <= r.decisions, "Complete membership family exceeds maximum_decisions");
                    var intervals = new HashMap<String,Interval>();
                    for (String[] row : metadata.rows) require(intervals.putIfAbsent(row[0], interval(row,1,r)) == null, "Duplicate metadata peak ID");
                    var groups = new LinkedHashMap<String,List<Peak>>(); for (String cell : r.cells) groups.put(cell,new ArrayList<>());
                    for (String[] row : accessible.rows) {
                        require(groups.containsKey(row[0]), "Accessible peak has undeclared cell type");
                        groups.get(row[0]).add(new Peak(row[1],interval(row,2,r)));
                    }
                    var indices = new LinkedHashMap<String,CiceroPredictionSubset.Index>();
                    for (var group : groups.entrySet()) indices.put(group.getKey(),new CiceroPredictionSubset.Index(group.getValue()));
                    var predictionIds = new HashSet<String>();
                    for (String[] row : predictions.rows) {
                        require(predictionIds.add(row[0]), "Duplicate prediction ID");
                        require(intervals.containsKey(row[1]), "Unknown source peak ID");
                        if (r.scope.equals("both-endpoints")) require(intervals.containsKey(row[2]), "Unknown target peak ID for both-endpoints scope");
                    }
                    Path results = stage.resolve("results"); Files.createDirectory(results);
                    var budget = new Budget(r.operations);var outputBudget=new OutputBudget(r.outputBytes);
                    var reports = new ArrayList<Map<String,Object>>();
                    try (var ledger = Files.newOutputStream(stage.resolve("membership.tsv"),StandardOpenOption.CREATE_NEW)) {
                        outputBudget.write(ledger,"cell_type\tprediction_id\tsource_accessible\tsource_witness_peak_id\tsource_matches\ttarget_accessible\ttarget_witness_peak_id\ttarget_matches\tdecision\n");
                        int ordinal = 0;
                        for (var group : indices.entrySet()) {
                            String file = String.format(Locale.ROOT,"cell-type-%04d.tsv",++ordinal); long retained = 0;
                            try (var selected = Files.newOutputStream(results.resolve(file),StandardOpenOption.CREATE_NEW)) {
                                outputBudget.write(selected,predictions.originalLines.get(0));
                                for (int i=0;i<predictions.rows.size();i++) {
                                    String[] row = predictions.rows.get(i);
                                    Hit source = group.getValue().match(intervals.get(row[1]),r.minimum,budget);
                                    Hit target = r.scope.equals("both-endpoints") ? group.getValue().match(intervals.get(row[2]),r.minimum,budget) : null;
                                    boolean keep = source.accessible() && (target == null || target.accessible());
                                    outputBudget.write(ledger,String.join("\t",group.getKey(),row[0],Boolean.toString(source.accessible()),source.firstWitness(),Integer.toString(source.matchingPeaks()),target==null?"not-tested":Boolean.toString(target.accessible()),target==null?"":target.firstWitness(),target==null?"not-tested":Integer.toString(target.matchingPeaks()),keep?"retained":"excluded")+"\n");
                                    if (keep) { outputBudget.write(selected,predictions.originalLines.get(i+1)); retained++; }
                                }
                            }
                            reports.add(Map.of("cell_type",group.getKey(),"file",file,"input_predictions",predictions.rows.size(),"retained",retained,"excluded",predictions.rows.size()-retained,"declared_accessible_peaks",groups.get(group.getKey()).size()));
                            if (retained==0) warnings.add("EMPTY SUBSET: cell type ["+group.getKey()+"] retains no supplied predictions under the declared accessibility policy. This is a filtering disposition, not evidence of absent biological interactions.");
                        }
                    }
                    verify(r.predictions,predictions.pin); verify(r.metadata,metadata.pin); verify(r.accessible,accessible.pin); verify(request,requestPin);
                    FollowupSupport.table(stage.resolve("counts.tsv"),List.of("cell_type","file","input_predictions","retained","excluded","declared_accessible_peaks"),reports.stream().map(x->new String[]{x.get("cell_type").toString(),x.get("file").toString(),x.get("input_predictions").toString(),x.get("retained").toString(),x.get("excluded").toString(),x.get("declared_accessible_peaks").toString()}).toList());
                    var provenance = new LinkedHashMap<String,Object>();
                    provenance.put("coordinate_system",r.coordinates); provenance.put("accessibility_scope",r.scope); provenance.put("minimum_overlap_bp",r.minimum); provenance.put("cell_types",r.cells); provenance.put("operation_count",budget.used()); provenance.put("generated_table_bytes",outputBudget.used); provenance.put("input_sha256",Map.of("predictions",predictions.pin.sha256(),"peak_metadata",metadata.pin.sha256(),"accessible_peaks",accessible.pin.sha256())); provenance.put("request_sha256",requestPin.sha256()); provenance.put("gene_expression_tested",false); provenance.put("original_prediction_rows_rewritten",false); provenance.put("whole_paper_reproduced",false);
                    NextflowFiles.writeJson(stage.resolve("provenance.json"),provenance);
                    state.put("status","PASS_ACCESSIBILITY_SUBSET"); state.put("result_admitted",true); state.put("prediction_rows",predictions.rows.size()); state.put("cell_types",r.cells.size());
                } catch (Exception failure) {
                    exit[0]=2; state.put("status","FAIL2_NO_RESULT_ADMITTED"); state.put("result_admitted",false); state.put("cause",failure.toString()); warnings.add("FAIL2: "+failure);
                    if (Files.exists(stage.resolve("results"))) Files.move(stage.resolve("results"),stage.resolve("unadmitted-partial-results"));
                } catch (OutOfMemoryError failure) {
                    exit[0]=2; state.put("status","FAIL2_NO_RESULT_ADMITTED"); state.put("result_admitted",false); state.put("cause","Java heap exhausted"); warnings.add("FAIL2: Java heap exhausted; choose a permitted heap/input size and retry NEW paths.");
                    if (Files.exists(stage.resolve("results"))) Files.move(stage.resolve("results"),stage.resolve("unadmitted-partial-results"));
                }
                state.put("required_action",ACTION); NextflowFiles.writeJson(stage.resolve("analysis-status.json"),state);
                Files.writeString(stage.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                manifest.put("result_admitted",state.get("result_admitted")); manifest.put("scope",SCOPE);
            });
            emit(exit[0]==0?output:error,(exit[0]==0?"Accessibility subset admitted: ":"FAIL2 NO RESULT ADMITTED: ")+r.out);
            emit(error,"WARNINGS / REQUIRED ACTIONS: "+r.out.resolve("WARNINGS.md")+"\n"+ACTION); return exit[0];
        } catch (Exception failure) { emit(error,"FAIL2 NO RESULT ADMITTED: "+failure+"\n"+ACTION); return 2; }
    }
    private static Request parse(Map<String,Object> m,Path parent) throws IOException {
        allowed(m,"schema_version","mode","predictions","peak_metadata","accessible_peaks","out","coordinate_system","accessibility_scope","cell_types","minimum_overlap_bp","maximum_input_bytes","maximum_rows","maximum_decisions","maximum_operations","maximum_output_table_bytes");
        integer(m.get("schema_version"),1,1); require(text(m.get("mode")).equals("SUBSET"),"Require mode SUBSET");
        String coordinates=text(m.get("coordinate_system")),scope=text(m.get("accessibility_scope"));
        require(Set.of("one-based-closed","zero-based-half-open").contains(coordinates),"Declare coordinate_system");
        require(Set.of("source-only","both-endpoints").contains(scope),"Declare accessibility_scope; paper settings are not inferred");
        List<String> cells=strings(m.get("cell_types"),128); require(!cells.isEmpty()&&new HashSet<>(cells).size()==cells.size(),"Require ordered unique cell types"); for(String cell:cells) literal(cell);
        return new Request(path(parent,m.get("predictions")),path(parent,m.get("peak_metadata")),path(parent,m.get("accessible_peaks")),path(parent,m.get("out")),coordinates,scope,cells,integer(m.get("minimum_overlap_bp"),1,Long.MAX_VALUE),optional(m,"maximum_input_bytes",128L<<20,1,512L<<20),(int)optional(m,"maximum_rows",1000000,1,5000000),optional(m,"maximum_decisions",10000000,1,100000000),optional(m,"maximum_operations",100000000,1,10000000000L),optional(m,"maximum_output_table_bytes",1L<<30,1,128L<<30));
    }
    private static long optional(Map<String,Object> m,String k,long fallback,long min,long max)throws IOException {return m.containsKey(k)?integer(m.get(k),min,max):fallback;}
    private static Path path(Path parent,Object raw)throws IOException {return parent.resolve(text(raw)).toAbsolutePath().normalize();}
    private static Interval interval(String[] row,int at,Request r)throws IOException {
        long start=position(row[at+1]),end=position(row[at+2]); if(r.coordinates.equals("one-based-closed")){require(start>=1&&end>=start,"Invalid one-based closed interval");start--;}
        try{return new Interval(row[at],start,end);}catch(IllegalArgumentException e){throw new IOException("Invalid interval",e);}
    }
    private static long position(String s)throws IOException {require(s.matches("0|[1-9][0-9]*"),"Require exact nonnegative integer coordinates");try{return Long.parseLong(s);}catch(NumberFormatException e){throw new IOException("Coordinate outside signed 64-bit range",e);}}
    private static Table capture(Path original,Path copy,long capacity,int maximumRows)throws IOException {
        require(capacity>=0,"Complete inputs exceed maximum_input_bytes"); var expected=NextflowFiles.pin(original,capacity,()->{});
        try(var in=Files.newInputStream(original,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(copy,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];long count=0;for(int n=in.read(buffer);n>=0;n=in.read(buffer)){require(n<=capacity-count,"Input grew beyond capacity");out.write(buffer,0,n);count+=n;}require(count==expected.bytes(),"Input size changed during capture");
        }
        verify(copy,expected); verify(original,expected); byte[] raw=Files.readAllBytes(copy);
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}catch(CharacterCodingException e){throw new IOException("Require literal UTF-8 TSV",e);}
        require(!text.startsWith("\ufeff")&&text.indexOf('\0')<0,"Refuse BOM/NUL in literal TSV");
        var lines=new ArrayList<byte[]>();var rows=new ArrayList<String[]>();List<String> header=null;int start=0;String ending=null;
        for(int i=0;i<=text.length();i++)if(i==text.length()||text.charAt(i)=='\n'){
            if(i==text.length()&&start==i)break;
            int end=i;String eol=i<text.length()?"\n":"";if(end>start&&text.charAt(end-1)=='\r'){end--;require(i<text.length(),"Bare terminal CR refused");eol="\r\n";}
            if(!eol.isEmpty()){if(ending==null)ending=eol;require(ending.equals(eol),"Mixed line endings refused");}
            String value=text.substring(start,end);require(value.indexOf('\r')<0&&!value.isEmpty(),"Empty or bare-CR record refused");String[] fields=value.split("\t",-1);
            if(header==null){require(!eol.isEmpty(),"Require terminated header");header=List.of(fields);require(new HashSet<>(header).size()==header.size(),"Duplicate header");for(String f:fields)literal(f);}
            else{require(fields.length==header.size(),"TSV row width differs");for(int j=0;j<Math.min(3,fields.length);j++)literal(fields[j]);require(rows.size()<maximumRows,"Input exceeds maximum_rows");rows.add(fields);}
            lines.add(text.substring(start,i<text.length()?i+1:i).getBytes(StandardCharsets.UTF_8));start=i+1;
        }
        require(header!=null,"Empty TSV input");return new Table(header,rows,lines,expected);
    }
    private static void literal(String s)throws IOException {require(!s.isEmpty()&&s.chars().noneMatch(c->c==0||c=='\r'||c=='\n'||c=='\t'),"Require literal nonempty identifiers");}
    private static void verify(Path path,NextflowFiles.Pin expected)throws IOException {require(expected.equals(NextflowFiles.pin(path,expected.bytes(),()->{})),"Input/request changed during capture or analysis");}
    private static void emit(PrintStream stream,String text){byte[] raw=(text+"\n").getBytes(StandardCharsets.UTF_8);stream.write(raw,0,raw.length);stream.flush();}
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit library-free protein-quantification request; no author or scientific defaults inferred. */
final class DiannRequest {
    private DiannRequest() { }
    record Run(String original,String format,String basename,String name) { }
    record Generation(int minLength,int maxLength,int missed,boolean metExcision,double minMz,double maxMz,
            int minCharge,int maxCharge,double minFragment,double maxFragment,int variableMods) { }
    record Quantification(int threads,boolean mbr,String normalization,String strategy,double q,double ms1,double ms2,int window) { }
    record Capacity(long inputBytes,int inputNodes,long outputBytes,long reportRows,int referenceRecords,
            long referenceResidues,long joins,long wallMillis,long parquetMemory) { }
    record Spec(Map<String,Object> document,String edition,String reference,String provenance,List<Run> runs,
            Generation generation,Quantification quantification,Capacity capacity,NextflowProcess.Limits process) { }
    static Spec read(Path file,boolean live)throws IOException{return parse(SimpleJson.parse(utf8(bytes(file,1048576))),live);}
    static Spec parse(Object raw,boolean live)throws IOException {
        var doc=object(raw);keys(doc,"schema_version","edition","mode","private_academic_local","reference","raw_runs","library_generation","quantification","capacity","process");integer(doc.get("schema_version"),1,1);
        String edition=text(doc.get("edition"));DiannRuntime.profile(edition);
        require("library-free-protein-quantification".equals(doc.get("mode")),"Select the explicit library-free protein-quantification mode; empirical/custom-decoy libraries, multiplexing and PTM-site analysis require separate qualification");
        if(!bool(doc.get("private_academic_local")))throw new DiannRuntime.Unavailable("The qualified original-runtime profile requires permitted private local academic use. This command cannot grant a licence; skip dependent analysis without the necessary authorization");
        var reference=object(doc.get("reference"));keys(reference,"file","provenance");String fasta=original(reference.get("file"),live,false),provenance=literal(reference.get("provenance"),8192,"reference provenance (state an unreported release explicitly)");
        var runs=new ArrayList<Run>();var names=new HashSet<String>();var paths=new HashSet<String>();
        for(Object value:list(doc.get("raw_runs"))){
            require(runs.size()<10000,"Raw-run population exceeds10000");var row=object(value);keys(row,"path","format","centroided_spectra");String format=text(row.get("format"));
            if(!Set.of("bruker-tdf-d","centroided-mzml","diann-dia").contains(format))throw new DiannRuntime.Unavailable("Unqualified Linux input format: "+format+". Supply a permitted Bruker TDF .d directory, centroided spectral mzML or original DIA-NN .dia. Linux releases1.8/1.8.1/2.0.2 do not read vendor .raw/.wiff directly; conversion is separate and must preserve the experiment");
            require(format.equals("centroided-mzml")?Boolean.TRUE.equals(row.get("centroided_spectra")):row.get("centroided_spectra")==null,"For centroided-mzml explicitly declare centroided spectra; otherwise centroided_spectra must be null. Chromatogram mzML is not an accepted substitute");
            String path=original(row.get("path"),live,format.equals("bruker-tdf-d")),basename=path.substring(path.lastIndexOf('/')+1),suffix=format.equals("bruker-tdf-d")?".d":format.equals("centroided-mzml")?".mzml":".dia";
            require(basename.toLowerCase(Locale.ROOT).endsWith(suffix)&&basename.length()>suffix.length(),"Declared input format and literal source filename disagree");String name=basename.substring(0,basename.length()-suffix.length());
            require(names.add(name)&&paths.add(path),"Duplicate raw run/name; no sample is renamed, deduplicated or omitted");runs.add(new Run(path,format,basename,name));
        }
        require(!runs.isEmpty(),"Require the complete nonempty raw-run population");
        var g=object(doc.get("library_generation"));keys(g,"enzyme","minimum_peptide_length","maximum_peptide_length","missed_cleavages","n_terminal_methionine_excision","minimum_precursor_mz","maximum_precursor_mz","minimum_precursor_charge","maximum_precursor_charge","minimum_fragment_mz","maximum_fragment_mz","fixed_carbamidomethyl_C","variable_oxidation_M","maximum_variable_modifications");
        require("trypsin-excluding-proline".equals(g.get("enzyme")),"Declare the supported K/R cuts excluding proline; no enzyme is guessed");
        if(!bool(g.get("fixed_carbamidomethyl_C"))||!bool(g.get("variable_oxidation_M")))throw new DiannRuntime.Unavailable("This qualified protein workflow uses explicit fixed C/UniMod4 and variable M/UniMod35. Other modification profiles require separate qualification; none is silently substituted");
        var generation=new Generation((int)integer(g.get("minimum_peptide_length"),7,50),(int)integer(g.get("maximum_peptide_length"),7,50),(int)integer(g.get("missed_cleavages"),0,10),bool(g.get("n_terminal_methionine_excision")),number(g.get("minimum_precursor_mz"),1,10000),number(g.get("maximum_precursor_mz"),1,10000),(int)integer(g.get("minimum_precursor_charge"),1,6),(int)integer(g.get("maximum_precursor_charge"),1,6),number(g.get("minimum_fragment_mz"),0,10000),number(g.get("maximum_fragment_mz"),1,10000),(int)integer(g.get("maximum_variable_modifications"),1,5));
        require(generation.minLength()<=generation.maxLength()&&generation.minMz()<generation.maxMz()&&generation.minCharge()<=generation.maxCharge()&&generation.minFragment()<generation.maxFragment(),"Reversed/empty explicit generation intervals");
        var q=object(doc.get("quantification"));keys(q,"threads","match_between_runs","normalization","strategy","report_qvalue","ms1_mass_accuracy_ppm","ms2_mass_accuracy_ppm","scan_window_radius");
        String normalization=text(q.get("normalization")),strategy=text(q.get("strategy"));require(Set.of("source-default","global","none").contains(normalization)&&Set.of("source-default","robust-lc").contains(strategy),"Choose explicit supported normalization and quantification strategy");
        var quantification=new Quantification((int)integer(q.get("threads"),1,256),bool(q.get("match_between_runs")),normalization,strategy,number(q.get("report_qvalue"),.01,.05),number(q.get("ms1_mass_accuracy_ppm"),0,1000),number(q.get("ms2_mass_accuracy_ppm"),0,1000),(int)integer(q.get("scan_window_radius"),0,1000000));
        require(!quantification.mbr()||runs.size()>=2,"Match-between-runs requires at least two distinct requested raw runs");
        var c=object(doc.get("capacity"));keys(c,"input_bytes","input_nodes","output_bytes","report_rows","reference_records","reference_residues","join_entries","overall_wall_millis","parquet_memory_bytes");
        var capacity=new Capacity(integer(c.get("input_bytes"),1,1000000000000L),(int)integer(c.get("input_nodes"),1,1000000),integer(c.get("output_bytes"),1,1000000000000L),integer(c.get("report_rows"),1,1000000000L),(int)integer(c.get("reference_records"),1,10000000),integer(c.get("reference_residues"),1,10000000000L),integer(c.get("join_entries"),1,1000000000L),integer(c.get("overall_wall_millis"),1,604800000),integer(c.get("parquet_memory_bytes"),67108864,1099511627776L));
        var p=object(doc.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");NextflowProcess.Limits process;
        try{process=new NextflowProcess.Limits(positive(p,"wall_millis"),positive(p,"address_space_bytes"),positive(p,"cpu_seconds"),positive(p,"file_bytes"),small(p,"open_files"),positive(p,"workspace_bytes"),small(p,"file_count"),small(p,"processes"),small(p,"threads"),positive(p,"sampled_rss_bytes"),small(p,"monitor_millis"));}catch(IllegalArgumentException e){throw new IOException("Invalid explicit original-process capacities",e);}
        require(quantification.threads()<=process.threads()&&Math.addExact(Math.addExact(capacity.inputBytes(),capacity.outputBytes()),33554432)<=process.workspaceBytes(),"Process thread/workspace bounds do not contain the declared complete workflow capacities and32MiB metadata reserve");
        var copy=new LinkedHashMap<String,Object>();doc.forEach((k,v)->copy.put(k.toString(),v));return new Spec(Collections.unmodifiableMap(copy),edition,fasta,provenance,List.copyOf(runs),generation,quantification,capacity,process);
    }
    private static String original(Object raw,boolean live,boolean directory)throws IOException {
        String value=literal(raw,32768,"absolute Linux original input path");require(value.startsWith("/")&&!value.endsWith("/")&&value.indexOf('\\')<0,"Use canonical absolute Linux paths for original execution");
        if(live){Path p=Path.of(value);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(p))throw new DiannRuntime.Unavailable("Original input unavailable/unreadable: "+value+". Supply the complete original input or retain SKIP3");require(p.normalize().equals(p)&&p.toRealPath().equals(p)&&(directory?Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS):Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)),"Original input is linked/noncanonical or has the wrong file/directory type");}return value;
    }
    private static String literal(Object raw,int maximum,String role)throws IOException {String value=text(raw);require(!value.isBlank()&&value.length()<=maximum&&value.chars().noneMatch(c->c<32||c==127),"Require explicit bounded "+role+" without control characters");return value;}
    private static boolean bool(Object raw)throws IOException {require(raw instanceof Boolean,"Require an explicit JSON boolean");return(Boolean)raw;}
    private static double number(Object raw,double min,double max)throws IOException {require(raw instanceof Number,"Require a finite JSON number");double value=((Number)raw).doubleValue();require(Double.isFinite(value)&&value>=min&&value<=max,"Numeric request value outside explicit supported interval");return value;}
    private static long positive(Map<?,?> p,String name)throws IOException{return integer(p.get(name),1,Long.MAX_VALUE);}
    private static int small(Map<?,?> p,String name)throws IOException{return(int)integer(p.get(name),1,Integer.MAX_VALUE);}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}

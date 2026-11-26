/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Typed original DIA-NN capture and portable saved-result verification. */
public final class DiannToolsCli {
    private DiannToolsCli() { }
    public static int run(String[] arguments,PrintStream out,PrintStream err){
        Path scratch=null;boolean success=false;
        try {
            if(arguments.length==0||Arrays.equals(arguments,new String[]{"--help"})){emit(out,"diann-tools --profiles\n--example\n--request TYPED_REQUEST.json --home EXACT_PRIVATE_LINUX_RUNTIME --out NEW_CAPTURE [--scratch NEW_SEPARATE_DIRECTORY]\n--verify COMPLETE_CAPTURE [--scratch NEW_SEPARATE_DIRECTORY]\n\nOriginal1.8/1.8.1/2.0.2 library-free protein quantification, full input capture and saved consistency readback. Install permitted originals separately; use Linux Java for execution. Java saved readback also runs on Windows. See docs/literature/DIA_NN.md.\n\n"+DiannWorkflow.ACTION);return 0;}
            if(Arrays.equals(arguments,new String[]{"--profiles"})){var profiles=new LinkedHashMap<String,Object>();for(String edition:List.of("1.8","1.8.1","2.0.2"))profiles.put(edition,DiannRuntime.profile(edition));emit(out,NextflowJson.json(map("schema_version",1,"engine","original","profiles",profiles,"automatic_installation",false)));return 0;}
            if(Arrays.equals(arguments,new String[]{"--example"})){emit(out,NextflowJson.json(example()));return 0;}
            var options=options(arguments);
            if(options.containsKey("--verify")){
                require(options.keySet().equals(Set.of("--verify"))||options.keySet().equals(Set.of("--verify","--scratch")),"Saved verification accepts only --verify and optional --scratch");Path root=Path.of(options.get("--verify")).toAbsolutePath().normalize();scratch=scratch(options,root);DiannBundle.verify(root,false,scratch,()->{});success=true;emit(out,"PASS_COMPLETE_ORIGINAL_DIA_NN_CAPTURE_READBACK\n"+DiannWorkflow.ACTION);return 0;
            }
            require(options.keySet().equals(Set.of("--request","--home","--out"))||options.keySet().equals(Set.of("--request","--home","--out","--scratch")),"Use --request JSON --home EXACT_RUNTIME --out NEW_CAPTURE and optional --scratch NEW_SEPARATE_DIRECTORY");
            Path root=Path.of(options.get("--out")).toAbsolutePath().normalize();scratch=scratch(options,root);Path result=DiannWorkflow.start(Path.of(options.get("--request")),Path.of(options.get("--home")),root,scratch);success=true;emit(out,"COMPLETE_ORIGINAL_DIA_NN_CAPTURED_WORKFLOW "+result+"\nREVIEW WARNINGS.md AND OUTPUT-AUDIT.json BEFORE DOWNSTREAM USE\n"+DiannWorkflow.ACTION);return 0;
        }catch(DiannRuntime.Unavailable error){emit(err,"SKIP3 DEPENDENT DIA-NN ANALYSIS SKIPPED: "+error.getMessage()+"\n"+DiannWorkflow.ACTION);return 3;}
        catch(IOException|RuntimeException|OutOfMemoryError error){emit(err,"FAIL2 NO COMPLETE DIA-NN RESULT: "+error.getMessage()+"\n"+DiannWorkflow.ACTION);return 2;}
        finally{if(success&&scratch!=null)for(Path path:List.of(scratch,scratch.resolveSibling(scratch.getFileName()+"-final"))){String retained=emptyScratch(path);if(retained!=null)emit(err,"WARNING: "+retained+". Captured scientific inputs/results were preserved; inspect retained scratch before manual cleanup.");}}
    }
    private static Map<String,String> options(String[] arguments)throws IOException {
        require(arguments.length%2==0,"Every option requires one explicit value");var result=new LinkedHashMap<String,String>();
        for(int i=0;i<arguments.length;i+=2){require(Set.of("--verify","--request","--home","--out","--scratch").contains(arguments[i])&&!arguments[i+1].isBlank()&&result.putIfAbsent(arguments[i],arguments[i+1])==null,"Unknown/duplicate option or blank option value");}return result;
    }
    private static Path scratch(Map<String,String> options,Path root)throws IOException {
        Path parent=root.getParent();require(parent!=null,"Require a capture with a parent directory");Path target=options.containsKey("--scratch")?Path.of(options.get("--scratch")).toAbsolutePath().normalize():parent.resolve(".diann-audit-"+UUID.randomUUID());
        require(!target.startsWith(root)&&!root.startsWith(target)&&!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&target.getParent()!=null&&target.getParent().toRealPath().equals(target.getParent()),"Scratch must be a NEW separate path with canonical existing parent");return target;
    }
    private static String emptyScratch(Path root){
        try{if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return null;if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root)||!root.toRealPath().equals(root))return"Retained changed verification scratch "+root;try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(path)){try(var entries=Files.list(path)){if(entries.findAny().isEmpty())Files.delete(path);}}}return Files.exists(root,LinkOption.NOFOLLOW_LINKS)?"Retained nonempty verification scratch "+root:null;}
        catch(IOException error){return"Retained verification scratch "+root+": "+error.getMessage();}
    }
    static Map<String,Object> example(){return map("schema_version",1,"edition","1.8.1","mode","library-free-protein-quantification","private_academic_local",true,
        "reference",map("file","/absolute/original-reference.fasta","provenance","Replace with actual species/database/release and preparation; state unreported release explicitly"),
        "raw_runs",List.of(map("path","/absolute/run-A.d","format","bruker-tdf-d","centroided_spectra",null),map("path","/absolute/run-B.d","format","bruker-tdf-d","centroided_spectra",null)),
        "library_generation",map("enzyme","trypsin-excluding-proline","minimum_peptide_length",7,"maximum_peptide_length",30,"missed_cleavages",2,"n_terminal_methionine_excision",true,"minimum_precursor_mz",350,"maximum_precursor_mz",950,"minimum_precursor_charge",2,"maximum_precursor_charge",4,"minimum_fragment_mz",500,"maximum_fragment_mz",1500,"fixed_carbamidomethyl_C",true,"variable_oxidation_M",true,"maximum_variable_modifications",1),
        "quantification",map("threads",4,"match_between_runs",true,"normalization","source-default","strategy","source-default","report_qvalue",.01,"ms1_mass_accuracy_ppm",10,"ms2_mass_accuracy_ppm",10,"scan_window_radius",0),
        "capacity",map("input_bytes",8589934592L,"input_nodes",10000,"output_bytes",17179869184L,"report_rows",10000000,"reference_records",1000000,"reference_residues",1000000000,"join_entries",100000000,"overall_wall_millis",28800000,"parquet_memory_bytes",1073741824),
        "process",map("wall_millis",14400000,"address_space_bytes",34359738368L,"cpu_seconds",28800,"file_bytes",21474836480L,"open_files",1024,"workspace_bytes",34359738368L,"file_count",100000,"processes",64,"threads",128,"sampled_rss_bytes",12884901888L,"monitor_millis",250));}
    private static Map<String,Object> map(Object... items){var result=new LinkedHashMap<String,Object>();for(int i=0;i<items.length;i+=2)result.put(items[i].toString(),items[i+1]);return result;}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}

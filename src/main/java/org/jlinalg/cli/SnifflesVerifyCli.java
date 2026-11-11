/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.SnifflesVcf;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Portable frozen-data integrity/consistency checks. Never executes the caller or refits SVs. */
public final class SnifflesVerifyCli {
    private SnifflesVerifyCli() { }
    public static void main(String[] args) { System.exit(run(args)); }
    public static int run(String[] args) {
        Path out = null; int code = 2;
        var state = new LinkedHashMap<String, Object>(); state.put("status", "FAIL2_SNIFFLES_FROZEN_VERIFICATION"); state.put("verification_passed", false);
        state.put("original_caller_executed", false); state.put("original_runtime_checked_now", false); state.put("independent_SV_refit_or_biological_certification", false);
        try {
            require(args.length == 4 && args[0].equals("--result") && args[2].equals("--out"), "Use --result ABSOLUTE_COMPLETE_RESULT_DIRECTORY --out NEW_ABSOLUTE_VERIFICATION_DIRECTORY");
            Path result = canonical(Path.of(args[1])), requested = Path.of(args[3]);
            require(requested.isAbsolute() && requested.normalize().equals(requested) && requested.getParent() != null && requested.getParent().equals(requested.getParent().toRealPath()), "Require a NEW canonical absolute verification output");
            require(!requested.startsWith(result) && !result.startsWith(requested), "Verification output must be disjoint from immutable result data");
            out = Files.createDirectory(requested);
            Path master = result.resolve("COMPLETED.json"); var masterPin = pin(master, 67108864, () -> {}); var receipt = read(master, 67108864);
            require(text(receipt.get("status")).equals("PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW") && Boolean.TRUE.equals(receipt.get("analysis_admitted")) && !receipt.containsKey("error"), "The complete original batch did not pass; no earlier provisional case is admitted");
            require(Boolean.TRUE.equals(receipt.get("raw_inputs_unchanged")) && Boolean.FALSE.equals(receipt.get("source_namespace_patched"))
                && Boolean.FALSE.equals(receipt.get("analysis_installed_software")) && list(receipt.get("owned_live_after_cleanup")).isEmpty(), "Whole-batch source/input/cleanup declarations are incomplete");
            require(fingerprint(receipt.get("warnings_pin"),16777216).equals(pin(result.resolve("WARNINGS.md"),16777216,()->{})),"Original prominent warnings changed or are missing");
            Path fileList = result.resolve("result-files.json"); var filesPin = fingerprint(receipt.get("result_files_pin"), 67108864);
            require(filesPin.equals(pin(fileList, 67108864, () -> {})), "Complete result-file inventory changed"); var files = read(fileList, 67108864); verifyFiles(result, files);
            var effective = read(result.resolve("effective-limits.json"), 1048576);
            var limits = new SnifflesVcf.Limits(integer(effective.get("maximum_vcf_bytes"),1,1000000000000L),integer(effective.get("maximum_vcf_records"),1,1000000000),
                (int)integer(effective.get("maximum_line_characters"),128,100000000),(int)integer(effective.get("maximum_header_lines"),1,1000000),(int)integer(effective.get("maximum_samples"),1,100000));
            for (String name : List.of("profiles.json", "bootstrap.py", "worker.py")) require(fingerprint(SnifflesRuntimeProfile.resource(name)).equals(pin(result.resolve("helpers").resolve(name),8388608,()->{})), "Captured helper/source profile differs from this exact verifier edition; use the corresponding frozen artifact");
            var request = read(result.resolve("request.original.json"),16777216); var requestedCases = list(request.get("analyses")); var completed = list(receipt.get("analyses"));
            require(!completed.isEmpty() && completed.size() == requestedCases.size() && completed.equals(receipt.get("earlier_cases_remain_provisional_unless_complete_batch_passes")), "Whole-case population or subordinate admission relationship changed");
            var reports = new ArrayList<Object>(); var ids = new HashSet<String>(); long total = 0;
            for (int index=0; index<completed.size(); index++) {
                var item=object(completed.get(index)); var declared=object(requestedCases.get(index)); String id=text(item.get("id")), edition=text(item.get("edition"));
                require(id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") && ids.add(id) && id.equals(declared.get("id")) && edition.equals(declared.get("edition")), "Declared original case id/edition/order changed");
                require(Boolean.FALSE.equals(item.get("analysis_admitted")) && text(item.get("status")).equals("PROVISIONAL_COMPLETE_ORIGINAL_OUTPUT_AND_NATIVE_VIEW_ONLY"), "Per-case admission is not subordinate to the complete batch");
                var selected=edition(edition); Path home=result.resolve("analyses").resolve(id), view=home.resolve("native"), vcf=home.resolve("original/calls.vcf");
                require(read(home.resolve("PROVISIONAL.json"),1048576).equals(item), "Subordinate case report differs from the whole-batch receipt");
                Path process=result.resolve("processes/case-"+id); var processReport=read(process.resolve("process.json"),16777216);
                require(integer(processReport.get("exit_code"),0,0)==0 && text(processReport.get("status")).equals("completed-process-only") && list(processReport.get("owned_live_after_cleanup")).isEmpty(), "Original case process/cleanup did not complete");
                if(selected!=SnifflesVcf.Edition.V1_0_7) require(text(read(home.resolve("PROGRAM-RETURNED.json"),1048576).get("status")).equals("ORIGINAL_SNIFFLES2_PROGRAM_RETURNED_ONLY"), "Original program-return marker missing");
                SnifflesVcf.Summary summary;
                try(BufferedReader rows=Files.newBufferedReader(view.resolve("variants.jsonl"),StandardCharsets.UTF_8)) {
                    summary=SnifflesVcf.read(Files.newInputStream(vcf),selected,limits,
                        header->{var wanted=Map.of("metadata_lines",header.metadataLines(),"column_line",header.columnLine(),"samples",header.samples());require(wanted.equals(read(view.resolve("header.json"),67108864)),"Frozen native header differs from original VCF");},
                        variant->{String encoded=rows.readLine();require(encoded!=null && encoded.equals(SnifflesWorkflowJson.encode(row(variant,selected))),"Frozen native full-record view differs from original VCF row "+variant.row());});
                    require(rows.readLine()==null,"Frozen native view contains extra records");
                }
                Map<String,Object> expected=Map.of("records",summary.records(),"variant_types",summary.variantTypes(),"pass_records",summary.passRecords(),"support_present",summary.supportPresent(),"support_missing",summary.supportMissing(),"phased_genotypes",summary.phasedGenotypes(),"phase_info_records",summary.phaseInfoRecords(),"samples",summary.header().samples(),"read_support_info_field",selected.readSupportInfoField());
                require(expected.equals(read(view.resolve("summary.json"),67108864)) && summary.records()==integer(item.get("records"),0,Long.MAX_VALUE)
                    && summary.phasedGenotypes()==integer(item.get("phased_genotypes"),0,Long.MAX_VALUE),"Frozen original/native/whole-case summary relationship changed");
                reports.add(Map.of("id",id,"edition",edition,"records",summary.records(),"phased_genotypes",summary.phasedGenotypes())); total=Math.addExact(total,summary.records());
            }
            var originalRuntime=read(result.resolve("runtime.original.json"),67108864);
            require(object(read(result.resolve("host-before/host-binding.json"),67108864)).equals(originalRuntime.get("host_binding"))
                && read(result.resolve("host-after/host-binding.json"),67108864).equals(originalRuntime.get("host_binding")),"Recorded host/library/search populations differ across the original batch");
            verifyFiles(result,files); require(masterPin.equals(pin(master,67108864,()->{})) && filesPin.equals(pin(fileList,67108864,()->{})),"Frozen master/inventory changed during verification");
            state.put("status","PASS_FROZEN_SNIFFLES_WORKFLOW_INTEGRITY_AND_CONSISTENCY_ONLY"); state.put("verification_passed",true); state.put("result",result.toString()); state.put("master_pin",masterPin); state.put("inventory_pin",filesPin); state.put("cases",reports); state.put("complete_records",total); code=0;
            state.put("original_warnings",list(receipt.get("warnings")));
        } catch(Exception failure) { state.put("error",failure.toString()); System.err.println("FAIL2: "+failure+"\nACTION: preserve the original bundle and this failed check. Recover exact original bytes or rerun the analysis into NEW paths; editing hashes does not validate scientific results."); }
        finally {
            if(out!=null)try {
                var originalWarnings = new StringBuilder();
                if(state.containsKey("original_warnings")) {
                    originalWarnings.append("\n## Warnings retained from the original batch\n\n");
                    for(Object warning:list(state.get("original_warnings"))) originalWarnings.append("- ").append(text(warning)).append('\n');
                }
                Files.writeString(out.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\nThis is a portable frozen-data integrity and structural-consistency check. It does not execute Sniffles, check a currently installed original runtime, independently refit SVs, authenticate execution or certify biology/paper reproduction. Only a complete original batch PASS is eligible; earlier provisional cases from a failed/skipped/cancelled batch must not be used.\n\n"+(state.containsKey("error")?"**ACTION REQUIRED: "+state.get("error")+"**\n\nPreserve all data and logs. Recover exact original bytes or rerun into NEW paths; never refresh hashes to bless changed scientific output.\n":"Original 1.0.7 missed an initial deletion in constructed controls; source behavior and missing calls are not repaired by verification.\n"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                if(!originalWarnings.isEmpty()) Files.writeString(out.resolve("WARNINGS.md"),originalWarnings,StandardCharsets.UTF_8,StandardOpenOption.APPEND);
                write(out.resolve("COMPLETED.json"),state);
            }catch(IOException failure){System.err.println("FAIL2: verification receipt unavailable: "+failure);code=2;}
        }
        if(code==0)System.out.println(state.get("status")+": "+out);return code;
    }
    private static SnifflesVcf.Edition edition(String version)throws IOException { for(var value:SnifflesVcf.Edition.values())if(value.version().equals(version))return value;throw new IOException("Unknown exact original source edition"); }
    private static Map<String,Object> row(SnifflesVcf.Variant variant,SnifflesVcf.Edition edition)throws IOException {
        var row=new LinkedHashMap<String,Object>();row.put("row",variant.row());row.put("raw_line",variant.rawLine());row.put("columns",variant.columns());
        var fields=new ArrayList<Object>();for(var field:variant.info()){var entry=new LinkedHashMap<String,Object>();entry.put("key",field.key());entry.put("value",field.value());fields.add(entry);}
        row.put("info",fields);row.put("format",variant.format());row.put("samples",variant.samples());row.put("read_support_info_field",edition.readSupportInfoField());var support=variant.readSupport(edition);row.put("read_support",support.isPresent()?support.getAsLong():null);return row;
    }
    private static void verifyFiles(Path root,Map<?,?> expected)throws IOException {
        require(expected.size()<=1000000,"Frozen file inventory exceeds capacity");var found=new HashSet<String>();
        try(var tree=Files.walk(root)) {
            var iterator=tree.iterator();int nodes=0;
            while(iterator.hasNext()) {
                Path path=iterator.next();require(++nodes<=2000000 && !Files.isSymbolicLink(path),"Frozen population exceeds capacity or contains a link");
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;
                require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Frozen output contains a nonregular node");String name=root.relativize(path).toString().replace('\\','/');
                if(name.equals("COMPLETED.json") || name.equals("result-files.json") || name.equals("WARNINGS.md"))continue;
                require(expected.containsKey(name) && found.add(name),"Unexpected frozen output member: "+name);
                require(fingerprint(expected.get(name),1000000000000L).equals(pin(path,1000000000000L,()->{})),"Frozen file bytes changed: "+name);
            }
        }
        require(found.equals(expected.keySet()),"Frozen output population is incomplete");
    }
}

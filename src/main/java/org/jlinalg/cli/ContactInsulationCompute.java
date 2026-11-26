/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.omics.contact.PreparedInsulation;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.ContactInsulationRequest.require;

/** Complete typed-map calculation and deterministic full numerical output replay. */
final class ContactInsulationCompute {
    private ContactInsulationCompute() {}
    static Map<String,Object> map(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    static Map<String,Object> run(Path root,ContactInsulationRequest.Spec spec,boolean replay,Runnable check)throws IOException {
        var result=PreparedInsulation.calculate(root.resolve("inputs/bins.tsv"),root.resolve("inputs/pixels.tsv"),spec.settings(),check);
        Path output=root.resolve("outputs/insulation.tsv");var summaries=new ArrayList<Map<String,Object>>();Pin identity;long totalRows=0;
        try(var sink=new Sink(replay?OutputStream.nullOutputStream():Files.newOutputStream(output,StandardOpenOption.CREATE_NEW),spec.capacity().outputBytes(),check)) {
            sink.text("window_bp\tbin_id\tchrom\tstart\tend\tregion\tweight\tsource_output_ordinal\tscore\tlog2_insulation_score\tn_valid_pixels\tsum_balanced\tsum_counts\tis_bad_bin\tdistance_to_bad\tboundary_eligible\tboundary_strength\tis_boundary\n");
            for(var window:result.windows()) {
                long missingScore=0,missingLog=0,missingStrength=0,infiniteScore=0,eligible=0,called=0;
                for(var row:window.rows()) {
                    check.run();var bin=row.bin();totalRows=Math.addExact(totalRows,1);
                    if(Double.isNaN(row.score()))missingScore++;if(Double.isInfinite(row.score()))infiniteScore++;
                    if(Double.isNaN(row.log2Score()))missingLog++;if(Double.isNaN(row.strength()))missingStrength++;
                    if(row.boundaryEligible())eligible++;if(row.isBoundary())called++;
                    sink.text(String.join("\t",Long.toString(row.windowBp()),Integer.toString(bin.id()),bin.chrom(),Long.toString(bin.start()),Long.toString(bin.end()),bin.region(),number(bin.weight()),
                        Integer.toString(row.sourceOutputOrdinal()),number(row.score()),number(row.log2Score()),number(row.nValidPixels()),number(row.sumBalanced()),number(row.sumCounts()),
                        Boolean.toString(row.badBin()),Integer.toString(row.distanceToBad()),Boolean.toString(row.boundaryEligible()),number(row.strength()),Boolean.toString(row.isBoundary()))+"\n");
                }
                summaries.add(map("window_bp",window.windowBp(),"rows",window.rows().size(),"minimum_valid_pixels",window.minimumValidPixels(),
                    "missing_score_markers",missingScore,"infinite_normalized_score_markers",infiniteScore,"missing_log2_markers",missingLog,
                    "missing_strength_markers",missingStrength,"eligible_bins",eligible,"called_boundaries",called));
            }
            sink.flush();identity=sink.finish();
        }
        require(totalRows==Math.multiplyExact(result.bins(),(long)spec.settings().windowsBp().size()),"Complete bin/window output accounting differs");
        if(replay)require(pin(output,spec.capacity().outputBytes(),check).equals(identity),"Saved insulation table differs from full original-input numerical replay");
        return map("schema_version",1,"status","COMPLETE_NATIVE_PREPARED_CIS_INSULATION_FIXED_BOUNDARIES","complete_bins",result.bins(),"regions",result.regions(),
            "bad_weight_bins",result.badBins(),"complete_pixel_records",result.pixelRecords(),"cis_within_region_records",result.cisPixelRecords(),
            "outside_region_records_not_used_by_cis_algorithm",result.outsideRegionRecords(),"complete_decoded_input_bytes",result.decodedInputBytes(),
            "output_rows",totalRows,"windows",summaries,"output",identity,"source_behavior","cooltools-0.5.1-0.5.4-common-insulation",
            "pixel_records_sorted_dropped_or_consolidated",false,"full_original_inputs_retained",true,"original_runtime_executed",false,
            "whole_cooltools_package_admitted",false,"whole_paper_reproduced",false);
    }
    private static String number(double value) {return Double.isNaN(value)?"NaN":value==Double.POSITIVE_INFINITY?"Inf":value==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(value);}
    private static final class Sink extends OutputStream {
        final OutputStream output;final MessageDigest digest;final long maximum;final Runnable check;long count;boolean finished;
        Sink(OutputStream output,long maximum,Runnable check) {
            this.output=new BufferedOutputStream(output,65536);this.maximum=maximum;this.check=check;
            try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        }
        void text(String value)throws IOException {write(value.getBytes(StandardCharsets.UTF_8));}
        @Override public void write(int value)throws IOException {write(new byte[]{(byte)value});}
        @Override public void write(byte[] bytes,int offset,int length)throws IOException {
            check.run();require(!finished&&length<=maximum-count,"Complete insulation output byte capacity exceeded");
            output.write(bytes,offset,length);digest.update(bytes,offset,length);count+=length;
        }
        @Override public void flush()throws IOException {output.flush();}
        Pin finish() {if(finished)throw new IllegalStateException("Digest finalized twice");finished=true;return new Pin(count,HexFormat.of().formatHex(digest.digest()));}
        @Override public void close()throws IOException {output.close();}
    }
}

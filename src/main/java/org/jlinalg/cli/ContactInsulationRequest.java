/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.omics.contact.PreparedInsulation;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit prepared-map contract. Author settings and raw Cooler preparation are separate. */
final class ContactInsulationRequest {
    private ContactInsulationRequest() {}
    record Capacity(long inputBytes,long outputBytes,long wallMillis) {}
    record Spec(Map<String,Object> value,String bins,String pixels,String preparation,
                PreparedInsulation.Settings settings,Capacity capacity) {}
    static Spec read(Path path,boolean original)throws IOException {
        return parse(SimpleJson.parse(utf8(bytes(path,1048576)).replaceFirst("^\\uFEFF","")),original);
    }
    static Spec parse(Object raw,boolean original)throws IOException {
        var doc=object(raw);
        keys(doc,"schema_version","engine","mode","source_behavior","preparation","input","settings","capacity","process");
        integer(doc.get("schema_version"),1,1);
        require("native".equals(doc.get("engine"))&&"prepared-cis-insulation-fixed-boundaries".equals(doc.get("mode"))
            &&"cooltools-0.5.1-0.5.4-common-insulation".equals(doc.get("source_behavior"))&&doc.get("process")==null,
            "Require the explicit native prepared cis-map profile with process:null");
        String preparation=scalar(doc.get("preparation"),8192,"input preparation provenance");
        var input=object(doc.get("input"));keys(input,"bins","pixels","format");
        require("literal-utf8-tsv".equals(input.get("format")),"Declare literal-utf8-tsv; no format inference");
        String bins=scalar(input.get("bins"),32768,"bin input path"),pixels=scalar(input.get("pixels"),32768,"pixel input path");
        require(!bins.equals(pixels),"Bin and pixel inputs must be separate complete files");
        if(original)for(String name:List.of(bins,pixels)) {
            Path p=Path.of(name);require(p.isAbsolute()&&p.normalize().equals(p),"Require canonical absolute input paths");
        }
        var numeric=object(doc.get("settings"));
        keys(numeric,"bin_width","windows_bp","ignore_diagonals","min_distance_bad","min_fraction_valid","strength_threshold","threshold_comparison","weights","normalization");
        require(">=".equals(numeric.get("threshold_comparison"))&&"supplied-weight-column".equals(numeric.get("weights"))
                &&"region-nanmedian".equals(numeric.get("normalization")),
                "Explicit inclusive numeric boundary threshold, supplied weights and region median normalization required");
        require(numeric.get("windows_bp")instanceof List<?>,"Require explicit windows_bp array");
        var windows=new ArrayList<Long>();for(Object value:(List<?>)numeric.get("windows_bp"))windows.add(integer(value,1,Long.MAX_VALUE));
        var cap=object(doc.get("capacity"));keys(cap,"bins","pixel_rows","input_bytes","output_bytes","wall_millis");
        var capacity=new Capacity(integer(cap.get("input_bytes"),1,Long.MAX_VALUE/64),integer(cap.get("output_bytes"),1,Long.MAX_VALUE/64),integer(cap.get("wall_millis"),1,604800000));
        var settings=new PreparedInsulation.Settings(integer(numeric.get("bin_width"),1,Long.MAX_VALUE),windows,
                Math.toIntExact(integer(numeric.get("ignore_diagonals"),0,Integer.MAX_VALUE)),
                Math.toIntExact(integer(numeric.get("min_distance_bad"),0,Integer.MAX_VALUE)),
                finite(numeric.get("min_fraction_valid")),finite(numeric.get("strength_threshold")),
                Math.toIntExact(integer(cap.get("bins"),1,Integer.MAX_VALUE-8)),integer(cap.get("pixel_rows"),1,Long.MAX_VALUE/64),capacity.inputBytes());
        var copy=new LinkedHashMap<String,Object>();doc.forEach((k,v)->copy.put(k.toString(),v));
        return new Spec(Collections.unmodifiableMap(copy),bins,pixels,preparation,settings,capacity);
    }
    private static double finite(Object value)throws IOException {
        require(value instanceof Number,"Require explicit finite JSON number");double result=((Number)value).doubleValue();
        require(Double.isFinite(result),"Nonfinite JSON number refused");return result;
    }
    private static String scalar(Object raw,int limit,String role)throws IOException {
        String value=text(raw);require(!value.isBlank()&&value.length()<=limit&&value.chars().noneMatch(c->c<32||c==127),"Require bounded "+role+" without controls");return value;
    }
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}

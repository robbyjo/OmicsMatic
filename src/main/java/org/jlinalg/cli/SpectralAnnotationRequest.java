/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit preparation contract. No instrument, scientific search or author policy is inferred. */
final class SpectralAnnotationRequest {
    private SpectralAnnotationRequest() { }
    record Capacity(long rows,long inputBytes,long outputBytes,long wallMillis,int chargeLimit) { }
    record Spec(Map<String,Object> value,String file,String column,String preparation,Capacity capacity) { }

    static Spec read(Path path,boolean source) throws IOException {
        return parse(SimpleJson.parse(utf8(bytes(path,1048576)).replaceFirst("^\\uFEFF","")),source);
    }
    static Spec parse(Object raw,boolean source) throws IOException {
        var doc=object(raw);keys(doc,"schema_version","engine","mode","annotation_syntax","preparation","input","capacity","process");integer(doc.get("schema_version"),1,1);
        require("native".equals(doc.get("engine"))&&"expand-explicit-fragment-annotations".equals(doc.get("mode"))&&"by-ordinal-caret-charge-v1".equals(doc.get("annotation_syntax"))&&doc.get("process")==null,"Require the native explicit bN^Z/yN^Z preparation contract and process:null");
        String preparation=scalar(doc.get("preparation"),8192,"preparation provenance");
        var input=object(doc.get("input"));keys(input,"file","annotation_column","format");require("literal-utf8-tsv".equals(input.get("format")),"Declare uncompressed literal-utf8-tsv; quoting, compression and separator are not guessed");
        String file=scalar(input.get("file"),32768,"input path"),column=scalar(input.get("annotation_column"),1024,"literal annotation column");
        if(source){Path p=Path.of(file);require(p.isAbsolute()&&p.normalize().equals(p),"Require canonical absolute spectral-library input path");}
        var cap=object(doc.get("capacity"));keys(cap,"rows","input_bytes","output_bytes","wall_millis","fragment_charge");
        var c=new Capacity(integer(cap.get("rows"),1,Long.MAX_VALUE/64),integer(cap.get("input_bytes"),1,Long.MAX_VALUE/64),integer(cap.get("output_bytes"),1,Long.MAX_VALUE/64),integer(cap.get("wall_millis"),1,604800000),Math.toIntExact(integer(cap.get("fragment_charge"),1,65535)));
        var copy=new LinkedHashMap<String,Object>();doc.forEach((k,v)->copy.put(k.toString(),v));return new Spec(Collections.unmodifiableMap(copy),file,column,preparation,c);
    }
    private static String scalar(Object raw,int limit,String role)throws IOException{
        String s=text(raw);require(!s.isBlank()&&s.length()<=limit&&s.chars().noneMatch(c->c<32||c==127),"Require explicit bounded "+role+" without controls");return s;
    }
    static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}

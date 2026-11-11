/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import static org.jlinalg.cli.ScdrsJson.*;

/** External native-BH consumer; scientific references are generated independently upstream. */
public final class ScdrsWorkflowReferenceProbe {
    private ScdrsWorkflowReferenceProbe() { }
    public static void main(String[] args)throws Exception {
        need(args.length==2,"Require INPUT and NEW_OUTPUT");
        var object=object(ScdrsJson.read(Path.of(args[0]),32000000));keys(object,"schema_version","cases");integer(object.get("schema_version"),1,1);
        try(var out=Files.newBufferedWriter(Path.of(args[1]),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("case\trow\tp_hex\tq_hex\tsignificant_005\n");
            for(Object value:list(object.get("cases"))){
                var c=object(value);keys(c,"id","p_hex");String id=text(c.get("id"));need(id.matches("[A-Za-z0-9_.-]+"),"Invalid fixture label");var raw=list(c.get("p_hex"));double[] p=new double[raw.size()];
                for(int i=0;i<p.length;i++)p[i]=Double.parseDouble(text(raw.get(i)));
                double[] q=ScdrsAudit.bh(p);
                for(int i=0;i<p.length;i++)out.write(id+"\t"+i+"\t"+Double.toHexString(p[i])+"\t"+Double.toHexString(q[i])+"\t"+(q[i]<.05)+"\n");
            }
        }
    }
}

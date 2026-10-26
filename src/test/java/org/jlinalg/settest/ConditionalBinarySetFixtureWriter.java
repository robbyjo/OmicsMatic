/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.yaml.snakeyaml.Yaml;

/** Offline fixture input writer; expected MC statistics are computed independently in Python. */
public final class ConditionalBinarySetFixtureWriter {
    private ConditionalBinarySetFixtureWriter() { }
    private static double[] vector(Map<?,?> m,String key){return ((List<?>)m.get(key)).stream().mapToDouble(v->((Number)v).doubleValue()).toArray();}
    public static void main(String[] args)throws Exception{
        Map<?,?> root=new Yaml().load(Files.readString(Path.of("src/test/resources/conditional-binary-set/reference.json")));
        Map<?,?> c=(Map<?,?>)((List<?>)root.get("cases")).get(1);double[] y=vector(c,"y");
        double[][] g=((List<?>)c.get("genotype")).stream().map(v->((List<?>)v).stream().mapToDouble(x->((Number)x).doubleValue()).toArray()).toArray(double[][]::new);
        var model=ConditionalSetNull.prepare(y,Arrays.stream(vector(c,"strata")).mapToInt(v->(int)v).toArray(),vector(c,"offset"),1000000);
        var budget=new ConditionalSetNull.Budget(1000000000,20000000);var d=new ConditionalSetNull.Distribution(model,g,100000,budget);var random=new SplittableRandom(123);
        StringBuilder out=new StringBuilder("{\"case\":\"known-offset\",\"seed\":123,\"observed\":").append(Arrays.toString(d.observed));
        for(String name:List.of("pilot","main")){out.append(",\"").append(name).append("\":[");int n=name.equals("pilot")?200:5000;for(int i=0;i<n;i++){if(i>0)out.append(',');out.append(Arrays.toString(d.sample(random,budget)));}out.append(']');}
        Files.writeString(Path.of(args[0]),out.append("}\n"));
    }
}

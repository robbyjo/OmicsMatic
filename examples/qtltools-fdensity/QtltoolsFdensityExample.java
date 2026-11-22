/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import org.jlinalg.genetics.GenomicIntervals.Interval;
import org.jlinalg.genetics.QtltoolsFunctionalDensity;

/** Two declared QTL rows and three annotations; no external tool/runtime is invoked. */
public final class QtltoolsFdensityExample {
    private QtltoolsFdensityExample() { }
    public static void main(String[] args) {
        if(args.length!=0)throw new IllegalArgumentException("This example accepts no arguments");
        var qtls=List.of(new Interval("1",100,101,"rs1","+"),new Interval("1",200,201,"rs2","-"));
        var annotations=List.of(new Interval("1",100,105,"a","."),new Interval("1",101,103,"b","."),new Interval("1",195,200,"c","."));
        var result=QtltoolsFunctionalDensity.calculate(qtls,annotations,20,10,100);
        if(result.sourceOrderGuardedQtlRows()!=0)throw new IllegalStateException("Unexpected source-order guard in the pinned example");
        for(var bin:result.bins())System.out.println(bin.start()+" "+bin.end()+" "+bin.qtlCount());
    }
}

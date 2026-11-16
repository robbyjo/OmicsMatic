/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.genetics.LdCorrelation;
import org.jlinalg.genetics.LdPred2;
import org.jlinalg.genetics.LdPred2Random;

/** Compares every Java effect with actual original R/C++ binary outputs. */
public final class LargeControl {
    private static final int M=1100000;
    private LargeControl() {}
    private static DataInputStream input(Path p) throws Exception {return new DataInputStream(new BufferedInputStream(Files.newInputStream(p)));}
    private static int[] ints(Path p,int count) throws Exception {
        if(Files.size(p)!=(long)count*4)throw new IllegalArgumentException("Input integer count differs: "+p);
        int[] values=new int[count];try(var stream=input(p)){for(int i=0;i<count;i++)values[i]=Integer.reverseBytes(stream.readInt());}return values;
    }
    private static double[] doubles(Path p,int count) throws Exception {
        if(Files.size(p)!=(long)count*8)throw new IllegalArgumentException("Input double count differs: "+p);
        double[] values=new double[count];try(var stream=input(p)){for(int i=0;i<count;i++)values[i]=Double.longBitsToDouble(Long.reverseBytes(stream.readLong()));}return values;
    }
    private static LdCorrelation correlation(Path p) throws Exception {
        return LdCorrelation.ofCsc(M,ints(p.resolve("ld-pointers.i32"),M+1),ints(p.resolve("ld-rows.i32"),2*M),doubles(p.resolve("ld-values.f64"),2*M));
    }
    private static double compare(double[] actual,Path expected,double tolerance) throws Exception {
        if(Files.size(expected)!=(long)actual.length*8)throw new IllegalArgumentException("Expected effect count differs");
        double worst=0;
        try(var stream=input(expected)) {
            for(int i=0;i<actual.length;i++) {
                double target=Double.longBitsToDouble(Long.reverseBytes(stream.readLong()));
                double error=Math.abs(actual[i]-target);
                if(!Double.isFinite(actual[i])||!Double.isFinite(target)||error>tolerance)throw new ArithmeticException("Original effect mismatch at "+i+": "+error);
                worst=Math.max(worst,error);
            }
        }
        return worst;
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("REFERENCE_DIRECTORY NEW_OUTPUT_DIRECTORY required");
        Path source=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);
        LdCorrelation corr=correlation(source);
        double[] beta=doubles(source.resolve("beta.f64"),M),se=doubles(source.resolve("beta_se.f64"),M),n=doubles(source.resolve("n_eff.f64"),M);
        var inf=LdPred2.infinitesimal(corr,beta,se,n,0.3,new LdPred2.SolveOptions(1e-10,1000));
        double infError=compare(inf.alleleEffects(),source.resolve("original-inf-allele.f64"),2e-10);
        int[] before=Files.readAllLines(source.resolve("seed-before.txt")).stream().mapToInt(Integer::parseInt).toArray();
        var rng=new LdPred2Random.RStream(before);
        var grid=LdPred2.gibbs(corr,beta,se,n,new LdPred2.GridParameters(0.3,1,false,1,3),rng,null);
        double gridError=compare(grid.alleleEffects(),source.resolve("original-grid-allele.f64"),2e-11);
        int[] after=Files.readAllLines(source.resolve("seed-after.txt")).stream().mapToInt(Integer::parseInt).toArray();
        if(!java.util.Arrays.equals(after,rng.state()))throw new ArithmeticException("Original R final RNG state differs");
        List<String> record=List.of("status=PASS_ALL_ORIGINAL_LDPRED2_EFFECTS_AT_1100000_VARIANTS","variants="+M,"stored_ld_entries="+corr.nonzeroCount(),"java_max_heap_bytes="+Runtime.getRuntime().maxMemory(),"inf_iterations="+inf.iterations(),"inf_relative_residual="+inf.relativeResidual(),"inf_max_absolute_error="+infError,"grid_max_absolute_error="+gridError,"grid_completed_sweeps="+grid.completedIterations(),"original_final_rng_state_equal=true","large_ld_is_synthetic_pair_blocks=true","full_author_environment_reproduced=false");
        Files.write(out.resolve("COMPLETED.properties"),record);for(String line:record)System.out.println(line);
    }
}

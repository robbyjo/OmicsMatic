/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import java.util.Map;
import org.jlinalg.singlecell.SoupXAmbient;

/** Synthetic arithmetic only; no biological marker assumption or count correction is established. */
public final class Example {
    private Example() { }
    public static void main(String[] args){
        var budget=new SoupXAmbient.Budget(10000);
        var genes=List.of("marker","other");
        var raw=new SoupXAmbient.Counts(genes,List.of("empty1","empty2","c1","c2"),List.of(
            new SoupXAmbient.Entry(0,0,3),new SoupXAmbient.Entry(1,0,1),
            new SoupXAmbient.Entry(0,1,1),new SoupXAmbient.Entry(1,1,3),
            new SoupXAmbient.Entry(0,2,1),new SoupXAmbient.Entry(1,2,9),new SoupXAmbient.Entry(1,3,10)),budget);
        var filtered=new SoupXAmbient.Counts(genes,List.of("c1","c2"),List.of(
            new SoupXAmbient.Entry(0,0,1),new SoupXAmbient.Entry(1,0,9),new SoupXAmbient.Entry(1,1,10)),budget);
        SoupXAmbient.verifyRawSubset(raw,filtered,budget);
        var profile=SoupXAmbient.profile(raw,0,5,budget);
        var sets=Map.of("synthetic-only",List.of("marker"));
        var mask=SoupXAmbient.nonExpressing(filtered,profile,sets,null,1,.05,100,budget);
        var fit=SoupXAmbient.globalPoisson(filtered,profile,sets,mask,budget);
        System.out.println("Selected droplets: "+profile.selectedDroplets());
        System.out.println("Soup fractions: "+profile.fractions());
        System.out.println("Analytical Poisson rho: "+fit.rho());
        System.out.println("No count correction was performed.");
    }
}

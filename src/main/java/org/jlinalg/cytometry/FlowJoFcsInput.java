/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Declared FCS preprocessing for WSP import; workspace compensation is applied separately, exactly once. */
public final class FlowJoFcsInput {
    public enum LabelPolicy { EXACT, FLOWJO_SLASH_TO_UNDERSCORE }
    public enum TimePolicy { NONE, APPLY_FCS_TIMESTEP_IGNORE_TIME_GAIN }
    private final List<String> names, warnings;
    private final double[] decades, minimum, range, gain;
    private final int timeColumn;
    private final double timeStep;
    public FlowJoFcsInput(FcsReader reader, LabelPolicy labels, TimePolicy time, String timeChannel, boolean legacyZeroMinimumAsOne) throws IOException {
        var parameters=reader.info().parameters(); var ns=new ArrayList<String>(); var notes=new ArrayList<String>(); int n=parameters.size();
        decades=new double[n]; minimum=new double[n]; range=new double[n]; gain=new double[n]; int tc=-1; double step=1;
        for (int i=0; i<n; i++) {
            var p=parameters.get(i); ns.add(labels==LabelPolicy.EXACT ? p.name() : p.name().replace('/','_'));
            String[] pair=p.amplification().split(",",-1); require(pair.length==2,"Require complete FCS PnE pair");
            decades[i]=number(reader,pair[0]); minimum[i]=number(reader,pair[1]); range[i]=number(reader,p.range()); gain[i]=p.gain()==null ? 1 : number(reader,p.gain());
            require(decades[i]>=0 && minimum[i]>=0 && range[i]>0 && gain[i]>0,"Invalid FCS scale/gain metadata; no zero gain repair");
            require(decades[i]!=0 || minimum[i]==0,"Linear FCS scale requires PnE=0,0");
            if (decades[i]>0) {
                if (reader.info().version().equals("3.1") && !reader.info().dataType().equals("I")) throw new IOException("Floating FCS3.1 data requires PnE=0,0");
                if (gain[i]!=1) throw new FcsReader.UnsupportedFormatException("Logarithmic FCS data with nonunit PnG requires separately qualified vendor preprocessing; dependent analysis skipped");
                if (minimum[i]==0) {
                    if (!legacyZeroMinimumAsOne || reader.info().version().equals("3.1")) throw new FcsReader.UnsupportedFormatException("Undefined zero log minimum; supply supported FCS or explicitly declare the legacy zero-as-one interpretation; dependent analysis skipped");
                    minimum[i]=1; notes.add("Explicit legacy zero log minimum as one for "+p.name()+"; original metadata unchanged.");
                }
            }
            if (timeChannel!=null && p.name().equals(timeChannel)) tc=i;
        }
        require(new HashSet<>(ns).size()==ns.size(),"Source labels collide under the explicitly selected label policy; no inferred repair");
        if (time==TimePolicy.NONE) require(timeChannel==null,"NONE time policy requires null timeChannel");
        else {
            require(timeChannel!=null && tc>=0,"Declare the exact original FCS time-channel name"); require(decades[tc]==0,"Time preprocessing requires a linear source time channel");
            String token=reader.keyword("$TIMESTEP"); if (token==null) throw new FcsReader.UnsupportedFormatException("Explicit time policy requires an actual $TIMESTEP value; dependent analysis skipped"); step=number(reader,token); require(step>0,"Time step must be positive; no empty/missing-value fallback");
            notes.add("Explicit time policy: "+timeChannel+" multiplied by source $TIMESTEP="+token+"; its PnG="+gain[tc]+" is not applied, following the selected FlowIO-compatible interpretation. Other channels use their declared scale/gain.");
        }
        names=List.copyOf(ns); timeColumn=time==TimePolicy.NONE ? -1 : tc; timeStep=step;
        notes.add("Label policy "+labels+" is explicit; original FCS names remain in captured metadata. No labels are guessed from antibodies or aliases.");
        notes.add("Only the sample-local WSP spectrum supplies compensation; FCS compensation keywords are retained as metadata and are not applied in addition. No off-scale/negative-event filtering or subsampling.");
        warnings=List.copyOf(notes);
    }
    public List<String> names() { return names; }
    public List<String> warnings() { return warnings; }
    public double[] scale(double[] stored) {
        if (stored==null || stored.length!=names.size()) throw new IllegalArgumentException("Require complete stored FCS row"); double[] values=new double[stored.length];
        for (int i=0; i<stored.length; i++) {
            if (!Double.isFinite(stored[i])) throw new IllegalArgumentException("Nonfinite event value; no event dropped");
            values[i]=i==timeColumn ? stored[i]*timeStep : decades[i]>0 ? Math.pow(10,decades[i]*stored[i]/range[i])*minimum[i] : stored[i]/gain[i];
            if (!Double.isFinite(values[i])) throw new ArithmeticException("FCS preprocessing overflow; no repaired value");
        }
        return values;
    }
    private static double number(FcsReader reader,String value) throws IOException { String s=reader.numericMetadataText(value); require(s.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require complete finite FCS numeric token"); double d=Double.parseDouble(s); require(Double.isFinite(d),"Nonfinite FCS metadata"); return d; }
    private static void require(boolean b,String s) throws IOException { if (!b) throw new IOException(s); }
}

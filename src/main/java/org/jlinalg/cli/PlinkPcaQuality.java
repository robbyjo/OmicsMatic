/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.genetics.PlinkBedDataset;

/** Streaming mean-GRM Ritz diagnostics without allocating the sample-square matrix. */
final class PlinkPcaQuality {
    private PlinkPcaQuality() { }
    record Component(int ordinal,double relativeResidual,double printedValueEnvelope,boolean accepted) { }
    record Result(List<Component> components,double maximumRelativeResidual,double trace,boolean accepted) {
        Result { components=List.copyOf(components); }
    }

    /**
     * Uses the declared AF reference and fitted basis. Acceptance compares the
     * observed residual directly to the requested tolerance; decimal uncertainty
     * is reported separately and never used to widen that tolerance. This is a
     * Ritz-residual diagnostic, not a guarantee of exact top-K subspace recovery.
     */
    static Result mean(PlinkPcaInputs.Prepared input,PlinkPcaReports.Basis basis,long maximumProducts,
            long maximumRetainedBytes,double tolerance) throws Exception {
        if(!Double.isFinite(tolerance)||tolerance<=0||tolerance>=1||maximumProducts<1||maximumRetainedBytes<1)
            throw new IOException("Mean-GRM validation requires positive work/storage bounds and a finite relative residual tolerance in (0,1)");
        if(!basis.samples().keySet().equals(input.basis()))throw new IOException("Mean-GRM quality sample identities differ from fitted basis");
        int n=basis.samples().size(),m=input.panel().size(),k=basis.eigenvalues().size();
        admit(n,m,k,maximumProducts,maximumRetainedBytes);
        double[][] vectors=new double[n][k],product=new double[n][k];int[] sourceIndices=new int[n];
        double[] vectorNorm=new double[k],printError=new double[k];int sampleOrdinal=0;
        for(var row:basis.samples().values()) {
            sourceIndices[sampleOrdinal]=input.bed().samples().get(row.id()).ordinal();
            for(int c=0;c<k;c++) {
                var value=row.components().get(c);vectors[sampleOrdinal][c]=value.value();
                vectorNorm[c]=Math.hypot(vectorNorm[c],value.value());printError[c]=Math.hypot(printError[c],value.roundingRadius());
            }
            sampleOrdinal++;
        }
        var originalIds=new java.util.LinkedHashMap<String,String>();
        for(String id:input.panel())originalIds.put(input.bed().variants().get(id).original(),id);
        var b=input.bed().bounds();var limits=new PlinkBedDataset.Limits(b.samples(),b.variants(),b.variants(),b.metadata(),b.retained(),b.line());
        double trace=0;double[] z=new double[n],dots=new double[k];
        try(var data=PlinkBedDataset.open(input.bed().prefix(),originalIds.keySet(),limits)) {
            for(var marker:data.selectedVariants()) {
                double p=input.calls().get(originalIds.get(marker.id())).frequency(),sigma=Math.sqrt(2*p*(1-p));
                byte[] packed=data.readPackedRow(marker);java.util.Arrays.fill(dots,0);
                for(int i=0;i<n;i++) {
                    int source=sourceIndices[i],code=(packed[source/4]>>>(2*(source%4)))&3;
                    z[i]=code==1?0:((code==0?2:code==2?1:0)-2*p)/sigma;
                    trace+=z[i]*z[i]/m;
                    for(int c=0;c<k;c++)dots[c]+=z[i]*vectors[i][c];
                }
                for(int i=0;i<n;i++)for(int c=0;c<k;c++)product[i][c]+=z[i]*dots[c]/m;
            }
        }
        if(!Double.isFinite(trace))throw new IOException("Nonfinite mean-GRM validation trace");
        var diagnostics=new ArrayList<Component>();double maximum=0;boolean accepted=true;
        for(int c=0;c<k;c++) {
            var eigen=basis.eigenvalues().get(c);double residual=0;
            for(int i=0;i<n;i++)residual=Math.hypot(residual,product[i][c]-eigen.value()*vectors[i][c]);
            double denominator=eigen.value()*vectorNorm[c];
            double relative=residual/denominator;
            double envelope=((trace+Math.abs(eigen.value()))*printError[c]+eigen.roundingRadius()*(vectorNorm[c]+printError[c]))/denominator;
            if(!Double.isFinite(relative)||!Double.isFinite(envelope)||denominator<=0)throw new IOException("Nonfinite/unreportable mean-GRM eigen-residual diagnostic");
            boolean pass=relative<=tolerance;accepted&=pass;maximum=Math.max(maximum,relative);
            diagnostics.add(new Component(c+1,relative,envelope,pass));
        }
        return new Result(diagnostics,maximum,trace,accepted);
    }
    static void admit(int n,int m,int k,long maximumProducts,long maximumRetainedBytes) throws IOException {
        long cells=Math.multiplyExact((long)n,k),products=Math.multiplyExact(Math.multiplyExact(2L*m,n),k);
        long bytes=Math.addExact(Math.multiplyExact(cells,24L),Math.multiplyExact(128L,n+(long)k));
        if(n<2||m<1||k<1||cells>Integer.MAX_VALUE||products>maximumProducts||bytes>maximumRetainedBytes)
            throw new IOException("Mean-GRM validation M*N*K work/storage exceeds explicit bounds;review limits or reduce the declared panel/basis");
    }
}

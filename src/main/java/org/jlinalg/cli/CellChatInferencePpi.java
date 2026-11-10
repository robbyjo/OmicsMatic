/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Lossless complete original PPI float64 transport; no sparsification or prefix. */
final class CellChatInferencePpi {
    private CellChatInferencePpi() { }
    static double[][] read(Path file,int n,long workingBytes)throws IOException {
        long values=Math.multiplyExact((long)n,n),bytes=Math.multiplyExact(values,8);
        if(n<1||bytes>workingBytes||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=bytes)throw new IOException("Complete PPI dimensions/byte capacity differ");
        double[][] result=new double[n][n];int width=Math.multiplyExact(n,8);
        try(var input=new BufferedInputStream(Files.newInputStream(file),65536)) {
            for(int i=0;i<n;i++) {
                if(Thread.currentThread().isInterrupted())throw new IOException("PPI read interrupted; no result");
                byte[] raw=input.readNBytes(width);if(raw.length!=width)throw new IOException("Truncated original PPI row");
                var buffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                for(int j=0;j<n;j++){double value=buffer.getDouble();if(!Double.isFinite(value)||value<0)throw new IOException("Nonfinite/negative original PPI weight");result[i][j]=value;}
            }
            if(input.read()!=-1)throw new IOException("Extra original PPI values");
        }return result;
    }
}

/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Binary layout/capacity and complete-record failure controls. */
public final class CellChatInferenceProbe {
    private CellChatInferenceProbe() { }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectory(out);Path file=out.resolve("values.f64le");
        ByteBuffer raw=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);for(double x:new double[]{0,1,2.5,8})raw.putDouble(x);Files.write(file,raw.array());
        double[][] result=CellChatInferencePpi.read(file,2,32);if(result[0][0]!=0||result[0][1]!=1||result[1][0]!=2.5||result[1][1]!=8)throw new AssertionError("PPI orientation/endian changed");
        refusal(()->CellChatInferencePpi.read(file,2,31));
        Path truncated=out.resolve("truncated");Files.write(truncated,new byte[31]);refusal(()->CellChatInferencePpi.read(truncated,2,32));
        Path extra=out.resolve("extra");Files.write(extra,new byte[33]);refusal(()->CellChatInferencePpi.read(extra,2,64));
        for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {raw.putDouble(0,value);Path bad=out.resolve("bad-"+Double.toHexString(value));Files.write(bad,raw.array());refusal(()->CellChatInferencePpi.read(bad,2,32));}
        System.out.println("PASS_COMPLETE_PPI_BINARY_LAYOUT_AND_SIX_REFUSALS");
    }
    private interface Read {void run()throws IOException;}
    private static void refusal(Read read)throws IOException {boolean refused=false;try{read.run();}catch(IOException expected){refused=true;}if(!refused)throw new AssertionError("Invalid binary PPI admitted");}
}

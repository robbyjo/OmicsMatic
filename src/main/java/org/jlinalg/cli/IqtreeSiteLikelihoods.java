/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Complete original single-tree site-likelihood output, with exact printed rounding intervals. */
final class IqtreeSiteLikelihoods {
    private IqtreeSiteLikelihoods() { }
    private static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?");
    record Audit(IqtreeFiles.Identity source,int sites,String rowName,String reportedTotal,
                 String sumOfPrintedSites,String combinedHalfRoundingInterval,boolean totalAgrees,
                 long positiveSourceCells,long nativeComparisons,long nativeDisagreements,double nativeExtraAllowance) { }

    static Audit audit(Path path,long maximumBytes,int expectedSites,String reportedTotal,
                       double[] independentSites,double extraAllowance,Runnable cancellation)throws IOException {
        if(expectedSites<1 || !Double.isFinite(extraAllowance) || extraAllowance<0 || extraAllowance>0.001
                || independentSites==null&&extraAllowance!=0 || independentSites!=null&&independentSites.length!=expectedSites)
            throw new IOException("Require complete site dimensions and explicit independent-comparison allowance");
        BigDecimal total=decimal(reportedTotal);var before=IqtreeFiles.identity(path,maximumBytes,cancellation);
        BigDecimal sum=BigDecimal.ZERO,rounding=halfUnit(total);long positive=0,disagreements=0;String row;
        try(var tokens=new Tokens(Files.newBufferedReader(path,StandardCharsets.UTF_8),maximumBytes,cancellation)) {
            if(!"1".equals(tokens.next()))throw new IOException("Expected exactly one complete IQ-TREE site-likelihood row");
            String dimension=tokens.next();
            if(dimension==null || !dimension.matches("[0-9]{1,10}"))throw new IOException("Missing/invalid complete IQ-TREE site dimension");
            long sites=Long.parseLong(dimension);if(sites!=expectedSites)throw new IOException("IQ-TREE site dimension differs from the complete alignment");
            row=tokens.next();if(!"Site_Lh".equals(row))throw new IOException("Unrecognized complete IQ-TREE site row label");
            for(int i=0;i<expectedSites;i++) {
                cancellation.run();BigDecimal source=decimal(tokens.next());sum=sum.add(source);rounding=rounding.add(halfUnit(source));
                if(source.signum()>0)positive++;
                if(independentSites!=null && (!Double.isFinite(independentSites[i])
                        ||Math.abs(independentSites[i]-source.doubleValue())>halfUnit(source).doubleValue()+extraAllowance))disagreements++;
            }
            if(tokens.next()!=null)throw new IOException("Extra IQ-TREE site cells/rows; no prefix is admitted");
        }
        var after=IqtreeFiles.identity(path,maximumBytes,cancellation);
        if(!before.equals(after))throw new IOException("IQ-TREE site-likelihood output changed during its complete audit");
        return new Audit(before,expectedSites,row,reportedTotal,sum.toPlainString(),rounding.toPlainString(),
                sum.subtract(total).abs().compareTo(rounding)<=0,positive,independentSites==null?0:expectedSites,disagreements,extraAllowance);
    }
    private static BigDecimal halfUnit(BigDecimal value){return value.ulp().abs().divide(BigDecimal.valueOf(2));}
    private static BigDecimal decimal(String text)throws IOException {
        if(text==null || text.length()>128 || !DECIMAL.matcher(text).matches())
            throw new IOException("Missing/invalid finite IQ-TREE likelihood token");
        BigDecimal value=new BigDecimal(text);
        if(Math.abs((long)value.scale())>4096 || !Double.isFinite(value.doubleValue()))throw new IOException("IQ-TREE likelihood precision/range exceeds its explicit parser contract");
        return value;
    }
    private static final class Tokens implements AutoCloseable {
        private final Reader source;private final long maximum;private final Runnable cancellation;private long characters;
        Tokens(Reader source,long maximum,Runnable cancellation){this.source=source;this.maximum=maximum;this.cancellation=cancellation;}
        String next()throws IOException {
            var token=new StringBuilder();int value;
            while((value=read())>=0) {
                if(Character.isWhitespace(value)){if(!token.isEmpty())break;continue;}
                if(token.length()>=128)throw new IOException("IQ-TREE likelihood token exceeds complete character capacity");token.append((char)value);
            }
            return token.isEmpty()?null:token.toString();
        }
        private int read()throws IOException {
            int value=source.read();if(value>=0) {
                if(++characters>maximum)throw new IOException("IQ-TREE likelihood character capacity exceeded");
                if((characters&8191)==0)cancellation.run();
            }
            return value;
        }
        @Override public void close()throws IOException{source.close();}
    }
}

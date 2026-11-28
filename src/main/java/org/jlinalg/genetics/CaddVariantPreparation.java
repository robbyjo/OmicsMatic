/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.util.Locale;

/** Explicit opt-in CADD1.6 VCF2vepVCF representation; original variants must be retained separately. */
public final class CaddVariantPreparation {
    private CaddVariantPreparation(){ }
    public record Prepared(String chromosome,String position,String reference,String alternate,String state,int prefixTrim,int suffixTrim){public boolean eligible(){return state.equals("ELIGIBLE");}}
    public static Prepared prepare(String chromosome,String position,String reference,String alternate,boolean originalPreparation)throws IOException {
        long pos;try{pos=Long.parseLong(position);}catch(NumberFormatException e){throw new IOException("Invalid CADD coordinate",e);}if(pos<1||pos>Integer.MAX_VALUE)throw new IOException("CADD tabix coordinate outside supported complete range");
        if(!originalPreparation)return new Prepared(chromosome,position,reference,alternate,"ELIGIBLE",0,0);
        if(!nucleotides(reference)||!nucleotides(alternate))return new Prepared(chromosome,position,reference,alternate,"SOURCE_OMITS_NON_ACGT",0,0);
        String ref=reference.toUpperCase(Locale.ROOT),alt=alternate.toUpperCase(Locale.ROOT);if(ref.equals(alt))return new Prepared(chromosome,position,ref,alt,"SOURCE_OMITS_REF_EQUALS_ALT",0,0);
        if(ref.length()==1&&alt.length()==1)return new Prepared(chromosome,position,ref,alt,"ELIGIBLE",0,0);
        int prefix=0;while(prefix<Math.min(ref.length(),alt.length())-1&&ref.charAt(prefix)==alt.charAt(prefix))prefix++;String r=ref.substring(prefix),a=alt.substring(prefix);int suffix=0;while(suffix<Math.min(r.length(),a.length())-1&&r.charAt(r.length()-1-suffix)==a.charAt(a.length()-1-suffix))suffix++;
        if(suffix>0){r=r.substring(0,r.length()-suffix);a=a.substring(0,a.length()-suffix);}if(pos+prefix>Integer.MAX_VALUE)throw new IOException("CADD prepared coordinate overflow");
        return new Prepared(chromosome,prefix==0?position:Long.toString(pos+prefix),r,a,"ELIGIBLE",prefix,suffix);
    }
    private static boolean nucleotides(String text){return !text.isEmpty()&&text.matches("[ACGTacgt]+");}
}

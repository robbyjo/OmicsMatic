/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Palette data and behavior derived from wesanderson 0.3.7, MIT license,
 * Copyright (c) 2022 Karthik Ram. Sequence/interpolation behavior follows
 * R 4.5.3 (R Core Team; GPL-2.0-or-later). Original sources/notices accompany this component. */
package org.jlinalg.plot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Versioned native palette generation; no paper palette or category assignment is inferred. */
public final class WesAnderson037 {
    private WesAnderson037() { }
    private static final Map<String,List<String>> PALETTES=palettesData();
    /** Exact original vector attributes, expressed as an immutable Java value. */
    public record Palette(String name,List<String> colors) {
        public Palette { colors=List.copyOf(colors); }
        public String sourceClass(){return "palette";}
    }
    public static Map<String,List<String>> palettes(){return PALETTES;}
    /** Missing number/type correspond to omitted n and the original default type, respectively. */
    public static Palette palette(String name,Double number,String type){return palette(name,number,type,1_000_000);}
    /** Explicit capacity refuses complete output rather than truncating its colors. */
    public static Palette palette(String name,Double number,String type,int maximumColors){
        String selected=type(type);List<String> raw=PALETTES.get(name);
        if(selected.equals("continuous")&&"Zissou1".equals(name))raw=PALETTES.get("Zissou1Continuous");
        if(raw==null)throw new IllegalArgumentException("Palette not found.");
        if(maximumColors<1)throw new IllegalArgumentException("Require a positive complete palette capacity");
        double n=number==null?raw.size():number;
        if(selected.equals("discrete")&&n>raw.size())throw new IllegalArgumentException("Number of requested colors greater than what palette can offer");
        if(!Double.isFinite(n))throw new IllegalArgumentException("Original numeric palette length is not finite; no repaired output accepted");
        if(selected.equals("discrete")){
            double sequenceLength=Math.floor(Math.abs(n-1.0)+1.0+1.1920928955078125e-7);
            if(n<1&&sequenceLength>=3)throw new IllegalArgumentException("only 0's may be mixed with negative subscripts");
            // R's 1:n includes the first positive index for n=0 and for -1<n<0.
            int count=n<1?1:(int)sequenceLength;
            if(count>maximumColors)throw capacity();
            return new Palette(name,raw.subList(0,count));
        }
        if(n<0)throw new IllegalArgumentException("'length.out' must be a non-negative number");
        double length=Math.ceil(n);if(length>maximumColors)throw capacity();int count=(int)length;
        var colors=new ArrayList<String>(count);int k=raw.size();double[][] rgb=new double[k][3];
        for(int i=0;i<k;i++){int value=Integer.parseInt(raw.get(i).substring(1),16);rgb[i][0]=((value>>>16)&255)/255.0;rgb[i][1]=((value>>>8)&255)/255.0;rgb[i][2]=(value&255)/255.0;}
        // seq.int(0,1,length.out=...) multiplies a precomputed step, then fixes endpoints.
        double[] knots=new double[k];double step=1.0/(k-1);for(int i=0;i<k;i++)knots[i]=i==k-1?1.0:i*step;
        double outputStep=count>1?1.0/(count-1):0.0;
        for(int i=0;i<count;i++){
            double x=count>1&&i==count-1?1.0:i*outputStep;int low=0,high=k-1;
            while(high-low>1){int middle=(low+high)/2;if(x<knots[middle])high=middle;else low=middle;}
            int[] channel=new int[3];
            for(int c=0;c<3;c++){
                double value=x==knots[low]?rgb[low][c]:x==knots[high]?rgb[high][c]:rgb[low][c]+(rgb[high][c]-rgb[low][c])*((x-knots[low])/(knots[high]-knots[low]));
                channel[c]=(int)(Math.max(0.0,Math.min(1.0,value))*255.0);
            }
            colors.add(String.format(Locale.ROOT,"#%02X%02X%02X",channel[0],channel[1],channel[2]));
        }
        return new Palette(name,colors);
    }
    private static IllegalArgumentException capacity(){return new IllegalArgumentException("Complete palette capacity exceeded; no prefix accepted");}
    private static String type(String value){
        if(value==null)return "discrete";
        if(!value.isEmpty())for(String choice:new String[]{"discrete","continuous"})if(choice.startsWith(value))return choice;
        throw new IllegalArgumentException("'arg' should be one of discrete, continuous");
    }
    private static Map<String,List<String>> palettesData(){
        var p=new LinkedHashMap<String,List<String>>();
        p.put("BottleRocket1",List.of("#A42820","#5F5647","#9B110E","#3F5151","#4E2A1E","#550307","#0C1707"));
        p.put("BottleRocket2",List.of("#FAD510","#CB2314","#273046","#354823","#1E1E1E"));
        p.put("Rushmore1",List.of("#E1BD6D","#EABE94","#0B775E","#35274A","#F2300F"));
        p.put("Rushmore",List.of("#E1BD6D","#EABE94","#0B775E","#35274A","#F2300F"));
        p.put("Royal1",List.of("#899DA4","#C93312","#FAEFD1","#DC863B"));
        p.put("Royal2",List.of("#9A8822","#F5CDB4","#F8AFA8","#FDDDA0","#74A089"));
        p.put("Zissou1",List.of("#3B9AB2","#78B7C5","#EBCC2A","#E1AF00","#F21A00"));
        p.put("Zissou1Continuous",List.of("#3A9AB2","#6FB2C1","#91BAB6","#A5C2A3","#BDC881","#DCCB4E","#E3B710","#E79805","#EC7A05","#EF5703","#F11B00"));
        p.put("Darjeeling1",List.of("#FF0000","#00A08A","#F2AD00","#F98400","#5BBCD6"));
        p.put("Darjeeling2",List.of("#ECCBAE","#046C9A","#D69C4E","#ABDDDE","#000000"));
        p.put("Chevalier1",List.of("#446455","#FDD262","#D3DDDC","#C7B19C"));
        p.put("FantasticFox1",List.of("#DD8D29","#E2D200","#46ACC8","#E58601","#B40F20"));
        p.put("Moonrise1",List.of("#F3DF6C","#CEAB07","#D5D5D3","#24281A"));
        p.put("Moonrise2",List.of("#798E87","#C27D38","#CCC591","#29211F"));
        p.put("Moonrise3",List.of("#85D4E3","#F4B5BD","#9C964A","#CDC08C","#FAD77B"));
        p.put("Cavalcanti1",List.of("#D8B70A","#02401B","#A2A475","#81A88D","#972D15"));
        p.put("GrandBudapest1",List.of("#F1BB7B","#FD6467","#5B1A18","#D67236"));
        p.put("GrandBudapest2",List.of("#E6A0C4","#C6CDF7","#D8A499","#7294D4"));
        p.put("IsleofDogs1",List.of("#9986A5","#79402E","#CCBA72","#0F0D0E","#D9D0D3","#8D8680"));
        p.put("IsleofDogs2",List.of("#EAD3BF","#AA9486","#B6854D","#39312F","#1C1718"));
        p.put("FrenchDispatch",List.of("#90D4CC","#BD3027","#B0AFA2","#7FC0C6","#9D9C85"));
        p.put("AsteroidCity1",List.of("#0A9F9D","#CEB175","#E54E21","#6C8645","#C18748"));
        p.put("AsteroidCity2",List.of("#C52E19","#AC9765","#54D8B1","#b67c3b","#175149","#AF4E24"));
        p.put("AsteroidCity3",List.of("#FBA72A","#D3D4D8","#CB7A5C","#5785C1"));
        return Collections.unmodifiableMap(p);
    }
}

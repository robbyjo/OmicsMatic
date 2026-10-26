/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.plot;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;
import org.knowm.xchart.internal.chartpart.IChart;

/** Exclusive-intersection bars, complete set-size bars and binary membership matrix. */
final class UpSetChart implements IChart {
    private final List<String> labels;
    private final List<Long> sizes;
    private final List<DiagramPlots.SetIntersection> intersections;
    private final String caption;
    private final PlotOptions options;
    UpSetChart(DiagramPlots.UpSetData data,PlotOptions options){
        if(data==null||data.labels()==null||data.labels().isEmpty()||data.labels().size()>128||data.setSizes()==null||data.setSizes().size()!=data.labels().size()||data.intersections()==null||data.intersections().isEmpty()||data.intersections().size()>100)throw new IllegalArgumentException("UpSet needs 1..128 sets and 1..100 displayed intersections");
        labels=List.copyOf(data.labels());sizes=List.copyOf(data.setSizes());intersections=List.copyOf(data.intersections());caption=data.caption()==null?"":data.caption();this.options=options;
        if(labels.stream().anyMatch(s->s.isBlank())||labels.stream().distinct().count()!=labels.size()||sizes.stream().anyMatch(n->n<0))throw new IllegalArgumentException("UpSet labels/sizes are invalid");
        var seen=new java.util.HashSet<List<Boolean>>();long[] shown=new long[labels.size()];
        for(var intersection:intersections){if(intersection.included()==null||intersection.included().size()!=labels.size()||intersection.included().stream().anyMatch(java.util.Objects::isNull)||intersection.size()<0||!seen.add(List.copyOf(intersection.included())))throw new IllegalArgumentException("Invalid/duplicate UpSet intersection");for(int i=0;i<labels.size();i++)if(intersection.included().get(i))shown[i]=Math.addExact(shown[i],intersection.size());}
        for(int i=0;i<shown.length;i++)if(shown[i]>sizes.get(i))throw new IllegalArgumentException("Displayed exclusive intersections exceed complete set size");
    }
    @Override public int getWidth(){return options.width();}
    @Override public int getHeight(){return options.height();}
    @Override public String getTitle(){return options.title();}
    @Override public void paint(Graphics2D source,int width,int height){var g=DiagramSupport.begin(source,options,width,height);try{
        int top=DiagramSupport.top(options)+35,bottom=height-65,matrixTop=Math.min(top+260,bottom-22*labels.size()),labelWidth=Math.min(240,width/5),barWidth=130,left=labelWidth+barWidth+65,right=width-35;
        double rowHeight=(bottom-matrixTop)/(double)labels.size(),columnWidth=(right-left)/(double)intersections.size();if(rowHeight<10||columnWidth<8)throw new IllegalArgumentException("Increase UpSet dimensions to preserve readable sets and intersections");
        long high=intersections.stream().mapToLong(DiagramPlots.SetIntersection::size).max().orElse(1),maxSize=sizes.stream().mapToLong(Long::longValue).max().orElse(1);high=Math.max(1,high);maxSize=Math.max(1,maxSize);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));g.setColor(DiagramSupport.TEXT);g.drawString("Exclusive intersection size",left,top-12);g.drawString("Complete set size",labelWidth, matrixTop-13);
        for(int r=0;r<labels.size();r++){double y=matrixTop+(r+.5)*rowHeight;g.setColor(r%2==0?new Color(0xf2f5f8):Color.WHITE);g.fillRect(8,(int)(y-rowHeight/2),width-16,(int)Math.ceil(rowHeight));g.setColor(DiagramSupport.TEXT);g.drawString(DiagramSupport.shorten(labels.get(r),g.getFontMetrics(),labelWidth-14),10,(int)y+4);int size=(int)(barWidth*sizes.get(r)/(double)maxSize);g.setColor(DiagramSupport.color(options,0));g.fillRect(labelWidth+barWidth-size,(int)y-5,size,10);g.setColor(DiagramSupport.TEXT);g.drawString(String.valueOf(sizes.get(r)),labelWidth+barWidth+5,(int)y+4);}
        for(int c=0;c<intersections.size();c++){var intersection=intersections.get(c);double x=left+(c+.5)*columnWidth,barHeight=(matrixTop-top-40)*intersection.size()/(double)high;g.setColor(DiagramSupport.color(options,0));g.fillRect((int)(x-columnWidth*.31),(int)(matrixTop-25-barHeight),(int)Math.max(2,columnWidth*.62),(int)barHeight);g.setColor(DiagramSupport.TEXT);String number=String.valueOf(intersection.size());var original=g.getTransform();g.translate(x,matrixTop-30-barHeight);if(columnWidth<g.getFontMetrics().stringWidth(number)+3)g.rotate(-Math.PI/2);g.drawString(number,-g.getFontMetrics().stringWidth(number)/2,0);g.setTransform(original);
            int first=-1,last=-1;for(int r=0;r<labels.size();r++)if(intersection.included().get(r)){if(first<0)first=r;last=r;}if(first>=0){g.setStroke(PlotSupport.strokeWidth(2));g.drawLine((int)x,(int)(matrixTop+(first+.5)*rowHeight),(int)x,(int)(matrixTop+(last+.5)*rowHeight));}
            for(int r=0;r<labels.size();r++){g.setColor(intersection.included().get(r)?DiagramSupport.TEXT:new Color(0xd9dfe7));int radius=(int)Math.min(5,Math.min(columnWidth,rowHeight)*.22);g.fillOval((int)x-radius,(int)(matrixTop+(r+.5)*rowHeight)-radius,2*radius,2*radius);}
        }
        g.setColor(DiagramSupport.TEXT);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));g.drawString(DiagramSupport.shorten(caption,g.getFontMetrics(),width-35),18,height-21);
    }finally{g.dispose();}}
}

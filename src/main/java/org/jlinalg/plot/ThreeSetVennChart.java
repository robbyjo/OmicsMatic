/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.plot;
import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.List;
import org.knowm.xchart.internal.chartpart.IChart;

/** Area-neutral three-set diagram with exclusive region counts. */
final class ThreeSetVennChart implements IChart {
    private final List<String> labels;
    private final List<Long> counts;
    private final PlotOptions options;
    ThreeSetVennChart(DiagramPlots.ThreeSetVennData data,PlotOptions options){
        if(data==null||data.labels()==null||data.labels().size()!=3||data.counts()==null||data.counts().size()!=7)throw new IllegalArgumentException("Three-set Venn requires three labels and seven exclusive counts");
        labels=List.copyOf(data.labels());counts=List.copyOf(data.counts());this.options=options;
        if(labels.stream().anyMatch(s->s==null||s.isBlank())||labels.stream().distinct().count()!=3||counts.stream().anyMatch(n->n<0)||counts.stream().allMatch(n->n==0))throw new IllegalArgumentException("Venn labels must be unique/nonblank and counts nonnegative/nonempty");
        long total=0;for(long count:counts)total=Math.addExact(total,count);
    }
    @Override public int getWidth(){return options.width();}
    @Override public int getHeight(){return options.height();}
    @Override public String getTitle(){return options.title();}
    @Override public void paint(Graphics2D source,int width,int height){var g=DiagramSupport.begin(source,options,width,height);try{
        double radius=Math.min(width*.25,(height-DiagramSupport.top(options)-80)/3.3),cx=width/2.,cy=DiagramSupport.top(options)+radius*1.15;
        double[][] centres={{cx-radius*.52,cy},{cx+radius*.52,cy},{cx,cy+radius*.88}};
        for(int i=0;i<3;i++){var circle=new Ellipse2D.Double(centres[i][0]-radius,centres[i][1]-radius,2*radius,2*radius);g.setComposite(AlphaComposite.SrcOver.derive(.28f));g.setColor(DiagramSupport.color(options,i));g.fill(circle);g.setComposite(AlphaComposite.SrcOver);g.setStroke(PlotSupport.strokeWidth(2));g.setColor(DiagramSupport.color(options,i).darker());g.draw(circle);}
        g.setColor(DiagramSupport.TEXT);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));double[][] positions={{cx-radius*.94,cy-radius*.15},{cx+radius*.94,cy-radius*.15},{cx,cy+radius*1.48},{cx,cy-radius*.43},{cx-radius*.50,cy+radius*.69},{cx+radius*.50,cy+radius*.69},{cx,cy+radius*.28}};
        for(int i=0;i<7;i++)center(g,String.valueOf(counts.get(i)),positions[i][0],positions[i][1]);
        g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,13));center(g,DiagramSupport.shorten(labels.get(0),g.getFontMetrics(),(int)(width*.40)),cx-radius*.9,cy-radius*1.08);center(g,DiagramSupport.shorten(labels.get(1),g.getFontMetrics(),(int)(width*.40)),cx+radius*.9,cy-radius*1.08);center(g,DiagramSupport.shorten(labels.get(2),g.getFontMetrics(),(int)(width*.8)),cx,cy+radius*2.02);
        g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));center(g,"Exclusive counts; circle areas do not encode set sizes",cx,height-17);
    }finally{g.dispose();}}
    private static void center(Graphics2D g,String s,double x,double y){g.drawString(s,(float)(x-g.getFontMetrics().stringWidth(s)/2.),(float)y);}
}

package com.freddy.wintertodthud;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

public final class MeterRenderer {
    private MeterRenderer(){}
    public static Dimension render(Graphics2D source, MeterSpec s){
        Graphics2D g=(Graphics2D)source.create();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=Math.max(1,s.width), h=Math.max(1,s.height), pct=Math.max(0,Math.min(100,s.value));
            Shape base=baseShape(s.shape,w,h);
            g.setColor(s.emptyColor); fillBase(g,base,s,w,h,100);
            drawPreviewSegments(g,base,s,w,h,pct);
            g.setColor(s.fillColor); fillProgress(g,base,s,w,h,pct);
            if(s.flash){g.setColor(s.flashColor);fillBase(g,base,s,w,h,100);}
            if(s.borderWidth>0){g.setStroke(new BasicStroke(s.borderWidth));g.setColor(s.borderColor);drawBorder(g,base,s,w,h,pct);}
            if(s.outerProgressEnabled){drawOuterProgress(g,s,w,h);}
            drawText(g,s,w,h);
            drawPreviewLabel(g,s,w,h);
            drawOuterLabel(g,s,w,h);
            return new Dimension(w,h);
        }finally{g.dispose();}
    }
    private static Shape baseShape(MeterShape shape,int w,int h){
        switch(shape){
            case ROUNDED_RECTANGLE:return new RoundRectangle2D.Double(0,0,w-1,h-1,Math.min(h,18),Math.min(h,18));
            case CIRCLE_ORB: case RING:return new Ellipse2D.Double(1,1,w-2,h-2);
            case TRIANGLE:return new Polygon(new int[]{w/2,w-1,1},new int[]{1,h-1,h-1},3);
            default:return new Rectangle2D.Double(0,0,w-1,h-1);
        }
    }
    private static void fillBase(Graphics2D g,Shape base,MeterSpec s,int w,int h,int pct){
        if(s.shape==MeterShape.RING){g.setStroke(new BasicStroke(Math.max(3,Math.min(w,h)/8f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(base);}else g.fill(base);
    }
    private static void fillProgress(Graphics2D g,Shape base,MeterSpec s,int w,int h,int pct){
        if(s.shape==MeterShape.RING){
            float stroke=Math.max(3,Math.min(w,h)/8f);g.setStroke(new BasicStroke(stroke,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            double inset=stroke/2.0;Arc2D arc=new Arc2D.Double(inset,inset,w-stroke,h-stroke,90,-360.0*pct/100.0,Arc2D.OPEN);g.draw(arc);return;
        }
        Shape old=g.getClip();g.clip(base);g.fill(fillRect(s.direction,w,h,pct));g.setClip(old);
    }
    private static Rectangle fillRect(FillDirection d,int w,int h,int pct){
        int fw=(int)Math.round(w*pct/100.0), fh=(int)Math.round(h*pct/100.0);
        switch(d){
            case RIGHT_TO_LEFT:return new Rectangle(w-fw,0,fw,h);
            case BOTTOM_TO_TOP:return new Rectangle(0,h-fh,w,fh);
            case TOP_TO_BOTTOM:return new Rectangle(0,0,w,fh);
            case CENTER_HORIZONTAL:return new Rectangle((w-fw)/2,0,fw,h);
            case CENTER_VERTICAL:return new Rectangle(0,(h-fh)/2,w,fh);
            default:return new Rectangle(0,0,fw,h);
        }
    }
    private static void drawBorder(Graphics2D g,Shape base,MeterSpec s,int w,int h,int pct){
        if(s.shape==MeterShape.RING){g.draw(base);}else g.draw(base);
    }

    private static void drawPreviewSegments(Graphics2D g,Shape base,MeterSpec s,int w,int h,int current){
        int food=Math.max(0,s.previewPrimaryAmount);
        int regen=Math.max(0,s.previewSecondaryAmount);
        int foodEnd=Math.min(100,current+food);
        int regenEnd=Math.min(100,foodEnd+regen);
        if(foodEnd>current){g.setColor(s.previewPrimaryColor);fillSegment(g,base,s,w,h,current,foodEnd);}
        if(regenEnd>foodEnd){g.setColor(s.previewSecondaryColor);fillSegment(g,base,s,w,h,foodEnd,regenEnd);}
    }
    private static void fillSegment(Graphics2D g,Shape base,MeterSpec s,int w,int h,int startPct,int endPct){
        startPct=Math.max(0,Math.min(100,startPct));endPct=Math.max(startPct,Math.min(100,endPct));
        if(endPct<=startPct)return;
        if(s.shape==MeterShape.RING){
            float stroke=Math.max(3,Math.min(w,h)/8f);g.setStroke(new BasicStroke(stroke,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            double inset=stroke/2.0;double start=90.0-360.0*startPct/100.0;double extent=-360.0*(endPct-startPct)/100.0;
            g.draw(new Arc2D.Double(inset,inset,w-stroke,h-stroke,start,extent,Arc2D.OPEN));return;
        }
        Area segment=new Area(fillRect(s.direction,w,h,endPct));
        segment.subtract(new Area(fillRect(s.direction,w,h,startPct)));
        segment.intersect(new Area(base));
        g.fill(segment);
    }

    private static void drawOuterProgress(Graphics2D g,MeterSpec s,int w,int h){
        int inset=Math.max(2,(int)Math.ceil(s.outerProgressWidth/2.0)+1);
        Shape ring=outerShape(s.shape,w,h,inset);
        float stroke=Math.max(1f,s.outerProgressWidth);
        g.setStroke(new BasicStroke(stroke,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(s.outerTrackColor);
        g.draw(ring);
        g.setColor(s.outerProgressColor);
        drawPartialShape(g,ring,Math.max(0.0,Math.min(1.0,s.outerProgress)));
    }
    private static Shape outerShape(MeterShape shape,int w,int h,int inset){
        int rw=Math.max(2,w-1-inset*2), rh=Math.max(2,h-1-inset*2);
        switch(shape){
            case CIRCLE_ORB: case RING:return new Ellipse2D.Double(inset,inset,rw,rh);
            case TRIANGLE:return new Polygon(new int[]{w/2,w-1-inset,inset},new int[]{inset,h-1-inset,h-1-inset},3);
            case ROUNDED_RECTANGLE:return new RoundRectangle2D.Double(inset,inset,rw,rh,Math.min(rh,16),Math.min(rh,16));
            default:return new Rectangle2D.Double(inset,inset,rw,rh);
        }
    }
    private static void drawPartialShape(Graphics2D g,Shape shape,double progress){
        if(progress<=0)return;
        PathIterator it=new FlatteningPathIterator(shape.getPathIterator(null),0.6);
        double[] c=new double[6];
        List<Point2D.Double> pts=new ArrayList<>();
        Point2D.Double first=null,last=null;
        while(!it.isDone()){
            int type=it.currentSegment(c);
            if(type==PathIterator.SEG_MOVETO){first=new Point2D.Double(c[0],c[1]);last=first;pts.add(first);}
            else if(type==PathIterator.SEG_LINETO){last=new Point2D.Double(c[0],c[1]);pts.add(last);}
            else if(type==PathIterator.SEG_CLOSE && first!=null && last!=null && !last.equals(first)){pts.add(first);}
            it.next();
        }
        if(pts.size()<2)return;
        double total=0;for(int i=1;i<pts.size();i++)total+=pts.get(i-1).distance(pts.get(i));
        double remain=total*progress;
        for(int i=1;i<pts.size() && remain>0;i++){
            Point2D.Double a=pts.get(i-1),b=pts.get(i);double len=a.distance(b);
            if(len<=remain){g.draw(new Line2D.Double(a,b));remain-=len;}
            else{double r=remain/len;g.draw(new Line2D.Double(a.x,a.y,a.x+(b.x-a.x)*r,a.y+(b.y-a.y)*r));remain=0;}
        }
    }

    private static void drawPreviewLabel(Graphics2D g,MeterSpec s,int w,int h){
        if(s.previewLabel==null||s.previewLabel.isEmpty())return;
        int size=Math.max(8,Math.min(10,s.fontSize-3));
        g.setFont(new Font(s.fontName,Font.BOLD,size));
        Color pc=s.previewPrimaryColor;g.setColor(pc==null?Color.WHITE:new Color(pc.getRed(),pc.getGreen(),pc.getBlue(),255));
        FontMetrics fm=g.getFontMetrics();
        g.drawString(s.previewLabel,6,Math.max(fm.getAscent()+2,(h+fm.getAscent())/2));
    }
    private static void drawOuterLabel(Graphics2D g,MeterSpec s,int w,int h){
        if(s.outerLabel==null||s.outerLabel.isEmpty())return;
        int size=Math.max(8,Math.min(10,s.fontSize-3));
        g.setFont(new Font(s.fontName,Font.BOLD,size));
        g.setColor(s.outerProgressColor);
        FontMetrics fm=g.getFontMetrics();
        g.drawString(s.outerLabel,Math.max(3,w-fm.stringWidth(s.outerLabel)-6),Math.max(fm.getAscent()+2,(h+fm.getAscent())/2));
    }

    private static void drawText(Graphics2D g,MeterSpec s,int w,int h){
        if(s.customText!=null&&!s.customText.isEmpty()){
            String text=s.customText;g.setFont(new Font(s.fontName,s.bold?Font.BOLD:Font.PLAIN,s.fontSize));g.setColor(s.textColor);
            FontMetrics fm=g.getFontMetrics();int x=(w-fm.stringWidth(text))/2+s.textX;int y=(h-fm.getHeight())/2+fm.getAscent()+s.textY;g.drawString(text,x,y);return;
        }
        if(s.textMode==MeterTextMode.OFF)return;String text;
        switch(s.textMode){
            case VALUE:text=Integer.toString(s.value);break;
            case PERCENT:text=s.value+"%";break;
            case VALUE_MAX:text=s.value+" / 100";break;
            default:text=s.label+": "+s.value+"%";
        }
        text=s.prefix+text+s.suffix;
        g.setFont(new Font(s.fontName,s.bold?Font.BOLD:Font.PLAIN,s.fontSize));g.setColor(s.textColor);
        FontMetrics fm=g.getFontMetrics();int x=(w-fm.stringWidth(text))/2+s.textX;int y=(h-fm.getHeight())/2+fm.getAscent()+s.textY;
        g.drawString(text,x,y);
    }
    public static final class MeterSpec{
        int width,height,value,borderWidth,fontSize,textX,textY;String label,fontName,prefix="",suffix="",customText="";boolean bold,flash;
        MeterShape shape;FillDirection direction;MeterTextMode textMode;Color fillColor,emptyColor,borderColor,textColor,flashColor;
        int previewPrimaryAmount,previewSecondaryAmount;Color previewPrimaryColor,previewSecondaryColor;String previewLabel="";
        boolean outerProgressEnabled;double outerProgress;float outerProgressWidth;Color outerProgressColor,outerTrackColor;String outerLabel="";
    }
}

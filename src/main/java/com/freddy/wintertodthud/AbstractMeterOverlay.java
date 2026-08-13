package com.freddy.wintertodthud;

import java.awt.*;
import net.runelite.client.ui.overlay.*;

abstract class AbstractMeterOverlay extends Overlay {
    final WintertodtState state; final WintertodtHudConfig config; final AlertEngine alerts;
    AbstractMeterOverlay(WintertodtHudPlugin plugin,WintertodtState state,WintertodtHudConfig config,AlertEngine alerts){
        super(plugin);this.state=state;this.config=config;this.alerts=alerts;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setSnappable(true);
    }
    boolean pulseOn(){int p=Math.max(100,config.flashPeriodMs());return (System.currentTimeMillis()%p)<p/2;}
    boolean visible(boolean enabled){return enabled && (!config.onlyInsideWintertodt() || state.inWintertodt());}
    void applyPreset(MeterRenderer.MeterSpec s, boolean warmth){
        MeterPreset p=config.preset(); if(p==MeterPreset.CUSTOM)return;
        switch(p){
            case OSRS:s.width=190;s.height=30;s.shape=MeterShape.RECTANGLE;s.borderWidth=2;s.fontSize=14;break;
            case MINIMAL:s.width=160;s.height=20;s.shape=MeterShape.ROUNDED_RECTANGLE;s.borderWidth=1;s.fontSize=12;break;
            case THIN_BARS:s.width=210;s.height=14;s.shape=MeterShape.ROUNDED_RECTANGLE;s.borderWidth=1;s.fontSize=10;break;
            case VERTICAL:s.width=34;s.height=180;s.shape=MeterShape.ROUNDED_RECTANGLE;s.direction=FillDirection.BOTTOM_TO_TOP;s.fontSize=11;break;
            case ORBS:s.width=74;s.height=74;s.shape=MeterShape.CIRCLE_ORB;s.direction=FillDirection.BOTTOM_TO_TOP;s.fontSize=12;break;
            case RINGS:s.width=80;s.height=80;s.shape=MeterShape.RING;s.fontSize=12;break;
            case COMPACT:s.width=150;s.height=26;s.shape=MeterShape.ROUNDED_RECTANGLE;s.borderWidth=2;s.fontSize=13;break;
            default:break;
        }
    }
}

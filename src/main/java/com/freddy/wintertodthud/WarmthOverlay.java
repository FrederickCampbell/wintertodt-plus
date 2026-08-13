package com.freddy.wintertodthud;
import java.awt.*;import javax.inject.Inject;
public class WarmthOverlay extends AbstractMeterOverlay{
    private final WarmthRegenTracker regen;private final WarmthConsumablePreview consumable;
    @Inject WarmthOverlay(WintertodtHudPlugin p,WintertodtState s,WintertodtHudConfig c,AlertEngine a,WarmthRegenTracker r,WarmthConsumablePreview h){super(p,s,c,a);regen=r;consumable=h;}
    @Override public Dimension render(Graphics2D g){if(!visible(config.warmthEnabled()))return null;MeterRenderer.MeterSpec m=new MeterRenderer.MeterSpec();
        m.width=config.warmthWidth();m.height=config.warmthHeight();m.value=state.warmth();m.label="Warmth";m.shape=config.warmthShape();m.direction=config.warmthFillDirection();
        m.fillColor=config.warmthFillColor();m.emptyColor=config.warmthEmptyColor();m.borderColor=config.warmthBorderColor();m.borderWidth=config.warmthBorderWidth();m.textMode=config.warmthTextMode();
        m.prefix=config.warmthPrefix();m.suffix=config.warmthSuffix();m.fontName=config.warmthFontName();m.fontSize=config.warmthFontSize();m.bold=config.warmthBold();m.textColor=config.warmthTextColor();m.textX=config.warmthTextX();m.textY=config.warmthTextY();
        m.flash=alerts.meterActive(AlertKind.MeterTarget.WARMTH)&&pulseOn();m.flashColor=config.meterFlashColor();applyPreset(m,true);
        int food=state.inWintertodt()&&config.warmthConsumablePreviewEnabled()?consumable.hoveredWarmthGain():0;
        int basePulse=state.inWintertodt()?Math.max(0,regen.basePulseAmount()):0;int pulse=config.warmthRegenPreviewEnabled()?basePulse:0;
        m.previewPrimaryAmount=food;m.previewSecondaryAmount=pulse;m.previewPrimaryColor=config.warmthConsumablePreviewColor();m.previewSecondaryColor=config.warmthRegenPreviewColor();m.previewLabel=food>0?"+"+food:"";
        m.outerProgressEnabled=config.warmthRegenRingEnabled()&&state.inWintertodt();m.outerProgress=regen.progress();m.outerProgressWidth=config.warmthRegenRingWidth();m.outerProgressColor=config.warmthRegenRingColor();m.outerTrackColor=config.warmthRegenRingTrackColor();
        m.outerLabel=config.warmthRegenAmountText()&&basePulse>0?"+"+basePulse:"";
        return MeterRenderer.render(g,m);}
}

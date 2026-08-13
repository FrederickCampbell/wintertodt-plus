package com.freddy.wintertodthud;
import java.awt.*;import javax.inject.Inject;import net.runelite.api.Client;import net.runelite.api.gameval.VarbitID;
public class EnergyOverlay extends AbstractMeterOverlay{
    private final Client client;
    @Inject EnergyOverlay(WintertodtHudPlugin p,WintertodtState s,WintertodtHudConfig c,AlertEngine a,Client client){super(p,s,c,a);this.client=client;}
    @Override public Dimension render(Graphics2D g){if(!visible(config.energyEnabled()))return null;int respawn=state.inWintertodt()?client.getVarbitValue(VarbitID.WINT_TRANSMIT_RESPAWNDELAY):0;boolean countdown=config.energyNextRoundTimerEnabled()&&!state.roundActive()&&respawn>0;if(!countdown&&state.energy()<0)return null;MeterRenderer.MeterSpec m=new MeterRenderer.MeterSpec();
        m.width=config.energyWidth();m.height=config.energyHeight();m.value=state.energy();m.label="Energy";m.shape=config.energyShape();m.direction=config.energyFillDirection();
        m.fillColor=config.energyFillColor();m.emptyColor=config.energyEmptyColor();m.borderColor=config.energyBorderColor();m.borderWidth=config.energyBorderWidth();m.textMode=config.energyTextMode();
        m.prefix=config.energyPrefix();m.suffix=config.energySuffix();m.fontName=config.energyFontName();m.fontSize=config.energyFontSize();m.bold=config.energyBold();m.textColor=config.energyTextColor();m.textX=config.energyTextX();m.textY=config.energyTextY();
        m.flash=alerts.meterActive(AlertKind.MeterTarget.ENERGY)&&pulseOn();m.flashColor=config.meterFlashColor();applyPreset(m,false);
        if(countdown){m.value=RoundRestartMath.fillPercent(respawn);m.customText=RoundRestartMath.label(respawn);}
        return MeterRenderer.render(g,m);}
}

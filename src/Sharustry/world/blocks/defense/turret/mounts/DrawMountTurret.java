package Sharustry.world.blocks.defense.turret.mounts;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.part.DrawPart;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.world.Block;

public class DrawMountTurret {
    public Seq<DrawPart> parts = new Seq<>();
    /** Prefix to use when loading base region. */
    public String basePrefix = "";
    /** Overrides the liquid to draw in the liquid region. */
    public @Nullable Liquid liquidDraw;
    public TextureRegion liquid, top, heat, preview, outline, mask;

    public DrawMountTurret(String basePrefix){
        this.basePrefix = basePrefix;
    }

    public DrawMountTurret(){
    }

    float drawX(MountTurretType.MountTurret mount) {
        return mount.x;
    }
    float drawY(MountTurretType.MountTurret mount) {
        return mount.y;
    }
    public void draw(MountTurretType.MountTurret mount){
        float mountX = drawX(mount), mountY = drawY(mount);
        Draw.color();

        Draw.z(Layer.turret + 5 - 0.5f);

        Drawf.shadow(preview, mountX + mount.recoilOffset.x - mount.type.elevation, mountY + mount.recoilOffset.y - mount.type.elevation, mount.drawrot());

        Draw.z(Layer.turret + 5);

        drawTurret(mount);
        drawHeat(mount);

        if(outline.found()){
            //draw outline under everything when parts are involved
            Draw.z(Layer.turret + 5 - 0.01f);
            Draw.rect(outline, mountX + mount.recoilOffset.x, mountY + mount.recoilOffset.y, mount.drawrot());
            Draw.z(Layer.turret + 5);
        }
        if(parts.size > 0){
            float progress = mount.progress();

            //TODO no smooth reload
            var params = DrawPart.params.set(mount.warmup(), 1f - progress, 1f - progress, mount.heat, mount.curRecoil, mount.charge, mountX + mount.recoilOffset.x, mountY + mount.recoilOffset.y, mount.rotation);

            for(var part : parts){
                part.draw(params);
            }
        }
    }

    public void drawTurret(MountTurretType.MountTurret mount){
        float mountX = drawX(mount), mountY = drawY(mount);

        Draw.rect(mount.type.region, mountX + mount.recoilOffset.x, mountY + mount.recoilOffset.y, mount.drawrot());

        if(liquid.found()){
            Liquid toDraw = liquidDraw == null ? mount.build.liquids.current() : liquidDraw;
            Drawf.liquid(liquid, mountX + mount.recoilOffset.x, mountY + mount.recoilOffset.y, mount.build.liquids.get(toDraw) / mount.block.liquidCapacity, toDraw.color.write(Tmp.c1).a(1f), mount.drawrot());
        }

        if(top.found()){
            Draw.rect(top, mountX + mount.recoilOffset.x, mountY + mount.recoilOffset.y, mount.drawrot());
        }
    }

    public void drawSelect(MountTurretType.MountTurret mount) {
        float mountX = drawX(mount), mountY = drawY(mount);

        float fade = 
            Mathf.curve(Time.time % mount.block.totalRangeTime, 
                mount.block.rangeTime * mount.mountIndex, 
                mount.block.rangeTime * mount.mountIndex + mount.block.fadeTime) - 
            Mathf.curve(Time.time % mount.block.totalRangeTime, 
                mount.block.rangeTime * (mount.mountIndex + 1) - mount.block.fadeTime, 
                mount.block.rangeTime * (mount.mountIndex + 1));
        Lines.stroke(3, Pal.gray);
        Draw.alpha(fade);

        Lines.dashCircle(mountX, mountY, mount.type.range);
        Lines.stroke(1, mount.canHeal() ? Pal.heal : mount.build.team.color);
        Draw.alpha(fade);
        Lines.dashCircle(mountX, mountY, mount.type.range);
        Draw.z(Layer.turret + 5 + 1);
        Draw.color(mount.build.team.color, fade);
        Draw.rect(mask, mountX, mountY, mount.drawrot());
    }
    public void drawHeat(MountTurretType.MountTurret mount) {
        if(mount.heat <= 0.00001f || !heat.found()) return;

        Drawf.additive(heat, mount.type.heatColor.write(Tmp.c1).a(mount.heat), drawX(mount) + mount.recoilOffset.x, drawY(mount) + mount.recoilOffset.y, mount.drawrot(), Layer.turretHeat);
    }

    /** Load any relevant texture regions. */
    public void load(MountTurretType type){
        preview = Core.atlas.find("shar-" + type.name + "-preview", type.region);
        outline = Core.atlas.find("shar-" + type.name + "-outline");
        liquid = Core.atlas.find("shar-" + type.name + "-liquid");
        top = Core.atlas.find("shar-" + type.name + "-top");
        heat = Core.atlas.find("shar-" + type.name + "-heat");
        mask = Core.atlas.find("shar-" + type.name + "-mask");
        for(var part : parts){
            part.turretShading = true;
            part.load("shar-" + type.name);
        }
    }
}

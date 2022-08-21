package Sharustry.world.blocks.defense.turret.mounts;

import Sharustry.world.blocks.defense.turret.*;
import arc.Core;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.entities.*;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.LAccess;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.consumers.ConsumeLiquidFilter;
import mindustry.world.meta.BlockStatus;

import static mindustry.Vars.tilesize;

public class MountTurret<T extends MountTurretType> {
    public float reloadCounter = 0f;
    public float shotCounter = 0f;
    public float rotation = 90f;
    public float strength = 0f;
    public boolean wasShooting = false;
    public boolean charging = false;
    public int totalAmmo;
    public float curRecoil, heat, reTargetHeat;
    public float shootWarmup, charge;
    public int totalShots;
    public float x, y, xOffset, yOffset;
    public int mountIndex;
    public int queuedBullets;
    public int skillCounter;
    Seq<ItemEntry> ammo = new Seq<>();

    public MultiTurret block;
    public Posc target;
    public Vec2 targetPos = new Vec2();
    public Vec2 recoilOffset = new Vec2();

    public T type;
    MultiTurret.MultiTurretBuild build;
    public MountTurret(T type, MultiTurret block, MultiTurret.MultiTurretBuild build, int mountIndex, float xOffset, float yOffset){
        this.type = type;
        this.block = block;
        this.build = build;
        this.mountIndex = mountIndex;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.x = build.x + xOffset;
        this.y = build.y + yOffset;
    }

    public float getPowerEfficiency(){
        return Mathf.clamp(build.power.graph.getPowerBalance()/type.powerUse, 0, 1);
    }

    public Vec2 getMountLocation(){
        return new Vec2(x, y);
    }

    public BlockStatus status() {
        if(hasAmmo()) return BlockStatus.active;
        return BlockStatus.noInput;
    }

    public void control(LAccess type, double p1, double p2){
        if(type == LAccess.shoot && !build.unit.isPlayer())
            targetPos.set(World.unconv((float)p1), World.unconv((float)p2));
    }

    public void control(LAccess type, Object p1){
        if(type == LAccess.shootp && !build.unit.isPlayer() && p1 instanceof Posc pos){
            if(!hasAmmo()) return;
            BulletType bullet = peekAmmo();
            float speed = bullet.speed;
            //slow bullets never intersect
            if(speed < 0.1f) speed = 9999999f;

            targetPos.set(Predict.intercept(build, pos, speed));
            if(targetPos.isZero()) targetPos.set(pos);
        }
    }
    public boolean isShooting(){
        return (build.isControlled() ? build.unit.isShooting() : build.logicControlled() ? build.logicShooting : target != null);
    }
    public void removeFromProximity(){ }
    public void handleItem(Item item){ }
    public boolean acceptItem(Item item){
        return false;
    }

    public boolean acceptLiquid(Liquid liquid) {
        return false;
    }

    public int acceptStack(Item item, int amount){
        return 0;
    }

    public void display(Table table){
        if(block.basicMounts.size > 3 && mountIndex % 4 == 0) table.row();
        else if(mountIndex % 4 == 0) table.row();
        table.stack(
            new Table(o -> {
                o.left();
                o.image(Core.atlas.find("shar-" + type.name + "-full")).size(5*8f);
            }),
            new Table(h -> {
                h.stack(
                    new Table(e -> {
                        e.defaults().growX().height(9).width(42f).padRight(2*8).padTop(8*2f);
                        e.add(new Bar("", Pal.powerBar, this::getPowerEfficiency));
                        e.pack();
                    }),
                    new Table(e -> {
                        e.defaults().growX().height(9).width(42f).padRight(2*8).padTop(8*5f);
                        e.add(new Bar(() -> "", () -> Pal.accent.cpy().lerp(Color.orange, reloadCounter / type.reload), () -> reloadCounter / type.reload));
                        e.pack();
                    }),
                    new Table(e -> {
                        if(type.chargeTime <= 0) return;
                        e.defaults().growX().height(9).width(42f).padRight(2*8).padTop(8*8f);
                        e.add(new Bar(() -> "", () -> Pal.surge.cpy().lerp(Pal.accent, charge / type.chargeTime), () -> charge));
                        e.pack();
                    }),
                    new Table(e -> {
                        MultiReqImage powerReq = new MultiReqImage();
                        powerReq.add(new ReqImage(Icon.powerSmall.getRegion(), () -> getPowerEfficiency() >= 0.001f));
                        e.add(powerReq);
                    })
                ).padTop(2*8).padLeft(2*8);
                h.pack();
            })
        ).left().size(7*8f);
    }


    public void targetPosition(Posc pos){
        if(!hasAmmo() || pos == null) return;
        BulletType bullet = peekAmmo();

        Vec2 offset = Tmp.v1.setZero();

        //when delay is accurate, assume unit has moved by chargeTime already
        if(type.accurateDelay && pos instanceof Hitboxc h){
            offset.set(h.deltaX(), h.deltaY()).scl(type.shoot.firstShotDelay / Time.delta);
        }

        targetPos.set(Predict.intercept(build, pos, offset.x, offset.y, bullet.speed <= 0.01f ? 99999999f : bullet.speed));

        if(targetPos.isZero()){
            targetPos.set(pos);
        }
    }
    public float progress(){
        return Mathf.clamp(reloadCounter / type.reload);
    }

    public float warmup(){
        return shootWarmup;
    }
    public float drawrot(){
        return rotation - 90;
    }
    public void draw() {
        type.drawer.draw(this);
    }
    public void drawConfigure() { }

    public void handlePayload(Bullet bullet, DriverBulletData data){ }

    public boolean onConfigureTileTapped(Building other){ return true; }
    public void update() {
        if(!Vars.headless && type.loopSound != null) {
            Vec2 vec = getMountLocation();
            type.loopSoundLoop.update(vec.x, vec.y, wasShooting && !build.dead());
        }
    }
    public void updateTile() {
        if(block.hasItems) build.unit.ammo((float)build.unit.type().ammoCapacity * totalAmmo /  type.maxAmmo);
        if(block.hasLiquids) build.unit.ammo(build.unit.type().ammoCapacity * build.liquids.currentAmount() / block.liquidCapacity);
        if(block.hasPower) build.unit.ammo(build.power.status * build.unit.type().ammoCapacity);

        if(!validateTarget()) target = null;

        float warmupTarget = isShooting() && build.canConsume() ? 1f : 0f;
        if(type.linearWarmup){
            shootWarmup = Mathf.approachDelta(shootWarmup, warmupTarget, type.shootWarmupSpeed * (warmupTarget > 0 ? build.efficiency : 1f));
        }else{
            shootWarmup = Mathf.lerpDelta(shootWarmup, warmupTarget, type.shootWarmupSpeed * (warmupTarget > 0 ? build.efficiency : 1f));
        }

        wasShooting = false;

        curRecoil = Mathf.approachDelta(curRecoil, 0, 1 / type.recoilTime);
        heat = Mathf.approachDelta(heat, 0, 1 / type.cooldown);
        charge = charging() ? Mathf.approachDelta(charge, 1, 1 / type.shoot.firstShotDelay) : 0;
        recoilOffset.trns(rotation, -Mathf.pow(curRecoil, type.recoilPow) * type.recoil);
        reTargetHeat += Time.delta;

        updateReload();
        if(hasAmmo()){
            if(Float.isNaN(reloadCounter)) reloadCounter = 0;

            if(reTargetHeat >= 20f){
                reTargetHeat = 0;
                findTarget();
            }

            if(validateTarget()){
                boolean canShoot = true;

                if(build.isControlled()){ //player behavior
                    targetPos.set(build.unit.aimX(), build.unit.aimY());
                    canShoot = build.unit.isShooting();
                }else if(build.logicControlled()){ //logic behavior
                    canShoot = build.logicShooting;
                }else{ //default AI behavior
                    targetPosition(target);

                    if(Float.isNaN(rotation)) rotation = 0;
                }

                if(!build.isControlled()){
                    build.unit.aimX(targetPos.x);
                    build.unit.aimY(targetPos.y);
                }

                float targetRot = build.angleTo(targetPos);
                if(shouldTurn()){
                    turnToTarget(targetRot);
                }

                if(Angles.angleDist(rotation, targetRot) < type.shootCone && canShoot){
                    wasShooting = true;
                    updateShooting();
                }
            }
        }

        if(block.coolant != null) {
            updateCooling();
        }
    }


    protected boolean validateTarget(){
        return !Units.invalidateTarget(target, canHeal() ? Team.derelict : build.team, x,y) || build.isControlled() || build.logicControlled();
    }

    protected boolean canHeal(){
        return type.targetHealing && hasAmmo() && peekAmmo().collidesTeam && peekAmmo().heals();
    }
    public void findTarget(){
        if(type.targetAir && !type.targetGround){
            target = Units.bestEnemy(build.team, x,y, type.range, e -> !e.dead() && !e.isGrounded() && type.unitFilter.get(e), type.unitSort);
        }else{
            target = Units.bestTarget(build.team, x, y, type.range, e -> !e.dead() && type.unitFilter.get(e) && (e.isGrounded() || type.targetAir) && (!e.isGrounded() || type.targetGround), b -> type.targetGround && type.buildingFilter.get(b), type.unitSort);

            if(target == null && canHeal()){
                target = Units.findAllyTile(build.team, x, y, type.range, b -> b.damaged() && b != build);
            }
        }
    }

    public void turnToTarget(float targetRot){
        rotation = Angles.moveToward(rotation, targetRot, type.rotateSpeed * build.delta());
    }
    public boolean shouldTurn(){
        return type.moveWhileCharging || !charging();
    }
    public boolean cheating() {return build.team.rules().cheat;}

    public BulletType useAmmo(){
        if(cheating()) return peekAmmo();

        ItemEntry entry = ammo.peek();
        entry.amount -= type.ammoPerShot;
        if(entry.amount <= 0) ammo.pop();
        totalAmmo -= type.ammoPerShot;
        totalAmmo = Math.max(totalAmmo, 0);
        return peekAmmo();
    }

    public BulletType peekAmmo(){
        return type.bullet;
    }
    public boolean hasAmmo(){
        //used for "side-ammo" like gas in some turrets
        if(!build.canConsume()) return false;

        //skip first entry if it has less than the required amount of ammo
        if(ammo.size >= 2 && ammo.peek().amount < type.ammoPerShot && ammo.get(ammo.size - 2).amount >= type.ammoPerShot){
            ammo.swap(ammo.size - 1, ammo.size - 2);
        }
        return ammo.size > 0 && ammo.peek().amount >= type.ammoPerShot;
    }

    public boolean charging(){
        return queuedBullets > 0 && type.shoot.firstShotDelay > 0;
    }
    public void updateReload() {
        float multiplier = hasAmmo() ? peekAmmo().reloadMultiplier : 1f;
        reloadCounter += build.delta() * multiplier;

        //cap reload for visual reasons
        reloadCounter = Math.min(reloadCounter, type.reload);
    }

    public void updateShooting(){
        if(reloadCounter >= type.reload && shootWarmup >= type.minWarmup){
            shoot(peekAmmo());
            reloadCounter %= type.reload;
        }
    }

    protected void shoot(BulletType bullet){
        float
                bulletX = x + Angles.trnsx(rotation - 90, type.shootX, type.shootY),
                bulletY = y + Angles.trnsy(rotation - 90, type.shootX, type.shootY);

        if(type.shoot.firstShotDelay > 0){
            type.chargeSound.at(bulletX, bulletY, Mathf.random(type.soundPitchMin, type.soundPitchMax));
            bullet.chargeEffect.at(bulletX, bulletY, rotation);
        }

        type.shoot.shoot(totalShots, (xOffset, yOffset, angle, delay, mover) -> {
            queuedBullets ++;
            if(delay > 0f){
                Time.run(delay, () -> bullet(bullet, xOffset, yOffset, angle, mover));
            }else{
                bullet(bullet, xOffset, yOffset, angle, mover);
            }
            totalShots ++;
        });

        if(type.consumeAmmoOnce){
            useAmmo();
        }

        if(!type.sequential) skillCounter++;
        for(int i = 0; i < type.skillDelays.size; i++) if(skillCounter % type.skillDelays.get(i) == 0) {
            skillCounter = 0;
            type.skillSeq.get(i).get(build, type).run();
        }
    }

    public void ejectEffects(){
        if(!build.isValid()) return;

        int side = Mathf.signs[(int) (shotCounter % 2)];
        Vec2 vec = getMountLocation();

        type.ejectEffect.at(vec.x, vec.y, rotation * side);
    }

    protected void handleBullet(@Nullable Bullet bullet, float offsetX, float offsetY, float angleOffset){

    }
    protected void bullet(BulletType bullet, float xOffset, float yOffset, float angleOffset, Mover mover){
        queuedBullets --;

        if(build.dead || (!type.consumeAmmoOnce && !hasAmmo())) return;

        float
                xSpread = Mathf.range(type.xRand),
                ySpread = Mathf.range(type.yRand),
                bulletX = build.x + this.xOffset + Angles.trnsx(rotation - 90, type.shootX + xOffset + xSpread, type.shootY + yOffset + ySpread),
                bulletY = build.y + this.yOffset + Angles.trnsy(rotation - 90, type.shootX + xOffset + xSpread, type.shootY + yOffset + ySpread),
                shootAngle = rotation + angleOffset + Mathf.range(type.inaccuracy),
                lifeScl = bullet.scaleLife ? Mathf.clamp(Mathf.dst(bulletX, bulletY, targetPos.x, targetPos.y) / bullet.range, type.minRange / bullet.range, type.range / bullet.range) : 1f;

        //Log.info("build: ("  + build.x + ". "+ build.y +"), offset: ("+ this.xOffset + ", "+ this.yOffset + "), bullet will be on "+bulletX + ", "+bulletY);
        handleBullet(bullet.create(build, build.team, bulletX, bulletY, shootAngle, -1f, (1f - type.velocityRnd) + Mathf.random(type.velocityRnd), lifeScl, null, mover, targetPos.x, targetPos.y), xOffset, yOffset, shootAngle - rotation);

        (type.shootEffect == null ? bullet.shootEffect : type.shootEffect).at(bulletX, bulletY, rotation + angleOffset, bullet.hitColor);
        (type.smokeEffect == null ? bullet.smokeEffect : type.smokeEffect).at(bulletX, bulletY, rotation + angleOffset, bullet.hitColor);
        type.shootSound.at(bulletX, bulletY, Mathf.random(type.soundPitchMin, type.soundPitchMax));

        type.ammoUseEffect.at(
                build.x + this.xOffset - Angles.trnsx(rotation, type.ammoEjectBack),
                build.y + this.yOffset - Angles.trnsy(rotation, type.ammoEjectBack),
                rotation * Mathf.sign(xOffset)
        );

        if(type.shake > 0){
            Effect.shake(type.shake, type.shake, build);
        }

        curRecoil = 1f;
        heat = 1f;

        if(!type.consumeAmmoOnce){
            useAmmo();
        }
    }

    /*TODO make multi cooling*/
    public void updateCooling() {
        if(reloadCounter < type.reload && block.coolant.efficiency(build) > 0 && build.efficiency > 0){
            float capacity = block.coolant instanceof ConsumeLiquidFilter filter ? filter.getConsumed(build).heatCapacity : 1f;
            block.coolant.update(build);
            reloadCounter += block.coolant.amount * build.edelta() * capacity * block.coolantMultiplier;

            if(Mathf.chance(0.06 * block.coolant.amount)){
                type.coolEffect.at(xOffset + Mathf.range(block.size * tilesize / 2f), yOffset + Mathf.range(block.size * tilesize / 2f));
            }
        }
    }

    public void write(Writes write){
        try{
            write.f(reloadCounter);
            write.f(rotation);
        } catch(Throwable e){
            Log.warn(String.valueOf(e));
        }
    }

    public void read(Reads read, byte revision){
        try{
            reloadCounter = read.f();
            rotation = read.f();
        } catch(Throwable e){
            Log.warn(String.valueOf(e));
        }
    }
}
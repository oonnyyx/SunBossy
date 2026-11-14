package pl.onyxdev.bossy;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class StormDragon extends Boss {
    private enum State { ARRIVE, CIRCLE, BOLTS, DIVE, BREATH, LAND, STORM, STUN, ROAR, DEATH }

    private static final Color CYAN = Color.fromRGB(0x5CE1FF);
    private static final Color PALE = Color.fromRGB(0xE8FBFF);
    private static final Color VIOLET = Color.fromRGB(0xB07CFF);
    private static final double FLY_H = 8;

    private final Rig.Bone body, neck1, neck2, head, jaw, wingL1, wingL2, wingR1, wingR2, tail1, tail2, tail3,
            legFL, legFR, legBL, legBR;
    private final List<Rig.Part> glowing = new ArrayList<>();

    private State state = State.ARRIVE;
    private int st;
    private int cooldown = 50;
    private int attacksSinceLand;
    private boolean phase2, storm70, storm40;
    private int lastStormT, nextAmbientStrike;
    private Player target;

    private Location pos;
    private final Vector vel = new Vector();
    private double circleA;
    private double flap;
    private float yawDelta;
    private boolean landed;

    private ArmorStand chestBox, rearBox, headBox;

    private Location diveFrom, diveTo, diveMid;
    private Vector diveDir;
    private int diveT = -1;
    private final Set<UUID> diveHit = new HashSet<>();
    private Location hoverAt, landAt;
    private int actionT, actionKind;
    private final List<Strike> strikes = new ArrayList<>();
    private final List<StormOrb> orbs = new ArrayList<>();
    private final List<Crystal> crystals = new ArrayList<>();
    private boolean crashing;

    public StormDragon(SunBossy plugin, Location home) {
        super(plugin, "smok", home, BossBar.bossBar(MM.deserialize("<#5ce1ff><b>⚡ Smok Burzy</b>"), 1f,
                BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        Material scale = Material.CYAN_TERRACOTTA, armor = Material.DEEPSLATE_TILES, belly = Material.LIGHT_BLUE_TERRACOTTA,
                fin = Material.CALCITE, glow = Material.SEA_LANTERN, membrane = Material.CYAN_STAINED_GLASS, bone = Material.CALCITE;
        Quaternionf spike = new Quaternionf().rotateX((float) Math.PI / 4);

        body = rig.bone(null, 0, 3.0f, 0);
        rig.part(body, scale, 0, 3.0f, 0, 2.2f, 1.8f, 4.0f);
        rig.part(body, scale, 0, 3.05f, 2.1f, 1.8f, 1.5f, 0.6f);
        rig.part(body, belly, 0, 2.08f, 0.1f, 1.8f, 0.12f, 3.6f);
        rig.part(body, armor, 0, 3.95f, 0.2f, 1.9f, 0.16f, 3.5f);
        rig.part(body, armor, 0, 3.4f, 2.42f, 1.5f, 1.0f, 0.1f);
        glowing.add(rig.part(body, glow, 0, 2.01f, 0.1f, 0.3f, 0.04f, 3.0f));
        for (int s = -1; s <= 1; s += 2) glowing.add(rig.part(body, glow, 1.11f * s, 3.0f, 0, 0.04f, 0.12f, 3.2f));
        for (float z : new float[]{1.3f, 0.3f, -0.7f, -1.6f}) rig.part(body, fin, 0, 4.0f, z, 0.2f, 0.55f, 0.55f, spike);

        neck1 = rig.bone(body, 0, 3.4f, 1.9f);
        rig.part(neck1, scale, 0, 3.55f, 2.75f, 1.25f, 1.15f, 1.8f);
        rig.part(neck1, armor, 0, 4.14f, 2.75f, 1.05f, 0.1f, 1.7f);
        rig.part(neck1, belly, 0, 2.97f, 2.75f, 1.0f, 0.06f, 1.6f);
        rig.part(neck1, fin, 0, 4.2f, 2.7f, 0.18f, 0.45f, 0.45f, spike);
        neck2 = rig.bone(neck1, 0, 3.6f, 3.6f);
        rig.part(neck2, scale, 0, 3.7f, 4.3f, 1.0f, 0.95f, 1.5f);
        rig.part(neck2, armor, 0, 4.2f, 4.3f, 0.85f, 0.1f, 1.4f);
        rig.part(neck2, fin, 0, 4.25f, 4.3f, 0.16f, 0.4f, 0.4f, spike);

        head = rig.bone(neck2, 0, 3.75f, 5.0f);
        rig.part(head, scale, 0, 3.95f, 5.7f, 1.35f, 1.05f, 1.4f);
        rig.part(head, armor, 0, 4.5f, 6.0f, 1.45f, 0.2f, 1.0f);
        rig.part(head, scale, 0, 3.75f, 6.85f, 0.95f, 0.6f, 1.1f);
        rig.part(head, armor, 0, 3.9f, 7.35f, 0.6f, 0.25f, 0.2f);
        Quaternionf horn = new Quaternionf().rotateX(0.45f);
        for (int s = -1; s <= 1; s += 2) {
            glowing.add(rig.part(head, glow, 0.69f * s, 4.1f, 6.1f, 0.04f, 0.14f, 0.34f));
            rig.part(head, bone, 0.45f * s, 4.55f, 5.1f, 0.18f, 0.18f, 1.3f, horn);
            rig.part(head, armor, 0.75f * s, 3.9f, 5.3f, 0.1f, 0.5f, 0.6f);
        }
        jaw = rig.bone(head, 0, 3.5f, 5.4f);
        rig.part(jaw, scale, 0, 3.35f, 6.35f, 0.85f, 0.28f, 1.7f);
        glowing.add(rig.part(jaw, glow, 0, 3.5f, 6.4f, 0.5f, 0.05f, 1.2f));
        for (int s = -1; s <= 1; s += 2) rig.part(jaw, bone, 0.3f * s, 3.53f, 6.9f, 0.08f, 0.12f, 0.6f);

        wingL1 = rig.bone(body, -1.05f, 3.7f, 0.9f);
        wingR1 = rig.bone(body, 1.05f, 3.7f, 0.9f);
        wingL2 = rig.bone(wingL1, -4.45f, 3.7f, 0.9f);
        wingR2 = rig.bone(wingR1, 4.45f, 3.7f, 0.9f);
        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone w1 = s < 0 ? wingL1 : wingR1, w2 = s < 0 ? wingL2 : wingR2;
            rig.part(w1, armor, 2.75f * s, 3.7f, 0.9f, 3.4f, 0.32f, 0.32f);
            rig.part(w1, membrane, 2.75f * s, 3.62f, -0.45f, 3.4f, 0.06f, 2.5f);
            rig.part(w1, membrane, 1.9f * s, 3.62f, -2.1f, 1.7f, 0.06f, 0.8f);
            rig.part(w1, armor, 3.2f * s, 3.66f, -0.7f, 0.1f, 0.1f, 2.3f, new Quaternionf().rotateY(0.35f * s));
            rig.part(w2, armor, 6.0f * s, 3.7f, 0.75f, 3.1f, 0.26f, 0.26f);
            rig.part(w2, membrane, 5.9f * s, 3.62f, -0.35f, 2.9f, 0.06f, 2.1f);
            rig.part(w2, armor, 5.1f * s, 3.66f, -0.4f, 0.09f, 0.09f, 2.1f, new Quaternionf().rotateY(-0.3f * s));
            rig.part(w2, armor, 6.6f * s, 3.66f, -0.3f, 0.09f, 0.09f, 1.9f, new Quaternionf().rotateY(-0.55f * s));
            rig.part(w2, bone, 7.6f * s, 3.7f, 0.6f, 0.3f, 0.2f, 0.2f);
            rig.part(w1, bone, 4.45f * s, 3.9f, 1.0f, 0.18f, 0.4f, 0.18f);
            glowing.add(rig.part(w2, glow, 5.9f * s, 3.62f, -1.42f, 2.9f, 0.07f, 0.06f));
        }

        tail1 = rig.bone(body, 0, 3.0f, -1.9f);
        rig.part(tail1, scale, 0, 3.0f, -2.8f, 1.2f, 1.0f, 1.8f);
        rig.part(tail1, fin, 0, 3.6f, -2.8f, 0.18f, 0.45f, 0.45f, spike);
        tail2 = rig.bone(tail1, 0, 3.0f, -3.7f);
        rig.part(tail2, scale, 0, 3.0f, -4.5f, 0.85f, 0.7f, 1.6f);
        rig.part(tail2, fin, 0, 3.45f, -4.5f, 0.15f, 0.35f, 0.35f, spike);
        tail3 = rig.bone(tail2, 0, 3.0f, -5.3f);
        rig.part(tail3, scale, 0, 3.0f, -6.1f, 0.55f, 0.45f, 1.6f);
        rig.part(tail3, armor, 0, 3.0f, -7.2f, 1.1f, 0.08f, 0.9f, new Quaternionf().rotateY((float) Math.PI / 4));
        glowing.add(rig.part(tail3, glow, 0, 3.0f, -7.2f, 0.45f, 0.1f, 0.45f, new Quaternionf().rotateY((float) Math.PI / 4)));
        rig.part(tail1, armor, 0, 3.52f, -2.8f, 1.0f, 0.08f, 1.7f);
        rig.part(tail2, armor, 0, 3.37f, -4.5f, 0.7f, 0.07f, 1.5f);

        legFL = rig.bone(body, -0.8f, 2.4f, 1.3f);
        legFR = rig.bone(body, 0.8f, 2.4f, 1.3f);
        legBL = rig.bone(body, -0.85f, 2.5f, -1.2f);
        legBR = rig.bone(body, 0.85f, 2.5f, -1.2f);
        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone f = s < 0 ? legFL : legFR, b = s < 0 ? legBL : legBR;
            rig.part(f, scale, 0.8f * s, 1.75f, 1.35f, 0.5f, 1.3f, 0.55f);
            rig.part(f, bone, 0.8f * s, 1.08f, 1.6f, 0.55f, 0.15f, 0.4f);
            rig.part(b, scale, 0.85f * s, 1.8f, -1.2f, 0.6f, 1.4f, 0.7f);
            rig.part(b, bone, 0.85f * s, 1.05f, -0.95f, 0.6f, 0.15f, 0.5f);
        }
    }

    @Override
    public String displayName() {
        return "<#5ce1ff><b>⚡ Smok Burzy</b></#5ce1ff>";
    }

    @Override
    public void spawnAt(float yaw) {
        rig.yaw = yaw;
        pos = home.clone().add(forwardOf(yaw).multiply(-30)).add(0, 22, 0);
        pos.setYaw(0);
        pos.setPitch(0);
        rig.spawn(pos);
        for (Rig.Part p : glowing) if (p.d != null) p.d.setBrightness(new Display.Brightness(15, 15));
        chestBox = addHurtbox(home.clone().add(0, -20, 0), 2.2f);
        rearBox = addHurtbox(home.clone().add(0, -20, 0), 2.0f);
        headBox = addHurtbox(home.clone().add(0, -20, 0), 1.2f);
    }

    @Override
    protected void onTick() {
        st++;
        switch (state) {
            case ARRIVE -> arrive();
            case CIRCLE -> circle();
            case BOLTS -> bolts();
            case DIVE -> dive();
            case BREATH -> breath();
            case LAND -> land();
            case STORM -> storm();
            case STUN -> stun();
            case ROAR -> roar();
            case DEATH -> death();
        }
        strikes.removeIf(Strike::tick);
        orbs.removeIf(StormOrb::tick);
        for (Crystal c : crystals) c.tick();
        if (phase2 && state != State.DEATH) stormWeather();
        bodyTilt();
        if (t % 2 == 0) {
            rig.moveTo(pos, 2);
            rig.apply(2);
        }
        if (state != State.DEATH) placeHurtboxes();
    }

    private void go(State s) {
        state = s;
        st = 0;
    }

    private static Vector forwardOf(float yaw) {
        double y = Math.toRadians(yaw);
        return new Vector(-Math.sin(y), 0, Math.cos(y));
    }

    private Vector forward() {
        return forwardOf(rig.yaw);
    }

    private int cd(int ticks) {
        return phase2 ? (int) (ticks * 0.7) : ticks;
    }

    private void placeHurtboxes() {
        teleportBox(chestBox, rig.worldPoint(body, 0, 3.0f, 1.0f), 2.2f);
        teleportBox(rearBox, rig.worldPoint(body, 0, 3.0f, -1.3f), 2.0f);
        teleportBox(headBox, rig.worldPoint(head, 0, 3.9f, 6.2f), 1.2f);
    }

    private static void teleportBox(ArmorStand s, Location center, float scale) {
        if (s == null) return;
        s.teleport(center.subtract(0, 1.975 * scale / 2, 0));
    }

    private void flyTo(Location goal, double maxSpeed, double accel, boolean faceMove) {
        Vector want = goal.toVector().subtract(pos.toVector());
        double d = want.length();
        if (d > 0.001) want.multiply(Math.min(maxSpeed, d * 0.2) / d);
        else want.zero();
        vel.add(want.subtract(vel).multiply(accel));
        pos.add(vel);
        if (faceMove && vel.clone().setY(0).lengthSquared() > 0.01) turnTo(pos.clone().add(vel.clone().multiply(10)), 5);
    }

    private void turnTo(Location at, float maxDeg) {
        Vector d = at.toVector().subtract(pos.toVector());
        if (d.clone().setY(0).lengthSquared() < 0.01) return;
        float want = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float diff = ((want - rig.yaw) % 360 + 540) % 360 - 180;
        float step = Math.max(-maxDeg, Math.min(maxDeg, diff));
        rig.yaw += step;
        yawDelta = yawDelta * 0.8f + step * 0.2f;
    }

    private void bodyTilt() {
        if (state == State.DEATH && st > 50) return;
        float pitch = landed ? 0 : (float) Math.max(-0.45, Math.min(0.45, -vel.getY() * 1.1));
        float bank = landed ? 0 : Math.max(-0.5f, Math.min(0.5f, -yawDelta * 0.12f));
        if (state == State.DEATH) bank = (float) Math.sin(st * 0.4) * 0.8f;
        body.rot.identity().rotateZ(bank).rotateX(pitch);
    }

    private void flyPose(double amp, double speed) {
        double before = Math.sin(flap);
        flap += speed;
        double f = Math.sin(flap);
        float w1 = (float) (f * amp), w2 = (float) (Math.sin(flap - 0.7) * amp * 0.8);
        wingL1.pose(0, 0, -w1 - 0.05f);
        wingR1.pose(0, 0, w1 + 0.05f);
        wingL2.pose(0, 0, -w2 - 0.12f);
        wingR2.pose(0, 0, w2 + 0.12f);
        body.offset.set(0, (float) (-f * 0.2), 0);
        tailSway(0.12f);
        neck1.pose(-0.12f, 0, 0);
        neck2.pose(0.06f, 0, 0);
        head.pose(0.12f, 0, 0);
        jaw.pose(0, 0, 0);
        for (Rig.Bone l : new Rig.Bone[]{legFL, legFR, legBL, legBR}) l.pose(0.95f, 0, 0);
        if (before > 0 && f <= 0) sound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, phase2 ? 0.8f : 0.7f);
    }

    private void tailSway(float amp) {
        tail1.pose(0.06f, (float) Math.sin(t * 0.07) * amp, 0);
        tail2.pose(0.05f, (float) Math.sin(t * 0.07 - 0.8) * amp * 1.3f, 0);
        tail3.pose(0.05f, (float) Math.sin(t * 0.07 - 1.6) * amp * 1.6f, 0);
    }

    private void groundPose() {
        body.offset.set(0, -0.95f, 0);
        for (Rig.Bone l : new Rig.Bone[]{legFL, legFR, legBL, legBR}) l.pose(0, 0, 0);
        wingL1.pose(0, -0.85f, 0.35f);
        wingR1.pose(0, 0.85f, -0.35f);
        wingL2.pose(0, -0.7f, 0.25f);
        wingR2.pose(0, 0.7f, -0.25f);
        neck1.pose(-0.35f, 0, 0);
        neck2.pose(0.15f, 0, 0);
        head.pose(0.25f, 0, 0);
        jaw.pose(0, 0, 0);
        tailSway(0.2f);
    }

    private Location groundY(Location at) {
        Location g = groundBelow(at.clone(), 6, 40);
        if (g == null) {
            g = at.clone();
            g.setY(home.getY());
        }
        return g;
    }

    private void arrive() {
        invulnerable = true;
        Location hover = home.clone().add(0, FLY_H, 0);
        if (st == 1) {
            titleNear("<#5ce1ff><b>SMOK BURZY</b>", "<gray>nadlatuje…", 72);
            sound(home, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.8f);
        }
        flyTo(hover, 0.7, 0.06, true);
        flyPose(0.8, 0.17);
        if (st == 30 || st == 55) {
            Location l = home.clone().add(ThreadLocalRandom.current().nextDouble(-8, 8), 0, ThreadLocalRandom.current().nextDouble(-8, 8));
            l.getWorld().strikeLightningEffect(groundY(l));
        }
        if (st == 100) {
            sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.6f);
            sound(pos, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.7f);
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i + 0.4;
                home.getWorld().strikeLightningEffect(groundY(home.clone().add(Math.cos(a) * 9, 0, Math.sin(a) * 9)));
            }
        }
        if (st >= 120) {
            invulnerable = false;
            go(State.CIRCLE);
        }
    }

    private void circle() {
        landed = false;
        if (checkPhaseAndStorm()) return;
        target = nearestPlayer(48);
        circleA += phase2 ? 0.028 : 0.021;
        double r = 12;
        Location goal = home.clone().add(Math.cos(circleA) * r, FLY_H + Math.sin(t * 0.03) * 1.5, Math.sin(circleA) * r);
        flyTo(goal, phase2 ? 0.6 : 0.5, 0.08, true);
        flyPose(0.75, phase2 ? 0.2 : 0.16);
        if (target == null) return;
        if (--cooldown > 0) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (attacksSinceLand >= 3 || rnd.nextInt(100) < 15) {
            go(State.LAND);
            return;
        }
        attacksSinceLand++;
        int roll = rnd.nextInt(100);
        if (roll < 34) go(State.BOLTS);
        else if (roll < 68) go(State.DIVE);
        else go(State.BREATH);
    }

    private boolean checkPhaseAndStorm() {
        double f = hp / maxHp;
        if (!phase2 && f <= 0.5) {
            go(State.ROAR);
            return true;
        }
        if ((!storm70 && f <= 0.7) || (!storm40 && f <= 0.4) || (phase2 && t - lastStormT > 20 * 75)) {
            if (f <= 0.7) storm70 = true;
            if (f <= 0.4) storm40 = true;
            lastStormT = t;
            go(State.STORM);
            return true;
        }
        return false;
    }

    private void backToCircle(int cool) {
        cooldown = cd(cool);
        go(State.CIRCLE);
    }

    private void bolts() {
        Location hover = home.clone().add(0, FLY_H + 2, 0);
        flyTo(hover, 0.4, 0.1, false);
        if (target != null) turnTo(target.getLocation(), 6);
        flyPose(0.95, 0.24);
        neck1.pose(-0.5f, 0, 0);
        head.pose(0.35f, 0, 0);
        jaw.pose(st > 8 && st < 20 ? 0.45f : 0, 0, 0);
        if (st == 10 || (phase2 && st == 45)) {
            sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 1.1f);
            int n = 0;
            for (Player p : playersNear(40)) {
                if (n++ >= 8) break;
                Location at = groundBelow(p.getLocation(), 2, 10);
                if (at != null) strikes.add(new Strike(at, 30, 2.6, phase2 ? 9 : 8));
            }
            if (phase2) {
                for (int i = 0; i < 2; i++) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    Location at = groundBelow(home.clone().add(r.nextDouble(-10, 10), 0, r.nextDouble(-10, 10)), 6, 12);
                    if (at != null) strikes.add(new Strike(at, 30, 2.6, 8));
                }
            }
        }
        if (st >= (phase2 ? 90 : 60)) backToCircle(40);
    }

    private class Strike {
        final Location at;
        final int delay;
        final double radius, dmg;
        int left;

        Strike(Location at, int delay, double radius, double dmg) {
            this.at = at;
            this.delay = delay;
            this.left = delay;
            this.radius = radius;
            this.dmg = dmg;
            sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
        }

        boolean tick() {
            if (dead) return true;
            left--;
            if (left % 3 == 0) {
                ring(at, radius, CYAN, 1.4f);
                ring(at, Math.max(0.3, radius * left / delay), PALE, 1.0f);
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0.05);
            }
            if (left > 0) return false;
            at.getWorld().strikeLightningEffect(at);
            at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.5, 0), 40, radius * 0.4, 0.3, radius * 0.4, 0.3);
            for (Player p : playersNear(64)) {
                Location l = p.getLocation();
                double dx = l.getX() - at.getX(), dz = l.getZ() - at.getZ();
                if (dx * dx + dz * dz <= radius * radius && Math.abs(l.getY() - at.getY()) < 4) {
                    hurt(p, dmg, new Vector(0, 0.35, 0));
                }
            }
            return true;
        }
    }

    private void dive() {
        if (st == 1) {
            if (target == null || !target.isValid()) {
                backToCircle(20);
                return;
            }
            Location tl = target.getLocation();
            diveDir = tl.toVector().subtract(pos.toVector()).setY(0);
            if (diveDir.lengthSquared() < 0.01) diveDir = forward();
            diveDir.normalize();
            diveMid = groundY(tl);
            diveFrom = diveMid.clone().add(diveDir.clone().multiply(-15)).add(0, 7, 0);
            diveTo = diveMid.clone().add(diveDir.clone().multiply(15)).add(0, 7, 0);
            diveT = -1;
            diveHit.clear();
            sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 1.3f);
        }
        if (diveT < 0) {
            flyTo(diveFrom, 0.8, 0.12, false);
            turnTo(diveMid, 9);
            flyPose(0.9, 0.22);
            if (st % 3 == 0) {
                for (double d = -11; d <= 11; d += 0.7) {
                    Location l = diveMid.clone().add(diveDir.clone().multiply(d)).add(0, 0.2, 0);
                    l.getWorld().spawnParticle(Particle.DUST, l, 1, 0.25, 0, 0.25, 0, new Particle.DustOptions(CYAN, 1.3f));
                }
            }
            if (st >= 35 || (st > 12 && pos.distanceSquared(diveFrom) < 2.5)) {
                diveT = 0;
                sound(pos, Sound.ENTITY_PHANTOM_SWOOP, 2.5f, 0.5f);
            }
            return;
        }
        diveT++;
        double u = diveT / 38.0;
        Location p = diveFrom.clone().add(diveTo.clone().subtract(diveFrom).toVector().multiply(u));
        double low = diveMid.getY() - 1.4;
        p.setY(low + 8.4 * Math.pow(2 * u - 1, 2));
        vel.copy(p.toVector().subtract(pos.toVector()));
        pos = p;
        turnTo(diveTo, 20);

        wingL1.pose(0, -0.5f, -0.25f);
        wingR1.pose(0, 0.5f, 0.25f);
        wingL2.pose(0, -0.4f, -0.1f);
        wingR2.pose(0, 0.4f, 0.1f);
        body.offset.set(0, 0, 0);
        neck1.pose(0.05f, 0, 0);
        head.pose(0.1f, 0, 0);
        jaw.pose(0.35f, 0, 0);
        tailSway(0.05f);
        pos.getWorld().spawnParticle(Particle.CLOUD, rig.worldPoint(tail3, 0, 3.0f, -6.5f), 2, 0.2, 0.2, 0.2, 0.01);
        if (diveT == 19) sound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 0.6f);
        Location bodyC = rig.worldPoint(body, 0, 3.0f, 0.5f), headC = rig.worldPoint(head, 0, 3.9f, 6.2f);
        for (Player pl : playersNear(40)) {
            if (diveHit.contains(pl.getUniqueId())) continue;
            Location e = pl.getLocation().add(0, 0.9, 0);
            if (e.distanceSquared(bodyC) < 3.4 * 3.4 || e.distanceSquared(headC) < 2.2 * 2.2) {
                diveHit.add(pl.getUniqueId());
                hurt(pl, phase2 ? 12 : 10, diveDir.clone().multiply(0.9).setY(0.5));
                sound(e, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
            }
        }
        if (diveT >= 38) {
            diveT = -1;
            backToCircle(45);
        }
    }

    private void breath() {
        if (target == null || !target.isValid()) {
            backToCircle(20);
            return;
        }
        if (st == 1) {
            Vector away = pos.toVector().subtract(target.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 0.01) away = forward().multiply(-1);
            hoverAt = groundY(target.getLocation()).add(away.normalize().multiply(10)).add(0, FLY_H, 0);
        }
        flyTo(hoverAt, 0.5, 0.1, false);
        turnTo(target.getLocation(), 8);
        flyPose(0.9, 0.22);
        int shots = phase2 ? 5 : 3;
        int last = 25 + (shots - 1) * 12;
        boolean open = st >= 20 && st <= last + 4;
        neck1.pose(open ? -0.05f : -0.3f, 0, 0);
        head.pose(open ? 0.25f : 0.1f, 0, 0);
        jaw.pose(open ? 0.55f : 0, 0, 0);
        if (open && st % 3 == 0) {
            pos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, rig.worldPoint(head, 0, 3.7f, 7.4f), 3, 0.15, 0.15, 0.15, 0.05);
        }
        if (st >= 25 && st <= last && (st - 25) % 12 == 0) {
            Location mouth = rig.worldPoint(head, 0, 3.7f, 7.5f);
            Location aim = target.getEyeLocation().subtract(0, 0.5, 0).add(target.getVelocity().clone().setY(0).multiply(8));
            Vector v = aim.toVector().subtract(mouth.toVector()).normalize().multiply(0.8);
            orbs.add(new StormOrb(mouth, v));
            sound(mouth, Sound.ENTITY_BREEZE_SHOOT, 1.8f, 0.6f);
            sound(mouth, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.2f, 1.3f);
        }
        if (st >= last + 25) backToCircle(40);
    }

    private class StormOrb {
        final ItemDisplay d;
        final Location p;
        final Vector v;
        int life;

        StormOrb(Location from, Vector v) {
            this.p = from.clone();
            this.p.setYaw(0);
            this.p.setPitch(0);
            this.v = v;
            this.d = from.getWorld().spawn(this.p, ItemDisplay.class, e -> {
                plugin.tag(e);
                e.setItemStack(new ItemStack(Material.HEART_OF_THE_SEA));
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setGlowing(true);
                e.setGlowColorOverride(CYAN);
                e.setTeleportDuration(2);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.3f), new Quaternionf()));
            });
        }

        boolean tick() {
            if (dead || !d.isValid()) {
                d.remove();
                return true;
            }
            life++;
            p.add(v);
            if (life % 2 == 0) d.teleport(p);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, 2, 0.15, 0.15, 0.15, 0.02);
            p.getWorld().spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(CYAN, 1.2f));
            boolean hit = !p.getBlock().isPassable() || life > 55;
            for (Player pl : playersNear(64)) if (pl.getLocation().add(0, 1, 0).distanceSquared(p) < 1.5 * 1.5) hit = true;
            if (!hit) return false;
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, 30, 0.6, 0.6, 0.6, 0.25);
            Fx.plain(p, Particle.FLASH, 1, 0, 0);
            sound(p, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 1.3f);
            for (Player pl : playersNear(64)) {
                Location l = pl.getLocation().add(0, 1, 0);
                if (l.distanceSquared(p) < 2.4 * 2.4) {
                    hurt(pl, phase2 ? 7 : 6, v.clone().normalize().multiply(0.4).setY(0.3));
                }
            }
            d.remove();
            return true;
        }
    }

    private void land() {
        if (st == 1) {
            landAt = groundY(home);
            landed = false;
            actionT = 0;
            actionKind = 0;
            sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.9f);
        }
        int stay = phase2 ? 170 : 150;
        if (!landed) {
            flyTo(landAt, 0.45, 0.12, false);
            if (target != null) turnTo(target.getLocation(), 6);
            flyPose(0.95, 0.24);
            float k = (float) Math.min(1, st / 40.0);
            for (Rig.Bone l : new Rig.Bone[]{legFL, legFR, legBL, legBR}) l.pose(0.95f * (1 - k), 0, 0);
            if (pos.distanceSquared(landAt) < 0.5 || st > 90) {
                pos = landAt.clone();
                vel.zero();
                landed = true;
                st = 1;
                sound(pos, Sound.ENTITY_RAVAGER_STEP, 2f, 0.5f);
                sound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                org.bukkit.block.data.BlockData under = pos.clone().add(0, -0.5, 0).getBlock().getBlockData();
                if (!under.getMaterial().isAir()) pos.getWorld().spawnParticle(Particle.BLOCK, pos, 50, 2.5, 0.2, 2.5, 0.2, under);
            }
            return;
        }
        groundPose();
        target = nearestPlayer(30);
        if (st >= stay && actionKind == 0) {
            takeOff();
            return;
        }
        if (actionKind == 0) {
            if (target != null) turnTo(target.getLocation(), 5);
            if (++actionT >= 20 && target != null) {
                Location headP = rig.worldPoint(head, 0, 3.9f, 6.2f);
                actionKind = target.getLocation().distanceSquared(headP) < 5 * 5 ? 1 : 2;
                actionT = 0;
            }
            return;
        }
        actionT++;
        if (actionKind == 1) bite();
        else gust();
    }

    private void bite() {
        if (actionT <= 10) {
            float k = actionT / 10f;
            neck1.pose(-0.35f - 0.35f * k, 0, 0);
            head.pose(0.25f - 0.3f * k, 0, 0);
            jaw.pose(0.6f * k, 0, 0);
            if (target != null) turnTo(target.getLocation(), 8);
            if (actionT == 3) sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.4f);
        } else if (actionT <= 16) {
            neck1.pose(0.35f, 0, 0);
            neck2.pose(0.25f, 0, 0);
            head.pose(0.1f, 0, 0);
            jaw.pose(actionT < 13 ? 0.6f : 0, 0, 0);
            if (actionT == 13) {
                Location headP = rig.worldPoint(head, 0, 3.8f, 6.5f);
                sound(headP, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.5f, 0.6f);
                for (Player p : playersNear(30)) {
                    if (p.getLocation().add(0, 1, 0).distanceSquared(headP) < 3.2 * 3.2) {
                        hurt(p, phase2 ? 11 : 9, forward().multiply(0.6).setY(0.3));
                    }
                }
            }
        }
        if (actionT >= 26) {
            actionKind = 0;
            actionT = phase2 ? 8 : 0;
        }
    }

    private void gust() {
        double radius = 7.5;
        if (actionT <= 24) {
            float k = Math.min(1, actionT / 18f);
            wingL1.pose(0, -0.2f * (1 - k), -1.15f * k);
            wingR1.pose(0, 0.2f * (1 - k), 1.15f * k);
            wingL2.pose(0, 0, -0.35f * k);
            wingR2.pose(0, 0, 0.35f * k);
            neck1.pose(-0.6f * k, 0, 0);
            head.pose(0.4f * k, 0, 0);
            if (actionT % 4 == 0) {
                ring(pos, radius, CYAN, 1.5f);
                ring(pos, radius * 0.55, PALE, 1.1f);
            }
            if (actionT == 2) sound(pos, Sound.ENTITY_BREEZE_INHALE, 2f, 0.5f);
        } else if (actionT <= 32) {
            wingL1.pose(0, 0, 0.55f);
            wingR1.pose(0, 0, -0.55f);
            wingL2.pose(0, 0, 0.3f);
            wingR2.pose(0, 0, -0.3f);
            if (actionT == 26) {
                sound(pos, Sound.ENTITY_BREEZE_WIND_BURST, 2.5f, 0.5f);
                sound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 0.5f);
                pos.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, pos.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    pos.getWorld().spawnParticle(Particle.CLOUD, pos.clone().add(Math.cos(a) * 2, 0.4, Math.sin(a) * 2), 0,
                            Math.cos(a), 0.05, Math.sin(a), 0.6);
                }
                for (Player p : playersNear(radius + 2)) {
                    Vector out = p.getLocation().toVector().subtract(pos.toVector()).setY(0);
                    if (out.length() > radius) continue;
                    if (out.lengthSquared() < 0.01) out = forward();
                    hurt(p, 4, out.normalize().multiply(1.1).setY(0.45));
                }
            }
        }
        if (actionT >= 42) {
            actionKind = 0;
            actionT = phase2 ? 8 : 0;
        }
    }

    private void takeOff() {
        landed = false;
        vel.setX(0).setY(0.35).setZ(0);
        pos.add(0, 0.2, 0);
        sound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.5f, 0.6f);
        attacksSinceLand = 0;
        backToCircle(40);
    }

    private static final int STORM_TICKS = 200;

    private void storm() {
        landed = false;
        Location hover = home.clone().add(0, FLY_H + 6, 0);
        if (st == 1) {
            int n = Math.max(1, playersNear(40).size());
            int count = n >= 4 ? 4 : 3;
            double base = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            for (int i = 0; i < count; i++) {
                double a = base + Math.PI * 2 * i / count;
                Location at = null;
                for (double r : new double[]{8, 6, 4}) {
                    at = groundBelow(home.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 6, 12);
                    if (at != null) break;
                }
                if (at == null) at = groundY(home);
                crystals.add(new Crystal(at.add(0, 2.0, 0), 20 + 10 * n));
            }
            titleNear("<#5ce1ff><b>⚡ BURZA SIĘ ZBIERA</b>", "<white>Rozbijcie kryształy burzy!", 56);
            sound(home, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 0.6f);
            sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.7f);
        }
        flyTo(hover, 0.5, 0.08, false);
        if (target != null) turnTo(target.getLocation(), 3);
        flyPose(1.0, 0.24);
        neck1.pose(-0.7f, 0, 0);
        head.pose(-0.2f, 0, 0);
        jaw.pose(0.5f, 0, 0);
        target = nearestPlayer(48);
        if (st % (phase2 ? 16 : 22) == 0) {
            List<Player> near = playersNear(40);
            if (!near.isEmpty()) {
                Player p = near.get(ThreadLocalRandom.current().nextInt(near.size()));
                Location at = groundBelow(p.getLocation(), 2, 10);
                if (at != null) strikes.add(new Strike(at, 25, 2.2, 6));
            }
        }
        int alive = 0;
        for (Crystal c : crystals) if (c.alive()) alive++;
        if (st % 4 == 0) {
            StringBuilder s = new StringBuilder("<#5ce1ff>⚡ Kryształy: ");
            for (Crystal c : crystals) s.append(c.alive() ? "<#5ce1ff>◆" : "<dark_gray>◇");
            s.append(" <gray>| burza za <white>").append(Math.max(0, (STORM_TICKS - st) / 20)).append(" s");
            var msg = MM.deserialize(s.toString());
            for (Player p : playersNear(56)) plugin.messages().actionBar(p, SunBossy.MSG_ACTION, msg);
        }
        if (alive == 0) {
            clearCrystals();
            titleNear("<#7ee081><b>SMOK SPADA!</b>", "<white>Kryształy rozbite — bijcie go teraz!", 56);
            sound(pos, Sound.ENTITY_ENDER_DRAGON_HURT, 3f, 0.5f);
            crashing = true;
            landAt = groundY(home);
            go(State.STUN);
            return;
        }
        if (st >= STORM_TICKS) stormBlast();
    }

    private void stormBlast() {
        clearCrystals();
        titleNear("<#b07cff><b>BURZA UDERZA</b>", "<gray>Następnym razem rozbijcie kryształy…", 56);
        sound(pos, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.5f);
        for (Player p : playersNear(44)) {
            p.getWorld().strikeLightningEffect(p.getLocation());
            hurt(p, 13, new Vector(0, 0.4, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false, true));
        }
        backToCircle(40);
    }

    private class Crystal {
        final Location at;
        final BlockDisplay d;
        final ArmorStand box;
        double hpLeft;
        int spin;

        Crystal(Location at, double hp) {
            this.at = at;
            this.hpLeft = hp;
            Location l = at.clone();
            l.setYaw(0);
            l.setPitch(0);
            BlockData data = Material.SEA_LANTERN.createBlockData();
            d = l.getWorld().spawn(l, BlockDisplay.class, e -> {
                plugin.tag(e);
                e.setBlock(data);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setGlowing(true);
                e.setGlowColorOverride(CYAN);
            });
            box = addHurtbox(at.clone().subtract(0, 1.0, 0), 1.0f);
            at.getWorld().strikeLightningEffect(at.clone().subtract(0, 2, 0));
        }

        boolean alive() {
            return hpLeft > 0;
        }

        void tick() {
            if (!alive() || !d.isValid()) return;
            spin++;
            if (spin % 2 == 0) {
                Quaternionf q = new Quaternionf().rotateY(spin * 0.12f).rotateX(0.6f).rotateZ(0.6f);
                float s = 0.9f + (float) Math.sin(spin * 0.25) * 0.06f;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setTransformation(new Transformation(q.transform(new Vector3f(-s / 2)), q, new Vector3f(s), new Quaternionf()));
            }
            if (spin % 3 == 0) {
                Location from = at.clone();
                Location to = rig.worldPoint(body, 0, 3.0f, 0);
                Vector dir = to.toVector().subtract(from.toVector());
                double len = dir.length();
                dir.normalize();
                for (double k = 0; k < len; k += 0.8) {
                    from.getWorld().spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(k)), 1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(spin % 6 == 0 ? CYAN : PALE, 0.9f));
                }
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 3, 0.4, 0.4, 0.4, 0.05);
            }
        }

        void damage(double amount) {
            hpLeft -= amount;
            at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 8, 0.3, 0.3, 0.3, 0.2);
            if (hpLeft > 0) return;
            sound(at, Sound.BLOCK_GLASS_BREAK, 2f, 0.7f);
            sound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 2f, 0.6f);
            at.getWorld().spawnParticle(Particle.END_ROD, at, 30, 0.2, 0.2, 0.2, 0.2);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 5; i++) {
                Debris.spawn(plugin, at, Material.SEA_LANTERN, 0.3f, new Vector(r.nextDouble(-0.4, 0.4), r.nextDouble(0.2, 0.5), r.nextDouble(-0.4, 0.4)), 30);
            }
            remove();
        }

        void remove() {
            d.remove();
            plugin.unregisterHurtbox(box);
            hurtboxes.remove(box);
            box.remove();
        }
    }

    private void clearCrystals() {
        for (Crystal c : crystals) if (c.alive()) c.remove();
        crystals.clear();
    }

    private Crystal crystalOf(ArmorStand box) {
        for (Crystal c : crystals) if (c.box == box && c.alive()) return c;
        return null;
    }

    private void stun() {
        if (crashing) {
            vel.setX(vel.getX() * 0.9).setZ(vel.getZ() * 0.9).setY(Math.max(-1.2, vel.getY() - 0.06));
            Location next = pos.clone().add(vel);
            yawDelta = 8;
            rig.yaw += 7;
            wingL1.pose(0, 0, (float) Math.sin(st * 0.8) * 0.6f);
            wingR1.pose(0, 0, (float) -Math.sin(st * 0.8) * 0.6f);
            if (next.getY() <= landAt.getY() || st > 80) {
                pos = landAt.clone();
                vel.zero();
                crashing = false;
                landed = true;
                st = 1;
                sound(pos, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.6f);
                sound(pos, Sound.ENTITY_ENDER_DRAGON_HURT, 2.5f, 0.6f);
                pos.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, pos, 1, 0, 0, 0, 0);
                org.bukkit.block.data.BlockData under = pos.clone().add(0, -0.5, 0).getBlock().getBlockData();
                if (!under.getMaterial().isAir()) pos.getWorld().spawnParticle(Particle.BLOCK, pos, 80, 3, 0.3, 3, 0.3, under);
                for (Player p : playersNear(5)) hurt(p, 5, p.getLocation().toVector().subtract(pos.toVector()).setY(0).normalize().multiply(0.6).setY(0.4));
            } else {
                pos = next;
            }
            return;
        }
        groundPose();
        body.offset.set(0, -1.15f, 0);
        neck1.pose(0.45f, 0, 0.15f);
        neck2.pose(0.25f, 0, 0);
        head.pose(0.3f, 0, 0.2f);
        jaw.pose(0.25f, 0, 0);
        wingL1.pose(0, -0.4f, 0.55f);
        wingR1.pose(0, 0.4f, -0.55f);
        if (st % 15 == 0) pos.getWorld().spawnParticle(Particle.CRIT, rig.worldPoint(head, 0, 4.6f, 5.8f), 10, 0.4, 0.1, 0.4, 0.1);
        if (st % 20 == 0) pos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, rig.worldPoint(body, 0, 3.5f, 0), 12, 1, 0.5, 1.5, 0.1);
        if (st >= 130) {
            sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5f, 0.8f);
            takeOff();
        }
    }

    private void roar() {
        invulnerable = true;
        landed = false;
        Location hover = home.clone().add(0, FLY_H + 3, 0);
        flyTo(hover, 0.4, 0.1, false);
        flyPose(0.3, 0.1);
        float k = Math.min(1, st / 12f);
        wingL1.pose(0, 0, -0.6f * k);
        wingR1.pose(0, 0, 0.6f * k);
        neck1.pose(-0.8f * k, 0, 0);
        head.pose(-0.3f * k, 0, 0);
        jaw.pose(0.7f * k, 0, 0);
        if (st == 1) {
            titleNear("<#b07cff><b>FAZA 2</b>", "<white>Nad areną zbiera się burza!", 64);
            sound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.5f);
        }
        if (st == 16) {
            sound(pos, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.5f);
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                home.getWorld().strikeLightningEffect(groundY(home.clone().add(Math.cos(a) * 11, 0, Math.sin(a) * 11)));
            }
        }
        if (st == 30) {
            phase2 = true;
            bar.color(BossBar.Color.PURPLE);
            bar.name(MM.deserialize("<#b07cff><b>⚡ Smok Burzy</b></#b07cff> <gray>— szaleje"));
            BlockData violet = Material.PEARLESCENT_FROGLIGHT.createBlockData();
            for (Rig.Part p : glowing) if (p.d != null) p.d.setBlock(violet);
            lastStormT = t;
            nextAmbientStrike = t + 60;
        }
        if (st >= 50) {
            invulnerable = false;
            backToCircle(20);
        }
    }

    private void stormWeather() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                Location c = home.clone().add(r.nextDouble(-16, 16), FLY_H + 11 + r.nextDouble(-1, 1), r.nextDouble(-16, 16));
                c.getWorld().spawnParticle(Particle.LARGE_SMOKE, c, 2, 1.5, 0.4, 1.5, 0.005);
            }
        }
        if (t >= nextAmbientStrike && state != State.ROAR) {
            nextAmbientStrike = t + 70 + r.nextInt(40);
            List<Player> near = playersNear(40);
            if (!near.isEmpty()) {
                Player p = near.get(r.nextInt(near.size()));
                Location at = groundBelow(p.getLocation().add(r.nextDouble(-2, 2), 0, r.nextDouble(-2, 2)), 2, 10);
                if (at != null) strikes.add(new Strike(at, 30, 2.2, 5));
            }
        }
    }

    @Override
    protected double damageMultiplier(ArmorStand hitbox) {
        if (crystalOf(hitbox) != null) return 0.5;
        if (state == State.STORM) return 0.3;
        if (state == State.STUN && !crashing) return 1.6;
        return 1.0;
    }

    @Override
    protected void onDamaged(Player p, double amount, ArmorStand hitbox) {
        Crystal c = crystalOf(hitbox);
        if (c != null) {
            c.damage(amount / 0.5);
            p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.4f);
        }
    }

    @Override
    protected void hurtSound() {
        sound(pos, Sound.ENTITY_ENDER_DRAGON_HURT, 1.2f, 1.2f);
    }

    @Override
    protected void onDeath() {
        clearCrystals();
        orbs.forEach(o -> o.d.remove());
        orbs.clear();
        strikes.clear();
        landAt = groundY(landed ? pos : home);
        go(State.DEATH);
        titleNear("<#5ce1ff><b>SMOK BURZY SPADA</b>", "<gray>…", 64);
    }

    private void death() {
        Location c = pos;
        if (st == 1) {
            sound(c, Sound.ENTITY_ENDER_DRAGON_HURT, 3f, 0.4f);
            sound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.5f);
        }
        if (st < 50) {
            double f = st / 50.0;
            double a = st * 0.25;
            double r = 5 * (1 - f);
            Location goal = landAt.clone().add(Math.cos(a) * r, (1 - f) * Math.max(0, pos.getY() - landAt.getY()), Math.sin(a) * r);
            vel.copy(goal.toVector().subtract(pos.toVector()));
            pos = goal;
            turnTo(pos.clone().add(-Math.sin(a), 0, Math.cos(a)), 25);
            wingL1.pose(0, 0, (float) Math.sin(st * 0.9) * 0.9f);
            wingR1.pose(0, 0, (float) -Math.sin(st * 0.9) * 0.9f);
            neck1.pose(0.3f, 0, 0);
            if (st % 5 == 0) {
                c.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, rig.worldPoint(body, 0, 3, 0), 15, 1, 1, 1, 0.2);
                c.getWorld().spawnParticle(Particle.LARGE_SMOKE, rig.worldPoint(body, 0, 3, -1), 4, 0.5, 0.5, 0.5, 0.02);
            }
            if (st % 12 == 0) sound(c, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.4f);
        }
        if (st == 50) {
            pos = landAt.clone();
            landed = true;
            groundPose();
            body.offset.set(0, -1.2f, 0);
            neck1.pose(0.5f, 0, 0.2f);
            head.pose(0.3f, 0, 0.3f);
            sound(pos, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
            sound(pos, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.2f, 1.4f);
            pos.getWorld().strikeLightningEffect(pos);
            pos.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, pos, 1, 0, 0, 0, 0);
            org.bukkit.block.data.BlockData under = pos.clone().add(0, -0.5, 0).getBlock().getBlockData();
            if (!under.getMaterial().isAir()) pos.getWorld().spawnParticle(Particle.BLOCK, pos, 100, 3, 0.3, 3, 0.3, under);
        }
        if (st > 50 && st < 72 && st % 4 == 0) {
            pos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, rig.worldPoint(body, 0, 2.5f, 0), 20, 1.5, 0.8, 2.5, 0.2);
        }
        if (st == 72) {
            Location mid = pos.clone().add(0, 2, 0);
            sound(mid, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.7f);
            sound(mid, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1.2f);
            mid.getWorld().spawnParticle(Particle.FIREWORK, mid, 150, 0.4, 0.4, 0.4, 0.35);
            mid.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, mid, 200, 1, 1, 1, 0.6);
            mid.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mid, 120, 0.4, 0.8, 0.4, 0.6);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Rig.Part p : new ArrayList<>(rig.parts())) {
                BlockDisplay d = rig.detach(p);
                if (d == null) continue;
                Debris.launch(plugin, d, new Vector(r.nextDouble(-0.45, 0.45), r.nextDouble(0.2, 0.75), r.nextDouble(-0.45, 0.45)), 60 + r.nextInt(40));
            }
            for (Player p : playersNear(64)) p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (st >= 80) finishDeath();
    }

    @Override
    public void adminDamage(double amount) {
        if (state == State.STORM && !crystals.isEmpty()) {
            for (Crystal c : new ArrayList<>(crystals)) if (c.alive()) c.damage(amount);
            return;
        }
        super.adminDamage(amount);
    }

    @Override
    public String debug() {
        return super.debug() + " stan=" + state + (landed ? " (na ziemi)" : "") + " krysztaly=" + crystals.stream().filter(Crystal::alive).count() + " faza2=" + phase2;
    }

    @Override
    protected void cleanup() {
        clearCrystals();
        orbs.forEach(o -> o.d.remove());
        orbs.clear();
        strikes.clear();
    }
}

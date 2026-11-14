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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class VoidKraken extends Boss {
    private enum State { RISE, LURK, INK, EXPOSE, PULL, ROAR, DEATH }

    private enum TState { GONE, EMERGE, IDLE, SLAM_WINDUP, SLAM, GRAB_WINDUP, HOLD, RETREAT }

    private static final Color VOID = Color.fromRGB(0x9B5CFF);
    private static final Color INK = Color.fromRGB(0x2A1640);
    private static final Color EYE = Color.fromRGB(0xFFD24A);
    private static final float LURK_Y = -3.2f;
    private static final float SEG = 1.25f;

    private final Rig.Bone root;
    private final Rig.Bone[] mouth = new Rig.Bone[6];
    private final List<Rig.Part> eyes = new ArrayList<>();

    private State state = State.RISE;
    private int st;
    private int cooldown = 60;
    private int sinceExpose;
    private boolean phase2;
    private Player target;

    private Vector facing;
    private Location headPos, arenaC;
    private double advance;
    private float lift = -16;

    private ArmorStand headBox, eyeBoxL, eyeBoxR;
    private final List<Tentacle> tentacles = new ArrayList<>();
    private final List<InkCloud> clouds = new ArrayList<>();
    private InkBlob blob;
    private int grabCooldown;

    public VoidKraken(SunBossy plugin, Location home) {
        super(plugin, "kraken", home, BossBar.bossBar(MM.deserialize("<#b07cff><b>☠ Kraken Pustki</b>"), 1f,
                BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10));
        Material skin = Material.BLUE_TERRACOTTA, dark = Material.BLACK_CONCRETE, patch = Material.SCULK,
                eye = Material.OCHRE_FROGLIGHT, sucker = Material.MAGENTA_TERRACOTTA, fin = Material.PURPLE_TERRACOTTA,
                crystal = Material.AMETHYST_BLOCK;

        root = rig.bone(null, 0, 3.0f, 0);

        rig.part(root, skin, 0, 2.6f, 0.3f, 4.4f, 3.4f, 3.8f);
        rig.part(root, skin, 0, 1.3f, 0.6f, 3.6f, 1.0f, 3.2f);

        rig.part(root, skin, 0, 5.3f, -0.9f, 4.0f, 3.0f, 3.8f, new Quaternionf().rotateX(-0.2f));
        rig.part(root, skin, 0, 7.1f, -2.1f, 3.3f, 2.6f, 3.3f, new Quaternionf().rotateX(-0.42f));
        rig.part(root, skin, 0, 8.6f, -3.5f, 2.4f, 2.0f, 2.6f, new Quaternionf().rotateX(-0.62f));
        rig.part(root, skin, 0, 9.6f, -4.8f, 1.4f, 1.3f, 1.8f, new Quaternionf().rotateX(-0.85f));

        rig.part(root, patch, 0, 6.35f, 0.72f, 2.2f, 1.2f, 0.06f, new Quaternionf().rotateX(-0.2f));
        rig.part(root, patch, 0, 8.25f, -1.55f, 1.6f, 0.9f, 0.06f, new Quaternionf().rotateX(-0.62f));
        for (int s = -1; s <= 1; s += 2) {
            rig.part(root, patch, 2.02f * s, 5.2f, -1.0f, 0.06f, 1.5f, 1.8f, new Quaternionf().rotateX(-0.2f));
            rig.part(root, patch, 2.22f * s, 2.2f, -0.6f, 0.06f, 1.2f, 1.5f);
            rig.part(root, fin, 2.0f * s, 7.2f, -2.4f, 0.12f, 2.3f, 2.8f, new Quaternionf().rotateX(-0.42f).rotateZ(0.55f * s));
            rig.part(root, sucker, 2.55f * s, 6.4f, -1.5f, 0.1f, 0.35f, 1.6f, new Quaternionf().rotateX(-0.42f).rotateZ(0.55f * s));

            eyes.add(rig.part(root, eye, 1.4f * s, 3.0f, 2.22f, 1.1f, 1.3f, 0.12f));
            rig.part(root, dark, 1.4f * s, 3.0f, 2.3f, 0.24f, 1.1f, 0.05f);
            rig.part(root, skin, 1.45f * s, 3.85f, 2.3f, 1.45f, 0.32f, 0.5f, new Quaternionf().rotateZ(-0.28f * s));
            rig.part(root, patch, 1.4f * s, 2.25f, 2.23f, 1.2f, 0.18f, 0.08f);
        }

        float[][] cr = {{-0.7f, 7.9f, -1.0f, 0.55f}, {0.5f, 8.3f, -1.6f, 0.45f}, {0.9f, 6.9f, 0.1f, 0.4f},
                {-1.1f, 9.3f, -3.2f, 0.4f}, {0.2f, 10.2f, -4.3f, 0.35f}, {-1.6f, 5.9f, 0.4f, 0.35f}};
        for (float[] c : cr) {
            rig.part(root, crystal, c[0], c[1], c[2], c[3], c[3] * 1.6f, c[3],
                    new Quaternionf().rotateX(-0.5f + c[0] * 0.2f).rotateZ(c[0] * 0.35f));
        }
        rig.part(root, Material.OBSIDIAN, 0, 1.2f, 2.22f, 1.4f, 0.55f, 0.2f);
        float[][] mp = {{-1.6f, 1.2f}, {-0.6f, 1.7f}, {0.6f, 1.7f}, {1.6f, 1.2f}, {-1.7f, -0.8f}, {1.7f, -0.8f}};
        for (int i = 0; i < mouth.length; i++) {
            float x = mp[i][0], z = mp[i][1];
            mouth[i] = rig.bone(root, x, 1.0f, z);
            rig.part(mouth[i], skin, x, 0.1f, z, 0.6f, 1.9f, 0.6f);
            rig.part(mouth[i], sucker, x, 0.1f, z + 0.31f, 0.3f, 1.4f, 0.04f);
            rig.part(mouth[i], sucker, x, -1.05f, z, 0.4f, 0.6f, 0.4f);
        }
    }

    @Override
    public String displayName() {
        return "<#b07cff><b>☠ Kraken Pustki</b></#b07cff>";
    }

    @Override
    public void spawnAt(float yaw) {
        rig.yaw = yaw;
        double y = Math.toRadians(yaw);
        facing = new Vector(-Math.sin(y), 0, Math.cos(y));
        headPos = home.clone();
        headPos.setYaw(0);
        headPos.setPitch(0);
        Location c = home.clone().add(facing.clone().multiply(10));
        arenaC = groundBelow(c, 6, 14);
        if (arenaC == null) arenaC = c;
        root.offset.set(0, lift, 0);
        rig.spawn(headPos);
        for (Rig.Part p : eyes) if (p.d != null) p.d.setBrightness(new Display.Brightness(15, 15));
        headBox = addHurtbox(home.clone().add(0, -30, 0), 3.0f);
        eyeBoxL = addHurtbox(home.clone().add(0, -30, 0), 1.0f);
        eyeBoxR = addHurtbox(home.clone().add(0, -30, 0), 1.0f);
    }

    @Override
    protected void onTick() {
        st++;
        if (grabCooldown > 0) grabCooldown--;
        switch (state) {
            case RISE -> rise();
            case LURK -> lurk();
            case INK -> ink();
            case EXPOSE -> expose();
            case PULL -> pull();
            case ROAR -> roar();
            case DEATH -> death();
        }
        for (Tentacle tn : tentacles) tn.tick();
        clouds.removeIf(InkCloud::tick);
        if (blob != null && blob.tick()) blob = null;
        mouthSway(state == State.PULL ? 0.5f : 0.18f);
        root.offset.y = lift;
        if (t % 2 == 0) {
            rig.moveTo(headPos, 2);
            rig.apply(2);
        }
        if (state != State.DEATH) placeHurtboxes();
    }

    private void go(State s) {
        state = s;
        st = 0;
    }

    private int cd(int ticks) {
        return phase2 ? (int) (ticks * 0.75) : ticks;
    }

    private void placeHurtboxes() {
        box(headBox, rig.worldPoint(root, 0, 4.2f, 0.3f), 3.0f);
        box(eyeBoxL, rig.worldPoint(root, -1.4f, 3.2f, 2.3f), 1.0f);
        box(eyeBoxR, rig.worldPoint(root, 1.4f, 3.2f, 2.3f), 1.0f);
    }

    private static void box(ArmorStand s, Location center, float scale) {
        if (s != null) s.teleport(center.subtract(0, 1.975 * scale / 2, 0));
    }

    private void mouthSway(float amp) {
        for (int i = 0; i < mouth.length; i++) {
            float a = (float) Math.sin(t * 0.09 + i * 1.1) * amp;
            float b = (float) Math.cos(t * 0.07 + i * 0.8) * amp;
            mouth[i].pose(a + (state == State.PULL ? -0.5f : 0), 0, b);
        }
    }

    private void headTrack() {
        if (target == null) {
            root.rot.identity();
            return;
        }
        Vector d = target.getLocation().toVector().subtract(headPos.toVector()).setY(0);
        Vector3f local = new Quaternionf().rotateY((float) Math.toRadians(rig.yaw))
                .transform(new Vector3f((float) d.getX(), 0, (float) d.getZ()));
        float yaw = (float) Math.atan2(local.x, local.z);
        root.rot.identity().rotateY(Math.max(-0.5f, Math.min(0.5f, yaw)));
    }

    private void setAdvance(double a) {
        advance = a;
        headPos = home.clone().add(facing.clone().multiply(a));
        headPos.setYaw(0);
        headPos.setPitch(0);
    }

    private void rise() {
        invulnerable = true;
        if (st == 1) {
            titleNear("<#b07cff><b>KRAKEN PUSTKI</b>", "<gray>wynurza się z otchłani…", 72);
            sound(home, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 3f, 0.5f);
            sound(home, Sound.AMBIENT_UNDERWATER_ENTER, 3f, 0.5f);
        }
        float k = Math.min(1, st / 80f);
        lift = -16 + (16 + LURK_Y) * (float) (1 - Math.pow(1 - k, 3));
        if (st % 3 == 0) {
            home.getWorld().spawnParticle(Particle.REVERSE_PORTAL, headPos.clone().add(0, lift + 3, 0), 20, 2.5, 2, 2.5, 0.05);
            home.getWorld().spawnParticle(Particle.SQUID_INK, headPos.clone().add(0, lift + 1, 0), 6, 2, 1, 2, 0.02);
        }
        if (st % 20 == 0) sound(home, Sound.BLOCK_SCULK_SPREAD, 2f, 0.5f);
        if (st == 80) {
            sound(home, Sound.ENTITY_WARDEN_ROAR, 3f, 0.6f);
            sound(home, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 3f, 0.5f);
            for (int i = 0; i < 3; i++) {
                Tentacle tn = new Tentacle(i * 1.3f);
                tn.respawnIn = 5 + i * 18;
                tentacles.add(tn);
            }
        }
        if (st >= 100) {
            invulnerable = false;
            go(State.LURK);
        }
    }

    private void lurk() {
        if (!phase2 && hp / maxHp <= 0.5) {
            go(State.ROAR);
            return;
        }
        target = nearestPlayer(40);
        setAdvance(advance * 0.9);
        lift = LURK_Y + (float) Math.sin(t * 0.05) * 0.3f;
        headTrack();
        if (st % 40 == 0) home.getWorld().spawnParticle(Particle.BUBBLE_POP, headPos.clone().add(0, lift + 1, 0), 10, 2, 0.5, 2, 0.02);
        if (st % 100 == 0) sound(headPos, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.5f, 0.5f);
        if (target == null || --cooldown > 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (sinceExpose >= 2 || r.nextInt(100) < 25) {
            sinceExpose = 0;
            go(State.EXPOSE);
        } else if (phase2 && r.nextInt(100) < 35) {
            sinceExpose++;
            go(State.PULL);
        } else {
            sinceExpose++;
            go(State.INK);
        }
    }

    private void backToLurk(int cool) {
        cooldown = cd(cool);
        go(State.LURK);
    }

    private void ink() {
        headTrack();
        float k = Math.min(1, st / 15f);
        lift = LURK_Y + 1.0f * k;
        if (st == 16 && target != null && target.isValid()) {
            Location from = rig.worldPoint(root, 0, 1.0f, 2.3f);
            Location land = groundBelow(target.getLocation().add(target.getVelocity().clone().setY(0).multiply(10)), 3, 10);
            if (land == null) land = target.getLocation();
            blob = new InkBlob(from, land);
            sound(from, Sound.ENTITY_SQUID_SQUIRT, 2.5f, 0.5f);
            sound(from, Sound.ENTITY_GLOW_SQUID_SQUIRT, 2f, 0.6f);
        }
        if (st >= 40) backToLurk(60);
    }

    private class InkBlob {
        final BlockDisplay d;
        final Location from, to;
        final int flight;
        int k;

        InkBlob(Location from, Location to) {
            this.from = from;
            this.to = to;
            this.flight = (int) Math.max(14, Math.min(28, from.distance(to) * 1.1));
            Location l = from.clone();
            l.setYaw(0);
            l.setPitch(0);
            d = from.getWorld().spawn(l, BlockDisplay.class, e -> {
                plugin.tag(e);
                e.setBlock(Material.BLACK_CONCRETE.createBlockData());
                e.setTeleportDuration(2);
                e.setTransformation(new Transformation(new Vector3f(-0.4f), new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
            });
        }

        boolean tick() {
            k++;
            if (dead || !d.isValid()) {
                d.remove();
                return true;
            }
            if (k % 3 == 0) ring(to, 3.5, VOID, 1.3f);
            double f = (double) k / flight;
            Location p = from.clone().add(to.clone().subtract(from).toVector().multiply(f)).add(0, Math.sin(Math.PI * f) * 4, 0);
            if (k % 2 == 0) d.teleport(p);
            p.getWorld().spawnParticle(Particle.SQUID_INK, p, 2, 0.15, 0.15, 0.15, 0.01);
            if (k < flight) return false;
            d.remove();
            sound(to, Sound.ENTITY_SLIME_SQUISH, 2f, 0.5f);
            sound(to, Sound.ENTITY_SQUID_SQUIRT, 2f, 0.7f);
            to.getWorld().spawnParticle(Particle.SQUID_INK, to.clone().add(0, 0.5, 0), 60, 1.5, 0.4, 1.5, 0.05);
            clouds.add(new InkCloud(to));
            return true;
        }
    }

    private class InkCloud {
        final Location at;
        int life;

        InkCloud(Location at) {
            this.at = at;
        }

        boolean tick() {
            life++;
            if (dead || life > 110) return true;
            if (life % 3 == 0) {
                at.getWorld().spawnParticle(Particle.SQUID_INK, at.clone().add(0, 0.8, 0), 8, 1.8, 0.6, 1.8, 0.005);
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.4, 0), 6, 1.8, 0.3, 1.8, 0,
                        new Particle.DustOptions(INK, 2.2f));
            }
            if (life % 10 == 0) {
                for (Player p : playersNear(64)) {
                    Location l = p.getLocation();
                    double dx = l.getX() - at.getX(), dz = l.getZ() - at.getZ();
                    if (dx * dx + dz * dz > 3.5 * 3.5 || Math.abs(l.getY() - at.getY()) > 3) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, false, false, true));
                    if (life % 20 == 0) hurt(p, 2, null);
                }
            }
            return false;
        }
    }

    private void expose() {
        target = nearestPlayer(40);
        int open = phase2 ? 150 : 170;
        if (st == 1) {
            sound(headPos, Sound.ENTITY_WARDEN_ROAR, 2.5f, 0.7f);
            sound(headPos, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2f, 0.7f);
        }
        if (st <= 25) {
            float k = st / 25f;
            setAdvance(3.5 * k);
            lift = LURK_Y + (LURK_Y * -1 - 1.2f) * k;
            root.rot.identity().rotateX(0.3f * k);
            if (st == 25) {
                titleNear("<#ffd24a><b>OCZY ODSŁONIĘTE</b>", "<white>Bijcie w oczy, póki leży na krawędzi!", 48);
                org.bukkit.block.data.BlockData under = groundBelow(headPos.clone().add(facing.clone().multiply(2)), 4, 8) == null
                        ? Material.STONE.createBlockData() : Material.SCULK.createBlockData();
                headPos.getWorld().spawnParticle(Particle.BLOCK, headPos.clone().add(facing.clone().multiply(2.5)).add(0, 0.5, 0), 40, 2, 0.3, 1, 0.2, under);
                sound(headPos, Sound.ENTITY_RAVAGER_STEP, 2f, 0.5f);
            }
        } else if (st <= 25 + open) {
            float wobble = (float) Math.sin(st * 0.15) * 0.05f;
            root.rot.identity().rotateX(0.3f + wobble).rotateY((float) Math.sin(st * 0.05) * 0.15f);
            if (st % 10 == 0) {
                for (Rig.Part e : eyes) {
                    if (e.d != null) headPos.getWorld().spawnParticle(Particle.DUST, rig.worldPoint(root, e == eyes.get(0) ? -1.4f : 1.4f, 3.2f, 2.4f),
                            4, 0.3, 0.3, 0.1, 0, new Particle.DustOptions(EYE, 1.2f));
                }
            }
            if (st % 40 == 0) sound(headPos, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1.8f, 0.6f);
        } else {
            float k = Math.min(1, (st - 25 - open) / 20f);
            setAdvance(3.5 * (1 - k));
            lift = -1.2f + (LURK_Y + 1.2f) * k;
            root.rot.identity().rotateX(0.3f * (1 - k));
            if (k >= 1) backToLurk(50);
        }
    }

    private boolean exposed() {
        return state == State.EXPOSE && st > 20 && st < 25 + (phase2 ? 150 : 170) + 5;
    }

    private void pull() {
        target = nearestPlayer(40);
        headTrack();
        float k = Math.min(1, st / 15f);
        lift = LURK_Y + 1.8f * k;
        Location maw = rig.worldPoint(root, 0, 1.2f, 2.3f);
        if (st == 12) {
            titleNear("<#b07cff><b>KRAKEN WSYSA</b>", "<white>Uciekajcie od krawędzi!", 40);
            sound(headPos, Sound.ENTITY_BREEZE_INHALE, 3f, 0.4f);
            sound(headPos, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2f, 0.5f);
        }
        if (st > 15 && st <= 85) {
            for (Player p : playersNear(24)) {
                Vector dir = maw.toVector().subtract(p.getLocation().toVector()).setY(0);
                if (dir.lengthSquared() < 1) continue;
                dir.normalize();

                if (groundBelow(p.getLocation().add(dir.clone().multiply(1.5)), 2, 6) == null) continue;
                p.setVelocity(p.getVelocity().add(dir.multiply(0.075)));
            }
            if (st % 2 == 0) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 4; i++) {
                    Location from = maw.clone().add(r.nextDouble(-12, 12), r.nextDouble(-1, 3), r.nextDouble(-12, 12));
                    Vector v = maw.toVector().subtract(from.toVector()).multiply(0.08);
                    from.getWorld().spawnParticle(Particle.CLOUD, from, 0, v.getX(), v.getY(), v.getZ(), 1);
                }
            }
            if (st % 20 == 0) sound(headPos, Sound.ENTITY_BREEZE_INHALE, 2f, 0.5f);
        }
        if (st == 88) {
            sound(maw, Sound.ENTITY_EVOKER_FANGS_ATTACK, 2.5f, 0.5f);
            sound(maw, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 2.5f, 0.6f);
            maw.getWorld().spawnParticle(Particle.SQUID_INK, maw, 40, 1.5, 1, 1.5, 0.1);
            for (Player p : playersNear(30)) {
                if (p.getLocation().distanceSquared(maw) < 6 * 6) {
                    Vector back = p.getLocation().toVector().subtract(maw.toVector()).setY(0);
                    if (back.lengthSquared() < 0.01) back = facing.clone();
                    hurt(p, 12, back.normalize().multiply(0.9).setY(0.4));
                }
            }
        }
        if (st >= 100) backToLurk(60);
    }

    private void roar() {
        invulnerable = true;
        float k = Math.min(1, st / 20f);
        lift = LURK_Y + (LURK_Y * -1) * k;
        root.rot.identity().rotateX(-0.2f * k);
        if (st == 1) {
            titleNear("<#ff5a7a><b>FAZA 2</b>", "<white>Otchłań się budzi…", 64);
            sound(headPos, Sound.ENTITY_WARDEN_ROAR, 3f, 0.5f);
            sound(headPos, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 3f, 0.5f);
        }
        if (st % 3 == 0) headPos.getWorld().spawnParticle(Particle.REVERSE_PORTAL, headPos.clone().add(0, 4, 0), 30, 3, 3, 3, 0.1);
        if (st == 25) {
            phase2 = true;
            bar.color(BossBar.Color.RED);
            bar.name(MM.deserialize("<#ff5a7a><b>☠ Kraken Pustki</b></#ff5a7a> <gray>— wściekły"));
            BlockData pink = Material.PEARLESCENT_FROGLIGHT.createBlockData();
            for (Rig.Part e : eyes) if (e.d != null) e.d.setBlock(pink);
            Tentacle tn = new Tentacle(4.1f);
            tn.respawnIn = 10;
            tentacles.add(tn);
            for (Tentacle x : tentacles) if (x.state == TState.GONE) x.respawnIn = Math.min(x.respawnIn, 20);
        }
        if (st >= 50) {
            invulnerable = false;
            backToLurk(40);
        }
    }

    private class Tentacle {
        final Rig r = new Rig(plugin);
        final Rig.Bone[] seg = new Rig.Bone[7];
        final float phase;
        final ArmorStand boxLow, boxHigh;
        TState state = TState.GONE;
        int st, cool, respawnIn;
        double hpLeft;
        Location base;
        Vector dir;
        Player grabbed, aim;
        double freeDmg, freeNeed;

        Tentacle(float phase) {
            this.phase = phase;
            for (int i = 0; i < seg.length; i++) {
                seg[i] = r.bone(i == 0 ? null : seg[i - 1], 0, i * SEG, 0);
                float w = 1.25f - i * 0.13f;
                r.part(seg[i], Material.BLUE_TERRACOTTA, 0, i * SEG + SEG / 2, 0, w, SEG + 0.05f, w);
                r.part(seg[i], Material.MAGENTA_TERRACOTTA, 0, i * SEG + SEG / 2, w / 2 + 0.01f, w * 0.55f, SEG * 0.75f, 0.04f);
                if (i % 2 == 0) r.part(seg[i], Material.SCULK, 0, i * SEG + SEG / 2, -w / 2 - 0.01f, w * 0.6f, SEG * 0.6f, 0.04f);
            }
            r.part(seg[6], Material.AMETHYST_BLOCK, 0, 7 * SEG + 0.2f, 0, 0.32f, 0.45f, 0.32f, new Quaternionf().rotateY(0.785f));
            boxLow = addHurtbox(home.clone().add(0, -30, 0), 1.4f);
            boxHigh = addHurtbox(home.clone().add(0, -30, 0), 1.1f);
        }

        Location segPoint(int i) {
            return r.worldPoint(seg[i], 0, i * SEG + SEG / 2, 0);
        }

        Location tip() {
            return r.worldPoint(seg[6], 0, 7 * SEG, 0);
        }

        boolean up() {
            return state != TState.GONE && state != TState.EMERGE && state != TState.RETREAT;
        }

        void go(TState s) {
            state = s;
            st = 0;
        }

        void tick() {
            st++;
            switch (state) {
                case GONE -> {
                    if (--respawnIn <= 0 && VoidKraken.this.state != State.DEATH && VoidKraken.this.state != State.RISE) emerge();
                }
                case EMERGE -> emergeTick();
                case IDLE -> idle();
                case SLAM_WINDUP -> slamWindup(false);
                case GRAB_WINDUP -> slamWindup(true);
                case SLAM -> slam();
                case HOLD -> hold();
                case RETREAT -> retreat();
            }
            if (state != TState.GONE && r.isValid() && t % 2 == 0) r.apply(state == TState.SLAM && st <= 2 ? 1 : 2);
            if (up() || state == TState.EMERGE) {
                box(boxLow, segPoint(2), 1.4f);
                box(boxHigh, segPoint(5), 1.1f);
            } else if (base != null) {
                boxLow.teleport(base.clone().add(0, -30, 0));
                boxHigh.teleport(base.clone().add(0, -30, 0));
            }
        }

        private void emerge() {
            Location spot = pickSpot(this);
            if (spot == null) {
                respawnIn = 40;
                return;
            }
            base = spot;
            r.remove();
            r.yaw = ThreadLocalRandom.current().nextFloat() * 360;
            seg[0].offset.set(0, -10, 0);
            Location a = base.clone();
            a.setYaw(0);
            a.setPitch(0);
            r.spawn(a);
            int n = Math.max(1, playersNear(40).size());
            hpLeft = 35 + 12 * n;
            go(TState.EMERGE);
        }

        private void emergeTick() {
            if (st <= 20) {
                if (st % 2 == 0) {
                    base.getWorld().spawnParticle(Particle.REVERSE_PORTAL, base.clone().add(0, 0.2, 0), 12, 0.6, 0.1, 0.6, 0.05);
                    base.getWorld().spawnParticle(Particle.SQUID_INK, base.clone().add(0, 0.2, 0), 3, 0.5, 0.1, 0.5, 0.02);
                }
                if (st == 2) sound(base, Sound.BLOCK_SCULK_SPREAD, 1.5f, 0.6f);
                return;
            }
            float k = Math.min(1, (st - 20) / 18f);
            seg[0].offset.set(0, -10 * (1 - k), 0);
            sway(1f);
            if (st == 21) {
                sound(base, Sound.ENTITY_WARDEN_EMERGE, 1.5f, 1.2f);
                org.bukkit.block.data.BlockData under = base.clone().add(0, -0.5, 0).getBlock().getBlockData();
                if (!under.getMaterial().isAir()) base.getWorld().spawnParticle(Particle.BLOCK, base, 30, 0.6, 0.1, 0.6, 0.2, under);
                for (Player p : playersNear(40)) {
                    if (p.getLocation().distanceSquared(base) < 1.6 * 1.6) hurt(p, 4, new Vector(0, 0.6, 0));
                }
            }
            if (k >= 1) {
                cool = 30 + ThreadLocalRandom.current().nextInt(30);
                go(TState.IDLE);
            }
        }

        private void sway(float amp) {
            for (int i = 0; i < seg.length; i++) {
                float a = (float) Math.sin(t * 0.08 + i * 0.7 + phase) * 0.16f * amp + (i == 0 ? 0.05f : 0);
                float b = (float) Math.cos(t * 0.06 + i * 0.6 + phase) * 0.12f * amp;
                seg[i].pose(a, 0, b);
            }
        }

        private void idle() {
            seg[0].offset.set(0, 0, 0);
            sway(1f);
            if (VoidKraken.this.state == State.DEATH) return;
            int speed = exposed() ? 2 : 1;
            cool -= speed == 2 ? 0 : 1;
            if (speed == 2 && t % 2 == 0) cool--;
            if (cool > 0) return;
            Player tp = null;
            double best = 11 * 11;
            for (Player p : playersNear(64)) {
                double d = p.getLocation().distanceSquared(base);
                if (d < best && d > 1) {
                    best = d;
                    tp = p;
                }
            }
            if (tp == null) {
                cool = 20;
                return;
            }
            aim = tp;
            dir = tp.getLocation().toVector().subtract(base.toVector()).setY(0).normalize();
            r.yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            boolean grab = grabCooldown <= 0 && !anyHolding() && ThreadLocalRandom.current().nextInt(100) < 30 && best < 8 * 8;
            go(grab ? TState.GRAB_WINDUP : TState.SLAM_WINDUP);
        }

        private void slamWindup(boolean grab) {
            int wind = grab ? 16 : (phase2 ? 18 : 22);
            float k = Math.min(1, st / (float) wind);
            seg[0].pose(-0.45f * k, 0, 0);
            for (int i = 1; i < seg.length; i++) seg[i].pose(-0.14f * k + (float) Math.sin(st * 0.9 + i) * 0.03f, 0, 0);
            if (st % 3 == 0) {
                for (double d = 1; d <= 8.8; d += 0.6) {
                    Location l = base.clone().add(dir.clone().multiply(d)).add(0, 0.15, 0);
                    l.getWorld().spawnParticle(Particle.DUST, l, 1, 0.35, 0, 0.35, 0, new Particle.DustOptions(grab ? EYE : VOID, 1.3f));
                }
            }
            if (grab && st % 2 == 0) tip().getWorld().spawnParticle(Particle.WITCH, tip(), 4, 0.2, 0.2, 0.2, 0);
            if (st == 2) sound(base, Sound.ENTITY_SQUID_AMBIENT, 2f, 0.4f);
            if (st >= wind) {
                go(TState.SLAM);
                slamGrab = grab;
            }
        }

        boolean slamGrab;

        private void slam() {
            if (st <= 2) {
                seg[0].pose(1.48f, 0, 0);
                for (int i = 1; i < seg.length; i++) seg[i].pose(0.015f, 0, 0);
            }
            if (st == 2) {
                sound(base, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f);
                sound(base, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 2f, 0.7f);
                for (double d = 1; d <= 8.8; d += 0.9) {
                    Location l = base.clone().add(dir.clone().multiply(d)).add(0, 0.3, 0);
                    l.getWorld().spawnParticle(Particle.SQUID_INK, l, 3, 0.3, 0.1, 0.3, 0.05);
                    org.bukkit.block.data.BlockData under = l.clone().add(0, -0.8, 0).getBlock().getBlockData();
                    if (!under.getMaterial().isAir()) l.getWorld().spawnParticle(Particle.BLOCK, l, 5, 0.4, 0.1, 0.4, 0.1, under);
                }
                for (Player p : playersNear(64)) {
                    if (!inLine(p.getLocation())) continue;
                    if (slamGrab && p == aim && grabbed == null && !anyHolding()) {
                        grabbed = p;
                        freeDmg = 0;
                        freeNeed = 20 + 8 * Math.max(1, playersNear(40).size());
                        hurt(p, 3, null);
                        titleNear("<#ffd24a><b>MACKA ŁAPIE " + p.getName().toUpperCase() + "!</b>", "<white>Bijcie mackę, żeby go uwolnić!", 40);
                        sound(p.getLocation(), Sound.ENTITY_SLIME_SQUISH, 2f, 0.4f);
                    } else {
                        hurt(p, slamGrab ? 6 : (phase2 ? 10 : 9), dir.clone().multiply(0.5).setY(0.45));
                    }
                }
            }
            if (grabbed != null && st >= 4) {
                grabCooldown = 20 * 22;
                go(TState.HOLD);
                return;
            }
            if (st > 24) {
                float k = Math.min(1, (st - 24) / 14f);
                seg[0].pose(1.48f * (1 - k), 0, 0);
            }
            if (st >= 38) {
                cool = (phase2 ? 55 : 75) + ThreadLocalRandom.current().nextInt(35);
                go(TState.IDLE);
            }
        }

        private boolean inLine(Location l) {
            Vector rel = l.toVector().subtract(base.toVector());
            if (Math.abs(rel.getY()) > 2.5) return false;
            rel.setY(0);
            double along = rel.dot(dir);
            if (along < 0.3 || along > 9.2) return false;
            double side = rel.clone().subtract(dir.clone().multiply(along)).length();
            return side < 1.3;
        }

        private void hold() {
            float k = Math.min(1, st / 14f);
            seg[0].pose(1.48f * (1 - k), 0, 0);
            for (int i = 1; i < seg.length; i++) seg[i].pose(i >= 4 ? -0.35f * k : 0.02f, 0, (float) Math.sin(st * 0.3 + i) * 0.04f);
            if (grabbed == null || !grabbed.isValid() || grabbed.isDead()) {
                release(false);
                return;
            }
            Location hook = tip().subtract(0, 1.8, 0);
            Vector pullV = hook.toVector().subtract(grabbed.getLocation().toVector()).multiply(0.35);
            if (pullV.length() > 1.2) pullV.normalize().multiply(1.2);
            grabbed.setVelocity(pullV);
            grabbed.setFallDistance(0);
            if (st % 20 == 0) hurt(grabbed, 2, null);
            if (st % 4 == 0) {
                int bars = 10, full = (int) Math.round(Math.min(1, freeDmg / freeNeed) * bars);
                var msg = MM.deserialize("<#ffd24a>☠ Uwolnij <white>" + grabbed.getName() + "</white>: <#7ee081>" + "▮".repeat(full)
                        + "</#7ee081><dark_gray>" + "▮".repeat(bars - full) + "</dark_gray> <gray>| rzut za <white>" + Math.max(0, (90 - st) / 20) + " s");
                for (Player p : playersNear(40)) plugin.messages().actionBar(p, SunBossy.MSG_ACTION, msg);
            }
            if (freeDmg >= freeNeed) {
                release(true);
                return;
            }
            if (st >= 90) {
                Vector toC = arenaC.toVector().subtract(grabbed.getLocation().toVector()).setY(0);
                if (toC.lengthSquared() < 0.01) toC = facing.clone();
                grabbed.setVelocity(new Vector(0, 0, 0));
                hurt(grabbed, 9, toC.normalize().multiply(0.8).setY(0.5));
                sound(grabbed.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 2f, 0.5f);
                grabbed.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false, false));
                grabbed = null;
                cool = 70;
                go(TState.IDLE);
            }
        }

        private void release(boolean freed) {
            if (grabbed != null) {
                grabbed.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
                grabbed.setVelocity(new Vector(0, 0.2, 0));
                if (freed) titleNear("<#7ee081><b>UWOLNIONY!</b>", "<gray>Macka puszcza " + grabbed.getName(), 40);
            }
            grabbed = null;
            if (freed) hurtRetreat();
            else {
                cool = 60;
                go(TState.IDLE);
            }
        }

        void hurtRetreat() {
            if (state == TState.RETREAT || state == TState.GONE) return;
            if (grabbed != null) {
                Player g = grabbed;
                grabbed = null;
                g.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
            }
            sound(base, Sound.ENTITY_SQUID_HURT, 2.5f, 0.4f);
            sound(base, Sound.BLOCK_SCULK_BREAK, 2f, 0.5f);
            go(TState.RETREAT);
        }

        private void retreat() {
            float k = Math.min(1, st / 20f);
            seg[0].offset.set(0, -10 * k, 0);
            sway(2f);
            if (st % 3 == 0) base.getWorld().spawnParticle(Particle.SQUID_INK, base.clone().add(0, 0.3, 0), 4, 0.5, 0.2, 0.5, 0.02);
            if (k >= 1) {
                r.remove();
                respawnIn = phase2 ? 100 : 140;
                go(TState.GONE);
            }
        }

        void damage(double raw) {
            if (!up()) return;
            hpLeft -= raw;
            if (grabbed != null) freeDmg += raw;
            base.getWorld().spawnParticle(Particle.SQUID_INK, segPoint(3), 4, 0.3, 0.5, 0.3, 0.05);
            if (hpLeft <= 0 && grabbed == null) hurtRetreat();
        }

        void remove() {
            if (grabbed != null) grabbed.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
            grabbed = null;
            r.remove();
        }
    }

    private boolean anyHolding() {
        for (Tentacle tn : tentacles) if (tn.grabbed != null) return true;
        return false;
    }

    private Location pickSpot(Tentacle self) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Player> ps = playersNear(30);
        for (int k = 0; k < 14; k++) {
            Location c;
            if (!ps.isEmpty() && r.nextInt(100) < 60) {
                double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(3, 6);
                c = ps.get(r.nextInt(ps.size())).getLocation().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            } else {
                double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(3, 11);
                c = arenaC.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            }
            Location spot = groundBelow(c, 4, 8);
            if (spot == null) continue;
            if (spot.distanceSquared(headPos) < 5 * 5) continue;
            boolean crowded = false;
            for (Tentacle o : tentacles) {
                if (o != self && o.base != null && o.state != TState.GONE && o.base.distanceSquared(spot) < 3 * 3) crowded = true;
            }
            if (crowded) continue;
            return spot;
        }
        return null;
    }

    private Tentacle tentacleOf(ArmorStand box) {
        for (Tentacle tn : tentacles) if (tn.boxLow == box || tn.boxHigh == box) return tn;
        return null;
    }

    @Override
    protected double damageMultiplier(ArmorStand hitbox) {
        Tentacle tn = tentacleOf(hitbox);
        if (tn != null) return tn.up() ? 0.6 : 0;
        if (hitbox == eyeBoxL || hitbox == eyeBoxR) return exposed() ? 2.2 : 0.5;
        return exposed() ? 1.0 : 0.35;
    }

    @Override
    protected void onDamaged(Player p, double amount, ArmorStand hitbox) {
        Tentacle tn = tentacleOf(hitbox);
        if (tn != null) {
            tn.damage(amount / 0.6);
            p.playSound(p, Sound.BLOCK_SCULK_HIT, 1f, 0.8f);
        }
    }

    @Override
    protected void hurtSound() {
        sound(headPos, Sound.ENTITY_SQUID_HURT, 1.5f, 0.5f);
        sound(headPos, Sound.ENTITY_ELDER_GUARDIAN_HURT, 1f, 0.6f);
    }

    @Override
    protected void onDeath() {
        for (Tentacle tn : tentacles) tn.hurtRetreat();
        if (blob != null) blob.d.remove();
        blob = null;
        clouds.clear();
        go(State.DEATH);
        titleNear("<#b07cff><b>KRAKEN TONIE W OTCHŁANI</b>", "<gray>…", 64);
    }

    private void death() {
        if (st == 1) {
            sound(headPos, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 3f, 0.5f);
            sound(headPos, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 3f, 0.4f);
        }
        setAdvance(advance * 0.95);
        root.rot.identity().rotateX((float) Math.sin(st * 0.3) * 0.15f).rotateZ(Math.min(0.6f, st * 0.01f));
        lift = Math.min(lift, LURK_Y + 1) - (st > 10 ? 0.18f : 0);
        if (st % 3 == 0) {
            headPos.getWorld().spawnParticle(Particle.REVERSE_PORTAL, headPos.clone().add(0, lift + 4, 0), 25, 2.5, 2.5, 2.5, 0.08);
            headPos.getWorld().spawnParticle(Particle.SQUID_INK, headPos.clone().add(0, lift + 2, 0), 10, 2, 2, 2, 0.05);
        }
        if (st % 15 == 0) sound(headPos, Sound.ENTITY_SQUID_DEATH, 2.5f, 0.4f + st / 200f);
        if (st == 60) {
            Location mid = headPos.clone().add(0, lift + 4, 0);
            sound(mid, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
            mid.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0);
            mid.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mid, 300, 1, 1, 1, 0.8);
            mid.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mid.clone().add(facing.clone().multiply(4)), 120, 0.4, 0.8, 0.4, 0.6);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Rig.Part p : new ArrayList<>(rig.parts())) {
                BlockDisplay d = rig.detach(p);
                if (d == null) continue;
                Debris.launch(plugin, d, new Vector(r.nextDouble(-0.5, 0.5), r.nextDouble(0.1, 0.6), r.nextDouble(-0.5, 0.5)), 50 + r.nextInt(40));
            }
            for (Player p : playersNear(64)) p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (st >= 72) finishDeath();
    }

    @Override
    public String debug() {
        return super.debug() + " stan=" + state + " macki=" + tentacles.stream().map(x -> x.state + (x.grabbed != null ? "(trzyma)" : "")).toList() + " faza2=" + phase2;
    }

    @Override
    protected void cleanup() {
        for (Tentacle tn : tentacles) tn.remove();
        tentacles.clear();
        if (blob != null) blob.d.remove();
        blob = null;
        clouds.clear();
    }
}

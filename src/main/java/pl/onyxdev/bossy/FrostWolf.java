package pl.onyxdev.bossy;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import pl.onyxdev.suncore.SunCore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FrostWolf extends Boss {
    private enum State { RISE, IDLE, RUN, BITE, POUNCE, LEAP, HOWL, ROAR, DEATH }

    private static final Color ICE = Color.fromRGB(0x9BE7FF);
    private static final Color DEEP = Color.fromRGB(0x3A7BFF);
    private static final Color MARK = Color.fromRGB(0xFF4D6D);
    private static final int MARK_TICKS = 20 * 12;

    private final Rig.Bone body, neck, head, jaw, tail, legFL, legFR, legBL, legBR;
    private final List<Rig.Part> spikes = new ArrayList<>();
    private final List<Rig.Part> eyes = new ArrayList<>();

    private State state = State.RISE;
    private int st;
    private int cooldown = 30;
    private double stride;
    private boolean phase2;
    private ArmorStand chest, rear, mid;

    private UUID hunted;
    private UUID lastHunted;
    private int markedAt = -9999;
    private Player target;

    private Location jumpFrom, jumpTo;
    private int jumpLen;
    private int pouncesLeft;
    private final List<Wolf> pack = new ArrayList<>();
    private int blizzardUntil = -1;
    private int nextBlizzard;

    public FrostWolf(SunBossy plugin, Location home) {
        super(plugin, "wilk", home, BossBar.bossBar(MM.deserialize("<#9be7ff><b>❄ Lodowy Wilk Alfa</b>"), 1f,
                BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        Material fur = Material.WHITE_WOOL, grey = Material.LIGHT_GRAY_WOOL, dark = Material.GRAY_WOOL;

        body = rig.bone(null, 0, 2.6f, 0);
        neck = rig.bone(body, 0, 3.0f, 1.9f);
        head = rig.bone(neck, 0, 3.4f, 2.5f);
        jaw = rig.bone(head, 0, 3.05f, 3.0f);
        tail = rig.bone(body, 0, 2.95f, -2.2f);
        legFL = rig.bone(body, -0.75f, 2.2f, 1.55f);
        legFR = rig.bone(body, 0.75f, 2.2f, 1.55f);
        legBL = rig.bone(body, -0.75f, 2.2f, -1.55f);
        legBR = rig.bone(body, 0.75f, 2.2f, -1.55f);

        rig.part(body, fur, 0, 2.6f, -0.2f, 1.9f, 1.55f, 3.9f);
        rig.part(body, grey, 0, 1.85f, -0.2f, 1.45f, 0.2f, 3.2f);
        rig.part(body, grey, 0, 3.42f, -0.4f, 1.5f, 0.25f, 3.3f);
        rig.part(neck, fur, 0, 2.75f, 1.75f, 2.3f, 2.1f, 1.2f);
        rig.part(neck, grey, 0, 2.2f, 2.05f, 1.6f, 0.6f, 0.9f);
        Quaternionf tilt = new Quaternionf().rotateX(-0.45f);
        for (int i = 0; i < 5; i++) {
            float z = 1.2f - i * 0.7f;
            float h = i == 0 || i == 4 ? 0.6f : 0.9f;
            spikes.add(rig.part(body, i % 2 == 0 ? Material.PACKED_ICE : Material.BLUE_ICE, 0, 3.55f + h / 2, z, 0.32f, h, 0.32f, tilt));
        }
        for (int s = -1; s <= 1; s += 2) {
            spikes.add(rig.part(neck, Material.PACKED_ICE, 0.75f * s, 3.75f, 1.6f, 0.28f, 0.7f, 0.28f,
                    new Quaternionf().rotateX(-0.5f).rotateZ(-0.35f * s)));
        }

        rig.part(head, fur, 0, 3.45f, 2.75f, 1.3f, 1.1f, 1.2f);
        rig.part(head, grey, 0, 3.2f, 3.6f, 0.72f, 0.55f, 0.85f);
        rig.part(head, Material.BLACK_CONCRETE, 0, 3.38f, 4.03f, 0.3f, 0.2f, 0.08f);
        for (int s = -1; s <= 1; s += 2) {
            eyes.add(rig.part(head, Material.SEA_LANTERN, 0.3f * s, 3.62f, 3.36f, 0.22f, 0.12f, 0.04f));
            rig.part(head, fur, 0.38f * s, 4.15f, 2.55f, 0.3f, 0.48f, 0.2f);
            rig.part(head, Material.PINK_WOOL, 0.38f * s, 4.12f, 2.66f, 0.16f, 0.3f, 0.04f);
        }
        rig.part(jaw, grey, 0, 2.9f, 3.55f, 0.62f, 0.2f, 0.75f);
        rig.part(jaw, Material.QUARTZ_BLOCK, 0, 3.02f, 3.85f, 0.5f, 0.08f, 0.1f);
        rig.part(tail, fur, 0, 2.95f, -2.95f, 0.5f, 0.5f, 1.5f);
        rig.part(tail, grey, 0, 2.95f, -3.75f, 0.4f, 0.4f, 0.4f);

        for (Rig.Bone leg : List.of(legFL, legFR, legBL, legBR)) {
            float x = leg == legFL || leg == legBL ? -0.75f : 0.75f;
            float z = leg == legFL || leg == legFR ? 1.55f : -1.55f;
            rig.part(leg, fur, x, 1.55f, z, 0.62f, 1.3f, 0.75f);
            rig.part(leg, grey, x, 0.6f, z + 0.05f, 0.5f, 1.1f, 0.55f);
            rig.part(leg, dark, x, 0.13f, z + 0.2f, 0.62f, 0.26f, 0.8f);
        }
    }

    @Override
    public String displayName() {
        return "<#9be7ff><b>❄ Lodowy Wilk Alfa</b></#9be7ff>";
    }

    @Override
    public void spawnAt(float yaw) {
        rig.yaw = yaw;
        rig.spawn(home.clone());
        rig.scale = 0.01f;
        for (Rig.Part p : eyes) {
            if (p.d == null) continue;
            p.d.setBrightness(new Display.Brightness(15, 15));
            p.d.setGlowing(true);
            p.d.setGlowColorOverride(ICE);
        }
        mid = addHurtbox(home.clone().add(0, -20, 0), 1.7f);
        chest = addHurtbox(home.clone().add(0, -20, 0), 1.8f);
        rear = addHurtbox(home.clone().add(0, -20, 0), 1.4f);
    }

    @Override
    protected void onTick() {
        st++;
        switch (state) {
            case RISE -> rise();
            case IDLE, RUN -> hunt();
            case BITE -> bite();
            case POUNCE -> pounce();
            case LEAP -> leap();
            case HOWL -> howl();
            case ROAR -> roar();
            case DEATH -> death();
        }
        if (state != State.RISE && state != State.DEATH) {
            placeHurtboxes();
            markTick();
            blizzardTick();
        }
        pack.removeIf(w -> !w.isValid() || w.isDead());
        if (t % 2 == 0) rig.apply(2);
    }

    private void go(State s) {
        state = s;
        st = 0;
    }

    private void neutral() {
        body.pose(0, 0, 0).offset.set(0, 0, 0);
        neck.pose(0, 0, 0);
        head.pose(0, 0, 0);
        jaw.pose(0, 0, 0);
        tail.pose(0.35f + (float) Math.sin(t * 0.15) * 0.08f, (float) Math.sin(t * 0.2) * 0.25f, 0);
        legFL.pose(0, 0, 0);
        legFR.pose(0, 0, 0);
        legBL.pose(0, 0, 0);
        legBR.pose(0, 0, 0);
    }

    private Vector forward() {
        double y = Math.toRadians(rig.yaw);
        return new Vector(-Math.sin(y), 0, Math.cos(y));
    }

    private void placeHurtboxes() {
        Location c = rig.location();
        mid.teleport(c);
        chest.teleport(c.clone().add(forward().multiply(1.9)));
        rear.teleport(c.clone().add(forward().multiply(-1.7)));
    }

    private void lookAt(Location at, float maxDeg) {
        Vector d = at.toVector().subtract(rig.location().toVector());
        if (d.lengthSquared() < 0.01) return;
        float want = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float diff = ((want - rig.yaw) % 360 + 540) % 360 - 180;
        rig.yaw += Math.max(-maxDeg, Math.min(maxDeg, diff));
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private int cd(int ticks) {
        return phase2 ? (int) (ticks * 0.65) : ticks;
    }

    private void markTick() {
        Player h = hunted == null ? null : Bukkit.getPlayer(hunted);
        boolean lost = h == null || !h.isValid() || h.isDead() || !playersNear(44).contains(h);
        if (lost || t - markedAt > MARK_TICKS) pickHunted();
        h = hunted == null ? null : Bukkit.getPlayer(hunted);
        target = h != null ? h : nearestPlayer(40);
        if (h != null && t % 5 == 0) {
            Location top = h.getLocation().add(0, 2.4, 0);
            for (int i = 0; i < 8; i++) {
                double a = t * 0.2 + Math.PI * 2 * i / 8;
                top.getWorld().spawnParticle(Particle.DUST, top.clone().add(Math.cos(a) * 0.5, 0, Math.sin(a) * 0.5), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(MARK, 0.9f));
            }
        }
    }

    private void pickHunted() {
        List<Player> ps = playersNear(40);
        if (ps.isEmpty()) {
            hunted = null;
            return;
        }
        List<Player> fresh = new ArrayList<>(ps);
        if (fresh.size() > 1) fresh.removeIf(p -> p.getUniqueId().equals(lastHunted));
        Player p = fresh.get(ThreadLocalRandom.current().nextInt(fresh.size()));
        hunted = p.getUniqueId();
        lastHunted = hunted;
        markedAt = t;
        p.playSound(p, Sound.ENTITY_WOLF_BIG_GROWL, 1.2f, 0.6f);
        SunCore.messages().title(p, SunBossy.MSG_TITLE, net.kyori.adventure.title.Title.title(
                MM.deserialize("<#ff4d6d><b>🎯 ALFA CIĘ ŚCIGA</b>"), MM.deserialize("<gray>uciekaj i nie stój w miejscu — reszta niech bije"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(500))));
        var msg = MM.deserialize("<#9be7ff>❄ Alfa poluje na <white>" + MM.escapeTags(p.getName()) + "</white> <gray>— pomóżcie mu!");
        for (Player o : ps) if (o != p) plugin.messages().actionBar(o, SunBossy.MSG_ACTION, msg);
    }

    private void rise() {
        invulnerable = true;
        neutral();
        Location h = home;
        if (st == 1) {
            titleNear("<#9be7ff><b>LODOWY WILK ALFA</b>", "<gray>wyłania się z zamieci…", 64);
            sound(h, Sound.ENTITY_WOLF_BIG_WHINE, 2f, 0.5f);
            sound(h, Sound.ITEM_ELYTRA_FLYING, 1.5f, 0.6f);
        }
        if (st < 40 && st % 2 == 0) {
            h.getWorld().spawnParticle(Particle.SNOWFLAKE, h.clone().add(0, 2, 0), 30, 3, 2, 3, 0.05);
            h.getWorld().spawnParticle(Particle.WHITE_ASH, h.clone().add(0, 2, 0), 20, 3, 2, 3, 0.02);
        }
        rig.scale = Math.min(1f, st / 30f);
        if (st >= 32 && st < 60) {
            float k = Math.min(1, (st - 32) / 8f);
            neck.pose(-0.7f * k, 0, 0);
            head.pose(-0.5f * k, 0, 0);
            jaw.pose(0.5f * k, 0, 0);
            body.offset.set(0, -0.25f * k, 0);
            legBL.pose(0.35f * k, 0, 0);
            legBR.pose(0.35f * k, 0, 0);
        }
        if (st == 36) howlSound(h);
        if (st >= 65) {
            rig.scale = 1f;
            invulnerable = false;
            go(State.IDLE);
        }
    }

    private void howlSound(Location at) {
        sound(at, Sound.ENTITY_WOLF_BIG_WHINE, 3f, 0.45f);
        sound(at, Sound.ENTITY_WOLF_BIG_AMBIENT, 2.5f, 0.5f);
        sound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
    }

    private void hunt() {
        if (!phase2 && hp / maxHp <= 0.5) {
            go(State.ROAR);
            return;
        }
        neutral();
        if (target == null) {
            state = State.IDLE;
            return;
        }
        Location c = rig.location();
        double dist = horizontal(c, target.getLocation());
        if (--cooldown <= 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (dist < 3.6) {
                go(State.BITE);
                cooldown = cd(18);
                return;
            }
            if (pack.size() < 2 && r.nextInt(100) < 25) {
                go(State.HOWL);
                cooldown = cd(60);
                return;
            }
            if (dist >= 5 && dist <= 20) {
                pouncesLeft = phase2 ? 2 : 1;
                go(State.POUNCE);
                cooldown = cd(45);
                return;
            }
            if (dist > 20 || r.nextInt(100) < 20) {
                if (startLeap()) {
                    cooldown = cd(25);
                    return;
                }
            }
            cooldown = 10;
        }
        lookAt(target.getLocation(), phase2 ? 14 : 10);
        if (dist > 2.6 && c.distanceSquared(home) < 20 * 20) {
            state = State.RUN;
            double speed = phase2 ? 0.26 : 0.2;
            Location next = ground(c.clone().add(forward().multiply(speed)));
            if (next != null && Math.abs(next.getY() - c.getY()) <= 1.2) rig.moveTo(next, 1);
            stride += speed * 1.9;
            float s = (float) Math.sin(stride);
            legFL.pose(s * 0.7f, 0, 0);
            legBR.pose(s * 0.7f, 0, 0);
            legFR.pose(-s * 0.7f, 0, 0);
            legBL.pose(-s * 0.7f, 0, 0);
            body.offset.set(0, (float) (Math.abs(Math.cos(stride)) * 0.15), 0);
            neck.pose(0.1f, 0, 0);
            if (t % 7 == 0) {
                sound(c, Sound.BLOCK_POWDER_SNOW_STEP, 1.2f, 0.7f);
                c.getWorld().spawnParticle(Particle.SNOWFLAKE, c, 4, 0.6, 0.1, 0.6, 0.02);
            }
            if (t % 50 == 0) sound(c, Sound.ENTITY_WOLF_BIG_PANT, 1.2f, 0.6f);
        } else {
            state = State.IDLE;
            if (t % 60 == 0) sound(c, Sound.ENTITY_WOLF_BIG_GROWL, 1.2f, 0.6f);
        }
    }

    private void bite() {
        neutral();
        if (target != null && st < 6) lookAt(target.getLocation(), 20);
        if (st <= 6) {
            float k = st / 6f;
            neck.pose(-0.3f * k, 0, 0);
            jaw.pose(0.7f * k, 0, 0);
            body.offset.set(0, 0, -0.3f * k);
        } else if (st <= 10) {
            neck.pose(0.35f, 0, 0);
            jaw.pose(0, 0, 0);
            body.offset.set(0, -0.1f, 0.6f);
            if (st == 7) {
                Location c = rig.location();
                sound(c, Sound.ENTITY_WOLF_BIG_GROWL, 1.5f, 0.8f);
                sound(c, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.6f);
                Location mouth = rig.worldPoint(head, 0, 3.2f, 3.9f);
                for (Player p : playersNear(6)) {
                    if (p.getLocation().add(0, 1, 0).distanceSquared(mouth) < 2.6 * 2.6) {
                        hurt(p, phase2 ? 8 : 6, forward().multiply(0.6).setY(0.3));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true, true));
                        p.setFreezeTicks(Math.max(p.getFreezeTicks(), 100));
                    }
                }
            }
        } else {
            float k = Math.max(0, 1 - (st - 10) / 6f);
            body.offset.set(0, 0, 0.6f * k);
        }
        if (st >= 16) go(State.IDLE);
    }

    private void pounce() {
        neutral();
        Location c = rig.location();
        if (st <= 16) {
            if (target != null) lookAt(target.getLocation(), 15);
            float k = st / 16f;
            body.offset.set(0, -0.6f * k, -0.3f * k);
            legFL.pose(-0.5f * k, 0, 0);
            legFR.pose(-0.5f * k, 0, 0);
            legBL.pose(0.7f * k, 0, 0);
            legBR.pose(0.7f * k, 0, 0);
            neck.pose(0.25f * k, 0, 0);
            if (st == 2) sound(c, Sound.ENTITY_WOLF_BIG_GROWL, 1.8f, 0.5f);
            if (st == 9) {
                Location aim = target == null ? null : groundBelow(target.getLocation(), 2, 8);

                if (aim == null || aim.distanceSquared(home) > 22 * 22) {
                    jumpTo = null;
                    go(State.IDLE);
                    return;
                }
                jumpTo = aim;
                jumpFrom = c.clone();
                jumpLen = (int) Math.max(12, Math.min(22, horizontal(c, aim) * 1.1));
            }
            if (st >= 9 && st % 2 == 1 && jumpTo != null) {
                ring(jumpTo, 3, DEEP, 1.6f);
                ring(jumpTo, 1.6, ICE, 1.2f);
            }
        } else {
            int k = st - 16;
            if (jumpTo == null) {
                go(State.IDLE);
                return;
            }
            double f = Math.min(1, k / (double) jumpLen);
            lookAt(jumpTo, 30);
            Location p = jumpFrom.clone().add(jumpTo.clone().subtract(jumpFrom).toVector().multiply(f))
                    .add(0, Math.sin(Math.PI * f) * 4, 0);
            rig.moveTo(p, 1);
            legFL.pose(-1.0f, 0, 0);
            legFR.pose(-1.0f, 0, 0);
            legBL.pose(0.9f, 0, 0);
            legBR.pose(0.9f, 0, 0);
            jaw.pose(0.6f, 0, 0);
            if (k == 1) sound(c, Sound.ENTITY_WOLF_BIG_AMBIENT, 2f, 0.7f);
            c.getWorld().spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 2, 0), 3, 0.5, 0.5, 0.5, 0.02);
            if (k == jumpLen) {
                land(jumpTo);
                if (--pouncesLeft > 0 && target != null) {
                    st = 4;
                    return;
                }
            }
            if (k >= jumpLen + 8) go(State.IDLE);
        }
    }

    private void land(Location at) {
        sound(at, Sound.BLOCK_POWDER_SNOW_BREAK, 2f, 0.5f);
        sound(at, Sound.ITEM_MACE_SMASH_GROUND, 1.5f, 0.7f);
        sound(at, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        at.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 0.3, 0), 60, 2, 0.2, 2, 0.12);
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), 30, 1.5, 0.1, 1.5, 0, Material.PACKED_ICE.createBlockData());
        for (Player p : playersNear(30)) {
            double d = horizontal(p.getLocation(), at);
            if (d > 3.2 || Math.abs(p.getLocation().getY() - at.getY()) > 2.5) continue;
            Vector out = p.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) out = forward();
            hurt(p, (phase2 ? 11 : 9) * (1 - 0.4 * d / 3.2), out.normalize().multiply(0.9).setY(0.5));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
            p.setFreezeTicks(Math.max(p.getFreezeTicks(), 120));
        }
    }

    private boolean startLeap() {
        Location c = rig.location();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = target != null ? target.getLocation() : home;
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(6, 11);
            Location spot = groundBelow(base.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d), 6, 10);
            if (spot == null || spot.distanceSquared(home) > 20 * 20 || spot.distanceSquared(c) < 25) continue;
            jumpFrom = c.clone();
            jumpTo = spot;
            jumpLen = (int) Math.max(12, Math.min(24, horizontal(c, spot) * 0.9));
            go(State.LEAP);
            return true;
        }
        return false;
    }

    private void leap() {
        neutral();
        if (st <= 6) {
            body.offset.set(0, -0.4f * st / 6f, 0);
            lookAt(jumpTo, 30);
            return;
        }
        int k = st - 6;
        double f = Math.min(1, k / (double) jumpLen);
        lookAt(jumpTo, 30);
        Location p = jumpFrom.clone().add(jumpTo.clone().subtract(jumpFrom).toVector().multiply(f))
                .add(0, Math.sin(Math.PI * f) * 6, 0);
        rig.moveTo(p, 1);
        legFL.pose(-1.1f, 0, 0);
        legFR.pose(-1.1f, 0, 0);
        legBL.pose(1.0f, 0, 0);
        legBR.pose(1.0f, 0, 0);
        tail.pose(-0.2f, 0, 0);
        if (k == 1) sound(p, Sound.ENTITY_WOLF_BIG_AMBIENT, 1.5f, 0.9f);
        if (k % 2 == 0) p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.clone().add(0, 2, 0), 4, 0.5, 0.3, 0.5, 0.01);
        if (k == jumpLen) {
            sound(jumpTo, Sound.BLOCK_POWDER_SNOW_FALL, 2f, 0.6f);
            jumpTo.getWorld().spawnParticle(Particle.SNOWFLAKE, jumpTo.clone().add(0, 0.3, 0), 30, 1.2, 0.1, 1.2, 0.08);
        }
        if (k >= jumpLen + 4) go(State.IDLE);
    }

    private void howl() {
        neutral();
        float k = Math.min(1, st / 8f);
        neck.pose(-0.9f * k, 0, 0);
        head.pose(-0.5f * k, 0, 0);
        jaw.pose(0.55f * k, 0, 0);
        body.offset.set(0, -0.2f * k, 0);
        legBL.pose(0.4f * k, 0, 0);
        legBR.pose(0.4f * k, 0, 0);
        Location c = rig.location();
        if (st == 6) {
            howlSound(c);
            titleNear("", "<#9be7ff>🐺 Alfa wzywa watahę!", 40);
        }
        if (st == 20) {
            int n = Math.min(5, 2 + playersNear(36).size() / 2 + (phase2 ? 1 : 0));
            for (int i = 0; i < n; i++) summonPackWolf(c);
        }
        if (st >= 34) go(State.IDLE);
    }

    private void summonPackWolf(Location c) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble(Math.PI * 2);
        Location at = ground(c.clone().add(Math.cos(a) * 3.5, 1, Math.sin(a) * 3.5));
        if (at == null) at = c.clone();
        Location spawn = at;
        Wolf w = c.getWorld().spawn(spawn, Wolf.class, e -> {
            plugin.tag(e);
            e.customName(MM.deserialize("<#9be7ff>Wilk z watahy"));
            e.setCustomNameVisible(false);
            Wolf.Variant snowy = Registry.WOLF_VARIANT.get(org.bukkit.NamespacedKey.minecraft("snowy"));
            if (snowy != null) e.setVariant(snowy);
            e.setAngry(true);
            e.setRemoveWhenFarAway(true);
            var max = e.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) max.setBaseValue(12);
            e.setHealth(12);
            var dmg = e.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(3);
        });
        Player p = target != null ? target : nearestPlayer(40);
        if (p != null) w.setTarget(p);
        spawn.getWorld().spawnParticle(Particle.SNOWFLAKE, spawn.clone().add(0, 0.8, 0), 20, 0.4, 0.5, 0.4, 0.05);
        pack.add(w);
    }

    private void blizzardTick() {
        if (!phase2) return;
        if (blizzardUntil < 0 && t >= nextBlizzard) {
            blizzardUntil = t + 20 * 9;
            titleNear("<#e8fbff><b>ZAMIEĆ</b>", "<gray>trzymajcie się razem…", 48);
            sound(rig.location(), Sound.ITEM_ELYTRA_FLYING, 2f, 0.4f);
        }
        if (blizzardUntil < 0) return;
        if (t >= blizzardUntil) {
            blizzardUntil = -1;
            nextBlizzard = t + 20 * 28;
            return;
        }
        if (t % 5 == 0) {
            for (Player p : playersNear(44)) {
                p.setFreezeTicks(Math.max(p.getFreezeTicks(), 132));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 12, 0, false, false, false));
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1.5, 0), 25, 4, 2, 4, 0.08);
                p.getWorld().spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 1.5, 0), 25, 4, 2, 4, 0.02);
            }
        }
    }

    private void roar() {
        invulnerable = true;
        neutral();
        float k = Math.min(1, st / 10f);
        neck.pose(-0.95f * k, 0, 0);
        head.pose(-0.4f * k, 0, 0);
        jaw.pose(0.6f * k, 0, 0);
        legFL.pose(-0.6f * k, 0, 0);
        legFR.pose(-0.6f * k, 0, 0);
        body.pose(-0.35f * k, 0, 0);
        Location c = rig.location();
        if (st == 1) {
            titleNear("<#9be7ff><b>FAZA 2</b>", "<white>Alfa wpada w szał — nadciąga zamieć!", 64);
            howlSound(c);
        }
        if (st >= 10 && st <= 30) {
            float g = 1 + 0.8f * (st - 10) / 20f;
            for (Rig.Part p : spikes) {
                p.extraScale = g;
                if (p.d != null) {
                    p.d.setGlowing(true);
                    p.d.setGlowColorOverride(DEEP);
                    p.d.setBrightness(new Display.Brightness(15, 15));
                }
            }
            if (st % 4 == 0) sound(c, Sound.BLOCK_GLASS_PLACE, 1.5f, 0.6f + (st - 10) / 30f);
        }
        if (st == 30) {
            phase2 = true;
            nextBlizzard = t + 40;
            bar.color(BossBar.Color.WHITE);
            bar.name(MM.deserialize("<#e8fbff><b>❄ Lodowy Wilk Alfa</b></#e8fbff> <gray>— szał"));
            for (int i = 0; i < 2; i++) summonPackWolf(c);
        }
        if (st >= 45) {
            invulnerable = false;
            cooldown = 15;
            go(State.IDLE);
        }
    }

    @Override
    protected double damageMultiplier(ArmorStand hitbox) {
        if (state == State.POUNCE && st <= 16 && hitbox == chest) return 1.5;
        return 1;
    }

    @Override
    protected void hurtSound() {
        Location c = rig.location().add(0, 2.5, 0);
        sound(c, Sound.ENTITY_WOLF_BIG_HURT, 1.2f, 0.6f);
        sound(c, Sound.BLOCK_GLASS_HIT, 0.8f, 0.8f);
    }

    @Override
    protected void onDeath() {
        for (Wolf w : pack) if (w.isValid()) w.remove();
        pack.clear();
        blizzardUntil = -1;
        go(State.DEATH);
        titleNear("<#9be7ff><b>ALFA PADA</b>", "<gray>zamieć cichnie…", 64);
    }

    private void death() {
        neutral();
        Location c = rig.location();
        float k = Math.min(1, st / 30f);
        body.pose(0, 0, 1.45f * k).offset.set(0, -1.3f * k, 0);
        neck.pose(0.3f * k, 0, 0);
        jaw.pose(0.3f * k, 0, 0);
        if (st == 1) sound(c, Sound.ENTITY_WOLF_BIG_DEATH, 2.5f, 0.5f);
        if (st == 12) sound(c, Sound.ENTITY_WOLF_BIG_WHINE, 2f, 0.4f);
        if (st % 5 == 0 && st < 50) c.getWorld().spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 1.5, 0), 15, 1.5, 1, 1.5, 0.03);
        if (st == 55) {
            sound(c, Sound.BLOCK_GLASS_BREAK, 2.5f, 0.5f);
            sound(c, Sound.BLOCK_POWDER_SNOW_BREAK, 2f, 0.6f);
            Location mid0 = c.clone().add(0, 1.5, 0);
            mid0.getWorld().spawnParticle(Particle.SNOWFLAKE, mid0, 150, 1.5, 1, 1.5, 0.25);
            mid0.getWorld().spawnParticle(Particle.FIREWORK, mid0, 60, 0.5, 0.5, 0.5, 0.25);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Rig.Part p : new ArrayList<>(rig.parts())) {
                BlockDisplay d = rig.detach(p);
                if (d == null) continue;
                Debris.launch(plugin, d, new Vector(r.nextDouble(-0.4, 0.4), r.nextDouble(0.2, 0.6), r.nextDouble(-0.4, 0.4)), 50 + r.nextInt(40));
            }
            for (Player p : playersNear(64)) p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (st >= 62) finishDeath();
    }

    @Override
    protected void cleanup() {
        for (Wolf w : pack) if (w.isValid()) w.remove();
        pack.clear();
    }
}

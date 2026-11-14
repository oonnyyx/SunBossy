package pl.onyxdev.bossy;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Guardian extends Boss {
    private enum State { RISE, IDLE, WALK, PUNCH, SLAM, THROW, CHARGE, STUN, ROAR, DEATH }

    private static final Color ORANGE = Color.fromRGB(0xFF8A1F);
    private static final Color GOLD = Color.fromRGB(0xFFC02E);

    private final Rig.Bone hips, legL, legR, torso, head, armL, armR, foreL, foreR;
    private final List<Rig.Part> armor = new ArrayList<>();
    private final Rig.Part core;
    private final List<Rig.Part> glowing = new ArrayList<>();

    private State state = State.RISE;
    private int st;
    private int cooldown = 40;
    private double stride;
    private boolean phase2;
    private boolean charged70, charged35;
    private int lastChargeT;
    private Player target;
    private boolean punchLeft;

    private ArmorStand main, sideA, sideB, coreBox;
    private double coreDamage, coreNeeded;
    private Location slamAt;

    private BlockDisplay heldBoulder;
    private final List<Orb> orbs = new ArrayList<>();

    public Guardian(SunBossy plugin, Location home) {
        super(plugin, "straznik", home, BossBar.bossBar(MM.deserialize("<#ffc02e><b>☀ Strażnik Słońca</b>"), 1f,
                BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10));
        Material stone = Material.STONE_BRICKS, chis = Material.CHISELED_STONE_BRICKS, dark = Material.POLISHED_DEEPSLATE,
                gold = Material.GOLD_BLOCK, vein = Material.OCHRE_FROGLIGHT, sun = Material.SHROOMLIGHT;

        hips = rig.bone(null, 0, 2.3f, 0);
        legL = rig.bone(hips, -0.75f, 2.3f, 0);
        legR = rig.bone(hips, 0.75f, 2.3f, 0);
        torso = rig.bone(hips, 0, 2.6f, 0);
        head = rig.bone(torso, 0, 5.0f, 0.1f);
        armL = rig.bone(torso, -1.95f, 4.4f, 0);
        armR = rig.bone(torso, 1.95f, 4.4f, 0);
        foreL = rig.bone(armL, -1.95f, 3.0f, 0);
        foreR = rig.bone(armR, 1.95f, 3.0f, 0);

        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone leg = s < 0 ? legL : legR;
            rig.part(leg, stone, 0.75f * s, 1.6f, 0, 1.0f, 1.4f, 1.0f);
            rig.part(leg, dark, 0.75f * s, 0.45f, 0.1f, 1.2f, 0.9f, 1.4f);
            armor.add(rig.part(leg, gold, 0.75f * s, 1.15f, 0.52f, 0.6f, 0.4f, 0.1f));
        }
        rig.part(hips, dark, 0, 2.45f, 0, 2.2f, 0.6f, 1.3f);
        armor.add(rig.part(hips, gold, 0, 2.78f, 0, 2.3f, 0.25f, 1.4f));

        rig.part(torso, stone, 0, 3.75f, 0, 3.0f, 2.1f, 1.8f);
        rig.part(torso, chis, 0, 4.5f, -0.3f, 2.6f, 0.9f, 1.4f);
        armor.add(rig.part(torso, gold, 0, 3.9f, 0.92f, 1.1f, 1.1f, 0.12f));
        core = rig.part(torso, sun, 0, 3.9f, 1.0f, 0.7f, 0.7f, 0.35f, new Quaternionf().rotateZ((float) Math.PI / 4));
        glowing.add(core);
        glowing.add(rig.part(torso, vein, -0.9f, 3.3f, 0.91f, 0.12f, 1.0f, 0.05f));
        glowing.add(rig.part(torso, vein, 0.9f, 3.3f, 0.91f, 0.12f, 1.0f, 0.05f));
        glowing.add(rig.part(torso, vein, 0, 2.95f, 0.91f, 1.4f, 0.12f, 0.05f));
        armor.add(rig.part(torso, gold, -1.95f, 4.85f, 0, 1.3f, 0.35f, 1.3f));
        armor.add(rig.part(torso, gold, 1.95f, 4.85f, 0, 1.3f, 0.35f, 1.3f));

        rig.part(head, chis, 0, 5.55f, 0.1f, 1.2f, 1.1f, 1.2f);
        rig.part(head, dark, 0, 5.85f, 0.72f, 1.25f, 0.2f, 0.1f);
        glowing.add(rig.part(head, vein, -0.28f, 5.6f, 0.71f, 0.28f, 0.16f, 0.04f));
        glowing.add(rig.part(head, vein, 0.28f, 5.6f, 0.71f, 0.28f, 0.16f, 0.04f));
        armor.add(rig.part(head, gold, 0, 6.15f, 0.1f, 1.3f, 0.18f, 1.3f));
        Quaternionf diamond = new Quaternionf().rotateY((float) Math.PI / 4);
        armor.add(rig.part(head, gold, -0.4f, 6.35f, 0.1f, 0.22f, 0.35f, 0.22f, diamond));
        armor.add(rig.part(head, gold, 0, 6.45f, 0.1f, 0.24f, 0.5f, 0.24f, diamond));
        armor.add(rig.part(head, gold, 0.4f, 6.35f, 0.1f, 0.22f, 0.35f, 0.22f, diamond));

        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone arm = s < 0 ? armL : armR, fore = s < 0 ? foreL : foreR;
            rig.part(arm, stone, 1.95f * s, 3.75f, 0, 0.95f, 1.5f, 0.95f);
            rig.part(fore, stone, 1.95f * s, 2.45f, 0, 1.05f, 1.2f, 1.05f);
            rig.part(fore, dark, 1.95f * s, 1.55f, 0.05f, 1.3f, 0.9f, 1.3f);
            armor.add(rig.part(fore, gold, 1.95f * s, 1.55f, 0.7f, 1.1f, 0.3f, 0.1f));
        }
    }

    @Override
    public String displayName() {
        return "<#ffc02e><b>☀ Strażnik Słońca</b></#ffc02e>";
    }

    public void spawnAt(float yaw) {
        rig.yaw = yaw;
        rig.spawn(home.clone().add(0, -7, 0));
        for (Rig.Part p : glowing) if (p.d != null) p.d.setBrightness(new Display.Brightness(0, 0));
        main = addHurtbox(home.clone().add(0, -20, 0), 3.3f);
        sideA = addHurtbox(home.clone().add(0, -20, 0), 2.7f);
        sideB = addHurtbox(home.clone().add(0, -20, 0), 2.7f);
    }

    @Override
    protected void onTick() {
        st++;
        switch (state) {
            case RISE -> rise();
            case IDLE, WALK -> roam();
            case PUNCH -> punch();
            case SLAM -> slam();
            case THROW -> throwBoulder();
            case CHARGE -> charge();
            case STUN -> stun();
            case ROAR -> roar();
            case DEATH -> death();
        }
        orbs.forEach(Orb::tick);
        if (state != State.RISE && state != State.DEATH) placeHurtboxes();
        if (t % 2 == 0 || state == State.SLAM && st >= 24 && st <= 28) rig.apply(st >= 24 && st <= 28 && state == State.SLAM ? 1 : 2);
    }

    private void go(State s) {
        state = s;
        st = 0;
    }

    private void neutral() {
        hips.pose(0, 0, 0).offset.set(0, 0, 0);
        legL.pose(0, 0, 0);
        legR.pose(0, 0, 0);
        torso.pose(0, 0, 0);
        head.pose(0, 0, 0);
        armL.pose(0, 0, -0.06f);
        armR.pose(0, 0, 0.06f);
        foreL.pose(0, 0, 0);
        foreR.pose(0, 0, 0);
    }

    private Vector forward() {
        double y = Math.toRadians(rig.yaw);
        return new Vector(-Math.sin(y), 0, Math.cos(y));
    }

    private Vector right() {
        double y = Math.toRadians(rig.yaw);
        return new Vector(Math.cos(y), 0, Math.sin(y));
    }

    private void placeHurtboxes() {
        Location c = rig.location();
        main.teleport(c);
        sideA.teleport(c.clone().add(right().multiply(1.35)).add(0, 0.6, 0));
        sideB.teleport(c.clone().add(right().multiply(-1.35)).add(0, 0.6, 0));
        if (coreBox != null) coreBox.teleport(rig.worldPoint(torso, 0, 3.9f, 1.15f).subtract(0, 0.55, 0));
    }

    private void lookAt(Location at, float maxDeg) {
        Vector d = at.toVector().subtract(rig.location().toVector());
        if (d.lengthSquared() < 0.01) return;
        float want = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float diff = ((want - rig.yaw) % 360 + 540) % 360 - 180;
        rig.yaw += Math.max(-maxDeg, Math.min(maxDeg, diff));
    }

    private void headTrack() {
        if (target == null) return;
        Vector d = target.getEyeLocation().toVector().subtract(rig.location().toVector());
        Vector3f local = new Quaternionf().rotateY((float) Math.toRadians(rig.yaw)).transform(new Vector3f((float) d.getX(), 0, (float) d.getZ()));
        float yaw = (float) Math.atan2(local.x, local.z);
        head.rot.mul(new Quaternionf().rotateY(Math.max(-0.8f, Math.min(0.8f, yaw))));
    }

    private void rise() {
        invulnerable = true;
        neutral();
        head.pose(0.35f, 0, 0);
        armL.pose(-0.2f, 0, -0.3f);
        armR.pose(-0.2f, 0, 0.3f);
        Location h = home;
        if (st == 1) {
            sound(h, Sound.ENTITY_WARDEN_EMERGE, 2f, 0.6f);
            titleNear("<#ffc02e><b>STRAŻNIK SŁOŃCA</b>", "<gray>przebudza się…", 64);
        }
        if (st % 10 == 1 && st < 50) {
            sound(h, Sound.ENTITY_WARDEN_DIG, 1.5f, 0.6f);
            rig.moveTo(h.clone().add(0, -7 + 7 * Math.min(1, (st + 9) / 50.0), 0), 10);
        }
        if (st % 3 == 0 && st < 50) crack(h, 2.5 + st / 25.0, 10);
        if (st == 5 || st == 30) debrisBurst(h, 8, 0.45);
        if (st == 50) {
            for (Rig.Part p : glowing) if (p.d != null) p.d.setBrightness(new Display.Brightness(15, 15));
            sound(h, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
        }
        if (st >= 50) {
            head.pose(-0.35f, 0, 0);
            armL.pose(0, 0, -1.0f);
            armR.pose(0, 0, 1.0f);
        }
        if (st == 55) {
            sound(h, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.5f);
            sound(h, Sound.ENTITY_IRON_GOLEM_DEATH, 1.5f, 0.4f);
            h.getWorld().spawnParticle(Particle.EXPLOSION, h.clone().add(0, 3, 0), 3, 1, 1, 1, 0);
        }
        if (st >= 75) {
            invulnerable = false;
            go(State.IDLE);
        }
    }

    private void roam() {
        if (checkPhaseAndCharge()) return;
        target = nearestPlayer(40);
        neutral();
        double breathe = Math.sin(t * 0.08);
        torso.pose((float) (0.03 * breathe), 0, 0);
        if (target == null) {
            state = State.IDLE;
            headTrack();
            return;
        }
        Location c = rig.location();
        double dist = horizontal(c, target.getLocation());
        cooldown--;
        if (cooldown <= 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (dist < 4.4) {
                go(State.PUNCH);
                punchLeft = !punchLeft;
                cooldown = cd(22);
                return;
            }
            if (!playersNear(9).isEmpty() && r.nextInt(100) < 50) {
                go(State.SLAM);
                cooldown = cd(55);
                return;
            }
            if (dist > 5) {
                go(State.THROW);
                cooldown = cd(45);
                return;
            }
        }
        lookAt(target.getLocation(), phase2 ? 9 : 6);
        boolean leashed = c.distanceSquared(home) > 20 * 20 && target.getLocation().distanceSquared(home) > c.distanceSquared(home);
        if (dist > 3.2 && !leashed) {
            state = State.WALK;
            double speed = phase2 ? 0.13 : 0.095;
            if (t % 2 == 0) {
                Location next = ground(c.clone().add(forward().multiply(speed * 2)));
                if (next != null && Math.abs(next.getY() - c.getY()) <= 1.2) rig.moveTo(next, 2);
            }
            double before = Math.sin(stride);
            stride += speed * 2.3;
            float s = (float) Math.sin(stride);
            legL.pose(s * 0.55f, 0, 0);
            legR.pose(-s * 0.55f, 0, 0);
            armL.pose(-s * 0.35f, 0, -0.08f);
            armR.pose(s * 0.35f, 0, 0.08f);
            hips.offset.set(0, (float) (-Math.abs(Math.cos(stride)) * 0.12), 0);
            torso.pose(0.05f, s * 0.08f, 0);
            if (Math.signum(before) != Math.signum(s)) step();
        } else {
            state = State.IDLE;
        }
        headTrack();
    }

    private int cd(int ticks) {
        return phase2 ? (int) (ticks * 0.7) : ticks;
    }

    private void step() {
        Location c = rig.location();
        sound(c, Sound.ENTITY_IRON_GOLEM_STEP, 1.6f, 0.5f);
        sound(c, Sound.ENTITY_WARDEN_STEP, 0.8f, 0.6f);
        BlockData under = c.clone().add(0, -0.5, 0).getBlock().getBlockData();
        if (!under.getMaterial().isAir()) c.getWorld().spawnParticle(Particle.BLOCK, c, 12, 1, 0.1, 1, 0, under);
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean checkPhaseAndCharge() {
        double f = hp / maxHp;
        if (!phase2 && f <= 0.5) {
            go(State.ROAR);
            return true;
        }
        if ((!charged70 && f <= 0.72) || (!charged35 && f <= 0.35) || (phase2 && t - lastChargeT > 20 * 70)) {
            if (f <= 0.72) charged70 = true;
            if (f <= 0.35) charged35 = true;
            lastChargeT = t;
            go(State.CHARGE);
            return true;
        }
        return false;
    }

    private void punch() {
        neutral();
        Rig.Bone arm = punchLeft ? armL : armR, fore = punchLeft ? foreL : foreR;
        float side = punchLeft ? -1 : 1;
        if (target != null && st < 9) lookAt(target.getLocation(), 12);
        if (st <= 8) {
            float k = st / 8f;
            arm.pose(0.9f * k, 0, 0.25f * side * k);
            torso.pose(-0.05f * k, -0.35f * side * k, 0);
        } else if (st <= 13) {
            arm.pose(-1.55f, 0, 0.05f * side);
            fore.pose(-0.3f, 0, 0);
            torso.pose(0.18f, 0.35f * side, 0);
            if (st == 10) {
                Location c = rig.location();
                sound(c, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
                sound(c, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.5f);
                Location fist = rig.worldPoint(fore, 1.95f * side, 1.55f, 0.5f);
                fist.getWorld().spawnParticle(Particle.EXPLOSION, fist, 1, 0, 0, 0, 0);
                for (Player p : playersNear(5.5)) {
                    Vector to = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                    if (to.lengthSquared() < 0.01 || to.clone().normalize().dot(forward()) > 0.25) {
                        hurt(p, phase2 ? 9 : 7, forward().multiply(1.3).setY(0.45));
                    }
                }
            }
        } else {
            float k = Math.max(0, 1 - (st - 13) / 6f);
            arm.pose(-1.55f * k, 0, 0);
            torso.pose(0.18f * k, 0, 0);
        }
        if (st >= 20) go(State.IDLE);
    }

    private void slam() {
        neutral();
        double radius = phase2 ? 9 : 8;
        if (st == 1) {
            sound(rig.location(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.6f);
            slamAt = rig.location().add(forward().multiply(2.2));
        }
        if (st <= 24) {
            float k = Math.min(1, st / 18f);
            armL.pose(-2.9f * k, 0, -0.15f);
            armR.pose(-2.9f * k, 0, 0.15f);
            torso.pose(-0.25f * k, 0, 0);
            head.pose(-0.3f * k, 0, 0);
            hips.offset.set(0, 0.25f * k, 0);
            if (st % 4 == 0) {
                ring(slamAt, radius, ORANGE, 1.6f);
                ring(slamAt, radius * 0.6, GOLD, 1.2f);
            }
            if (st % 6 == 0) sound(slamAt, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.5f + st / 40f);
        } else if (st <= 30) {
            armL.pose(-0.95f, 0, -0.1f);
            armR.pose(-0.95f, 0, 0.1f);
            torso.pose(0.55f, 0, 0);
            head.pose(0.2f, 0, 0);
            hips.offset.set(0, -0.7f, 0);
            if (st == 26) impact(slamAt, radius);
        } else {
            float k = Math.max(0, 1 - (st - 30) / 12f);
            armL.pose(-0.95f * k, 0, -0.1f);
            armR.pose(-0.95f * k, 0, 0.1f);
            torso.pose(0.55f * k, 0, 0);
            hips.offset.set(0, -0.7f * k, 0);
        }
        if (st >= 44) go(State.IDLE);
    }

    private void impact(Location at, double radius) {
        sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.6f);
        sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        sound(at, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 2f, 0.5f);
        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
        crack(at, 2.5, 40);
        debrisBurst(at, 14, 0.7);
        for (int k = 0; k < 8; k++) {
            int step = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double r = 1 + step * radius / 8.0;
                int n = (int) (r * 8);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    Location l = at.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                    at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0,
                            new Particle.DustTransition(ORANGE, Color.fromRGB(0x6b6b6b), 2f));
                }
                if (step % 2 == 0) crack(at, r, 6);
            }, k);
        }
        for (Player p : playersNear(radius + 3)) {
            double d = horizontal(p.getLocation(), at);
            if (d > radius) continue;
            boolean grounded = p.isOnGround() || p.getLocation().getY() - at.getY() < 0.6;
            if (!grounded && d > 2.5) continue;
            Vector out = p.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) out = forward();
            hurt(p, (phase2 ? 14 : 12) * (1 - 0.5 * d / radius), out.normalize().multiply(0.7).setY(1.0));
        }
    }

    private void throwBoulder() {
        neutral();
        if (target == null || !target.isValid()) {
            dropHeld();
            go(State.IDLE);
            return;
        }
        if (st < 18) lookAt(target.getLocation(), 10);
        if (st <= 12) {
            float k = st / 12f;
            armR.pose(0.8f * k, 0, 0.35f * k);
            torso.pose(0.25f * k, -0.25f * k, 0);
            hips.offset.set(0, -0.35f * k, 0);
            if (st == 6) {
                Location fist = rig.worldPoint(foreR, 1.95f, 1.2f, 0.2f);
                sound(fist, Sound.BLOCK_STONE_BREAK, 1.5f, 0.5f);
                crack(fist, 1.2, 12);
                heldBoulder = fist.getWorld().spawn(fist, BlockDisplay.class, d -> {
                    plugin.tag(d);
                    d.setBlock(Material.COBBLED_DEEPSLATE.createBlockData());
                    d.setTeleportDuration(2);
                    d.setTransformation(new Transformation(new Vector3f(-0.7f), new Quaternionf(), new Vector3f(1.4f), new Quaternionf()));
                });
            }
        } else if (st <= 18) {
            float k = (st - 12) / 6f;
            armR.pose(0.8f - 3.4f * k, 0, 0.35f - 0.25f * k);
            torso.pose(0.25f - 0.45f * k, -0.25f + 0.5f * k, 0);
        } else {
            float k = Math.max(0, 1 - (st - 18) / 10f);
            armR.pose(-2.6f * k, 0, 0);
            torso.pose(-0.2f * k, 0.25f * k, 0);
        }
        if (heldBoulder != null && st <= 18 && t % 2 == 0) {
            heldBoulder.teleport(rig.worldPoint(foreR, 1.95f, 1.1f, 0.3f));
        }
        if (st == 19 && heldBoulder != null) {
            Location from = heldBoulder.getLocation();
            Vector lead = target.getVelocity().clone().setY(0).multiply(12);
            Location land = ground(target.getLocation().add(lead));
            if (land == null) land = target.getLocation();
            new Boulder(heldBoulder, from, land).runTaskTimer(plugin, 1, 1);
            heldBoulder = null;
            sound(from, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
            sound(from, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.6f);
        }
        if (st >= 34) go(State.IDLE);
    }

    private void dropHeld() {
        if (heldBoulder != null) heldBoulder.remove();
        heldBoulder = null;
    }

    private class Boulder extends org.bukkit.scheduler.BukkitRunnable {
        private final BlockDisplay d;
        private final Location from, to;
        private final int flight;
        private int k;

        Boulder(BlockDisplay d, Location from, Location to) {
            this.d = d;
            this.from = from;
            this.to = to;
            this.flight = (int) Math.max(14, Math.min(30, from.distance(to) * 1.1));
        }

        @Override
        public void run() {
            k++;
            if (!d.isValid() || dead) {
                d.remove();
                cancel();
                return;
            }
            if (k % 3 == 0) {
                ring(to, 3, ORANGE, 1.3f);
                ring(to, 1.5, GOLD, 1f);
            }
            double f = (double) k / flight;
            Location p = from.clone().add(to.clone().subtract(from).toVector().multiply(f)).add(0, Math.sin(Math.PI * f) * 5, 0);
            if (k % 2 == 0) {
                d.teleport(p);
                Quaternionf q = new Quaternionf().rotateXYZ(k * 0.3f, k * 0.2f, 0);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setTransformation(new Transformation(q.transform(new Vector3f(-0.7f)), q, new Vector3f(1.4f), new Quaternionf()));
            }
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p, 1, 0.1, 0.1, 0.1, 0.01);
            if (k >= flight) {
                d.remove();
                cancel();
                sound(to, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
                to.getWorld().spawnParticle(Particle.EXPLOSION, to, 3, 0.6, 0.3, 0.6, 0);
                crack(to, 1.8, 20);
                debrisBurst(to, 6, 0.5);
                for (Player pl : playersNear(64)) {
                    double dd = pl.getLocation().distance(to);
                    if (dd <= 3.2) {
                        Vector out = pl.getLocation().toVector().subtract(to.toVector()).setY(0);
                        if (out.lengthSquared() < 0.01) out = new Vector(0, 0, 0.01);
                        hurt(pl, (phase2 ? 11 : 9) * (1 - 0.4 * dd / 3.2), out.normalize().multiply(0.8).setY(0.6));
                    }
                }
            }
        }
    }

    private static final int CHARGE_TICKS = 170;

    private void charge() {
        neutral();
        Location c = rig.location();
        if (st == 1) {
            int n = Math.max(1, playersNear(40).size());
            coreNeeded = 45 + 25 * n;
            coreDamage = 0;
            coreBox = addHurtbox(rig.worldPoint(torso, 0, 3.9f, 1.15f), 0.6f);
            titleNear("<#ffc02e><b>⚠ SŁOŃCE SIĘ ŁADUJE</b>", "<white>Zbijcie świecący rdzeń na jego piersi!", 48);
            sound(c, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        }
        float k = Math.min(1, st / 20f);
        hips.offset.set(0, -1.0f * k, 0);
        legL.pose(1.25f * k, 0, 0);
        legR.pose(-0.35f * k, 0, 0);
        torso.pose(-0.15f * k, 0, 0);
        armL.pose(0, 0, -1.25f * k);
        armR.pose(0, 0, 1.25f * k);
        head.pose(-0.45f * k, 0, 0);

        double f = Math.min(1, st / (double) CHARGE_TICKS);
        core.extraScale = (float) (1 + 1.6 * f + Math.sin(st * 0.6) * 0.08);
        Location corePos = rig.worldPoint(torso, 0, 3.9f, 1.1f);
        if (st % 10 == 0) sound(c, Sound.BLOCK_BEACON_AMBIENT, 2f, (float) (0.6 + f * 1.3));
        if (st % 2 == 0) {
            double r = 5 - 3.5 * f;
            for (int i = 0; i < 3; i++) {
                double a = st * 0.25 + i * Math.PI * 2 / 3;
                c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.3 + f * 4, Math.sin(a) * r), 1,
                        0, 0, 0, 0, new Particle.DustOptions(GOLD, 1.8f));
            }
            c.getWorld().spawnParticle(Particle.END_ROD, corePos, 2, 0.3, 0.3, 0.3, 0.05);
        }
        if (st % 4 == 0) {
            int bars = 10, full = (int) Math.round(Math.min(1, coreDamage / coreNeeded) * bars);
            var msg = MM.deserialize("<#ffc02e>☀ Rdzeń: <#7ee081>" + "▮".repeat(full) + "</#7ee081><dark_gray>"
                    + "▮".repeat(bars - full) + "</dark_gray> <gray>| wybuch za <white>" + Math.max(0, (CHARGE_TICKS - st) / 20) + " s");
            for (Player p : playersNear(48)) plugin.messages().actionBar(p, SunBossy.MSG_ACTION, msg);
        }
        if (coreDamage >= coreNeeded) {
            breakCore();
            return;
        }
        if (st >= CHARGE_TICKS) sunBlast();
    }

    private void breakCore() {
        Location at = rig.worldPoint(torso, 0, 3.9f, 1.1f);
        sound(at, Sound.BLOCK_GLASS_BREAK, 2f, 0.6f);
        sound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2f, 0.7f);
        sound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.5f);
        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
        at.getWorld().spawnParticle(Particle.END_ROD, at, 40, 0.2, 0.2, 0.2, 0.25);
        core.extraScale = 0.6f;
        if (core.d != null) core.d.setBrightness(new Display.Brightness(4, 4));
        removeCoreBox();
        titleNear("<#7ee081><b>RDZEŃ PĘKŁ!</b>", "<white>Strażnik ogłuszony — bijcie go teraz!", 48);
        go(State.STUN);
    }

    private void sunBlast() {
        Location c = rig.location().add(0, 3, 0);
        removeCoreBox();
        core.extraScale = 1f;
        sound(c, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
        sound(c, Sound.ENTITY_WITHER_SPAWN, 1.5f, 1.4f);
        sound(c, Sound.ITEM_TOTEM_USE, 2f, 0.6f);
        Fx.plain(c, Particle.FLASH, 3, 0.5, 0);
        c.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, c, 2, 1, 1, 1, 0);
        c.getWorld().spawnParticle(Particle.END_ROD, c, 200, 0.5, 0.5, 0.5, 0.6);
        c.getWorld().spawnParticle(Particle.FLAME, c, 150, 0.5, 0.5, 0.5, 0.5);
        for (Player p : playersNear(15)) {
            double d = p.getLocation().distance(c);
            Vector out = p.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (out.lengthSquared() < 0.01) out = forward();
            hurt(p, 16 * (1 - 0.55 * d / 15), out.normalize().multiply(1.1).setY(0.8));
            p.setFireTicks(80);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
        }
        titleNear("<#ff8a1f><b>SŁOŃCE WYBUCHŁO</b>", "<gray>Następnym razem zbijcie rdzeń…", 48);
        go(State.IDLE);
        cooldown = 40;
    }

    private void removeCoreBox() {
        if (coreBox != null) {
            plugin.unregisterHurtbox(coreBox);
            hurtboxes.remove(coreBox);
            coreBox.remove();
            coreBox = null;
        }
    }

    private void stun() {
        neutral();
        float k = Math.min(1, st / 10f);
        torso.pose(0.65f * k, 0, 0.1f * k);
        head.pose(0.5f * k, 0, 0);
        armL.pose(0.2f, 0, -0.1f);
        armR.pose(0.2f, 0, 0.1f);
        hips.offset.set(0, -0.9f * k, 0);
        legL.pose(1.1f * k, 0, 0);
        legR.pose(-0.3f * k, 0, 0);
        if (st % 15 == 0) rig.location().getWorld().spawnParticle(Particle.CRIT, rig.worldPoint(head, 0, 6.3f, 0.1f), 10, 0.4, 0.1, 0.4, 0.1);
        if (st >= 110) {
            core.extraScale = 1f;
            if (core.d != null) core.d.setBrightness(new Display.Brightness(15, 15));
            sound(rig.location(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.5f, 0.6f);
            go(State.IDLE);
            cooldown = 20;
        }
    }

    @Override
    protected double damageMultiplier(ArmorStand hitbox) {
        if (state == State.CHARGE) return hitbox == coreBox ? 1.0 : 0.15;
        if (state == State.STUN) return 1.6;
        return 1.0;
    }

    @Override
    protected void onDamaged(Player p, double amount, ArmorStand hitbox) {
        if (state == State.CHARGE && hitbox == coreBox) {
            coreDamage += amount;
            p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.6f);
        }
    }

    @Override
    protected void hurtSound() {
        Location c = rig.location().add(0, 3, 0);
        sound(c, Sound.ENTITY_IRON_GOLEM_HURT, 1.2f, 0.5f);
        sound(c, Sound.BLOCK_STONE_HIT, 1f, 0.6f);
    }

    private void roar() {
        invulnerable = true;
        neutral();
        float k = Math.min(1, st / 12f);
        armL.pose(-0.4f * k, 0, -1.3f * k);
        armR.pose(-0.4f * k, 0, 1.3f * k);
        torso.pose(-0.3f * k, 0, 0);
        head.pose(-0.6f * k, 0, 0);
        Location c = rig.location();
        if (st == 1) {
            titleNear("<#ff8a1f><b>FAZA 2</b>", "<white>Strażnik zrzuca złotą zbroję!", 64);
            sound(c, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.45f);
            sound(c, Sound.ENTITY_IRON_GOLEM_DEATH, 2f, 0.4f);
        }
        if (st == 16) {
            sound(c, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2f, 0.5f);
            sound(c, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
            for (Rig.Part p : armor) {
                BlockDisplay d = rig.detach(p);
                if (d == null) continue;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Vector v = new Vector(r.nextDouble(-0.5, 0.5), r.nextDouble(0.35, 0.7), r.nextDouble(-0.5, 0.5));
                Debris.launch(plugin, d, v, 70);
            }
            c.getWorld().spawnParticle(Particle.FLAME, c.clone().add(0, 3.5, 0), 80, 1, 1.5, 1, 0.08);
        }
        if (st == 30) {
            phase2 = true;
            bar.color(BossBar.Color.RED);
            bar.name(MM.deserialize("<#ff8a1f><b>☀ Strażnik Słońca</b></#ff8a1f> <gray>— rozgniewany"));
            for (int i = 0; i < 3; i++) orbs.add(new Orb(i * (Math.PI * 2 / 3)));
            sound(c, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.6f);
            lastChargeT = t;
        }
        if (st >= 50) {
            invulnerable = false;
            go(State.IDLE);
            cooldown = 20;
        }
    }

    private class Orb {
        private final double phase;
        private ItemDisplay d;
        private int respawnAt;
        private int flyT = -1;
        private Location pos;
        private Vector vel;

        Orb(double phase) {
            this.phase = phase;
            respawnAt = t;
        }

        void tick() {
            if (dead) {
                gone();
                return;
            }
            if (d == null) {
                if (t < respawnAt) return;
                Location at = orbitPos();
                d = at.getWorld().spawn(at, ItemDisplay.class, e -> {
                    plugin.tag(e);
                    e.setItemStack(new ItemStack(Material.FIRE_CHARGE));
                    e.setBillboard(Display.Billboard.CENTER);
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setGlowing(true);
                    e.setGlowColorOverride(ORANGE);
                    e.setTeleportDuration(2);
                    e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.1f), new Quaternionf()));
                });
                flyT = -1;
                return;
            }
            if (flyT < 0) {
                if (t % 2 == 0) d.teleport(orbitPos());
                if (t % 4 == 0) d.getWorld().spawnParticle(Particle.FLAME, d.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                if (state != State.DEATH && state != State.ROAR && ThreadLocalRandom.current().nextInt(90) == 0) {
                    Player p = nearestPlayer(30);
                    if (p != null) {
                        pos = d.getLocation();
                        vel = p.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(pos.toVector()).normalize().multiply(0.7);
                        flyT = 0;
                        sound(pos, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
                    }
                }
                return;
            }
            flyT++;
            pos.add(vel);
            if (t % 2 == 0) d.teleport(pos);
            pos.getWorld().spawnParticle(Particle.FLAME, pos, 3, 0.1, 0.1, 0.1, 0.02);
            pos.getWorld().spawnParticle(Particle.SMOKE, pos, 1, 0.05, 0.05, 0.05, 0.01);
            for (Player p : playersNear(64)) {
                if (p.getLocation().add(0, 1, 0).distanceSquared(pos) < 1.6 * 1.6) {
                    hurt(p, 5, vel.clone().normalize().multiply(0.5).setY(0.3));
                    p.setFireTicks(60);
                    explodeOrb();
                    return;
                }
            }
            if (flyT > 34 || !pos.getBlock().isPassable()) explodeOrb();
        }

        private void explodeOrb() {
            pos.getWorld().spawnParticle(Particle.EXPLOSION, pos, 1, 0, 0, 0, 0);
            sound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
            gone();
            respawnAt = t + 100;
        }

        private Location orbitPos() {
            double a = t * 0.06 + phase;
            return rig.location().add(Math.cos(a) * 2.8, 4.2 + Math.sin(t * 0.1 + phase) * 0.3, Math.sin(a) * 2.8);
        }

        void gone() {
            if (d != null) d.remove();
            d = null;
            flyT = -1;
        }
    }

    @Override
    protected void onDeath() {
        removeCoreBox();
        dropHeld();
        orbs.forEach(Orb::gone);
        orbs.clear();
        go(State.DEATH);
        titleNear("<#ffc02e><b>STRAŻNIK UPADA</b>", "<gray>…", 64);
    }

    private void death() {
        neutral();
        Location c = rig.location();
        float k = Math.min(1, st / 40f);
        torso.pose(0.5f * k, 0, (float) Math.sin(st * 0.7) * 0.05f);
        head.pose(0.6f * k, 0, 0);
        armL.pose(0.3f * k, 0, -0.2f);
        armR.pose(0.3f * k, 0, 0.2f);
        hips.offset.set(0, -1.1f * k, 0);
        legL.pose(1.3f * k, 0, 0);
        legR.pose(1.3f * k, 0, 0);
        core.extraScale = (float) (1 + Math.sin(st * 1.3) * 0.4);
        if (core.d != null) core.d.setBrightness(new Display.Brightness(st % 4 < 2 ? 15 : 3, 15));
        if (st == 1) sound(c, Sound.ENTITY_IRON_GOLEM_DEATH, 3f, 0.4f);
        if (st % 6 == 0 && st < 60) {
            sound(c, Sound.BLOCK_STONE_BREAK, 1.5f, 0.5f + st / 120f);
            c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(0, 3, 0), 6, 0.8, 1, 0.8, 0.08);
        }
        if (st == 25) sound(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2f, 0.5f);
        if (st == 62) {
            sound(c, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
            sound(c, Sound.ENTITY_WITHER_BREAK_BLOCK, 2f, 0.6f);
            Location mid = c.clone().add(0, 3, 0);
            mid.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0);
            mid.getWorld().spawnParticle(Particle.FIREWORK, mid, 150, 0.4, 0.4, 0.4, 0.35);
            mid.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mid, 150, 0.4, 0.8, 0.4, 0.6);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Rig.Part p : new ArrayList<>(rig.parts())) {
                BlockDisplay d = rig.detach(p);
                if (d == null) continue;
                Debris.launch(plugin, d, new Vector(r.nextDouble(-0.45, 0.45), r.nextDouble(0.2, 0.75), r.nextDouble(-0.45, 0.45)), 60 + r.nextInt(40));
            }
            for (Player p : playersNear(64)) p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (st >= 70) finishDeath();
    }

    private void crack(Location at, double radius, int count) {
        BlockData data = at.clone().add(0, -0.5, 0).getBlock().getBlockData();
        if (data.getMaterial().isAir()) data = Material.STONE.createBlockData();
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.2, 0), count, radius * 0.5, 0.1, radius * 0.5, 0.2, data);
    }

    private void debrisBurst(Location at, int n, double power) {
        Material m = at.clone().add(0, -0.5, 0).getBlock().getType();
        if (m.isAir() || !m.isBlock()) m = Material.COBBLESTONE;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            Vector v = new Vector(r.nextDouble(-1, 1), 0, r.nextDouble(-1, 1)).normalize().multiply(r.nextDouble(0.15, power))
                    .setY(r.nextDouble(0.35, 0.8));
            Debris.spawn(plugin, at.clone().add(0, 0.4, 0), m, (float) r.nextDouble(0.25, 0.6), v, 40 + r.nextInt(20));
        }
    }

    @Override
    protected void cleanup() {
        dropHeld();
        orbs.forEach(Orb::gone);
        orbs.clear();
        removeCoreBox();
    }
}

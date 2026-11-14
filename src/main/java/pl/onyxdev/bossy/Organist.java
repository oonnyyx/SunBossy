package pl.onyxdev.bossy;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Vex;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Organist extends Boss {
    private enum State { PLAY, TURN, IDLE, WALK, NOTES, CHORD, DISSONANCE, CRESCENDO, STUN, RISE, SWEEP, SMASH, DEATH }

    private static final Color VIOLET = Color.fromRGB(0xB05CFF);
    private static final Color PINK = Color.fromRGB(0xFF5CD6);
    private static final int CRESCENDO_TICKS = 20 * 14;

    private final Rig.Bone hips, legL, legR, torso, head, armL, armR, foreL, foreR;
    private final Rig.Part baton, batonTip;
    private final List<Rig.Part> eyes = new ArrayList<>();

    private State state = State.PLAY;
    private int st;
    private int cooldown = 50;
    private boolean phase2, crescendo70, crescendo30;
    private Player target;
    private double stride;
    private ArmorStand main, headBox;

    private final List<Pipe> pipes = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final List<Location> marks = new ArrayList<>();
    private final Set<UUID> waveHit = new HashSet<>();
    private final List<Entity> echoes = new ArrayList<>();
    private double waveR;
    private Location waveCenter;

    public Organist(SunBossy plugin, Location home) {
        super(plugin, "organista", home, BossBar.bossBar(MM.deserialize("<#b05cff><b>♪ Organista Pustki</b>"), 1f,
                BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10));
        Material bone = Material.BONE_BLOCK, skull = Material.CALCITE, robe = Material.BLACK_CONCRETE,
                robe2 = Material.BLACK_WOOL, trim = Material.PURPLE_CONCRETE, metal = Material.POLISHED_BLACKSTONE,
                copper = Material.WAXED_OXIDIZED_COPPER, eye = Material.MAGENTA_CONCRETE;

        hips = rig.bone(null, 0, 3.2f, 0);
        legL = rig.bone(hips, -0.6f, 3.2f, 0);
        legR = rig.bone(hips, 0.6f, 3.2f, 0);
        torso = rig.bone(hips, 0, 3.4f, 0);
        head = rig.bone(torso, 0, 6.5f, 0.1f);
        armL = rig.bone(torso, -1.2f, 6.0f, 0);
        armR = rig.bone(torso, 1.2f, 6.0f, 0);
        foreL = rig.bone(armL, -1.2f, 4.05f, 0);
        foreR = rig.bone(armR, 1.2f, 4.05f, 0);

        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone leg = s < 0 ? legL : legR;
            rig.part(leg, bone, 0.6f * s, 2.3f, 0, 0.32f, 1.7f, 0.32f);
            rig.part(leg, bone, 0.6f * s, 0.75f, 0.05f, 0.28f, 1.5f, 0.28f);
            rig.part(leg, metal, 0.6f * s, 0.12f, 0.2f, 0.5f, 0.25f, 0.8f);
            rig.part(leg, robe2, 0.62f * s, 1.9f, -0.05f, 1.0f, 2.6f, 1.1f);
        }
        rig.part(hips, robe, 0, 3.15f, 0, 2.0f, 0.7f, 1.3f);
        rig.part(hips, trim, 0, 3.0f, 0.66f, 1.6f, 0.2f, 0.05f);

        rig.part(torso, skull, 0, 4.8f, -0.25f, 0.3f, 2.6f, 0.3f);
        for (int i = 0; i < 4; i++) {
            float y = 4.15f + i * 0.42f;
            rig.part(torso, bone, 0, y, 0.05f, 1.55f - i * 0.12f, 0.16f, 0.9f);
        }
        rig.part(torso, robe, 0, 4.9f, -0.45f, 2.0f, 2.9f, 0.5f);
        rig.part(torso, robe2, 0, 6.05f, -0.05f, 2.9f, 0.55f, 1.5f);
        rig.part(torso, trim, 0, 6.3f, 0.7f, 2.2f, 0.18f, 0.08f);
        for (int s = -1; s <= 1; s += 2) {
            rig.part(torso, metal, 0.7f * s, 6.55f, -0.3f, 0.18f, 0.7f, 0.18f, new Quaternionf().rotateZ(-0.35f * s));
        }

        rig.part(torso, robe2, 0, 3.6f, -0.85f, 2.4f, 5.2f, 0.18f);
        rig.part(torso, trim, 0, 1.05f, -0.86f, 2.45f, 0.25f, 0.2f);

        rig.part(head, skull, 0, 7.1f, 0.15f, 1.0f, 1.0f, 1.0f);
        rig.part(head, bone, 0, 6.5f, 0.3f, 0.75f, 0.28f, 0.6f);
        for (int s = -1; s <= 1; s += 2) {
            rig.part(head, Material.BLACK_CONCRETE, 0.24f * s, 7.15f, 0.64f, 0.3f, 0.26f, 0.04f);
            eyes.add(rig.part(head, eye, 0.24f * s, 7.15f, 0.67f, 0.14f, 0.12f, 0.03f));
        }
        rig.part(head, robe2, 0, 7.75f, 0.05f, 1.5f, 0.25f, 1.45f);
        rig.part(head, robe2, -0.72f, 7.05f, 0.05f, 0.2f, 1.4f, 1.4f);
        rig.part(head, robe2, 0.72f, 7.05f, 0.05f, 0.2f, 1.4f, 1.4f);
        rig.part(head, robe2, 0, 7.05f, -0.62f, 1.5f, 1.4f, 0.2f);
        float[] crownH = {0.5f, 0.75f, 1.05f, 0.75f, 0.5f};
        for (int i = 0; i < 5; i++) {
            float x = (i - 2) * 0.3f;
            rig.part(head, copper, x, 7.9f + crownH[i] / 2, 0.1f, 0.18f, crownH[i], 0.18f);
        }

        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone arm = s < 0 ? armL : armR, fore = s < 0 ? foreL : foreR;
            rig.part(arm, bone, 1.2f * s, 5.0f, 0, 0.26f, 1.95f, 0.26f);
            rig.part(arm, robe2, 1.2f * s, 5.45f, 0, 0.75f, 1.3f, 0.75f);
            rig.part(fore, bone, 1.2f * s, 3.2f, 0, 0.24f, 1.7f, 0.24f);
            rig.part(fore, skull, 1.2f * s, 2.25f, 0.12f, 0.5f, 0.22f, 0.6f);
            for (int f = -1; f <= 1; f++) {
                rig.part(fore, skull, 1.2f * s + f * 0.16f, 1.85f, 0.32f, 0.07f, 0.6f, 0.07f, new Quaternionf().rotateX(0.35f));
            }
        }

        baton = rig.part(foreR, metal, 1.2f, 2.2f, 2.3f, 0.16f, 0.16f, 4.4f);
        batonTip = rig.part(foreR, Material.AMETHYST_BLOCK, 1.2f, 2.2f, 4.6f, 0.45f, 0.45f, 0.45f,
                new Quaternionf().rotateXYZ(0.6f, 0.6f, 0));
        baton.extraScale = 0.001f;
        batonTip.extraScale = 0.001f;
    }

    @Override
    public String displayName() {
        return "<#b05cff><b>♪ Organista Pustki</b></#b05cff>";
    }

    @Override
    public void spawnAt(float yaw) {
        rig.yaw = 180;
        rig.spawn(home.clone());
        for (Rig.Part p : eyes) if (p.d != null) p.d.setBrightness(new Display.Brightness(15, 15));
        if (batonTip.d != null) batonTip.d.setBrightness(new Display.Brightness(15, 15));
        main = addHurtbox(home.clone(), 3.1f);
        headBox = addHurtbox(home.clone().add(0, 5, 0), 1.4f);
        seatedPose(0);
        rig.apply(0);
    }

    @Override
    protected void onTick() {
        st++;
        switch (state) {
            case PLAY -> play();
            case TURN -> turn();
            case IDLE, WALK -> roam();
            case NOTES -> notesAttack();
            case CHORD -> chord();
            case DISSONANCE -> dissonance();
            case CRESCENDO -> crescendo();
            case STUN -> stun();
            case RISE -> rise();
            case SWEEP -> sweep();
            case SMASH -> smash();
            case DEATH -> death();
        }
        notes.removeIf(n -> !n.tick());
        echoes.removeIf(e -> !e.isValid());
        if (state != State.DEATH) placeHurtboxes();
        if (t % 2 == 0) rig.apply(2);
    }

    private void go(State s) {
        state = s;
        st = 0;
    }

    private Vector forward() {
        double y = Math.toRadians(rig.yaw);
        return new Vector(-Math.sin(y), 0, Math.cos(y));
    }

    private void placeHurtboxes() {
        Location c = rig.location();
        main.teleport(c.clone().add(forward().multiply(phase2 ? 0 : 0.2)));
        headBox.teleport(rig.worldPoint(head, 0, 6.6f, 0.15f));
    }

    private void lookAt(Location at, float maxDeg) {
        Vector d = at.toVector().subtract(rig.location().toVector());
        if (d.lengthSquared() < 0.01) return;
        float want = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float diff = ((want - rig.yaw) % 360 + 540) % 360 - 180;
        rig.yaw += Math.max(-maxDeg, Math.min(maxDeg, diff));
    }

    private void neutral() {
        hips.pose(0, 0, 0).offset.set(0, 0, 0);
        legL.pose(0, 0, 0);
        legR.pose(0, 0, 0);
        torso.pose(0, 0, 0);
        head.pose(0, 0, 0);
        armL.pose(0, 0, -0.08f);
        armR.pose(0, 0, 0.08f);
        foreL.pose(0, 0, 0);
        foreR.pose(0, 0, 0);
    }

    private void seatedPose(double k) {
        neutral();
        hips.offset.set(0, -1.15f, 0);
        legL.pose(-1.45f, 0, 0);
        legR.pose(-1.45f, 0, 0);
        torso.pose(0.18f, 0, 0);
        head.pose(0.15f, 0, 0);
        float a = (float) (Math.sin(t * 0.45) * 0.12 * k), b = (float) (Math.cos(t * 0.38) * 0.12 * k);
        armL.pose(-1.05f + a, 0, -0.1f);
        armR.pose(-1.05f + b, 0, 0.1f);
        foreL.pose(-0.45f - a, 0, 0);
        foreR.pose(-0.45f - b, 0, 0);
    }

    private static float pitch(int semis) {
        return (float) Math.pow(2, (semis - 12) / 12.0);
    }

    private void organChord(int root, float vol) {
        Location c = home.clone().add(0, 4, -3);
        sound(c, Sound.BLOCK_NOTE_BLOCK_FLUTE, vol, pitch(root));
        sound(c, Sound.BLOCK_NOTE_BLOCK_FLUTE, vol, pitch(root + 3));
        sound(c, Sound.BLOCK_NOTE_BLOCK_FLUTE, vol, pitch(root + 7));
        sound(c, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, vol, pitch(root - 12 + 12));
        if (root % 2 == 0) sound(c, Sound.BLOCK_NOTE_BLOCK_BELL, vol * 0.5f, pitch(root + 12 > 24 ? root : root + 12));
    }

    private static final int[] TOCCATA = {12, 10, 12, 7, 3, 7, 0, 0, 12, 10, 12, 7, 3, 7, 0, 0};

    private void play() {
        invulnerable = true;
        seatedPose(1);
        if (st % 6 == 0) {
            int n = TOCCATA[(st / 6) % TOCCATA.length];
            sound(home, Sound.BLOCK_NOTE_BLOCK_FLUTE, 2.5f, pitch(n + 6));
            sound(home, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.5f, pitch(n));
        }
        if (st % 4 == 0) home.getWorld().spawnParticle(Particle.NOTE, home.clone().add(0, 9, -4), 2, 4, 2, 0.5, 1);
        if (st == 1) titleNear("<#b05cff><b>ORGANISTA PUSTKI</b>", "<gray>gra ostatnią pieśń…", 48);
        if (st >= 90) go(State.TURN);
    }

    private void turn() {
        seatedPose(0);
        float k = Math.min(1, st / 30f);
        rig.yaw = 180 - 180 * k;
        head.pose(-0.2f, 0, 0);
        if (st == 1) {
            sound(home, Sound.ENTITY_WARDEN_ROAR, 1.6f, 1.4f);
            organChord(0, 3f);
        }
        if (st == 30) {
            organChord(-5, 3f);
            for (Player p : playersNear(40)) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, true, false, false));
        }
        if (st >= 40) {
            invulnerable = false;
            go(State.IDLE);
        }
    }

    private void roam() {
        if (checkPhase()) return;
        target = nearestPlayer(40);
        if (!phase2) {
            seatedPose(0.6);
            if (st % 10 == 0) organChord(ThreadLocalRandom.current().nextInt(0, 8), 0.8f);
        } else {
            neutral();
            torso.pose((float) (0.04 * Math.sin(t * 0.07)), 0, 0);
            armR.pose(-0.3f, 0, 0.15f);
            foreR.pose(-0.6f, 0, 0);
        }
        if (target == null) {
            state = State.IDLE;
            return;
        }
        cooldown--;
        Location c = rig.location();
        double dist = horizontal(c, target.getLocation());
        if (cooldown <= 0) {
            int r = ThreadLocalRandom.current().nextInt(100);
            if (!phase2) {
                if (r < 40) go(State.NOTES);
                else if (r < 72) go(State.CHORD);
                else go(State.DISSONANCE);
                cooldown = 45;
            } else {
                if (dist < 6.2 && r < 55) go(State.SWEEP);
                else if (dist < 13 && r < 75) go(State.SMASH);
                else if (r < 88) go(State.NOTES);
                else go(State.CHORD);
                cooldown = 30;
            }
            return;
        }
        if (!phase2) return;
        lookAt(target.getLocation(), 7);
        boolean leashed = c.distanceSquared(home) > 13 * 13 && target.getLocation().distanceSquared(home) > c.distanceSquared(home);
        if (dist > 3.5 && !leashed) {
            state = State.WALK;
            double speed = 0.12;
            if (t % 2 == 0) {
                Location next = ground(c.clone().add(forward().multiply(speed * 2)));
                if (next != null && Math.abs(next.getY() - c.getY()) <= 1.2) rig.moveTo(next, 2);
            }
            double before = Math.sin(stride);
            stride += speed * 2.0;
            float s = (float) Math.sin(stride);
            legL.pose(s * 0.5f, 0, 0);
            legR.pose(-s * 0.5f, 0, 0);
            armL.pose(-s * 0.3f, 0, -0.1f);
            hips.offset.set(0, (float) (-Math.abs(Math.cos(stride)) * 0.1), 0);
            if (Math.signum(before) != Math.signum(s)) {
                sound(c, Sound.ENTITY_SKELETON_STEP, 2f, 0.4f);
                sound(c, Sound.BLOCK_BONE_BLOCK_STEP, 1.5f, 0.5f);
            }
        } else {
            state = State.IDLE;
        }
    }

    private boolean checkPhase() {
        double f = hp / maxHp;
        if (!crescendo70 && f <= 0.70) {
            crescendo70 = true;
            go(State.CRESCENDO);
            return true;
        }
        if (!phase2 && f <= 0.5) {
            go(State.RISE);
            return true;
        }
        if (phase2 && !crescendo30 && f <= 0.30) {
            crescendo30 = true;
            go(State.CRESCENDO);
            return true;
        }
        return false;
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void notesAttack() {
        if (!phase2) seatedPose(1.4);
        else {
            neutral();
            armR.pose((float) (-1.4 + Math.sin(st * 0.5) * 0.4), 0, 0.2f);
        }
        int count = phase2 ? 9 : 7;
        int every = phase2 ? 4 : 6;
        if (st % every == 0 && st / every <= count) {
            List<Player> ps = playersNear(40);
            if (ps.isEmpty()) {
                go(State.IDLE);
                return;
            }
            Player p = ps.get(ThreadLocalRandom.current().nextInt(ps.size()));
            Location from = phase2 ? rig.worldPoint(foreR, 1.2f, 2.2f, 4.6f)
                    : home.clone().add(ThreadLocalRandom.current().nextDouble(-8, 8), ThreadLocalRandom.current().nextDouble(7, 14), -4);
            notes.add(new Note(from, p));
            sound(from, Sound.BLOCK_NOTE_BLOCK_CHIME, 2f, pitch(ThreadLocalRandom.current().nextInt(0, 24)));
        }
        if (st >= count * every + 10) go(State.IDLE);
    }

    private final class Note {
        final TextDisplay d;
        Location pos;
        Vector vel;
        final Player target;
        int life;

        Note(Location from, Player target) {
            this.pos = from.clone();
            this.target = target;
            Vector to = target.getEyeLocation().toVector().subtract(from.toVector()).normalize();
            this.vel = to.multiply(0.35);
            boolean dbl = ThreadLocalRandom.current().nextBoolean();
            this.d = from.getWorld().spawn(from, TextDisplay.class, e -> {
                plugin.tag(e);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setShadowed(true);
                e.text(MM.deserialize((dbl ? "<#ff5cd6>" : "<#b05cff>") + "<b>" + (dbl ? "♫" : "♪")));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.6f), new AxisAngle4f()));
                e.setTeleportDuration(1);
            });
        }

        boolean tick() {
            life++;
            if (!d.isValid() || life > 90 || dead) {
                d.remove();
                return false;
            }
            if (target.isValid() && !target.isDead()) {
                Vector want = target.getEyeLocation().toVector().subtract(pos.toVector()).normalize().multiply(phase2 ? 0.5 : 0.42);
                vel = vel.multiply(0.88).add(want.multiply(0.12));
            }
            pos.add(vel);
            d.teleport(pos);
            if (life % 2 == 0) pos.getWorld().spawnParticle(Particle.DUST, pos, 1, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(VIOLET, 1.2f));
            if (!pos.getBlock().isPassable()) {
                pos.getWorld().spawnParticle(Particle.NOTE, pos, 4, 0.3, 0.3, 0.3, 1);
                d.remove();
                return false;
            }
            for (Player p : playersNear(48)) {
                if (p.getLocation().add(0, 1, 0).distanceSquared(pos) < 1.3) {
                    hurt(p, phase2 ? 6 : 5, vel.clone().normalize().multiply(0.4).setY(0.2));
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    pos.getWorld().spawnParticle(Particle.NOTE, pos, 8, 0.4, 0.4, 0.4, 1);
                    d.remove();
                    return false;
                }
            }
            return true;
        }
    }

    private void chord() {
        if (!phase2) {
            seatedPose(0);
            float k = Math.min(1, st / 18f);
            armL.pose(-1.05f - 1.3f * k, 0, -0.2f);
            armR.pose(-1.05f - 1.3f * k, 0, 0.2f);
        } else {
            neutral();
            float k = Math.min(1, st / 18f);
            armL.pose(-2.6f * k, 0, -0.2f);
            armR.pose(-2.6f * k, 0, 0.2f);
        }
        if (st == 1) {
            waveCenter = phase2 ? rig.location() : home.clone().add(0, 0, 2);
            for (Player p : playersNear(40)) plugin.messages().actionBar(p, SunBossy.MSG_ACTION, MM.deserialize("<#ff5cd6><b>♪ AKORD!</b> <white>Skacz nad falą!"));
            sound(waveCenter, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 1.6f);
        }
        if (st < 20 && st % 3 == 0) ring(waveCenter, 2.5, PINK, 1.5f);
        if (st == 20) {
            waveR = 0.5;
            waveHit.clear();
            organChord(-3, 4f);
            sound(waveCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
            if (!phase2) {
                armL.pose(-0.9f, 0, -0.1f);
                armR.pose(-0.9f, 0, 0.1f);
            }
        }
        if (st >= 20) {
            waveR += 0.55;
            ring(waveCenter, waveR, VIOLET, 2.0f);
            if (st % 2 == 0) ring(waveCenter, waveR - 0.3, PINK, 1.2f);
            for (Player p : playersNear(40)) {
                if (waveHit.contains(p.getUniqueId())) continue;
                double d = horizontal(p.getLocation(), waveCenter);
                if (d > waveR - 0.6 && d < waveR + 0.4) {
                    boolean airborne = !p.isOnGround() && p.getLocation().getY() - waveCenter.getY() > 0.45;
                    waveHit.add(p.getUniqueId());
                    if (!airborne) {
                        Vector out = p.getLocation().toVector().subtract(waveCenter.toVector()).setY(0);
                        if (out.lengthSquared() > 0.01) out.normalize();
                        hurt(p, 7, out.multiply(0.6).setY(0.55));
                    }
                }
            }
            if (waveR > 24) go(State.IDLE);
        }
    }

    private void dissonance() {
        if (!phase2) seatedPose(1.6);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (st == 1) {
            marks.clear();
            for (Player p : playersNear(40)) marks.add(p.getLocation().clone());
            int extra = phase2 ? 6 : 4;
            for (int i = 0; i < extra; i++) {
                Location l = home.clone().add(r.nextDouble(-6, 6), 0, r.nextDouble(2, 22));
                Location g = ground(l);
                if (g != null) marks.add(g);
            }
            sound(home, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2f, 0.8f);
        }
        if (st < 30 && st % 3 == 0) {
            for (Location m : marks) ring(m, 2.2 - st / 30.0, PINK, 1.4f);
        }
        if (st == 30) {
            for (Location m : marks) {
                m.getWorld().spawnParticle(Particle.SONIC_BOOM, m.clone().add(0, 1, 0), 1);
                m.getWorld().spawnParticle(Particle.DUST, m.clone().add(0, 1.5, 0), 25, 0.5, 1.2, 0.5, 0, new Particle.DustOptions(VIOLET, 2f));
                sound(m, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.2f);
                for (Player p : playersNear(48)) {
                    if (p.getLocation().distanceSquared(m) < 2.3 * 2.3) hurt(p, 9, new Vector(0, 0.7, 0));
                }
            }
        }
        if (st >= 40) go(State.IDLE);
    }

    private final class Pipe {
        final BlockDisplay d;
        final ArmorStand box;
        double hp;
        boolean broken;

        Pipe(Location at, double hp) {
            this.hp = hp;
            BlockData data = Material.AMETHYST_BLOCK.createBlockData();
            this.d = at.getWorld().spawn(at, BlockDisplay.class, e -> {
                plugin.tag(e);
                e.setBlock(data);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setGlowing(true);
                e.setGlowColorOverride(PINK);
                e.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new AxisAngle4f(), new Vector3f(1f, 3.2f, 1f), new AxisAngle4f()));
            });
            this.box = addHurtbox(at, 1.6f);
        }

        void hit(double amount) {
            if (broken) return;
            hp -= amount;
            d.getWorld().playSound(d.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.5f, 0.8f + (float) ThreadLocalRandom.current().nextDouble(0.6));
            if (hp <= 0) {
                broken = true;
                Location l = d.getLocation().add(0, 1.6, 0);
                l.getWorld().spawnParticle(Particle.BLOCK, l, 40, 0.4, 1, 0.4, 0, Material.AMETHYST_BLOCK.createBlockData());
                l.getWorld().playSound(l, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 2f, 0.6f);
                l.getWorld().playSound(l, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
                remove();
            }
        }

        void remove() {
            d.remove();
            plugin.unregisterHurtbox(box);
            hurtboxes.remove(box);
            box.remove();
        }
    }

    private void crescendo() {
        if (!phase2) seatedPose(2.0);
        else {
            neutral();
            armL.pose(-2.4f, 0, -0.5f);
            armR.pose(-2.4f, 0, 0.5f);
            head.pose(-0.4f, 0, 0);
        }
        if (st == 1) {
            int n = Math.max(1, playersNear(40).size());
            double each = 18 + 8 * n;
            pipes.clear();
            double[][] spots = phase2
                    ? new double[][]{{-6, 5}, {6, 5}, {-6, 15}, {6, 15}}
                    : new double[][]{{-7, 1}, {7, 1}, {-5, 9}, {5, 9}};
            for (double[] s : spots) {
                Location l = ground(home.clone().add(s[0], 0, s[1]));
                if (l == null) l = home.clone().add(s[0], 0, s[1]);
                pipes.add(new Pipe(l, each));
            }
            titleNear("<#ff5cd6><b>♫ CRESCENDO ♫</b>", "<white>Rozbijcie 4 rezonujące piszczałki, zanim muzyka eksploduje!", 48);
            sound(home, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
        }
        if (st % 5 == 0) organChord((st / 5) % 12, 1.5f + st / (float) CRESCENDO_TICKS * 2);
        if (st % 3 == 0) {
            for (Pipe p : pipes) {
                if (p.broken) continue;
                Location top = p.d.getLocation().add(0, 3.2, 0);
                top.getWorld().spawnParticle(Particle.NOTE, top, 1, 0.3, 0.3, 0.3, 1);
                Location body = rig.location().add(0, 5, 0);
                Vector dir = body.toVector().subtract(top.toVector());
                for (double k = 0; k < 1; k += 0.15) {
                    top.getWorld().spawnParticle(Particle.DUST, top.clone().add(dir.clone().multiply(k)), 1, 0, 0, 0, 0, new Particle.DustOptions(PINK, 0.9f));
                }
            }
        }
        long left = pipes.stream().filter(p -> !p.broken).count();
        if (st % 4 == 0) {
            var msg = MM.deserialize("<#ff5cd6>♫ Piszczałki: <white>" + left + "/4</white> <gray>| wybuch za <white>"
                    + Math.max(0, (CRESCENDO_TICKS - st) / 20) + " s");
            for (Player p : playersNear(48)) plugin.messages().actionBar(p, SunBossy.MSG_ACTION, msg);
        }
        if (left == 0) {
            titleNear("<#7ee081><b>CISZA!</b>", "<white>Organista ogłuszony — bijcie go teraz!", 48);
            sound(home, Sound.BLOCK_BELL_RESONATE, 2f, 0.6f);
            go(State.STUN);
            return;
        }
        if (st >= CRESCENDO_TICKS) {
            for (Pipe p : pipes) if (!p.broken) p.remove();
            pipes.clear();
            sound(home, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
            sound(home, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.5f);
            home.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, home.clone().add(0, 4, 4), 2, 3, 1, 3, 0);
            for (Player p : playersNear(40)) {
                Vector out = p.getLocation().toVector().subtract(home.toVector()).setY(0);
                if (out.lengthSquared() > 0.01) out.normalize();
                hurt(p, 14, out.multiply(1.1).setY(0.6));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false, false));
            }
            go(State.IDLE);
        }
    }

    private void stun() {
        if (!phase2) {
            seatedPose(0);
            torso.pose(0.75f, 0, 0);
            head.pose(0.6f, 0, 0.2f);
            armL.pose(-0.4f, 0, -0.3f);
            armR.pose(-0.4f, 0, 0.3f);
        } else {
            neutral();
            hips.offset.set(0, -1.2f, 0);
            legL.pose(-1.2f, 0, 0);
            legR.pose(-0.4f, 0, 0);
            torso.pose(0.6f, 0, 0);
            head.pose(0.5f, 0, 0);
        }
        if (st == 1) {
            for (Rig.Part p : eyes) if (p.d != null) p.d.setBrightness(new Display.Brightness(2, 2));
            sound(rig.location(), Sound.ENTITY_SKELETON_HURT, 2f, 0.4f);
        }
        if (st % 10 == 0) rig.location().getWorld().spawnParticle(Particle.NOTE, rig.location().add(0, 7, 0), 3, 0.5, 0.3, 0.5, 1);
        if (st >= 20 * 7) {
            for (Rig.Part p : eyes) if (p.d != null) p.d.setBrightness(new Display.Brightness(15, 15));
            go(State.IDLE);
        }
    }

    private void rise() {
        invulnerable = true;
        float k = Math.min(1, st / 40f);
        neutral();
        hips.offset.set(0, -1.15f * (1 - k), 0);
        legL.pose(-1.45f * (1 - k), 0, 0);
        legR.pose(-1.45f * (1 - k), 0, 0);
        armL.pose(-2.5f * k, 0, -0.6f * k);
        armR.pose(-2.5f * k, 0, 0.6f * k);
        head.pose(-0.5f * k, 0, 0);
        if (st == 1) {
            titleNear("<#b05cff><b>FINAŁ</b>", "<gray>Organista wstaje od organów…", 48);
            sound(home, Sound.ENTITY_WITHER_SPAWN, 1.5f, 1.4f);
            organChord(-12 + 12, 4f);
        }
        if (st == 30) {
            baton.extraScale = 1f;
            batonTip.extraScale = 1f;
            sound(home, Sound.ITEM_TRIDENT_RETURN, 2f, 0.5f);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                Location l = home.clone().add(r.nextDouble(-5, 5), 4, r.nextDouble(3, 10));
                Vex v = l.getWorld().spawn(l, Vex.class, e -> {
                    e.customName(MM.deserialize("<#b05cff>Echo"));
                    e.setLimitedLifetime(true);
                    e.setLimitedLifetimeTicks(20 * 25);
                    e.setPersistent(false);
                });
                echoes.add(v);
            }
        }
        if (st == 45) {
            sound(home, Sound.ENTITY_WARDEN_ROAR, 2f, 1.2f);
            home.getWorld().spawnParticle(Particle.SONIC_BOOM, rig.location().add(0, 4, 0), 1);
        }
        if (st >= 55) {
            phase2 = true;
            invulnerable = false;
            cooldown = 30;
            go(State.IDLE);
        }
    }

    private void sweep() {
        neutral();
        Location c = rig.location();
        if (st <= 14) {
            float k = st / 14f;
            torso.pose(0, 0.7f * k, 0);
            armR.pose(-1.3f * k, 0, 1.2f * k);
            if (st % 3 == 0) arc(c, 6.5, PINK);
            if (st == 1) sound(c, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.5f, 0.6f);
        } else if (st <= 20) {
            float k = (st - 14) / 6f;
            torso.pose(0, 0.7f - 1.4f * k, 0);
            armR.pose(-1.3f, 0, 1.2f - 2.4f * k);
            if (st == 16) {
                sound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.5f);
                Vector f = forward();
                for (Player p : playersNear(7)) {
                    Vector to = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                    if (to.length() > 6.5 || to.clone().normalize().dot(f) < -0.2) continue;
                    hurt(p, 9, to.normalize().multiply(1.2).setY(0.45));
                }
                c.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(f.clone().multiply(2.5)).add(0, 2, 0), 6, 2, 0.4, 2, 0);
            }
        } else if (st >= 30) {
            go(State.IDLE);
        }
    }

    private void arc(Location c, double r, Color color) {
        double base = Math.toRadians(rig.yaw);
        for (double a = -1.8; a <= 1.8; a += 0.15) {
            double ang = base + a;
            Location l = c.clone().add(-Math.sin(ang) * r, 0.2, Math.cos(ang) * r);
            c.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.3f));
        }
    }

    private void smash() {
        neutral();
        Location c = rig.location();
        Vector f = forward();
        if (st <= 20) {
            float k = st / 20f;
            armR.pose(-3.0f * k, 0, 0.1f);
            armL.pose(-2.6f * k, 0, -0.1f);
            torso.pose(-0.25f * k, 0, 0);
            if (st % 2 == 0) {
                for (double d = 1; d <= 11; d += 0.5) {
                    Location l = c.clone().add(f.clone().multiply(d)).add(0, 0.2, 0);
                    c.getWorld().spawnParticle(Particle.DUST, l, 1, 0.25, 0, 0.25, 0, new Particle.DustOptions(VIOLET, 1.3f));
                }
            }
            if (st == 1) sound(c, Sound.BLOCK_BELL_USE, 2f, 0.5f);
        } else if (st <= 26) {
            float k = (st - 20) / 6f;
            armR.pose(-3.0f + 2.8f * k, 0, 0.1f);
            armL.pose(-2.6f + 2.4f * k, 0, -0.1f);
            torso.pose(-0.25f + 0.65f * k, 0, 0);
            if (st == 24) {
                sound(c, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.6f);
                sound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.9f);
                BlockData under = c.clone().add(0, -0.5, 0).getBlock().getBlockData();
                if (under.getMaterial().isAir()) under = Material.DEEPSLATE_TILES.createBlockData();
                for (double d = 1; d <= 11; d += 0.8) {
                    Location l = c.clone().add(f.clone().multiply(d));
                    c.getWorld().spawnParticle(Particle.BLOCK, l.clone().add(0, 0.3, 0), 10, 0.4, 0.2, 0.4, 0.1, under);
                    c.getWorld().spawnParticle(Particle.DUST, l.clone().add(0, 0.6, 0), 3, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(PINK, 1.6f));
                }
                for (Player p : playersNear(13)) {
                    Vector to = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                    double along = to.dot(f);
                    double side = to.clone().subtract(f.clone().multiply(along)).length();
                    if (along > 0 && along < 11.5 && side < 1.6) hurt(p, 11, new Vector(0, 0.8, 0));
                }
            }
        } else if (st >= 36) {
            go(State.IDLE);
        }
    }

    @Override
    protected double damageMultiplier(ArmorStand hitbox) {
        for (Pipe p : pipes) if (p.box == hitbox) return 1.0;
        if (state == State.CRESCENDO) return 0.15;
        if (state == State.STUN) return 1.6;
        return hitbox == headBox ? 1.15 : 1.0;
    }

    @Override
    protected void onDamaged(Player p, double amount, ArmorStand hitbox) {
        for (Pipe pipe : pipes) {
            if (pipe.box == hitbox) {
                hp = Math.min(maxHp, hp + amount);
                pipe.hit(amount);
                return;
            }
        }
    }

    @Override
    protected void hurtSound() {
        Location c = rig.location().add(0, 5, 0);
        sound(c, Sound.ENTITY_SKELETON_HURT, 1.5f, 0.5f);
        sound(c, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, pitch(ThreadLocalRandom.current().nextInt(0, 6)));
    }

    @Override
    protected void onDeath() {
        go(State.DEATH);
    }

    private void death() {
        Location c = rig.location();
        float k = Math.min(1, st / 40f);
        neutral();
        hips.offset.set(0, -1.6f * k, 0);
        legL.pose(-1.4f * k, 0, 0);
        legR.pose(-1.4f * k, 0, 0);
        torso.pose(0.9f * k, 0, (float) Math.sin(st * 0.6) * 0.04f);
        head.pose(0.7f * k, 0, 0.3f * k);
        armL.pose(-1.0f * k, 0, -0.5f);
        armR.pose(-1.0f * k, 0, 0.5f);
        if (st == 1) {
            for (Pipe p : pipes) if (!p.broken) p.remove();
            pipes.clear();
            notes.forEach(n -> n.d.remove());
            notes.clear();
            sound(c, Sound.ENTITY_SKELETON_DEATH, 3f, 0.4f);
        }
        if (st % 5 == 0 && st < 45) {
            int root = 12 - st / 5 * 2;
            organChord(root, 2.5f);
            sound(c, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 2f, pitch(Math.max(0, root - 6)));
        }
        if (st == 45) {
            for (Rig.Part p : eyes) if (p.d != null) p.d.setBrightness(new Display.Brightness(0, 0));
            sound(c, Sound.BLOCK_BELL_RESONATE, 3f, 0.4f);
        }
        if (st == 60) {
            sound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
            Location mid = c.clone().add(0, 4, 0);
            World w = mid.getWorld();
            w.spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1);
            w.spawnParticle(Particle.NOTE, mid, 80, 2.5, 2.5, 2.5, 1);
            w.spawnParticle(Particle.DUST, mid, 120, 2, 2.5, 2, 0, new Particle.DustOptions(VIOLET, 2.2f));
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Rig.Part p : new ArrayList<>(rig.parts())) {
                BlockDisplay d = rig.detach(p);
                if (d == null) continue;
                Debris.launch(plugin, d, new Vector(r.nextDouble(-0.4, 0.4), r.nextDouble(0.15, 0.6), r.nextDouble(-0.4, 0.4)), 60 + r.nextInt(40));
            }
            for (Player p : playersNear(64)) p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (st >= 70) finishDeath();
    }

    @Override
    protected void cleanup() {
        for (Pipe p : pipes) if (!p.broken) p.remove();
        pipes.clear();
        notes.forEach(n -> n.d.remove());
        notes.clear();
        for (Entity e : echoes) e.remove();
        echoes.clear();
    }
}

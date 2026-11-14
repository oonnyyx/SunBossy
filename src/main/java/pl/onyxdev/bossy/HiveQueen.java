package pl.onyxdev.bossy;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;
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

public class HiveQueen extends Boss {
    private enum State { RISE, HOVER, SWARM, EGGS, HONEY, DIVE, GROUNDED, COLLAPSE, STINGS, DEATH }

    private static final Color HONEY = Color.fromRGB(0xFFB21A);
    private static final Color AMBER = Color.fromRGB(0xFF7A00);
    private static final Color VENOM = Color.fromRGB(0x9BE600);
    private static final float SCALE = 1.6f;
    private static final int MAX_BEES = 8;
    private static final int EGG_TICKS = 20 * 15;

    private final Rig.Bone thorax, head, abdomen, wingL, wingR;
    private final List<Rig.Part> eyes = new ArrayList<>();

    private State state = State.RISE;
    private int st;
    private int cooldown = 40;
    private boolean phase2;
    private ArmorStand bodyBox, tailBox;
    private Player target;

    private final List<BlockDisplay> hive = new ArrayList<>();
    private final List<Bee> bees = new ArrayList<>();
    private final List<Egg> eggs = new ArrayList<>();
    private final List<Puddle> puddles = new ArrayList<>();
    private final List<Glob> globs = new ArrayList<>();
    private final List<Sting> stings = new ArrayList<>();

    private double hoverAngle;
    private Location hiveBase;
    private Location diveFrom, diveTo;

    public HiveQueen(SunBossy plugin, Location home) {
        super(plugin, "krolowa", home, BossBar.bossBar(MM.deserialize("<#ffb21a><b>♛ Królowa Roju</b>"), 1f,
                BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10));
        Material fuzz = Material.ORANGE_WOOL, yellow = Material.YELLOW_CONCRETE, black = Material.BLACK_CONCRETE,
                brown = Material.BROWN_CONCRETE, wing = Material.WHITE_STAINED_GLASS;

        thorax = rig.bone(null, 0, 1.6f, 0);
        head = rig.bone(thorax, 0, 1.85f, 0.75f);
        abdomen = rig.bone(thorax, 0, 1.5f, -0.6f);
        wingL = rig.bone(thorax, -0.45f, 2.2f, 0.1f);
        wingR = rig.bone(thorax, 0.45f, 2.2f, 0.1f);

        rig.part(thorax, fuzz, 0, 1.6f, 0.05f, 1.3f, 1.2f, 1.35f);
        rig.part(thorax, brown, 0, 1.0f, 0.05f, 0.9f, 0.2f, 1.0f);
        for (int s = -1; s <= 1; s += 2) {
            for (int i = -1; i <= 1; i++) {
                rig.part(thorax, black, 0.6f * s, 0.75f, 0.35f * i, 0.12f, 0.75f, 0.12f,
                        new Quaternionf().rotateZ(0.5f * s));
            }
        }

        rig.part(abdomen, yellow, 0, 1.45f, -1.15f, 1.5f, 1.4f, 1.05f);
        rig.part(abdomen, black, 0, 1.4f, -1.95f, 1.38f, 1.28f, 0.6f);
        rig.part(abdomen, yellow, 0, 1.35f, -2.5f, 1.15f, 1.05f, 0.6f);
        rig.part(abdomen, black, 0, 1.3f, -3.0f, 0.85f, 0.8f, 0.45f);
        rig.part(abdomen, Material.IRON_BLOCK, 0, 1.25f, -3.5f, 0.14f, 0.14f, 0.6f);

        rig.part(head, brown, 0, 1.9f, 1.25f, 1.0f, 0.9f, 0.8f);
        for (int s = -1; s <= 1; s += 2) {
            eyes.add(rig.part(head, Material.CRYING_OBSIDIAN, 0.42f * s, 1.95f, 1.4f, 0.24f, 0.55f, 0.5f));
            rig.part(head, black, 0.2f * s, 2.6f, 1.55f, 0.08f, 0.65f, 0.08f, new Quaternionf().rotateX(0.5f).rotateZ(-0.25f * s));
        }
        rig.part(head, Material.GOLD_BLOCK, 0, 2.42f, 1.2f, 0.82f, 0.14f, 0.62f);
        Quaternionf d45 = new Quaternionf().rotateY((float) Math.PI / 4);
        for (int i = -1; i <= 1; i++) {
            rig.part(head, Material.GOLD_BLOCK, 0.28f * i, 2.62f + (i == 0 ? 0.08f : 0), 1.2f, 0.16f, i == 0 ? 0.42f : 0.3f, 0.16f, d45);
        }
        rig.part(wingL, wing, -1.35f, 2.25f, -0.25f, 1.9f, 0.05f, 1.15f);
        rig.part(wingR, wing, 1.35f, 2.25f, -0.25f, 1.9f, 0.05f, 1.15f);
    }

    @Override
    public String displayName() {
        return "<#ffb21a><b>♛ Królowa Roju</b></#ffb21a>";
    }

    @Override
    public void spawnAt(float yaw) {
        rig.yaw = yaw;
        rig.scale = SCALE;
        buildHive();
        rig.spawn(hivePoint().add(0, 2, 0));
        for (Rig.Part p : eyes) {
            if (p.d == null) continue;
            p.d.setBrightness(new Display.Brightness(15, 15));
        }
        bodyBox = addHurtbox(home.clone().add(0, -20, 0), 1.9f);
        tailBox = addHurtbox(home.clone().add(0, -20, 0), 1.5f);
    }

    private Location hivePoint() {
        if (hiveBase == null) {
            double y = Math.toRadians(rig.yaw);
            Location l = home.clone().add(Math.sin(y) * 7, 0, -Math.cos(y) * 7);
            Location g = groundBelow(l, 4, 8);
            hiveBase = g == null ? l : g;
        }
        return hiveBase.clone();
    }

    private void buildHive() {
        Location base = hivePoint();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int y = 0; y < 7; y++) {
            double radius = y < 4 ? 2.6 + (y == 1 || y == 2 ? 0.4 : 0) : 2.6 - (y - 3) * 0.7;
            int n = (int) Math.max(6, radius * 5);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                Material m = r.nextInt(5) == 0 ? Material.BEE_NEST : r.nextInt(3) == 0 ? Material.HONEY_BLOCK : Material.HONEYCOMB_BLOCK;
                Location l = base.clone().add(Math.cos(a) * radius, y * 0.9, Math.sin(a) * radius);
                hive.add(hiveBlock(l, m, 1.05f));
            }
        }
        hive.add(hiveBlock(base.clone().add(0, 6.3, 0), Material.HONEYCOMB_BLOCK, 1.6f));
    }

    private BlockDisplay hiveBlock(Location l, Material m, float size) {
        return l.getWorld().spawn(l, BlockDisplay.class, d -> {
            plugin.tag(d);
            d.setBlock(m.createBlockData());
            d.setViewRange(2f);
            d.setTransformation(new Transformation(new Vector3f(-size / 2), new Quaternionf(), new Vector3f(size), new Quaternionf()));
        });
    }

    @Override
    protected void onTick() {
        st++;
        switch (state) {
            case RISE -> rise();
            case HOVER -> hover();
            case SWARM -> swarm();
            case EGGS -> layEggs();
            case HONEY -> honey();
            case DIVE -> dive();
            case GROUNDED -> grounded();
            case COLLAPSE -> collapse();
            case STINGS -> stingRain();
            case DEATH -> death();
        }
        bees.removeIf(b -> !b.isValid() || b.isDead());
        eggs.removeIf(Egg::tick);
        puddles.removeIf(Puddle::tick);
        globs.removeIf(Glob::tick);
        stings.removeIf(Sting::tick);
        if (state != State.RISE && state != State.DEATH) placeHurtboxes();
        if (state != State.DEATH) flap();
        if (t % 2 == 0) rig.apply(2);
        if (state != State.DEATH && t % 40 == 0) sound(rig.location(), Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.6f, 0.5f);
    }

    private void go(State s) {
        state = s;
        st = 0;
    }

    private void flap() {
        float a = (t % 2 == 0) ? 0.55f : -0.35f;
        wingL.pose(0, 0, a);
        wingR.pose(0, 0, -a);
    }

    private void neutral() {
        thorax.pose(0, 0, 0).offset.set(0, 0, 0);
        head.pose(0, 0, 0);
        abdomen.pose(0.15f + (float) Math.sin(t * 0.12) * 0.06f, 0, 0);
    }

    private Vector forward() {
        double y = Math.toRadians(rig.yaw);
        return new Vector(-Math.sin(y), 0, Math.cos(y));
    }

    private void placeHurtboxes() {
        Location c = rig.location();
        bodyBox.teleport(c.clone().add(0, 0.6, 0));
        tailBox.teleport(c.clone().add(forward().multiply(-2.8)).add(0, 0.6, 0));
    }

    private void lookAt(Location at, float maxDeg) {
        Vector d = at.toVector().subtract(rig.location().toVector());
        if (d.lengthSquared() < 0.01) return;
        float want = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float diff = ((want - rig.yaw) % 360 + 540) % 360 - 180;
        rig.yaw += Math.max(-maxDeg, Math.min(maxDeg, diff));
    }

    private int cd(int ticks) {
        return phase2 ? (int) (ticks * 0.7) : ticks;
    }

    private double hoverHeight() {
        return phase2 ? 4.5 : 1.3;
    }

    private void rise() {
        invulnerable = true;
        neutral();
        Location from = hivePoint().add(0, 2, 0);
        Location to = home.clone().add(0, hoverHeight(), 0);
        if (st == 1) {
            titleNear("<#ffb21a><b>KRÓLOWA ROJU</b>", "<gray>ul zaczyna brzęczeć…", 64);
            sound(from, Sound.BLOCK_BEEHIVE_WORK, 2f, 0.5f);
            sound(from, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 2f, 0.6f);
        }
        if (st < 40) {
            if (st % 4 == 0) from.getWorld().spawnParticle(Particle.FALLING_HONEY, from.clone().add(0, 2, 0), 8, 2, 2, 2, 0);
            if (st % 8 == 0) sound(from, Sound.BLOCK_BEEHIVE_DRIP, 1.5f, 0.7f);
        }
        if (st == 40) sound(from, Sound.BLOCK_BEEHIVE_EXIT, 2f, 0.5f);
        if (st >= 40 && st <= 70) {
            double f = (st - 40) / 30.0;
            Location p = from.clone().add(to.clone().subtract(from).toVector().multiply(f)).add(0, Math.sin(Math.PI * f) * 2, 0);
            rig.moveTo(p, 1);
            lookAt(to.clone().add(forward().multiply(5)), 10);
        }
        if (st == 72) {
            sound(to, Sound.ENTITY_BEE_STING, 2f, 0.5f);
            sound(to, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.6f);
            for (int i = 0; i < 3; i++) spawnBee(to);
        }
        if (st >= 80) {
            invulnerable = false;
            go(State.HOVER);
        }
    }

    private void hover() {
        if (!phase2 && hp / maxHp <= 0.4) {
            go(State.COLLAPSE);
            return;
        }
        neutral();
        target = nearestPlayer(40);
        hoverAngle += phase2 ? 0.02 : 0.01;
        double r = phase2 ? 6 : 3;
        Location want = home.clone().add(Math.cos(hoverAngle) * r, hoverHeight() + Math.sin(t * 0.08) * 0.35, Math.sin(hoverAngle) * r);
        if (t % 2 == 0) rig.moveTo(want, 2);
        if (target != null) lookAt(target.getLocation(), 6);
        if (target == null) return;
        if (--cooldown > 0) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int roll = rnd.nextInt(100);
        if (bees.size() < 3 && roll < 25) {
            go(State.SWARM);
            cooldown = cd(60);
        } else if (eggs.isEmpty() && roll < 45) {
            go(State.EGGS);
            cooldown = cd(70);
        } else if (roll < 70) {
            go(State.HONEY);
            cooldown = cd(55);
        } else if (phase2 && roll < 82) {
            go(State.STINGS);
            cooldown = cd(60);
        } else {
            go(State.DIVE);
            cooldown = cd(50);
        }
    }

    private void swarm() {
        neutral();
        abdomen.pose(0.6f, 0, 0);
        Location c = rig.location();
        if (st == 1) {
            sound(c, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 2.5f, 0.8f);
            titleNear("", "<#ffb21a>🐝 Królowa wzywa rój!", 40);
        }
        int n = Math.min(MAX_BEES - bees.size(), 2 + playersNear(36).size());
        if (st >= 8 && st < 8 + n * 4 && (st - 8) % 4 == 0) spawnBee(rig.worldPoint(abdomen, 0, 1.3f, -3.2f));
        if (st >= 30) go(State.HOVER);
    }

    private void spawnBee(Location at) {
        if (bees.size() >= MAX_BEES) return;
        Bee b = at.getWorld().spawn(at, Bee.class, e -> {
            plugin.tag(e);
            e.customName(MM.deserialize("<#ffb21a>Osa z roju"));
            e.setCustomNameVisible(false);
            e.setAnger(20 * 60);
            e.setRemoveWhenFarAway(true);
        });
        Player p = nearestPlayer(40);
        if (p != null) b.setTarget(p);
        at.getWorld().spawnParticle(Particle.WAX_ON, at, 4, 0.2, 0.2, 0.2, 0);
        bees.add(b);
    }

    private void layEggs() {
        neutral();
        Location c = rig.location();
        abdomen.pose(0.9f, 0, 0);
        thorax.pose(-0.25f, 0, 0);
        if (st == 1) {
            titleNear("", "<#ffb21a>🥚 Królowa składa jaja — rozbijcie je, zanim się wyklują!", 40);
            sound(c, Sound.BLOCK_SNIFFER_EGG_PLOP, 2f, 0.6f);
        }
        int n = phase2 ? 4 : 3;
        if (st >= 10 && st < 10 + n * 8 && (st - 10) % 8 == 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int tries = 0; tries < 10; tries++) {
                double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(4, 11);
                Location spot = groundBelow(home.clone().add(Math.cos(a) * d, 2, Math.sin(a) * d), 4, 8);
                if (spot == null) continue;
                eggs.add(new Egg(rig.worldPoint(abdomen, 0, 1.3f, -3.2f), spot));
                sound(c, Sound.BLOCK_SNIFFER_EGG_PLOP, 1.5f, 1.0f);
                break;
            }
        }
        if (st >= 20 + n * 8) go(State.HOVER);
    }

    private class Egg {
        private final BlockDisplay d;
        private final ArmorStand box;
        private final Location spot;
        private final double needed;
        private double taken;
        private int k;
        private boolean done;

        Egg(Location from, Location spot) {
            this.spot = spot;
            this.needed = 18 + 7 * Math.max(1, playersNear(36).size());
            d = spot.getWorld().spawn(spot, BlockDisplay.class, e -> {
                plugin.tag(e);
                e.setBlock(Material.SNIFFER_EGG.createBlockData());
                e.setGlowing(true);
                e.setGlowColorOverride(HONEY);
                e.setTransformation(new Transformation(new Vector3f(-0.6f, 0, -0.6f), new Quaternionf(), new Vector3f(1.2f), new Quaternionf()));
            });
            box = addHurtbox(spot.clone(), 0.75f);
            from.getWorld().spawnParticle(Particle.FALLING_HONEY, from, 6, 0.2, 0.2, 0.2, 0);
        }

        void hit(double raw) {
            if (done) return;
            taken += raw;
            spot.getWorld().spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.6, 0), 6, 0.3, 0.3, 0.3, 0, Material.SNIFFER_EGG.createBlockData());
            if (taken >= needed) {
                sound(spot, Sound.BLOCK_SNIFFER_EGG_CRACK, 1.5f, 0.8f);
                sound(spot, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.5f, 0.6f);
                spot.getWorld().spawnParticle(Particle.FALLING_HONEY, spot.clone().add(0, 0.6, 0), 15, 0.4, 0.3, 0.4, 0);
                done = true;
            }
        }

        boolean tick() {
            k++;
            if (dead) {
                remove();
                return true;
            }
            if (done) {
                remove();
                return true;
            }
            if (k % 20 == 0) {
                int left = (EGG_TICKS - k) / 20;
                ring(spot, 1.0, left <= 4 ? Color.RED : HONEY, 1f);
                if (left <= 4) sound(spot, Sound.BLOCK_SNIFFER_EGG_CRACK, 0.8f, 1.6f);
                if (k % 40 == 0) {
                    long alive = eggs.stream().filter(e -> !e.done).count();
                    var msg = MM.deserialize("<#ffb21a>🥚 Jaja: <white>" + alive + "</white> <gray>· wyklują się za <white>" + Math.max(0, left) + " s");
                    for (Player p : playersNear(40)) plugin.messages().actionBar(p, SunBossy.MSG_ACTION, msg);
                }
            }
            if (k >= EGG_TICKS) {
                sound(spot, Sound.BLOCK_SNIFFER_EGG_HATCH, 2f, 0.8f);
                spot.getWorld().spawnParticle(Particle.EXPLOSION, spot.clone().add(0, 0.6, 0), 1, 0, 0, 0, 0);
                for (int i = 0; i < 3; i++) spawnBee(spot.clone().add(0, 1, 0));
                hp = Math.min(maxHp, hp + maxHp * 0.03);
                remove();
                return true;
            }
            return false;
        }

        void remove() {
            d.remove();
            plugin.unregisterHurtbox(box);
            hurtboxes.remove(box);
            box.remove();
        }
    }

    private void honey() {
        neutral();
        Location c = rig.location();
        abdomen.pose(0.5f, 0, 0);
        if (target != null) lookAt(target.getLocation(), 10);
        int n = phase2 ? 4 : 3;
        if (st >= 6 && st < 6 + n * 7 && (st - 6) % 7 == 0) {
            List<Player> ps = playersNear(36);
            if (!ps.isEmpty()) {
                Player p = ps.get(ThreadLocalRandom.current().nextInt(ps.size()));
                Location land = groundBelow(p.getLocation().add(p.getVelocity().clone().setY(0).multiply(8)), 3, 8);
                if (land == null) land = p.getLocation();
                globs.add(new Glob(rig.worldPoint(head, 0, 1.6f, 1.6f), land));
                sound(c, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1.5f, 0.6f);
            }
        }
        if (st >= 12 + n * 7) go(State.HOVER);
    }

    private class Glob {
        private final BlockDisplay d;
        private final Location from, to;
        private final int flight;
        private int k;

        Glob(Location from, Location to) {
            this.from = from;
            this.to = to;
            this.flight = (int) Math.max(14, Math.min(28, from.distance(to) * 1.1));
            d = from.getWorld().spawn(from, BlockDisplay.class, e -> {
                plugin.tag(e);
                e.setBlock(Material.HONEY_BLOCK.createBlockData());
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
            if (k % 3 == 0) ring(to, 2.5, AMBER, 1.2f);
            double f = (double) k / flight;
            Location p = from.clone().add(to.clone().subtract(from).toVector().multiply(f)).add(0, Math.sin(Math.PI * f) * 4, 0);
            if (k % 2 == 0) d.teleport(p);
            p.getWorld().spawnParticle(Particle.DRIPPING_HONEY, p, 1, 0.1, 0.1, 0.1, 0);
            if (k >= flight) {
                d.remove();
                sound(to, Sound.BLOCK_HONEY_BLOCK_FALL, 2f, 0.6f);
                sound(to, Sound.BLOCK_SLIME_BLOCK_FALL, 1.2f, 0.6f);
                to.getWorld().spawnParticle(Particle.LANDING_HONEY, to.clone().add(0, 0.2, 0), 30, 1.5, 0.05, 1.5, 0);
                for (Player pl : playersNear(40)) {
                    if (pl.getLocation().distanceSquared(to) < 2.2 * 2.2) hurt(pl, 4, null);
                }
                puddles.add(new Puddle(to));
                return true;
            }
            return false;
        }
    }

    private class Puddle {
        private final Location at;
        private int k;

        Puddle(Location at) {
            this.at = at;
        }

        boolean tick() {
            k++;
            if (dead || k > 160) return true;
            if (k % 4 == 0) {
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.12, 0), 10, 1.2, 0, 1.2, 0, new Particle.DustOptions(HONEY, 1.6f));
                if (k % 12 == 0) at.getWorld().spawnParticle(Particle.LANDING_HONEY, at.clone().add(0, 0.1, 0), 4, 1.2, 0, 1.2, 0);
            }
            if (k % 5 == 0) {
                for (Player p : playersNear(40)) {
                    double dx = p.getLocation().getX() - at.getX(), dz = p.getLocation().getZ() - at.getZ();
                    if (dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(p.getLocation().getY() - at.getY()) < 1.5) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 12, 3, false, false, true));
                        if (k % 20 == 0) p.playSound(p, Sound.BLOCK_HONEY_BLOCK_STEP, 1f, 0.8f);
                    }
                }
            }
            return false;
        }
    }

    private void dive() {
        neutral();
        Location c = rig.location();
        if (st == 1) {
            if (target == null) {
                go(State.HOVER);
                return;
            }
            diveFrom = c.clone();
            Location land = groundBelow(target.getLocation(), 3, 10);

            if (land == null || land.distanceSquared(home) > 24 * 24) {
                go(State.HOVER);
                return;
            }
            diveTo = land;
            sound(c, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 2f, 1.3f);
        }
        if (st <= 22) {
            lookAt(diveTo, 20);
            float k = st / 22f;
            thorax.pose(-0.3f * k, 0, 0);
            abdomen.pose(1.1f * k, 0, 0);
            if (st % 2 == 0) warnLine(c.clone().add(0, 1.5, 0), diveTo.clone().add(0, 0.5, 0));
            if (st % 3 == 0) ring(diveTo, 2.2, VENOM, 1.4f);
            return;
        }
        int k = st - 22;
        int len = 12;
        double f = Math.min(1, k / (double) len);
        Location p = diveFrom.clone().add(diveTo.clone().subtract(diveFrom).toVector().multiply(f));
        rig.moveTo(p, 1);
        thorax.pose(0.5f, 0, 0);
        abdomen.pose(1.3f, 0, 0);
        if (k == 1) sound(c, Sound.ENTITY_BEE_STING, 2f, 0.5f);
        p.getWorld().spawnParticle(Particle.DUST, p.clone().add(0, 1.5, 0), 3, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(VENOM, 1.2f));
        for (Player pl : playersNear(30)) {
            if (pl.getLocation().add(0, 1, 0).distanceSquared(p.clone().add(0, 1.2, 0)) < 2.2 * 2.2 && pl.getNoDamageTicks() == 0) {
                hurt(pl, phase2 ? 10 : 8, forward().multiply(0.9).setY(0.45));
                pl.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, false, true, true));
            }
        }
        if (k >= len) {
            sound(diveTo, Sound.ITEM_MACE_SMASH_GROUND, 1.5f, 1.2f);
            diveTo.getWorld().spawnParticle(Particle.FALLING_HONEY, diveTo.clone().add(0, 0.5, 0), 20, 1.5, 0.5, 1.5, 0);
            go(State.GROUNDED);
        }
    }

    private void grounded() {
        neutral();
        thorax.pose(0.2f, 0, 0.12f * (float) Math.sin(st * 0.3));
        head.pose(0.3f, 0, 0);
        Location c = rig.location();
        if (st == 1) {
            titleNear("", "<#7ee081>Królowa ogłuszona — bijcie!", 30);
            sound(c, Sound.ENTITY_BEE_HURT, 2f, 0.5f);
        }
        if (st % 10 == 0) c.getWorld().spawnParticle(Particle.CRIT, c.clone().add(0, 3, 0), 8, 0.6, 0.2, 0.6, 0.1);
        int window = phase2 ? 50 : 70;
        if (st > window) {
            Location up = c.clone().add(0, hoverHeight(), 0);
            rig.moveTo(up, 14);
            go(State.HOVER);
        }
    }

    private void warnLine(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double len = Math.min(30, dir.length());
        dir.normalize();
        for (double s = 0; s < len; s += 0.8) {
            from.getWorld().spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(s)), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(VENOM, 0.7f));
        }
    }

    private void collapse() {
        invulnerable = true;
        neutral();
        Location c = rig.location();
        thorax.pose(-0.4f, 0, 0);
        abdomen.pose(0.8f, 0, 0);
        if (st == 1) {
            titleNear("<#ffb21a><b>UL SIĘ WALI!</b>", "<white>Królowa wzbija się w powietrze", 64);
            sound(c, Sound.ENTITY_RAVAGER_ROAR, 2f, 1.4f);
            sound(hivePoint(), Sound.BLOCK_BEEHIVE_SHEAR, 2f, 0.5f);
        }
        if (st == 10) {
            Location base = hivePoint();
            sound(base, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f);
            sound(base, Sound.BLOCK_HONEY_BLOCK_BREAK, 2f, 0.5f);
            base.getWorld().spawnParticle(Particle.FALLING_HONEY, base.clone().add(0, 3, 0), 80, 2.5, 3, 2.5, 0);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (BlockDisplay d : hive) {
                if (!d.isValid()) continue;
                Debris.launch(plugin, d, new Vector(r.nextDouble(-0.35, 0.35), r.nextDouble(0.1, 0.5), r.nextDouble(-0.35, 0.35)), 60 + r.nextInt(50));
            }
            hive.clear();
            for (int i = 0; i < 4; i++) spawnBee(base.clone().add(0, 2, 0));
        }
        if (st >= 10 && st <= 40) rig.moveTo(home.clone().add(0, 1.3 + 3.2 * (st - 10) / 30.0, 0), 2);
        if (st == 40) {
            phase2 = true;
            bar.color(BossBar.Color.GREEN);
            bar.name(MM.deserialize("<#9be600><b>♛ Królowa Roju</b></#9be600> <gray>— wściekła"));
            for (Rig.Part p : eyes) if (p.d != null) {
                p.d.setGlowing(true);
                p.d.setGlowColorOverride(VENOM);
            }
        }
        if (st >= 50) {
            invulnerable = false;
            cooldown = 20;
            go(State.HOVER);
        }
    }

    private void stingRain() {
        neutral();
        abdomen.pose(1.0f, 0, 0);
        if (st == 1) {
            sound(rig.location(), Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 2f, 1.5f);
            titleNear("", "<#9be600>☄ Deszcz żądeł — uciekajcie z zielonych kręgów!", 40);
        }
        if (st >= 5 && st <= 45 && st % 5 == 0) {
            for (Player p : playersNear(36)) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Location at = groundBelow(p.getLocation().add(r.nextDouble(-2.5, 2.5), 0, r.nextDouble(-2.5, 2.5)), 3, 8);
                if (at != null) stings.add(new Sting(at));
            }
        }
        if (st >= 60) go(State.HOVER);
    }

    private class Sting {
        private final Location at;
        private BlockDisplay d;
        private int k;

        Sting(Location at) {
            this.at = at;
        }

        boolean tick() {
            k++;
            if (dead) {
                if (d != null) d.remove();
                return true;
            }
            if (k <= 20) {
                if (k % 3 == 0) ring(at, 1.3, VENOM, 1.1f);
                if (k == 10) {
                    d = at.getWorld().spawn(at.clone().add(0, 9, 0), BlockDisplay.class, e -> {
                        plugin.tag(e);
                        e.setBlock(Material.POINTED_DRIPSTONE.createBlockData());
                        e.setTeleportDuration(2);
                        e.setGlowing(true);
                        e.setGlowColorOverride(VENOM);
                        e.setTransformation(new Transformation(new Vector3f(-0.4f, 0, -0.4f), new Quaternionf(), new Vector3f(0.8f, 1.6f, 0.8f), new Quaternionf()));
                    });
                }
                if (d != null && k % 2 == 0) d.teleport(at.clone().add(0, 9 - 9 * (k - 10) / 10.0, 0));
                return false;
            }
            if (d != null) d.remove();
            sound(at, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1.2f, 0.8f);
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.3, 0), 12, 0.6, 0.2, 0.6, 0, new Particle.DustOptions(VENOM, 1.4f));
            for (Player p : playersNear(40)) {
                if (p.getLocation().distanceSquared(at) < 1.4 * 1.4) {
                    hurt(p, 5, null);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1, false, true, true));
                }
            }
            return true;
        }
    }

    private static final double EGG_MUL = 0.02;

    @Override
    protected double damageMultiplier(ArmorStand hitbox) {
        for (Egg e : eggs) if (e.box == hitbox) return EGG_MUL;
        if (state == State.GROUNDED) return 1.4;
        return 1;
    }

    @Override
    protected void onDamaged(Player p, double amount, ArmorStand hitbox) {
        for (Egg e : eggs) {
            if (e.box == hitbox) {
                e.hit(amount / EGG_MUL);
                return;
            }
        }
    }

    @Override
    protected void hurtSound() {
        Location c = rig.location().add(0, 2.5, 0);
        sound(c, Sound.ENTITY_BEE_HURT, 1.5f, 0.5f);
    }

    @Override
    protected void onDeath() {
        clearAdds();
        go(State.DEATH);
        titleNear("<#ffb21a><b>KRÓLOWA UPADA</b>", "<gray>rój milknie…", 64);
    }

    private void death() {
        neutral();
        Location c = rig.location();
        if (st == 1) {
            sound(c, Sound.ENTITY_BEE_DEATH, 2.5f, 0.4f);
            Location g = groundBelow(c, 2, 12);
            diveTo = g == null ? c : g;
        }
        float k = Math.min(1, st / 25f);
        thorax.pose(0, 0, 2.6f * k);
        wingL.pose(0, 0, 0.2f);
        wingR.pose(0, 0, -0.2f);
        if (st <= 25) rig.moveTo(c.clone().add(diveTo.clone().subtract(c).toVector().multiply(0.15)), 2);
        if (st % 6 == 0 && st < 45) c.getWorld().spawnParticle(Particle.FALLING_HONEY, c.clone().add(0, 2, 0), 10, 1, 1, 1, 0);
        if (st == 50) {
            sound(c, Sound.BLOCK_HONEY_BLOCK_BREAK, 2.5f, 0.5f);
            sound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1.2f);
            Location mid = c.clone().add(0, 2, 0);
            mid.getWorld().spawnParticle(Particle.FALLING_HONEY, mid, 120, 1.5, 1, 1.5, 0);
            mid.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mid, 120, 0.6, 0.8, 0.6, 0.5);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Rig.Part p : new ArrayList<>(rig.parts())) {
                BlockDisplay d = rig.detach(p);
                if (d == null) continue;
                Debris.launch(plugin, d, new Vector(r.nextDouble(-0.4, 0.4), r.nextDouble(0.1, 0.6), r.nextDouble(-0.4, 0.4)), 50 + r.nextInt(40));
            }
            for (Player p : playersNear(64)) p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (st >= 58) finishDeath();
    }

    private void clearAdds() {
        for (Bee b : bees) if (b.isValid()) b.remove();
        bees.clear();
        for (Egg e : eggs) e.remove();
        eggs.clear();
        puddles.clear();
        for (Glob g : globs) g.d.remove();
        globs.clear();
        for (Sting s : stings) if (s.d != null) s.d.remove();
        stings.clear();
    }

    @Override
    protected void cleanup() {
        clearAdds();
        for (BlockDisplay d : hive) d.remove();
        hive.clear();
    }
}

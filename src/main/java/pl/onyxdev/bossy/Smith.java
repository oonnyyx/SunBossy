package pl.onyxdev.bossy;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Smith extends Boss {
    private enum State { RISE, IDLE, WALK, HAMMER, SWORDS, FORGE, SPLIT, MINIS, REFORM, DEATH }

    private static final Color EMBER = Color.fromRGB(0xFF6A1A);
    private static final Color HOT = Color.fromRGB(0xFFC02E);
    private static final Color STORM = Color.fromRGB(0x7FD8FF);
    private static final double ARENA = 14;

    private static final double MINI_SHARE = 0.07;

    private static final double SPLIT_FLOOR = 0.15;

    private static final double MINI_MUL = 0.01;

    private static final int MINI_WINDOW = 20 * 12;

    private final Rig.Bone hips, legL, legR, torso, head, armL, armR, foreL, foreR, hammer;
    private final Rig.Part furnace;
    private final List<Rig.Part> glowing = new ArrayList<>();

    private State state = State.RISE;
    private int st;
    private int cooldown = 40;
    private double stride;
    private boolean phase2, splitDone;
    private Player target;
    private ArmorStand main, sideA, sideB;

    private final List<Sword> swords = new ArrayList<>();
    private final List<Mini> minis = new ArrayList<>();
    private int firstBrokenAt = -1;

    private final Set<Integer> burning = new HashSet<>();

    public Smith(SunBossy plugin, Location home) {
        super(plugin, "kowal", home, BossBar.bossBar(MM.deserialize("<#ff6a1a><b>⚒ Kowal Burzowej Kuźni</b>"), 1f,
                BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
        Material body = Material.POLISHED_BLACKSTONE, bricks = Material.POLISHED_BLACKSTONE_BRICKS, tiles = Material.DEEPSLATE_TILES,
                iron = Material.IRON_BLOCK, leather = Material.BROWN_TERRACOTTA, magma = Material.MAGMA_BLOCK,
                ember = Material.OCHRE_FROGLIGHT, beard = Material.GRAY_WOOL;

        hips = rig.bone(null, 0, 2.2f, 0);
        legL = rig.bone(hips, -0.7f, 2.2f, 0);
        legR = rig.bone(hips, 0.7f, 2.2f, 0);
        torso = rig.bone(hips, 0, 2.5f, 0);
        head = rig.bone(torso, 0, 4.8f, 0.2f);
        armL = rig.bone(torso, -1.9f, 4.3f, 0);
        armR = rig.bone(torso, 1.9f, 4.3f, 0);
        foreL = rig.bone(armL, -1.9f, 2.9f, 0);
        foreR = rig.bone(armR, 1.9f, 2.9f, 0);
        hammer = rig.bone(foreR, 1.9f, 1.5f, 0.2f);

        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone leg = s < 0 ? legL : legR;
            rig.part(leg, body, 0.7f * s, 1.55f, 0, 0.95f, 1.3f, 0.95f);
            rig.part(leg, tiles, 0.7f * s, 0.45f, 0.12f, 1.15f, 0.9f, 1.35f);
            rig.part(leg, iron, 0.7f * s, 0.95f, 0.66f, 0.7f, 0.25f, 0.1f);
        }
        rig.part(hips, leather, 0, 2.4f, 0, 2.15f, 0.5f, 1.3f);
        rig.part(hips, Material.GOLD_BLOCK, 0, 2.4f, 0.66f, 0.45f, 0.35f, 0.08f);

        rig.part(torso, bricks, 0, 3.7f, 0, 3.0f, 2.2f, 1.9f);
        rig.part(torso, body, 0, 4.55f, -0.2f, 2.6f, 0.8f, 1.6f);
        rig.part(torso, leather, 0, 3.0f, 0.98f, 1.9f, 1.9f, 0.12f);
        furnace = rig.part(torso, magma, 0, 3.95f, 0.99f, 0.85f, 0.6f, 0.1f);
        glowing.add(furnace);
        rig.part(torso, Material.IRON_BARS, 0, 3.95f, 1.06f, 0.95f, 0.7f, 0.08f);
        for (int s = -1; s <= 1; s += 2) {
            rig.part(torso, iron, 1.9f * s, 4.75f, 0, 1.3f, 0.35f, 1.3f);
            glowing.add(rig.part(torso, ember, 1.9f * s, 4.95f, 0, 0.5f, 0.12f, 0.5f));
        }

        rig.part(torso, Material.BRICKS, 0, 5.0f, -1.05f, 0.75f, 1.7f, 0.75f);
        Rig.Part flame = rig.part(torso, Material.CAMPFIRE, 0, 6.0f, -1.05f, 0.7f, 0.45f, 0.7f);
        glowing.add(flame);

        rig.part(head, Material.POLISHED_DEEPSLATE, 0, 5.3f, 0.25f, 1.1f, 1.0f, 1.1f);
        glowing.add(rig.part(head, Material.SHROOMLIGHT, -0.25f, 5.45f, 0.81f, 0.24f, 0.14f, 0.04f));
        glowing.add(rig.part(head, Material.SHROOMLIGHT, 0.25f, 5.45f, 0.81f, 0.24f, 0.14f, 0.04f));
        rig.part(head, beard, 0, 4.9f, 0.78f, 1.0f, 0.75f, 0.35f);
        rig.part(head, beard, 0, 4.4f, 0.86f, 0.35f, 0.55f, 0.22f);
        rig.part(head, iron, 0, 5.88f, 0.25f, 1.22f, 0.2f, 1.22f);
        rig.part(head, Material.LIGHTNING_ROD, 0, 6.3f, 0.25f, 0.6f, 0.75f, 0.6f);

        for (int s = -1; s <= 1; s += 2) {
            Rig.Bone arm = s < 0 ? armL : armR, fore = s < 0 ? foreL : foreR;
            rig.part(arm, body, 1.9f * s, 3.65f, 0, 0.9f, 1.4f, 0.9f);
            rig.part(fore, iron, 1.9f * s, 2.35f, 0, 1.05f, 1.2f, 1.05f);
            rig.part(fore, tiles, 1.9f * s, 1.5f, 0.05f, 1.15f, 0.8f, 1.15f);
        }

        rig.part(hammer, Material.STRIPPED_DARK_OAK_WOOD, 1.9f, 1.5f, 1.45f, 0.24f, 0.24f, 2.4f);
        rig.part(hammer, Material.NETHERITE_BLOCK, 1.9f, 1.5f, 2.75f, 1.0f, 1.45f, 0.85f);
        rig.part(hammer, Material.GOLD_BLOCK, 1.9f, 1.5f, 2.75f, 1.05f, 0.25f, 0.9f);
        glowing.add(rig.part(hammer, ember, 1.9f, 1.5f, 3.2f, 0.55f, 0.55f, 0.06f));
    }

    @Override
    public String displayName() {
        return "<#ff6a1a><b>⚒ Kowal Burzowej Kuźni</b></#ff6a1a>";
    }

    @Override
    public void spawnAt(float yaw) {
        rig.yaw = yaw;
        rig.spawn(home.clone().add(0, -7, 0));
        for (Rig.Part p : rig.parts()) {
            if (p.material == Material.CAMPFIRE && p.d != null) {
                Campfire c = (Campfire) Material.CAMPFIRE.createBlockData();
                c.setLit(true);
                p.d.setBlock(c);
            }
        }
        for (Rig.Part p : glowing) if (p.d != null) p.d.setBrightness(new Display.Brightness(15, 15));
        main = addHurtbox(home.clone().add(0, -20, 0), 3.2f);
        sideA = addHurtbox(home.clone().add(0, -20, 0), 2.6f);
        sideB = addHurtbox(home.clone().add(0, -20, 0), 2.6f);
    }

    @Override
    protected void onTick() {
        st++;
        switch (state) {
            case RISE -> rise();
            case IDLE, WALK -> roam();
            case HAMMER -> hammer();
            case SWORDS -> forgeSwords();
            case FORGE -> hotForge();
            case SPLIT -> split();
            case MINIS -> minisTick();
            case REFORM -> reform();
            case DEATH -> death();
        }
        swords.removeIf(Sword::tick);
        burnTick();
        if (state != State.RISE && state != State.DEATH && state != State.MINIS && state != State.SPLIT) placeHurtboxes();
        if (t % 2 == 0) rig.apply(2);
        if (t % 6 == 0 && state != State.DEATH && rig.parts().size() > 0) {
            Location chimney = rig.worldPoint(torso, 0, 6.3f, -1.05f);
            chimney.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, chimney, 1, 0.1, 0, 0.1, 0.02);
        }
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
        armL.pose(0, 0, -0.08f);
        armR.pose(-0.35f, 0, 0.08f);
        foreL.pose(0, 0, 0);
        foreR.pose(-0.2f, 0, 0);
        hammer.pose(0, 0, 0);
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
        sideA.teleport(c.clone().add(right().multiply(1.3)).add(0, 0.6, 0));
        sideB.teleport(c.clone().add(right().multiply(-1.3)).add(0, 0.6, 0));
    }

    private void hideMainHurtboxes() {
        Location under = home.clone().add(0, -25, 0);
        main.teleport(under);
        sideA.teleport(under);
        sideB.teleport(under);
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
        return phase2 ? (int) (ticks * 0.7) : ticks;
    }

    private void rise() {
        invulnerable = true;
        neutral();
        head.pose(0.3f, 0, 0);
        Location h = home;
        if (st == 1) {
            sound(h, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 2f, 0.5f);
            sound(h, Sound.ENTITY_WARDEN_EMERGE, 2f, 0.7f);
            titleNear("<#ff6a1a><b>KOWAL BURZOWEJ KUŹNI</b>", "<gray>rozpala palenisko…", 64);
        }
        if (st % 10 == 1 && st < 50) rig.moveTo(h.clone().add(0, -7 + 7 * Math.min(1, (st + 9) / 50.0), 0), 10);
        if (st % 3 == 0 && st < 50) {
            h.getWorld().spawnParticle(Particle.LAVA, h, 3, 1.5, 0.1, 1.5, 0);
            h.getWorld().spawnParticle(Particle.LARGE_SMOKE, h, 4, 1.5, 0.2, 1.5, 0.02);
        }
        if (st >= 50 && st < 70) {
            float k = Math.min(1, (st - 50) / 10f);
            armR.pose(-2.7f * k, 0, 0.1f);
            head.pose(-0.3f * k, 0, 0);
        }
        if (st == 70) {
            Location tip = rig.worldPoint(hammer, 1.9f, 1.5f, 2.75f);
            h.getWorld().strikeLightningEffect(tip);
            sound(h, Sound.BLOCK_ANVIL_LAND, 2f, 0.5f);
            sound(h, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
        }
        if (st >= 85) {
            invulnerable = false;
            go(State.IDLE);
        }
    }

    private void roam() {
        if (!splitDone && hp / maxHp <= 0.5) {
            go(State.SPLIT);
            return;
        }
        target = nearestPlayer(40);
        neutral();
        torso.pose((float) (0.03 * Math.sin(t * 0.08)), 0, 0);
        if (target == null) {
            state = State.IDLE;
            return;
        }
        Location c = rig.location();
        double dist = horizontal(c, target.getLocation());
        if (--cooldown <= 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (dist < 5) {
                go(State.HAMMER);
                cooldown = cd(30);
                return;
            }
            int roll = r.nextInt(100);
            if (roll < 45) {
                go(State.SWORDS);
                cooldown = cd(70);
                return;
            }
            if (roll < 80) {
                go(State.FORGE);
                cooldown = cd(80);
                return;
            }
        }
        lookAt(target.getLocation(), phase2 ? 8 : 5.5f);
        boolean leashed = c.distanceSquared(home) > 16 * 16 && target.getLocation().distanceSquared(home) > c.distanceSquared(home);
        if (dist > 3.5 && !leashed) {
            state = State.WALK;
            double speed = phase2 ? 0.12 : 0.085;
            if (t % 2 == 0) {
                Location next = ground(c.clone().add(forward().multiply(speed * 2)));
                if (next != null && Math.abs(next.getY() - c.getY()) <= 1.2) rig.moveTo(next, 2);
            }
            double before = Math.sin(stride);
            stride += speed * 2.4;
            float s = (float) Math.sin(stride);
            legL.pose(s * 0.5f, 0, 0);
            legR.pose(-s * 0.5f, 0, 0);
            armL.pose(-s * 0.3f, 0, -0.1f);
            hips.offset.set(0, (float) (-Math.abs(Math.cos(stride)) * 0.12), 0);
            if (Math.signum(before) != Math.signum(s)) {
                sound(c, Sound.ENTITY_IRON_GOLEM_STEP, 1.5f, 0.45f);
                sound(c, Sound.BLOCK_ANVIL_STEP, 0.6f, 0.6f);
            }
        } else {
            state = State.IDLE;
        }
    }

    private void hammer() {
        neutral();
        if (target != null && st < 14) lookAt(target.getLocation(), 10);
        if (st <= 16) {
            float k = st / 16f;
            armR.pose(-0.35f - 2.55f * k, 0, 0.1f);
            foreR.pose(-0.2f - 0.3f * k, 0, 0);
            torso.pose(-0.25f * k, 0, 0);
            hips.offset.set(0, 0.2f * k, 0);
            if (st % 4 == 0) {
                Location at = rig.location().add(forward().multiply(3.6));
                ring(at, 2.6, phase2 ? STORM : EMBER, 1.4f);
            }
            if (st == 4) sound(rig.location(), Sound.BLOCK_GRINDSTONE_USE, 1.5f, 0.5f);
        } else if (st <= 24) {
            armR.pose(-0.9f, 0, 0.05f);
            foreR.pose(-0.35f, 0, 0);
            torso.pose(0.45f, 0, 0);
            hips.offset.set(0, -0.55f, 0);
            if (st == 18) hammerImpact();
        } else {
            float k = Math.max(0, 1 - (st - 24) / 10f);
            armR.pose(-0.35f - 0.55f * k, 0, 0.08f);
            torso.pose(0.45f * k, 0, 0);
            hips.offset.set(0, -0.55f * k, 0);
        }
        if (st >= 36) go(State.IDLE);
    }

    private void hammerImpact() {
        Location c = rig.location();
        Location at = ground(c.clone().add(forward().multiply(3.6)));
        if (at == null) at = c.clone().add(forward().multiply(3.6));
        sound(at, Sound.BLOCK_ANVIL_LAND, 2f, 0.4f);
        sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.7f);
        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 2, 0.4, 0.1, 0.4, 0);
        at.getWorld().spawnParticle(Particle.LAVA, at, 12, 1, 0.2, 1, 0);
        sparks(at, 30);
        for (Player p : playersNear(30)) {
            if (horizontal(p.getLocation(), at) <= 3) {
                Vector out = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (out.lengthSquared() < 0.01) out = forward();
                hurt(p, phase2 ? 11 : 9, out.normalize().multiply(1.1).setY(0.5));
                p.setFireTicks(40);
            }
        }
        if (phase2) {
            at.getWorld().strikeLightningEffect(at);
            shockwave(at, 9);
        }
    }

    private void shockwave(Location at, double radius) {
        for (int k = 0; k < 9; k++) {
            int step = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (dead) return;
                double r = 1.5 + step * radius / 9.0;
                int n = (int) (r * 7);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(Math.cos(a) * r, 0.25, Math.sin(a) * r),
                            1, 0, 0, 0, 0, new Particle.DustTransition(STORM, Color.WHITE, 1.8f));
                }
                for (Player p : playersNear(radius + 4)) {
                    double d = horizontal(p.getLocation(), at);
                    boolean grounded = p.isOnGround() || p.getLocation().getY() - at.getY() < 0.5;
                    if (grounded && Math.abs(d - r) < 0.8) {
                        Vector out = p.getLocation().toVector().subtract(at.toVector()).setY(0);
                        if (out.lengthSquared() < 0.01) out = forward();
                        hurt(p, 6, out.normalize().multiply(0.6).setY(0.6));
                    }
                }
            }, k * 2L);
        }
    }

    private void forgeSwords() {
        neutral();
        Location c = rig.location();
        if (target != null) lookAt(target.getLocation(), 6);
        float k = Math.min(1, st / 10f);
        armL.pose(-1.2f * k, 0, -0.5f * k);
        armR.pose(-1.2f * k, 0, 0.5f * k);
        head.pose(-0.35f * k, 0, 0);
        if (st == 1) {
            sound(c, Sound.BLOCK_SMITHING_TABLE_USE, 2f, 0.6f);
            sound(c, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 2f, 0.7f);
            titleNear("", "<#ffc02e>⚔ Kowal wykuwa miecze — uważaj, polecą w ciebie!", 40);
        }
        int n = Math.min(7, 3 + playersNear(36).size() / 2 + (phase2 ? 2 : 0));
        if (st >= 6 && st <= 6 + n * 5 && (st - 6) % 5 == 0) {
            int i = (st - 6) / 5;
            double a = Math.toRadians(rig.yaw) + Math.PI * (0.2 + 0.6 * i / Math.max(1, n - 1)) + Math.PI / 2;
            Location spot = c.clone().add(Math.cos(a) * 3, 5.2 + (i % 2) * 0.6, Math.sin(a) * 3);
            sound(spot, Sound.BLOCK_ANVIL_USE, 1f, 1.4f);
            spot.getWorld().spawnParticle(Particle.LAVA, spot, 4, 0.2, 0.2, 0.2, 0);
            swords.add(new Sword(spot, 30 + (n - i) * 5));
        }
        if (st >= 20 + n * 5) go(State.IDLE);
    }

    private class Sword {
        private final ItemDisplay d;
        private Location pos;
        private Vector vel;
        private Player aim;
        private Location aimPoint;
        private int k;
        private final int launchAt;

        Sword(Location at, int launchAt) {
            this.pos = at.clone();
            this.launchAt = launchAt;
            d = at.getWorld().spawn(at, ItemDisplay.class, e -> {
                plugin.tag(e);
                e.setItemStack(new ItemStack(Material.IRON_SWORD));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setGlowing(true);
                e.setGlowColorOverride(phase2 ? STORM : EMBER);
                e.setTeleportDuration(2);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.6f), new Quaternionf()));
            });
        }

        boolean tick() {
            k++;
            if (dead || !d.isValid()) {
                d.remove();
                return true;
            }
            if (k < launchAt) {
                if (aim == null || !aim.isValid()) {
                    List<Player> ps = playersNear(36);
                    if (!ps.isEmpty()) aim = ps.get(ThreadLocalRandom.current().nextInt(ps.size()));
                }
                Location hover = pos.clone().add(0, Math.sin((t + launchAt) * 0.2) * 0.15, 0);
                if (aim != null) {
                    aimPoint = aim.getLocation().add(0, 1, 0);
                    point(aimPoint.toVector().subtract(hover.toVector()));
                    if (k >= launchAt - 18 && k % 2 == 0) warnLine(hover, aimPoint);
                }
                if (t % 2 == 0) d.teleport(hover);
                if (k % 4 == 0) hover.getWorld().spawnParticle(Particle.FLAME, hover, 1, 0.1, 0.1, 0.1, 0.01);
                return false;
            }
            if (vel == null) {
                if (aimPoint == null) {
                    d.remove();
                    return true;
                }
                vel = aimPoint.toVector().subtract(pos.toVector()).normalize().multiply(phase2 ? 1.0 : 0.85);
                sound(pos, Sound.ITEM_TRIDENT_THROW, 1.2f, 0.7f);
            }
            pos.add(vel);
            if (t % 2 == 0) d.teleport(pos);
            pos.getWorld().spawnParticle(Particle.FLAME, pos, 2, 0.05, 0.05, 0.05, 0.01);
            for (Player p : playersNear(64)) {
                if (p.getLocation().add(0, 1, 0).distanceSquared(pos) < 1.3 * 1.3) {
                    hurt(p, phase2 ? 7 : 6, vel.clone().normalize().multiply(0.4).setY(0.25));
                    p.setFireTicks(50);
                    sound(pos, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1f, 1f);
                    d.remove();
                    return true;
                }
            }
            if (k > launchAt + 40 || !pos.getBlock().isPassable()) {
                pos.getWorld().spawnParticle(Particle.LAVA, pos, 4, 0.1, 0.1, 0.1, 0);
                sound(pos, Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
                d.remove();
                return true;
            }
            return false;
        }

        private void point(Vector dir) {
            if (dir.lengthSquared() < 1e-4) return;
            Vector3f from = new Vector3f(1, 1, 0).normalize();
            Vector3f to = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()).normalize();
            Quaternionf q = new Quaternionf().rotationTo(from, to);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
            d.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(1.6f), new Quaternionf()));
        }

        void remove() {
            d.remove();
        }
    }

    private void warnLine(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double len = Math.min(30, dir.length());
        dir.normalize();
        for (double s = 0; s < len; s += 0.8) {
            from.getWorld().spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(s)), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(phase2 ? STORM : EMBER, 0.7f));
        }
    }

    private int forgeStart = -9999;

    private void hotForge() {
        neutral();
        if (st <= 14) {
            float k = st / 14f;
            armR.pose(-0.35f - 2.4f * k, 0, 0.1f);
            torso.pose(-0.2f * k, 0, 0);
        } else if (st <= 22) {
            armR.pose(-0.7f, 0, 0.05f);
            torso.pose(0.5f, 0, 0);
            hips.offset.set(0, -0.5f, 0);
            if (st == 16) {
                Location c = rig.location();
                sound(c, Sound.BLOCK_ANVIL_LAND, 2f, 0.6f);
                sound(c, Sound.ITEM_FIRECHARGE_USE, 2f, 0.5f);
                c.getWorld().spawnParticle(Particle.LAVA, c, 20, 2, 0.2, 2, 0);
                burning.clear();
                ThreadLocalRandom r = ThreadLocalRandom.current();
                int n = phase2 ? 5 : 3;
                while (burning.size() < n) burning.add(r.nextInt(8));
                forgeStart = t;
                titleNear("", "<#ff6a1a>🔥 Podłoga się rozgrzewa — uciekaj z zaznaczonych pól!", 40);
            }
        } else {
            float k = Math.max(0, 1 - (st - 22) / 10f);
            armR.pose(-0.35f - 0.35f * k, 0, 0.08f);
            torso.pose(0.5f * k, 0, 0);
        }
        if (st >= 34) go(State.IDLE);
    }

    private void burnTick() {
        if (burning.isEmpty()) return;
        int age = t - forgeStart;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean warn = age < 40;
        if (age > 100) {
            burning.clear();
            return;
        }
        if (age % (warn ? 3 : 2) == 0) {
            for (int sector : burning) {
                for (int i = 0; i < (warn ? 6 : 10); i++) {
                    double a = Math.toRadians(sector * 45 + r.nextDouble(45));
                    double d = r.nextDouble(2, ARENA);
                    Location l = groundBelow(home.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d), 4, 6);
                    if (l == null) continue;
                    if (warn) l.getWorld().spawnParticle(Particle.DUST, l.add(0, 0.15, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(EMBER, 1.6f));
                    else {
                        l.getWorld().spawnParticle(Particle.FLAME, l.add(0, 0.2, 0), 1, 0.1, 0.05, 0.1, 0.01);
                        if (i % 3 == 0) l.getWorld().spawnParticle(Particle.LAVA, l, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        if (age == 40) sound(home, Sound.BLOCK_FIRE_AMBIENT, 2f, 0.5f);
        if (!warn && age % 10 == 0) {
            for (Player p : playersNear(ARENA + 10)) {
                if (inBurning(p.getLocation())) {
                    hurt(p, phase2 ? 4 : 3, null);
                    p.setFireTicks(40);
                }
            }
        }
    }

    private boolean inBurning(Location l) {
        double dx = l.getX() - home.getX(), dz = l.getZ() - home.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 2 || d > ARENA + 0.5 || Math.abs(l.getY() - home.getY()) > 3) return false;
        double deg = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
        return burning.contains((int) (deg / 45) % 8);
    }

    private void split() {
        invulnerable = true;
        neutral();
        Location c = rig.location();
        float k = Math.min(1, st / 12f);
        armL.pose(-0.3f * k, 0, -1.2f * k);
        armR.pose(-0.3f * k, 0, 1.2f * k);
        head.pose(-0.5f * k, 0, 0);
        if (st == 1) {
            titleNear("<#ff6a1a><b>KOWAL PĘKA!</b>", "<white>Rozbijcie trzy golemy <u>naraz</u> — inaczej się sklejają", 64);
            sound(c, Sound.ENTITY_RAVAGER_ROAR, 2.5f, 0.5f);
            swords.forEach(Sword::remove);
            swords.clear();
        }
        if (st % 4 == 0 && st < 30) {
            c.getWorld().spawnParticle(Particle.LAVA, c.clone().add(0, 3, 0), 6, 1, 1.5, 1, 0);
            sound(c, Sound.BLOCK_STONE_BREAK, 1.5f, 0.5f + st / 40f);
        }
        if (st == 30) {
            sound(c, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.6f);
            sound(c, Sound.ENTITY_IRON_GOLEM_DEATH, 2f, 0.5f);
            c.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, 3, 0), 1, 0, 0, 0, 0);
            debris(c.clone().add(0, 3, 0), 16, 0.6);
            rig.scale = 0.01f;
            hideMainHurtboxes();
            int players = Math.max(1, playersNear(40).size());
            for (int i = 0; i < 3; i++) {
                double a = Math.toRadians(rig.yaw) + Math.PI * 2 * i / 3;
                Location at = ground(c.clone().add(Math.cos(a) * 4, 1, Math.sin(a) * 4));
                minis.add(new Mini(at == null ? c.clone() : at, 45 + 30 * Math.min(6, players)));
            }
            firstBrokenAt = -1;
            invulnerable = false;
            go(State.MINIS);
        }
    }

    private void minisTick() {
        for (Mini m : minis) m.tick();
        boolean any = false, all = true;
        for (Mini m : minis) {
            if (m.broken) any = true;
            else all = false;
        }
        if (all) {
            splitDone = true;
            go(State.REFORM);
            return;
        }
        if (any && firstBrokenAt >= 0) {
            int left = MINI_WINDOW - (t - firstBrokenAt);
            if (t % 10 == 0) {
                var msg = MM.deserialize("<#ff6a1a>⚒ Rozbite: <white>" + minis.stream().filter(m -> m.broken).count()
                        + "/3</white> <gray>· sklejają się za <white>" + Math.max(0, left / 20) + " s");
                for (Player p : playersNear(48)) plugin.messages().actionBar(p, SunBossy.MSG_ACTION, msg);
            }
            if (left <= 0) {
                for (Mini m : minis) if (m.broken) m.revive();
                hp = Math.min(maxHp, hp + maxHp * 0.05);
                firstBrokenAt = -1;
                titleNear("", "<#ff6a1a>Golemy się skleiły! Bijcie wszystkie naraz.", 48);
                sound(rig.location(), Sound.BLOCK_ANVIL_USE, 2f, 0.5f);
            }
        }
    }

    private void reform() {
        invulnerable = true;
        neutral();
        Location c = rig.location();
        if (st == 1) {
            for (Mini m : minis) m.flyHome(c);
            sound(c, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        }
        if (st >= 20 && st <= 40) {
            float k = (st - 20) / 20f;
            rig.scale = Math.max(0.01f, k);
            if (st % 4 == 0) c.getWorld().spawnParticle(Particle.LAVA, c.clone().add(0, 3, 0), 4, 1, 1.5, 1, 0);
        }
        if (st == 40) {
            for (Mini m : minis) m.remove();
            minis.clear();
            rig.scale = 1f;
            phase2 = true;
            bar.color(BossBar.Color.BLUE);
            bar.name(MM.deserialize("<#7fd8ff><b>⚒ Kowal Burzowej Kuźni</b></#7fd8ff> <gray>— burza w kuźni"));
            for (Rig.Part p : glowing) if (p.d != null) {
                p.d.setGlowing(true);
                p.d.setGlowColorOverride(STORM);
            }
            Location tip = rig.worldPoint(head, 0, 6.6f, 0.25f);
            c.getWorld().strikeLightningEffect(tip);
            sound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.8f);
            titleNear("<#7fd8ff><b>FAZA 2</b>", "<white>Kowal ściąga burzę do kuźni!", 64);
            placeHurtboxes();
        }
        if (st >= 55) {
            invulnerable = false;
            cooldown = 20;
            go(State.IDLE);
        }
    }

    @Override
    protected double damageMultiplier(ArmorStand hitbox) {
        if (state == State.MINIS) {
            for (Mini m : minis) if (m.box == hitbox) return m.broken ? 0 : MINI_MUL;
            return 0;
        }
        return 1;
    }

    @Override
    protected void onDamaged(Player p, double amount, ArmorStand hitbox) {
        if (state != State.MINIS) return;
        for (Mini m : minis) if (m.box == hitbox) m.hit(amount / MINI_MUL);
    }

    private class Mini {
        private final Rig r = new Rig(plugin);
        private final Rig.Bone body, armA, armB;
        private final ArmorStand box;
        private final double needed;
        private double taken;
        boolean broken;
        private int punchCd = 30;
        private double walk;
        private Location flyTarget;

        Mini(Location at, double needed) {
            this.needed = needed;
            body = r.bone(null, 0, 0.9f, 0);
            armA = r.bone(body, -0.85f, 1.8f, 0);
            armB = r.bone(body, 0.85f, 1.8f, 0);
            r.part(body, Material.POLISHED_BLACKSTONE_BRICKS, 0, 1.2f, 0, 1.3f, 1.4f, 1.0f);
            r.part(body, Material.DEEPSLATE_TILES, -0.35f, 0.3f, 0, 0.45f, 0.6f, 0.5f);
            r.part(body, Material.DEEPSLATE_TILES, 0.35f, 0.3f, 0, 0.45f, 0.6f, 0.5f);
            Rig.Part core = r.part(body, Material.MAGMA_BLOCK, 0, 1.25f, 0.51f, 0.5f, 0.4f, 0.05f);
            r.part(body, Material.POLISHED_DEEPSLATE, 0, 2.15f, 0.05f, 0.7f, 0.55f, 0.7f);
            Rig.Part eyeA = r.part(body, Material.SHROOMLIGHT, -0.15f, 2.2f, 0.41f, 0.14f, 0.1f, 0.03f);
            Rig.Part eyeB = r.part(body, Material.SHROOMLIGHT, 0.15f, 2.2f, 0.41f, 0.14f, 0.1f, 0.03f);
            r.part(armA, Material.IRON_BLOCK, -0.85f, 1.3f, 0, 0.45f, 1.1f, 0.45f);
            r.part(armB, Material.IRON_BLOCK, 0.85f, 1.3f, 0, 0.45f, 1.1f, 0.45f);
            r.spawn(at);
            for (Rig.Part p : List.of(core, eyeA, eyeB)) if (p.d != null) p.d.setBrightness(new Display.Brightness(15, 15));
            box = addHurtbox(at, 1.3f);
            at.getWorld().spawnParticle(Particle.LAVA, at.clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0);
        }

        void hit(double amount) {
            if (broken) return;
            taken += amount;
            if (taken >= needed) breakMe();
        }

        private void breakMe() {
            broken = true;
            hp = Math.max(maxHp * SPLIT_FLOOR, hp - maxHp * MINI_SHARE);
            Location at = r.location();
            sound(at, Sound.BLOCK_ANVIL_DESTROY, 1.5f, 0.8f);
            sound(at, Sound.ENTITY_IRON_GOLEM_DEATH, 1f, 1.4f);
            at.getWorld().spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            debris(at.clone().add(0, 1, 0), 6, 0.4);
            r.scale = 0.01f;
            r.apply(3);
            box.teleport(at.clone().add(0, -25, 0));
            if (firstBrokenAt < 0) firstBrokenAt = t;
        }

        void revive() {
            broken = false;
            taken = 0;
            r.scale = 1f;
            Location at = r.location();
            at.getWorld().spawnParticle(Particle.LAVA, at.clone().add(0, 1, 0), 10, 0.4, 0.4, 0.4, 0);
            sound(at, Sound.BLOCK_ANVIL_USE, 1.2f, 1.2f);
        }

        void tick() {
            if (!r.isValid()) return;
            Location c = r.location();
            if (broken) {
                if (t % 10 == 0) c.getWorld().spawnParticle(Particle.SMOKE, c.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0.01);
                return;
            }
            box.teleport(c);
            Player p = null;
            double best = Double.MAX_VALUE;
            for (Player pl : playersNear(40)) {
                double d = pl.getLocation().distanceSquared(c);
                if (d < best) {
                    best = d;
                    p = pl;
                }
            }
            body.pose(0, 0, 0);
            armA.pose(0, 0, 0);
            armB.pose(0, 0, 0);
            if (p != null) {
                Vector d = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (d.lengthSquared() > 0.01) r.yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
                double dist = Math.sqrt(best);
                if (dist > 1.8 && c.distanceSquared(home) < 18 * 18) {
                    Location next = ground(c.clone().add(d.normalize().multiply(phase2 ? 0.2 : 0.16)));
                    if (next != null && Math.abs(next.getY() - c.getY()) <= 1.2 && t % 2 == 0) r.moveTo(next, 2);
                    walk += 0.5;
                    armA.pose((float) Math.sin(walk) * 0.6f, 0, 0);
                    armB.pose((float) -Math.sin(walk) * 0.6f, 0, 0);
                }
                if (--punchCd <= 0 && dist < 2.4) {
                    punchCd = 26;
                    armA.pose(-1.5f, 0, 0);
                    armB.pose(-1.5f, 0, 0);
                    sound(c, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1.3f);
                    hurt(p, 4, d.normalize().multiply(0.7).setY(0.35));
                }
            }
            if (t % 2 == 0) r.apply(2);
        }

        void flyHome(Location to) {
            flyTarget = to;
            r.scale = 1f;
            r.moveTo(to.clone().add(0, 2, 0), 20);
            r.apply(10);
        }

        void remove() {
            Location at = r.isValid() ? r.location() : home;
            if (flyTarget != null) at.getWorld().spawnParticle(Particle.LAVA, at, 6, 0.4, 0.4, 0.4, 0);
            r.remove();
            plugin.unregisterHurtbox(box);
            hurtboxes.remove(box);
            box.remove();
        }
    }

    @Override
    protected void hurtSound() {
        Location c = rig.location().add(0, 3, 0);
        sound(c, Sound.BLOCK_ANVIL_HIT, 1f, 0.6f);
        sound(c, Sound.ENTITY_IRON_GOLEM_HURT, 1f, 0.6f);
    }

    @Override
    protected void onDeath() {
        swords.forEach(Sword::remove);
        swords.clear();
        for (Mini m : minis) m.remove();
        minis.clear();
        burning.clear();
        rig.scale = 1f;
        go(State.DEATH);
        titleNear("<#ff6a1a><b>KUŹNIA GAŚNIE</b>", "<gray>…", 64);
    }

    private void death() {
        neutral();
        Location c = rig.location();
        float k = Math.min(1, st / 40f);
        torso.pose(0.45f * k, 0, 0);
        head.pose(0.5f * k, 0, 0);
        armR.pose(0.2f * k, 0, 0.3f * k);
        hips.offset.set(0, -1.1f * k, 0);
        legL.pose(1.3f * k, 0, 0);
        legR.pose(1.3f * k, 0, 0);
        if (furnace.d != null) furnace.d.setBrightness(new Display.Brightness(Math.max(0, 15 - st / 4), 15));
        if (st == 1) sound(c, Sound.BLOCK_FIRE_EXTINGUISH, 2f, 0.5f);
        if (st % 6 == 0 && st < 60) {
            c.getWorld().spawnParticle(Particle.LARGE_SMOKE, c.clone().add(0, 3, 0), 8, 1, 1.5, 1, 0.03);
            sound(c, Sound.BLOCK_ANVIL_STEP, 1.2f, 0.5f + st / 120f);
        }
        if (st == 62) {
            sound(c, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
            sound(c, Sound.BLOCK_ANVIL_DESTROY, 2f, 0.5f);
            Location mid = c.clone().add(0, 3, 0);
            mid.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0);
            mid.getWorld().spawnParticle(Particle.LAVA, mid, 60, 1, 1, 1, 0);
            sparks(mid, 80);
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

    private void sparks(Location at, int n) {
        at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at, n, 0.8, 0.5, 0.8, 0, new Particle.DustTransition(HOT, EMBER, 1f));
        at.getWorld().spawnParticle(Particle.FLAME, at, n / 3, 0.5, 0.3, 0.5, 0.08);
    }

    private void debris(Location at, int n, double power) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Material[] mats = {Material.POLISHED_BLACKSTONE, Material.DEEPSLATE_TILES, Material.MAGMA_BLOCK, Material.IRON_BLOCK};
        for (int i = 0; i < n; i++) {
            Vector v = new Vector(r.nextDouble(-1, 1), 0, r.nextDouble(-1, 1)).normalize().multiply(r.nextDouble(0.15, power))
                    .setY(r.nextDouble(0.35, 0.8));
            Debris.spawn(plugin, at, mats[r.nextInt(mats.length)], (float) r.nextDouble(0.25, 0.55), v, 40 + r.nextInt(20));
        }
    }

    @Override
    protected void cleanup() {
        swords.forEach(Sword::remove);
        swords.clear();
        for (Mini m : minis) m.remove();
        minis.clear();
        burning.clear();
    }
}

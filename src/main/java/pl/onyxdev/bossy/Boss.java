package pl.onyxdev.bossy;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import pl.onyxdev.suncore.SunCore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public abstract class Boss extends BukkitRunnable {
    protected static final MiniMessage MM = MiniMessage.miniMessage();

    protected final SunBossy plugin;
    public final String id;
    protected final Location home;
    protected final Rig rig;
    protected final List<ArmorStand> hurtboxes = new ArrayList<>();
    protected final Map<UUID, Double> damage = new LinkedHashMap<>();
    protected final Map<UUID, String> names = new HashMap<>();
    protected final BossBar bar;
    private final Set<UUID> barViewers = new HashSet<>();

    protected double maxHp;
    protected double hp;
    protected int t;

    protected boolean invulnerable = true;
    protected boolean dead;
    private int lonelyTicks;
    private int lastHurtSound;
    private int hitsThisTick;

    protected Boss(SunBossy plugin, String id, Location home, BossBar bar) {
        this.plugin = plugin;
        this.id = id;
        this.home = home.clone();
        this.rig = new Rig(plugin);
        this.bar = bar;
    }

    public abstract String displayName();

    protected abstract void onTick();

    protected double damageMultiplier(ArmorStand hitbox) {
        return 1;
    }

    protected void onDamaged(Player p, double amount, ArmorStand hitbox) {
    }

    protected abstract void onDeath();

    public abstract void spawnAt(float yaw);

    public void start(int playersNearby, double baseHp, double perPlayer) {
        maxHp = baseHp + perPlayer * Math.max(0, playersNearby - 1);
        hp = maxHp;
        runTaskTimer(plugin, 1, 1);
    }

    @Override
    public void run() {
        try {
            t++;
            hitsThisTick = 0;
            if (!rig.isValid() && !dead) {
                remove();
                return;
            }
            onTick();
            if (t % 10 == 0) updateBar();
            if (!dead && t % 20 == 0) checkLonely();
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Błąd bossa " + id, e);
            remove();
        }
    }

    private void checkLonely() {
        if (playersNear(56).isEmpty()) {
            lonelyTicks += 20;
            if (lonelyTicks >= 20 * 45) {
                plugin.messages().broadcast(SunBossy.MSG_LEFT, MM.deserialize(SunBossy.PREFIX + displayName()
                        + " <gray>odszedł — nikt nie stanął do walki."));
                remove();
            }
        } else {
            lonelyTicks = 0;
        }
    }

    public List<Player> playersNear(double r) {
        List<Player> out = new ArrayList<>();
        Location c = rig.isValid() ? rig.location() : home;
        for (Player p : c.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            if (p.getLocation().distanceSquared(c) <= r * r) out.add(p);
        }
        return out;
    }

    public Player nearestPlayer(double r) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        Location c = rig.location();
        for (Player p : playersNear(r)) {
            double d = p.getLocation().distanceSquared(c);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    protected void hurt(Player p, double amount, Vector knock) {
        ArmorStand src = hurtboxes.isEmpty() ? null : hurtboxes.getFirst();
        if (src != null) p.damage(amount, src);
        else p.damage(amount);
        if (knock != null) safePush(p, knock);
    }

    protected ArmorStand addHurtbox(Location at, float scale) {
        ArmorStand s = at.getWorld().spawn(at, ArmorStand.class, a -> {
            plugin.tag(a);
            a.setInvisible(true);
            a.setGravity(false);
            a.setBasePlate(false);
            a.setSilent(true);
            a.setCollidable(false);
            a.setPersistent(false);
            a.customName(MM.deserialize(displayName()));
            a.setCustomNameVisible(false);
            var attr = a.getAttribute(Attribute.SCALE);
            if (attr != null) attr.setBaseValue(scale);
        });
        hurtboxes.add(s);
        plugin.registerHurtbox(s, this);
        return s;
    }

    protected void setHurtboxScale(ArmorStand s, float scale) {
        var attr = s.getAttribute(Attribute.SCALE);
        if (attr != null) attr.setBaseValue(scale);
    }

    void damageFrom(Player p, double raw, ArmorStand hitbox) {
        if (dead) return;
        if (invulnerable) {
            if (t - lastHurtSound > 10) {
                lastHurtSound = t;
                p.playSound(p, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 0.4f, 1.8f);
                p.sendActionBar(MM.deserialize("<gray>Boss jest teraz odporny!"));
            }
            return;
        }
        double amount = raw * damageMultiplier(hitbox);
        if (amount <= 0) return;
        hp = Math.max(0, hp - amount);
        damage.merge(p.getUniqueId(), amount, Double::sum);
        names.put(p.getUniqueId(), p.getName());
        onDamaged(p, amount, hitbox);
        if (hitsThisTick++ < 3) damageNumber(hitbox.getLocation().add(0, hitbox.getHeight() * 0.7, 0), amount,
                damageMultiplier(hitbox) > 1);
        if (t - lastHurtSound > 5) {
            lastHurtSound = t;
            hurtSound();
            rig.glow(true, Color.WHITE);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!dead) rig.glow(false, null);
            }, 3);
        }
        if (hp <= 0) {
            dead = true;
            invulnerable = true;
            onDeath();
        }
    }

    protected void hurtSound() {
    }

    public void adminDamage(double amount) {
        if (dead) return;
        hp = Math.max(0, hp - amount);
        if (hp <= 0) {
            dead = true;
            invulnerable = true;
            onDeath();
        }
    }

    public String debug() {
        Rig.Part p = rig.parts().isEmpty() ? null : rig.parts().getFirst();
        return "kotwica=" + fmt(rig.location()) + " czesc=" + (p == null || p.d == null ? "-" : fmt(p.d.getLocation()))
                + " hp=" + Math.round(hp) + "/" + Math.round(maxHp) + " niesmiertelny=" + invulnerable;
    }

    private static String fmt(Location l) {
        return String.format(java.util.Locale.ROOT, "%.2f/%.2f/%.2f", l.getX(), l.getY(), l.getZ());
    }

    private void damageNumber(Location at, double amount, boolean crit) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location l = at.clone().add(r.nextDouble(-0.8, 0.8), r.nextDouble(0, 0.6), r.nextDouble(-0.8, 0.8));
        String color = crit ? "<#ffc02e>" : "<white>";
        TextDisplay d = l.getWorld().spawn(l, TextDisplay.class, e -> {
            plugin.tag(e);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setBrightness(new Display.Brightness(15, 15));
            e.text(MM.deserialize(color + "<b>-" + Math.max(1, Math.round(amount)) + (crit ? "!" : "")));
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(crit ? 1.4f : 1.1f), new AxisAngle4f()));
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(16);
            d.setTransformation(new Transformation(new Vector3f(0, 0.9f, 0), new AxisAngle4f(), new Vector3f(0.4f), new AxisAngle4f()));
        }, 2);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 18);
    }

    protected void updateBar() {
        bar.progress((float) Math.max(0, Math.min(1, hp / maxHp)));
        Set<UUID> now = new HashSet<>();
        for (Player p : playersNear(64)) {
            now.add(p.getUniqueId());
            if (barViewers.add(p.getUniqueId())) p.showBossBar(bar);
        }
        for (UUID id : new ArrayList<>(barViewers)) {
            if (!now.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(bar);
                barViewers.remove(id);
            }
        }
    }

    private void hideBar() {
        for (UUID id : barViewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        barViewers.clear();
    }

    protected void sound(Location at, Sound s, float v, float p) {
        at.getWorld().playSound(at, s, SoundCategory.HOSTILE, v, p);
    }

    protected void titleNear(String title, String sub, double r) {
        Title tt = Title.title(MM.deserialize(title), MM.deserialize(sub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(800)));
        for (Player p : playersNear(r)) SunCore.messages().title(p, SunBossy.MSG_TITLE, tt);
    }

    protected void ring(Location c, double radius, Color color, float size) {
        int n = (int) Math.max(16, radius * 10);
        World w = c.getWorld();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, size));
        }
    }

    protected Location ground(Location at) {
        Location l = at.clone();
        int top = l.getBlockY() + 2;
        for (int y = top; y >= l.getBlockY() - 6; y--) {
            if (!l.getWorld().getBlockAt(l.getBlockX(), y, l.getBlockZ()).isPassable()
                    && l.getWorld().getBlockAt(l.getBlockX(), y + 1, l.getBlockZ()).isPassable()) {
                l.setY(y + 1);
                return l;
            }
        }
        return null;
    }

    protected Location groundBelow(Location at, int up, int down) {
        Location l = at.clone();
        for (int y = l.getBlockY() + up; y >= l.getBlockY() - down; y--) {
            if (!l.getWorld().getBlockAt(l.getBlockX(), y, l.getBlockZ()).isPassable()
                    && l.getWorld().getBlockAt(l.getBlockX(), y + 1, l.getBlockZ()).isPassable()) {
                l.setY(y + 1);
                return l;
            }
        }
        return null;
    }

    protected void safePush(Player p, Vector v) {
        Vector flat = v.clone().setY(0);
        if (flat.lengthSquared() > 0.0001) {
            Location land = p.getLocation().add(flat.clone().multiply(6));
            Location half = p.getLocation().add(flat.clone().multiply(3));
            if (groundBelow(land, 3, 12) == null || groundBelow(half, 3, 12) == null) {
                v = flat.multiply(0.15).setY(v.getY());
            }
        }
        p.setVelocity(p.getVelocity().add(v));
    }

    protected void finishDeath() {
        plugin.rewards(this, damage, names);
        remove();
    }

    public void remove() {
        try {
            cancel();
        } catch (IllegalStateException ignored) {
        }
        hideBar();
        rig.remove();
        for (Entity e : hurtboxes) {
            plugin.unregisterHurtbox(e);
            e.remove();
        }
        hurtboxes.clear();
        cleanup();
        plugin.bossGone(this);
    }

    protected void cleanup() {
    }

    public Location location() {
        return rig.isValid() ? rig.location() : home;
    }

    public double hp() {
        return hp;
    }
}

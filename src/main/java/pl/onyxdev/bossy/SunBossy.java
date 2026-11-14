package pl.onyxdev.bossy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import pl.onyxdev.suncore.Messages;
import pl.onyxdev.suncore.SunCore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class SunBossy extends JavaPlugin implements Listener, TabExecutor {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    static final String PREFIX = "<gradient:#ffc02e:#ff5a1f><b>Bossy</b></gradient> <dark_gray>»</dark_gray> ";

    static final String MSG_ANNOUNCE = "bossy.ogloszenie";
    static final String MSG_SPAWN = "bossy.pojawienie";
    static final String MSG_DEFEAT = "bossy.pokonany";
    static final String MSG_LEFT = "bossy.odszedl";
    static final String MSG_REWARD = "bossy.nagroda";
    static final String MSG_TITLE = "bossy.tytul";
    static final String MSG_ACTION = "bossy.pasek";
    static final String MSG_INFO = "bossy.info";

    static final List<String> BOSSES = List.of("straznik", "smok", "kraken", "organista", "kowal", "wilk", "krolowa");

    private static final class Altar {
        final String boss;
        final String world;
        final int x, y, z;
        final double ax, ay, az;
        final float ayaw;
        long cooldownUntil;
        Interaction hit;
        TextDisplay holo;

        Altar(String boss, String world, int x, int y, int z, double ax, double ay, double az, float ayaw) {
            this.boss = boss;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.ayaw = ayaw;
        }

        Location block() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }

        Location arena() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, ax, ay, az, ayaw, 0);
        }

        boolean loaded() {
            World w = Bukkit.getWorld(world);
            return w != null && w.isChunkLoaded(x >> 4, z >> 4);
        }
    }

    private NamespacedKey fxKey, summonKey;
    private final Map<UUID, Boss> hurtboxes = new HashMap<>();
    private final List<Boss> active = new ArrayList<>();
    private final List<Altar> altars = new ArrayList<>();
    private final Map<String, Long> lastEvent = new HashMap<>();

    private final Map<String, Integer> eventCountdown = new HashMap<>();
    private int serverTick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        addMissingBossSections();
        fxKey = new NamespacedKey(this, "efekt");
        summonKey = new NamespacedKey(this, "przywolanie");
        Messages m = SunCore.messages();
        m.register(this, MSG_ANNOUNCE, "Zapowiedź eventu z bossem (za 5 min / 1 min)", true, true);
        m.register(this, MSG_SPAWN, "Ogłoszenie, że pojawił się boss", true, true);
        m.register(this, MSG_DEFEAT, "Ogłoszenie pokonania bossa z podium", true, true);
        m.register(this, MSG_LEFT, "Ogłoszenie, że boss odszedł bez walki", true, true);
        m.register(this, MSG_REWARD, "Twój wynik i nagroda po walce z bossem", true, false);
        m.register(this, MSG_TITLE, "Duże napisy w trakcie walki (faza 2, ładowanie słońca…)", true, true);
        m.register(this, MSG_ACTION, "Pasek nad hotbarem w trakcie walki (np. stan rdzenia)", true, false);
        m.register(this, MSG_INFO, "Komunikaty ołtarza (brak przedmiotu, odnowienie…)", true, false);
        loadAltars();
        Bukkit.getPluginManager().registerEvents(this, this);
        var cmd = getCommand("bossy");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20, 20);
    }

    private void addMissingBossSections() {
        var in = getResource("config.yml");
        if (in == null) return;
        YamlConfiguration def = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        List<String> added = new ArrayList<>();
        for (String id : BOSSES) {
            String path = "bossy." + id;

            if (getConfig().contains(path, true) || !def.isConfigurationSection(path)) continue;
            ConfigurationSection src = def.getConfigurationSection(path);
            for (String k : src.getKeys(true)) {
                if (!src.isConfigurationSection(k)) getConfig().set(path + "." + k, src.get(k));
            }
            added.add(id);
        }
        if (added.isEmpty()) return;
        saveConfig();
        getLogger().info("Dopisano do config.yml sekcje nowych bossów: " + String.join(", ", added));
    }

    @Override
    public void onDisable() {
        for (Boss b : new ArrayList<>(active)) b.remove();
        for (Altar a : altars) removeAltarVisuals(a);
        saveAltars();
    }

    Messages messages() {
        return SunCore.messages();
    }

    void tag(Entity e) {
        e.setPersistent(false);
        e.getPersistentDataContainer().set(fxKey, PersistentDataType.BYTE, (byte) 1);
    }

    void registerHurtbox(ArmorStand s, Boss b) {
        hurtboxes.put(s.getUniqueId(), b);
    }

    void unregisterHurtbox(Entity e) {
        hurtboxes.remove(e.getUniqueId());
    }

    void bossGone(Boss b) {
        active.remove(b);
    }

    private Boss create(String id, Location at) {
        return switch (id) {
            case "straznik" -> new Guardian(this, at);
            case "smok" -> new StormDragon(this, at);
            case "kraken" -> new VoidKraken(this, at);
            case "organista" -> new Organist(this, at);
            case "kowal" -> new Smith(this, at);
            case "wilk" -> new FrostWolf(this, at);
            case "krolowa" -> new HiveQueen(this, at);
            default -> null;
        };
    }

    private String bossName(String id) {
        return getConfig().getString("bossy." + id + ".nazwa", id);
    }

    public Boss summon(String id, Location at) {
        Boss b = create(id, at);
        if (b == null) return null;
        int players = 0;
        for (Player p : at.getWorld().getPlayers()) if (p.getLocation().distanceSquared(at) < 48 * 48) players++;
        b.spawnAt(at.getYaw());
        String path = "bossy." + id + ".";
        b.start(Math.max(1, players), getConfig().getDouble(path + "zycie-bazowe", 800), getConfig().getDouble(path + "zycie-na-gracza", 350));
        active.add(b);
        messages().broadcast(MSG_SPAWN, MM.deserialize(PREFIX + b.displayName() + " <gray>pojawił się! <dark_gray>("
                + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + ")"));
        return b;
    }

    private boolean arenaBusy(Location arena) {
        for (Boss b : active) {
            if (b.location().getWorld().equals(arena.getWorld()) && b.location().distanceSquared(arena) < 40 * 40) return true;
        }
        return false;
    }

    public ItemStack summonItem(String id, int amount) {
        Material mat = switch (id) {
            case "smok" -> Material.PRISMARINE_CRYSTALS;
            case "kraken" -> Material.ECHO_SHARD;
            case "organista" -> Material.AMETHYST_SHARD;
            case "kowal" -> Material.NETHERITE_SCRAP;
            case "wilk" -> Material.BONE;
            case "krolowa" -> Material.HONEYCOMB;
            default -> Material.RAW_GOLD;
        };
        ItemStack it = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        String name = "<b>" + itemName(id) + "</b>";
        meta.displayName(MM.deserialize(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(Stream.of(
                        "<gray>Połóż na ołtarzu (<white>PPM</white>), żeby przywołać:",
                        "<white>" + bossName(id),
                        "",
                        "<dark_gray>Walczcie razem — nagrody wg zadanych obrażeń.")
                .map(l -> MM.deserialize(l).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(summonKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    static String itemName(String id) {
        return switch (id) {
            case "straznik" -> "<gradient:#fff3b0:#ffc02e:#ff7a1a>Serce Słońca</gradient>";
            case "smok" -> "<gradient:#e8fbff:#5ce1ff:#3a7bff>Oko Burzy</gradient>";
            case "kraken" -> "<gradient:#e3c8ff:#9b5cff:#3b1a6b>Perła Otchłani</gradient>";
            case "organista" -> "<gradient:#ffd6f5:#ff5cd6:#6b1a8b>Nuta Pustki</gradient>";
            case "kowal" -> "<gradient:#ffe0a0:#ff6a1a:#7a1a00>Żarząca się Sztaba</gradient>";
            case "wilk" -> "<gradient:#ffffff:#9be7ff:#3a7bff>Kieł Alfy</gradient>";
            case "krolowa" -> "<gradient:#fff3b0:#ffb21a:#a85a00>Królewski Plaster</gradient>";
            default -> "<gold>Przedmiot przywołania: " + id;
        };
    }

    private static String altarStyle(String id) {
        return switch (id) {
            case "smok" -> "<#5ce1ff><b>⚡ ";
            case "kraken" -> "<#b07cff><b>☠ ";
            case "kowal" -> "<#ff6a1a><b>⚒ ";
            case "wilk" -> "<#9be7ff><b>❄ ";
            case "krolowa" -> "<#ffb21a><b>♛ ";
            default -> "<#ffc02e><b>☀ ";
        };
    }

    private Location spawnPoint(String boss, Location arena) {
        if (!boss.equals("kraken")) return arena;
        double y = Math.toRadians(arena.getYaw());
        Location l = arena.clone().add(-Math.sin(y) * 7, 0, Math.cos(y) * 7);
        l.setYaw(arena.getYaw() + 180);
        return l;
    }

    private String summonOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(summonKey, PersistentDataType.STRING);
    }

    private void loadAltars() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "oltarze.yml"));
        for (Map<?, ?> m : y.getMapList("oltarze")) {
            try {
                altars.add(new Altar(String.valueOf(m.get("boss")), String.valueOf(m.get("swiat")),
                        num(m, "x"), num(m, "y"), num(m, "z"),
                        ((Number) m.get("arena-x")).doubleValue(), ((Number) m.get("arena-y")).doubleValue(),
                        ((Number) m.get("arena-z")).doubleValue(), ((Number) m.get("arena-yaw")).floatValue()));
            } catch (Exception e) {
                getLogger().warning("Zły ołtarz w oltarze.yml: " + m);
            }
        }
        ConfigurationSection ev = y.getConfigurationSection("ostatni-event");
        if (ev != null) for (String k : ev.getKeys(false)) lastEvent.put(k, ev.getLong(k));
    }

    private static int num(Map<?, ?> m, String k) {
        return ((Number) m.get(k)).intValue();
    }

    private void saveAltars() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Altar a : altars) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("boss", a.boss);
            m.put("swiat", a.world);
            m.put("x", a.x);
            m.put("y", a.y);
            m.put("z", a.z);
            m.put("arena-x", a.ax);
            m.put("arena-y", a.ay);
            m.put("arena-z", a.az);
            m.put("arena-yaw", (double) a.ayaw);
            list.add(m);
        }
        y.set("oltarze", list);
        lastEvent.forEach((k, v) -> y.set("ostatni-event." + k, v));
        try {
            y.save(new File(getDataFolder(), "oltarze.yml"));
        } catch (IOException e) {
            getLogger().warning("Nie zapisano oltarze.yml: " + e.getMessage());
        }
    }

    private void ensureAltarVisuals(Altar a) {
        Location c = a.block();
        if (c == null) return;
        if (a.hit == null || !a.hit.isValid()) {
            a.hit = c.getWorld().spawn(c.clone().add(0, -0.52, 0), Interaction.class, e -> {
                tag(e);
                e.setInteractionWidth(1.08f);
                e.setInteractionHeight(1.08f);
                e.setResponsive(true);
            });
        }
        if (a.holo == null || !a.holo.isValid()) {
            a.holo = c.getWorld().spawn(c.clone().add(0, 1.2, 0), TextDisplay.class, d -> {
                tag(d);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(100, 16, 8, 4));
                d.setShadowed(true);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(0.5f);
            });
        }
        long left = a.cooldownUntil - System.currentTimeMillis();
        String state = left > 0 ? "<gray>Odnowienie: <white>" + (left / 60000 + 1) + " min"
                : "<gray>PPM trzymając " + itemName(a.boss) + "<gray>, żeby przywołać";
        a.holo.text(MM.deserialize(altarStyle(a.boss) + "Ołtarz — " + MM.escapeTags(bossName(a.boss)) + "</b>\n" + state));
        if (left <= 0 && serverTick % 3 == 0) {
            int rgb = switch (a.boss) {
                case "smok" -> 0x5CE1FF;
                case "kraken" -> 0x9B5CFF;
                case "kowal" -> 0xFF6A1A;
                case "wilk" -> 0x9BE7FF;
                case "krolowa" -> 0xFFB21A;
                default -> 0xFFC02E;
            };
            c.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, 0.7, 0), 6, 0.35, 0.25, 0.35, 0,
                    new Particle.DustOptions(Color.fromRGB(rgb), 1.1f));
        }
    }

    private void removeAltarVisuals(Altar a) {
        if (a.hit != null) a.hit.remove();
        if (a.holo != null) a.holo.remove();
        a.hit = null;
        a.holo = null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAltarUse(PlayerInteractEntityEvent e) {
        Altar a = altarByHitbox(e.getRightClicked());
        if (a == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!a.boss.equals(summonOf(hand))) {
            info(p, "<gray>Ten ołtarz przywołuje <white>" + MM.escapeTags(bossName(a.boss))
                    + "</white>. Potrzebujesz przedmiotu przywołania w ręce.");
            p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        if (System.currentTimeMillis() < a.cooldownUntil) {
            info(p, "<gray>Ołtarz jeszcze się odnawia.");
            return;
        }
        Location arena = a.arena();
        if (arena == null || arenaBusy(spawnPoint(a.boss, arena)) || eventCountdown.containsKey(a.boss)) {
            info(p, "<gray>Boss już tu jest albo zaraz nadejdzie!");
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        a.cooldownUntil = System.currentTimeMillis() + getConfig().getLong("bossy." + a.boss + ".oltarz-cooldown-minut", 20) * 60_000L;
        altarRitual(a, p.getName());
    }

    private Altar altarByHitbox(Entity e) {
        for (Altar a : altars) if (a.hit != null && a.hit.getUniqueId().equals(e.getUniqueId())) return a;
        return null;
    }

    private void altarRitual(Altar a, String by) {
        Location from = a.block().add(0, 0.6, 0);
        Location to = spawnPoint(a.boss, a.arena());
        messages().broadcast(MSG_ANNOUNCE, MM.deserialize(PREFIX + "<white>" + MM.escapeTags(by) + "</white> <gray>składa ofiarę na ołtarzu… "
                + "<#ffc02e>" + MM.escapeTags(bossName(a.boss)) + "</#ffc02e> <gray>nadchodzi!"));
        from.getWorld().playSound(from, Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 1.2f);
        from.getWorld().playSound(from, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
        for (int k = 0; k < 12; k++) {
            int step = k;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                var dir = to.clone().add(0, 1, 0).toVector().subtract(from.toVector());
                double len = dir.length();
                dir.normalize();
                for (double d = 0; d < len; d += 0.5) {
                    from.getWorld().spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(d)), 1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(step % 2 == 0 ? 0xFFC02E : 0xFF7A1A), 1.3f));
                }
                from.getWorld().spawnParticle(Particle.END_ROD, from, 4, 0.2, 0.2, 0.2, 0.05);
            }, k * 5L);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> summon(a.boss, to), 60);
    }

    private void info(Player p, String mini) {
        messages().send(p, MSG_INFO, MM.deserialize(PREFIX + mini));
    }

    private void tick() {
        serverTick++;
        for (Altar a : altars) if (a.loaded()) ensureAltarVisuals(a);
        if (serverTick % 30 != 0) {
            countdowns();
            return;
        }
        countdowns();
        for (String boss : BOSSES) {
            String path = "bossy." + boss + ".event.";
            if (!getConfig().getBoolean(path + "wlaczony", false) || eventCountdown.containsKey(boss)) continue;
            long every = (long) (getConfig().getDouble(path + "co-ile-godzin", 3) * 3_600_000L);
            long last = lastEvent.getOrDefault(boss, 0L);
            if (last == 0) {
                lastEvent.put(boss, System.currentTimeMillis());
                saveAltars();
                continue;
            }
            if (System.currentTimeMillis() - last < every) continue;
            if (Bukkit.getOnlinePlayers().size() < getConfig().getInt(path + "min-graczy-online", 1)) continue;
            startEvent(boss, 300);
        }
    }

    private boolean startEvent(String boss, int seconds) {
        Altar a = altars.stream().filter(x -> x.boss.equals(boss)).findFirst().orElse(null);
        if (a == null || a.arena() == null || arenaBusy(spawnPoint(boss, a.arena()))) return false;
        eventCountdown.put(boss, serverTick + seconds);
        announce(boss, seconds, a);
        return true;
    }

    private void countdowns() {
        for (var e : new ArrayList<>(eventCountdown.entrySet())) {
            String boss = e.getKey();
            int left = e.getValue() - serverTick;
            Altar a = altars.stream().filter(x -> x.boss.equals(boss)).findFirst().orElse(null);
            if (a == null) {
                eventCountdown.remove(boss);
                continue;
            }
            if (left == 60 || left == 10) announce(boss, left, a);
            if (left <= 0) {
                eventCountdown.remove(boss);
                lastEvent.put(boss, System.currentTimeMillis());
                saveAltars();
                if (a.arena() != null && !arenaBusy(spawnPoint(boss, a.arena()))) {
                    a.arena().getChunk().load();
                    summon(boss, spawnPoint(boss, a.arena()));
                }
            }
        }
    }

    private void announce(String boss, int seconds, Altar a) {
        String when = seconds >= 60 ? "za <white>" + (seconds / 60) + " min" : "za <white>" + seconds + " s";
        messages().broadcast(MSG_ANNOUNCE, MM.deserialize(PREFIX + "<#ffc02e>" + MM.escapeTags(bossName(boss))
                + "</#ffc02e> <gray>pojawi się " + when + "</white> przy ołtarzu <dark_gray>(" + (int) a.ax + " " + (int) a.ay + " " + (int) a.az + ")"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p, Sound.BLOCK_BELL_USE, 0.8f, 0.7f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHurtboxDamage(EntityDamageEvent e) {
        Boss b = hurtboxes.get(e.getEntity().getUniqueId());
        if (b == null) return;
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent ee)) return;
        Player p = null;
        if (ee.getDamager() instanceof Player pl) p = pl;
        else if (ee.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) {
            p = pl;
            if (pr instanceof AbstractArrow && !(pr instanceof Trident)) pr.remove();
        }
        if (p == null) return;
        b.damageFrom(p, e.getDamage(), (ArmorStand) e.getEntity());
    }

    @EventHandler
    public void onStandUse(PlayerArmorStandManipulateEvent e) {
        if (hurtboxes.containsKey(e.getRightClicked().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onStandInteract(PlayerInteractAtEntityEvent e) {
        if (hurtboxes.containsKey(e.getRightClicked().getUniqueId())) e.setCancelled(true);
    }

    private static final java.util.regex.Pattern CHANCE = java.util.regex.Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)%\\s*:\\s*(.+)$");
    private static final java.util.regex.Pattern PICK = java.util.regex.Pattern.compile("\\{losowo:([^}]+)}");

    static String rollReward(String entry) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        String cmd = entry;
        var m = CHANCE.matcher(entry);
        if (m.matches()) {
            double pct = Double.parseDouble(m.group(1).replace(',', '.'));
            if (r.nextDouble(100) >= pct) return null;
            cmd = m.group(2);
        }
        var pm = PICK.matcher(cmd);
        StringBuilder out = new StringBuilder();
        while (pm.find()) {
            String[] opts = pm.group(1).split("\\|");
            pm.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(opts[r.nextInt(opts.length)].trim()));
        }
        pm.appendTail(out);
        return out.toString();
    }

    void rewards(Boss b, Map<UUID, Double> damage, Map<UUID, String> names) {
        double total = damage.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(damage.entrySet());
        ranked.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        String path = "bossy." + b.id + ".nagrody.";
        StringBuilder podium = new StringBuilder();
        for (int i = 0; i < ranked.size(); i++) {
            UUID id = ranked.get(i).getKey();
            double share = total <= 0 ? 0 : ranked.get(i).getValue() / total;
            String name = names.getOrDefault(id, "?");
            if (i < 3) podium.append("\n <#ffc02e>").append(i + 1).append(".</#ffc02e> <white>").append(MM.escapeTags(name))
                    .append("</white> <gray>").append(Math.round(share * 100)).append("%");
            if (share < 0.03) continue;
            List<String> cmds = new ArrayList<>(getConfig().getStringList(path + "wszyscy"));
            if (i < 3) cmds.addAll(getConfig().getStringList(path + "top3"));
            if (i == 0) cmds.addAll(getConfig().getStringList(path + "top1"));
            for (String c : cmds) {
                String cmd = rollReward(c);
                if (cmd != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", name));
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                messages().send(p, MSG_REWARD, MM.deserialize(PREFIX + "<gray>Twój udział: <white>" + Math.round(share * 100)
                        + "%</white> · miejsce <white>" + (i + 1) + "</white>. Nagrody trafiły do ekwipunku!"));
            }
        }
        messages().broadcast(MSG_DEFEAT, MM.deserialize(PREFIX + b.displayName() + " <gray>pokonany!" + podium));
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (!s.hasPermission("bossy.admin")) {
            s.sendMessage(MM.deserialize(PREFIX + "<gray>Bossów przywołuje się na ołtarzach. Walczcie razem!"));
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase();
        String boss = a.length > 1 ? a[1].toLowerCase() : "straznik";
        switch (sub) {
            case "przywolaj" -> {
                if (!BOSSES.contains(boss)) return unknown(s);
                if (a.length >= 6 && Bukkit.getWorld(a[2]) != null) {
                    summon(boss, new Location(Bukkit.getWorld(a[2]), Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5])));
                    return true;
                }
                if (!(s instanceof Player p)) return true;
                Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(8));
                at.setYaw(p.getLocation().getYaw() + 180);
                summon(boss, at);
            }
            case "oltarz" -> {
                if (!(s instanceof Player p)) return true;
                if (!BOSSES.contains(boss)) return unknown(s);
                Block b = p.getTargetBlockExact(6);
                if (b == null) {
                    s.sendMessage(MM.deserialize(PREFIX + "<gray>Spójrz na blok ołtarza (z bliska). Arena = miejsce, gdzie stoisz."));
                    return true;
                }
                Location ar = p.getLocation();
                altars.add(new Altar(boss, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), ar.getX(), ar.getY(), ar.getZ(), ar.getYaw()));
                saveAltars();
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Ołtarz <white>" + boss + "</white> ustawiony. Arena: tu, gdzie stoisz ("
                        + ar.getBlockX() + " " + ar.getBlockY() + " " + ar.getBlockZ() + "), boss patrzy w twoją stronę."));
            }
            case "usun-oltarz" -> {
                if (!(s instanceof Player p)) return true;
                Block b = p.getTargetBlockExact(6);
                Altar found = null;
                if (b != null) for (Altar al : altars) if (al.world.equals(b.getWorld().getName()) && al.x == b.getX() && al.y == b.getY() && al.z == b.getZ()) found = al;
                if (found == null) {
                    s.sendMessage(MM.deserialize(PREFIX + "<gray>Ten blok nie jest ołtarzem."));
                    return true;
                }
                removeAltarVisuals(found);
                altars.remove(found);
                saveAltars();
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Usunięto ołtarz."));
            }
            case "przedmiot" -> {
                if (!BOSSES.contains(boss)) return unknown(s);
                Player target = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player p ? p : null;
                if (target == null) {
                    s.sendMessage(MM.deserialize(PREFIX + "<gray>Nie ma takiego gracza online."));
                    return true;
                }
                int n = a.length > 3 ? parse(a[3]) : 1;
                target.getInventory().addItem(summonItem(boss, n)).values()
                        .forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Dano przedmiot przywołania → <white>" + target.getName()));
            }
            case "event" -> {
                if (!BOSSES.contains(boss)) return unknown(s);
                boolean now = a.length > 2 && a[2].equalsIgnoreCase("teraz");
                if (!startEvent(boss, now ? 10 : 300)) {
                    s.sendMessage(MM.deserialize(PREFIX + "<gray>Brak ołtarza dla tego bossa albo arena zajęta."));
                }
            }
            case "zabij" -> {
                int n = active.size();
                for (Boss b : new ArrayList<>(active)) b.remove();
                eventCountdown.clear();
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Usunięto bossów: <white>" + n));
            }
            case "lista" -> {
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Aktywni: <white>" + active.size()));
                for (Boss b : active) {
                    Location l = b.location();
                    s.sendMessage(MM.deserialize(" <dark_gray>•</dark_gray> " + b.displayName() + " <gray>" + Math.round(b.hp())
                            + " HP · " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
                }
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Ołtarze: <white>" + altars.size()));
                for (Altar al : altars) {
                    s.sendMessage(MM.deserialize(" <dark_gray>•</dark_gray> <white>" + al.boss + "</white> <gray>" + al.x + " " + al.y + " " + al.z));
                }
            }
            case "rana" -> {
                double dmg = a.length > 1 ? Double.parseDouble(a[1]) : 100;
                for (Boss b : new ArrayList<>(active)) b.adminDamage(dmg);
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Zadano <white>" + dmg + "</white> obrażeń aktywnym bossom."));
            }
            case "zrzut" -> {
                if (active.isEmpty()) return true;
                Location c = active.getFirst().location();
                StringBuilder out = new StringBuilder();
                for (Entity e : c.getWorld().getNearbyEntities(c, 48, 48, 48)) {
                    if (!(e instanceof org.bukkit.entity.BlockDisplay d) || !e.getPersistentDataContainer().has(fxKey)) continue;
                    var tr = d.getTransformation();
                    var q = tr.getLeftRotation();
                    Location l = d.getLocation();
                    out.append(String.format(java.util.Locale.ROOT, "%s %.3f %.3f %.3f %.4f %.4f %.4f %.5f %.5f %.5f %.5f %.4f %.4f %.4f %d%n",
                            d.getBlock().getMaterial().name(), l.getX() - c.getX(), l.getY() - c.getY(), l.getZ() - c.getZ(),
                            tr.getTranslation().x, tr.getTranslation().y, tr.getTranslation().z, q.x, q.y, q.z, q.w,
                            tr.getScale().x, tr.getScale().y, tr.getScale().z, d.getBrightness() == null ? -1 : d.getBrightness().getBlockLight()));
                }
                String name = a.length > 1 ? a[1] : "zrzut";
                try {
                    java.nio.file.Files.writeString(new File(getDataFolder(), name + ".txt").toPath(), out);
                } catch (IOException ex) {
                    s.sendMessage("Nie zapisano: " + ex.getMessage());
                }
                s.sendMessage("Zrzut: " + name + ".txt");
            }
            case "debug" -> {
                for (Boss b : active) s.sendMessage(b.id + ": " + b.debug());
            }
            case "reload" -> {
                reloadConfig();
                s.sendMessage(MM.deserialize(PREFIX + "<gray>Przeładowano config."));
            }
            default -> {
                s.sendMessage(MM.deserialize(PREFIX + "<white>/bossy przywolaj [boss]</white> <gray>— boss 8 kratek przed tobą"));
                s.sendMessage(MM.deserialize(PREFIX + "<white>/bossy oltarz [boss]</white> <gray>— patrząc na blok; arena = gdzie stoisz (kraken: stań na krawędzi i patrz w pustkę)"));
                s.sendMessage(MM.deserialize(PREFIX + "<white>/bossy przedmiot [boss] [gracz] [ilość]</white> <gray>— przedmiot przywołania"));
                s.sendMessage(MM.deserialize(PREFIX + "<white>/bossy event [boss] [teraz]</white> <gray>· <white>zabij</white> · <white>lista</white> · <white>usun-oltarz</white> · <white>reload"));
            }
        }
        return true;
    }

    private boolean unknown(CommandSender s) {
        s.sendMessage(MM.deserialize(PREFIX + "<gray>Znani bossowie: <white>" + String.join(", ", BOSSES)));
        return true;
    }

    private static int parse(String s) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String label, String[] a) {
        if (!s.hasPermission("bossy.admin")) return List.of();
        if (a.length == 1) return Stream.of("przywolaj", "oltarz", "usun-oltarz", "przedmiot", "event", "zabij", "lista", "reload")
                .filter(x -> x.startsWith(a[0].toLowerCase())).toList();
        if (a.length == 2 && !a[0].equalsIgnoreCase("zabij") && !a[0].equalsIgnoreCase("lista")) return BOSSES;
        if (a.length == 3 && a[0].equalsIgnoreCase("przedmiot")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (a.length == 3 && a[0].equalsIgnoreCase("event")) return List.of("teraz");
        return List.of();
    }
}

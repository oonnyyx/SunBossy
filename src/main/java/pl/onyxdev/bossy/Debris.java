package pl.onyxdev.bossy;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

public class Debris extends BukkitRunnable {
    private final BlockDisplay d;
    private final Vector vel;
    private final Vector3f spinAxis;
    private final float spinSpeed;
    private final int life;
    private final Vector3f size;
    private final Quaternionf base;
    private Location pos;
    private float angle;
    private int t;

    private Debris(BlockDisplay d, Location pos, Vector vel, Vector3f size, int life, Quaternionf base) {
        this.d = d;
        this.base = base;
        this.pos = pos;
        this.vel = vel;
        this.size = size;
        this.life = life;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        this.spinAxis = new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f).normalize();
        this.spinSpeed = 0.15f + r.nextFloat() * 0.35f;
    }

    public static void spawn(SunBossy plugin, Location at, Material m, float size, Vector vel, int life) {
        BlockData data = m.createBlockData();
        Location a = at.clone();
        a.setYaw(0);
        a.setPitch(0);
        BlockDisplay d = a.getWorld().spawn(a, BlockDisplay.class, e -> {
            plugin.tag(e);
            e.setBlock(data);
            e.setTeleportDuration(2);
            e.setTransformation(new Transformation(new Vector3f(-size / 2), new Quaternionf(), new Vector3f(size), new Quaternionf()));
        });
        new Debris(d, a, vel, new Vector3f(size), life, new Quaternionf()).runTaskTimer(plugin, 1, 2);
    }

    public static void launch(SunBossy plugin, BlockDisplay d, Vector vel, int life) {
        Transformation t = d.getTransformation();
        Vector3f s = t.getScale();
        Quaternionf r = t.getLeftRotation();

        Vector3f c = r.transform(new Vector3f(s).mul(0.5f)).add(t.getTranslation());
        Location at = d.getLocation().add(c.x, c.y, c.z);
        at.setYaw(0);
        at.setPitch(0);
        d.teleport(at);
        d.setTransformation(new Transformation(r.transform(new Vector3f(s).mul(-0.5f)), r, s, new Quaternionf()));
        d.setTeleportDuration(2);
        new Debris(d, at, vel, new Vector3f(s), life, new Quaternionf(r)).runTaskTimer(plugin, 2, 2);
    }

    @Override
    public void run() {
        t += 2;
        if (!d.isValid() || t > life) {
            d.remove();
            cancel();
            return;
        }
        vel.setY(vel.getY() - 0.08);
        vel.multiply(0.98);
        Location next = pos.clone().add(vel.clone().multiply(2));
        if (!next.getBlock().isPassable() && vel.getY() < 0) {
            vel.setY(-vel.getY() * 0.3);
            vel.setX(vel.getX() * 0.5);
            vel.setZ(vel.getZ() * 0.5);
            next = pos.clone();
        }
        pos = next;
        d.teleport(pos);
        angle += spinSpeed * 2;
        float shrink = t > life - 12 ? Math.max(0.05f, (life - t) / 12f) : 1f;
        Vector3f s = new Vector3f(size).mul(shrink);
        Quaternionf q = new Quaternionf().rotateAxis(angle, spinAxis).mul(base);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(new Transformation(q.transform(new Vector3f(s).mul(-0.5f)), q, s, new Quaternionf()));
    }
}

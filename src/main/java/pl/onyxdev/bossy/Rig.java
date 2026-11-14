package pl.onyxdev.bossy;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Rig {
    public static final class Bone {
        final Bone parent;
        final Vector3f pivot;

        public final Quaternionf rot = new Quaternionf();

        public final Vector3f offset = new Vector3f();
        final Quaternionf wr = new Quaternionf();
        final Vector3f wt = new Vector3f();

        Bone(Bone parent, Vector3f pivot) {
            this.parent = parent;
            this.pivot = pivot;
        }

        public Bone pose(float x, float y, float z) {
            rot.identity().rotateXYZ(x, y, z);
            return this;
        }
    }

    public static final class Part {
        public final Bone bone;
        final Vector3f center;
        final Vector3f size;
        final Quaternionf rot;
        public final Material material;
        public BlockDisplay d;

        public float extraScale = 1f;

        public boolean detached;

        Part(Bone bone, Material m, Vector3f center, Vector3f size, Quaternionf rot) {
            this.bone = bone;
            this.material = m;
            this.center = center;
            this.size = size;
            this.rot = rot;
        }
    }

    private final List<Bone> bones = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();
    private final SunBossy plugin;
    private ItemDisplay anchor;
    public float yaw;
    public float scale = 1f;

    public Rig(SunBossy plugin) {
        this.plugin = plugin;
    }

    public Bone bone(Bone parent, float x, float y, float z) {
        Bone b = new Bone(parent, new Vector3f(x, y, z));
        bones.add(b);
        return b;
    }

    public Part part(Bone b, Material m, float cx, float cy, float cz, float sx, float sy, float sz) {
        return part(b, m, cx, cy, cz, sx, sy, sz, new Quaternionf());
    }

    public Part part(Bone b, Material m, float cx, float cy, float cz, float sx, float sy, float sz, Quaternionf rot) {
        Part p = new Part(b, m, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz), rot);
        parts.add(p);
        return p;
    }

    public Part mirror(Bone b, Material m, float cx, float cy, float cz, float sx, float sy, float sz) {
        return part(b, m, -cx, cy, cz, sx, sy, sz);
    }

    public List<Part> parts() {
        return parts;
    }

    public void spawn(Location at) {
        Location a = at.clone();
        a.setYaw(0);
        a.setPitch(0);
        anchor = a.getWorld().spawn(a, ItemDisplay.class, d -> {
            plugin.tag(d);
            d.setTeleportDuration(2);
        });
        for (Part p : parts) {
            BlockData data = p.material.createBlockData();
            p.d = a.getWorld().spawn(a, BlockDisplay.class, d -> {
                plugin.tag(d);
                d.setBlock(data);
                d.setViewRange(2f);
                d.setShadowRadius(0);
            });
            anchor.addPassenger(p.d);
        }
        apply(0);
    }

    public Location location() {
        return anchor.getLocation();
    }

    public void moveTo(Location to, int ticks) {
        Location l = to.clone();
        l.setYaw(0);
        l.setPitch(0);
        anchor.setTeleportDuration(Math.max(0, Math.min(59, ticks)));

        anchor.teleport(l, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    public boolean isValid() {
        return anchor != null && anchor.isValid();
    }

    public void apply(int ticks) {
        for (Bone b : bones) {
            if (b.parent == null) {
                b.wr.set(b.rot);
                b.wt.set(b.pivot).add(b.offset);
            } else {
                b.wr.set(b.parent.wr).mul(b.rot);
                Vector3f rel = new Vector3f(b.pivot).sub(b.parent.pivot);
                b.parent.wr.transform(rel);
                b.wt.set(b.parent.wt).add(rel).add(b.offset);
            }
        }
        Quaternionf y = new Quaternionf().rotateY((float) Math.toRadians(-yaw));
        for (Part p : parts) {
            if (p.detached || p.d == null || !p.d.isValid()) continue;
            Bone b = p.bone;
            Quaternionf r = new Quaternionf(b.wr).mul(p.rot);
            Vector3f c = new Vector3f(p.center).sub(b.pivot);
            b.wr.transform(c).add(b.wt);
            Vector3f s = new Vector3f(p.size).mul(p.extraScale);
            Vector3f half = r.transform(new Vector3f(s).mul(0.5f));
            Vector3f t = c.sub(half);

            Quaternionf gr = new Quaternionf(y).mul(r);
            Vector3f gt = y.transform(t.mul(scale));
            p.d.setInterpolationDelay(0);
            p.d.setInterpolationDuration(ticks);
            p.d.setTransformation(new Transformation(gt, gr, s.mul(scale), new Quaternionf()));
        }
    }

    public Location worldPoint(Bone b, float x, float y, float z) {
        Vector3f c = new Vector3f(x, y, z).sub(b.pivot);
        b.wr.transform(c).add(b.wt);
        new Quaternionf().rotateY((float) Math.toRadians(-yaw)).transform(c.mul(scale));
        return location().add(c.x, c.y, c.z);
    }

    public void glow(boolean on, org.bukkit.Color color) {
        for (Part p : parts) {
            if (p.d == null || p.detached) continue;
            p.d.setGlowing(on);
            if (on) p.d.setGlowColorOverride(color);
        }
    }

    public void brightness(Display.Brightness b) {
        for (Part p : parts) if (p.d != null && !p.detached) p.d.setBrightness(b);
    }

    public BlockDisplay detach(Part p) {
        if (p.detached || p.d == null) return null;
        p.detached = true;
        Transformation t = p.d.getTransformation();
        anchor.removePassenger(p.d);
        BlockDisplay d = p.d;
        p.d = null;
        d.setTransformation(t);
        return d;
    }

    public void remove() {
        for (Part p : parts) if (p.d != null) p.d.remove();
        if (anchor != null) anchor.remove();
    }
}

package pl.onyxdev.bossy;

import org.bukkit.Location;
import org.bukkit.Particle;

final class Fx {
    private Fx() {
    }

    static void plain(Location at, Particle p, int count, double spread, double speed) {
        if (p.getDataType() != Void.class) return;
        at.getWorld().spawnParticle(p, at, count, spread, spread, spread, speed);
    }
}

package vn.meowchan12.mobarenav2.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Utility for safely spawning particles across various Paper / Spigot versions.
 * Gracefully handles required particle data (Float, DustOptions, etc.) to prevent
 * IllegalArgumentExceptions when server builds enforce strict particle data classes.
 */
public class ParticleUtils {

    public static void spawnSafeParticle(World world, Particle particle, Location loc, int count) {
        spawnSafeParticle(world, particle, loc, count, 0.0, 0.0, 0.0, 0.0);
    }

    public static void spawnSafeParticle(World world, Particle particle, Location loc, int count,
                                         double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || loc == null || particle == null) return;
        try {
            Class<?> dataType = particle.getDataType();
            if (dataType == Void.class) {
                world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra);
            } else if (dataType == Float.class) {
                world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra, 1.0f);
            } else if (dataType == Particle.DustOptions.class) {
                world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra, new Particle.DustOptions(Color.RED, 1.0f));
            } else if (dataType == Integer.class) {
                world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra, 0);
            } else {
                world.spawnParticle(Particle.CRIT, loc, count, offsetX, offsetY, offsetZ, extra);
            }
        } catch (Throwable ignored) {
            try {
                world.spawnParticle(Particle.CRIT, loc, count, offsetX, offsetY, offsetZ, extra);
            } catch (Throwable ignoredAgain) {}
        }
    }
}

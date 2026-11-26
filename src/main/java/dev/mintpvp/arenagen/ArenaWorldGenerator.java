package dev.mintpvp.arenagen;

import dev.mintpvp.arenagen.ArenaGenerator.Slot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** chunk generator */
final class ArenaWorldGenerator extends ChunkGenerator {

    private static final BlockData WATER = Blocks.data(org.bukkit.Material.WATER);
    private static final BlockData BARRIER = Blocks.data(Material.BARRIER);

    private final ArenaGenerator owner;

    ArenaWorldGenerator(ArenaGenerator owner) {
        this.owner = owner;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        int minY = data.getMinHeight(), maxY = data.getMaxHeight();
        int bx = chunkX << 4, bz = chunkZ << 4;

        Map<Long, BlockData> lobby = owner.lobbyChunk(chunkX, chunkZ);
        if (lobby != null) {
            for (var e : lobby.entrySet()) {
                long k = e.getKey();
                int y = Pack.y(k);
                if (y >= minY && y < maxY) data.setBlock(Pack.x(k) - bx, y, Pack.z(k) - bz, e.getValue());
            }
        }

        Slot slot = owner.slotAt(bx + 8, bz + 8);
        if (slot == null) return;
        ArenaModel m = owner.model(slot);
        int cx = slot.cx(), cz = slot.cz();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int dx = bx + lx - cx, dz = bz + lz - cz;
                if (m.inside(dx, dz)) column(data, m, dx, dz, lx, lz, minY, maxY);
            }
        }
    }

    private void column(ChunkData data, ArenaModel m, int dx, int dz, int lx, int lz, int minY, int maxY) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        double d = Math.hypot(dx, dz);
        int s = m.surface(dx, dz);
        boolean ring = m.ring(dx, dz);
        boolean edge = d > m.rWall - 3 || m.nearPond(dx, dz); // no caves at the edge
        // solid ground

        for (int y = minY; y <= s - 4; y++) {
            BlockData b = Underground.block(dx + m.seed * 977, y, dz + m.seed * 1319, s, false, edge, minY);
            if (b != null) data.setBlock(lx, y, lz, b);
        }
        BlockData sub = m.sub();
        for (int y = s - 3; y < s; y++) data.setBlock(lx, y, lz, sub);
        int water = m.waterDepth(dx, dz);
        for (int y = s - water; y < s; y++) data.setBlock(lx, y, lz, WATER); // a frozen pond
        data.setBlock(lx, s, lz, m.top(dx, dz));

        int top = Math.min(maxY - 1, s + m.maxOverlayHeight());
        for (int y = s + 1; y <= top; y++) {
            BlockData f = m.featureAt(dx, dz, y - s);
            if (f == null) f = m.overlayAt(dx, y, dz);
            if (f != null) data.setBlock(lx, y, lz, f);
        }
        if (ring) {
            for (int y = s + 1; y <= Math.min(maxY - 1, m.barrierTop); y++) data.setBlock(lx, y, lz, BARRIER);
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo w, int x, int y, int z) {
                Slot s = owner.slotAt(x, z);
                return s == null ? Biome.PLAINS : s.theme().biome();
            }

            @Override
            public List<Biome> getBiomes(WorldInfo w) {
                return List.of(Biome.PLAINS, Biome.DESERT, Biome.FOREST, Biome.SNOWY_PLAINS, Biome.CHERRY_GROVE);
            }
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, ArenaGenerator.LOBBY_X + 0.5, ArenaGenerator.FLOOR_Y + 1, ArenaGenerator.LOBBY_Z + LobbyDesign.SPAWN_DZ + 0.5);
    }
}

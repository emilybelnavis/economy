package ca.emilybelnavis.mcmods.economy.village;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Release-excluded API experiments for A7-01; no production village hook is installed. */
class VillageGenerationFeasibilityTest {
    @BeforeAll static void bootstrap() {
        // Standalone API fixture has no discovered mods; use the real empty loader list.
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void pinnedFiveStyleJigsawParametersSurviveCodecRoundTrip() throws Exception {
        var ops = VanillaRegistries.createLookup().createSerializationContext(JsonOps.INSTANCE);
        for (var style : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            String resource = "/data/minecraft/worldgen/structure/village_" + style + ".json";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                var original = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                var codec = JigsawStructure.CODEC.codec();
                var delegate = codec.parse(ops, original).getOrThrow();
                // An enclosing wrapper can retain the delegate without reading private start fields.
                var encoded = codec.encodeStart(ops, delegate).getOrThrow();
                var reloaded = codec.parse(ops, encoded).getOrThrow();
                assertEquals(encoded, codec.encodeStart(ops, reloaded).getOrThrow(), style);
                for (var key : List.of("start_pool", "size", "start_height", "use_expansion_hack",
                        "project_start_to_heightmap", "max_distance_from_center", "terrain_adaptation", "biomes", "step")) {
                    assertEquals(original.get(key), encoded.getAsJsonObject().get(key), style + ": " + key);
                }
            }
        }
    }

    @Test void deferredLayoutCanBeMaterializedOnceBeforeImmutableStartSnapshot() {
        var calls = new AtomicInteger();
        var original = new ProbePiece(new BoundingBox(0, 60, 0, 10, 67, 10));
        var appended = new ProbePiece(new BoundingBox(40, 60, 0, 50, 67, 10));
        var position = new BlockPos(0, 60, 0);
        var deferred = new Structure.GenerationStub(position, builder -> {
            calls.incrementAndGet();
            builder.addPiece(original);
        });
        var builder = deferred.getPiecesBuilder();
        var vanillaSnapshot = builder.build();
        builder.addPiece(appended);
        var wrapped = new Structure.GenerationStub(deferred.position(), Either.right(builder));
        var finalSnapshot = wrapped.getPiecesBuilder().build();
        assertSame(builder, wrapped.getPiecesBuilder());
        assertEquals(1, calls.get());
        assertEquals(List.of(original), vanillaSnapshot.pieces());
        assertEquals(List.of(original, appended), finalSnapshot.pieces());
        assertSame(original, finalSnapshot.pieces().getFirst());
        assertEquals(position, wrapped.position());
        assertEquals(50, finalSnapshot.calculateBoundingBox().maxX());
    }

    private static final class ProbePiece extends StructurePiece {
        ProbePiece(BoundingBox box) { super(null, 0, box); }
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}
        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos origin) {
            throw new AssertionError("The API experiment must not place world blocks");
        }
    }
}

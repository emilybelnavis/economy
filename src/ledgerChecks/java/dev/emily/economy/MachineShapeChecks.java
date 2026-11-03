package dev.emily.economy;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;

/** The assembled four tiles must equal the entire model silhouette in each facing. */
public final class MachineShapeChecks {
    public static void main(String[] args) throws Exception {
        var catalog=JsonParser.parseString(Files.readString(Path.of("tools/machine_shapes.json"))).getAsJsonObject();
        int checks=0;
        for(var entry:catalog.entrySet()) for(Direction facing:Direction.Plane.HORIZONTAL) {
            VoxelShape expected=Shapes.empty(),assembled=Shapes.empty();
            for(var row:entry.getValue().getAsJsonArray()) {
                var b=row.getAsJsonArray();
                double x=b.get(0).getAsDouble()/16,y=b.get(1).getAsDouble()/16,z=b.get(2).getAsDouble()/16;
                double X=b.get(3).getAsDouble()/16,Y=b.get(4).getAsDouble()/16,Z=b.get(5).getAsDouble()/16;
                // Reference transform of the full 2x2 model around the anchor cell's centre.
                VoxelShape box=switch(facing) {
                    case EAST -> Shapes.box(1-Z,y,x,1-z,Y,X);
                    case SOUTH -> Shapes.box(1-X,y,1-Z,1-x,Y,1-z);
                    case WEST -> Shapes.box(z,y,1-X,Z,Y,1-x);
                    default -> Shapes.box(x,y,z,X,Y,Z);
                };
                expected=Shapes.or(expected,box);
            }
            for(boolean right:new boolean[]{false,true}) for(boolean upper:new boolean[]{false,true}) {
                VoxelShape tile=MachineShapes.part(entry.getKey(),facing,right,upper);
                if(tile.isEmpty()) throw new AssertionError("Missing model tile");
                var bounds=tile.bounds();
                if(bounds.minX<0 || bounds.minY<0 || bounds.minZ<0 || bounds.maxX>1 || bounds.maxY>1 || bounds.maxZ>1)
                    throw new AssertionError("Collision escapes tile");
                BlockPos cell=WideMachineLayout.cell(BlockPos.ZERO,facing,right,upper);
                assembled=Shapes.or(assembled,tile.move(cell.getX(),cell.getY(),cell.getZ()));
                checks+=2;
            }
            if(Shapes.joinIsNotEmpty(expected,assembled,BooleanOp.NOT_SAME)) throw new AssertionError(entry.getKey()+" collision seam/rotation "+facing);
            checks++;
        }
        System.out.println("Machine shape checks passed: "+checks);
    }
}

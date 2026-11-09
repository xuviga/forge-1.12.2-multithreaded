package net.minecraft.client.renderer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Merged far-ring LOD renderer. One VBO covers an 8x8 chunk region, so the distant ring costs one draw call per
 * region instead of one draw call per 16x16x16 section. Built from client heightmaps; full chunks inside the same
 * region are skipped while the LOD mesh is active.
 */
@SideOnly(Side.CLIENT)
public final class LodRenderer
{
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<Long, Region> regions = new HashMap<Long, Region>();

    public boolean isEnabled()
    {
        // The merged LOD path is experimental and was not viable on the Intel Iris test client with RD32.
        // It must be enabled explicitly in addition to setting a start distance.
        if (!Boolean.getBoolean("forge.threading.lodEnable"))
        {
            return false;
        }
        int start = Integer.getInteger("forge.threading.lodStartChunks", 0).intValue();
        return start > 0 && start < this.mc.gameSettings.renderDistanceChunks;
    }

    public int getRegionChunks()
    {
        return Math.max(4, Math.min(16, Integer.getInteger("forge.threading.lodRegionChunks", 8).intValue()));
    }

    public void markDirty(int blockX, int blockZ)
    {
        int size = this.getRegionChunks() * 16;
        int rx = Math.floorDiv(blockX, size);
        int rz = Math.floorDiv(blockZ, size);
        Region region = this.regions.get(Long.valueOf(ChunkPos.asLong(rx, rz)));
        if (region != null)
        {
            region.dirty = true;
        }
    }

    public void markChunkDirty(int chunkX, int chunkZ)
    {
        this.markDirty(chunkX << 4, chunkZ << 4);
    }

    public boolean isLodChunk(int chunkX, int chunkZ)
    {
        if (!this.isEnabled())
        {
            return false;
        }
        Region region = this.regions.get(Long.valueOf(ChunkPos.asLong(
                Math.floorDiv(chunkX, this.getRegionChunks()), Math.floorDiv(chunkZ, this.getRegionChunks()))));
        return region != null && region.built && region.nonEmpty && !region.dirty
                && this.isFarRegion(region) && this.isRegionLoaded(region);
    }

    private boolean isRegionLoaded(Region region)
    {
        int sizeChunks = this.getRegionChunks();
        int minX = region.regionX * sizeChunks;
        int minZ = region.regionZ * sizeChunks;
        for (int cx = minX; cx < minX + sizeChunks; ++cx)
        {
            for (int cz = minZ; cz < minZ + sizeChunks; ++cz)
            {
                net.minecraft.world.chunk.Chunk chunk = this.mc.world.getChunk(cx, cz);
                if (chunk == null || chunk.isEmpty() || !chunk.isTerrainPopulated() || !chunk.isLoaded())
                {
                    return false;
                }
            }
        }
        return true;
    }

    public void update(Entity view)
    {
        if (!this.isEnabled())
        {
            this.deleteAll();
            return;
        }

        int sizeChunks = this.getRegionChunks();
        int sizeBlocks = sizeChunks * 16;
        int playerChunkX = MathHelper.floor(view.posX) >> 4;
        int playerChunkZ = MathHelper.floor(view.posZ) >> 4;
        int renderDistance = this.mc.gameSettings.renderDistanceChunks;
        int minRegionX = Math.floorDiv(playerChunkX - renderDistance, sizeChunks) - 1;
        int maxRegionX = Math.floorDiv(playerChunkX + renderDistance, sizeChunks) + 1;
        int minRegionZ = Math.floorDiv(playerChunkZ - renderDistance, sizeChunks) - 1;
        int maxRegionZ = Math.floorDiv(playerChunkZ + renderDistance, sizeChunks) + 1;

        for (Iterator<Map.Entry<Long, Region>> iterator = this.regions.entrySet().iterator(); iterator.hasNext();)
        {
            Region region = iterator.next().getValue();
            if (region.regionX < minRegionX || region.regionX > maxRegionX
                    || region.regionZ < minRegionZ || region.regionZ > maxRegionZ)
            {
                if (region.displayList >= 0)
                {
                    GLAllocation.deleteDisplayLists(region.displayList);
                    region.displayList = -1;
                }
                iterator.remove();
            }
        }

        Region toBuild = null;
        for (int rx = minRegionX; rx <= maxRegionX; ++rx)
        {
            for (int rz = minRegionZ; rz <= maxRegionZ; ++rz)
            {
                long key = ChunkPos.asLong(rx, rz);
                Region region = this.regions.get(Long.valueOf(key));
                if (region == null)
                {
                    region = new Region(rx, rz, rx * sizeBlocks, rz * sizeBlocks, sizeBlocks);
                    this.regions.put(Long.valueOf(key), region);
                }
                if (this.isFarRegion(region) && (!region.built || region.dirty))
                {
                    if (this.isRegionLoaded(region))
                    {
                        toBuild = region;
                        break;
                    }
                    region.dirty = true;
                }
            }
            if (toBuild != null)
            {
                break;
            }
        }

        if (toBuild != null)
        {
            this.build(toBuild);
        }
    }

    private boolean isFarRegion(Region region)
    {
        int start = Integer.getInteger("forge.threading.lodStartChunks", 0).intValue();
        int sizeChunks = this.getRegionChunks();
        Entity view = this.mc.getRenderViewEntity();
        if (view == null || start <= 0)
        {
            return false;
        }

        int playerChunkX = MathHelper.floor(view.posX) >> 4;
        int playerChunkZ = MathHelper.floor(view.posZ) >> 4;
        int minX = region.regionX * sizeChunks;
        int maxX = minX + sizeChunks - 1;
        int minZ = region.regionZ * sizeChunks;
        int maxZ = minZ + sizeChunks - 1;
        int dx = Math.max(0, Math.max(minX - playerChunkX, playerChunkX - maxX));
        int dz = Math.max(0, Math.max(minZ - playerChunkZ, playerChunkZ - maxZ));
        return Math.sqrt((double)(dx * dx + dz * dz)) > (double)start;
    }

    private void build(Region region)
    {
        World world = this.mc.world;
        if (world == null)
        {
            return;
        }

        int step = Math.max(2, Math.min(16, Integer.getInteger("forge.threading.lodStep", 4).intValue()));
        BufferBuilder buffer = new BufferBuilder(1 << 20);
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        boolean nonEmpty = false;

        for (int x = 0; x < region.sizeBlocks; x += step)
        {
            for (int z = 0; z < region.sizeBlocks; z += step)
            {
                int worldX = region.originX + x;
                int worldZ = region.originZ + z;
                sample.setPos(worldX, 0, worldZ);
                int height = world.getHeight(worldX, worldZ);
                int topY = height - 1;
                if (topY < 0 || topY > 255)
                {
                    continue;
                }

                sample.setPos(worldX, topY, worldZ);
                IBlockState state = world.getBlockState(sample);
                if (state.getMaterial() == net.minecraft.block.material.Material.AIR)
                {
                    continue;
                }

                TextureAtlasSprite sprite;
                try
                {
                    sprite = this.mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(state);
                }
                catch (Throwable ignored)
                {
                    sprite = null;
                }
                if (sprite == null)
                {
                    continue;
                }

                MapColor mapColor = state.getBlock().getMapColor(state, world, sample);
                int color = mapColor == null ? 0xFFFFFF : mapColor.colorValue;
                float red = (float)(color >> 16 & 255) / 255.0F;
                float green = (float)(color >> 8 & 255) / 255.0F;
                float blue = (float)(color & 255) / 255.0F;
                int light = world.getCombinedLight(sample.up(), 0);

                double x0 = x;
                double x1 = x + step;
                double z0 = z;
                double z1 = z + step;
                double y = topY + 1.0D - region.originY;

                nonEmpty = true;
                this.emitQuad(buffer, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, sprite, red, green, blue, light);
                this.emitQuad(buffer, x1, y, z0, x1, y, z1, x0, y, z1, x0, y, z0, sprite, red, green, blue, light);

                int eastTop = world.getHeight(worldX + step, worldZ) - 1;
                int southTop = world.getHeight(worldX, worldZ + step) - 1;
                if (eastTop >= 0 && eastTop < topY)
                {
                    double ey = eastTop + 1.0D - region.originY;
                    this.emitQuad(buffer, x1, y, z0, x1, y, z1, x1, ey, z1, x1, ey, z0,
                            sprite, red * 0.8F, green * 0.8F, blue * 0.8F, light);
                }
                if (southTop >= 0 && southTop < topY)
                {
                    double sy = southTop + 1.0D - region.originY;
                    this.emitQuad(buffer, x0, y, z1, x1, y, z1, x1, sy, z1, x0, sy, z1,
                            sprite, red * 0.7F, green * 0.7F, blue * 0.7F, light);
                }
            }
        }

        buffer.finishDrawing();
        if (region.displayList >= 0)
        {
            GLAllocation.deleteDisplayLists(region.displayList);
        }
        region.displayList = GLAllocation.generateDisplayLists(1);
        GlStateManager.glNewList(region.displayList, 4864);
        WorldVertexBufferUploader uploader = new WorldVertexBufferUploader();
        uploader.draw(buffer);
        GlStateManager.glEndList();
        region.built = true;
        region.nonEmpty = nonEmpty;
        region.dirty = false;
    }

    private void emitQuad(BufferBuilder buffer, double x0, double y0, double z0,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, TextureAtlasSprite sprite,
            float red, float green, float blue, int light)
    {
        double u0 = sprite.getMinU();
        double v0 = sprite.getMinV();
        double u1 = sprite.getMaxU();
        double v1 = sprite.getMaxV();

        buffer.pos(x0, y0, z0).color(red, green, blue, 1.0F).tex(u0, v0).lightmap(light & 0xFFFF, light >>> 16).endVertex();
        buffer.pos(x1, y1, z1).color(red, green, blue, 1.0F).tex(u0, v1).lightmap(light & 0xFFFF, light >>> 16).endVertex();
        buffer.pos(x2, y2, z2).color(red, green, blue, 1.0F).tex(u1, v1).lightmap(light & 0xFFFF, light >>> 16).endVertex();
        buffer.pos(x3, y3, z3).color(red, green, blue, 1.0F).tex(u1, v0).lightmap(light & 0xFFFF, light >>> 16).endVertex();
    }

    public void render(Entity view, float partialTicks)
    {
        if (!this.isEnabled() || this.regions.isEmpty())
        {
            return;
        }

        this.mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        this.mc.entityRenderer.enableLightmap();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        double viewX = view.lastTickPosX + (view.posX - view.lastTickPosX) * (double)partialTicks;
        double viewY = view.lastTickPosY + (view.posY - view.lastTickPosY) * (double)partialTicks;
        double viewZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * (double)partialTicks;

        int drawLimit = Integer.getInteger("forge.threading.lodDrawLimit", Integer.MAX_VALUE).intValue();
        int drawn = 0;
        for (Region region : this.regions.values())
        {
            if (!region.built || !region.nonEmpty || region.displayList < 0 || !this.isFarRegion(region))
            {
                continue;
            }
            if (drawn++ >= drawLimit)
            {
                break;
            }

            GlStateManager.pushMatrix();
            GlStateManager.translate((float)(region.originX - viewX), (float)(region.originY - viewY), (float)(region.originZ - viewZ));
            GlStateManager.callList(region.displayList);
            GlStateManager.popMatrix();
        }

        GlStateManager.resetColor();
        GlStateManager.enableCull();
        this.mc.entityRenderer.disableLightmap();
    }

    private void setupArrayPointers()
    {
        GlStateManager.glVertexPointer(3, 5126, 28, 0);
        GlStateManager.glColorPointer(4, 5121, 28, 12);
        GlStateManager.glTexCoordPointer(2, 5126, 28, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, 5122, 28, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    public void deleteAll()
    {
        for (Region region : this.regions.values())
        {
            if (region.displayList >= 0)
            {
                GLAllocation.deleteDisplayLists(region.displayList);
                region.displayList = -1;
            }
        }
        this.regions.clear();
    }

    private static final class Region
    {
        private final int regionX;
        private final int regionZ;
        private final int originX;
        private final int originY = 0;
        private final int originZ;
        private final int sizeBlocks;
        private int displayList = -1;
        private boolean built;
        private boolean nonEmpty;
        private boolean dirty;

        private Region(int regionX, int regionZ, int originX, int originZ, int sizeBlocks)
        {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.originX = originX;
            this.originZ = originZ;
            this.sizeBlocks = sizeBlocks;
        }
    }
}

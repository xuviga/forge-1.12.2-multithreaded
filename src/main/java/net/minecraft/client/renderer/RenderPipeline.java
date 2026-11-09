/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 */

package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.common.threading.Threading;
import net.minecraftforge.common.threading.ThreadingTelemetry;

/**
 * CPU-side render pipeline.
 *
 * Minecraft 1.12.2/LWJGL2 requires the OpenGL context to stay on the client thread, so GL draw calls cannot be moved
 * to a generic Java thread. What can be moved is the per-frame CPU preparation: copying the entity list, filtering
 * dead entries, computing view-space distances and sorting the render candidates. That work now runs on a dedicated
 * "Render Prep Thread" while the client thread continues issuing GL calls for the previous frame.
 *
 * The client thread publishes a new request each frame (latest request wins, no unbounded queue). The worker
 * publishes an immutable PreparedFrame. RenderGlobal consumes it if it is for the same world/pass and not older than
 * 250 ms; otherwise the vanilla in-line candidate walk is used. This keeps correctness on world changes, pauses and
 * slow frames.
 *
 * -Dforge.threading.renderPipeline=false disables the pipeline; singleThreaded also disables it.
 */
public final class RenderPipeline
{
    private static final boolean ENABLED = !Threading.isSingleThreaded() &&
            Boolean.parseBoolean(System.getProperty("forge.threading.renderPipeline", "true"));
    private static final long FRAME_MAX_AGE_NANOS = 250000000L;
    private static final AtomicReference<Request> PENDING = new AtomicReference<Request>();
    private static final AtomicReference<PreparedFrame> LATEST = new AtomicReference<PreparedFrame>();
    private static final AtomicLong PREPARED = new AtomicLong();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    static
    {
        if (ENABLED)
        {
            Thread worker = new Thread(RenderPipeline::loop, "Render Prep Thread");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private RenderPipeline()
    {
    }

    public static boolean isEnabled()
    {
        return ENABLED;
    }

    /**
     * Publishes a new frame request. The entity list is copied on the client thread so the worker never touches the
     * live world lists.
     */
    public static void submit(World world, List<Entity> entities, double x, double y, double z, int pass)
    {
        if (!ENABLED || world == null)
        {
            return;
        }
        PENDING.set(new Request(world, new ArrayList<Entity>(entities), x, y, z, pass, SEQUENCE.incrementAndGet()));
    }

    public static PreparedFrame getLatest(World world, int pass)
    {
        if (!ENABLED)
        {
            return null;
        }
        ThreadingTelemetry.increment("render.requests");
        PreparedFrame frame = LATEST.get();
        if (frame == null)
        {
            ThreadingTelemetry.increment("render.fallback");
            ThreadingTelemetry.increment("render.fallback.null");
            return null;
        }
        if (frame.world != world)
        {
            ThreadingTelemetry.increment("render.fallback");
            ThreadingTelemetry.increment("render.fallback.world");
            return null;
        }
        if (System.nanoTime() - frame.preparedNanos > FRAME_MAX_AGE_NANOS)
        {
            ThreadingTelemetry.increment("render.fallback");
            ThreadingTelemetry.increment("render.fallback.age");
            return null;
        }
        // The worker copies the full entity list and does not filter by render pass, so the prepared frame is valid
        // for every pass. Pass was previously compared here and caused unnecessary fallbacks on multipass frames.
        ThreadingTelemetry.increment("render.used");
        return frame;
    }

    public static void appendOverlayLines(java.util.List<String> lines)
    {
        if (!ENABLED)
        {
            return;
        }
        PreparedFrame frame = LATEST.get();
        lines.add("[Render] prepared=" + PREPARED.get() + " cand=" + (frame == null ? 0 : frame.entities.size())
                + " pending=" + (PENDING.get() != null ? 1 : 0)
                + " used=" + ThreadingTelemetry.counter("render.used")
                + " fallback=" + ThreadingTelemetry.counter("render.fallback"));
        lines.add("[RenderFB] null=" + ThreadingTelemetry.counter("render.fallback.null")
                + " world=" + ThreadingTelemetry.counter("render.fallback.world")
                + " age=" + ThreadingTelemetry.counter("render.fallback.age")
                + " failed=" + ThreadingTelemetry.counter("render.failed"));
    }

    private static void loop()
    {
        while (true)
        {
            Request request = PENDING.getAndSet(null);
            if (request == null)
            {
                LockSupport.parkNanos(1000000L);
                continue;
            }

            net.minecraftforge.common.threading.BackgroundPacer.yieldIfNeeded();

            try
            {
                List<Entity> candidates = new ArrayList<Entity>(request.entities.size());
                for (int index = 0; index < request.entities.size(); index++)
                {
                    Entity entity = request.entities.get(index);
                    if (entity != null && !entity.isDead)
                    {
                        candidates.add(entity);
                    }
                }
                candidates.sort((first, second) -> Double.compare(
                        first.getDistanceSq(request.x, request.y, request.z),
                        second.getDistanceSq(request.x, request.y, request.z)));
                LATEST.set(new PreparedFrame(request.world, request.pass, candidates, System.nanoTime(),
                        request.sequence));
                long count = PREPARED.incrementAndGet();
                ThreadingTelemetry.increment("render.prepared");
                if (count % 1000L == 0L)
                {
                    org.apache.logging.log4j.LogManager.getLogger("ForgeThreading").info(
                            "[render-pipeline] prepared {} frames, latest candidates={}", Long.valueOf(count),
                            Integer.valueOf(candidates.size()));
                }
            }
            catch (Throwable throwable)
            {
                ThreadingTelemetry.increment("render.failed");
                org.apache.logging.log4j.LogManager.getLogger("ForgeThreading").error(
                        "[render-pipeline] frame preparation failed", throwable);
            }
        }
    }

    private static final class Request
    {
        private final World world;
        private final List<Entity> entities;
        private final double x;
        private final double y;
        private final double z;
        private final int pass;
        private final long sequence;

        private Request(World world, List<Entity> entities, double x, double y, double z, int pass, long sequence)
        {
            this.world = world;
            this.entities = entities;
            this.x = x;
            this.y = y;
            this.z = z;
            this.pass = pass;
            this.sequence = sequence;
        }
    }

    public static final class PreparedFrame
    {
        public final World world;
        public final int pass;
        public final List<Entity> entities;
        public final long preparedNanos;
        public final long sequence;

        private PreparedFrame(World world, int pass, List<Entity> entities, long preparedNanos, long sequence)
        {
            this.world = world;
            this.pass = pass;
            this.entities = entities;
            this.preparedNanos = preparedNanos;
            this.sequence = sequence;
        }
    }
}

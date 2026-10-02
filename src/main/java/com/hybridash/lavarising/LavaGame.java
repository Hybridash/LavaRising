package com.hybridash.lavarising;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.border.WorldBorder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One round of Lava Rising. */
public class LavaGame {
	private final MinecraftServer server;
	private final ServerWorld world;
	private final int centerX;
	private final int centerZ;
	private final int size;
	private final int riseTicks;
	private final int graceTicks;
	private final int topY;

	private final double oldBorderSize;
	private final double oldBorderX;
	private final double oldBorderZ;

	private final Set<UUID> alive = new HashSet<>();
	private final Set<UUID> eliminated = new HashSet<>();
	private final ServerBossBar bar = new ServerBossBar(Text.literal("Lava Rising"), BossBar.Color.RED, BossBar.Style.PROGRESS);

	private int lavaY;          // highest layer that is lava (starts one below the first layer)
	private int ticks = 0;      // ticks since start
	private int nextRise;       // tick of the next rise
	private boolean finished = false;

	public LavaGame(MinecraftServer server, ServerWorld world, BlockPos center, int size, int riseSeconds, int graceSeconds) {
		this.server = server;
		this.world = world;
		this.centerX = center.getX();
		this.centerZ = center.getZ();
		this.size = size;
		this.riseTicks = riseSeconds * 20;
		this.graceTicks = graceSeconds * 20;
		this.topY = world.getTopY() - 1;

		WorldBorder border = world.getWorldBorder();
		oldBorderSize = border.getSize();
		oldBorderX = border.getCenterX();
		oldBorderZ = border.getCenterZ();

		// Start the lava a bit below the lowest ground in the arena, so nobody is underwater-in-lava at the start
		int lowest = Integer.MAX_VALUE;
		int half = size / 2;
		for (int x = centerX - half; x < centerX + half; x += 4) {
			for (int z = centerZ - half; z < centerZ + half; z += 4) {
				lowest = Math.min(lowest, world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z));
			}
		}
		this.lavaY = Math.max(world.getBottomY(), lowest - 8);
		this.nextRise = graceTicks;
	}

	public void start() {
		WorldBorder border = world.getWorldBorder();
		border.setCenter(centerX + 0.5, centerZ + 0.5);
		border.setSize(size);

		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.isSpectator()) continue;
			alive.add(p.getUuid());
			bar.addPlayer(p);
			if (!border.contains(p.getBlockPos())) {
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
				p.teleport(world, centerX + 0.5, y, centerZ + 0.5, p.getYaw(), p.getPitch());
			}
			title(p, Text.literal("Lava Rising").formatted(Formatting.GOLD, Formatting.BOLD),
					Text.literal("Get high. Last one alive wins.").formatted(Formatting.YELLOW));
		}
		broadcast(Text.literal("Lava Rising started! ").formatted(Formatting.GOLD)
				.append(Text.literal(alive.size() + " players, " + size + "×" + size + " arena. Lava starts rising in "
						+ graceTicks / 20 + " seconds, then every " + riseTicks / 20 + "s.").formatted(Formatting.YELLOW)));
		updateBar();
	}

	/** Called every server tick. Returns false when the game is over. */
	public boolean tick() {
		if (finished) return false;
		ticks++;

		if (ticks >= nextRise && lavaY < topY) {
			lavaY++;
			fillLayer(lavaY);
			nextRise = ticks + riseTicks;
			if (lavaY % 5 == 0) {
				for (ServerPlayerEntity p : world.getPlayers()) {
					p.playSoundToPlayer(SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.BLOCKS, 1f, 0.8f);
				}
			}
		}

		if (ticks % 20 == 0) {
			// Anyone hiding in a hole under the lava gets burned out
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (alive.contains(p.getUuid()) && p.getY() < lavaY - 1 && !p.isInLava()) {
					p.setOnFireFor(4);
					p.damage(world.getDamageSources().lava(), 2f);
				}
			}
			// Players who left the server count as out
			alive.removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
			updateBar();
			checkWinner();
		}
		return !finished;
	}

	private void fillLayer(int y) {
		BlockState lava = Blocks.LAVA.getDefaultState();
		int half = size / 2;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = centerX - half; x < centerX + half; x++) {
			for (int z = centerZ - half; z < centerZ + half; z++) {
				pos.set(x, y, z);
				BlockState state = world.getBlockState(pos);
				// Only fill empty space and water, so player-built towers and the terrain stay
				if (state.isAir() || state.getFluidState().isOf(Fluids.WATER) || state.getFluidState().isOf(Fluids.FLOWING_WATER)
						|| state.isReplaceable()) {
					world.setBlockState(pos, lava, 2);
				}
			}
		}
	}

	public void onDeath(ServerPlayerEntity player) {
		if (!alive.remove(player.getUuid())) return;
		eliminated.add(player.getUuid());
		broadcast(Text.literal("☠ " + player.getName().getString() + " is out! ").formatted(Formatting.RED)
				.append(Text.literal(alive.size() + " left.").formatted(Formatting.GRAY)));
		checkWinner();
	}

	/** After respawning, eliminated players watch as spectators. */
	public void onRespawn(ServerPlayerEntity player) {
		if (eliminated.contains(player.getUuid())) {
			player.changeGameMode(GameMode.SPECTATOR);
			bar.addPlayer(player);
		}
	}

	private void checkWinner() {
		if (finished) return;
		if (alive.size() == 1) {
			ServerPlayerEntity winner = server.getPlayerManager().getPlayer(alive.iterator().next());
			String name = winner != null ? winner.getName().getString() : "Someone";
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				title(p, Text.literal(name + " wins!").formatted(Formatting.GOLD, Formatting.BOLD),
						Text.literal("Survived to Y=" + lavaY).formatted(Formatting.YELLOW));
				p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f);
			}
			broadcast(Text.literal("★ " + name + " wins Lava Rising! The lava reached Y=" + lavaY + ".").formatted(Formatting.GOLD));
			stop(false);
		} else if (alive.isEmpty()) {
			broadcast(Text.literal("Everyone's out. Nobody wins this one.").formatted(Formatting.RED));
			stop(false);
		}
	}

	/** Ends the game and puts the world border back. The lava stays (it's your world now). */
	public void stop(boolean announce) {
		if (finished) return;
		finished = true;
		bar.clearPlayers();
		WorldBorder border = world.getWorldBorder();
		border.setCenter(oldBorderX, oldBorderZ);
		border.setSize(oldBorderSize);
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (eliminated.contains(p.getUuid()) && p.isSpectator()) p.changeGameMode(GameMode.SURVIVAL);
		}
		if (announce) broadcast(Text.literal("Lava Rising stopped.").formatted(Formatting.GRAY));
	}

	private void updateBar() {
		int secondsLeft = Math.max(0, (nextRise - ticks) / 20);
		String phase = ticks < graceTicks ? "Lava starts in " + secondsLeft + "s" : "Next rise in " + secondsLeft + "s";
		bar.setName(Text.literal("Lava: Y=" + lavaY + "  ·  " + phase + "  ·  " + alive.size() + " alive"));
		int span = ticks < graceTicks ? graceTicks : riseTicks;
		bar.setPercent(Math.max(0f, Math.min(1f, (nextRise - ticks) / (float) span)));
	}

	private void title(ServerPlayerEntity p, Text title, Text subtitle) {
		p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		p.networkHandler.sendPacket(new TitleS2CPacket(title));
	}

	private void broadcast(Text text) {
		server.getPlayerManager().broadcast(text, false);
	}

	public ServerWorld world() {
		return world;
	}

	public List<String> status() {
		return List.of("Lava at Y=" + lavaY, alive.size() + " players alive", "Arena " + size + "×" + size + " around " + centerX + ", " + centerZ);
	}
}

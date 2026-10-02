package com.hybridash.lavarising;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lava Rising minigame. /lava start [size] [seconds between rises] [grace seconds]
 */
public class LavaRising implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("LavaRising");

	private static LavaGame game;

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (game != null && !game.tick()) game = null;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (game != null && entity instanceof ServerPlayerEntity player) game.onDeath(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (game != null) game.onRespawn(newPlayer);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (game != null) game.stop(false);
			game = null;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("lava")
						.requires(src -> src.hasPermissionLevel(2))
						.then(CommandManager.literal("start")
								.executes(ctx -> start(ctx, 64, 15, 60))
								.then(CommandManager.argument("size", IntegerArgumentType.integer(16, 512))
										.executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "size"), 15, 60))
										.then(CommandManager.argument("riseSeconds", IntegerArgumentType.integer(1, 600))
												.executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "size"),
														IntegerArgumentType.getInteger(ctx, "riseSeconds"), 60))
												.then(CommandManager.argument("graceSeconds", IntegerArgumentType.integer(0, 1800))
														.executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "size"),
																IntegerArgumentType.getInteger(ctx, "riseSeconds"),
																IntegerArgumentType.getInteger(ctx, "graceSeconds")))))))
						.then(CommandManager.literal("stop").executes(ctx -> {
							if (game == null) {
								ctx.getSource().sendError(Text.literal("No Lava Rising game is running."));
								return 0;
							}
							game.stop(true);
							game = null;
							return 1;
						}))
						.then(CommandManager.literal("status").executes(ctx -> {
							if (game == null) {
								ctx.getSource().sendFeedback(() -> Text.literal("No game running. /lava start [size] [riseSeconds] [graceSeconds]").formatted(Formatting.GRAY), false);
							} else {
								for (String line : game.status()) {
									ctx.getSource().sendFeedback(() -> Text.literal(line).formatted(Formatting.GOLD), false);
								}
							}
							return 1;
						}))));
		LOGGER.info("LavaRising loaded. /lava start to play.");
	}

	private static int start(CommandContext<ServerCommandSource> ctx, int size, int riseSeconds, int graceSeconds) {
		ServerCommandSource src = ctx.getSource();
		if (game != null) {
			src.sendError(Text.literal("A game is already running. /lava stop first."));
			return 0;
		}
		BlockPos center = BlockPos.ofFloored(src.getPosition());
		game = new LavaGame(src.getServer(), src.getWorld(), center, size, riseSeconds, graceSeconds);
		game.start();
		return 1;
	}
}

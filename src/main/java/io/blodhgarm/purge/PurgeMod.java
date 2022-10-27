package io.blodhgarm.purge;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.EntitySummonArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class PurgeMod implements ModInitializer {

	private static final String ENTITY_TYPE_ARGUMENT_KEY = "entity_type";
	private static final String RANGE_ARGUMENT_KEY = "range";
	private static final String MURDER_PETS_KEY = "murder_pets";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
			dispatcher.register(
					CommandManager.literal("purge")
							.requires(source -> source.hasPermissionLevel(2))
							.then(CommandManager.argument(ENTITY_TYPE_ARGUMENT_KEY, EntitySummonArgumentType.entitySummon())
									.suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
									.executes(context -> {
										return purgeEntities(context, EntitySummonArgumentType.getEntitySummon(context, ENTITY_TYPE_ARGUMENT_KEY), -1, false);
									})
									.then(
											CommandManager.argument(RANGE_ARGUMENT_KEY, IntegerArgumentType.integer(-1))
													.executes(context -> {
														return purgeEntities(context,
																EntitySummonArgumentType.getEntitySummon(context, ENTITY_TYPE_ARGUMENT_KEY),
																IntegerArgumentType.getInteger(context, RANGE_ARGUMENT_KEY),
																false
														);
													}).then(
															CommandManager.argument(MURDER_PETS_KEY, BoolArgumentType.bool()).executes(context -> {
																return purgeEntities(context,
																		EntitySummonArgumentType.getEntitySummon(context, ENTITY_TYPE_ARGUMENT_KEY),
																		IntegerArgumentType.getInteger(context, RANGE_ARGUMENT_KEY),
																		BoolArgumentType.getBool(context, MURDER_PETS_KEY)
																);
															})
													)
									)
							)

			);
		});
	}

	private static <T extends Entity> int purgeEntities(CommandContext<ServerCommandSource> context, Identifier entityTypeId, int range, boolean muderPets){
		ServerCommandSource source = context.getSource();

		var entityType = Registry.ENTITY_TYPE.getOrEmpty(entityTypeId);

		if(entityType.isEmpty()){
			source.sendError(Text.of("The given entity type was not found within the Entity Type Registry"));

			return -1;
		}

		Predicate<Entity> testForEntity = entity -> {
			if(muderPets) return true;

			if(entity instanceof TameableEntity tameableEntity && tameableEntity.isTamed()) return false;

			NbtCompound tag = new NbtCompound();

			entity.writeNbt(tag);

			return !tag.contains("Owner");
		};

		List<T> targets;

		if(range == -1){
			targets = (List<T>) context.getSource().getWorld().getEntitiesByType(entityType.get(), testForEntity);
		} else {
			targets = (List<T>) context.getSource().getWorld().getEntitiesByType(entityType.get(), Box.from(context.getSource().getPosition()).expand(range), testForEntity);
		}

		targets.forEach(Entity::kill);

		if (targets.size() == 1) {
			source.sendFeedback(new TranslatableText("commands.kill.success.single", targets.iterator().next().getDisplayName()), true);
		} else {
			source.sendFeedback(new TranslatableText("commands.kill.success.multiple", targets.size()), true);
		}

		return targets.size();
	}
}

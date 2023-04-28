package io.blodhgarm.purge;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntitySummonArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.function.Predicate;

public class PurgeMod implements ModInitializer {

	private static final String ENTITY_TYPE_ARGUMENT_KEY = "entity_type";
	private static final String RANGE_ARGUMENT_KEY = "range";
	private static final String MURDER_PETS_KEY = "murder_pets";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) -> {
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

	private static <T extends Entity> int purgeEntities(CommandContext<ServerCommandSource> context, Identifier entityTypeId, int range, boolean murderPets){
		ServerCommandSource source = context.getSource();

		var entityType = Registry.ENTITY_TYPE.getOrEmpty(entityTypeId);

		if(entityType.isEmpty()){
			source.sendError(Text.of("The given entity type was not found within the Entity Type Registry"));

			return -1;
		}

		Predicate<Entity> testForEntity = e -> {
			if(murderPets) return true;

			if((e instanceof TameableEntity te && te.isTamed()) || e.hasCustomName()) return false;

			return !(e.writeNbt(new NbtCompound()))
					.contains("Owner");
		};

		List<T> targets =  (List<T>) ((range == -1)
				? context.getSource().getWorld().getEntitiesByType(entityType.get(), testForEntity)
				: context.getSource().getWorld().getEntitiesByType(entityType.get(), Box.from(context.getSource().getPosition()).expand(range), testForEntity));

		targets.forEach(Entity::kill);

		Text feedbackMSG = targets.size() == 1
				? Text.translatable("commands.kill.success.single", targets.iterator().next().getDisplayName())
				: Text.translatable("commands.kill.success.multiple", targets.size());

		source.sendFeedback(feedbackMSG, true);

		return targets.size();
	}
}

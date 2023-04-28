package io.blodhgarm.purge;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.function.Predicate;

public class PurgeMod implements ModInitializer {

	private static final String ENTITY_TYPE_ARGUMENT_KEY = "entity_type";
	private static final String RANGE_ARGUMENT_KEY = "range";
	private static final String MURDER_PETS_KEY = "murder_pets";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			dispatcher.register(
					CommandManager.literal("purge")
							.requires(source -> source.hasPermissionLevel(2))
							.then(CommandManager.argument(ENTITY_TYPE_ARGUMENT_KEY, RegistryEntryArgumentType.registryEntry(access, RegistryKeys.ENTITY_TYPE))
									.suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
									.executes(context -> {
										return purgeEntities(context, RegistryEntryArgumentType.getSummonableEntityType(context, ENTITY_TYPE_ARGUMENT_KEY), -1, false);
									})
									.then(
											CommandManager.argument(RANGE_ARGUMENT_KEY, IntegerArgumentType.integer(-1))
													.executes(context -> {
														return purgeEntities(context,
																RegistryEntryArgumentType.getSummonableEntityType(context, ENTITY_TYPE_ARGUMENT_KEY),
																IntegerArgumentType.getInteger(context, RANGE_ARGUMENT_KEY),
																false
														);
													}).then(
															CommandManager.argument(MURDER_PETS_KEY, BoolArgumentType.bool()).executes(context -> {
																return purgeEntities(context,
																		RegistryEntryArgumentType.getSummonableEntityType(context, ENTITY_TYPE_ARGUMENT_KEY),
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

	private static <T extends Entity> int purgeEntities(CommandContext<ServerCommandSource> context, RegistryEntry.Reference<EntityType<?>> reference, int range, boolean murderPets){
		ServerCommandSource source = context.getSource();

		var entityType = Registries.ENTITY_TYPE.get(reference.registryKey());

		Predicate<Entity> testForEntity = e -> {
			if(murderPets) return true;

			if((e instanceof TameableEntity te && te.isTamed()) || e.hasCustomName()) return false;

			return !(e.writeNbt(new NbtCompound()))
					.contains("Owner");
		};

		List<T> targets =  (List<T>) ((range == -1)
				? context.getSource().getWorld().getEntitiesByType(entityType, testForEntity)
				: context.getSource().getWorld().getEntitiesByType(entityType, Box.from(context.getSource().getPosition()).expand(range), testForEntity));

		targets.forEach(Entity::kill);

		Text feedbackMSG = targets.size() == 1
				? Text.translatable("commands.kill.success.single", targets.iterator().next().getDisplayName())
				: Text.translatable("commands.kill.success.multiple", targets.size());

		source.sendFeedback(feedbackMSG, true);

		return targets.size();
	}
}

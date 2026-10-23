package com.cursecycle;

import com.mojang.authlib.GameProfile;
import com.cursecycle.challenge.ChallengeRegistry;
import com.cursecycle.challenge.impl.ArmorDisabledChallenge;
import com.cursecycle.state.CurseCycleState;
import com.cursecycle.state.CycleManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.util.UUID;

public class ArmorDisabledGameTest implements FabricGameTest {
    // One test owns the global state, avoiding interference between parallel game tests.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void armorAndElytraLifecycle(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        var state = CurseCycleState.getServerState(server);
        var challenge = (ArmorDisabledChallenge) ChallengeRegistry.get(ArmorDisabledChallenge.ID);
        ServerPlayerEntity player = createPlayer(context);
        var enchantments = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        helmet.addEnchantment(enchantments.entryOf(Enchantments.PROTECTION), 4);
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
        boots.addEnchantment(enchantments.entryOf(Enchantments.FEATHER_FALLING), 4);
        player.equipStack(EquipmentSlot.HEAD, helmet);
        player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        player.equipStack(EquipmentSlot.FEET, boots);

        // Apply vanilla equipment attribute tracking before comparing damage.
        CycleManager.activateChallenge(server, state, "test_easy");
        player.playerTick();
        context.assertTrue(player.getArmor() > 0, "Control: equipped armor must work before activation");
        DamageSource mobAttack = player.getDamageSources().mobAttack(new ZombieEntity(EntityType.ZOMBIE, world));
        float protectedDamage = damage(player, mobAttack, 8);
        context.assertTrue(protectedDamage < 8, "Control: armor must reduce mob damage");

        var armorAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        var foreignModifier = new EntityAttributeModifier(Identifier.of("test", "foreign_armor"),
                3, EntityAttributeModifier.Operation.ADD_VALUE);
        armorAttribute.addTemporaryModifier(foreignModifier);
        double originalAttribute = armorAttribute.getValue();

        CycleManager.activateChallenge(server, state, ArmorDisabledChallenge.ID);
        challenge.onStart(server, player);
        context.assertEquals(0, player.getArmor(), "Active challenge must expose zero armor");
        context.assertEquals(8.0f, damage(player, player.getDamageSources().generic(), 8),
                "Armor and Protection must not reduce incoming damage");
        context.assertEquals(8.0f, damage(player, mobAttack, 8), "Mob damage must ignore armor");
        context.assertEquals(8.0f, damage(player, player.getDamageSources().fall(), 8),
                "Feather Falling must not reduce fall damage");
        context.assertEquals(8.0f, damage(player, player.getDamageSources().fallingAnvil(mobAttack.getAttacker()), 8),
                "Helmet must not protect from falling anvils");
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
        context.assertTrue(Math.abs(damage(player, player.getDamageSources().generic(), 8) - 6.4f) < 0.001f,
                "Resistance from other sources must still work");
        player.removeStatusEffect(StatusEffects.RESISTANCE);
        context.assertEquals(originalAttribute, armorAttribute.getValue(), "Attributes must remain untouched");
        context.assertTrue(armorAttribute.hasModifier(foreignModifier.id()), "Foreign modifier must remain");
        context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD) == helmet, "Armor must stay equipped");

        player.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        player.setOnGround(false);
        context.assertFalse(player.checkFallFlying(), "Elytra takeoff must be rejected");
        player.startFallFlying();
        context.assertFalse(player.isFallFlying(), "Direct flight activation must be rejected");

        ServerPlayerEntity latePlayer = createPlayer(context);
        challenge.onPlayerJoin(server, latePlayer);
        context.assertTrue(ArmorDisabledChallenge.isActiveForPlayer(latePlayer),
                "A new player object must immediately inherit the global restriction");

        CycleManager.activateChallenge(server, state, "double_challenge:armor_disabled,no_sprint");
        context.assertTrue(ArmorDisabledChallenge.isActiveForPlayer(player), "Double challenge must include armor restriction");
        CycleManager.activateChallenge(server, state, "test_easy");
        context.assertFalse(ArmorDisabledChallenge.isActiveForPlayer(player), "Changing challenge must remove restriction");
        context.assertTrue(player.getArmor() > 0, "Armor must work again");
        context.assertTrue(damage(player, player.getDamageSources().fall(), 8) < 8, "Feather Falling must work again");
        player.setOnGround(false);
        context.assertTrue(player.checkFallFlying(), "Elytra must work again");
        CycleManager.activateChallenge(server, state, ArmorDisabledChallenge.ID);
        challenge.onStart(server, player);
        context.assertFalse(player.isFallFlying(), "Activation must interrupt existing flight");

        challenge.setEnabled(false);
        context.assertFalse(ArmorDisabledChallenge.isActiveForPlayer(player), "Disabled challenge must have no effect");
        challenge.setEnabled(true);
        CycleManager.activateChallenge(server, state, "test_easy");
        context.complete();
    }

    private static float damage(ServerPlayerEntity player, DamageSource source, float amount) {
        player.setHealth(20);
        player.timeUntilRegen = 0;
        player.damage(source, amount);
        return 20 - player.getHealth();
    }

    private static ServerPlayerEntity createPlayer(TestContext context) {
        var server = context.getWorld().getServer();
        var client = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "armor-test"), false);
        var player = new ServerPlayerEntity(server, context.getWorld(), client.gameProfile(), client.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player, client);
        player.changeGameMode(GameMode.SURVIVAL);
        // Expire the vanilla login invulnerability before damage assertions.
        for (int i = 0; i < 61; i++) player.tick();
        return player;
    }
}

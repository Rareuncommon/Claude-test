package com.rareuncommon.skyblockvaluealerts.client.mixin;

import com.rareuncommon.skyblockvaluealerts.client.ItemValueChecker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.item.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ItemPickupMixin {
	/**
	 * Fires when the server confirms an item entity was collected. The injection point is
	 * inside the {@code instanceof ItemEntity} branch of the handler, after the packet has
	 * been re-dispatched to the main thread, so the entity still exists in the world here.
	 */
	@Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getItem()Lnet/minecraft/world/item/ItemStack;"))
	private void skyblock_value_alerts$onItemPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return;
		if (packet.getPlayerId() != minecraft.player.getId()) return;
		if (minecraft.level.getEntity(packet.getItemId()) instanceof ItemEntity itemEntity) {
			ItemValueChecker.onItemPickedUp(itemEntity.getItem(), packet.getAmount());
		}
	}
}

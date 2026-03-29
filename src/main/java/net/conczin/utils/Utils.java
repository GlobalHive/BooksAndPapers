package net.conczin.utils;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import net.conczin.gui.BookUISupplier;

import java.lang.reflect.Field;
import java.util.UUID;

public class Utils {
    public static UUID getUUID(Ref<EntityStore> ref) {
        UUIDComponent uuidComponent = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        assert uuidComponent != null;
        return uuidComponent.getUuid();
    }

    public static <T> void setData(Ref<EntityStore> ref, BlockPosition block, String field, BuilderCodec<T> codec, T data) {
        if (block == null) {
            Inventory inventory = getInventory(ref);
            ItemStack itemInHand = inventory.getActiveHotbarItem();
            if (itemInHand != null) {
                ItemStack newItemInHand = itemInHand.withMetadata(field, codec, data);
                inventory.getHotbar().replaceItemStackInSlot(inventory.getActiveHotbarSlot(), itemInHand, newItemInHand);
            }
        } else {
            World world = ref.getStore().getExternalData().getWorld();
            ItemStack stack = getItemFromContainer(world, block, 0);
            if (stack != null) {
                ItemStack newStack = stack.withMetadata(field, codec, data);
                ItemContainerBlock inventory = getInventory(world, block);
                if (inventory != null) {
                    inventory.getItemContainer().setItemStackForSlot((short) 0, newStack);
                }
            }
        }
    }

    public static <T> T getData(Ref<EntityStore> ref, BlockPosition block, String field, BuilderCodec<T> codec) {
        ItemStack stack;
        if (block == null) {
            Inventory inventory = getInventory(ref);
            stack = inventory.getActiveHotbarItem();
        } else {
            World world = ref.getStore().getExternalData().getWorld();
            stack = getItemFromContainer(world, block, 0);
        }
        if (stack != null) {
            return stack.getFromMetadataOrDefault(field, codec);
        }
        return codec.getDefaultValue();
    }

    public static Inventory getInventory(Ref<EntityStore> ref) {
        Player player = ref.getStore().getComponent(ref, Player.getComponentType());
        assert player != null;
        return player.getInventory();
    }


    public static <T> T get(Object target, String fieldName, Class<T> type) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object value = f.get(target);
            return type.cast(value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static ItemContainerBlock getInventory(World world, BlockPosition targetBlock) {
        int x = targetBlock.x;
        int y = targetBlock.y;
        int z = targetBlock.z;

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }

        Store<ChunkStore> chunkStoreData = chunkStore.getStore();
        BlockChunk blockChunk = chunkStoreData.getComponent(chunkRef, BlockChunk.getComponentType());
        BlockComponentChunk blockComponentChunk = chunkStoreData.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockChunk == null || blockComponentChunk == null) {
            return null;
        }

        BlockSection section = blockChunk.getSectionAtBlockY(y);
        if (section == null) {
            return null;
        }

        int filler = section.getFiller(x, y, z);
        if (filler != 0) {
            x -= FillerBlockUtil.unpackX(filler);
            y -= FillerBlockUtil.unpackY(filler);
            z -= FillerBlockUtil.unpackZ(filler);
        }

        Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(x, y, z));
        if (blockRef == null) {
            return null;
        }

        return chunkStoreData.getComponent(blockRef, ItemContainerBlock.getComponentType());
    }

    public static ItemStack getItemFromContainer(World world, BlockPosition targetBlock, int slot) {
        ItemContainerBlock inventory = getInventory(world, targetBlock);
        if (inventory != null) {
            return inventory.getItemContainer().getItemStack((short) slot);
        }
        return null;
    }

    public static BookUISupplier getBookSupplier(ItemStack itemStack) {
        String rootInteraction = itemStack.getItem().getInteractions().get(InteractionType.Secondary);
        RootInteraction asset = RootInteraction.getAssetMap().getAsset(rootInteraction);
        if (asset == null) return null;
        for (String interactionId : asset.getInteractionIds()) {
            Interaction interaction = Interaction.getAssetMap().getAsset(interactionId);
            if (interaction instanceof OpenCustomUIInteraction openCustomUIInteraction) {
                if (Utils.get(openCustomUIInteraction, "customPageSupplier", OpenCustomUIInteraction.CustomPageSupplier.class) instanceof BookUISupplier supplier) {
                    return supplier;
                }
            }
        }
        return null;
    }
}

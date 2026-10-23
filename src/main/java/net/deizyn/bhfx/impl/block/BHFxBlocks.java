package net.deizyn.bhfx.impl.block;

import net.deizyn.bhfx.BHFxMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The few blocks bhfx ships. Everything visual about them is client-side (see {@code MirrorRenderer}). */
public final class BHFxBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BHFxMod.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BHFxMod.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BHFxMod.MODID);

    public static final RegistryObject<MirrorBlock> MIRROR = BLOCKS.register("mirror", () -> new MirrorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.GLASS)));
    public static final RegistryObject<BlockItem> MIRROR_ITEM = ITEMS.register("mirror",
            () -> new BlockItem(MIRROR.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MirrorBlockEntity>> MIRROR_ENTITY = BLOCK_ENTITIES.register("mirror",
            () -> BlockEntityType.Builder.of(MirrorBlockEntity::new, MIRROR.get()).build(null));

    private BHFxBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(BHFxBlocks::fillTabs);
    }

    private static void fillTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MIRROR_ITEM);
        }
    }
}

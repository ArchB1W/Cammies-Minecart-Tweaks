package dev.cammiescorner.cammiesminecarttweaks.mixin;

import dev.cammiescorner.cammiesminecarttweaks.MinecartTweaks;
import dev.cammiescorner.cammiesminecarttweaks.utils.Linkable;
import dev.cammiescorner.cammiesminecarttweaks.utils.MinecartHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(FurnaceMinecartEntity.class)
public abstract class FurnaceMinecartEntityMixin extends AbstractMinecartEntity implements Linkable {
	@Shadow protected abstract boolean isLit();
	@Shadow private int fuel;
	@Shadow public double pushX;
	@Shadow public double pushZ;
	@Shadow @Final @Mutable private static Ingredient ACCEPTABLE_FUEL;
	@Shadow public abstract ActionResult interact(PlayerEntity player, Hand hand);

	@Unique private int altFuel;
	@Unique private double altPushX;
	@Unique private double altPushZ;
	@Unique private static final Ingredient OLD_ACCEPTABLE_FUEL = ACCEPTABLE_FUEL;

	protected FurnaceMinecartEntityMixin(EntityType<?> entityType, World world) { super(entityType, world); }

	@Inject(method = "getMaxOffRailSpeed", at = @At("RETURN"), cancellable = true)
	public void minecarttweaks$increaseSpeed(CallbackInfoReturnable<Double> info) {
		if(isLit())
			info.setReturnValue(super.getMaxOffRailSpeed() * MinecartTweaks.getConfig().getFurnaceSpeedMultiplier());
		else
			info.setReturnValue(super.getMaxOffRailSpeed());
	}

	@Inject(method = "moveOnRail", at = @At("TAIL"))
	public void minecarttweaks$slowDown(BlockPos pos, BlockState state, CallbackInfo info) {
		if(MinecartTweaks.getConfig().commonTweaks.shouldPoweredRailsStopFurnace) {
			if(altFuel <= 0 && fuel > 0) {
				if(state.isOf(Blocks.POWERED_RAIL) && !state.get(PoweredRailBlock.POWERED)) {
					altPushX = pushX;
					altPushZ = pushZ;
					altFuel += fuel;
					fuel = 0;
				}
			}
			else if(!state.isOf(Blocks.POWERED_RAIL) || (state.isOf(Blocks.POWERED_RAIL) && state.get(PoweredRailBlock.POWERED))) {
				fuel += altFuel;
				altFuel = 0;
				pushX = altPushX;
				pushZ = altPushZ;
			}
		}

		AtomicBoolean shouldSlowDown = new AtomicBoolean(MinecartHelper.shouldSlowDown(this, world));

		if(getLinkedChild() != null) {
			Linkable linkable = (Linkable) getLinkedChild();
			Set<Linkable> train = new HashSet<>();
			train.add(linkable);

			while((linkable = (Linkable) linkable.getLinkedChild()) instanceof Linkable && !train.contains(linkable)) {
				train.add(linkable);
			}

			train.forEach(child -> shouldSlowDown.set(shouldSlowDown.get() || MinecartHelper.shouldSlowDown((AbstractMinecartEntity) child, world)));
		}

		if(shouldSlowDown.get() && getVelocity().length() > MinecartTweaks.getConfig().getMinecartBaseSpeed())
			setVelocity(getVelocity().normalize().multiply(MinecartTweaks.getConfig().getMinecartBaseSpeed()));
	}

	@Inject(method = "interact", at = @At("HEAD"))
	public void minecarttweaks$addOtherFuels(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> info) {
		if(MinecartTweaks.getConfig().commonTweaks.furnacesCanUseAllFuels) {
			ItemStack stack = player.getStackInHand(hand);
			Map<Item, Integer> fuels = AbstractFurnaceBlockEntity.createFuelTimeMap();

			if(fuels.containsKey(stack.getItem())) {
				int fuelTime = fuels.getOrDefault(stack.getItem(), 0);

				if(!player.isCreative() && fuelTime > 0) {
					if(stack.getItem() instanceof BucketItem)
						player.getInventory().setStack(player.getInventory().selectedSlot, BucketItem.getEmptiedStack(stack, player));
					else
						stack.decrement(1);
				}

				if(stack.getItem() instanceof BucketItem) {
					SoundEvent soundEvent = SoundEvents.ITEM_BUCKET_EMPTY_LAVA;
					world.playSound(player, player.getBlockPos(), soundEvent, SoundCategory.BLOCKS, 1.0f, 1.0f);
				}

				fuel = (int) Math.min(MinecartTweaks.getConfig().commonTweaks.furnaceMaxBurnTime, fuel + (fuelTime * 2.25));
			}

			ACCEPTABLE_FUEL = Ingredient.empty();
		}
		else {
			ACCEPTABLE_FUEL = OLD_ACCEPTABLE_FUEL;
		}
	}

	@ModifyConstant(method = "interact", constant = @Constant(intValue = 32000))
	public int minecarttweaks$maxBurnTime(int maxBurnTime) {
		return MinecartTweaks.getConfig().commonTweaks.furnaceMaxBurnTime;
	}

	@ModifyArgs(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
	))
	public void minecarttweaks$changeSmokeParticle(Args args) {
		if(MinecartTweaks.getConfig().clientTweaks.useCampfireSmoke)
			args.set(0, ParticleTypes.CAMPFIRE_COSY_SMOKE);

		args.set(1, getX() + (random.nextFloat() - 0.5));
		args.set(2, getY() + 1);
		args.set(3, getZ() + (random.nextFloat() - 0.5));
		args.set(5, 0.2);
	}

	@ModifyArg(method = "tick", at = @At(value = "INVOKE",
			target = "Ljava/util/Random;nextInt(I)I"
	))
	public int minecarttweaks$removeRandom(int i) {
		return 1;
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("HEAD"))
	public void minecarttweaks$readNbt(NbtCompound nbt, CallbackInfo info) {
		altFuel = nbt.getInt("AltFuel");
		altPushX = nbt.getDouble("AltPushX");
		altPushZ = nbt.getDouble("AltPushZ");
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("HEAD"))
	public void minecarttweaks$writeNbt(NbtCompound nbt, CallbackInfo info) {
		nbt.putInt("AltFuel", altFuel);
		nbt.putDouble("AltPushX", altPushX);
		nbt.putDouble("AltPushZ", altPushZ);
	}
}

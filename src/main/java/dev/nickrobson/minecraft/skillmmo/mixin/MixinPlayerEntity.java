package dev.nickrobson.minecraft.skillmmo.mixin;

import dev.nickrobson.minecraft.skillmmo.config.SkillMmoConfig;
import dev.nickrobson.minecraft.skillmmo.skill.PlayerSkillUnlockManager;
import dev.nickrobson.minecraft.skillmmo.skill.SkillMmoPlayerDataHolder;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity implements SkillMmoPlayerDataHolder {
    private static final String ROOT_NBT_KEY = "skillMmo";
    private static final String EXPERIENCE_NBT_KEY = "experience";
    private static final String AVAILABLE_SKILL_POINTS_NBT_KEY = "availableSkillPoints";
    private static final String SKILL_LEVELS_NBT_KEY = "skillLevels";
    private SkillMmoPlayerData skillMmo$playerData = null;

    @Inject(
            method = "readCustomDataFromNbt",
            at = @At(value = "TAIL")
    )
    public void readSkillMmoNbtData(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound skillMmoNbt = nbt.contains(ROOT_NBT_KEY, NbtElement.COMPOUND_TYPE)
                ? nbt.getCompound(ROOT_NBT_KEY)
                : new NbtCompound();

        long experience = skillMmoNbt.contains(EXPERIENCE_NBT_KEY, NbtElement.NUMBER_TYPE)
                ? skillMmoNbt.getLong(EXPERIENCE_NBT_KEY)
                : 0L;

        int availableSkillPoints = skillMmoNbt.contains(AVAILABLE_SKILL_POINTS_NBT_KEY, NbtElement.NUMBER_TYPE)
                ? skillMmoNbt.getInt(AVAILABLE_SKILL_POINTS_NBT_KEY)
                : 0;

        Map<Identifier, Integer> skillLevels = new HashMap<>();
        if (skillMmoNbt.contains(SKILL_LEVELS_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound skillLevelsNbt = skillMmoNbt.getCompound(SKILL_LEVELS_NBT_KEY);
            for (String skillLevelKey : skillLevelsNbt.getKeys()) {
                if (skillLevelsNbt.contains(skillLevelKey, NbtElement.NUMBER_TYPE)) {
                    Identifier skillId = Identifier.tryParse(skillLevelKey);
                    int level = skillLevelsNbt.getInt(skillLevelKey);

                    if (skillId == null) {
                        continue;
                    }
                    skillLevels.put(skillId, level);
                }
            }
        }

        this.skillMmo$playerData = new SkillMmoPlayerData(experience, availableSkillPoints, skillLevels);
    }

    @Inject(
            method = "writeCustomDataToNbt",
            at = @At(value = "TAIL")
    )
    public void writeSkillMmoNbtData(NbtCompound nbt, CallbackInfo ci) {
        SkillMmoPlayerData playerData = this.getSkillMmoPlayerData();
        NbtCompound skillMmoNbt = new NbtCompound();

        {
            long experience = playerData.getExperience();
            skillMmoNbt.putLong(EXPERIENCE_NBT_KEY, experience);
        }

        {
            int availableSkillPoints = playerData.getAvailableSkillPoints();
            skillMmoNbt.putLong(AVAILABLE_SKILL_POINTS_NBT_KEY, availableSkillPoints);
        }

        {
            NbtCompound skillLevelsNbt = new NbtCompound();
            playerData.getSkillLevels().forEach((skillId, level) ->
                    skillLevelsNbt.putInt(skillId.toString(), level));
            skillMmoNbt.put(SKILL_LEVELS_NBT_KEY, skillLevelsNbt);
        }

        nbt.put(ROOT_NBT_KEY, skillMmoNbt);
    }

    @Override
    public SkillMmoPlayerData getSkillMmoPlayerData() {
        return skillMmo$playerData != null
                ? skillMmo$playerData
                : SkillMmoPlayerData.UNINITIALISED;
    }

    @Override
    public void setSkillMmoPlayerData(@Nonnull SkillMmoPlayerData playerData) {
        this.skillMmo$playerData = playerData;
    }

    @Inject(
            method = "addExperience",
            at = @At("TAIL")
    )
    public void onAddExperience(int experience, CallbackInfo ci) {
        this.skillMmo$playerData.addExperience(experience);
    }

    // This prevents blocks from dropping items when you haven't unlocked them
    @Inject(
            method = "canHarvest",
            at = @At("HEAD"),
            cancellable = true
    )
    public void checkCanHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this; // safe as this is a mixin for PlayerEntity
        if (!PlayerSkillUnlockManager.getInstance().hasBlockUnlock(player, state)) {
            if (SkillMmoConfig.getConfig().announceRequiredSkillWhenBreakingBlock) {
                // Only announce what skill is required to break a certain block if configured – it can be quite verbose
                PlayerSkillUnlockManager.getInstance().reportBlockBreakLocked(player, state.getBlock());
            }
            cir.setReturnValue(false);
        }
    }
}

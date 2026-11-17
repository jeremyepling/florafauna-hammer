package net.j40climb.florafauna_hammer.client;

import net.j40climb.florafauna_hammer.HammerMod;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.ChargedThrowPayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.HammerCorePayload;
import net.j40climb.florafauna_hammer.common.item.abilities.networking.HammerCorePayload.Cue;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerItem;
import net.j40climb.florafauna_hammer.common.item.hammer.HammerStage;
import net.j40climb.florafauna_hammer.setup.HammerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = HammerMod.MOD_ID, value = Dist.CLIENT)
public final class HammerCoreEffects {
    private static final DustParticleOptions VIOLET = new DustParticleOptions(0xBF87FA, 0.6F);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private record Hand(Player player, InteractionHand hand) {}
    private record Pulse(Cue cue, long start, int duration) {}
    private static final Map<Hand, Long> charging = new HashMap<>();
    private static final Map<Hand, Pulse> pulses = new HashMap<>();

    private HammerCoreEffects() {}

    @SubscribeEvent
    public static void networking(RegisterClientPayloadHandlersEvent event) {
        event.register(HammerCorePayload.TYPE, (packet, context) -> receive(packet));
    }

    private static void receive(HammerCorePayload packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(packet.playerId()) instanceof Player player)) return;
        Hand hand = new Hand(player, packet.hand());
        Cue cue = packet.cue();
        long now = level.getGameTime();
        switch (cue) {
            case WINDUP -> charging.put(hand, now);
            case READY -> {
                charging.put(hand, now - ChargedThrowPayload.HOLD_TICKS);
                burst(level, hand, cue);
            }
            case STOP_THROW -> charging.remove(hand);
            case STOP_SLAM -> pulses.remove(hand);
            case RELEASE -> {
                charging.remove(hand);
                burst(level, hand, cue);
            }
            case MODE, SCAN, LIGHTNING, DESCENT, IMPACT -> {
                int duration = switch (cue) {
                    case SCAN, IMPACT -> 14;
                    case DESCENT -> 200;
                    case LIGHTNING -> 9;
                    default -> 5;
                };
                pulses.put(hand, new Pulse(cue, now, duration));
                burst(level, hand, cue);
            }
        }
    }

    /** The renderer uses fractional world ticks. Inventory charge updates never reset these clocks. */
    public static float drive(ItemStack stack, @Nullable ItemOwner owner, double time, ItemDisplayContext context) {
        if (pulses.isEmpty() && charging.isEmpty()) return 0;
        if (owner == null || !(owner.asLivingEntity() instanceof Player player)) return 0;
        HumanoidArm arm = switch (context) {
            case FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_RIGHT_HAND -> HumanoidArm.RIGHT;
            case FIRST_PERSON_LEFT_HAND, THIRD_PERSON_LEFT_HAND -> HumanoidArm.LEFT;
            default -> null;
        };
        if (arm == null) return 0;
        InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!ItemStack.isSameItem(stack, player.getItemInHand(hand))) return 0;
        var key = new Hand(player, hand);
        Long windup = charging.get(key);
        float throwGlow = windup == null ? 0 : ramp((time - windup) / ChargedThrowPayload.HOLD_TICKS);
        Pulse pulse = pulses.get(key);
        if (pulse == null) return throwGlow;
        double age = time - pulse.start;
        if (age < 0 || age >= pulse.duration) return throwGlow;
        float envelope = switch (pulse.cue) {
            case DESCENT -> 0.55F + 0.45F * ramp(age / 5);
            case SCAN -> ramp((age + 1) / 4) * (1 - ramp((age - 4) / 10));
            case LIGHTNING -> ramp((age + 1) / 2) * (1 - ramp((age - 2) / 7));
            case IMPACT -> -0.85F * (1 - ramp(age / 14));
            case MODE -> 0.28F * (1 - ramp(age / 5));
            default -> 0;
        };
        return windup == null ? envelope : Math.max(throwGlow, envelope);
    }

    private static float ramp(double x) {
        float t = (float) Math.clamp(x, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static Vec3 handPosition(Hand hand) {
        Player p = hand.player;
        boolean right = (p.getMainArm() == HumanoidArm.RIGHT) == (hand.hand == InteractionHand.MAIN_HAND);
        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();
        if (camera.entity() == p && mc.options.getCameraType().isFirstPerson()) {
            // Particles live in world space; project the held head's screen offset onto a near plane.
            double depth = 0.85;
            double halfHeight = depth * Math.tan(Math.toRadians(camera.getFov() * 0.5));
            return camera.position().add(new Vec3(camera.forwardVector()).scale(depth))
                    .add(new Vec3(camera.leftVector()).scale((right ? -1.04 : 1.04) * halfHeight))
                    .add(new Vec3(camera.upVector()).scale(-0.28 * halfHeight));
        }
        Vec3 across = p.calculateViewVector(0, p.getYRot()).cross(UP);
        return p.getEyePosition().add(p.getViewVector(1).scale(0.54)).add(across.scale(right ? 0.36 : -0.36)).add(0, -0.36, 0);
    }

    private static void burst(ClientLevel level, Hand hand, Cue cue) {
        Vec3 pos = handPosition(hand);
        if (cue == Cue.MODE) {
            if (hand.player == Minecraft.getInstance().player) {
                level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.16F, 1.5F, false);
            }
            return;
        }
        if (cue == Cue.READY) {
            level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.48F, 1.4F, false);
        }
        if (cue == Cue.IMPACT || cue == Cue.DESCENT) return; // The existing world shockwave and launch sound carry these beats.
        Vec3 forward = hand.player.getViewVector(1);
        int count = cue == Cue.RELEASE ? 6 : 3;
        for (int i = 0; i < count; i++) {
            Vec3 at = pos.add(forward.scale((cue == Cue.READY ? 0 : 0.15) * i));
            Vec3 drift = forward.scale(0.05 + i * 0.016);
            level.addParticle(cue == Cue.LIGHTNING || cue == Cue.READY ? ParticleTypes.ELECTRIC_SPARK : VIOLET,
                    at.x, at.y + level.getRandom().nextDouble() * 0.07, at.z, drift.x, drift.y + 0.02, drift.z);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null) {
            pulses.clear();
            charging.clear();
            return;
        }
        long now = level.getGameTime();
        pulses.entrySet().removeIf(entry -> inactive(entry.getKey(), level) || now - entry.getValue().start >= entry.getValue().duration);
        charging.keySet().removeIf(hand -> inactive(hand, level));
        if (mc.isPaused()) return;
        // Stagger one inexpensive hand check per nearby player every two seconds.
        for (var player : level.players()) {
            if ((now + player.getId()) % 40 != 0 || mc.player.distanceToSqr(player) > 24 * 24) continue;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (!stack.is(HammerRegistry.HAMMER) || HammerItem.charge(stack) == 0) continue;
                boolean tempest = HammerStage.of(stack) == HammerStage.TEMPEST;
                if (!tempest && level.getRandom().nextBoolean()) continue;
                Vec3 pos = handPosition(new Hand(player, hand));
                level.addParticle(tempest ? ParticleTypes.ELECTRIC_SPARK : VIOLET, pos.x, pos.y, pos.z,
                        0, 0.018, 0);
            }
        }
    }

    private static boolean inactive(Hand key, ClientLevel level) {
        return key.player.isRemoved() || key.player.level() != level || !key.player.getItemInHand(key.hand).is(HammerRegistry.HAMMER);
    }
}

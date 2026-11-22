// Modified by covalscy (Bleedout fork): updated mod ID to bleedout_ragdolls, enabled F5 first-person ragdoll toggle, and added solid-block anti-clipping
package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.neoforge.config.RagdollClientConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollCameraHelper {
   private static final int MAX_CAMERA_RETRY_TICKS = 40;
   private static final double RAGDOLL_EYE_LOCAL_UP = 0.57;
   private static final double RAGDOLL_EYE_LOCAL_FORWARD = 0.25;
   private static final double CAMERA_CLIP_RADIUS = 0.09;
   private static final double CAMERA_CLEAR_RADIUS = 0.12;
   private static final double CLIP_MARGIN = 0.06;
   private static final double ESCAPE_STEP = 0.04;
   private static final double MAX_ESCAPE = 0.64;
   private static final double DIAG = 0.7071067811865475;
   private static final Vec3[] ESCAPE_DIRECTIONS = new Vec3[]{
      new Vec3(0.0, 1.0, 0.0),
      new Vec3(1.0, 0.0, 0.0),
      new Vec3(-1.0, 0.0, 0.0),
      new Vec3(0.0, 0.0, 1.0),
      new Vec3(0.0, 0.0, -1.0),
      new Vec3(DIAG, DIAG, 0.0),
      new Vec3(-DIAG, DIAG, 0.0),
      new Vec3(0.0, DIAG, DIAG),
      new Vec3(0.0, DIAG, -DIAG),
      new Vec3(0.0, -1.0, 0.0)
   };
   // ShoulderSurfing crashes on unknown CameraType values in Perspective.of(), fall back to vanilla 3rd person cam.
   private static final boolean SHOULDER_SURFING = ModList.get().isLoaded("shouldersurfing");

   private static int pendingCameraTicks;
   private static boolean suppressLocalPlayerRender;
   private static boolean cameraSwitched;
   private static boolean ridingRagdollSeat;
   private static boolean firstPersonAligned;
   private static CameraType cameraTypeBeforeRagdoll;
   private static Quaterniond lastSubLevelOrientation;

   private RagdollCameraHelper() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onClientTick);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onRenderPlayer);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onRenderHand);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onCalculateCameraDistance);
   }

   private static void onClientTick(Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      boolean nowRidingRagdollSeat = player != null && player.getVehicle() instanceof RagdollSeatEntity;

      if (nowRidingRagdollSeat && !ridingRagdollSeat) {
         cameraTypeBeforeRagdoll = minecraft.options.getCameraType();
         suppressLocalPlayerRender = true;
         firstPersonAligned = false;
         cameraSwitched = false;
         pendingCameraTicks = MAX_CAMERA_RETRY_TICKS;
         player.setForcedPose(Pose.STANDING);
      } else if (!nowRidingRagdollSeat && ridingRagdollSeat) {
         if (player != null) {
            player.setForcedPose(null);
         }
         resetFromContraptionCamera();
      }
      ridingRagdollSeat = nowRidingRagdollSeat;

      if (pendingCameraTicks > 0) {
         pendingCameraTicks--;
         if (tryActivateUnlockedContraptionCamera()) {
            pendingCameraTicks = 0;
         } else if (pendingCameraTicks == 0) {
            SablePlayerRagdoll.LOGGER.debug(
               "[bleedout_ragdolls] ragdoll camera sub-level not ready within {} ticks; keeping original camera",
               MAX_CAMERA_RETRY_TICKS
            );
         }
      } else if (nowRidingRagdollSeat && player != null) {
         boolean nowFirstPerson = minecraft.options.getCameraType() == CameraType.FIRST_PERSON;
         if (nowFirstPerson && !firstPersonAligned) {
            SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(player);
            if (subLevel != null) {
               alignFirstPersonRotationToPlayerView(player, subLevel);
            }
         } else if (!nowFirstPerson && firstPersonAligned) {
            SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(player);
            if (subLevel != null) {
               restoreWorldRotationFromFirstPerson(player, subLevel.logicalPose().orientation());
            }
            firstPersonAligned = false;
            lastSubLevelOrientation = null;
         } else if (nowFirstPerson) {
            SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(player);
            if (subLevel != null) {
               lastSubLevelOrientation = new Quaterniond(subLevel.logicalPose().orientation());
            }
         }
      }
   }

   private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (event.getEntity().getVehicle() instanceof RagdollSeatEntity) {
         event.setCanceled(true);
         return;
      }
      if (suppressLocalPlayerRender && minecraft.player != null && event.getEntity() == minecraft.player) {
         event.setCanceled(true);
      }
   }

   private static void onRenderHand(RenderHandEvent event) {
      if (suppressLocalPlayerRender) {
         event.setCanceled(true);
      }
   }

   private static void onCalculateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.options.getCameraType() != SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) return;
      if (minecraft.player == null || event.getCamera().getEntity() != minecraft.player) return;
      if (!(minecraft.player.getVehicle() instanceof RagdollSeatEntity)) return;
      event.setDistance((float) RagdollClientConfig.subLevelCameraDistance());
   }

   private static boolean tryActivateUnlockedContraptionCamera() {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || minecraft.level == null || !player.isPassenger()) return false;
      SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(player);
      if (subLevel == null) return false;
      CameraType targetCameraType;
      if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON || RagdollClientConfig.useFirstPersonCamera()) {
         targetCameraType = CameraType.FIRST_PERSON;
         alignFirstPersonRotationToPlayerView(player, subLevel);
      } else {
         targetCameraType = SHOULDER_SURFING ? CameraType.THIRD_PERSON_BACK : SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED;
      }
      if (minecraft.options.getCameraType() != targetCameraType) {
         minecraft.options.setCameraType(targetCameraType);
      }
      cameraSwitched = true;
      return true;
   }

   public static Vec3 firstPersonHeadAnchor(Vec3 fallback, float partialTick) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null
         || !(player.getVehicle() instanceof RagdollSeatEntity)
         || minecraft.options.getCameraType() != CameraType.FIRST_PERSON
         || !(Sable.HELPER.getVehicleSubLevel(player) instanceof ClientSubLevel torso)) {
         return fallback;
      }
      BlockPos plot = torso.getPlot().getCenterBlock();
      Vec3 anchorCenter = torso.renderPose(partialTick).transformPosition(Vec3.atCenterOf(plot));
      Vec3 localEye = new Vec3(
         plot.getX() + 0.5,
         plot.getY() + 0.5 + RAGDOLL_EYE_LOCAL_UP,
         plot.getZ() + 0.5 + RAGDOLL_EYE_LOCAL_FORWARD
      );
      Vec3 desiredEye = torso.renderPose(partialTick).transformPosition(localEye);
      ClientLevel level = minecraft.level;
      if (level == null) {
         return desiredEye;
      }
      return clipToClearPosition(level, player, anchorCenter, desiredEye);
   }

   private static Vec3 clipToClearPosition(ClientLevel level, Entity entity, Vec3 anchorCenter, Vec3 desiredEye) {
      Vec3 safeStart = escapeSolids(level, anchorCenter);
      Vec3 delta = desiredEye.subtract(safeStart);
      double dist = delta.length();
      Vec3 candidate = desiredEye;

      if (dist >= 1.0E-6) {
         double minDist = dist;
         for (int i = 0; i < 8; i++) {
            double ox = ((i & 1) * 2 - 1) * CAMERA_CLIP_RADIUS;
            double oy = (((i >> 1) & 1) * 2 - 1) * CAMERA_CLIP_RADIUS;
            double oz = (((i >> 2) & 1) * 2 - 1) * CAMERA_CLIP_RADIUS;
            Vec3 rayStart = safeStart.add(ox, oy, oz);
            Vec3 rayEnd = desiredEye.add(ox, oy, oz);
            BlockHitResult hit = level.clip(new ClipContext(
               rayStart,
               rayEnd,
               ClipContext.Block.VISUAL,
               ClipContext.Fluid.NONE,
               entity
            ));
            if (hit.getType() != HitResult.Type.MISS) {
               double hitDist = Math.max(0.0, hit.getLocation().distanceTo(rayStart) - CLIP_MARGIN);
               minDist = Math.min(minDist, hitDist);
            }
         }
         candidate = safeStart.add(delta.scale(Math.min(1.0, minDist / dist)));
      }

      return escapeSolids(level, candidate);
   }

   private static Vec3 escapeSolids(ClientLevel level, Vec3 pos) {
      if (isClear(level, pos)) {
         return pos;
      }
      for (double dist = ESCAPE_STEP; dist <= MAX_ESCAPE + 1.0E-6; dist += ESCAPE_STEP) {
         for (Vec3 dir : ESCAPE_DIRECTIONS) {
            Vec3 candidate = pos.add(dir.x * dist, dir.y * dist, dir.z * dist);
            if (isClear(level, candidate)) {
               return candidate;
            }
         }
      }
      return pos;
   }

   private static boolean isClear(ClientLevel level, Vec3 pos) {
      AABB cameraBox = new AABB(
         pos.x - CAMERA_CLEAR_RADIUS,
         pos.y - CAMERA_CLEAR_RADIUS,
         pos.z - CAMERA_CLEAR_RADIUS,
         pos.x + CAMERA_CLEAR_RADIUS,
         pos.y + CAMERA_CLEAR_RADIUS,
         pos.z + CAMERA_CLEAR_RADIUS
      );
      VoxelShape cameraShape = Shapes.create(cameraBox);
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      int minX = Mth.floor(cameraBox.minX);
      int minY = Mth.floor(cameraBox.minY);
      int minZ = Mth.floor(cameraBox.minZ);
      int maxX = Mth.floor(cameraBox.maxX);
      int maxY = Mth.floor(cameraBox.maxY);
      int maxZ = Mth.floor(cameraBox.maxZ);

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cursor.set(x, y, z);
               BlockState state = level.getBlockState(cursor);
               if (state.isAir() || state.getBlock() instanceof RagdollPartBlock) {
                  continue;
               }
               VoxelShape blockShape = state.getCollisionShape(level, cursor, CollisionContext.empty());
               if (blockShape.isEmpty()) {
                  continue;
               }
               if (Shapes.joinIsNotEmpty(blockShape.move(x, y, z), cameraShape, BooleanOp.AND)) {
                  return false;
               }
            }
         }
      }
      return true;
   }

   private static void alignFirstPersonRotationToPlayerView(LocalPlayer player, SubLevel subLevel) {
      double yawRad = Math.toRadians(player.getYRot());
      double pitchRad = Math.toRadians(player.getXRot());
      Quaterniond worldRotation = new Quaterniond().rotationYXZ(Math.PI - yawRad, -pitchRad, 0.0);
      lastSubLevelOrientation = new Quaterniond(subLevel.logicalPose().orientation());
      Quaterniond localRotation = new Quaterniond(lastSubLevelOrientation).invert().mul(worldRotation);
      applyPlayerRotation(player, localRotation);
      firstPersonAligned = true;
   }

   private static void restoreWorldRotationFromFirstPerson(LocalPlayer player, org.joml.Quaterniondc subLevelOrientation) {
      double yawRad = Math.toRadians(player.getYRot());
      double pitchRad = Math.toRadians(player.getXRot());
      Quaterniond localRotation = new Quaterniond().rotationYXZ(Math.PI - yawRad, -pitchRad, 0.0);
      Quaterniond worldRotation = new Quaterniond(subLevelOrientation).mul(localRotation);
      applyPlayerRotation(player, worldRotation);
   }

   private static void applyPlayerRotation(LocalPlayer player, Quaterniond rotation) {
      Vector3d euler = new Vector3d();
      rotation.getEulerAnglesYXZ(euler);
      float yRot = (float) Math.toDegrees(Math.PI - euler.y);
      float xRot = (float) Math.toDegrees(-euler.x);

      player.setYRot(yRot);
      player.setXRot(xRot);
      player.yRotO = yRot;
      player.xRotO = xRot;
      player.setYHeadRot(yRot);
      player.yHeadRotO = yRot;
   }

   public static boolean isFirstPersonAligned() {
      return firstPersonAligned;
   }

   private static void resetFromContraptionCamera() {
      Minecraft minecraft = Minecraft.getInstance();
      if (firstPersonAligned && minecraft.player != null && lastSubLevelOrientation != null) {
         restoreWorldRotationFromFirstPerson(minecraft.player, lastSubLevelOrientation);
      }
      if (minecraft.options != null) {
         if (cameraSwitched) {
            minecraft.options.setCameraType(cameraTypeBeforeRagdoll != null ? cameraTypeBeforeRagdoll : CameraType.FIRST_PERSON);
            cameraSwitched = false;
         }
         cameraTypeBeforeRagdoll = null;
         pendingCameraTicks = 0;
         suppressLocalPlayerRender = false;
         firstPersonAligned = false;
         lastSubLevelOrientation = null;
      }
   }

}

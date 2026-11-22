// Modified by covalscy (Bleedout fork): support dynamic F5 switching into first-person ragdoll camera
package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class RagdollSableCameraBypass {
   private RagdollSableCameraBypass() {
   }
   private static boolean isRagdollCameraPlayer(Entity entity) {
      Minecraft minecraft = Minecraft.getInstance();
      return entity != null && entity == minecraft.player && entity.getVehicle() instanceof RagdollSeatEntity;
   }
   private static boolean isFirstPerson() {
      return Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
   }
   public static boolean suppressSubLevelCameraRotation(Entity entity) {
      if (!isRagdollCameraPlayer(entity)) {
         return false;
      }
      if (isFirstPerson()) {
         return !RagdollCameraHelper.isFirstPersonAligned();
      }
      return true;
   }
   public static boolean detachSubLevelCameraPosition(Entity entity) {
      return isRagdollCameraPlayer(entity) && !isFirstPerson();
   }
}

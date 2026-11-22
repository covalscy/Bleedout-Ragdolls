// Modified by covalscy (Bleedout fork): updated mod ID to bleedout_ragdolls
package dev.leo.sableplayerragdoll;

import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.ryanhcode.sable.platform.SableEventPlatform;

public final class SablePlayerRagdollBootstrap {
   private SablePlayerRagdollBootstrap() {
   }

   public static void init() {
      SableEventPlatform.INSTANCE.onPostPhysicsTick(RagdollRegistry::onPostPhysicsTick);
      SablePlayerRagdoll.LOGGER.info("[bleedout_ragdolls] post-physics ragdoll hook registered");
   }
}

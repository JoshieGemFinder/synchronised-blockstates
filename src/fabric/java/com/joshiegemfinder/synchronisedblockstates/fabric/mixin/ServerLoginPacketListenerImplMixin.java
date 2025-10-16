package com.joshiegemfinder.synchronisedblockstates.fabric.mixin;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.joshiegemfinder.synchronisedblockstates.common.network.login.TaskManager;
import com.joshiegemfinder.synchronisedblockstates.common.network.login.TaskManagerGetter;
import com.joshiegemfinder.synchronisedblockstates.fabric.network.login.FabricTaskManager;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl.State;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginPacketListenerImplMixin implements TaskManagerGetter {

	// ==== Shadows ====
	
	@Shadow
	@Final
	MinecraftServer server;

	@Shadow
	ServerLoginPacketListenerImpl.State state;
	
	@Shadow
	@Nullable
	GameProfile gameProfile;

	@Shadow
	@Nullable
	private ServerPlayer delayedAcceptPlayer;
	

	@Shadow
	private void placeNewPlayer(ServerPlayer serverPlayer) { }
	
	// ==== Content ====
	
	@Unique
	@Nullable
	private TaskManager taskManager = null;

	@Override
	@Nullable
	public TaskManager getTaskManager() {
		return this.taskManager;
	}
	
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	public void tick(CallbackInfo ci) {
		if(this.state == State.ACCEPTED) {
			boolean runningTasks = this.tickTaskManager();
			if(runningTasks) {
				ci.cancel();
			} else {
				this.state = ServerLoginPacketListenerImpl.State.READY_TO_ACCEPT;
			}
		}
	}

	@Inject(
			method = "handleAcceptedLogin", 
			at = @At(
					value = "FIELD",
					target = "net/minecraft/server/network/ServerLoginPacketListenerImpl.connection:Lnet/minecraft/network/Connection;",
					opcode = Opcodes.GETFIELD,
					ordinal = 0
				),
			slice = @Slice(
					from = @At(
							value = "INVOKE",
							target = "net/minecraft/network/Connection.send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V"
						),
					to = @At(
							value = "NEW",
							target = "net/minecraft/network/protocol/login/ClientboundGameProfilePacket"
						)
				),
			cancellable = true
			)
	public void checkIfTasksNeedRunning(CallbackInfo ci) {
		boolean tasksNeedToRun;
		if(this.taskManager == null) {
			tasksNeedToRun = this.setupTaskManager();
		} else {
			tasksNeedToRun = !this.taskManager.allTasksFinishedRunning();
		}
		if(tasksNeedToRun) {
			ci.cancel();
		}
	}
	
//	@Definition(id = "serverPlayer", local = @Local(type = ServerPlayer.class, ordinal = 0))
//	@Expression("serverPlayer != null")
//	@ModifyExpressionValue(
//			method = "handleAcceptedLogin", 
//			at = @At("MIXINEXTRAS:EXPRESSION"),
//			slice = @Slice(
//					from = @At(
//							value = "INVOKE",
//							target = "net/minecraft/server/players/PlayerList.getPlayerForLogin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/server/level/ServerPlayer;"
//						),
//					to = @At(
//							value = "INVOKE",
//							target = "net/minecraft/server/network/ServerLoginPacketListenerImpl.placeNewPlayer(Lnet/minecraft/server/level/ServerPlayer;)V"
//						)
//				)
//			)
////	@Inject(
////			method = "handleAcceptedLogin", 
////			at = @At(value = "JUMP", opcode = Opcodes.IFNULL, shift = Shift.NONE),
////			slice = @Slice(
////					from = @At(
////							value = "INVOKE",
////							target = "net/minecraft/server/players/PlayerList.getPlayerForLogin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/server/level/ServerPlayer;"
////						),
////					to = @At(
////							value = "INVOKE",
////							target = "net/minecraft/server/network/ServerLoginPacketListenerImpl.placeNewPlayer(Lnet/minecraft/server/level/ServerPlayer;)V"
////						)
////				)
////			)
//	public boolean checkIfTasksNeedRunning(boolean original) {
//		boolean tasksNeedToRun = this.setupTaskManager();
//		return original || tasksNeedToRun;
//	}

//	@Redirect(
//			method = "handleAcceptedLogin", 
//			at = @At(
//					value = "INVOKE",
//					target = "net/minecraft/server/network/ServerLoginPacketListenerImpl.placeNewPlayer(Lnet/minecraft/server/level/ServerPlayer;)V"
//				)
//			)
//	public void delayAcceptPlayer(ServerLoginPacketListenerImpl self, ServerPlayer serverPlayer) {
//		if(this.taskManager == null) {
//			boolean tasksNeedToRun = this.setupTaskManager();
//			if(tasksNeedToRun) {
//				this.state = ServerLoginPacketListenerImpl.State.DELAY_ACCEPT;
//				this.delayedAcceptPlayer = serverPlayer;
//				return;
//			}
//		}
//		
//		this.placeNewPlayer(serverPlayer);
//	}
	
	/**
	 * Sets up a new Task Manager
	 * @return whether the Task Manager has tasks to run
	 */
	@Unique
	private boolean setupTaskManager() {
		this.taskManager = new FabricTaskManager((ServerLoginPacketListenerImpl)(Object)this);
		
		this.taskManager.collectTasks(this.server, this.gameProfile);
		
		return !this.taskManager.allTasksFinishedRunning();
	}

	/**
	 * Ticks the existing Task Manager
	 * @return whether the Task Manager is still running
	 */
	@Unique
	private boolean tickTaskManager() {
		if(this.taskManager == null) { this.setupTaskManager(); }
		
		if(this.taskManager.allTasksFinishedRunning()) {
			return false;
		}
		
		this.taskManager.tick();
		return true;
	}
}

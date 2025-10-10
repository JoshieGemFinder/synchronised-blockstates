package com.joshiegemfinder.synchronisedblockstates.common.network.login;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;

public abstract class TaskManager {

	// if a single task takes more than 60 seconds, disconnect
	public static final int TASK_MAX_TICKS = 60 * 20;
	
	protected final Queue<Task> taskQueue = new ConcurrentLinkedQueue<Task>();
	@Nullable
	protected Task currentTask = null;
	protected int taskWatchdogTimer = 0;
	
	private boolean hasCollectedTasks = false;
	
	/**
	 * Collects all of the Tasks to be run before this player finishes logging in.
	 * This may only be run once.
	 * @param server the current server
	 * @param profile the profile of the player to log in
	 */
	public final void collectTasks(MinecraftServer server, GameProfile profile) {
		if(this.hasCollectedTasks) {
			throw new IllegalStateException("Tasks have already been collected");
		} else {
			this.collectTasksInternal(server, profile);
			this.hasCollectedTasks = true;
		}
	}
	
	/**
	 * @return if the tasks have been collected yet
	 */
	public final boolean hasCollectedTasks() {
		return this.hasCollectedTasks;
	}
	
	/**
	 * @return Whether all of the tasks have finished
	 */
	public final boolean allTasksFinishedRunning() {
		return this.hasCollectedTasks && this.taskQueue.isEmpty() && this.currentTask == null;
	}

	/**
	 * @return if there is currently a task running
	 */
	public final boolean isTaskRunning() {
		return this.currentTask != null;
	}
	
	/**
	 * @param type the type of task
	 * @return if there is currently a task running of the specified type
	 */
	public final boolean isTaskRunning(Task.Type<?> type) {
		return this.currentTask != null && this.currentTask.type() == type;
	}
	
	/**
	 * Attempt to complete the current task if it's for a specified type
	 * @param type the type of task to complete
	 * @throws IllegalStateException if the type does not match the type of the current type
	 */
	public void completeTask(Task.Type<?> type) {
		Task.Type<?> currentType = this.currentTask != null ? this.currentTask.type() : null;
		if (currentType == type) {
			this.currentTask = null;
			
			this.startNextTask();
		} else {
			throw new IllegalStateException("Attempted to complete task of unexpected type " + type + " when the current task is of type " + currentType);
		}
	}

	/**
	 * Return the current task if it's for the specified type
	 * @param type the type of task to complete
	 * @return the running task if it's of the requested type, or null if it isn't
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public <T extends Task> T getCurrentTask(Task.Type<T> type) {
		Task.Type<?> currentType = this.currentTask != null ? this.currentTask.type() : null;
		if(currentType == type) {
			return (T)this.currentTask;
		} else {
			return null;
		}
	}
	/**
	 * Attempt to start the next task if there is not already one running and it is possible to start one
	 */
	public void startNextTask() {
		if(this.currentTask != null) {
			throw new IllegalStateException("Cannot start next task until previous task of id " + this.currentTask.type().id() + " has completed.");
		}
		if(!this.isAcceptingMessages()) {
			return;
		}
		
		Task nextTask = this.taskQueue.poll();
		if(nextTask != null) { // something something concurrency
			this.currentTask = nextTask;
			nextTask.start(this.getPacketSender());
			this.taskWatchdogTimer = 0;
		}
	}
	
	
	/**
	 * Tick the manager at the default rate
	 */
	public void tick() {
		this.keepConnectionAlive();
		
		if(this.isTaskRunning()) {
			if(this.taskWatchdogTimer++ >= TASK_MAX_TICKS) {
				this.disconnect(Component.translatable("multiplayer.disconnect.slow_login"));
			}
		} else {
			this.taskWatchdogTimer = 0;
			this.startNextTask();
		}
	}

	
	/**
	 * Collect all the tasks to run before the player logs in.
	 * Abstract for cross-platforming reasons.
	 * @param server
	 * @param profile
	 */
	protected abstract void collectTasksInternal(MinecraftServer server, GameProfile profile);
	
	/**
	 * @return whether this task manager can accept new messages and is not disconnected
	 */
	protected abstract boolean isAcceptingMessages();

	/**
	 * @return the packet sender
	 */
	protected abstract Consumer<Packet<?>> getPacketSender();

	/**
	 * Keep the connection alive, if necessary
	 */
	protected abstract void keepConnectionAlive();

	/**
	 * Disconnect the client. No more calls should be made to {@linkplain TaskManager#tick()} after this is called 
	 */
	protected abstract void disconnect(Component component);
}

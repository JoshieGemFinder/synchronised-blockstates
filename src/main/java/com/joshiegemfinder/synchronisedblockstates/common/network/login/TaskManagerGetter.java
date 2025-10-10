package com.joshiegemfinder.synchronisedblockstates.common.network.login;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public interface TaskManagerGetter {
	/**
	 * @return the task manager
	 */
	@Nullable
	public TaskManager getTaskManager();

	/**
	 * @return Whether all of the tasks have finished
	 */
	public default boolean allTasksFinishedRunning() {
		final TaskManager manager = this.getTaskManager();
		return manager != null ? manager.allTasksFinishedRunning() : false;
	}

	/**
	 * @return if there is currently a task running
	 */
	public default boolean isTaskRunning() {
		final TaskManager manager = this.getTaskManager();
		return manager != null ? manager.isTaskRunning() : false;
	}
	
	/**
	 * @param type the type of task
	 * @return if there is currently a task running of the specified type
	 */
	public default boolean isTaskRunning(Task.Type<?> type) {
		final TaskManager manager = this.getTaskManager();
		return manager != null ? manager.isTaskRunning(type) : false;
	}

	/**
	 * Attempt to complete the current task if it's for a specified type
	 * @param type the type of task to complete
	 * @throws IllegalStateException if the type does not match the type of the current type
	 * @throws NullPointerException if there is no task manager currently associated with this task getter
	 */
	public default void completeTask(Task.Type<?> type) {
		Objects.requireNonNull(this.getTaskManager()).completeTask(type);
	}

	/**
	 * Attempt to complete the current task if it's for a specified type without erroring
	 * @param type the type of task to complete
	 */
	public default void tryCompleteTask(Task.Type<?> type) {
		final TaskManager manager = this.getTaskManager();
		if(manager != null && manager.isTaskRunning(type)) {
			manager.completeTask(type);
		}
	}

	/**
	 * Return the current task if it's for the specified type
	 * @param type the type of task to complete
	 * @return the running task if it's of the requested type, or null if it isn't
	 */
	@Nullable
	public default <T extends Task> T getCurrentTask(Task.Type<T> type) {
		final TaskManager manager = this.getTaskManager();
		return manager != null ? manager.getCurrentTask(type) : null;
	}
}

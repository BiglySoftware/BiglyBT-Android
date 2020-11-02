/*
 *    This file is part of mlDHT. 
 * 
 *    mlDHT is free software: you can redistribute it and/or modify 
 *    it under the terms of the GNU General Public License as published by 
 *    the Free Software Foundation, either version 2 of the License, or 
 *    (at your option) any later version. 
 * 
 *    mlDHT is distributed in the hope that it will be useful, 
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 *    GNU General Public License for more details. 
 * 
 *    You should have received a copy of the GNU General Public License 
 *    along with mlDHT.  If not, see <http://www.gnu.org/licenses/>. 
 */
package lbms.plugins.mldht.kad.tasks;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import lbms.plugins.mldht.kad.DHTBase;

/**
 * Manages all dht tasks.
 *
 * @author Damokles
 */
public class TaskManager {

	private Map<Integer, Task>	tasks;
	private Deque<Task>			queued;
	private AtomicInteger		next_id = new AtomicInteger();

	public TaskManager () {
		tasks = new HashMap<Integer, Task>();
		queued = new LinkedList<Task>();
		next_id.set(1);
	}
	
	public void addTask(Task task)
	{
		addTask(task, false);
	}

	/**
	 * Add a task to manage.
	 * @param task
	 */
	public void addTask (Task task, boolean isPriority) {
		int id = next_id.incrementAndGet();
		task.setTaskID(id);
		if (task.isQueued()) {
			synchronized (queued) {
				if(isPriority)
					queued.addFirst(task);
				else
					queued.addLast(task);
			}
		} else {
			synchronized (tasks) {
				tasks.put(id, task);

			}
		}
	}

	/**
	 * Remove all finished tasks.
	 * @param dh_table Needed to ask permission to start a task
	 */
	public void removeFinishedTasks (DHTBase dh_table) {
		synchronized (tasks) {

			List<Integer> rm = new ArrayList<Integer>(tasks.size());
			for (Task task : tasks.values()) {
				if (task.isFinished()) {
					rm.add(task.getTaskID());
				}
			}

			for (Integer i : rm) {
				tasks.remove(i);
			}
			synchronized (queued) {
				Task t = null;
				while (queued.size() > 0 && dh_table.canStartTask(t = queued.peekFirst())) {
					t = queued.removeFirst();
					//Out(SYS_DHT|LOG_NOTICE) << "DHT: starting queued task" << endl;
					t.start();
					tasks.put(t.getTaskID(), t);
				}
			}
		}
	}

	/// Get the number of running tasks
	public int getNumTasks () {
		return tasks.size();
	}

	/// Get the number of queued tasks
	public int getNumQueuedTasks () {
		return queued.size();
	}

	public Task[] getActiveTasks () {
		synchronized (tasks) {
			return tasks.values().toArray(new Task[tasks.size()]);
		}
	}

	public Task[] getQueuedTasks () {
		synchronized (queued) {
			return queued.toArray(new Task[queued.size()]);
		}
	}

}

package org.apollo.game.sync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;

import org.apollo.game.GameService;
import org.apollo.game.model.World;
import org.apollo.game.model.entity.Npc;
import org.apollo.game.model.entity.Player;
import org.apollo.game.sync.task.NpcSynchronizationTask;
import org.apollo.game.sync.task.PhasedSynchronizationTask;
import org.apollo.game.sync.task.PlayerSynchronizationTask;
import org.apollo.game.sync.task.PostNpcSynchronizationTask;
import org.apollo.game.sync.task.PostPlayerSynchronizationTask;
import org.apollo.game.sync.task.PreNpcSynchronizationTask;
import org.apollo.game.sync.task.PrePlayerSynchronizationTask;
import org.apollo.game.sync.task.SynchronizationTask;
import org.apollo.util.MobRepository;
import org.apollo.util.NamedThreadFactory;

/**
 * An implementation of {@link ClientSynchronizer} which runs in a thread pool. A {@link Phaser} is used to ensure that
 * the synchronization is complete, allowing control to return to the {@link GameService} that started the
 * synchronization. This class will scale well with machines that have multiple cores/processors. The
 * {@link SequentialClientSynchronizer} will work better on machines with a single core/processor, however, both classes
 * will work.
 * 
 * @author Graham
 * @author Major
 */
public final class ParallelClientSynchronizer extends ClientSynchronizer {

	/**
	 * The executor service.
	 */
	private final ExecutorService executor;

	/**
	 * The phaser.
	 */
	private final Phaser phaser = new Phaser(1);

	/**
	 * Creates the parallel client synchronizer backed by a thread pool with a number of threads equal to the number of
	 * processing cores available (this is found by the {@link Runtime#availableProcessors()} method.
	 */
	public ParallelClientSynchronizer() {
		int processors = Runtime.getRuntime().availableProcessors();
		ThreadFactory factory = new NamedThreadFactory("ClientSynchronizer");
		executor = Executors.newFixedThreadPool(processors, factory);
	}

	@Override
	public void synchronize() {
		MobRepository<Player> players = World.getWorld().getPlayerRepository();
		MobRepository<Npc> npcs = World.getWorld().getNpcRepository();
		int playerCount = players.size();
		int npcCount = npcs.size();

		phaser.bulkRegister(playerCount);
		for (Player player : players) {
			SynchronizationTask task = new PrePlayerSynchronizationTask(player);
			executor.submit(new PhasedSynchronizationTask(phaser, task));
		}
		phaser.arriveAndAwaitAdvance();

		phaser.bulkRegister(npcCount);
		for (Npc npc : npcs) {
			SynchronizationTask task = new PreNpcSynchronizationTask(npc);
			executor.submit(new PhasedSynchronizationTask(phaser, task));
		}
		phaser.arriveAndAwaitAdvance();

		phaser.bulkRegister(playerCount);
		for (Player player : players) {
			SynchronizationTask task = new PlayerSynchronizationTask(player);
			executor.submit(new PhasedSynchronizationTask(phaser, task));
		}
		phaser.arriveAndAwaitAdvance();

		phaser.bulkRegister(playerCount);
		for (Player player : players) {
			SynchronizationTask task = new NpcSynchronizationTask(player);
			executor.submit(new PhasedSynchronizationTask(phaser, task));
		}
		phaser.arriveAndAwaitAdvance();

		phaser.bulkRegister(playerCount);
		for (Player player : players) {
			SynchronizationTask task = new PostPlayerSynchronizationTask(player);
			executor.submit(new PhasedSynchronizationTask(phaser, task));
		}
		phaser.arriveAndAwaitAdvance();

		phaser.bulkRegister(npcCount);
		for (Npc npc : npcs) {
			SynchronizationTask task = new PostNpcSynchronizationTask(npc);
			executor.submit(new PhasedSynchronizationTask(phaser, task));
		}
		phaser.arriveAndAwaitAdvance();
	}

}
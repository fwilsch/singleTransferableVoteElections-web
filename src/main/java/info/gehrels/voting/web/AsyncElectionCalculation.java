/*
 * Copyright © 2014 Benjamin Gehrels
 *
 * This file is part of The Single Transferable Vote Elections Web Interface.
 *
 * The Single Transferable Vote Elections Web Interface is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * The Single Transferable Vote Elections Web Interface is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with The Single Transferable Vote
 * Elections Web Interface. If not, see <http://www.gnu.org/licenses/>.
 */
package info.gehrels.voting.web;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import info.gehrels.voting.AmbiguityResolver;
import info.gehrels.voting.AmbiguityResolver.AmbiguityResolverResult;
import info.gehrels.voting.Ballot;
import info.gehrels.voting.NotMoreThanTheAllowedNumberOfCandidatesCanReachItQuorum;
import info.gehrels.voting.genderedElections.ElectionCalculationWithFemaleExclusivePositions;
import info.gehrels.voting.genderedElections.ElectionCalculationWithFemaleExclusivePositions.Result;
import info.gehrels.voting.genderedElections.GenderedCandidate;
import info.gehrels.voting.genderedElections.GenderedElection;
import info.gehrels.voting.genderedElections.StringBuilderBackedElectionCalculationWithFemaleExclusivePositionsListener;
import info.gehrels.voting.singleTransferableVote.STVElectionCalculationFactory;
import info.gehrels.voting.singleTransferableVote.StringBuilderBackedSTVElectionCalculationListener;
import org.joda.time.DateTime;

import java.util.List;

import static org.apache.commons.math3.fraction.BigFraction.ONE;

public final class AsyncElectionCalculation implements Runnable {
	private final List<GenderedElection> elections;
	private final ImmutableCollection<Ballot<GenderedCandidate>> ballots;
	private final ElectionCalculationWithFemaleExclusivePositions electionCalculation;
	private final StringBuilder auditLogBuilder;
	private final ImmutableList.Builder<ElectionCalculationResultBean> resultModelBuilder = ImmutableList.builder();
	private final DateTime startDateTime = DateTime.now();

	private ImmutableList<ElectionCalculationResultBean> result;
	private ElectionCalculationState state = ElectionCalculationState.NOT_YET_STARTED;
	private Optional<AmbiguityResolverResult<GenderedCandidate>> ambiguityResulutionResult;
	private Optional<AmbiguityResolutionTask> ambiguityResulutionTask;

	public AsyncElectionCalculation(List<GenderedElection> elections,
	                                ImmutableCollection<Ballot<GenderedCandidate>> ballots) {
		this.elections = elections;
		this.ballots = ballots;
		this.auditLogBuilder = new StringBuilder();
		this.electionCalculation = createGenderedElectionCalculation();
	}

	@Override
	public void run() {
		ElectionCalculationState currentState = getState();
		if (currentState != ElectionCalculationState.NOT_YET_STARTED) {
			throw new IllegalStateException(
				"Election calculations may only be started once. Current State: " + currentState);
		}

		setState(ElectionCalculationState.RUNNING);
		for (GenderedElection election : elections) {
			reset(auditLogBuilder);
			Result electionResult = electionCalculation
				.calculateElectionResult(election, ImmutableList.copyOf(ballots));
			String auditLog = auditLogBuilder.toString();
			resultModelBuilder.add(new ElectionCalculationResultBean(election, electionResult, auditLog));
			setResult(resultModelBuilder.build());
		}

		setState(ElectionCalculationState.FINISHED);
	}

	private synchronized void setResult(ImmutableList<ElectionCalculationResultBean> result) {
		this.result = result;
	}

	private synchronized ElectionCalculationState getState() {
		return state;
	}

	private synchronized void setState(ElectionCalculationState state) {
		this.state = state;
		notifyAll();
	}

	public synchronized void setAmbiguityResulutionResult(AmbiguityResolverResult<GenderedCandidate> ambiguityResolverResult) {
		this.ambiguityResulutionResult = Optional.fromNullable(ambiguityResolverResult);
		if (ambiguityResulutionResult.isPresent()) {
			setState(ElectionCalculationState.AMBIGUITY_RESOLVED);
		}
	}

	private ElectionCalculationWithFemaleExclusivePositions createGenderedElectionCalculation() {
		return new ElectionCalculationWithFemaleExclusivePositions(
			new STVElectionCalculationFactory<>(
				createQuorumCalculation(),
				new StringBuilderBackedSTVElectionCalculationListener<GenderedCandidate>(auditLogBuilder),
				new TakeFirstOneAmbiguityResolver(this)),
			new StringBuilderBackedElectionCalculationWithFemaleExclusivePositionsListener(auditLogBuilder));
	}

	private void reset(StringBuilder stringBuilder) {
		stringBuilder.setLength(0);
	}

	public synchronized Snapshot getSnapshot() {
		return new Snapshot(startDateTime, state, result, ambiguityResulutionTask);
	}

	private NotMoreThanTheAllowedNumberOfCandidatesCanReachItQuorum createQuorumCalculation() {
		// § 18 Satz 2 Nr. 2 WahlO-GJ:
		// Berechne das Quorum: q = [(gültige Stimmen) / (zu vergebende Sitze + 1)] +1. Hat der so berechnete Wert des
		// Quorums mehr als sieben Nachkommastellen, so wird das Quorum auf sieben Nachkommastellen aufgerundet, d.h.
		// die überzähligen	Nachkommastellen werden abgeschnitten und der Wert des Quorums wird	um die	kleinste
		// positive Zahl, die mit sieben Nachkommastellen darstellbar ist, erhöht.
		// TODO: SÄ zur Streichung der Rundung bei der Quorenberechnung
		return new NotMoreThanTheAllowedNumberOfCandidatesCanReachItQuorum(ONE);
	}

	public DateTime getStartDateTime() {
		return startDateTime;
	}

	public synchronized Optional<AmbiguityResolverResult<GenderedCandidate>> getAmbiguityResulutionResult() {
		return ambiguityResulutionResult;
	}

	public synchronized Optional<AmbiguityResolutionTask> getAmbiguityResulutionTask() {
		return ambiguityResulutionTask;
	}

	// TODO: Verfahren nach Satzung implementieren
	// § 18 Satz 2 Nr. 7 sub (II) WahlO-GJ:
	// Haben mehrere KandidatInnen einen Überschuss, so wird zunächst der größte Überschuss übertragen. Haben zwei oder
	// mehr KandidatInnen einen gleich großen Überschuss, so wird der Überschuss jener / jenes dieser KandidatInnen
	// zuerst übertragen, die / der die meisten Stimmen hatte, als sich die	Stimmenzahl der	betreffenden KandidatInnen
	// zuletzt unterschied; hatten zwei oder mehr dieser KandidatInnen zu jedem Zeitpunkt jeweils die gleiche
	// Stimmenzahl, so wird durch eine Zufallsauswahl entschieden, welcher Überschuss als erstes übertragen wird.
	// TODO: Eventuell eine synchrone Übertragung bei Gleichstand in die Satzung schreiben?
	// TODO: Auch für die Stimmgewichtsübertragung bei Patt beim Streichen von Kandidierenden einfacheres Verfahren finden:
	// § 18 Satz 2 Nr. 8 sub (i) WahlO-GJ
	// Falls zwei oder mehr KandidatInnen gleichermaßen die wenigsten Stimmen haben, so wird jeneR dieser KandidatInnen
	// aus dem Rennen genommen, die / der die wenigsten Stimmen hatte, als sich die Stimmenzahl der betreffenden
	// KandidatInnen zuletzt unterschied; hatten zwei oder mehr dieser KandidatInnen zu jedem Zeitpunkt jeweils die
	// gleiche Stimmenzahl, so wird durch eine Zufallsauswahl entschieden, welcheR dieser KandidatInnen aus dem Rennen
	// ausscheidet
		/*
		 * § 19 Abs. 4 WahlO-GJ:
		 * Sofern Zufallsauswahlen gemäß § 18 Nr. 7, 8 erforderlich sind, entscheidet das von der Tagungsleitung zu ziehende
		 * Los; die Ziehung und die Eingabe des Ergebnisses in den Computer müssen mitgliederöffentlich erfolgen.
		 */
	private static final class TakeFirstOneAmbiguityResolver implements AmbiguityResolver<GenderedCandidate> {
		private final AsyncElectionCalculation asyncElectionCalculation;

		private TakeFirstOneAmbiguityResolver(AsyncElectionCalculation asyncElectionCalculation) {
			this.asyncElectionCalculation = asyncElectionCalculation;
		}

		@Override
		public AmbiguityResolverResult<GenderedCandidate> chooseOneOfMany(
			ImmutableSet<GenderedCandidate> bestCandidates) {

			synchronized (asyncElectionCalculation) {
				asyncElectionCalculation.setAmbuguityResulutionTask(new AmbiguityResolutionTask(bestCandidates, asyncElectionCalculation.getCurrentLog()));
				while (asyncElectionCalculation.getState() != ElectionCalculationState.AMBIGUITY_RESOLVED) {
					try {
						asyncElectionCalculation.wait();
					} catch (InterruptedException e) {
						// TODO: Gaaaanz schlechte Idee...
					}
				}
			}
			return asyncElectionCalculation.getAmbiguityResulutionResult().get();
		}

	}

	private String getCurrentLog() {
		return auditLogBuilder.toString();
	}

	private synchronized void setAmbuguityResulutionTask(AmbiguityResolutionTask ambuguityResulutionTask) {
		setState(ElectionCalculationState.MANUAL_AMBIGUITY_RESOLUTION_NECCESSARY);
		this.ambiguityResulutionResult = Optional.absent();
		this.ambiguityResulutionTask = Optional.of(ambuguityResulutionTask);
	}

	public static final class AmbiguityResolutionTask {

		private final ImmutableSet<GenderedCandidate> candidatesToChooseFrom;
		private final String currentLog;

		public AmbiguityResolutionTask(ImmutableSet<GenderedCandidate> candidatesToChooseFrom, String currentLog) {
			this.candidatesToChooseFrom = candidatesToChooseFrom;
			this.currentLog = currentLog;
		}

		public ImmutableSet<GenderedCandidate> getCandidatesToChooseFrom() {
			return candidatesToChooseFrom;
		}

		public String getCurrentLog() {
			return currentLog;
		}
	}

	public static final class Snapshot {
		private final DateTime startDateTime;
		private final ImmutableList<ElectionCalculationResultBean> resultsOfFinishedCalculations;
		private final Optional<AmbiguityResolutionTask> ambiguityResulutionTask;
		private final ElectionCalculationState state;

		public Snapshot(DateTime startDateTime, ElectionCalculationState state,
		                ImmutableList<ElectionCalculationResultBean> result,
		                Optional<AmbiguityResolutionTask> ambiguityResulutionTask) {
			this.startDateTime = startDateTime;
			this.state = state;
			this.resultsOfFinishedCalculations = result;
			this.ambiguityResulutionTask = ambiguityResulutionTask;
		}

		public DateTime getStartDateTime() {
			return startDateTime;
		}

		public ImmutableList<ElectionCalculationResultBean> getResultsOfFinishedCalculations() {
			return resultsOfFinishedCalculations;
		}

		public ElectionCalculationState getState() {
			return state;
		}

		public AmbiguityResolutionTask getAmbiguityResulutionTask() {
			return ambiguityResulutionTask.orNull();
		}
	}

}

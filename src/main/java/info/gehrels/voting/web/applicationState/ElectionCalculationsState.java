package info.gehrels.voting.web.applicationState;

import com.google.common.collect.ImmutableMap;
import info.gehrels.voting.web.AsyncElectionCalculation;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map;

public class ElectionCalculationsState {
	private Map<DateTime, AsyncElectionCalculation> historyOfElectionCalculations = new HashMap<>();

	public void addElectionCalculation(AsyncElectionCalculation asyncElectionCalculation) {
		historyOfElectionCalculations.put(new DateTime(), asyncElectionCalculation);
	}

	public ImmutableMap<DateTime, AsyncElectionCalculation> getHistoryOfElectionCalculations() {
		return ImmutableMap.copyOf(historyOfElectionCalculations);
	}
}
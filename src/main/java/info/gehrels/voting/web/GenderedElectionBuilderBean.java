package info.gehrels.voting.web;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import info.gehrels.voting.genderedElections.GenderedCandidate;
import info.gehrels.voting.genderedElections.GenderedElection;

import java.util.List;

public class GenderedElectionBuilderBean {
	private String officeName;
	private int numberOfFemaleExclusivePositions;
	private int numberOfNonFemaleExclusivePositions;
	private List<GenderedCandidateBuilderBean> candidates;

	public String getOfficeName() {
		return officeName;
	}

	public void setOfficeName(String officeName) {
		this.officeName = officeName;
	}

	public int getNumberOfFemaleExclusivePositions() {
		return numberOfFemaleExclusivePositions;
	}

	public void setNumberOfFemaleExclusivePositions(int numberOfFemaleExclusivePositions) {
		this.numberOfFemaleExclusivePositions = numberOfFemaleExclusivePositions;
	}

	public int getNumberOfNonFemaleExclusivePositions() {
		return numberOfNonFemaleExclusivePositions;
	}

	public void setNumberOfNonFemaleExclusivePositions(int numberOfNonFemaleExclusivePositions) {
		this.numberOfNonFemaleExclusivePositions = numberOfNonFemaleExclusivePositions;
	}

	public List<GenderedCandidateBuilderBean> getCandidates() {
		return candidates;
	}

	public void setCandidates(List<GenderedCandidateBuilderBean> candidates) {
		this.candidates = candidates;
	}

	public GenderedElection build() {
		Builder<GenderedCandidate> builder = ImmutableSet.builder();
		for (GenderedCandidateBuilderBean candidate : candidates) {
			builder.add(candidate.build());
		}
		return new GenderedElection(this.officeName, this.numberOfFemaleExclusivePositions,
		                            this.numberOfNonFemaleExclusivePositions, builder.build());
	}
}

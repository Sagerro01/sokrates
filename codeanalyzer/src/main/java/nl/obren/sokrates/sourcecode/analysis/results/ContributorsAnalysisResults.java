package nl.obren.sokrates.sourcecode.analysis.results;

import com.fasterxml.jackson.annotation.JsonIgnore;
import nl.obren.sokrates.sourcecode.contributors.ContributionYear;
import nl.obren.sokrates.sourcecode.contributors.Contributor;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ContributorsAnalysisResults {
    private List<Contributor> contributors = new ArrayList<>();

    private List<ContributionYear> contributorsPerYear = new ArrayList<>();

    public List<Contributor> getContributors() {
        return contributors;
    }

    public void setContributors(List<Contributor> contributors) {
        this.contributors = contributors;
    }

    public List<ContributionYear> getContributorsPerYear() {
        return contributorsPerYear;
    }

    public void setContributorsPerYear(List<ContributionYear> contributorsPerYear) {
        this.contributorsPerYear = contributorsPerYear;
    }

    @JsonIgnore
    public int getCommitsCount() {
        return contributors.stream().mapToInt(c -> c.getCommitsCount()).sum();
    }

    @JsonIgnore
    public int getCommitsThisYear() {
        String year = "" + Calendar.getInstance().get(Calendar.YEAR);

        return contributorsPerYear.stream().filter(c -> c.getYear().equals(year)).mapToInt(c -> c.getCommitsCount()).sum();
    }
}

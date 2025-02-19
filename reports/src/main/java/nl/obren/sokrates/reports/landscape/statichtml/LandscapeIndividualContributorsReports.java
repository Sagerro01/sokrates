package nl.obren.sokrates.reports.landscape.statichtml;

import nl.obren.sokrates.reports.core.RichTextReport;
import nl.obren.sokrates.reports.utils.DataImageUtils;
import nl.obren.sokrates.sourcecode.contributors.Contributor;
import nl.obren.sokrates.sourcecode.filehistory.DateUtils;
import nl.obren.sokrates.sourcecode.landscape.analysis.ContributorProjectInfo;
import nl.obren.sokrates.sourcecode.landscape.analysis.ContributorProjects;
import nl.obren.sokrates.sourcecode.landscape.analysis.LandscapeAnalysisResults;
import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LandscapeIndividualContributorsReports {
    private LandscapeAnalysisResults landscapeAnalysisResults;
    private List<RichTextReport> reports = new ArrayList<>();
    private boolean recent = false;

    public LandscapeIndividualContributorsReports(LandscapeAnalysisResults landscapeAnalysisResults) {
        this.landscapeAnalysisResults = landscapeAnalysisResults;
    }

    public static String getSafeFileName(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        string = Normalizer.normalize(string, Normalizer.Form.NFD);
        for (char c : string.toCharArray()) {
            if (c == '.' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else {
                sb.append("_");
            }
        }
        return sb.toString();
    }

    public static String getContributorIndividualReportFileName(String email) {
        return getSafeFileName(email).toLowerCase() + ".html";
    }

    public List<RichTextReport> getIndividualReports(List<ContributorProjects> contributors) {
        contributors.forEach(contributor -> {
            reports.add(getIndividualReport(contributor));
        });

        return reports;
    }

    private RichTextReport getIndividualReport(ContributorProjects contributorProjects) {
        Contributor contributor = contributorProjects.getContributor();
        RichTextReport report = new RichTextReport(contributor.getEmail(), getContributorIndividualReportFileName(contributor.getEmail()));
        report.setDisplayName(contributor.getEmail());
        report.setLogoLink(DataImageUtils.DEVELOPER);

        report.startDiv("margin-top: 10px; margin-bottom: 22px;");
        String template = this.landscapeAnalysisResults.getConfiguration().getContributorLinkTemplate();
        if (StringUtils.isNotBlank(template)) {
            String link = LandscapeContributorsReport.getContributorUrlFromTemplate(contributor.getEmail(), template);
            report.addNewTabLink("More details...", link);
            report.setParentUrl(link);
            report.addLineBreak();
            report.addLineBreak();
        }
        report.addContentInDiv("First commit date: <b>" + contributor.getFirstCommitDate() + "</b>");
        report.addContentInDiv("Latest commit date: <b>" + contributor.getLatestCommitDate() + "</b>");
        report.addContentInDiv("Projects count: " +
                "<b>" + contributorProjects.getProjects().stream().filter(p -> p.getCommits30Days() > 0).count()
                + "</b><span style='color: lightgrey; font-size: 90%'> (30d)</span>&nbsp;&nbsp;&nbsp;" + "<b>" + contributorProjects.getProjects().stream().filter(p -> p.getCommits90Days() > 0).count()
                + "</b><span style='color: lightgrey; font-size: 90%'> (3m)</span>&nbsp;&nbsp;&nbsp;" +
                "<b>" + contributorProjects.getProjects().size() + "</b> <span style='color: lightgrey; font-size: 90%'> (all time)</span>");
        report.addContentInDiv("Commits count: <b>" + contributor.getCommitsCount30Days() + "</b> " +
                "<span style='color: lightgrey; font-size: 90%'>(30d)&nbsp;&nbsp;&nbsp;</span>" +
                "<b>" + contributor.getCommitsCount90Days() + "</b><span style='color: lightgrey; font-size: 90%'> (3m)&nbsp;&nbsp;&nbsp;</span>" +
                "<b>" + contributor.getCommitsCount180Days() + "</b><span style='color: lightgrey; font-size: 90%'> (6m)&nbsp;&nbsp;&nbsp;</span>" +
                "<b>" + contributor.getCommitsCount365Days() + "</b><span style='color: lightgrey; font-size: 90%'> (1y)&nbsp;&nbsp;&nbsp;</span>" +
                "<b>" + contributor.getCommitsCount() + "</b><span style='color: lightgrey; font-size: 90%'> (all time)</span>"
        );
        report.endDiv();

        report.startTabGroup();
        report.addTab("month", "Project Activity Per Month", true);
        report.addTab("week", "Per Week", false);
        report.endTabGroup();

        Collections.sort(contributorProjects.getProjects(), (a, b) -> 10000 * (b.getCommits30Days() - a.getCommits30Days()) +
                100 * (b.getCommits90Days() - a.getCommits90Days()) +
                (b.getCommitsCount() - a.getCommitsCount()));

        report.startTabContentSection("week", false);
        addPerWeek(contributorProjects, report);
        report.endTabContentSection();

        report.startTabContentSection("month", true);
        addPerMonth(contributorProjects, report);
        report.endTabContentSection();

        return report;
    }

    private void addPerWeek(ContributorProjects contributorProjects, RichTextReport report) {

        report.startDiv("width: 100%; overflow-x: scroll;");
        report.startTable();

        final List<String> pastWeeks = DateUtils.getPastWeeks(104, landscapeAnalysisResults.getLatestCommitDate());
        report.startTableRow();
        report.addTableCell("", "min-width: 300px; border: none");
        report.addTableCell("Commits<br>(3m)", "max-width: 100px; text-align: center; border: none");
        report.addTableCell("Commit<br>Days", "max-width: 100px; text-align: center; border: none");
        List<ContributorProjectInfo> projects = new ArrayList<>(contributorProjects.getProjects());
        pastWeeks.forEach(pastWeek -> {
            int projectCount[] = {0};
            projects.forEach(project -> {
                boolean found[] = {false};
                project.getCommitDates().forEach(date -> {
                    String weekMonday = DateUtils.getWeekMonday(date);
                    if (weekMonday.equals(pastWeek)) {
                        found[0] = true;
                        return;
                    }
                });
                if (found[0]) {
                    projectCount[0] += 1;
                    return;
                }
            });
            String tooltip = "Week of " + pastWeek + ": " + projectCount[0] + (projectCount[0] == 1 ? " project" : " projects");
            report.startTableCell("font-size: 70%; border: none; color: lightgrey; text-align: center");
            report.addContentInDivWithTooltip(projectCount[0] + "", tooltip, "text-align: center");
            report.endTableCell();
        });
        report.endTableRow();

        List<ContributorProjectInfo> activeProjects = new ArrayList<>();

        projects.forEach(project -> {
            int daysCount[] = {0};
            pastWeeks.forEach(pastWeek -> {
                project.getCommitDates().forEach(date -> {
                    String weekMonday = DateUtils.getWeekMonday(date);
                    if (weekMonday.equals(pastWeek)) {
                        daysCount[0] += 1;
                    }
                });

            });
            if (daysCount[0] > 0) {
                activeProjects.add(project);
            }
        });

        Collections.sort(activeProjects, (a, b) -> b.getLatestCommitDate().compareTo(a.getLatestCommitDate()));

        activeProjects.forEach(project -> {
            String textOpacity = project.getCommits90Days() > 0 ? "font-weight: bold;" : "opacity: 0.4";
            report.startTableRow();
            report.startTableCell("border: none;" + textOpacity);
            report.addNewTabLink(project.getProjectAnalysisResults().getAnalysisResults().getMetadata().getName(),
                    "../../" + project.getProjectAnalysisResults().getSokratesProjectLink().getHtmlReportsRoot() + "/index.html");
            report.endTableCell();
            report.addTableCell(project.getCommits90Days() > 0 ? project.getCommits90Days() + "" : "-", "text-align: center; border: none; " + textOpacity);
            report.addTableCell(project.getCommitDates().size() + "", "text-align: center; border: none; " + textOpacity);
            int index[] = {0};
            pastWeeks.forEach(pastWeek -> {
                int daysCount[] = {0};
                index[0] += 1;
                project.getCommitDates().forEach(date -> {
                    String weekMonday = DateUtils.getWeekMonday(date);
                    if (weekMonday.equals(pastWeek)) {
                        daysCount[0] += 1;
                    }
                });
                report.startTableCell("text-align: center; padding: 0; border: none; vertical-align: middle");
                if (daysCount[0] > 0) {
                    int size = 10 + daysCount[0] * 4;
                    String tooltip = "Week of " + pastWeek + ": " + daysCount[0] + (daysCount[0] == 1 ? " commit day" : " commit days");
                    String opacity = "" + Math.max(0.9 - (index[0] - 1) * 0.05, 0.2);
                    report.addContentInDivWithTooltip("", tooltip,
                            "display: inline-block; padding: 0; margin: 0; " +
                                    "background-color: #483D8B; border-radius: 50%; width: " + size + "px; height: " + size + "px; opacity: " + opacity + ";");
                } else {
                    report.addContentInDiv("-", "color: lightgrey; font-size: 80%");
                }
                report.endTableCell();
            });
            report.endTableRow();
        });
        report.endTable();
        report.endDiv();
    }

    private void addPerMonth(ContributorProjects contributorProjects, RichTextReport report) {
        report.startDiv("width: 100%; overflow-x: scroll;");
        report.startTable();

        final List<String> pastMonths = DateUtils.getPastMonths(24, landscapeAnalysisResults.getLatestCommitDate());
        report.startTableRow();
        report.addTableCell("", "min-width: 200px; border: none; border: none");
        report.addTableCell("Commits<br>(3m)", "max-width: 100px; text-align: center; border: none");
        report.addTableCell("Commit<br>Days", "max-width: 100px; text-align: center; border: none");
        pastMonths.forEach(pastMonth -> {
            int projectCount[] = {0};
            contributorProjects.getProjects().forEach(project -> {
                boolean found[] = {false};
                project.getCommitDates().forEach(date -> {
                    String weekMonday = DateUtils.getMonth(date);
                    if (weekMonday.equals(pastMonth)) {
                        found[0] = true;
                        return;
                    }
                });
                if (found[0]) {
                    projectCount[0] += 1;
                    return;
                }
            });
            String tooltip = "Month " + pastMonth + ": " + projectCount[0] + (projectCount[0] == 1 ? " project" : " projects");
            report.startTableCell("font-size: 70%; border: none; color: lightgrey; text-align: center");
            report.addContentInDivWithTooltip(projectCount[0] + "", tooltip, "text-align: center");
            report.endTableCell();
        });
        report.endTableRow();
        List<ContributorProjectInfo> projects = new ArrayList<>(contributorProjects.getProjects());
        Collections.sort(projects, (a, b) -> b.getLatestCommitDate().compareTo(a.getLatestCommitDate()));

        projects.forEach(project -> {
            report.startTableRow();
            String textOpacity = project.getCommits90Days() > 0 ? "font-weight: bold;" : "opacity: 0.4";
            report.startTableCell("border: none; " + textOpacity);
            report.addNewTabLink(project.getProjectAnalysisResults().getAnalysisResults().getMetadata().getName(),
                    "../../" + project.getProjectAnalysisResults().getSokratesProjectLink().getHtmlReportsRoot() + "/index.html");
            report.endTableCell();
            report.addTableCell(project.getCommits90Days() > 0 ? project.getCommits90Days() + "" : "-", "text-align: center; border: none; " + textOpacity);
            report.addTableCell(project.getCommitDates().size() + "", "text-align: center; border: none; " + textOpacity);
            int index[] = {0};
            pastMonths.forEach(pastMonth -> {
                int count[] = {0};
                project.getCommitDates().forEach(date -> {
                    String month = DateUtils.getMonth(date);
                    if (month.equals(pastMonth)) {
                        count[0] += 1;
                    }
                });
                index[0] += 1;
                report.startTableCell("text-align: center; padding: 0; border: none; vertical-align: middle;");
                if (count[0] > 0) {
                    int size = 10 + (count[0] / 4) * 4;
                    String tooltip = "Month " + pastMonth + ": " + count[0] + (count[0] == 1 ? " commit day" : " commit days");
                    String opacity = "" + Math.max(0.9 - (index[0] - 1) * 0.2, 0.2);
                    report.addContentInDivWithTooltip("", tooltip,
                            "padding: 0; margin: 0; display: inline-block; background-color: #483D8B; opacity: " + opacity + "; border-radius: 50%; width: " + size + "px; height: " + size + "px;");
                } else {
                    report.addContentInDiv("-", "color: lightgrey; font-size: 80%");
                }
                report.endTableCell();
            });
            report.endTableRow();
        });
        report.endTable();
        report.endDiv();
    }

}

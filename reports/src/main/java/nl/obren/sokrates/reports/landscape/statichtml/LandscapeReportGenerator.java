/*
 * Copyright (c) 2021 Željko Obrenović. All rights reserved.
 */

package nl.obren.sokrates.reports.landscape.statichtml;

import nl.obren.sokrates.common.io.JsonMapper;
import nl.obren.sokrates.common.renderingutils.VisualizationItem;
import nl.obren.sokrates.common.renderingutils.VisualizationTemplate;
import nl.obren.sokrates.common.renderingutils.force3d.Force3DLink;
import nl.obren.sokrates.common.renderingutils.force3d.Force3DNode;
import nl.obren.sokrates.common.renderingutils.force3d.Force3DObject;
import nl.obren.sokrates.common.utils.FormattingUtils;
import nl.obren.sokrates.reports.core.RichTextReport;
import nl.obren.sokrates.reports.landscape.data.LandscapeDataExport;
import nl.obren.sokrates.reports.utils.DataImageUtils;
import nl.obren.sokrates.reports.utils.GraphvizDependencyRenderer;
import nl.obren.sokrates.sourcecode.Metadata;
import nl.obren.sokrates.sourcecode.analysis.results.CodeAnalysisResults;
import nl.obren.sokrates.sourcecode.contributors.ContributionTimeSlot;
import nl.obren.sokrates.sourcecode.contributors.Contributor;
import nl.obren.sokrates.sourcecode.dependencies.ComponentDependency;
import nl.obren.sokrates.sourcecode.filehistory.DateUtils;
import nl.obren.sokrates.sourcecode.githistory.CommitsPerExtension;
import nl.obren.sokrates.sourcecode.landscape.ContributorConnectionUtils;
import nl.obren.sokrates.sourcecode.landscape.LandscapeConfiguration;
import nl.obren.sokrates.sourcecode.landscape.SubLandscapeLink;
import nl.obren.sokrates.sourcecode.landscape.WebFrameLink;
import nl.obren.sokrates.sourcecode.landscape.analysis.*;
import nl.obren.sokrates.sourcecode.metrics.NumericMetric;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

interface ContributorsExtractor {
    List<String> getContributors(String timeSlot, boolean rookiesOnly);
}

public class LandscapeReportGenerator {
    public static final String DEPENDENCIES_ICON = "\n" +
            "<svg height='100px' width='100px'  fill=\"#000000\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\" x=\"0px\" y=\"0px\" viewBox=\"0 0 48 48\" enable-background=\"new 0 0 48 48\" xml:space=\"preserve\"><path d=\"M12,19.666v6.254l1.357-1.357c0.391-0.391,1.023-0.391,1.414,0s0.391,1.023,0,1.414l-3.064,3.064  c-0.195,0.195-0.451,0.293-0.707,0.293s-0.512-0.098-0.707-0.293l-3.064-3.064c-0.391-0.391-0.391-1.023,0-1.414  s1.023-0.391,1.414,0L10,25.92v-6.254c0-0.552,0.448-1,1-1S12,19.114,12,19.666z M28.334,36H22.08l1.357-1.357  c0.391-0.391,0.391-1.023,0-1.414s-1.023-0.391-1.414,0l-3.064,3.064c-0.391,0.391-0.391,1.023,0,1.414l3.064,3.064  c0.195,0.195,0.451,0.293,0.707,0.293s0.512-0.098,0.707-0.293c0.391-0.391,0.391-1.023,0-1.414L22.08,38h6.254c0.553,0,1-0.447,1-1  S28.887,36,28.334,36z M37,18.666c-0.553,0-1,0.448-1,1v6.254l-1.357-1.357c-0.391-0.391-1.023-0.391-1.414,0s-0.391,1.023,0,1.414  l3.064,3.064c0.195,0.195,0.451,0.293,0.707,0.293s0.512-0.098,0.707-0.293l3.064-3.064c0.391-0.391,0.391-1.023,0-1.414  s-1.023-0.391-1.414,0L38,25.92v-6.254C38,19.114,37.553,18.666,37,18.666z M31.58,16.421c-0.391-0.391-1.023-0.391-1.414,0  L18.127,28.458v-1.92c0-0.553-0.448-1-1-1s-1,0.447-1,1v4.334c0,0.13,0.027,0.26,0.077,0.382c0.101,0.245,0.296,0.439,0.541,0.541  c0.122,0.051,0.251,0.077,0.382,0.077h4.333c0.552,0,1-0.447,1-1s-0.448-1-1-1h-1.919L31.58,17.835  C31.971,17.444,31.971,16.812,31.58,16.421z M16.334,37c0,2.941-2.393,5.334-5.334,5.334S5.666,39.941,5.666,37  S8.059,31.666,11,31.666S16.334,34.059,16.334,37z M14.334,37c0-1.838-1.496-3.334-3.334-3.334S7.666,35.162,7.666,37  S9.162,40.334,11,40.334S14.334,38.838,14.334,37z M42.334,37c0,2.941-2.393,5.334-5.334,5.334S31.666,39.941,31.666,37  s2.393-5.334,5.334-5.334S42.334,34.059,42.334,37z M40.334,37c0-1.838-1.496-3.334-3.334-3.334S33.666,35.162,33.666,37  s1.496,3.334,3.334,3.334S40.334,38.838,40.334,37z M5.666,11c0-2.941,2.393-5.334,5.334-5.334S16.334,8.059,16.334,11  S13.941,16.334,11,16.334S5.666,13.941,5.666,11z M7.666,11c0,1.838,1.496,3.334,3.334,3.334s3.334-1.496,3.334-3.334  S12.838,7.666,11,7.666S7.666,9.162,7.666,11z M31.666,11c0-2.941,2.393-5.334,5.334-5.334S42.334,8.059,42.334,11  S39.941,16.334,37,16.334S31.666,13.941,31.666,11z M33.666,11c0,1.838,1.496,3.334,3.334,3.334s3.334-1.496,3.334-3.334  S38.838,7.666,37,7.666S33.666,9.162,33.666,11z\"></path></svg>";

    public static final int RECENT_THRESHOLD_DAYS = 30;
    public static final String OVERVIEW_TAB_ID = "overview";
    public static final String SOURCE_CODE_TAB_ID = "source code";
    public static final String CONTRIBUTORS_TAB_ID = "contributors";
    public static final String CUSTOM_TAB_ID_PREFIX = "custom_tab_";
    public static final String CONTRIBUTORS_30_D = "contributors_30d_";
    public static final String COMMITS_30_D = "commits_30d_";
    public static final String MAIN_LOC = "main_loc_";
    private static final Log LOG = LogFactory.getLog(LandscapeReportGenerator.class);
    private static final String DEVELOPER_SVG_ICON = "<svg width=\"16pt\" height=\"16pt\" version=\"1.1\" viewBox=\"0 0 100 100\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
            " <g>\n" +
            "  <path d=\"m82 61.801-14-14c-2.1016-2.1016-4.8008-3.1992-7.8008-3.1992h-20.398c-2.8984 0-5.6992 1.1016-7.8008 3.1992l-14 14c-1.3008 1.3008-2 3.1016-2 4.8984 0 1.8984 0.69922 3.6016 2 5l3.8984 3.8984c1.3008 1.3008 3.1016 2.1016 5 2.1016 1.8984 0 3.6016-0.69922 4.8984-2.1016l1.6016-1.6016v10c0 3.8984 3.1016 7 7 7h19.102c3.8984 0 7-3.1016 7-7v-9.9961l1.6016 1.6016c1.3008 1.3008 3.1016 2.1016 5 2.1016 1.8984 0 3.6016-0.69922 4.8984-2.1016l3.8984-3.8984c2.8008-2.8047 2.8008-7.2031 0.10156-9.9023zm-4.3008 5.5977-3.8984 3.8984c-0.39844 0.39844-1 0.39844-1.3984 0l-6.6992-6.6992c-0.89844-0.89844-2.1016-1.1016-3.3008-0.69922-1.1016 0.5-1.8984 1.6016-1.8984 2.8008l-0.003906 17.301c0 0.60156-0.39844 1-1 1h-19c-0.60156 0-1-0.39844-1-1v-17.301c0-1.1992-0.69922-2.3008-1.8984-2.8008-0.39844-0.19922-0.80078-0.19922-1.1016-0.19922-0.80078 0-1.6016 0.30078-2.1016 0.89844l-6.6992 6.6992c-0.39844 0.39844-1 0.39844-1.3984 0l-3.8984-3.8984c-0.39844-0.39844-0.39844-1 0-1.3984l14-14c0.89844-0.89844 2.1992-1.5 3.5-1.5h20.5c1.3008 0 2.6016 0.5 3.5 1.5l14 14c0.19922 0.19922 0.30078 0.39844 0.30078 0.69922-0.003906 0.30078-0.30469 0.5-0.50391 0.69922z\"></path>\n" +
            "  <path d=\"m50 42.102c9.1016 0 16.5-7.3984 16.5-16.5 0-9.2031-7.3984-16.602-16.5-16.602s-16.5 7.3984-16.5 16.5c0 9.1992 7.3984 16.602 16.5 16.602zm0-27.102c5.8008 0 10.5 4.6992 10.5 10.5s-4.6992 10.602-10.5 10.602-10.5-4.6992-10.5-10.5c0-5.8008 4.6992-10.602 10.5-10.602z\"></path>\n" +
            " </g>\n" +
            "</svg>";
    private static final String OPEN_IN_NEW_TAB_SVG_ICON = "<svg width=\"14pt\" height=\"14pt\" version=\"1.1\" viewBox=\"0 0 100 100\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
            " <path d=\"m87.5 16.918-35.289 35.289c-1.2266 1.1836-3.1719 1.168-4.3789-0.039062s-1.2227-3.1523-0.039062-4.3789l35.289-35.289h-23.707c-1.7266 0-3.125-1.3984-3.125-3.125s1.3984-3.125 3.125-3.125h31.25c0.82812 0 1.625 0.32812 2.2109 0.91406 0.58594 0.58594 0.91406 1.3828 0.91406 2.2109v31.25c0 1.7266-1.3984 3.125-3.125 3.125s-3.125-1.3984-3.125-3.125zm-56.25 1.832h-15.633c-5.1719 0-9.3672 4.1797-9.3672 9.3516v56.305c0 5.1562 4.2422 9.3516 9.3867 9.3516h56.219c2.4922 0 4.8828-0.98437 6.6406-2.7461 1.7617-1.7617 2.75-4.1523 2.7461-6.6445v-15.613 0.003906c0-1.7266-1.3984-3.125-3.125-3.125-1.7227 0-3.125 1.3984-3.125 3.125v15.613-0.003906c0.003906 0.83594-0.32422 1.6328-0.91406 2.2227s-1.3906 0.91797-2.2227 0.91797h-56.219c-1.7148-0.007812-3.1094-1.3867-3.1367-3.1016v-56.305c0-1.7148 1.3945-3.1016 3.1172-3.1016h15.633c1.7266 0 3.125-1.3984 3.125-3.125s-1.3984-3.125-3.125-3.125z\"/>\n" +
            "</svg>";
    ;
    private RichTextReport landscapeReport = new RichTextReport("Landscape Report", "index.html");
    private RichTextReport landscapeProjectsReport = new RichTextReport("", "projects.html");
    private RichTextReport landscapeRecentContributorsReport = new RichTextReport("", "contributors-recent.html");
    private RichTextReport landscapeContributorsReport = new RichTextReport("", "contributors.html");
    private LandscapeAnalysisResults landscapeAnalysisResults;
    private int dependencyVisualCounter = 1;
    private File folder;
    private File reportsFolder;
    private List<RichTextReport> individualContributorReports = new ArrayList<>();
    private Map<String, List<String>> contributorsPerWeekMap = new HashMap<>();
    private Map<String, List<String>> rookiesPerWeekMap = new HashMap<>();
    private Map<String, List<String>> contributorsPerMonthMap = new HashMap<>();
    private Map<String, List<String>> rookiesPerMonthMap = new HashMap<>();
    private Map<String, List<String>> contributorsPerYearMap = new HashMap<>();
    private Map<String, List<String>> rookiesPerYearMap = new HashMap<>();

    public LandscapeReportGenerator(LandscapeAnalysisResults landscapeAnalysisResults, File folder, File reportsFolder) {
        this.folder = folder;
        this.reportsFolder = reportsFolder;

        this.landscapeAnalysisResults = landscapeAnalysisResults;
        populateTimeSlotMaps();

        landscapeProjectsReport.setEmbedded(true);
        landscapeContributorsReport.setEmbedded(true);
        landscapeRecentContributorsReport.setEmbedded(true);
        LandscapeDataExport dataExport = new LandscapeDataExport(landscapeAnalysisResults, folder);

        System.out.println("Exporting projects...");
        dataExport.exportProjects();
        System.out.println("Exporting contributors...");
        dataExport.exportContributors();
        System.out.println("Exporting analysis results...");
        dataExport.exportAnalysisResults();

        LandscapeConfiguration configuration = landscapeAnalysisResults.getConfiguration();
        Metadata metadata = configuration.getMetadata();
        String landscapeName = metadata.getName();
        if (StringUtils.isNotBlank(landscapeName)) {
            landscapeReport.setDisplayName(landscapeName);
        }
        landscapeReport.setParentUrl(configuration.getParentUrl());
        landscapeReport.setLogoLink(metadata.getLogoLink());
        String description = metadata.getDescription();
        String tooltip = metadata.getTooltip();
        if (StringUtils.isNotBlank(description)) {
            if (StringUtils.isBlank(tooltip)) {
                landscapeReport.addParagraph(description, "font-size: 90%; color: #787878; margin-top: 8px; margin-bottom: 5px;");
            }
            if (StringUtils.isNotBlank(tooltip)) {
                landscapeReport.addParagraphWithTooltip(description, tooltip, "font-size: 90%; color: #787878; margin-top: 8px;");
            }
        }
        if (metadata.getLinks().size() > 0) {
            landscapeReport.startDiv("font-size: 80%; margin-top: 2px;");
            boolean first[] = {true};
            metadata.getLinks().forEach(link -> {
                if (!first[0]) {
                    landscapeReport.addHtmlContent(" | ");
                }
                landscapeReport.addNewTabLink(link.getLabel(), link.getHref());
                first[0] = false;
            });
            landscapeReport.endDiv();
        }

        landscapeReport.addLineBreak();

        landscapeReport.startTabGroup();
        landscapeReport.addTab(OVERVIEW_TAB_ID, "Overview", true);
        landscapeReport.addTab(SOURCE_CODE_TAB_ID, "Projects (" + landscapeAnalysisResults.getFilteredProjectAnalysisResults().size() + ")", false);
        landscapeReport.addTab(CONTRIBUTORS_TAB_ID, "Contributors (" + landscapeAnalysisResults.getRecentContributorsCount() + ")", false);
        configuration.getCustomTabs().forEach(tab -> {
            int index = configuration.getCustomTabs().indexOf(tab);
            landscapeReport.addTab(CUSTOM_TAB_ID_PREFIX + index, tab.getName(), false);
        });
        landscapeReport.endTabGroup();

        landscapeReport.startTabContentSection(OVERVIEW_TAB_ID, true);
        addBigSummary(landscapeAnalysisResults);
        if (configuration.isShowExtensionsOnFirstTab()) {
            addExtensions();
        }
        addIFrames(configuration.getiFramesAtStart());
        addIFrames(configuration.getiFrames());
        landscapeReport.endTabContentSection();

        landscapeReport.startTabContentSection(SOURCE_CODE_TAB_ID, false);
        System.out.println("Adding big summary...");
        addBigProjectsSummary(landscapeAnalysisResults);
        addIFrames(configuration.getiFramesProjectsAtStart());
        if (!configuration.isShowExtensionsOnFirstTab()) {
            System.out.println("Adding extensions...");
            addExtensions();
        }
        System.out.println("Adding project section...");
        addProjectsSection(configuration, getProjects());
        addIFrames(configuration.getiFramesProjects());
        landscapeReport.endTabContentSection();

        landscapeReport.startTabContentSection(CONTRIBUTORS_TAB_ID, false);
        System.out.println("Adding big contributors summary...");
        addBigContributorsSummary(landscapeAnalysisResults);
        addIFrames(configuration.getiFramesContributorsAtStart());
        System.out.println("Adding contributors...");
        addContributors();
        System.out.println("Adding people dependencies...");
        addPeopleDependencies();
        addIFrames(configuration.getiFramesContributors());
        landscapeReport.endTabContentSection();

        configuration.getCustomTabs().forEach(tab -> {
            int index = configuration.getCustomTabs().indexOf(tab);
            landscapeReport.startTabContentSection(CUSTOM_TAB_ID_PREFIX + index, false);
            landscapeReport.addLineBreak();
            addIFrames(tab.getiFrames());
            landscapeReport.endTabContentSection();
        });

        landscapeReport.addParagraph("<span style='color: grey; font-size: 90%'>updated: " + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "</span>");
        System.out.println("Done report generation.");
    }

    public static List<ContributionTimeSlot> getContributionWeeks(List<ContributionTimeSlot> contributorsPerWeekOriginal, int pastWeeks, String lastCommitDate) {
        List<ContributionTimeSlot> contributorsPerWeek = new ArrayList<>(contributorsPerWeekOriginal);
        List<String> slots = contributorsPerWeek.stream().map(slot -> slot.getTimeSlot()).collect(Collectors.toCollection(ArrayList::new));
        List<String> pastDates = DateUtils.getPastWeeks(pastWeeks, lastCommitDate);
        pastDates.forEach(pastDate -> {
            if (!slots.contains(pastDate)) {
                contributorsPerWeek.add(new ContributionTimeSlot(pastDate));
            }
        });
        return contributorsPerWeek;
    }

    public static List<ContributionTimeSlot> getContributionMonths(List<ContributionTimeSlot> contributorsPerMonthOriginal, int pastMonths, String lastCommitDate) {
        List<ContributionTimeSlot> contributorsPerMonth = new ArrayList<>(contributorsPerMonthOriginal);
        List<String> slots = contributorsPerMonth.stream().map(slot -> slot.getTimeSlot()).collect(Collectors.toCollection(ArrayList::new));
        List<String> pastDates = DateUtils.getPastMonths(pastMonths, lastCommitDate);
        pastDates.forEach(pastDate -> {
            if (!slots.contains(pastDate)) {
                contributorsPerMonth.add(new ContributionTimeSlot(pastDate));
            }
        });
        return contributorsPerMonth;
    }

    private void addPeopleDependencies() {
        landscapeReport.startSubSection("People Dependencies", "");

        List<ComponentDependency> peopleDependencies30Days = landscapeAnalysisResults.getPeopleDependencies30Days();
        List<ContributorConnections> connectionsViaProjects30Days = landscapeAnalysisResults.getConnectionsViaProjects30Days();
        this.renderPeopleDependencies(peopleDependencies30Days, connectionsViaProjects30Days,
                landscapeAnalysisResults.getcIndex30Days(), landscapeAnalysisResults.getpIndex30Days(),
                landscapeAnalysisResults.getcMean30Days(), landscapeAnalysisResults.getpMean30Days(),
                landscapeAnalysisResults.getcMedian30Days(), landscapeAnalysisResults.getpMedian30Days(),
                30);

        List<ComponentDependency> peopleDependencies90Days = landscapeAnalysisResults.getPeopleDependencies90Days();
        List<ContributorConnections> connectionsViaProjects90Days = landscapeAnalysisResults.getConnectionsViaProjects90Days();
        this.renderPeopleDependencies(peopleDependencies90Days, connectionsViaProjects90Days,
                landscapeAnalysisResults.getcIndex90Days(), landscapeAnalysisResults.getpIndex90Days(),
                landscapeAnalysisResults.getcMean90Days(), landscapeAnalysisResults.getpMean90Days(),
                landscapeAnalysisResults.getcMedian90Days(), landscapeAnalysisResults.getpMedian90Days(),
                90);

        List<ComponentDependency> peopleDependencies180Days = landscapeAnalysisResults.getPeopleDependencies180Days();
        List<ContributorConnections> connectionsViaProjects180Days = landscapeAnalysisResults.getConnectionsViaProjects180Days();
        this.renderPeopleDependencies(peopleDependencies180Days, connectionsViaProjects180Days,
                landscapeAnalysisResults.getcIndex180Days(), landscapeAnalysisResults.getpIndex180Days(),
                landscapeAnalysisResults.getcMean180Days(), landscapeAnalysisResults.getpMean180Days(),
                landscapeAnalysisResults.getcMedian180Days(), landscapeAnalysisResults.getpMedian180Days(),
                180);

        landscapeReport.endSection();
    }

    private List<ProjectAnalysisResults> getProjects() {
        return landscapeAnalysisResults.getFilteredProjectAnalysisResults();
    }

    private void addSubLandscapeSection(List<SubLandscapeLink> subLandscapes) {
        List<SubLandscapeLink> links = new ArrayList<>(subLandscapes);
        if (links.size() > 0) {
            Collections.sort(links, Comparator.comparing(a -> getLabel(a).toLowerCase()));
            landscapeReport.startSubSection("Sub-Landscapes (" + links.size() + ")", "");
            landscapeReport.addHtmlContent("zoomable circles: ");
            landscapeReport.addNewTabLink("contributors (30d)", "visuals/sub_landscapes_zoomable_circles_" + CONTRIBUTORS_30_D + ".html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("commits (30d)", "visuals/sub_landscapes_zoomable_circles_" + COMMITS_30_D + ".html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("lines of code (main)", "visuals/sub_landscapes_zoomable_circles_" + MAIN_LOC + ".html");
            landscapeReport.addLineBreak();
            landscapeReport.addHtmlContent("zoomable sunburst: ");
            landscapeReport.addNewTabLink("contributors (30d)", "visuals/sub_landscapes_zoomable_sunburst_" + CONTRIBUTORS_30_D + ".html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("commits (30d)", "visuals/sub_landscapes_zoomable_sunburst_" + COMMITS_30_D + ".html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("lines of code (main)", "visuals/sub_landscapes_zoomable_sunburst_" + MAIN_LOC + ".html");
            landscapeReport.addLineBreak();
            landscapeReport.addLineBreak();
            landscapeReport.startTable();
            landscapeReport.addTableHeader("", "projects", "main loc", "test loc", "other loc", "recent contributors", "commits (30 days)");
            String prevRoot[] = {""};
            List<LandscapeAnalysisResultsReadData> loadedSubLandscapes = new ArrayList<>();
            links.forEach(subLandscape -> {
                System.out.println("Adding " + subLandscape.getIndexFilePath());
                String label = StringUtils.removeEnd(getLabel(subLandscape), "/");
                String style = "";
                String root = label.replaceAll("/.*", "");
                if (!prevRoot[0].equals(root)) {
                    label = "<b>" + label + "</b>";
                    style = "color: black; font-weight: bold;";
                } else {
                    int lastIndex = label.lastIndexOf("/");
                    label = "<span style='color: lightgrey'>" + label.substring(0, lastIndex + 1) + "</span>" + label.substring(lastIndex + 1) + "";
                    style = "color: grey; font-size: 90%";
                }
                landscapeReport.startTableRow(style);
                landscapeReport.startTableCell();
                String href = landscapeAnalysisResults.getConfiguration().getProjectReportsUrlPrefix() + subLandscape.getIndexFilePath();
                landscapeReport.addNewTabLink(label, href);
                LandscapeAnalysisResultsReadData subLandscapeAnalysisResults = getSubLandscapeAnalysisResults(subLandscape);
                loadedSubLandscapes.add(subLandscapeAnalysisResults);
                landscapeReport.endTableCell();
                landscapeReport.startTableCell("text-align: right;");
                if (subLandscapeAnalysisResults != null) {
                    landscapeReport.addHtmlContent(FormattingUtils.formatCount(subLandscapeAnalysisResults.getProjectsCount()) + "");
                }
                landscapeReport.endTableCell();
                landscapeReport.startTableCell("text-align: right;");
                if (subLandscapeAnalysisResults != null) {
                    landscapeReport.addHtmlContent(FormattingUtils.formatCount(subLandscapeAnalysisResults.getMainLoc()) + "");
                }
                landscapeReport.endTableCell();
                landscapeReport.startTableCell("text-align: right;");
                if (subLandscapeAnalysisResults != null) {
                    landscapeReport.addHtmlContent(FormattingUtils.formatCount(subLandscapeAnalysisResults.getTestLoc()) + "");
                }
                landscapeReport.endTableCell();
                landscapeReport.endTableCell();
                landscapeReport.startTableCell("text-align: right;");
                if (subLandscapeAnalysisResults != null) {
                    int other = subLandscapeAnalysisResults.getBuildAndDeploymentLoc()
                            + subLandscapeAnalysisResults.getGeneratedLoc() + subLandscapeAnalysisResults.getOtherLoc();
                    landscapeReport.addHtmlContent("<span style='color: lightgrey'>" + FormattingUtils.formatCount(other) + "</span>");
                }
                landscapeReport.endTableCell();
                landscapeReport.startTableCell("text-align: right;");
                if (subLandscapeAnalysisResults != null) {
                    landscapeReport.addHtmlContent(FormattingUtils.formatCount(subLandscapeAnalysisResults.getRecentContributorsCount()) + "");
                }
                landscapeReport.endTableCell();
                landscapeReport.startTableCell("text-align: right;");
                if (subLandscapeAnalysisResults != null) {
                    landscapeReport.addHtmlContent(FormattingUtils.formatCount(subLandscapeAnalysisResults.getCommitsCount30Days()) + "");
                }
                landscapeReport.endTableCell();
                landscapeReport.endTableRow();

                prevRoot[0] = root;
            });
            landscapeReport.endTable();

            landscapeReport.endSection();
        }

    }

    private VisualizationItem getParent(Map<String, VisualizationItem> parents, List<String> pathElements) {
        String parentName = "";
        for (int i = 0; i < pathElements.size() - 1; i++) {
            if (parentName.length() > 0) {
                parentName += "/";
            }
            parentName += pathElements.get(i);
        }

        if (parents.containsKey(parentName)) {
            return parents.get(parentName);
        }

        VisualizationItem newParent = new VisualizationItem(parentName, 0);
        parents.put(parentName, newParent);

        if (parentName.length() > 0) {
            getParent(parents, pathElements.subList(0, pathElements.size() - 1)).getChildren().add(newParent);
        }

        return newParent;
    }

    private void exportZoomableCircles(String type, List<ProjectAnalysisResults> projectsAnalysisResults, ZommableCircleCountExtractors zommableCircleCountExtractors) {
        Map<String, VisualizationItem> parents = new HashMap<>();
        VisualizationItem root = new VisualizationItem("", 0);
        parents.put("", root);

        projectsAnalysisResults.forEach(analysisResults -> {
            String name = getProjectCircleName(analysisResults);
            String[] elements = name.split("/");
            System.out.println(name);
            if (elements.length > 1) {
                name = name.substring(elements[0].length() + 1);
            }
            int count = zommableCircleCountExtractors.getCount(analysisResults);
            if (count > 0) {
                VisualizationItem item = new VisualizationItem(name + " (" + FormattingUtils.getPlainTextForNumber(count) + ")", count);
                getParent(parents, Arrays.asList(elements)).getChildren().add(item);
            }
        });
        try {
            File folder = new File(reportsFolder, "visuals");
            folder.mkdirs();
            FileUtils.write(new File(folder, "sub_landscapes_zoomable_circles_" + type + ".html"), new VisualizationTemplate().renderZoomableCircles(root.getChildren()), UTF_8);
            FileUtils.write(new File(folder, "sub_landscapes_zoomable_sunburst_" + type + ".html"), new VisualizationTemplate().renderZoomableSunburst(root.getChildren()), UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getProjectCircleName(ProjectAnalysisResults analysisResults) {
        String name = analysisResults.getSokratesProjectLink().getAnalysisResultsPath().replace("\\", "/");
        name = name.replace("/data/analysisResults.json", "");
        return name;
    }

    private LandscapeAnalysisResultsReadData getSubLandscapeAnalysisResults(SubLandscapeLink subLandscape) {
        try {
            String prefix = landscapeAnalysisResults.getConfiguration().getProjectReportsUrlPrefix();
            File resultsFile = new File(new File(folder, prefix + subLandscape.getIndexFilePath()).getParentFile(), "data/landscapeAnalysisResults.json");
            System.out.println(resultsFile.getPath());
            String json = FileUtils.readFileToString(resultsFile, StandardCharsets.UTF_8);
            return (LandscapeAnalysisResultsReadData) new JsonMapper().getObject(json, LandscapeAnalysisResultsReadData.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private LandscapeConfiguration getSubLandscapeConfig(SubLandscapeLink subLandscape) {
        try {
            String prefix = landscapeAnalysisResults.getConfiguration().getProjectReportsUrlPrefix();
            File resultsFile = new File(new File(folder, prefix + subLandscape.getIndexFilePath()).getParentFile(), "config.json");
            System.out.println(resultsFile.getPath());
            String json = FileUtils.readFileToString(resultsFile, StandardCharsets.UTF_8);
            return (LandscapeConfiguration) new JsonMapper().getObject(json, LandscapeConfiguration.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getLabel(SubLandscapeLink subLandscape) {
        return subLandscape.getIndexFilePath()
                .replaceAll("(/|\\\\)_sokrates_landscape(/|\\\\).*", "");
    }

    private void addBigSummary(LandscapeAnalysisResults landscapeAnalysisResults) {
        landscapeReport.startDiv("margin-top: 0px;");
        LandscapeConfiguration configuration = landscapeAnalysisResults.getConfiguration();
        int thresholdContributors = configuration.getProjectThresholdContributors();
        int size = getProjects().size();
        addInfoBlock(FormattingUtils.getSmallTextForNumber(size), (size == 1 ? "active project" : "active projects"),
                "", "active project with " + (thresholdContributors > 1 ? "(" + thresholdContributors + "+&nbsp;contributors)" : ""));
        addInfoBlock(FormattingUtils.getSmallTextForNumber(landscapeAnalysisResults.getMainLoc()), "lines of code (main)", "", getExtraLocInfo());
        int mainLocActive = landscapeAnalysisResults.getMainLocActive();
        addInfoBlock(FormattingUtils.getSmallTextForNumber(mainLocActive), "lines of code (active)", "", "files updated in past year");
        int mainLocNew = landscapeAnalysisResults.getMainLocNew();
        addInfoBlock(FormattingUtils.getSmallTextForNumber(mainLocNew), "lines of code (new)", "", "files created in past year");

        List<ContributorProjects> contributors = landscapeAnalysisResults.getContributors();
        long contributorsCount = contributors.size();
        if (contributorsCount > 0) {
            int recentContributorsCount = landscapeAnalysisResults.getRecentContributorsCount();
            int locPerRecentContributor = 0;
            if (recentContributorsCount > 0) {
                locPerRecentContributor = (int) Math.round((double) mainLocActive / recentContributorsCount);
            }
            addPeopleInfoBlock(FormattingUtils.getSmallTextForNumber(recentContributorsCount), "recent contributors",
                    "(past 30 days)", getExtraPeopleInfo(contributors, contributorsCount) + "\n" + FormattingUtils.formatCount(locPerRecentContributor) + " active lines of code per recent contributor");
            int rookiesContributorsCount = landscapeAnalysisResults.getRookiesContributorsCount();
            addPeopleInfoBlock(FormattingUtils.getSmallTextForNumber(rookiesContributorsCount),
                    rookiesContributorsCount == 1 ? "active rookie" : "active rookies",
                    "(started in past year)", "active contributors with the first commit in past year");
        }

        addContributorsPerYear(configuration.isShowContributorsTrendsOnFirstTab());

        landscapeReport.addLineBreak();

        landscapeReport.endDiv();
        landscapeReport.addLineBreak();
    }

    private void addBigProjectsSummary(LandscapeAnalysisResults landscapeAnalysisResults) {
        LandscapeConfiguration configuration = landscapeAnalysisResults.getConfiguration();
        int thresholdContributors = configuration.getProjectThresholdContributors();
        int size = getProjects().size();
        addInfoBlock(FormattingUtils.getSmallTextForNumber(size), (size == 1 ? "active project" : "active projects"),
                "", "active project with " + (thresholdContributors > 1 ? "(" + thresholdContributors + "+&nbsp;contributors)" : ""));
        addInfoBlock(FormattingUtils.getSmallTextForNumber(landscapeAnalysisResults.getMainLoc()), "lines of code (main)", "", getExtraLocInfo());
        int mainLocActive = landscapeAnalysisResults.getMainLocActive();
        addInfoBlock(FormattingUtils.getSmallTextForNumber(mainLocActive), "lines of code (active)", "", "files updated in past year");
        int mainLocNew = landscapeAnalysisResults.getMainLocNew();
        addInfoBlock(FormattingUtils.getSmallTextForNumber(mainLocNew), "lines of code (new)", "", "files created in past year");
    }

    private void addBigContributorsSummary(LandscapeAnalysisResults landscapeAnalysisResults) {
        List<ContributorProjects> contributors = landscapeAnalysisResults.getContributors();
        long contributorsCount = contributors.size();
        int mainLocActive = landscapeAnalysisResults.getMainLocActive();
        int mainLocNew = landscapeAnalysisResults.getMainLocNew();
        if (contributorsCount > 0) {
            int recentContributorsCount = landscapeAnalysisResults.getRecentContributorsCount();
            int locPerRecentContributor = 0;
            int locNewPerRecentContributor = 0;
            if (recentContributorsCount > 0) {
                locPerRecentContributor = (int) Math.round((double) mainLocActive / recentContributorsCount);
                locNewPerRecentContributor = (int) Math.round((double) mainLocNew / recentContributorsCount);
            }
            addPeopleInfoBlock(FormattingUtils.getSmallTextForNumber(recentContributorsCount), "recent contributors",
                    "(past 30 days)", getExtraPeopleInfo(contributors, contributorsCount) + "\n" + FormattingUtils.formatCount(locPerRecentContributor) + " active lines of code per recent contributor");
            int rookiesContributorsCount = landscapeAnalysisResults.getRookiesContributorsCount();
            addPeopleInfoBlock(FormattingUtils.getSmallTextForNumber(rookiesContributorsCount),
                    rookiesContributorsCount == 1 ? "active rookie" : "active rookies",
                    "(started in past year)", "active contributors with the first commit in past year");
            addPeopleInfoBlock(FormattingUtils.getSmallTextForNumber(locPerRecentContributor), "contributor load",
                    "(active LOC/contributor)", "active lines of code per recent contributor\n\n" + FormattingUtils.getPlainTextForNumber(locNewPerRecentContributor) + " new LOC/recent contributor");
            List<ComponentDependency> peopleDependencies = ContributorConnectionUtils.getPeopleDependencies(contributors, 0, 30);
            peopleDependencies.sort((a, b) -> b.getCount() - a.getCount());

            double cMedian = landscapeAnalysisResults.getcMedian30Days();

            addPeopleInfoBlock(FormattingUtils.getSmallTextForNumber((int) Math.round(cMedian)), "C-Median", "30 days", "");
        }

        addContributorsPerYear(true);
        System.out.println("Adding contributors per extension...");
        addContributorsPerExtension(true);

        landscapeReport.startSubSection("Commits & Contributors Per Month", "Past two years");
        addContributorsPerMonth();
        landscapeReport.endSection();

        landscapeReport.startSubSection("Commits & Contributors Per Week", "Past two years");
        addContributorsPerWeek();
        landscapeReport.endSection();

        addContributorsPerExtension();


        landscapeReport.addParagraph("latest commit date: <b>" + landscapeAnalysisResults.getLatestCommitDate() + "</b>", "color: grey");
    }

    private void addIFrames(List<WebFrameLink> iframes) {
        if (iframes.size() > 0) {
            iframes.forEach(iframe -> {
                addIFrame(iframe);
            });
        }
    }

    private void addIFrame(WebFrameLink iframe) {
        if (StringUtils.isNotBlank(iframe.getTitle())) {
            String title;
            if (StringUtils.isNotBlank(iframe.getMoreInfoLink())) {
                title = "<a href='" + iframe.getMoreInfoLink() + "' target='_blank' style='text-decoration: none'>" + iframe.getTitle() + "</a>";
                title += "&nbsp;&nbsp;" + OPEN_IN_NEW_TAB_SVG_ICON;
            } else {
                title = iframe.getTitle();
            }
            landscapeReport.startSubSection(title, "");
        }
        String style = StringUtils.defaultIfBlank(iframe.getStyle(), "width: 100%; height: 200px; border: 1px solid lightgrey;");
        landscapeReport.addHtmlContent("<iframe src='" + iframe.getSrc()
                + "' frameborder='0' style='" + style + "'"
                + (iframe.getScrolling() ? "" : " scrolling='no' ")
                + "></iframe>");
        if (StringUtils.isNotBlank(iframe.getTitle())) {
            landscapeReport.endSection();
        }
    }

    private void addExtensions() {
        addMainExtensions("Main", LandscapeGeneratorUtils.getLinesOfCodePerExtension(landscapeAnalysisResults, landscapeAnalysisResults.getMainLinesOfCodePerExtension()), true);
        landscapeReport.startShowMoreBlockDisappear("", "Show test and other code...");
        addMainExtensions("Test", LandscapeGeneratorUtils.getLinesOfCodePerExtension(landscapeAnalysisResults, landscapeAnalysisResults.getTestLinesOfCodePerExtension()), false);
        addMainExtensions("Other", LandscapeGeneratorUtils.getLinesOfCodePerExtension(landscapeAnalysisResults, landscapeAnalysisResults.getOtherLinesOfCodePerExtension()), false);
        landscapeReport.endShowMoreBlock();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
    }

    private void addMainExtensions(String type, List<NumericMetric> linesOfCodePerExtension, boolean linkCharts) {
        int threshold = landscapeAnalysisResults.getConfiguration().getExtensionThresholdLoc();
        landscapeReport.startSubSection("File Extensions in " + type + " Code (" + linesOfCodePerExtension.size() + ")",
                threshold >= 1 ? threshold + "+ lines of code" : "");
        if (linkCharts) {
            landscapeReport.startDiv("");
            landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_extensions.html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("tree map", "visuals/tree_map_extensions.html");
            landscapeReport.addLineBreak();
            landscapeReport.addLineBreak();
            landscapeReport.endDiv();
        }
        landscapeReport.startDiv("");
        boolean tooLong = linesOfCodePerExtension.size() > 25;
        List<NumericMetric> linesOfCodePerExtensionDisplay = tooLong ? linesOfCodePerExtension.subList(0, 25) : linesOfCodePerExtension;
        List<NumericMetric> linesOfCodePerExtensionHide = tooLong ? linesOfCodePerExtension.subList(25, linesOfCodePerExtension.size()) : new ArrayList<>();
        linesOfCodePerExtensionDisplay.forEach(extension -> {
            addLangInfo(extension);
        });
        if (linesOfCodePerExtensionHide.size() > 0) {
            landscapeReport.startShowMoreBlockDisappear("", "show all...");
            linesOfCodePerExtensionHide.forEach(extension -> {
                addLangInfo(extension);
            });
            landscapeReport.endShowMoreBlock();
        }
        landscapeReport.endDiv();
        landscapeReport.endSection();
    }


    private void addContributorsPerExtension(boolean linkCharts) {
        landscapeReport.startSubSection("Contributors per File Extensions (past 30 days)", "");
        if (linkCharts) {
            landscapeReport.startDiv("");
            landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_extensions_contributors_30d.html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("tree map", "visuals/tree_map_extensions_contributors_30d.html");
            landscapeReport.addLineBreak();
            landscapeReport.addLineBreak();
            landscapeReport.endDiv();
        }
        landscapeReport.startDiv("");
        List<String> mainExtensions = getMainExtensions();
        List<CommitsPerExtension> contributorsPerExtension = landscapeAnalysisResults.getContributorsPerExtension()
                .stream().filter(c -> mainExtensions.contains(c.getExtension())).collect(Collectors.toList());
        Collections.sort(contributorsPerExtension, (a, b) -> b.getCommitters30Days().size() - a.getCommitters30Days().size());
        boolean tooLong = contributorsPerExtension.size() > 25;
        List<CommitsPerExtension> contributorsPerExtensionDisplay = tooLong ? contributorsPerExtension.subList(0, 25) : contributorsPerExtension;
        List<CommitsPerExtension> linesOfCodePerExtensionHide = tooLong ? contributorsPerExtension.subList(25, contributorsPerExtension.size()) : new ArrayList<>();
        contributorsPerExtensionDisplay.stream().filter(e -> e.getCommitters30Days().size() > 0).forEach(extension -> {
            addLangInfo(extension, (e) -> e.getCommitters30Days(), extension.getCommitsCount30Days(), DEVELOPER_SVG_ICON);
        });
        if (linesOfCodePerExtensionHide.stream().filter(e -> e.getCommitters30Days().size() > 0).count() > 0) {
            landscapeReport.startShowMoreBlockDisappear("", "show all...");
            linesOfCodePerExtensionHide.stream().filter(e -> e.getCommitters30Days().size() > 0).forEach(extension -> {
                addLangInfo(extension, (e) -> e.getCommitters30Days(), extension.getCommitsCount30Days(), DEVELOPER_SVG_ICON);
            });
            landscapeReport.endShowMoreBlock();
        }
        landscapeReport.endDiv();
        addContributorDependencies(contributorsPerExtension);
        landscapeReport.endSection();
    }

    private void addContributorDependencies(List<CommitsPerExtension> contributorsPerExtension) {
        Map<String, List<String>> contrExtMap = new HashMap<>();
        Set<String> extensionsNames = new HashSet<>();
        contributorsPerExtension.stream().filter(e -> e.getCommitters30Days().size() > 0).forEach(commitsPerExtension -> {
            String extensionDisplayLabel = commitsPerExtension.getExtension() + " (" + commitsPerExtension.getCommitters30Days().size() + ")";
            extensionsNames.add(extensionDisplayLabel);
            commitsPerExtension.getCommitters30Days().forEach(contributor -> {
                if (contrExtMap.containsKey(contributor)) {
                    contrExtMap.get(contributor).add(extensionDisplayLabel);
                } else {
                    contrExtMap.put(contributor, new ArrayList<>(Arrays.asList(extensionDisplayLabel)));
                }
            });
        });
        List<ComponentDependency> dependencies = new ArrayList<>();
        Map<String, ComponentDependency> dependencyMap = new HashMap<>();

        List<String> mainExtensions = getMainExtensions();
        contrExtMap.values().stream().filter(v -> v.size() > 1).forEach(extensions -> {
            extensions.stream().filter(extension1 -> mainExtensions.contains(extension1.replaceAll("\\(.*\\)", "").trim())).forEach(extension1 -> {
                extensions.stream().filter(extension2 -> mainExtensions.contains(extension2.replaceAll("\\(.*\\)", "").trim())).filter(extension2 -> !extension1.equalsIgnoreCase(extension2)).forEach(extension2 -> {
                    String key1 = extension1 + "::" + extension2;
                    String key2 = extension2 + "::" + extension1;

                    if (dependencyMap.containsKey(key1)) {
                        dependencyMap.get(key1).increment(1);
                    } else if (dependencyMap.containsKey(key2)) {
                        dependencyMap.get(key2).increment(1);
                    } else {
                        ComponentDependency dependency = new ComponentDependency(extension1, extension2);
                        dependencyMap.put(key1, dependency);
                        dependencies.add(dependency);
                    }
                });
            });
        });

        dependencies.forEach(dependency -> dependency.setCount(dependency.getCount() / 2));

        GraphvizDependencyRenderer renderer = new GraphvizDependencyRenderer();
        renderer.setMaxNumberOfDependencies(100);
        renderer.setTypeGraph();
        String graphvizContent = renderer.getGraphvizContent(new ArrayList<>(), dependencies);

        landscapeReport.startShowMoreBlock("show extension dependencies...");
        landscapeReport.addGraphvizFigure("extension_dependencies_30d", "Extension dependencies", graphvizContent);
        addDownloadLinks("extension_dependencies_30d");
        landscapeReport.endShowMoreBlock();
        landscapeReport.addLineBreak();
        landscapeReport.addNewTabLink(" - show extension dependencies as 3D force graph...", "visuals/extension_dependencies_30d_force_3d.html");
        export3DForceGraph(dependencies, "extension_dependencies_30d");
    }

    private List<String> getMainExtensions() {
        return landscapeAnalysisResults.getMainLinesOfCodePerExtension().stream().map(l -> l.getName().replace("*.", "").trim()).collect(Collectors.toList());
    }

    private void addLangInfo(CommitsPerExtension extension, ExtractStringListValue<CommitsPerExtension> extractor, int commitsCount, String suffix) {
        int size = extractor.getValue(extension).size();
        String smallTextForNumber = FormattingUtils.getSmallTextForNumber(size) + suffix;
        addLangInfoBlockExtra(smallTextForNumber, extension.getExtension().replace("*.", "").trim(),
                size + " " + (size == 1 ? "contributor" : "contributors (" + commitsCount + " commits)") + ":\n" +
                        extractor.getValue(extension).stream().limit(100)
                                .collect(Collectors.joining(", ")), FormattingUtils.getSmallTextForNumber(commitsCount) + " commits");
    }

    private void addLangInfo(NumericMetric extension) {
        String smallTextForNumber = FormattingUtils.getSmallTextForNumber(extension.getValue().intValue());
        int size = extension.getDescription().size();
        Collections.sort(extension.getDescription(), (a, b) -> b.getValue().intValue() - a.getValue().intValue());
        addLangInfoBlock(smallTextForNumber, extension.getName().replace("*.", "").trim(),
                size + " " + (size == 1 ? "project" : "projects") + ":\n  " +
                        extension.getDescription().stream()
                                .map(a -> a.getName() + " (" + FormattingUtils.formatCount(a.getValue().intValue()) + " LOC)")
                                .collect(Collectors.joining("\n  ")));
    }


    private void addContributors() {
        int contributorsCount = landscapeAnalysisResults.getContributorsCount();

        if (contributorsCount > 0) {
            List<ContributorProjects> contributors = landscapeAnalysisResults.getContributors();
            List<ContributorProjects> recentContributors = landscapeAnalysisResults.getContributors().stream()
                    .filter(c -> c.getContributor().getCommitsCount30Days() > 0)
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.sort(recentContributors, (a, b) -> b.getContributor().getCommitsCount30Days() - a.getContributor().getCommitsCount30Days());
            int totalCommits = contributors.stream().mapToInt(c -> c.getContributor().getCommitsCount()).sum();
            int totalRecentCommits = recentContributors.stream().mapToInt(c -> c.getContributor().getCommitsCount30Days()).sum();
            final String[] latestCommit = {""};
            contributors.forEach(c -> {
                if (c.getContributor().getLatestCommitDate().compareTo(latestCommit[0]) > 0) {
                    latestCommit[0] = c.getContributor().getLatestCommitDate();
                }
            });

            landscapeReport.startSubSection("<a href='contributors-recent.html' target='_blank' style='text-decoration: none'>" +
                            "Recent Contributors (" + recentContributors.size() + ")</a>&nbsp;&nbsp;" + OPEN_IN_NEW_TAB_SVG_ICON,
                    "latest commit " + latestCommit[0]);
            addRecentContributorLinks();

            landscapeReport.addHtmlContent("<iframe src='contributors-recent.html' frameborder=0 style='height: 450px; width: 100%; margin-bottom: 0px; padding: 0;'></iframe>");

            landscapeReport.endSection();

            landscapeReport.startSubSection("<a href='contributors.html' target='_blank' style='text-decoration: none'>" +
                            "All Contributors (" + contributorsCount + ")</a>&nbsp;&nbsp;" + OPEN_IN_NEW_TAB_SVG_ICON,
                    "latest commit " + latestCommit[0]);

            landscapeReport.startShowMoreBlock("show details...");
            addContributorLinks();

            landscapeReport.addHtmlContent("<iframe src='contributors.html' frameborder=0 style='height: 450px; width: 100%; margin-bottom: 0px; padding: 0;'></iframe>");

            landscapeReport.endShowMoreBlock();
            landscapeReport.endSection();

            new LandscapeContributorsReport(landscapeAnalysisResults, landscapeRecentContributorsReport).saveContributorsTable(recentContributors, totalRecentCommits, true);
            new LandscapeContributorsReport(landscapeAnalysisResults, landscapeContributorsReport).saveContributorsTable(contributors, totalCommits, false);

            individualContributorReports = new LandscapeIndividualContributorsReports(landscapeAnalysisResults).getIndividualReports(contributors);
        }
    }

    private void addContributorsPerExtension() {
        int commitsCount = landscapeAnalysisResults.getCommitsCount();
        if (commitsCount > 0) {
            List<CommitsPerExtension> perExtension = landscapeAnalysisResults.getContributorsPerExtension();

            if (perExtension.size() > 0) {
                int count = perExtension.size();
                int limit = 100;
                if (perExtension.size() > limit) {
                    perExtension = perExtension.subList(0, limit);
                }
                landscapeReport.startSubSection("Commits & File Extensions (" + count + ")", "");

                landscapeReport.startShowMoreBlock("show details...");

                landscapeReport.startTable("");
                landscapeReport.addTableHeader("", "Extension",
                        "# contributors<br>30 days", "# commits<br>30 days", "# files<br>30 days",
                        "# contributors<br>90 days", "# commits<br>90 days", "# files<br>90 days",
                        "# contributors", "# commits", "# files");

                perExtension.forEach(commitsPerExtension -> {
                    addCommitExtension(commitsPerExtension);
                });
                landscapeReport.endTable();
                if (perExtension.size() < count) {
                    landscapeReport.addParagraph("Showing top " + limit + " items (out of " + count + ").");
                }

                landscapeReport.endShowMoreBlock();

                landscapeReport.endSection();
            }
        }
    }

    private void addContributorLinks() {
        landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_contributors.html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("tree map", "visuals/tree_map_contributors.html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("data", "data/contributors.txt");
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
    }

    private void addRecentContributorLinks() {
        landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_contributors_30_days.html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("tree map", "visuals/tree_map_contributors_30_days.html");
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
    }

    private void addCommitExtension(CommitsPerExtension commitsPerExtension) {
        landscapeReport.startTableRow(commitsPerExtension.getCommitters30Days().size() > 0 ? "font-weight: bold;"
                : "color: " + (commitsPerExtension.getCommitters90Days().size() > 0 ? "grey" : "lightgrey"));
        String extension = commitsPerExtension.getExtension();
        landscapeReport.addTableCell("" + DataImageUtils.getLangDataImageDiv42(extension), "text-align: center;");
        landscapeReport.addTableCell("" + extension, "text-align: center; max-width: 100px; width: 100px");
        landscapeReport.addTableCell("" + commitsPerExtension.getCommitters30Days().size(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getCommitsCount30Days(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getFilesCount30Days(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getCommitters90Days().size(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getFilesCount90Days(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getCommitsCount90Days(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getCommitters().size(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getCommitsCount(), "text-align: center;");
        landscapeReport.addTableCell("" + commitsPerExtension.getFilesCount(), "text-align: center;");
        landscapeReport.endTableCell();
        landscapeReport.endTableRow();
    }

    private void addProjectsSection(LandscapeConfiguration configuration, List<ProjectAnalysisResults> projectsAnalysisResults) {
        Collections.sort(projectsAnalysisResults, (a, b) -> b.getAnalysisResults().getMainAspectAnalysisResults().getLinesOfCode() - a.getAnalysisResults().getMainAspectAnalysisResults().getLinesOfCode());
        exportZoomableCircles(CONTRIBUTORS_30_D, projectsAnalysisResults, new ZommableCircleCountExtractors() {
            @Override
            public int getCount(ProjectAnalysisResults projectAnalysisResults) {
                List<ContributionTimeSlot> contributorsPerMonth = projectAnalysisResults.getAnalysisResults().getContributorsAnalysisResults().getContributorsPerMonth();
                if (contributorsPerMonth.size() > 0) {
                    return contributorsPerMonth.get(0).getContributorsCount();
                }
                return 0;
            }
        });
        exportZoomableCircles(COMMITS_30_D, projectsAnalysisResults, new ZommableCircleCountExtractors() {
            @Override
            public int getCount(ProjectAnalysisResults projectAnalysisResults) {
                return projectAnalysisResults.getAnalysisResults().getContributorsAnalysisResults().getCommitsCount30Days();
            }
        });
        exportZoomableCircles(MAIN_LOC, projectsAnalysisResults, new ZommableCircleCountExtractors() {
            @Override
            public int getCount(ProjectAnalysisResults projectAnalysisResults) {
                return projectAnalysisResults.getAnalysisResults().getMainAspectAnalysisResults().getLinesOfCode();
            }
        });
        System.out.println("Adding sub landscape section...");
        addSubLandscapeSection(configuration.getSubLandscapes());
        landscapeReport.startSubSection("<a href='projects.html' target='_blank' style='text-decoration: none'>" +
                "All Projects (" + projectsAnalysisResults.size() + ")</a>&nbsp;&nbsp;" + OPEN_IN_NEW_TAB_SVG_ICON, "");
        if (projectsAnalysisResults.size() > 0) {
            List<NumericMetric> projectSizes = new ArrayList<>();
            projectsAnalysisResults.forEach(project -> {
                System.out.println("Adding " + project.getSokratesProjectLink().getAnalysisResultsPath());
                CodeAnalysisResults analysisResults = project.getAnalysisResults();
                projectSizes.add(new NumericMetric(analysisResults.getMetadata().getName(), analysisResults.getMainAspectAnalysisResults().getLinesOfCode()));
            });
            landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_projects.html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("tree map", "visuals/tree_map_projects.html");
            landscapeReport.addHtmlContent(" | ");
            landscapeReport.addNewTabLink("data", "data/projects.txt");
            landscapeReport.addLineBreak();

            landscapeReport.addHtmlContent("<iframe src='projects.html' frameborder=0 style='height: 600px; width: 100%; margin-bottom: 0px; padding: 0;'></iframe>");

            new LandscapeProjectsReport(landscapeAnalysisResults).saveProjectsReport(landscapeProjectsReport, projectsAnalysisResults);
        }

        landscapeReport.endSection();
    }

    private void addInfoBlock(String mainValue, String subtitle, String description, String tooltip) {
        if (StringUtils.isNotBlank(description)) {
            subtitle += "<br/><span style='color: grey; font-size: 80%'>" + description + "</span>";
        }
        addInfoBlockWithColor(mainValue, subtitle, "skyblue", tooltip);
    }

    private String getExtraLocInfo() {
        String info = "";

        info += FormattingUtils.getPlainTextForNumber(landscapeAnalysisResults.getMainLoc()) + " LOC (main)\n";
        info += FormattingUtils.getPlainTextForNumber(landscapeAnalysisResults.getTestLoc()) + " LOC (test)\n";
        info += FormattingUtils.getPlainTextForNumber(landscapeAnalysisResults.getGeneratedLoc()) + " LOC (generated)\n";
        info += FormattingUtils.getPlainTextForNumber(landscapeAnalysisResults.getBuildAndDeploymentLoc()) + " LOC (build and deployment)\n";
        info += FormattingUtils.getPlainTextForNumber(landscapeAnalysisResults.getOtherLoc()) + " LOC (other)";

        return info;
    }

    private String getExtraPeopleInfo(List<ContributorProjects> contributors, long contributorsCount) {
        String info = "";

        int recentContributorsCount6Months = landscapeAnalysisResults.getRecentContributorsCount6Months();
        int recentContributorsCount3Months = landscapeAnalysisResults.getRecentContributorsCount3Months();
        info += FormattingUtils.getPlainTextForNumber(landscapeAnalysisResults.getRecentContributorsCount()) + " contributors (30 days)\n";
        info += FormattingUtils.getPlainTextForNumber(recentContributorsCount3Months) + " contributors (3 months)\n";
        info += FormattingUtils.getPlainTextForNumber(recentContributorsCount6Months) + " contributors (6 months)\n";

        LandscapeConfiguration configuration = landscapeAnalysisResults.getConfiguration();
        int thresholdCommits = configuration.getContributorThresholdCommits();
        info += FormattingUtils.getPlainTextForNumber((int) contributorsCount) + " contributors (all time)\n";
        info += "\nOnly the contributors with " + (thresholdCommits > 1 ? "(" + thresholdCommits + "+&nbsp;commits)" : "") + " included";

        return info;
    }

    private void addPeopleInfoBlock(String mainValue, String subtitle, String description, String tooltip) {
        if (StringUtils.isNotBlank(description)) {
            subtitle += "<br/><span style='color: grey; font-size: 80%'>" + description + "</span>";
        }
        addInfoBlockWithColor(mainValue, subtitle, "lavender", tooltip);
    }

    private void addCommitsInfoBlock(String mainValue, String subtitle, String description, String tooltip) {
        if (StringUtils.isNotBlank(description)) {
            subtitle += "<br/><span style='color: grey; font-size: 80%'>" + description + "</span>";
        }
        addInfoBlockWithColor(mainValue, subtitle, "#fefefe", tooltip);
    }

    private void addInfoBlockWithColor(String mainValue, String subtitle, String color, String tooltip) {
        String style = "border-radius: 12px;";

        style += "margin: 12px 12px 12px 0px;";
        style += "display: inline-block; width: 160px; height: 120px;";
        style += "background-color: " + color + "; text-align: center; vertical-align: middle; margin-bottom: 36px;";

        landscapeReport.startDiv(style, tooltip);
        landscapeReport.addHtmlContent("<div style='font-size: 50px; margin-top: 20px'>" + mainValue + "</div>");
        landscapeReport.addHtmlContent("<div style='color: #434343; font-size: 16px'>" + subtitle + "</div>");
        landscapeReport.endDiv();
    }

    private void addSmallInfoBlockLoc(String value, String subtitle, String link) {
        addSmallInfoBlock(value, subtitle, "skyblue", link);
    }

    private void addSmallInfoBlockPeople(String value, String subtitle, String link) {
        addSmallInfoBlock(value, subtitle, "lavender", link);
    }

    private void addLangInfoBlock(String value, String lang, String description) {
        String style = "border-radius: 8px; margin: 4px 4px 4px 0px; display: inline-block; " +
                "width: 80px; height: 114px;background-color: #dedede; " +
                "text-align: center; vertical-align: middle; margin-bottom: 16px;";

        landscapeReport.startDivWithLabel(description, style);

        landscapeReport.addContentInDiv("", "margin-top: 8px");
        landscapeReport.addHtmlContent(DataImageUtils.getLangDataImageDiv42(lang));
        landscapeReport.addHtmlContent("<div style='font-size: 24px; margin-top: 8px;'>" + value + "</div>");
        landscapeReport.addHtmlContent("<div style='color: #434343; font-size: 13px'>" + lang + "</div>");
        landscapeReport.endDiv();
    }

    private void addLangInfoBlockExtra(String value, String lang, String description, String extra) {
        String style = "border-radius: 8px; margin: 4px 4px 4px 0px; display: inline-block; " +
                "width: 80px; height: 114px;background-color: #dedede; " +
                "text-align: center; vertical-align: middle; margin-bottom: 16px;";

        landscapeReport.startDivWithLabel(description, style);

        landscapeReport.addContentInDiv("", "margin-top: 8px");
        landscapeReport.addHtmlContent(DataImageUtils.getLangDataImageDiv42(lang));
        landscapeReport.addHtmlContent("<div style='font-size: 24px; margin-top: 8px;'>" + value + "</div>");
        landscapeReport.addHtmlContent("<div style='color: #434343; font-size: 13px'>" + lang + "</div>");
        landscapeReport.addHtmlContent("<div style='color: #767676; font-size: 9px; margin-top: 1px;'>" + extra + "</div>");
        landscapeReport.endDiv();
    }

    private void addSmallInfoBlock(String value, String subtitle, String color, String link) {
        String style = "border-radius: 8px;";

        style += "margin: 4px 4px 4px 0px;";
        style += "display: inline-block; width: 80px; height: 76px;";
        style += "background-color: " + color + "; text-align: center; vertical-align: middle; margin-bottom: 16px;";

        landscapeReport.startDiv(style);
        if (StringUtils.isNotBlank(link)) {
            landscapeReport.startNewTabLink(link, "text-decoration: none");
        }
        landscapeReport.addHtmlContent("<div style='font-size: 24px; margin-top: 8px;'>" + value + "</div>");
        landscapeReport.addHtmlContent("<div style='color: #434343; font-size: 13px'>" + subtitle + "</div>");
        if (StringUtils.isNotBlank(link)) {
            landscapeReport.endNewTabLink();
        }
        landscapeReport.endDiv();
    }

    public List<RichTextReport> report() {
        List<RichTextReport> reports = new ArrayList<>();

        reports.add(this.landscapeReport);
        reports.add(this.landscapeProjectsReport);
        reports.add(this.landscapeContributorsReport);
        reports.add(this.landscapeRecentContributorsReport);

        return reports;
    }

    private void addContributorsPerYear(boolean showContributorsCount) {
        List<ContributionTimeSlot> contributorsPerYear = landscapeAnalysisResults.getContributorsPerYear();
        if (contributorsPerYear.size() > 0) {
            int limit = landscapeAnalysisResults.getConfiguration().getCommitsMaxYears();
            if (contributorsPerYear.size() > limit) {
                contributorsPerYear = contributorsPerYear.subList(0, limit);
            }

            int maxCommits = contributorsPerYear.stream().mapToInt(c -> c.getCommitsCount()).max().orElse(1);

            landscapeReport.startDiv("overflow-y: none;");
            landscapeReport.startTable();

            landscapeReport.startTableRow();
            landscapeReport.startTableCell("border: none; height: 100px");
            int commitsCount = landscapeAnalysisResults.getCommitsCount();
            if (commitsCount > 0) {
                landscapeReport.startDiv("max-height: 105px");
                addSmallInfoBlock(FormattingUtils.getSmallTextForNumber(commitsCount), "commits", "white", "");
                landscapeReport.endDiv();
            }
            landscapeReport.endTableCell();
            String style = "border: none; text-align: center; vertical-align: bottom; font-size: 80%; height: 100px";
            int thisYear = Calendar.getInstance().get(Calendar.YEAR);
            contributorsPerYear.forEach(year -> {
                landscapeReport.startTableCell(style);
                int count = year.getCommitsCount();
                String color = year.getTimeSlot().equals(thisYear + "") ? "#343434" : "#989898";
                landscapeReport.addParagraph(count + "", "margin: 2px; color: " + color);
                int height = 1 + (int) (64.0 * count / maxCommits);
                String bgColor = year.getTimeSlot().equals(thisYear + "") ? "#343434" : "lightgrey";
                landscapeReport.addHtmlContent("<div style='width: 100%; background-color: " + bgColor + "; height:" + height + "px'></div>");
                landscapeReport.endTableCell();
            });
            landscapeReport.endTableRow();

            if (showContributorsCount) {
                int maxContributors[] = {1};
                contributorsPerYear.forEach(year -> {
                    int count = getContributorsCountPerYear(year.getTimeSlot());
                    maxContributors[0] = Math.max(maxContributors[0], count);
                });
                landscapeReport.startTableRow();
                landscapeReport.startTableCell("border: none; height: 100px");
                int contributorsCount = landscapeAnalysisResults.getContributors().size();
                if (contributorsCount > 0) {
                    landscapeReport.startDiv("max-height: 105px");
                    addSmallInfoBlock(FormattingUtils.getSmallTextForNumber(contributorsCount), "contributors", "white", "");
                    landscapeReport.endDiv();
                }
                landscapeReport.endTableCell();
                contributorsPerYear.forEach(year -> {
                    landscapeReport.startTableCell(style);
                    int count = getContributorsCountPerYear(year.getTimeSlot());
                    String color = year.getTimeSlot().equals(thisYear + "") ? "#343434" : "#989898";
                    landscapeReport.addParagraph(count + "", "margin: 2px; color: " + color + ";");
                    int height = 1 + (int) (64.0 * count / maxContributors[0]);
                    landscapeReport.addHtmlContent("<div style='width: 100%; background-color: skyblue; height:" + height + "px'></div>");
                    landscapeReport.endTableCell();
                });
                landscapeReport.endTableRow();
            }

            landscapeReport.startTableRow();
            landscapeReport.addTableCell("", "border: none; ");
            var ref = new Object() {
                String latestCommitDate = landscapeAnalysisResults.getLatestCommitDate();
            };
            if (ref.latestCommitDate.length() > 5) {
                ref.latestCommitDate = ref.latestCommitDate.substring(5);
            }
            contributorsPerYear.forEach(year -> {
                String color = year.getTimeSlot().equals(thisYear + "") ? "#343434" : "#989898";
                landscapeReport.startTableCell("vertical-align: top; border: none; text-align: center; font-size: 90%; color: " + color);
                landscapeReport.addHtmlContent(year.getTimeSlot());
                if (landscapeAnalysisResults.getLatestCommitDate().startsWith(year.getTimeSlot() + "-")) {
                    landscapeReport.addContentInDiv(ref.latestCommitDate, "text-align: center; color: grey; font-size: 9px");
                }
                landscapeReport.endTableCell();
            });
            landscapeReport.endTableRow();

            landscapeReport.endTable();
            landscapeReport.endDiv();

            landscapeReport.addLineBreak();
        }
    }

    private void addContributorsPerWeek() {
        int limit = 104;
        List<ContributionTimeSlot> contributorsPerWeek = getContributionWeeks(landscapeAnalysisResults.getContributorsPerWeek(),
                limit, landscapeAnalysisResults.getLatestCommitDate());

        contributorsPerWeek.sort(Comparator.comparing(ContributionTimeSlot::getTimeSlot).reversed());

        if (contributorsPerWeek.size() > 0) {
            if (contributorsPerWeek.size() > limit) {
                contributorsPerWeek = contributorsPerWeek.subList(0, limit);
            }

            landscapeReport.startDiv("overflow: hidden");
            landscapeReport.startTable();

            int minMaxWindow = contributorsPerWeek.size() >= 4 ? 4 : contributorsPerWeek.size();

            addChartRows(contributorsPerWeek, "weeks", minMaxWindow,
                    (timeSlot, rookiesOnly) -> getContributorsPerWeek(timeSlot, rookiesOnly),
                    (timeSlot, rookiesOnly) -> getLastContributorsPerWeek(timeSlot, true),
                    (timeSlot, rookiesOnly) -> getLastContributorsPerWeek(timeSlot, false), 14);

            landscapeReport.endTable();
            landscapeReport.endDiv();

            landscapeReport.addLineBreak();
        }
    }

    private void addContributorsPerMonth() {
        int limit = 24;
        List<ContributionTimeSlot> contributorsPerMonth = getContributionMonths(landscapeAnalysisResults.getContributorsPerMonth(),
                limit, landscapeAnalysisResults.getLatestCommitDate());

        contributorsPerMonth.sort(Comparator.comparing(ContributionTimeSlot::getTimeSlot).reversed());

        if (contributorsPerMonth.size() > 0) {
            if (contributorsPerMonth.size() > limit) {
                contributorsPerMonth = contributorsPerMonth.subList(0, limit);
            }

            landscapeReport.startDiv("overflow: hidden");
            landscapeReport.startTable();

            int minMaxWindow = contributorsPerMonth.size() >= 3 ? 3 : contributorsPerMonth.size();

            addChartRows(contributorsPerMonth, "months", minMaxWindow, (timeSlot, rookiesOnly) -> getContributorsPerMonth(timeSlot, rookiesOnly),
                    (timeSlot, rookiesOnly) -> getLastContributorsPerMonth(timeSlot, true),
                    (timeSlot, rookiesOnly) -> getLastContributorsPerMonth(timeSlot, false), 40);

            landscapeReport.endTable();
            landscapeReport.endDiv();

            landscapeReport.addLineBreak();
        }
    }

    private void addChartRows(List<ContributionTimeSlot> contributorsPerWeek, String unit, int minMaxWindow, ContributorsExtractor contributorsExtractor, ContributorsExtractor firstContributorsExtractor, ContributorsExtractor lastContributorsExtractor, int barWidth) {
        addTickMarksPerWeekRow(contributorsPerWeek, barWidth);
        addCommitsPerWeekRow(contributorsPerWeek, minMaxWindow, barWidth);
        addContributersPerWeekRow(contributorsPerWeek, unit, minMaxWindow, contributorsExtractor);
        int maxContributors = contributorsPerWeek.stream().mapToInt(c -> contributorsExtractor.getContributors(c.getTimeSlot(), false).size()).max().orElse(1);
        addContributorsPerTimeUnitRow(contributorsPerWeek, unit, minMaxWindow, firstContributorsExtractor, maxContributors, true, "bottom");
        addContributorsPerTimeUnitRow(contributorsPerWeek, unit, minMaxWindow, lastContributorsExtractor, maxContributors, false, "top");
    }

    private void addContributersPerWeekRow(List<ContributionTimeSlot> contributorsPerWeek, String unit, int minMaxWindow, ContributorsExtractor contributorsExtractor) {
        landscapeReport.startTableRow();
        int max = 1;
        for (ContributionTimeSlot contributionTimeSlot : contributorsPerWeek) {
            max = Math.max(contributorsExtractor.getContributors(contributionTimeSlot.getTimeSlot(), false).size(), max);
        }
        int maxContributors = max;
        int maxContributors4Weeks = contributorsPerWeek.subList(0, minMaxWindow).stream().mapToInt(c -> contributorsExtractor.getContributors(c.getTimeSlot(), false).size()).max().orElse(0);
        int minContributors4Weeks = contributorsPerWeek.subList(0, minMaxWindow).stream().mapToInt(c -> contributorsExtractor.getContributors(c.getTimeSlot(), false).size()).min().orElse(0);
        landscapeReport.addTableCell("<b>Contributors</b>" +
                "<div style='font-size: 80%; margin-left: 8px'><div style='color: green'>rookies</div> vs.<div style='color: #588BAE'>veterans</div></div>", "border: none");
        contributorsPerWeek.forEach(week -> {
            landscapeReport.startTableCell("max-width: 20px; padding: 0; margin: 1px; border: none; text-align: center; vertical-align: bottom; font-size: 80%; height: 100px");
            List<String> contributors = contributorsExtractor.getContributors(week.getTimeSlot(), false);
            List<String> rookies = contributorsExtractor.getContributors(week.getTimeSlot(), true);
            int count = contributors.size();
            int rookiesCount = rookies.size();
            int height = 2 + (int) (64.0 * count / maxContributors);
            int heightRookies = 1 + (int) (64.0 * rookiesCount / maxContributors);
            String title = "week of " + week.getTimeSlot() + " = " + count + " contributors (" + rookiesCount + " rookies):\n\n" +
                    contributors.subList(0, contributors.size() < 200 ? contributors.size() : 200).stream().collect(Collectors.joining(", "));
            String yearString = week.getTimeSlot().split("[-]")[0];

            String color = "darkgrey";

            if (StringUtils.isNumeric(yearString)) {
                int year = Integer.parseInt(yearString);
                color = year % 2 == 0 ? "#89CFF0" : "#588BAE";
            }

            landscapeReport.addHtmlContent("<div>");
            landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; color: grey; font-size: 80%; margin: 1px'>" + count + "</div>");
            landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; background-color: green; height:" + (heightRookies) + "px; margin: 1px'></div>");
            landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; background-color: " + color + "; height:" + (height - heightRookies) + "px; margin: 1px'></div>");
            landscapeReport.addHtmlContent("</div>");
            landscapeReport.endTableCell();
        });
        landscapeReport.endTableRow();
    }

    private void addContributorsPerTimeUnitRow(List<ContributionTimeSlot> contributorsPerWeek, String unit, int minMaxWindow, ContributorsExtractor contributorsExtractor, int maxContributors, boolean first, final String valign) {
        landscapeReport.startTableRow();
        int maxRookies4Weeks = contributorsPerWeek.subList(0, minMaxWindow).stream().mapToInt(c -> contributorsExtractor.getContributors(c.getTimeSlot(), true).size()).max().orElse(0);
        int minRookies4Weeks = contributorsPerWeek.subList(0, minMaxWindow).stream().mapToInt(c -> contributorsExtractor.getContributors(c.getTimeSlot(), true).size()).min().orElse(0);
        landscapeReport.addTableCell("<b>" + (first ? "First" : "Last") + " Contribution</b>" +
                "<div style='color: grey; font-size: 80%; margin-left: 8px; margin-top: 4px;'>"
                + "</div>", "border: none; vertical-align: " + (first ? "bottom" : "top"));
        contributorsPerWeek.forEach(week -> {
            landscapeReport.startTableCell("max-width: 20px; padding: 0; margin: 1px; border: none; text-align: center; vertical-align: " + valign + "; font-size: 80%; height: 100px");
            List<String> contributors = contributorsExtractor.getContributors(week.getTimeSlot(), true);
            int count = contributors.size();
            int height = 4 + (int) (64.0 * count / maxContributors);
            String title = "week of " + week.getTimeSlot() + " = " + count + " contributors:\n\n" +
                    contributors.subList(0, contributors.size() < 200 ? contributors.size() : 200).stream().collect(Collectors.joining(", "));
            String yearString = week.getTimeSlot().split("[-]")[0];

            String color = "lightgrey";

            if (count > 0 && StringUtils.isNumeric(yearString)) {
                int year = Integer.parseInt(yearString);
                if (first) {
                    color = year % 2 == 0 ? "limegreen" : "darkgreen";
                } else {
                    color = year % 2 == 0 ? "crimson" : "rgba(100,0,0,100)";
                }
            } else {
                height = 1;
            }

            if (first && count > 0) {
                landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; color: grey; font-size: 80%; margin: 1px'>" + count + "</div>");
            }
            landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; background-color: " + color + "; height:" + height + "px; margin: 1px'></div>");
            if (!first && count > 0) {
                landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; color: grey; font-size: 80%; margin: 1px'>" + count + "</div>");
            }
            landscapeReport.endTableCell();
        });
        landscapeReport.endTableRow();
    }

    private void addTickMarksPerWeekRow(List<ContributionTimeSlot> contributorsPerWeek, int barWidth) {
        landscapeReport.startTableRow();
        landscapeReport.addTableCell("", "border: none");

        for (int i = 0; i < contributorsPerWeek.size(); i++) {
            ContributionTimeSlot week = contributorsPerWeek.get(i);

            String yearString = week.getTimeSlot().split("[-]")[0];

            String color = "darkgrey";

            if (StringUtils.isNumeric(yearString)) {
                int year = Integer.parseInt(yearString);
                color = year % 2 == 0 ? "#c9c9c9" : "#656565";
            }
            String[] splitNow = week.getTimeSlot().split("-");
            String textNow = splitNow.length < 2 ? "" : splitNow[0] + "<br>" + splitNow[1];

            int colspan = 1;

            while (true) {
                String nextTimeSlot = contributorsPerWeek.size() > i + 1 ? contributorsPerWeek.get(i + 1).getTimeSlot() : "";
                String[] splitNext = nextTimeSlot.split("-");
                String textNext = splitNext.length < 2 ? "" : splitNext[0] + "<br>" + splitNext[1];
                if (contributorsPerWeek.size() <= i + 1 || !textNow.equalsIgnoreCase(textNext)) {
                    break;
                }
                colspan++;
                i++;
            }
            landscapeReport.startTableCellColSpan(colspan, "width: "
                    + barWidth + "px; min-width: "
                    + barWidth + "px; padding: 0; margin: 1px; border: none; text-align: center; vertical-align: bottom; font-size: 80%; height: 16px");
            landscapeReport.addHtmlContent("<div style='width: 100%; margin: 1px; font-size: 80%; color: '" + color + ">"
                    + textNow + "</div>");
            landscapeReport.endTableCell();
        }
        landscapeReport.endTableRow();
    }

    private void addCommitsPerWeekRow(List<ContributionTimeSlot> contributorsPerWeek, int minMaxWindow, int barWidth) {
        landscapeReport.startTableRow();
        int maxCommits = contributorsPerWeek.stream().mapToInt(c -> c.getCommitsCount()).max().orElse(1);
        int maxCommits4Weeks = contributorsPerWeek.subList(0, minMaxWindow).stream().mapToInt(c -> c.getCommitsCount()).max().orElse(0);
        int minCommits4Weeks = contributorsPerWeek.subList(0, minMaxWindow).stream().mapToInt(c -> c.getCommitsCount()).min().orElse(0);
        landscapeReport.addTableCell("<b>Commits</b>" +
                "<div style='color: grey; font-size: 80%; margin-left: 8px; margin-top: 4px;'>"
                + "min (" + minMaxWindow + " weeks): " + minCommits4Weeks
                + "<br>max (" + minMaxWindow + " weeks): " + maxCommits4Weeks + "</div>", "border: none");
        contributorsPerWeek.forEach(week -> {
            landscapeReport.startTableCell("width: " + barWidth + "px; min-width: " + barWidth + "px; padding: 0; margin: 1px; border: none; text-align: center; vertical-align: bottom; font-size: 80%; height: 100px");
            int count = week.getCommitsCount();
            int height = 1 + (int) (64.0 * count / maxCommits);
            String title = "week of " + week.getTimeSlot() + " = " + count + " commits";
            String yearString = week.getTimeSlot().split("[-]")[0];

            String color = "darkgrey";

            if (StringUtils.isNumeric(yearString)) {
                int year = Integer.parseInt(yearString);
                color = year % 2 == 0 ? "#c9c9c9" : "#656565";
            }

            landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; color: grey; font-size: 70%; margin: 0px'>" + count + "</div>");
            landscapeReport.addHtmlContent("<div title='" + title + "' style='width: 100%; background-color: " + color + "; height:" + height + "px; margin: 1px'></div>");
            landscapeReport.endTableCell();
        });
        landscapeReport.endTableRow();
    }

    private int getContributorsCountPerYear(String year) {
        return this.contributorsPerYearMap.containsKey(year) ? contributorsPerYearMap.get(year).size() : 0;
    }

    private void populateTimeSlotMaps() {
        landscapeAnalysisResults.getContributors().forEach(contributorProjects -> {
            List<String> commitDates = contributorProjects.getContributor().getCommitDates();
            commitDates.forEach(day -> {
                String week = DateUtils.getWeekMonday(day);
                String month = DateUtils.getMonth(day);
                String year = DateUtils.getYear(day);

                updateTimeSlotMap(contributorProjects, contributorsPerWeekMap, rookiesPerWeekMap, week, week);
                updateTimeSlotMap(contributorProjects, contributorsPerMonthMap, rookiesPerMonthMap, month, month + "-01");
                updateTimeSlotMap(contributorProjects, contributorsPerYearMap, rookiesPerYearMap, year, year + "-01-01");
            });
        });

    }

    private void updateTimeSlotMap(ContributorProjects contributorProjects,
                                   Map<String, List<String>> map, Map<String, List<String>> rookiesMap, String key, String rookieDate) {
        boolean rookie = contributorProjects.getContributor().isRookieAtDate(rookieDate);

        String email = contributorProjects.getContributor().getEmail();
        if (map.containsKey(key)) {
            if (!map.get(key).contains(email)) {
                map.get(key).add(email);
            }
        } else {
            map.put(key, new ArrayList<>(Arrays.asList(email)));
        }
        if (rookie) {
            if (rookiesMap.containsKey(key)) {
                if (!rookiesMap.get(key).contains(email)) {
                    rookiesMap.get(key).add(email);
                }
            } else {
                rookiesMap.put(key, new ArrayList<>(Arrays.asList(email)));
            }
        }
    }

    private List<String> getContributorsPerWeek(String week, boolean rookiesOnly) {
        Map<String, List<String>> map = rookiesOnly ? rookiesPerWeekMap : contributorsPerWeekMap;
        return map.containsKey(week) ? map.get(week) : new ArrayList<>();
    }

    private List<String> getLastContributorsPerWeek(String week, boolean first) {
        Map<String, String> emails = new HashMap();

        landscapeAnalysisResults.getContributors().stream()
                .sorted((a, b) -> b.getContributor().getCommitsCount30Days() - a.getContributor().getCommitsCount30Days())
                .filter(c -> !DateUtils.getWeekMonday(c.getContributor().getFirstCommitDate()).equals(DateUtils.getWeekMonday(c.getContributor().getLatestCommitDate())))
                .filter(c -> c.getContributor().getCommitDates().size() >= 10)
                .forEach(contributorProjects -> {
                    Contributor contributor = contributorProjects.getContributor();
                    if (DateUtils.getWeekMonday(first ? contributor.getFirstCommitDate() : contributor.getLatestCommitDate()).equals(week)) {
                        String email = contributor.getEmail();
                        emails.put(email, email);
                        return;
                    }
                });

        return new ArrayList<>(emails.values());
    }

    private List<String> getContributorsPerMonth(String month, boolean rookiesOnly) {
        Map<String, List<String>> map = rookiesOnly ? rookiesPerMonthMap : contributorsPerMonthMap;
        return map.containsKey(month) ? map.get(month) : new ArrayList<>();
    }

    private List<String> getLastContributorsPerMonth(String month, boolean first) {
        Map<String, String> emails = new HashMap();

        landscapeAnalysisResults.getContributors().stream()
                .sorted((a, b) -> b.getContributor().getCommitsCount30Days() - a.getContributor().getCommitsCount30Days())
                .filter(c -> !DateUtils.getMonth(c.getContributor().getLatestCommitDate()).equals(DateUtils.getMonth(c.getContributor().getFirstCommitDate())))
                .forEach(contributorProjects -> {
                    Contributor contributor = contributorProjects.getContributor();
                    if (DateUtils.getMonth(first ? contributor.getFirstCommitDate() : contributor.getLatestCommitDate()).equals(month)) {
                        String email = contributor.getEmail();
                        emails.put(email, email);
                        return;
                    }
                });

        return new ArrayList<>(emails.values());
    }

    private void renderPeopleDependencies(List<ComponentDependency> peopleDependencies, List<ContributorConnections> contributorConnections,
                                          double cIndex, double pIndex,
                                          double cMean, double pMean,
                                          double cMedian, double pMedian,
                                          int daysAgo) {
        List<ContributorProjects> contributors = landscapeAnalysisResults.getContributors();
        List<ComponentDependency> projectDependenciesViaPeople = ContributorConnectionUtils.getProjectDependenciesViaPeople(contributors, 0, daysAgo);

        landscapeReport.addLevel2Header("People Dependencies (past " + daysAgo + " days)", "margin-top: 40px");

        landscapeReport.startTable();
        landscapeReport.startTableRow();
        landscapeReport.startTableCell("border: none; vertical-align: top");
        landscapeReport.addHtmlContent(DEPENDENCIES_ICON);
        landscapeReport.endTableCell();
        landscapeReport.startTableCell("border: none");
        addPeopleGraph(peopleDependencies, daysAgo);
        addProjectsGraph(daysAgo, projectDependenciesViaPeople);
        landscapeReport.endTableCell();
        landscapeReport.endTableRow();
        landscapeReport.endTable();

        if (daysAgo > 60) {
            landscapeReport.startShowMoreBlock("show details...");
        }
        int connectionSum = contributorConnections.stream().mapToInt(c -> c.getConnectionsCount()).sum();

        List<Double> activeContributors30DaysHistory = landscapeAnalysisResults.getActiveContributors30DaysHistory();
        if (activeContributors30DaysHistory.size() > 0) {
            landscapeReport.addLineBreak();
            landscapeReport.addLineBreak();
            addDataSection("Active Contributors", activeContributors30DaysHistory.get(0), daysAgo, activeContributors30DaysHistory,
                    "An active contributor is anyone who has committed code changes in past " + daysAgo + " days.");
        }
        List<Double> peopleDependenciesCount30DaysHistory = landscapeAnalysisResults.getPeopleDependenciesCount30DaysHistory();
        if (peopleDependenciesCount30DaysHistory.size() > 0) {
            addDataSection("Unique Contributor-to-Contributor (C2C) Connections",
                    peopleDependenciesCount30DaysHistory.get(0), daysAgo, peopleDependenciesCount30DaysHistory,
                    "C2C dependencies are measured via the same repositories that two persons changed in the past " + daysAgo + " days. " +
                            "<br>Currently there are <b>" + FormattingUtils.formatCount(peopleDependencies.size()) + "</b> " +
                            "unique contributor-to-contributor (C2C) connections via <b>" +
                            FormattingUtils.formatCount(connectionSum) + "</b> shared repositories.");
        }

        addDataSection("C-median", cMedian, daysAgo, landscapeAnalysisResults.getcMedian30DaysHistory(),
                "C-median is the average number of contributors a person worked with in the past " + daysAgo + " days.");
        landscapeReport.startShowMoreBlock("show c-mean and c-index...");
        addDataSection("C-mean", cMean, daysAgo, landscapeAnalysisResults.getcMean30DaysHistory(), "");
        addDataSection("C-index", cIndex, daysAgo, landscapeAnalysisResults.getcIndex30DaysHistory(),
                "you have people with " + cIndex + " or more project connections with other people");
        landscapeReport.endShowMoreBlock();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();

        addDataSection("P-median", pMedian, daysAgo, landscapeAnalysisResults.getpMedian30DaysHistory(),
                "P-median is the average number of projects (repositories) a person worked on in the past " + daysAgo + " days.");
        landscapeReport.startShowMoreBlock("show p-mean and p-index...");
        addDataSection("P-mean", pMean, daysAgo, landscapeAnalysisResults.getpMean30DaysHistory(), "");
        addDataSection("P-index", pIndex, daysAgo, landscapeAnalysisResults.getpIndex30DaysHistory(),
                "you have " + pIndex + " people committing to " + pIndex + " or more projects");
        landscapeReport.endShowMoreBlock();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();

        peopleDependencies.sort((a, b) -> b.getCount() - a.getCount());

        addMostConnectedPeopleSection(contributorConnections, daysAgo);
        addMostProjectsPeopleSection(contributorConnections, daysAgo);
        addTopConnectionsSection(peopleDependencies, daysAgo, contributors);
        addProjectContributors(contributors, daysAgo);
        addProjectDependenciesViaPeople(projectDependenciesViaPeople);

        if (daysAgo > 60) {
            landscapeReport.endShowMoreBlock();
        }

    }

    private void addProjectsGraph(int daysAgo, List<ComponentDependency> projectDependenciesViaPeople) {
        landscapeReport.startShowMoreBlock("show project dependencies graph...<br>");
        StringBuilder builder = new StringBuilder();
        builder.append("Project 1\tProject 2\t# people\n");
        projectDependenciesViaPeople.subList(0, Math.min(10000, projectDependenciesViaPeople.size())).forEach(d -> builder
                .append(d.getFromComponent()).append("\t")
                .append(d.getToComponent()).append("\t")
                .append(d.getCount()).append("\n"));
        String fileName = "projects_dependencies_via_people_" + daysAgo + "_days.txt";
        saveData(fileName, builder.toString());

        landscapeReport.addHtmlContent("&nbsp;&nbsp;&nbsp;&nbsp;");
        landscapeReport.addNewTabLink("See data...", "data/" + fileName);
        String graphId = addDependencyGraphVisuals(projectDependenciesViaPeople, new ArrayList<>(), "project_dependencies_" + daysAgo + "_");

        landscapeReport.endShowMoreBlock();
        landscapeReport.addNewTabLink(" - show project dependencies as 3D force graph", "visuals/" + graphId + "_force_3d.html");
        landscapeReport.addLineBreak();
    }

    private void addDataSection(String type, double value, int daysAgo, List<Double> history, String info) {
        if (StringUtils.isNotBlank(info)) {
            landscapeReport.addParagraph(type + ": <b>" + ((int) Math.round(value)) + "</b>");
            landscapeReport.addParagraph("<span style='color: #a2a2a2; font-size: 90%;'>" + info + "</span>", "margin-top: -12px;");
        } else {
            landscapeReport.addParagraph(type + ": <b>" + ((int) Math.round(value)) + "</b>");
        }

        if (daysAgo == 30 && history.size() > 0) {
            landscapeReport.startTable("border: none");
            landscapeReport.startTableRow("font-size: 70%;");
            double max = history.stream().max(Double::compare).get();
            history.forEach(historicalValue -> {
                landscapeReport.startTableCell("text-align: center; vertical-align: bottom;border: none");
                landscapeReport.addContentInDiv((int) Math.round(historicalValue) + "", "width: 20px;border: none");
                landscapeReport.addContentInDiv("", "width: 20px; background-color: skyblue; border: none; height:"
                        + (int) (1 + Math.round(40.0 * historicalValue / max)) + "px;");
                landscapeReport.endTableCell();
            });
            landscapeReport.endTableRow();
            landscapeReport.startTableRow("font-size: 70%;");
            landscapeReport.addTableCell("<b>now</b>", "border: none");
            landscapeReport.addTableCell("1m<br>ago", "text-align: center; border: none");
            for (int i = 0; i < history.size() - 2; i++) {
                landscapeReport.addTableCell((i + 2) + "m<br>ago", "text-align: center; border: none");
            }
            landscapeReport.endTableRow();
            landscapeReport.endTable();
            landscapeReport.addLineBreak();
            landscapeReport.addLineBreak();
        }
    }

    private void addProjectDependenciesViaPeople(List<ComponentDependency> projectDependenciesViaPeople) {
        landscapeReport.startShowMoreBlock("show project dependencies via people...<br>");
        landscapeReport.startTable();
        int maxListSize = Math.min(100, projectDependenciesViaPeople.size());
        if (maxListSize < projectDependenciesViaPeople.size()) {
            landscapeReport.addParagraph("&nbsp;&nbsp;&nbsp;&nbsp;Showing top " + maxListSize + " items (out of " + projectDependenciesViaPeople.size() + ").");
        } else {
            landscapeReport.addParagraph("&nbsp;&nbsp;&nbsp;&nbsp;Showing all " + maxListSize + (maxListSize == 1 ? " item" : " items") + ".");
        }
        projectDependenciesViaPeople.subList(0, maxListSize).forEach(dependency -> {
            landscapeReport.startTableRow();
            landscapeReport.addTableCell(dependency.getFromComponent());
            landscapeReport.addTableCell(dependency.getToComponent());
            landscapeReport.addTableCell(dependency.getCount() + (dependency.getCount() == 1 ? " person" : " people"));
            landscapeReport.endTableRow();
        });
        landscapeReport.endTable();
        landscapeReport.endShowMoreBlock();
    }

    private void addProjectContributors(List<ContributorProjects> contributors, int daysAgo) {
        Map<String, Pair<String, Integer>> map = new HashMap<>();
        final List<String> list = new ArrayList<>();

        contributors.forEach(contributorProjects -> {
            contributorProjects.getProjects().stream().filter(project -> DateUtils.isAnyDateCommittedBetween(project.getCommitDates(), 0, daysAgo)).forEach(project -> {
                String key = project.getProjectAnalysisResults().getAnalysisResults().getMetadata().getName();
                if (map.containsKey(key)) {
                    Integer currentValue = map.get(key).getRight();
                    map.put(key, Pair.of(key, currentValue + 1));
                } else {
                    Pair<String, Integer> pair = Pair.of(key, 1);
                    map.put(key, pair);
                    list.add(key);
                }
            });
        });

        Collections.sort(list, (a, b) -> map.get(b).getRight() - map.get(a).getRight());

        List<String> displayList = list;
        if (list.size() > 100) {
            displayList = list.subList(0, 100);
        }

        landscapeReport.startShowMoreBlock("show projects with most people...<br>");
        StringBuilder builder = new StringBuilder();
        builder.append("Contributor\t# people\n");
        list.forEach(project -> builder.append(map.get(project).getLeft()).append("\t")
                .append(map.get(project).getRight()).append("\n"));
        String prefix = "projects_with_most_people_" + daysAgo + "_days";
        String fileName = prefix + ".txt";
        saveData(fileName, builder.toString());

        if (displayList.size() < list.size()) {
            landscapeReport.addParagraph("&nbsp;&nbsp;&nbsp;&nbsp;Showing top 100 items (out of " + list.size() + ").");
        }
        landscapeReport.addHtmlContent("&nbsp;&nbsp;&nbsp;&nbsp;");
        landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_" + prefix + ".html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("tree map", "visuals/tree_map_" + prefix + ".html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("data", "data/" + fileName);
        landscapeReport.addHtmlContent("</p>");
        List<VisualizationItem> visualizationItems = new ArrayList<>();
        list.forEach(project -> visualizationItems.add(new VisualizationItem(project, map.get(project).getRight())));
        exportVisuals(prefix, visualizationItems);
        landscapeReport.startTable();
        displayList.forEach(project -> {
            landscapeReport.startTableRow();
            landscapeReport.addTableCell(map.get(project).getLeft());
            Integer count = map.get(project).getRight();
            landscapeReport.addTableCell(count + (count == 1 ? " person" : " people"));
            landscapeReport.endTableRow();
        });
        landscapeReport.endTable();
        landscapeReport.endShowMoreBlock();
    }

    private void addPeopleGraph(List<ComponentDependency> peopleDependencies, int daysAgo) {
        StringBuilder builder = new StringBuilder();
        builder.append("Contributor 1\tContributor 2\t# shared projects\n");
        peopleDependencies.forEach(d -> builder
                .append(d.getFromComponent()).append("\t")
                .append(d.getToComponent()).append("\t")
                .append(d.getCount()).append("\n"));
        String fileName = "projects_shared_projects_" + daysAgo + "_days.txt";
        saveData(fileName, builder.toString());

        landscapeReport.startShowMoreBlock("show people dependencies graph...<br>");
        landscapeReport.addHtmlContent("&nbsp;&nbsp;&nbsp;&nbsp;");
        landscapeReport.addNewTabLink("See data...", "data/" + fileName);

        GraphvizDependencyRenderer graphvizDependencyRenderer = new GraphvizDependencyRenderer();
        graphvizDependencyRenderer.setMaxNumberOfDependencies(100);
        graphvizDependencyRenderer.setType("graph");
        graphvizDependencyRenderer.setArrow("--");
        String graphId = addDependencyGraphVisuals(peopleDependencies, new ArrayList<>(), "people_dependencies_" + daysAgo + "_");
        landscapeReport.endShowMoreBlock();

        landscapeReport.addNewTabLink(" - show people dependencies as 3D force graph", "visuals/" + graphId + "_force_3d.html");
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();
    }

    private void addTopConnectionsSection(List<ComponentDependency> peopleDependencies, int daysAgo, List<ContributorProjects> contributors) {
        landscapeReport.startShowMoreBlock("show top connections...<br>");
        landscapeReport.startTable();
        List<ComponentDependency> displayListConnections = peopleDependencies.subList(0, Math.min(100, peopleDependencies.size()));
        if (displayListConnections.size() < peopleDependencies.size()) {
            landscapeReport.addParagraph("&nbsp;&nbsp;&nbsp;&nbsp;Showing top " + displayListConnections.size() + " items (out of " + peopleDependencies.size() + ").");
        } else {
            landscapeReport.addParagraph("&nbsp;&nbsp;&nbsp;&nbsp;Showing all " + displayListConnections.size() + (displayListConnections.size() == 1 ? " item" : " items") + ".");
        }
        int index[] = {0};
        displayListConnections.forEach(dependency -> {
            index[0] += 1;
            landscapeReport.startTableRow();
            String from = dependency.getFromComponent();
            String to = dependency.getToComponent();
            int dependencyCount = dependency.getCount();
            landscapeReport.addTableCell(index[0] + ".");
            int projectCount1 = ContributorConnectionUtils.getProjectCount(contributors, from, 0, daysAgo);
            int projectCount2 = ContributorConnectionUtils.getProjectCount(contributors, to, 0, daysAgo);
            double perc1 = 0;
            double perc2 = 0;
            if (projectCount1 > 0) {
                perc1 = 100.0 * dependencyCount / projectCount1;
            }
            if (projectCount2 > 0) {
                perc2 = 100.0 * dependencyCount / projectCount2;
            }
            landscapeReport.addTableCell(from + "<br><span style='color: grey'>" + projectCount1 + " projects (" + FormattingUtils.getFormattedPercentage(perc1) + "%)</span>", "");
            landscapeReport.addTableCell(to + "<br><span style='color: grey'>" + projectCount2 + " projects (" + FormattingUtils.getFormattedPercentage(perc2) + "%)</span>", "");
            landscapeReport.addTableCell(dependencyCount + " shared projects", "");
            landscapeReport.endTableRow();
        });
        landscapeReport.endTable();
        landscapeReport.endShowMoreBlock();
    }

    private void addMostConnectedPeopleSection(List<ContributorConnections> contributorConnections, int daysAgo) {
        landscapeReport.startShowMoreBlock("show most connected people...<br>");
        StringBuilder builder = new StringBuilder();
        builder.append("Contributor\t# projects\t# connections\n");
        contributorConnections.forEach(c -> builder.append(c.getEmail()).append("\t")
                .append(c.getProjectsCount()).append("\t")
                .append(c.getConnectionsCount()).append("\n"));
        String prefix = "most_connected_people_" + daysAgo + "_days";
        String fileName = prefix + ".txt";

        saveData(fileName, builder.toString());

        List<ContributorConnections> displayListPeople = contributorConnections.subList(0, Math.min(100, contributorConnections.size()));
        if (displayListPeople.size() < contributorConnections.size()) {
            landscapeReport.addHtmlContent("<p>&nbsp;&nbsp;&nbsp;&nbsp;Showing top " + displayListPeople.size() + " items (out of " + contributorConnections.size() + "). ");
        } else {
            landscapeReport.addHtmlContent("<p>&nbsp;&nbsp;&nbsp;&nbsp;Showing all " + displayListPeople.size() + (displayListPeople.size() == 1 ? " item" : " items") + ". ");
        }
        landscapeReport.addHtmlContent("&nbsp;&nbsp;&nbsp;&nbsp;");
        landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_" + prefix + ".html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("tree map", "visuals/tree_map_" + prefix + ".html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("data", "data/" + fileName);
        landscapeReport.addHtmlContent("</p>");
        List<VisualizationItem> visualizationItems = new ArrayList<>();
        contributorConnections.forEach(c -> visualizationItems.add(new VisualizationItem(c.getEmail(), c.getConnectionsCount())));
        exportVisuals(prefix, visualizationItems);
        landscapeReport.addHtmlContent("</p>");
        int index[] = {0};
        landscapeReport.startTable();
        displayListPeople.forEach(name -> {
            index[0] += 1;
            landscapeReport.startTableRow();
            landscapeReport.addTableCell(index[0] + ".", "");
            landscapeReport.addTableCell(name.getEmail(), "");
            landscapeReport.addTableCell(name.getProjectsCount() + "&nbsp;projects");
            landscapeReport.addTableCell(name.getConnectionsCount() + " connections", "");
            landscapeReport.endTableRow();
        });
        landscapeReport.endTable();
        landscapeReport.endShowMoreBlock();

    }

    private void addMostProjectsPeopleSection(List<ContributorConnections> contributorConnections, int daysAgo) {
        landscapeReport.startShowMoreBlock("show people with most projects...<br>");
        List<ContributorConnections> sorted = new ArrayList<>(contributorConnections);
        sorted.sort((a, b) -> b.getProjectsCount() - a.getProjectsCount());
        List<ContributorConnections> displayListPeople = sorted.subList(0, Math.min(100, sorted.size()));
        if (displayListPeople.size() < contributorConnections.size()) {
            landscapeReport.addHtmlContent("<p>&nbsp;&nbsp;&nbsp;&nbsp;Showing top " + displayListPeople.size() + " items (out of " + contributorConnections.size() + "). ");
        } else {
            landscapeReport.addHtmlContent("&nbsp;&nbsp;&nbsp;&nbsp;Showing all " + displayListPeople.size() + (displayListPeople.size() == 1 ? " item" : " items") + ". ");
        }
        String prefix = "most_projects_people_" + daysAgo + "_days";
        StringBuilder builder = new StringBuilder();
        builder.append("Contributor\t# projects\t# connections\n");
        contributorConnections.forEach(c -> builder.append(c.getEmail()).append("\t")
                .append(c.getProjectsCount()).append("\t")
                .append(c.getConnectionsCount()).append("\n"));
        String fileName = prefix + ".txt";
        saveData(fileName, builder.toString());

        landscapeReport.addHtmlContent("&nbsp;&nbsp;&nbsp;&nbsp;");
        landscapeReport.addNewTabLink("bubble chart", "visuals/bubble_chart_" + prefix + ".html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("tree map", "visuals/tree_map_" + prefix + ".html");
        landscapeReport.addHtmlContent(" | ");
        landscapeReport.addNewTabLink("data", "data/" + fileName);
        landscapeReport.addHtmlContent("</p>");
        List<VisualizationItem> visualizationItems = new ArrayList<>();
        contributorConnections.forEach(c -> visualizationItems.add(new VisualizationItem(c.getEmail(), c.getProjectsCount())));
        int index[] = {0};
        landscapeReport.startTable();
        displayListPeople.forEach(name -> {
            index[0] += 1;
            landscapeReport.startTableRow();
            landscapeReport.addTableCell(index[0] + ".", "");
            landscapeReport.addTableCell(name.getEmail(), "");
            landscapeReport.addTableCell(name.getProjectsCount() + "&nbsp;projects");
            landscapeReport.addTableCell(name.getConnectionsCount() + " connections", "");
            landscapeReport.endTableRow();
        });

        exportVisuals(prefix, visualizationItems);
        landscapeReport.endTable();
        landscapeReport.endShowMoreBlock();
    }

    private void exportVisuals(String prefix, List<VisualizationItem> visualizationItems) {
        try {
            new LandscapeVisualsGenerator(reportsFolder).exportVisuals(prefix, visualizationItems);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String addDependencyGraphVisuals(List<ComponentDependency> componentDependencies, List<String> componentNames, String prefix) {
        GraphvizDependencyRenderer graphvizDependencyRenderer = new GraphvizDependencyRenderer();
        graphvizDependencyRenderer.setMaxNumberOfDependencies(100);
        graphvizDependencyRenderer.setType("graph");
        graphvizDependencyRenderer.setArrow("--");

        if (100 < componentDependencies.size()) {
            landscapeReport.addParagraph("&nbsp;&nbsp;&nbsp;&nbsp;Showing top " + 100 + " items (out of " + componentDependencies.size() + ").");
        } else {
            landscapeReport.addParagraph("&nbsp;&nbsp;&nbsp;&nbsp;Showing all " + componentDependencies.size() + (componentDependencies.size() == 1 ? " item" : " items") + ".");
        }
        String graphvizContent = graphvizDependencyRenderer.getGraphvizContent(componentNames, componentDependencies);
        String graphId = prefix + dependencyVisualCounter++;
        landscapeReport.addGraphvizFigure(graphId, "", graphvizContent);
        landscapeReport.addLineBreak();
        landscapeReport.addLineBreak();

        addDownloadLinks(graphId);
        export3DForceGraph(componentDependencies, graphId);

        return graphId;
    }

    private void export3DForceGraph(List<ComponentDependency> componentDependencies, String graphId) {
        Force3DObject force3DObject = new Force3DObject();
        Map<String, Integer> names = new HashMap<>();
        componentDependencies.forEach(dependency -> {
            String from = dependency.getFromComponent();
            String to = dependency.getToComponent();
            if (names.containsKey(from)) {
                names.put(from, names.get(from) + 1);
            } else {
                names.put(from, 1);
            }
            if (names.containsKey(to)) {
                names.put(to, names.get(to) + 1);
            } else {
                names.put(to, 1);
            }
            force3DObject.getLinks().add(new Force3DLink(from, to, dependency.getCount()));
            force3DObject.getLinks().add(new Force3DLink(to, from, dependency.getCount()));
        });
        names.keySet().forEach(key -> {
            force3DObject.getNodes().add(new Force3DNode(key, names.get(key)));
        });
        File folder = new File(reportsFolder, "visuals");
        folder.mkdirs();
        try {
            FileUtils.write(new File(folder, graphId + "_force_3d.html"), new VisualizationTemplate().render3DForceGraph(force3DObject), UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addDownloadLinks(String graphId) {
        landscapeReport.startDiv("");
        landscapeReport.addHtmlContent("Download: ");
        landscapeReport.addNewTabLink("SVG", "visuals/" + graphId + ".svg");
        landscapeReport.addHtmlContent(" ");
        landscapeReport.addNewTabLink("DOT", "visuals/" + graphId + ".dot.txt");
        landscapeReport.addHtmlContent(" ");
        landscapeReport.addNewTabLink("(open online Graphviz editor)", "https://obren.io/tools/graphviz/");
        landscapeReport.endDiv();
    }

    private void saveData(String fileName, String content) {
        File reportsFolder = Paths.get(this.folder.getParent(), "").toFile();
        File folder = Paths.get(reportsFolder.getPath(), "_sokrates_landscape/data").toFile();
        folder.mkdirs();

        try {
            File file = new File(folder, fileName);
            FileUtils.writeStringToFile(file, content, UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<RichTextReport> getIndividualContributorReports() {
        return individualContributorReports;
    }

    public void setIndividualContributorReports(List<RichTextReport> individualContributorReports) {
        this.individualContributorReports = individualContributorReports;
    }

    abstract class ZommableCircleCountExtractors {
        public abstract int getCount(ProjectAnalysisResults projectAnalysisResults);
    }
}

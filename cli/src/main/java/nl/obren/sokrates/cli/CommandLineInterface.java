/*
 * Copyright (c) 2021 Željko Obrenović. All rights reserved.
 */

package nl.obren.sokrates.cli;

import nl.obren.sokrates.cli.git.GitHistoryExtractor;
import nl.obren.sokrates.common.io.JsonGenerator;
import nl.obren.sokrates.common.io.JsonMapper;
import nl.obren.sokrates.common.renderingutils.Thresholds;
import nl.obren.sokrates.common.renderingutils.VisualizationItem;
import nl.obren.sokrates.common.renderingutils.VisualizationTemplate;
import nl.obren.sokrates.common.renderingutils.force3d.Force3DLink;
import nl.obren.sokrates.common.renderingutils.force3d.Force3DNode;
import nl.obren.sokrates.common.renderingutils.force3d.Force3DObject;
import nl.obren.sokrates.common.renderingutils.x3d.Unit3D;
import nl.obren.sokrates.common.renderingutils.x3d.X3DomExporter;
import nl.obren.sokrates.common.utils.BasicColorInfo;
import nl.obren.sokrates.common.utils.ProgressFeedback;
import nl.obren.sokrates.reports.core.ReportFileExporter;
import nl.obren.sokrates.reports.core.RichTextReport;
import nl.obren.sokrates.reports.dataexporters.DataExporter;
import nl.obren.sokrates.reports.generators.statichtml.BasicSourceCodeReportGenerator;
import nl.obren.sokrates.reports.landscape.statichtml.LandscapeAnalysisCommands;
import nl.obren.sokrates.sourcecode.Link;
import nl.obren.sokrates.sourcecode.SourceFile;
import nl.obren.sokrates.sourcecode.analysis.CodeAnalyzer;
import nl.obren.sokrates.sourcecode.analysis.CodeAnalyzerSettings;
import nl.obren.sokrates.sourcecode.analysis.results.CodeAnalysisResults;
import nl.obren.sokrates.sourcecode.core.AnalysisConfig;
import nl.obren.sokrates.sourcecode.core.CodeConfiguration;
import nl.obren.sokrates.sourcecode.core.CodeConfigurationUtils;
import nl.obren.sokrates.sourcecode.filehistory.DateUtils;
import nl.obren.sokrates.sourcecode.githistory.ExtractGitHistoryFileHandler;
import nl.obren.sokrates.sourcecode.githistory.GitHistoryUtils;
import nl.obren.sokrates.sourcecode.landscape.analysis.LandscapeAnalysisUtils;
import nl.obren.sokrates.sourcecode.lang.LanguageAnalyzerFactory;
import nl.obren.sokrates.sourcecode.scoping.ScopeCreator;
import nl.obren.sokrates.sourcecode.scoping.custom.CustomConventionsHelper;
import nl.obren.sokrates.sourcecode.scoping.custom.CustomScopingConventions;
import nl.obren.sokrates.sourcecode.stats.SourceFileSizeDistribution;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CommandLineInterface {

    private static final Log LOG = LogFactory.getLog(CommandLineInterface.class);

    private ProgressFeedback progressFeedback;
    private DataExporter dataExporter = new DataExporter(this.progressFeedback);

    private Commands commands = new Commands();

    public static void main(String args[]) throws ParseException, IOException {
        CommandLineInterface commandLineInterface = new CommandLineInterface();
        commandLineInterface.run(args);

        System.exit(0);
    }

    public void run(String args[]) throws IOException {
        if (args.length == 0) {
            commands.usage();
            return;
        }

        if (progressFeedback != null) {
            progressFeedback.clear();
        }

        try {
            if (args[0].equalsIgnoreCase(commands.INIT)) {
                init(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.UPDATE_CONFIG)) {
                updateConfig(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.EXPORT_STANDARD_CONVENTIONS)) {
                exportConventions(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.UPDATE_LANDSCAPE)) {
                updateLandscape(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.INIT_CONVENTIONS)) {
                createNewConventionsFile(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.EXTRACT_GIT_SUB_HISTORY)) {
                extractGitSubHistory(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.EXTRACT_FILES)) {
                extractFiles(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.EXTRACT_GIT_HISTORY)) {
                extractGitHistory(args);
                return;
            } else if (args[0].equalsIgnoreCase(commands.UPDATE_LANDSCAPE)) {
                updateLandscape(args);
                return;
            } else if (!args[0].equalsIgnoreCase(commands.GENERATE_REPORTS)) {
                commands.usage();
                return;
            }

            generateReports(args);
        } catch (ParseException e) {
            System.out.println("ERROR: " + e.getMessage() + "\n");
            commands.usage();
        }
    }

    private void extractGitHistory(String[] args) throws ParseException {
        Options options = commands.getExtractGitHistoryOption();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(commands.getHelp().getOpt())) {
            commands.usage(Commands.EXTRACT_GIT_HISTORY, commands.getExtractGitHistoryOption(), Commands.EXTRACT_GIT_HISTORY_DESCRIPTION);
            return;
        }

        String strRootPath = cmd.getOptionValue(commands.getAnalysisRoot().getOpt());
        if (!cmd.hasOption(commands.getAnalysisRoot().getOpt())) {
            strRootPath = ".";
        }

        File root = new File(strRootPath);
        if (!root.exists()) {
            LOG.error("The analysis root \"" + root.getPath() + "\" does not exist.");
            return;
        }

        new GitHistoryExtractor().extractGitHistory(root);
    }

    private void extractGitSubHistory(String[] args) throws ParseException, IOException {
        Options options = commands.getExtractGitSubHistoryOption();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(commands.getHelp().getOpt())) {
            commands.usage(Commands.EXTRACT_GIT_SUB_HISTORY, commands.getExtractGitSubHistoryOption(), Commands.EXTRACT_GIT_SUB_HISTORY_DESCRIPTION);
            return;
        }

        String strRootPath = cmd.getOptionValue(commands.getAnalysisRoot().getOpt());
        if (!cmd.hasOption(commands.getAnalysisRoot().getOpt())) {
            strRootPath = ".";
        }

        File root = new File(strRootPath);
        if (!root.exists()) {
            LOG.error("The analysis root \"" + root.getPath() + "\" does not exist.");
            return;
        }

        String prefixValue = cmd.getOptionValue(commands.getPrefix().getOpt());

        new ExtractGitHistoryFileHandler().extractSubHistory(new File(root, GitHistoryUtils.GIT_HISTORY_FILE_NAME), prefixValue);
    }

    private void extractFiles(String[] args) throws ParseException, IOException {
        Options options = commands.getExtractFilesOption();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(commands.getHelp().getOpt())) {
            commands.usage(Commands.EXTRACT_FILES, commands.getExtractFilesOption(), Commands.EXTRACT_FILES_DESCRIPTION);
            return;
        }

        File root = cmd.hasOption(commands.getAnalysisRoot().getOpt()) ? new File(cmd.getOptionValue(commands.getAnalysisRoot().getOpt())) : new File(".");
        String patternValue = cmd.getOptionValue(commands.getPattern().getOpt());
        String dest = cmd.getOptionValue(commands.getDestRoot().getOpt());
        String destParentValue = cmd.getOptionValue(commands.getDestParent().getOpt());

        if (patternValue == null) {
            System.out.println("the pattern value is missing");
            return;
        }
        if (dest == null) {
            System.out.println("the destination folder value is missing");
            return;
        }
        if (destParentValue == null) {
            destParentValue = dest;
        }

        SokratesFileUtils.extractFiles(root, new File(root, dest), new File(root, destParentValue), patternValue);
    }

    private void updateDateParam(CommandLine cmd) {
        String dateString = cmd.getOptionValue(commands.getDate().getOpt());
        if (dateString != null) {
            System.out.println("Using '" + dateString + "' as latest source code update date for active contributors reports.");
            DateUtils.setDateParam(dateString);
        }
    }

    private void updateLandscape(String[] args) throws ParseException {
        Options options = commands.getUpdateLandscapeOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(commands.getHelp().getOpt())) {
            commands.usage(Commands.UPDATE_LANDSCAPE, commands.getUpdateLandscapeOptions(), Commands.UPDATE_LANDSCAPE_DESCRIPTION);
            return;
        }

        startTimeoutIfDefined(cmd);

        String strRootPath = cmd.getOptionValue(commands.getAnalysisRoot().getOpt());
        if (!cmd.hasOption(commands.getAnalysisRoot().getOpt())) {
            strRootPath = ".";
        }

        File root = new File(strRootPath);
        if (!root.exists()) {
            LOG.error("The analysis root \"" + root.getPath() + "\" does not exist.");
            return;
        }

        String confFilePath = cmd.getOptionValue(commands.getConfFile().getOpt());
        updateDateParam(cmd);

        if (cmd.hasOption(commands.getRecursive().getOpt())) {
            List<File> landscapeConfigFiles = LandscapeAnalysisUtils.findAllSokratesLandscapeConfigFiles(root);
            landscapeConfigFiles.forEach(landscapeConfigFile -> {
                File landscapeFolder = landscapeConfigFile.getParentFile().getParentFile();
                String absolutePath = landscapeFolder.getAbsolutePath().replace("/./", "/");
                System.out.println(System.getProperty("user.dir"));
                System.setProperty("user.dir", absolutePath);
                System.out.println(System.getProperty("user.dir"));
                LandscapeAnalysisCommands.update(new File(landscapeFolder.getAbsolutePath()), null);
            });
            System.out.println("Analysed " + landscapeConfigFiles + " landscape(s):");
            landscapeConfigFiles.forEach(landscapeConfigFile -> {
                System.out.println(" -  " + landscapeConfigFile.getPath());
            });
        } else {
            LandscapeAnalysisCommands.update(root, confFilePath != null ? new File(confFilePath) : null);
        }
    }

    private void generateReports(String[] args) throws ParseException, IOException {
        Options options = commands.getReportingOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(commands.getHelp().getOpt())) {
            commands.usage(Commands.GENERATE_REPORTS, commands.getReportingOptions(), Commands.GENERATE_REPORTS_DESCRIPTION);
            return;
        }

        startTimeoutIfDefined(cmd);

        generateReports(cmd);
    }

    private void init(String[] args) throws ParseException, IOException {
        Options options = commands.getInitOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(commands.getHelp().getOpt())) {
            commands.usage(Commands.INIT, commands.getInitOptions(), Commands.INIT_DESCRIPTION);
            return;
        }

        startTimeoutIfDefined(cmd);

        String strRootPath = cmd.getOptionValue(commands.getSrcRoot().getOpt());
        if (!cmd.hasOption(commands.getSrcRoot().getOpt())) {
            strRootPath = ".";
        }

        CustomScopingConventions customScopingConventions = null;
        if (cmd.hasOption(commands.getConventionsFile().getOpt())) {
            File scopingConventionsFile = new File(cmd.getOptionValue(commands.getConventionsFile().getOpt()));
            if (scopingConventionsFile.exists()) {
                customScopingConventions = CustomConventionsHelper.readFromFile(scopingConventionsFile);
            }
        }
        String nameValue = "";
        String descriptionValue = "";
        String logoLinkValue = "";
        if (cmd.hasOption(commands.getName().getOpt())) {
            nameValue = cmd.getOptionValue(commands.getName().getOpt());
        }
        if (cmd.hasOption(commands.getDescription().getOpt())) {
            descriptionValue = cmd.getOptionValue(commands.getDescription().getOpt());
        }
        if (cmd.hasOption(commands.getLogoLink().getOpt())) {
            logoLinkValue = cmd.getOptionValue(commands.getLogoLink().getOpt());
        }
        Link link = null;
        if (cmd.hasOption(commands.getAddLink().getOpt())) {
            String linkData[] = cmd.getOptionValues(commands.getAddLink().getOpt());
            if (linkData.length >= 1 && StringUtils.isNotBlank(linkData[0])) {
                String href = linkData[0];
                String label = linkData.length >= 1 ? linkData[1] : "";
                link = new Link(label, href);
            }
        }


        File root = new File(strRootPath);
        if (!root.exists()) {
            LOG.error("The src root \"" + root.getPath() + "\" does not exist.");
            return;
        }

        File conf = getConfigFile(cmd, root);

        updateDateParam(cmd);

        new ScopeCreator(root, conf, customScopingConventions).createScopeFromConventions(nameValue, descriptionValue, logoLinkValue, link);

        System.out.println("Configuration stored in " + conf.getPath());
    }

    private void startTimeoutIfDefined(CommandLine cmd) {
        String timeoutSeconds = cmd.getOptionValue(commands.getTimeout().getOpt());
        if (StringUtils.isNumeric(timeoutSeconds)) {
            int seconds = Integer.parseInt(timeoutSeconds);
            System.out.println("Timeout timer set to " + seconds + " seconds.");
            Executors.newCachedThreadPool().execute(() -> {
                try {
                    Thread.sleep(seconds * 1000);
                    System.out.println("Timeout after " + seconds + " seconds.");
                    System.exit(-1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void updateConfig(String[] args) throws ParseException, IOException {
        Options options = commands.getUpdateConfigOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(commands.getHelp().getOpt())) {
            commands.usage(Commands.UPDATE_CONFIG, commands.getUpdateConfigOptions(), Commands.UPDATE_CONFIG_DESCRIPTION);
            return;
        }

        startTimeoutIfDefined(cmd);

        String strRootPath = cmd.getOptionValue(commands.getSrcRoot().getOpt());
        if (!cmd.hasOption(commands.getSrcRoot().getOpt())) {
            strRootPath = ".";
        }

        File root = new File(strRootPath);
        if (!root.exists()) {
            LOG.error("The src root \"" + root.getPath() + "\" does not exist.");
            return;
        }

        File confFile = getConfigFile(cmd, root);
        System.out.println("Configuration file '" + confFile.getPath() + "'.");

        String jsonContent = FileUtils.readFileToString(confFile, UTF_8);
        CodeConfiguration codeConfiguration = (CodeConfiguration) new JsonMapper().getObject(jsonContent, CodeConfiguration.class);

        if (cmd.hasOption(commands.getSkipComplexAnalyses().getOpt())) {
            codeConfiguration.getAnalysis().setSkipDependencies(true);
            codeConfiguration.getAnalysis().setSkipDuplication(true);
            codeConfiguration.getAnalysis().setCacheSourceFiles(false);
        }

        if (cmd.hasOption(commands.getSkipDuplicationAnalyses().getOpt())) {
            codeConfiguration.getAnalysis().setSkipDuplication(true);
        }

        if (cmd.hasOption(commands.getEnableDuplicationAnalyses().getOpt())) {
            codeConfiguration.getAnalysis().setSkipDuplication(false);
        }

        if (cmd.hasOption(commands.getSetName().getOpt())) {
            String name = cmd.getOptionValue(commands.getSetName().getOpt());
            if (StringUtils.isNotBlank(name)) {
                codeConfiguration.getMetadata().setName(name);
            }
        }

        if (cmd.hasOption(commands.getSetDescription().getOpt())) {
            String description = cmd.getOptionValue(commands.getSetDescription().getOpt());
            if (StringUtils.isNotBlank(description)) {
                codeConfiguration.getMetadata().setDescription(description);
            }
        }

        if (cmd.hasOption(commands.getSetLogoLink().getOpt())) {
            String logoLink = cmd.getOptionValue(commands.getSetLogoLink().getOpt());
            if (StringUtils.isNotBlank(logoLink)) {
                codeConfiguration.getMetadata().setLogoLink(logoLink);
            }
        }

        if (cmd.hasOption(commands.getSetCacheFiles().getOpt())) {
            String cacheFileValue = cmd.getOptionValue(commands.getSetCacheFiles().getOpt());
            if (StringUtils.isNotBlank(cacheFileValue)) {
                codeConfiguration.getAnalysis().setCacheSourceFiles(cacheFileValue.equalsIgnoreCase("true"));
            }
        }

        if (cmd.hasOption(commands.getAddLink().getOpt())) {
            String linkData[] = cmd.getOptionValues(commands.getAddLink().getOpt());
            if (linkData.length >= 1 && StringUtils.isNotBlank(linkData[0])) {
                String href = linkData[0];
                String label = linkData.length >= 1 ? linkData[1] : "";
                codeConfiguration.getMetadata().getLinks().add(new Link(label, href));
            }
        }

        FileUtils.write(confFile, new JsonGenerator().generate(codeConfiguration), UTF_8);
    }

    private void exportConventions(String[] args) throws ParseException, IOException {
        File file = new File("standard_analysis_conventions.json");

        CustomConventionsHelper.saveStandardConventionsToFile(file);

        System.out.println("A standard conventions file saved to '" + file.getPath() + "'.");
    }

    private void createNewConventionsFile(String[] args) throws ParseException, IOException {
        File file = new File("analysis_conventions.json");

        CustomConventionsHelper.saveEmptyConventionsToFile(file);

        System.out.println("A new conventions file saved to '" + file.getPath() + "'.");
    }

    private File getConfigFile(CommandLine cmd, File root) {
        File conf;
        if (cmd.hasOption(commands.getConfFile().getOpt())) {
            conf = new File(cmd.getOptionValue(commands.getConfFile().getOpt()));
        } else {
            conf = CodeConfigurationUtils.getDefaultSokratesConfigFile(root);
        }
        return conf;
    }

    private void generateReports(CommandLine cmd) throws IOException {
        updateDateParam(cmd);

        File sokratesConfigFile;

        if (!cmd.hasOption(commands.getConfFile().getOpt())) {
            String confFilePath = "./_sokrates/config.json";
            sokratesConfigFile = new File(confFilePath);
        } else {
            sokratesConfigFile = new File(cmd.getOptionValue(commands.getConfFile().getOpt()));
        }

        System.out.println("Configuration file: " + sokratesConfigFile.getPath());
        if (noFileError(sokratesConfigFile)) return;

        String jsonContent = FileUtils.readFileToString(sokratesConfigFile, UTF_8);
        CodeConfiguration codeConfiguration = (CodeConfiguration) new JsonMapper().getObject(jsonContent, CodeConfiguration.class);
        LanguageAnalyzerFactory.getInstance().setOverrides(codeConfiguration.getAnalysis().getAnalyzerOverrides());

        detailedInfo("Starting analysis based on the configuration file " + sokratesConfigFile.getPath());

        File reportsFolder;

        if (!cmd.hasOption(commands.getOutputFolder().getOpt())) {
            reportsFolder = prepareReportsFolder("./_sokrates/reports");
        } else {
            reportsFolder = prepareReportsFolder(cmd.getOptionValue(commands.getOutputFolder().getOpt()));
        }

        System.out.println("Reports folder: " + reportsFolder.getPath());
        if (noFileError(reportsFolder)) return;

        if (this.progressFeedback == null) {
            this.progressFeedback = new ProgressFeedback() {
                public void setText(String text) {
                    System.out.println(text);
                }

                public void setDetailedText(String text) {
                    System.out.println(text);
                }
            };
        }

        try {
            CodeAnalyzer codeAnalyzer = new CodeAnalyzer(getCodeAnalyzerSettings(cmd), codeConfiguration, sokratesConfigFile);
            CodeAnalysisResults analysisResults = codeAnalyzer.analyze(progressFeedback);

            boolean useDefault = noReportingOptions(cmd);

            dataExporter.saveData(sokratesConfigFile, codeConfiguration, reportsFolder, analysisResults);
            saveTextualSummary(reportsFolder, analysisResults);

            generateVisuals(reportsFolder, analysisResults);

            generateAndSaveReports(sokratesConfigFile, reportsFolder, sokratesConfigFile.getParentFile(), codeAnalyzer, analysisResults);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean noFileError(File inputFile) {
        if (!inputFile.exists()) {
            System.out.println("ERROR: " + inputFile.getPath() + " does not exist.");
            return true;
        }
        return false;
    }

    private boolean noReportingOptions(CommandLine cmd) {
        for (Option arg : cmd.getOptions()) {
            if (arg.getOpt().toLowerCase().startsWith("report")) {
                return false;
            }
        }
        return true;
    }

    private void info(String text) {
        LOG.info(text);
        if (progressFeedback != null) {
            progressFeedback.setText(text);
        }
    }

    public void detailedInfo(String text) {
        LOG.info(text);
        if (progressFeedback != null) {
            progressFeedback.setDetailedText(text);
        }
    }

    private void generateAndSaveReports(File inputFile, File reportsFolder, File sokratesConfigFolder, CodeAnalyzer codeAnalyzer, CodeAnalysisResults analysisResults) {
        File htmlReports = getHtmlFolder(reportsFolder);
        File dataReports = dataExporter.getDataFolder();
        File srcCache = dataExporter.getCodeCacheFolder();
        CodeAnalyzerSettings codeAnalyzerSettings = codeAnalyzer.getCodeAnalyzerSettings();
        if (new File(htmlReports, "index.html").exists() || codeAnalyzerSettings.isUpdateIndex()) {
            info("HTML reports: <a href='" + htmlReports.getPath() + "/index.html'>" + htmlReports.getPath() + "</a>");
        } else {
            info("HTML reports: <a href='" + htmlReports.getPath() + "'>" + htmlReports.getPath() + "</a>");
        }
        info("Raw data: <a href='" + dataReports.getPath() + "'>" + dataReports.getPath() + "</a>");
        if (analysisResults.getCodeConfiguration().getAnalysis().isCacheSourceFiles()) {
            info("Source code cache : <a href='" + srcCache.getPath() + "'>" + srcCache.getPath() + "</a>");
        }
        info("");
        info("");
        BasicSourceCodeReportGenerator generator = new BasicSourceCodeReportGenerator(codeAnalyzerSettings, analysisResults, inputFile, reportsFolder);
        List<RichTextReport> reports = generator.report();
        reports.forEach(report -> {
            info("Generating the '" + report.getId().toUpperCase() + "' report...");
            ReportFileExporter.exportHtml(reportsFolder, "html", report, analysisResults.getCodeConfiguration().getAnalysis().getCustomHtmlReportHeaderFragment());
        });
        if (!codeAnalyzerSettings.isDataOnly() && codeAnalyzerSettings.isUpdateIndex()) {
            ReportFileExporter.exportReportsIndexFile(reportsFolder, analysisResults, sokratesConfigFolder);
        }
    }


    private void generateVisuals(File reportsFolder, CodeAnalysisResults analysisResults) {
        AtomicInteger index = new AtomicInteger();
        analysisResults.getLogicalDecompositionsAnalysisResults().forEach(logicalDecomposition -> {
            index.getAndIncrement();
            List<VisualizationItem> items = new ArrayList<>();
            Force3DObject force3DObject = new Force3DObject();
            logicalDecomposition.getComponents().forEach(component -> {
                items.add(new VisualizationItem(component.getName(), component.getLinesOfCode()));
                force3DObject.getNodes().add(new Force3DNode(component.getName(), component.getLinesOfCode()));
            });
            logicalDecomposition.getComponentDependencies().forEach(dependency -> {
                force3DObject.getLinks().add(new Force3DLink(dependency.getFromComponent(), dependency.getToComponent(), dependency.getCount()));
            });
            try {
                String nameSuffix = "components_" + index.toString() + ".html";
                String nameSuffixDependencies = "dependencies_" + index.toString() + ".html";
                File folder = new File(reportsFolder, "html/visuals");
                folder.mkdirs();
                FileUtils.write(new File(folder, "bubble_chart_" + nameSuffix), new VisualizationTemplate().renderBubbleChart(items), UTF_8);
                FileUtils.write(new File(folder, "tree_map_" + nameSuffix), new VisualizationTemplate().renderTreeMap(items), UTF_8);
                FileUtils.write(new File(folder, "force_3d_" + nameSuffixDependencies), new VisualizationTemplate().render3DForceGraph(force3DObject), UTF_8);

                generate3DUnitsView(folder, analysisResults);
            } catch (IOException e) {
                LOG.warn(e);
            }
        });

        try {
            File folder = new File(reportsFolder, "html/visuals");
            folder.mkdirs();

            generateFileStructureExplorers("main", folder, analysisResults.getMainAspectAnalysisResults().getAspect().getSourceFiles());
            generateFileStructureExplorers("test", folder, analysisResults.getTestAspectAnalysisResults().getAspect().getSourceFiles());
            generateFileStructureExplorers("generated", folder, analysisResults.getGeneratedAspectAnalysisResults().getAspect().getSourceFiles());
            generateFileStructureExplorers("build", folder, analysisResults.getBuildAndDeployAspectAnalysisResults().getAspect().getSourceFiles());
            generateFileStructureExplorers("other", folder, analysisResults.getOtherAspectAnalysisResults().getAspect().getSourceFiles());

            generate3DUnitsView(folder, analysisResults);
        } catch (IOException e) {
            LOG.warn(e);
        }

    }

    private void generateFileStructureExplorers(String nameSuffix, File folder, List<SourceFile> sourceFiles) throws IOException {
        List<VisualizationItem> items = getZoomableCirclesItems(sourceFiles);
        FileUtils.write(new File(folder, "zoomable_circles_" + nameSuffix + ".html"), new VisualizationTemplate().renderZoomableCircles(items), UTF_8);
        FileUtils.write(new File(folder, "zoomable_sunburst_" + nameSuffix + ".html"), new VisualizationTemplate().renderZoomableSunburst(items), UTF_8);
    }

    private List<VisualizationItem> getZoomableCirclesItems(List<SourceFile> sourceFiles) {
        DirectoryNode directoryTree = PathStringsToTreeStructure.createDirectoryTree(sourceFiles);
        if (directoryTree != null) {
            return directoryTree.toVisualizationItems();
        }

        return new ArrayList<>();
    }

    private void generate3DUnitsView(File visualsFolder, CodeAnalysisResults analysisResults) {
        AnalysisConfig analysisConfig = analysisResults.getCodeConfiguration().getAnalysis();

        List<Unit3D> unit3DConditionalComplexity = new ArrayList<>();
        analysisResults.getUnitsAnalysisResults().getAllUnits().forEach(unit -> {
            BasicColorInfo color = Thresholds.getColor(Thresholds.UNIT_MCCABE, unit.getMcCabeIndex());
            unit3DConditionalComplexity.add(new Unit3D(unit.getLongName(), unit.getLinesOfCode(), color));
        });

        List<Unit3D> unit3DSize = new ArrayList<>();
        analysisResults.getUnitsAnalysisResults().getAllUnits().forEach(unit -> {
            BasicColorInfo color = Thresholds.getColor(Thresholds.UNIT_LINES, unit.getLinesOfCode());
            unit3DSize.add(new Unit3D(unit.getLongName(), unit.getLinesOfCode(), color));
        });

        List<Unit3D> files3D = new ArrayList<>();
        analysisResults.getCodeConfiguration().getMain().getSourceFiles().forEach(file -> {
            SourceFileSizeDistribution sourceFileSizeDistribution = new SourceFileSizeDistribution(analysisConfig.getFileSizeThresholds());
            BasicColorInfo color = getFileSizeColor(sourceFileSizeDistribution, file.getLinesOfCode());
            files3D.add(new Unit3D(file.getFile().getPath(), file.getLinesOfCode(), color));
        });

        new X3DomExporter(new File(visualsFolder, "units_3d_complexity.html"), "A 3D View of All Units (Conditional Complexity)", "Each block is one unit. The height of the block represents the file unit size in lines of code. The color of the unit represents its conditional complexity category.").export(unit3DConditionalComplexity, false, 10);

        new X3DomExporter(new File(visualsFolder, "units_3d_size.html"), "A 3D View of All Units (Unit Size)", "Each block is one unit. The height of the block represents the file unit size in lines of code. The color of the unit represents its unit size category.").export(unit3DSize, false, 10);

        new X3DomExporter(new File(visualsFolder, "files_3d.html"), "A 3D View of All Files", "Each block is one file. The height of the block represents the file relative size in lines of code. The color of the file represents its unit size category.").export(files3D, false, 50);
    }

    public BasicColorInfo getFileSizeColor(SourceFileSizeDistribution distribution, int linesOfCode) {
        if (linesOfCode <= distribution.getLowRiskThreshold()) {
            return Thresholds.RISK_GREEN;
        } else if (linesOfCode <= distribution.getMediumRiskThreshold()) {
            return Thresholds.RISK_LIGHT_GREEN;
        } else if (linesOfCode <= distribution.getHighRiskThreshold()) {
            return Thresholds.RISK_YELLOW;
        } else if (linesOfCode <= distribution.getVeryHighRiskThreshold()) {
            return Thresholds.RISK_ORANGE;
        } else {
            return Thresholds.RISK_RED;
        }
    }


    private File getHtmlFolder(File reportsFolder) {
        File folder = new File(reportsFolder, commands.ARG_HTML_REPORTS_FOLDER_NAME);
        folder.mkdirs();
        return folder;
    }

    private void saveTextualSummary(File reportsFolder, CodeAnalysisResults analysisResults) throws IOException {
        File jsonFile = new File(dataExporter.getTextDataFolder(), "textualSummary.txt");
        FileUtils.write(jsonFile, analysisResults.getTextSummary().toString(), UTF_8);
    }

    private File prepareReportsFolder(String path) throws IOException {
        File reportsFolder = new File(path);
        reportsFolder.mkdirs();

        return reportsFolder;
    }

    private CodeAnalyzerSettings getCodeAnalyzerSettings(CommandLine cmd) {
        CodeAnalyzerSettings settings = new CodeAnalyzerSettings();

        return settings;
    }


    public void setProgressFeedback(ProgressFeedback progressFeedback) {
        this.progressFeedback = progressFeedback;
    }


}

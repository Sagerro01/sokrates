/*
 * Copyright (c) 2021 Željko Obrenović. All rights reserved.
 */

package nl.obren.sokrates.reports.utils;

import nl.obren.sokrates.sourcecode.SourceFile;
import nl.obren.sokrates.sourcecode.filehistory.FileModificationHistory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.List;

public class FilesReportUtils {

    public static String getFilesTable(List<SourceFile> sourceFiles, boolean linkToFiles, boolean showAge, boolean showLineLength) {
        return getFilesTable(sourceFiles, linkToFiles, showAge, showLineLength, 300);
    }

    public static String getFilesTable(List<SourceFile> sourceFiles, boolean linkToFiles, boolean showAge, boolean showLineLength, int maxHeight) {
        StringBuilder table = new StringBuilder();

        table.append("<div style='width: 100%; overflow-x: scroll; overflow-y: scroll; max-height: " + maxHeight + "px;'>\n");
        table.append("<table style='width: 80%'>\n");
        table.append("<tr>");
        String header = "<th>File</th><th># lines</th><th># units</th>";
        if (showLineLength) {
            header += "<th># long lines</th>";
        }
        if (showAge) {
            header += "<th>last modified<br/>(days ago)</th>";
            header += "<th>created<br/>(days ago)</th>";
            header += "<th># changes</th>";
        }
        table.append(header + "\n");
        table.append("<tr>");

        sourceFiles.stream().forEach(sourceFile -> {
            table.append("<tr>\n");

            File file = new File(sourceFile.getRelativePath());

            String fileNameFragment;

            if (linkToFiles) {
                String href = "../src/main/" + sourceFile.getRelativePath();
                fileNameFragment = "<a target='blank' href='" + href + ".html'>" + file.getName() + "</a>";
            } else {
                fileNameFragment = file.getName();
            }

            String parent = StringUtils.abbreviate(file.getParent(), 150);
            table.append("<td>" +
                    "<div><div style='display: inline-block; vertical-align: top; margin-top: 3px; margin-right: 4px;'>" +
                    DataImageUtils.getLangDataImageDiv30(FilenameUtils.getExtension(file.getName())) +
                    "</div><div style='display: inline-block;'><b>"
                    + fileNameFragment + "</b><br/>in " + (parent != null ? parent : "root") + "<br/>" +
                    "</div></div>" +
                    "</td>\n");
            table.append("<td style='text-align: center'>" + sourceFile.getLinesOfCode() + "</td>\n");
            if (sourceFile.getUnitsCount() > 0) {
                table.append("<td style='text-align: center'>" + sourceFile.getUnitsCount() + "</td>\n");
            } else {
                table.append("<td style='text-align: center; color: lightgrey'>-</td>\n");
            }
            if (showLineLength) {
                table.append("<td style='text-align: center'>" + sourceFile.getLongLinesCount(120) + "</td>\n");
            }

            if (showAge) {
                FileModificationHistory history = sourceFile.getFileModificationHistory();
                if (history != null) {
                    table.append("<td style='text-align: center'>" + history.daysSinceLatestUpdate() + "</td>\n");
                    table.append("<td style='text-align: center'>" + history.daysSinceFirstUpdate() + "</td>\n");
                    table.append("<td style='text-align: center'>" + history.getDates().size() + "</td>\n");
                } else {
                    table.append("<td style='text-align: center'></td>\n");
                    table.append("<td style='text-align: center'></td>\n");
                    table.append("<td style='texgetLongLinesCount(threshold)t-align: center'></td>\n");
                }
            }

            table.append("</tr>\n");
        });

        table.append("</table>\n");
        table.append("</div>\n");

        return table.toString();
    }
}

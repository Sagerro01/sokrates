package nl.obren.sokrates.reports.landscape.statichtml;

import nl.obren.sokrates.sourcecode.githistory.CommitsPerExtension;

import java.util.List;

public interface ExtractStringListValue<T> {
    List<String> getValue(T object);
}

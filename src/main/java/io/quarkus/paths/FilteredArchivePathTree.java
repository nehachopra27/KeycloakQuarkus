package io.quarkus.paths;

import java.nio.file.Path;

public class FilteredArchivePathTree extends ArchivePathTree {

    public FilteredArchivePathTree(Path archive, PathFilter pathFilter) {
        super(archive, pathFilter);
    }
}

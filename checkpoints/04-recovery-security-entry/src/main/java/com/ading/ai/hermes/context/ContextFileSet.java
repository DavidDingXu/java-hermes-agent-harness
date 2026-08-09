package com.ading.ai.hermes.context;

import java.util.List;

public record ContextFileSet(List<ContextFile> files, List<ContextFileRejection> rejections) {

    public ContextFileSet {
        files = List.copyOf(files);
        rejections = List.copyOf(rejections);
    }
}

package org.rri.server.completions.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record MergeEditsResult(
    @NotNull TextEditWithOffsets mainEdit,
    @NotNull List<TextEditWithOffsets> additionalEdits
) {
}

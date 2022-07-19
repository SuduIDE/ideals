package org.rri.server.commands;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.rri.server.MyTextDocumentService;

import java.util.function.Function;

public abstract class LspCommand<R> implements Function<ExecutorContext, R>, Disposable {
    protected static final Logger LOG = Logger.getInstance(MyTextDocumentService.class);

    @Override
    public void dispose() {
        // Do nothing
    }
}

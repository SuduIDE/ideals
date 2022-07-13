package org.rri.server.definition;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorFileSwapper;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.codehaus.plexus.util.ExceptionUtils;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.rri.server.LspPath;
import org.rri.server.MyTextDocumentService;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

import java.lang.reflect.Constructor;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FindDefinitionCommand implements Function<ExecutorContext, List<? extends Location>>, Disposable {
    private static final Logger LOG = Logger.getInstance(MyTextDocumentService.class);
    private final Position pos;

    public FindDefinitionCommand(Position pos) {
        this.pos = pos;
    }

    @Override
    public void dispose() {
        // Do nothing
    }

    @Override
    public List<? extends Location> apply(ExecutorContext ctx) {
        PsiFile file = MiscUtil.resolvePsiFile(ctx.getProject(), ctx.getLspPath());
        // TODO check Nullable case
        assert file != null;
        Document doc = MiscUtil.getDocument(file);
        assert doc != null;
        int offset = MiscUtil.positionToOffset(pos, doc);
        PsiReference ref = file.findReferenceAt(offset);
        assert ref != null;
        PsiElement elem = ref.resolve();

        // TODO Code from reference solution
        /*Document doc = MiscUtil.getDocument(ctx.getFile());
        if (doc == null) {
            LOG.error("No document found");
            throw new RuntimeException("No document found");
        }

//        Position pos = ctx.getPosition();
        int offset = doc.getLineStartOffset(pos.getLine()) + pos.getCharacter();

        List<? extends Location> list = findDefinitionByReference(ctx, offset);
        if (list == null) {
            LOG.error("Fail find definition for: " + ctx.getProject().getProjectFilePath());
            throw new RuntimeException("Can't find var location");
        }
        return list;*/
    }

    private List<? extends Location> findDefinitionByReference(ExecutorContext ctx, int offset) {
        List<Location> loc = new ArrayList<>();
        EditorUtil.withEditor(this, ctx.getFile(), offset, editor -> {
//            val targetElements = GotoDeclarationAction.findTargetElementsNoVS(ctx.getProject(), editor, offset, false);
            PsiElement[] targetElements = GotoDeclarationAction.findAllTargetElements(ctx.getProject(), editor, offset);
            Collection<Location> results = Arrays.stream(targetElements)
                    .map(this::sourceLocationIfPossible)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            loc.addAll(results);
        });
        return loc;
    }

    private Location sourceLocationIfPossible(PsiElement pe) {
        Document doc = MiscUtil.getDocument(pe.getContainingFile());
        String uri = LspPath.getURIForFile(pe.getContainingFile());
        Location location = MiscUtil.psiElementLocation(pe, uri, doc);
        return location;

        // TODO What this code do!?
        /*EditorWithProviderComposite editor = newEditorComposite(pe.getContainingFile().getVirtualFile(), pe.getProject());
        if (editor == null) {
            return location;
        }
        EditorFileSwapper[] swappers = Extensions.getExtensions(EditorFileSwapper.EP_NAME);
        Pair<VirtualFile, Integer> newFilePair = null;
        TextEditorImpl psiAwareEditor = EditorFileSwapper.findSinglePsiAwareEditor(editor.getEditors());
        if (psiAwareEditor == null) {
            return location;
        }
        assert doc != null; // TODO Check this case
        psiAwareEditor.getEditor().getCaretModel().moveToOffset(MiscUtil.positionToOffset(location.getRange().getStart(), doc));
        psiAwareEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);

        for(var each : swappers) {
            newFilePair = each.getFileToSwapTo(pe.getProject(), editor);
            // TODO Strange string: why continue?
            // if (newFilePair != null) return@forEach
            if (newFilePair != null) { break; }
        }

        if (newFilePair == null || newFilePair.first == null){
            return location;
        }

        LspPath lspPathForPsiFile = LspPath.fromLocalPath(Paths.get(newFilePair.first.getPath()));
        PsiFile sourcePsiFile = MiscUtil.resolvePsiFile(pe.getProject(), lspPathForPsiFile);
        if (sourcePsiFile == null) { return location; }
        Document sourceDoc = MiscUtil.getDocument(sourcePsiFile);
        if (sourceDoc == null) { return location; }
        location = getLocationBySwappedSourcePosition(newFilePair, sourceDoc);

        return location;*/
    }

    private Location getLocationBySwappedSourcePosition(Pair<VirtualFile, Integer> pair, Document sourceDoc) {
        VirtualFile file = pair.first;
        int offset = pair.second == null ? 0 : pair.second;
        return new Location(
                LspPath.getURIForFile(file),
                new Range(MiscUtil.offsetToPosition(sourceDoc, offset), MiscUtil.offsetToPosition(sourceDoc, offset)));
    }

    private EditorWithProviderComposite newEditorComposite(VirtualFile file, Project project) {
        if (file == null) {
            return null;
        }

        FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
        FileEditorProvider[] providers = editorProviderManager.getProviders(project, file);
        if (providers.length == 0) return null;
        FileEditor[] editors = new FileEditor[providers.length];
        Arrays.fill(editors, null);
        for (int i = 0; i < providers.length; ++i) {
            FileEditorProvider provider = providers[i];
            assert (provider.accept(project, file));
            FileEditor editor = provider.createEditor(project, file);
            editors[i] = editor;
            assert (editor.isValid());
        }

        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManagerEx.getInstance(project);
        EditorWithProviderComposite newComposite = newEditorCompositeInstance(file, editors, providers, fileEditorManager);
        EditorHistoryManager editorHistoryManager = EditorHistoryManager.getInstance(project);
        for (int i = 0; i < editors.length; ++i) {
            FileEditor editor = editors[i];
            FileEditorProvider provider = providers[i];

            // Restore myEditor state
            FileEditorState state = editorHistoryManager.getState(file, provider);
            if (state != null) {
                editor.setState(state);
            }
        }
        return newComposite;
    }

    private static Constructor<EditorWithProviderComposite> sConstructor;

    //TODO EditorWithProviderComposite is depricated
    private static EditorWithProviderComposite newEditorCompositeInstance(VirtualFile file,
                                                                          FileEditor[] editors,
                                                                          FileEditorProvider[] providers,
                                                                          FileEditorManagerEx fileEditorManager) {
        try {
            synchronized (sConstructor) {
                Constructor<EditorWithProviderComposite> cached = sConstructor;

                Constructor<EditorWithProviderComposite> ctor;
                if (cached == null) {
                    ctor = EditorWithProviderComposite.class.getDeclaredConstructor(
                            VirtualFile.class /* file */,
                            FileEditor[].class /* editors */,
                            FileEditorProvider[].class /* providers */,
                            FileEditorManagerEx.class /* fileEditorManager */
                    );
                    ctor.setAccessible(true);
                    sConstructor = ctor;
                } else {
                    ctor = cached;
                }

                return ctor.newInstance(file, editors, providers, fileEditorManager);
            }
        } catch (Throwable e) {
            LOG.error("Can't create EditorWithProviderComposite " + ExceptionUtils.getStackTrace(e));
        }

        return null;
    }
}

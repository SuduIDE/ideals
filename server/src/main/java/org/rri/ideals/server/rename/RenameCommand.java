package org.rri.ideals.server.rename;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.ideals.server.LspPath;
import org.rri.ideals.server.commands.ExecutorContext;
import org.rri.ideals.server.commands.LspCommand;
import org.rri.ideals.server.util.EditorUtil;
import org.rri.ideals.server.util.MiscUtil;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RenameCommand extends LspCommand<WorkspaceEdit> {

  private final Position pos;
  private final String newName;

  public RenameCommand(Position pos, String newName) {
    this.pos = pos;
    this.newName = newName;
  }

  @Override
  protected @NotNull Supplier<@NotNull String> getMessageSupplier() {
    return () -> "Rename call";
  }

  @Override
  protected boolean isCancellable() {
    return false;
  }

  @Override
  protected @Nullable WorkspaceEdit execute(@NotNull ExecutorContext ctx) {
    final var file = ctx.getPsiFile();
    Document doc = MiscUtil.getDocument(file);
    if (doc == null) {
      return null;
    }
    var elementRef = new Ref<PsiElement>();
    try {
      EditorUtil.withEditor(this, file, pos, editor -> {
        var elementToRename = TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().getAllAccepted());
        if (elementToRename != null) {
          final var processor = RenamePsiElementProcessor.forElement(elementToRename);
          final var newElementToRename = processor.substituteElementToRename(elementToRename, editor);
          if (newElementToRename != null) {
            elementToRename = newElementToRename;
          }
        }
        elementRef.set(elementToRename);
      });
    } finally {
      Disposer.dispose(this);
    }

    if (elementRef.get() == null) {
      return null;
    }

    final var elemToName = new LinkedHashMap<PsiElement, String>();
    elemToName.put(elementRef.get(), newName);
    final var renamer = new RenameProcessor(ctx.getProject(), elementRef.get(), newName, false, false);
    renamer.prepareRenaming(elementRef.get(), newName, elemToName);
    elemToName.forEach(renamer::addElement);

    final var usages = renamer.findUsages();
    final var usagesEdits = Arrays.stream(usages)
        .filter(usage -> !(usage instanceof NonCodeUsageInfo))
        .map(usageInfo -> new Pair<>(usageInfoToLocation(usageInfo), newName));

    final var elementsEdits = Arrays.stream(elemToName.keySet().toArray(new PsiElement[0]))
        .map(elem -> new Pair<>(MiscUtil.psiElementToLocation(elem), elemToName.get(elem)));

    final var checkSet = new HashSet<Location>();
    final var textDocumentEdits = Stream.concat(elementsEdits, usagesEdits)
        .filter(pair -> {
          final var loc = pair.getFirst();
          if (loc == null || checkSet.contains(loc)) {
            return false;
          }
          return checkSet.add(loc);
        })
        .collect(
            Collectors.groupingBy(
                pair -> pair.getFirst().getUri(),
                Collectors.mapping(pair -> new Pair<>(pair.getFirst().getRange(), pair.getSecond()), Collectors.toList())
            )
        )
        .entrySet().stream()
        .map(this::convertEntry)
        .toList();

    return new WorkspaceEdit(textDocumentEdits);
  }

  private static @Nullable Location usageInfoToLocation(@NotNull UsageInfo info) {
    final var psiFile = info.getFile();
    final var segment = info.getSegment();
    if (psiFile == null || segment == null) {
      return null;
    }
    final var uri = LspPath.fromVirtualFile(psiFile.getVirtualFile()).toLspUri();
    final var doc = MiscUtil.getDocument(psiFile);
    if (doc == null) {
      return null;
    }
    return new Location(uri, segmentToRange(doc, segment));
  }

  private static @NotNull Range segmentToRange(@NotNull Document doc, @NotNull Segment segment) {
    return new Range(MiscUtil.offsetToPosition(doc, segment.getStartOffset()),
        MiscUtil.offsetToPosition(doc, segment.getEndOffset()));
  }

  private @NotNull Either<@NotNull TextDocumentEdit, @NotNull ResourceOperation> convertEntry(
      @NotNull Map.Entry<@NotNull String, @NotNull List<@NotNull Pair<@NotNull Range, @NotNull String>>> entry) {
    return Either.forLeft(
        new TextDocumentEdit(
            new VersionedTextDocumentIdentifier(entry.getKey(), 1),
            entry.getValue().stream().map(pair -> new TextEdit(pair.getFirst(), pair.getSecond())).toList()
        ));
  }
}

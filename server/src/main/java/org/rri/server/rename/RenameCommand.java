package org.rri.server.rename;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameViewDescriptor;
import com.intellij.usageView.UsageInfo;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rri.server.LspPath;
import org.rri.server.commands.ExecutorContext;
import org.rri.server.commands.LspCommand;
import org.rri.server.util.EditorUtil;
import org.rri.server.util.MiscUtil;

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

    final var map = new LinkedHashMap<PsiElement, String>();
    map.put(elementRef.get(), newName);
    final var renamer = new RenameProcessor(ctx.getProject(), elementRef.get(), newName, false, false);
    renamer.prepareRenaming(elementRef.get(), newName, map);
    map.forEach(renamer::addElement);

    final var usages = renamer.findUsages();
    final var usagesLocationsStream = Arrays.stream(usages)
        .map(RenameCommand::usageInfoToLocation);

    final var descriptor = new RenameViewDescriptor(map);
    final var elementsLocationsStream = Arrays.stream(descriptor.getElements())
        .map(MiscUtil::psiElementToLocation);


    final var textDocumentEdits = Stream.concat(usagesLocationsStream, elementsLocationsStream)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.groupingBy(Location::getUri, Collectors.mapping(Location::getRange, Collectors.toList())))
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

  private @NotNull Either<TextDocumentEdit, ResourceOperation> convertEntry(Map.Entry<String, List<Range>> entry) {
    return Either.forLeft(
        new TextDocumentEdit(
            new VersionedTextDocumentIdentifier(entry.getKey(), 1),
            entry.getValue().stream().map(range -> new TextEdit(range, newName)).toList()
        ));
  }
}

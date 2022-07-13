package org.rri.server.definition;

import com.intellij.openapi.components.Service;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;

import java.util.List;

@Service(Service.Level.PROJECT)
final public class DefinitionService {
    public List<? extends Location> execute(DefinitionParams params) {
//        val doc = getDocument(ctx.file)
//                ?: throw LanguageServerException("No document found.")
//
//        val offset = position.toOffset(doc)
//
//        val list = findDefinitionByReference(ctx, offset)
//        if(list != null) {
//            return list
//        }
        return null;
    }

}

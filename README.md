<a name="readme-top"></a>

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#universal-lsp-server">Universal LSP server</a></li>
    <li><a href="#requirements">Requirements</a></li>
    <li><a href="#caveats">Caveats</a></li>
    <li><a href="#feature-list">Feature list</a></li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#installation">Installation</a></li>
    <li><a href="#contribute-with-us">Contribute with us</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>


# IdeaLS (IDEA Language Server)

An Intellij IDEA plugin that is intended to turn [IntelliJ IDEA](https://github.com/JetBrains/intellij-community) into an LSP server and deliver the power of IDEA's language support to LSP clients.

[LSP](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)
is a protocol that allows decoupling the editor and the language support logic,
so you can (theoretically) use any editor supporting LSP client functionality
(like Visual Studio Code, Sublime Text, Vim, Emacs, Eclipse, etc.) with any LSP server.

There are a lot of LSP servers with different feature sets for different languages.
This one differs from the others in that it doesn't define its own language logic
but rather translates LSP requests into IDEA API calls.
So it does what your IDEA does, with the languages your IDEA supports, but with your favorite editor.
If you are working on a multi-language project you don't need many language-specific LSP servers anymore.
Just install IntelliJ IDEA with appropriate set of plugins and turn it into one LSP server for all the languages you need. 

IdeaLS is designed to be as much language agnostic as possible.
However, for a better user experience some parts are still dependent on language-specific API. 

The project was heavily inspired by [intellij-lsp-server](https://github.com/Ruin0x11/intellij-lsp-server)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Requirements
- IntelliJ IDEA (Community or Ultimate) 2022.2 or higher

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Caveats
- The project is in a very early stage and really unstable.
- The server is tested with Visual Studio Code only.
- There are conflicts with the Android plugin on startup, so you have to disable it in the IDEA.
- There is A LOT of work yet to be done, please be indulgent.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Feature list

| Name                          | Method                               |                    | VSCode Action                                         |
|-------------------------------|--------------------------------------|--------------------|-------------------------------------------------------|
| Workspace Symbols             | `workspace/symbol`                   | :heavy_check_mark: | `Ctrl + T`                                            |
| Execute Command               | `workspace/executeCommand`           | :x:                |                                                       |
| Diagnostics                   | `textDocument/publishDiagnostics`    | :heavy_check_mark: | N/A                                                   |
| Completion                    | `textDocument/completion`            | :heavy_check_mark: | `Ctrl + Space`                                        |
| Hover                         | `textDocument/hover`                 | :x:                |                                                       |
| Signature Help                | `textDocument/signatureHelp`         | :x:                |                                                       |
| Goto Declaration              | `textDocument/declaration`           | :x:                |                                                       |
| Goto Definition               | `textDocument/definition`            | :heavy_check_mark: | `Ctrl + Left mouse click`                             |
| Goto Type Definition          | `textDocument/typeDefinition`        | :heavy_check_mark: | `Right mouse click -> Go to -> Type defintion`        |
| Goto Implementation           | `textDocument/implementation`        | :x:                |                                                       |
| Find References               | `textDocument/references`            | :heavy_check_mark: | `Right mouse click -> Go To -> References`            |
| Document Highlights           | `textDocument/documentHighlight`     | :heavy_check_mark: | `Left mouse click on symbol`                          |
| Document Symbols              | `textDocument/documentSymbol`        | :heavy_check_mark: | `Ctrl + O`                                            |
| Code Action                   | `textDocument/codeAction`            | :heavy_check_mark: | `Ctrl + .`                                            |
| Code Lens                     | `textDocument/codeLens`              | :x:                |                                                       |
| Document Formatting           | `textDocument/formatting`            | :heavy_check_mark: | `Ctrl + Shift + P -> Format Document`                 |
| Document Range Formatting     | `textDocument/rangeFormatting`       | :heavy_check_mark: | `Select text -> Ctrl + Shift + P -> Format Selection` |
| Document on Type Formatting   | `textDocument/onTypeFormatting`      | :heavy_check_mark: | N/A                                                   |
| Rename                        | `textDocument/rename`                | :heavy_check_mark: | N/A                                                   |
| Prepare Call Hierarchy        | `textDocument/prepareCallHierarchy`  | :x:                |                                                       |
| Call Hierarchy Incoming Calls | `callHierarchy/incomingCalls`        | :x:                |                                                       |
| Call Hierarchy Outgoing Calls | `callHierarchy/outgoingCalls`        | :x:                |                                                       |
| Prepare Type Hierarchy        | `textDocument/prepareTypeHierarchy’` | :x:                |                                                       |
| Type Hierarchy Supertypes     | `typeHierarchy/supertypes`           | :x:                |                                                       |
| Type Hierarchy Subtypes       | `typeHierarchy/subtypes`             | :x:                |                                                       |
| Document Link                 | `textDocument/documentLink`          | :x:                |                                                       |
| Folding Range                 | `textDocument/foldingRange`          | :x:                |                                                       |
| Selection Range               | `textDocument/selectionRange`        | :x:                |                                                       |
| Semantic Tokens               | `textDocument/semanticTokens`        | :x:                |                                                       |
| Inline Value                  | `textDocument/inlineValue`           | :x:                |                                                       |
| Inlay Hint                    | `textDocument/inlayHint`             | :x:                |                                                       |
| Monikers                      | `textDocument/moniker`               | :x:                |                                                       |
| Signature Help                | `textDocument/signatureHelp`         | :x:                |                                                       |
| Document Color                | `textDocument/documentColor`         | :x:                |                                                       |
| Linked Editing Range          | `textDocument/linkedEditingRange`    | :x:                |                                                       |

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Installation

### Trying it out
Autoconfiguration of IDEA project is not implemented yet,
So, before launching IDEA as a language server you have to start IDEA in normal mode
and configure your project manually (setup SDK, modules, dependencies, install required plugins etc.).
As a result the `.idea` directory should be present in the project root.

Run `runIde` gradle task to open a testing instance of IDEA.
After that you will need to run the client extension, that is described in *Running client* section  

### Installing plugin
1. Run `:clean :buildPlugin` gradle tasks to create the plugin distribution.
2. In IDEA, go to `File -> Plugins -> Install plugin from disk...` and select the `.zip` file that was output inside `build/distributions`.

### Running server
For running as language server IDEA must be configured to be executed in headless mode (no GUI).
Add line `-Djava.awt.headless=true` into `idea.vmoptions` (can be found inside the `bin` directory in the IDEA installation root).
(we're working on making this part less cumbersome)

Run `idea lsp-server` on Windows or `idea.sh lsp-server` on Unix.

Now server is working on 8989 port.

### Running client
You need to build vscode extension, that is placed in `client/vscode` folder.
[Extension building guide](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#packaging-extensions)

In vscode go to Extensions -> Install from VSIX... and choose built extension.

Note, that you need to enable extension before, not after the server has started.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contribute with us.

IdeaLS is an opensource product. We welcome everyone who wants to make it better.
If you want to contribute with us, write [@serganch](https://t.me/serganch) in Telegram.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contact

Sergey Anchipolevsky - [@serganch](https://t.me/serganch)

Project Link: [https://github.com/serganch/ideals](https://github.com/serganch/ideals)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

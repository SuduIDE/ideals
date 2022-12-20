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
- IntelliJ IDEA (Community or Ultimate) or IDEA Platform based IDE 2022.3 or higher

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Caveats
- The project is in a very early stage and really unstable.
- The server is tested with Visual Studio Code only.
- There are conflicts with the Android plugin on startup, so you have to disable it in the IDEA.
- There is A LOT of work yet to be done, please be indulgent.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Feature list

| Name                          | Method                               |                    | VSCode Action                                         |
| ----------------------------- | ------------------------------------ | ------------------ | ----------------------------------------------------- |
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

## Using IdeaLS

### Building and Installing Plugin
If you want to use the server plugin in your locally installed Idea:

1. Run `:clean :buildPlugin` gradle tasks to create the plugin distribution.
2. In IDEA, go to `File -> Plugins -> Install plugin from disk...` and select the `.zip` file that was output inside `build/distributions`.

### Preparing IDEA Project
Autoconfiguration of an IDEA project is not implemented yet.
So, before launching IDEA as a language server, you have to start IDEA in normal mode
and configure your project manually (setup SDK, modules, dependencies, install required plugins etc.).
As a result the `.idea` directory should be present in the project root.

### Running Server
To run a testing instance of IDEA with the development version of the plugin (as TCP server, port 8989) 
execute `runIde` gradle task from the project root.

Server can be executed in two modes: `STDIO` and `TCP`.

In `STDIO` mode the client starts the server as a child process,
and they communicate with each other through the standard input/output channels.

In `TCP` mode the server must be started before the client, which can communicate with the server through a TCP connection.
Note, that even in TCP mode both the client and the server must run on the same machine as they share the same file system.

If you're going to use IdeaLS in STDIO mode from VS Code, you can skip the rest of this section.

#### STDIO Mode
STDIO is the default mode for VS Code IdeaLS client. If you want to use IdeaLS with another LSP client,   
configure (or extend) it to start the IDEA using the command line like this:

`[<idea executable path>] lsp-server`.

#### TCP Mode
For TCP mode you should run IDEA before the client using command:

`[<idea executable path>] lsp-server tcp [<port number>]` 

where `<port number>` is the port to listen, 8989 by default.

#### Configuring IDEA for Headless Mode
When using IDEA as an LSP server it must be configured to be executed in headless mode (no GUI).
You need to add `-Djava.awt.headless=true` to a `*.vmoptions` file that your IDEA uses.
[More information about these files you can read here](https://www.jetbrains.com/help/idea/tuning-the-ide.html#configure-jvm-options).

However, if you're going to use our VS Code extension as the client in STDIO (see sections below) mode,
you don't have to manually configure IDEA for headless mode. It's done automatically.

### Building and Running Client
IdeaLS VS Code extension project is in `client/vscode` folder.

To run a development instance of the extension open that folder in VS Code
and run one of the `Run Extension *` launch configurations according to preferred development configuration.

To build and run a standalone extension first refer to the guides:

[Extension implementing guide](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide#lsp-sample-a-simple-language-server-for-plain-text-files)

[Extension building guide](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#packaging-extensions)

Once you have a `vsix` file, add it to your VS Code instance by `Extensions -> Install from VSIX...`.

To configure the VS Code extension:

1. Open user settings (ctrl + shift + p => Open User Settings)
2. Find extension configurations (Extensions => IdeaLS)
3. Select run mode (STDIO or TCP)
4. * Provide a path to the Idea **executable** (not script) if you selected STDIO on a previous step (etc. C:\Program Files\JetBrains\IDEA\bin\idea64.exe)
   * Type TCP port if you selected TCP on a previous step. 8989 is by default
5. Restart VS Code to apply the configuration.

Instead of extension configuration environment variables can also be used:

| Variable name    | Description             | Expected values                            |
| ---------------- |-------------------------|--------------------------------------------|
| IDEALS_TRANSPORT | Run mode (or transport) | TCP, STDIO                                 |
| IDEALS_TCP_PORT  | Port for TCP connection | Any available port number. 8989 if not set |
| IDEALS_IJ_PATH   | Idea executable path    | Path to the IDEA binary executable         |


<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contribute with us.

IdeaLS is an open-source product. We welcome everyone who wants to make it better.
If you want to contribute with us, write [@serganch](https://t.me/serganch) in Telegram.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contact

Sergey Anchipolevsky - [@serganch](https://t.me/serganch)

Project Link: [https://github.com/SuduIDE/ideals](https://github.com/SuduIDE/ideals)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

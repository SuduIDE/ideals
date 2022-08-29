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


# Universal LSP server

A plugin that launches the Intellij IDEA core like LSP server and processes requests from client using IDEA's opensource code.
In this way other editors can use IDEA's features.

[LSP](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/) is a protocol that allow split
language features in IDE on server and client part. So, you can use any client that support LSP if you have working LSP server.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Requirements
- IntelliJ IDEA 2022.1
   + Due to the way the plugin interacts with internal APIs, 
     there currently isn't support for other versions of IDEA.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Caveats
- Alpha-quality, and probably really unstable.
- Java, Kotlin and Python are currently fully supported.
- Editing in both IDEA and the LSP client at the same time isn't supported currently.
- The server should work across any LSP client, but some nonstandard features (like using IntelliJ to build and run projects) are only implemented in the Emacs client.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Feature list

| Name                        | Method                            |                    | VSCode call                                           |
|-----------------------------|-----------------------------------|--------------------|-------------------------------------------------------|
| Workspace Symbols           | `workspace/symbol`                | :heavy_check_mark: | `Ctrl + t`                                            |
| Execute Command             | `workspace/executeCommand`        | :x:                |                                                       |
| Diagnostics                 | `textDocument/publishDiagnostics` | :heavy_check_mark: | Nothing                                               |
| Completion                  | `textDocument/completion`         | :heavy_check_mark: | `Enter` on typing                                     |
| Hover                       | `textDocument/hover`              | :x:                |                                                       |
| Signature Help              | `textDocument/signatureHelp`      | :x:                |                                                       |
| Goto Definition             | `textDocument/definition`         | :heavy_check_mark: | `Ctrl + Left mouse click`                             |
| Goto Type Definition        | `textDocument/typeDefinition`     | :heavy_check_mark: | `Right mouse click -> Go to -> Type defintion`        |
| Goto Implementation         | `textDocument/implementation`     | :x:                |                                                       |
| Find References             | `textDocument/references`         | :heavy_check_mark: | `Right mouse click -> Go To -> References`            |
| Document Highlights         | `textDocument/documentHighlight`  | :heavy_check_mark: | Nothing                                               |
| Document Symbols            | `textDocument/documentSymbol`     | :heavy_check_mark: | `Ctrl + o`                                            |
| Code Action                 | `textDocument/codeAction`         | :heavy_check_mark: | `Ctrl + .`                                            |
| Code Lens                   | `textDocument/codeLens`           | :x:                |                                                       |
| Document Formatting         | `textDocument/formatting`         | :heavy_check_mark: | `Ctrl + Shift + p -> Format Document`                 |
| Document Range Formatting   | `textDocument/rangeFormatting`    | :heavy_check_mark: | Nothing                                               |
| Document on Type Formatting | `textDocument/onTypeFormatting`   | :heavy_check_mark: | `Select text -> Ctrl + Shift + p -> Format Selection` |
| Rename                      | `textDocument/rename`             | :x:                |                                                       |

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Installation

### Trying it out
By now we have start up bug, so you need to run `plainIde` gradle task, open plugin settings and turn off Android plugin.
Run `runIde` gradle task to open a testing instance of IDEA. After that you need to run client extension, that described inside *Running client* section  

### Installing the plugin
Run `:clean :buildPlugin` gradle tasks to create the plugin distribution. In IDEA, go to `File -> Plugins -> Install plugin from disk...` and select the `.zip` file that was output inside `build/distributions`.

Before trying server functionality, you need to open the project where you want to work in common idea. Specify SDK and plugins that you want to use.

### Running server
Find where `idea.sh` (on Linux) or `idea.bat` (on Windows) is placed. Near this script you can find `idea*.vmoptions`. Add -Djava.awt.headless=true into end of this file.

Run `idea.sh` or `idea.bat` with `lsp-server` argument. 
```
./idea.sh lsp-server
```
Now server is working on 8989 port.

### Running client
You need to build vscode extension, that is placed in `client/vscode` folder. [Extension building guide](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#packaging-extensions)

In vscode go to Extensions -> Install from VSIX... and choose built extension.

Remember, that by now you need to enable on extension just before, not after server running. If you installed extension before server run, you can always disable or enable it in extension settings

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contribute with us.

Universal LSP server is opensource product. We welcome everyone who wants to make UTBot Java better.
If you want to contribute with us, write @serganch in Telegram.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Contact

Sergey Anchipolevsky - [@serganch](https://t.me/serganch)

Project Link: [https://github.com/serganch/intellij-lsp](https://github.com/serganch/intellij-lsp)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

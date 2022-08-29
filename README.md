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


## Requirements
- IntelliJ IDEA 2022.1
   + Due to the way the plugin interacts with internal APIs, 
     there currently isn't support for other versions of IDEA.


## Caveats
- Alpha-quality, and probably really unstable.
- Java, Kotlin and Python are currently fully supported.
- Editing in both IDEA and the LSP client at the same time isn't supported currently.
- The server should work across any LSP client, but some nonstandard features (like using IntelliJ to build and run projects) are only implemented in the Emacs client.

## Feature list

| Name                        | Method                            |                    | VSCode call                                    |
|-----------------------------|-----------------------------------|--------------------|------------------------------------------------|
| Workspace Symbols           | `workspace/symbol`                | :heavy_check_mark: | `Ctrl + t`                                     |
| Execute Command             | `workspace/executeCommand`        | :x:                |                                                |
| Diagnostics                 | `textDocument/publishDiagnostics` | :heavy_check_mark: | Nothing                                        |
| Completion                  | `textDocument/completion`         | :heavy_check_mark: | `Enter` on typing                              |
| Hover                       | `textDocument/hover`              | :x:                |                                                |
| Signature Help              | `textDocument/signatureHelp`      | :x:                |                                                |
| Goto Definition             | `textDocument/definition`         | :heavy_check_mark: | `Ctrl + Left mouse click`                      |
| Goto Type Definition        | `textDocument/typeDefinition`     | :heavy_check_mark: | `Right mouse click -> Go to -> Type defintion` |
| Goto Implementation         | `textDocument/implementation`     | :x:                |                                                |
| Find References             | `textDocument/references`         | :heavy_check_mark: | `Right mouse click -> Go To -> References`     |
| Document Highlights         | `textDocument/documentHighlight`  | :heavy_check_mark: | Nothing                                        |
| Document Symbols            | `textDocument/documentSymbol`     | :heavy_check_mark: | `Ctrl + o`                                     |
| Code Action                 | `textDocument/codeAction`         | :heavy_check_mark: | `Ctrl + .`                                     |
| Code Lens                   | `textDocument/codeLens`           | :x:                |                                                |
| Document Formatting         | `textDocument/formatting`         | :heavy_check_mark: | `Ctrl + Shift + p -> Document Formatting`      |
| Document Range Formatting   | `textDocument/rangeFormatting`    | :heavy_check_mark: | Nothing                                        |
| Document on Type Formatting | `textDocument/onTypeFormatting`   | :heavy_check_mark: | Nothing                                        |
| Rename                      | `textDocument/rename`             | :x:                |                                                |


## Usage
The server will start automatically on TCP port 8080 when the IDE is loaded. 
You must configure the project SDK inside IDEA before connecting your client.

To use the server with Emacs/Spacemacs, see the [lsp-intellij](https://www.github.com/Ruin0x11/lsp-intellij) repository.

## Installation

## Contribute with us.

Universal LSP server is opensource product. We welcome everyone who wants to make UTBot Java better.
If you want to contribute with us, write @serganch in Telegram.

## Contact

Sergey Anchipolevsky - [@serganch](https://t.me/serganch)

Project Link: [https://github.com/serganch/intellij-lsp](https://github.com/serganch/intellij-lsp)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

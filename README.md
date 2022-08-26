# Universal LSP server

## What is Universal LSP server?

[LSP](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/) is a protocol that allow split
language features in IDE on server and client part. So, you can use any client that support LSP if you have working LSP server.

This project launches the Intellij IDEA core like LSP server and processes requests from client using IDEA's opensource code.
It allows processing requests in common way. If you want use new language, all that you need is install appropriate plugin in Intellij IDEA.
At this time you can use any editor that support LSP (VSCode, Vim, Sublime and others).

### Current features

1. Diagnostic.
2. Goto definition and type definition.
3. Find usages.
4. Completion.
5. Document structure.
6. Symbol search.

### Current editors

1. VSCode

## How easy it is to use?

1. Clone this project.
2. Run plainIde task.
3. Disable Android plugin in new IDEA window:

    File -> Settings -> Plugins -> Installed -> Android -> Disable.
4. Open project where you want to work, IDEA will generate .idea directory.
5. Close environment.
6. Run 'Run Plugin' task.
7. Open VSCode.
8. Run extension in VSCode (client/vscode).
9. Open your project.
10. Write code.

## Contribute with us.

Universal LSP server is opensource product. We welcome everyone who wants to make UTBot Java better.
If you want contribute with us, write @serganch in Telegram.

## Support

If you find bug in Universal LSP server, you can write issue on Github.
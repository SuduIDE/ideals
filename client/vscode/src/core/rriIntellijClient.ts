import path = require("path");
import * as vscode from "vscode";


import * as net from 'net';

import {
    Disposable,
  LanguageClientOptions,
  RevealOutputChannelOn,
} from "vscode-languageclient";

import {
  LanguageClient,
  ServerOptions,
  State,
  StreamInfo,
} from "vscode-languageclient/node";

const outputChannel = vscode.window.createOutputChannel("RRI IntelliJ LSP client");
const LS_LAUNCHER_MAIN: string = "RriIntellijLanguageServerLauncher";

export class RriIntellijClient {
  private languageClient?: LanguageClient;
  private context?: vscode.ExtensionContext;

  setContext(context: vscode.ExtensionContext) {
    this.context = context;
  }

  async init(): Promise<void> {
    try {
      //Server options. LS client will use these options to start the LS.
      let serverOptions: ServerOptions = getServerOptions();

      //creating the language client.
      let clientId = "rri-intellij-client";
      let clientName = "RRI IntelliJ LSP Client";
      let clientOptions: LanguageClientOptions = {
        documentSelector: [{ pattern: "**/*" /* scheme: "file", language: "java" */}],
        outputChannel: outputChannel,
        revealOutputChannelOn: RevealOutputChannelOn.Never,
      };
      this.languageClient = new LanguageClient(
        clientId,
        clientName,
        serverOptions,
        clientOptions
      );

      const disposeDidChange = this.languageClient.onDidChangeState(
        (stateChangeEvent) => {
          if (stateChangeEvent.newState === State.Stopped) {
            vscode.window.showErrorMessage(
              "Failed to initialize the extension"
            );
          } else if (stateChangeEvent.newState === State.Running) {
            vscode.window.showInformationMessage(
              "Extension initialized successfully!"
            );
          }
        }
      );

      this.languageClient.start().then(()=>{ disposeDidChange.dispose(); });

    } catch (exception) {
      return Promise.reject("Extension error!");
    }
  }
}

//Create a command to be run to start the LS java process.
function getServerOptions() {
  //Change the project home accordingly.
  // const PROJECT_HOME = "d:/projects";
//  const LS_LIB = "lsp-tutorial/ballerina-language-server/language_server_lib/*";
//  const LS_HOME = path.join(PROJECT_HOME, LS_LIB);



// const JAR_PATH = "D:/projects/pythonlsp/out/artifacts/pythonlsp_jar/pythonlsp.jar";
// const JAVA_HOME = "C:/Users/aWX1180413/.jdks/corretto-18.0.1";

//   let executable: string = path.join(JAVA_HOME, "bin", "java");
//  // let args: string[] = ["-cp", LS_HOME];
//  let args: string[] = ["-jar", JAR_PATH];

//   let serverOptions: ServerOptions = {
//     command: executable,
//     args: [...args, LS_LAUNCHER_MAIN],
//     options: {},
//   };
//   return serverOptions;

    // The server is a started as a separate app and listens on port 5007
    let connectionInfo = {
        port: 8989
    };
    let serverOptions = () => {
        // Connect to language server via socket

        try {
            let socket = net.connect(connectionInfo);
            let result: StreamInfo = {
                writer: socket,
                reader: socket
            };
            return Promise.resolve(result);
                
        } catch(exception) {
            console.log("failed to connect: " + exception);
            throw exception;
        }
    };

    return serverOptions;
}

export const lspClient = new RriIntellijClient();
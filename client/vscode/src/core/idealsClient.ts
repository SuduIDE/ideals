import * as vscode from "vscode";
import * as net from 'net';

import {integer, LanguageClientOptions, RevealOutputChannelOn,} from "vscode-languageclient";

import {LanguageClient, ServerOptions, State, StreamInfo,} from "vscode-languageclient/node";
import path = require("path");
import fs = require("fs");
import os = require('node:os');

const outputChannel = vscode.window.createOutputChannel("IdeaLS Client");

export class IdealsClient {
  private languageClient?: LanguageClient;
  private context?: vscode.ExtensionContext;

  setContext(context: vscode.ExtensionContext) {
    this.context = context;
  }

  async init(): Promise<void> {
    try {
      //Server options. LS client will use these options to start the LS.
      let ideaLSInitOptions = this.getIdealsInitOptions();
      //creating the language client.
      let clientId = "ideals-client";
      let clientName = "IdeaLS Client";
      let clientOptions: LanguageClientOptions = {
        documentSelector: [{ pattern: "**/*" /* scheme: "file", language: "java" */ }],
        outputChannel: outputChannel,
        revealOutputChannelOn: RevealOutputChannelOn.Never,
      };
      this.languageClient = new LanguageClient(
        clientId,
        clientName,
        ideaLSInitOptions.serverOptions,
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
      const pathToVmoptions = ideaLSInitOptions.pathToVmoptions;
      this.languageClient.start().then(() => {
        if (pathToVmoptions) {
          fs.unlink(path.normalize(pathToVmoptions), (err) => {
            if (err) throw err;
          });
        }
        disposeDidChange.dispose();
      });

    } catch (exception) {
      return Promise.reject("Extension error: " + exception);
    }
  }

  //Create a command to be run to start the LS java process.
  getIdealsInitOptions() : IdealsInitOptions {
    let configuredTransport: String =
      vscode.workspace.getConfiguration('ideals').get('startup.transport') || process.env.IDEALS_TRANSPORT || "STDIO";
  if (configuredTransport.toUpperCase() === "TCP") {
      // Connect to language server via socket
      let configuredPort: String | integer = vscode.workspace.getConfiguration('ideals').get('startup.port') || process.env.IDEALS_TCP_PORT || 8989;

      let connectionInfo = {
        port: +configuredPort
      };

      return new IdealsInitOptions( () => {
        try {
          let socket = net.connect(connectionInfo);
          let result: StreamInfo = {
            writer: socket,
            reader: socket
          };
          return Promise.resolve(result);

        } catch (exception) {
          console.log("failed to connect: " + exception);
          throw exception;
        }
      });
    }

    let ideaExecutablePath: string | undefined =
      vscode.workspace.getConfiguration('ideals').get('startup.ideaExecutablePath') || process.env.IDEALS_IJ_PATH;

    if (!ideaExecutablePath) {
      throw new Error("Path to IntelliJ IDEA executable must be specified in extension configuration or in environment variable IDEALS_IJ_PATH");
    }

    const ideaVersionDir = path.normalize(path.dirname(path.dirname(ideaExecutablePath)));
    const ideaVersion = path.basename(ideaVersionDir);
    const dirWithVmOptions = path.dirname(ideaVersionDir);
    let vmoptionsPath = path.join(dirWithVmOptions, path.basename(ideaVersion) + ".vmoptions");

    if (!fs.existsSync(vmoptionsPath)) {
      vmoptionsPath = ideaExecutablePath + ".vmoptions";
    }

    let content = fs.readFileSync(vmoptionsPath).toString();
    content += "\n-Djava.awt.headless=true";

    const tmpdir = os.tmpdir();
    const pathToTempVmoptionsFile =
        path.join(tmpdir, String(process.pid) + Math.random().toString().substring(2, 8) + ".vmoptions");
    fs.writeFileSync(pathToTempVmoptionsFile, content);

    let serverOptions: ServerOptions = {
      command: ideaExecutablePath,
      args: ["lsp-server"],
      options: {
        env: {
          /* we want to support our plugin in all IDEA based IDEs, so if a new IDE published,
           please add assignment to the required ENV VAR */
          IDEA_VM_OPTIONS: pathToTempVmoptionsFile,
          PYCHARM_VM_OPTIONS: pathToTempVmoptionsFile,
          PHPSTORM_VM_OPTIONS: pathToTempVmoptionsFile,
          WEBIDE_VM_OPTIONS: pathToTempVmoptionsFile,
          CLION_VM_OPTIONS: pathToTempVmoptionsFile,
          CLION64_VM_OPTIONS: pathToTempVmoptionsFile,
          DATAGRIP_VM_OPTIONS: pathToTempVmoptionsFile,
          RIDER_VM_OPTIONS: pathToTempVmoptionsFile,
          GOLAND_VM_OPTIONS: pathToTempVmoptionsFile,
          RUBYMINE_VM_OPTIONS: pathToTempVmoptionsFile,
        }
      },
    };
    const ans = new IdealsInitOptions(serverOptions);
    ans.pathToVmoptions = pathToTempVmoptionsFile;
    return ans;
  }
}

export const lspClient = new IdealsClient();

export class IdealsInitOptions {
  private readonly _serverOptions : ServerOptions;
  private _pathToVmoptions ?: string;
  constructor(newServerOptions : ServerOptions) {
    this._serverOptions = newServerOptions;
  }


  set pathToVmoptions(value: string | undefined) {
    this._pathToVmoptions = value;
  }

  get pathToVmoptions(): string | undefined {
    return this._pathToVmoptions;
  }

  get serverOptions(): ServerOptions {
    return this._serverOptions;
  }
}
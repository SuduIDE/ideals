// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import {JAR_CONTENT_SCHEME, ZIP_CONTENT_SCHEME} from './jar-content-provider/Jar';
import JarDocumentContentProvider from './jar-content-provider/JarDocumentContentProvider';

import {lspClient} from './core/idealsClient';

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {
	const provider = new JarDocumentContentProvider(context);
	const jarRegistration = vscode.workspace.registerTextDocumentContentProvider(JAR_CONTENT_SCHEME, provider);
	const zipRegistration = vscode.workspace.registerTextDocumentContentProvider(ZIP_CONTENT_SCHEME, provider);

	context.subscriptions.push(jarRegistration);
	context.subscriptions.push(zipRegistration);

	//Set the context of the extension instance
	lspClient.setContext(context);
	//Initialize the LS Client extension instance.
	lspClient.init().catch((error)=> {
		console.log("Failed to activate RRI IdeaLS extension. " + (error));
	});


	// Use the console to output diagnostic information (console.log) and errors (console.error)
	// This line of code will only be executed once when your extension is activated
	console.log('Congratulations, your extension "rri.ideals" is now active!');

	/*
	// The command has been defined in the package.json file
	// Now provide the implementation of the command with registerCommand
	// The commandId parameter must match the command field in package.json
	let disposable = vscode.commands.registerCommand('rri.intellij.helloWorld', () => {
		// The code you place here will be executed every time your command is executed
		// Display a message box to the user
		vscode.window.showInformationMessage('Hello World from RRI IntelliJ!');
	});
	context.subscriptions.push(disposable);
	*/
}

// this method is called when your extension is deactivated
export function deactivate() { }

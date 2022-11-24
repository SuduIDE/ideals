import * as vscode from 'vscode';
import JarDocument from './JarDocument';

export default class JarDocumentContentProvider implements vscode.TextDocumentContentProvider {

	private readonly openDocumentURIs = new Set<vscode.Uri>();
	private readonly subscriptions = new Array<vscode.Disposable>();
	
	public constructor(_context: vscode.ExtensionContext) {
		this.subscriptions.push(vscode.workspace.onDidCloseTextDocument(document => {
			this.openDocumentURIs.delete(document.uri);
		}));
	}


	dispose() {
		this.openDocumentURIs.clear();
		this.subscriptions.forEach(subscription => subscription.dispose);
		this.subscriptions.length = 0;
	}

	async provideTextDocumentContent(uri: vscode.Uri): Promise<string|undefined> {
		this.openDocumentURIs.add(uri);
		return JarDocument.readContent(uri);
	}
}

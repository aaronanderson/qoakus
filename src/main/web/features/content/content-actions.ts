import { Router } from '@vaadin/router';

import { qoakusDB } from '../../app/store';

export const VIEW_CONTENT = 'VIEW_CONTENT'
export const VIEW_CONTENT_SUCCESS = 'VIEW_CONTENT_SUCCESS'
export const VIEW_CONTENT_ERROR = 'VIEW_CONTENT_ERROR'

export const MAIN_CONTENT = 'MAIN_CONTENT'
export const MAIN_CONTENT_SUCCESS = 'MAIN_CONTENT_SUCCESS'
export const MAIN_CONTENT_ERROR = 'MAIN_CONTENT_ERROR'


export interface ContentState {
	loading: boolean;
	errorMessage?: string;
	action?: string;

	path: string;
	contentDetails?: ContentDetails;

}

export const viewContent: any = (path: string) => async (dispatch: any) => {
	console.log("viewContent", path);
	dispatch({ type: VIEW_CONTENT });


	try {
		const contentResponse = await fetch(`/api/content/view${path}`, {
			method: 'GET',
			headers: {
				'Accept': 'application/json',
			},
		});
		const viewResult: ViewResult = await contentResponse.json();
		if (viewResult.status == "error") {
			throw Error(viewResult.message);
		}
		let contentDetails: any = Object.assign({}, viewResult);
		delete contentDetails.status;
		delete contentDetails.message;

		dispatch({ type: VIEW_CONTENT_SUCCESS, payload: { contentDetails: contentDetails } });
		dispatch(fetchMainContent(contentDetails));
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: VIEW_CONTENT_ERROR, payload: { error: error } })
	}
}


export const fetchMainContent: any = (contentDetails: ContentDetails) => async (dispatch: any) => {

	dispatch({ type: MAIN_CONTENT });


	try {
		let content = undefined;
		const mainFile = contentDetails.files.find((f: ContentFile) => f.fileType == "main");
		if (mainFile) {
			const path = contentDetails.path == "/" ? "" : contentDetails.path;
			console.log("fetchMainContent - before", contentDetails, path, mainFile.name);
			const fileResponse = await fetch(`/api/content/file${path}/${mainFile.name}`, {
				method: 'GET',
			});
			const fileBlob: Blob = await fileResponse.blob();
			content = new File([fileBlob], mainFile.name, { type: mainFile.mimeType, lastModified: mainFile.lastModified ? new Date(mainFile.lastModified).getTime() : undefined });
			console.log("fetchMainContent", content);
		}

		dispatch({ type: MAIN_CONTENT_SUCCESS, payload: { path: contentDetails.path, content: content } });
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: MAIN_CONTENT_ERROR, payload: { error: error } })
	}
}

export const readFile = (file: File): Promise<string> => {
	const fileReader = new FileReader();

	return new Promise((resolve, reject) => {
		fileReader.onerror = () => {
			fileReader.abort();
			reject(new DOMException("Problem parsing input file."));
		};

		fileReader.onload = () => {
			resolve(fileReader.result as string);
		};
		fileReader.readAsText(file);
	});
};


export interface ViewResult {
	status: string;
	message?: string;


}


export interface Content {
	path: string;
	title: string;
}

export interface ContentFile {
	fileType?: string;
	name: string;
	mimeType: string;
	lastModified?: string;
}


export interface ContentDetails extends Content {
	parent?: Content;
	files: Array<ContentFile>;
	children: Array<Content>;
	mainContent?: File;
}


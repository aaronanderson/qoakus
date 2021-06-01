import { Router } from '@vaadin/router';

import { qoakusDB } from '../../app/store';

export const VIEW_CONTENT = 'VIEW_CONTENT'
export const VIEW_CONTENT_SUCCESS = 'VIEW_CONTENT_SUCCESS'
export const VIEW_CONTENT_ERROR = 'VIEW_CONTENT_ERROR'


export interface ContentState {
	loading: boolean;
	errorMessage?: string;
	action?: string;

	path: string;
	parent?: Content;
	details?: ContentDetails;
	children?: Array<Content>;

}

export const viewContent: any = (path: string) => async (dispatch: any) => {
	console.log("viewContent", path);
	dispatch({ type: VIEW_CONTENT });


	try {
		const response = await fetch(`/api/content/view${path}`, {
			method: 'GET',
			headers: {
				'Accept': 'application/json',
			},
		});
		const viewResult: ViewResult = await response.json();
		if (viewResult.status == "error") {
			throw Error(viewResult.message);
		}
		let payload: any = Object.assign({}, viewResult);
		delete payload.status;
		delete payload.message;

		dispatch({ type: VIEW_CONTENT_SUCCESS, payload: payload })
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: VIEW_CONTENT_ERROR, payload: { error: error } })
	}
}


export interface ViewResult {
	status: string;
	message?: string;

	parent?: Content;
	details: ContentDetails;
	children?: Array<Content>;
}


export interface Content {
	path: string;
	title: string;
}


export interface ContentDetails extends Content {

}


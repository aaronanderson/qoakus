import { Router } from '@vaadin/router';

import { qoakusDB } from '../../app/store';

import marked from 'marked';
export const FETCH_USER = 'FETCH_USER'
export const FETCH_USER_SUCCESS = 'FETCH_USER_SUCCESS'
export const FETCH_USER_ERROR = 'FETCH_USER_ERROR'


export const VIEW_CONTENT = 'VIEW_CONTENT'
export const VIEW_CONTENT_SUCCESS = 'VIEW_CONTENT_SUCCESS'
export const VIEW_CONTENT_ERROR = 'VIEW_CONTENT_ERROR'

export const NEW_CONTENT = 'NEW_CONTENT'

export const EDIT_CONTENT = 'EDIT_CONTENT'

export const SAVE_CONTENT = 'SAVE_CONTENT'
export const SAVE_CONTENT_SUCCESS = 'SAVE_CONTENT_SUCCESS'
export const SAVE_CONTENT_ERROR = 'SAVE_CONTENT_ERROR'

export const FILE_UPLOAD = 'FILE_UPLOAD'
export const FILE_UPLOAD_SUCCESS = 'FILE_UPLOAD_SUCCESS'
export const FILE_UPLOAD_ERROR = 'FILE_UPLOAD_ERROR'

export const FILE_DELETE = 'FILE_DELETE'
export const FILE_DELETE_SUCCESS = 'FILE_DELETE_SUCCESS'
export const FILE_DELETE_ERROR = 'FILE_DELETE_ERROR'


export const DELETE_CONTENT = 'DELETE_CONTENT'
export const DELETE_CONTENT_SUCCESS = 'DELETE_CONTENT_SUCCESS'
export const DELETE_CONTENT_ERROR = 'DELETE_CONTENT_ERROR'


export const MAIN_CONTENT = 'MAIN_CONTENT'
export const MAIN_CONTENT_SUCCESS = 'MAIN_CONTENT_SUCCESS'
export const MAIN_CONTENT_ERROR = 'MAIN_CONTENT_ERROR'


export interface ContentState {
	loading: boolean;
	errorMessage?: string;
	action?: string;

	path: string;
	contentDetails?: ContentDetails;
	editMode?: EditMode;

	user?: User;


}


export const fetchUser: any = () => async (dispatch: any, getState: any) => {
	//cache the user
	const existingUser = getState().content?.user;
	if (!existingUser) {
		dispatch({ type: FETCH_USER });

		try {
			const contentResponse = await fetch(`/api/user`, {
				method: 'GET',
				headers: {
					'Accept': 'application/json',
				},
			});
			const userResult: UserResult = await contentResponse.json();
			if (userResult.status == "error") {
				throw Error(userResult.message);
			}

			dispatch({ type: FETCH_USER_SUCCESS, payload: { user: userResult.user } });
		} catch (error) {
			console.error('Error:', error);
			dispatch({ type: FETCH_USER_ERROR, payload: { error: error } })
		}

	}
}


export const viewContent: any = (path: string) => async (dispatch: any) => {
	console.log("viewContent", path);
	dispatch({ type: VIEW_CONTENT });

	try {
		path = path == "/" ? "" : path;
		const contentResponse = await fetch(`/api/content/${path}`, {
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

export const newContent: any = (parent: Content) => async (dispatch: any) => {
	console.log("newContent", parent);
	const newContentDetails = <ContentDetails>{
		title: "",
		path: "",
		parent: <Content>{ path: parent.path, title: parent.title },
		files: [],
		children: [],
	}
	dispatch({ type: NEW_CONTENT, payload: { contentDetails: newContentDetails, editMode: EditMode.add } });
	Router.go("/edit");

}


export const editContent: any = () => async (dispatch: any) => {
	dispatch({ type: EDIT_CONTENT, payload: { editMode: EditMode.update } });
	Router.go("/edit");

}


export const saveContent: any = (details: ContentDetails) => async (dispatch: any, getState: any) => {
	console.log("saveContent", details);
	dispatch({ type: SAVE_CONTENT });

	try {
		const { editMode } = getState().content;
		let path = undefined;
		let filePath = undefined;
		let method = undefined;
		let update = true;
		if (editMode == EditMode.add) {
			path = details.parent?.path;
			method = "POST";
		} else if (editMode == EditMode.update) {
			path = details.path;
			filePath = details.path;
			update = path != "/";
			method = "PUT";
		} else {
			throw Error("Unsupported edit mode " + editMode);
		}
		if (update) {
			const saveRequest = <SaveRequest>{
				title: details.title
			};
			const contentResponse = await fetch(`/api/content${path}`, {
				method: method,
				headers: {
					'Accept': 'application/json',
					'Content-Type': 'application/json'
				},
				body: JSON.stringify(saveRequest)
			});
			const saveResult: SaveResult = await contentResponse.json();
			if (saveResult.status == "error") {
				throw Error(saveResult.message);
			}
			if (editMode == EditMode.add) {
				filePath = saveResult.newPath;
			}
		}

		await dispatch(fileUpload("main", filePath, [details.mainContent]));

		dispatch({ type: SAVE_CONTENT_SUCCESS, payload: { contentDetails: details } });
		//dispatch(fetchMainContent(contentDetails));
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: SAVE_CONTENT_ERROR, payload: { error: error } })
	}
}

export const deleteContent: any = (path: string) => async (dispatch: any) => {
	console.log("deleteContent", path);
	dispatch({ type: DELETE_CONTENT });


	try {
		const contentResponse = await fetch(`/api/content${path}`, {
			method: 'DELETE',
			headers: {
				'Accept': 'application/json',
			},
		});
		const deleteResult: DeleteResult = await contentResponse.json();
		if (deleteResult.status == "error") {
			throw Error(deleteResult.message);
		}
		dispatch({ type: DELETE_CONTENT_SUCCESS, payload: {} });
		let len = path.lastIndexOf("/");
		let redirect = len > 0 ? path.substring(0, len) : "/";
		Router.go(redirect);

	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: DELETE_CONTENT_ERROR, payload: { error: error } })
	}
}


export const fileUpload: any = (fileType: string, path: string, files: Array<File>) => async (dispatch: any, getState: any) => {
	console.log("fileUpload", path, files);
	dispatch({ type: FILE_UPLOAD });
	path = path == "/" ? "" : path;
	try {
		//let details = new Blob([JSON.stringify(request)], { type: 'application/json' });
		const formData = new FormData();
		//formData.append('details', details, 'details.json');
		for (let i = 0; i < files.length; i++) {
			formData.append(`file${i + 1}`, files[i]);
		}

		const fileResponse = await fetch(`/api/content/file${path}?type=${fileType}`, {
			method: "POST",
			headers: {
				'Accept': 'application/json',
			},
			body: formData
		});
		const fileResult: FileUploadResult = await fileResponse.json();
		if (fileResult.status == "error") {
			throw Error(fileResult.message);
		}

		const contentFiles = files.map((f: File) => <ContentFile>{
			fileType: fileType,
			name: f.name,
			mimeType: f.type,
			lastModified: new Date(f.lastModified).toISOString()

		});
		dispatch({ type: FILE_UPLOAD_SUCCESS, payload: { files: contentFiles } });
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: FILE_UPLOAD_ERROR, payload: { error: error } })
	}
}

export const imageUpload: any = (file: File, onSuccess: Function, onError: Function, path: string) => async (dispatch: any, getState: any) => {
	dispatch({ type: FILE_UPLOAD });
	try {
		path = path == "/" ? "" : path;
		const formData = new FormData();
		formData.append(`file1`, file);

		if (!file.type.startsWith("image")) {
			throw Error(`unsupported image format ${file.type}`);
		}


		const fileResponse = await fetch(`/api/content/file/marked/${path}`, {
			method: "POST",
			headers: {
				'Accept': 'application/json',
			},
			body: formData
		});
		const fileResult: ImageFileUploadResult = await fileResponse.json();
		if (fileResult.status == "error") {
			throw Error(fileResult.message);
		}
		const fileName = fileResult.fileName;
		const contentFile = <ContentFile>{
			fileType: "image",
			name: fileName,
			mimeType: file.type,
			lastModified: new Date(file.lastModified).toISOString()

		};
		dispatch({ type: FILE_UPLOAD_SUCCESS, payload: { files: [contentFile] } });
		onSuccess.call(this, fileName);
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: FILE_UPLOAD_ERROR, payload: { error: error } })
		onError.call(this, error);
	}
}


export const fileDelete: any = (path: string, fileName: string) => async (dispatch: any, getState: any) => {
	console.log("fileDelete", path, fileName);
	dispatch({ type: FILE_DELETE });
	path = path == "/" ? "" : path;
	try {
		const fileResponse = await fetch(`/api/content/file${path}/${fileName}`, {
			method: "DELETE",
			headers: {
				'Accept': 'application/json',
			},
		});
		const fileResult: FileDeleteResult = await fileResponse.json();
		if (fileResult.status == "error") {
			throw Error(fileResult.message);
		}
		dispatch({ type: FILE_DELETE_SUCCESS, payload: { fileName: fileName } });
	} catch (error) {
		console.error('Error:', error);
		dispatch({ type: FILE_DELETE_ERROR, payload: { error: error } })
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


export const markedRenderer = (basePath?: string) => {
	const renderer = new marked.Renderer();
	let baseUrl = "";
	if (basePath) {
		baseUrl = `/api/content/file${basePath}/`;
	}


	const originalRendererLink = renderer.link.bind(renderer);
	const originalRendererImage = renderer.image.bind(renderer);
	const regularExpressionForURL = /^https?:\/\//i;
	renderer.link = (href: string, title: string, text: string) => {
		if (href && !regularExpressionForURL.test(href)) {
			href = baseUrl + href;
		}
		return `<a href="${href}" target="_blank">${text}</a>`;
		//return originalRendererLink(href, title, text);
	};

	renderer.image = (href: string, title: string, text: string) => {
		console.log("marked image", href, title, text);
		if (href && !regularExpressionForURL.test(href)) {
			href = baseUrl + href;
			//https://github.com/markedjs/marked/issues/339#issuecomment-479347433
			const exec = /=\s*(\d*)\s*x\s*(\d*)\s*$/.exec(title);
			const sanitize = (str: string) => {
				return str.replace(/&<"/g, (m) => {
					if (m === "&") return "&amp;"
					if (m === "<") return "&lt;"
					return "&quot;"
				})
			};
			let res = '<img src="' + sanitize(href) + '" alt="' + sanitize(text)
			if (exec && exec[1]) res += '" height="' + exec[1]
			if (exec && exec[2]) res += '" width="' + exec[2]
			return res + '">'
		}
		return originalRendererImage(href, title, text);
	};

	return renderer;
}

export interface UserResult {
	status: string;
	message?: string;

	user?: User;
}

export interface User {
	userId: string;
	principal: string;
	path: string;
	name: string;
	email: string;

}


export interface ViewResult {
	status: string;
	message?: string;
}

export interface SaveRequest {
	title: string;
}

export interface SaveResult {
	status: string;
	message?: string;
	newPath: string;
}

export interface FileUploadResult {
	status: string;
	message?: string;
}

export interface ImageFileUploadResult {
	status: string;
	message?: string;
	fileName: string;
}

export interface FileDeleteResult {
	status: string;
	message?: string;
}

export interface DeleteResult {
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

export enum EditMode {
	add = 'ADD',
	update = 'UPDATE',
}


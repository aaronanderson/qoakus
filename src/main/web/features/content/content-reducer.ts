import {
	ContentState, ContentFile,
	VIEW_CONTENT, VIEW_CONTENT_SUCCESS, VIEW_CONTENT_ERROR,
	NEW_CONTENT, EDIT_CONTENT,
	FILE_UPLOAD, FILE_UPLOAD_SUCCESS, FILE_UPLOAD_ERROR,
	FILE_DELETE, FILE_DELETE_SUCCESS, FILE_DELETE_ERROR,
	SAVE_CONTENT, SAVE_CONTENT_SUCCESS, SAVE_CONTENT_ERROR,
	MAIN_CONTENT, MAIN_CONTENT_SUCCESS, MAIN_CONTENT_ERROR,
} from './content-actions'

import produce from 'immer'


const initialState: ContentState = {
	loading: false,
	path: "/"


}

const editor = (state: ContentState = initialState, action: any) => {
	return produce(state, (draft: ContentState) => {

		switch (action.type) {
			case VIEW_CONTENT: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}
			case VIEW_CONTENT_SUCCESS: {
				draft.errorMessage = undefined;
				draft.contentDetails = action.payload.contentDetails;
				draft.loading = false;
				break
			}
			case VIEW_CONTENT_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.loading = false;
				break
			}

			case NEW_CONTENT: {
				draft.contentDetails = action.payload.contentDetails;
				draft.editMode = action.payload.editMode;
				break
			}

			case EDIT_CONTENT: {
				draft.editMode = action.payload.editMode;
				break
			}
			case MAIN_CONTENT: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}

			case SAVE_CONTENT: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}
			case SAVE_CONTENT_SUCCESS: {
				draft.errorMessage = undefined;
				draft.contentDetails = action.payload.contentDetails;
				draft.loading = false;
				break
			}
			case SAVE_CONTENT_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.loading = false;
				break
			}

			case FILE_UPLOAD: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}
			case FILE_UPLOAD_SUCCESS: {
				draft.errorMessage = undefined;
				if (draft.contentDetails && action.payload.files) {
					draft.contentDetails.files = draft.contentDetails.files.concat(action.payload.files);
				}
				draft.loading = false;
				break
			}
			case FILE_UPLOAD_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.loading = false;
				break
			}

			case FILE_DELETE: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}
			case FILE_DELETE_SUCCESS: {
				draft.errorMessage = undefined;
				if (draft.contentDetails && action.payload.fileName) {
					draft.contentDetails.files = draft.contentDetails.files.filter((f: ContentFile) => f.name != action.payload.fileName);
				}
				draft.loading = false;
				break
			}
			case FILE_DELETE_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.loading = false;
				break
			}


			case MAIN_CONTENT_SUCCESS: {
				draft.errorMessage = undefined;
				if (draft.contentDetails) {
					if (draft.contentDetails.path == action.payload.path) {
						draft.contentDetails.mainContent = action.payload.content;
					}
				}
				draft.loading = false;
				break
			}
			case MAIN_CONTENT_ERROR: {
				draft.errorMessage = action.payload.error;
				draft.loading = false;
				break
			}
			default:
				return draft
		}
	});
}



export default editor
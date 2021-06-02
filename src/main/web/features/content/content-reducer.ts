import {
	ContentState,
	VIEW_CONTENT, VIEW_CONTENT_SUCCESS, VIEW_CONTENT_ERROR,
	NEW_CONTENT, EDIT_CONTENT,
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
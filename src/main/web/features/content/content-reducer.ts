import {
	ContentState,
	VIEW_CONTENT, VIEW_CONTENT_SUCCESS, VIEW_CONTENT_ERROR,
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
				draft.parent = action.payload.parent;
				draft.details = action.payload.details;
				draft.children = action.payload.children;
				draft.loading = false;
				break
			}
			case VIEW_CONTENT_ERROR: {
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
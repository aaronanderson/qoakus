import {
	ContentState,
	FETCH_SCRIPTS, FETCH_SCRIPTS_SUCCESS, FETCH_SCRIPTS_ERROR,
} from './content-actions'

import produce from 'immer'


const initialState: ContentState = {
	loading: false,


}

const editor = (state: ContentState = initialState, action: any) => {
	return produce(state, (draft: ContentState) => {

		switch (action.type) {
			case FETCH_SCRIPTS: {
				draft.errorMessage = undefined;
				draft.loading = true;
				break
			}
			case FETCH_SCRIPTS_SUCCESS: {
				draft.errorMessage = undefined;
				draft.loading = false;
				break
			}
			case FETCH_SCRIPTS_ERROR: {
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
import { Router } from '@vaadin/router';

import { qoakusDB } from '../../app/store';

export const FETCH_SCRIPTS = 'FETCH_SCRIPTS'
export const FETCH_SCRIPTS_SUCCESS = 'FETCH_SCRIPTS_SUCCESS'
export const FETCH_SCRIPTS_ERROR = 'FETCH_SCRIPTS_ERROR'


export interface ContentState {
	loading: boolean;
	errorMessage?: string;
	action?: string;

}



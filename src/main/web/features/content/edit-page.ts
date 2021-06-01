import { LitElement, CSSResult, html, css } from 'lit';
import { property, customElement } from 'lit/decorators.js';

import { ViewElement } from '../../components/view';

import { ContentStore } from '../../app/store';
//import {  } from './content-actions';



@customElement('edit-page')
export class EditPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Edit';



	render() {

		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
				Edit
			</div>
			
			

    `;
	}



	stateChanged(state: ContentStore) {
		if (state.content) {
			this.loading = state.content.loading;
			this.errorMessage = state.content.errorMessage;
		}

	}




}


export default EditPageElement;


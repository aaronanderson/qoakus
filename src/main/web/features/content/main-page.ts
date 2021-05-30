import {LitElement, CSSResult, html, css} from 'lit';
import {property, customElement} from 'lit/decorators.js';

import { ViewElement } from '../../components/view';

import { ContentStore } from '../../app/store';
//import {  } from './content-actions';



@customElement('main-page')
export class MainPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Main';

	//@property({ type: Array })
	//scripts: Array<Script> = [];
	
	@property({ type: Number })
	selected?: number = -1;



	firstUpdated() {
		//this.dispatch(fetchScripts());		
	}


	render() {

		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
				Main
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


export default MainPageElement;


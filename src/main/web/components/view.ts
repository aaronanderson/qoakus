import {html, css, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';


import { connect, store, ContentStore } from '../app/store';

import { Router } from '@vaadin/router';

//@ts-ignore
import {bootstrapRebootCSS, bootstrapCSS} from './bootstrap';

export class ViewElement extends connect<ContentStore>(store)(LitElement) {

	@property({ type: String, attribute: 'page-title', reflect: true })
	pageTitle?: string;

	@property({ type: String, attribute: 'page-sub-title', reflect: true })
	subPageTitle?: string;

	@property({ type: Boolean })
	loading?: boolean = false;

	@property({ type: String })
	loadingMessage = "";

	@property({ type: String })
	errorMessage?: string;

	location?: Router.Location;

	//lwdc-section-row

	static get styles() {
	  return [bootstrapRebootCSS, bootstrapCSS];
	}

	get pageTitleTemplate() {
		return html`<div class="container">
  						<div class="page-header">
    						<h2>${this.pageTitle}</h2>      
  						</div>
					</div>
					<br/>
					`;
	}

	get errorTemplate(){
		if (this.errorMessage) {
			return html `<div class="container">
							<div class="alert alert-danger" role="alert">${this.errorMessage}</div>
						</div>`;
		}
	}

	get subPageTitleTemplate() {
		if (this.subPageTitle) {
			return html`<div class="wdc-page-sub-header-title">${this.subPageTitle}</div>`;
		}
	}

	get loadingTemplate() {
		if (this.loading) {
			return html`<div class="container">
							<div class="spinner-border text-primary" role="status">
	  							<span class="visually-hidden">Loading...</span>
							</div>
						</div>	
			`;
		}
	}
	


}





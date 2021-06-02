import { LitElement, CSSResult, html, css, TemplateResult } from 'lit';
import { property, customElement } from 'lit/decorators.js';

import { Router, RouterLocation, EmptyCommands } from '@vaadin/router';
import marked from 'marked';

import { ViewElement } from '../../components/view';

import { Content, ContentDetails, viewContent, newContent, editContent, deleteContent, readFile } from './content-actions';
import { ContentStore } from '../../app/store';



@customElement('view-page')
export class ViewPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'View';

	@property({ type: Object })
	details?: ContentDetails;

	onAfterEnter(location: RouterLocation, commands: EmptyCommands, router: Router) {
		console.log("dispatch", location.pathname, commands);
		this.dispatch(viewContent(location.pathname.substring(5)));
	}

	render() {
		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
				${this.parentTemplate}
				${this.detailsTemplate}
				${this.childTemplate}
				
				<div class="btn-group" role="group" aria-label="Scripts">			  		
					<button type="button" ?disabled=${!this.details} class="btn btn-primary mr-2" @click=${(e: MouseEvent)=> this.dispatch(newContent(this.details?.parent))}>New</button>
					<button type="button" ?disabled=${!this.details} class="btn btn-secondary mr-2" @click=${(e: MouseEvent)=> this.dispatch(editContent())}>Edit</button>
					<button type="button" ?disabled=${!this.details || this.details.path =="/"} class="btn btn-danger mr-4" @click=${(e: MouseEvent)=> this.dispatch(deleteContent(this.details?.path))}>Delete</button>
				</div>
			</div>
			
			
    `;
	}

	get parentTemplate() {
		if (this.details && this.details.parent) {
			return html`
			<span>Parent</span>
			<ul class="nav flex-column">
				<li class="nav-item">
					<a class="nav-link" href="/view${this.details.parent.path}">${this.details.parent.title}</a>
				</li>
			</ul>
			`;
		}
	}

	get detailsTemplate() {
		if (this.details && this.details.mainContent) {
			const div = document.createElement('div');
			div.classList.add("container");
			readFile(this.details.mainContent).then((text: string) =>
				div.innerHTML = marked(text)
			);

			return html
				`<hr class="mt-5 mb-5" />${div}
<hr class="mt-5 mb-5" />`;
		}
	}

	get childTemplate() {
		if (this.details && this.details.children.length > 0) {
			return html`
				<span>Children</span>
				<ul class="nav flex-column">
					${this.details.children.map((c: Content) => html`<li class="nav-item">
						<a class="nav-link" href="/view${c.path}">${c.title}</a>
					</li>`)}
				</ul>
			
			`;
		}
	}

	stateChanged(state: ContentStore) {
		if (state.content) {
			console.log(state.content);
			const { contentDetails, loading, errorMessage } = state.content;
			this.loading = loading;
			this.errorMessage = errorMessage;
			this.details = contentDetails;
			if (this.details) {
				this.pageTitle = this.details.title;
			}
		}

	}




}


export default ViewPageElement;


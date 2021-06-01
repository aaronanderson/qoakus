import { LitElement, CSSResult, html, css, TemplateResult } from 'lit';
import { property, customElement } from 'lit/decorators.js';

import { Router, RouterLocation, EmptyCommands } from '@vaadin/router';

import { ViewElement } from '../../components/view';

import { Content, ContentDetails, viewContent } from './content-actions';
import { ContentStore } from '../../app/store';



@customElement('view-page')
export class ViewPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'View';

	@property({ type: Object })
	parentContent?: Content;

	@property({ type: Object })
	details?: ContentDetails;



	@property({ type: Array })
	childContent?: Array<Content>;

	onAfterEnter(location: RouterLocation, commands: EmptyCommands, router: Router) {
		console.log("dispatch", location.pathname, commands);
		this.dispatch(viewContent(location.pathname));
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
			</div>
    `;
	}

	get parentTemplate() {
		if (this.parentContent) {
			return html`
			<span>Parent</span>
			<ul class="nav flex-column">
				<li class="nav-item">
					<a class="nav-link" href="${this.parentContent.path}">${this.parentContent.title}</a>
				</li>
			</ul>
			`;
		}
	}

	get detailsTemplate() {
		if (this.details) {
			return html`<div>Content Details</div>`;
		}
	}

	get childTemplate() {
		if (this.childContent) {
			return html`
				<span>Children</span>
				<ul class="nav flex-column">
					${this.childContent.map((c: Content) => html`<li class="nav-item">
						<a class="nav-link" href="${c.path}">${c.title}</a>
					</li>`)}
				</ul>
			
			`;
		}
	}

	stateChanged(state: ContentStore) {
		if (state.content) {
			console.log(state.content);
			const { parent, children, loading, errorMessage } = state.content;
			this.loading = loading;
			this.errorMessage = errorMessage;
			this.parentContent = parent;
			this.childContent = children;
		}

	}




}


export default ViewPageElement;


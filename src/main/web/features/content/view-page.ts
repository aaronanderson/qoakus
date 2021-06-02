import { LitElement, CSSResult, html, css, TemplateResult } from 'lit';
import { property, customElement } from 'lit/decorators.js';

import { Router, RouterLocation, EmptyCommands } from '@vaadin/router';
import marked from 'marked';

import { ViewElement } from '../../components/view';

import { Content, ContentDetails, ContentFile, viewContent, newContent, editContent, deleteContent, readFile } from './content-actions';
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
				${this.filesTemplate}
				${this.childTemplate}
				
				<div class="btn-group my-5" role="group" aria-label="Scripts">			  		
					<button type="button" ?disabled=${!this.details} class="btn btn-primary m-2" @click=${(e: MouseEvent)=> this.dispatch(newContent(this.details?.parent))}>New</button>
					<button type="button" ?disabled=${!this.details} class="btn btn-secondary m-2" @click=${(e: MouseEvent)=> this.dispatch(editContent())}>Edit</button>
					<button type="button" ?disabled=${!this.details || this.details.path =="/"} class="btn btn-danger m-2" @click=${(e: MouseEvent)=> this.dispatch(deleteContent(this.details?.path))}>Delete</button>
				</div>
			</div>
			
			
    `;
	}

	get parentTemplate() {
		if (this.details && this.details.parent) {
			return html`
			<span class="lead">Parent</span>
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
				`<hr class="my-5" />${div}
<hr class="my-5" />`;
		}
	}
	
	get filesTemplate() {
		if (this.details) {
			const attachments =this.details.files.filter((c: ContentFile) => c.fileType =="attachment");
			if (attachments.length > 0){
				let path = this.details?.path;
				path = path == "/"? "" : path;				 
				return html`
					<span class="lead">Files</span>
					<ul class="nav flex-column">
						${attachments.map((c: ContentFile) => html`<li class="nav-item">
							<a class="nav-link" download href="/api/content/file${path}/${c.name}">${c.name}</a>
						</li>`)}
					</ul>
				
				`;
			}
		}
	}

	get childTemplate() {
		if (this.details && this.details.children.length > 0) {
			return html`
				<span class="lead">Children</span>
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


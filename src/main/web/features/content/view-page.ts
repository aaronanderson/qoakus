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
				div.innerHTML = marked(text, <marked.MarkedOptions>{renderer: this.renderer})
			);

			return html
				`<hr class="my-5" />${div}<hr class="my-5" />`;
		}
	}
	
	get renderer(){
		const renderer = new marked.Renderer();
		let baseUrl = "";
		if (this.details){
			baseUrl = `/api/content/file${this.details.path}/`;
		}
		

		const originalRendererLink = renderer.link.bind(renderer);
		const originalRendererImage = renderer.image.bind(renderer);
		const regularExpressionForURL = /^https?:\/\//i;
		renderer.link = (href, title, text) => {				
			if (href && !regularExpressionForURL.test(href)) {
			href = baseUrl + href;		
			}
			return `<a href="${href}" target="_blank">${text}</a>`;					
  			//return originalRendererLink(href, title, text);
		};

		renderer.image = (href, title, text) => {		  
			if (title && href && !regularExpressionForURL.test(href)) {
				href = baseUrl + href;
				//https://github.com/markedjs/marked/issues/339#issuecomment-479347433
		  		const exec = /=\s*(\d*)\s*x\s*(\d*)\s*$/.exec(title);
				 const sanitize = (str: string)=> {
		  			return str.replace(/&<"/g, (m)=> {
		    		if (m === "&") return "&amp;"
		    		if (m === "<") return "&lt;"
		    		return "&quot;"
		  		})
				};
   	  		    let res = '<img src="' + sanitize(href) + '" alt="' + sanitize(text)
		  		if (exec && exec[1]) res += '" height="' + exec[1]
		  		if (exec && exec[2]) res += '" width="' + exec[2]
		  		return res + '">'
			}	
		  	return originalRendererImage(href, title, text);
		};
		return renderer;
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


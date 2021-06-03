import { LitElement, CSSResult, html, css } from 'lit';
import { property, customElement, query } from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';

import { ViewElement } from '../../components/view';
import {biFilePlusSVG, biFileMinusSVG} from '../../components/bootstrap';

import { ContentStore } from '../../app/store';
import {ContentDetails, ContentFile, EditMode, saveContent, fileUpload, fileDelete  } from './content-actions';

//import 'easymde/dist/easymde.min.js';
//import EasyMDE from 'easymde';
//import EasyMDE from 'easymde';
//import 'easymde/src/js/easymde.js';
//const easymdeCSS = css`'!cssx|easymde/src/css/easymde.css'`;


@customElement('edit-page')
export class EditPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Edit';
	
	@property({ type: Boolean })
	modified= false;
	
	@property({ type: Boolean })
	hasErrors= false;

	@property({ type: Object })
	details?: ContentDetails;
	
	@property({ type: Object })
	editMode?: EditMode;
	
	
	@query('#contentTitle')
	contentTitleElement?:HTMLInputElement;
	
	@query('#contents')
	contentsElement?:HTMLTextAreaElement;
	
	@query("#fileUpload")
	fileInputElement?: HTMLInputElement;

	@query('form')
	formElement?: HTMLFormElement;
	
	static get styles() {
	  return [super.styles, css `
			.bi-file-plus-fill {
				transform: scale(2.0); 
				color: var(--bs-blue);
			 }
			 .bi-file-minus-fill {
				transform: scale(2.0); 
				color: var(--bs-red);
			 }
				
			 input[type=file] {
				display: none;
			 }
		`];
	}
	
	firstUpdated(){
		//var easyMDE = new EasyMDE({element: this.contentsElement});	
	}
	
	render() {
		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
			
				<form class="needs-validation w-50">
					<div class="form-group">
						<label  for="contentTitle">Name</label>
	    				<input class="form-control" type="text" required placeholder="Content Title" id="contentTitle" .value=${ifDefined(this.details?.title)} @change=${(e: Event) => this.modified = true}></input>
					</div>
					
					<div class="form-group my-5">
						<label  for="contents">Content</label>
	    				<textarea class="form-control" type="textarea" required placeholder="Content Title" id="contents"  @change=${(e: Event) => this.modified = true}></textarea>
					 </div>
					
					 <input type="file" id="fileUpload" ?multiple=${true} @change=${(e: Event) => { this.handleFileSelected(); this.requestUpdate();}}/>
					 
				</form>
				${this.editorTemplate}
				
				${this.filesTemplate}
					
				<div class="btn-group my-5" role="group" aria-label="Run">			  		
					<button ?disabled=${!this.modified || this.hasErrors } type="button" class="btn btn-primary m-2" @click=${this.handleSave}>Save</button>
					<button type="button" class="btn btn-secondary m-2" @click=${this.handleBack}>Back</button>	
				</div>
			</div>
			
			

    `;
	}
	
	get filesTemplate(){
		if (this.details && this.editMode == EditMode.update){
			let path = this.details?.path;
			path = path == "/"? "" : path;
		
			return html `
			<div class="container">
					<div class="row w-50 my-3">
					    <div class="col lead">Files</div>
					    <div class="col" @click=${(e: MouseEvent)=> this.handleFileAdd()}>${biFilePlusSVG}</div>						   
					</div>
					${this.details.files.filter((c: ContentFile) => c.fileType =="attachment").map((c: ContentFile) => html `
					<div class="row w-50 my-3">
					    <div class="col"><a class="nav-link" download href="/api/content/file${path}/${c.name}">${c.name}</a></div>
					    <div class="col"  @click=${(e: MouseEvent)=> this.handleFileRemove(c)}>${biFileMinusSVG}</div>						   
					</div>`)}
					</ul>
										
				</div>
			
			
			`;
		}
	}
	
	get editorTemplate(){
			return html ``;
	}
	
	handleFileAdd(){		
		console.log("Add");
		if (this.fileInputElement) {
			this.fileInputElement.click();
		}
	}
	
	handleFileSelected() {
		if (this.fileInputElement && this.fileInputElement.files && this.details){
			const files = Array.from(this.fileInputElement.files);
			console.log("Selected", files);	
			this.dispatch(fileUpload(this.details.path, files));
		}
	}
	
	handleFileRemove(c: ContentFile){		
		console.log("Remove", c);
		if (this.details){
			this.dispatch(fileDelete(this.details.path, c.name));
		}
	}

	handleSave(e: MouseEvent){		
		if (this.formElement){
			if (this.formElement.checkValidity()){
				console.log("valid!");
				const contentUpdate = <ContentDetails>{
					title: ""
				}	
			} else {
				console.log("invalid!");
			}
			this.formElement.classList.add('was-validated');
		}
		
	}
	
	handleBack(e: MouseEvent){
		 window.history.back();
	}


	stateChanged(state: ContentStore) {
		if (state.content) {
			const {editMode, contentDetails, loading, errorMessage} = state.content;
			this.details= contentDetails;
			this.editMode = editMode;			
			if (this.editMode == EditMode.add){
				this.pageTitle = "New";	
			} else if (this.editMode == EditMode.update){
				this.pageTitle = "Edit";
			}
			this.loading = loading;
			this.errorMessage = errorMessage;			
		}

	}
	
}

export default EditPageElement;


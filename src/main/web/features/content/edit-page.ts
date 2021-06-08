import { LitElement, CSSResult, html, css } from 'lit';
import { property, customElement, query } from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';

import { ViewElement } from '../../components/view';
import {biFilePlusSVG, biFileMinusSVG, biMailboxSVG} from '../../components/bootstrap';

import { ContentStore } from '../../app/store';
import {ContentDetails, ContentFile, EditMode, saveContent, readFile, fileUpload, fileDelete, markedRenderer, imageUpload  } from './content-actions';

import EasyMDE from 'easymde';
import CodeMirror, {
	Editor,	
	EditorChange,
} from 'codemirror';


const easymdeCSS = css`'!cssx|easymde/src/css/easymde.css'`;
//EasyMDE doesn't include the CodeMirror CSS so manually include it
const editorCSS = css`'!cssx|codemirror/lib/codemirror.css'`;


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
	
	easyMDE?: EasyMDE;
	
	fileType?: string; 
	
	static get styles() {
	  return [super.styles,editorCSS, easymdeCSS, css `
			.bi {
				transform: scale(2.0); 			
			 }
		
			 .bi-file-plus-fill, .bi-mailbox2 {
				color: var(--bs-blue);			
			 }
		
			 .bi-file-minus-fill {
				color: var(--bs-red);			
			 }
		
			 .bi-file-plus-fill, .bi-file-minus-fill {
					cursor: pointer;
			 }
				
			 input[type=file] {
				display: none;
			 }
		`];
	}
	
	firstUpdated(){
		//let EasyMDE add the fontawesome stylesheet to the document head for the @font-face reference. Also include fontawesome inline so that it gets added to the shadowDOM. 
		//pass a custom image upload function so Lit can be refreshed on completion and a custom image relative path can be returned.
		let imageUploadFunction = (file: File, onSuccess: Function, onError: Function)=> this.dispatch(imageUpload(file, onSuccess, onError, this.details?.path));
		this.easyMDE = new EasyMDE({element: this.contentsElement, 
									autoDownloadFontAwesome: true, 
									uploadImage: true, 
									imageUploadFunction: imageUploadFunction, 
									renderingConfig: {markedOptions: {renderer: markedRenderer(this.details?.path)}}
							});
		if (this.details && this.details.mainContent){
			readFile(this.details.mainContent).then((text: string) => {
					this.easyMDE?.value(text);
			});	
		}
		console.log(this.easyMDEElement);		 
		this.easyMDEElement?.classList.add("form-control");		
		
		
		this.easyMDE.codemirror.on("changes", (e: Editor, c: Array<EditorChange>) => {
			console.log("MD changes", c);
			if (c.find((c: any) => c.origin != "setValue")){
				this.modified = true;
			}			 
		});
		
	
	}
	
	get easyMDEElement(){		
			return this.easyMDE?.codemirror.getWrapperElement().parentNode as HTMLElement;
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
					
					
					<div class="form-group">
						<link rel="stylesheet" type="text/css" href="https://maxcdn.bootstrapcdn.com/font-awesome/latest/css/font-awesome.min.css"></link>
						<label  for="contents">Content</label>						  
	    				<textarea id="contents"></textarea>
						<div id="validationServer03Feedback" class="invalid-feedback">Content required..</div>
					 </div>
					
					 <input type="file" id="fileUpload" ?multiple=${true} @change=${(e: Event) => { this.handleFileSelected(); this.requestUpdate();}}/>
					 
				</form>
				
				${this.filesTemplate("Images","image")}
				${this.filesTemplate("Files","attachment")}
					
				<div class="btn-group my-5" role="group" aria-label="Run">			  		
					<button ?disabled=${!this.modified || this.hasErrors } type="button" class="btn btn-primary m-2" @click=${this.handleSave}>Save</button>
					<button type="button" class="btn btn-secondary m-2" @click=${this.handleBack}>Back</button>	
				</div>
			</div>
			
			

    `;
	}
	
	filesTemplate(title: string, fileType: string){
		if (this.details && this.editMode == EditMode.update){
			let path = this.details?.path;
			path = path == "/"? "" : path;
		
			return html `
			<div class="container">
					<div class="row w-50 my-3">
					    <div class="col lead">${title}</div>
					    <div class="col d-flex flex-row">
							<div  @click=${(e: MouseEvent)=> this.handleFileAdd(fileType)}>${biFilePlusSVG}</div>
							${fileType == "attachment"? html `<div class="mx-4" @dragover=${this.handleDrag} @drop=${this.handleDrop}>${biMailboxSVG}</div>`: undefined}
						</div>													  
					</div>
					${this.details.files.filter((c: ContentFile) => c.fileType ==fileType).map((c: ContentFile) => html `
					<div class="row w-50 my-3">
					    <div class="col"><a class="nav-link" download href="/api/content/file${path}/${c.name}">${c.name}</a></div>
					    <div class="col"><div  @click=${(e: MouseEvent)=> this.handleFileRemove(c)}>${biFileMinusSVG}</div></div>						   
					</div>`)}
					</ul>
										
				</div>
			
			
			`;
		}
	}
	
	handleDrag(e: DragEvent){
		console.log("Drag Over", e);
		//(e as any).originalEvent.dataTransfer.dropEffect = 'copy';
		if (e.dataTransfer){
			 const isFile = e.dataTransfer.types.includes("Files");
			console.log("Drag types", e.dataTransfer.types);
			if (isFile){
				e.dataTransfer.dropEffect = 'copy';
				e.preventDefault();		
			}
		}
	}
	
	handleDrop(e: DragEvent){
		console.log("Drop", e);
		e.preventDefault();
		if (e.dataTransfer){
			for (let i=0; i<e.dataTransfer.items.length; i++){
				let item = e.dataTransfer.items[i];
				console.log("Dropped item", item.type, item.kind);
				if ("file"== item.kind){
					let file = item.getAsFile();
					if (file){
						this.dispatch(fileUpload("attachment", this.details?.path, [file]));					
					}					
				}else if ("string"== item.kind){
					//item.getAsString((s)=> console.log("Dropped string", s));	
				} 
			}
		}
	}
	
	handleFileAdd(fileType: string){
		this.fileType= fileType;		
		console.log("Add");
		if (this.fileInputElement) {
			this.fileInputElement.click();
		}
	}
	
	handleFileSelected() {
		if (this.fileInputElement && this.fileInputElement.files && this.details){
			const files = Array.from(this.fileInputElement.files);
			console.log("Selected", files);	
			this.dispatch(fileUpload(this.fileType, this.details.path, files));
		}
		this.fileType = undefined;
	}
	
	handleFileRemove(c: ContentFile){		
		console.log("Remove", c);
		if (this.details){
			this.dispatch(fileDelete(this.details.path, c.name));
		}
	}

	handleSave(e: MouseEvent){
		let isValid = true;
		let contentValue = undefined;
		if (this.easyMDE){
			contentValue = this.easyMDE.value();
			if (contentValue==""){
				this.easyMDEElement?.classList.remove("is-valid");
				this.easyMDEElement?.classList.add("is-invalid");
			}else {
				this.easyMDEElement?.classList.remove("is-invalid");
				this.easyMDEElement?.classList.add("is-valid");
			}
			
		}		
		if (this.formElement){
			isValid =  isValid && this.formElement.checkValidity();
			this.formElement.classList.add('was-validated');			
		}
		if (isValid && this.details){
				console.log("valid!");
				const contentUpdate = <ContentDetails>{
					path: this.details.path,
					title: "",
					mainContent: this.details.mainContent
					
				};
				this.dispatch(saveContent(contentUpdate));	
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


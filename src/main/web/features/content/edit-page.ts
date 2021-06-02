import { LitElement, CSSResult, html, css } from 'lit';
import { property, customElement, query } from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';

import { ViewElement } from '../../components/view';

import { ContentStore } from '../../app/store';
import {ContentDetails, EditMode, saveContent  } from './content-actions';



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
	
	@query('#contentTitle')
	contentTitleElement?:HTMLInputElement;

	@query('form')
	formElement?: HTMLFormElement;

	render() {

		return html`

			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">
			
				<form class="needs-validation">
					<div class="form-group">
						<label  for="contentTitle">Name</label>
	    				<input class="form-control" type="text" required placeholder="Content Title" id="contentTitle" .value=${ifDefined(this.details?.title)} @change=${(e: Event) => this.modified = true}></input>
					 </div>
				</form>
					
				<div class="btn-group" role="group" aria-label="Run">			  		
					<button ?disabled=${!this.modified || this.hasErrors } type="button" class="btn btn-primary mr-2" @click=${this.handleSave}>Save</button>
					<button type="button" class="btn btn-secondary" @click=${this.handleBack}>Back</button>	
				</div>
			</div>
			
			

    `;
	}

	handleSave(e: MouseEvent){		
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
	
	handleBack(e: MouseEvent){
		 window.history.back();
	}


	stateChanged(state: ContentStore) {
		if (state.content) {
			const {editMode, contentDetails, loading, errorMessage} = state.content;
			this.details= contentDetails;			
			if (editMode == EditMode.add){
				this.pageTitle = "New";	
			} else if (editMode == EditMode.update){
				this.pageTitle = "Edit";
			}
			this.loading = loading;
			this.errorMessage = errorMessage;			
		}

	}
	
}

export default EditPageElement;


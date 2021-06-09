import { LitElement, CSSResult, html, css, TemplateResult } from 'lit';
import { property, customElement } from 'lit/decorators.js';

import { Router, RouterLocation, EmptyCommands } from '@vaadin/router';
import marked from 'marked';

import { ViewElement } from '../../components/view';
import { biFileTextSVG} from '../../components/bootstrap';

import { Content, ContentDetails, ContentFile, User, viewContent, fetchUser, newContent, editContent, deleteContent, readFile, markedRenderer } from './content-actions';
import { ContentStore } from '../../app/store';


@customElement('search-page')
export class SearchPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Search';

		
	@property({ type: Object })
	details?: ContentDetails;


	
	//https://github.com/apache/jackrabbit-oak/blob/trunk/oak-search-elastic/src/test/java/org/apache/jackrabbit/oak/plugins/index/elastic/ElasticFullTextAsyncTest.java
	
	render() {
		return html`
			${this.pageTitleTemplate}
			${this.loadingTemplate}
			${this.errorTemplate}
			<div class="container">				
				Search 
				<div class="btn-group my-5" role="group" aria-label="Scripts">			  		
					<button type="button" ?disabled=${!this.details} class="btn btn-primary m-2" @click=${(e: MouseEvent)=> this.dispatch(newContent(this.details?.parent))}>New</button>
					<button type="button" ?disabled=${!this.details} class="btn btn-secondary m-2" @click=${(e: MouseEvent)=> this.dispatch(editContent())}>Edit</button>
					<button type="button" ?disabled=${!this.details || this.details.path =="/"} class="btn btn-danger m-2" @click=${(e: MouseEvent)=> this.dispatch(deleteContent(this.details?.path))}>Delete</button>
				</div>
			</div>
			
			
    `;
	}

	

	stateChanged(state: ContentStore) {
		if (state.content) {
			//console.log(state.content);
			const { loading, errorMessage } = state.content;
			this.loading = loading;
			this.errorMessage = errorMessage;			
		}

	}




}


export default SearchPageElement;


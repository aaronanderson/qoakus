import { LitElement, CSSResult, html, css, TemplateResult } from 'lit';
import { property, customElement, query } from 'lit/decorators.js';

import { Router, RouterLocation, EmptyCommands } from '@vaadin/router';

import { ViewElement } from '../../components/view';

import { Content, search, searchReset } from './content-actions';
import { ContentStore } from '../../app/store';


@customElement('search-page')
export class SearchPageElement extends ViewElement {

	@property({ type: String })
	pageTitle = 'Search';

	@property({ type: Array })
	searchResults?: Array<Content>;

	@query('#searchBar')
	searchElement?: HTMLInputElement;

	@query('form')
	formElement?: HTMLFormElement;


	//cleanup
	onAfterLeave(location: RouterLocation, commands: EmptyCommands, router: Router) {
		if (this.searchElement){
			this.searchElement.value = "";
		}
		this.dispatch(searchReset());				
	}
		
	render() {
		return html`
			${this.pageTitleTemplate}
			<div class="container w-50">
				${this.loadingTemplate}
				${this.errorTemplate}
				
				<form class="needs-validation" onsubmit="return false;">
					<div class="form-group">
						<label for="contentTitle">Name</label>
						<input class="form-control" type="search" required placeholder="Search" id="searchBar" @input=${(e:Event)=> {if((e.target as any).value=="")this.dispatch(searchReset())}}></input>
					</div>
				</form>
			
				${this.searchResultsTemplate}
			
				<div class="btn-group my-5" role="group" aria-label="Scripts">
					<button type="button" class="btn btn-info m-2" @click=${this.handleSearch}>Search</button>
					<button type="button" class="btn btn-secondary m-2" @click=${this.handleBack}>Back</button>
				</div>
			</div>
			
			
    `;
	}

	get searchResultsTemplate() {
		if (this.searchResults && this.searchResults.length > 0) {
			return html`
			<div class="container my-5">
				${this.searchResults.map((c: Content)=> html `
						<div class="row my-2">
    						<div class="col"><a href="/view${c.path}">${c.title}</a></div>
						</div>			
				`)}
				
			</div>
			
			`;
		} else if (this.searchResults){
			return html`<div class="alert alert-primary my-5" role="alert">No results found.</div>`;
		}
	}

	handleSearch(e: MouseEvent) {
		let isValid = true;
		if (this.formElement) {
			isValid = isValid && this.formElement.checkValidity();
			this.formElement.classList.add('was-validated');
		}
		if (isValid && this.searchElement) {
			this.dispatch(search(this.searchElement.value));
		}

	}

	handleBack() {
		window.history.back();
	}

	stateChanged(state: ContentStore) {
		if (state.content) {
			const {  searchResults, loading, errorMessage } = state.content;
			this.searchResults = searchResults;
			this.loading = loading;
			this.errorMessage = errorMessage;
		}

	}




}


export default SearchPageElement;


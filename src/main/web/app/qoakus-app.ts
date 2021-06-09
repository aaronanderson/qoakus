import { Router } from '@vaadin/router';

import {html, css, LitElement, CSSResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';


import '../features/content/view-page';
import '../features/content/edit-page';
import '../features/content/search-page';

//@ts-ignore
import {bootstrapRebootCSS, bootstrapCSS} from '../components/bootstrap';

export const constructableStylesheetsSupported = ('adoptedStyleSheets' in Document.prototype) && ('replace' in CSSStyleSheet.prototype);
	
export const styleLightDOM = (styles: CSSResult, styleID: string) => {
	if (constructableStylesheetsSupported) {
		(document as any).adoptedStyleSheets = !!(document as any).adoptedStyleSheets ? [...(document as any).adoptedStyleSheets, styles.styleSheet] : [styles.styleSheet];
	} else {
		if (!document.head.querySelector('#' + styleID)) {
			const styleNode = document.createElement('style');
			styleNode.id = styleID;
			styleNode.innerHTML = styles.cssText;
			document.head.appendChild(styleNode);
		}
	}	
}

//@font-face css can only be set at the document level.
styleLightDOM((bootstrapRebootCSS as CSSResult), 'bootstrap-reboot');		
styleLightDOM((bootstrapCSS as CSSResult), 'bootstrap');
			
@customElement('qoakus-app')
export class AppElement extends LitElement {


  firstUpdated() {
	
    if (this.shadowRoot) {

      let mainContent: HTMLElement = this.shadowRoot.getElementById('main-content') as HTMLElement;
      let router = new Router(mainContent);
      router.setRoutes([
        { path: '/', redirect: '/view/' },
		{ path: '/edit', component: 'edit-page' },
		{ path: '/search', component: 'search-page' },
		{ path: '/view/(.*)', component: 'view-page' },
      ]);


    }

  }


  static get styles() {
    return [bootstrapRebootCSS, bootstrapCSS];
  }


  render() {
    return html`
	  <div class="bg-light p-5 rounded-lg m-3">
	    <h1 class="display-4">Qoakus - Jackrabbit Oak, Quarkus, AWS Demo</h1>      
	  </div>
      <main>
        <section class="main-content" id="main-content"></section>
      </main>



    `;
  }




 


}



export default AppElement;




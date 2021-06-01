import { Router } from '@vaadin/router';

import {html, css, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';


import '../features/content/view-page';
import '../features/content/edit-page';

//@ts-ignore
import {bootstrapStyles} from '@granite-elements/granite-lit-bootstrap/granite-lit-bootstrap.js';


@customElement('qoakus-app')
export class AppElement extends LitElement {


  firstUpdated() {
	
    if (this.shadowRoot) {

      let mainContent: HTMLElement = this.shadowRoot.getElementById('main-content') as HTMLElement;
      let router = new Router(mainContent);
      router.setRoutes([
        { path: '/edit/(.*)', component: 'edit-page' },
		{ path: '/(.*)', component: 'view-page' },
      ]);


    }

  }


  static get styles() {
    return [bootstrapStyles];
  }


  render() {
    return html`
	  <div class="jumbotron">
	    <h1>Qoakus - Jackrabbit Oak, Quarkus, AWS Demo</h1>      
	  </div>
      <main>
        <section class="main-content" id="main-content"></section>
      </main>



    `;
  }




 


}



export default AppElement;



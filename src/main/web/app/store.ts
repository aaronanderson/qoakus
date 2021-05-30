import { Store, Unsubscribe, AnyAction } from 'redux';
import { combineReducers } from 'redux'
import { createStore, applyMiddleware } from 'redux';
import devTool from 'redux-devtools-extension';
import thunk from 'redux-thunk';

import content from '../features/content/content-reducer';
import { ContentState } from '../features/content/content-actions';


import { openDB } from "idb";

export const qoakusDB = () => {
	return openDB("qoakusDB", 1, {
		upgrade(db, oldVersion) {
			if (oldVersion == 0) {
				let contentStore = db.createObjectStore('content', { keyPath: 'contentId', autoIncrement: true });
				contentStore.createIndex("lastModified", "lastModified", { unique: false });				
			}
		},
	});
}

console.log('qoakusDB created');


const rootReducer = combineReducers({
	content: content,
})

export const store = configureStore();

export default function configureStore() {
	console.log('store created');
	const store = createStore(rootReducer, devTool.composeWithDevTools(
		applyMiddleware(thunk)
	));
	// if (process.env.NODE_ENV !== 'production' && (module as any).hot) {
	// 	(module as any).hot.accept('./reducers', () => store.replaceReducer(rootReducer))
	// }
	return store
}



export interface ContentStore {
	content: ContentState
}



type Constructor<T> = new (...args: any[]) => T;


//exporting LitElement with it's private/protected members generates a 'TS4094 exported class expression may not be private or protected' error so define a limited interface
interface ConnectedLitElement {
	connectedCallback?(): void;
	disconnectedCallback?(): void;
}

export const connect =
	<S>(store: Store<S>) =>
		<T extends Constructor<ConnectedLitElement>>(baseElement: T) =>
			class extends baseElement {
				_storeUnsubscribe!: Unsubscribe;

				connectedCallback() {

					super.connectedCallback && super.connectedCallback();


					this._storeUnsubscribe = store.subscribe(() => this.stateChanged(store.getState()));
					this.stateChanged(store.getState());
				}

				disconnectedCallback() {
					this._storeUnsubscribe();


					super.disconnectedCallback && super.disconnectedCallback();

				}

				stateChanged(_state: S) { }

				dispatch<A extends AnyAction>(_function: A) { return store.dispatch(_function); }
			};
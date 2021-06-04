import {css} from 'lit';
import { svg } from 'lit/static-html.js';


export const bootstrapCSS = css`'!cssx|bootstrap/dist/css/bootstrap.css'`;
export const bootstrapRebootCSS = css`'!cssx|bootstrap/dist/css/bootstrap-reboot.css'`;

//@font-face src: url is not being loaded, load individual SVG icons instead.
//export const bootstrapIconsCSS = css`'!cssx|bootstrap-icons/font/bootstrap-icons.css'`;

export const biFilePlusSVG = svg `'!cssx|bootstrap-icons/icons/file-plus-fill.svg'`;
export const biFileMinusSVG = svg `'!cssx|bootstrap-icons/icons/file-minus-fill.svg'`;
export const biFileTextSVG = svg `'!cssx|bootstrap-icons/icons/file-earmark-text-fill.svg'`;
export const biMailboxSVG = svg `'!cssx|bootstrap-icons/icons/mailbox2.svg'`;







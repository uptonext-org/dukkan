import 'leaflet/dist/leaflet.css';
import './fonts';

import { AppRegistry } from 'react-native';

import { WebApp } from './WebApp';

const root = document.getElementById('root');
if (root === null) {
  throw new Error('#root is missing in index.html');
}
AppRegistry.registerComponent('Dukkan', () => WebApp);
// react-native-web takes the DOM element; react-native's typings only know native root tags.
AppRegistry.runApplication('Dukkan', { rootTag: root as never, initialProps: {} });

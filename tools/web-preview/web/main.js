// Entry: load the bundled fonts, register the app exactly as apps/mobile/index.js does, run it.
import './fonts.js';
import { AppRegistry } from 'react-native';
import '../../../apps/mobile/index.js';

AppRegistry.runApplication('Dukkan', { rootTag: document.getElementById('root') });

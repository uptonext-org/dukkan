/* Runs after the test framework is installed: clean up per-test React Query clients. */
import { disposeQueryClients } from './src/test/render';

afterEach(() => {
  disposeQueryClients();
});

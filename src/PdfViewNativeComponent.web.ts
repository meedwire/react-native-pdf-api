import { View } from 'react-native';

// Web fallback: the native Fabric component does not exist on web, so we expose
// a plain View to keep imports/bundling working. Native operations are no-ops.
export default View;

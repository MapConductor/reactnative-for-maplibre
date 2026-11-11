// react-native の代役。node では本物（Flow 構文）を読めないので、このパッケージの
// JS が触る API だけを最小限に置く。ネイティブへ送ったコマンドは `commands` に残る。
export const commands = [];

export function requireNativeComponent(name) {
  return { nativeViewName: name };
}

export function findNodeHandle(ref) {
  return ref?.nodeHandle ?? null;
}

export const UIManager = {
  dispatchViewManagerCommand(node, command, args) {
    commands.push({ node, command, args });
  },
};

// 本物は数値 (ARGB) を返す。ここでは何が渡ったかが見えるよう包むだけにする。
export function processColor(color) {
  return color == null ? null : { color };
}

export const StyleSheet = { create: (styles) => styles, absoluteFill: {}, absoluteFillObject: {} };
export const View = 'View';
export const Text = 'Text';
export const Linking = { openURL: async () => {} };
export const NativeModules = {};
export const Platform = { OS: 'ios', select: (o) => o.ios ?? o.default };

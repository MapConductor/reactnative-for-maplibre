module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath:
          'import com.mapconductor.react.maplibre.MapConductorMapLibrePackage;',
        packageInstance: 'new MapConductorMapLibrePackage()',
      },
      ios: {
        sourceDir: './ios',
      },
    },
  },
};
